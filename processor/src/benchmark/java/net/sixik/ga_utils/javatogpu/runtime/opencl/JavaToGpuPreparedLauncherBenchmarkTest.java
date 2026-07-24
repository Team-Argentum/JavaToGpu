package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher;
import net.sixik.ga_utils.javatogpu.api.GpuScope;
import net.sixik.ga_utils.javatogpu.api.JavaToGpu;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFeature;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.security.CodeSource;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real OpenCL prepared-launcher benchmark.
 *
 * <p>This is intentionally kept in the benchmark source set rather than the regular unit-test source set because it
 * needs an OpenCL device with fp64 support. The JUnit entry point skips when that runtime is unavailable; the main
 * entry point is useful for explicit local measurements.</p>
 */
public final class JavaToGpuPreparedLauncherBenchmarkTest {
    private static final String POINTS_PROPERTY = "javatogpu.preparedLauncherBenchmark.points";
    private static final String WARMUP_PROPERTY = "javatogpu.preparedLauncherBenchmark.warmup";
    private static final String ITERATIONS_PROPERTY = "javatogpu.preparedLauncherBenchmark.iterations";
    private static final int[] DEFAULT_POINTS = {128, 512, 2048, 8192, 32768};
    private static final double SCALE = 1.75D;
    private static final double BIAS = -32.0D;
    private static final double LIMIT = 4096.0D;
    private static final double EPSILON = 0.0D;

    @Test
    void benchmarksPreparedLauncherHotPathOnAvailableOpenClDevice() {
        assumeOpenClFp64Available();
        Result[] results = runAll(Options.fromSystemProperties());
        StaticPayloadResult[] payloadResults = runStaticPayloadAll(Options.fromSystemProperties());

        assertTrue(results.length > 0);
        for (Result result : results) {
            assertTrue(result.prepareNanos() > 0L);
            assertTrue(result.coldNanos() > 0L);
            assertTrue(result.warmFirstNanos() > 0L);
            assertTrue(result.avgNanos() > 0.0D);
            assertTrue(result.maxAbsError() <= EPSILON, result::toCsv);
        }
        assertTrue(payloadResults.length > 0);
        for (StaticPayloadResult result : payloadResults) {
            assertTrue(result.normalAvgNanos() > 0.0D);
            assertTrue(result.staticAvgNanos() > 0.0D);
            assertTrue(result.maxAbsError() <= EPSILON, result::toCsv);
        }
    }

    public static void main(String[] args) {
        Options options = Options.parse(args);
        runAll(options);
        runStaticPayloadAll(options);
    }

    private static Result[] runAll(Options options) {
        System.out.println("JavaToGpu API: " + apiLocation());
        System.out.println("points=" + Arrays.toString(options.points)
                + ", warmup=" + options.warmup
                + ", iterations=" + options.iterations);
        System.out.println("points,prepareMs,coldMs,warmFirstMs,avgMs,minMs,p50Ms,p95Ms,maxMs,cpuMs,maxAbsError,firstGpu,firstCpu");

        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .withoutBackendDevicePreflight();
        Result[] results = new Result[options.points.length];

        try {
            try (GpuScope ignored = JavaToGpu.useOpenClSharedCache()) {
                for (int index = 0; index < options.points.length; index++) {
                    Result result = runOne(options.points[index], options.warmup, options.iterations, compileOptions);
                    results[index] = result;
                    System.out.println(result.toCsv());
                }
            }
        } finally {
            JavaToGpu.shutdownOpenClSharedCache();
        }

        return results;
    }

    private static StaticPayloadResult[] runStaticPayloadAll(Options options) {
        System.out.println();
        System.out.println("Static payload + transfer policy prepared benchmark");
        System.out.println("points=" + Arrays.toString(options.points)
                + ", warmup=" + options.warmup
                + ", iterations=" + options.iterations);
        System.out.println("points,cpuMs,normalWarmFirstMs,normalAvgMs,normalMinMs,normalP50Ms,normalP95Ms,normalMaxMs,"
                + "staticWarmFirstMs,staticAvgMs,staticMinMs,staticP50Ms,staticP95Ms,staticMaxMs,speedup,maxAbsError,firstGpu,firstCpu");

        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions
                .defaults(GpuBackendTarget.OPENCL)
                .withoutBackendDevicePreflight();
        StaticPayloadResult[] results = new StaticPayloadResult[options.points.length];

        try {
            try (GpuScope ignored = JavaToGpu.useOpenClSharedCache()) {
                for (int index = 0; index < options.points.length; index++) {
                    StaticPayloadResult result = runStaticPayloadOne(
                            options.points[index],
                            options.warmup,
                            options.iterations,
                            compileOptions
                    );
                    results[index] = result;
                    System.out.println(result.toCsv());
                }
            }
        } finally {
            JavaToGpu.shutdownOpenClSharedCache();
        }

        return results;
    }

