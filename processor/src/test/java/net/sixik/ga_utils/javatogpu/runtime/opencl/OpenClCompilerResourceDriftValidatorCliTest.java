package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenClCompilerResourceDriftValidatorCliTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsStableArtifact() throws Exception {
        Path artifact = temporaryDirectory.resolve("stable.properties");
        Files.writeString(artifact, "status=stable\nregression=false\ndiagnostic=unchanged\n");

        assertDoesNotThrow(() -> OpenClCompilerResourceDriftValidatorCli.main(
                new String[]{artifact.toString()}
        ));
    }

    @Test
    void rejectsRegressionArtifact() throws Exception {
        Path artifact = temporaryDirectory.resolve("regressed.properties");
        Files.writeString(artifact, "status=regressed\nregression=true\ndiagnostic=spills increased\n");

        assertThrows(
                IllegalStateException.class,
                () -> OpenClCompilerResourceDriftValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsMissingArtifact() {
        assertThrows(
                IllegalStateException.class,
                () -> OpenClCompilerResourceDriftValidatorCli.main(
                        new String[]{temporaryDirectory.resolve("missing.properties").toString()}
                )
        );
    }
}
