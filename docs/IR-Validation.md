# IR Validation

The `javatogpu-ir-validation` artifact is an optional strict-build module for compiler development, CI, and safety-focused builds.

It plugs into JavaToGpu through Java `ServiceLoader` and runs extra lowered-IR checks before OpenCL emission. The main `javatogpu` artifact stays lightweight; users opt in by adding the validation artifact to the annotation-processor path.

The artifact is intentionally inert unless the compiler option `javatogpu.irValidation` is enabled, so projects can keep it on a CI annotation-processor path without changing normal local builds.

## Add The Module

Use the same version as the main JavaToGpu artifact:

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.1'

    // Optional: stricter lowered-IR validation and read-only optimizer planning checks.
    annotationProcessor 'io.github.deussixik:javatogpu-ir-validation:0.1.0-alpha.1'
}

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=diagnostic'
    options.compilerArgs += '-Ajavatogpu.irValidationDiagnostics=summary'
    options.compilerArgs += '-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties'
}
```

Use `diagnostic` first when adopting the module. It runs the unified validation pipeline without failing builds for optimizer diagnostics.

## What It Checks

The strict validator currently checks the lowered IR for issues that should be caught before backend emission:

- Unknown variable references and duplicate local declarations.
- Invalid assignment targets.
- Invalid `break`, loop-break, and `continue` placement.
- Helper-call reachability, dependency metadata, and void-helper result metadata.
- Intrinsic backend/template/result metadata and placeholder consistency.
- Struct-initializer metadata, return-shape consistency, and blank type/operator metadata.
- Non-positive literal private-array sizes.
- Pure expression statements that have no observable side effect.

## Unified Pipeline

The module registers a `GpuIrValidationProvider` through Java `ServiceLoader`. When `javatogpu.irValidation` is enabled, the compiler invokes a unified read-only pipeline for every lowered helper and entry-point method.

That pipeline combines safety validation, CSE planning preview, and auto-vectorization preview. It is intentionally read-only: it does not rewrite IR or change generated OpenCL. The goal is to harden optimizer analysis before real transformations are enabled.

Current planning stages include:

- Pure-expression classification.
- Stable expression fingerprints.
- Conservative canonical matching for safe commutative operators, associative bitwise chains, and nested simple reference-only arithmetic such as `x + (y + z)` vs `(z + x) + y`.
- Scanner options for diagnostic vs optimizer-focused candidate reports.
- Candidate classification into local reuse, helper reuse, intrinsic reuse, and unsafe shapes.
- Straight-line scope filtering to avoid branch/loop/switch boundaries.
- Mutation proximity checks for variables and arrays between occurrences.
- Read-only rewrite-plan reports with skipped-candidate reasons.

## Planning Modes

Compiler builds can select one of these annotation-processor option values:

```text
off
diagnostic
strictSafety
strictOptimizer
```

`off` is the default and skips optional validation providers.

`diagnostic` runs safety validation plus read-only CSE and auto-vectorization previews. It is the safest first opt-in mode for public alpha users and reports a compact javac `NOTE` summary for each validated lowered method.

`strictSafety` fails builds when lowered-IR safety validation fails.

`strictOptimizer` fails builds when safety validation fails or optimizer diagnostics report skipped CSE candidates, auto-vectorization warnings, or rejected vectorization shapes.

Example strict CI configuration:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=strictSafety'
}
```

Optimizer diagnostics can include reasons such as:

- `NOT_LOCAL_REUSE`
- `CONTROL_FLOW_BOUNDARY`
- `MUTATED_BETWEEN_OCCURRENCES`
- `UNSUPPORTED_LANE_COUNT`
- `SIDE_EFFECTING_VALUE`

Use `strictOptimizer` mainly for compiler development and internal hardening. It is expected to be conservative while optimizer analysis is still maturing.

## Diagnostic Output

`javatogpu.irValidationDiagnostics` controls javac `NOTE` output in `diagnostic` mode:

```text
quiet
summary
detailed
```

`summary` is the default and emits compact aggregate counters, including optimizer-gate, proof-bundle rewrite-safety, diagnostic, unsafe-proof, proof-decision, and unsafe proof-layer counts such as `optimizerGateBlocked`, `optimizerGateSource`, `optimizerGateFamily`, `optimizerGateSourceCounts`, `optimizerGateFamilyCounts`, `autoVectorizationProofBundleRewriteSafe`, `autoVectorizationProofBundleDiagnostics`, `autoVectorizationProofBundleUnsafeProofs`, `autoVectorizationProofDecision`, `autoVectorizationProofDecisionAllowRewrite`, `autoVectorizationProofDecisionBlockingKinds`, `autoVectorizationProofBundleUnsafeProofKindCounts`, and `autoVectorizationProofBundleFirstUnsafeProof`. `quiet` suppresses diagnostic notes while still running the read-only pipeline. `detailed` emits nested optimizer context, including `optimizerGate={...}`, `optimizerGateSourceCounts={...}`, `optimizerGateFamilyCounts={...}`, and `autoVectorizationProofBundle={...}`, and is intended for CI/debug runs where verbose javac output is acceptable.

## Report Artifact

`javatogpu.irValidationReport` writes a machine-readable `.properties` artifact under the generated-source output directory. The value must be a relative resource path, for example:

```text
reports/javatogpu-ir-validation.properties
```

