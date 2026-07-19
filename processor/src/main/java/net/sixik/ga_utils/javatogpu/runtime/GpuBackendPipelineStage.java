package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Backend-neutral runtime pipeline stages shared by OpenCL, CUDA, and future adapters.
 */
public enum GpuBackendPipelineStage {
    DISCOVER("discover", 0, false),
    SELECT("select", 10, false),
    LOWER("lower", 20, true),
    COMPILE("compile", 30, true),
    LOAD("load", 40, true),
    PREPARE("prepare", 50, true),
    BIND("bind", 60, true),
    INVOKE("invoke", 70, true),
    READBACK("readback", 80, true),
    CLOSE("close", 90, false);

    private final String key;
    private final int order;
    private final boolean productionAffecting;

    GpuBackendPipelineStage(String key, int order, boolean productionAffecting) {
        this.key = key;
        this.order = order;
        this.productionAffecting = productionAffecting;
    }

    public String key() {
        return key;
    }

    public int order() {
        return order;
    }

    public boolean productionAffecting() {
        return productionAffecting;
    }
}
