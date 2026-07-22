package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestFixtureReadiness;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestFixtureValueBindingPlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbePlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestProbes;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestReferenceComparisonPlan;
import net.sixik.ga_utils.javatogpu.runtime.methodtest.GpuRuntimeMethodTestInvocationMaterializationPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodTestProbeExampleTest {

    @Test
    void rendersGeneratedMethodTestProbeReadiness() {
        String output = MethodTestProbeExample.renderProbeReadiness();

        assertTrue(output.contains("Method test probe example"));
        assertTrue(output.contains("Method test probe plan: metadata-ready"));
        assertTrue(output.contains("Method test fixture readiness: ready"));
        assertTrue(output.contains("scale-smoke"));
        assertTrue(output.contains("fixtures/method-test-probe/scale-smoke.inputs.json"));
        assertTrue(output.contains("fixtures/method-test-probe/scale-smoke.outputs.json"));
        assertTrue(output.contains("sizeBytes="));
        assertTrue(output.contains("sha256="));
        assertTrue(output.contains("Fixture payload previews ready: 2/2"));
        assertTrue(output.contains("payload=json-object-v1 input[4]"));
        assertTrue(output.contains("payload=json-object-v1 output[4]"));
        assertTrue(output.contains("Method test fixture value bindings: ready"));
        assertTrue(output.contains("input:float[] ready=true numeric-array[4]"));
        assertTrue(output.contains("output:float[] ready=true numeric-array[4]"));
        assertTrue(output.contains("Method test invocation materialization plan: ready"));
        assertTrue(output.contains("input:float[] ready=true java-float-array[4]"));
        assertTrue(output.contains("output:float[] ready=true zero-filled-float-array[4] expected=java-float-array[4]"));
        assertTrue(output.contains("Method test reference comparison: passed"));
        assertTrue(output.contains("output:float[] ready=true passed=true actual=java-float-array[4] expected=java-float-array[4]"));
    }

    @Test
    void generatedDescriptorCarriesIrGpuTestVectorsAndReadyFixtures() {
        ClassLoader classLoader = MethodTestProbeExample.class.getClassLoader();
        GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(
                MethodTestProbeExample.descriptor(),
                classLoader
        );
        GpuRuntimeMethodTestFixtureReadiness readiness = GpuRuntimeMethodTestProbes.fixtureReadiness(plan, classLoader);
        GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                MethodTestProbeExample.descriptor(),
                plan,
                classLoader
        );
        GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(MethodTestProbeExample.descriptor(), bindings);
        GpuRuntimeMethodTestReferenceComparisonPlan referenceComparison = GpuRuntimeMethodTestProbes.compareWithReference(
                materialization,
                plan,
                invocationArguments -> MethodTestProbeExample.scaleKernelReference(
                        (float[]) invocationArguments[0],
                        (float[]) invocationArguments[1]
                )
        );

        assertTrue(plan.metadataReady());
        assertEquals(1, plan.testVectors().size());
        assertEquals("scale-smoke", plan.testVectors().get(0).testId());
        assertEquals("abs=1e-5", plan.testVectors().get(0).tolerance());
        assertTrue(plan.hasSelectionProbes());
        assertTrue(readiness.fixtureResourcesReady());
        assertTrue(readiness.selectionProbeResourcesReady());
        assertTrue(readiness.fixturePayloadPreviewsReady());
        assertEquals(2, readiness.availableResourceCount());
        assertEquals(0, readiness.missingResourceCount());
        assertEquals(2, readiness.payloadPreviewReadyCount());
        assertEquals(0, readiness.payloadPreviewBlockedCount());
        assertTrue(readiness.resources().stream().allMatch(resource -> resource.sizeBytes() > 0));
        assertTrue(readiness.resources().stream().allMatch(resource -> resource.sha256().length() == 64));
        assertTrue(readiness.resources().stream().allMatch(resource -> resource.payloadPreview().schemaReady()));
        assertTrue(bindings.bindingsReady());
        assertEquals(2, bindings.bindings().size());
        assertEquals("input", bindings.bindings().get(0).parameterName());
        assertEquals("output", bindings.bindings().get(1).parameterName());
        assertEquals(4, bindings.bindings().get(0).itemCount());
        assertEquals("1.0", bindings.bindings().get(0).numericValues().get(0));
        assertTrue(materialization.materializationReady());
        assertEquals(1, materialization.invocations().size());
        Object[] invocationArguments = materialization.invocations().get(0).invocationArguments();
        assertEquals(2, invocationArguments.length);
        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, (float[]) invocationArguments[0], 0.0001f);
        assertArrayEquals(new float[]{0.0f, 0.0f, 0.0f, 0.0f}, (float[]) invocationArguments[1], 0.0001f);
        assertArrayEquals(
                new float[]{2.0f, 4.0f, 6.0f, 8.0f},
                (float[]) materialization.invocations().get(0).arguments().get(1).expectedOutputValue(),
                0.0001f
        );
        assertTrue(referenceComparison.referenceComparisonReady());
        assertTrue(referenceComparison.referenceComparisonPassed());
        assertEquals("passed", referenceComparison.status());
        assertEquals(1, referenceComparison.comparisonPassedCount());
        assertEquals("none", referenceComparison.firstFailure());
    }
}