The report currently uses format `javatogpu.ir.validation.v1` and records one entry per validated lowered method, including provider name, method name, entry-point flag, safety status, unified optimizer-gate fields such as `optimizerGateBlocked`, `optimizerGateSource`, `optimizerGateFamily`, `optimizerGateSummary`, `optimizerGateCompactSummary`, `optimizerGateSourceCounts`, `optimizerGateSourceCount.*`, `optimizerGateFamilyCounts`, and `optimizerGateFamilyCount.*`, optimizer-gate policy fields such as `optimizerGatePolicyMode`, `optimizerGatePolicyBlocked`, `optimizerGatePolicySource`, `optimizerGatePolicyFamily`, and `optimizerGatePolicySummary`, CSE counters, first skipped CSE fields such as `cseFirstSkippedReason`, `cseFirstSkippedDominanceStatus`, and `cseFirstSkippedDominanceSummary`, dominance grouping fields such as `cseSkippedDominanceStatusCounts` and `cseSkippedDominanceStatus.*`, local-expression dominance proof fields such as `cseLocalExpressionProvenCandidates`, `cseLocalExpressionProvenReplacements`, `cseLocalExpressionBlockedCandidates`, `cseLocalExpressionHasEvidence`, and first-blocked fields such as `cseLocalExpressionFirstBlocked*`, simple-arithmetic proof fields such as `cseSimpleArithmeticProofProvenCandidates`, `cseSimpleArithmeticProofHasProofs`, `cseSimpleArithmeticProofProofBoundary`, `cseSimpleArithmeticProofBlockedBoundary`, and first-proven fields such as `cseSimpleArithmeticProofFirstProven*`, simple-arithmetic numeric-boundary fields such as `cseSimpleArithmeticNumericBoundaryBlockedCandidates`, `cseSimpleArithmeticNumericBoundaryLiteralOperands`, `cseSimpleArithmeticNumericBoundaryCastOperands`, `cseSimpleArithmeticNumericBoundaryBlockedReasonCounts`, and first-blocked operand metadata such as `cseSimpleArithmeticNumericBoundaryFirstBlocked*`, read-only CSE rewrite-policy fields such as `cseRewritePolicyCanRewrite`, `cseRewritePolicyReadiness`, `cseRewritePolicyBlockingSkippedCandidates`, `cseRewritePolicyBlockingSkipReasonCounts`, `cseRewritePolicyBlockingDominanceStatusCounts`, and first-blocking-skipped fields such as `cseRewritePolicyFirstBlockingSkipped*`, auto-vectorization counters, read-only rewrite-plan operation counters such as `autoVectorizationRewritePlanOperations`, preview-level rewrite gates such as `autoVectorizationCanApplyRewrite` and `autoVectorizationHasPolicyBlockedRewrite`, read-only rewrite-policy fields such as `autoVectorizationRewritePolicyCanRewrite`, `autoVectorizationRewritePolicyPlannedOperations`, `autoVectorizationRewritePolicyBlockingGuards`, and `autoVectorizationRewritePolicyFirstBlockingGuardFamily`, rewrite dry-run fields such as `autoVectorizationRewriteDryRunReadiness`, `autoVectorizationRewriteDryRunSuccessful`, `autoVectorizationRewriteDryRunDiagnostics`, `autoVectorizationRewriteDryRunCandidates`, `autoVectorizationRewriteDryRunOperations`, and `autoVectorizationRewriteDryRunFirstDiagnostic`, resolved rewrite fields such as `autoVectorizationResolvedRewriteInsertions`, `autoVectorizationResolvedRewriteReplacements`, `autoVectorizationResolvedRewriteOperations`, `autoVectorizationResolvedRewriteFirstInsertion`, and `autoVectorizationResolvedRewriteFirstReplacement`, readiness state as `autoVectorizationRewriteReadiness`, rewrite-blocked counters such as `autoVectorizationRewriteBlockedCandidates`, rewrite-plan guard-family counters such as `autoVectorizationRewritePlanGuardFamily.neighborTargetWrite`, `autoVectorizationRewritePlanGuardFamily.backendVectorWidth`, `autoVectorizationRewritePlanGuardFamily.backendDoubleVector`, or `autoVectorizationRewritePlanGuardFamily.memoryAddressSpace`, proof-summary fields such as `autoVectorizationProofRewritePlanRewriteSafe`, `autoVectorizationProofRewritePlanDiagnostics`, `autoVectorizationProofRewritePlanSummary`, and `autoVectorizationProofRewritePlanGuardFamily.controlFlowBoundary`, proof-decision fields such as `autoVectorizationProofDecisionStatus`, `autoVectorizationProofDecisionAllowRewrite`, `autoVectorizationProofDecisionBlockingProofKinds`, and `autoVectorizationProofDecisionFirstBlockingProof*`, proof-bundle fields such as `autoVectorizationProofBundleProofs`, compact `autoVectorizationProofBundleKinds`, raw repetition counters such as `autoVectorizationProofBundleKindCounts` and `autoVectorizationProofBundleKind.controlFlowBoundary`, rewrite-safety and diagnostic fields such as `autoVectorizationProofBundleRewriteSafe`, `autoVectorizationProofBundleDiagnostics`, `autoVectorizationProofBundleUnsafeProofs`, unsafe proof-layer grouping fields such as `autoVectorizationProofBundleUnsafeProofKindCounts` and `autoVectorizationProofBundleUnsafeProofKind.rewritePlan`, nested proof-bundle decision fields such as `autoVectorizationProofBundleDecisionStatus`, `autoVectorizationProofBundleDecisionAllowRewrite`, and `autoVectorizationProofBundleDecisionBlockingProofKinds`, and first unsafe proof fields such as `autoVectorizationProofBundleFirstUnsafeProof*`, plus guard-family fields such as `autoVectorizationProofBundleGuardFamily.memoryAddressSpace`, the first blocking auto-vectorization diagnostic as `autoVectorizationFirstBlockingDiagnostic`, its grouping key as `autoVectorizationFirstBlockingDiagnosticFamily`, aggregate vector-shape fields such as `autoVectorizationVectorTypeCounts` and `autoVectorizationUniqueVectorTypes`, per-type vector-shape counters such as `autoVectorizationVectorType.int4`, aggregate warning-family fields such as `autoVectorizationWarningFamilyCounts` and `autoVectorizationUniqueWarningFamilies`, warning-family counters such as `autoVectorizationWarningFamily.crossLaneRead`, aggregate rejection-reason fields such as `autoVectorizationRejectionReasonCounts` and `autoVectorizationUniqueRejectionReasons`, rejection-reason counters such as `autoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT`, and aggregate optimizer diagnostic counts. This is intended for CI trend tracking and build artifacts; javac diagnostics remain controlled separately by `javatogpu.irValidationDiagnostics`.

