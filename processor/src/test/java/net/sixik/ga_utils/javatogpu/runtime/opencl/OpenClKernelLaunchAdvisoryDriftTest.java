package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClKernelLaunchAdvisoryDriftTest {

    @Test
    void classifiesNonPreferredIncreaseAsRegression() {
        OpenClValidationHistoryEntry previous = entry(
                "GPU A",
                "Driver 1",
                "recorded (kernels=5, aligned=2, nonPreferred=0, driverSelected=3, unavailable=0, missing=0, blocking=0)",
                Instant.parse("2026-07-10T10:00:00Z")
        );
        OpenClValidationHistoryEntry current = entry(
                "GPU A",
                "Driver 2",
                "recorded (kernels=5, aligned=1, nonPreferred=1, driverSelected=3, unavailable=0, missing=0, blocking=0)",
                Instant.parse("2026-07-10T11:00:00Z")
        );

        OpenClKernelLaunchAdvisoryDrift drift = OpenClKernelLaunchAdvisoryDrift.compare(
                current,
                List.of(previous)
        );

        assertEquals("regressed", drift.status());
        assertTrue(drift.regression());
        assertEquals("Driver 1", drift.previousDriverVersion());
        assertTrue(drift.toMarkdown().contains("nonPreferred=+1"));
        assertTrue(drift.toPropertiesText().contains("status=regressed"));
        assertTrue(drift.toPropertiesText().contains("delta.nonPreferred=1"));
    }

    @Test
    void reportsStableCountsAndIgnoresAnotherDevice() {
        String counts = "recorded (kernels=5, aligned=1, nonPreferred=1, driverSelected=3, unavailable=0, missing=0, blocking=0)";
        OpenClValidationHistoryEntry current = entry(
                "GPU A",
                "Driver 2",
                counts,
                Instant.parse("2026-07-10T11:00:00Z")
        );
        OpenClValidationHistoryEntry sameDevice = entry(
                "GPU A",
                "Driver 1",
                counts,
                Instant.parse("2026-07-10T10:00:00Z")
        );
        OpenClValidationHistoryEntry anotherDevice = entry(
                "GPU B",
                "Driver 9",
                "recorded (kernels=5, aligned=0, nonPreferred=5, driverSelected=0, unavailable=0, missing=0, blocking=0)",
                Instant.parse("2026-07-10T10:30:00Z")
        );

        OpenClKernelLaunchAdvisoryDrift drift = OpenClKernelLaunchAdvisoryDrift.compare(
                current,
                List.of(anotherDevice, sameDevice)
        );

        assertEquals("stable", drift.status());
        assertFalse(drift.regression());
        assertEquals("Driver 1", drift.previousDriverVersion());
    }

    private static OpenClValidationHistoryEntry entry(
            String device,
            String driver,
            String advisoryStatus,
            Instant generatedAt
    ) {
        return new OpenClValidationHistoryEntry(
                generatedAt,
                "nvidia",
                "OpenCL",
                device,
                "NVIDIA Corporation",
                driver,
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
