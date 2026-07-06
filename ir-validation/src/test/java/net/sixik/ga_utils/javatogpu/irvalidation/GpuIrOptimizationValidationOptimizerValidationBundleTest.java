package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerValidationBundleTest {
    @Test
    void bundlesValidationReportEnablementArtifactAndGateForTooling() {
        GpuIrOptimizationValidationReport validationReport = validationReport("validationBundleKernel");

        GpuIrOptimizationValidationOptimizerValidationBundle bundle =
                GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport);
        Map<String, String> fields = bundle.artifactFields();

        assertEquals("validationBundleKernel", bundle.methodName());
        assertEquals("notReady/cseBlocked", bundle.verdict());
        assertFalse(bundle.readyForProductionMutation());
        assertFalse(bundle.productionMutationEnabled());
        assertEquals("validationBundleKernel", fields.get("optimizerValidationBundleMethod"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerValidationBundleVerdict"));
        assertEquals("false", fields.get("optimizerValidationBundleReadyForProductionMutation"));
        assertEquals("false", fields.get("optimizerValidationBundleProductionMutationEnabled"));
        assertEquals("false", fields.get("optimizerValidationBundleValidationHasSafetyError"));
        assertEquals("false", fields.get("optimizerValidationBundleValidationHasOptimizerDiagnostics"));
        assertEquals("pass", fields.get("optimizerValidationBundleRuleArtifactVerdict"));
        assertEquals("true", fields.get("optimizerValidationBundleRuleArtifactAccepted"));
        assertEquals("reviewAllowed/productionMutationDisabled", fields.get("optimizerValidationBundleEnablementPolicyVerdict"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerValidationBundleGateFirstBlockingReason"));
        assertEquals("collectPreviewCandidates", fields.get("optimizerValidationBundleGateFirstRemainingWork"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
        assertEquals("true", fields.get("optimizerProductionPreflightBlocked"));
        assertEquals("cseLiteralPromotionNotReady", fields.get("optimizerProductionPreflightFirstBlockingReason"));
        assertEquals("validationBundleKernel", fields.get("validationRulesMethod"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerEnablementGateVerdict"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void canAppendBundleFieldsIntoExistingMap() {
        GpuIrOptimizationValidationOptimizerValidationBundle bundle =
                GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport("appendBundleKernel"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("existing", "preserved");

        bundle.putArtifactFields(fields);

        assertEquals("preserved", fields.get("existing"));
        assertEquals("appendBundleKernel", fields.get("optimizerValidationBundleMethod"));
        assertEquals("appendBundleKernel", fields.get("validationRulesMethod"));
        assertEquals("appendBundleKernel", fields.get("optimizerEnablementGateMethod"));
    }

    @Test
    void runnerCanExportValidationBundleAndFields() {
        GpuIrOptimizationValidationReport validationReport = validationReport("runnerBundleKernel");
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();

        GpuIrOptimizationValidationOptimizerValidationBundle bundle = runner.runBundle(validationReport);
        Map<String, String> fields = runner.runBundleFields(validationReport);

        assertEquals("runnerBundleKernel", bundle.methodName());
        assertEquals("notReady/cseBlocked", fields.get("optimizerValidationBundleVerdict"));
        assertEquals("false", fields.get("optimizerValidationBundleProductionMutationEnabled"));
        assertEquals("notReady/cseBlocked", fields.get("optimizerEnablementGateVerdict"));
        assertEquals("blocked/optimizerValidationBundleNotReady", fields.get("optimizerProductionPreflightVerdict"));
    }

    @Test
    void rejectsNullInputsAndMismatchedContracts() {
        GpuIrOptimizationValidationReport validationReport = validationReport("invalidBundleKernel");
        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner().run(validationReport);
        GpuIrOptimizationValidationOptimizerEnablementGateReport mismatchedGate =
                new GpuIrOptimizationValidationOptimizerEnablementGateReport(
                        "otherKernel",
                        "reviewReady/productionMutationDisabled",
                        false,
                        true,
                        true,
                        true,
                        true,
                        false,
                        "readyForProductionMutation",
                        "readyForPrototypeRewrite",
                        "reviewAllowed/productionMutationDisabled",
                        List.of("productionMutationDisabled"),
                        List.of("enableProductionMutationPolicy")
                );
        GpuIrOptimizationValidationOptimizerValidationBundle bundle =
                GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport);
        GpuIrOptimizationValidationOptimizerEnablementArtifactRunner runner =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner();

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerValidationBundle.from(null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerValidationBundle.from(validationReport, null));
        assertThrows(NullPointerException.class, () -> bundle.putArtifactFields(null));
        assertThrows(NullPointerException.class, () -> runner.runBundle(null));
        assertThrows(NullPointerException.class, () -> runner.runBundleFields(null));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerValidationBundle(
                validationReport,
                artifact,
                mismatchedGate
        ));
    }

    @Test
    void canRepresentFutureReadyBundleContract() {
        GpuIrOptimizationValidationReport validationReport = validationReport("futureReadyBundleKernel");
        GpuIrOptimizationValidationOptimizerEnablementArtifact artifact =
                new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner().run(validationReport);
        GpuIrOptimizationValidationOptimizerEnablementGateReport readyGate =
                new GpuIrOptimizationValidationOptimizerEnablementGateReport(
                        "futureReadyBundleKernel",
                        "readyForProductionMutation",
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        "readyForProductionMutation",
                        "readyForPrototypeRewrite",
                        "futureProductionMutationEnabled",
                        List.of(),
                        List.of()
                );

        GpuIrOptimizationValidationOptimizerValidationBundle bundle =
                new GpuIrOptimizationValidationOptimizerValidationBundle(validationReport, artifact, readyGate);

        assertTrue(bundle.readyForProductionMutation());
        assertTrue(bundle.productionMutationEnabled());
        assertEquals("readyForProductionMutation", bundle.verdict());
        assertTrue(bundle.ciSummaryLine().contains("readyForProductionMutation=true"));
    }
}
