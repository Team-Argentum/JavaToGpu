package com.sixik.gpu.test;

import com.sixik.gpu.ir.interpreter.samples.LocalKernels;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Образцы ядер с барьером и local-памятью ({@link LocalKernels}): на JVM их не исполнить, поэтому у каждого есть
 * {@code expected}, посчитанное на хосте обычным Java-кодом, а размер запуска задан явно (кратный группе).
 */
final class LocalSamples {

    private LocalSamples() {
    }

    /** Ключ - {@code LocalKernels.<ядро>} (как {@code SampleKernels.Kernel.label()}), значение - образец. */
    static Map<String, KernelSample> all() {
        Map<String, KernelSample> m = new LinkedHashMap<>();
        m.put("LocalKernels.reduceSum", reduceSum());
        m.put("LocalKernels.transpose", transpose());
        m.put("LocalKernels.scan", scan());
        m.put("LocalKernels.histogram", histogram());
        m.put("LocalKernels.counters", counters());
        m.put("LocalKernels.wide", wide());
        return m;
    }

    /** Ядра, которые на устройстве сверяются полностью (в {@code wide} порядок обмена не определён). */
    static boolean comparableOnDevice(String label) {
        return !label.equals("LocalKernels.wide");
    }

    private static int[] ints(int n, long seed) {
        Random r = new Random(seed);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.nextInt();
        }
        return a;
    }

    static KernelSample reduceSum() {
        int groups = 5;
        int[] in = ints(groups * LocalKernels.REDUCE_GROUP, 1);
        int[] sums = new int[groups];
        for (int i = 0; i < in.length; i++) {
            sums[i / LocalKernels.REDUCE_GROUP] += in[i];
        }
        return KernelSample.named("reduceSum").args(() -> new Object[]{in.clone(), new int[groups]})
                .expected(null, sums).build();
    }

    static KernelSample transpose() {
        int w = 16;
        int h = 24;
        float[] in = new float[w * h];
        for (int i = 0; i < in.length; i++) {
            in[i] = i * 0.5f - 7f;
        }
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[x * h + y] = in[y * w + x];
            }
        }
        return KernelSample.named("transpose").args(() -> new Object[]{new float[w * h], in.clone(), w, h})
                .range(KernelSample.Range.of2D(w, h, LocalKernels.TILE, LocalKernels.TILE))
                .expected(out, null, null, null).build();
    }

    static KernelSample scan() {
        int groups = 3;
        int[] in = ints(groups * LocalKernels.SCAN_GROUP, 3);
        int[] out = new int[in.length];
        for (int g = 0; g < groups; g++) {
            int acc = 0;
            for (int i = 0; i < LocalKernels.SCAN_GROUP; i++) {
                acc += in[g * LocalKernels.SCAN_GROUP + i];
                out[g * LocalKernels.SCAN_GROUP + i] = acc;
            }
        }
        return KernelSample.named("scan").args(() -> new Object[]{in.clone(), new int[in.length]})
                .expected(null, out).build();
    }

    static KernelSample histogram() {
        int[] data = ints(4 * LocalKernels.HIST_GROUP, 5);
        int[] bins = new int[LocalKernels.BINS];
        for (int v : data) {
            bins[v & 15]++;
        }
        return KernelSample.named("histogram").args(() -> new Object[]{data.clone(), new int[LocalKernels.BINS]})
                .expected(null, bins).build();
    }

    static KernelSample counters() {
        int[] data = ints(100, 7);
        int sum = 0;
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        int or = 0;
        int xor = 0;
        int and = -1;
        for (int v : data) {
            sum++;
            max = Math.max(max, v);
            min = Math.min(min, v);
            or |= v;
            xor ^= v;
            and &= v;
        }
        int[] expected = {sum, max, min, or, xor, and, -data.length};
        // olds - прежние значения убывающего счётчика: их порядок зависит от порядка work-item-ов
        return KernelSample.named("counters")
                .args(() -> new Object[]{data.clone(), new int[]{0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0, -1, 0},
                        new int[data.length]})
                .expected(null, expected, null).ignoring(2).build();
    }

    static KernelSample wide() {
        long[] data = new long[50];
        Random r = new Random(9);
        long sum = 0;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < data.length; i++) {
            data[i] = r.nextLong();
            sum += data[i];
            max = Math.max(max, data[i]);
        }
        // sums[2] - последнее записанное: порядок обмена на устройстве не определён
        return KernelSample.named("wide").args(() -> new Object[]{data.clone(), new long[]{0, Long.MIN_VALUE, 0}})
                .expected(null, new long[]{sum, max, data[data.length - 1]})
                .without(KernelSample.Leg.DEVICE).build();
    }
}
