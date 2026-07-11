package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClCompilerResourceDriftTest {

    @Test
    void identicalPerKernelResourcesRemainStable() {
        String summary = summary(entry("kernel.cl", 38, 0, 0, 0));
        OpenClCompilerResourceDrift drift = OpenClCompilerResourceDrift.compare(
                history("2026-07-10T12:00:00Z", "595.97", summary),
                List.of(history("2026-07-09T12:00:00Z", "595.90", summary))
        );

        assertEquals("stable", drift.status());
        assertFalse(drift.regression());
        assertTrue(drift.kernelComparisonAvailable());
        assertTrue(drift.kernelChanges().isEmpty());
    }

    @Test
    void smallRegisterIncreaseStaysWithinTolerance() {
        OpenClCompilerResourceDrift drift = OpenClCompilerResourceDrift.compare(
                history("2026-07-10T12:00:00Z", "595.97", summary(entry("kernel.cl", 40, 0, 0, 0))),
                List.of(history("2026-07-09T12:00:00Z", "595.90", summary(entry("kernel.cl", 38, 0, 0, 0))))
        );

        assertEquals("changed", drift.status());
        assertFalse(drift.regression());
        assertEquals("changed", drift.kernelChanges().get(0).classification());
    }

    @Test
    void registerIncreaseAboveToleranceIsARegression() {
        OpenClCompilerResourceDrift drift = OpenClCompilerResourceDrift.compare(
                history("2026-07-10T12:00:00Z", "595.97", summary(entry("kernel.cl", 44, 0, 0, 0))),
                List.of(history("2026-07-09T12:00:00Z", "595.90", summary(entry("kernel.cl", 38, 0, 0, 0))))
        );

        assertEquals("regressed", drift.status());
        assertTrue(drift.regression());
        assertEquals("regressed", drift.kernelChanges().get(0).classification());
    }

    @Test
    void newSpillsAreARegression() {
        OpenClCompilerResourceDrift drift = OpenClCompilerResourceDrift.compare(
                history("2026-07-10T12:00:00Z", "595.97", summary(entry("kernel.cl", 38, 8, 4, 0))),
                List.of(history("2026-07-09T12:00:00Z", "595.90", summary(entry("kernel.cl", 38, 0, 0, 0))))
        );

        assertEquals("regressed", drift.status());
        assertTrue(drift.regression());
        assertTrue(drift.kernelChanges().get(0).diagnostic().contains("spill stores 0 -> 8"));
    }

    @Test
    void losingPreviouslyAvailableMetricsIsARegression() {
        OpenClCompilerResourceDrift drift = OpenClCompilerResourceDrift.compare(
                history("2026-07-10T12:00:00Z", "595.97", summary(new OpenClCompilerResourceSummary.Entry(
                        "kernel.cl", "unavailable", "none", "none", -1, -1, -1, -1
                ))),
                List.of(history("2026-07-09T12:00:00Z", "595.90", summary(entry("kernel.cl", 38, 0, 0, 0))))
        );

        assertEquals("regressed", drift.status());
        assertTrue(drift.regression());
        assertTrue(drift.kernelChanges().get(0).diagnostic().contains("status recorded -> unavailable"));
    }

    @Test
    void newlyAvailableMetricsAreAnImprovement() {
        OpenClCompilerResourceDrift drift = OpenClCompilerResourceDrift.compare(
                history("2026-07-10T12:00:00Z", "595.97", summary(entry("kernel.cl", 38, 0, 0, 0))),
                List.of(history("2026-07-09T12:00:00Z", "595.90", summary(entry("kernel.cl", -1, -1, -1, -1))))
        );

        assertEquals("improved", drift.status());
        assertFalse(drift.regression());
        assertEquals(-1, drift.previous().totalSpillBytes());
        assertEquals(0, drift.current().totalSpillBytes());
        assertEquals("improved", drift.kernelChanges().get(0).classification());
    }

    private static OpenClCompilerResourceSummary.Entry entry(
            String resource,
            int registers,
            int spillStores,
            int spillLoads,
            int stack
    ) {
        return new OpenClCompilerResourceSummary.Entry(
                resource,
                "recorded",
                "compiler-feedback:generic",
                "ptxas",
                registers,
                spillStores,
                spillLoads,
                stack
        );
    }

    private static String summary(OpenClCompilerResourceSummary.Entry... entries) {
        return new OpenClCompilerResourceSummary("recorded", List.of(entries), "").toHistorySummary();
    }

    private static OpenClValidationHistoryEntry history(String timestamp, String driver, String compilerSummary) {
        return new OpenClValidationHistoryEntry(
                Instant.parse(timestamp),
                "nvidia",
                "OpenCL",
                "NVIDIA CUDA / RTX 5070",
                "NVIDIA Corporation",
                driver,
                "OpenCL 3.0 CUDA",
                "passed",
                "passed",
                "passed",
                "passed",
                "passed",
                "review-ready",
                "review-ready",
                "review-ready",
                "not recorded",
                compilerSummary
        );
    }
}
