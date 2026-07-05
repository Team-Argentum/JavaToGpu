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

The report also includes read-only simple-arithmetic literal proof fields under `cseSimpleArithmeticLiteralProof*`, including candidate, safe, and blocked counters, proof boundary values, first safe candidate metadata, first blocked candidate metadata, and operator/type matrix fields such as `cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts`, `cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts`, `cseSimpleArithmeticLiteralProofFirstSafeOperatorTypeKey`, and `cseSimpleArithmeticLiteralProofFirstBlockedOperatorTypeKey`. Compact and detailed javac diagnostics include the same safe/blocked operator-type count maps for quick triage. These fields are diagnostic evidence for future typed numeric CSE work and do not enable production rewriting by themselves.

The literal canonicalization preview exports `cseSimpleArithmeticLiteralCanonicalization*` fields for safe literal-proof candidates. It records candidate counts, readiness, operator/type counters, prospective canonical-key counters, the aggregate `cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys`, and first-candidate metadata such as `cseSimpleArithmeticLiteralCanonicalizationFirstCanonicalKey`. Preview keys are built from lowered IR literal sources, so integration artifacts reflect the frontend-lowered representation such as `1000` or `16` rather than every raw Java spelling. The report-level key builder still preserves raw literal fragments when constructed directly, and escapes ambiguous delimiters inside canonical keys so future artifact summaries remain parseable. `cseSimpleArithmeticLiteralNumericSemanticsProof*` now proves the narrow Java `int` `+` / `*` literal-preview shape with fields such as readiness, proven/blocked candidate counts, `FullyProven`, and operator/type counters, without proving runtime equivalence or enabling fingerprint promotion. `cseSimpleArithmeticLiteralCanonicalizationGate*` adds an explicit read-only promotion gate with fields such as `cseSimpleArithmeticLiteralCanonicalizationGateCanPromoteToFingerprint`, `cseSimpleArithmeticLiteralCanonicalizationGateReadiness`, and `cseSimpleArithmeticLiteralCanonicalizationGateBlockingReasons`; fully proven preview candidates currently report `blockedPreview` with only runtime-equivalence and fingerprint-integration blockers. This preview is intentionally read-only: it does not change `GpuIrCanonicalExpressionFingerprint`, scanner fingerprints, or production rewrite readiness.

## When To Enable It

Recommended uses:

- CI builds for the compiler itself.
- Projects that want stricter diagnostics while adopting JavaToGpu.
- Development of new frontends, lowering rules, intrinsics, or optimizer passes.
- Debugging generated IR before investigating backend-specific OpenCL behavior.

For simple application experiments, the main `javatogpu` artifact is enough. Add `javatogpu-ir-validation` when you prefer earlier and more explicit compiler diagnostics.

## Limitations

The validation module is not a proof of full optimization safety yet.