`autoVectorizationReadiness*` summarizes the auto-vectorization gate into one CI-friendly rollup with verdict, ready flag, blocker counts, first blocker, remaining work, and `CiSummaryLine`. It combines candidate availability, warnings, rejections, rewrite-plan guards, proof-bundle safety, rewrite-policy status, dry-run status, and resolved-operation availability without applying production rewrites.

Auto-vectorization bridge tests connect that readiness surface to behavior evidence: ready previews are checked against successful `GpuIrAutoVectorizationPrototypeArtifactRunner` runtime-equivalence artifacts, and warning/guard-blocked previews are verified to fail closed before a prototype equivalence artifact can be accepted.

The prototype artifact runner also has multi-output compared-output coverage: ready previews can compare multiple outputs in one artifact, missing compared outputs are reported by name in failed artifact diagnostics, and multi-case failure artifacts preserve aggregated diagnostic counts, joined diagnostic text, indexed diagnostic fields, plus a stable first diagnostic for CI summaries.

The report also includes read-only simple-arithmetic literal proof fields under `cseSimpleArithmeticLiteralProof*`, including candidate, safe, and blocked counters, proof boundary values, first safe candidate metadata, first blocked candidate metadata, and operator/type matrix fields such as `cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts`, `cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts`, `cseSimpleArithmeticLiteralProofFirstSafeOperatorTypeKey`, and `cseSimpleArithmeticLiteralProofFirstBlockedOperatorTypeKey`. Compact and detailed javac diagnostics include the same safe/blocked operator-type count maps for quick triage. Blocked literal proof metadata now uses explicit reason keys such as `longLiteralOverflowSemanticsRequireProof`, `floatingLiteralSemanticsRequireProof`, `castRequiresExplicitNumericProof`, and `nonIntOperandSemanticsRequireProof`, with first-blocked explanation fields for CI triage. `cseSimpleArithmeticLiteralTypedNumericBlockers*` aggregates literal-proof and numeric-semantics blocker families into one CI-friendly surface with readiness, total blocked candidates, combined family counts, first blocker, first explanation, and a one-line summary. These fields are diagnostic evidence for future typed numeric CSE work and do not enable production rewriting by themselves.

The literal canonicalization preview exports `cseSimpleArithmeticLiteralCanonicalization*` fields for safe literal-proof candidates. It records candidate counts, readiness, operator/type counters, prospective canonical-key counters, the aggregate `cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys`, and first-candidate metadata such as `cseSimpleArithmeticLiteralCanonicalizationFirstCanonicalKey`. Preview keys are built from lowered IR literal sources, so integration artifacts reflect the frontend-lowered representation such as `1000` or `16` rather than every raw Java spelling. The report-level key builder still preserves raw literal fragments when constructed directly, and escapes ambiguous delimiters inside canonical keys so future artifact summaries remain parseable. `cseSimpleArithmeticLiteralNumericSemanticsProof*` now proves the narrow Java `int` `+` / `*` literal-preview shape with fields such as readiness, proven/blocked candidate counts, `FullyProven`, and operator/type counters, without enabling fingerprint promotion. `cseSimpleArithmeticLiteralTypedNumericBlockers*` summarizes the remaining typed numeric proof blockers across literal proof and numeric semantics proof layers, making long overflow, floating precision/backend, cast semantics, unsupported operator, missing literal-source, and non-int operand gaps visible as grouped CI families. `cseSimpleArithmeticLiteralRuntimeEquivalence*` adds a separate explicit evidence container for tests and opt-in runners, exporting success/equivalence status, input-case/output counts, diagnostics, preview-candidate counts, unique canonical-key counts, numeric-semantics readiness, and canonical-key counts. Normal validation reports this evidence as `notProven` / not run, while `GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceRunner` can produce successful evidence for explicit safe `int` literal `+` / `*` cases without rewriting IR. `GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport` and `GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary` combine preview, numeric proof, runtime evidence, gate state, fingerprint-decision state, fingerprint-parity state, and nested explicit-artifact consistency fields into one opt-in `cseSimpleArithmeticLiteralArtifact*` export map and compact summary for CI logs. `GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport` checks that explicit `runArtifact(...)` output remains internally coherent across runtime evidence, gate state, decision/parity state, and summary fields. `cseSimpleArithmeticLiteralCanonicalizationGate*` adds an explicit read-only promotion gate with fields such as `cseSimpleArithmeticLiteralCanonicalizationGateCanPromoteToFingerprint`, `cseSimpleArithmeticLiteralCanonicalizationGateReadiness`, and `cseSimpleArithmeticLiteralCanonicalizationGateBlockingReasons`; fully proven preview candidates currently report `blockedPreview` with runtime-equivalence and fingerprint-integration blockers, and successful explicit runtime evidence leaves only the fingerprint-integration blocker. `cseSimpleArithmeticLiteralFingerprintDecision*` adds the next read-only decision layer for future fingerprint integration, exporting evidence completeness, readiness, production-disablement-only status, preview/key counts, and blockers such as `productionFingerprintIntegrationDisabled`. `cseSimpleArithmeticLiteralFingerprintParity*` compares those preview keys against current production CSE insertion/skipped fingerprints, exporting production-overlap counts, preview-only key counts, and blockers such as `previewOnlyKeysNotInProductionFingerprints`; it is a blast-radius artifact only. `cseSimpleArithmeticLiteralEnablement*` folds numeric proof, runtime evidence, decision, and parity into a single method-level verdict such as `notReady/runtimeMissing`, `notReady/previewOnlyBlastRadius`, or `evidenceCompleteButDisabled`. `cseSimpleArithmeticLiteralRewritePreflight*` adds a final read-only typed rewrite preflight layer over the same evidence, reporting candidate, eligible, blocked, canonical-key, blocker-count, and first-blocked-candidate fields so CI can see which literal arithmetic rewrites would remain blocked before any production mutation is enabled. `cseSimpleArithmeticLiteralRewriteOperationPreview*` then exposes the future operation-shape surface, including eligible/blocked operation counts, shape counters such as `literalArithmeticCse(plus:int,int,int)`, blocker counters, and first blocked operation metadata. `cseSimpleArithmeticLiteralPromotionChecklist*` combines enablement, preflight, and operation-shape preview into one final read-only verdict with remaining-work counters such as `runRuntimeEquivalenceEvidence`, `clearRewritePreflightBlockers`, and `enableProductionFingerprintIntegration`. `cseSimpleArithmeticLiteralConsistencyCheck*` verifies that normal validation layers agree on candidate counts, canonical-key counts, evidence completion, readiness, and blocked operation counts before CI treats the stack as coherent; it now also exports CI summary and first-failure explanation fields for faster triage. This preview is intentionally read-only: it does not change `GpuIrCanonicalExpressionFingerprint`, scanner fingerprints, or production rewrite readiness.

