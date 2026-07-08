package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

public record IrGpuBackendOutput(
        String backend,
        String kind,
        String resource,
        String format
) {

    public static IrGpuBackendOutput openClSource(String resource) {
        return new IrGpuBackendOutput("opencl", "source", resource, "opencl-c");
    }
}
