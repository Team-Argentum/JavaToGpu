package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Diagnostic-only entrypoint for future structural runtime IR peephole rewrites.
 *
 * <p>The current packaged artifact stores {@code ir-text-v1} method bodies rather than a typed runtime expression tree.
 * This pass refuses to implement string-based rewrites and reports the missing typed-IR capability instead. Future rule
 * implementations can replace this blocker once reconstructable typed nodes are available at runtime.</p>
 */
public final class GpuRuntimeIrPeepholePass implements GpuRuntimeIrOptimizationPass {

    public static final String VERSION = "optimizer:peephole-diagnostic-v1";
    public static final String TYPED_BODY_FORMAT = net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody.FORMAT;
    private final GpuRuntimeIrPeepholeRuleRegistry ruleRegistry;

    public GpuRuntimeIrPeepholePass() {
        this(GpuRuntimeIrPeepholeRuleRegistry.loadWithBuiltIns());
    }

    public GpuRuntimeIrPeepholePass(GpuRuntimeIrPeepholeRuleRegistry ruleRegistry) {
        this.ruleRegistry = java.util.Objects.requireNonNull(ruleRegistry, "ruleRegistry");
    }

    @Override
    public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
        Optional<IrGpuArtifact> artifact = request.artifact();
        String identity = GpuRuntimeIrOptimizerRegistry.identityOf(artifact);
        GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis = artifact
                .map(value -> ruleRegistry.analyze(request, value))
                .orElseGet(() -> new GpuRuntimeIrPeepholeRuleRegistry.Analysis(List.of(), List.of(), false));
        Map<String, String> fields = diagnosticFields(artifact, request, analysis, identity);
        GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.skipped(
                VERSION,
                identity,
                diagnostic(fields)
        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "runtime.peephole.preflight",
                "blocked",
                fields
        ));
        return new GpuRuntimeIrOptimizationReport(artifact, List.of(passReport), request.strategyDecision());
    }

    @Override
    public String passName() {
        return "peephole";
    }

    @Override
    public String passVersion() {
        return VERSION;
    }

    @Override
    public boolean requiresFastMath() {
        return true;
    }

    private Map<String, String> diagnosticFields(
            Optional<IrGpuArtifact> artifact,
            GpuRuntimeIrOptimizationRequest request,
            GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis,
            String originalIrIdentity
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("optimizerFamily", "peephole");
        fields.put("mode", "diagnostic-only");
        fields.put("mutationEnabled", "false");
        fields.put("fastMath", Boolean.toString(request.fastMathEnabled()));
        fields.put("policyEnabled", Boolean.toString(request.optimizerPolicyEnabled()));
        fields.put("policyProfile", request.optimizerPolicy().profile());
        fields.put("policyEnabledFamilies", String.join(",", request.optimizerPolicy().enabledFamilies()));
        fields.put("policyDisabledFamilies", String.join(",", request.optimizerPolicy().disabledFamilies()));
        fields.put("policyJournal", Boolean.toString(request.optimizerJournalRequested()));
        fields.put("policyDumpArtifacts", Boolean.toString(request.optimizerArtifactDumpRequested()));
        fields.put("policyProductionIntent", Boolean.toString(request.optimizerPolicy().productionIntent()));
        fields.put("policyVendorAdaptation", Boolean.toString(request.optimizerPolicy().vendorAdaptation()));
        fields.put("policyVectorization", request.optimizerPolicy().vectorization());
        fields.put("policyResourceShaping", Boolean.toString(request.optimizerPolicy().resourceShaping()));
        fields.put("policySource", request.optimizerPolicy().source());
        int methodBodyCount = artifact.map(value -> value.module().methodBodies().size()).orElse(0);
        long typedBodyCount = artifact.stream()
                .flatMap(value -> value.module().methodBodies().stream())
                .filter(body -> body.typedBody().available())
                .count();
        long textBodyCount = artifact.stream()
                .flatMap(value -> value.module().methodBodies().stream())
                .filter(body -> "ir-text-v1".equals(body.format()))
                .count();
        boolean typedIrAvailable = methodBodyCount > 0 && typedBodyCount == methodBodyCount;
        fields.put("methodBody.count", Integer.toString(methodBodyCount));
        fields.put("typedBody.count", Long.toString(typedBodyCount));
        fields.put("irTextBody.count", Long.toString(textBodyCount));
        fields.put("typedIrAvailable", Boolean.toString(typedIrAvailable));
        fields.put("rule.count", Integer.toString(ruleRegistry.rules().size()));
        fields.put("candidate.count", Integer.toString(analysis.candidateCount()));
        fields.put("proposal.count", Integer.toString(analysis.proposalCount()));
        fields.put("replacementPlan.partial.count", Integer.toString(analysis.partialReplacementPlanCount()));
        fields.put("replacementPlan.firstBlocker", analysis.firstReplacementPlanBlocker());
        fields.put(
                "replacementPlan.validation.invalid.count",
                Integer.toString(analysis.invalidReplacementPlanValidationCount())
        );
        fields.put("replacementPlan.validation.firstBlocker", analysis.firstReplacementPlanValidationBlocker());
        List<GpuRuntimeIrPeepholeRewriteVisitPreflight> visitPreflights = rewriteVisitPreflights(analysis.ruleReports());
        List<GpuRuntimeIrPeepholeReplacementBlueprint> blueprints = replacementBlueprints(visitPreflights);
        appendRewriteVisitorFields(fields, "rewriteVisitor", visitPreflights);
        appendReplacementBlueprintFields(fields, "replacementBlueprint", blueprints);
        List<GpuRuntimeIrPeepholeRewriteTransactionPreflight> transactions = rewriteTransactions(blueprints, visitPreflights);
        appendRewriteTransactionFields(fields, "rewriteTransaction", transactions);
        List<GpuRuntimeIrPeepholeNodeIdAllocationPreflight> nodeIdAllocations = nodeIdAllocations(transactions, visitPreflights);
        appendNodeIdAllocationFields(fields, "nodeIdAllocation", nodeIdAllocations);
        List<GpuRuntimeIrPeepholeReplacementNodePreflight> replacementNodes = replacementNodes(nodeIdAllocations, blueprints);
        appendReplacementNodeFields(fields, "replacementNode", replacementNodes);
        List<GpuRuntimeIrPeepholeGraphPatchPreflight> graphPatches = graphPatches(transactions, replacementNodes);
        appendGraphPatchFields(fields, "graphPatch", graphPatches);
        List<GpuRuntimeIrPeepholeTransformedGraphPreflight> transformedGraphs = transformedGraphs(graphPatches, originalIrIdentity);
        appendTransformedGraphFields(fields, "transformedGraph", transformedGraphs);
        List<GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight> artifactEnvelopes = irArtifactEnvelopes(transformedGraphs);
        appendIrArtifactEnvelopeFields(fields, "irArtifactEnvelope", artifactEnvelopes);
        List<GpuRuntimeIrPeepholeRewriteSketch> rewriteSketches = rewriteSketches(analysis.ruleReports());
        List<GpuRuntimeIrPeepholeRewriteSketchConflict> rewriteSketchConflicts = rewriteSketchConflicts(rewriteSketches);
        appendRewriteSketchFields(fields, "rewriteSketch", rewriteSketches);
        appendRewriteSketchConflictFields(fields, "rewriteSketch.conflict", rewriteSketchConflicts);
        GpuRuntimeIrPeepholeRewriteSelectionReadiness selectionReadiness =
                GpuRuntimeIrPeepholeRewriteSelectionReadiness.from(rewriteSketches, rewriteSketchConflicts);
        fields.putAll(selectionReadiness.fields("rewriteSelection"));
        GpuRuntimeIrPeepholeRewriteProofReadiness proofReadiness =
                GpuRuntimeIrPeepholeRewriteProofReadiness.from(selectionReadiness, originalIrIdentity);
        fields.putAll(proofReadiness.fields("rewriteProof"));
        GpuRuntimeIrPeepholeRewriteReviewPackage reviewPackage = GpuRuntimeIrPeepholeRewriteReviewPackage
                .from(selectionReadiness, proofReadiness);
        fields.putAll(reviewPackage.fields("rewriteReviewPackage"));
        List<GpuRuntimeIrPeepholeArtifactProofBindingPreflight> artifactProofBindings = artifactProofBindings(
                artifactEnvelopes,
                proofReadiness,
                reviewPackage
        );
        appendArtifactProofBindingFields(
                fields,
                "artifactProofBinding",
                artifactProofBindings
        );
        appendArtifactSelectionFields(
                fields,
                "artifactSelection",
                artifactSelections(artifactProofBindings)
        );
        fields.put("rule.mix.candidate.count", Integer.toString(candidateCount(analysis, "mix")));
        fields.put("rule.madFma.candidate.count", Integer.toString(candidateCount(analysis, "madFma")));
        fields.put("rule.clamp.candidate.count", Integer.toString(candidateCount(analysis, "clamp")));
        fields.put("rule.step.candidate.count", Integer.toString(candidateCount(analysis, "step")));
        fields.put("rule.dot.candidate.count", Integer.toString(candidateCount(analysis, "dot")));
        appendRuleFields(fields, analysis, originalIrIdentity);
        fields.put("rule.execution.count", Integer.toString(analysis.executionReports().size()));
        fields.put("rule.execution.failedContinued.count", Long.toString(analysis.executionReports().stream()
                .filter(report -> report.outcome() == net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome.FAILED_CONTINUED)
                .count()));
        fields.put("rule.execution.failedClosed.count", Long.toString(analysis.executionReports().stream()
                .filter(report -> report.outcome() == net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome.FAILED_CLOSED)
                .count()));
        fields.put("firstBlocker", firstBlocker(
                artifact,
                typedIrAvailable,
                analysis.candidateCount(),
                analysis.invalidReplacementPlanValidationCount(),
                analysis.firstReplacementPlanValidationBlocker(),
                analysis.partialReplacementPlanCount(),
                analysis.firstReplacementPlanBlocker(),
                analysis.failedClosed()
        ));
        return Map.copyOf(fields);
    }

    private static int candidateCount(GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis, String ruleId) {
        return analysis.ruleReports().stream()
                .filter(report -> ruleId.equals(report.ruleId()))
                .mapToInt(GpuRuntimeIrPeepholeRuleReport::candidateCount)
                .sum();
    }

    private void appendRuleFields(
            LinkedHashMap<String, String> fields,
            GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis,
            String originalIrIdentity
    ) {
        java.util.LinkedHashMap<String, List<GpuRuntimeIrPeepholeRuleReport>> reportsByRule = new java.util.LinkedHashMap<>();
        for (GpuRuntimeIrPeepholeRuleReport report : analysis.ruleReports()) {
            reportsByRule.computeIfAbsent(report.ruleId(), ignored -> new java.util.ArrayList<>()).add(report);
        }
        for (int index = 0; index < ruleRegistry.rules().size(); index++) {
            GpuRuntimeIrPeepholeRule rule = ruleRegistry.rules().get(index);
            List<GpuRuntimeIrPeepholeRuleReport> reports = reportsByRule.getOrDefault(rule.ruleId(), List.of());
            String prefix = "rule." + index;
            fields.put(prefix + ".id", rule.ruleId());
            fields.put(prefix + ".version", rule.ruleVersion());
            fields.put(prefix + ".extensionId", rule.extensionId());
            fields.put(prefix + ".extensionVersion", rule.extensionVersion());
            fields.put(prefix + ".candidate.count", Integer.toString(reports.stream()
                    .mapToInt(GpuRuntimeIrPeepholeRuleReport::candidateCount)
                    .sum()));
            fields.put(prefix + ".proposal.count", Integer.toString(reports.stream()
                    .mapToInt(GpuRuntimeIrPeepholeRuleReport::proposalCount)
                    .sum()));
            int candidateCount = reports.stream().mapToInt(GpuRuntimeIrPeepholeRuleReport::candidateCount).sum();
            int proposalCount = reports.stream().mapToInt(GpuRuntimeIrPeepholeRuleReport::proposalCount).sum();
            long partialPlanCount = reports.stream()
                    .flatMap(report -> report.replacementPlans().stream())
                    .filter(plan -> !plan.complete())
                    .count();
            boolean mutationProposed = reports.stream().anyMatch(GpuRuntimeIrPeepholeRuleReport::mutationProposed);
            fields.put(prefix + ".applied.count", mutationProposed ? "1" : "0");
            fields.put(prefix + ".skipped.count", reports.isEmpty() || (candidateCount == 0 && proposalCount == 0 && partialPlanCount == 0) ? "1" : "0");
            fields.put(prefix + ".blocked.count", !mutationProposed && (candidateCount > 0 || partialPlanCount > 0) ? "1" : "0");
            fields.put(prefix + ".mutationProposed", Boolean.toString(mutationProposed));
            fields.put(prefix + ".proofStatus", reports.isEmpty()
                    ? executionProofStatus(rule, analysis)
                    : reports.get(0).proofStatus());
            appendReplacementPlanFields(fields, prefix, reports);
            List<GpuRuntimeIrPeepholeRewriteVisitPreflight> ruleVisitPreflights = rewriteVisitPreflights(reports);
            List<GpuRuntimeIrPeepholeReplacementBlueprint> ruleBlueprints = replacementBlueprints(ruleVisitPreflights);
            appendRewriteVisitorFields(fields, prefix + ".rewriteVisitor", ruleVisitPreflights);
            appendReplacementBlueprintFields(
                    fields,
                    prefix + ".replacementBlueprint",
                    ruleBlueprints
            );
            List<GpuRuntimeIrPeepholeRewriteTransactionPreflight> ruleTransactions = rewriteTransactions(
                    ruleBlueprints,
                    ruleVisitPreflights
            );
            appendRewriteTransactionFields(
                    fields,
                    prefix + ".rewriteTransaction",
                    ruleTransactions
            );
            List<GpuRuntimeIrPeepholeNodeIdAllocationPreflight> ruleNodeIdAllocations = nodeIdAllocations(
                    ruleTransactions,
                    ruleVisitPreflights
            );
            appendNodeIdAllocationFields(
                    fields,
                    prefix + ".nodeIdAllocation",
                    ruleNodeIdAllocations
            );
            List<GpuRuntimeIrPeepholeReplacementNodePreflight> ruleReplacementNodes = replacementNodes(
                    ruleNodeIdAllocations,
                    ruleBlueprints
            );
            appendReplacementNodeFields(
                    fields,
                    prefix + ".replacementNode",
                    ruleReplacementNodes
            );
            List<GpuRuntimeIrPeepholeGraphPatchPreflight> ruleGraphPatches = graphPatches(
                    ruleTransactions,
                    ruleReplacementNodes
            );
            appendGraphPatchFields(
                    fields,
                    prefix + ".graphPatch",
                    ruleGraphPatches
            );
            List<GpuRuntimeIrPeepholeTransformedGraphPreflight> ruleTransformedGraphs = transformedGraphs(
                    ruleGraphPatches,
                    originalIrIdentity
            );
            appendTransformedGraphFields(
                    fields,
                    prefix + ".transformedGraph",
                    ruleTransformedGraphs
            );
            List<GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight> ruleArtifactEnvelopes = irArtifactEnvelopes(
                    ruleTransformedGraphs
            );
            appendIrArtifactEnvelopeFields(
                    fields,
                    prefix + ".irArtifactEnvelope",
                    ruleArtifactEnvelopes
            );
            List<GpuRuntimeIrPeepholeRewriteSketch> ruleSketches = rewriteSketches(reports);
            List<GpuRuntimeIrPeepholeRewriteSketchConflict> ruleConflicts = rewriteSketchConflicts(ruleSketches);
            appendRewriteSketchFields(fields, prefix + ".rewriteSketch", ruleSketches);
            appendRewriteSketchConflictFields(fields, prefix + ".rewriteSketch.conflict", ruleConflicts);
            GpuRuntimeIrPeepholeRewriteSelectionReadiness ruleSelectionReadiness =
                    GpuRuntimeIrPeepholeRewriteSelectionReadiness.from(ruleSketches, ruleConflicts);
            fields.putAll(ruleSelectionReadiness.fields(prefix + ".rewriteSelection"));
            GpuRuntimeIrPeepholeRewriteProofReadiness ruleProofReadiness =
                    GpuRuntimeIrPeepholeRewriteProofReadiness.from(ruleSelectionReadiness, originalIrIdentity);
            fields.putAll(ruleProofReadiness.fields(prefix + ".rewriteProof"));
            GpuRuntimeIrPeepholeRewriteReviewPackage ruleReviewPackage = GpuRuntimeIrPeepholeRewriteReviewPackage
                    .from(ruleSelectionReadiness, ruleProofReadiness);
            fields.putAll(ruleReviewPackage.fields(prefix + ".rewriteReviewPackage"));
            List<GpuRuntimeIrPeepholeArtifactProofBindingPreflight> ruleArtifactProofBindings = artifactProofBindings(
                    ruleArtifactEnvelopes,
                    ruleProofReadiness,
                    ruleReviewPackage
            );
            appendArtifactProofBindingFields(
                    fields,
                    prefix + ".artifactProofBinding",
                    ruleArtifactProofBindings
            );
            appendArtifactSelectionFields(
                    fields,
                    prefix + ".artifactSelection",
                    artifactSelections(ruleArtifactProofBindings)
            );
            fields.put(prefix + ".firstBlocker", ruleFirstBlocker(fields, prefix, reports.isEmpty()));
        }
    }

    private static String ruleFirstBlocker(
            Map<String, String> fields,
            String prefix,
            boolean reportsEmpty
    ) {
        String validationBlocker = fields.getOrDefault(prefix + ".replacementPlan.validation.firstBlocker", "none");
        if (!validationBlocker.isBlank() && !"none".equals(validationBlocker)) {
            return validationBlocker;
        }
        String replacementBlocker = fields.getOrDefault(prefix + ".replacementPlan.firstBlocker", "none");
        if (!replacementBlocker.isBlank() && !"none".equals(replacementBlocker)) {
            return replacementBlocker;
        }
        if (parsePositiveInt(fields.get(prefix + ".candidate.count")) > 0) {
            return "rewrite-engine-not-implemented";
        }
        if (reportsEmpty) {
            return fields.getOrDefault(prefix + ".proofStatus", "not-run");
        }
        return "none";
    }

    private static void appendReplacementPlanFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeRuleReport> reports
    ) {
        List<GpuRuntimeIrPeepholeReplacementPlan> plans = reports.stream()
                .flatMap(report -> report.replacementPlans().stream())
                .toList();
        fields.put(prefix + ".replacementPlan.count", Integer.toString(plans.size()));
        fields.put(prefix + ".replacementPlan.complete.count", Long.toString(plans.stream()
                .filter(GpuRuntimeIrPeepholeReplacementPlan::complete)
                .count()));
        fields.put(prefix + ".replacementPlan.partial.count", Long.toString(plans.stream()
                .filter(plan -> !plan.complete())
                .count()));
        fields.put(prefix + ".replacementPlan.firstBlocker", plans.stream()
                .filter(plan -> !plan.complete())
                .map(GpuRuntimeIrPeepholeReplacementPlan::firstBlocker)
                .findFirst()
                .orElse("none"));
        if (!plans.isEmpty()) {
            fields.putAll(plans.get(0).fields(prefix + ".replacementPlan.0"));
        }
        List<GpuRuntimeIrPeepholeReplacementPlanValidation> validations = reports.stream()
                .flatMap(report -> report.replacementPlanValidations().stream())
                .toList();
        fields.put(prefix + ".replacementPlan.validation.count", Integer.toString(validations.size()));
        fields.put(prefix + ".replacementPlan.validation.valid.count", Long.toString(validations.stream()
                .filter(GpuRuntimeIrPeepholeReplacementPlanValidation::valid)
                .count()));
        fields.put(prefix + ".replacementPlan.validation.invalid.count", Long.toString(validations.stream()
                .filter(validation -> !validation.valid())
                .count()));
        fields.put(prefix + ".replacementPlan.validation.firstBlocker", validations.stream()
                .filter(validation -> !validation.valid())
                .map(GpuRuntimeIrPeepholeReplacementPlanValidation::firstBlocker)
                .findFirst()
                .orElse("none"));
        if (!validations.isEmpty()) {
            fields.putAll(validations.get(0).fields(prefix + ".replacementPlan.validation.0"));
        }
    }

    private static List<GpuRuntimeIrPeepholeRewriteSketch> rewriteSketches(
            List<GpuRuntimeIrPeepholeRuleReport> reports
    ) {
        java.util.ArrayList<GpuRuntimeIrPeepholeRewriteSketch> sketches = new java.util.ArrayList<>();
        for (GpuRuntimeIrPeepholeRuleReport report : reports) {
            List<GpuRuntimeIrPeepholeReplacementPlan> plans = report.replacementPlans();
            List<GpuRuntimeIrPeepholeReplacementPlanValidation> validations = report.replacementPlanValidations();
            for (int index = 0; index < plans.size(); index++) {
                GpuRuntimeIrPeepholeReplacementPlanValidation validation = index < validations.size()
                        ? validations.get(index)
                        : null;
                sketches.add(GpuRuntimeIrPeepholeRewriteSketch.from(plans.get(index), validation));
            }
        }
        return List.copyOf(sketches);
    }

    private static List<GpuRuntimeIrPeepholeRewriteVisitPreflight> rewriteVisitPreflights(
            List<GpuRuntimeIrPeepholeRuleReport> reports
    ) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        return reports.stream()
                .flatMap(report -> report.rewriteVisitPreflights().stream())
                .toList();
    }

    private static <T> void appendPreflightSummaryFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<T> items,
            Predicate<T> ready,
            Function<T, String> firstBlocker
    ) {
        fields.put(prefix + ".count", Integer.toString(items.size()));
        fields.put(prefix + ".ready.count", Long.toString(items.stream()
                .filter(ready)
                .count()));
        fields.put(prefix + ".blocked.count", Long.toString(items.stream()
                .filter(item -> !ready.test(item))
                .count()));
        fields.put(prefix + ".firstBlocker", items.stream()
                .filter(item -> !ready.test(item))
                .map(firstBlocker)
                .findFirst()
                .orElse("none"));
    }

    private static <T> void appendFirstPreflightFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<T> items,
            Function<T, Map<String, String>> itemFields
    ) {
        if (!items.isEmpty()) {
            fields.putAll(itemFields.apply(items.get(0)));
        }
    }

    private static void appendProofBindingGuardFields(LinkedHashMap<String, String> fields, String prefix) {
        fields.put(prefix + ".proofBound", "false");
        fields.put(prefix + ".rollbackBound", "false");
        fields.put(prefix + ".approvalBound", "false");
        appendArtifactBuildGuardFields(fields, prefix);
        appendMutationGuardFields(fields, prefix);
    }

    private static void appendArtifactSelectionGuardFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            boolean productionGateRequired
    ) {
        fields.put(prefix + ".productionGateRequired", Boolean.toString(productionGateRequired));
        fields.put(prefix + ".productionGateAccepted", "false");
        fields.put(prefix + ".mutationPolicyAllowed", "false");
        fields.put(prefix + ".selectionApplied", "false");
        fields.put(prefix + ".optimizedArtifactSelected", "false");
        appendArtifactBuildGuardFields(fields, prefix);
        appendMutationGuardFields(fields, prefix);
    }

    private static void appendArtifactBuildGuardFields(LinkedHashMap<String, String> fields, String prefix) {
        fields.put(prefix + ".optimizedArtifactBuilt", "false");
        fields.put(prefix + ".transformedIrBuilt", "false");
    }

    private static void appendMutationGuardFields(LinkedHashMap<String, String> fields, String prefix) {
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
    }

    private static void appendRewriteVisitorFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeRewriteVisitPreflight> preflights
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                preflights,
                GpuRuntimeIrPeepholeRewriteVisitPreflight::visitorReady,
                GpuRuntimeIrPeepholeRewriteVisitPreflight::firstBlocker
        );
        fields.put(prefix + ".visitorImplemented", "true");
        fields.put(prefix + ".replacementBuilderImplemented", "false");
        fields.put(prefix + ".transformedIrBuilt", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        appendFirstPreflightFields(fields, prefix, preflights, preflight -> preflight.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeReplacementBlueprint> replacementBlueprints(
            List<GpuRuntimeIrPeepholeRewriteVisitPreflight> preflights
    ) {
        if (preflights == null || preflights.isEmpty()) {
            return List.of();
        }
        return preflights.stream()
                .map(GpuRuntimeIrPeepholeReplacementBlueprint::from)
                .toList();
    }

    private static void appendReplacementBlueprintFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeReplacementBlueprint> blueprints
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                blueprints,
                GpuRuntimeIrPeepholeReplacementBlueprint::blueprintReady,
                GpuRuntimeIrPeepholeReplacementBlueprint::firstBlocker
        );
        fields.put(prefix + ".blueprintImplemented", "true");
        fields.put(prefix + ".replacementBuilderImplemented", "false");
        fields.put(prefix + ".transformedIrBuilt", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        appendFirstPreflightFields(fields, prefix, blueprints, blueprint -> blueprint.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeRewriteTransactionPreflight> rewriteTransactions(
            List<GpuRuntimeIrPeepholeReplacementBlueprint> blueprints,
            List<GpuRuntimeIrPeepholeRewriteVisitPreflight> preflights
    ) {
        if (blueprints == null || blueprints.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<GpuRuntimeIrPeepholeRewriteTransactionPreflight> transactions = new java.util.ArrayList<>();
        for (int index = 0; index < blueprints.size(); index++) {
            GpuRuntimeIrPeepholeRewriteVisitPreflight preflight = preflights != null && index < preflights.size()
                    ? preflights.get(index)
                    : null;
            transactions.add(GpuRuntimeIrPeepholeRewriteTransactionPreflight.from(blueprints.get(index), preflight));
        }
        return List.copyOf(transactions);
    }

    private static void appendRewriteTransactionFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeRewriteTransactionPreflight> transactions
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                transactions,
                GpuRuntimeIrPeepholeRewriteTransactionPreflight::transactionReady,
                GpuRuntimeIrPeepholeRewriteTransactionPreflight::firstBlocker
        );
        fields.put(prefix + ".transactionPreflightImplemented", "true");
        fields.put(prefix + ".nodeIdAllocatorImplemented", "false");
        fields.put(prefix + ".graphRewriteImplemented", "false");
        fields.put(prefix + ".transformedIrBuilt", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        appendFirstPreflightFields(fields, prefix, transactions, transaction -> transaction.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeNodeIdAllocationPreflight> nodeIdAllocations(
            List<GpuRuntimeIrPeepholeRewriteTransactionPreflight> transactions,
            List<GpuRuntimeIrPeepholeRewriteVisitPreflight> preflights
    ) {
        if (transactions == null || transactions.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<GpuRuntimeIrPeepholeNodeIdAllocationPreflight> allocations = new java.util.ArrayList<>();
        for (int index = 0; index < transactions.size(); index++) {
            GpuRuntimeIrPeepholeRewriteVisitPreflight preflight = preflights != null && index < preflights.size()
                    ? preflights.get(index)
                    : null;
            allocations.add(GpuRuntimeIrPeepholeNodeIdAllocationPreflight.from(transactions.get(index), preflight));
        }
        return List.copyOf(allocations);
    }

    private static void appendNodeIdAllocationFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeNodeIdAllocationPreflight> allocations
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                allocations,
                GpuRuntimeIrPeepholeNodeIdAllocationPreflight::allocationReady,
                GpuRuntimeIrPeepholeNodeIdAllocationPreflight::firstBlocker
        );
        fields.put(prefix + ".allocationPreflightImplemented", "true");
        fields.put(prefix + ".nodeIdsReserved", "false");
        fields.put(prefix + ".nodeIdAllocatorApplied", "false");
        fields.put(prefix + ".graphRewriteImplemented", "false");
        fields.put(prefix + ".transformedIrBuilt", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        appendFirstPreflightFields(fields, prefix, allocations, allocation -> allocation.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeReplacementNodePreflight> replacementNodes(
            List<GpuRuntimeIrPeepholeNodeIdAllocationPreflight> allocations,
            List<GpuRuntimeIrPeepholeReplacementBlueprint> blueprints
    ) {
        if (allocations == null || allocations.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<GpuRuntimeIrPeepholeReplacementNodePreflight> nodes = new java.util.ArrayList<>();
        for (int index = 0; index < allocations.size(); index++) {
            GpuRuntimeIrPeepholeReplacementBlueprint blueprint = blueprints != null && index < blueprints.size()
                    ? blueprints.get(index)
                    : null;
            nodes.add(GpuRuntimeIrPeepholeReplacementNodePreflight.from(allocations.get(index), blueprint));
        }
        return List.copyOf(nodes);
    }

    private static void appendReplacementNodeFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeReplacementNodePreflight> nodes
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                nodes,
                GpuRuntimeIrPeepholeReplacementNodePreflight::replacementNodeReady,
                GpuRuntimeIrPeepholeReplacementNodePreflight::firstBlocker
        );
        fields.put(prefix + ".replacementNodePreflightImplemented", "true");
        fields.put(prefix + ".replacementNodeBuilt", "false");
        fields.put(prefix + ".replacementBuilderImplemented", "false");
        fields.put(prefix + ".graphRewriteImplemented", "false");
        fields.put(prefix + ".transformedIrBuilt", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        appendFirstPreflightFields(fields, prefix, nodes, node -> node.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeGraphPatchPreflight> graphPatches(
            List<GpuRuntimeIrPeepholeRewriteTransactionPreflight> transactions,
            List<GpuRuntimeIrPeepholeReplacementNodePreflight> replacementNodes
    ) {
        if (transactions == null || transactions.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<GpuRuntimeIrPeepholeGraphPatchPreflight> patches = new java.util.ArrayList<>();
        for (int index = 0; index < transactions.size(); index++) {
            GpuRuntimeIrPeepholeReplacementNodePreflight replacementNode = replacementNodes != null && index < replacementNodes.size()
                    ? replacementNodes.get(index)
                    : null;
            patches.add(GpuRuntimeIrPeepholeGraphPatchPreflight.from(transactions.get(index), replacementNode));
        }
        return List.copyOf(patches);
    }

    private static void appendGraphPatchFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeGraphPatchPreflight> patches
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                patches,
                GpuRuntimeIrPeepholeGraphPatchPreflight::graphPatchReady,
                GpuRuntimeIrPeepholeGraphPatchPreflight::firstBlocker
        );
        fields.put(prefix + ".graphPatchPreflightImplemented", "true");
        fields.put(prefix + ".graphPatchApplied", "false");
        fields.put(prefix + ".graphRewriteImplemented", "false");
        fields.put(prefix + ".transformedIrBuilt", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        appendFirstPreflightFields(fields, prefix, patches, patch -> patch.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeTransformedGraphPreflight> transformedGraphs(
            List<GpuRuntimeIrPeepholeGraphPatchPreflight> graphPatches,
            String originalIrIdentity
    ) {
        if (graphPatches == null || graphPatches.isEmpty()) {
            return List.of();
        }
        return graphPatches.stream()
                .map(patch -> GpuRuntimeIrPeepholeTransformedGraphPreflight.from(patch, originalIrIdentity))
                .toList();
    }

    private static void appendTransformedGraphFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeTransformedGraphPreflight> transformedGraphs
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                transformedGraphs,
                GpuRuntimeIrPeepholeTransformedGraphPreflight::materializationReady,
                GpuRuntimeIrPeepholeTransformedGraphPreflight::firstBlocker
        );
        fields.put(prefix + ".materializationPreflightImplemented", "true");
        fields.put(prefix + ".transformedGraphBuilt", "false");
        fields.put(prefix + ".transformedIrBuilt", "false");
        fields.put(prefix + ".graphPatchApplied", "false");
        fields.put(prefix + ".graphRewriteImplemented", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        appendFirstPreflightFields(fields, prefix, transformedGraphs, graph -> graph.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight> irArtifactEnvelopes(
            List<GpuRuntimeIrPeepholeTransformedGraphPreflight> transformedGraphs
    ) {
        if (transformedGraphs == null || transformedGraphs.isEmpty()) {
            return List.of();
        }
        return transformedGraphs.stream()
                .map(GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight::from)
                .toList();
    }

    private static void appendIrArtifactEnvelopeFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight> envelopes
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                envelopes,
                GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight::artifactEnvelopeReady,
                GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight::firstBlocker
        );
        fields.put(prefix + ".artifactEnvelopePreflightImplemented", "true");
        fields.put(prefix + ".artifactEnvelopeBuilt", "false");
        fields.put(prefix + ".optimizedArtifactBuilt", "false");
        fields.put(prefix + ".transformedGraphBuilt", "false");
        fields.put(prefix + ".transformedIrBuilt", "false");
        fields.put(prefix + ".graphPatchApplied", "false");
        fields.put(prefix + ".graphRewriteImplemented", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        appendFirstPreflightFields(fields, prefix, envelopes, envelope -> envelope.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeArtifactProofBindingPreflight> artifactProofBindings(
            List<GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight> envelopes,
            GpuRuntimeIrPeepholeRewriteProofReadiness proofReadiness,
            GpuRuntimeIrPeepholeRewriteReviewPackage reviewPackage
    ) {
        if (envelopes == null || envelopes.isEmpty()) {
            return List.of();
        }
        return envelopes.stream()
                .map(envelope -> GpuRuntimeIrPeepholeArtifactProofBindingPreflight.from(
                        envelope,
                        proofReadiness,
                        reviewPackage
                ))
                .toList();
    }

    private static void appendArtifactProofBindingFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeArtifactProofBindingPreflight> bindings
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                bindings,
                GpuRuntimeIrPeepholeArtifactProofBindingPreflight::bindingReady,
                GpuRuntimeIrPeepholeArtifactProofBindingPreflight::firstBlocker
        );
        fields.put(prefix + ".bindingPreflightImplemented", "true");
        appendProofBindingGuardFields(fields, prefix);
        appendFirstPreflightFields(fields, prefix, bindings, binding -> binding.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight> artifactSelections(
            List<GpuRuntimeIrPeepholeArtifactProofBindingPreflight> bindings
    ) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        return bindings.stream()
                .map(GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight::from)
                .toList();
    }

    private static void appendArtifactSelectionFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight> selections
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                selections,
                GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight::selectionReady,
                GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight::firstBlocker
        );
        fields.put(prefix + ".selectionPreflightImplemented", "true");
        appendArtifactSelectionGuardFields(fields, prefix, !selections.isEmpty());
        appendFirstPreflightFields(fields, prefix, selections, selection -> selection.fields(prefix + ".0"));
    }

    private static void appendRewriteSketchFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeRewriteSketch> sketches
    ) {
        appendPreflightSummaryFields(
                fields,
                prefix,
                sketches,
                GpuRuntimeIrPeepholeRewriteSketch::sketchReady,
                GpuRuntimeIrPeepholeRewriteSketch::firstBlocker
        );
        fields.put(prefix + ".rewriteBuilderImplemented", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        fields.put(prefix + ".runtimeEquivalenceRequired", Boolean.toString(!sketches.isEmpty()));
        fields.put(prefix + ".approvalRequired", Boolean.toString(!sketches.isEmpty()));
        appendFirstPreflightFields(fields, prefix, sketches, sketch -> sketch.fields(prefix + ".0"));
    }

    private static List<GpuRuntimeIrPeepholeRewriteSketchConflict> rewriteSketchConflicts(
            List<GpuRuntimeIrPeepholeRewriteSketch> sketches
    ) {
        java.util.ArrayList<GpuRuntimeIrPeepholeRewriteSketchConflict> conflicts = new java.util.ArrayList<>();
        for (int leftIndex = 0; leftIndex < sketches.size(); leftIndex++) {
            GpuRuntimeIrPeepholeRewriteSketch left = sketches.get(leftIndex);
            if (!left.sketchReady()) {
                continue;
            }
            for (int rightIndex = leftIndex + 1; rightIndex < sketches.size(); rightIndex++) {
                GpuRuntimeIrPeepholeRewriteSketch right = sketches.get(rightIndex);
                if (!right.sketchReady() || !left.methodName().equals(right.methodName())) {
                    continue;
                }
                List<Integer> overlapping = overlappingNodeIds(left.coveredNodeIds(), right.coveredNodeIds());
                if (!overlapping.isEmpty()) {
                    conflicts.add(GpuRuntimeIrPeepholeRewriteSketchConflict.between(left, right, overlapping));
                }
            }
        }
        return List.copyOf(conflicts);
    }

    private static List<Integer> overlappingNodeIds(List<Integer> first, List<Integer> second) {
        java.util.LinkedHashSet<Integer> secondIds = new java.util.LinkedHashSet<>(second == null ? List.of() : second);
        java.util.ArrayList<Integer> overlap = new java.util.ArrayList<>();
        for (Integer id : first == null ? List.<Integer>of() : first) {
            if (id != null && secondIds.contains(id) && !overlap.contains(id)) {
                overlap.add(id);
            }
        }
        return List.copyOf(overlap);
    }

    private static void appendRewriteSketchConflictFields(
            LinkedHashMap<String, String> fields,
            String prefix,
            List<GpuRuntimeIrPeepholeRewriteSketchConflict> conflicts
    ) {
        fields.put(prefix + ".count", Integer.toString(conflicts.size()));
        fields.put(prefix + ".firstBlocker", conflicts.stream()
                .map(GpuRuntimeIrPeepholeRewriteSketchConflict::firstBlocker)
                .findFirst()
                .orElse("none"));
        fields.put(prefix + ".conflictResolutionImplemented", "false");
        fields.put(prefix + ".selectionApplied", "false");
        fields.put(prefix + ".mutationAllowed", "false");
        fields.put(prefix + ".selectedIrReplacement", "false");
        if (!conflicts.isEmpty()) {
            fields.putAll(conflicts.get(0).fields(prefix + ".0"));
        }
    }

    private static String executionProofStatus(
            GpuRuntimeIrPeepholeRule rule,
            GpuRuntimeIrPeepholeRuleRegistry.Analysis analysis
    ) {
        return analysis.executionReports().stream()
                .filter(report -> rule.extensionId().equals(report.extensionId()))
                .map(report -> switch (report.outcome()) {
                    case FAILED_CONTINUED -> "rule-execution-failed-continued";
                    case FAILED_CLOSED -> "rule-execution-failed-closed";
                    case SKIPPED -> "rule-execution-skipped";
                    case SUCCEEDED -> "no-report";
                })
                .findFirst()
                .orElse("not-run");
    }

    private static String firstBlocker(
            Optional<IrGpuArtifact> artifact,
            boolean typedIrAvailable,
            int candidateCount,
            int invalidReplacementPlanValidationCount,
            String firstReplacementPlanValidationBlocker,
            int partialReplacementPlanCount,
            String firstReplacementPlanBlocker,
            boolean failedClosed
    ) {
        if (artifact.isEmpty()) {
            return "irgpu-artifact-missing";
        }
        if (!typedIrAvailable) {
            return "typed-ir-unavailable";
        }
        if (failedClosed) {
            return "rule-execution-failed-closed";
        }
        if (invalidReplacementPlanValidationCount > 0) {
            return firstReplacementPlanValidationBlocker == null || firstReplacementPlanValidationBlocker.isBlank()
                    ? "replacement-plan-validation-failed"
                    : firstReplacementPlanValidationBlocker;
        }
        if (partialReplacementPlanCount > 0) {
            return firstReplacementPlanBlocker == null || firstReplacementPlanBlocker.isBlank()
                    ? "replacement-plan-incomplete"
                    : firstReplacementPlanBlocker;
        }
        return candidateCount == 0 ? "no-peephole-candidates" : "rewrite-engine-not-implemented";
    }

    private static String diagnostic(Map<String, String> fields) {
        return switch (fields.getOrDefault("firstBlocker", "unknown")) {
            case "irgpu-artifact-missing" -> "IrGpu artifact is unavailable; peephole analysis skipped";
            case "typed-ir-unavailable" ->
                    "typed runtime IR is required; text-based peephole rewriting is disabled";
            case "no-peephole-candidates" -> "typed runtime IR contains no supported peephole candidates";
            case "rule-execution-failed-closed" -> "peephole rule execution failed closed in a production profile";
            case "replacement-plan-root-missing", "replacement-plan-root-not-covered",
                 "replacement-plan-covered-node-missing", "replacement-plan-input-node-missing",
                 "replacement-plan-validation-failed" ->
                    "typed peephole replacement plan failed structural validation; no mutation proposal was emitted";
            case "add-operands-incomplete", "multiply-operands-incomplete", "replacement-plan-incomplete" ->
                    "typed peephole replacement plan is incomplete; no mutation proposal was emitted";
            case "rewrite-engine-not-implemented" ->
                    "typed peephole candidates detected, but structural rewrite and proof emission are not implemented";
            default -> "peephole preflight blocked";
        };
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value == null ? "0" : value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

}
