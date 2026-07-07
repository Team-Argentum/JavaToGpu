package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuBackendSourcePromotionWorkloadGateFormatterTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesKernelEntriesAndAggregatesBlockerFamilies() throws IOException {
        Path gateFile = tempDir.resolve("backend-source-promotion-workload-gate.properties");

        writeGate(gateFile, GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-a.cl",
                blockedGateProperties(
                        "backend source must be reconstructed from IrGpu before promotion review",
                        "runtime equivalence must execute and pass before backend source promotion"
                )
        ));

        Properties gate = loadProperties(GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-b.cl",
                blockedGateProperties(
                        "reconstructed source must match descriptor source before promotion review"
                )
        ));

        assertEquals("blocked", gate.getProperty("status"));
        assertEquals("false", gate.getProperty("reviewReady"));
        assertEquals("runtime-snapshot", gate.getProperty("realWorkloadEvidence"));
        assertEquals("real-workload", gate.getProperty("scope"));
        assertEquals("false", gate.getProperty("productionSourceSwitching"));
        assertEquals("2", gate.getProperty("kernel.count"));
        assertEquals("kernel-a.cl", gate.getProperty("kernel.0.sourceKernelResource"));
        assertEquals("kernel-b.cl", gate.getProperty("kernel.1.sourceKernelResource"));
        assertEquals("3", gate.getProperty("blockerFamily.count"));
        assertEquals("reconstruction", gate.getProperty("blockerFamily.0.name"));
        assertEquals("1", gate.getProperty("blockerFamily.0.count"));
        assertEquals("runtime-equivalence", gate.getProperty("blockerFamily.1.name"));
        assertEquals("1", gate.getProperty("blockerFamily.1.count"));
        assertEquals("source-parity", gate.getProperty("blockerFamily.2.name"));
        assertEquals("1", gate.getProperty("blockerFamily.2.count"));
        assertEquals("1", gate.getProperty("kernel.0.reconstruction.blocker.count"));
        assertEquals("irgpu-artifact-missing", gate.getProperty("kernel.0.reconstruction.blocker.0"));
        assertEquals("1", gate.getProperty("kernel.0.reconstruction.diagnostic.count"));
        assertEquals(
                "OpenCL reconstruction preview skipped because no IrGpu artifact was available",
                gate.getProperty("kernel.0.reconstruction.diagnostic.0")
        );
        assertEquals("1", gate.getProperty("kernel.1.reconstruction.blocker.count"));
        assertEquals("irgpu-artifact-missing", gate.getProperty("kernel.1.reconstruction.blocker.0"));
        assertEquals("reconstruction", gate.getProperty("kernel.0.blockerFamily.0.name"));
        assertEquals("runtime-equivalence", gate.getProperty("kernel.0.blockerFamily.1.name"));
        assertEquals("source-parity", gate.getProperty("kernel.1.blockerFamily.0.name"));
    }

    @Test
    void updatesExistingKernelResourceInsteadOfDuplicatingIt() throws IOException {
        Path gateFile = tempDir.resolve("backend-source-promotion-workload-gate.properties");

        writeGate(gateFile, GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-a.cl",
                blockedGateProperties(
                        "runtime equivalence must execute and pass before backend source promotion"
                )
        ));

        Properties gate = loadProperties(GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-a.cl",
                blockedGateProperties(
                        "fallback descriptor source must remain clean before backend source promotion"
                )
        ));

        assertEquals("1", gate.getProperty("kernel.count"));
        assertEquals("kernel-a.cl", gate.getProperty("kernel.0.sourceKernelResource"));
        assertEquals("1", gate.getProperty("blockerFamily.count"));
        assertEquals("fallback-clean", gate.getProperty("blockerFamily.0.name"));
        assertEquals("1", gate.getProperty("blockerFamily.0.count"));
        assertEquals("fallback-clean", gate.getProperty("kernel.0.blockerFamily.0.name"));
        assertEquals(
                "fallback descriptor source must remain clean before backend source promotion",
                gate.getProperty("kernel.0.diagnostic.0")
        );
    }

    private static void writeGate(Path gateFile, String properties) throws IOException {
        Files.writeString(gateFile, properties, StandardCharsets.UTF_8);
    }

    private static Properties loadProperties(String propertiesText) throws IOException {
        Properties properties = new Properties();
        try (StringReader reader = new StringReader(propertiesText)) {
            properties.load(reader);
        }
        return properties;
    }

    private static String blockedGateProperties(String... diagnostics) {
        StringBuilder builder = new StringBuilder();
        builder.append("status=blocked\n");
        builder.append("reviewReady=false\n");
        builder.append("reconstructed=false\n");
        builder.append("sourceAvailable=false\n");
        builder.append("sourceParityChecked=false\n");
        builder.append("sourceParityMatched=false\n");
        builder.append("runtimeEquivalencePassed=false\n");
        builder.append("fallbackClean=true\n");
        builder.append("selectedSource=descriptor-opencl-source\n");
        builder.append("payloadFormat=unknown\n");
        builder.append("runtimeLoadMode=opencl-descriptor-source-compile\n");
        builder.append("reconstruction.blocker.count=1\n");
        builder.append("reconstruction.blocker.0=irgpu-artifact-missing\n");
        builder.append("reconstruction.diagnostic.count=1\n");
        builder.append("reconstruction.diagnostic.0=OpenCL reconstruction preview skipped because no IrGpu artifact was available\n");
        builder.append("diagnostic.count=").append(diagnostics.length).append('\n');
        for (int index = 0; index < diagnostics.length; index++) {
            builder.append("diagnostic.").append(index).append('=').append(diagnostics[index]).append('\n');
        }
        return builder.toString();
    }
}
