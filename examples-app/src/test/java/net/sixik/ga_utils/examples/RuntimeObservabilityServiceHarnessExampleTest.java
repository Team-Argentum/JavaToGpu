package net.sixik.ga_utils.examples;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeObservabilityServiceHarnessExampleTest {

    @Test
    void rendersServiceLoaderObservabilityHarnessWithoutNativeRuntime() {
        String output = RuntimeObservabilityServiceHarnessExample.renderServiceHarnessPreview();

        assertTrue(output.contains("Runtime observability service harness:"), output);
        assertTrue(output.contains("status=succeeded"), output);
        assertTrue(output.contains("backend=OPENCL"), output);
        assertTrue(output.contains("lifecycleListeners="), output);
        assertTrue(output.contains("logServices=1"), output);
        assertTrue(output.contains("lifecycleAllSucceeded=true"), output);
        assertTrue(output.contains("logAllSucceeded=true"), output);
        assertTrue(output.contains("event=BACKEND_DEVICE_PREFLIGHT_COMPLETED"), output);
        assertTrue(output.contains("logLevel=INFO"), output);
        assertTrue(output.contains("no OpenCL/CUDA runtime is opened"), output);
    }
}
