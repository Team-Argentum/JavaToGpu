package com.sixik.gpu.codegen.opencl;

/**
 * Целевое устройство кодогенератора (REWORK_ARCHITECTURE.md, 8.5): то, что эмиттер знает об устройстве.
 *
 * <p>Шов под будущую адаптацию к вендору. В v1 вендорных правил нет: любой {@code Target} даёт один и тот же
 * текст (единый нейтральный путь), но значение уже передаётся в {@link OpenClEmitter#emit}, попадает в
 * {@link Artifact#target()} (а значит, в ключ кэша бинаря) и в имя golden-файла через {@link #id()}.</p>
 *
 * @param backend       целевой язык, в v1 всегда {@code "opencl"}
 * @param vendor        вендор устройства ({@code "nvidia"}, {@code "amd"}, {@code "intel"}) либо {@code "generic"}
 * @param deviceClass   класс устройства ({@code "gpu"}, {@code "cpu"}, {@code "igpu"}) либо {@code "any"}
 * @param features      маска {@link com.sixik.gpu.ir.Features}, поддерживаемых устройством; 0 - не проверять
 * @param subGroupSize  размер subgroup/warp/wavefront; 0 - неизвестен
 * @param computeUnits  число вычислительных блоков; 0 - неизвестно
 * @param nativeMath    какие float-функции можно печатать как {@code native_*} в ядрах с {@code fp=native}: биты
 *                      {@link #NATIVE_SIN}, {@link #NATIVE_COS}, {@link #NATIVE_SQRT}; рантайм ставит только те,
 *                      чья точность проверена на устройстве. 0 - всегда точные функции
 * @param exactDivideSqrt устройство умеет корректно округлённые {@code float}-деление и {@code sqrt}
 *                      ({@code CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT}): строгие ядра собираются с
 *                      {@link #EXACT_DIVIDE_SQRT_OPTION} и делят побитово как JVM
 */
public record Target(String backend, String vendor, String deviceClass, int features, int subGroupSize,
                     int computeUnits, int nativeMath, boolean exactDivideSqrt) {

    /** Опция сборки: {@code float}-деление и {@code sqrt} корректно округлены (не ослабляет, а ужесточает математику). */
    public static final String EXACT_DIVIDE_SQRT_OPTION = "-cl-fp32-correctly-rounded-divide-sqrt";

    /** {@code native_sin} разрешён. */
    public static final int NATIVE_SIN = 1;
    /** {@code native_cos} разрешён. */
    public static final int NATIVE_COS = 2;
    /** {@code native_sqrt} разрешён. */
    public static final int NATIVE_SQRT = 4;
    /** Все известные {@code native_*}. */
    public static final int NATIVE_ALL = NATIVE_SIN | NATIVE_COS | NATIVE_SQRT;

    /** Нейтральная цель: «любое OpenCL-устройство», ничего не известно. */
    public static final Target GENERIC = new Target("opencl", "generic", "any", 0, 0, 0);

    /**
     * Цель без {@code native_*}.
     *
     * @param backend      целевой язык
     * @param vendor       вендор
     * @param deviceClass  класс устройства
     * @param features     маска возможностей
     * @param subGroupSize размер subgroup
     * @param computeUnits число вычислительных блоков
     */
    public Target(String backend, String vendor, String deviceClass, int features, int subGroupSize,
                  int computeUnits) {
        this(backend, vendor, deviceClass, features, subGroupSize, computeUnits, 0, false);
    }

    /**
     * Цель без точного деления.
     *
     * @param backend      целевой язык
     * @param vendor       вендор
     * @param deviceClass  класс устройства
     * @param features     маска возможностей
     * @param subGroupSize размер subgroup
     * @param computeUnits число вычислительных блоков
     * @param nativeMath   разрешённые {@code native_*}
     */
    public Target(String backend, String vendor, String deviceClass, int features, int subGroupSize,
                  int computeUnits, int nativeMath) {
        this(backend, vendor, deviceClass, features, subGroupSize, computeUnits, nativeMath, false);
    }

    /**
     * Бит {@code native_*} для встроенной функции.
     *
     * @param builtin имя встроенной функции IR ({@code "sin"}, {@code "cos"}, {@code "sqrt"})
     * @return бит либо 0, если у функции нет {@code native_*}-варианта
     */
    public static int nativeBit(String builtin) {
        return switch (builtin) {
            case "sin" -> NATIVE_SIN;
            case "cos" -> NATIVE_COS;
            case "sqrt" -> NATIVE_SQRT;
            default -> 0;
        };
    }

    /**
     * Та же цель с другой маской {@code native_*}.
     *
     * @param mask биты {@link #NATIVE_SIN} и т.д.
     * @return новая цель
     */
    public Target withNativeMath(int mask) {
        return new Target(backend, vendor, deviceClass, features, subGroupSize, computeUnits, mask & NATIVE_ALL,
                exactDivideSqrt);
    }

    /**
     * Та же цель с другим признаком точного деления.
     *
     * @param exact устройство умеет корректно округлённые {@code float}-деление и {@code sqrt}
     * @return новая цель
     */
    public Target withExactDivideSqrt(boolean exact) {
        return new Target(backend, vendor, deviceClass, features, subGroupSize, computeUnits, nativeMath, exact);
    }

    /**
     * Проверяет, что строки заданы.
     */
    public Target {
        if (backend == null || vendor == null || deviceClass == null) {
            throw new IllegalArgumentException("backend, vendor and deviceClass must not be null");
        }
        if ((nativeMath & ~NATIVE_ALL) != 0) {
            throw new IllegalArgumentException("unknown native math bits: " + Integer.toHexString(nativeMath));
        }
    }

    /**
     * Короткий стабильный идентификатор цели для имён golden-файлов и ключей кэша.
     *
     * <p>Формат: {@code backend-vendor} и, если класс устройства известен, {@code -deviceClass}; при ненулевой
     * {@link #nativeMath()} - суффикс {@code -nN} (маска меняет текст ядер с {@code fp=native}), при
     * {@link #exactDivideSqrt()} - {@code -cr} (меняет опции сборки). Остальные поля в идентификатор не входят, потому
     * что не влияют на артефакт.</p>
     *
     * @return идентификатор вида {@code "opencl-generic"} или {@code "opencl-nvidia-gpu-n7-cr"}
     */
    public String id() {
        String base = deviceClass.equals("any") ? backend + "-" + vendor : backend + "-" + vendor + "-" + deviceClass;
        return base + (nativeMath == 0 ? "" : "-n" + nativeMath) + (exactDivideSqrt ? "-cr" : "");
    }
}
