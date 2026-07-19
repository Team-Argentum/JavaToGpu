package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionDescriptor;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendHookContractTest {

    @Test
    void backendHooksExposeStableMetadataTargetFiltersAndArtifactFields() {
        GpuBackendPolicyContributor hook = new GpuBackendPolicyContributor() {
            @Override
            public String extensionId() {
                return "test.backend-policy";
            }

            @Override
            public int extensionOrder() {
                return 20;
            }

            @Override
            public Set<GpuBackendTarget> backendTargets() {
                return Set.of(GpuBackendTarget.OPENCL);
            }
        };

        Map<String, String> fields = hook.artifactFields("hook");

        assertEquals(Set.of(GpuExtensionCapability.BACKEND_POLICY_CONTRIBUTION), hook.extensionCapabilities());
        assertEquals(GpuExtensionPhase.BACKEND_POLICY, hook.extensionPhase());
        assertEquals(GpuExtensionPermission.READ_ONLY, hook.extensionPermission());
        assertEquals(GpuExtensionFailurePolicy.CONTINUE, hook.failurePolicy());
        assertTrue(hook.appliesTo(GpuBackendTarget.OPENCL));
        assertFalse(hook.appliesTo(GpuBackendTarget.CUDA));
        assertEquals("test.backend-policy", fields.get("hook.id"));
        assertEquals("20", fields.get("hook.order"));
        assertEquals("BACKEND_POLICY", fields.get("hook.phase"));
        assertEquals("READ_ONLY", fields.get("hook.permission"));
        assertEquals("CONTINUE", fields.get("hook.failurePolicy"));
        assertEquals("filtered", fields.get("hook.backendTarget.mode"));
        assertEquals("OPENCL", fields.get("hook.backendTarget.0"));
        assertEquals("test.backend-policy", fields.get("runtime.backend.hook.id"));
    }

    @Test
    void concreteBackendHooksDeclareDistinctCapabilitiesAndPhases() {
        assertHookContract(
                new GpuRuntimeBackendScoreContributor() {},
                GpuExtensionCapability.BACKEND_SCORE_CONTRIBUTION,
                GpuExtensionPhase.BACKEND_POLICY
        );
        assertHookContract(
                new GpuBackendDiscoveryContributor() {},
                GpuExtensionCapability.BACKEND_DISCOVERY_CONTRIBUTION,
                GpuExtensionPhase.BACKEND_DISCOVERY
        );
        assertHookContract(
                new GpuBackendLoweringHook() {},
                GpuExtensionCapability.BACKEND_LOWERING_HOOK,
                GpuExtensionPhase.BACKEND_LOWERING
        );
        assertHookContract(
                new GpuBackendCompilationHook() {},
                GpuExtensionCapability.BACKEND_COMPILATION_HOOK,
                GpuExtensionPhase.BACKEND_COMPILATION
        );
        assertHookContract(
                new GpuBackendInvocationHook() {},
                GpuExtensionCapability.BACKEND_INVOCATION_HOOK,
                GpuExtensionPhase.BACKEND_INVOCATION
        );
        assertHookContract(
                new GpuBackendArtifactHook() {},
                GpuExtensionCapability.BACKEND_ARTIFACT_HOOK,
                GpuExtensionPhase.ARTIFACT_EMISSION
        );
    }

    @Test
    void backendHookDefaultsDoNotMutateStageResults() {
        GpuBackendDiscoveryContributor discoveryHook = new GpuBackendDiscoveryContributor() {};
        GpuBackendLoweringHook loweringHook = new GpuBackendLoweringHook() {};
        GpuBackendCompilationHook compilationHook = new GpuBackendCompilationHook() {};
        GpuBackendInvocationHook invocationHook = new GpuBackendInvocationHook() {};
        GpuBackendArtifactHook artifactHook = new GpuBackendArtifactHook() {};

        GpuRuntimeDeviceDiscoveryResult discoveryResult = GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.CUDA,
                "CUDA",
                "cuda-inventory-unavailable",
                null
        );
        GpuBackendLoweringResult loweringResult = GpuBackendLoweringResult.unsupported(
                GpuBackendTarget.CUDA,
                null,
                List.of("cuda-lowerer-not-implemented"),
                List.of("CUDA lowering is not enabled")
        );
        GpuBackendCompilationResult compilationResult = GpuBackendCompilationResult.unsupported(
                GpuBackendTarget.CUDA,
                loweringResult,
                List.of("cuda-compiler-not-implemented"),
                List.of("CUDA compilation is not enabled")
        );
        GpuBackendInvocationResult invocationResult = GpuBackendInvocationResult.skipped(
                GpuBackendTarget.CUDA,
                null,
                List.of("prepare-stage-skipped"),
                List.of("CUDA invocation is not enabled")
        );

        assertSame(discoveryResult, discoveryHook.afterDiscovery(null, discoveryResult));
        assertTrue(discoveryHook.discoveryFacts(discoveryResult).isEmpty());
        assertSame(loweringResult, loweringHook.afterLowering(null, loweringResult));
        assertSame(compilationResult, compilationHook.afterCompilation(null, compilationResult));
        assertSame(invocationResult, invocationHook.afterInvocation(null, invocationResult));
        assertTrue(artifactHook.contributeArtifactFields(null, Map.of()).isEmpty());
    }

    @Test
    void hookMetadataCanBeValidatedByTheCommonExtensionRegistry() {
        GpuBackendInvocationHook hook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.backend-invocation-hook";
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }

            @Override
            public GpuExtensionFailurePolicy failurePolicy() {
                return GpuExtensionFailurePolicy.THROW;
            }
        };

        GpuExtensionRegistry registry = GpuExtensionRegistry.of(List.of(hook));
        GpuExtensionDescriptor descriptor = registry.requireDescriptor("test.backend-invocation-hook");

        assertEquals(GpuExtensionPhase.BACKEND_INVOCATION, descriptor.phase());
        assertEquals(GpuExtensionPermission.PRODUCTION_AFFECTING, descriptor.permission());
        assertEquals(List.of(GpuExtensionCapability.BACKEND_INVOCATION_HOOK), descriptor.capabilities());
        assertEquals(GpuExtensionFailurePolicy.THROW, hook.failurePolicy());
    }

    @Test
    void hookRegistrySortsFiltersAndRendersCatalogFields() {
        GpuBackendLoweringHook laterOpenClHook = new GpuBackendLoweringHook() {
            @Override
            public String extensionId() {
                return "test.hook.opencl-lowering";
            }

            @Override
            public int extensionOrder() {
                return 20;
            }

            @Override
            public Set<GpuBackendTarget> backendTargets() {
                return Set.of(GpuBackendTarget.OPENCL);
            }
        };
        GpuBackendArtifactHook earlierAllBackendsHook = new GpuBackendArtifactHook() {
            @Override
            public String extensionId() {
                return "test.hook.all-artifacts";
            }

            @Override
            public int extensionOrder() {
                return 5;
            }
        };

        GpuBackendHookRegistry registry = GpuBackendHookRegistry.of(List.of(laterOpenClHook, earlierAllBackendsHook));
        Map<String, String> fields = registry.artifactFields("hooks");

        assertEquals(2, registry.size());
        assertEquals("test.hook.all-artifacts", registry.hooks().get(0).extensionId());
        assertEquals("test.hook.opencl-lowering", registry.hooks().get(1).extensionId());
        assertEquals(List.of(earlierAllBackendsHook, laterOpenClHook), registry.forBackendTarget(GpuBackendTarget.OPENCL));
        assertEquals(List.of(earlierAllBackendsHook), registry.forBackendTarget(GpuBackendTarget.CUDA));
        assertEquals(List.of(laterOpenClHook), registry.forCapability(GpuExtensionCapability.BACKEND_LOWERING_HOOK));
        assertEquals("true", fields.get("runtime.backend.hookRegistry.present"));
        assertEquals("2", fields.get("runtime.backend.hookRegistry.hook.count"));
        assertEquals("test.hook.all-artifacts", fields.get("hooks.hook.0.id"));
        assertTrue(registry.toMarkdown().contains("test.hook.opencl-lowering"));
    }

    @Test
    void hookRegistryLoadsConcreteHookServices(@TempDir Path tempDir) throws Exception {
        Path serviceFile = tempDir.resolve("META-INF/services/" + GpuBackendPolicyContributor.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(
                serviceFile,
                TestServiceLoadedBackendPolicyContributor.class.getName() + System.lineSeparator()
        );

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{tempDir.toUri().toURL()},
                Thread.currentThread().getContextClassLoader()
        )) {
            GpuBackendHookRegistry registry = GpuBackendHookRegistry.loadWithServiceLoader(classLoader);

            assertEquals(1, registry.size());
            assertTrue(registry.extensionRegistry()
                    .findDescriptor("test.backend-hook:service-loaded-policy")
                    .isPresent());
            assertEquals(1, registry.forCapability(GpuExtensionCapability.BACKEND_POLICY_CONTRIBUTION).size());
            assertTrue(registry.forBackendTarget(GpuBackendTarget.CUDA).stream()
                    .anyMatch(hook -> hook.extensionId().equals("test.backend-hook:service-loaded-policy")));
            assertTrue(registry.forBackendTarget(GpuBackendTarget.OPENCL).isEmpty());
        }
    }

    private static void assertHookContract(
            GpuBackendHook hook,
            GpuExtensionCapability capability,
            GpuExtensionPhase phase
    ) {
        assertEquals(Set.of(capability), hook.extensionCapabilities());
        assertEquals(phase, hook.extensionPhase());
        assertEquals(GpuExtensionPermission.READ_ONLY, hook.extensionPermission());
        assertEquals(GpuExtensionFailurePolicy.CONTINUE, hook.failurePolicy());
        assertTrue(hook.appliesTo(GpuBackendTarget.OPENCL));
        assertTrue(hook.appliesTo(GpuBackendTarget.CUDA));
    }
}
