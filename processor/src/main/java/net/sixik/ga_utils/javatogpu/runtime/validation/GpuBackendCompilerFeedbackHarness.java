package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collection;
import java.util.List;

/**
 * Hardware-free harness for backend compiler feedback providers.
 */
public final class GpuBackendCompilerFeedbackHarness {

    private final GpuBackendCompilerFeedbackRegistry registry;

    private GpuBackendCompilerFeedbackHarness(GpuBackendCompilerFeedbackRegistry registry) {
        this.registry = registry == null ? GpuBackendCompilerFeedbackRegistry.of(List.of()) : registry;
    }

    public static GpuBackendCompilerFeedbackHarness empty() {
        return new GpuBackendCompilerFeedbackHarness(GpuBackendCompilerFeedbackRegistry.of(List.of()));
    }

    public static GpuBackendCompilerFeedbackHarness of(
            Collection<? extends GpuBackendCompilerFeedbackProvider> providers
    ) {
        return new GpuBackendCompilerFeedbackHarness(GpuBackendCompilerFeedbackRegistry.of(
                providers == null ? List.of() : List.copyOf(providers)
        ));
    }

    public static GpuBackendCompilerFeedbackHarness of(GpuBackendCompilerFeedbackRegistry registry) {
        return new GpuBackendCompilerFeedbackHarness(registry);
    }

    public static GpuBackendCompilerFeedbackHarness loadWithBuiltIns() {
        return new GpuBackendCompilerFeedbackHarness(GpuBackendCompilerFeedbackRegistry.loadWithBuiltIns());
    }

    public GpuBackendCompilerFeedbackRegistry registry() {
        return registry;
    }

    public GpuBackendCompilerFeedbackHarnessReport runSyntheticOpenCl() {
        return runSynthetic(syntheticOpenClRequest());
    }

    public GpuBackendCompilerFeedbackHarnessReport runSynthetic(GpuBackendCompilerFeedbackRequest request) {
        GpuBackendCompilerFeedbackRequest resolvedRequest = request == null ? syntheticOpenClRequest() : request;
        return new GpuBackendCompilerFeedbackHarnessReport(
                resolvedRequest.backendTarget(),
                registry.extensionRegistry().artifactFields("runtime.compilerFeedback.harness.extension"),
                registry.inspect(resolvedRequest)
        );
    }

    public static GpuBackendCompilerFeedbackRequest syntheticOpenClRequest() {
        return new GpuBackendCompilerFeedbackRequest(
                GpuBackendTarget.OPENCL,
                "opencl-c",
                "synthetic/compiler-feedback-kernel.opencl-c",
                syntheticOpenClCompileLog()
        );
    }

    public static String syntheticOpenClCompileLog() {
        return """
                ptxas info    : Function properties for syntheticCompilerFeedbackKernel
                    0 bytes stack frame, 0 bytes spill stores, 0 bytes spill loads
                ptxas info    : Used 32 registers, 384 bytes cmem[0]
                Occupancy: 75%
                [javatogpu-opencl-kernel-resource-info]
                status=recorded
                max work-group size: 1024
                preferred work-group size multiple: 32
                local memory: 2048 bytes
                private memory: 96 bytes
                """;
    }
}
