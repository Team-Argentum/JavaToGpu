package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendHookAuthorizationValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendHookAuthorizationValidatorExampleTest {

    @Test
    void rendersPassingServiceLoaderAuthorizationGateWithoutNativeRuntime() {
        GpuBackendHookAuthorizationValidationResult result = BackendHookAuthorizationValidatorExample.validateOpenClHooks();
        String output = BackendHookAuthorizationValidatorExample.renderValidation(result);

        assertTrue(result.passed());
        assertTrue(output.contains("Backend hook authorization validator:"), output);
        assertTrue(output.contains("status=passed"), output);
        assertTrue(output.contains("backend=OPENCL"), output);
        assertTrue(output.contains("decisionCount=25"), output);
        assertTrue(output.contains("executableHooks=5"), output);
        assertTrue(output.contains("blockedHooks=0"), output);
        assertTrue(output.contains("futureAuthorizedButDisabled=0"), output);
        assertTrue(output.contains("firstBlocker=none"), output);
        assertTrue(output.contains("recommendedExitCode=0"), output);
        assertTrue(output.contains("does not open OpenCL/CUDA"), output);
    }
}
