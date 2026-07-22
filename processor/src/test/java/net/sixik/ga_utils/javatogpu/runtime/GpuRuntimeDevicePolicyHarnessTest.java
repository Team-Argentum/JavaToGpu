package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimeDevicePolicyHarness;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimeDevicePolicyHarnessReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeDevicePolicyHarnessTest {

    @Test
    void builtInHarnessSelectsSyntheticDiscreteGpuWithoutNativeRuntime() {
        GpuRuntimeDevicePolicyHarnessReport report = GpuRuntimeDevicePolicyHarness
                .loadWithBuiltIns()
                .runSyntheticOpenCl();
        Map<String, String> fields = report.artifactFields("devicePolicyHarness");

        assertEquals(GpuBackendTarget.OPENCL, report.backendTarget());
        assertEquals("selected", report.status());
        assertTrue(report.selected());
        assertEquals("Synthetic Discrete GPU", report.selectedDeviceLabel());
        assertEquals("none", report.selection().firstBlocker());
        assertEquals(3, report.candidates().size());
        assertTrue(report.selection().policyDecisions().size() >= 6);
        assertTrue(report.allPoliciesSucceeded());
        assertEquals("selected", fields.get("devicePolicyHarness.status"));
        assertEquals("true", fields.get("devicePolicyHarness.selected"));
        assertEquals("Synthetic Discrete GPU", fields.get("devicePolicyHarness.selectedDeviceLabel"));
        assertEquals("none", fields.get("devicePolicyHarness.firstBlocker"));
        assertTrue(report.toMarkdown().contains("Runtime device policy harness: selected"));
        assertTrue(report.toMarkdown().contains("Synthetic Discrete GPU"));
    }

    @Test
    void harnessRunsCustomPoliciesAgainstSyntheticCandidates() {
        GpuRuntimeDeviceProfile integrated = GpuRuntimeDevicePolicyHarness.syntheticOpenClIntegratedGpu();
        GpuRuntimeDeviceProfile discrete = GpuRuntimeDevicePolicyHarness.syntheticOpenClDiscreteGpu();
        String integratedKey = GpuRuntimeDevicePolicyContext.deviceKey(integrated);
        String discreteKey = GpuRuntimeDevicePolicyContext.deviceKey(discrete);
        GpuRuntimeDevicePolicy policy = new GpuRuntimeDevicePolicy() {
            @Override
            public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
                return new GpuRuntimeDevicePolicyDecision(
                        policyId(),
                        policyVersion(),
                        Map.of(integratedKey, 10_000_000),
                        Set.of(discreteKey),
                        Map.of("example.preferIntegrated", "true"),
                        List.of(),
                        true,
                        List.of(),
                        List.of("prefer synthetic integrated GPU for this test")
                );
            }

            @Override
            public String policyId() {
                return "test.device-policy.harness-preference";
            }
        };

        GpuRuntimeDevicePolicyHarnessReport report = GpuRuntimeDevicePolicyHarness
                .of(List.of(policy))
                .runSynthetic(GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL), List.of(integrated, discrete));

        assertEquals("selected", report.status());
        assertEquals("Synthetic Integrated GPU", report.selectedDeviceLabel());
        assertTrue(report.selection().rankedCandidates().stream()
                .filter(ranking -> ranking.deviceKey().equals(discreteKey))
                .findFirst()
                .orElseThrow()
                .rejected());
        assertEquals("test.device-policy.harness-preference",
                report.selection().policyDecisions().get(0).policyId());
        assertEquals("true", report.selection().artifactFields("selection")
                .get("selection.policy.0.capabilityFact.0.value"));
    }

    @Test
    void harnessReportsPolicyFailuresWithoutChangingRegistryBehavior() {
        GpuRuntimeDevicePolicy failing = new GpuRuntimeDevicePolicy() {
            @Override
            public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
                throw new IllegalStateException("synthetic policy failure");
            }

            @Override
            public String policyId() {
                return "test.device-policy.failing";
            }
        };

        GpuRuntimeDevicePolicyHarnessReport advisory = GpuRuntimeDevicePolicyHarness
                .of(List.of(failing))
                .runSyntheticOpenCl();
        GpuRuntimeDevicePolicyHarnessReport production = GpuRuntimeDevicePolicyHarness
                .of(List.of(failing))
                .runSyntheticOpenCl(new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "production"));

        assertEquals("selected-with-policy-failures", advisory.status());
        assertTrue(advisory.selected());
        assertFalse(advisory.allPoliciesSucceeded());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED,
                advisory.selection().executionReports().get(0).outcome());
        assertEquals("failed-closed", production.status());
        assertFalse(production.selected());
        assertEquals("device-policy-failed-closed", production.selection().firstBlocker());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CLOSED,
                production.selection().executionReports().get(0).outcome());
    }
}
