package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenClKernelLaunchAdvisoryDriftValidatorCliTest {

    @Test
    void acceptsAdvisoryStatusesAndRejectsRegression() throws Exception {
        Path artifact = Files.createTempFile("javatogpu-launch-advisory-drift", ".properties");
        Files.writeString(artifact, String.join("\n",
                "status=changed",
                "regression=false",
                "diagnostic=non-blocking drift",
                ""
        ));
        assertDoesNotThrow(() -> OpenClKernelLaunchAdvisoryDriftValidatorCli.main(
                new String[]{artifact.toString()}
        ));

        Files.writeString(artifact, String.join("\n",
                "status=regressed",
                "regression=true",
                "diagnostic=non-preferred count increased",
                ""
        ));
        assertThrows(
                IllegalStateException.class,
                () -> OpenClKernelLaunchAdvisoryDriftValidatorCli.main(new String[]{artifact.toString()})
        );
    }

    @Test
    void rejectsMissingOrUnknownArtifacts() throws Exception {
        Path missing = Files.createTempDirectory("javatogpu-launch-advisory-drift-missing")
                .resolve("missing.properties");
        assertThrows(
                IllegalStateException.class,
                () -> OpenClKernelLaunchAdvisoryDriftValidatorCli.main(new String[]{missing.toString()})
        );

        Path unknown = Files.createTempFile("javatogpu-launch-advisory-drift-unknown", ".properties");
        Files.writeString(unknown, "status=unknown\nregression=false\n");
        assertThrows(
                IllegalStateException.class,
                () -> OpenClKernelLaunchAdvisoryDriftValidatorCli.main(new String[]{unknown.toString()})
        );
    }
}
