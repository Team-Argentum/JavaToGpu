package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Device-тест M5: сгенерированный OpenCL C компилируется реальным драйвером и исполняется, а результат совпадает с
 * результатом {@code ir-interpreter} (эталон семантики IR, M4).
 *
 * <p>Целые и логика совпадают бит в бит; вещественные - в пределах заданного числа ULP (допуски builtins из
 * {@code builtins.def}: {@code sin/cos} 4 ULP, {@code sqrt} 3 ULP; деление на GPU по умолчанию тоже неточное). {@code NaN}
 * считается равным любому {@code NaN}. Без драйвера тест пропускается. Запуск:
 * {@code ./gradlew :codegen-opencl:deviceTest}.</p>
 */
@Tag("device")
class DeviceTest {

    private static ClDevice device;

    @BeforeAll
    static void open() {
        device = ClDevice.open();
        Assumptions.assumeTrue(device != null, "no OpenCL driver or device");
        System.out.println("OpenCL device: " + device.name());
    }

    @AfterAll
    static void close() {
        if (device != null) {
            device.close();
        }
    }

    // =============================================================================================
    // образцы-ядра, собранные из Java
    // =============================================================================================

    static Stream<String> sampleNames() {
        return SampleKernels.all().stream().map(SampleKernels.Kernel::label).filter(l -> !SampleArgs.notComparableOnDevice(l))
                .filter(l -> SampleKernels.all().stream().noneMatch(k -> k.label().equals(l) && SampleArgs.hasObjectArguments(k)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sampleNames")
    void samples(String label) {
        SampleKernels.Kernel k = SampleKernels.all().stream().filter(x -> x.label().equals(label)).findFirst()
                .orElseThrow();
        IrModule m = k.module();
        int f = m.functionCount() - 1;
        NdRange range = m.fReqWorkGroup(f, 0) > 0 ? NdRange.of2D(8, 4) : NdRange.of1D(16);
        boolean floats = false;
        for (int i = 0; i < m.fParamCount(f); i++) {
            floats |= Types.isFloat(SampleArgs.scalarOf(m, m.fParamType(f, i)));
        }
        check(label, m, range, SampleArgs.random(m, f, 4242), floats ? 4 : 0);
    }

    // =============================================================================================
    // ручные модули
    // =============================================================================================

    private static final int[] I_EDGE = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -65536, -3, -2, -1, 0, 1, 2, 3, 7,
            31, 32, 33, 65535, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
    private static final long[] L_EDGE = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -4294967296L, -3, -2, -1, 0, 1, 2, 3,
            63, 64, 65, 4294967295L, 4294967296L, Long.MAX_VALUE - 1, Long.MAX_VALUE};
    private static final float[] F_EDGE = {0f, -0f, 1f, -1f, 0.5f, -2.5f, 3f, 1e-30f, -1e30f, 16777216f, 255.5f,
            -255.5f, 2147483648f, -2147483904f, 4294967296f, 1.8446744e19f, Float.MAX_VALUE, Float.MIN_VALUE,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN};
    private static final double[] D_EDGE = {0d, -0d, 1d, -1d, 0.5, -2.5, 3d, 1e-300, -1e300, 9007199254740992d, 255.5,
            -255.5, 2147483648d, -2147483649d, 4294967296d, 9.223372036854775807e18, 1.8446744073709552e19,
            Double.MAX_VALUE, Double.MIN_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};

    @ParameterizedTest(name = "{0}")
    @MethodSource("opsTypes")
    void arithmetic(int t) {
        IrModule m = HandModules.ops(t);
        int n = HandModules.opsResultCount(t);
        boolean isFloat = Types.isFloat(t);
        Object[] pool = poolOf(t);
        int len = java.lang.reflect.Array.getLength(pool[0]);
        for (int i = 0; i < len; i++) {
            for (int j = 0; j < len; j++) {
                final int a = i;
                final int b = j;
                if (!isFloat && isZero(pool[0], b)) {
                    continue; // деление на ноль: ошибка интерпретатора и неопределённость на устройстве
                }
                check("ops_" + HandModules.name(t) + "[" + a + "," + b + "]", m, NdRange.of1D(1),
                        () -> new Object[]{pair(t, pool[0], a, b), newArray(t, n)}, isFloat ? 3 : 0);
            }
        }
    }

    static Stream<Integer> opsTypes() {
        return Stream.of(Types.I32, Types.U32, Types.I64, Types.U64, Types.F32, Types.F64);
    }

    /** Наборы значений по типу: единый массив, значения которого комбинируются попарно. */
    private static Object[] poolOf(int t) {
        return switch (t) {
            case Types.I32, Types.U32 -> new Object[]{I_EDGE};
            case Types.I64, Types.U64 -> new Object[]{L_EDGE};
            case Types.F32 -> new Object[]{F_EDGE};
            default -> new Object[]{D_EDGE};
        };
    }

    private static boolean isZero(Object pool, int i) {
        return pool instanceof int[] x ? x[i] == 0 : ((long[]) pool)[i] == 0;
    }

    private static Object pair(int t, Object pool, int i, int j) {
        return switch (t) {
            case Types.I32, Types.U32 -> new int[]{((int[]) pool)[i], ((int[]) pool)[j]};
            case Types.I64, Types.U64 -> new long[]{((long[]) pool)[i], ((long[]) pool)[j]};
            case Types.F32 -> new float[]{((float[]) pool)[i], ((float[]) pool)[j]};
            default -> new double[]{((double[]) pool)[i], ((double[]) pool)[j]};
        };
    }

    private static Object newArray(int t, int n) {
        return switch (t) {
            case Types.I8, Types.U8 -> new byte[n];
            case Types.I16 -> new short[n];
            case Types.U16 -> new char[n];
            case Types.I32, Types.U32 -> new int[n];
            case Types.I64, Types.U64 -> new long[n];
            case Types.F32 -> new float[n];
            default -> new double[n];
        };
    }

    static Stream<Integer> numericTypes() {
        return java.util.Arrays.stream(HandModules.NUMERIC).boxed();
    }

    @ParameterizedTest(name = "from {0}")
    @MethodSource("numericTypes")
    void conversions(int from) {
        IrModule m = HandModules.conversions(from);
        // по CONVERT_INPUTS входов за запуск; перебираем окна по краевым значениям
        Object edge = switch (from) {
            case Types.I8, Types.U8 -> new byte[]{Byte.MIN_VALUE, -1, 0, 1, 127, 5, 100, -100};
            case Types.I16 -> new short[]{Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE, 300, -300, 12345};
            case Types.U16 -> new char[]{0, 1, 255, 256, 32767, 32768, 65535, 12345};
            case Types.I32, Types.U32 -> new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE, 16777217, -16777217,
                    0x7fffff80};
            case Types.I64, Types.U64 -> new long[]{Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE, 9007199254740993L,
                    -9007199254740993L, 0x7fffffffffffff00L};
            case Types.F32 -> null;
            default -> null;
        };
        if (from == Types.F32) {
            for (int off = 0; off + HandModules.CONVERT_INPUTS <= F_EDGE.length; off += 4) {
                float[] w = java.util.Arrays.copyOfRange(F_EDGE, off, off + HandModules.CONVERT_INPUTS);
                check("convert_from_f32@" + off, m, NdRange.of1D(1), () -> conversionArgs(from, w.clone()), 0);
            }
        } else if (from == Types.F64) {
            for (int off = 0; off + HandModules.CONVERT_INPUTS <= D_EDGE.length; off += 4) {
                double[] w = java.util.Arrays.copyOfRange(D_EDGE, off, off + HandModules.CONVERT_INPUTS);
                check("convert_from_f64@" + off, m, NdRange.of1D(1), () -> conversionArgs(from, w.clone()), 0);
            }
        } else {
            final Object e = edge;
            check("convert_from_" + HandModules.name(from), m, NdRange.of1D(1), () -> conversionArgs(from, cloneArray(e)), 0);
        }
    }

    private static Object[] conversionArgs(int from, Object in) {
        Object[] args = new Object[1 + HandModules.NUMERIC.length];
        args[0] = in;
        for (int i = 0; i < HandModules.NUMERIC.length; i++) {
            args[1 + i] = newArray(HandModules.NUMERIC[i], HandModules.CONVERT_INPUTS);
        }
        return args;
    }

    private static Object cloneArray(Object a) {
        return switch (a) {
            case byte[] x -> x.clone();
            case short[] x -> x.clone();
            case char[] x -> x.clone();
            case int[] x -> x.clone();
            case long[] x -> x.clone();
            default -> throw new IllegalArgumentException();
        };
    }

    @Test
    void bitcasts() {
        check("bitcasts", HandModules.bitcasts(), NdRange.of1D(1), () -> new Object[]{
                new int[]{0, -1, 0x3f800000, 0x7fc00000}, new long[]{0, -1, 0x3ff0000000000000L, 0x7ff8000000000000L},
                new int[4], new float[4], new long[4], new double[4], new int[4], new long[4]}, 0);
    }

    @Test
    void bools() {
        for (int[] in : new int[][]{{1, 2}, {2, 1}, {0, 0}, {-5, 3}, {7, 7}}) {
            check("bools" + java.util.Arrays.toString(in), HandModules.bools(), NdRange.of1D(1),
                    () -> new Object[]{in.clone(), new float[]{0.5f}, new int[12]}, 0);
        }
    }

    @Test
    void control() {
        for (int n : new int[]{-7, -1, 0, 1, 2, 3, 4, 5, 7, 9, 10, 12}) {
            check("control(" + n + ")", HandModules.control(), NdRange.of1D(1),
                    () -> new Object[]{new int[]{n}, new int[8]}, 0);
        }
    }

    @Test
    void deviceFunctions() {
        check("device_functions", HandModules.deviceFunctions(), NdRange.of1D(1), () -> new Object[]{
                new float[]{1f, 2.5f, -3f, 4f}, new float[]{1f, 2f, 3f}, new float[2], new int[4]}, 0);
    }

    @Test
    void constants() {
        check("constants", HandModules.constants(), NdRange.of1D(1), () -> new Object[]{
                new byte[4], new byte[4], new short[4], new char[4], new int[4], new int[4], new long[4], new long[4],
                new float[13], new double[14]}, 0);
    }

    @Test
    void builtins() {
        for (NdRange range : new NdRange[]{NdRange.of1D(8, 4), NdRange.of2D(4, 2, 2, 1), NdRange.of3D(4, 2, 2, 2, 1, 1)}) {
            int items = (int) range.workItems();
            check("builtins" + range.dim() + "D", HandModules.builtins(), range, () -> new Object[]{
                    new float[]{0.5f, 2.25f, -1f}, new double[]{0.5, 2.25, -1}, new int[]{3, -2, 3},
                    new int[items * HandModules.BUILTINS_I32_RESULTS], new float[items * HandModules.BUILTINS_FLOAT_RESULTS],
                    new double[items * HandModules.BUILTINS_FLOAT_RESULTS]}, 4);
        }
    }

    @Test
    void atomics() {
        check("atomics", HandModules.atomics(), NdRange.of1D(64), () -> new Object[]{
                new int[2], new int[2], new long[2], new long[2], new int[64]}, 0);
    }

    @Test
    void requiredWorkGroup() {
        check("work_group_attr", HandModules.workGroupAttribute(), NdRange.of2D(128, 2), () -> new Object[]{
                new float[256]}, 0);
    }

    @Test
    void computeSine() {
        Random rnd = new Random(5);
        check("computeSine", Fixtures.computeSine(), NdRange.of1D(256), () -> {
            Random r = new Random(5);
            float[] in = new float[256];
            for (int i = 0; i < in.length; i++) {
                in[i] = (r.nextFloat() - 0.5f) * 20f;
            }
            return new Object[]{in, new float[256]};
        }, 4);
        assertTrue(rnd != null);
    }

    // =============================================================================================
    // векторы и struct (M8)
    // =============================================================================================

    @Test
    void vectorsOfFloat() {
        float[] in = {1.5f, -2f, 3.25f, 0f, 4f, 0.5f, -8f, 2f};
        check("vectors_f32", HandModules.vectorsF32(), NdRange.of1D(1), () -> new Object[]{
                in.clone(), new float[HandModules.VECTORS_F32_OUT], new int[HandModules.VECTORS_F32_OUTI]}, 4);
        float[] edge = {Float.NaN, -0f, Float.POSITIVE_INFINITY, 1e30f, 0f, Float.NEGATIVE_INFINITY, 3e9f, -3e9f};
        check("vectors_f32(edge)", HandModules.vectorsF32(), NdRange.of1D(1), () -> new Object[]{
                edge.clone(), new float[HandModules.VECTORS_F32_OUT], new int[HandModules.VECTORS_F32_OUTI]}, 4);
    }

    @Test
    void vectorsOfInteger() {
        int[][] inputs = {
                {Integer.MAX_VALUE, Integer.MIN_VALUE, 7, -7, 1, -1, 2, 3},
                {100, -100, 31, 33, 5, 5, 31, 32},
                {Integer.MIN_VALUE, 0, -1, 1, -1, 3, -33, 64}};
        long[][] longs = {{Long.MAX_VALUE, -1, 3, 65}, {Long.MIN_VALUE, 7, -1, 2}, {5, -5, 2, 130}};
        for (int i = 0; i < inputs.length; i++) {
            final int[] in = inputs[i];
            final long[] inl = longs[i];
            check("vectors_i32[" + i + "]", HandModules.vectorsI32(), NdRange.of1D(1), () -> new Object[]{
                    in.clone(), new int[HandModules.VECTORS_I32_OUT], inl.clone(), new long[HandModules.VECTORS_I32_OUTL],
                    new float[4]}, 0);
        }
    }

    @Test
    void vectorsOfDouble() {
        double[] in = {1.5, -2, 3.25, 0, 4, 0.5, -8, 2};
        check("vectors_f64", HandModules.vectorsF64(), NdRange.of1D(1), () -> new Object[]{
                in.clone(), new double[HandModules.VECTORS_F64_OUT], new float[2]}, 4);
    }

    @Test
    void structValues() {
        for (float x : new float[]{1.5f, -3f, 0f}) {
            check("structs(" + x + ")", HandModules.structs(), NdRange.of1D(1), () -> new Object[]{
                    new float[]{x, 2f, 3f, 4f, 5f, 6f}, new float[HandModules.STRUCTS_OUT]}, 0);
        }
    }

    // ---- группы, local-память, барьеры, атомики (M10) -----------------------------------------------

    private static IrModule local(String kernel) {
        return SampleKernels.kernel(com.sixik.gpu.ir.interpreter.samples.LocalKernels.class, kernel).module();
    }

    private static void runOnDevice(IrModule m, NdRange range, Object... args) {
        int f = m.functionCount() - 1;
        Result<Artifact> emitted = OpenClEmitter.emit(m, f, Target.GENERIC, EmitOptions.DEBUG);
        assertTrue(emitted.isOk(), emitted.diagnostics().toString());
        Artifact art = emitted.value();
        long[] global = new long[range.dim()];
        long[] local = new long[range.dim()];
        for (int d = 0; d < range.dim(); d++) {
            global[d] = range.global(d);
            int req = m.fReqWorkGroup(f, d);
            local[d] = req > 0 ? req : 1;
        }
        try (ClDevice.Program p = device.build(art.source(), art.buildOptions())) {
            device.run(p, art, args, global, local);
        } catch (ClDevice.ClException e) {
            fail(e.getMessage() + "\n----\n" + art.source());
        }
    }

    @Test
    void treeReductionMatchesInterpreter() {
        check("reduceSum", local("reduceSum"), NdRange.of1D(512), () -> {
            int[] in = new int[512];
            for (int i = 0; i < in.length; i++) {
                in[i] = i * 2654435 - 1000;
            }
            return new Object[]{in, new int[8]};
        }, 0);
    }

    @Test
    void tileTransposeMatchesInterpreter() {
        check("transpose", local("transpose"), NdRange.of2D(24, 16), () -> {
            float[] in = new float[24 * 16];
            for (int i = 0; i < in.length; i++) {
                in[i] = i * 0.25f - 11;
            }
            return new Object[]{new float[24 * 16], in, 24, 16};
        }, 0);
    }

    @Test
    void prefixSumMatchesInterpreter() {
        check("scan", local("scan"), NdRange.of1D(64), () -> {
            int[] in = new int[64];
            for (int i = 0; i < in.length; i++) {
                in[i] = (i * 37) % 11 - 3;
            }
            return new Object[]{in, new int[64]};
        }, 0);
    }

    @Test
    void histogramWithLocalAtomicsMatchesInterpreter() {
        check("histogram", local("histogram"), NdRange.of1D(256), () -> {
            int[] data = new int[256];
            Random rnd = new Random(77);
            for (int i = 0; i < data.length; i++) {
                data[i] = rnd.nextInt(5000);
            }
            int[] bins = new int[16];
            for (int i = 0; i < bins.length; i++) {
                bins[i] = 10 * i;
            }
            return new Object[]{data, bins};
        }, 0);
    }

    @Test
    void globalAtomicsGiveTheSameTotalsAndAPermutationOfOldValues() {
        int n = 1000;
        int[] data = new int[n];
        Random rnd = new Random(3);
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
        runOnDevice(local("counters"), NdRange.of1D(n), data, counters, olds);
        assertArrayEquals(new int[]{n, max, min, or, xor, and, -n}, counters);
        int[] sorted = olds.clone();
        java.util.Arrays.sort(sorted);
        for (int i = 0; i < n; i++) {
            assertEquals(-(n - 1) + i, sorted[i], "previous values of atomic_sub are a permutation of 0, -1, ..., -(n-1)");
        }
    }

    @Test
    void sixtyFourBitAtomicsIncludingExtendedOnes() {
        Assumptions.assumeTrue(device.extensions().contains("cl_khr_int64_extended_atomics"), "no 64-bit extended atomics");
        int n = 300;
        long[] data = new long[n];
        long sum = 0;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            data[i] = (i - 100) * 0x123456789L;
            sum += data[i];
            max = Math.max(max, data[i]);
        }
        long[] sums = {0, Long.MIN_VALUE, 0};
        runOnDevice(local("wide"), NdRange.of1D(n), data, sums);
        assertEquals(sum, sums[0]);
        assertEquals(max, sums[1]);
        boolean written = false;
        for (long v : data) {
            written |= v == sums[2];
        }
        assertTrue(written, "atom_xchg left one of the written values");
    }

