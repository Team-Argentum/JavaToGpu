package com.sixik.gpu.api;

import com.sixik.gpu.api.types.Float2;
import com.sixik.gpu.api.types.FloatPtr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Образец из концепта не только компилируется, но и работает на JVM: те же методы под {@link WorkItem#run} —
 * это и есть JVM-fallback и эталон тестов.
 */
class ConceptSampleTest {

    @Test
    void computeSineRunsOnJvmOverWorkItems() {
        int n = 1000;
        float[] in = new float[n];
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            in[i] = i * 0.01f;
        }
        var ctx = WorkItemContext.of1D(n, 10);
        WorkItem.run(ctx, () -> {
            for (int i = 0; i < n; i++) {
                ctx.setGlobalId(i, 0, 0);
                ConceptSample.computeSine(in, out);
            }
        });
        for (int i = 0; i < n; i++) {
            float want = (float) Math.sin(in[i]) + 2.0f;
            assertTrue(Math.abs(out[i] - want) <= Math.ulp(want), "i=" + i + " got=" + out[i] + " want=" + want);
        }
    }

    @Test
    void processStepsDoWhileLoop() {
        int[] in = {-5, 0, 3, 10};
        int[] out = new int[in.length];
        var ctx = WorkItemContext.of1D(in.length, 1);
        WorkItem.run(ctx, () -> {
            for (int i = 0; i < in.length; i++) {
                ctx.setGlobalId(i, 0, 0);
                ConceptSample.processSteps(in, out, 100);
            }
        });
        for (int i = 0; i < in.length; i++) {
            int value = in[i];
            for (int step = 0; step < 2; step++) {
                value = Math.max(value, step) + 100;
            }
            assertEquals(value, out[i], "i=" + i);
        }
    }

    @Test
    void deviceFunctionsAndLibrary() {
        assertEquals(15f, ConceptSample.lerp(10f, 20f, 0.5f));
        FloatPtr p = new FloatPtr(50f);
        ConceptSample.clamp(p);
        assertEquals(32f, p.value, "reference value is bounded");
        p.value = 5f;
        ConceptSample.clamp(p);
        assertEquals(5f, p.value);
        assertEquals(new Float2(4f, 6f), ConceptSample.MathLibrary.add(new Float2(1f, 2f), new Float2(3f, 4f)));
        assertEquals(32f, ConceptSample.MathLibrary.VALUE);
        assertEquals(3f, new ConceptSample.GamePos(1f, 2f, 3f).z);
    }
}
