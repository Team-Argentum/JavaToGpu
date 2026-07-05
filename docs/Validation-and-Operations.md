# Validation And Operations

This page describes the validation flow for the current alpha.

## Current Operational Position

The active production-confidence path is NVIDIA OpenCL because that is the currently available real hardware stack.

Current local evidence proves the available NVIDIA path. Intel and AMD remain future cross-vendor promotion gates.

## Main Validation Buckets

- `:processor:benchmarkTest`
- `:processor:integrationOpenClSmokeTest`
- `:processor:openClLongRunningStabilityTest`
- `:processor:atomicsCompileTest`
- `:processor:compileOnlyTest`
- `:processor:imageOpenClTest`
- `:processor:localMemoryTest`
- `:processor:performanceStressTest`
- `:processor:runtimeOpenClTest`
- `:processor:structAbiTest`
- `:processor:openClVendorValidation`
- `:processor:openClWorkloadValidationTest`
- `:processor:openClValidationReport`

## Strict IR Validation

For compiler-development or CI builds, add the optional `javatogpu-ir-validation` artifact to the annotation-processor path and enable `-Ajavatogpu.irValidation=diagnostic`, `strictSafety`, or `strictOptimizer`. It runs additional lowered-IR validation before OpenCL emission and includes read-only CSE / auto-vectorization planning reports without rewriting production IR. The auto-vectorization rewrite applicator is currently a no-op safety gate in the production-facing `apply(...)` path: it accepts only previews where `autoVectorizationCanApplyRewrite=true`, rejects guarded, warned, or rejected previews before mutation can happen, reports structured dry-run artifact fields such as `autoVectorizationRewriteDryRunReadiness`, `autoVectorizationRewriteDryRunSuccessful`, and `autoVectorizationRewriteDryRunDiagnostics`, and resolves accepted operations into read-only loop metadata fields such as `autoVectorizationResolvedRewriteOperations`, `autoVectorizationResolvedRewriteFirstInsertion`, and `autoVectorizationResolvedRewriteFirstReplacement`. `GpuIrAutoVectorizationArtifactSnapshot` now centralizes those auto-vectorization report exports while preserving the existing `autoVectorization*` keys used by CI artifacts, including aggregate vector-shape fields such as `autoVectorizationVectorTypeCounts` and `autoVectorizationUniqueVectorTypes` alongside per-type keys like `autoVectorizationVectorType.int4`, plus aggregate warning/rejection fields such as `autoVectorizationWarningFamilyCounts`, `autoVectorizationUniqueWarningFamilies`, `autoVectorizationRejectionReasonCounts`, and `autoVectorizationUniqueRejectionReasons`. `GpuIrAutoVectorizationMemoryLegalityAnalyzer` now centralizes read-only target/source alias warnings and memory-address-space guards into a typed `GpuIrAutoVectorizationMemoryLegalityReport`, while scanner/provider artifacts continue surfacing the same warning and guard families. `GpuIrAutoVectorizationControlFlowBoundaryAnalyzer` now provides the same reusable read-only surface for control-flow and early-exit sibling boundary guards. Both reports expose `GpuIrAutoVectorizationProofSummary` and uniform `artifactFields(...)` maps using default prefixes `autoVectorizationProofMemoryLegality` and `autoVectorizationProofControlFlowBoundary`, giving future rewrite gates a shared compact pass/block/warning/guard view across proof analyzers. `GpuIrAutoVectorizationProofBundle` now aggregates rewrite-plan, memory-legality, and control-flow proof summaries into one read-only gate input with compact proof-kind summaries, raw proof-kind counters, unsafe-proof helpers, and stable `autoVectorizationProofBundle*` artifact fields. An explicit test/integration `rewritePrototype(...)` path now rewrites only narrow lane-copy, unary lane, simple lane-wise binary, and lane/literal binary shapes and reports applied rewrites through `GpuIrAutoVectorizationPrototypeRewriteReport` plus typed `GpuIrAutoVectorizationPrototypeAppliedRewrite` entries, including the applied expression shape (`LANE_COPY`, `UNARY_LANE_OP`, `BINARY_LANE_OP`, or `LANE_LITERAL_BINARY_OP`) plus the unary or binary operator when relevant. The prototype report also exposes applied rewrite family counters by enum and artifact value, with summaries such as `appliedRewriteFamilies={laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}`, and explicit prototype runners can export `.properties`-friendly fields with `artifactFields(...)`. `GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport` can separately export explicit pre/post rewrite equivalence evidence fields such as `autoVectorizationPrototypeRuntimeEquivalenceSuccessful`, `autoVectorizationPrototypeRuntimeEquivalenceInputCases`, and `autoVectorizationPrototypeRuntimeEquivalenceAppliedRewriteFamilies`. `GpuIrAutoVectorizationPrototypeArtifactReport` combines both reports into one opt-in export map for integration artifacts, exposes `autoVectorizationPrototypeArtifactSummary`, and provides `GpuIrAutoVectorizationPrototypeArtifactSummary` for typed one-line logging of method, success, rewrite, input/output, diagnostic, and rewrite-family counters. `GpuIrAutoVectorizationPrototypeArtifactRunner` can build that artifact from explicit integer-array input cases plus compared output names while remaining disabled for normal compiler validation. Prototype equivalence mismatches and runner execution limitations are preserved as failed artifact diagnostics instead of being surfaced as production validation failures. `-Ajavatogpu.irValidationDiagnostics=quiet|summary|detailed` controls javac note verbosity in diagnostic mode: compact summaries include proof-bundle status fields such as `autoVectorizationProofBundleRewriteSafe`, `autoVectorizationProofBundleDiagnostics`, `autoVectorizationProofBundleUnsafeProofs`, and `autoVectorizationProofBundleFirstUnsafeProof`, while detailed diagnostics include the nested `autoVectorizationProofBundle={...}` context. `-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties` writes a machine-readable CI artifact with safety/CSE/vectorization counters plus auto-vectorization vector-shape, rewrite-readiness, rewrite-plan, rewrite-policy, rewrite-dry-run, resolved-rewrite, rewrite-blocked, rewrite-guard, rewrite-plan proof-summary fields such as `autoVectorizationProofRewritePlanRewriteSafe`, `autoVectorizationProofRewritePlanDiagnostics`, and `autoVectorizationProofRewritePlanGuardFamily.*`, proof-bundle fields such as `autoVectorizationProofBundleProofs`, `autoVectorizationProofBundleKinds`, `autoVectorizationProofBundleKindCounts`, `autoVectorizationProofBundleRewriteSafe`, `autoVectorizationProofBundleDiagnostics`, `autoVectorizationProofBundleUnsafeProofs`, `autoVectorizationProofBundleFirstUnsafeProof*`, and `autoVectorizationProofBundleGuardFamily.*`, warning-family, rejection-reason, first-blocking-diagnostic, and first-blocking-family fields, including backend and memory constraint guard families such as `backendVectorWidth`, `backendDoubleVector`, and `memoryAddressSpace`. Strict modes fail the build on the configured validation boundary with detailed optimizer context.

