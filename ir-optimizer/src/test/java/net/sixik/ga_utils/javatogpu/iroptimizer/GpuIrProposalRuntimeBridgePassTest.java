package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDump;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileProvenance;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationOutcome;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPass;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationStage;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizerRegistry;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClRuntimeIrOptimizerEvidenceValidatorCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrProposalRuntimeBridgePassTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void runtimeBridgeIsTheOnlyServiceLoadedRuntimePassFromModule() {
        List<GpuRuntimeIrOptimizationPass> passes = ServiceLoader
                .load(GpuRuntimeIrOptimizationPass.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(pass -> pass.getClass().getName().startsWith("net.sixik.ga_utils.javatogpu.iroptimizer"))
                .toList();

        assertEquals(1, passes.size());
        assertInstanceOf(GpuIrProposalRuntimeBridgePass.class, passes.get(0));
        assertEquals(GpuIrProposalRuntimeBridgePass.PASS_VERSION, passes.get(0).passVersion());
    }

    @Test
    void bridgeLoadsProposalProvidersInDeterministicOrder() {
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass();

        assertEquals(15, bridge.proposalProviders().size());
        assertInstanceOf(GpuIrNoOpProposalProvider.class, bridge.proposalProviders().get(0));
        assertInstanceOf(GpuIrTextCanonicalizationProposalProvider.class, bridge.proposalProviders().get(1));
        assertInstanceOf(GpuIrHelperDependencyDeduplicationProposalProvider.class, bridge.proposalProviders().get(2));
        assertInstanceOf(GpuIrBackendNeutralSourceMaterializationProposalProvider.class, bridge.proposalProviders().get(3));
        assertInstanceOf(GpuIrConstantFoldingPreviewProposalProvider.class, bridge.proposalProviders().get(4));
        assertInstanceOf(GpuIrConstantFoldingMaterializationProposalProvider.class, bridge.proposalProviders().get(5));
        assertInstanceOf(GpuIrSafeLocalCsePreviewProposalProvider.class, bridge.proposalProviders().get(6));
        assertInstanceOf(GpuIrSafeLocalCseMaterializationProposalProvider.class, bridge.proposalProviders().get(7));
        assertInstanceOf(GpuIrMadFmaMaterializationProposalProvider.class, bridge.proposalProviders().get(8));
        assertInstanceOf(GpuIrTypedDeadCodePreviewProposalProvider.class, bridge.proposalProviders().get(9));
        assertInstanceOf(GpuIrClampMaterializationProposalProvider.class, bridge.proposalProviders().get(10));
        assertInstanceOf(GpuIrStepMaterializationProposalProvider.class, bridge.proposalProviders().get(11));
        assertInstanceOf(GpuIrMixMaterializationProposalProvider.class, bridge.proposalProviders().get(12));
        assertInstanceOf(GpuIrTypedDeadCodeMaterializationProposalProvider.class, bridge.proposalProviders().get(13));
        assertInstanceOf(GpuIrLoopVectorizationMaterializationProposalProvider.class, bridge.proposalProviders().get(14));
    }

    @Test
    void modulePublishesOnlyBackendNeutralProposalProviderDescriptorEntries() throws Exception {
        List<String> neutralDescriptor = serviceDescriptorLines(GpuIrOptimizationProposalProvider.class.getName());
        List<String> vendorDescriptor = serviceDescriptorLines(GpuIrVendorOptimizationProposalProvider.class.getName());

        assertTrue(neutralDescriptor.contains(GpuIrNoOpProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrTextCanonicalizationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrHelperDependencyDeduplicationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrBackendNeutralSourceMaterializationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrConstantFoldingPreviewProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrConstantFoldingMaterializationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrSafeLocalCsePreviewProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrSafeLocalCseMaterializationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrMadFmaMaterializationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrClampMaterializationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrStepMaterializationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrMixMaterializationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrTypedDeadCodePreviewProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrTypedDeadCodeMaterializationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.contains(GpuIrLoopVectorizationMaterializationProposalProvider.class.getName()));
        assertTrue(neutralDescriptor.stream().noneMatch(value -> value.contains("irvendoroptimizer")));
        assertTrue(vendorDescriptor.isEmpty());
    }

    @Test
    void bridgeKeepsCanonicalizationProposalOnlyByDefault() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");

        GpuRuntimeIrOptimizationReport report = new GpuIrProposalRuntimeBridgePass()
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals("body\n  return original\n", report.candidateArtifact().orElseThrow().module().methodBodies().get(0).body());
        assertFalse(report.requiresRollback());
        assertEquals(15, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY, report.passReports().get(1).stage());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(1).outcome());
        assertEquals("proposal-only", report.passReports().get(1).proofStatus());
        assertEquals(
                "candidate-ready",
                report.passReports().get(1).proofArtifact().fields().get("optimizedArtifactCandidate.status")
        );
        assertEquals(
                "mutation-disabled",
                report.passReports().get(1).proofArtifact().fields().get("optimizedArtifactCandidate.selectionFirstBlocker")
        );
        assertEquals(
                "false",
                report.passReports().get(1).proofArtifact().fields().get("optimizedArtifactCandidate.selectionApplied")
        );
        assertEquals(
                "false",
                report.passReports().get(1).proofArtifact().fields().get("optimizedArtifactCandidate.selectedIrReplacement")
        );
        assertTrue(report.passReports().get(1).diagnostics().contains(
                "optimized artifact validated but mutation is disabled; original IR remains selected"
        ));
    }

    @Test
    void bridgeSkipsProviderWhenFamilyIsDisabledByMethodPolicy() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        IrGpuArtifact original = artifactWithPolicy(
                "body\n  return original\n",
                IrGpuOptimizerPolicyMetadata.fromGpuOptimize(
                        false,
                        true,
                        "review",
                        List.of(),
                        List.of("mix"),
                        false,
                        false,
                        false,
                        false,
                        "auto",
                        false
                )
        );
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new CountingProposalProvider("mix", invoked)),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertFalse(invoked.get());
        assertSame(original, report.artifact().orElseThrow());
        assertTrue(report.candidateArtifact().isEmpty());
        assertEquals(1, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
        assertEquals("optimizer-family-disabled", report.passReports().get(0).proofStatus());
        Map<String, String> fields = report.passReports().get(0).proofArtifact().fields();
        assertEquals("mix", fields.get("optimizerFamily"));
        assertEquals("skipped", fields.get("policyGate.status"));
        assertEquals("false", fields.get("policyGate.allowed"));
        assertEquals("optimizer-family-disabled", fields.get("policyGate.reason"));
        assertEquals("false", fields.get("policyGate.providerInvoked"));
        assertEquals("false", fields.get("selectionApplied"));
        assertEquals("false", fields.get("optimizedArtifactSelected"));
        assertEquals("false", fields.get("selectedIrReplacement"));

        String evidence = runtimeOptimizerEvidence(original, report);
        assertTrue(evidence.contains("policyGate.skipped.count=1"));
        assertTrue(evidence.contains("policyGate.optimizerPolicyDisabled.count=0"));
        assertTrue(evidence.contains("policyGate.familyDisabled.count=1"));
        assertTrue(evidence.contains("policyGate.familyNotEnabled.count=0"));
        assertTrue(evidence.contains("policyGate.providerInvoked.count=0"));
        assertTrue(evidence.contains("policyGate.firstBlocker=optimizer-family-disabled"));
        assertTrue(evidence.contains("policyGate.family.summary=mix=1"));
        assertTrue(evidence.contains("policyGate.selectionApplied=false"));
        assertTrue(evidence.contains("policyGate.optimizedArtifactSelected=false"));
        assertTrue(evidence.contains("policyGate.selectedIrReplacement=false"));
    }

    @Test
    void bridgeSkipsProviderWhenFamilyIsNotInMethodAllowList() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        IrGpuArtifact original = artifactWithPolicy(
                "body\n  return original\n",
                IrGpuOptimizerPolicyMetadata.fromGpuOptimize(
                        false,
                        true,
                        "review",
                        List.of("clamp"),
                        List.of(),
                        false,
                        false,
                        false,
                        false,
                        "auto",
                        false
                )
        );
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new CountingProposalProvider("mix", invoked)),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertFalse(invoked.get());
        assertSame(original, report.artifact().orElseThrow());
        assertEquals("optimizer-family-not-enabled", report.passReports().get(0).proofStatus());
        Map<String, String> fields = report.passReports().get(0).proofArtifact().fields();
        assertEquals("clamp", fields.get("optimizerPolicy.enabledFamilies"));
        assertEquals("optimizer-family-not-enabled", fields.get("policyGate.reason"));
        assertEquals("mix", fields.get("policyGate.family"));
    }

    @Test
    void bridgeRunsProviderWhenFamilyIsAllowedByMethodAllowList() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        IrGpuArtifact original = artifactWithPolicy(
                "body\n  return original\n",
                IrGpuOptimizerPolicyMetadata.fromGpuOptimize(
                        false,
                        true,
                        "review",
                        List.of("mix-materialization"),
                        List.of(),
                        false,
                        false,
                        false,
                        false,
                        "auto",
                        false
                )
        );
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new CountingProposalProvider("mix", invoked)),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertTrue(invoked.get());
        assertSame(original, report.artifact().orElseThrow());
        assertEquals(1, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
        assertEquals("not-mutating", report.passReports().get(0).proofStatus());
    }

    @Test
    void bridgeSkipsAllProvidersWhenExplicitMethodPolicyDisablesOptimizer() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        IrGpuArtifact original = artifactWithPolicy(
                "body\n  return original\n",
                IrGpuOptimizerPolicyMetadata.fromGpuOptimize(
                        false,
                        false,
                        "review",
                        List.of(),
                        List.of(),
                        false,
                        false,
                        false,
                        false,
                        "auto",
                        false
                )
        );
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new CountingProposalProvider("clamp", invoked)),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertFalse(invoked.get());
        assertSame(original, report.artifact().orElseThrow());
        assertEquals("optimizer-policy-disabled", report.passReports().get(0).proofStatus());
        assertEquals(
                "optimizer-policy-disabled",
                report.passReports().get(0).proofArtifact().fields().get("policyGate.reason")
        );
    }

    @Test
    void bridgeMaterializesConstantFoldingCandidateButKeepsOriginalSelectedByDefault() {
        IrGpuArtifact original = constantFoldingArtifact();
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrConstantFoldingMaterializationProposalProvider()),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertSame(original, report.artifact().orElseThrow());
        IrGpuArtifact candidate = report.candidateArtifact().orElseThrow();
        assertEquals("body\n  set output[0] = 5\n", candidate.module().methodBodies().get(0).body());
        assertTrue(candidate.regenerationMetadata().backendNeutralSourceReady());
        assertEquals(1, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
        assertEquals("proposal-only", report.passReports().get(0).proofStatus());
        assertEquals(
                "true",
                report.passReports().get(0).proofArtifact().fields().get("rewrite.materialized")
        );
        assertEquals(
                "true",
                report.passReports().get(0).proofArtifact().fields().get("runtimeEquivalencePayload.present")
        );
        assertEquals(
                "true",
                report.passReports().get(0).proofArtifact().fields().get("runtimeEquivalencePayload.passed")
        );
        assertEquals(
                "none",
                report.passReports().get(0).proofArtifact().fields().get("runtimeEquivalencePayload.firstBlocker")
        );
        assertEquals(
                "pending",
                report.passReports().get(0).proofArtifact().fields().get("approvalTemplate.status")
        );
        assertEquals(
                "optimizer-family:constant-folding-materialization:review-candidate",
                report.passReports().get(0).proofArtifact().fields().get("approvalTemplate.runtimeEquivalencePayload.comparisonMode")
        );
        assertTrue(report.passReports().get(0).proofArtifact().fields()
                .get("approvalTemplate.resourcePath")
                .startsWith(GpuIrOptimizationApprovalManifest.RESOURCE_DIRECTORY + "approval-"));
        assertEquals(
                "pending-manifest-validation",
                report.passReports().get(0).proofArtifact().fields().get("approvalManifest.status")
        );
        assertEquals(
                "false",
                report.passReports().get(0).proofArtifact().fields().get("approvalManifest.present")
        );
        assertEquals(
                "false",
                report.passReports().get(0).proofArtifact().fields().get("approvalManifest.accepted")
        );
        assertEquals(
                "approval-manifest-not-loaded",
                report.passReports().get(0).proofArtifact().fields().get("approvalManifest.firstBlocker")
        );
        assertEquals(
                "false",
                report.passReports().get(0).proofArtifact().fields().get("optimizedArtifactCandidate.selectionApplied")
        );
        assertEquals(
                "false",
                report.passReports().get(0).proofArtifact().fields().get("optimizedArtifactCandidate.selectedIrReplacement")
        );
    }

    @Test
    void bridgeSelectsMaterializedCandidateWhenExperimentalApplyCompileOptionIsSet() {
        IrGpuArtifact original = constantFoldingArtifact();
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrConstantFoldingMaterializationProposalProvider()),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        );

        GpuRuntimeIrOptimizationReport report = bridge.run(new GpuRuntimeIrOptimizationRequest(
                request(original, GpuRuntimeCompileOptions.openClIrOptimizerExperimentalApply(List.of(), "diagnostic")),
                Optional.of(original)
        ));

        IrGpuArtifact selected = report.artifact().orElseThrow();
        assertNotSame(original, selected);
        assertEquals("body\n  set output[0] = 5\n", selected.module().methodBodies().get(0).body());
        assertEquals(selected, report.candidateArtifact().orElseThrow());
        assertEquals(GpuRuntimeIrOptimizationOutcome.APPLIED, report.passReports().get(0).outcome());
        assertEquals("optimized-selected", report.passReports().get(0).proofStatus());
        Map<String, String> fields = report.passReports().get(0).proofArtifact().fields();
        assertEquals("true", fields.get("experimentalApply.requested"));
        assertEquals("true", fields.get("experimentalApply.enabled"));
        assertEquals("true", fields.get("experimentalApply.selected"));
        assertEquals("true", fields.get("optimizedArtifactCandidate.selectionApplied"));
        assertEquals("true", fields.get("optimizedArtifactCandidate.selectedIrReplacement"));
        assertEquals("none", fields.get("optimizedArtifactCandidate.selectionFirstBlocker"));
    }

    @Test
    void bridgeChainsReviewOnlyMaterializationsWithoutSelectingOptimizedIr() {
        IrGpuArtifact original = chainedMaterializationArtifact();
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(
                        new GpuIrConstantFoldingMaterializationProposalProvider(),
                        new GpuIrSafeLocalCseMaterializationProposalProvider()
                ),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals(
                "body\n  var int tmp = (a + b)\n  set output[0] = 5\n  set output[1] = tmp\n",
                report.candidateArtifact().orElseThrow().module().methodBodies().get(0).body()
        );
        assertEquals(2, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(1).outcome());
        assertEquals(report.passReports().get(0).transformedIrIdentity(), report.passReports().get(1).originalIrIdentity());
        assertEquals(
                "1",
                report.passReports().get(0).proofArtifact().fields().get("transformedNode.count")
        );
        assertEquals(
                "1",
                report.passReports().get(1).proofArtifact().fields().get("transformedNode.count")
        );
        assertEquals(
                "false",
                report.passReports().get(1).proofArtifact().fields().get("optimizedArtifactCandidate.selectionApplied")
        );
        assertEquals(
                "false",
                report.passReports().get(1).proofArtifact().fields().get("optimizedArtifactCandidate.selectedIrReplacement")
        );
    }

    @Test
    void bridgeMaterializesMadFmaCandidateButKeepsOriginalSelectedByDefault() {
        IrGpuArtifact original = madFmaArtifact();
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrMadFmaMaterializationProposalProvider()),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals(
                "body\n  set output[0] = intrinsic(mad template=\"\" args=[left, right, addend])\n",
                report.candidateArtifact().orElseThrow().module().methodBodies().get(0).body()
        );
        assertEquals(1, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
        assertEquals("proposal-only", report.passReports().get(0).proofStatus());
        Map<String, String> fields = report.passReports().get(0).proofArtifact().fields();
        assertEquals("mad-fma-materialization", fields.get("optimizerFamily"));
        assertEquals("true", fields.get("policy.fastMathAllowed"));
        assertEquals("1", fields.get("transformedNode.count"));
        assertEquals("true", fields.get("rewrite.materialized"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.present"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.passed"));
        assertEquals("pending", fields.get("approvalTemplate.status"));
        assertEquals("false", fields.get("optimizedArtifactCandidate.selectionApplied"));
        assertEquals("false", fields.get("optimizedArtifactCandidate.selectedIrReplacement"));
    }

    @Test
    void bridgeLoadsApprovedManifestAsEvidenceWithoutSelectingOptimizedIr() throws Exception {
        IrGpuArtifact original = constantFoldingArtifact();
        GpuRuntimeCompileRequest compileRequest = request(original);
        GpuIrOptimizationProposalRequest proposalRequest = proposalRequest(compileRequest, original, false);
        GpuIrOptimizationProposal proposal = new GpuIrConstantFoldingMaterializationProposalProvider()
                .propose(proposalRequest);
        Path classpathRoot = temporaryDirectory.resolve("bridge-approved-manifest");
        writeApprovedManifest(classpathRoot, proposal, proposalRequest);

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{classpathRoot.toUri().toURL()},
                GpuIrProposalRuntimeBridgePassTest.class.getClassLoader()
        )) {
            Thread.currentThread().setContextClassLoader(classLoader);
            GpuRuntimeIrOptimizationReport report = new GpuIrProposalRuntimeBridgePass(
                    List.of(new GpuIrConstantFoldingMaterializationProposalProvider()),
                    GpuIrOptimizationSandwichRunner.alwaysValid(),
                    false
            ).run(new GpuRuntimeIrOptimizationRequest(compileRequest, Optional.of(original)));

            assertSame(original, report.artifact().orElseThrow());
            assertEquals("body\n  set output[0] = 5\n", report.candidateArtifact().orElseThrow()
                    .module().methodBodies().get(0).body());
            Map<String, String> fields = report.passReports().get(0).proofArtifact().fields();
            assertEquals("accepted", fields.get("approvalManifest.status"));
            assertEquals("true", fields.get("approvalManifest.present"));
            assertEquals("true", fields.get("approvalManifest.accepted"));
            assertEquals("none", fields.get("approvalManifest.firstBlocker"));
            assertEquals("disabled", fields.get("approvalManifest.productionMutation"));
            assertEquals("disabled", fields.get("approvalManifest.selectedIrReplacement"));
            assertEquals("false", fields.get("optimizedArtifactCandidate.selectionApplied"));
            assertEquals("false", fields.get("optimizedArtifactCandidate.selectedIrReplacement"));

            GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                    "__kernel void run(__global int* out) { out[0] = 1; }",
                    "runtime/lowered/run.cl",
                    "test-lowerer-v1"
            );
            GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(
                    GpuRuntimeCompileArtifactSnapshot.from(
                            compileRequest,
                            compileRequest,
                            backendArtifact,
                            GpuRuntimeCompileInvalidationStamp.from(compileRequest, backendArtifact, "approval-loader"),
                            GpuRuntimeCompileProvenance.from(compileRequest),
                            report
                    )
            );
            String evidence = dump.artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
            assertTrue(evidence.contains("reviewPackage.approvalManifest.status=accepted"));
            assertTrue(evidence.contains("reviewPackage.approvalManifest.present.count=1"));
            assertTrue(evidence.contains("reviewPackage.approvalManifest.accepted.count=1"));
            assertTrue(evidence.contains("reviewPackage.approvalManifest.productionMutation=disabled"));
            assertTrue(evidence.contains("optimizedArtifactCandidate.selectionApplied=false"));
            assertTrue(evidence.contains("optimizedArtifactCandidate.selectedIrReplacement=false"));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void bridgeCandidateEnvelopeReachesRuntimeEvidenceArtifact() throws Exception {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");
        GpuRuntimeCompileRequest compileRequest = request(original);
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrTextCanonicalizationProposalProvider()),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        ).run(new GpuRuntimeIrOptimizationRequest(compileRequest, Optional.of(original)));
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void run(__global int* out) { out[0] = 1; }",
                "runtime/lowered/run.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                compileRequest,
                compileRequest,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(compileRequest, backendArtifact, "ir-optimizer:e2e"),
                GpuRuntimeCompileProvenance.from(compileRequest),
                optimizationReport
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.hasArtifact(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT));
        String evidence = dump.artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        assertTrue(evidence.contains("status=recorded"));
        assertTrue(evidence.contains("pass.count=1"));
        assertTrue(evidence.contains("proposalOnly.count=1"));
        assertTrue(evidence.contains("selectedOptimized.count=0"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.status=candidate-ready"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.count=1"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.ready.count=1"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectionApplied.count=0"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectedIrReplacement.count=0"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectionFirstBlocker=mutation-disabled"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectionApplied=false"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectedIrReplacement=false"));
        assertTrue(evidence.contains("pass.0.proofArtifact.field.optimizedArtifactCandidate.status=candidate-ready"));
        assertTrue(evidence.contains(
                "pass.0.proofArtifact.field.approvalTemplate.resourcePath=META-INF/javatogpu/ir-optimization-approvals/approval-"
        ));
        assertTrue(evidence.contains(
                "pass.0.approvalTemplate.field.resourcePath=META-INF/javatogpu/ir-optimization-approvals/approval-"
        ));
        assertTrue(evidence.contains("pass.0.proofArtifact.field.optimizedArtifactCandidate.selectionApplied=false"));
        assertTrue(evidence.contains("pass.0.proofArtifact.field.optimizedArtifactCandidate.selectedIrReplacement=false"));

        Path evidenceFile = temporaryDirectory.resolve(
                GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT
        );
        Files.writeString(evidenceFile, evidence, StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(
                new String[]{evidenceFile.toString()}
        ));
    }

    @Test
    void bridgeDumpsLoopVectorizationValidatedTypedBodyRebuildAsReviewReadyEvidence() throws Exception {
        IrGpuArtifact original = loopVectorizationValidatedArtifact();
        GpuRuntimeCompileRequest compileRequest = request(original);
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrLoopVectorizationMaterializationProposalProvider()),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        ).run(new GpuRuntimeIrOptimizationRequest(compileRequest, Optional.of(original)));

        assertSame(original, optimizationReport.artifact().orElseThrow());
        IrGpuArtifact optimized = optimizationReport.candidateArtifact().orElseThrow();
        assertTrue(optimized.module().methodBodies().get(0).body()
                .contains("intrinsic(vload4 template=\"\" args=[0, (&input[(id * 4)])])"));
        assertTrue(optimized.module().methodBodies().get(0).typedBody().available());
        assertTrue(optimized.module().methodBodies().get(0).typedBody().nodes().stream().anyMatch(node ->
                "GpuIrIntrinsicCall".equals(node.kind()) && "vload4".equals(node.attributes().get("backendName"))
        ));
        Map<String, String> fields = optimizationReport.passReports().get(0).proofArtifact().fields();
        assertEquals("validated", fields.get("typedBody.rebuild.status"));
        assertEquals("none", fields.get("typedBody.rebuild.firstBlocker"));

        GpuRuntimeCompileArtifactDump dump = runtimeOptimizerDump(original, optimizationReport);
        String evidence = dump.artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);

        assertTrue(dump.artifact("backend.opencl-c").contains("out[0] = 1"));
        assertFalse(dump.artifact("backend.opencl-c").contains("vload4"));
        assertTrue(dump.artifact("optimized.backend.opencl-c").contains("vload4"));
        String optimizedIrGpu = dump.artifact("optimized.irgpu.properties");
        assertTrue(optimizedIrGpu.contains("GpuIrIntrinsicCall"));
        assertTrue(serializedNamedValuePresent(optimizedIrGpu, ".typed.node.", "backendName", "vload4"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("selectedStage=original"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("selectedStage=original"));

        assertTrue(evidence.contains("loopVectorizationMaterialization.transformedLoop.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.bodyTextReplacement.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.materialized.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.invalidated.count=0"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.attempted.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.parsed.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.built.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.graphValidated.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.rejected.count=0"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.status=validated"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.firstBlocker=none"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.runtimeEquivalencePayloadPresent.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.runtimeEquivalencePassed.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.status=review-ready"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.firstBlocker=none"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.status=review-ready"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.firstBlocker=none"));
        assertTrue(evidence.contains("loop-vectorization-materialization=review-ready"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectionApplied=false"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectedIrReplacement=false"));

        Path evidenceFile = temporaryDirectory.resolve(
                GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT
        );
        Files.writeString(evidenceFile, evidence, StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(
                new String[]{evidenceFile.toString()}
        ));
    }

    @Test
    void bridgeDumpsLoopVectorizationInvalidatedTypedBodyRebuildAsBlockedEvidence() throws Exception {
        IrGpuArtifact original = loopVectorizationInvalidatedArtifact();
        GpuRuntimeCompileRequest compileRequest = request(original);
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrLoopVectorizationMaterializationProposalProvider()),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        ).run(new GpuRuntimeIrOptimizationRequest(compileRequest, Optional.of(original)));

        assertSame(original, optimizationReport.artifact().orElseThrow());
        IrGpuArtifact optimized = optimizationReport.candidateArtifact().orElseThrow();
        assertTrue(optimized.module().methodBodies().get(0).body()
                .contains("intrinsic(vload4 template=\"\" args=[0, (&input[(id * 4)])])"));
        assertFalse(optimized.module().methodBodies().get(0).typedBody().available());
        Map<String, String> fields = optimizationReport.passReports().get(0).proofArtifact().fields();
        assertEquals("invalidated", fields.get("typedBody.rebuild.status"));
        assertEquals("typed-body-rebuild-statement-conversion-blocked", fields.get("typedBody.rebuild.firstBlocker"));

        String evidence = runtimeOptimizerEvidence(original, optimizationReport);

        assertTrue(evidence.contains("loopVectorizationMaterialization.transformedLoop.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.bodyTextReplacement.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.materialized.count=0"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.invalidated.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.attempted.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.parsed.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.built.count=0"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.graphValidated.count=0"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.rejected.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.typedBody.rebuild.status=invalidated"));
        assertTrue(evidence.contains(
                "loopVectorizationMaterialization.typedBody.rebuild.firstBlocker="
                        + "typed-body-rebuild-statement-conversion-blocked"
        ));
        assertTrue(evidence.contains("loopVectorizationMaterialization.runtimeEquivalencePayloadPresent.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.runtimeEquivalencePassed.count=1"));
        assertTrue(evidence.contains("loopVectorizationMaterialization.status=blocked"));
        assertTrue(evidence.contains(
                "loopVectorizationMaterialization.firstBlocker=typed-body-rebuild-statement-conversion-blocked"
        ));
        assertTrue(evidence.contains("runtimeEquivalenceReview.status=blocked"));
        assertTrue(evidence.contains(
                "runtimeEquivalenceReview.firstBlocker=typed-body-rebuild-statement-conversion-blocked"
        ));
        assertTrue(evidence.contains("loop-vectorization-materialization=blocked"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectionApplied=false"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectedIrReplacement=false"));

        Path evidenceFile = temporaryDirectory.resolve(
                GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT
        );
        Files.writeString(evidenceFile, evidence, StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> OpenClRuntimeIrOptimizerEvidenceValidatorCli.main(
                new String[]{evidenceFile.toString()}
        ));
    }

    @Test
    void bridgeCanSelectOptimizedArtifactWhenExplicitlyAllowed() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrTextCanonicalizationProposalProvider()),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                true
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertEquals("body\n  return original\n", report.artifact().orElseThrow().module().methodBodies().get(0).body());
        assertEquals(1, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.APPLIED, report.passReports().get(0).outcome());
        assertEquals("optimized-selected", report.passReports().get(0).proofStatus());
        assertEquals(
                "none",
                report.passReports().get(0).proofArtifact().fields().get("optimizedArtifactCandidate.selectionFirstBlocker")
        );
        assertEquals(
                "true",
                report.passReports().get(0).proofArtifact().fields().get("optimizedArtifactCandidate.selectionApplied")
        );
        assertEquals(
                "true",
                report.passReports().get(0).proofArtifact().fields().get("optimizedArtifactCandidate.selectedIrReplacement")
        );
        assertFalse(report.requiresRollback());
    }

    @Test
    void bridgeRollsBackWhenSandwichRejectsOptimizedArtifact() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");
        GpuIrOptimizationValidationGate validationGate = validationRequest -> {
            if (validationRequest.stage() == GpuIrOptimizationValidationStage.OPTIMIZED_AFTER) {
                return GpuIrOptimizationValidationResult.invalid(
                        validationRequest.stage(),
                        "optimized-invalid",
                        "bridge test rejected optimized artifact"
                );
            }
            return GpuIrOptimizationValidationResult.valid(validationRequest.stage());
        };
        GpuIrProposalRuntimeBridgePass bridge = new GpuIrProposalRuntimeBridgePass(
                List.of(new GpuIrTextCanonicalizationProposalProvider()),
                new GpuIrOptimizationSandwichRunner(validationGate),
                true
        );

        GpuRuntimeIrOptimizationReport report = bridge
                .run(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertSame(original, report.artifact().orElseThrow());
        assertTrue(report.requiresRollback());
        assertEquals(GpuRuntimeIrOptimizationOutcome.ROLLED_BACK, report.passReports().get(0).outcome());
        assertEquals("optimized-invalid", report.passReports().get(0).proofStatus());
        assertEquals("optimized artifact rejected by validation sandwich", report.passReports().get(0).rollbackReason());
    }

    @Test
    void runtimeRegistrySeesBridgeButKeepsOriginalIrByDefault() {
        IrGpuArtifact original = artifact("body  \r\n  return original\t\r\n");

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.loadFromServiceLoader()
                .optimizeWithReport(new GpuRuntimeIrOptimizationRequest(request(original), Optional.of(original)));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals("body\n  return original\n", report.candidateArtifact().orElseThrow().module().methodBodies().get(0).body());
        assertTrue(report.passReports().stream().anyMatch(passReport ->
                GpuIrProposalRuntimeBridgePass.PASS_VERSION.equals(passReport.optimizerVersion())
                        || passReport.optimizerVersion().startsWith(GpuIrOptimizerModule.MODULE_ID)
        ));
    }

    private static GpuRuntimeCompileRequest request(IrGpuArtifact artifact) {
        return request(artifact, GpuRuntimeCompileOptions.openCl(List.of(), "diagnostic"));
    }

    private static GpuRuntimeCompileRequest request(
            IrGpuArtifact artifact,
            GpuRuntimeCompileOptions compileOptions
    ) {
        GpuRuntimeDeviceProfile profile = GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "test-device");
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor("run", "test.Kernel.run", "", "test.Kernel.run.irgpu", List.of()),
                compileOptions,
                profile,
                Optional.of(artifact)
        );
    }

    private static String runtimeOptimizerEvidence(
            IrGpuArtifact original,
            GpuRuntimeIrOptimizationReport optimizationReport
    ) {
        return runtimeOptimizerDump(original, optimizationReport)
                .artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
    }

    private static boolean serializedNamedValuePresent(
            String propertiesText,
            String keyScope,
            String name,
            String value
    ) {
        Map<String, String> namesByPrefix = new HashMap<>();
        Map<String, String> valuesByPrefix = new HashMap<>();
        for (String line : propertiesText.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String key = line.substring(0, separator);
            if (!key.contains(keyScope)) {
                continue;
            }
            String serializedValue = line.substring(separator + 1);
            if (key.endsWith(".name")) {
                namesByPrefix.put(key.substring(0, key.length() - ".name".length()), serializedValue);
            } else if (key.endsWith(".value")) {
                valuesByPrefix.put(key.substring(0, key.length() - ".value".length()), serializedValue);
            }
        }
        return namesByPrefix.entrySet().stream().anyMatch(entry ->
                name.equals(entry.getValue()) && value.equals(valuesByPrefix.get(entry.getKey()))
        );
    }

    private static GpuRuntimeCompileArtifactDump runtimeOptimizerDump(
            IrGpuArtifact original,
            GpuRuntimeIrOptimizationReport optimizationReport
    ) {
        GpuRuntimeCompileRequest compileRequest = request(original);
        GpuRuntimeCompileRequest optimizedRequest = optimizationReport.candidateArtifact()
                .map(GpuIrProposalRuntimeBridgePassTest::request)
                .orElse(compileRequest);
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void run(__global int* out) { out[0] = 1; }",
                "runtime/lowered/run.cl",
                "test-lowerer-v1"
        );
        GpuBackendModuleArtifact optimizedBackendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void run(__global const float* input, __global float* output) { vload4(0, input); }",
                "runtime/lowered/run.optimized.cl",
                "test-lowerer-v1"
        );
        return GpuRuntimeCompileArtifactDumper.dump(
                GpuRuntimeCompileArtifactSnapshot.from(
                        compileRequest,
                        optimizedRequest,
                        backendArtifact,
                        GpuRuntimeCompileInvalidationStamp.from(compileRequest, backendArtifact, "policy-gate"),
                        GpuRuntimeCompileProvenance.from(compileRequest),
                        optimizationReport
                ).withBackendStageModuleArtifacts(backendArtifact, optimizedBackendArtifact)
        );
    }

    private static GpuIrOptimizationProposalRequest proposalRequest(
            GpuRuntimeCompileRequest compileRequest,
            IrGpuArtifact artifact,
            boolean mutationAllowed
    ) {
        GpuRuntimeDeviceProfile profile = compileRequest.deviceProfile();
        return new GpuIrOptimizationProposalRequest(
                artifact,
                compileRequest.options().optimizationProfile(),
                mutationAllowed,
                Map.of(
                        "backendTarget", compileRequest.options().backendTarget().name(),
                        "optimizationProfile", compileRequest.options().optimizationProfile(),
                        "deviceProfile.backendTarget", profile.backendTarget().name(),
                        "deviceProfile.id", profile.deviceId(),
                        "deviceProfile.label", profile.deviceLabel(),
                        "deviceProfile.vendor", profile.vendor(),
                        "deviceProfile.driverVersion", profile.driverVersion(),
                        "deviceProfile.apiVersionText", profile.apiVersionText(),
                        "deviceProfile.deviceClass", profile.deviceClass().name(),
                        "mutationAllowed", Boolean.toString(mutationAllowed)
                )
        );
    }

    private static void writeApprovedManifest(
            Path classpathRoot,
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) throws Exception {
        Path resource = classpathRoot.resolve(GpuIrOptimizationApprovalManifest.resourcePath(proposal, request));
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, approvedManifestText(proposal, request), StandardCharsets.UTF_8);
    }

    private static String approvedManifestText(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        return GpuIrOptimizationApprovalManifest.template(proposal, request)
                .replace("status=pending", "status=approved")
                .replace("approval.id=REQUIRED", "approval.id=approval:bridge-test")
                .replace("approval.approvedBy=REQUIRED", "approval.approvedBy=CI")
                .replace("approval.approvedAtUtc=REQUIRED", "approval.approvedAtUtc=2026-07-12T00:00:00Z");
    }

    private static IrGpuArtifact artifact(String body) {
        IrGpuMethodBody methodBody = IrGpuMethodBody.entry(
                "test.Kernel.run",
                "run",
                body,
                List.of()
        );
        IrGpuModule module = new IrGpuModule("test.Kernel.run", "run", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(IrGpuBackendOutput.openClSource("test.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact artifactWithPolicy(String body, IrGpuOptimizerPolicyMetadata policy) {
        IrGpuArtifact artifact = artifact(body);
        return new IrGpuArtifact(
                artifact.header(),
                artifact.module(),
                artifact.entryParameters(),
                artifact.launchMetadata(),
                artifact.validationMetadata(),
                artifact.featureMetadata(),
                policy,
                artifact.regenerationMetadata(),
                artifact.structMetadata(),
                artifact.constants(),
                artifact.constantData(),
                artifact.backendOutputs(),
                artifact.runtimeDefaultBackend(),
                artifact.runtimeOptimizationProfile(),
                artifact.methodDeviceConstraints(),
                artifact.methodFallbackVariants(),
                artifact.extensionParticipationMetadata()
        );
    }

    private record CountingProposalProvider(
            String optimizerFamily,
            AtomicBoolean invoked
    ) implements GpuIrOptimizationProposalProvider {

        @Override
        public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
            invoked.set(true);
            return GpuIrOptimizationProposal.noChange(
                    extensionId(),
                    extensionVersion(),
                    request.originalArtifact(),
                    "counting provider invoked"
            );
        }

        @Override
        public String optimizerFamily() {
            return optimizerFamily;
        }

        @Override
        public String extensionId() {
            return "test." + optimizerFamily;
        }

        @Override
        public String extensionVersion() {
            return extensionId() + ":1";
        }
    }

    private static IrGpuArtifact constantFoldingArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of()),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "3"), Map.of())
                )
        );
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "test.Kernel.run",
                "run",
                "ir-text-v1",
                "body\n  set output[0] = (2 + 3)\n",
                typedBody,
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("test.Kernel.run")
        );
        IrGpuModule module = new IrGpuModule("test.Kernel.run", "run", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())),
                List.of(IrGpuBackendOutput.openClSource("test.Kernel.run.irgpu")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact chainedMaterializationArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0, 4, 9),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrVariableDeclaration", Map.of(
                                "typeName", "int",
                                "name", "tmp"
                        ), Map.of("initializer", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(2, "GpuIrVariableRef", Map.of("name", "a"), Map.of()),
                        new IrGpuTypedNode(3, "GpuIrVariableRef", Map.of("name", "b"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrAssignment", Map.of(), Map.of(
                                "target", List.of(5),
                                "value", List.of(6)
                        )),
                        new IrGpuTypedNode(5, "GpuIrVariableRef", Map.of("name", "output[0]"), Map.of()),
                        new IrGpuTypedNode(6, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(7),
                                "right", List.of(8)
                        )),
                        new IrGpuTypedNode(7, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of()),
                        new IrGpuTypedNode(8, "GpuIrLiteral", Map.of("sourceText", "3"), Map.of()),
                        new IrGpuTypedNode(9, "GpuIrAssignment", Map.of(), Map.of(
                                "target", List.of(10),
                                "value", List.of(11)
                        )),
                        new IrGpuTypedNode(10, "GpuIrVariableRef", Map.of("name", "output[1]"), Map.of()),
                        new IrGpuTypedNode(11, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(12),
                                "right", List.of(13)
                        )),
                        new IrGpuTypedNode(12, "GpuIrVariableRef", Map.of("name", "a"), Map.of()),
                        new IrGpuTypedNode(13, "GpuIrVariableRef", Map.of("name", "b"), Map.of())
                )
        );
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "test.Kernel.run",
                "run",
                "ir-text-v1",
                "body\n  var int tmp = (a + b)\n  set output[0] = (2 + 3)\n  set output[1] = (a + b)\n",
                typedBody,
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("test.Kernel.run")
        );
        IrGpuModule module = new IrGpuModule("test.Kernel.run", "run", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(
                        new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("a", "int", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("b", "int", "PRIVATE", false, List.of())
                ),
                List.of(IrGpuBackendOutput.openClSource("test.Kernel.run.irgpu")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact loopVectorizationValidatedArtifact() {
        return loopVectorizationArtifact("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var float sum = 0.0F
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set sum = (sum + input[((id * 4) + i)])
                  set output[id] = sum
                """);
    }

    private static IrGpuArtifact loopVectorizationInvalidatedArtifact() {
        return loopVectorizationArtifact("""
                body
                  var int id = intrinsic(get_global_id template="" args=[0])
                  var float sum = 0.0F
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set sum = (sum + input[((id * 4) + i)])
                  set output[id] = (flag ? sum : 0.0F)
                """);
    }

    private static IrGpuArtifact loopVectorizationArtifact(String body) {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "kernel",
                "ir-text-v1",
                body,
                new IrGpuTypedBody(
                        IrGpuTypedBody.FORMAT,
                        List.of(0),
                        List.of(new IrGpuTypedNode(0, "GpuIrVariableRef", Map.of("name", "sum"), Map.of()))
                ),
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("kernel")
        );
        IrGpuModule module = new IrGpuModule("kernel", "kernel", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuOptimizerPolicyMetadata.defaultStrict(),
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/test/Kernel/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact madFmaArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrAssignment", Map.of(), Map.of(
                                "target", List.of(1),
                                "value", List.of(2)
                        )),
                        new IrGpuTypedNode(1, "GpuIrVariableRef", Map.of("name", "output[0]"), Map.of()),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(3),
                                "right", List.of(6)
                        )),
                        new IrGpuTypedNode(3, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(4),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "left"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrVariableRef", Map.of("name", "right"), Map.of()),
                        new IrGpuTypedNode(6, "GpuIrVariableRef", Map.of("name", "addend"), Map.of())
                )
        );
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "test.Kernel.run",
                "run",
                "ir-text-v1",
                "body\n  set output[0] = ((left * right) + addend)\n",
                typedBody,
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("test.Kernel.run")
        );
        IrGpuModule module = new IrGpuModule("test.Kernel.run", "run", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("left", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("right", "float", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("addend", "float", "PRIVATE", false, List.of())
                ),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("test.Kernel.run.irgpu")),
                "opencl",
                "off"
        );
    }

    private static List<String> serviceDescriptorLines(String serviceName) throws Exception {
        String resourceName = "META-INF/services/" + serviceName;
        ClassLoader classLoader = GpuIrProposalRuntimeBridgePassTest.class.getClassLoader();
        try (java.io.InputStream stream = classLoader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                return List.of();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .filter(line -> !line.startsWith("#"))
                        .toList();
            }
        }
    }
}
