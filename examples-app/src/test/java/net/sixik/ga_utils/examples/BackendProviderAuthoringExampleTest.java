package net.sixik.ga_utils.examples;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendProviderAuthoringExampleTest {

    @Test
    void rendersProviderReadinessPathWithoutNativeBackendProbe() {
        String output = BackendProviderAuthoringExample.renderProviderAuthoringChecklist();

        assertTrue(output.contains("Backend provider authoring checklist:"), output);
        assertTrue(output.contains("1. Discovery-only provider"), output);
        assertTrue(output.contains("provider=example.cuda.discovery-only"), output);
        assertTrue(output.contains("supportedStages=discover"), output);
        assertTrue(output.contains("moduleFormats=cuda-c,ptx"), output);
        assertTrue(output.contains("capabilityVocabulary=compute-capability,device-class,driver-version,global-memory,runtime-version"), output);
        assertTrue(output.contains("status=execution-unavailable"), output);
        assertTrue(output.contains("backend-execution-stage-missing:compile"), output);
        assertTrue(output.contains("backend-execution-stage-missing:prepare"), output);
        assertTrue(output.contains("backend-execution-stage-missing:invoke"), output);
        assertTrue(output.contains("unsupportedReceipt=compile=UNSUPPORTED, prepare=SKIPPED, invoke=SKIPPED"), output);
        assertTrue(output.contains("2. Lowering-only provider"), output);
        assertTrue(output.contains("provider=example.cuda.lowering-only"), output);
        assertTrue(output.contains("supportedStages=discover,lower"), output);
        assertTrue(output.contains("3. Production pipeline provider"), output);
        assertTrue(output.contains("provider=example.cuda.production-pipeline"), output);
        assertTrue(output.contains("status=execution-pipeline-available"), output);
        assertTrue(output.contains("sharedRunner=true"), output);
        assertTrue(output.contains("wire native execution, lifecycle fields, artifact dumps"), output);
    }
}
