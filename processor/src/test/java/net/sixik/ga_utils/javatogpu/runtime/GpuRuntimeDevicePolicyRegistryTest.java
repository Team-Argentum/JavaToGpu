package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodDeviceConstraint;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeDevicePolicyRegistryTest {

    @Test
    void builtInPolicySelectsStrongestCompatibleDeviceDeterministically() {
        GpuRuntimeDeviceProfile integrated = device("Intel UHD", "Intel", 8, 256, 32_768);
        GpuRuntimeDeviceProfile discrete = device("RTX 5070", "NVIDIA", 48, 1024, 65_536);
        GpuRuntimeDevicePolicyContext context = context(List.of(integrated, discrete));

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(context);

        assertTrue(selection.selectedDevice().isPresent());
        assertEquals("RTX 5070", selection.selectedDevice().orElseThrow().deviceLabel());
        assertEquals("none", selection.firstBlocker());
        assertTrue(selection.rankedCandidates().get(0).totalScore() > selection.rankedCandidates().get(1).totalScore());
        Map<String, String> fields = selection.artifactFields("device");
        assertEquals("true", fields.get("device.selected"));
        assertEquals("RTX 5070", fields.get("device.selected.deviceLabel"));
        assertEquals(GpuRuntimeBackendCompatibilityDevicePolicy.POLICY_ID, fields.get("device.policy.0.policyId"));
        assertEquals(GpuRuntimeBackendCompatibilityDevicePolicy.POLICY_ID, fields.get("device.execution.0.extensionId"));
        assertEquals("SUCCEEDED", fields.get("device.execution.0.outcome"));
        assertEquals("2", fields.get("device.diagnostic.count"));
    }

    @Test
    void discreteGpuOutranksIntegratedGpuBeforeCapabilityScoring() {
        GpuRuntimeDeviceProfile integrated = classifiedDevice(
                "opencl-0",
                "Large iGPU",
                "Intel",
                GpuDeviceClassTarget.IGPU,
                128,
                true
        );
        GpuRuntimeDeviceProfile discrete = classifiedDevice(
                "opencl-1",
                "Small dGPU",
                "NVIDIA",
                GpuDeviceClassTarget.DGPU,
                16,
                false
        );

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns()
                .select(GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        List.of(integrated, discrete)
                ));

        assertEquals("Small dGPU", selection.selectedDevice().orElseThrow().deviceLabel());
        assertEquals("dgpu", selection.artifactFields("device").get("device.candidate.0.deviceClass"));
        assertTrue(selection.rankedCandidates().get(0).totalScore() > selection.rankedCandidates().get(1).totalScore());
    }

    @Test
    void explicitVendorOverrideSelectsMatchingDeviceInsteadOfAutomaticWinner() {
        GpuRuntimeDeviceProfile integrated = classifiedDevice(
                "opencl-0",
                "Intel Arc Integrated",
                "Intel",
                GpuDeviceClassTarget.IGPU,
                8,
                true
        );
        GpuRuntimeDeviceProfile discrete = classifiedDevice(
                "opencl-1",
                "NVIDIA RTX",
                "NVIDIA Corporation",
                GpuDeviceClassTarget.DGPU,
                48,
                false
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .withDeviceOverride(GpuRuntimeDeviceOverride.byVendor("Intel"));

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                new GpuRuntimeDevicePolicyContext(descriptor(), options, List.of(integrated, discrete))
        );

        assertEquals("Intel Arc Integrated", selection.selectedDevice().orElseThrow().deviceLabel());
        assertTrue(selection.rankedCandidates().stream()
                .filter(ranking -> ranking.profile() == discrete)
                .findFirst()
                .orElseThrow()
                .rejected());
        assertEquals(
                GpuRuntimeExplicitDeviceOverridePolicy.POLICY_ID,
                selection.artifactFields("device").get("device.policy.1.policyId")
        );
    }

    @Test
    void unmatchedExplicitOverrideRejectsSelectionFailClosed() {
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .withDeviceOverride(GpuRuntimeDeviceOverride.byDeviceId("missing-device"));

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                new GpuRuntimeDevicePolicyContext(
                        descriptor(),
                        options,
                        List.of(classifiedDevice(
                                "opencl-0",
                                "NVIDIA RTX",
                                "NVIDIA",
                                GpuDeviceClassTarget.DGPU,
                                48,
                                false
                        ))
                )
        );

        assertTrue(selection.selectedDevice().isEmpty());
        assertEquals("compatible-device-missing", selection.firstBlocker());
        assertTrue(selection.diagnostics().stream().anyMatch(value -> value.contains("matched no discovered candidates")));
    }

    @Test
    void irGpuMethodConstraintRejectsIncompatibleVendorBeforeCompile() {
        GpuRuntimeDeviceProfile nvidia = classifiedDevice(
                "opencl-0",
                "NVIDIA RTX",
                "NVIDIA",
                GpuDeviceClassTarget.DGPU,
                64,
                false
        );
        GpuRuntimeDeviceProfile amd = classifiedDevice(
                "opencl-1",
                "AMD Radeon",
                "AMD",
                GpuDeviceClassTarget.DGPU,
                32,
                false
        );
        IrGpuArtifact artifact = constrainedArtifact(new IrGpuMethodDeviceConstraint(
                "kernel",
                "jtg_kernel",
                List.of(GpuBackendTarget.OPENCL),
                List.of(net.sixik.ga_utils.javatogpu.api.GpuVendorTarget.AMD),
                List.of(GpuDeviceClassTarget.DGPU),
                List.of("fp64"),
                "test"
        ));

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                new GpuRuntimeDevicePolicyContext(
                        descriptor(),
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        List.of(nvidia, amd),
                        java.util.Optional.of(artifact)
                )
        );

        assertEquals("AMD Radeon", selection.selectedDevice().orElseThrow().deviceLabel());
        assertTrue(selection.rankedCandidates().stream()
                .filter(ranking -> ranking.profile() == nvidia)
                .findFirst()
                .orElseThrow()
                .rejected());
        assertEquals(
                GpuRuntimeMethodDeviceConstraintPolicy.POLICY_ID,
                selection.artifactFields("device").get("device.policy.2.policyId")
        );
    }

    @Test
    void deviceOverrideSurvivesCompileOptionDerivations() {
        GpuRuntimeDeviceOverride override = GpuRuntimeDeviceOverride.byDeviceLabel("RTX 5070");
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .withDeviceOverride(override)
                .withProductionPromotionOperatorAccepted(true)
                .withProductionPromotionDecision(GpuProductionPromotionDecision.diagnosticOnly());

        assertEquals(override, options.deviceOverride());
    }

    @Test
    void thirdPartyPolicyCanAdjustRankingAndRejectCandidates() {
        GpuRuntimeDeviceProfile first = device("GPU A", "Vendor A", 32, 512, 65_536);
        GpuRuntimeDeviceProfile second = device("GPU B", "Vendor B", 16, 256, 32_768);
        String firstKey = GpuRuntimeDevicePolicyContext.deviceKey(first);
        String secondKey = GpuRuntimeDevicePolicyContext.deviceKey(second);
        GpuRuntimeDevicePolicy policy = new GpuRuntimeDevicePolicy() {
            @Override
            public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
                return new GpuRuntimeDevicePolicyDecision(
                        policyId(),
                        policyVersion(),
                        Map.of(secondKey, 1_000_000),
                        Set.of(firstKey),
                        Map.of(secondKey + ".preferred", "true"),
                        List.of("Vendor A driver is blocked for this workload"),
                        true,
                        List.of(),
                        List.of("prefer GPU B")
                );
            }

            @Override
            public String policyId() {
                return "test.device-policy";
            }
        };
        GpuRuntimeDevicePolicyRegistry registry = GpuRuntimeDevicePolicyRegistry.of(List.of(
                new GpuRuntimeBackendCompatibilityDevicePolicy(),
                policy
        ));

        GpuRuntimeDeviceSelection selection = registry.select(context(List.of(first, second)));

        assertEquals("GPU B", selection.selectedDevice().orElseThrow().deviceLabel());
        assertTrue(selection.rankedCandidates().stream()
                .filter(ranking -> ranking.deviceKey().equals(firstKey))
                .findFirst()
                .orElseThrow()
                .rejected());
        assertEquals(2, selection.policyDecisions().size());
    }

    @Test
    void advisoryPolicyFailureIsIsolatedButProductionFailureBlocksSelection() {
        GpuRuntimeDevicePolicy failing = context -> {
            throw new IllegalStateException("policy exploded");
        };
        GpuRuntimeDevicePolicyRegistry registry = GpuRuntimeDevicePolicyRegistry.of(List.of(failing));
        GpuRuntimeDeviceProfile candidate = device("GPU", "Vendor", 10, 256, 32_768);

        GpuRuntimeDeviceSelection advisory = registry.select(context(List.of(candidate)));
        GpuRuntimeDeviceSelection production = registry.select(new GpuRuntimeDevicePolicyContext(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "production"),
                List.of(candidate)
        ));

        assertTrue(advisory.selectedDevice().isPresent());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED, advisory.executionReports().get(0).outcome());
        assertFalse(production.selectedDevice().isPresent());
        assertTrue(production.failedClosed());
        assertEquals("device-policy-failed-closed", production.firstBlocker());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CLOSED, production.executionReports().get(0).outcome());
    }

    @Test
    void invalidCompileOptionsBlockDeviceSelection() {
        GpuRuntimeDevicePolicy policy = new GpuRuntimeDevicePolicy() {
            @Override
            public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
                return new GpuRuntimeDevicePolicyDecision(
                        policyId(),
                        policyVersion(),
                        Map.of(),
                        Set.of(),
                        Map.of(),
                        List.of(),
                        false,
                        List.of("unsupported vendor compile option"),
                        List.of()
                );
            }

            @Override
            public String policyId() {
                return "test.compile-options";
            }
        };

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.of(List.of(policy))
                .select(context(List.of(device("GPU", "Vendor", 10, 256, 32_768))));

        assertFalse(selection.selectedDevice().isPresent());
        assertFalse(selection.compileOptionsValid());
        assertEquals("compile-options-rejected-by-device-policy", selection.firstBlocker());
    }

    @Test
    void registryRejectsDuplicatePolicyIdsEvenWhenExtensionIdsDiffer() {
        GpuRuntimeDevicePolicy first = namedPolicy("duplicate-policy", "test.policy.first");
        GpuRuntimeDevicePolicy second = namedPolicy("duplicate-policy", "test.policy.second");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuRuntimeDevicePolicyRegistry.of(List.of(first, second))
        );

        assertTrue(exception.getMessage().contains("Duplicate device policy id 'duplicate-policy'"));
    }

    private static GpuRuntimeDevicePolicyContext context(List<GpuRuntimeDeviceProfile> candidates) {
        return new GpuRuntimeDevicePolicyContext(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                candidates
        );
    }

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static GpuRuntimeDeviceProfile device(
            String label,
            String vendor,
            long computeUnits,
            long maxWorkGroupSize,
            long localMemoryBytes
    ) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                label,
                vendor,
                "test-driver",
                "OpenCL 3.0 Test",
                computeUnits,
                localMemoryBytes,
                maxWorkGroupSize,
                1,
                true,
                true,
                false
        );
    }

    private static GpuRuntimeDeviceProfile classifiedDevice(
            String deviceId,
            String label,
            String vendor,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            boolean unifiedMemory
    ) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                deviceId,
                label,
                vendor,
                "test-driver",
                "OpenCL 3.0 Test",
                deviceClass,
                computeUnits,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                unifiedMemory,
                true,
                true,
                false
        );
    }

    private static GpuRuntimeDevicePolicy namedPolicy(String policyId, String extensionId) {
        return new GpuRuntimeDevicePolicy() {
            @Override
            public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
                return GpuRuntimeDevicePolicyDecision.noChange(this);
            }

            @Override
            public String policyId() {
                return policyId;
            }

            @Override
            public String extensionId() {
                return extensionId;
            }
        };
    }

    private static IrGpuArtifact constrainedArtifact(IrGpuMethodDeviceConstraint constraint) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "jtg_kernel", List.of(), List.of(), List.of()),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        ).withMethodDeviceConstraints(List.of(constraint));
    }
}
