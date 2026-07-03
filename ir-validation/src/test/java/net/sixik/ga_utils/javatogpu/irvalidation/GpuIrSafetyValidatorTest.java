package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
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
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrPrivateArrayDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

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
                "float"
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
                "float"
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
                "float"
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
                "void"
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
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                returnType,
                List.of(
                        new ParsedGpuParameter("input", "float[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("output", "float[]", GpuAddressSpace.GLOBAL, false, List.of())
                ),
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
}
