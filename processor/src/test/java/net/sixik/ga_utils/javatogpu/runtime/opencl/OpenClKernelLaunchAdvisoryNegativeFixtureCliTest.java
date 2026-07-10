package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClKernelLaunchAdvisoryNegativeFixtureCliTest {

    @Test
    void raisesBaselineKernelMaxAndForcesValidatorRegression() throws Exception {
        Path baselineFile = Files.createTempFile("javatogpu-launch-advisory-negative-baseline", ".properties");
        String baselineSummary = historySummary("256");
        OpenClValidationHistoryEntry baseline = entry(
                Instant.parse("2026-07-10T10:00:00Z"),
                baselineSummary
        );
        OpenClValidationHistoryIO.writeAll(baselineFile, List.of(baseline));

        OpenClKernelLaunchAdvisoryNegativeFixtureCli.main(new String[]{baselineFile.toString()});

        OpenClValidationHistoryEntry mutatedBaseline = OpenClValidationHistoryIO.readAll(baselineFile).get(0);
        List<OpenClKernelLaunchAdvisorySummary.Entry> mutatedAdvisories =
                OpenClKernelLaunchAdvisorySummary.parseHistoryEntries(
                        mutatedBaseline.kernelLaunchAdvisoryStatus()
                ).orElseThrow();
        assertEquals("512", mutatedAdvisories.get(0).kernelMax());
        assertEquals(
                OpenClKernelLaunchAdvisorySummary.aggregateHistorySummary(baselineSummary),
                OpenClKernelLaunchAdvisorySummary.aggregateHistorySummary(
                        mutatedBaseline.kernelLaunchAdvisoryStatus()
                )
        );

        OpenClValidationHistoryEntry current = entry(
                Instant.parse("2026-07-10T11:00:00Z"),
                baselineSummary
        );
        OpenClKernelLaunchAdvisoryDrift drift = OpenClKernelLaunchAdvisoryDrift.compare(
                current,
                List.of(mutatedBaseline)
        );

        assertEquals("regressed", drift.status());
        assertTrue(drift.regression());
        assertTrue(drift.kernelComparisonAvailable());
        assertTrue(drift.kernelChanges().get(0).diagnostic().contains("kernel max 512 -> 256"));

        Path driftFile = Files.createTempFile("javatogpu-launch-advisory-negative-drift", ".properties");
        Files.writeString(driftFile, drift.toPropertiesText());
        assertThrows(
                IllegalStateException.class,
                () -> OpenClKernelLaunchAdvisoryDriftValidatorCli.main(
                        new String[]{driftFile.toString()}
                )
        );
    }

    @Test
    void rejectsMissingBaseline() throws Exception {
        Path missing = Files.createTempDirectory("javatogpu-launch-advisory-negative-missing")
                .resolve("validation-history.properties");

        assertThrows(
                IllegalStateException.class,
                () -> OpenClKernelLaunchAdvisoryNegativeFixtureCli.main(
                        new String[]{missing.toString()}
                )
        );
    }

    private static String historySummary(String kernelMax) {
        return new OpenClKernelLaunchAdvisorySummary(
                "recorded",
                List.of(new OpenClKernelLaunchAdvisorySummary.Entry(
                        "workload/grid.cl",
                        "aligned",
                        "8x8x1",
                        "64",
                        kernelMax,
                        "32",
                        "true",
                        "false"
                )),
                ""
        ).toHistorySummary();
    }

    private static OpenClValidationHistoryEntry entry(Instant generatedAt, String advisoryStatus) {
        return new OpenClValidationHistoryEntry(
                generatedAt,
                "nvidia",
                "OpenCL",
                "GPU A",
                "NVIDIA Corporation",
                "Driver 1",
                "OpenCL 3.0",
                "passed",
                "passed",
                "passed",
                "passed",
                "passed",
                "review-ready",
                "not-promoted",
                "blocked",
                advisoryStatus
        );
    }
}
