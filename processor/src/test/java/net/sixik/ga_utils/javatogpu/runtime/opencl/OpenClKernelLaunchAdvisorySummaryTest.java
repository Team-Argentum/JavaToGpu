package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClKernelLaunchAdvisorySummaryTest {

    @Test
    void aggregatesOnlyWorkloadGateKernelResources() throws Exception {
        Path reportDirectory = Files.createTempDirectory("javatogpu-launch-advisory-summary");
        Path workloadGate = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        Files.writeString(workloadGate, String.join("\n",
                "kernel.count=3",
                "kernel.0.sourceKernelResource=workload/perlin.cl",
                "kernel.1.sourceKernelResource=workload/grid.cl",
                "kernel.2.sourceKernelResource=workload/missing.cl",
                ""
        ));
        writeAdvisory(
                reportDirectory,
                "perlin",
                "workload/perlin.cl",
                "non-preferred-multiple",
                "48",
                "48",
                "256",
                "32",
                "false",
                "false"
        );
        writeAdvisory(
                reportDirectory,
                "grid",
                "workload/grid.cl",
                "aligned",
                "8x8x1",
                "64",
                "256",
                "32",
                "true",
                "false"
        );
        writeAdvisory(
                reportDirectory,
                "unrelated",
                "integration/unrelated.cl",
                "driver-selected",
                "driver-selected",
                "0",
                "256",
                "32",
                "false",
                "false"
        );

        OpenClKernelLaunchAdvisorySummary summary = OpenClKernelLaunchAdvisorySummary.read(workloadGate);
        String markdown = summary.toMarkdown();

        assertEquals("recorded", summary.status());
        assertEquals(3, summary.entries().size());
        assertEquals(1, summary.count("aligned"));
        assertEquals(1, summary.count("non-preferred-multiple"));
        assertEquals(1, summary.count("missing"));
        assertEquals(0, summary.count("driver-selected"));
        assertEquals(0, summary.blockingCount());
        assertEquals(
                "recorded (kernels=3, aligned=1, nonPreferred=1, driverSelected=0, unavailable=0, missing=1, blocking=0)",
                summary.toHistorySummary()
        );
        assertTrue(markdown.contains("## Kernel Launch Advisories"));
        assertTrue(markdown.contains("- Non-preferred multiple: `1`"));
        assertTrue(markdown.contains("`workload/perlin.cl` | `non-preferred-multiple`"));
        assertTrue(markdown.contains("`workload/missing.cl` | `missing`"));
        assertTrue(!markdown.contains("integration/unrelated.cl"));
    }

    private static void writeAdvisory(
            Path reportDirectory,
            String directory,
            String resource,
            String status,
            String shape,
            String size,
            String kernelMax,
            String preferred,
            String matched,
            String blocking
    ) throws Exception {
        Path artifactDirectory = reportDirectory.resolve("runtime-compile-artifacts").resolve(directory);
        Files.createDirectories(artifactDirectory);
        Files.writeString(
                artifactDirectory.resolve(OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME),
                String.join("\n",
                        "status=" + status,
                        "blocking=" + blocking,
                        "kernelResource=" + resource,
                        "requestedLocalWorkGroupShape=" + shape,
                        "requestedLocalWorkGroupSize=" + size,
                        "kernelMaxWorkGroupSize=" + kernelMax,
                        "preferredWorkGroupSizeMultiple=" + preferred,
                        "preferredMultipleMatched=" + matched,
                        ""
                )
        );
    }
}
