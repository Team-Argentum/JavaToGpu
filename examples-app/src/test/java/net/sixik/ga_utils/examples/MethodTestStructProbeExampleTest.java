package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestFixtureReadiness;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestFixtureValueBindingPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestInvocationMaterialization;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestInvocationMaterializationPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbePlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestProbes;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodTestReferenceComparisonPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodTestStructProbeExampleTest {

    @Test
    void rendersGeneratedStructMethodTestProbeReadiness() {
        String output = MethodTestStructProbeExample.renderProbeReadiness();

        assertTrue(output.contains("Method test struct probe example"));
        assertTrue(output.contains("Method test probe plan: metadata-ready"));
        assertTrue(output.contains("Method test fixture readiness: ready"));
        assertTrue(output.contains("vec2-scale-smoke"));
        assertTrue(output.contains("fixtures/method-test-struct-probe/vec2-scale-smoke.inputs.json"));
        assertTrue(output.contains("fixtures/method-test-struct-probe/vec2-scale-smoke.outputs.json"));
        assertTrue(output.contains("Fixture payload previews ready: 2/2"));
        assertTrue(output.contains("payload=json-object-v1 input[4]"));
        assertTrue(output.contains("payload=json-object-v1 output[4]"));
        assertTrue(output.contains("Method test fixture value bindings: ready"));
        assertTrue(output.contains("input:net.sixik.ga_utils.examples.Vec2[] ready=true struct-array[4]"));
        assertTrue(output.contains("scale:float ready=true numeric-scalar[1]"));
        assertTrue(output.contains("output:net.sixik.ga_utils.examples.Vec2[] ready=true struct-array[4]"));
        assertTrue(output.contains("Method test invocation materialization plan: ready"));
        assertTrue(output.contains("java-struct-array[4]"));
        assertTrue(output.contains("zero-filled-struct-array[4] expected=java-struct-array[4]"));
        assertTrue(output.contains("Method test reference comparison: passed"));
        assertTrue(output.contains("actual=java-struct-array[8] expected=java-struct-array[8]"));
    }

    @Test
    void generatedDescriptorCarriesStructFixturesAndReferenceComparison() {
        ClassLoader classLoader = MethodTestStructProbeExample.class.getClassLoader();
        GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(
                MethodTestStructProbeExample.descriptor(),
                classLoader
        );
        GpuRuntimeMethodTestFixtureReadiness readiness = GpuRuntimeMethodTestProbes.fixtureReadiness(plan, classLoader);
        GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                MethodTestStructProbeExample.descriptor(),
                plan,
                classLoader
        );
        GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(MethodTestStructProbeExample.descriptor(), bindings);
        GpuRuntimeMethodTestReferenceComparisonPlan referenceComparison = GpuRuntimeMethodTestProbes.compareWithReference(
                materialization,
                plan,
                invocationArguments -> MethodTestStructProbeExample.scaleStructKernelReference(
                        (Vec2[]) invocationArguments[0],
                        (Float) invocationArguments[1],
                        (Vec2[]) invocationArguments[2]
                )
        );

        assertTrue(plan.metadataReady());
        assertEquals(1, plan.testVectors().size());
        assertEquals("vec2-scale-smoke", plan.testVectors().get(0).testId());
        assertEquals("abs=1e-9", plan.testVectors().get(0).tolerance());
        assertTrue(plan.hasSelectionProbes());
        assertTrue(readiness.fixtureResourcesReady());
        assertTrue(readiness.selectionProbeResourcesReady());
        assertTrue(readiness.fixturePayloadPreviewsReady());
        assertEquals(2, readiness.availableResourceCount());
        assertEquals(0, readiness.missingResourceCount());
        assertTrue(bindings.bindingsReady());
        assertEquals(3, bindings.bindings().size());
        assertEquals("struct-array", bindings.bindings().get(0).valueKind());
        assertEquals(4, bindings.bindings().get(0).itemCount());
        assertEquals(List.of(
                "[0].x=1.0",
                "[0].y=2.0",
                "[1].x=3.0",
                "[1].y=4.0",
                "[2].x=-1.5",
                "[2].y=0.25",
                "[3].x=8.0",
                "[3].y=-2.0"
        ), bindings.bindings().get(0).numericValues());

        assertTrue(materialization.materializationReady());
        assertEquals(1, materialization.invocations().size());
        GpuRuntimeMethodTestInvocationMaterialization invocation = materialization.invocations().get(0);
        Object[] invocationArguments = invocation.invocationArguments();
        assertEquals(3, invocationArguments.length);
        Vec2[] input = (Vec2[]) invocationArguments[0];
        Vec2[] output = (Vec2[]) invocationArguments[2];
        assertEquals(1.0, input[0].x, 0.0);
        assertEquals(-2.0, input[3].y, 0.0);
        assertEquals(0.0, output[0].x, 0.0);
        assertEquals("java-struct-array", invocation.arguments().get(0).argumentKind());
        assertEquals("zero-filled-struct-array", invocation.arguments().get(2).argumentKind());
        assertEquals("java-struct-array", invocation.arguments().get(2).expectedOutputKind());

        assertTrue(referenceComparison.referenceComparisonReady());
        assertTrue(referenceComparison.referenceComparisonPassed());
        assertEquals("passed", referenceComparison.status());
        assertEquals(1, referenceComparison.comparisonPassedCount());
        assertEquals("java-struct-array", referenceComparison.comparisons().get(0).actualKind());
        assertEquals("[0].x=2.0", referenceComparison.comparisons().get(0).actualNumericValues().get(0));
        assertEquals("[3].y=-4.0", referenceComparison.comparisons().get(0).expectedNumericValues().get(7));
    }
}
