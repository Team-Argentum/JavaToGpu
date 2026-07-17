package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendCompileOptionsTest {

    @Test
    void methodTestProbeModeDefaultsToDisabled() {
        GpuBackendCompileOptions options = GpuBackendCompileOptions.openCl(List.of());

        assertEquals(GpuRuntimeMethodTestProbeMode.DISABLED, options.methodTestProbeMode());
        assertTrue(options.methodTestProbeModeBlocker().isEmpty());
        assertFalse(options.requestsMethodTestProbeEvidenceRanking());
    }

    @Test
    void legacyCachedEvidenceRankingMapsToCacheOnlyMode() {
        GpuBackendCompileOptions options = GpuBackendCompileOptions.openCl(List.of(), Map.of(
                GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY,
                GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_CACHED
        ));

        assertEquals(GpuRuntimeMethodTestProbeMode.CACHE_ONLY, options.methodTestProbeMode());
        assertTrue(options.methodTestProbeModeBlocker().isEmpty());
        assertTrue(options.requestsMethodTestProbeEvidenceRanking());
    }

    @Test
    void explicitCacheOnlyModeKeepsLegacyRankingPropertyForCompatibility() {
        GpuBackendCompileOptions options = GpuBackendCompileOptions.openCl(List.of())
                .withMethodTestProbeMode(GpuRuntimeMethodTestProbeMode.CACHE_ONLY);

        assertEquals(GpuRuntimeMethodTestProbeMode.CACHE_ONLY, options.methodTestProbeMode());
        assertEquals(
                GpuRuntimeMethodTestProbeMode.CACHE_ONLY.optionValue(),
                options.properties().get(GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY)
        );
        assertEquals(
                GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_CACHED,
                options.properties().get(GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY)
        );
        assertTrue(options.requestsMethodTestProbeEvidenceRanking());
    }

    @Test
    void invalidMethodTestProbeModeIsBlockedFailClosed() {
        GpuBackendCompileOptions options = GpuBackendCompileOptions.openCl(List.of(), Map.of(
                GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY,
                "run-before-first-invoke"
        ));

        assertEquals(GpuRuntimeMethodTestProbeMode.DISABLED, options.methodTestProbeMode());
        assertEquals("runtime-method-test-probe-mode-invalid", options.methodTestProbeModeBlocker().orElseThrow());
        assertFalse(options.requestsMethodTestProbeEvidenceRanking());
    }

    @Test
    void withoutMethodTestProbeEvidenceRankingRemovesModeAndCacheProperties() {
        GpuBackendCompileOptions options = GpuBackendCompileOptions.openCl(List.of())
                .withPersistentMethodTestProbeEvidenceRanking(java.nio.file.Path.of(".javatogpu/method-test-probes"), null)
                .withoutMethodTestProbeEvidenceRanking();

        assertEquals(GpuRuntimeMethodTestProbeMode.DISABLED, options.methodTestProbeMode());
        assertFalse(options.properties().containsKey(GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_MODE_PROPERTY));
        assertFalse(options.properties().containsKey(
                GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY
        ));
        assertFalse(options.properties().containsKey(
                GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_EVIDENCE_CACHE_PATH_PROPERTY
        ));
    }
}
