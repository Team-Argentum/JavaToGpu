package com.sixik.gpu.runtime.ffm;

import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceApiProvider;

/**
 * Бэкенд OpenCL через FFM ({@code java.lang.foreign}, Java 25+). Приоритет выше, чем у LWJGL: при обоих бэкендах на
 * classpath в JVM 25 выбирается этот.
 */
public final class FfmDeviceApiProvider implements DeviceApiProvider {

    /** Имя бэкенда ({@code -Djavatogpu.backend=ffm}). */
    public static final String NAME = "ffm";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 200;
    }

    @Override
    public boolean isSupported() {
        return Runtime.version().feature() >= 25;
    }

    @Override
    public DeviceApi load() {
        return OpenClApi.load();
    }
}
