package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Safe backend entry point for preparing cached runtime device self-test evidence.
 */
public final class GpuRuntimeDeviceSelfTests {

    private GpuRuntimeDeviceSelfTests() {
    }

    public static List<GpuRuntimeDeviceSelfTestCacheEntry> prepare(
            GpuRuntimeDeviceSelfTestMode mode,
            List<GpuRuntimeDeviceProfile> candidates,
            GpuRuntimeDeviceSelfTestRunner runner,
            GpuRuntimeDeviceSelfTestCache cache
    ) {
        GpuRuntimeDeviceSelfTestMode selfTestMode = mode == null
                ? GpuRuntimeDeviceSelfTestMode.AUTO
                : mode;
        List<GpuRuntimeDeviceProfile> profiles = candidates == null ? List.of() : List.copyOf(candidates);
        Objects.requireNonNull(runner, "runner");
        Objects.requireNonNull(cache, "cache");
        if (selfTestMode == GpuRuntimeDeviceSelfTestMode.DISABLED
                || selfTestMode == GpuRuntimeDeviceSelfTestMode.AUTO && profiles.size() < 2) {
            return List.of();
        }

        ArrayList<GpuRuntimeDeviceSelfTestCacheEntry> entries = new ArrayList<>();
        for (GpuRuntimeDeviceProfile profile : profiles) {
            if (!runner.supports(profile)) {
                continue;
            }
            GpuRuntimeDeviceSelfTestIdentity identity = GpuRuntimeDeviceSelfTestIdentity.from(profile, runner);
            entries.add(cache.getOrRun(identity, () -> runSafely(runner, profile, identity)));
        }
        return List.copyOf(entries);
    }

    private static GpuRuntimeDeviceSelfTestResult runSafely(
            GpuRuntimeDeviceSelfTestRunner runner,
            GpuRuntimeDeviceProfile profile,
            GpuRuntimeDeviceSelfTestIdentity identity
    ) {
        try {
            GpuRuntimeDeviceSelfTestResult result = runner.run(
                    new GpuRuntimeDeviceSelfTestRequest(profile, identity)
            );
            if (result == null) {
                return GpuRuntimeDeviceSelfTestResult.failed(
                        identity,
                        0L,
                        List.of("self-test runner returned null")
                );
            }
            return result;
        } catch (RuntimeException | LinkageError exception) {
            String message = exception.getMessage();
            return GpuRuntimeDeviceSelfTestResult.failed(
                    identity,
                    0L,
                    List.of(
                            "self-test runner failed: "
                                    + exception.getClass().getSimpleName()
                                    + (message == null || message.isBlank() ? "" : ": " + message)
                    )
            );
        }
    }
}
