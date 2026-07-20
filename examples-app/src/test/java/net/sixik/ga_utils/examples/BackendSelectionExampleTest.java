package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviders;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeSelectionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendSelectionExampleTest {

    @Test
    void rendersCatalogWithoutNativeBackendProbe() {
        String catalog = BackendSelectionExample.renderCatalog(GpuRuntimeBackendCatalog.standardWithPlannedBackends());

        assertTrue(catalog.contains("Backend catalog:"));
        assertTrue(catalog.contains("OpenCL (shared cache) (`OPENCL`)"));
        assertTrue(catalog.contains("CUDA (`CUDA`)"));
        assertTrue(catalog.contains("VULKAN (`VULKAN`)"));
        assertTrue(catalog.contains("METAL (`METAL`)"));
        assertTrue(catalog.contains("moduleFormats: opencl-c"));
        assertTrue(catalog.contains("moduleFormats: cuda-c,ptx"));
        assertTrue(catalog.contains("capabilityVocabulary: compute-capability"));
        assertTrue(catalog.contains("Runtime backend adapter is not implemented for CUDA"));
    }

    @Test
    void rendersPlannedBackendDiagnosticWithoutNativeBackendProbe() {
        String diagnostic = BackendSelectionExample.renderPlannedBackendDiagnostics(GpuBackendTarget.CUDA);

        assertTrue(diagnostic.contains("Planned backend diagnostic:"));
        assertTrue(diagnostic.contains("Backend selection: not matched"));
        assertTrue(diagnostic.contains("CUDA: Runtime backend adapter is not implemented for CUDA"));
        assertTrue(diagnostic.contains("score: preference=1000000"));
        assertTrue(diagnostic.contains("rejected=true"));
        assertTrue(diagnostic.contains("moduleFormats: cuda-c,ptx"));
        assertTrue(diagnostic.contains("executionPipeline: available=true"));
    }

    @Test
    void rendersBackendExecutionAvailabilityWithoutNativeBackendProbe() {
        String availability = BackendSelectionExample.renderBackendExecutionAvailability(
                GpuRuntimeBackendProviders.standardWithPlannedBackends()
        );

        assertTrue(availability.contains("Backend execution availability:"));
        assertTrue(availability.contains("OPENCL: status=execution-pipeline-available"));
        assertTrue(availability.contains("sharedRunner=true"));
        assertTrue(availability.contains("moduleFormats: opencl-c"));
        assertTrue(availability.contains("CUDA: status=execution-pipeline-available"));
        assertTrue(availability.contains("moduleFormats: cuda-c,ptx"));
        assertTrue(availability.contains("VULKAN: status=execution-unavailable"));
        assertTrue(availability.contains("METAL: status=execution-unavailable"));
        assertTrue(availability.contains("sharedRunner=false"));
        assertTrue(availability.contains("backend-execution-stage-missing:compile"));
        assertTrue(availability.contains("backend-execution-stage-missing:prepare"));
        assertTrue(availability.contains("backend-execution-stage-missing:invoke"));
    }

    @Test
    void rendersDeviceDiscoveryResultWithoutNativeBackendProbe() {
        String discovery = BackendSelectionExample.renderDeviceDiscovery(GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "opencl-device-discovery-failed",
                new IllegalStateException("No OpenCL device found")
        ));

        assertTrue(discovery.contains("OpenCL device discovery:"));
        assertTrue(discovery.contains("Device discovery: unavailable"));
        assertTrue(discovery.contains("First blocker: opencl-device-discovery-failed"));
        assertTrue(discovery.contains("IllegalStateException: No OpenCL device found"));
    }

    @Test
    void rendersCombinedBackendDeviceExplanationWithoutNativeBackendProbe() {
        GpuRuntimeSelectionResult backendSelection = GpuRuntimeBackendPolicy.builder()
                .preferCatalogEntry(GpuRuntimeBackendCatalog.plannedUnsupported(GpuBackendTarget.CUDA))
                .build()
                .trySelect();
        GpuRuntimeDeviceDiscoveryResult deviceDiscovery = GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "opencl-device-discovery-failed",
                new IllegalStateException("No OpenCL device found")
        );

        String combined = BackendSelectionExample.renderBackendDeviceSelection(
                backendSelection,
                GpuRuntimeDeviceDiscoveryCatalog.of(List.of(deviceDiscovery))
        );

        assertTrue(combined.contains("Combined backend/device selection:"));
        assertTrue(combined.contains("Runtime selection: backend-not-selected"));
        assertTrue(combined.contains("Backend:"));
        assertTrue(combined.contains("Device discoveries:"));
        assertTrue(combined.contains("CUDA: Runtime backend adapter is not implemented for CUDA"));
        assertTrue(combined.contains("score: preference=1000000"));
        assertTrue(combined.contains("First blocker: opencl-device-discovery-failed"));
    }

    @Test
    void rendersAutomaticBackendDevicePreflightGuideWithoutNativeBackendProbe() {
        String guide = BackendSelectionExample.renderAutomaticBackendDevicePreflightGuide();

        assertTrue(guide.contains("Automatic backend/device preflight profile:"));
        assertTrue(guide.contains("withStandardBackendDevicePreflight()"));
        assertTrue(guide.contains("DemoKernel_GpuLauncher.invokeWithCompileOptions(options, ...)"));
        assertTrue(guide.contains("mode: standard"));
        assertTrue(guide.contains("requested: true"));
        assertTrue(guide.contains("only when no backend is already installed"));
    }

    @Test
    void rendersScoreBasedRankingGuideWithoutNativeBackendProbe() {
        String guide = BackendSelectionExample.renderScoreBasedRankingGuide();

        assertTrue(guide.contains("Score-based backend ranking profile:"));
        assertTrue(guide.contains("rankCandidatesByScore()"));
        assertTrue(guide.contains("mode: score-descending"));
        assertTrue(guide.contains("fallback-order remains the safe behavior"));
    }

    @Test
    void rendersCudaInventoryFactsWithoutNativeBackendProbe() {
        GpuRuntimeDeviceProfile cudaDevice = GpuRuntimeDeviceProfile.cuda(
                "GPU-test",
                "NVIDIA GeForce RTX 3060",
                "NVIDIA",
                "551.86",
                "CUDA 12.4, compute capability 8.6",
                GpuDeviceClassTarget.DGPU,
                12_884_901_888L,
                "NVIDIA CUDA",
                "driver 551.86, CUDA 12.4"
        );
        GpuRuntimeDeviceDiscoveryResult result = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.CUDA,
                "CUDA",
                List.of(cudaDevice),
                null
        );

        String facts = BackendSelectionExample.renderCudaInventoryFacts(
                GpuRuntimeDeviceDiscoveryCatalog.of(List.of(result))
        );

        assertTrue(facts.contains("CUDA inventory facts:"));
        assertTrue(facts.contains("NVIDIA GeForce RTX 3060"));
        assertTrue(facts.contains("runtime=12.4"));
        assertTrue(facts.contains("computeCapability=8.6"));
        assertTrue(facts.contains("memoryBytes=12884901888"));
    }
}
