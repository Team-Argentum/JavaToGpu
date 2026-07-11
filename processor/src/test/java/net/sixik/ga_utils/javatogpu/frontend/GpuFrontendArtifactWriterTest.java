package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuFrontendArtifactWriterTest {

    @Test
    void writesOpenClAndIrGpuArtifactsThroughOnePackagingSink() throws Exception {
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.sourceFrontendV1("asm"),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "jtg_kernel",
                                "body\n  return\n",
                                List.of()
                        ))
                ),
                List.of(new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of("__global"))),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
        GpuFrontendCompilationResult result = new GpuFrontendCompilationResult(
                "__kernel void jtg_kernel(__global float* output) { output[0] = 1.0f; }",
                artifact
        );
        Map<String, StringWriter> written = new LinkedHashMap<>();

        GpuFrontendArtifactWriter.write(result, resourcePath -> {
            StringWriter writer = new StringWriter();
            written.put(resourcePath, writer);
            return nonClosing(writer);
        });

        assertEquals(List.of(
                "javatogpu/sample/Demo/kernel.cl",
                "javatogpu/sample/Demo/kernel.irgpu.properties"
        ), List.copyOf(written.keySet()));
        assertEquals(result.openClSource(), written.get("javatogpu/sample/Demo/kernel.cl").toString());

        String manifest = written.get("javatogpu/sample/Demo/kernel.irgpu.properties").toString();
        assertTrue(manifest.contains("format=javatogpu.irgpu.v1"));
        assertTrue(manifest.contains("sourceFrontend=asm"));
        assertTrue(manifest.contains("entryMethod=kernel"));
        assertTrue(manifest.contains("derived.opencl.resource=javatogpu/sample/Demo/kernel.cl"));
        assertTrue(manifest.contains("entryParameter.0.name=output"));
        assertTrue(manifest.contains("extensionParticipation.count=1"));
        assertTrue(manifest.contains("extensionParticipation.0.source=artifact-writer"));
        assertTrue(manifest.contains("extensionParticipation.0.extensionId=artifact-writer:frontend"));
        assertTrue(manifest.contains("extensionParticipation.0.phase=ARTIFACT_EMISSION"));
        assertTrue(manifest.contains("extensionParticipation.0.permission=READ_ONLY"));
        assertTrue(manifest.contains("extensionParticipation.0.outcome=SUCCEEDED"));
        assertTrue(manifest.contains("extensionParticipation.0.diagnostic.0=openClResource=javatogpu/sample/Demo/kernel.cl"));
        assertTrue(manifest.contains("extensionParticipation.0.diagnostic.1=irGpuResource=javatogpu/sample/Demo/kernel.irgpu.properties"));
    }

    private Writer nonClosing(StringWriter writer) {
        return new java.io.FilterWriter(writer) {
            @Override
            public void close() {
            }
        };
    }
}
