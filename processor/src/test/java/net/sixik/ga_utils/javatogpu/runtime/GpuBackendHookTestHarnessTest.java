package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendHookTestHarness;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendHookTestHarnessReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendHookTestHarnessTest {

    @Test
    void runsReadOnlyBackendHooksAgainstSyntheticReceiptsWithoutNativeRuntime() {
        AtomicInteger discoveryCalls = new AtomicInteger();
        AtomicInteger loweringCalls = new AtomicInteger();
        AtomicInteger compilationCalls = new AtomicInteger();
        AtomicInteger artifactCalls = new AtomicInteger();

        GpuBackendDiscoveryContributor discoveryHook = new GpuBackendDiscoveryContributor() {
            @Override
            public String extensionId() {
                return "test.harness.discovery";
            }

            @Override
            public GpuRuntimeDeviceDiscoveryResult afterDiscovery(
                    GpuRuntimeCompileOptions compileOptions,
                    GpuRuntimeDeviceDiscoveryResult discoveryResult
            ) {
                discoveryCalls.incrementAndGet();
                return discoveryResult;
            }

            @Override
            public Map<String, String> discoveryFacts(GpuRuntimeDeviceDiscoveryResult discoveryResult) {
                return Map.of("test.discovery.target", discoveryResult.backendTarget().name());
            }
        };
        GpuBackendLoweringHook replacingLoweringHook = new GpuBackendLoweringHook() {
            @Override
            public String extensionId() {
                return "test.harness.replacing-lowering";
            }

            @Override
            public GpuBackendLoweringResult afterLowering(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendLoweringResult loweringResult
            ) {
                loweringCalls.incrementAndGet();
                return GpuBackendLoweringResult.unsupported(
                        GpuBackendTarget.OPENCL,
                        loweringResult.sourceSelectionPlan(),
                        List.of("replacement-must-be-ignored"),
                        List.of()
                );
            }
        };
        GpuBackendCompilationHook failingCompilationHook = new GpuBackendCompilationHook() {
            @Override
            public String extensionId() {
                return "test.harness.failing-compilation";
            }

            @Override
            public GpuBackendCompilationResult afterCompilation(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendCompilationResult compilationResult
            ) {
                compilationCalls.incrementAndGet();
                throw new IllegalStateException("synthetic hook failure");
            }
        };
        GpuBackendInvocationHook productionInvocationHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.harness.production-invocation";
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }
        };
        GpuBackendArtifactHook artifactHook = new GpuBackendArtifactHook() {
            @Override
            public String extensionId() {
                return "test.harness.artifact";
            }

            @Override
            public Map<String, String> contributeArtifactFields(
                    GpuRuntimeCompileRequest compileRequest,
                    Map<String, String> currentFields
            ) {
                artifactCalls.incrementAndGet();
                return Map.of("test.artifact.status", currentFields.getOrDefault("status", "missing"));
            }
        };

        GpuBackendHookTestHarnessReport report = GpuBackendHookTestHarness.of(List.of(
                discoveryHook,
                replacingLoweringHook,
                failingCompilationHook,
                productionInvocationHook,
                artifactHook
        )).runSyntheticOpenCl();

        assertEquals(GpuBackendTarget.OPENCL, report.backendTarget());
        assertEquals(5, report.loadedHookCount());
        assertEquals("1", report.registryFields()
                .get("runtime.backend.hookRegistry.contract.warning.count"));
        assertEquals("authorization-required", report.registryFields()
                .get("runtime.backend.hookRegistry.contract.hook.3.status"));
        assertEquals("read-only-ready", report.authorizationFields()
                .get("runtime.backend.hookAuthorization.discovery.status"));
        assertEquals("1", report.authorizationFields()
                .get("runtime.backend.hookAuthorization.discovery.currentRegistryExecutable.count"));
        assertEquals("blocked", report.authorizationFields()
                .get("runtime.backend.hookAuthorization.invocation.status"));
        assertEquals("1", report.authorizationFields()
                .get("runtime.backend.hookAuthorization.invocation.blocked.count"));
        assertEquals("test.harness.production-invocation:PERMISSION_EXCEEDS_POLICY", report.authorizationFields()
                .get("runtime.backend.hookAuthorization.invocation.firstBlocker"));
        assertEquals(1, discoveryCalls.get());
        assertEquals(1, loweringCalls.get());
        assertEquals(1, compilationCalls.get());
        assertEquals(1, artifactCalls.get());
        assertEquals("1", report.discoveryFields().get("runtime.backend.hookExecution.discovery.applied.count"));
        assertEquals(
                "OPENCL",
                contributionValue(
                        report.discoveryFields(),
                        "runtime.backend.hookExecution.discovery.hook.0",
                        "test.discovery.target"
                )
        );
        assertEquals("1", report.loweringFields().get("runtime.backend.hookExecution.lowering.mutationIgnored.count"));
        assertEquals("mutation-ignored", report.loweringFields().get("runtime.backend.hookExecution.lowering.hook.0.status"));
        assertEquals("1", report.compilationFields().get("runtime.backend.hookExecution.compilation.failed.count"));
        assertEquals("failed", report.compilationFields().get("runtime.backend.hookExecution.compilation.hook.0.status"));
        assertEquals("1", report.invocationFields().get("runtime.backend.hookExecution.invocation.skipped.count"));
        assertEquals("non-read-only-permission", report.invocationFields().get("runtime.backend.hookExecution.invocation.hook.0.skipReason"));
        assertEquals("succeeded", contributionValue(
                report.artifactFields(),
                "runtime.backend.hookExecution.artifact.hook.0",
                "test.artifact.status"
        ));
        assertTrue(report.toMarkdown().contains("Backend hook harness: OPENCL"));
        assertTrue(report.toMarkdown().contains("Invocation authorization: status=blocked"));
        assertTrue(report.toMarkdown().contains("firstBlocker=test.harness.production-invocation:PERMISSION_EXCEEDS_POLICY"));
        assertTrue(report.toMarkdown().contains("Compilation: hooks=1, applied=0, skipped=0, failed=1"));
        assertEquals("true", report.artifactFields("harness").get("harness.present"));
        assertTrue(Integer.parseInt(report.artifactFields("harness")
                .get("harness.authorization.field.count")) > 0);
    }

    @Test
    void syntheticFactoryHelpersReturnTargetedReceipts() {
        GpuRuntimeCompileRequest compileRequest = GpuBackendHookTestHarness.syntheticCompileRequest(GpuBackendTarget.CUDA);
        GpuBackendLoweringResult loweringResult = GpuBackendHookTestHarness.syntheticLoweringResult(GpuBackendTarget.CUDA);
        GpuBackendCompilationResult compilationResult = GpuBackendHookTestHarness.syntheticCompilationResult(loweringResult);
        GpuBackendInvocationResult invocationResult = GpuBackendHookTestHarness.syntheticInvocationResult(
                GpuBackendHookTestHarness.syntheticPreparationResult(compilationResult)
        );

        assertEquals(GpuBackendTarget.CUDA, compileRequest.options().backendTarget());
        assertEquals(GpuBackendTarget.CUDA, loweringResult.moduleArtifact().backendTarget());
        assertEquals("cuda-c", loweringResult.moduleArtifact().format());
        assertEquals(GpuBackendTarget.CUDA, compilationResult.moduleArtifact().backendTarget());
        assertEquals(GpuBackendTarget.CUDA, invocationResult.stageResult().backendTarget());
    }

    private static String contributionValue(Map<String, String> fields, String hookPrefix, String contributionKey) {
        int contributionCount = Integer.parseInt(fields.getOrDefault(hookPrefix + ".contribution.count", "0"));
        for (int index = 0; index < contributionCount; index++) {
            String contributionPrefix = hookPrefix + ".contribution." + index;
            if (contributionKey.equals(fields.get(contributionPrefix + ".key"))) {
                return fields.get(contributionPrefix + ".value");
            }
        }
        return "missing";
    }
}
