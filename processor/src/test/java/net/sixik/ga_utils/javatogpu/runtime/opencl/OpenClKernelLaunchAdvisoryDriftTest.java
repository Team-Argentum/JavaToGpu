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
        assertFalse(drift.kernelComparisonAvailable());
    }

    @Test
    void detectsPerKernelStatusSwapWithStableAggregateCounts() {
        OpenClValidationHistoryEntry previous = entry(
                "GPU A",
                "Driver 1",
                historySummary(
                        advisory("workload/a.cl", "aligned", "64", "256", "32", "true", "false"),
                        advisory("workload/b.cl", "non-preferred-multiple", "48", "256", "32", "false", "false")
                ),
                Instant.parse("2026-07-10T10:00:00Z")
        );
        OpenClValidationHistoryEntry current = entry(
                "GPU A",
                "Driver 2",
                historySummary(
                        advisory("workload/a.cl", "non-preferred-multiple", "48", "256", "32", "false", "false"),
                        advisory("workload/b.cl", "aligned", "64", "256", "32", "true", "false")
                ),
                Instant.parse("2026-07-10T11:00:00Z")
        );

        OpenClKernelLaunchAdvisoryDrift drift = OpenClKernelLaunchAdvisoryDrift.compare(
                current,
                List.of(previous)
        );

        assertEquals("regressed", drift.status());
        assertTrue(drift.regression());
        assertTrue(drift.kernelComparisonAvailable());
        assertEquals(2, drift.kernelChanges().size());
        assertTrue(drift.toPropertiesText().contains("kernelChange.regressed.count=1"));
        assertTrue(drift.toPropertiesText().contains("kernelChange.improved.count=1"));
        assertTrue(drift.toMarkdown().contains("`workload/a.cl` | `regressed`"));
    }

    @Test
    void detectsKernelMaxDecreaseWithoutAggregateCountChange() {
        OpenClValidationHistoryEntry previous = entry(
                "GPU A",
                "Driver 1",
                historySummary(advisory(
                        "workload/grid.cl", "aligned", "64", "256", "32", "true", "false"
                )),
                Instant.parse("2026-07-10T10:00:00Z")
        );
        OpenClValidationHistoryEntry current = entry(
                "GPU A",
                "Driver 2",
                historySummary(advisory(
                        "workload/grid.cl", "aligned", "64", "64", "32", "true", "false"
                )),
                Instant.parse("2026-07-10T11:00:00Z")
        );

        OpenClKernelLaunchAdvisoryDrift drift = OpenClKernelLaunchAdvisoryDrift.compare(
                current,
                List.of(previous)
        );

        assertEquals("regressed", drift.status());
        assertTrue(drift.regression());
        assertEquals(1, drift.kernelChanges().size());
        assertTrue(drift.kernelChanges().get(0).diagnostic().contains("kernel max 256 -> 64"));
    }

    private static String historySummary(OpenClKernelLaunchAdvisorySummary.Entry... entries) {
        return new OpenClKernelLaunchAdvisorySummary("recorded", List.of(entries), "").toHistorySummary();
    }

    private static OpenClKernelLaunchAdvisorySummary.Entry advisory(
            String resource,
            String status,
            String size,
            String kernelMax,
            String preferred,
            String matched,
            String blocking
    ) {
        String shape = "driver-selected".equals(status) ? "driver-selected" : size;
        return new OpenClKernelLaunchAdvisorySummary.Entry(
                resource,
                status,
                shape,
                size,
                kernelMax,
                preferred,
                matched,
                blocking
        );
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
