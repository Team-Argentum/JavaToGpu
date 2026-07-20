package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.CudaRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendCatalogEntry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapability;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaInventoryContractReportTest {

    @Test
    void builtInCudaProviderExposesInventoryOnlyContractWithoutNativeDiscovery() {
        CudaInventoryContractReport report = CudaInventoryContractReport.inspectBuiltInProvider();
        Map<String, String> fields = report.artifactFields("test.cuda.inventory");

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals("backend-provider:cuda", report.provider().providerId());
        assertEquals(GpuBackendTarget.CUDA, report.provider().backendTarget());
        assertEquals(GpuBackendTarget.CUDA, report.adapter().backendTarget());
        assertEquals("CUDA", report.adapter().backendName());
        assertEquals(GpuBackendTarget.CUDA, report.catalogEntry().backendTarget());
        assertFalse(report.catalogEntry().productionAdapter());
        assertTrue(report.catalogEntry().executionSupport().isPresent());
        assertFalse(report.provider().executionSupport().productionExecution());
        assertFalse(report.provider().executionSupport().executionPipelineAvailable());
        assertTrue(report.provider().executionPipelineFactory().isEmpty());
        assertTrue(report.provider().executionSupport().declaresModuleFormat(GpuBackendModuleFormat.CUDA_C));
        assertTrue(report.provider().executionSupport().declaresModuleFormat(GpuBackendModuleFormat.PTX));
        assertTrue(report.provider().executionSupport().declaresCapability(GpuRuntimeCapability.COMPUTE_CAPABILITY));
        assertEquals("cuda-irgpu-source-unavailable", report.lowererSourceSelectionPlan().selectedSource());
        assertEquals("irgpu-missing", report.lowererSourceSelectionPlan().payloadFormat());
        assertEquals("cuda-source-preview-unavailable", report.lowererSourceSelectionPlan().runtimeLoadMode());
        assertTrue(report.lowererSourceSelectionPlan().blockers().contains("cuda-irgpu-artifact-missing"));
        assertEquals("ready", fields.get("runtime.cuda.inventoryContract.status"));
        assertEquals("false", fields.get("runtime.cuda.inventoryContract.provider.executionPipeline.available"));
        assertEquals("cuda-irgpu-source-unavailable", fields.get("runtime.cuda.inventoryContract.lowerer.selectedSource"));
        assertTrue(report.toMarkdown().contains("CUDA inventory contract: ready"));
        assertTrue(CudaInventoryContractCli.render(report).contains("lowererSelectedSource=cuda-irgpu-source-unavailable"));
    }

    @Test
    void blocksIfCudaCatalogEntryPretendsToBeProductionBeforeExecutionExists() {
        GpuRuntimeBackendProvider provider = new CudaRuntimeBackendProvider();
        GpuRuntimeBackendAdapter adapter = provider.createAdapter();
        GpuRuntimeBackendCatalogEntry productionEntry = GpuRuntimeBackendCatalogEntry.owned(
                GpuBackendTarget.CUDA,
                "CUDA",
                () -> provider.createAdapter().catalogEntry().factory().create(),
                true,
                GpuRuntimeBackendExecutionSupport.discoveryOnly(
                        GpuBackendTarget.CUDA,
                        provider.providerId(),
                        "test production catalog mismatch"
                ),
                "test production catalog mismatch"
        );

        CudaInventoryContractReport report = new CudaInventoryContractReport(
                provider,
                adapter,
                productionEntry,
                adapter.lowerer().sourceSelectionPlan(new net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest(
                        new net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor(
                                "kernel",
                                "kernel.cu",
                                "extern \"C\" __global__ void kernel() {}",
                                java.util.List.of()
                        ),
                        net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                        net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
                )),
                Map.of(),
                Map.of()
        );

        assertEquals("blocked", report.status());
        assertTrue(report.blockers().contains("cuda-catalog-production-enabled-before-execution-slice"));
    }
}
