package net.sixik.ga_utils.examples;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendHookAuthorizationPreviewExampleTest {

    @Test
    void rendersAuthorizationPreviewWithoutEnablingProductionHooks() {
        String output = BackendHookAuthorizationPreviewExample.renderAuthorizationPreview();

        assertTrue(output.contains("Backend hook authorization preview:"), output);
        assertTrue(output.contains("defaultStatus=blocked"), output);
        assertTrue(output.contains("defaultFirstBlocker=invocation:examples.backend-hook.preview-production-invocation:PERMISSION_EXCEEDS_POLICY"), output);
        assertTrue(output.contains("defaultExecutableHooks=1"), output);
        assertTrue(output.contains("previewStatus=future-authorized-execution-disabled"), output);
        assertTrue(output.contains("previewFirstBlocker=none"), output);
        assertTrue(output.contains("previewExecutableHooks=1"), output);
        assertTrue(output.contains("previewFutureAuthorizedButDisabled=1"), output);
        assertTrue(output.contains("preview authorization is diagnostic only"), output);
    }
}
