package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPass;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrPrivateArrayDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrSafetyValidatorTest {
    private final GpuIrSafetyValidator validator = new GpuIrSafetyValidator();

    @Test
    void moduleRegistersValidatorThroughServiceLoader() {
        List<GpuIrPass> passes = ServiceLoader.load(GpuIrPass.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertTrue(passes.stream().anyMatch(GpuIrSafetyValidator.class::isInstance));
    }

    @Test
    void acceptsValidVariableArrayAndLoopReferences() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "id", new GpuIrLiteral("0")),
                new GpuIrForLoop(
                        new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                        new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral("4")),
                        new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                        List.of(
                                new GpuIrAssignment(
                                        new GpuIrArrayAccess("output", new GpuIrVariableRef("i")),
                                        new GpuIrArrayAccess("input", new GpuIrVariableRef("i"))
                                ),
                                new GpuIrContinue()
                        )
                ),
                new GpuIrReturn(null)
        )));

        assertDoesNotThrow(() -> validator.run(context(method)));
    }

    @Test
    void rejectsUnknownVariableReferences() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrArrayAccess("output", new GpuIrLiteral("0")), new GpuIrVariableRef("missing"))
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("unknown variable reference: missing"));
    }

    @Test
    void rejectsDuplicateDeclarationsInSameScope() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("2"))
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("duplicate local declaration: value"));
    }

    @Test
    void rejectsContinueAndBreakOutsideValidControlFlow() {
        GpuIrCompiledMethod continueMethod = method(new GpuIrMethod("continueKernel", List.of(new GpuIrContinue())));
        GpuIrCompiledMethod breakMethod = method(new GpuIrMethod("breakKernel", List.of(new GpuIrBreak())));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(continueMethod)))
                .getMessage().contains("continue used outside loop"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(breakMethod)))
                .getMessage().contains("break used outside loop or switch"));
    }

    @Test
    void rejectsUnknownHelperCallTargets() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrHelperCall("missing_helper", "float", List.of()))
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("unknown helper call target: missing_helper"));
    }

    @Test
    void acceptsKnownHelperCallTargets() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("helper", List.of(new GpuIrReturn(new GpuIrLiteral("1.0f")))),
                "jtg_helper",
                List.of(),
                "float",
                List.of()
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrHelperCall("jtg_helper", "float", List.of()))
        )), "jtg_kernel", List.of("jtg_helper"));

        assertDoesNotThrow(() -> validator.run(new GpuIrPassContext(method, List.of(helper), List.of(), true)));
    }

    @Test
    void rejectsHelperCallsMissingFromDependencyMetadata() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("helper", List.of(new GpuIrReturn(new GpuIrLiteral("1.0f")))),
                "jtg_helper",
                List.of(),
                "float",
                List.of()
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrHelperCall("jtg_helper", "float", List.of()))
        )));

        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(helper), List.of(), true))
        );

        assertTrue(exception.getMessage().contains("missing from helperDependencies metadata: jtg_helper"));
    }

    @Test
    void rejectsDuplicateHelperDependencyMetadata() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("helper", List.of(new GpuIrReturn(new GpuIrLiteral("1.0f")))),
                "jtg_helper",
                List.of(),
                "float",
                List.of()
        );
        GpuIrCompiledMethod method = method(
                new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))),
                "jtg_kernel",
                List.of("jtg_helper", "jtg_helper")
        );

        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(helper), List.of(), true))
        );

        assertTrue(exception.getMessage().contains("duplicate helper dependency metadata: jtg_helper"));
    }

    @Test
    void rejectsIntrinsicTemplatesThatReferenceMissingArguments() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrIntrinsicCall(null, "OpenCL", "mad({0}, {1}, {2})", "float", List.of(new GpuIrLiteral("1.0f")))
                )
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("intrinsic template references missing argument {1}"));
    }

    @Test
    void rejectsReceiverTemplateWithoutReceiver() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrIntrinsicCall(null, "OpenCL", "native_sin({this})", "float", List.of())
                )
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("references {this} but has no receiver"));
    }

    @Test
    void rejectsBlankDeclarationTypesAndOperators() {
        GpuIrCompiledMethod blankTypeMethod = method(new GpuIrMethod("blankTypeKernel", List.of(
                new GpuIrVariableDeclaration("", "value", new GpuIrLiteral("1"))
        )));
        GpuIrCompiledMethod blankOperatorMethod = method(new GpuIrMethod("blankOperatorKernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrUnary("", new GpuIrLiteral("1")))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(blankTypeMethod)))
                .getMessage().contains("blank local declaration type for value"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(blankOperatorMethod)))
                .getMessage().contains("blank unary operator"));
    }

    @Test
    void rejectsInvalidReturnShapes() {
        GpuIrCompiledMethod voidMethod = method(new GpuIrMethod("voidKernel", List.of(new GpuIrReturn(new GpuIrLiteral("1")))));
        GpuIrCompiledMethod nonVoidMethod = method(
                new GpuIrMethod("nonVoidKernel", List.of(new GpuIrReturn(null))),
                "jtg_non_void",
                List.of(),
                "int"
        );

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(voidMethod)))
                .getMessage().contains("void method returns a value"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(nonVoidMethod)))
                .getMessage().contains("non-void method returns without a value"));
    }

    @Test
    void rejectsNonPositivePrivateArrayLiteralSizes() {
        GpuIrCompiledMethod zeroSizedArray = method(new GpuIrMethod("kernel", List.of(
                new GpuIrPrivateArrayDeclaration("float", "scratch", new GpuIrLiteral("0"))
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(zeroSizedArray)));

        assertTrue(exception.getMessage().contains("private array literal size must be positive: 0"));
    }

    @Test
    void rejectsPureExpressionStatements() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrExpressionStatement(new GpuIrBinary("+", new GpuIrLiteral("1"), new GpuIrLiteral("2")))
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("expression statement has no observable side effect"));
    }

    @Test
    void acceptsKnownSideEffectIntrinsicExpressionStatements() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrExpressionStatement(new GpuIrIntrinsicCall(null, "barrier", "barrier(1)", "void", List.of()))
        )));

        assertDoesNotThrow(() -> validator.run(context(method)));
    }

    @Test
    void rejectsVoidHelperCallsWithValueResultMetadata() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("helper", List.of(new GpuIrReturn(null))),
                "jtg_helper",
                List.of(),
                "void",
                List.of()
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrExpressionStatement(new GpuIrHelperCall("jtg_helper", "float", List.of()))
        )), "jtg_kernel", List.of("jtg_helper"));

        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(helper), List.of(), true))
        );

        assertTrue(exception.getMessage().contains("void helper call must use void result metadata: jtg_helper"));
    }

    @Test
    void rejectsMissingAssignmentOperands() {
        GpuIrCompiledMethod missingTarget = method(new GpuIrMethod("missingTarget", List.of(
                new GpuIrAssignment(null, new GpuIrLiteral("1"))
        )));
        GpuIrCompiledMethod missingValue = method(new GpuIrMethod("missingValue", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("0")),
                new GpuIrAssignment(new GpuIrVariableRef("value"), null)
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingTarget)))
                .getMessage().contains("missing assignment target"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingValue)))
                .getMessage().contains("missing assignment value"));
    }

    @Test
    void rejectsMissingControlFlowConditionsAndBodies() {
        GpuIrCompiledMethod missingIfCondition = method(new GpuIrMethod("missingIfCondition", List.of(
                new GpuIrIf(null, List.of(), List.of())
        )));
        GpuIrCompiledMethod missingForBody = method(new GpuIrMethod("missingForBody", List.of(
                new GpuIrForLoop(null, new GpuIrLiteral("1"), null, null)
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingIfCondition)))
                .getMessage().contains("missing if condition"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingForBody)))
                .getMessage().contains("missing statement list: for loop body"));
    }

    @Test
    void rejectsMissingSwitchStructure() {
        GpuIrCompiledMethod missingCases = method(new GpuIrMethod("missingCases", List.of(
                new GpuIrSwitch(new GpuIrLiteral("1"), null)
        )));
        GpuIrCompiledMethod missingLabel = method(new GpuIrMethod("missingLabel", List.of(
                new GpuIrSwitch(new GpuIrLiteral("1"), List.of(
                        new GpuIrSwitchCase(Arrays.asList((GpuIrExpression) null), List.of(), false)
                ))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingCases)))
                .getMessage().contains("missing switch cases"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingLabel)))
                .getMessage().contains("missing switch case label"));
    }

    @Test
    void rejectsMissingExpressionOperands() {
        GpuIrCompiledMethod missingBinaryLeft = method(new GpuIrMethod("missingBinaryLeft", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrBinary("+", null, new GpuIrLiteral("1")))
        )));
        GpuIrCompiledMethod missingUnaryOperand = method(new GpuIrMethod("missingUnaryOperand", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrUnary("-", null))
        )));
        GpuIrCompiledMethod missingTernaryBranch = method(new GpuIrMethod("missingTernaryBranch", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrTernary(
                        new GpuIrLiteral("1"),
                        new GpuIrLiteral("2"),
                        null
                ))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingBinaryLeft)))
                .getMessage().contains("missing binary left operand"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingUnaryOperand)))
                .getMessage().contains("missing unary operand"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingTernaryBranch)))
                .getMessage().contains("missing ternary false branch"));
    }

    @Test
    void rejectsMissingExpressionTargetsAndIndexes() {
        GpuIrCompiledMethod missingArrayIndex = method(new GpuIrMethod("missingArrayIndex", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrArrayAccess("input", null))
        )));
        GpuIrCompiledMethod missingCastExpression = method(new GpuIrMethod("missingCastExpression", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrCast("float", null))
        )));
        GpuIrCompiledMethod missingFieldTarget = method(new GpuIrMethod("missingFieldTarget", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrFieldAccess(null, "x"))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingArrayIndex)))
                .getMessage().contains("missing array access index"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingCastExpression)))
                .getMessage().contains("missing cast expression"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingFieldTarget)))
                .getMessage().contains("missing field access target"));
    }

    @Test
    void rejectsMissingCallAndInitializerArguments() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("helper", List.of(new GpuIrReturn(new GpuIrLiteral("1.0f")))),
                "jtg_helper",
                List.of(),
                "float",
                List.of()
        );
        GpuIrCompiledMethod missingHelperArgument = method(new GpuIrMethod("missingHelperArgument", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrHelperCall("jtg_helper", "float", Arrays.asList((GpuIrExpression) null))
                )
        )), "jtg_kernel", List.of("jtg_helper"));
        GpuIrCompiledMethod missingIntrinsicArguments = method(new GpuIrMethod("missingIntrinsicArguments", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrIntrinsicCall(null, "OpenCL", "native_sin({0})", "float", null))
        )));
        GpuIrCompiledMethod missingStructArgument = method(new GpuIrMethod("missingStructArgument", List.of(
                new GpuIrVariableDeclaration("float2", "value", new GpuIrStructInit("float2", Arrays.asList((GpuIrExpression) null)))
        )));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(missingHelperArgument, List.of(helper), List.of(), true))
        ).getMessage().contains("missing helper call argument"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingIntrinsicArguments)))
                .getMessage().contains("missing intrinsic argument list"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingStructArgument)))
                .getMessage().contains("missing struct initializer argument"));
    }

    @Test
    void rejectsVoidMetadataInValueExpressions() {
        GpuIrCompiledMethod voidHelper = method(
                new GpuIrMethod("helper", List.of(new GpuIrReturn(null))),
                "jtg_void_helper",
                List.of(),
                "void",
                List.of()
        );
        GpuIrCompiledMethod voidHelperValue = method(new GpuIrMethod("voidHelperValue", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrHelperCall("jtg_void_helper", "void", List.of()))
        )), "jtg_kernel", List.of("jtg_void_helper"));
        GpuIrCompiledMethod voidIntrinsicValue = method(new GpuIrMethod("voidIntrinsicValue", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrIntrinsicCall(null, "barrier", "barrier(1)", "void", List.of()))
        )));
        GpuIrCompiledMethod voidCastValue = method(new GpuIrMethod("voidCastValue", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrCast("void", new GpuIrLiteral("1")))
        )));
        GpuIrCompiledMethod voidStructValue = method(new GpuIrMethod("voidStructValue", List.of(
                new GpuIrVariableDeclaration("void", "value", new GpuIrStructInit("void", List.of()))
        )));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(voidHelperValue, List.of(voidHelper), List.of(), true))
        ).getMessage().contains("void helper call cannot be used as a value expression: jtg_void_helper"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(voidIntrinsicValue)))
                .getMessage().contains("void intrinsic call cannot be used as a value expression: barrier"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(voidCastValue)))
                .getMessage().contains("cast expression cannot target void"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(voidStructValue)))
                .getMessage().contains("struct initializer cannot target void"));
    }

    @Test
    void rejectsObviousDeclaredTypeMismatches() {
        GpuIrCompiledMethod initializerMismatch = method(new GpuIrMethod("initializerMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "value", new GpuIrCast("int", new GpuIrLiteral("1")))
        )));
        GpuIrCompiledMethod assignmentMismatch = method(new GpuIrMethod("assignmentMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("false")),
                new GpuIrAssignment(new GpuIrVariableRef("flag"), new GpuIrCast("int", new GpuIrLiteral("1")))
        )));
        GpuIrCompiledMethod returnMismatch = method(
                new GpuIrMethod("returnMismatch", List.of(new GpuIrReturn(new GpuIrCast("boolean", new GpuIrLiteral("1"))))),
                "jtg_return_mismatch",
                List.of(),
                "int"
        );

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(initializerMismatch)))
                .getMessage().contains("type mismatch in local initializer for value: expected boolean but got int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(assignmentMismatch)))
                .getMessage().contains("type mismatch in assignment: expected boolean but got int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(returnMismatch)))
                .getMessage().contains("type mismatch in return value: expected int but got boolean"));
    }

    @Test
    void rejectsInferredExpressionTypeMismatches() {
        GpuIrCompiledMethod binaryMismatch = method(new GpuIrMethod("binaryMismatch", List.of(
                new GpuIrVariableDeclaration("float", "left", new GpuIrLiteral("1.0f")),
                new GpuIrVariableDeclaration("double", "right", new GpuIrLiteral("2.0")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrBinary("+", new GpuIrVariableRef("left"), new GpuIrVariableRef("right")))
        )));
        GpuIrCompiledMethod comparisonMismatch = method(new GpuIrMethod("comparisonMismatch", List.of(
                new GpuIrVariableDeclaration("int", "left", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "right", new GpuIrLiteral("2")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrBinary("<", new GpuIrVariableRef("left"), new GpuIrVariableRef("right")))
        )));
        GpuIrCompiledMethod unaryMismatch = method(new GpuIrMethod("unaryMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("false")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrUnary("!", new GpuIrVariableRef("flag")))
        )));
        GpuIrCompiledMethod ternaryMismatch = method(new GpuIrMethod("ternaryMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("float", "left", new GpuIrLiteral("1.0f")),
                new GpuIrVariableDeclaration("double", "right", new GpuIrLiteral("2.0")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrTernary(
                        new GpuIrVariableRef("flag"),
                        new GpuIrVariableRef("left"),
                        new GpuIrVariableRef("right")
                ))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(binaryMismatch)))
                .getMessage().contains("type mismatch in local initializer for value: expected int but got double"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(comparisonMismatch)))
                .getMessage().contains("type mismatch in local initializer for value: expected int but got boolean"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unaryMismatch)))
                .getMessage().contains("type mismatch in local initializer for value: expected int but got boolean"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(ternaryMismatch)))
                .getMessage().contains("type mismatch in local initializer for value: expected int but got double"));
    }

    @Test
    void rejectsWritesToReadOnlyArrayStorage() {
        GpuIrCompiledMethod constantParameterWrite = method(
                new GpuIrMethod("constantParameterWrite", List.of(
                        new GpuIrAssignment(new GpuIrArrayAccess("input", new GpuIrLiteral("0")), new GpuIrLiteral("1.0f"))
                )),
                List.of(
                        new ParsedGpuParameter("input", "float[]", GpuAddressSpace.CONSTANT, false, List.of()),
                        new ParsedGpuParameter("output", "float[]", GpuAddressSpace.GLOBAL, false, List.of())
                )
        );
        GpuIrCompiledMethod constantGlobalWrite = method(
                new GpuIrMethod("constantGlobalWrite", List.of(
                        new GpuIrAssignment(new GpuIrArrayAccess("input", new GpuIrLiteral("0")), new GpuIrLiteral("1.0f"))
                )),
                List.of(
                        new ParsedGpuParameter("input", "float[]", GpuAddressSpace.GLOBAL, true, List.of()),
                        new ParsedGpuParameter("output", "float[]", GpuAddressSpace.GLOBAL, false, List.of())
                )
        );
        GpuIrCompiledMethod writableGlobalWrite = method(new GpuIrMethod("writableGlobalWrite", List.of(
                new GpuIrAssignment(new GpuIrArrayAccess("output", new GpuIrLiteral("0")), new GpuIrLiteral("1.0f"))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(constantParameterWrite)))
                .getMessage().contains("read-only storage cannot be used as array assignment target: input"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(constantGlobalWrite)))
                .getMessage().contains("read-only storage cannot be used as array assignment target: input"));
        assertDoesNotThrow(() -> validator.run(context(writableGlobalWrite)));
    }

    @Test
    void rejectsReadOnlyStoragePassedToMutableHelperParameters() {
        GpuIrCompiledMethod mutatingHelper = method(
                new GpuIrMethod("writeHelper", List.of(new GpuIrReturn(null))),
                "jtg_write_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("target", "float[]", GpuAddressSpace.GLOBAL, false, List.of()))
        );
        GpuIrCompiledMethod readOnlyArgument = method(
                new GpuIrMethod("readOnlyArgument", List.of(
                        new GpuIrExpressionStatement(new GpuIrHelperCall("jtg_write_helper", "void", List.of(new GpuIrVariableRef("input"))))
                )),
                "jtg_kernel",
                List.of("jtg_write_helper"),
                "void",
                List.of(
                        new ParsedGpuParameter("input", "float[]", GpuAddressSpace.CONSTANT, false, List.of()),
                        new ParsedGpuParameter("output", "float[]", GpuAddressSpace.GLOBAL, false, List.of())
                )
        );
        GpuIrCompiledMethod writableArgument = method(new GpuIrMethod("writableArgument", List.of(
                new GpuIrExpressionStatement(new GpuIrHelperCall("jtg_write_helper", "void", List.of(new GpuIrVariableRef("output"))))
        )), "jtg_kernel", List.of("jtg_write_helper"));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(readOnlyArgument, List.of(mutatingHelper), List.of(), true))
        ).getMessage().contains("read-only storage cannot be used as mutable helper argument target for jtg_write_helper: input"));
        assertDoesNotThrow(() -> validator.run(new GpuIrPassContext(writableArgument, List.of(mutatingHelper), List.of(), true)));
    }

    @Test
    void allowsReadOnlyStoragePassedToReadOnlyHelperParameters() {
        GpuIrCompiledMethod readOnlyHelper = method(
                new GpuIrMethod("readHelper", List.of(new GpuIrReturn(null))),
                "jtg_read_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("source", "float[]", GpuAddressSpace.CONSTANT, false, List.of()))
        );
        GpuIrCompiledMethod method = method(
                new GpuIrMethod("readOnlyHelperArgument", List.of(
                        new GpuIrExpressionStatement(new GpuIrHelperCall("jtg_read_helper", "void", List.of(new GpuIrVariableRef("input"))))
                )),
                "jtg_kernel",
                List.of("jtg_read_helper"),
                "void",
                List.of(
                        new ParsedGpuParameter("input", "float[]", GpuAddressSpace.CONSTANT, false, List.of()),
                        new ParsedGpuParameter("output", "float[]", GpuAddressSpace.GLOBAL, false, List.of())
                )
        );

        assertDoesNotThrow(() -> validator.run(new GpuIrPassContext(method, List.of(readOnlyHelper), List.of(), true)));
    }

    @Test
    void rejectsHelperCallArgumentCountMismatch() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("oneArgHelper", List.of(new GpuIrReturn(null))),
                "jtg_one_arg_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("target", "float[]", GpuAddressSpace.GLOBAL, false, List.of()))
        );
        GpuIrCompiledMethod missingArgument = method(
                new GpuIrMethod("missingArgument", List.of(
                        new GpuIrExpressionStatement(new GpuIrHelperCall("jtg_one_arg_helper", "void", List.of()))
                )),
                "jtg_kernel",
                List.of("jtg_one_arg_helper")
        );
        GpuIrCompiledMethod extraArgument = method(
                new GpuIrMethod("extraArgument", List.of(
                        new GpuIrExpressionStatement(new GpuIrHelperCall(
                                "jtg_one_arg_helper",
                                "void",
                                List.of(new GpuIrVariableRef("output"), new GpuIrVariableRef("input"))
                        ))
                )),
                "jtg_kernel",
                List.of("jtg_one_arg_helper")
        );

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(missingArgument, List.of(helper), List.of(), true))
        ).getMessage().contains("helper call argument count mismatch for jtg_one_arg_helper: expected 1 but got 0"));
        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(extraArgument, List.of(helper), List.of(), true))
        ).getMessage().contains("helper call argument count mismatch for jtg_one_arg_helper: expected 1 but got 2"));
    }

    @Test
    void rejectsHelperCallArgumentTypeMismatch() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("flagHelper", List.of(new GpuIrReturn(null))),
                "jtg_flag_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("flag", "boolean", GpuAddressSpace.PRIVATE, false, List.of()))
        );
        GpuIrCompiledMethod mismatch = method(
                new GpuIrMethod("helperTypeMismatch", List.of(
                        new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                        new GpuIrExpressionStatement(new GpuIrHelperCall(
                                "jtg_flag_helper",
                                "void",
                                List.of(new GpuIrVariableRef("value"))
                        ))
                )),
                "jtg_kernel",
                List.of("jtg_flag_helper")
        );
        GpuIrCompiledMethod valid = method(
                new GpuIrMethod("helperTypeMatch", List.of(
                        new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("true")),
                        new GpuIrExpressionStatement(new GpuIrHelperCall(
                                "jtg_flag_helper",
                                "void",
                                List.of(new GpuIrVariableRef("flag"))
                        ))
                )),
                "jtg_kernel",
                List.of("jtg_flag_helper")
        );

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(mismatch, List.of(helper), List.of(), true))
        ).getMessage().contains("type mismatch in helper argument flag for jtg_flag_helper: expected boolean but got int"));
        assertDoesNotThrow(() -> validator.run(new GpuIrPassContext(valid, List.of(helper), List.of(), true)));
    }

    private GpuIrPassContext context(GpuIrCompiledMethod method) {
        return new GpuIrPassContext(method, List.of(), List.of(), true);
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod) {
        return method(irMethod, "jtg_kernel");
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod, String emittedName) {
        return method(irMethod, emittedName, List.of());
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod, String emittedName, List<String> helperDependencies) {
        return method(irMethod, emittedName, helperDependencies, "void");
    }

    private GpuIrCompiledMethod method(
            GpuIrMethod irMethod,
            String emittedName,
            List<String> helperDependencies,
            String returnType
    ) {
        return method(irMethod, emittedName, helperDependencies, returnType, defaultParameters());
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod, List<ParsedGpuParameter> parameters) {
        return method(irMethod, "jtg_kernel", List.of(), "void", parameters);
    }

    private GpuIrCompiledMethod method(
            GpuIrMethod irMethod,
            String emittedName,
            List<String> helperDependencies,
            String returnType,
            List<ParsedGpuParameter> parameters
    ) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                returnType,
                parameters,
                List.of(),
                List.of(),
                null,
                false,
                List.of(),
                null,
                "",
                null,
                false
        );
        return new GpuIrCompiledMethod(parsedMethod, irMethod, emittedName, helperDependencies);
    }

    private List<ParsedGpuParameter> defaultParameters() {
        return List.of(
                new ParsedGpuParameter("input", "float[]", GpuAddressSpace.GLOBAL, false, List.of()),
                new ParsedGpuParameter("output", "float[]", GpuAddressSpace.GLOBAL, false, List.of())
        );
    }
}