Explicit CSE equivalence runs can also package `GpuIrCommonSubexpressionArtifactSnapshot` and `GpuIrCommonSubexpressionRuntimeEquivalenceReport` together through `GpuIrCommonSubexpressionArtifactReport`. `GpuIrCommonSubexpressionArtifactRunner` can produce that aggregate from explicit integer input cases and compared output names, including the current `binary_assoc_simple(...)` nested simple-arithmetic CSE path for `+` and `*`. The aggregate exposes stable opt-in keys such as `cseArtifactSuccessful`, `cseArtifactSummary`, `cseArtifactSnapshot.*`, and `cseArtifactRuntimeEquivalence.*` for CI artifacts without enabling the mutating CSE prototype in normal validation. Failed CSE equivalence artifacts now export the same joined and indexed diagnostic text fields as auto-vectorization prototype artifacts for machine-readable failure triage.

Literal-arithmetic runtime-equivalence artifacts use the same first, joined, and indexed diagnostic export shape as the broader CSE and auto-vectorization runtime artifacts, so CI can triage failed or not-run evidence without parsing summaries.

All runtime-equivalence artifact surfaces now include grouped diagnostic-family counters for `executionFailed`, `missingOutput`, `outputDiffers`, and `other`, giving CI stable failure-type metrics alongside the raw diagnostic text.

