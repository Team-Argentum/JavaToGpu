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
 */
public record Target(String backend, String vendor, String deviceClass, int features, int subGroupSize,
                     int computeUnits) {

    /** Нейтральная цель: «любое OpenCL-устройство», ничего не известно. */
    public static final Target GENERIC = new Target("opencl", "generic", "any", 0, 0, 0);

    /**
     * Проверяет, что строки заданы.
     */
    public Target {
        if (backend == null || vendor == null || deviceClass == null) {
            throw new IllegalArgumentException("backend, vendor и deviceClass не могут быть null");
        }
    }

    /**
     * Короткий стабильный идентификатор цели для имён golden-файлов и ключей кэша.
     *
     * <p>Формат: {@code backend-vendor} и, если класс устройства известен, {@code -deviceClass}; остальные
     * поля в идентификатор не входят, потому что в v1 не влияют на текст.</p>
     *
     * @return идентификатор вида {@code "opencl-generic"} или {@code "opencl-nvidia-gpu"}
     */
    public String id() {
        return deviceClass.equals("any") ? backend + "-" + vendor : backend + "-" + vendor + "-" + deviceClass;
    }
}
