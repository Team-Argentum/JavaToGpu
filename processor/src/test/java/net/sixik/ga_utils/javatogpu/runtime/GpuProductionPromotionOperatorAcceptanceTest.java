package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuProductionPromotionOperatorAcceptanceTest {

    @Test
    void acceptsManifestBoundToExactRuntimeContext() {
        GpuKernelDescriptor descriptor = descriptor();
        GpuRuntimeDeviceProfile device = device("595.97");
        GpuRuntimeCompileOptions options = productionOptions();
        GpuProductionPromotionOperatorAcceptance acceptance =
                GpuProductionPromotionOperatorAcceptance.forContext(
                        "acceptance:test-rtx5070",
                        GpuBackendTarget.OPENCL,
                        device,
                        options.optimizationProfile(),
                        descriptor,
                        GpuProductionPromotionDecision.PRODUCTION_ENABLED
                );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor,
                options.withProductionPromotionOperatorAcceptance(acceptance),
                device
        );

        GpuProductionPromotionOperatorAcceptance.Result result =
                GpuProductionPromotionOperatorAcceptance.evaluate(request);

        assertTrue(result.accepted());
        assertEquals("accepted", result.status());
        assertEquals("acceptance:test-rtx5070", result.acceptanceId());
        assertTrue(result.blockers().isEmpty());
        assertTrue(request.options().backendOptions().productionPromotionOperatorAcceptance().isPresent());
    }

    @Test
    void legacyBooleanWithoutIdentityBindingRemainsBlocked() {
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                productionOptions().withProductionPromotionOperatorAccepted(true),
                device("595.97")
        );

        GpuProductionPromotionOperatorAcceptance.Result result =
                GpuProductionPromotionOperatorAcceptance.evaluate(request);

        assertFalse(result.accepted());
        assertEquals(List.of("operator-acceptance-binding-missing"), result.blockers());
    }

    @Test
    void driverVersionMismatchInvalidatesAcceptance() {
        GpuKernelDescriptor descriptor = descriptor();
        GpuRuntimeCompileOptions options = productionOptions();
        GpuProductionPromotionOperatorAcceptance acceptance =
                GpuProductionPromotionOperatorAcceptance.forContext(
                        "acceptance:test-old-driver",
                        GpuBackendTarget.OPENCL,
                        device("595.90"),
                        options.optimizationProfile(),
                        descriptor,
                        GpuProductionPromotionDecision.PRODUCTION_ENABLED
                );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor,
                options.withProductionPromotionOperatorAcceptance(acceptance),
                device("595.97")
        );

        GpuProductionPromotionOperatorAcceptance.Result result =
                GpuProductionPromotionOperatorAcceptance.evaluate(request);

        assertFalse(result.accepted());
        assertEquals("operator-acceptance-driver-version-mismatch", result.blockers().get(0));
    }

    private static GpuRuntimeCompileOptions productionOptions() {
        return GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                .withProductionPromotionDecision(new GpuProductionPromotionDecision(
                        GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                        "production-ready",
                        true,
                        true,
                        true,
                        "none",
                        "none",
                        "test production decision"
                ));
    }

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "gpu_kernel",
                "inline://integration/operator-acceptance-kernel.cl",
                "__kernel void gpu_kernel(__global int* output) { output[0] = 1; }",
                List.of()
        );
    }

    private static GpuRuntimeDeviceProfile device(String driverVersion) {
        return new GpuRuntimeDeviceProfile(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "NVIDIA CUDA / NVIDIA GeForce RTX 5070",
                "NVIDIA Corporation",
                driverVersion,
                "OpenCL 3.0 CUDA"
        );
    }
}
