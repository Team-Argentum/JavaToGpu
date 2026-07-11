package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * ServiceLoader SPI for exposing generated or third-party fallback variants across module boundaries.
 */
public interface GpuRuntimeMethodVariantProvider {

    String providerId();

    default String providerVersion() {
        return "1";
    }

    List<GpuRuntimeMethodVariantRegistration> variants();
}
