package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.interpreter.samples.LocalKernels;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Критерий M10 в интерпретаторе: ядра {@link LocalKernels}, собранные настоящим фронтендом из Java, дают результат,
 * посчитанный на хосте обычным кодом (на JVM эти ядра не исполнить: барьер требует группы). Сверка с устройством - в
 * {@code codegen-opencl} (DeviceTest).
 */
class LocalMemoryTest {

    private static IrModule module(String kernel) {
        return SampleKernels.kernel(LocalKernels.class, kernel).module();
    }

    private static Result<Interpreter.Execution> run(String kernel, NdRange range, Object... args) {
        Result<Interpreter.Execution> r = Interpreter.run(module(kernel), range, args);
        assertTrue(r.isOk(), r.diagnostics().toString());
        return r;
    }

    @Test
    void sampleKernelsHaveNoBarrierWarnings() {
        for (String k : new String[]{"reduceSum", "transpose", "scan", "histogram", "counters", "wide"}) {
            Validator.Report report = Validator.validate(module(k));
            assertTrue(report.diagnostics().isEmpty(), k + ": " + report.diagnostics());
        }
        assertTrue(Validator.validate(module("reduceSum")).functions()[1].usesGroupSync(), "a kernel with barrier is marked uses_group_sync");
        assertTrue(Validator.validate(module("counters")).functions()[0].usesGroupSync() == false);
    }

    @Test
    void treeReductionSumsEveryGroup() {
        int groups = 5;
        int[] in = new int[groups * LocalKernels.REDUCE_GROUP];
        Random rnd = new Random(1);
        for (int i = 0; i < in.length; i++) {
            in[i] = rnd.nextInt();
        }
        int[] expected = new int[groups];
        for (int i = 0; i < in.length; i++) {
            expected[i / LocalKernels.REDUCE_GROUP] += in[i];
        }
        int[] partial = new int[groups];
        run("reduceSum", NdRange.of1D(in.length), in, partial);
        assertArrayEquals(expected, partial);
    }

    @Test
    void transposeThroughATileMovesEveryElement() {
        int w = 24;
        int h = 16;
        float[] in = new float[w * h];
        for (int i = 0; i < in.length; i++) {
            in[i] = i * 0.5f - 7;
        }
        float[] out = new float[w * h];
        run("transpose", NdRange.of2D(w, h), out, in, w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                assertEquals(in[y * w + x], out[x * h + y], "x=" + x + " y=" + y);
            }
        }
    }

    @Test
    void inclusivePrefixSumPerGroup() {
        int[] in = new int[64];
        for (int i = 0; i < in.length; i++) {
            in[i] = (i * 37) % 11 - 3;
        }
        int[] expected = new int[in.length];
        for (int g = 0; g < in.length / LocalKernels.SCAN_GROUP; g++) {
            int acc = 0;
            for (int k = 0; k < LocalKernels.SCAN_GROUP; k++) {
                acc += in[g * LocalKernels.SCAN_GROUP + k];
                expected[g * LocalKernels.SCAN_GROUP + k] = acc;
            }
        }
        int[] out = new int[in.length];
        run("scan", NdRange.of1D(in.length), in, out);
        assertArrayEquals(expected, out);
    }

    @Test
    void histogramMergesGroupBinsIntoGlobalWithAtomics() {
        int n = 128;
        int[] data = new int[n];
        Random rnd = new Random(5);
        for (int i = 0; i < n; i++) {
            data[i] = rnd.nextInt(1000);
        }
        int[] bins = new int[LocalKernels.BINS];
        int[] expected = new int[LocalKernels.BINS];
        for (int i = 0; i < bins.length; i++) {
            bins[i] = 100 + i; // слияние прибавляется к уже лежащему значению
            expected[i] = bins[i];
        }
        for (int v : data) {
            expected[v & 15]++;
        }
        run("histogram", NdRange.of1D(n), data, bins);
        assertArrayEquals(expected, bins);
    }

    @Test
    void globalAtomicCountersAndOldValues() {
        int n = 40;
        int[] data = new int[n];
        Random rnd = new Random(9);
        int or = 0;
        int xor = 0;
        int and = -1;
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            data[i] = rnd.nextInt() | 0x0F0F0000;
            or |= data[i];
            xor ^= data[i];
            and &= data[i];
            max = Math.max(max, data[i]);
            min = Math.min(min, data[i]);
        }
        int[] counters = {0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0, -1, 0};
        int[] olds = new int[n];
        run("counters", NdRange.of1D(n), data, counters, olds);
        assertArrayEquals(new int[]{n, max, min, or, xor, and, -n}, counters);
        int[] expectedOlds = new int[n];
        for (int i = 0; i < n; i++) {
            expectedOlds[i] = -i; // work-item-ы идут по возрастанию номера
        }
        assertArrayEquals(expectedOlds, olds);
    }

    @Test
    void sixtyFourBitAtomics() {
        long[] data = {5, -3, Long.MAX_VALUE - 10, 7, 1L << 40, -1};
        long[] sums = {0, Long.MIN_VALUE, 0};
        run("wide", NdRange.of1D(data.length), data, sums);
        long sum = 0;
        long max = Long.MIN_VALUE;
        for (long v : data) {
            sum += v;
            max = Math.max(max, v);
        }
        assertArrayEquals(new long[]{sum, max, data[data.length - 1]}, sums);
    }

    @Test
    void groupedKernelsReportStepsOfAllWorkItems() {
        int[] in = new int[64];
        long steps = run("reduceSum", NdRange.of1D(64), in, new int[1]).value().steps();
        assertTrue(steps > 64 * 10, "steps of all 64 work-items of the group are summed: " + steps);
    }

    @Test
    void launchWithoutWholeGroupsIsRejected() {
        Result<Interpreter.Execution> r = Interpreter.run(module("reduceSum"), NdRange.of1D(100), new int[100], new int[2]);
        assertEquals("IN-L01", r.diagnostics().get(0).code());
    }
}
