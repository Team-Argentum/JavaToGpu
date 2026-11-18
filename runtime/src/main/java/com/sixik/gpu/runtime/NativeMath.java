package com.sixik.gpu.runtime;

import com.sixik.gpu.codegen.opencl.Target;
import com.sixik.gpu.runtime.device.DeviceApi;

import com.sixik.gpu.runtime.device.HostArena;
import com.sixik.gpu.runtime.device.HostMemory;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Проверенная на устройстве точность {@code native_sin}, {@code native_cos} и {@code native_sqrt}.
 *
 * <p>Стандарт OpenCL не задаёт точность {@code native_*}: «implementation-defined». Поэтому ядро с
 * {@code fp=native} получает {@code native_*} только для функций, прошедших калибровку на этом устройстве: небольшое
 * ядро считает функции на фиксированной выборке, результат сравнивается с эталоном в {@code double}.</p>
 *
 * <ul>
 *   <li>{@code sin}/{@code cos}: максимальная абсолютная ошибка {@code max|g(x) - ref(x)|} на
 *       {@code x ∈ [-32π, 32π]} не больше {@link #TRIG_MAX_ABS} {@code = 2^-14 ≈ 6.1e-5}. Абсолютная, а не
 *       относительная: аппаратный синус после редукции аргумента ошибается на величину, растущую с {@code |x|}, а не с
 *       {@code 1/|sin x|}.</li>
 *   <li>{@code sqrt}: максимальная относительная ошибка {@code max|g(x) - √x| / √x} на {@code x = m·2^e},
 *       {@code e ∈ [-60, 60]}, не больше {@link #SQRT_MAX_REL} {@code = 2^-20 ≈ 9.5e-7} (около 8 ulp float).</li>
 * </ul>
 *
 * <p>NaN или бесконечность в результате - отказ. Результат калибровки хранится в дисковом кэше по устройству и
 * драйверу, поэтому ядро калибровки собирается один раз.</p>
 *
 * @param mask      разрешённые функции: биты {@link Target#NATIVE_SIN}, {@link Target#NATIVE_COS},
 *                  {@link Target#NATIVE_SQRT}
 * @param sinError  измеренная максимальная абсолютная ошибка {@code native_sin}; {@code NaN} - не измерена
 * @param cosError  измеренная максимальная абсолютная ошибка {@code native_cos}; {@code NaN} - не измерена
 * @param sqrtError измеренная максимальная относительная ошибка {@code native_sqrt}; {@code NaN} - не измерена
 */
public record NativeMath(int mask, double sinError, double cosError, double sqrtError) {

    /** Порог абсолютной ошибки {@code native_sin}/{@code native_cos}: {@code 2^-14}. */
    public static final double TRIG_MAX_ABS = 0x1p-14;
    /** Порог относительной ошибки {@code native_sqrt}: {@code 2^-20}. */
    public static final double SQRT_MAX_REL = 0x1p-20;
    /** Граница выборки {@code sin}/{@code cos}: {@code 32π}. */
    static final double TRIG_RANGE = 32 * Math.PI;

    /** Ничего не разрешено, ничего не измерено (калибровка не удалась). */
    public static final NativeMath NONE = new NativeMath(0, Double.NaN, Double.NaN, Double.NaN);

    /** Версия формата записи в дисковом кэше и самой процедуры: смена порогов или выборки её повышает. */
    static final String VERSION = "1";

    /** Число точек выборки каждой функции. */
    static final int N = 4096;

    /** Имя ядра калибровки. */
    public static final String KERNEL = "javatogpu_native_math";

    /**
     * Ядро калибровки: для {@code i}-й точки пишет {@code native_sin(t[i])}, {@code native_cos(t[i])},
     * {@code native_sqrt(s[i])} в три подряд идущих элемента.
     */
    static final String SOURCE = """
            __kernel void javatogpu_native_math(__global const float* t, __global const float* s,
                                                __global float* out) {
                int i = get_global_id(0);
                float x = t[i];
                out[3 * i] = native_sin(x);
                out[3 * i + 1] = native_cos(x);
                out[3 * i + 2] = native_sqrt(s[i]);
            }
            """;

    /**
     * Точки выборки {@code sin}/{@code cos}: равномерная сетка на {@code [-32π, 32π]}.
     *
     * @return {@link #N} значений
     */
    static float[] trigSamples() {
        float[] t = new float[N];
        for (int i = 0; i < N; i++) {
            t[i] = (float) (-TRIG_RANGE + 2 * TRIG_RANGE * i / (N - 1));
        }
        return t;
    }

    /**
     * Точки выборки {@code sqrt}: {@code x = (1 + k/64)·2^e}, {@code e ∈ [-60, 60]}, мантиссы обходят
     * {@code [1, 2)}, поэтому проверяются и чётные, и нечётные порядки.
     *
     * @return {@link #N} положительных значений
     */
    static float[] sqrtSamples() {
        float[] s = new float[N];
        for (int i = 0; i < N; i++) {
            int e = -60 + i % 121;
            float mant = 1f + (i * 7 % 64) / 64f;
            s[i] = Math.scalb(mant, e);
        }
        return s;
    }

    /**
     * Оценивает результаты ядра калибровки.
     *
     * @param t   точки {@code sin}/{@code cos}
     * @param s   точки {@code sqrt}
     * @param out результаты ядра: по три на точку
     * @return разрешённые функции и измеренные ошибки
     */
    static NativeMath evaluate(float[] t, float[] s, float[] out) {
        double sinErr = 0;
        double cosErr = 0;
        double sqrtErr = 0;
        for (int i = 0; i < t.length; i++) {
            sinErr = Math.max(sinErr, error(out[3 * i], Math.sin(t[i]), 1));
            cosErr = Math.max(cosErr, error(out[3 * i + 1], Math.cos(t[i]), 1));
            double root = Math.sqrt(s[i]);
            sqrtErr = Math.max(sqrtErr, error(out[3 * i + 2], root, root));
        }
        int mask = (sinErr <= TRIG_MAX_ABS ? Target.NATIVE_SIN : 0)
                | (cosErr <= TRIG_MAX_ABS ? Target.NATIVE_COS : 0)
                | (sqrtErr <= SQRT_MAX_REL ? Target.NATIVE_SQRT : 0);
        return new NativeMath(mask, sinErr, cosErr, sqrtErr);
    }

    /**
     * Ошибка {@code |got - ref| / scale}; для NaN и бесконечности - бесконечность (функция отвергается).
     */
    private static double error(float got, double ref, double scale) {
        if (!Float.isFinite(got)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(got - ref) / scale;
    }

    /**
     * Калибрует устройство: собирает и запускает {@link #SOURCE}. Вызывать под замком контекста.
     *
     * @param api     API устройства
     * @param context контекст OpenCL
     * @param queue   очередь
     * @param device  устройство
     * @return результат калибровки
     * @throws RuntimeException ошибка драйвера (сборка, выделение памяти, запуск)
     */
    static NativeMath calibrate(DeviceApi api, long context, long queue, long device) {
        float[] t = trigSamples();
        float[] s = sqrtSamples();
        long program = 0;
        long kernel = 0;
        long bt = 0;
        long bs = 0;
        long bo = 0;
        try (HostArena arena = api.arena(HostArena.Kind.CONFINED)) {
            program = api.createProgram(context, SOURCE);
            DeviceApi.BuildOutcome b = api.build(program, device, "");
            if (b.status() != 0) {
                throw new IllegalStateException("calibration kernel build failed (" + b.status() + "): " + b.log());
            }
            kernel = api.createKernel(program, KERNEL);
            long in = 4L * N;
            long outBytes = 3 * in;
            HostMemory ht = arena.allocate(in, 8);
            ht.copyFrom(t, 0, 0, N);
            HostMemory hs = arena.allocate(in, 8);
            hs.copyFrom(s, 0, 0, N);
            HostMemory ho = arena.allocate(outBytes, 8);
            bt = api.createBuffer(context, in);
            bs = api.createBuffer(context, in);
            bo = api.createBuffer(context, outBytes);
            api.writeBuffer(queue, bt, 0, in, ht);
            api.writeBuffer(queue, bs, 0, in, hs);
            api.setArgBuffer(kernel, 0, bt);
            api.setArgBuffer(kernel, 1, bs);
            api.setArgBuffer(kernel, 2, bo);
            api.enqueue(queue, kernel, 1, new long[]{N}, null);
            api.readBuffer(queue, bo, 0, outBytes, ho);
            api.finish(queue);
            float[] out = new float[3 * N];
            ho.copyTo(0, out, 0, out.length);
            return evaluate(t, s, out);
        } finally {
            release(api, DeviceApi.HandleKind.BUFFER, bo);
            release(api, DeviceApi.HandleKind.BUFFER, bs);
            release(api, DeviceApi.HandleKind.BUFFER, bt);
            release(api, DeviceApi.HandleKind.KERNEL, kernel);
            release(api, DeviceApi.HandleKind.PROGRAM, program);
        }
    }

    private static void release(DeviceApi api, DeviceApi.HandleKind kind, long handle) {
        if (handle != 0) {
            try {
                api.release(kind, handle);
            } catch (RuntimeException ignored) {
                // освобождение после ошибки драйвера - лучшее, что можно сделать
            }
        }
    }

    /**
     * @return запись для дискового кэша
     */
    byte[] encode() {
        return (VERSION + ";" + mask + ";" + sinError + ";" + cosError + ";" + sqrtError)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param data запись из дискового кэша либо {@code null}
     * @return результат либо {@code null}, если записи нет или она другой версии/повреждена
     */
    static NativeMath decode(byte[] data) {
        if (data == null) {
            return null;
        }
        String[] p = new String(data, StandardCharsets.UTF_8).split(";");
        if (p.length != 5 || !p[0].equals(VERSION)) {
            return null;
        }
        try {
            return new NativeMath(Integer.parseInt(p[1]) & Target.NATIVE_ALL, Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]), Double.parseDouble(p[4]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return краткое описание для событий: разрешённые функции и ошибки
     */
    public String describe() {
        return String.format(Locale.ROOT, "native_sin %s (abs %.2e), native_cos %s (abs %.2e), native_sqrt %s (rel %.2e)",
                (mask & Target.NATIVE_SIN) != 0 ? "on" : "off", sinError,
                (mask & Target.NATIVE_COS) != 0 ? "on" : "off", cosError,
                (mask & Target.NATIVE_SQRT) != 0 ? "on" : "off", sqrtError);
    }
}
