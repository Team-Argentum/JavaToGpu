package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUTest;
import net.sixik.ga_utils.javatogpu.runtime.GpuGeneratedLauncherInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestFixtureReadiness;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestFixtureValueBindingPlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestInvocationMaterializationPlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbePlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbes;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestReferenceComparisonPlan;

/**
 * Shows how to test a {@link GPUGlobal} {@code @GPUStruct[]} method with {@link GPUTest} fixtures.
 */
public final class MethodTestStructProbeExample {

    private MethodTestStructProbeExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderProbeReadiness());
    }

    static String renderProbeReadiness() {
        GpuKernelDescriptor descriptor = descriptor();
        ClassLoader classLoader = MethodTestStructProbeExample.class.getClassLoader();
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
                invocationArguments -> scaleStructKernelReference(
                        (Vec2[]) invocationArguments[0],
                        (Float) invocationArguments[1],
                        (Vec2[]) invocationArguments[2]
                )
        );

        return "Method test struct probe example" + System.lineSeparator()
                + plan.toMarkdown()
                + readiness.toMarkdown()
                + bindings.toMarkdown()
                + materialization.toMarkdown()
                + referenceComparison.toMarkdown();
    }

    static GpuKernelDescriptor descriptor() {
        return GpuGeneratedLauncherInvoker.descriptor(MethodTestStructProbeExample.class, "scaleStructKernel");
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    @GPUTest(
            id = "vec2-scale-smoke",
            inputs = {"fixtures/method-test-struct-probe/vec2-scale-smoke.inputs.json"},
            expectedOutputs = {"fixtures/method-test-struct-probe/vec2-scale-smoke.outputs.json"},
            tolerance = "abs=1e-9",
            tags = {"selection", "struct", "smoke"}
    )
    public static void scaleStructKernel(
            @GPUGlobal Vec2[] input,
            float scale,
            @GPUGlobal Vec2[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id].x = input[id].x * scale;
        output[id].y = input[id].y * scale;
    }

    static void scaleStructKernelReference(Vec2[] input, float scale, Vec2[] output) {
        for (int index = 0; index < input.length; index++) {
            output[index].x = input[index].x * scale;
            output[index].y = input[index].y * scale;
        }
    }
}