Literal runtime-equivalence artifacts now mirror the broader CSE and auto-vectorization diagnostic export shape by emitting first, joined, and indexed diagnostic fields for failed or not-run evidence.

Runtime-equivalence artifacts now also expose grouped diagnostic-family counters such as `executionFailed`, `missingOutput`, `outputDiffers`, and `other`, so CI can trend failure types without parsing diagnostic strings. The validation-provider report entry also mirrors the literal runtime-equivalence evidence into top-level `runtimeEquivalence*` fields, including `runtimeEquivalenceDiagnostics`, `hasRuntimeEquivalenceDiagnostics`, `runtimeEquivalenceDiagnosticFamilyCounts`, and `runtimeEquivalenceDiagnosticFamily.*`, so CI dashboards can read one stable rollup before drilling into `cseSimpleArithmeticLiteralRuntimeEquivalence*` details. The same family-count rollup is also included in compact and detailed validation summaries as `runtimeEquivalenceDiagnosticFamilyCounts=...`, making javac diagnostics and CI log text consistent with the `.properties` artifact.

The I2 registry prototype now includes a minimal read-only rule-registry skeleton: `GpuIrOptimizationValidationRule`, `GpuIrOptimizationValidationRuleContext`, `GpuIrOptimizationValidationRuleResult`, and `GpuIrOptimizationValidationRuleRegistry`. It is intentionally opt-in and not wired into normal compiler validation yet; the goal is to give future optimizer checks a common ordered rule/result/export contract before production mutation is enabled. `GpuIrOptimizationValidationRules` adds the first built-in opt-in adapters, `safety.clean`, `optimizer.noBlockingDiagnostics`, and `optimizer.advisoryDiagnostics`, plus a default registry helper for tests/tooling that want stable safety and optimizer-gate rule artifacts without changing normal validation behavior. `GpuIrOptimizationValidationRuleResult` now carries stable `pass` / `warn` / `fail` status metadata, a blocking flag, immutable metadata snapshots, and `MetadataCount` artifact fields, allowing advisory warnings to remain passing and non-blocking while CI can sanity-check metadata shape without parsing nested keys. The advisory rule returns `warn` for non-blocking optimizer opportunities such as CSE insertion/replacement work while blocking diagnostics remain absent. `GpuIrOptimizationValidationRuleArtifactReport` packages explicit evaluations into one `validationRules*` export surface with pass/fail/warning/blocking totals, boolean rollups such as `validationRulesHasFailures` and `validationRulesHasWarnings`, stable `validationRulesVerdict` values (`pass`, `warn`, or `fail`), status counters, failed/warning/blocking rule ids, top-level ordered indexes such as `validationRulesRuleIndex`, `validationRulesWarningRuleIndex`, and `validationRulesBlockingRuleIndex`, first-failed, first-warning, and first-blocking result fields, typed `GpuIrOptimizationValidationRuleArtifactSummary`, `validationRulesCiSummaryLine`, summary text, nested registry result fields, compact ordered registry indexes such as `validationRulesRegistryRuleIndex`, `validationRulesRegistryWarningRuleIndex`, and `validationRulesRegistryBlockingRuleIndex`, grouped rule-family rollups such as `validationRulesRuleFamilyCounts`, `validationRulesWarningRuleFamilyCounts`, `validationRulesBlockingRuleFamilyCounts`, and `validationRulesFailedRuleFamilyCounts`, plus nested `validationRulesConsistency.*` self-check fields for CI artifact drift detection, while `GpuIrOptimizationValidationRuleArtifactRunner` gives tooling a one-call opt-in helper for packaging an already-created validation report. `GpuIrOptimizationValidationRuleArtifactFields` is the matching opt-in merge helper for copying those `validationRules*` fields into build/report maps without wiring rule-registry execution into `GpuIrOptimizationValidationProvider`.

