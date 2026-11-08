package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Воспроизводимые случайные аргументы для образцов-ядер по сигнатуре ядра: массивы нужного Java-вида и скаляры
 * нужного класса. Целые ненулевые и умеренные (чтобы ядра, которые делят на входные данные, не делили на ноль),
 * вещественные - в пределах {@code +-20} плюс {@code 0, -0, NaN, +-Inf}.
 *
 * <p>Нужны тестам, сверяющим исполнение на устройстве с интерпретатором: фабрика вызывается дважды и обязана
 * давать равные, но независимые массивы.</p>
 */
public final class SampleArgs {

    /** Длина массивов, достаточная для всех образцов при запуске на 16-24 work-item'ах. */
    public static final int ARRAY_LENGTH = 1024;

    private SampleArgs() {
    }

    /**
     * Образцы, которые нельзя сверять на устройстве с интерпретатором: гонки между work-item'ами (все пишут в одну
     * ячейку или читают чужие записи), алиасинг (на устройстве два аргумента - два буфера) и приватный массив в 1 МиБ,
     * который интерпретатор осиливает, а устройство отвергает ({@code CL_OUT_OF_RESOURCES}).
     *
     * @param label имя вида {@code "MemoryKernels.prefixSum"}
     * @return {@code true}, если образец на устройстве не сверяется
     */
    public static boolean notComparableOnDevice(String label) {
        return label.equals("MemoryKernels.prefixSum") || label.equals("MemoryKernels.aliasing")
                || label.equals("MemoryKernels.lengths") || label.equals("MemoryKernels.manyCalls");
    }

    /**
     * @param m    модуль
     * @param t    тип параметра
     * @return тип элемента для массива и сам тип для скаляра
     */
    public static int scalarOf(IrModule m, int t) {
        return m.typeKind(t) == Types.KIND_ARRAY ? Types.elem(m, t) : t;
    }

    /**
     * @param m    модуль
     * @param f    функция-ядро
     * @param seed начальное значение генератора
     * @return фабрика аргументов; каждый вызов возвращает новый набор с теми же значениями
     */
    public static Supplier<Object[]> random(IrModule m, int f, long seed) {
        return () -> {
            Random r = new Random(seed);
            Object[] out = new Object[m.fParamCount(f)];
            for (int i = 0; i < out.length; i++) {
                int t = m.fParamType(f, i);
                int e = scalarOf(m, t);
                if (m.typeKind(t) == Types.KIND_ARRAY) {
                    out[i] = randomArray(r, e);
                } else {
                    out[i] = switch (e) {
                        case Types.I32 -> nonZeroInt(r);
                        case Types.I64 -> (long) nonZeroInt(r) * 1000003L;
                        case Types.F32 -> randomFloat(r);
                        default -> (double) randomFloat(r);
                    };
                }
            }
            return out;
        };
    }

    private static int nonZeroInt(Random r) {
        int v = r.nextInt(201) - 100;
        return v == 0 ? 17 : v;
    }

    private static float randomFloat(Random r) {
        return switch (r.nextInt(20)) {
            case 0 -> 0f;
            case 1 -> -0f;
            case 2 -> Float.NaN;
            case 3 -> Float.POSITIVE_INFINITY;
            case 4 -> Float.NEGATIVE_INFINITY;
            default -> (r.nextFloat() - 0.5f) * 40f;
        };
    }

    private static Object randomArray(Random r, int elem) {
        int n = ARRAY_LENGTH;
        switch (elem) {
            case Types.I8, Types.U8: {
                byte[] a = new byte[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (byte) (r.nextInt(100) + 1);
                }
                return a;
            }
            case Types.I16: {
                short[] a = new short[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (short) (r.nextInt(3000) + 1);
                }
                return a;
            }
            case Types.U16: {
                char[] a = new char[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (char) (r.nextInt(60000) + 1);
                }
                return a;
            }
            case Types.I32, Types.U32: {
                int[] a = new int[n];
                for (int i = 0; i < n; i++) {
                    a[i] = nonZeroInt(r);
                }
                return a;
            }
            case Types.I64, Types.U64: {
                long[] a = new long[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (long) nonZeroInt(r) * 1000003L;
                }
                return a;
            }
            case Types.F32: {
                float[] a = new float[n];
                for (int i = 0; i < n; i++) {
                    a[i] = randomFloat(r);
                }
                return a;
            }
            default: {
                double[] a = new double[n];
                for (int i = 0; i < n; i++) {
                    a[i] = randomFloat(r);
                }
                return a;
            }
        }
    }
}
