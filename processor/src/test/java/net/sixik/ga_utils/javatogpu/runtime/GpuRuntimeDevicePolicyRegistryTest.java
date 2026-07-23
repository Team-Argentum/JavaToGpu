package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.methodtest.*;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodDeviceConstraint;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodTestVectorMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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
        GpuRuntimeDevicePreference preference = GpuRuntimeDevicePreference.builder()
                .preferVendor("NVIDIA")
                .excludeDeviceClass(GpuDeviceClassTarget.CPU)
                .build();
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .withDeviceOverride(override)
                .withDevicePreference(preference)
                .withProductionPromotionOperatorAccepted(true)
                .withProductionPromotionDecision(GpuProductionPromotionDecision.diagnosticOnly())
                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED);

        assertEquals(override, options.deviceOverride());
        assertEquals(preference, options.devicePreference());
    }

    @Test
    void compileProvenanceRecordsDeviceOverrideAndPreference() {
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .withDeviceOverride(GpuRuntimeDeviceOverride.byVendor("NVIDIA"))
                .preferDeviceClass(GpuDeviceClassTarget.DGPU)
                .excludeIntegratedAndCpuDevices();

        GpuRuntimeCompileProvenance provenance = GpuRuntimeCompileProvenance.from(new GpuRuntimeCompileRequest(
                descriptor(),
                options,
                classifiedDevice(
                        "opencl-1",
                        "NVIDIA RTX",
                        "NVIDIA Corporation",
                        GpuDeviceClassTarget.DGPU,
                        48,
                        false
                )
        ));

        assertEquals("deviceId=any, vendor=NVIDIA, deviceLabel=any, deviceClass=any", provenance.deviceOverride());
        assertTrue(provenance.devicePreference().contains("preferDeviceClasses=[dgpu]"));
        assertTrue(provenance.devicePreference().contains("excludeDeviceClasses=[cpu, igpu]"));
        assertTrue(provenance.toPropertiesText().contains("deviceOverride=deviceId=any, vendor=NVIDIA"));
        assertTrue(provenance.toPropertiesText().contains("devicePreference=preferDeviceIds=any"));
    }

    @Test
    void devicePreferenceCanPreferIntegratedGpuOverAutomaticDiscreteWinner() {
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
                .preferDeviceClass(GpuDeviceClassTarget.IGPU);

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                new GpuRuntimeDevicePolicyContext(descriptor(), options, List.of(integrated, discrete))
        );
        GpuRuntimeDevicePolicyDecision preferenceDecision = decision(selection, GpuRuntimeDevicePreferencePolicy.POLICY_ID);
        String integratedKey = GpuRuntimeDevicePolicyContext.deviceKey(integrated);

        assertEquals("Intel Arc Integrated", selection.selectedDevice().orElseThrow().deviceLabel());
        assertTrue(preferenceDecision.scoreAdjustments().get(integratedKey) > 0);
        assertEquals("true", preferenceDecision.capabilityFacts().get(integratedKey + ".preferenceMatched"));
        assertTrue(selection.diagnostics().stream().anyMatch(value -> value.contains("device preference applied")));
    }

    @Test
    void devicePreferenceCanExcludeCpuAndIntegratedDevices() {
        GpuRuntimeDeviceProfile cpu = classifiedDevice(
                "opencl-cpu",
                "CPU OpenCL",
                "PortableCL",
                GpuDeviceClassTarget.CPU,
                64,
                true
        );
        GpuRuntimeDeviceProfile integrated = classifiedDevice(
                "opencl-igpu",
                "Intel Integrated",
                "Intel",
                GpuDeviceClassTarget.IGPU,
                8,
                true
        );
        GpuRuntimeDeviceProfile discrete = classifiedDevice(
                "opencl-dgpu",
                "NVIDIA RTX",
                "NVIDIA",
                GpuDeviceClassTarget.DGPU,
                48,
                false
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .excludeIntegratedAndCpuDevices();

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                new GpuRuntimeDevicePolicyContext(descriptor(), options, List.of(cpu, integrated, discrete))
        );
        GpuRuntimeDevicePolicyDecision preferenceDecision = decision(selection, GpuRuntimeDevicePreferencePolicy.POLICY_ID);

        assertEquals("NVIDIA RTX", selection.selectedDevice().orElseThrow().deviceLabel());
        assertTrue(preferenceDecision.rejectedDeviceKeys().contains(GpuRuntimeDevicePolicyContext.deviceKey(cpu)));
        assertTrue(preferenceDecision.rejectedDeviceKeys().contains(GpuRuntimeDevicePolicyContext.deviceKey(integrated)));
        assertTrue(!preferenceDecision.rejectedDeviceKeys().contains(GpuRuntimeDevicePolicyContext.deviceKey(discrete)));
        assertEquals(
                "device class is excluded: cpu",
                preferenceDecision.capabilityFacts().get(GpuRuntimeDevicePolicyContext.deviceKey(cpu) + ".preferenceRejectionReason")
        );
    }

    @Test
    void cachedMethodTestProbeEvidenceCanPreferPassedCandidate() throws Exception {
        GpuRuntimeDeviceProfile integrated = classifiedDevice(
                "opencl-igpu",
                "Intel Integrated",
                "Intel",
                GpuDeviceClassTarget.IGPU,
                8,
                true
        );
        GpuRuntimeDeviceProfile discrete = classifiedDevice(
                "opencl-dgpu",
                "NVIDIA RTX",
                "NVIDIA",
                GpuDeviceClassTarget.DGPU,
                48,
                false
        );
        Path fixtureRoot = methodTestFixtureRoot("method-test-rank-passed");
        Path cacheDirectory = Files.createTempDirectory("javatogpu-method-test-rank-cache");
        GpuKernelDescriptor descriptor = descriptorWithInputScaleOutput("demo.irgpu.properties");
        IrGpuArtifact artifact = methodTestArtifact("method-test-rank-passed");
        GpuRuntimeCompileOptions baseOptions = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL);

        try (java.net.URLClassLoader classLoader = new java.net.URLClassLoader(new java.net.URL[]{fixtureRoot.toUri().toURL()})) {
            ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);
            try {
                GpuRuntimeMethodTestInvocationMaterialization invocation = materializedInvocation(
                        descriptor,
                        artifact,
                        classLoader
                );
                recordProbeEvidence(cacheDirectory, descriptor, invocation, integrated, baseOptions, true);

                GpuRuntimeCompileOptions rankingOptions = baseOptions.withPersistentMethodTestProbeEvidenceRanking(cacheDirectory);
                assertEquals(GpuRuntimeMethodTestProbeMode.CACHE_ONLY, rankingOptions.backendOptions().methodTestProbeMode());
                GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                        new GpuRuntimeDevicePolicyContext(
                                descriptor,
                                rankingOptions,
                                List.of(integrated, discrete),
                                java.util.Optional.of(artifact)
                        )
                );
                GpuRuntimeDevicePolicyDecision decision = decision(
                        selection,
                        GpuRuntimeMethodTestGpuProbeEvidencePolicy.POLICY_ID
                );
                String integratedKey = GpuRuntimeDevicePolicyContext.deviceKey(integrated);
                String discreteKey = GpuRuntimeDevicePolicyContext.deviceKey(discrete);

                assertEquals("Intel Integrated", selection.selectedDevice().orElseThrow().deviceLabel());
                assertTrue(decision.scoreAdjustments().get(integratedKey) > 0);
                assertEquals("cache-only", decision.capabilityFacts().get("methodTestProbeEvidence.mode"));
                assertEquals("cache-only", decision.capabilityFacts().get("methodTestProbeEvidence.execution"));
                assertEquals("passed", decision.capabilityFacts().get(integratedKey + ".methodTestProbeEvidence.status"));
                assertEquals("missing", decision.capabilityFacts().get(discreteKey + ".methodTestProbeEvidence.status"));
            } finally {
                Thread.currentThread().setContextClassLoader(previousClassLoader);
            }
        }
    }

    @Test
    void invalidMethodTestProbeModeRejectsCompileOptions() {
        GpuRuntimeDeviceProfile device = classifiedDevice(
                "opencl-gpu",
                "NVIDIA RTX",
                "NVIDIA",
                GpuDeviceClassTarget.DGPU,
                48,
                false
        );
        GpuRuntimeCompileOptions options = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                List.of(),
                "off",
                GpuBackendCompileOptions.openCl(List.of(), Map.of(
                        GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY,
                        "run-before-first-invoke"
                ))
        );

        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                new GpuRuntimeDevicePolicyContext(descriptor(), options, List.of(device))
        );
        GpuRuntimeDevicePolicyDecision decision = decision(
                selection,
                GpuRuntimeMethodTestGpuProbeEvidencePolicy.POLICY_ID
        );

        assertFalse(selection.selectedDevice().isPresent());
        assertFalse(selection.compileOptionsValid());
        assertEquals("compile-options-rejected-by-device-policy", selection.firstBlocker());
        assertEquals("compile-options-invalid", decision.capabilityFacts().get("methodTestProbeEvidence.status"));
        assertEquals("runtime-method-test-probe-mode-invalid", decision.capabilityFacts().get("methodTestProbeEvidence.firstBlocker"));
        assertTrue(decision.compileOptionDiagnostics().get(0).contains("method-test probe mode is invalid"));
    }

    @Test
    void cachedMethodTestProbeFailureRejectsCandidate() throws Exception {
        GpuRuntimeDeviceProfile integrated = classifiedDevice(
                "opencl-igpu",
                "Intel Integrated",
                "Intel",
                GpuDeviceClassTarget.IGPU,
                8,
                true
        );
        GpuRuntimeDeviceProfile discrete = classifiedDevice(
                "opencl-dgpu",
                "NVIDIA RTX",
                "NVIDIA",
                GpuDeviceClassTarget.DGPU,
                48,
                false
        );
        Path fixtureRoot = methodTestFixtureRoot("method-test-rank-failed");
        Path cacheDirectory = Files.createTempDirectory("javatogpu-method-test-rank-cache-failed");
        GpuKernelDescriptor descriptor = descriptorWithInputScaleOutput("demo.irgpu.properties");
        IrGpuArtifact artifact = methodTestArtifact("method-test-rank-failed");
        GpuRuntimeCompileOptions baseOptions = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL);

        try (java.net.URLClassLoader classLoader = new java.net.URLClassLoader(new java.net.URL[]{fixtureRoot.toUri().toURL()})) {
            ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);
            try {
                GpuRuntimeMethodTestInvocationMaterialization invocation = materializedInvocation(
                        descriptor,
                        artifact,
                        classLoader
                );
                recordProbeEvidence(cacheDirectory, descriptor, invocation, discrete, baseOptions, false);

                GpuRuntimeCompileOptions rankingOptions = baseOptions.withPersistentMethodTestProbeEvidenceRanking(cacheDirectory);
                GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                        new GpuRuntimeDevicePolicyContext(
                                descriptor,
                                rankingOptions,
                                List.of(integrated, discrete),
                                java.util.Optional.of(artifact)
                        )
                );
                GpuRuntimeDevicePolicyDecision decision = decision(
                        selection,
                        GpuRuntimeMethodTestGpuProbeEvidencePolicy.POLICY_ID
                );
                String discreteKey = GpuRuntimeDevicePolicyContext.deviceKey(discrete);

                assertEquals("Intel Integrated", selection.selectedDevice().orElseThrow().deviceLabel());
                assertTrue(decision.rejectedDeviceKeys().contains(discreteKey));
                assertEquals("failed", decision.capabilityFacts().get(discreteKey + ".methodTestProbeEvidence.status"));
            } finally {
                Thread.currentThread().setContextClassLoader(previousClassLoader);
            }
        }
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

    private static GpuKernelDescriptor descriptorWithInputScaleOutput(String irGpuResource) {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel(__global const float* input, float scale, __global float* output) { output[0] = input[0] * scale; }",
                irGpuResource,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
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

    private static GpuRuntimeDevicePolicyDecision decision(
            GpuRuntimeDeviceSelection selection,
            String policyId
    ) {
        return selection.policyDecisions().stream()
                .filter(value -> value.policyId().equals(policyId))
                .findFirst()
                .orElseThrow();
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

    private static IrGpuArtifact methodTestArtifact(String testId) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "jtg_kernel", List.of(), List.of(), List.of()),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        ).withMethodTestVectors(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                testId,
                List.of("fixtures/" + testId + ".inputs.json"),
                List.of("fixtures/" + testId + ".outputs.json"),
                "abs=1e-5",
                List.of("selection"),
                true,
                "GPUTest"
        )));
    }

    private static Path methodTestFixtureRoot(String testId) throws Exception {
        Path root = Files.createTempDirectory("javatogpu-method-test-rank-fixtures");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/" + testId + ".inputs.json"), "{\"input\":[1.0,2.0],\"scale\":2.5}");
        Files.writeString(root.resolve("fixtures/" + testId + ".outputs.json"), "{\"output\":[2.5,5.0]}");
        return root;
    }

    private static GpuRuntimeMethodTestInvocationMaterialization materializedInvocation(
            GpuKernelDescriptor descriptor,
            IrGpuArtifact artifact,
            ClassLoader classLoader
    ) {
        GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(
                descriptor,
                artifact,
                GpuRuntimeLifecycleEventBus.empty()
        );
        GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                descriptor,
                plan,
                classLoader,
                GpuRuntimeLifecycleEventBus.empty()
        );
        return GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(
                        descriptor,
                        bindings,
                        GpuRuntimeLifecycleEventBus.empty()
                )
                .invocations()
                .get(0);
    }

    private static void recordProbeEvidence(
            Path cacheDirectory,
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestInvocationMaterialization invocation,
            GpuRuntimeDeviceProfile profile,
            GpuRuntimeCompileOptions compileOptions,
            boolean passed
    ) {
        GpuExecutionConfig executionConfig = GpuExecutionConfig.oneDimensional(2);
        GpuRuntimeMethodTestGpuProbeEvidenceKey evidenceKey = GpuRuntimeMethodTestGpuProbeEvidenceKey.from(
                descriptor,
                invocation,
                executionConfig,
                compileOptions,
                GpuRuntimeBackendReport.available(
                        profile.backendTarget(),
                        profile.backendName(),
                        profile.deviceLabel(),
                        null,
                        profile.apiVersionText(),
                        Set.of(),
                        profile.localMemoryBytes(),
                        profile.maxWorkGroupSize(),
                        "test candidate"
                ),
                profile
        );
        GpuRuntimeMethodTestReferenceComparison comparison = new GpuRuntimeMethodTestReferenceComparison(
                invocation.testId(),
                2,
                "output",
                "float[]",
                GpuKernelParameterAccess.READ_WRITE,
                true,
                passed,
                "java-float-array",
                2,
                passed ? List.of("2.5", "5.0") : List.of("2.5", "4.0"),
                "java-float-array",
                2,
                List.of("2.5", "5.0"),
                1.0e-5d,
                0.0d,
                "none",
                passed ? "none" : "fixture-gpu-probe-output-mismatch",
                passed ? "none" : "Mismatch at item 1"
        );
        GpuRuntimeMethodTestGpuProbeCache.persistent(cacheDirectory).record(new GpuRuntimeMethodTestGpuProbeExecution(
                invocation.testId(),
                evidenceKey,
                false,
                true,
                passed,
                executionConfig,
                List.of(comparison),
                List.of(),
                List.of()
        ));
    }
}
