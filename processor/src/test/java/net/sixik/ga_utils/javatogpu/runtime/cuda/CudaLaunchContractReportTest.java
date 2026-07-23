package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaLaunchContractReportTest {

    @Test
    void builtInLaunchContractAcceptsValidShapesAndRejectsUnsafeShapesWithoutNativeCuda() {
        CudaLaunchContractReport report = CudaLaunchContractReport.inspectBuiltIns();
        Map<String, CudaLaunchContractReport.Case> cases = report.cases().stream()
                .collect(Collectors.toMap(CudaLaunchContractReport.Case::key, testCase -> testCase));
        Map<String, String> fields = report.artifactFields("test.cuda.launchContract");

        assertEquals("ready", report.status());
        assertTrue(report.ready());
        assertEquals("none", report.firstBlocker());
        assertEquals(8, report.cases().size());
        assertEquals(8, report.readyCount());
        assertEquals(0, report.blockedCount());
        assertCase(cases, "valid-1d", "succeeded", "none");
        assertCase(cases, "valid-3d", "succeeded", "none");
        assertCase(cases, "dynamic-shared-memory", "succeeded", "none");
        assertCase(cases, "auto-local-rejected", "unsupported", "cuda-driver-launch-local-size-required");
        assertCase(cases, "non-divisible-x-rejected", "unsupported", "cuda-driver-launch-global-local-mismatch:x");
        assertCase(cases, "block-limit-rejected", "unsupported", "cuda-driver-launch-block-item-count-exceeds-device");
        assertCase(cases, "shared-memory-limit-rejected", "unsupported", "cuda-driver-launch-shared-memory-exceeds-device-local-memory");
        assertCase(cases, "shared-memory-int-range-rejected", "unsupported", "cuda-driver-launch-shared-memory-too-large");
        assertEquals("ready", fields.get("runtime.cuda.launchContract.status"));
        assertEquals("8", fields.get("runtime.cuda.launchContract.case.count"));
        assertEquals("8", fields.get("runtime.cuda.launchContract.case.ready.count"));
        assertEquals("0", fields.get("runtime.cuda.launchContract.case.blocked.count"));
        assertEquals("none", fields.get("runtime.cuda.launchContract.firstBlocker"));
        assertTrue(report.toMarkdown().contains("CUDA launch contract: ready"));
        assertTrue(CudaLaunchContractCli.render(report).contains("caseReady=8/8"));
        assertTrue(CudaLaunchContractCli.render(report).contains("case.auto-local-rejected=ready"));
    }

    private static void assertCase(
            Map<String, CudaLaunchContractReport.Case> cases,
            String key,
            String expectedStatus,
            String expectedBlocker
    ) {
        CudaLaunchContractReport.Case testCase = cases.get(key);
        assertNotNull(testCase, "missing case " + key);
        assertTrue(testCase.ready(), key);
        assertEquals(expectedStatus, testCase.result().status());
        if (!"none".equals(expectedBlocker)) {
            assertTrue(testCase.result().blockers().contains(expectedBlocker), key);
        }
    }
}
