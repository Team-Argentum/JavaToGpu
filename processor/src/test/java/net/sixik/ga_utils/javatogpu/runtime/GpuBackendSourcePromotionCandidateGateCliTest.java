package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourcePromotionCandidateGateCliTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesReviewReadyCandidateArtifact() throws Exception {
        Path workloadGate = writeWorkloadGate("kernel-a.cl", "kernel-b.cl");
        Path controlledSummary = writeControlledSummary("kernel-a.cl", "kernel-b.cl");
        Path output = tempDirectory.resolve("ready/candidate.properties");

        assertDoesNotThrow(() -> GpuBackendSourcePromotionCandidateGateCli.main(new String[]{
                workloadGate.toString(),
                controlledSummary.toString(),
                output.toString()
        }));

        String artifact = Files.readString(output);
        assertTrue(artifact.contains("status=review-ready"));
        assertTrue(artifact.contains("candidateReady.count=2"));
        assertTrue(artifact.contains("operatorAcceptance.accepted.count=2"));
        assertTrue(artifact.contains("operatorAcceptance.bound.count=2"));
        assertTrue(artifact.contains("defaultProductionSourceSwitching=disabled"));
        assertTrue(artifact.contains("productionMutation=disabled"));
    }

    @Test
    void writesBlockedArtifactBeforeFailing() throws Exception {
        Path workloadGate = writeWorkloadGate("kernel-a.cl", "kernel-b.cl");
        Path controlledSummary = writeControlledSummary("kernel-a.cl");
        Path output = tempDirectory.resolve("blocked/candidate.properties");

        assertThrows(IllegalStateException.class, () -> GpuBackendSourcePromotionCandidateGateCli.main(new String[]{
                workloadGate.toString(),
                controlledSummary.toString(),
                output.toString()
        }));

        String artifact = Files.readString(output);
        assertTrue(artifact.contains("status=blocked"));
        assertTrue(artifact.contains("candidateReady.count=1"));
        assertTrue(artifact.contains("blocker.0=controlled-kernel-evidence-missing"));
        assertTrue(artifact.contains("candidateProductionSourceSwitching=blocked"));
    }

    private Path writeWorkloadGate(String... resources) throws Exception {
        Path path = tempDirectory.resolve("workload-" + resources.length + ".properties");
        StringBuilder properties = new StringBuilder()
                .append("status=review-ready\n")
                .append("reviewReady=true\n")
                .append("kernel.count=").append(resources.length).append('\n');
        for (int index = 0; index < resources.length; index++) {
            String prefix = "kernel." + index + ".";
            properties.append(prefix).append("sourceKernelResource=").append(resources[index]).append('\n');
            properties.append(prefix).append("reviewReady=true\n");
            properties.append(prefix).append("sourceParityMatched=true\n");
            properties.append(prefix).append("runtimeEquivalencePassed=true\n");
        }
        Files.writeString(path, properties);
        return path;
    }

    private Path writeControlledSummary(String... resources) throws Exception {
        Path path = tempDirectory.resolve("controlled-" + resources.length + ".properties");
        StringBuilder properties = new StringBuilder()
                .append("status=passed\n")
                .append("reviewReady=true\n")
                .append("operatorAcceptance.mode=identity-bound\n")
                .append("operatorAcceptance.bound=true\n")
                .append("operatorAcceptance.deviceVendor=NVIDIA Corporation\n")
                .append("operatorAcceptance.deviceLabel=NVIDIA CUDA / RTX 5070\n")
                .append("operatorAcceptance.driverVersion=595.97\n")
                .append("kernel.count=").append(resources.length).append('\n');
        for (int index = 0; index < resources.length; index++) {
            String prefix = "kernel." + index + ".";
            properties.append(prefix).append("resource=").append(resources[index]).append('\n');
            properties.append(prefix).append("status=passed\n");
            properties.append(prefix).append("operatorAcceptance.id=acceptance:").append(resources[index]).append('\n');
            properties.append(prefix).append("operatorAcceptance.status=accepted\n");
            properties.append(prefix).append("operatorAcceptance.bound=true\n");
        }
        Files.writeString(path, properties);
        return path;
    }
}
