package com.sixik.gpu.runtime.lwjgl;

import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceApiProvider;

/**
 * Бэкенд OpenCL через LWJGL ({@code lwjgl-opencl}, Java 17). Приоритет ниже FFM: если на classpath есть оба, в JVM 25
 * выбирается FFM, а этот - на Java 17-24 (Minecraft 1.18-1.21.x) или по {@code -Djavatogpu.backend=lwjgl}.
 */
public final class LwjglDeviceApiProvider implements DeviceApiProvider {

    /** Имя бэкенда ({@code -Djavatogpu.backend=lwjgl}). */
    public static final String NAME = "lwjgl";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean isSupported() {
        ClassLoader l = LwjglDeviceApiProvider.class.getClassLoader();
        return l.getResource("org/lwjgl/opencl/CL10.class") != null && l.getResource("org/lwjgl/system/MemoryUtil.class") != null;
    }

    @Override
    public DeviceApi load() {
        return LwjglOpenClApi.load();
    }
}
