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

    @Test
    void cudaArgumentBinderIsOptInAndCanUseDriverMode() {
        GpuBackendCompileOptions options = GpuBackendCompileOptions.cuda(List.of(), Map.of());

        assertEquals(GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_DISABLED, options.cudaArgumentBinderMode());
        assertFalse(options.requestsCudaNativeArgumentBinder());

        GpuBackendCompileOptions driverOptions = options.withCudaDriverArgumentBinder();

        assertEquals(GpuBackendCompileOptions.CUDA_ARGUMENT_BINDER_DRIVER, driverOptions.cudaArgumentBinderMode());
        assertTrue(driverOptions.requestsCudaNativeArgumentBinder());
    }

    @Test
    void cudaKernelLauncherIsOptInAndCanUseDriverMode() {
        GpuBackendCompileOptions options = GpuBackendCompileOptions.cuda(List.of(), Map.of());

        assertEquals(GpuBackendCompileOptions.CUDA_KERNEL_LAUNCHER_DISABLED, options.cudaKernelLauncherMode());
        assertFalse(options.requestsCudaNativeKernelLauncher());

        GpuBackendCompileOptions driverOptions = options.withCudaDriverKernelLauncher();

        assertEquals(GpuBackendCompileOptions.CUDA_KERNEL_LAUNCHER_DRIVER, driverOptions.cudaKernelLauncherMode());
        assertTrue(driverOptions.requestsCudaNativeKernelLauncher());
    }

    @Test
    void cudaReadbackIsOptInAndCanUseDriverMode() {
        GpuBackendCompileOptions options = GpuBackendCompileOptions.cuda(List.of(), Map.of());

        assertEquals(GpuBackendCompileOptions.CUDA_READBACK_DISABLED, options.cudaReadbackMode());
        assertFalse(options.requestsCudaNativeReadback());

        GpuBackendCompileOptions driverOptions = options.withCudaDriverReadback();

        assertEquals(GpuBackendCompileOptions.CUDA_READBACK_DRIVER, driverOptions.cudaReadbackMode());
        assertTrue(driverOptions.requestsCudaNativeReadback());
    }
}
