package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourcePromotionActivationGateCliTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesControlledActivationReadyArtifact() throws Exception {
        Path candidate = write("candidate.properties", GpuBackendSourcePromotionManifestTest.candidateText());
        Path manifestValidation = write(
                "manifest-validation.properties",
                GpuBackendSourcePromotionActivationGateTest.manifestValidationText()
        );
        Path controlled = write(
                "controlled.properties",
                GpuBackendSourcePromotionActivationGateTest.controlledSourceSwitchingText(
                        "kernel-a.cl",
                        "kernel-b.cl"
                )
        );
        Path output = tempDirectory.resolve("activation/activation-gate.properties");

        assertDoesNotThrow(() -> GpuBackendSourcePromotionActivationGateCli.main(new String[]{
                candidate.toString(),
                manifestValidation.toString(),
                controlled.toString(),
                output.toString()
        }));

        String artifact = Files.readString(output);
        assertTrue(artifact.contains("status=controlled-activation-ready"));
        assertTrue(artifact.contains("activationReady=true"));
        assertTrue(artifact.contains("controlledCoverage.count=2"));
        assertTrue(artifact.contains("defaultRuntimeActivation=false"));
        assertTrue(artifact.contains("blocker.count=0"));
    }

    @Test
    void writesBlockedArtifactBeforeFailing() throws Exception {
        Path candidate = write("candidate.properties", GpuBackendSourcePromotionManifestTest.candidateText());
        Path manifestValidation = write(
                "manifest-validation.properties",
                GpuBackendSourcePromotionActivationGateTest.manifestValidationText()
        );
        Path controlled = write(
                "controlled-missing.properties",
                GpuBackendSourcePromotionActivationGateTest.controlledSourceSwitchingText("kernel-a.cl")
        );
        Path output = tempDirectory.resolve("activation/activation-blocked.properties");

        assertThrows(IllegalStateException.class, () -> GpuBackendSourcePromotionActivationGateCli.main(new String[]{
                candidate.toString(),
                manifestValidation.toString(),
                controlled.toString(),
                output.toString()
        }));

        String artifact = Files.readString(output);
        assertTrue(artifact.contains("status=blocked"));
        assertTrue(artifact.contains("activationReady=false"));
        assertTrue(artifact.contains("blocker.0=controlled-kernel-evidence-missing"));
    }

    private Path write(String name, String content) throws Exception {
        Path path = tempDirectory.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}
