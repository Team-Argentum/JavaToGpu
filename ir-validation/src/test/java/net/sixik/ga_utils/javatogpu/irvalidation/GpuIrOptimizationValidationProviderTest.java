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
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("cseLocalExpressionProvenCandidates=0")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("cseLocalExpressionHasEvidence=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("cseRewritePolicyCanRewrite=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("cseRewritePolicyReadiness=none")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("cseRewritePolicyBlockingSkippedCandidates=0")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewriteReadiness=none")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationCanApplyRewrite=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofDecision=allow")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofDecisionAllowRewrite=true")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationHasPolicyBlockedRewrite=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewritePolicyCanRewrite=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewriteDryRunReadiness=skipped")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewriteDryRunSuccessful=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewriteDryRunDiagnostics=1")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofBundleRewriteSafe=true")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofBundleDiagnostics=0")));
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
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewriteReadiness=blockedByGuard")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationCanApplyRewrite=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofDecision=blockedByMultipleProofs")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofDecisionAllowRewrite=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofDecisionBlockingKinds=[rewritePlan, controlFlowBoundary]")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationHasPolicyBlockedRewrite=true")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewritePolicyCanRewrite=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewritePolicyBlockingGuards=1")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofBundleRewriteSafe=false")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofBundleDiagnostics=2")));
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
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("cseLocalExpression={")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("cseRewritePolicy={")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationRewritePolicy={")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorizationProofBundle={")));
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("autoVectorization={")));
    }

    @Test
    void reportEntryIncludesNoAutoVectorizationRewriteReadiness() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(
                        new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                        new GpuIrAssignment(new GpuIrVariableRef("value"), new GpuIrLiteral("2"))
                ))),
                List.of(),
                List.of(),
                true,
                GpuIrValidationMode.DIAGNOSTIC,
                GpuIrValidationDiagnosticPolicy.QUIET,
                ignored -> { },
                entries::add
        );

        provider.validate(request);

        assertEntryValue(entries, "autoVectorizationCandidates", "0");
        assertEntryValue(entries, "optimizerGateBlocked", "false");
        assertEntryValue(entries, "optimizerGateSource", "none");
        assertEntryValue(entries, "optimizerGateFamily", "none");
        assertEntryValue(entries, "optimizerGateSourceCounts", "{}");
        assertEntryValue(entries, "optimizerGateFamilyCounts", "{}");
        assertEntryValue(entries, "optimizerGatePolicyMode", "DIAGNOSTIC_ONLY");
        assertEntryValue(entries, "optimizerGatePolicyBlocked", "false");
        assertEntryValue(entries, "optimizerGatePolicySource", "none");
        assertEntryValue(entries, "optimizerGatePolicyFamily", "none");
        assertEntryValue(entries, "cseSkippedDominanceStatusCounts", "{}");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "none");
        assertEntryValue(entries, "autoVectorizationCanApplyRewrite", "false");
        assertEntryValue(entries, "autoVectorizationProofDecisionStatus", "allow");
        assertEntryValue(entries, "autoVectorizationProofDecisionAllowRewrite", "true");
        assertEntryValue(entries, "autoVectorizationProofDecisionBlockingProofKinds", "");
        assertEntryValue(entries, "autoVectorizationHasPolicyBlockedRewrite", "false");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "0");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "false");
        assertEntryValue(entries, "autoVectorizationRewritePlanOperations", "0");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuards", "0");
        assertEntryValue(entries, "autoVectorizationRewritePolicyCanRewrite", "false");
        assertEntryValue(entries, "autoVectorizationRewritePolicyReadiness", "none");
        assertEntryValue(entries, "autoVectorizationRewritePolicyPlannedOperations", "0");
        assertEntryValue(entries, "autoVectorizationRewritePolicyBlockingGuards", "0");
    }

    @Test
    void reportEntryIncludesAutoVectorizationRejectionReasonCounts() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(fixedWidthLoop(5, List.of(
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

        assertEntryValue(entries, "autoVectorizationCandidates", "0");
        assertEntryValue(entries, "optimizerGateBlocked", "true");
        assertEntryValue(entries, "optimizerGateSource", "autoVectorization");
        assertEntryValue(entries, "optimizerGateFamily", "rejection.UNSUPPORTED_LANE_COUNT");
        assertEntryValue(entries, "optimizerGateSourceCounts", "{autoVectorization=1}");
        assertEntryValue(entries, "optimizerGateSourceCount.autoVectorization", "1");
        assertEntryValue(entries, "optimizerGateFamilyCounts", "{rejection.UNSUPPORTED_LANE_COUNT=1}");
        assertEntryValue(entries, "optimizerGateFamilyCount.rejection.UNSUPPORTED_LANE_COUNT", "1");
        assertEntryValue(entries, "optimizerGatePolicyMode", "DIAGNOSTIC_ONLY");
        assertEntryValue(entries, "optimizerGatePolicyBlocked", "false");
        assertEntryValue(entries, "optimizerGatePolicySource", "none");
        assertEntryValue(entries, "optimizerGatePolicyFamily", "none");
        assertEntryValue(entries, "autoVectorizationRejections", "1");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "rejected");
        assertEntryValue(entries, "autoVectorizationCanApplyRewrite", "false");
        assertEntryValue(entries, "autoVectorizationHasPolicyBlockedRewrite", "false");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "0");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "false");
        assertEntryValue(entries, "autoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT", "1");
        assertEntryValue(entries, "autoVectorizationFirstBlockingDiagnosticFamily", "rejection.UNSUPPORTED_LANE_COUNT");
    }

    @Test
    void reportEntryIncludesAutoVectorizationWarningFamilyCounts() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(
                        new GpuIrVariableDeclaration("int", "j", new GpuIrLiteral("0")),
                        fixedWidthLoop(4, List.of(
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
                        ))
                )), List.of(
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

        assertEntryValue(entries, "autoVectorizationCandidates", "0");
        assertEntryValue(entries, "optimizerGateBlocked", "true");
        assertEntryValue(entries, "optimizerGateSource", "autoVectorization");
        assertEntryValue(entries, "optimizerGateFamily", "warning.alias");
        assertEntryValue(entries, "optimizerGateSourceCounts", "{autoVectorization=1,cseRewritePolicy=2}");
        assertEntryValue(entries, "optimizerGateSourceCount.autoVectorization", "1");
        assertEntryValue(entries, "optimizerGateSourceCount.cseRewritePolicy", "2");
        assertEntryValue(entries, "optimizerGateFamilyCounts", "{warning.alias=1,warning.repeatedTarget=1,warning.crossLaneRead=1,warning.nonLaneRead=1,cseRewritePolicy.blockedBySkippedCandidate=1,cseRewritePolicy.skipReason.CONTROL_FLOW_BOUNDARY=2,cseRewritePolicy.dominance.requiresLocalExpressionDominance=1,cseRewritePolicy.dominance.localExpressionDownstreamReplacements=1}");
        assertEntryValue(entries, "optimizerGateFamilyCount.warning.alias", "1");
        assertEntryValue(entries, "optimizerGateFamilyCount.cseRewritePolicy.skipReason.CONTROL_FLOW_BOUNDARY", "2");
        assertEntryValue(entries, "optimizerGateFamilyCount.cseRewritePolicy.dominance.requiresLocalExpressionDominance", "1");
        assertEntryValue(entries, "optimizerGateFamilyCount.cseRewritePolicy.dominance.localExpressionDownstreamReplacements", "1");
        assertEntryValue(entries, "optimizerGatePolicyMode", "DIAGNOSTIC_ONLY");
        assertEntryValue(entries, "optimizerGatePolicyBlocked", "false");
        assertEntryValue(entries, "optimizerGatePolicySource", "none");
        assertEntryValue(entries, "optimizerGatePolicyFamily", "none");
        assertEntryValue(entries, "cseSkippedDominanceStatusCounts", "{localExpressionDownstreamReplacements=1,requiresLocalExpressionDominance=1}");
        assertEntryValue(entries, "cseSkippedDominanceStatus.localExpressionDownstreamReplacements", "1");
        assertEntryValue(entries, "cseSkippedDominanceStatus.requiresLocalExpressionDominance", "1");
        assertEntryValue(entries, "cseFirstSkippedReason", "CONTROL_FLOW_BOUNDARY");
        assertEntryValue(entries, "cseFirstSkippedDominanceStatus", "requiresLocalExpressionDominance");
        assertEntryValueContains(entries, "cseFirstSkippedDominanceSummary", "dominance=requiresLocalExpressionDominance");
        assertEntryValue(entries, "autoVectorizationWarnings", "1");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "blockedByWarning");
        assertEntryValue(entries, "autoVectorizationCanApplyRewrite", "false");
        assertEntryValue(entries, "autoVectorizationHasPolicyBlockedRewrite", "false");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "0");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "false");
        assertEntryValue(entries, "autoVectorizationWarningFamily.alias", "1");
        assertEntryValue(entries, "autoVectorizationWarningFamily.repeatedTarget", "1");
        assertEntryValue(entries, "autoVectorizationWarningFamily.crossLaneRead", "1");
        assertEntryValue(entries, "autoVectorizationWarningFamily.nonLaneRead", "1");
        assertEntryValue(entries, "autoVectorizationFirstBlockingDiagnosticFamily", "warning.alias");
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

        assertEntryValue(entries, "autoVectorizationCandidates", "1");
        assertEntryValue(entries, "optimizerGateBlocked", "false");
        assertEntryValue(entries, "optimizerGateSource", "none");
        assertEntryValue(entries, "optimizerGateFamily", "none");
        assertEntryValue(entries, "optimizerGateSourceCounts", "{}");
        assertEntryValue(entries, "optimizerGateFamilyCounts", "{}");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "ready");
        assertEntryValue(entries, "autoVectorizationCanApplyRewrite", "true");
        assertEntryValue(entries, "autoVectorizationProofDecisionStatus", "allow");
        assertEntryValue(entries, "autoVectorizationProofDecisionAllowRewrite", "true");
        assertEntryValue(entries, "autoVectorizationProofDecisionBlockingProofKinds", "");
        assertEntryValue(entries, "autoVectorizationHasPolicyBlockedRewrite", "false");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "0");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "false");
        assertEntryValue(entries, "autoVectorizationRewritePlanCandidates", "1");
        assertEntryValue(entries, "autoVectorizationRewritePlanInsertions", "1");
        assertEntryValue(entries, "autoVectorizationRewritePlanReplacements", "1");
        assertEntryValue(entries, "autoVectorizationRewritePlanOperations", "2");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuards", "0");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanKind", "rewritePlan");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanLocation", "kernel");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanRewriteSafe", "true");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanWarnings", "0");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanGuardDiagnostics", "0");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanDiagnostics", "0");
        assertProofBundleFields(entries, "2", "rewritePlan,memoryLegality", "true", "0", "0", "0", "0", "{}", null);
        assertEntryValue(entries, "autoVectorizationRewritePolicyCanRewrite", "true");
        assertEntryValue(entries, "autoVectorizationRewritePolicyReadiness", "ready");
        assertEntryValue(entries, "autoVectorizationRewritePolicyPlannedOperations", "2");
        assertEntryValue(entries, "autoVectorizationRewritePolicyBlockingGuards", "0");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunReadiness", "ready");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunSuccessful", "true");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunDiagnostics", "0");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunCandidates", "1");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunOperations", "2");
        assertEntryValue(entries, "autoVectorizationVectorType.int4", "1");
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

        assertEntryValue(entries, "autoVectorizationCandidates", "1");
        assertEntryValue(entries, "optimizerGateBlocked", "true");
        assertEntryValue(entries, "optimizerGateSource", "autoVectorization");
        assertEntryValue(entries, "optimizerGateFamily", "guard.neighborSourceWrite");
        assertEntryValue(entries, "optimizerGateSourceCounts", "{autoVectorization=1}");
        assertEntryValue(entries, "optimizerGateSourceCount.autoVectorization", "1");
        assertEntryValue(entries, "optimizerGateFamilyCounts", "{guard.neighborSourceWrite=1}");
        assertEntryValue(entries, "optimizerGateFamilyCount.guard.neighborSourceWrite", "1");
        assertEntryValue(entries, "optimizerGatePolicyMode", "DIAGNOSTIC_ONLY");
        assertEntryValue(entries, "optimizerGatePolicyBlocked", "false");
        assertEntryValue(entries, "optimizerGatePolicySource", "none");
        assertEntryValue(entries, "optimizerGatePolicyFamily", "none");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "blockedByGuard");
        assertEntryValue(entries, "autoVectorizationCanApplyRewrite", "false");
        assertEntryValue(entries, "autoVectorizationProofDecisionStatus", "blockedByRewritePlan");
        assertEntryValue(entries, "autoVectorizationProofDecisionAllowRewrite", "false");
        assertEntryValue(entries, "autoVectorizationProofDecisionBlockingProofKinds", "rewritePlan");
        assertEntryValue(entries, "autoVectorizationProofDecisionFirstBlockingProofKind", "rewritePlan");
        assertEntryValue(entries, "autoVectorizationHasPolicyBlockedRewrite", "true");
        assertEntryValue(entries, "autoVectorizationRewritePlanOperations", "0");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "1");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "true");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuards", "1");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanKind", "rewritePlan");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanLocation", "kernel");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanRewriteSafe", "false");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanWarnings", "0");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanGuardDiagnostics", "1");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanDiagnostics", "1");
        assertEntryValue(entries, "autoVectorizationProofRewritePlanGuardFamily.neighborSourceWrite", "1");
        assertEntryValueContains(entries, "autoVectorizationProofRewritePlanSummary", "guardFamilies={neighborSourceWrite=1}");
        assertProofBundleFields(entries, "3", "rewritePlan,controlFlowBoundary,memoryLegality", "false", "0", "1", "1", "1", "{rewritePlan=1}", "neighborSourceWrite");
        assertEntryValue(entries, "autoVectorizationRewritePolicyCanRewrite", "false");
        assertEntryValue(entries, "autoVectorizationRewritePolicyReadiness", "blockedByGuard");
        assertEntryValue(entries, "autoVectorizationRewritePolicyPlannedOperations", "2");
        assertEntryValue(entries, "autoVectorizationRewritePolicyBlockingGuards", "1");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunReadiness", "ready");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunSuccessful", "true");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunDiagnostics", "0");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunCandidates", "1");
        assertEntryValue(entries, "autoVectorizationRewriteDryRunOperations", "2");
        assertEntryValue(entries, "autoVectorizationRewritePolicyFirstBlockingGuardFamily", "neighborSourceWrite");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuardFamily.neighborSourceWrite", "1");
        assertEntryValueContains(entries, "autoVectorizationFirstBlockingDiagnostic", "writes source array `input`");
        assertEntryValue(entries, "autoVectorizationFirstBlockingDiagnosticFamily", "guard.neighborSourceWrite");
    }

    @Test
    void reportEntryIncludesAutoVectorizationBackendVectorWidthGuardFamily() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(fixedWidthLoop(3, List.of(
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

        assertEntryValue(entries, "autoVectorizationCandidates", "1");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "blockedByGuard");
        assertEntryValue(entries, "autoVectorizationRewritePlanOperations", "0");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "1");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "true");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuards", "1");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuardFamily.backendVectorWidth", "1");
        assertEntryValueContains(entries, "autoVectorizationFirstBlockingDiagnostic", "backend vector width x3");
        assertEntryValue(entries, "autoVectorizationFirstBlockingDiagnosticFamily", "guard.backendVectorWidth");
    }

    @Test
    void reportEntryIncludesAutoVectorizationBackendDoubleVectorGuardFamily() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(fixedWidthLoop(4, List.of(
                        laneAssignment("out", arrayRead("input", new GpuIrVariableRef("i")))
                )))), List.of(
                        parameter("out", "double[]"),
                        parameter("input", "double[]")
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

        assertEntryValue(entries, "autoVectorizationCandidates", "1");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "blockedByGuard");
        assertEntryValue(entries, "autoVectorizationRewritePlanOperations", "0");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "1");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "true");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuards", "1");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuardFamily.backendDoubleVector", "1");
        assertEntryValueContains(entries, "autoVectorizationFirstBlockingDiagnostic", "backend double vector type double4");
        assertEntryValue(entries, "autoVectorizationFirstBlockingDiagnosticFamily", "guard.backendDoubleVector");
    }

    @Test
    void reportEntryIncludesAutoVectorizationMemoryAddressSpaceGuardFamily() {
        List<GpuIrValidationReportEntry> entries = new ArrayList<>();
        GpuIrValidationRequest request = new GpuIrValidationRequest(
                method(new GpuIrMethod("kernel", List.of(fixedWidthLoop(4, List.of(
                        laneAssignment("out", arrayRead("input", new GpuIrVariableRef("i")))
                )))), List.of(
                        parameter("out", "int[]"),
                        parameter("input", "int[]", GpuAddressSpace.CONSTANT, false)
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

        assertEntryValue(entries, "autoVectorizationCandidates", "1");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "blockedByGuard");
        assertEntryValue(entries, "autoVectorizationRewritePlanOperations", "0");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "1");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "true");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuards", "1");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuardFamily.memoryAddressSpace", "1");
        assertEntryValueContains(entries, "autoVectorizationFirstBlockingDiagnostic", "constant memory address space");
        assertEntryValue(entries, "autoVectorizationFirstBlockingDiagnosticFamily", "guard.memoryAddressSpace");
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

        assertEntryValue(entries, "autoVectorizationCandidates", "1");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "blockedByGuard");
        assertEntryValue(entries, "autoVectorizationRewritePlanOperations", "0");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "1");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "true");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuards", "1");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuardFamily.controlFlowBoundary", "1");
        assertEntryValueContains(entries, "autoVectorizationFirstBlockingDiagnostic", "control-flow boundary");
        assertEntryValue(entries, "autoVectorizationFirstBlockingDiagnosticFamily", "guard.controlFlowBoundary");
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

        assertEntryValue(entries, "autoVectorizationCandidates", "1");
        assertEntryValue(entries, "autoVectorizationRewriteReadiness", "blockedByGuard");
        assertEntryValue(entries, "autoVectorizationRewritePlanOperations", "0");
        assertEntryValue(entries, "autoVectorizationRewriteBlockedCandidates", "1");
        assertEntryValue(entries, "autoVectorizationHasRewriteBlockedCandidates", "true");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuards", "1");
        assertEntryValue(entries, "autoVectorizationRewritePlanGuardFamily.earlyExitBoundary", "1");
        assertEntryValueContains(entries, "autoVectorizationFirstBlockingDiagnostic", "early-exit boundary");
        assertEntryValue(entries, "autoVectorizationFirstBlockingDiagnosticFamily", "guard.earlyExitBoundary");
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

    private void assertEntryValue(List<GpuIrValidationReportEntry> entries, String key, String expectedValue) {
        String actualValue = firstEntryValue(entries, key);
        assertTrue(expectedValue.equals(actualValue), key + " expected=" + expectedValue + " actual=" + actualValue + " values=" + entries.get(0).values());
    }

    private void assertEntryValueContains(List<GpuIrValidationReportEntry> entries, String key, String expectedFragment) {
        String actualValue = firstEntryValue(entries, key);
        assertTrue(actualValue != null && actualValue.contains(expectedFragment));
    }

    private void assertProofBundleFields(
            List<GpuIrValidationReportEntry> entries,
            String proofs,
            String kinds,
            String rewriteSafe,
            String warnings,
            String guardDiagnostics,
            String diagnostics,
            String unsafeProofs,
            String unsafeProofKindCounts,
            String guardFamily
    ) {
        assertEntryValue(entries, "autoVectorizationProofBundleProofs", proofs);
        assertEntryValue(entries, "autoVectorizationProofBundleKinds", kinds);
        assertEntryValue(entries, "autoVectorizationProofBundleRewriteSafe", rewriteSafe);
        assertEntryValue(entries, "autoVectorizationProofBundleWarnings", warnings);
        assertEntryValue(entries, "autoVectorizationProofBundleGuardDiagnostics", guardDiagnostics);
        assertEntryValue(entries, "autoVectorizationProofBundleDiagnostics", diagnostics);
        assertEntryValue(entries, "autoVectorizationProofBundleUnsafeProofs", unsafeProofs);
        assertEntryValue(entries, "autoVectorizationProofBundleUnsafeProofKindCounts", unsafeProofKindCounts);
        if (!"0".equals(unsafeProofs)) {
            assertEntryValue(entries, "autoVectorizationProofBundleUnsafeProofKind.rewritePlan", "1");
        }
        if (guardFamily != null) {
            assertEntryValue(entries, "autoVectorizationProofBundleGuardFamily." + guardFamily, "1");
            assertEntryValueContains(entries, "autoVectorizationProofBundleSummary", "guardFamilies={" + guardFamily + "=1}");
        }
    }

    private String firstEntryValue(List<GpuIrValidationReportEntry> entries, String key) {
        return entries.get(0).values().get(key);
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
        return parameter(name, type, GpuAddressSpace.GLOBAL, false);
    }

    private ParsedGpuParameter parameter(String name, String type, GpuAddressSpace addressSpace, boolean constant) {
        return new ParsedGpuParameter(name, type, addressSpace, constant, List.of());
    }
}
