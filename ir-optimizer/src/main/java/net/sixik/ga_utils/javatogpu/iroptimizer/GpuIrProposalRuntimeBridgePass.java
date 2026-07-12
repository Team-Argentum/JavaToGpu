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
        ArrayList<GpuRuntimeIrOptimizationPassReport> passReports = new ArrayList<>();
        for (GpuIrOptimizationProposalProvider provider : providers) {
            GpuIrOptimizationProposalRequest proposalRequest = new GpuIrOptimizationProposalRequest(
                    selected,
                    request.compileRequest().options().optimizationProfile(),
                    mutationAllowed,
                    contextFields(request, mutationAllowed)
            );
            GpuIrOptimizationSandwichReport sandwichReport = sandwichRunner.run(proposalRequest, provider);
            passReports.add(toPassReport(provider, sandwichReport).withStage(stage()));
            selected = sandwichReport.selectedArtifact();
            if (sandwichReport.requiresRollback()
                    || sandwichReport.status() == GpuIrOptimizationSandwichStatus.ORIGINAL_INVALID) {
                break;
            }
        }
        return new GpuRuntimeIrOptimizationReport(Optional.of(selected), passReports);
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
            GpuIrOptimizationSandwichReport report
    ) {
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(report.originalArtifact());
        String selectedIdentity = IrGpuArtifactIdentity.stableIdentity(report.selectedArtifact());
        Optional<GpuIrOptimizationProposal> proposal = report.proposal();
        String transformedIdentity = proposal.map(GpuIrOptimizationProposal::optimizedIdentity).orElse(selectedIdentity);
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = proposal
                .map(GpuIrOptimizationProposal::proofArtifact)
                .orElseGet(() -> proof(report.status().name().toLowerCase(java.util.Locale.ROOT), Map.of()));

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
        fields.put("mutationAllowed", Boolean.toString(mutationAllowed));
        return Map.copyOf(fields);
    }

    private static List<GpuIrOptimizationProposalProvider> loadProviders() {
        return GpuIrOptimizationProposalRegistry.loadFromServiceLoader().providers();
    }
}
