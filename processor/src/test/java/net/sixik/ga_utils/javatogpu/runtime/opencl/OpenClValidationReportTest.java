package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClValidationReportTest {

    @Test
    void markdownIncludesKeyRuntimeSections() {
        OpenClValidationReport report = new OpenClValidationReport(
                Instant.parse("2026-06-24T12:00:00Z"),
                "OpenCL",
                "INSTANCE",
                "Mock GPU",
                "Mock Vendor",
                "1.2.3",
                "OpenCL 3.0 Mock",
                "Mock Platform",
                "OpenCL 3.0 Platform",
                true,
                true,
                false,
                32768,
                256,
                new OpenClRuntimeStatistics(3, 1, 2, 1, 4)
        );

        String markdown = report.toMarkdown();

        assertTrue(markdown.contains("# OpenCL Validation Report"));
        assertTrue(markdown.contains("- Backend: `OpenCL`"));
        assertTrue(markdown.contains("- Device label: `Mock GPU`"));
        assertTrue(markdown.contains("- Vendor: `Mock Vendor`"));
        assertTrue(markdown.contains("- Double precision: `yes`"));
        assertTrue(markdown.contains("- 3D image writes: `no`"));
        assertTrue(markdown.contains("- Compile cache hits: `2`"));
    }

    @Test
    void backendValidationReportUsesRuntimeDeviceInfo() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClValidationDeviceInfo runtimeValidationDeviceInfo() {
                return new OpenClValidationDeviceInfo(
                        "Shared Mock GPU",
                        "Vendor X",
                        "Driver 99",
                        "OpenCL 2.1 Vendor X",
                        "Platform X",
                        "OpenCL 2.1 Platform X",
                        true,
                        false,
                        false,
                        65536,
                        512
                );
            }
        };

        OpenClValidationReport report = backend.validationReport();

        assertEquals("OpenCL (shared cache)", report.backendName());
        assertEquals("SHARED", report.cacheMode());
        assertEquals("Shared Mock GPU", report.deviceLabel());
        assertEquals("Vendor X", report.vendor());
        assertEquals(0L, report.statistics().invocationCount());
        assertEquals(0L, report.statistics().compileCount());
    }
}
