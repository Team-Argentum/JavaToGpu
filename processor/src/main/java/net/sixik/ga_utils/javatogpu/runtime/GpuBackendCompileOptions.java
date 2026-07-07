package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend-specific compile options kept separate from legacy OpenCL-style command-line args.
 */
public record GpuBackendCompileOptions(
        GpuBackendTarget backendTarget,
        List<String> flags,
        Map<String, String> properties
) {

    public GpuBackendCompileOptions {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        flags = flags == null ? List.of() : List.copyOf(flags);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static GpuBackendCompileOptions empty(GpuBackendTarget backendTarget) {
        return new GpuBackendCompileOptions(backendTarget, List.of(), Map.of());
    }

    public static GpuBackendCompileOptions openCl(List<String> compileArgs) {
        return new GpuBackendCompileOptions(GpuBackendTarget.OPENCL, compileArgs, Map.of());
    }

    public static GpuBackendCompileOptions cuda(List<String> nvrtcOptions, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.CUDA, nvrtcOptions, properties);
    }

    public static GpuBackendCompileOptions vulkan(List<String> spirvOptions, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.VULKAN, spirvOptions, properties);
    }

    public static GpuBackendCompileOptions metal(List<String> metalOptions, Map<String, String> properties) {
        return new GpuBackendCompileOptions(GpuBackendTarget.METAL, metalOptions, properties);
    }

    public boolean empty() {
        return flags.isEmpty() && properties.isEmpty();
    }

    public Map<String, String> stableProperties() {
        return new LinkedHashMap<>(new java.util.TreeMap<>(properties));
    }
}
