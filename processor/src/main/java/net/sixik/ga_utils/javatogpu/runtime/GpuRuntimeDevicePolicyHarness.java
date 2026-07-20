package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Hardware-free harness for runtime device policy extensions.
 */
public final class GpuRuntimeDevicePolicyHarness {

    private final GpuRuntimeDevicePolicyRegistry registry;

    private GpuRuntimeDevicePolicyHarness(GpuRuntimeDevicePolicyRegistry registry) {
        this.registry = registry == null ? GpuRuntimeDevicePolicyRegistry.of(List.of()) : registry;
    }

    public static GpuRuntimeDevicePolicyHarness empty() {
        return new GpuRuntimeDevicePolicyHarness(GpuRuntimeDevicePolicyRegistry.of(List.of()));
    }

    public static GpuRuntimeDevicePolicyHarness of(Collection<? extends GpuRuntimeDevicePolicy> policies) {
        return new GpuRuntimeDevicePolicyHarness(GpuRuntimeDevicePolicyRegistry.of(
                policies == null ? List.of() : List.copyOf(policies)
        ));
    }

    public static GpuRuntimeDevicePolicyHarness of(GpuRuntimeDevicePolicyRegistry registry) {
        return new GpuRuntimeDevicePolicyHarness(registry);
    }

    public static GpuRuntimeDevicePolicyHarness loadWithBuiltIns() {
        return new GpuRuntimeDevicePolicyHarness(GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns());
    }

    public GpuRuntimeDevicePolicyRegistry registry() {
        return registry;
    }

    public GpuRuntimeDevicePolicyHarnessReport runSyntheticOpenCl() {
        return runSyntheticOpenCl(GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL));
    }

    public GpuRuntimeDevicePolicyHarnessReport runSyntheticOpenCl(GpuRuntimeCompileOptions compileOptions) {
        return runSynthetic(compileOptions, syntheticOpenClCandidates());
    }

    public GpuRuntimeDevicePolicyHarnessReport runSynthetic(
            GpuRuntimeCompileOptions compileOptions,
            List<GpuRuntimeDeviceProfile> candidates
    ) {
        GpuRuntimeCompileOptions options = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                : compileOptions;
        List<GpuRuntimeDeviceProfile> resolvedCandidates = candidates == null ? List.of() : List.copyOf(candidates);
        GpuRuntimeDevicePolicyContext context = new GpuRuntimeDevicePolicyContext(
                Optional.of(syntheticDescriptor()),
                options,
                resolvedCandidates,
                Optional.empty()
        );
        return new GpuRuntimeDevicePolicyHarnessReport(
                options.backendTarget(),
                resolvedCandidates,
                registry.extensionRegistry().artifactFields("runtime.devicePolicy.harness.extension"),
                registry.select(context)
        );
    }

    public static List<GpuRuntimeDeviceProfile> syntheticOpenClCandidates() {
        return List.of(
                syntheticOpenClCpu(),
                syntheticOpenClIntegratedGpu(),
                syntheticOpenClDiscreteGpu()
        );
    }

    public static GpuRuntimeDeviceProfile syntheticOpenClCpu() {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-cpu-0",
                "Synthetic CPU",
                "Portable CPU Vendor",
                "test-driver-cpu",
                "OpenCL 3.0 Synthetic",
                GpuDeviceClassTarget.CPU,
                8,
                16L * 1024L * 1024L * 1024L,
                32L * 1024L,
                256L,
                1L,
                true,
                true,
                false,
                false
        );
    }

    public static GpuRuntimeDeviceProfile syntheticOpenClIntegratedGpu() {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-igpu-0",
                "Synthetic Integrated GPU",
                "Intel",
                "test-driver-igpu",
                "OpenCL 3.0 Synthetic",
                GpuDeviceClassTarget.IGPU,
                16,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                512L,
                1L,
                true,
                true,
                true,
                false
        );
    }

    public static GpuRuntimeDeviceProfile syntheticOpenClDiscreteGpu() {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-dgpu-0",
                "Synthetic Discrete GPU",
                "NVIDIA",
                "test-driver-dgpu",
                "OpenCL 3.0 Synthetic",
                GpuDeviceClassTarget.DGPU,
                48,
                16L * 1024L * 1024L * 1024L,
                96L * 1024L,
                1024L,
                1L,
                false,
                true,
                true,
                true
        );
    }

    public static GpuKernelDescriptor syntheticDescriptor() {
        return new GpuKernelDescriptor(
                "syntheticDevicePolicyKernel",
                "javatogpu/synthetic/DevicePolicyHarness.cl",
                "__kernel void syntheticDevicePolicyKernel(__global float* output) { output[0] = 1.0f; }",
                List.of(new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }
}
