package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionDescriptor;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionRegistry;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendHookAuthorizationValidationResult;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendHookAuthorizationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void hookRegistryExecutesReadOnlyStageHooksFailSoftAndIgnoresReplacementResults() {
        GpuBackendModuleArtifact moduleArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel() {}",
                "kernel.cl",
                "test-lowerer"
        );
        GpuBackendLoweringResult loweringResult = GpuBackendLoweringResult.succeeded(
                moduleArtifact,
                GpuBackendSourceSelectionPlan.descriptorSource(GpuBackendTarget.OPENCL, "opencl-c", "test source"),
                List.of("lowered")
        );
        GpuBackendCompilationResult compilationResult = GpuBackendCompilationResult.succeeded(
                loweringResult,
                GpuRuntimeBackendCompilationSummary.from(moduleArtifact, null, "compiled:test"),
                "compiled:test",
                List.of("compiled")
        );
        GpuBackendInvocationResult invocationResult = GpuBackendInvocationResult.invoked(
                GpuBackendPreparationResult.prepared(
                        compilationResult,
                        "test-kernel",
                        GpuRuntimeInvocationBindingSummary.empty(),
                        List.of("prepared")
                ),
                GpuExecutionConfig.oneDimensional(1L),
                0,
                0,
                List.of("invoked")
        );
        GpuRuntimeDeviceDiscoveryResult discoveryResult = GpuRuntimeDeviceDiscoveryResult.unavailable(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "test-discovery-unavailable",
                null
        );
        AtomicInteger discoveryCalls = new AtomicInteger();
        AtomicInteger loweringCalls = new AtomicInteger();
        AtomicInteger compilationCalls = new AtomicInteger();
        AtomicInteger invocationCalls = new AtomicInteger();
        AtomicInteger artifactCalls = new AtomicInteger();

        GpuBackendDiscoveryContributor replacingDiscoveryHook = new GpuBackendDiscoveryContributor() {
            @Override
            public String extensionId() {
                return "test.hook.replacing-discovery";
            }

            @Override
            public GpuRuntimeDeviceDiscoveryResult afterDiscovery(
                    GpuRuntimeCompileOptions compileOptions,
                    GpuRuntimeDeviceDiscoveryResult observedResult
            ) {
                discoveryCalls.incrementAndGet();
                return GpuRuntimeDeviceDiscoveryResult.unavailable(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "replacement-must-be-ignored",
                        null
                );
            }

            @Override
            public Map<String, String> discoveryFacts(GpuRuntimeDeviceDiscoveryResult observedResult) {
                return Map.of("test.discovery.available", Boolean.toString(observedResult.discoveryAvailable()));
            }
        };
        GpuBackendLoweringHook replacingLoweringHook = new GpuBackendLoweringHook() {
            @Override
            public String extensionId() {
                return "test.hook.replacing-lowering";
            }

            @Override
            public GpuBackendLoweringResult afterLowering(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendLoweringResult observedResult
            ) {
                loweringCalls.incrementAndGet();
                return GpuBackendLoweringResult.unsupported(
                        GpuBackendTarget.OPENCL,
                        observedResult.sourceSelectionPlan(),
                        List.of("replacement-must-be-ignored"),
                        List.of()
                );
            }
        };
        GpuBackendCompilationHook replacingCompilationHook = new GpuBackendCompilationHook() {
            @Override
            public String extensionId() {
                return "test.hook.replacing-compilation";
            }

            @Override
            public GpuBackendCompilationResult afterCompilation(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendCompilationResult observedResult
            ) {
                compilationCalls.incrementAndGet();
                return GpuBackendCompilationResult.unsupported(
                        GpuBackendTarget.OPENCL,
                        loweringResult,
                        List.of("replacement-must-be-ignored"),
                        List.of()
                );
            }
        };
        GpuBackendInvocationHook failingInvocationHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.hook.failing-invocation";
            }

            @Override
            public GpuBackendInvocationResult afterInvocation(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendInvocationResult observedResult
            ) {
                invocationCalls.incrementAndGet();
                throw new IllegalStateException("simulated hook failure");
            }
        };
        GpuBackendArtifactHook artifactHook = new GpuBackendArtifactHook() {
            @Override
            public String extensionId() {
                return "test.hook.artifact";
            }

            @Override
            public Map<String, String> contributeArtifactFields(
                    GpuRuntimeCompileRequest compileRequest,
                    Map<String, String> currentFields
            ) {
                artifactCalls.incrementAndGet();
                return Map.of("test.artifact.field", currentFields.getOrDefault("status", "missing"));
            }
        };
        GpuBackendInvocationHook productionAffectingHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.hook.production-affecting";
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }
        };

        GpuBackendHookRegistry registry = GpuBackendHookRegistry.of(List.of(
                replacingDiscoveryHook,
                replacingLoweringHook,
                replacingCompilationHook,
                failingInvocationHook,
                artifactHook,
                productionAffectingHook
        ));
        Map<String, String> discoveryFields = registry.observeDiscovery(
                GpuBackendTarget.OPENCL,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                discoveryResult,
                "hook.discovery"
        );
        Map<String, String> loweringFields = registry.observeLowering(
                GpuBackendTarget.OPENCL,
                null,
                loweringResult,
                "hook.lowering"
        );
        Map<String, String> compilationFields = registry.observeCompilation(
                GpuBackendTarget.OPENCL,
                null,
                compilationResult,
                "hook.compilation"
        );
        Map<String, String> invocationFields = registry.observeInvocation(
                GpuBackendTarget.OPENCL,
                null,
                invocationResult,
                "hook.invocation"
        );
        Map<String, String> artifactFields = registry.contributeArtifactFields(
                GpuBackendTarget.OPENCL,
                null,
                Map.of("status", "succeeded"),
                "hook.artifact"
        );

        assertEquals(1, discoveryCalls.get());
        assertEquals(1, loweringCalls.get());
        assertEquals(1, compilationCalls.get());
        assertEquals(1, invocationCalls.get());
        assertEquals(1, artifactCalls.get());
        assertEquals("1", discoveryFields.get("hook.discovery.mutationIgnored.count"));
        assertEquals("mutation-ignored", discoveryFields.get("hook.discovery.hook.0.status"));
        assertEquals("test.discovery.available", discoveryFields.get("hook.discovery.hook.0.contribution.0.key"));
        assertEquals("false", discoveryFields.get("hook.discovery.hook.0.contribution.0.value"));
        assertEquals("1", loweringFields.get("hook.lowering.mutationIgnored.count"));
        assertEquals("mutation-ignored", loweringFields.get("hook.lowering.hook.0.status"));
        assertEquals("1", compilationFields.get("hook.compilation.mutationIgnored.count"));
        assertEquals("mutation-ignored", compilationFields.get("hook.compilation.hook.0.status"));
        assertEquals("1", invocationFields.get("hook.invocation.failed.count"));
        assertEquals("failed", invocationFields.get("hook.invocation.hook.0.status"));
        assertEquals("skipped", invocationFields.get("hook.invocation.hook.1.status"));
        assertEquals("non-read-only-permission", invocationFields.get("hook.invocation.hook.1.skipReason"));
        assertTrue(invocationFields.get("hook.invocation.hook.1.diagnostic")
                .contains("current backend hook execution only runs READ_ONLY hooks"));
        assertEquals("1", artifactFields.get("hook.artifact.contributionField.count"));
        assertEquals("test.artifact.field", artifactFields.get("hook.artifact.hook.0.contribution.0.key"));
        assertEquals("succeeded", artifactFields.get("hook.artifact.hook.0.contribution.0.value"));
    }

    @Test
    void hookRegistryRejectsDuplicateBackendHookIdsWithActionableDiagnostic() {
        GpuBackendLoweringHook firstHook = new GpuBackendLoweringHook() {
            @Override
            public String extensionId() {
                return "test.hook.duplicate";
            }
        };
        GpuBackendCompilationHook secondHook = new GpuBackendCompilationHook() {
            @Override
            public String extensionId() {
                return "test.hook.duplicate";
            }
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuBackendHookRegistry.of(List.of(firstHook, secondHook))
        );

        assertTrue(exception.getMessage().contains("Duplicate backend hook extension id 'test.hook.duplicate'"));
        assertTrue(exception.getMessage().contains("unique extensionId()"));
    }

    @Test
    void hookRegistryRejectsConcreteHookPhaseAndCapabilityMismatches() {
        GpuBackendLoweringHook wrongPhaseHook = new GpuBackendLoweringHook() {
            @Override
            public String extensionId() {
                return "test.hook.wrong-phase";
            }

            @Override
            public GpuExtensionPhase extensionPhase() {
                return GpuExtensionPhase.BACKEND_COMPILATION;
            }
        };
        GpuBackendLoweringHook wrongCapabilityHook = new GpuBackendLoweringHook() {
            @Override
            public String extensionId() {
                return "test.hook.wrong-capability";
            }

            @Override
            public Set<GpuExtensionCapability> extensionCapabilities() {
                return Set.of(GpuExtensionCapability.BACKEND_COMPILATION_HOOK);
            }
        };

        IllegalArgumentException phaseException = assertThrows(
                IllegalArgumentException.class,
                () -> GpuBackendHookRegistry.of(List.of(wrongPhaseHook))
        );
        IllegalArgumentException capabilityException = assertThrows(
                IllegalArgumentException.class,
                () -> GpuBackendHookRegistry.of(List.of(wrongCapabilityHook))
        );

        assertTrue(phaseException.getMessage().contains("Expected phase: BACKEND_LOWERING"));
        assertTrue(capabilityException.getMessage().contains("expected capability BACKEND_LOWERING_HOOK"));
    }

    @Test
    void hookRegistryRejectsNullBackendTargetFilters() {
        GpuBackendArtifactHook hook = new GpuBackendArtifactHook() {
            @Override
            public String extensionId() {
                return "test.hook.null-target";
            }

            @Override
            public Set<GpuBackendTarget> backendTargets() {
                return Collections.singleton(null);
            }
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuBackendHookRegistry.of(List.of(hook))
        );

        assertTrue(exception.getMessage().contains("backendTargets() must not contain null"));
    }

    @Test
    void discoveryHelpersStoreReadOnlyHookExecutionFieldsOnDiscoveryResults() {
        AtomicInteger discoveryCalls = new AtomicInteger();
        GpuBackendHookRegistry registry = GpuBackendHookRegistry.of(List.of(new GpuBackendDiscoveryContributor() {
            @Override
            public String extensionId() {
                return "test.hook.planned-discovery";
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
                return Map.of("test.discovery.backend", discoveryResult.backendTarget().name());
            }
        }));

        GpuRuntimeDeviceDiscoveryResult discovery = GpuRuntimeDeviceDiscovery.plannedUnavailable(
                GpuBackendTarget.CUDA,
                registry
        );
        Map<String, String> fields = discovery.artifactFields("cudaDiscovery");

        assertEquals(1, discoveryCalls.get());
        assertEquals("true", fields.get("runtime.backend.hookExecution.discovery.present"));
        assertEquals("1", fields.get("runtime.backend.hookExecution.discovery.hook.count"));
        assertEquals("test.discovery.backend", fields.get("runtime.backend.hookExecution.discovery.hook.0.contribution.0.key"));
        assertEquals("CUDA", fields.get("runtime.backend.hookExecution.discovery.hook.0.contribution.0.value"));
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
        assertEquals("true", fields.get("runtime.backend.hookRegistry.contract.present"));
        assertEquals("0", fields.get("runtime.backend.hookRegistry.contract.warning.count"));
        assertEquals("test.hook.all-artifacts", fields.get("hooks.hook.0.id"));
        assertTrue(registry.toMarkdown().contains("test.hook.opencl-lowering"));
    }

    @Test
    void hookRegistryContractDiagnosticsExposeAuthorizationWarnings() {
        GpuBackendInvocationHook productionHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.hook.production-invocation";
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }
        };

        GpuBackendHookRegistry registry = GpuBackendHookRegistry.of(List.of(productionHook));
        Map<String, String> fields = registry.contractDiagnosticFields("contracts");

        assertEquals("1", fields.get("contracts.warning.count"));
        assertEquals("1", fields.get("contracts.nonReadOnly.count"));
        assertEquals("authorization-required", fields.get("contracts.hook.0.status"));
        assertTrue(fields.get("contracts.hook.0.diagnostic").contains("explicit production authorization"));
    }

    @Test
    void hookAuthorizationReportBlocksNonReadOnlyHooksByDefault() {
        GpuBackendInvocationHook readOnlyHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.hook.read-only-invocation";
            }
        };
        GpuBackendInvocationHook productionHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.hook.production-invocation";
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }
        };

        GpuBackendHookAuthorizationReport report = GpuBackendHookRegistry.of(List.of(readOnlyHook, productionHook))
                .authorizationReport(GpuBackendTarget.OPENCL, GpuExtensionPhase.BACKEND_INVOCATION);
        GpuBackendHookAuthorizationDecision readOnlyDecision = decisionById(report, "test.hook.read-only-invocation");
        GpuBackendHookAuthorizationDecision productionDecision = decisionById(report, "test.hook.production-invocation");

        assertEquals("blocked", report.status());
        assertEquals(1, report.currentRegistryExecutableCount());
        assertEquals(1, report.blockedCount());
        assertEquals(1, report.blockedDecisions().size());
        assertEquals(0, report.authorizationRequiredCount());
        assertEquals("test.hook.production-invocation:PERMISSION_EXCEEDS_POLICY", report.firstBlocker());
        assertTrue(report.firstBlockedDecision().isPresent());
        assertEquals(GpuBackendHookAuthorizationStatus.READ_ONLY_AUTHORIZED, readOnlyDecision.status());
        assertTrue(readOnlyDecision.currentRegistryExecutable());
        assertEquals(GpuBackendHookAuthorizationStatus.PERMISSION_EXCEEDS_POLICY, productionDecision.status());
        assertFalse(productionDecision.currentRegistryExecutable());
        assertEquals("blocked", report.artifactFields("auth").get("auth.status"));
        assertEquals("1", report.artifactFields("auth").get("auth.blocked.count"));
        assertEquals("test.hook.production-invocation:PERMISSION_EXCEEDS_POLICY",
                report.artifactFields("auth").get("auth.firstBlocker"));
        assertEquals("hook declares PRODUCTION_AFFECTING but policy maximum is READ_ONLY",
                report.artifactFields("auth").get("auth.firstBlocker.diagnostic"));
    }

    @Test
    void hookAuthorizationReportCanPreviewExplicitAuthorizationWithoutEnablingExecution() {
        GpuBackendInvocationHook productionHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.hook.production-invocation";
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }
        };
        GpuBackendHookAuthorizationPolicy policy = GpuBackendHookAuthorizationPolicy.previewExplicitAuthorization(
                GpuExtensionPermission.PRODUCTION_AFFECTING,
                List.of("test.hook.production-invocation")
        );

        GpuBackendHookAuthorizationReport report = GpuBackendHookRegistry.of(List.of(productionHook))
                .authorizationReport(GpuBackendTarget.OPENCL, GpuExtensionPhase.BACKEND_INVOCATION, policy);
        GpuBackendHookAuthorizationDecision decision = decisionById(report, "test.hook.production-invocation");

        assertEquals("future-authorized-execution-disabled", report.status());
        assertEquals(0, report.blockedCount());
        assertEquals("none", report.firstBlocker());
        assertEquals(1, report.futureAuthorizedButDisabledCount());
        assertEquals(GpuBackendHookAuthorizationStatus.AUTHORIZED_BUT_EXECUTION_DISABLED, decision.status());
        assertTrue(decision.policyAuthorized());
        assertFalse(decision.currentRegistryExecutable());
        assertTrue(report.toMarkdown().contains("currentRegistryExecutable=false"));
    }

    @Test
    void hookAuthorizationReportExplainsTargetAndPhaseFilters() {
        GpuBackendLoweringHook cudaLoweringHook = new GpuBackendLoweringHook() {
            @Override
            public String extensionId() {
                return "test.hook.cuda-lowering";
            }

            @Override
            public Set<GpuBackendTarget> backendTargets() {
                return Set.of(GpuBackendTarget.CUDA);
            }
        };

        GpuBackendHookAuthorizationReport openClLoweringReport = GpuBackendHookRegistry.of(List.of(cudaLoweringHook))
                .authorizationReport(GpuBackendTarget.OPENCL, GpuExtensionPhase.BACKEND_LOWERING);
        GpuBackendHookAuthorizationReport cudaCompilationReport = GpuBackendHookRegistry.of(List.of(cudaLoweringHook))
                .authorizationReport(GpuBackendTarget.CUDA, GpuExtensionPhase.BACKEND_COMPILATION);

        assertEquals(GpuBackendHookAuthorizationStatus.TARGET_FILTERED,
                decisionById(openClLoweringReport, "test.hook.cuda-lowering").status());
        assertEquals(GpuBackendHookAuthorizationStatus.PHASE_FILTERED,
                decisionById(cudaCompilationReport, "test.hook.cuda-lowering").status());
    }

    @Test
    void hookAuthorizationCatalogAggregatesStandardStageReports() {
        GpuBackendDiscoveryContributor discoveryHook = new GpuBackendDiscoveryContributor() {
            @Override
            public String extensionId() {
                return "test.hook.discovery";
            }
        };
        GpuBackendInvocationHook productionHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.hook.production-invocation";
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }
        };

        GpuBackendHookAuthorizationCatalog catalog = GpuBackendHookRegistry.of(List.of(discoveryHook, productionHook))
                .authorizationCatalog(GpuBackendTarget.OPENCL);
        Map<String, String> fields = catalog.artifactFields("authCatalog");

        assertEquals("blocked", catalog.status());
        assertEquals(5, catalog.reports().size());
        assertEquals(10, catalog.decisionCount());
        assertEquals(1, catalog.currentRegistryExecutableCount());
        assertEquals(1, catalog.blockedCount());
        assertEquals("invocation:test.hook.production-invocation:PERMISSION_EXCEEDS_POLICY", catalog.firstBlocker());
        assertTrue(catalog.reportForPhase(GpuExtensionPhase.BACKEND_INVOCATION).isPresent());
        assertEquals("blocked", fields.get("authCatalog.status"));
        assertEquals("5", fields.get("authCatalog.stage.count"));
        assertEquals("read-only-ready", fields.get("authCatalog.discovery.status"));
        assertEquals("blocked", fields.get("authCatalog.invocation.status"));
        assertEquals("invocation:test.hook.production-invocation:PERMISSION_EXCEEDS_POLICY",
                fields.get("authCatalog.firstBlocker"));
        assertTrue(catalog.toMarkdown().contains("Backend hook authorization catalog: blocked"));
        assertTrue(catalog.toMarkdown().contains("invocation: status=blocked"));
    }

    @Test
    void backendHookAuthorizationValidatorPassesReadOnlyClasspath() {
        GpuBackendDiscoveryContributor discoveryHook = new GpuBackendDiscoveryContributor() {
            @Override
            public String extensionId() {
                return "test.validator.discovery";
            }
        };
        GpuBackendInvocationHook invocationHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.validator.invocation";
            }
        };

        GpuBackendHookAuthorizationValidationResult result = GpuBackendHookAuthorizationValidator
                .validateReadOnlyClasspath(
                        GpuBackendHookRegistry.of(List.of(discoveryHook, invocationHook)),
                        GpuBackendTarget.OPENCL
                );

        assertTrue(result.passed());
        assertEquals("passed", result.status());
        assertEquals(0, result.recommendedExitCode());
        assertEquals("read-only-ready", result.catalog().status());
        assertEquals("true", result.artifactFields("validator").get("validator.passed"));
        assertTrue(result.toMarkdown().contains("Backend hook authorization validation: passed"));
    }

    @Test
    void backendHookAuthorizationValidatorFailsNonReadOnlyClasspath() {
        GpuBackendInvocationHook productionHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.validator.production-invocation";
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }
        };

        GpuBackendHookAuthorizationValidationResult result = GpuBackendHookAuthorizationValidator
                .validateReadOnlyClasspath(
                        GpuBackendHookRegistry.of(List.of(productionHook)),
                        GpuBackendTarget.OPENCL
                );

        assertFalse(result.passed());
        assertEquals("blocked", result.status());
        assertEquals(1, result.recommendedExitCode());
        assertTrue(result.diagnostic().contains("test.validator.production-invocation"));
        assertEquals("false", result.artifactFields("validator").get("validator.passed"));
        assertThrows(IllegalStateException.class, result::throwIfFailed);
    }

    @Test
    void backendHookAuthorizationValidatorKeepsPreviewAuthorizationSeparateFromRuntimeReadiness() {
        GpuBackendInvocationHook productionHook = new GpuBackendInvocationHook() {
            @Override
            public String extensionId() {
                return "test.validator.preview-production";
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.PRODUCTION_AFFECTING;
            }
        };
        GpuBackendHookAuthorizationPolicy policy = GpuBackendHookAuthorizationPolicy.previewExplicitAuthorization(
                GpuExtensionPermission.PRODUCTION_AFFECTING,
                List.of("test.validator.preview-production")
        );

        GpuBackendHookAuthorizationValidationResult runtimeReady = GpuBackendHookAuthorizationValidator.validate(
                GpuBackendHookRegistry.of(List.of(productionHook)),
                GpuBackendTarget.OPENCL,
                policy,
                false
        );
        GpuBackendHookAuthorizationValidationResult previewOnly = GpuBackendHookAuthorizationValidator.validate(
                GpuBackendHookRegistry.of(List.of(productionHook)),
                GpuBackendTarget.OPENCL,
                policy,
                true
        );

        assertFalse(runtimeReady.passed());
        assertEquals("future-authorized-execution-disabled", runtimeReady.status());
        assertTrue(runtimeReady.diagnostic().contains("not executable by the current runner"));
        assertTrue(previewOnly.passed());
        assertEquals("passed", previewOnly.status());
        assertEquals(1, previewOnly.catalog().futureAuthorizedButDisabledCount());
        assertTrue(previewOnly.diagnostic().contains("still disabled by the current runner"));
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

    private static GpuBackendHookAuthorizationDecision decisionById(
            GpuBackendHookAuthorizationReport report,
            String hookId
    ) {
        return report.decisions().stream()
                .filter(decision -> decision.hookId().equals(hookId))
                .findFirst()
                .orElseThrow();
    }
}