    @Test
    void pointersToLocalsGlobalsConstantsAndFields() {
        for (float x : new float[]{1.5f, -3f, 0f}) {
            check("pointers(" + x + ")", HandModules.pointers(), NdRange.of1D(1), () -> new Object[]{
                    new float[]{x, 2f, 3f, 4f, 5f}, new float[]{10f, 20f, 30f, 40f},
                    new float[HandModules.POINTERS_OUT]}, 0);
        }
    }

    // =============================================================================================
    // драйвер принимает любой сгенерированный текст
    // =============================================================================================

    static Stream<String> allModuleNames() {
        return GoldenTest.names();
    }

    /** Каждый модуль (образцы и ручные), release и debug, строгий и fast-math, компилируется драйвером. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allModuleNames")
    void driverAcceptsEveryModule(String name) {
        IrModule m = GoldenTest.modules().get(name);
        for (EmitOptions o : new EmitOptions[]{EmitOptions.RELEASE, EmitOptions.DEBUG,
                EmitOptions.DEBUG.withFastMath(true).withSourceName("X.java")}) {
            Artifact art = OpenClEmitter.emit(m, m.functionCount() - 1, Target.GENERIC, o).value();
            try (ClDevice.Program p = device.build(art.source(), art.buildOptions())) {
                assertNotNull(p);
            } catch (ClDevice.ClException e) {
                fail(name + " " + o + ": " + e.getMessage() + System.lineSeparator() + "----"
                        + System.lineSeparator() + art.source());
            }
        }
    }

    // =============================================================================================
    // сравнение
    // =============================================================================================

    private static void check(String name, IrModule m, NdRange range, Supplier<Object[]> args, int ulps) {
        int f = m.functionCount() - 1;
        Result<Artifact> emitted = OpenClEmitter.emit(m, f, Target.GENERIC, EmitOptions.DEBUG);
        assertTrue(emitted.isOk(), name + ": " + emitted.diagnostics());
        Artifact art = emitted.value();

        Object[] expected = args.get();
        Object[] actual = args.get();
        Result<Interpreter.Execution> ref = Interpreter.run(m, range, expected);
        assertTrue(ref.isOk(), name + ": interpreter refused: " + ref.diagnostics());

        long[] global = new long[range.dim()];
        long[] local = new long[range.dim()];
        for (int d = 0; d < range.dim(); d++) {
            global[d] = range.global(d);
            int req = m.fReqWorkGroup(f, d);
            local[d] = req > 0 ? req : (range.local(d) != 0 ? range.local(d) : 1);
        }
        try (ClDevice.Program p = device.build(art.source(), art.buildOptions())) {
            device.run(p, art, actual, global, local);
        } catch (ClDevice.ClException e) {
            fail(name + ": " + e.getMessage() + "\n----\n" + art.source());
        }
        for (int i = 0; i < expected.length; i++) {
            compare(name + " arg" + i, expected[i], actual[i], ulps, art);
        }
    }

    private static void compare(String name, Object e, Object a, int ulps, Artifact art) {
        if (!e.getClass().isArray()) {
            return; // скалярные аргументы передаются по значению и не меняются
        }
        switch (e) {
            case float[] x -> {
                float[] y = (float[]) a;
                for (int i = 0; i < x.length; i++) {
                    if (!sameFloat(x[i], y[i], ulps)) {
                        fail(name + "[" + i + "]: expected " + x[i] + " (" + Integer.toHexString(Float.floatToRawIntBits(x[i]))
                                + "), got " + y[i] + " (" + Integer.toHexString(Float.floatToRawIntBits(y[i])) + ")");
                    }
                }
            }
            case double[] x -> {
                double[] y = (double[]) a;
                for (int i = 0; i < x.length; i++) {
                    if (!sameDouble(x[i], y[i], ulps)) {
                        fail(name + "[" + i + "]: expected " + x[i] + ", got " + y[i]);
                    }
                }
            }
            default -> {
                int n = java.lang.reflect.Array.getLength(e);
                for (int i = 0; i < n; i++) {
                    Object ex = java.lang.reflect.Array.get(e, i);
                    Object ac = java.lang.reflect.Array.get(a, i);
                    assertEquals(ex, ac, name + "[" + i + "]");
                }
            }
        }
    }

    private static boolean sameFloat(float x, float y, int ulps) {
        if (Float.isNaN(x) || Float.isNaN(y)) {
            return Float.isNaN(x) && Float.isNaN(y);
        }
        if (Float.floatToRawIntBits(x) == Float.floatToRawIntBits(y)) {
            return true;
        }
        if (ulps == 0 || x == 0f || y == 0f) {
            return false; // знак нуля - часть семантики (Math.max/min, деление), допуском не прощается
        }
        return Math.abs(ordered(Float.floatToRawIntBits(x)) - ordered(Float.floatToRawIntBits(y))) <= ulps;
    }

    private static boolean sameDouble(double x, double y, int ulps) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return Double.isNaN(x) && Double.isNaN(y);
        }
        if (Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y)) {
            return true;
        }
        if (ulps == 0 || x == 0d || y == 0d) {
            return false;
        }
        long a = Double.doubleToRawLongBits(x);
        long b = Double.doubleToRawLongBits(y);
        return Math.abs(orderedL(a) - orderedL(b)) <= ulps;
    }

    private static long ordered(int bits) {
        return bits >= 0 ? bits : Integer.MIN_VALUE - (long) bits;
    }

    private static long orderedL(long bits) {
        return bits >= 0 ? bits : Long.MIN_VALUE - bits;
    }
}