The CSE snapshot now also exports a conservative read-only rewrite policy surface. `cseRewritePolicyCanRewrite`, `cseRewritePolicyReadiness`, `cseRewritePolicyBlockingSkippedCandidates`, grouped `cseRewritePolicyBlockingSkipReason*`, and grouped `cseRewritePolicyBlockingDominanceStatus*` fields let CI distinguish an empty CSE plan, a rewrite-ready prototype plan, and a blocked future production rewrite without enabling mutation.

Canonical CSE matching also has a narrow normalization-depth slice for nested simple reference-only arithmetic. The scanner emits `binary_assoc_simple(...)` candidates for safe repeated shapes such as `x + (y + z)`, `(z + x) + y`, `x * (y * z)`, and `(z * x) * y`, the classifier/planner treats them as local read-only CSE candidates, and javac `.properties` reports surface the resulting `cseRewritePolicyReadiness=ready` path without wiring production CSE mutation into normal validation. The same artifacts now include `cseSimpleArithmeticProof*` fields, including `cseSimpleArithmeticProofProofBoundary=referenceOnlyNestedArithmetic` and `cseSimpleArithmeticProofBlockedBoundary=literalsAndCastsRequireTypedNumericProof`, so CI can distinguish proven reference-only cases from literal/cast cases that still need typed numeric proof metadata. A companion `cseSimpleArithmeticNumericBoundary*` artifact surface records blocked nested arithmetic shapes with literal/cast operands, grouped reasons such as `literalOperand`, `castOperand`, or `literalAndCastOperands`, operand types, literal sources, cast targets, and cast source types without making those blockers fail diagnostic mode.

The literal-arithmetic proof surface is also read-only. `cseSimpleArithmeticLiteralProof*` classifies numeric-boundary candidates, marking `int` literal-only nested `+` and `*` arithmetic as `safeIntLiteralNestedArithmetic` while keeping cast, non-int operand, and non-int literal cases blocked under `nonIntOrCastLiteralArithmetic` until production CSE mutation has stronger typed numeric proof coverage. It now splits blocked reasons into explicit buckets such as `longLiteralOverflowSemanticsRequireProof`, `floatingLiteralSemanticsRequireProof`, `castRequiresExplicitNumericProof`, and `nonIntOperandSemanticsRequireProof`, plus first-blocked explanation fields so CI output says why a family is still outside the safe proof boundary. `cseSimpleArithmeticLiteralTypedNumericBlockers*` then aggregates those proof blockers with `cseSimpleArithmeticLiteralNumericSemanticsProof*` blockers into readiness, total blocked candidates, grouped family counts, first blocker, first explanation, and `CiSummaryLine` fields. It also exports operator/type matrix metadata such as `plus:int,int,int`, `times:int,int,int`, and blocked non-int keys so CI can track which arithmetic families are approaching typed canonicalization readiness. The same safe/blocked operator-type maps now appear in compact and detailed validation diagnostics, so summary-mode javac output can show the typed proof frontier without opening the `.properties` artifact.

