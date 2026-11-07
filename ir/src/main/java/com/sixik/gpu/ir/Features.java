package com.sixik.gpu.ir;

/**
 * Битовая маска возможностей устройства, которые требуются функции (REWORK_ARCHITECTURE.md, 8.4).
 *
 * <p>Маску вычисляет валидатор ({@link Validator}); рантайм сверяет её с {@code DeviceInfo} до компиляции,
 * кодген эмитит соответствующие {@code #pragma OPENCL EXTENSION}.</p>
 */
public final class Features {

    private Features() {
    }

    /** Используется {@code double} (скаляр, вектор, поле, элемент массива). */
    public static final int FP64 = 1;
    /** Атомарные операции над 64-битными целыми. */
    public static final int INT64_ATOMICS = 1 << 1;
    /** Зарезервировано под {@code half}. */
    public static final int FP16 = 1 << 2;
    /** Зарезервировано под images. */
    public static final int IMAGES = 1 << 3;

    /**
     * @param mask маска возможностей
     * @return читаемое перечисление, например {@code "FP64|INT64_ATOMICS"}, либо {@code "-"} для нуля
     */
    public static String describe(int mask) {
        if (mask == 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, mask, FP64, "FP64");
        append(sb, mask, INT64_ATOMICS, "INT64_ATOMICS");
        append(sb, mask, FP16, "FP16");
        append(sb, mask, IMAGES, "IMAGES");
        return sb.toString();
    }

    private static void append(StringBuilder sb, int mask, int bit, String name) {
        if ((mask & bit) != 0) {
            if (!sb.isEmpty()) {
                sb.append('|');
            }
            sb.append(name);
        }
    }
}
