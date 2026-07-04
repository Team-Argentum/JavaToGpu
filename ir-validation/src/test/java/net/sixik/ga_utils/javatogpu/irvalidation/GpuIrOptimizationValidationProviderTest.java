package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationDiagnosticPolicy;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationMode;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProvider;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationReportEntry;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRequest;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRunner;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationProviderTest {
    private final GpuIrOptimizationValidationProvider provider = new GpuIrOptimizationValidationProvider();

    @Test
    void moduleRegistersValidationProviderThroughServiceLoader() {
        List<GpuIrValidationProvider> providers = ServiceLoader.load(GpuIrValidationProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertTrue(providers.stream().anyMatch(GpuIrOptimizationValidationProvider.class::isInstance));
    }

    @Test
    void validationRunnerLoadsProviderThroughServiceLoader() {
        GpuIrValidationRunner runner = GpuIrValidationRunner.loadFromServiceLoader(GpuIrValidationMode.STRICT_SAFETY);

        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> runner.run(brokenMethod(), List.of(), List.of())
        );

        assertTrue(exception.getMessage().contains("unknown variable reference: missing"));
    }

    @Test
    void diagnosticModeDoesNotFailBuildForSafetyDiagnostics() {
        GpuIrValidationRequest request = request(GpuIrValidationMode.DIAGNOSTIC, brokenMethod());

        assertDoesNotThrow(() -> provider.validate(request));
    }

    @Test
    void diagnosticModeReportsCompactUnifiedSummary() {
        List<String> diagnostics = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                brokenMethod(),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                diagnostics::add
        );

        provider.validate(request);

        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("ir optimization validation method=broken")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("safety=failed")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("optimizerDiagnostics=0")));
        assertTrue(diagnostics.stream().noneMatch(message -> message.contains("unknown variable reference: missing")));
    }

    @Test
    void diagnosticModeReportsCompactRewritePlanGuardFamilies() {
        List<String> diagnostics = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(
                        new GpuIrIf(new GpuIrBinary("!=", new GpuIrVariableRef("flag"), new GpuIrLiteral("0")), List.of(), List.of()),
                        fixedWidthLoop(4, List.of(
                                laneAssignment("out", arrayRead("input", new GpuIrVariableRef("i")))
                        ))
                )), List.of(
                        parameter("flag", "int"),
                        parameter("out", "int[]"),
                        parameter("input", "int[]")
                )),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                diagnostics::add
        );

        provider.validate(request);

        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewritePlanGuards=1")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewritePlanGuardFamilies={controlFlowBoundary=1}")));
        assertTrue(diagnostics.stream().noneMatch(message -> message.contains("firstRewritePlanGuard")));
    }

    @Test
    void quietDiagnosticPolicySuppressesDiagnosticSummary() {
        List<String> diagnostics = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                brokenMethod(),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.QUIET,
                diagnostics::add
        );

        provider.validate(request);

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void detailedDiagnosticPolicyReportsNestedSummary() {
        List<String> diagnostics = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                brokenMethod(),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.DETAILED,
                diagnostics::add
        );

        provider.validate(request);

        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("unknown variable reference: missing")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("cse={")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorization={")));
    }

    @Test
    void reportEntryIncludesAutoVectorizationRejectionReasonCounts() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(fixedWidthLoop(5, List.of(
                        laneAssignment("out", arrayRead("input", new GpuIrVariableRef("i")))
                ))))),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.QUIET,
                ignored -> { },
                entries::add
        );

        provider.validate(request);

        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRejections")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT")));
    }

    @Test
    void reportEntryIncludesAutoVectorizationWarningFamilyCounts() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(fixedWidthLoop(4, List.of(
                        laneAssignment("out", arrayRead("out", new GpuIrVariableRef("i"))),
                        laneAssignment("out", arrayRead(
                                "input",
                                new net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary(
                                        "+",
                                        new GpuIrVariableRef("i"),
                                        new GpuIrLiteral("1")
                                )
                        )),
                        laneAssignment("scratch", arrayRead("input", new GpuIrVariableRef("j")))
                )))), List.of(
                        parameter("out", "int[]"),
                        parameter("input", "int[]"),
                        parameter("scratch", "int[]")
                )),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.QUIET,
                ignored -> { },
                entries::add
        );

        provider.validate(request);

        assertTrue("0".equals(entries.get(0).values().get("autoVectorizationCandidates")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationWarnings")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationWarningFamily.alias")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationWarningFamily.repeatedTarget")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationWarningFamily.crossLaneRead")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationWarningFamily.nonLaneRead")));
    }

    @Test
    void reportEntryIncludesAutoVectorizationRewritePlanCounters() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(fixedWidthLoop(4, List.of(
                        laneAssignment("out", arrayRead("input", new GpuIrVariableRef("i")))
                )))), List.of(
                        parameter("out", "int[]"),
                        parameter("input", "int[]")
                )),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.QUIET,
                ignored -> { },
                entries::add
        );

        provider.validate(request);

        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationCandidates")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRewritePlanCandidates")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRewritePlanInsertions")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRewritePlanReplacements")));
        assertTrue("2".equals(entries.get(0).values().get("autoVectorizationRewritePlanOperations")));
        assertTrue("0".equals(entries.get(0).values().get("autoVectorizationRewritePlanGuards")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationVectorType.int4")));
    }

    @Test
    void reportEntryIncludesAutoVectorizationRewritePlanGuardFamilies() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(
                        new GpuIrAssignment(arrayRead("input", new GpuIrLiteral("0")), new GpuIrLiteral("7")),
                        fixedWidthLoop(4, List.of(
                                laneAssignment("out", arrayRead("input", new GpuIrVariableRef("i")))
                        ))
                )), List.of(
                        parameter("out", "int[]"),
                        parameter("input", "int[]")
                )),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.QUIET,
                ignored -> { },
                entries::add
        );

        provider.validate(request);

        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationCandidates")));
        assertTrue("0".equals(entries.get(0).values().get("autoVectorizationRewritePlanOperations")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRewritePlanGuards")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRewritePlanGuardFamily.neighborSourceWrite")));
    }

    @Test
    void reportEntryIncludesAutoVectorizationControlFlowGuardFamily() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(
                        new GpuIrIf(new GpuIrBinary("!=", new GpuIrVariableRef("flag"), new GpuIrLiteral("0")), List.of(), List.of()),
                        fixedWidthLoop(4, List.of(
                                laneAssignment("out", arrayRead("input", new GpuIrVariableRef("i")))
                        ))
                )), List.of(
                        parameter("flag", "int"),
                        parameter("out", "int[]"),
                        parameter("input", "int[]")
                )),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.QUIET,
                ignored -> { },
                entries::add
        );

        provider.validate(request);

        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationCandidates")));
        assertTrue("0".equals(entries.get(0).values().get("autoVectorizationRewritePlanOperations")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRewritePlanGuards")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRewritePlanGuardFamily.controlFlowBoundary")));
    }

    @Test
    void reportEntryIncludesAutoVectorizationEarlyExitGuardFamily() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(
                        new GpuIrReturn(null),
                        fixedWidthLoop(4, List.of(
                                laneAssignment("out", arrayRead("input", new GpuIrVariableRef("i")))
                        ))
                )), List.of(
                        parameter("out", "int[]"),
                        parameter("input", "int[]")
                )),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.QUIET,
                ignored -> { },
                entries::add
        );

        provider.validate(request);

        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationCandidates")));
        assertTrue("0".equals(entries.get(0).values().get("autoVectorizationRewritePlanOperations")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRewritePlanGuards")));
        assertTrue("1".equals(entries.get(0).values().get("autoVectorizationRewritePlanGuardFamily.earlyExitBoundary")));
    }

    @Test
    void strictSafetyModeFailsBuildForSafetyDiagnostics() {
        GpuIrValidationRequest request = request(GpuIrValidationMode.STRICT_SAFETY, brokenMethod());

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> provider.validate(request));

        assertTrue(exception.getMessage().contains("unknown variable reference: missing"));
    }

    @Test
    void offModeDoesNotRunValidation() {
        GpuIrValidationRequest request = request(GpuIrValidationMode.OFF, brokenMethod());

        assertDoesNotThrow(() -> provider.validate(request));
    }

    private GpuIrCompiledMethod brokenMethod() {
        return method(new GpuIrMethod("broken", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing")),
                new GpuIrAssignment(new GpuIrVariableRef("value"), new GpuIrLiteral("1"))
        )));
    }

    private GpuIrForLoop fixedWidthLoop(int endExclusive, List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary(
                        "<",
                        new GpuIrVariableRef("i"),
                        new GpuIrLiteral(Integer.toString(endExclusive))
                ),
                new GpuIrAssignment(
                        new GpuIrVariableRef("i"),
                        new net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary(
                                "+",
                                new GpuIrVariableRef("i"),
                                new GpuIrLiteral("1")
                        )
                ),
                body
        );
    }

    private GpuIrAssignment laneAssignment(String arrayName, net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression value) {
        return new GpuIrAssignment(arrayRead(arrayName, new GpuIrVariableRef("i")), value);
    }

    private net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess arrayRead(
            String arrayName,
            net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression index
    ) {
        return new net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess(arrayName, index);
    }

    private GpuIrValidationRequest request(GpuIrValidationMode mode, GpuIrCompiledMethod method) {
        return new GpuIrValidationRequest(method, List.of(), List.of(), true, mode);
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod) {
        return method(irMethod, List.of());
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod, List<ParsedGpuParameter> parameters) {
        return new GpuIrCompiledMethod(parsedMethod(irMethod.name(), parameters), irMethod, "jtg_" + irMethod.name(), List.of());
    }

    private ParsedGpuMethod parsedMethod(String name) {
        return parsedMethod(name, List.of());
    }

    private ParsedGpuMethod parsedMethod(String name, List<ParsedGpuParameter> parameters) {
        return new ParsedGpuMethod(
                "Owner",
                "test.Owner",
                name,
                "void",
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

    private ParsedGpuParameter parameter(String name, String type) {
        return new ParsedGpuParameter(name, type, GpuAddressSpace.GLOBAL, false, List.of());
    }
}