A compact opt-in rule artifact can be consumed by CI/tooling as a regular `.properties` fragment. `validationRulesVerdict=pass` means all rules passed with no warnings, `validationRulesVerdict=warn` means all blocking rules passed but at least one advisory rule reported follow-up work, and `validationRulesVerdict=fail` means at least one blocking rule failed. The first fields to read are usually `validationRulesVerdict`, `validationRulesPassed`, `validationRulesHasWarnings`, `validationRulesHasBlockingResults`, `validationRulesCiSummaryLine`, and `validationRulesConsistency.Consistent`:

```properties
validationRulesMethod=exampleKernel
validationRulesVerdict=warn
validationRulesPassed=true
validationRulesWarnings=1
validationRulesHasWarnings=true
validationRulesBlocking=0
validationRulesHasBlockingResults=false
validationRulesRuleIndex=[safety.clean=pass,optimizer.noBlockingDiagnostics=pass,optimizer.advisoryDiagnostics=warn]
validationRulesWarningRuleIds=optimizer.advisoryDiagnostics
validationRulesWarningRuleIndex=[optimizer.advisoryDiagnostics=warn]
validationRulesFirstWarningRuleId=optimizer.advisoryDiagnostics
validationRulesFirstWarningMessage=method has non-blocking optimizer advisory signals
validationRulesStatusCounts={pass=2,warn=1}
validationRulesWarningRuleFamilyCounts={optimizer=1}
validationRulesCiSummaryLine=validation rules method=exampleKernel verdict=warn passed=true rules=3 results=3 failed=0 warnings=1 blocking=0 hasFailures=false hasWarnings=true hasBlockingResults=false warningRuleIds=optimizer.advisoryDiagnostics ruleIndex=[safety.clean=pass,optimizer.noBlockingDiagnostics=pass,optimizer.advisoryDiagnostics=warn] warningRuleIndex=[optimizer.advisoryDiagnostics=warn] blockingRuleIndex=[] firstWarningRuleId=optimizer.advisoryDiagnostics statusCounts={pass=2,warn=1} ruleFamilyCounts={safety=1,optimizer=2} warningRuleFamilyCounts={optimizer=1} blockingRuleFamilyCounts={} failedRuleFamilyCounts={}
validationRulesConsistency.Consistent=true
validationRulesConsistency.FailedChecks=0
```

The registry artifact is deliberately separate from the normal provider report. Callers that want this surface should explicitly evaluate a `GpuIrOptimizationValidationRuleRegistry` or use `GpuIrOptimizationValidationRuleArtifactRunner`, then merge fields with `GpuIrOptimizationValidationRuleArtifactFields`.

`GpuIrOptimizationValidationRuleArtifactAcceptance` is the small helper for CI consumers that only need an accept/reject decision. It accepts `pass` artifacts and also accepts `warn` artifacts as non-blocking follow-up work, but rejects artifacts with failing/blocking rule results. It also rejects inconsistent rule artifacts with `validationRulesAcceptanceReason=rejected/inconsistentArtifact` before normal pass/warn/fail acceptance, so CI does not promote a drifted artifact surface. The default helpers use the report's own `consistencyReport()`; explicit consistency-report overloads are available for CI/tooling tests that intentionally simulate external artifact drift. Its default `validationRulesAcceptance*` fields include `validationRulesAcceptanceAccepted`, `validationRulesAcceptanceRejected`, `validationRulesAcceptanceAcceptedWithWarnings`, `validationRulesAcceptanceReason`, first warning/failure/blocking rule ids, and `validationRulesAcceptanceCiSummaryLine`, so tooling does not need to parse `validationRulesSummary`. `GpuIrOptimizationValidationRuleArtifactFields` can now export acceptance-only fields with `acceptanceFields(...)` / `putAcceptanceFields(...)`, or merge the full rule artifact plus acceptance decision in one call with `fieldsWithAcceptance(...)` / `putFieldsWithAcceptance(...)`.

`GpuIrOptimizationValidationOptimizerReadinessHandoffReport` is the next opt-in CI layer above rule-artifact acceptance. It converts a rule artifact plus acceptance decision into `optimizerReadinessHandoff*` fields such as `ReadyForOptimizerEnablement`, `Verdict`, `BlockingReasons`, `RemainingWork`, and `CiSummaryLine`. It is intentionally read-only and not wired into normal compiler validation or production mutation; warnings, rejected artifacts, blocking rules, and explicit consistency drift all fail closed before future optimizer enablement review.

`GpuIrOptimizationValidationOptimizerEnablementPolicyDecision` is the read-only policy layer above the handoff report. A clean handoff can allow an optimizer enablement review through `optimizerEnablementPolicyAllowOptimizerEnablementReview=true`, but `optimizerEnablementPolicyProductionMutationEnabled` remains `false` until a future explicit production policy is added. Blocked handoffs export `optimizerEnablementPolicyVerdict=blocked/handoffNotReady`, first-blocking reason, first remaining work, and CI summary text, so tooling can separate "ready for review" from "safe to mutate production IR".

