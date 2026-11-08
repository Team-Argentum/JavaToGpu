package com.sixik.gpu.runtime.device;

import com.sixik.gpu.ir.Features;

import java.util.ArrayList;
import java.util.List;

/**
 * Устройство, найденное драйвером: всё, что нужно политике выбора и проверке возможностей до компиляции
 * (REWORK_ARCHITECTURE.md, 8.4, 9.4). Значение неизменяемо и не владеет ресурсами драйвера.
 *
 * @param index           порядковый номер в перечислении (платформы по порядку, устройства внутри платформы по порядку)
 * @param platform        дескриптор платформы
 * @param handle          дескриптор устройства
 * @param platformName    имя платформы
 * @param name            имя устройства
 * @param vendor          производитель устройства
 * @param type            битовая маска {@code CL_DEVICE_TYPE_*}
 * @param openClVersion   строка версии OpenCL устройства
 * @param driverVersion   версия драйвера
 * @param globalMemory    объём глобальной памяти, байт
 * @param maxAllocation   наибольшая допустимая единичная аллокация, байт
 * @param computeUnits    число вычислительных блоков
 * @param maxWorkGroupSize наибольший размер work-group
 * @param localMemory     объём локальной памяти на work-group, байт
 * @param maxConstantBuffer наибольший размер одного буфера {@code __constant}, байт ({@code CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE})
 * @param maxConstantArgs число аргументов ядра в {@code __constant} ({@code CL_DEVICE_MAX_CONSTANT_ARGS})
 * @param extensions      строка расширений, разделённых пробелами
 * @param features        маска {@link Features}, которую устройство поддерживает (по расширениям)
 */
public record DeviceInfo(int index, long platform, long handle, String platformName, String name, String vendor,
                         long type, String openClVersion, String driverVersion, long globalMemory,
                         long maxAllocation, int computeUnits, long maxWorkGroupSize, long localMemory,
                         long maxConstantBuffer, int maxConstantArgs, String extensions, int features) {

    /**
     * Перечисляет все устройства всех платформ.
     *
     * @param api слой доступа к драйверу
     * @return устройства в порядке перечисления; пусто, если драйвера или устройств нет
     */
    public static List<DeviceInfo> enumerate(DeviceApi api) {
        List<DeviceInfo> out = new ArrayList<>();
        for (long platform : api.platforms()) {
            String platformName = api.platformInfo(platform, DeviceApi.PLATFORM_NAME);
            for (long device : api.devices(platform)) {
                out.add(query(api, out.size(), platform, platformName, device));
            }
        }
        return out;
    }

    private static DeviceInfo query(DeviceApi api, int index, long platform, String platformName, long device) {
        String extensions = api.deviceString(device, DeviceApi.DEVICE_EXTENSIONS);
        return new DeviceInfo(index, platform, device, platformName,
                api.deviceString(device, DeviceApi.DEVICE_NAME).strip(),
                api.deviceString(device, DeviceApi.DEVICE_VENDOR).strip(),
                api.deviceLong(device, DeviceApi.DEVICE_TYPE),
                api.deviceString(device, DeviceApi.DEVICE_VERSION).strip(),
                api.deviceString(device, DeviceApi.DRIVER_VERSION).strip(),
                api.deviceLong(device, DeviceApi.DEVICE_GLOBAL_MEM_SIZE),
                api.deviceLong(device, DeviceApi.DEVICE_MAX_MEM_ALLOC_SIZE),
                (int) api.deviceLong(device, DeviceApi.DEVICE_MAX_COMPUTE_UNITS),
                api.deviceLong(device, DeviceApi.DEVICE_MAX_WORK_GROUP_SIZE),
                api.deviceLong(device, DeviceApi.DEVICE_LOCAL_MEM_SIZE),
                api.deviceLong(device, DeviceApi.DEVICE_MAX_CONSTANT_BUFFER_SIZE),
                (int) api.deviceLong(device, DeviceApi.DEVICE_MAX_CONSTANT_ARGS),
                extensions, featuresOf(extensions));
    }

    /**
     * Переводит строку расширений в маску {@link Features}.
     *
     * @param extensions расширения через пробел
     * @return маска поддержанных возможностей
     */
    public static int featuresOf(String extensions) {
        List<String> ext = List.of(extensions.split("\\s+"));
        int mask = 0;
        if (ext.contains("cl_khr_fp64")) {
            mask |= Features.FP64;
        }
        if (ext.contains("cl_khr_int64_base_atomics")) {
            mask |= Features.INT64_ATOMICS;
        }
        if (ext.contains("cl_khr_int64_extended_atomics")) {
            mask |= Features.INT64_EXT_ATOMICS;
        }
        if (ext.contains("cl_khr_fp16")) {
            mask |= Features.FP16;
        }
        return mask;
    }

    /** @return {@code true}, если это GPU */
    public boolean isGpu() {
        return (type & DeviceApi.TYPE_GPU) != 0;
    }

    /** @return {@code true}, если это CPU */
    public boolean isCpu() {
        return (type & DeviceApi.TYPE_CPU) != 0;
    }

    /**
     * @param required маска {@link Features}
     * @return {@code true}, если устройство поддерживает все возможности маски
     */
    public boolean supports(int required) {
        return (features & required) == required;
    }

    /**
     * Короткое человекочитаемое описание для сообщений об ошибках и событий.
     *
     * @return например {@code "NVIDIA GeForce RTX 5070 [NVIDIA Corporation, GPU, 12 GiB]"}
     */
    public String describe() {
        String kind = isGpu() ? "GPU" : isCpu() ? "CPU" : "accelerator";
        return name + " [" + vendor + ", " + kind + ", " + (globalMemory >> 20) + " MiB]";
    }
}