`cseSimpleArithmeticLiteralCanonicalization*` adds the next read-only preview layer for safe `int` literal `+` / `*` candidates. It exports prospective canonical keys such as `literal_assoc_preview(plus:int,int,int;literals=1)` and `literal_assoc_preview(times:int,int,int;literals=2)` with readiness `preview`, plus `cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys` for CI checks that should not parse the key-count map; these keys are evidence for future typed canonicalization only and do not change expression fingerprints or rewrite readiness. `cseSimpleArithmeticLiteralNumericSemanticsProof*` now marks those safe `int` `+` / `*` preview candidates as `proven` for the narrow Java integer arithmetic shape and exports `FullyProven`, proven/blocked counts, and operator/type summaries. `cseSimpleArithmeticLiteralTypedNumericBlockers*` summarizes remaining long-overflow, floating, cast, non-int operand, unsupported operator, and missing-literal proof work across both literal-proof and numeric-semantics layers. `cseSimpleArithmeticLiteralRuntimeEquivalence*` stores explicit test/runner evidence for that preview surface, including successful/equivalent flags, input-case/output counts, diagnostics, numeric-semantics readiness, and canonical-key counters; normal validation exports a not-run diagnostic instead of executing rewrites. The opt-in `GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceRunner` evaluates explicit integer input cases against the original IR twice, never mutates IR, and can produce successful evidence for safe `int` literal `+` / `*` preview cases. Its `runArtifact(...)` helper packages that evidence through `GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport`, exposing `cseSimpleArithmeticLiteralArtifact*`, nested canonicalization/proof/runtime/gate/fingerprint-decision/fingerprint-parity fields, nested `Consistency.*` self-check fields, and `GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary` for one-line CI logs. `GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport` verifies that explicit runtime artifacts still agree across runtime evidence, gate state, fingerprint decision/parity state, and compact summary fields before CI treats the artifact as coherent. `cseSimpleArithmeticLiteralCanonicalizationGate*` makes the remaining boundary machine-readable by always reporting `CanPromoteToFingerprint=false`; fully proven preview candidates use readiness `blockedPreview` with blocking reasons `runtimeEquivalenceNotProven` and `fingerprintIntegrationDisabled`, successful explicit runtime evidence removes the runtime blocker, and empty previews use readiness `none`. `cseSimpleArithmeticLiteralFingerprintDecision*` records whether evidence is complete enough for a future fingerprint integration review while still reporting `ReadyForProduction=false`; successful explicit runtime evidence switches readiness to `evidenceCompleteButDisabled` and leaves `productionFingerprintIntegrationDisabled` as the only blocker. `cseSimpleArithmeticLiteralFingerprintParity*` compares literal preview keys with current production CSE insertion/skipped fingerprints, keeping readiness `previewOnly` whenever preview-only keys remain and exporting overlap counts plus blast-radius blockers. `cseSimpleArithmeticLiteralEnablement*` summarizes the full stack into a single verdict for CI triage, with values such as `notReady/runtimeMissing`, `notReady/previewOnlyBlastRadius`, and `evidenceCompleteButDisabled`. `cseSimpleArithmeticLiteralRewritePreflight*` exposes the read-only typed rewrite eligibility surface, including eligible/blocked candidate counts, blocker counters, blast-radius state, enablement verdict, and first blocked candidate metadata, while `cseSimpleArithmeticLiteralRewriteOperationPreview*` adds the matching read-only operation-shape preview with eligible/blocked operation counts, shape counters, blocker counters, and first blocked operation metadata. `cseSimpleArithmeticLiteralPromotionChecklist*` combines those layers into one final read-only promotion gate with a verdict, production-mutation flag, and remaining-work list for CI. `cseSimpleArithmeticLiteralConsistencyCheck*` keeps the normal validation stack coherent by reporting a pass/fail verdict, CI summary line, first-failure explanation, and failed-check list when exported artifact counts drift. These layers leave all production fingerprint and rewrite paths disabled. Integration artifacts use lowered IR literal sources, while report-level key generation escapes ambiguous literal delimiters before adding them to summary maps.

`cseSimpleArithmeticLiteralPromotionReadiness*` adds the one-line rollup above the checklist, combining typed numeric blockers, runtime-equivalence evidence, preview-only fingerprint blast radius, production fingerprint disablement, and production mutation disablement into a single verdict, blocker list, remaining-work list, and CI summary line.

The proof-bundle artifact also reports unsafe proof-layer grouping through `autoVectorizationProofBundleUnsafeProofKindCounts` and `autoVectorizationProofBundleUnsafeProofKind.*`, so CI can distinguish all blocking proof surfaces from the first unsafe proof summary.

The same proof state now feeds a read-only proof decision surface. Compact diagnostics report `autoVectorizationProofDecision`, `autoVectorizationProofDecisionAllowRewrite`, and optional `autoVectorizationProofDecisionBlockingKinds`, while CI artifacts also include `autoVectorizationProofDecisionStatus`, `autoVectorizationProofDecisionBlockingProofKinds`, and first-blocking-proof fields. Nested proof-bundle artifacts mirror the same decision with `autoVectorizationProofBundleDecision*` keys.