`GpuIrOptimizationValidationOptimizerEnablementArtifact` is the typed read-only bundle for that full CI/tooling chain. It keeps the validation-rule artifact, acceptance decision, optimizer-readiness handoff, and enablement policy in one immutable object, then exports the same `validationRules*`, `validationRulesAcceptance*`, `optimizerReadinessHandoff*`, and `optimizerEnablementPolicy*` fields. `GpuIrOptimizationValidationRuleArtifactFields.optimizerEnablementArtifactFields(...)` and `putOptimizerEnablementArtifactFields(...)` provide the default map-facing aliases while preserving the older `fieldsWithAcceptanceHandoffAndPolicy(...)` helper.

`GpuIrOptimizationValidationOptimizerEnablementArtifactRunner` is the matching detached smoke runner. It starts from an already-created `GpuIrOptimizationValidationReport`, evaluates an explicit validation-rule registry, and returns the typed enablement artifact or its `.properties`-friendly field map. Like the lower-level rule artifact runner, it is opt-in tooling only and does not connect the normal compiler validation path to optimizer mutation.

`GpuIrOptimizationValidationOptimizerEnablementGateReport` is the first read-only aggregate gate above those artifacts. It combines CSE literal-promotion readiness, auto-vectorization prototype readiness, and the conservative optimizer enablement policy into one `optimizerEnablementGate*` verdict with blocker counts, first blocker, remaining work, and CI summary text. A blocked CSE or auto-vectorization readiness layer fails the gate before policy review; a clean review still reports `reviewReady/productionMutationDisabled` until a future explicit production mutation switch is implemented.

`GpuIrOptimizationValidationOptimizerValidationBundle` is the typed read-only object intended for future A1/A2 production-enable checks. It keeps the original validation report, the full optimizer enablement artifact, the aggregate enablement gate, and the production preflight decision together, then exports `optimizerValidationBundle*` rollup fields plus the nested `validationRules*`, `optimizerEnablementPolicy*`, `optimizerEnablementGate*`, and `optimizerProductionPreflight*` fields. The detached runner exposes it through `runBundle(...)` and `runBundleFields(...)`.

`GpuIrOptimizationValidationProductionEnablementPreflightDecision` is the final read-only A1/A2 preflight layer above the bundle. It classifies the current bundle as `blocked/optimizerValidationBundleNotReady`, `reviewReady/productionMutationDisabled`, or `ready/productionMutationEnabled`, exports compact `optimizerProductionPreflight*` fields, and never flips production mutation by itself. Tooling can use this as the future production-enable handoff point without coupling it to normal compiler validation.

`GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry()` adds a separate opt-in rule pack for runtime-equivalence evidence checks. It is not part of `defaultRegistry()` and is not connected to normal compiler validation. The default pack currently contains `cse.literalRuntimeEquivalenceEvidence`, which warns when literal CSE preview candidates exist without successful runtime-equivalence evidence, and `cse.literalPromotionRuntimeEquivalenceGate`, which blocks promotion-oriented artifacts when preview candidates exist but runtime-equivalence evidence is missing or failed. Callers that also have explicit auto-vectorization prototype pre/post evidence can use `runtimeEquivalenceEvidenceRegistry(prePostReport)` or `autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(prePostReport)` to include `autoVectorization.prototypePrePostRuntimeEquivalenceEvidence` and `autoVectorization.prototypePrePostRuntimeEquivalenceGate`; those rules warn/block when prototype rewrite candidates exist but pre/post runtime-equivalence evidence is missing or failed. The rule metadata exports literal preview counts, auto-vectorization rewrite counts, runtime-equivalence success/diagnostic metadata, readiness verdicts, blocking reasons plus blocker counts, applied rewrite-family counters, and remaining-work hints plus remaining-work counts through normal rule-result metadata.

Rule-result metadata exports now include both `MetadataCount` and `MetadataPresent`, giving CI/tooling a stable metadata-shape check without parsing nested `Metadata.*` keys.

Use the runtime-evidence registries only from explicit artifact/tooling code. The default CSE-only registry checks the validation report's literal runtime-equivalence surface. The auto-vector-only registry needs an explicit `GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport`. The combined registry keeps CSE rules first, then appends auto-vectorization pre/post rules:

```java
GpuIrOptimizationValidationRuleArtifactReport cseOnly =
        GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport,
                GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry()
        );

GpuIrOptimizationValidationRuleArtifactReport autoVectorOnly =
        GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport,
                GpuIrOptimizationValidationRules.autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(prePostReport)
        );

GpuIrOptimizationValidationRuleArtifactReport combined =
        GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport,
                GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry(prePostReport)
        );

Map<String, String> ciFields =
        GpuIrOptimizationValidationRuleArtifactFields.optimizerEnablementArtifactFields(combined);

Map<String, String> smokeFields =
        new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runArtifactFields(validationReport);

Map<String, String> gateFields =
        new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runGateFields(validationReport);

Map<String, String> bundleFields =
        new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runBundleFields(validationReport);

Map<String, String> preflightFields =
        new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runProductionPreflightFields(validationReport);
```

For CI, read `validationRulesVerdict`, `validationRulesWarningRuleIndex`, `validationRulesBlockingRuleIndex`, `validationRulesAcceptanceAccepted`, `validationRulesAcceptanceReason`, `optimizerReadinessHandoffReadyForOptimizerEnablement`, `optimizerReadinessHandoffVerdict`, `optimizerReadinessHandoffBlockingReasons`, `optimizerEnablementPolicyAllowOptimizerEnablementReview`, `optimizerEnablementPolicyProductionMutationEnabled`, `optimizerEnablementGateVerdict`, `optimizerEnablementGateReadyForProductionMutation`, `optimizerEnablementGateFirstBlockingReason`, `optimizerValidationBundleVerdict`, `optimizerValidationBundleReadyForProductionMutation`, `optimizerValidationBundleGateFirstBlockingReason`, `optimizerProductionPreflightVerdict`, `optimizerProductionPreflightBlocked`, `optimizerProductionPreflightReviewReady`, `optimizerProductionPreflightFirstBlockingReason`, and the nested `validationRulesRegistryResult.*.Metadata.*` evidence fields first. This avoids parsing long summary strings, keeps runtime-evidence gating opt-in, and lets tooling fail closed before future optimizer enablement review.