    private static Result runOne(
            int points,
            int warmup,
            int iterations,
            GpuRuntimeCompileOptions compileOptions
    ) {
        double[] input = new double[points];
        double[] output = new double[points];
        double[] expected = new double[points];
        fillInput(input);

        long cpuStart = System.nanoTime();
        computeCpu(input, expected);
        long cpuNanos = System.nanoTime() - cpuStart;

        long prepareStart = System.nanoTime();
        try (GpuPreparedLauncher launcher = JavaToGpu.prepareWithConfigAndCompileOptions(
                TestKernel.class,
                "transform",
                JavaToGpu.launch1D(points),
                compileOptions,
                input,
                SCALE,
                BIAS,
                LIMIT,
                output
        )) {
            long prepareNanos = System.nanoTime() - prepareStart;

            long coldStart = System.nanoTime();
            launcher.invoke(input, SCALE, BIAS, LIMIT, output);
            long coldNanos = System.nanoTime() - coldStart;
            assertParity(points, "cold", output, expected);

            long warmFirstStart = System.nanoTime();
            launcher.invoke(input, SCALE, BIAS, LIMIT, output);
            long warmFirstNanos = System.nanoTime() - warmFirstStart;
            assertParity(points, "warm-first", output, expected);

            for (int i = 0; i < warmup; i++) {
                launcher.invoke(input, SCALE, BIAS, LIMIT, output);
            }

            long[] samples = new long[iterations];
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                launcher.invoke(input, SCALE, BIAS, LIMIT, output);
                samples[i] = System.nanoTime() - start;
            }
            assertParity(points, "measured", output, expected);

            return Result.from(points, prepareNanos, coldNanos, warmFirstNanos, samples, cpuNanos, output, expected);
        }
    }

    private static StaticPayloadResult runStaticPayloadOne(
            int points,
            int warmup,
            int iterations,
            GpuRuntimeCompileOptions compileOptions
    ) {
        PayloadData data = PayloadData.create(points);

        long cpuStart = System.nanoTime();
        computeStaticPayloadCpu(data);
        long cpuNanos = System.nanoTime() - cpuStart;

        TimedSamples normalSamples;
        long normalWarmFirstNanos;
        try (GpuPreparedLauncher normal = prepareStaticPayloadLauncher(data, compileOptions)) {
            invokeStaticPayloadNormal(normal, data);
            assertParity(points, "static-payload normal cold", data.output, data.expected);

            long warmFirstStart = System.nanoTime();
            invokeStaticPayloadNormal(normal, data);
            normalWarmFirstNanos = System.nanoTime() - warmFirstStart;
            assertParity(points, "static-payload normal warm-first", data.output, data.expected);

            for (int i = 0; i < warmup; i++) {
                invokeStaticPayloadNormal(normal, data);
            }
            normalSamples = measure(iterations, () -> invokeStaticPayloadNormal(normal, data));
            assertParity(points, "static-payload normal measured", data.output, data.expected);
        }

        TimedSamples staticSamples;
        long staticWarmFirstNanos;
        try (GpuPreparedLauncher parent = prepareStaticPayloadLauncher(data, compileOptions);
             GpuPreparedLauncher launcher = parent.withStaticArgumentNames(
                     "opcodes",
                     "arg0",
                     "arg1",
                     "arg2",
                     "flags",
                     "selector",
                     "value0",
                     "value1",
                     "value2",
                     "weights",
                     "thresholds",
                     "biasTable",
                     "gainTable",
                     "offsetTable"
             ).withoutHostUploadArgumentNames("output", "scratch")
                     .withoutHostReadbackArgumentNames("scratch")) {
            invokeStaticPayloadStatic(launcher, data);
            assertParity(points, "static-payload static cold", data.output, data.expected);

            long warmFirstStart = System.nanoTime();
            invokeStaticPayloadStatic(launcher, data);
            staticWarmFirstNanos = System.nanoTime() - warmFirstStart;
            assertParity(points, "static-payload static warm-first", data.output, data.expected);

            for (int i = 0; i < warmup; i++) {
                invokeStaticPayloadStatic(launcher, data);
            }
            staticSamples = measure(iterations, () -> invokeStaticPayloadStatic(launcher, data));
            assertParity(points, "static-payload static measured", data.output, data.expected);
        }

        return new StaticPayloadResult(
                points,
                cpuNanos,
                normalWarmFirstNanos,
                normalSamples,
                staticWarmFirstNanos,
                staticSamples,
                maxAbsError(data.output, data.expected),
                data.output.length == 0 ? Double.NaN : data.output[0],
                data.expected.length == 0 ? Double.NaN : data.expected[0]
        );
    }

    private static GpuPreparedLauncher prepareStaticPayloadLauncher(
            PayloadData data,
            GpuRuntimeCompileOptions compileOptions
    ) {
        return JavaToGpu.prepareWithConfigAndCompileOptions(
                StaticPayloadKernel.class,
                "evaluate",
                JavaToGpu.launch1D(data.coords.length),
                compileOptions,
                data.coords,
                data.opcodes,
                data.arg0,
                data.arg1,
                data.arg2,
                data.flags,
                data.selector,
                data.value0,
                data.value1,
                data.value2,
                data.weights,
                data.thresholds,
                data.biasTable,
                data.gainTable,
                data.offsetTable,
                data.output,
                data.scratch
        );
    }

    private static void invokeStaticPayloadNormal(GpuPreparedLauncher launcher, PayloadData data) {
        launcher.invoke(
                data.coords,
                data.opcodes,
                data.arg0,
                data.arg1,
                data.arg2,
                data.flags,
                data.selector,
                data.value0,
                data.value1,
                data.value2,
                data.weights,
                data.thresholds,
                data.biasTable,
                data.gainTable,
                data.offsetTable,
                data.output,
                data.scratch
        );
    }

    private static void invokeStaticPayloadStatic(GpuPreparedLauncher launcher, PayloadData data) {
        launcher.invoke(data.coords, data.output, data.scratch);
    }

    private static TimedSamples measure(int iterations, Runnable action) {
        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            action.run();
            samples[i] = System.nanoTime() - start;
        }
        return TimedSamples.from(samples);
    }

    private static void fillInput(double[] input) {
        for (int i = 0; i < input.length; i++) {
            input[i] = ((i & 1023) - 512) * 0.03125D + ((i >>> 10) & 31);
        }
    }

    private static void computeCpu(double[] input, double[] output) {
        for (int i = 0; i < output.length; i++) {
            output[i] = transformCpu(input[i]);
        }
    }

    private static double transformCpu(double value) {
        double a = value * SCALE + BIAS;
        double b = a * a - value * 0.125D;
        double c = b < 0.0D ? -b : b;
        return c > LIMIT ? LIMIT : c;
    }

    private static void computeStaticPayloadCpu(PayloadData data) {
        for (int point = 0; point < data.expected.length; point++) {
            data.expected[point] = staticPayloadCpu(data, point);
        }
    }

    private static double staticPayloadCpu(PayloadData data, int point) {
        int node = point & 63;
        int index0 = data.arg0[node] & 63;
        int index1 = data.arg1[node] & 63;
        int index2 = data.arg2[node] & 63;
        double selected = data.value0[index0];
        if (data.selector[node] == 1) {
            selected = data.value1[index1];
        } else if (data.selector[node] == 2) {
            selected = data.value2[index2];
        }
        double wave = data.coords[point] * data.weights[node] + data.biasTable[node];
        double mixed = wave + selected * data.gainTable[node] - data.offsetTable[node];
        if (data.flags[node] != 0) {
            mixed = -mixed;
        }
        double limited = mixed > data.thresholds[node] ? data.thresholds[node] : mixed;
        return limited + data.opcodes[node] * 0.001D;
    }

    private static void assertParity(int points, String stage, double[] gpu, double[] expected) {
        double maxAbsError = maxAbsError(gpu, expected);
        if (maxAbsError > EPSILON) {
            throw new AssertionError(points + " points " + stage + " parity failed: maxAbsError=" + maxAbsError
                    + ", firstGpu=" + gpu[0] + ", firstCpu=" + expected[0]);
        }
    }

    private static double maxAbsError(double[] gpu, double[] expected) {
        double max = 0.0D;
        for (int i = 0; i < gpu.length; i++) {
            double diff = gpu[i] - expected[i];
            double abs = diff < 0.0D ? -diff : diff;
            if (abs > max) {
                max = abs;
            }
        }
        return max;
    }

    private static String apiLocation() {
        CodeSource codeSource = JavaToGpu.class.getProtectionDomain().getCodeSource();
        return codeSource == null || codeSource.getLocation() == null ? "unknown" : codeSource.getLocation().toString();
    }

    private static void assumeOpenClFp64Available() {
        GpuRuntimeBackendReport report;
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            report = backend.describeCapabilities();
        } catch (RuntimeException exception) {
            Assumptions.assumeTrue(false, "Skipping prepared-launcher benchmark: " + exception.getMessage());
            return;
        }

        Assumptions.assumeTrue(report.available(), "Skipping prepared-launcher benchmark: " + report.detail());
        Assumptions.assumeTrue(
                report.supports(GpuRuntimeFeature.DOUBLE_PRECISION),
                "Skipping prepared-launcher benchmark: OpenCL device has no fp64 support"
        );
    }

    public static final class TestKernel {
        private TestKernel() {
        }

        @net.sixik.ga_utils.javatogpu.api.annotations.GPU
        public static void transform(
                @GPUGlobal(constant = true) double[] input,
                double scale,
                double bias,
                double limit,
                @GPUGlobal double[] output
        ) {
            int point = GPU.get_global_id(0);
            double value = input[point];
            double a = value * scale + bias;
            double b = a * a - value * 0.125D;
            double c = b < 0.0D ? -b : b;
            output[point] = c > limit ? limit : c;
        }
    }

    public static final class StaticPayloadKernel {
        private StaticPayloadKernel() {
        }

        @net.sixik.ga_utils.javatogpu.api.annotations.GPU
        public static void evaluate(
                @GPUGlobal(constant = true) double[] coords,
                @GPUGlobal(constant = true) int[] opcodes,
                @GPUGlobal(constant = true) int[] arg0,
                @GPUGlobal(constant = true) int[] arg1,
                @GPUGlobal(constant = true) int[] arg2,
                @GPUGlobal(constant = true) int[] flags,
                @GPUGlobal(constant = true) int[] selector,
                @GPUGlobal(constant = true) double[] value0,
                @GPUGlobal(constant = true) double[] value1,
                @GPUGlobal(constant = true) double[] value2,
                @GPUGlobal(constant = true) double[] weights,
                @GPUGlobal(constant = true) double[] thresholds,
                @GPUGlobal(constant = true) double[] biasTable,
                @GPUGlobal(constant = true) double[] gainTable,
                @GPUGlobal(constant = true) double[] offsetTable,
                @GPUGlobal double[] output,
                @GPUGlobal double[] scratch
        ) {
            int point = GPU.get_global_id(0);
            int node = point & 63;
            int index0 = arg0[node] & 63;
            int index1 = arg1[node] & 63;
            int index2 = arg2[node] & 63;
            double selected = value0[index0];
            if (selector[node] == 1) {
                selected = value1[index1];
            } else if (selector[node] == 2) {
                selected = value2[index2];
            }
            double wave = coords[point] * weights[node] + biasTable[node];
            double mixed = wave + selected * gainTable[node] - offsetTable[node];
            if (flags[node] != 0) {
                mixed = -mixed;
            }
            double limited = mixed > thresholds[node] ? thresholds[node] : mixed;
            scratch[point] = mixed;
            output[point] = limited + opcodes[node] * 0.001D;
        }
    }

    private record PayloadData(
            double[] coords,
            int[] opcodes,
            int[] arg0,
            int[] arg1,
            int[] arg2,
            int[] flags,
            int[] selector,
            double[] value0,
            double[] value1,
            double[] value2,
            double[] weights,
            double[] thresholds,
            double[] biasTable,
            double[] gainTable,
            double[] offsetTable,
            double[] output,
            double[] scratch,
            double[] expected
    ) {
        private static PayloadData create(int points) {
            double[] coords = new double[points];
            double[] output = new double[points];
            double[] scratch = new double[points];
            double[] expected = new double[points];
            int[] opcodes = new int[64];
            int[] arg0 = new int[64];
            int[] arg1 = new int[64];
            int[] arg2 = new int[64];
            int[] flags = new int[64];
            int[] selector = new int[64];
            double[] value0 = new double[64];
            double[] value1 = new double[64];
            double[] value2 = new double[64];
            double[] weights = new double[64];
            double[] thresholds = new double[64];
            double[] biasTable = new double[64];
            double[] gainTable = new double[64];
            double[] offsetTable = new double[64];
            for (int i = 0; i < coords.length; i++) {
                coords[i] = ((i & 511) - 256) * 0.015625D + ((i >>> 9) & 15);
            }
            for (int i = 0; i < 64; i++) {
                opcodes[i] = (i % 7) + 1;
                arg0[i] = (i * 3) & 63;
                arg1[i] = (i * 5 + 7) & 63;
                arg2[i] = (i * 11 + 13) & 63;
                flags[i] = i & 1;
                selector[i] = i % 3;
                value0[i] = i * 0.0625D - 2.0D;
                value1[i] = i * 0.03125D + 0.5D;
                value2[i] = 3.0D - i * 0.015625D;
                weights[i] = 0.75D + (i & 7) * 0.03125D;
                thresholds[i] = 8.0D + (i & 15) * 0.25D;
                biasTable[i] = (i - 32) * 0.0125D;
                gainTable[i] = 0.5D + (i & 3) * 0.125D;
                offsetTable[i] = (i & 5) * 0.05D;
            }
            return new PayloadData(
                    coords,
                    opcodes,
                    arg0,
                    arg1,
                    arg2,
                    flags,
                    selector,
                    value0,
                    value1,
                    value2,
                    weights,
                    thresholds,
                    biasTable,
                    gainTable,
                    offsetTable,
                    output,
                    scratch,
                    expected
            );
        }
    }

    private record Options(int[] points, int warmup, int iterations) {
        private static Options fromSystemProperties() {
            return new Options(
                    parsePoints(System.getProperty(POINTS_PROPERTY, formatPoints(DEFAULT_POINTS))),
                    parsePositiveInt("warmup", System.getProperty(WARMUP_PROPERTY, "32")),
                    parsePositiveInt("iterations", System.getProperty(ITERATIONS_PROPERTY, "256"))
            );
        }

        private static Options parse(String[] args) {
            int[] points = DEFAULT_POINTS;
            int warmup = 32;
            int iterations = 256;

            for (String arg : args) {
                if (arg.startsWith("--points=")) {
                    points = parsePoints(arg.substring("--points=".length()));
                } else if (arg.startsWith("--warmup=")) {
                    warmup = parsePositiveInt("warmup", arg.substring("--warmup=".length()));
                } else if (arg.startsWith("--iterations=")) {
                    iterations = parsePositiveInt("iterations", arg.substring("--iterations=".length()));
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    printHelpAndExit();
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return new Options(points, warmup, iterations);
        }

        private static int[] parsePoints(String text) {
            String[] parts = text.split(",");
            int[] parsed = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                parsed[i] = parsePositiveInt("points", parts[i].trim());
            }
            return parsed;
        }

        private static int parsePositiveInt(String name, String text) {
            int value = Integer.parseInt(text);
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive: " + value);
            }
            return value;
        }

        private static String formatPoints(int[] points) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < points.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(points[i]);
            }
            return builder.toString();
        }

        private static void printHelpAndExit() {
            System.out.println("Usage: JavaToGpuPreparedLauncherBenchmarkTest [--points=128,512,2048] [--warmup=32] [--iterations=256]");
            System.exit(0);
        }
    }

    private record Result(
            int points,
            long prepareNanos,
            long coldNanos,
            long warmFirstNanos,
            double avgNanos,
            long minNanos,
            long p50Nanos,
            long p95Nanos,
            long maxNanos,
            long cpuNanos,
            double maxAbsError,
            double firstGpu,
            double firstCpu
    ) {
        private static Result from(
                int points,
                long prepareNanos,
                long coldNanos,
                long warmFirstNanos,
                long[] samples,
                long cpuNanos,
                double[] output,
                double[] expected
        ) {
            long[] sorted = samples.clone();
            Arrays.sort(sorted);
            long total = 0L;
            for (long sample : samples) {
                total += sample;
            }
            return new Result(
                    points,
                    prepareNanos,
                    coldNanos,
                    warmFirstNanos,
                    (double) total / (double) samples.length,
                    sorted[0],
                    sorted[sorted.length / 2],
                    sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95D) - 1)],
                    sorted[sorted.length - 1],
                    cpuNanos,
                    JavaToGpuPreparedLauncherBenchmarkTest.maxAbsError(output, expected),
                    output.length == 0 ? Double.NaN : output[0],
                    expected.length == 0 ? Double.NaN : expected[0]
            );
        }

        private String toCsv() {
            return String.format(Locale.ROOT,
                    "%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3g,%.17g,%.17g",
                    points,
                    toMillis(prepareNanos),
                    toMillis(coldNanos),
                    toMillis(warmFirstNanos),
                    toMillis(avgNanos),
                    toMillis(minNanos),
                    toMillis(p50Nanos),
                    toMillis(p95Nanos),
                    toMillis(maxNanos),
                    toMillis(cpuNanos),
                    maxAbsError,
                    firstGpu,
                    firstCpu);
        }

        private static double toMillis(long nanos) {
            return nanos / 1_000_000.0D;
        }

        private static double toMillis(double nanos) {
            return nanos / 1_000_000.0D;
        }
    }

    private record TimedSamples(double avgNanos, long minNanos, long p50Nanos, long p95Nanos, long maxNanos) {
        private static TimedSamples from(long[] samples) {
            long[] sorted = samples.clone();
            Arrays.sort(sorted);
            long total = 0L;
            for (long sample : samples) {
                total += sample;
            }
            return new TimedSamples(
                    (double) total / (double) samples.length,
                    sorted[0],
                    sorted[sorted.length / 2],
                    sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95D) - 1)],
                    sorted[sorted.length - 1]
            );
        }
    }

    private record StaticPayloadResult(
            int points,
            long cpuNanos,
            long normalWarmFirstNanos,
            TimedSamples normalSamples,
            long staticWarmFirstNanos,
            TimedSamples staticSamples,
            double maxAbsError,
            double firstGpu,
            double firstCpu
    ) {
        private double normalAvgNanos() {
            return normalSamples.avgNanos();
        }

        private double staticAvgNanos() {
            return staticSamples.avgNanos();
        }

        private String toCsv() {
            double speedup = staticSamples.avgNanos() == 0.0D
                    ? Double.NaN
                    : normalSamples.avgNanos() / staticSamples.avgNanos();
            return String.format(Locale.ROOT,
                    "%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.2f,%.3g,%.17g,%.17g",
                    points,
                    toMillis(cpuNanos),
                    toMillis(normalWarmFirstNanos),
                    toMillis(normalSamples.avgNanos()),
                    toMillis(normalSamples.minNanos()),
                    toMillis(normalSamples.p50Nanos()),
                    toMillis(normalSamples.p95Nanos()),
                    toMillis(normalSamples.maxNanos()),
                    toMillis(staticWarmFirstNanos),
                    toMillis(staticSamples.avgNanos()),
                    toMillis(staticSamples.minNanos()),
                    toMillis(staticSamples.p50Nanos()),
                    toMillis(staticSamples.p95Nanos()),
                    toMillis(staticSamples.maxNanos()),
                    speedup,
                    maxAbsError,
                    firstGpu,
                    firstCpu);
        }

        private static double toMillis(long nanos) {
            return nanos / 1_000_000.0D;
        }

        private static double toMillis(double nanos) {
            return nanos / 1_000_000.0D;
        }
    }
}
