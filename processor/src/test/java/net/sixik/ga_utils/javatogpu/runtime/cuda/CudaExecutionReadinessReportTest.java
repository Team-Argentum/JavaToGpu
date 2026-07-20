package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.CudaRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleFormat;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviderCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviders;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapability;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendSpiContractReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaExecutionReadinessReportTest {

    @Test
    void builtInsExposeCudaExecutionSkeletonButDoNotEnableProductionCudaExecution() {
        CudaExecutionReadinessReport report = CudaExecutionReadinessReport.inspectBuiltIns();
        Map<String, String> fields = report.artifactFields("test.cuda.readiness");

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(report.checklist().size(), report.checklistReadyCount());
        assertEquals(0, report.checklistBlockedCount());
        assertEquals("none", report.firstBlockedChecklistItem());
        assertEquals("ready", report.openClSpiContract().status());
        assertEquals("backend-provider:cuda", report.cudaProvider().orElseThrow().providerId());
        assertFalse(report.cudaProvider().orElseThrow().executionSupport().productionExecution());
        assertTrue(report.cudaProvider().orElseThrow().executionSupport().executionPipelineAvailable());
        assertTrue(report.cudaProvider().orElseThrow().executionPipelineFactory().isPresent());
        assertTrue(report.cudaProvider().orElseThrow().executionSupport().declaresModuleFormat(GpuBackendModuleFormat.CUDA_C));
        assertTrue(report.cudaProvider().orElseThrow().executionSupport().declaresModuleFormat(GpuBackendModuleFormat.PTX));
        assertTrue(report.cudaProvider().orElseThrow().executionSupport().declaresCapability(GpuRuntimeCapability.COMPUTE_CAPABILITY));
        assertEquals("execution-pipeline-available", report.cudaExecutionAvailability().orElseThrow().status());
        assertTrue(report.cudaExecutionAvailability().orElseThrow().blockers().isEmpty());
        assertEquals("SUCCEEDED", report.cudaUnsupportedReceipt().compilationResult().stageResult().status().name());
        assertTrue(report.cudaUnsupportedReceipt().compilationResult().compiled());
        assertTrue(report.cudaUnsupportedReceipt().compiledKernel() instanceof CudaCompiledKernel);
        assertFalse(((CudaCompiledKernel) report.cudaUnsupportedReceipt().compiledKernel()).nativeHandleAvailable());
        assertEquals("UNSUPPORTED", report.cudaUnsupportedReceipt().preparationResult().stageResult().status().name());
        assertTrue(report.cudaUnsupportedReceipt().preparationResult().stageResult().blockers()
                .contains("cuda-native-argument-binding-missing"));
        assertEquals("SKIPPED", report.cudaUnsupportedReceipt().invocationResult().stageResult().status().name());
        assertEquals("ready", fields.get("runtime.cuda.executionReadiness.status"));
        assertEquals("9", fields.get("runtime.cuda.executionReadiness.checklist.item.count"));
        assertEquals("9", fields.get("runtime.cuda.executionReadiness.checklist.ready.count"));
        assertEquals("0", fields.get("runtime.cuda.executionReadiness.checklist.blocked.count"));
        assertEquals("none", fields.get("runtime.cuda.executionReadiness.checklist.firstBlocked"));
        assertEquals("true", fields.get("runtime.cuda.executionReadiness.cuda.executionPipeline.available"));
        assertEquals("true", fields.get("runtime.cuda.executionReadiness.cuda.executionPipeline.factory.present"));
        assertEquals("SUCCEEDED", fields.get("runtime.cuda.executionReadiness.cuda.unsupportedReceipt.compile.status"));
        assertTrue(report.toMarkdown().contains("CUDA execution green-light checklist: ready"));
        assertTrue(report.toMarkdown().contains("cuda-vertical-slice-skeleton-present: ready"));
        assertTrue(report.toMarkdown().contains("cuda-native-bridge-fail-closed: ready"));
        assertTrue(CudaExecutionReadinessCli.render(report).contains("cudaPipelineAvailable=true"));
        assertTrue(CudaExecutionReadinessCli.render(report).contains(
                "unsupportedReceipt=compile:SUCCEEDED,prepare:UNSUPPORTED,invoke:SKIPPED"
        ));
        assertTrue(CudaExecutionReadinessCli.render(report).contains("checklistReady=9/9"));
        assertTrue(CudaExecutionReadinessCli.render(report).contains(
                "checklist.cuda-unsupported-receipt-structured=ready"
        ));
    }

    @Test
    void blocksIfCudaProviderDeclaresProductionExecutionBeforeNativeBridgeExists() {
        GpuRuntimeBackendProvider prematureCudaProvider = new GpuRuntimeBackendProvider() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.CUDA;
            }

            @Override
            public String providerId() {
                return "backend-provider:cuda";
            }

            @Override
            public String providerVersion() {
                return "test";
            }

            @Override
            public int providerOrder() {
                return 100;
            }

            @Override
            public GpuRuntimeBackendAdapter createAdapter() {
                return new CudaRuntimeBackendProvider().createAdapter();
            }

            @Override
            public GpuRuntimeBackendExecutionSupport executionSupport() {
                return GpuRuntimeBackendExecutionSupport.productionPipeline(
                        GpuBackendTarget.CUDA,
                        providerId(),
                        Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
                        Set.of(GpuRuntimeCapability.COMPUTE_CAPABILITY, GpuRuntimeCapability.GLOBAL_MEMORY),
                        "test provider prematurely declares CUDA execution"
                );
            }
        };
        GpuRuntimeBackendProviderCatalog catalog = GpuRuntimeBackendProviderCatalog.of(List.of(
                GpuRuntimeBackendProviders.builtInsWithPlannedBackends().get(0),
                prematureCudaProvider
        ));
        CudaExecutionReadinessReport report = new CudaExecutionReadinessReport(
                OpenClBackendSpiContractReport.inspectBuiltInProvider(),
                catalog,
                Optional.of(prematureCudaProvider),
                Optional.of(prematureCudaProvider.executionAvailability()),
                prematureCudaProvider.unsupportedExecutionResult(null)
        );

        assertEquals("blocked", report.status());
        assertTrue(report.checklistBlockedCount() > 0);
        assertEquals("cuda-vertical-slice-skeleton-present", report.firstBlockedChecklistItem());
        assertTrue(report.blockers().contains("cuda-production-execution-enabled-before-green-light"));
        assertTrue(report.blockers().contains("cuda-execution-skeleton-factory-missing"));
        assertTrue(report.blockers().contains("cuda-compile-preview-artifact-missing"));
        assertTrue(report.blockers().contains("cuda-compiled-kernel-type-missing"));
        assertTrue(report.blockers().contains("cuda-native-argument-binding-blocker-missing"));
    }
}
