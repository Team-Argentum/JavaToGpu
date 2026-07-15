package net.sixik.ga_utils.javatogpu.iroptimizer;

/**
 * Module metadata for the optional backend-neutral IR optimizer package.
 */
public final class GpuIrOptimizerModule {

    public static final String MODULE_ID = "javatogpu.ir-optimizer";
    public static final String MODULE_VERSION = "1";
    public static final String VENDOR_PROVIDER_ADAPTER_ID = MODULE_ID + ".vendor-provider-adapter";
    public static final String VENDOR_PROVIDER_ADAPTER_VERSION = VENDOR_PROVIDER_ADAPTER_ID + ":1";

    private GpuIrOptimizerModule() {
    }
}
