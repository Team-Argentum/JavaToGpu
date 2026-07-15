package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationApprovalManifestTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesPendingTemplateBoundToProposalAndRuntimeContext() throws Exception {
        GpuIrOptimizationProposal proposal = proposal();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());

        String template = GpuIrOptimizationApprovalManifest.template(proposal, request);
        String resourceName = GpuIrOptimizationApprovalManifest.resourceName(proposal, request);
        String resourcePath = GpuIrOptimizationApprovalManifest.resourcePath(proposal, request);

        assertTrue(template.contains("status=pending"));
        assertTrue(template.contains("scope=" + GpuIrOptimizationApprovalManifest.SCOPE));
        assertTrue(resourceName.startsWith("approval-"));
        assertTrue(resourceName.endsWith(".properties"));
        assertEquals(GpuIrOptimizationApprovalManifest.RESOURCE_DIRECTORY + resourceName, resourcePath);
        assertTrue(template.contains("manifest.resourcePath=" + resourcePath));
        assertTrue(template.contains("approval.id=REQUIRED"));
        assertTrue(template.contains("binding.optimizerId=optimizer:test"));
        assertTrue(template.contains("binding.originalIrIdentity=" + proposal.originalIdentity()));
        assertTrue(template.contains("binding.optimizedIrIdentity=" + proposal.optimizedIdentity()));
        assertTrue(template.contains("binding.backendTarget=OPENCL"));
        assertTrue(template.contains("binding.deviceVendor=NVIDIA"));
        assertTrue(template.contains("binding.proof.verdict=runtime-equivalence-passed"));
        assertTrue(template.contains("binding.runtimeEquivalencePayload.required=false"));
        assertTrue(template.contains("binding.runtimeEquivalencePayload.resource=not-required"));
    }

    @Test
    void validatesApprovedManifestAgainstExactProposalAndContext() throws Exception {
        GpuIrOptimizationProposal proposal = proposal();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Properties manifest = approvedManifest(proposal, request);

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertTrue(validation.valid());
        assertEquals("approved", validation.status());
        assertEquals("approval:optimizer-test", validation.approvalId());
        assertEquals(
                GpuIrOptimizationApprovalManifest.resourcePath(proposal, request),
                validation.manifestResourcePath()
        );
        assertEquals(proposal.originalIdentity(), validation.originalIrIdentity());
        assertEquals(proposal.optimizedIdentity(), validation.optimizedIrIdentity());
        assertTrue(validation.toPropertiesText().contains("valid=true"));
        assertTrue(validation.toPropertiesText().contains(
                "manifest.resourcePath=" + GpuIrOptimizationApprovalManifest.resourcePath(proposal, request)
        ));
        assertTrue(validation.toPropertiesText().contains("blocker.count=0"));
        assertTrue(validation.toPropertiesText().contains("binding.runtimeEquivalencePayload.required=false"));
    }

    @Test
    void bindsApprovedManifestToRuntimeEquivalencePayloadEvidence() throws Exception {
        GpuIrOptimizationProposal proposal = proposalWithRuntimeEquivalencePayload();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());

        String template = GpuIrOptimizationApprovalManifest.template(proposal, request);
        Properties manifest = approvedManifest(proposal, request);
        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertTrue(template.contains("binding.runtimeEquivalencePayload.required=true"));
        assertTrue(template.contains("binding.runtimeEquivalencePayload.present=true"));
        assertTrue(template.contains("binding.runtimeEquivalencePayload.passed=true"));
        assertTrue(template.contains("binding.runtimeEquivalencePayload.componentsComplete=true"));
        assertTrue(template.contains("binding.runtimeEquivalencePayload.caseCount=1"));
        assertTrue(template.contains("binding.runtimeEquivalencePayload.resource=artifact://payload/cf"));
        assertTrue(validation.valid());
        assertTrue(validation.runtimeEquivalencePayloadRequired());
        assertTrue(validation.runtimeEquivalencePayloadComponentsComplete());
        assertEquals("artifact://payload/cf", validation.runtimeEquivalencePayloadResource());
    }

    @Test
    void blocksManifestForDifferentRuntimeEquivalencePayloadResource() throws Exception {
        GpuIrOptimizationProposal proposal = proposalWithRuntimeEquivalencePayload();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Properties manifest = approvedManifest(proposal, request);
        manifest.setProperty("binding.runtimeEquivalencePayload.resource", "artifact://payload/other");

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertFalse(validation.valid());
        assertTrue(validation.blockers().contains("manifest-runtime-equivalence-payload-resource-mismatch"));
    }

    @Test
    void blocksManifestForDifferentPackagedResourcePath() throws Exception {
        GpuIrOptimizationProposal proposal = proposalWithRuntimeEquivalencePayload();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Properties manifest = approvedManifest(proposal, request);
        manifest.setProperty(
                "manifest.resourcePath",
                GpuIrOptimizationApprovalManifest.RESOURCE_DIRECTORY + "approval-other.properties"
        );

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertFalse(validation.valid());
        assertTrue(validation.blockers().contains("manifest-resource-path-mismatch"));
    }

    @Test
    void blocksApprovalWhenRequiredRuntimeEquivalencePayloadIsIncomplete() throws Exception {
        GpuIrOptimizationProposal proposal = proposalWithIncompleteRuntimeEquivalencePayload();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Properties manifest = approvedManifest(proposal, request);

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertFalse(validation.valid());
        assertTrue(validation.blockers().contains("runtime-equivalence-payload-missing"));
    }

    @Test
    void blocksManifestForDifferentOptimizedIdentity() throws Exception {
        GpuIrOptimizationProposal proposal = proposal();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Properties manifest = approvedManifest(proposal, request);
        manifest.setProperty("binding.optimizedIrIdentity", "irgpu:sha256:other");

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertFalse(validation.valid());
        assertTrue(validation.blockers().contains("manifest-optimized-ir-identity-mismatch"));
    }

    @Test
    void blocksManifestForDifferentDeviceBinding() throws Exception {
        GpuIrOptimizationProposal proposal = proposal();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Properties manifest = approvedManifest(proposal, request);
        manifest.setProperty("binding.deviceVendor", "AMD");

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertFalse(validation.valid());
        assertTrue(validation.blockers().contains("manifest-device-vendor-mismatch"));
    }

    @Test
    void loaderReportsMissingClasspathManifestAsPendingValidation() {
        GpuIrOptimizationProposal proposal = proposalWithRuntimeEquivalencePayload();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());

        GpuIrOptimizationApprovalManifestLoader.Result result =
                GpuIrOptimizationApprovalManifestLoader.loadAndValidate(proposal, request, null);

        assertEquals("pending-manifest-validation", result.status());
        assertTrue(result.required());
        assertFalse(result.present());
        assertFalse(result.accepted());
        assertEquals("approval-manifest-not-loaded", result.firstBlocker());
        assertEquals(GpuIrOptimizationApprovalManifest.resourcePath(proposal, request), result.resourcePath());
        assertEquals("false", result.fields().get("accepted"));
        assertEquals("disabled", result.fields().get("productionMutation"));
    }

    @Test
    void loaderAcceptsApprovedClasspathManifestForExactProposal() throws Exception {
        GpuIrOptimizationProposal proposal = proposalWithRuntimeEquivalencePayload();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Path classpathRoot = temporaryDirectory.resolve("approval-loader");
        writeApprovedManifest(classpathRoot, proposal, request);

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{classpathRoot.toUri().toURL()},
                GpuIrOptimizationApprovalManifestTest.class.getClassLoader()
        )) {
            GpuIrOptimizationApprovalManifestLoader.Result result =
                    GpuIrOptimizationApprovalManifestLoader.loadAndValidate(proposal, request, classLoader);

            assertEquals("accepted", result.status());
            assertTrue(result.present());
            assertTrue(result.accepted());
            assertEquals(1, result.resourceCount());
            assertEquals("none", result.firstBlocker());
            assertTrue(result.validation().orElseThrow().valid());
            assertEquals("true", result.fields().get("accepted"));
            assertEquals("true", result.fields().get("validation.valid"));
        }
    }

    @Test
    void loaderBlocksStaleClasspathManifest() throws Exception {
        GpuIrOptimizationProposal proposal = proposalWithRuntimeEquivalencePayload();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Path classpathRoot = temporaryDirectory.resolve("stale-approval-loader");
        Path resource = resourcePath(classpathRoot, proposal, request);
        Files.createDirectories(resource.getParent());
        Files.writeString(
                resource,
                approvedManifestText(proposal, request)
                        .replace("binding.optimizedIrIdentity=" + proposal.optimizedIdentity(),
                                "binding.optimizedIrIdentity=irgpu:sha256:stale"),
                StandardCharsets.UTF_8
        );

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{classpathRoot.toUri().toURL()},
                GpuIrOptimizationApprovalManifestTest.class.getClassLoader()
        )) {
            GpuIrOptimizationApprovalManifestLoader.Result result =
                    GpuIrOptimizationApprovalManifestLoader.loadAndValidate(proposal, request, classLoader);

            assertEquals("blocked", result.status());
            assertTrue(result.present());
            assertFalse(result.accepted());
            assertEquals("manifest-optimized-ir-identity-mismatch", result.firstBlocker());
            assertTrue(result.validation().orElseThrow().blockers()
                    .contains("manifest-optimized-ir-identity-mismatch"));
        }
    }

    @Test
    void templateRequiresRealProposedDistinctArtifactAndAcceptedProof() {
        IrGpuArtifact artifact = artifact("body\n");
        GpuIrOptimizationProposal noChange = GpuIrOptimizationProposal.noChange(
                "optimizer:test",
                "optimizer:test:1",
                artifact,
                "no change"
        );
        GpuIrOptimizationProposal missingProof = GpuIrOptimizationProposal.proposed(
                "optimizer:test",
                "optimizer:test:1",
                artifact,
                artifact("body changed\n"),
                GpuRuntimeIrOptimizationProofArtifact.fromFields("ir-optimizer", "not-proven", Map.of()),
                List.of()
        );

        IllegalStateException noChangeFailure = assertThrows(
                IllegalStateException.class,
                () -> GpuIrOptimizationApprovalManifest.template(noChange, new GpuIrOptimizationProposalRequest(artifact))
        );
        IllegalStateException proofFailure = assertThrows(
                IllegalStateException.class,
                () -> GpuIrOptimizationApprovalManifest.template(missingProof, request(artifact))
        );

        assertTrue(noChangeFailure.getMessage().contains("proposal-decision-not-proposed"));
        assertTrue(proofFailure.getMessage().contains("proposal-proof-source-not-specific"));
    }

    private static GpuIrOptimizationProposal proposal() {
        IrGpuArtifact original = artifact("body\n");
        IrGpuArtifact optimized = artifact("body optimized\n");
        return GpuIrOptimizationProposal.proposed(
                "optimizer:test",
                "optimizer:test:1",
                original,
                optimized,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.test-proof",
                        "runtime-equivalence-passed",
                        Map.of("case.count", "1")
                ),
                List.of("test proposal")
        );
    }

    private static GpuIrOptimizationProposal proposalWithRuntimeEquivalencePayload() {
        return proposalWithProof(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "ir-optimizer.test-proof",
                "runtime-equivalence-passed",
                Map.ofEntries(
                        Map.entry("proof.runtimeEquivalencePayloadRequiredBeforeSelection", "true"),
                        Map.entry("runtimeEquivalencePayload.required", "true"),
                        Map.entry("runtimeEquivalencePayload.present", "true"),
                        Map.entry("runtimeEquivalencePayload.passed", "true"),
                        Map.entry("runtimeEquivalencePayload.cpuReference.present", "true"),
                        Map.entry("runtimeEquivalencePayload.preOptimizationOutput.present", "true"),
                        Map.entry("runtimeEquivalencePayload.postOptimizationOutput.present", "true"),
                        Map.entry("runtimeEquivalencePayload.tolerance.present", "true"),
                        Map.entry("runtimeEquivalencePayload.failureFixture.present", "true"),
                        Map.entry("runtimeEquivalencePayload.Case.Count", "1"),
                        Map.entry("runtimeEquivalencePayload.resource", "artifact://payload/cf"),
                        Map.entry(
                                "runtimeEquivalencePayload.comparisonMode",
                                "optimizer-family:constant-folding-materialization:review-candidate"
                        )
                )
        ));
    }

    private static GpuIrOptimizationProposal proposalWithIncompleteRuntimeEquivalencePayload() {
        return proposalWithProof(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "ir-optimizer.test-proof",
                "runtime-equivalence-passed",
                Map.of(
                        "proof.runtimeEquivalencePayloadRequiredBeforeSelection", "true",
                        "runtimeEquivalencePayload.required", "true"
                )
        ));
    }

    private static GpuIrOptimizationProposal proposalWithProof(GpuRuntimeIrOptimizationProofArtifact proofArtifact) {
        IrGpuArtifact original = artifact("body\n");
        IrGpuArtifact optimized = artifact("body optimized\n");
        return GpuIrOptimizationProposal.proposed(
                "optimizer:test",
                "optimizer:test:1",
                original,
                optimized,
                proofArtifact,
                List.of("test proposal")
        );
    }

    private static GpuIrOptimizationProposalRequest request(IrGpuArtifact artifact) {
        return new GpuIrOptimizationProposalRequest(
                artifact,
                "diagnostic",
                false,
                Map.of(
                        "backendTarget", "OPENCL",
                        "deviceProfile.vendor", "NVIDIA",
                        "deviceProfile.label", "RTX"
                )
        );
    }

    private static Properties approvedManifest(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) throws Exception {
        Properties properties = new Properties();
        properties.load(new StringReader(approvedManifestText(proposal, request)));
        return properties;
    }

    private static void writeApprovedManifest(
            Path classpathRoot,
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) throws Exception {
        Path resource = resourcePath(classpathRoot, proposal, request);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, approvedManifestText(proposal, request), StandardCharsets.UTF_8);
    }

    private static Path resourcePath(
            Path classpathRoot,
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        return classpathRoot.resolve(GpuIrOptimizationApprovalManifest.resourcePath(proposal, request));
    }

    private static String approvedManifestText(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) {
        return GpuIrOptimizationApprovalManifest.template(proposal, request)
                .replace("status=pending", "status=approved")
                .replace("approval.id=REQUIRED", "approval.id=approval:optimizer-test")
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
}
