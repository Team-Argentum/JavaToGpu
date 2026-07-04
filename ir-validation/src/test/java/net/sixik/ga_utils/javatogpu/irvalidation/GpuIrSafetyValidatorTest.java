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
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrPrivateArrayDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrSafetyValidatorTest {
    private final GpuIrSafetyValidator validator = new GpuIrSafetyValidator();

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
    void rejectsUnknownHelperDependencyMetadata() {
        GpuIrCompiledMethod method = method(
                new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))),
                "jtg_kernel",
                List.of("jtg_missing_helper")
        );

        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(), List.of(), true))
        );

        assertTrue(exception.getMessage().contains("unknown helper call target: jtg_missing_helper"));
    }

    @Test
    void rejectsMissingHelperDependencyMetadataList() {
        GpuIrCompiledMethod method = method(
                new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))),
                "jtg_kernel",
                null
        );

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("missing helper dependency metadata list"));
    }

    @Test
    void rejectsMalformedHelperDependencyMetadataEntries() {
        GpuIrCompiledMethod blankDependency = method(
                new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))),
                "jtg_kernel",
                List.of(" ")
        );
        GpuIrCompiledMethod nullDependency = method(
                new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))),
                "jtg_kernel",
                Arrays.asList((String) null)
        );

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(blankDependency)))
                .getMessage().contains("blank helper dependency name"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(nullDependency)))
                .getMessage().contains("blank helper dependency name"));
    }

    @Test
    void rejectsMissingEntryPointParameterMetadataList() {
        GpuIrCompiledMethod method = method(
                new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))),
                "jtg_kernel",
                List.of(),
                "void",
                null
        );

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("missing parameter metadata list"));
    }

    @Test
    void rejectsNullEntryPointParameterMetadata() {
        GpuIrCompiledMethod method = method(
                new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))),
                Arrays.asList((ParsedGpuParameter) null)
        );

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("null parameter metadata"));
    }

    @Test
    void rejectsMissingCompiledMethodMetadata() {
        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(null, List.of(), List.of(), true))
        );

        assertTrue(exception.getMessage().contains("missing compiled method metadata"));
    }

    @Test
    void rejectsMissingPassContext() {
        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> validator.run(null)
        );

        assertTrue(exception.getMessage().contains("missing pass context"));
    }

    @Test
    void rejectsMissingCompiledMethodEnvelopeMetadata() {
        GpuIrCompiledMethod missingIrMethod = new GpuIrCompiledMethod(
                parsedMethod("kernel", "void", defaultParameters()),
                null,
                "jtg_kernel",
                List.of()
        );
        GpuIrCompiledMethod missingParsedMethod = new GpuIrCompiledMethod(
                null,
                new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))),
                "jtg_kernel",
                List.of()
        );

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingIrMethod)))
                .getMessage().contains("missing IR method metadata for compiled method"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingParsedMethod)))
                .getMessage().contains("missing parsed method metadata for compiled method"));
    }

    @Test
    void rejectsMissingHelperCompiledMethodEnvelopeMetadata() {
        GpuIrCompiledMethod missingHelperIrMethod = new GpuIrCompiledMethod(
                parsedMethod("helper", "void", List.of()),
                null,
                "jtg_missing_ir_helper",
                List.of()
        );
        GpuIrCompiledMethod missingHelperParsedMethod = new GpuIrCompiledMethod(
                null,
                new GpuIrMethod("helper", List.of(new GpuIrReturn(null))),
                "jtg_missing_parsed_helper",
                List.of()
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(missingHelperIrMethod), List.of(), true))
        ).getMessage().contains("missing IR method metadata for helper jtg_missing_ir_helper"));
        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(missingHelperParsedMethod), List.of(), true))
        ).getMessage().contains("missing parsed method metadata for helper jtg_missing_parsed_helper"));
    }

    @Test
    void rejectsDuplicateHelperEmittedNames() {
        GpuIrCompiledMethod firstHelper = method(
                new GpuIrMethod("firstHelper", List.of(new GpuIrReturn(new GpuIrLiteral("1.0f")))),
                "jtg_helper",
                List.of(),
                "float",
                List.of()
        );
        GpuIrCompiledMethod secondHelper = method(
                new GpuIrMethod("secondHelper", List.of(new GpuIrReturn(new GpuIrLiteral("2.0f")))),
                "jtg_helper",
                List.of(),
                "float",
                List.of()
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));

        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(firstHelper, secondHelper), List.of(), true))
        );

        assertTrue(exception.getMessage().contains("duplicate helper emitted method name: jtg_helper"));
    }

    @Test
    void rejectsUnsupportedHelperSignatureMetadata() {
        GpuIrCompiledMethod badReturnHelper = method(
                new GpuIrMethod("badReturnHelper", List.of(new GpuIrReturn(new GpuIrLiteral("1")))),
                "jtg_bad_return_helper",
                List.of(),
                "Object",
                List.of()
        );
        GpuIrCompiledMethod badParameterHelper = method(
                new GpuIrMethod("badParameterHelper", List.of(new GpuIrReturn(null))),
                "jtg_bad_parameter_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("bad", "Object", GpuAddressSpace.PRIVATE, false, List.of()))
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(badReturnHelper), List.of(), true))
        ).getMessage().contains("unsupported helper return type for jtg_bad_return_helper: Object"));
        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(badParameterHelper), List.of(), true))
        ).getMessage().contains("unsupported helper parameter type for jtg_bad_parameter_helper: bad is Object"));
    }

    @Test
    void rejectsBlankHelperSignatureMetadata() {
        GpuIrCompiledMethod blankReturnHelper = method(
                new GpuIrMethod("blankReturnHelper", List.of(new GpuIrReturn(null))),
                "jtg_blank_return_helper",
                List.of(),
                " ",
                List.of()
        );
        GpuIrCompiledMethod blankParameterHelper = method(
                new GpuIrMethod("blankParameterHelper", List.of(new GpuIrReturn(null))),
                "jtg_blank_parameter_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("blank", " ", GpuAddressSpace.PRIVATE, false, List.of()))
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(blankReturnHelper), List.of(), true))
        ).getMessage().contains("blank helper return type for jtg_blank_return_helper"));
        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(blankParameterHelper), List.of(), true))
        ).getMessage().contains("blank helper parameter type for jtg_blank_parameter_helper: blank"));
    }

    @Test
    void rejectsMalformedHelperParameterNames() {
        GpuIrCompiledMethod blankParameterNameHelper = method(
                new GpuIrMethod("blankParameterNameHelper", List.of(new GpuIrReturn(null))),
                "jtg_blank_parameter_name_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter(" ", "float", GpuAddressSpace.PRIVATE, false, List.of()))
        );
        GpuIrCompiledMethod duplicateParameterNameHelper = method(
                new GpuIrMethod("duplicateParameterNameHelper", List.of(new GpuIrReturn(null))),
                "jtg_duplicate_parameter_name_helper",
                List.of(),
                "void",
                List.of(
                        new ParsedGpuParameter("value", "float", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("value", "int", GpuAddressSpace.PRIVATE, false, List.of())
                )
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(blankParameterNameHelper), List.of(), true))
        ).getMessage().contains("blank helper parameter name for jtg_blank_parameter_name_helper"));
        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(duplicateParameterNameHelper), List.of(), true))
        ).getMessage().contains("duplicate helper parameter name for jtg_duplicate_parameter_name_helper: value"));
    }

    @Test
    void rejectsNullHelperParameterMetadata() {
        GpuIrCompiledMethod nullParameterHelper = method(
                new GpuIrMethod("nullParameterHelper", List.of(new GpuIrReturn(null))),
                "jtg_null_parameter_helper",
                List.of(),
                "void",
                Arrays.asList((ParsedGpuParameter) null)
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(nullParameterHelper), List.of(), true))
        ).getMessage().contains("null helper parameter metadata for jtg_null_parameter_helper"));
    }

    @Test
    void rejectsMissingHelperParameterMetadataList() {
        GpuIrCompiledMethod missingParameterListHelper = method(
                new GpuIrMethod("missingParameterListHelper", List.of(new GpuIrReturn(null))),
                "jtg_missing_parameter_list_helper",
                List.of(),
                "void",
                null
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(missingParameterListHelper), List.of(), true))
        ).getMessage().contains("missing helper parameter metadata list for jtg_missing_parameter_list_helper"));
    }

    @Test
    void rejectsMissingHelperParameterAddressSpaceMetadata() {
        GpuIrCompiledMethod missingAddressSpaceHelper = method(
                new GpuIrMethod("missingAddressSpaceHelper", List.of(new GpuIrReturn(null))),
                "jtg_missing_address_space_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("value", "float", null, false, List.of()))
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(missingAddressSpaceHelper), List.of(), true))
        ).getMessage().contains("missing helper parameter address space for jtg_missing_address_space_helper: value"));
    }

    @Test
    void rejectsMalformedHelperParameterQualifierMetadata() {
        GpuIrCompiledMethod scalarQualifierHelper = method(
                new GpuIrMethod("scalarQualifierHelper", List.of(new GpuIrReturn(null))),
                "jtg_scalar_qualifier_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("value", "float", GpuAddressSpace.PRIVATE, false, List.of("const")))
        );
        GpuIrCompiledMethod duplicateQualifierHelper = method(
                new GpuIrMethod("duplicateQualifierHelper", List.of(new GpuIrReturn(null))),
                "jtg_duplicate_qualifier_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("values", "float[]", GpuAddressSpace.GLOBAL, false, List.of("const", "const")))
        );
        GpuIrCompiledMethod unsupportedQualifierHelper = method(
                new GpuIrMethod("unsupportedQualifierHelper", List.of(new GpuIrReturn(null))),
                "jtg_unsupported_qualifier_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("values", "float[]", GpuAddressSpace.GLOBAL, false, List.of("readonly")))
        );
        GpuIrCompiledMethod blankQualifierHelper = method(
                new GpuIrMethod("blankQualifierHelper", List.of(new GpuIrReturn(null))),
                "jtg_blank_qualifier_helper",
                List.of(),
                "void",
                List.of(new ParsedGpuParameter("values", "float[]", GpuAddressSpace.GLOBAL, false, List.of(" ")))
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(scalarQualifierHelper), List.of(), true))
        ).getMessage().contains("OpenCL qualifiers require a pointer-like helper parameter for jtg_scalar_qualifier_helper: value is float"));
        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(duplicateQualifierHelper), List.of(), true))
        ).getMessage().contains("duplicate OpenCL helper parameter qualifier for jtg_duplicate_qualifier_helper: values is const"));
        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(unsupportedQualifierHelper), List.of(), true))
        ).getMessage().contains("unsupported OpenCL helper parameter qualifier for jtg_unsupported_qualifier_helper: values is readonly"));
        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(blankQualifierHelper), List.of(), true))
        ).getMessage().contains("blank OpenCL helper parameter qualifier for jtg_blank_qualifier_helper: values"));
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
    void rejectsIntrinsicTemplatesThatReferenceNegativeArgumentIndexes() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrIntrinsicCall(null, "OpenCL", "native_sin({-1})", "float", List.of(new GpuIrLiteral("1.0f")))
                )
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("intrinsic template references negative argument index {-1}: OpenCL"));
    }

    @Test
    void rejectsMalformedIntrinsicTemplatePlaceholders() {
        GpuIrCompiledMethod unclosedPlaceholder = method(new GpuIrMethod("unclosedPlaceholder", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrIntrinsicCall(null, "OpenCL", "native_sin({0)", "float", List.of(new GpuIrLiteral("1.0f")))
                )
        )));
        GpuIrCompiledMethod unsupportedPlaceholder = method(new GpuIrMethod("unsupportedPlaceholder", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrIntrinsicCall(null, "OpenCL", "native_sin({value})", "float", List.of(new GpuIrLiteral("1.0f")))
                )
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unclosedPlaceholder)))
                .getMessage().contains("intrinsic template contains an unclosed placeholder: native_sin({0)"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedPlaceholder)))
                .getMessage().contains("intrinsic template contains unsupported placeholder {value}: OpenCL"));
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
    void rejectsUnsupportedLocalAndPrivateArrayElementTypes() {
        GpuIrCompiledMethod unsupportedLocalType = method(new GpuIrMethod("unsupportedLocalType", List.of(
                new GpuIrVariableDeclaration("Object", "value", new GpuIrLiteral("1"))
        )));
        GpuIrCompiledMethod unsupportedPrivateArrayElementType = method(new GpuIrMethod("unsupportedPrivateArrayElementType", List.of(
                new GpuIrPrivateArrayDeclaration("Object", "scratch", new GpuIrLiteral("4"))
        )));
        GpuIrCompiledMethod supportedTypes = method(new GpuIrMethod("supportedTypes", List.of(
                new GpuIrVariableDeclaration("Float2", "vector", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))),
                new GpuIrPrivateArrayDeclaration("float", "scratch", new GpuIrLiteral("4"))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedLocalType)))
                .getMessage().contains("unsupported local declaration type for value: Object"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedPrivateArrayElementType)))
                .getMessage().contains("unsupported private array element type for scratch: Object"));
        assertDoesNotThrow(() -> validator.run(context(supportedTypes)));
    }

    @Test
    void rejectsUnsupportedOperatorTokensWhenOperandTypesAreKnown() {
        GpuIrCompiledMethod unsupportedBinaryOperator = method(new GpuIrMethod("unsupportedBinaryOperator", List.of(
                new GpuIrVariableDeclaration("int", "left", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "right", new GpuIrLiteral("2")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrBinary("**", new GpuIrVariableRef("left"), new GpuIrVariableRef("right")))
        )));
        GpuIrCompiledMethod unsupportedUnaryOperator = method(new GpuIrMethod("unsupportedUnaryOperator", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "out", new GpuIrUnary("abs", new GpuIrVariableRef("value")))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedBinaryOperator)))
                .getMessage().contains("unsupported binary operator: **"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedUnaryOperator)))
                .getMessage().contains("unsupported unary operator: abs"));
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
    void rejectsUnsupportedMethodReturnTypes() {
        GpuIrCompiledMethod unsupportedReturnType = method(
                new GpuIrMethod("unsupportedReturnType", List.of(new GpuIrReturn(new GpuIrLiteral("1")))),
                "jtg_unsupported_return",
                List.of(),
                "Object"
        );
        GpuIrCompiledMethod supportedVectorReturnType = method(
                new GpuIrMethod("supportedVectorReturnType", List.of(new GpuIrReturn(new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))))),
                "jtg_supported_vector_return",
                List.of(),
                "Float2"
        );

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedReturnType)))
                .getMessage().contains("unsupported method return type: Object"));
        assertDoesNotThrow(() -> validator.run(context(supportedVectorReturnType)));
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
    void rejectsMissingPrivateArraySizes() {
        GpuIrCompiledMethod missingSize = method(new GpuIrMethod("missingPrivateArraySize", List.of(
                new GpuIrPrivateArrayDeclaration("float", "scratch", null)
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingSize)))
                .getMessage().contains("missing private array size"));
    }

    @Test
    void rejectsNonIntegralPrivateArraySizesWhenTypeIsKnown() {
        GpuIrCompiledMethod floatSizedArray = method(new GpuIrMethod("floatSizedArray", List.of(
                new GpuIrVariableDeclaration("float", "size", new GpuIrLiteral("4.0f")),
                new GpuIrPrivateArrayDeclaration("float", "scratch", new GpuIrVariableRef("size"))
        )));
        GpuIrCompiledMethod integralSizedArray = method(new GpuIrMethod("integralSizedArray", List.of(
                new GpuIrVariableDeclaration("int", "size", new GpuIrLiteral("4")),
                new GpuIrPrivateArrayDeclaration("float", "scratch", new GpuIrVariableRef("size"))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(floatSizedArray)))
                .getMessage().contains("private array size must be an integral scalar type but got float"));
        assertDoesNotThrow(() -> validator.run(context(integralSizedArray)));
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
    void rejectsHelperCallsWithWrongResultMetadata() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("helper", List.of(new GpuIrReturn(new GpuIrLiteral("1")))),
                "jtg_helper",
                List.of(),
                "int",
                List.of()
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrHelperCall("jtg_helper", "float", List.of()))
        )), "jtg_kernel", List.of("jtg_helper"));

        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(method, List.of(helper), List.of(), true))
        );

        assertTrue(exception.getMessage().contains("helper call result type mismatch for jtg_helper: expected int but got float"));
    }

    @Test
    void acceptsHelperCallsWithEquivalentVectorResultMetadataAliases() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("helper", List.of(new GpuIrReturn(new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))))),
                "jtg_vector_helper",
                List.of(),
                "net.sixik.ga_utils.javatogpu.api.Float2",
                List.of()
        );
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("Float2", "value", new GpuIrHelperCall("jtg_vector_helper", "Float2", List.of()))
        )), "jtg_kernel", List.of("jtg_vector_helper"));

        assertDoesNotThrow(() -> validator.run(new GpuIrPassContext(method, List.of(helper), List.of(), true)));
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
    void rejectsNonBooleanControlFlowAndTernaryConditions() {
        GpuIrCompiledMethod ifConditionMismatch = method(new GpuIrMethod("ifConditionMismatch", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrIf(new GpuIrVariableRef("value"), List.of(), List.of())
        )));
        GpuIrCompiledMethod forConditionMismatch = method(new GpuIrMethod("forConditionMismatch", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrForLoop(null, new GpuIrVariableRef("value"), null, List.of())
        )));
        GpuIrCompiledMethod whileConditionMismatch = method(new GpuIrMethod("whileConditionMismatch", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrWhileLoop(new GpuIrVariableRef("value"), List.of())
        )));
        GpuIrCompiledMethod doWhileConditionMismatch = method(new GpuIrMethod("doWhileConditionMismatch", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrDoWhileLoop(List.of(), new GpuIrVariableRef("value"))
        )));
        GpuIrCompiledMethod ternaryConditionMismatch = method(new GpuIrMethod("ternaryConditionMismatch", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "out", new GpuIrTernary(
                        new GpuIrVariableRef("value"),
                        new GpuIrLiteral("2"),
                        new GpuIrLiteral("3")
                ))
        )));
        GpuIrCompiledMethod booleanConditions = method(new GpuIrMethod("booleanConditions", List.of(
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("true")),
                new GpuIrIf(new GpuIrVariableRef("flag"), List.of(), List.of()),
                new GpuIrVariableDeclaration("int", "out", new GpuIrTernary(
                        new GpuIrVariableRef("flag"),
                        new GpuIrLiteral("2"),
                        new GpuIrLiteral("3")
                ))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(ifConditionMismatch)))
                .getMessage().contains("condition type mismatch in if condition: expected boolean but got int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(forConditionMismatch)))
                .getMessage().contains("condition type mismatch in for loop condition: expected boolean but got int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(whileConditionMismatch)))
                .getMessage().contains("condition type mismatch in while loop condition: expected boolean but got int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(doWhileConditionMismatch)))
                .getMessage().contains("condition type mismatch in do-while loop condition: expected boolean but got int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(ternaryConditionMismatch)))
                .getMessage().contains("condition type mismatch in ternary condition: expected boolean but got int"));
        assertDoesNotThrow(() -> validator.run(context(booleanConditions)));
    }

    @Test
    void rejectsMissingSwitchStructure() {
        GpuIrCompiledMethod missingSelector = method(new GpuIrMethod("missingSelector", List.of(
                new GpuIrSwitch(null, List.of(new GpuIrSwitchCase(List.of(), List.of(), true)))
        )));
        GpuIrCompiledMethod missingCases = method(new GpuIrMethod("missingCases", List.of(
                new GpuIrSwitch(new GpuIrLiteral("1"), null)
        )));
        GpuIrCompiledMethod nullCase = method(new GpuIrMethod("nullCase", List.of(
                new GpuIrSwitch(new GpuIrLiteral("1"), Arrays.asList((GpuIrSwitchCase) null))
        )));
        GpuIrCompiledMethod missingLabel = method(new GpuIrMethod("missingLabel", List.of(
                new GpuIrSwitch(new GpuIrLiteral("1"), List.of(
                        new GpuIrSwitchCase(Arrays.asList((GpuIrExpression) null), List.of(), false)
                ))
        )));
        GpuIrCompiledMethod duplicateDefaults = method(new GpuIrMethod("duplicateDefaults", List.of(
                new GpuIrSwitch(new GpuIrLiteral("1"), List.of(
                        new GpuIrSwitchCase(List.of(), List.of(), true),
                        new GpuIrSwitchCase(List.of(), List.of(), true)
                ))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingSelector)))
                .getMessage().contains("missing switch selector"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingCases)))
                .getMessage().contains("missing switch cases"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(nullCase)))
                .getMessage().contains("null switch case"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingLabel)))
                .getMessage().contains("missing switch case label"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(duplicateDefaults)))
                .getMessage().contains("switch contains more than one default case"));
    }

    @Test
    void rejectsNonIntegralSwitchSelectorsAndLabelsWhenTypeIsKnown() {
        GpuIrCompiledMethod nonIntegralSelector = method(new GpuIrMethod("nonIntegralSelector", List.of(
                new GpuIrVariableDeclaration("float", "selector", new GpuIrLiteral("1.0f")),
                new GpuIrSwitch(new GpuIrVariableRef("selector"), List.of(
                        new GpuIrSwitchCase(List.of(new GpuIrCast("int", new GpuIrLiteral("1"))), List.of(), false)
                ))
        )));
        GpuIrCompiledMethod nonIntegralLabel = method(new GpuIrMethod("nonIntegralLabel", List.of(
                new GpuIrVariableDeclaration("int", "selector", new GpuIrLiteral("1")),
                new GpuIrSwitch(new GpuIrVariableRef("selector"), List.of(
                        new GpuIrSwitchCase(List.of(new GpuIrCast("float", new GpuIrLiteral("1"))), List.of(), false)
                ))
        )));
        GpuIrCompiledMethod compatibleIntegralLabels = method(new GpuIrMethod("compatibleIntegralLabels", List.of(
                new GpuIrVariableDeclaration("int", "selector", new GpuIrLiteral("1")),
                new GpuIrSwitch(new GpuIrVariableRef("selector"), List.of(
                        new GpuIrSwitchCase(List.of(new GpuIrCast("int", new GpuIrLiteral("1"))), List.of(), false),
                        new GpuIrSwitchCase(List.of(new GpuIrCast("short", new GpuIrLiteral("2"))), List.of(), false),
                        new GpuIrSwitchCase(List.of(), List.of(), true)
                ))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(nonIntegralSelector)))
                .getMessage().contains("switch selector must be an integral scalar type but got float"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(nonIntegralLabel)))
                .getMessage().contains("switch case label must be an integral scalar type but got float"));
        assertDoesNotThrow(() -> validator.run(context(compatibleIntegralLabels)));
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
    void rejectsNonIntegralArrayIndexesWhenTypeIsKnown() {
        GpuIrCompiledMethod readWithFloatIndex = method(new GpuIrMethod("readWithFloatIndex", List.of(
                new GpuIrVariableDeclaration("float", "idx", new GpuIrLiteral("1.0f")),
                new GpuIrVariableDeclaration("float", "value", new GpuIrArrayAccess("input", new GpuIrVariableRef("idx")))
        )));
        GpuIrCompiledMethod writeWithBooleanIndex = method(new GpuIrMethod("writeWithBooleanIndex", List.of(
                new GpuIrVariableDeclaration("boolean", "idx", new GpuIrLiteral("true")),
                new GpuIrAssignment(new GpuIrArrayAccess("output", new GpuIrVariableRef("idx")), new GpuIrLiteral("1.0f"))
        )));
        GpuIrCompiledMethod readAndWriteWithIntegralIndexes = method(new GpuIrMethod("readAndWriteWithIntegralIndexes", List.of(
                new GpuIrVariableDeclaration("int", "readIndex", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("long", "writeIndex", new GpuIrLiteral("2L")),
                new GpuIrAssignment(
                        new GpuIrArrayAccess("output", new GpuIrVariableRef("writeIndex")),
                        new GpuIrArrayAccess("input", new GpuIrVariableRef("readIndex"))
                )
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(readWithFloatIndex)))
                .getMessage().contains("array access index for input must be an integral scalar type but got float"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(writeWithBooleanIndex)))
                .getMessage().contains("array access index for output must be an integral scalar type but got boolean"));
        assertDoesNotThrow(() -> validator.run(context(readAndWriteWithIntegralIndexes)));
    }

    @Test
    void rejectsArrayAccessOnNonArrayTargetsWhenTypeIsKnown() {
        GpuIrCompiledMethod readFromScalar = method(new GpuIrMethod("readFromScalar", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "out", new GpuIrArrayAccess("value", new GpuIrLiteral("0")))
        )));
        GpuIrCompiledMethod writeToScalar = method(new GpuIrMethod("writeToScalar", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrAssignment(new GpuIrArrayAccess("value", new GpuIrLiteral("0")), new GpuIrLiteral("2"))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(readFromScalar)))
                .getMessage().contains("array access target must be an array type: value is int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(writeToScalar)))
                .getMessage().contains("array access target must be an array type: value is int"));
    }

    @Test
    void validatesKnownVectorFieldAccessWhenTypeIsKnown() {
        GpuIrCompiledMethod unknownVectorField = method(new GpuIrMethod("unknownVectorField", List.of(
                new GpuIrVariableDeclaration("Float2", "vector", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))),
                new GpuIrVariableDeclaration("float", "value", new GpuIrFieldAccess(new GpuIrVariableRef("vector"), "z"))
        )));
        GpuIrCompiledMethod fieldTypeMismatch = method(new GpuIrMethod("fieldTypeMismatch", List.of(
                new GpuIrVariableDeclaration("Float2", "vector", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))),
                new GpuIrVariableDeclaration("int", "value", new GpuIrFieldAccess(new GpuIrVariableRef("vector"), "x"))
        )));
        GpuIrCompiledMethod knownVectorField = method(new GpuIrMethod("knownVectorField", List.of(
                new GpuIrVariableDeclaration("Float2", "vector", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))),
                new GpuIrVariableDeclaration("float", "value", new GpuIrFieldAccess(new GpuIrVariableRef("vector"), "x"))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unknownVectorField)))
                .getMessage().contains("unknown vector field z for type Float2"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(fieldTypeMismatch)))
                .getMessage().contains("type mismatch in local initializer for value: expected int but got float"));
        assertDoesNotThrow(() -> validator.run(context(knownVectorField)));
    }

    @Test
    void validatesKnownVectorFieldAssignmentTargetsWhenTypeIsKnown() {
        GpuIrCompiledMethod blankFieldName = method(new GpuIrMethod("blankVectorFieldAssignment", List.of(
                new GpuIrVariableDeclaration("Float2", "vector", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))),
                new GpuIrAssignment(new GpuIrFieldAccess(new GpuIrVariableRef("vector"), ""), new GpuIrLiteral("1.0f"))
        )));
        GpuIrCompiledMethod unknownVectorField = method(new GpuIrMethod("unknownVectorFieldAssignment", List.of(
                new GpuIrVariableDeclaration("Float2", "vector", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))),
                new GpuIrAssignment(new GpuIrFieldAccess(new GpuIrVariableRef("vector"), "z"), new GpuIrLiteral("1.0f"))
        )));
        GpuIrCompiledMethod knownVectorField = method(new GpuIrMethod("knownVectorFieldAssignment", List.of(
                new GpuIrVariableDeclaration("Float2", "vector", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))),
                new GpuIrAssignment(new GpuIrFieldAccess(new GpuIrVariableRef("vector"), "x"), new GpuIrLiteral("1.0f"))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(blankFieldName)))
                .getMessage().contains("blank field access name"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unknownVectorField)))
                .getMessage().contains("unknown vector field z for type Float2"));
        assertDoesNotThrow(() -> validator.run(context(knownVectorField)));
    }

    @Test
    void validatesKnownVectorInitializersWhenTypeIsKnown() {
        GpuIrCompiledMethod countMismatch = method(new GpuIrMethod("vectorCountMismatch", List.of(
                new GpuIrVariableDeclaration("Float2", "value", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f"),
                        new GpuIrLiteral("3.0f")
                )))
        )));
        GpuIrCompiledMethod scalarTypeMismatch = method(new GpuIrMethod("vectorScalarTypeMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("Float2", "value", new GpuIrStructInit("Float2", List.of(new GpuIrVariableRef("flag"))))
        )));
        GpuIrCompiledMethod vectorTypeMismatch = method(new GpuIrMethod("vectorTypeMismatch", List.of(
                new GpuIrVariableDeclaration("Int2", "source", new GpuIrStructInit("Int2", List.of(
                        new GpuIrLiteral("1"),
                        new GpuIrLiteral("2")
                ))),
                new GpuIrVariableDeclaration("Float2", "value", new GpuIrStructInit("Float2", List.of(new GpuIrVariableRef("source"))))
        )));
        GpuIrCompiledMethod validVectorInitializers = method(new GpuIrMethod("validVectorInitializers", List.of(
                new GpuIrVariableDeclaration("int", "source", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("Float2", "zero", new GpuIrStructInit("Float2", List.of())),
                new GpuIrVariableDeclaration("Float2", "splat", new GpuIrStructInit("Float2", List.of(new GpuIrVariableRef("source")))),
                new GpuIrVariableDeclaration("Float2", "explicit", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                )))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(countMismatch)))
                .getMessage().contains("vector initializer argument count mismatch for Float2: expected 0, 1 or 2 but got 3"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(scalarTypeMismatch)))
                .getMessage().contains("type mismatch in vector initializer argument for Float2: expected float but got boolean"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(vectorTypeMismatch)))
                .getMessage().contains("vector initializer argument type mismatch for Float2: expected Float2 but got Int2"));
        assertDoesNotThrow(() -> validator.run(context(validVectorInitializers)));
    }

    @Test
    void validatesWideVectorInitializersAndFieldsWhenTypeIsKnown() {
        GpuIrCompiledMethod countMismatch = method(new GpuIrMethod("wideVectorCountMismatch", List.of(
                new GpuIrVariableDeclaration("Int16", "value", new GpuIrStructInit("Int16", List.of(
                        new GpuIrLiteral("0"),
                        new GpuIrLiteral("1"),
                        new GpuIrLiteral("2"),
                        new GpuIrLiteral("3"),
                        new GpuIrLiteral("4"),
                        new GpuIrLiteral("5"),
                        new GpuIrLiteral("6"),
                        new GpuIrLiteral("7"),
                        new GpuIrLiteral("8"),
                        new GpuIrLiteral("9"),
                        new GpuIrLiteral("10"),
                        new GpuIrLiteral("11"),
                        new GpuIrLiteral("12"),
                        new GpuIrLiteral("13"),
                        new GpuIrLiteral("14")
                )))
        )));
        GpuIrCompiledMethod knownWideVectorField = method(new GpuIrMethod("knownWideVectorField", List.of(
                new GpuIrVariableDeclaration("Int16", "vector", new GpuIrStructInit("Int16", List.of(
                        new GpuIrLiteral("0"),
                        new GpuIrLiteral("1"),
                        new GpuIrLiteral("2"),
                        new GpuIrLiteral("3"),
                        new GpuIrLiteral("4"),
                        new GpuIrLiteral("5"),
                        new GpuIrLiteral("6"),
                        new GpuIrLiteral("7"),
                        new GpuIrLiteral("8"),
                        new GpuIrLiteral("9"),
                        new GpuIrLiteral("10"),
                        new GpuIrLiteral("11"),
                        new GpuIrLiteral("12"),
                        new GpuIrLiteral("13"),
                        new GpuIrLiteral("14"),
                        new GpuIrLiteral("15")
                ))),
                new GpuIrVariableDeclaration("int", "last", new GpuIrFieldAccess(new GpuIrVariableRef("vector"), "sf"))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(countMismatch)))
                .getMessage().contains("vector initializer argument count mismatch for Int16: expected 0, 1 or 16 but got 15"));
        assertDoesNotThrow(() -> validator.run(context(knownWideVectorField)));
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
    void rejectsMissingCallAndInitializerArgumentLists() {
        GpuIrCompiledMethod helper = method(
                new GpuIrMethod("helper", List.of(new GpuIrReturn(new GpuIrLiteral("1.0f")))),
                "jtg_helper",
                List.of(),
                "float",
                List.of()
        );
        GpuIrCompiledMethod missingHelperArgumentList = method(new GpuIrMethod("missingHelperArgumentList", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrHelperCall("jtg_helper", "float", null)
                )
        )), "jtg_kernel", List.of("jtg_helper"));
        GpuIrCompiledMethod missingStructArgumentList = method(new GpuIrMethod("missingStructArgumentList", List.of(
                new GpuIrVariableDeclaration("float2", "value", new GpuIrStructInit("float2", null))
        )));

        assertTrue(assertThrows(
                GpuIrPassException.class,
                () -> validator.run(new GpuIrPassContext(missingHelperArgumentList, List.of(helper), List.of(), true))
        ).getMessage().contains("missing helper call argument list"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingStructArgumentList)))
                .getMessage().contains("missing struct initializer argument list"));
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
    void rejectsUnsupportedScalarCastShapesWhenTypeIsKnown() {
        GpuIrCompiledMethod unsupportedCastTarget = method(new GpuIrMethod("unsupportedCastTarget", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrCast("Float2", new GpuIrLiteral("1")))
        )));
        GpuIrCompiledMethod unsupportedCastSource = method(new GpuIrMethod("unsupportedCastSource", List.of(
                new GpuIrVariableDeclaration("Float2", "vector", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))),
                new GpuIrVariableDeclaration("float", "value", new GpuIrCast("float", new GpuIrVariableRef("vector")))
        )));
        GpuIrCompiledMethod supportedScalarCast = method(new GpuIrMethod("supportedScalarCast", List.of(
                new GpuIrVariableDeclaration("int", "source", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("double", "value", new GpuIrCast("double", new GpuIrVariableRef("source")))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedCastTarget)))
                .getMessage().contains("unsupported cast target type: Float2"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedCastSource)))
                .getMessage().contains("cast source must be a supported scalar type but got Float2"));
        assertDoesNotThrow(() -> validator.run(context(supportedScalarCast)));
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
    void rejectsInvalidTypedOperatorOperandsWhenTypeIsKnown() {
        GpuIrCompiledMethod logicalOperandMismatch = method(new GpuIrMethod("logicalOperandMismatch", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("boolean", "out", new GpuIrBinary("&&", new GpuIrVariableRef("value"), new GpuIrVariableRef("flag")))
        )));
        GpuIrCompiledMethod bitwiseOperandMismatch = method(new GpuIrMethod("bitwiseOperandMismatch", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrLiteral("1.0f")),
                new GpuIrVariableDeclaration("int", "mask", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "out", new GpuIrBinary("&", new GpuIrVariableRef("value"), new GpuIrVariableRef("mask")))
        )));
        GpuIrCompiledMethod numericOperandMismatch = method(new GpuIrMethod("numericOperandMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "out", new GpuIrBinary("+", new GpuIrVariableRef("flag"), new GpuIrVariableRef("value")))
        )));
        GpuIrCompiledMethod unaryOperandMismatch = method(new GpuIrMethod("unaryOperandMismatch", List.of(
                new GpuIrVariableDeclaration("float", "value", new GpuIrLiteral("1.0f")),
                new GpuIrVariableDeclaration("int", "out", new GpuIrUnary("~", new GpuIrVariableRef("value")))
        )));
        GpuIrCompiledMethod validTypedOperators = method(new GpuIrMethod("validTypedOperators", List.of(
                new GpuIrVariableDeclaration("boolean", "leftFlag", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("boolean", "rightFlag", new GpuIrLiteral("false")),
                new GpuIrVariableDeclaration("int", "left", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "right", new GpuIrLiteral("2")),
                new GpuIrVariableDeclaration("boolean", "logical", new GpuIrBinary("&&", new GpuIrVariableRef("leftFlag"), new GpuIrVariableRef("rightFlag"))),
                new GpuIrVariableDeclaration("int", "bitwise", new GpuIrBinary("&", new GpuIrVariableRef("left"), new GpuIrVariableRef("right"))),
                new GpuIrVariableDeclaration("int", "numeric", new GpuIrBinary("+", new GpuIrVariableRef("left"), new GpuIrVariableRef("right"))),
                new GpuIrVariableDeclaration("int", "unary", new GpuIrUnary("~", new GpuIrVariableRef("left")))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(logicalOperandMismatch)))
                .getMessage().contains("operator && requires boolean left operand but got int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(bitwiseOperandMismatch)))
                .getMessage().contains("operator & requires integral left operand but got float"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(numericOperandMismatch)))
                .getMessage().contains("operator + requires numeric left operand but got boolean"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unaryOperandMismatch)))
                .getMessage().contains("operator ~ requires integral operand but got float"));
        assertDoesNotThrow(() -> validator.run(context(validTypedOperators)));
    }

    @Test
    void rejectsInvalidTypedComparisonOperandsWhenTypeIsKnown() {
        GpuIrCompiledMethod relationalOperandMismatch = method(new GpuIrMethod("relationalOperandMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("boolean", "out", new GpuIrBinary("<", new GpuIrVariableRef("flag"), new GpuIrVariableRef("value")))
        )));
        GpuIrCompiledMethod equalityOperandMismatch = method(new GpuIrMethod("equalityOperandMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("boolean", "out", new GpuIrBinary("==", new GpuIrVariableRef("flag"), new GpuIrVariableRef("value")))
        )));
        GpuIrCompiledMethod validTypedComparisons = method(new GpuIrMethod("validTypedComparisons", List.of(
                new GpuIrVariableDeclaration("boolean", "leftFlag", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("boolean", "rightFlag", new GpuIrLiteral("false")),
                new GpuIrVariableDeclaration("int", "left", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("double", "right", new GpuIrLiteral("2.0")),
                new GpuIrVariableDeclaration("boolean", "relational", new GpuIrBinary("<", new GpuIrVariableRef("left"), new GpuIrVariableRef("right"))),
                new GpuIrVariableDeclaration("boolean", "numericEquality", new GpuIrBinary("!=", new GpuIrVariableRef("left"), new GpuIrVariableRef("right"))),
                new GpuIrVariableDeclaration("boolean", "booleanEquality", new GpuIrBinary("==", new GpuIrVariableRef("leftFlag"), new GpuIrVariableRef("rightFlag")))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(relationalOperandMismatch)))
                .getMessage().contains("operator < requires numeric left operand but got boolean"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(equalityOperandMismatch)))
                .getMessage().contains("operator == cannot compare boolean and numeric operands: boolean and int"));
        assertDoesNotThrow(() -> validator.run(context(validTypedComparisons)));
    }

    @Test
    void rejectsInvalidTypedTernaryBranchesWhenTypeIsKnown() {
        GpuIrCompiledMethod booleanNumericBranchMismatch = method(new GpuIrMethod("booleanNumericBranchMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "condition", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("boolean", "flag", new GpuIrLiteral("false")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "out", new GpuIrTernary(
                        new GpuIrVariableRef("condition"),
                        new GpuIrVariableRef("flag"),
                        new GpuIrVariableRef("value")
                ))
        )));
        GpuIrCompiledMethod vectorBranchMismatch = method(new GpuIrMethod("vectorBranchMismatch", List.of(
                new GpuIrVariableDeclaration("boolean", "condition", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("Float2", "left", new GpuIrStructInit("Float2", List.of(
                        new GpuIrLiteral("1.0f"),
                        new GpuIrLiteral("2.0f")
                ))),
                new GpuIrVariableDeclaration("Int2", "right", new GpuIrStructInit("Int2", List.of(
                        new GpuIrLiteral("1"),
                        new GpuIrLiteral("2")
                ))),
                new GpuIrVariableDeclaration("Float2", "out", new GpuIrTernary(
                        new GpuIrVariableRef("condition"),
                        new GpuIrVariableRef("left"),
                        new GpuIrVariableRef("right")
                ))
        )));
        GpuIrCompiledMethod validNumericBranchWidening = method(new GpuIrMethod("validNumericBranchWidening", List.of(
                new GpuIrVariableDeclaration("boolean", "condition", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration("int", "left", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("double", "right", new GpuIrLiteral("2.0")),
                new GpuIrVariableDeclaration("double", "out", new GpuIrTernary(
                        new GpuIrVariableRef("condition"),
                        new GpuIrVariableRef("left"),
                        new GpuIrVariableRef("right")
                ))
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(booleanNumericBranchMismatch)))
                .getMessage().contains("ternary branch type mismatch: cannot mix boolean and numeric branches: boolean and int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(vectorBranchMismatch)))
                .getMessage().contains("ternary branch type mismatch: true branch is Float2 but false branch is Int2"));
        assertDoesNotThrow(() -> validator.run(context(validNumericBranchWidening)));
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
    void rejectsUnsupportedAndMisaddressedParameterMetadata() {
        GpuIrCompiledMethod unsupportedParameterType = method(
                new GpuIrMethod("unsupportedParameterType", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("bad", "Object", GpuAddressSpace.PRIVATE, false, List.of()))
        );
        GpuIrCompiledMethod globalScalarParameter = method(
                new GpuIrMethod("globalScalarParameter", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("value", "float", GpuAddressSpace.GLOBAL, false, List.of()))
        );
        GpuIrCompiledMethod privateArrayParameter = method(
                new GpuIrMethod("privateArrayParameter", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("values", "float[]", GpuAddressSpace.PRIVATE, false, List.of()))
        );
        GpuIrCompiledMethod vectorArrayParameter = method(
                new GpuIrMethod("vectorArrayParameter", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("vectors", "Float2[]", GpuAddressSpace.GLOBAL, false, List.of()))
        );

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedParameterType)))
                .getMessage().contains("unsupported entry-point parameter type for bad: Object"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(globalScalarParameter)))
                .getMessage().contains("GLOBAL parameter must be an array type: value is float"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(privateArrayParameter)))
                .getMessage().contains("array parameter must use an explicit GPU address space: values is float[]"));
        assertDoesNotThrow(() -> validator.run(context(vectorArrayParameter)));
    }

    @Test
    void rejectsMalformedEntryPointParameterNames() {
        GpuIrCompiledMethod blankParameterName = method(
                new GpuIrMethod("blankParameterName", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter(" ", "float", GpuAddressSpace.PRIVATE, false, List.of()))
        );
        GpuIrCompiledMethod duplicateParameterName = method(
                new GpuIrMethod("duplicateParameterName", List.of(new GpuIrReturn(null))),
                List.of(
                        new ParsedGpuParameter("value", "float", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("value", "int", GpuAddressSpace.PRIVATE, false, List.of())
                )
        );

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(blankParameterName)))
                .getMessage().contains("blank parameter name"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(duplicateParameterName)))
                .getMessage().contains("duplicate parameter declaration: value"));
    }

    @Test
    void rejectsMissingEntryPointParameterAddressSpaceMetadata() {
        GpuIrCompiledMethod missingAddressSpace = method(
                new GpuIrMethod("missingAddressSpace", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("value", "float", null, false, List.of()))
        );

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(missingAddressSpace)))
                .getMessage().contains("missing entry-point parameter address space for value"));
    }

    @Test
    void rejectsMalformedEntryPointParameterQualifierMetadata() {
        GpuIrCompiledMethod scalarQualifier = method(
                new GpuIrMethod("scalarQualifier", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("value", "float", GpuAddressSpace.PRIVATE, false, List.of("const")))
        );
        GpuIrCompiledMethod duplicateQualifier = method(
                new GpuIrMethod("duplicateQualifier", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("values", "float[]", GpuAddressSpace.GLOBAL, false, List.of("restrict", "restrict")))
        );
        GpuIrCompiledMethod unsupportedQualifier = method(
                new GpuIrMethod("unsupportedQualifier", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("values", "float[]", GpuAddressSpace.GLOBAL, false, List.of("readonly")))
        );
        GpuIrCompiledMethod blankQualifier = method(
                new GpuIrMethod("blankQualifier", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("values", "float[]", GpuAddressSpace.GLOBAL, false, List.of(" ")))
        );
        GpuIrCompiledMethod validQualifiers = method(
                new GpuIrMethod("validQualifiers", List.of(new GpuIrReturn(null))),
                List.of(new ParsedGpuParameter("values", "float[]", GpuAddressSpace.GLOBAL, false, List.of("const", "restrict", "volatile")))
        );

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(scalarQualifier)))
                .getMessage().contains("OpenCL qualifiers require a pointer-like parameter: value is float"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(duplicateQualifier)))
                .getMessage().contains("duplicate OpenCL parameter qualifier for values: restrict"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedQualifier)))
                .getMessage().contains("unsupported OpenCL parameter qualifier for values: readonly"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(blankQualifier)))
                .getMessage().contains("blank OpenCL parameter qualifier for values"));
        assertDoesNotThrow(() -> validator.run(context(validQualifiers)));
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

    @Test
    void rejectsIntrinsicArgumentMetadataMismatches() {
        GpuIrCompiledMethod metadataCountMismatch = method(new GpuIrMethod("intrinsicCountMismatch", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrIntrinsicCall(
                                null,
                                "native_sin",
                                "native_sin({0})",
                                "float",
                                List.of(new GpuIrLiteral("1.0f")),
                                List.of("float", "float")
                        )
                )
        )));
        GpuIrCompiledMethod metadataTypeMismatch = method(new GpuIrMethod("intrinsicTypeMismatch", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration(
                        "boolean",
                        "flag",
                        new GpuIrIntrinsicCall(
                                null,
                                "is_valid",
                                "is_valid({0})",
                                "boolean",
                                List.of(new GpuIrVariableRef("value")),
                                List.of("boolean")
                        )
                )
        )));
        GpuIrCompiledMethod blankMetadataType = method(new GpuIrMethod("intrinsicBlankMetadataType", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrIntrinsicCall(
                                null,
                                "native_sin",
                                "native_sin({0})",
                                "float",
                                List.of(new GpuIrLiteral("1.0f")),
                                List.of("")
                        )
                )
        )));
        GpuIrCompiledMethod unsupportedMetadataType = method(new GpuIrMethod("intrinsicUnsupportedMetadataType", List.of(
                new GpuIrVariableDeclaration(
                        "float",
                        "value",
                        new GpuIrIntrinsicCall(
                                null,
                                "native_sin",
                                "native_sin({0})",
                                "float",
                                List.of(new GpuIrLiteral("1.0f")),
                                List.of("Object")
                        )
                )
        )));
        GpuIrCompiledMethod unsupportedResultMetadataType = method(new GpuIrMethod("intrinsicUnsupportedResultType", List.of(
                new GpuIrVariableDeclaration(
                        "Object",
                        "value",
                        new GpuIrIntrinsicCall(
                                null,
                                "native_object",
                                "native_object({0})",
                                "Object",
                                List.of(new GpuIrLiteral("1.0f")),
                                List.of("float")
                        )
                )
        )));
        GpuIrCompiledMethod metadataTypeMatch = method(new GpuIrMethod("intrinsicTypeMatch", List.of(
                new GpuIrVariableDeclaration("boolean", "inputFlag", new GpuIrLiteral("true")),
                new GpuIrVariableDeclaration(
                        "boolean",
                        "flag",
                        new GpuIrIntrinsicCall(
                                null,
                                "is_valid",
                                "is_valid({0})",
                                "boolean",
                                List.of(new GpuIrVariableRef("inputFlag")),
                                List.of("boolean")
                        )
                )
        )));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(metadataCountMismatch)))
                .getMessage().contains("intrinsic argument metadata count mismatch for native_sin: expected 2 but got 1"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(metadataTypeMismatch)))
                .getMessage().contains("type mismatch in intrinsic argument 0 for is_valid: expected boolean but got int"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(blankMetadataType)))
                .getMessage().contains("blank intrinsic argument 0 type for native_sin"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedMetadataType)))
                .getMessage().contains("unsupported intrinsic argument 0 metadata type for native_sin: Object"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(unsupportedResultMetadataType)))
                .getMessage().contains("unsupported intrinsic result metadata type for native_object: Object"));
        assertDoesNotThrow(() -> validator.run(context(metadataTypeMatch)));
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
        ParsedGpuMethod parsedMethod = parsedMethod(irMethod.name(), returnType, parameters);
        return new GpuIrCompiledMethod(parsedMethod, irMethod, emittedName, helperDependencies);
    }

    private ParsedGpuMethod parsedMethod(String methodName, String returnType, List<ParsedGpuParameter> parameters) {
        return new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                methodName,
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
    }

    private List<ParsedGpuParameter> defaultParameters() {
        return List.of(
                new ParsedGpuParameter("input", "float[]", GpuAddressSpace.GLOBAL, false, List.of()),
                new ParsedGpuParameter("output", "float[]", GpuAddressSpace.GLOBAL, false, List.of())
        );
    }
}
