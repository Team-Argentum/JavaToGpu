package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.OpenClRuntimeBackendProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClBackendSpiContractReportTest {
    @Test
    void builtInOpenClProviderExposesStableExecutionContractWithoutNativeRuntime() {
        OpenClBackendSpiContractReport report = OpenClBackendSpiContractReport.inspectBuiltInProvider();
        Map<String, String> fields = report.artifactFields("test.opencl.spi");

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals("backend-provider:opencl", report.providerId());
        assertEquals(GpuBackendTarget.OPENCL, report.backendTarget());
        assertTrue(report.executionSupport().productionExecution());
        assertTrue(report.executionSupport().executionPipelineAvailable());
        assertTrue(report.executionPipelineFactory().isPresent());
        assertEquals("backend-execution-pipeline:opencl", report.executionPipelineFactory().orElseThrow().factoryId());
        assertEquals(OpenClGpuRuntimeBackend.class, report.executionPipelineFactory().orElseThrow().backendType());
        assertEquals("opencl-c", report.executionSupport().moduleFormatKeys());
        assertTrue(report.executionSupport().supportedStageKeys().contains("compile"));
        assertTrue(report.executionSupport().supportedStageKeys().contains("prepare"));
        assertTrue(report.executionSupport().supportedStageKeys().contains("invoke"));

        assertEquals("true", fields.get("runtime.opencl.spiContract.present"));
        assertEquals("ready", fields.get("runtime.opencl.spiContract.status"));
        assertEquals("backend-provider:opencl", fields.get("runtime.opencl.spiContract.providerId"));
        assertEquals("OPENCL", fields.get("runtime.opencl.spiContract.backendTarget"));
        assertEquals("true", fields.get("runtime.opencl.spiContract.executionPipeline.available"));
        assertEquals("true", fields.get("runtime.opencl.spiContract.executionPipeline.factory.present"));
        assertEquals("none", fields.get("runtime.opencl.spiContract.firstBlocker"));
        assertEquals("true", fields.get("test.opencl.spi.provider.runtime.backend.provider.present"));
        assertEquals("true", fields.get("test.opencl.spi.provider.runtime.backend.executionPipeline.factory.present"));
        assertEquals("backend-execution-pipeline:opencl", fields.get("test.opencl.spi.executionPipelineFactory.id"));
        assertTrue(report.toMarkdown().contains("OpenCL backend SPI contract: ready"));
    }

    @Test
    void reportBlocksProviderThatDoesNotExposeOpenClExecutionPipeline() {
        GpuRuntimeBackendProvider provider = new GpuRuntimeBackendProvider() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public String providerId() {
                return "backend-provider:opencl";
            }

            @Override
            public String providerVersion() {
                return "test";
            }

            @Override
            public int providerOrder() {
                return 0;
            }

            @Override
            public GpuRuntimeBackendAdapter createAdapter() {
                return new OpenClRuntimeBackendProvider().createAdapter();
            }
        };

        OpenClBackendSpiContractReport report = OpenClBackendSpiContractReport.inspect(provider);

        assertEquals("blocked", report.status());
        assertFalse(report.ready());
        assertTrue(report.blockers().contains("opencl-production-execution-not-declared"));
        assertTrue(report.blockers().contains("opencl-stage-missing:compile"));
        assertTrue(report.blockers().contains("opencl-execution-pipeline-factory-missing"));
    }
}