`cseSimpleArithmeticLiteralPromotionReadiness*` sits above the checklist as a compact CI rollup, exporting one verdict, blocker counters, first blocker, remaining-work list, and `CiSummaryLine` across typed numeric blockers, runtime-equivalence evidence, preview-only fingerprint blast radius, production fingerprint disablement, and production mutation disablement.

## When To Enable It

Recommended uses:

- CI builds for the compiler itself.
- Projects that want stricter diagnostics while adopting JavaToGpu.
- Development of new frontends, lowering rules, intrinsics, or optimizer passes.
- Debugging generated IR before investigating backend-specific OpenCL behavior.

For simple application experiments, the main `javatogpu` artifact is enough. Add `javatogpu-ir-validation` when you prefer earlier and more explicit compiler diagnostics.

## Limitations

The validation module is not a proof of full optimization safety yet.

Current CSE and auto-vectorization planning is intentionally conservative and mostly read-only. It does not perform production common-subexpression elimination, dominance analysis, alias analysis, helper-body equivalence, vectorization, or backend-specific cost modeling yet. CSE temp-extraction planning now proves both top-level downstream replacements and a narrow same-statement expression case where supported `initializer`, `value`, or `return` paths move forward to later sibling/child expression locations using scanner-order path semantics such as `receiver` before `arg[n]`, numeric argument indexes, and ternary `condition` before `true` before `false`. Unsupported or backward same-statement paths are still skipped as `NO_DOMINATING_FIRST_OCCURRENCE` with `requiresLocalExpressionDominance`, while proven local paths report `localExpressionDownstreamReplacements`. `GpuIrCommonSubexpressionDominanceStatus` carries those typed dominance reasons, so skipped diagnostics can distinguish proven top-level anchors, proven local expression anchors, future expression-level proof work, missing top-level anchors, or backward-moving locations; `GpuIrCommonSubexpressionArtifactSnapshot` centralizes the CSE CI export contract, including first-skipped fields, grouped dominance-status counters such as `cseSkippedDominanceStatusCounts` / `cseSkippedDominanceStatus.*`, and a dedicated `GpuIrCommonSubexpressionLocalExpressionDominanceReport` export with `cseLocalExpression*` fields for proven local-expression candidates, proven local replacements, blocked local candidates, and first blocked local proof details. `GpuIrCommonSubexpressionRewritePolicy` adds a conservative read-only gate for future production CSE rewrites: it reports `none`, `ready`, or `blockedBySkippedCandidate`, allows rewrites only when plans exist and no skipped candidates remain, and exports grouped blocking skip-reason / dominance-status counters without mutating IR. `GpuIrCommonSubexpressionRuntimeEquivalenceReport` now provides the matching opt-in CSE runtime-equivalence artifact surface with stable `cseRuntimeEquivalence*` fields for success/equivalence status, input-case counts, compared outputs, diagnostics, plan/insert/replacement/skipped counters, and skipped dominance-status grouping; it stores evidence produced by tests or integration runners and does not execute rewrites inside normal validation. `GpuIrCommonSubexpressionArtifactReport` and `GpuIrCommonSubexpressionArtifactSummary` now combine the read-only CSE snapshot with explicit runtime-equivalence evidence into one opt-in `.properties`-friendly artifact/log surface using keys such as `cseArtifactSuccessful`, `cseArtifactSummary`, `cseArtifactSnapshot.*`, and `cseArtifactRuntimeEquivalence.*`. `GpuIrCommonSubexpressionArtifactRunner` can produce that aggregate from explicit integer input cases and compared output names, again without wiring mutating CSE into normal compiler validation. `GpuIrCommonSubexpressionPrePostRuntimeEquivalenceRunner` and `GpuIrCommonSubexpressionPrePostRuntimeEquivalenceReport` add a thin opt-in wrapper around that aggregate with `csePrePostRuntimeEquivalence*` fields, pre-optimization insertion-candidate counts, post-optimization replacement-check counts, nested artifact fields, and diagnostic-family rollups for CI dashboards that want a dedicated pre/post optimizer evidence surface. `GpuIrAutoVectorizationArtifactSnapshot` likewise centralizes the auto-vectorization CI export contract, preserving the existing `autoVectorization*` artifact keys while grouping preview, rewrite-plan, rewrite-policy, dry-run, resolved-operation, proof, vector-type, warning-family, and rejection-reason fields behind one read-only snapshot. Backend-sensitive vector shapes such as `x3` lanes, `double*` vector rewrites, and non-global/read-only memory address spaces are reported as guarded rewrite candidates until ABI/backend, device capability, or memory-space rewrite policy support is proven safe. Internally, rewrite guards now carry typed family metadata while preserving stable string summaries and `.properties` artifact keys, typed insertion/replacement operation previews are available for future applicators, and the read-only rewrite policy exposes `canRewrite` / blocking-guard decisions without mutating IR. `GpuIrAutoVectorizationRewriteApplicator.apply(...)` is currently a no-op safety gate that refuses guarded, warned, or rejected previews before any rewrite attempt, then dry-run validates matching typed operations against the target method and returns unchanged IR. A read-only `GpuIrAutoVectorizationRewriteOperationResolver` also resolves accepted typed operations to concrete loop statement indexes, loop body statement counts, and loop body assignment counts. Memory/alias legality is now exposed through reusable read-only `GpuIrAutoVectorizationMemoryLegalityAnalyzer` and `GpuIrAutoVectorizationMemoryLegalityReport` types, so future rewrite passes can consume target/source alias warnings and memory-address-space guards without re-parsing scanner text. Control-flow and early-exit sibling boundaries are likewise classified through reusable read-only `GpuIrAutoVectorizationControlFlowBoundaryAnalyzer`, `GpuIrAutoVectorizationControlFlowBoundaryReport`, and `GpuIrAutoVectorizationControlFlowBoundaryKind` types while preserving the existing scanner guard summaries. Both proof surfaces expose a shared `GpuIrAutoVectorizationProofSummary` view and stable `artifactFields(...)` exports with default prefixes `autoVectorizationProofMemoryLegality` and `autoVectorizationProofControlFlowBoundary`, so future rewrite gates can consume one compact pass/block/warning/guard summary instead of special-casing each analyzer report. `GpuIrAutoVectorizationProofBundle` now aggregates rewrite-plan, memory-legality, and control-flow proof summaries into one read-only gate input with stable `autoVectorizationProofBundle*` artifact fields. It preserves raw `proofKinds()` ordering for debugging, exposes compact de-duplicated proof kinds for report summaries, tracks `proofKindCounts()` / `autoVectorizationProofBundleKind.*` repetition counters, and provides `unsafeProofSummaries()`, `unsafeProofKindCounts()` / `autoVectorizationProofBundleUnsafeProofKind.*`, `firstUnsafeProofSummary()`, and `decision()` so compact preview/report summaries can point directly at the first blocking proof layer while CI artifacts can group all unsafe layers. `GpuIrAutoVectorizationProofDecision` turns that aggregate proof state into stable gate statuses such as `allow`, `blockedByRewritePlan`, `blockedByMemory`, `blockedByControlFlow`, and `blockedByMultipleProofs` without mutating IR. Separately, the explicit opt-in `rewritePrototype(...)` path can rewrite the narrow fixed-width lane-copy, unary lane, simple lane-wise binary, and lane/literal binary cases into a Java vector alias temporary plus scalar lane writes, returning a `GpuIrAutoVectorizationPrototypeRewriteReport` with typed `GpuIrAutoVectorizationPrototypeAppliedRewrite` metadata for tests and future integration experiments. Applied rewrite metadata now includes the prototype expression family (`LANE_COPY` / `UNARY_LANE_OP` / `BINARY_LANE_OP` / `LANE_LITERAL_BINARY_OP`), stable artifact values such as `laneCopy`, `unaryLaneOp`, `binaryLaneOp`, and `laneLiteralBinaryOp`, plus the concrete unary or binary operator where applicable. The prototype report also exposes family counters through `appliedRewriteCount(...)`, `appliedRewriteCountsByKind()`, `appliedRewriteCountsByArtifactValue()`, and `appliedRewriteFamilyCountersSummary()` so integration experiments can track which rewrite shapes were actually applied. Explicit prototype runners can export `.properties`-friendly fields with `artifactFields(...)`, including stable keys such as `autoVectorizationPrototypeRewriteAppliedRewrites`, `autoVectorizationPrototypeRewriteAppliedRewriteFamily.unaryLaneOp`, and `autoVectorizationPrototypeRewriteFirstAppliedRewriteExpressionKind`. A separate `GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport` can wrap explicit test/integration equivalence results with fields such as `autoVectorizationPrototypeRuntimeEquivalenceSuccessful`, `autoVectorizationPrototypeRuntimeEquivalenceInputCases`, `autoVectorizationPrototypeRuntimeEquivalenceComparedOutputNames`, and `autoVectorizationPrototypeRuntimeEquivalenceAppliedRewriteFamilies`. `GpuIrAutoVectorizationPrototypeArtifactReport` combines rewrite and runtime-equivalence evidence into one export map with keys such as `autoVectorizationPrototypeArtifactSuccessful`, `autoVectorizationPrototypeArtifactSummary`, `autoVectorizationPrototypeArtifactRewrite.AppliedRewrites`, and `autoVectorizationPrototypeArtifactRuntimeEquivalence.Successful`. `GpuIrAutoVectorizationPrototypeArtifactSummary` provides a typed one-line log view with method, success status, rewrite count, input-case/output counts, diagnostic count, first diagnostic, and applied rewrite family counters. `GpuIrAutoVectorizationPrototypeArtifactRunner` is the current opt-in helper for tests/integration experiments: callers pass explicit integer-array input cases and compared output names, and it returns the combined artifact after running the prototype rewrite plus lightweight pre/post IR equivalence. Equivalence mismatches and narrow-runner execution failures are reported as failed artifact diagnostics such as `case ... output ... differs` or `case ... execution failed`, rather than being used as normal compiler/validation failures. These helpers do not wire the mutating prototype path into normal validation.

`GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceRunner` and `GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport` now mirror the CSE pre/post wrapper for opt-in prototype vectorization evidence. They expose `autoVectorizationPrototypePrePostRuntimeEquivalence*` fields for success, pre-optimization rewrite candidates, post-optimization applied rewrites, applied rewrite-family counters, nested artifact fields, and runtime-equivalence diagnostic-family rollups while remaining disconnected from normal compiler validation.

Those checks are being built incrementally so future optimizer transforms can be enabled with testable safety boundaries.

## Read Next

- [Getting Started](Getting-Started.md)
- [Validation and Operations](Validation-and-Operations.md)
- [Diagnostics Reference](Diagnostics-Reference.md)
- [Known Limitations](Known-Limitations.md)