Current CSE and auto-vectorization planning is intentionally conservative and mostly read-only. It does not perform production common-subexpression elimination, dominance analysis, alias analysis, helper-body equivalence, vectorization, or backend-specific cost modeling yet. CSE temp-extraction planning now proves both top-level downstream replacements and a narrow same-statement expression case where supported `initializer`, `value`, or `return` paths move forward to later sibling/child expression locations using scanner-order path semantics such as `receiver` before `arg[n]`, numeric argument indexes, and ternary `condition` before `true` before `false`. Unsupported or backward same-statement paths are still skipped as `NO_DOMINATING_FIRST_OCCURRENCE` with `requiresLocalExpressionDominance`, while proven local paths report `localExpressionDownstreamReplacements`. `GpuIrCommonSubexpressionDominanceStatus` carries those typed dominance reasons, so skipped diagnostics can distinguish proven top-level anchors, proven local expression anchors, future expression-level proof work, missing top-level anchors, or backward-moving locations; `GpuIrCommonSubexpressionArtifactSnapshot` centralizes the CSE CI export contract, including first-skipped fields, grouped dominance-status counters such as `cseSkippedDominanceStatusCounts` / `cseSkippedDominanceStatus.*`, and a dedicated `GpuIrCommonSubexpressionLocalExpressionDominanceReport` export with `cseLocalExpression*` fields for proven local-expression candidates, proven local replacements, blocked local candidates, and first blocked local proof details. `GpuIrCommonSubexpressionRewritePolicy` adds a conservative read-only gate for future production CSE rewrites: it reports `none`, `ready`, or `blockedBySkippedCandidate`, allows rewrites only when plans exist and no skipped candidates remain, and exports grouped blocking skip-reason / dominance-status counters without mutating IR. `GpuIrCommonSubexpressionRuntimeEquivalenceReport` now provides the matching opt-in CSE runtime-equivalence artifact surface with stable `cseRuntimeEquivalence*` fields for success/equivalence status, input-case counts, compared outputs, diagnostics, plan/insert/replacement/skipped counters, and skipped dominance-status grouping; it stores evidence produced by tests or integration runners and does not execute rewrites inside normal validation. `GpuIrCommonSubexpressionArtifactReport` and `GpuIrCommonSubexpressionArtifactSummary` now combine the read-only CSE snapshot with explicit runtime-equivalence evidence into one opt-in `.properties`-friendly artifact/log surface using keys such as `cseArtifactSuccessful`, `cseArtifactSummary`, `cseArtifactSnapshot.*`, and `cseArtifactRuntimeEquivalence.*`. `GpuIrCommonSubexpressionArtifactRunner` can produce that aggregate from explicit integer input cases and compared output names, again without wiring mutating CSE into normal compiler validation. `GpuIrAutoVectorizationArtifactSnapshot` likewise centralizes the auto-vectorization CI export contract, preserving the existing `autoVectorization*` artifact keys while grouping preview, rewrite-plan, rewrite-policy, dry-run, resolved-operation, proof, vector-type, warning-family, and rejection-reason fields behind one read-only snapshot. Backend-sensitive vector shapes such as `x3` lanes, `double*` vector rewrites, and non-global/read-only memory address spaces are reported as guarded rewrite candidates until ABI/backend, device capability, or memory-space rewrite policy support is proven safe. Internally, rewrite guards now carry typed family metadata while preserving stable string summaries and `.properties` artifact keys, typed insertion/replacement operation previews are available for future applicators, and the read-only rewrite policy exposes `canRewrite` / blocking-guard decisions without mutating IR. `GpuIrAutoVectorizationRewriteApplicator.apply(...)` is currently a no-op safety gate that refuses guarded, warned, or rejected previews before any rewrite attempt, then dry-run validates matching typed operations against the target method and returns unchanged IR. A read-only `GpuIrAutoVectorizationRewriteOperationResolver` also resolves accepted typed operations to concrete loop statement indexes, loop body statement counts, and loop body assignment counts. Memory/alias legality is now exposed through reusable read-only `GpuIrAutoVectorizationMemoryLegalityAnalyzer` and `GpuIrAutoVectorizationMemoryLegalityReport` types, so future rewrite passes can consume target/source alias warnings and memory-address-space guards without re-parsing scanner text. Control-flow and early-exit sibling boundaries are likewise classified through reusable read-only `GpuIrAutoVectorizationControlFlowBoundaryAnalyzer`, `GpuIrAutoVectorizationControlFlowBoundaryReport`, and `GpuIrAutoVectorizationControlFlowBoundaryKind` types while preserving the existing scanner guard summaries. Both proof surfaces expose a shared `GpuIrAutoVectorizationProofSummary` view and stable `artifactFields(...)` exports with default prefixes `autoVectorizationProofMemoryLegality` and `autoVectorizationProofControlFlowBoundary`, so future rewrite gates can consume one compact pass/block/warning/guard summary instead of special-casing each analyzer report. `GpuIrAutoVectorizationProofBundle` now aggregates rewrite-plan, memory-legality, and control-flow proof summaries into one read-only gate input with stable `autoVectorizationProofBundle*` artifact fields. It preserves raw `proofKinds()` ordering for debugging, exposes compact de-duplicated proof kinds for report summaries, tracks `proofKindCounts()` / `autoVectorizationProofBundleKind.*` repetition counters, and provides `unsafeProofSummaries()`, `unsafeProofKindCounts()` / `autoVectorizationProofBundleUnsafeProofKind.*`, `firstUnsafeProofSummary()`, and `decision()` so compact preview/report summaries can point directly at the first blocking proof layer while CI artifacts can group all unsafe layers. `GpuIrAutoVectorizationProofDecision` turns that aggregate proof state into stable gate statuses such as `allow`, `blockedByRewritePlan`, `blockedByMemory`, `blockedByControlFlow`, and `blockedByMultipleProofs` without mutating IR. Separately, the explicit opt-in `rewritePrototype(...)` path can rewrite the narrow fixed-width lane-copy, unary lane, simple lane-wise binary, and lane/literal binary cases into a Java vector alias temporary plus scalar lane writes, returning a `GpuIrAutoVectorizationPrototypeRewriteReport` with typed `GpuIrAutoVectorizationPrototypeAppliedRewrite` metadata for tests and future integration experiments. Applied rewrite metadata now includes the prototype expression family (`LANE_COPY` / `UNARY_LANE_OP` / `BINARY_LANE_OP` / `LANE_LITERAL_BINARY_OP`), stable artifact values such as `laneCopy`, `unaryLaneOp`, `binaryLaneOp`, and `laneLiteralBinaryOp`, plus the concrete unary or binary operator where applicable. The prototype report also exposes family counters through `appliedRewriteCount(...)`, `appliedRewriteCountsByKind()`, `appliedRewriteCountsByArtifactValue()`, and `appliedRewriteFamilyCountersSummary()` so integration experiments can track which rewrite shapes were actually applied. Explicit prototype runners can export `.properties`-friendly fields with `artifactFields(...)`, including stable keys such as `autoVectorizationPrototypeRewriteAppliedRewrites`, `autoVectorizationPrototypeRewriteAppliedRewriteFamily.unaryLaneOp`, and `autoVectorizationPrototypeRewriteFirstAppliedRewriteExpressionKind`. A separate `GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport` can wrap explicit test/integration equivalence results with fields such as `autoVectorizationPrototypeRuntimeEquivalenceSuccessful`, `autoVectorizationPrototypeRuntimeEquivalenceInputCases`, `autoVectorizationPrototypeRuntimeEquivalenceComparedOutputNames`, and `autoVectorizationPrototypeRuntimeEquivalenceAppliedRewriteFamilies`. `GpuIrAutoVectorizationPrototypeArtifactReport` combines rewrite and runtime-equivalence evidence into one export map with keys such as `autoVectorizationPrototypeArtifactSuccessful`, `autoVectorizationPrototypeArtifactSummary`, `autoVectorizationPrototypeArtifactRewrite.AppliedRewrites`, and `autoVectorizationPrototypeArtifactRuntimeEquivalence.Successful`. `GpuIrAutoVectorizationPrototypeArtifactSummary` provides a typed one-line log view with method, success status, rewrite count, input-case/output counts, diagnostic count, first diagnostic, and applied rewrite family counters. `GpuIrAutoVectorizationPrototypeArtifactRunner` is the current opt-in helper for tests/integration experiments: callers pass explicit integer-array input cases and compared output names, and it returns the combined artifact after running the prototype rewrite plus lightweight pre/post IR equivalence. Equivalence mismatches and narrow-runner execution failures are reported as failed artifact diagnostics such as `case ... output ... differs` or `case ... execution failed`, rather than being used as normal compiler/validation failures. These helpers do not wire the mutating prototype path into normal validation.

Those checks are being built incrementally so future optimizer transforms can be enabled with testable safety boundaries.

## Read Next

- [Getting Started](Getting-Started.md)
- [Validation and Operations](Validation-and-Operations.md)
- [Diagnostics Reference](Diagnostics-Reference.md)
- [Known Limitations](Known-Limitations.md)