`autoVectorizationReadiness*` now adds the matching one-line readiness rollup for auto-vectorization, combining candidate availability, warnings, rejections, rewrite-plan guards, proof-bundle safety, rewrite-policy status, dry-run status, and resolved-operation availability into one verdict, blocker list, remaining-work list, and CI summary line.

The readiness-to-equivalence bridge tests now cover both sides of that gate: a `readyForPrototypeRewrite` preview must produce a successful opt-in prototype runtime-equivalence artifact, while warning-blocked and rewrite-guard-blocked previews must stop before prototype equivalence is treated as usable evidence.

Prototype runtime-equivalence artifacts now include multi-output compared-output coverage for ready previews, including failure cases that preserve exact missing compared output names, aggregate diagnostics across multiple input cases, export joined and indexed diagnostic text, and keep a stable first diagnostic for compact CI output.

Optimizer validation now also exposes a unified read-only gate explanation. Compact diagnostics and CI artifacts include `optimizerGateBlocked`, `optimizerGateSource`, `optimizerGateFamily`, and `optimizerGateSummary`, choosing the first blocking gate in priority order: safety errors, auto-vectorization blocking diagnostics/proof decisions, then CSE rewrite-policy blockers. CI artifacts also include `optimizerGateCompactSummary`, which combines the first-gate explanation with grouped source/family counters for one-line trend logs. A separate read-only policy decision exports `optimizerGatePolicyMode`, `optimizerGatePolicyBlocked`, `optimizerGatePolicySource`, `optimizerGatePolicyFamily`, and `optimizerGatePolicySummary`, so diagnostic mode can record gate state without failing while strict modes explain the exact gate they enforce. Strict optimizer failures start with that compact policy explanation before the detailed nested report.

The gate explanation also exports grouped source counters through `optimizerGateSourceCounts` and `optimizerGateSourceCount.*`, so CI can distinguish the first blocking gate from the full blocking profile when, for example, auto-vectorization warnings and CSE rewrite-policy blockers are both present.

It also exports grouped family counters through `optimizerGateFamilyCounts` and `optimizerGateFamilyCount.*`, preserving concrete families such as `warning.alias`, `guard.memoryAddressSpace`, `rejection.UNSUPPORTED_LANE_COUNT`, `cseRewritePolicy.blockedBySkippedCandidate`, or `cseRewritePolicy.skipReason.CONTROL_FLOW_BOUNDARY` for CI trend analysis.

See [IR Validation](IR-Validation.md) for setup, checks, planning modes, and current limitations.

## Recommended Full Routine

Run this before publishing release notes or claiming a fresh local validation point:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

## Report Artifacts

The report bundle is written to:

```text
processor/build/reports/opencl/
```

Important files:

- `validation-report.md` - runtime/device snapshot and feature report.
- `validation-history.md` - rolling local validation history.
- `validation-history.properties` - machine-readable validation history.
- `bucket-status.properties` - per-bucket pass/fail/skip status.
- `workload-summary.properties` - serious workload equivalence summary.
- `long-running-summary.properties` - warm-session stability summary.

## What A Green Routine Proves

- The current compiler/runtime test buckets completed on the selected machine.
- OpenCL runtime selection and device capability reporting worked.
- Serious workload CPU-vs-GPU equivalence checks passed.
- Long-running warm-session reuse completed without detected ABI/resource regressions.
- Benchmark and stress buckets produced fresh evidence.

## What It Does Not Prove Yet

- Universal correctness across every OpenCL implementation.
- Intel or AMD behavior unless those devices were actually used.
- Stable public API compatibility before beta.
- Support for arbitrary Java bytecode or arbitrary Java application acceleration.

## Vendor Quirks

Record reproduced vendor-specific deviations in [Device Quirks](Device-Quirks.md). Keep quirks evidence-based and linked to report artifacts where possible.

## Runner Contract

For future self-hosted machines, see [OpenCL Runner Contract](OpenCL-Runner-Contract.md).
