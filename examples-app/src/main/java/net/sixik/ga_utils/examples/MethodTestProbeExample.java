package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUTest;
import net.sixik.ga_utils.javatogpu.runtime.GpuGeneratedLauncherInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestInvocationMaterializationPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestFixtureReadiness;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestFixtureValueBindingPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbePlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbes;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestReferenceComparisonPlan;

/**
 * Shows how to inspect {@link GPUTest} metadata and run a portable CPU-reference preflight.
 */
public final class MethodTestProbeExample {

    private MethodTestProbeExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderProbeReadiness());
    }

    static String renderProbeReadiness() {
        GpuKernelDescriptor descriptor = descriptor();
        ClassLoader classLoader = MethodTestProbeExample.class.getClassLoader();
        GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, classLoader);
        GpuRuntimeMethodTestFixtureReadiness readiness = GpuRuntimeMethodTestProbes.fixtureReadiness(plan, classLoader);
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
                invocationArguments -> scaleKernelReference(
                        (float[]) invocationArguments[0],
                        (float[]) invocationArguments[1]
                )
        );

        return "Method test probe example" + System.lineSeparator()
                + plan.toMarkdown()
                + readiness.toMarkdown()
                + bindings.toMarkdown()
                + materialization.toMarkdown()
                + referenceComparison.toMarkdown();
    }

    static GpuKernelDescriptor descriptor() {
        return GpuGeneratedLauncherInvoker.descriptor(MethodTestProbeExample.class, "scaleKernel");
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    @GPUTest(
            id = "scale-smoke",
            inputs = {"fixtures/method-test-probe/scale-smoke.inputs.json"},
            expectedOutputs = {"fixtures/method-test-probe/scale-smoke.outputs.json"},
            tolerance = "abs=1e-5",
            tags = {"selection", "smoke"}
    )
    public static void scaleKernel(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id] = input[id] * 2.0f;
    }

    static void scaleKernelReference(float[] input, float[] output) {
        for (int index = 0; index < input.length; index++) {
            output[index] = input[index] * 2.0f;
        }
    }
}
