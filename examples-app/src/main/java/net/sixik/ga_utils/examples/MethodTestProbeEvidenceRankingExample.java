package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodTestVectorMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestMode;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestFixtureValueBindingPlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestGpuProbeEvidencePolicy;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestGpuProbeOptions;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestGpuProbePlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestInvocationMaterializationPlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbeMode;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbeEvidenceSelection;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbeEvidenceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbeEvidenceWarmupCandidate;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbeEvidenceWarmupPlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbePlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbes;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestReferenceComparisonPlan;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Shows cache-only {@code @GPUTest} evidence ranking without requiring a real OpenCL device.
 */
public final class MethodTestProbeEvidenceRankingExample {

    private MethodTestProbeEvidenceRankingExample() {
    }

    public static void main(String[] args) throws Exception {
        Path cacheDirectory = args.length == 0
                ? Files.createTempDirectory("javatogpu-method-test-probe-evidence")
                : Path.of(args[0]);
        System.out.println(renderEvidenceRanking(cacheDirectory));
    }

    static String renderEvidenceRanking(Path cacheDirectory) {
        GpuKernelDescriptor descriptor = MethodTestProbeExample.descriptor();
        ClassLoader classLoader = MethodTestProbeEvidenceRankingExample.class.getClassLoader();

        GpuRuntimeDeviceProfile integrated = syntheticOpenClDevice(
                "opencl-igpu",
                "Intel Integrated",
                "Intel",
                GpuDeviceClassTarget.IGPU,
                8,
                true
        );
        GpuRuntimeDeviceProfile discrete = syntheticOpenClDevice(
                "opencl-dgpu",
                "NVIDIA RTX",
                "NVIDIA",
                GpuDeviceClassTarget.DGPU,
                48,
                false
        );
        GpuRuntimeCompileOptions baseOptions = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED);
        GpuRuntimeDeviceDiscoveryResult discovery = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL synthetic",
                List.of(integrated, discrete),
                null
        );

        GpuRuntimeMethodTestProbeEvidenceSelectionPlan selectionPlan = GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelect(
                descriptor,
                classLoader,
                List.of(GpuRuntimeMethodTestProbeEvidenceWarmupCandidate.borrowed(integrated, referenceBackend())),
                discovery,
                GpuRuntimeMethodTestGpuProbeOptions.persistentCached(cacheDirectory).withCompileOptions(baseOptions),
                baseOptions,
                Optional.empty(),
                null,
                null
        );
        GpuRuntimeMethodTestProbeEvidenceWarmupPlan warmup = selectionPlan.warmupPlan();
        GpuRuntimeMethodTestGpuProbePlan recordedProbe = warmup.candidateResults().get(0).gpuProbePlan();
        GpuRuntimeDeviceSelection selection = selectionPlan.deviceSelection();
        GpuRuntimeDevicePolicyDecision evidenceDecision = selection.policyDecisions().stream()
                .filter(decision -> decision.policyId().equals(GpuRuntimeMethodTestGpuProbeEvidencePolicy.POLICY_ID))
                .findFirst()
                .orElseThrow();
        StructFixtureExampleResult structFixture = structFixtureExample(cacheDirectory);

        String integratedKey = GpuRuntimeDevicePolicyContext.deviceKey(integrated);
        String discreteKey = GpuRuntimeDevicePolicyContext.deviceKey(discrete);
        return "Method test probe evidence ranking example" + System.lineSeparator()
                + "Cache directory: " + cacheDirectory.toAbsolutePath().normalize() + System.lineSeparator()
                + "Warm-up status: " + warmup.status() + System.lineSeparator()
                + "Recorded probe passed: " + recordedProbe.gpuProbePassed() + System.lineSeparator()
                + "Recorded probe cache hit: " + recordedProbe.executions().get(0).cacheHit() + System.lineSeparator()
                + "Evidence hash: " + recordedProbe.executions().get(0).evidenceKey().stableHash() + System.lineSeparator()
                + "Selection helper status: " + selectionPlan.status() + System.lineSeparator()
                + "Runtime method-test probe mode: " + GpuRuntimeMethodTestProbeMode.CACHE_ONLY.optionValue()
                + System.lineSeparator()
                + "Selected device: " + selection.selectedDevice()
                .map(device -> device.deviceLabel() + " (`" + GpuRuntimeDevicePolicyContext.deviceKey(device) + "`)")
                .orElse("none") + System.lineSeparator()
                + "Policy status: " + evidenceDecision.capabilityFacts().get("methodTestProbeEvidence.status") + System.lineSeparator()
                + "Integrated evidence: " + evidenceDecision.capabilityFacts().get(integratedKey + ".methodTestProbeEvidence.status")
                + " passed=" + evidenceDecision.capabilityFacts().get(integratedKey + ".methodTestProbeEvidence.passed.count")
                + " missing=" + evidenceDecision.capabilityFacts().get(integratedKey + ".methodTestProbeEvidence.missing.count")
                + System.lineSeparator()
                + "Discrete evidence: " + evidenceDecision.capabilityFacts().get(discreteKey + ".methodTestProbeEvidence.status")
                + " passed=" + evidenceDecision.capabilityFacts().get(discreteKey + ".methodTestProbeEvidence.passed.count")
                + " missing=" + evidenceDecision.capabilityFacts().get(discreteKey + ".methodTestProbeEvidence.missing.count")
                + System.lineSeparator()
                + System.lineSeparator()
                + "Struct fixture example" + System.lineSeparator()
                + "Struct binding ready: " + structFixture.bindings.bindingsReady() + System.lineSeparator()
                + "Struct argument kind: " + structFixture.materialization.invocations().get(0).arguments().get(0).argumentKind()
                + System.lineSeparator()
                + "Struct output kind: " + structFixture.materialization.invocations().get(0).arguments().get(2).expectedOutputKind()
                + System.lineSeparator()
                + "Struct reference passed: " + structFixture.referenceComparison.referenceComparisonPassed() + System.lineSeparator()
                + "Struct comparison preview: " + structFixture.referenceComparison.comparisons().get(0).actualNumericValues()
                + System.lineSeparator()
                + "Rule: passed cached selection-probe evidence boosts a candidate; missing evidence stays neutral."
                + System.lineSeparator();
    }

    private static GpuRuntimeBackend referenceBackend() {
        return MethodTestProbeEvidenceRankingExample::invokeReference;
    }

    private static void invokeReference(GpuKernelInvocation invocation) {
        MethodTestProbeExample.scaleKernelReference(
                (float[]) invocation.arguments()[0],
                (float[]) invocation.arguments()[1]
        );
    }

    private static StructFixtureExampleResult structFixtureExample(Path cacheDirectory) {
        try {
            Path fixtureRoot = cacheDirectory.resolve("struct-fixtures");
            Path fixtureDirectory = fixtureRoot.resolve("fixtures").resolve("method-test-probe");
            Files.createDirectories(fixtureDirectory);
            Files.writeString(fixtureDirectory.resolve("vec2-smoke.inputs.json"), """
                    {"input":[{"x":1.0,"y":2.0},{"x":3.0,"y":4.0}],"scale":2.0}
                    """);
            Files.writeString(fixtureDirectory.resolve("vec2-smoke.outputs.json"), """
                    {"output":[{"x":2.0,"y":4.0},{"x":6.0,"y":8.0}]}
                    """);

            GpuKernelDescriptor descriptor = structDescriptor();
            IrGpuArtifact artifact = structArtifact();
            try (URLClassLoader classLoader = new URLClassLoader(
                    new java.net.URL[]{fixtureRoot.toUri().toURL()},
                    MethodTestProbeEvidenceRankingExample.class.getClassLoader()
            )) {
                GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
                GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                        descriptor,
                        plan,
                        classLoader
                );
                GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                        GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings);
                GpuRuntimeMethodTestReferenceComparisonPlan referenceComparison = GpuRuntimeMethodTestProbes.compareWithReference(
                        materialization,
                        plan,
                        MethodTestProbeEvidenceRankingExample::invokeStructReference
                );
                return new StructFixtureExampleResult(bindings, materialization, referenceComparison);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare struct @GPUTest fixtures", exception);
        }
    }

    private static void invokeStructReference(Object[] arguments) {
        Vec2[] input = (Vec2[]) arguments[0];
        float scale = (Float) arguments[1];
        Vec2[] output = (Vec2[]) arguments[2];
        for (int index = 0; index < input.length; index++) {
            output[index].x = input[index].x * scale;
            output[index].y = input[index].y * scale;
        }
    }

    private static GpuKernelDescriptor structDescriptor() {
        return new GpuKernelDescriptor(
                "jtg_vec2_scale_kernel",
                "examples/method-test-probe/vec2-scale.cl",
                "__kernel void jtg_vec2_scale_kernel(__global const Vec2* input, float scale, __global Vec2* output) { }",
                "examples/method-test-probe/vec2-scale.irgpu.properties",
                List.of(
                        new GpuKernelParameterDescriptor("input", Vec2.class.getName() + "[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", Vec2.class.getName() + "[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static IrGpuArtifact structArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("vec2ScaleKernel", "jtg_vec2_scale_kernel", List.of(), List.of(), List.of()),
                List.of(IrGpuBackendOutput.openClSource("examples/method-test-probe/vec2-scale.cl")),
                "opencl",
                "off"
        ).withMethodTestVectors(List.of(new IrGpuMethodTestVectorMetadata(
                "vec2ScaleKernel",
                "jtg_vec2_scale_kernel",
                "vec2-struct-smoke",
                List.of("fixtures/method-test-probe/vec2-smoke.inputs.json"),
                List.of("fixtures/method-test-probe/vec2-smoke.outputs.json"),
                "abs=1e-5",
                List.of("selection", "struct", "smoke"),
                true,
                "GPUTest"
        )));
    }

    private record StructFixtureExampleResult(
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings,
            GpuRuntimeMethodTestInvocationMaterializationPlan materialization,
            GpuRuntimeMethodTestReferenceComparisonPlan referenceComparison
    ) {
    }

    private static GpuRuntimeDeviceProfile syntheticOpenClDevice(
            String deviceId,
            String label,
            String vendor,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            boolean unifiedMemory
    ) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL synthetic",
                deviceId,
                label,
                vendor,
                "example-driver",
                "OpenCL 3.0 Example",
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
}
