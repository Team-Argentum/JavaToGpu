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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
                "selection-gate-not-bound",
                report.passReports().get(0).proofArtifact().fields().get("optimizedArtifactCandidate.selectionFirstBlocker")
        );
        assertEquals(
                "false",
                report.passReports().get(0).proofArtifact().fields().get("optimizedArtifactCandidate.selectionApplied")
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
        GpuRuntimeDeviceProfile profile = GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "test-device");
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor("run", "test.Kernel.run", "", "test.Kernel.run.irgpu", List.of()),
                GpuRuntimeCompileOptions.openCl(List.of(), "diagnostic"),
                profile,
                Optional.of(artifact)
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
