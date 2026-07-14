package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationOutcome;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPass;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPassReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationStage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime bridge that runs optional immutable proposal providers through the validation sandwich.
 */
public final class GpuIrProposalRuntimeBridgePass implements GpuRuntimeIrOptimizationPass {

    public static final String PASS_ID = GpuIrOptimizerModule.MODULE_ID + ".proposal-runtime-bridge";
    public static final String PASS_VERSION = PASS_ID + ":1";

    private final List<GpuIrOptimizationProposalProvider> providers;
    private final GpuIrOptimizationSandwichRunner sandwichRunner;
    private final boolean mutationAllowed;

    public GpuIrProposalRuntimeBridgePass() {
        this(loadProviders(), GpuIrOptimizationSandwichRunner.alwaysValid(), false);
    }

    public GpuIrProposalRuntimeBridgePass(
            List<GpuIrOptimizationProposalProvider> providers,
            GpuIrOptimizationSandwichRunner sandwichRunner,
            boolean mutationAllowed
    ) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.sandwichRunner = sandwichRunner == null ? GpuIrOptimizationSandwichRunner.alwaysValid() : sandwichRunner;
        this.mutationAllowed = mutationAllowed;
    }

    @Override
    public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
        Optional<IrGpuArtifact> current = request.artifact();
        if (current.isEmpty()) {
            GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.skipped(
                    passVersion(),
                    IrGpuArtifactIdentity.MISSING_ARTIFACT_IDENTITY,
                    "proposal runtime bridge skipped because no IrGpu artifact is available"
            ).withStage(stage());
            return new GpuRuntimeIrOptimizationReport(current, List.of(passReport));
        }
        if (providers.isEmpty()) {
            GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.skipped(
                    passVersion(),
                    IrGpuArtifactIdentity.stableIdentity(current),
                    "proposal runtime bridge found no proposal providers"
            ).withStage(stage());
            return new GpuRuntimeIrOptimizationReport(current, List.of(passReport));
        }

        IrGpuArtifact selected = current.orElseThrow();
        IrGpuArtifact reviewWorkingArtifact = selected;
        Optional<IrGpuArtifact> candidateArtifact = Optional.empty();
        ArrayList<GpuRuntimeIrOptimizationPassReport> passReports = new ArrayList<>();
        for (GpuIrOptimizationProposalProvider provider : providers) {
            ProviderPolicyGate policyGate = providerPolicyGate(request, provider);
            if (!policyGate.allowed()) {
                passReports.add(policySkippedReport(
                        provider,
                        reviewWorkingArtifact,
                        request,
                        policyGate
                ).withStage(stage()));
                continue;
            }
            GpuIrOptimizationProposalRequest proposalRequest = new GpuIrOptimizationProposalRequest(
                    reviewWorkingArtifact,
                    request.compileRequest().options().optimizationProfile(),
                    mutationAllowed,
                    contextFields(request, mutationAllowed)
            );
            GpuIrOptimizationSandwichReport sandwichReport = sandwichRunner.run(proposalRequest, provider);
            passReports.add(toPassReport(provider, sandwichReport, proposalRequest).withStage(stage()));
            Optional<IrGpuArtifact> selectedCandidateArtifact = selectedCandidateArtifact(sandwichReport);
            if (selectedCandidateArtifact.isPresent()) {
                candidateArtifact = selectedCandidateArtifact;
                reviewWorkingArtifact = selectedCandidateArtifact.orElseThrow();
            }
            if (mutationAllowed) {
                selected = sandwichReport.selectedArtifact();
                reviewWorkingArtifact = selected;
            }
            if (sandwichReport.requiresRollback()
                    || sandwichReport.status() == GpuIrOptimizationSandwichStatus.ORIGINAL_INVALID) {
                break;
            }
        }
        return new GpuRuntimeIrOptimizationReport(Optional.of(selected), candidateArtifact, passReports);
    }

    private static ProviderPolicyGate providerPolicyGate(
            GpuRuntimeIrOptimizationRequest request,
            GpuIrOptimizationProposalProvider provider
    ) {
        net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata policy =
                request.optimizerPolicy();
        String family = provider.optimizerFamily();
        if ("GPUOptimize".equals(policy.source()) && !policy.enabled()) {
            return new ProviderPolicyGate(false, family, "optimizer-policy-disabled");
        }
        if (policy.disabledFamilies().contains(family)) {
            return new ProviderPolicyGate(false, family, "optimizer-family-disabled");
        }
        if (!policy.enabledFamilies().isEmpty() && !policy.enabledFamilies().contains(family)) {
            return new ProviderPolicyGate(false, family, "optimizer-family-not-enabled");
        }
        return new ProviderPolicyGate(true, family, "none");
    }

    private static GpuRuntimeIrOptimizationPassReport policySkippedReport(
            GpuIrOptimizationProposalProvider provider,
            IrGpuArtifact currentArtifact,
            GpuRuntimeIrOptimizationRequest request,
            ProviderPolicyGate policyGate
    ) {
        String identity = IrGpuArtifactIdentity.stableIdentity(currentArtifact);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>(contextFields(request, false));
        fields.put("optimizerFamily", policyGate.family());
        fields.put("provider.extensionId", provider.extensionId());
        fields.put("provider.extensionVersion", provider.extensionVersion());
        fields.put("policyGate.status", "skipped");
        fields.put("policyGate.allowed", "false");
        fields.put("policyGate.reason", policyGate.reason());
        fields.put("policyGate.family", policyGate.family());
        fields.put("policyGate.providerInvoked", "false");
        fields.put("analysisOnly", "true");
        fields.put("mutationAllowed", "false");
        fields.put("selectionApplied", "false");
        fields.put("optimizedArtifactSelected", "false");
        fields.put("selectedIrReplacement", "false");
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "ir-optimizer.policy-gate",
                policyGate.reason(),
                fields
        );
        return new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                provider.extensionVersion(),
                GpuRuntimeIrOptimizationOutcome.SKIPPED,
                identity,
                identity,
                policyGate.reason(),
                "",
                proofArtifact,
                List.of("optimizer provider family '" + policyGate.family()
                        + "' skipped by @GPUOptimize policy: " + policyGate.reason())
        );
    }

    @Override
    public GpuRuntimeIrOptimizationStage stage() {
        return GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY;
    }

    @Override
    public String passName() {
        return PASS_ID;
    }

    @Override
    public String passVersion() {
        return PASS_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 90;
    }

    public List<GpuIrOptimizationProposalProvider> proposalProviders() {
        return providers;
    }

    private static GpuRuntimeIrOptimizationPassReport toPassReport(
            GpuIrOptimizationProposalProvider provider,
            GpuIrOptimizationSandwichReport report,
            GpuIrOptimizationProposalRequest request
    ) {
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(report.originalArtifact());
        String selectedIdentity = IrGpuArtifactIdentity.stableIdentity(report.selectedArtifact());
        Optional<GpuIrOptimizationProposal> proposal = report.proposal();
        String transformedIdentity = proposal.map(GpuIrOptimizationProposal::optimizedIdentity).orElse(selectedIdentity);
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = proposal
                .map(GpuIrOptimizationProposal::proofArtifact)
                .orElseGet(() -> proof(report.status().name().toLowerCase(java.util.Locale.ROOT), Map.of()));
        proofArtifact = withApprovalTemplateFields(proofArtifact, proposal, request, provider.getClass().getClassLoader());
        proofArtifact = withCandidateFields(proofArtifact, report);

        return switch (report.status()) {
            case OPTIMIZED_SELECTED -> new GpuRuntimeIrOptimizationPassReport(
                    GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                    provider.extensionVersion(),
                    GpuRuntimeIrOptimizationOutcome.APPLIED,
                    originalIdentity,
                    selectedIdentity,
                    "optimized-selected",
                    "",
                    proofArtifact,
                    report.diagnostics()
            );
            case OPTIMIZED_INVALID_ROLLED_BACK -> new GpuRuntimeIrOptimizationPassReport(
                    GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                    provider.extensionVersion(),
                    GpuRuntimeIrOptimizationOutcome.ROLLED_BACK,
                    originalIdentity,
                    transformedIdentity,
                    report.optimizedValidation().map(GpuIrOptimizationValidationResult::verdict)
                            .orElse("optimized-validation-failed"),
                    "optimized artifact rejected by validation sandwich",
                    proofArtifact,
                    report.diagnostics()
            );
            case ORIGINAL_INVALID -> new GpuRuntimeIrOptimizationPassReport(
                    GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                    provider.extensionVersion(),
                    GpuRuntimeIrOptimizationOutcome.FAILED,
                    originalIdentity,
                    originalIdentity,
                    report.originalValidation().verdict(),
                    "original artifact rejected by validation sandwich",
                    proofArtifact,
                    report.diagnostics()
            );
            case PROPOSAL_ONLY -> skipped(provider, originalIdentity, transformedIdentity, "proposal-only", proofArtifact, report.diagnostics());
            case PROPOSAL_REJECTED -> skipped(provider, originalIdentity, originalIdentity, "proposal-rejected", proofArtifact, report.diagnostics());
            case NO_CHANGE -> skipped(provider, originalIdentity, originalIdentity, "not-mutating", proofArtifact, report.diagnostics());
        };
    }

    private static GpuRuntimeIrOptimizationPassReport skipped(
            GpuIrOptimizationProposalProvider provider,
            String originalIdentity,
            String transformedIdentity,
            String proofStatus,
            GpuRuntimeIrOptimizationProofArtifact proofArtifact,
            List<String> diagnostics
    ) {
        return new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                provider.extensionVersion(),
                GpuRuntimeIrOptimizationOutcome.SKIPPED,
                originalIdentity,
                transformedIdentity,
                proofStatus,
                "",
                proofArtifact,
                diagnostics
        );
    }

    private static GpuRuntimeIrOptimizationProofArtifact proof(String verdict, Map<String, String> fields) {
        return GpuRuntimeIrOptimizationProofArtifact.fromFields("ir-optimizer.proposal-runtime-bridge", verdict, fields);
    }

    private static GpuRuntimeIrOptimizationProofArtifact withCandidateFields(
            GpuRuntimeIrOptimizationProofArtifact proofArtifact,
            GpuIrOptimizationSandwichReport report
    ) {
        if (report.optimizedArtifactCandidate().isEmpty()) {
            return proofArtifact;
        }
        LinkedHashMap<String, String> fields = new LinkedHashMap<>(proofArtifact.fields());
        fields.putAll(report.optimizedArtifactCandidate().orElseThrow().fields("optimizedArtifactCandidate"));
        return GpuRuntimeIrOptimizationProofArtifact.fromFields(
                proofArtifact.source(),
                proofArtifact.verdict(),
                fields
        );
    }

    private static GpuRuntimeIrOptimizationProofArtifact withApprovalTemplateFields(
            GpuRuntimeIrOptimizationProofArtifact proofArtifact,
            Optional<GpuIrOptimizationProposal> proposal,
            GpuIrOptimizationProposalRequest request,
            ClassLoader providerClassLoader
    ) {
        if (proposal.isEmpty()) {
            return proofArtifact;
        }
        GpuIrOptimizationApprovalTemplateResult approvalTemplate =
                GpuIrOptimizationApprovalTemplateFormatter.format(proposal.orElseThrow(), request);
        GpuIrOptimizationApprovalManifestLoader.Result approvalManifest =
                GpuIrOptimizationApprovalManifestLoader.loadAndValidate(
                        proposal.orElseThrow(),
                        request,
                        providerClassLoader
                );
        LinkedHashMap<String, String> fields = new LinkedHashMap<>(proofArtifact.fields());
        fields.put("approvalTemplate.status", approvalTemplate.status());
        fields.put("approvalTemplate.applicable", Boolean.toString(approvalTemplate.applicable()));
        fields.put("approvalTemplate.firstBlocker", approvalTemplate.firstBlocker());
        approvalTemplate.fields().forEach((key, value) -> fields.put("approvalTemplate." + key, value));
        approvalManifest.fields().forEach((key, value) -> fields.put("approvalManifest." + key, value));
        return GpuRuntimeIrOptimizationProofArtifact.fromFields(
                proofArtifact.source(),
                proofArtifact.verdict(),
                fields
        );
    }

    private static Optional<IrGpuArtifact> selectedCandidateArtifact(GpuIrOptimizationSandwichReport report) {
        if (report.status() != GpuIrOptimizationSandwichStatus.PROPOSAL_ONLY
                && report.status() != GpuIrOptimizationSandwichStatus.OPTIMIZED_SELECTED) {
            return Optional.empty();
        }
        return report.proposal().flatMap(GpuIrOptimizationProposal::optimizedArtifact);
    }

    private static Map<String, String> contextFields(GpuRuntimeIrOptimizationRequest request, boolean mutationAllowed) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("backendTarget", request.compileRequest().options().backendTarget().name());
        fields.put("optimizationProfile", request.compileRequest().options().optimizationProfile());
        fields.put("deviceProfile.backendTarget", request.compileRequest().deviceProfile().backendTarget().name());
        fields.put("deviceProfile.id", request.compileRequest().deviceProfile().deviceId());
        fields.put("deviceProfile.label", request.compileRequest().deviceProfile().deviceLabel());
        fields.put("deviceProfile.vendor", request.compileRequest().deviceProfile().vendor());
        fields.put("deviceProfile.driverVersion", request.compileRequest().deviceProfile().driverVersion());
        fields.put("deviceProfile.apiVersionText", request.compileRequest().deviceProfile().apiVersionText());
        fields.put("deviceProfile.deviceClass", request.compileRequest().deviceProfile().deviceClass().name());
        fields.put("fastMathAllowed", Boolean.toString(request.fastMathEnabled()));
        fields.put(GpuIrOptimizationPolicy.FAST_MATH_ALLOWED_FIELD, Boolean.toString(request.fastMathEnabled()));
        fields.put(
                GpuIrOptimizationPolicy.VENDOR_ADAPTATION_ALLOWED_FIELD,
                Boolean.toString(request.optimizerPolicy().vendorAdaptation())
        );
        fields.put("optimizerPolicy.fastMath", Boolean.toString(request.fastMathEnabled()));
        fields.put("optimizerPolicy.enabled", Boolean.toString(request.optimizerPolicyEnabled()));
        fields.put("optimizerPolicy.profile", request.optimizerPolicy().profile());
        fields.put("optimizerPolicy.enabledFamilies", String.join(",", request.optimizerPolicy().enabledFamilies()));
        fields.put("optimizerPolicy.disabledFamilies", String.join(",", request.optimizerPolicy().disabledFamilies()));
        fields.put("optimizerPolicy.journal", Boolean.toString(request.optimizerJournalRequested()));
        fields.put("optimizerPolicy.dumpArtifacts", Boolean.toString(request.optimizerArtifactDumpRequested()));
        fields.put("optimizerPolicy.productionIntent", Boolean.toString(request.optimizerPolicy().productionIntent()));
        fields.put("optimizerPolicy.vendorAdaptation", Boolean.toString(request.optimizerPolicy().vendorAdaptation()));
        fields.put("optimizerPolicy.vectorization", request.optimizerPolicy().vectorization());
        fields.put("optimizerPolicy.resourceShaping", Boolean.toString(request.optimizerPolicy().resourceShaping()));
        fields.put("optimizerPolicy.source", request.optimizerPolicy().source());
        fields.put("mutationAllowed", Boolean.toString(mutationAllowed));
        return Map.copyOf(fields);
    }

    private static List<GpuIrOptimizationProposalProvider> loadProviders() {
        return GpuIrOptimizationProposalRegistry.loadFromServiceLoader().providers();
    }

    private record ProviderPolicyGate(boolean allowed, String family, String reason) {
    }
}
