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

For compiler-development or CI builds, add the optional `javatogpu-ir-validation` artifact to the annotation-processor path and enable `-Ajavatogpu.irValidation=diagnostic`, `strictSafety`, or `strictOptimizer`. It runs additional lowered-IR validation before OpenCL emission and includes read-only CSE / auto-vectorization planning reports without rewriting production IR. The auto-vectorization rewrite applicator is currently a no-op safety gate in the production-facing `apply(...)` path: it accepts only previews where `autoVectorizationCanApplyRewrite=true`, rejects guarded, warned, or rejected previews before mutation can happen, reports structured dry-run artifact fields such as `autoVectorizationRewriteDryRunReadiness`, `autoVectorizationRewriteDryRunSuccessful`, and `autoVectorizationRewriteDryRunDiagnostics`, and resolves accepted operations into read-only loop metadata fields such as `autoVectorizationResolvedRewriteOperations`, `autoVectorizationResolvedRewriteFirstInsertion`, and `autoVectorizationResolvedRewriteFirstReplacement`. `GpuIrAutoVectorizationArtifactSnapshot` now centralizes those auto-vectorization report exports while preserving the existing `autoVectorization*` keys used by CI artifacts. `GpuIrAutoVectorizationMemoryLegalityAnalyzer` now centralizes read-only target/source alias warnings and memory-address-space guards into a typed `GpuIrAutoVectorizationMemoryLegalityReport`, while scanner/provider artifacts continue surfacing the same warning and guard families. `GpuIrAutoVectorizationControlFlowBoundaryAnalyzer` now provides the same reusable read-only surface for control-flow and early-exit sibling boundary guards. Both reports expose `GpuIrAutoVectorizationProofSummary` and uniform `artifactFields(...)` maps using default prefixes `autoVectorizationProofMemoryLegality` and `autoVectorizationProofControlFlowBoundary`, giving future rewrite gates a shared compact pass/block/warning/guard view across proof analyzers. `GpuIrAutoVectorizationProofBundle` now aggregates rewrite-plan, memory-legality, and control-flow proof summaries into one read-only gate input with compact proof-kind summaries, raw proof-kind counters, unsafe-proof helpers, and stable `autoVectorizationProofBundle*` artifact fields. An explicit test/integration `rewritePrototype(...)` path now rewrites only narrow lane-copy, unary lane, simple lane-wise binary, and lane/literal binary shapes and reports applied rewrites through `GpuIrAutoVectorizationPrototypeRewriteReport` plus typed `GpuIrAutoVectorizationPrototypeAppliedRewrite` entries, including the applied expression shape (`LANE_COPY`, `UNARY_LANE_OP`, `BINARY_LANE_OP`, or `LANE_LITERAL_BINARY_OP`) plus the unary or binary operator when relevant. The prototype report also exposes applied rewrite family counters by enum and artifact value, with summaries such as `appliedRewriteFamilies={laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}`, and explicit prototype runners can export `.properties`-friendly fields with `artifactFields(...)`. `GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport` can separately export explicit pre/post rewrite equivalence evidence fields such as `autoVectorizationPrototypeRuntimeEquivalenceSuccessful`, `autoVectorizationPrototypeRuntimeEquivalenceInputCases`, and `autoVectorizationPrototypeRuntimeEquivalenceAppliedRewriteFamilies`. `GpuIrAutoVectorizationPrototypeArtifactReport` combines both reports into one opt-in export map for integration artifacts, exposes `autoVectorizationPrototypeArtifactSummary`, and provides `GpuIrAutoVectorizationPrototypeArtifactSummary` for typed one-line logging of method, success, rewrite, input/output, diagnostic, and rewrite-family counters. `GpuIrAutoVectorizationPrototypeArtifactRunner` can build that artifact from explicit integer-array input cases plus compared output names while remaining disabled for normal compiler validation. Prototype equivalence mismatches and runner execution limitations are preserved as failed artifact diagnostics instead of being surfaced as production validation failures. `-Ajavatogpu.irValidationDiagnostics=quiet|summary|detailed` controls javac note verbosity in diagnostic mode: compact summaries include proof-bundle status fields such as `autoVectorizationProofBundleRewriteSafe`, `autoVectorizationProofBundleDiagnostics`, `autoVectorizationProofBundleUnsafeProofs`, and `autoVectorizationProofBundleFirstUnsafeProof`, while detailed diagnostics include the nested `autoVectorizationProofBundle={...}` context. `-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties` writes a machine-readable CI artifact with safety/CSE/vectorization counters plus auto-vectorization vector-shape, rewrite-readiness, rewrite-plan, rewrite-policy, rewrite-dry-run, resolved-rewrite, rewrite-blocked, rewrite-guard, rewrite-plan proof-summary fields such as `autoVectorizationProofRewritePlanRewriteSafe`, `autoVectorizationProofRewritePlanDiagnostics`, and `autoVectorizationProofRewritePlanGuardFamily.*`, proof-bundle fields such as `autoVectorizationProofBundleProofs`, `autoVectorizationProofBundleKinds`, `autoVectorizationProofBundleKindCounts`, `autoVectorizationProofBundleRewriteSafe`, `autoVectorizationProofBundleDiagnostics`, `autoVectorizationProofBundleUnsafeProofs`, `autoVectorizationProofBundleFirstUnsafeProof*`, and `autoVectorizationProofBundleGuardFamily.*`, warning-family, rejection-reason, first-blocking-diagnostic, and first-blocking-family fields, including backend and memory constraint guard families such as `backendVectorWidth`, `backendDoubleVector`, and `memoryAddressSpace`. Strict modes fail the build on the configured validation boundary with detailed optimizer context.

Explicit CSE equivalence runs can also package `GpuIrCommonSubexpressionArtifactSnapshot` and `GpuIrCommonSubexpressionRuntimeEquivalenceReport` together through `GpuIrCommonSubexpressionArtifactReport`. `GpuIrCommonSubexpressionArtifactRunner` can produce that aggregate from explicit integer input cases and compared output names. The aggregate exposes stable opt-in keys such as `cseArtifactSuccessful`, `cseArtifactSummary`, `cseArtifactSnapshot.*`, and `cseArtifactRuntimeEquivalence.*` for CI artifacts without enabling the mutating CSE prototype in normal validation.

The proof-bundle artifact also reports unsafe proof-layer grouping through `autoVectorizationProofBundleUnsafeProofKindCounts` and `autoVectorizationProofBundleUnsafeProofKind.*`, so CI can distinguish all blocking proof surfaces from the first unsafe proof summary.

The same proof state now feeds a read-only proof decision surface. Compact diagnostics report `autoVectorizationProofDecision`, `autoVectorizationProofDecisionAllowRewrite`, and optional `autoVectorizationProofDecisionBlockingKinds`, while CI artifacts also include `autoVectorizationProofDecisionStatus`, `autoVectorizationProofDecisionBlockingProofKinds`, and first-blocking-proof fields. Nested proof-bundle artifacts mirror the same decision with `autoVectorizationProofBundleDecision*` keys.

Optimizer validation now also exposes a unified read-only gate explanation. Compact diagnostics and CI artifacts include `optimizerGateBlocked`, `optimizerGateSource`, `optimizerGateFamily`, and `optimizerGateSummary`, choosing the first blocking gate in priority order: safety errors, auto-vectorization blocking diagnostics/proof decisions, then CSE skipped candidates. CI artifacts also include `optimizerGateCompactSummary`, which combines the first-gate explanation with grouped source/family counters for one-line trend logs. A separate read-only policy decision exports `optimizerGatePolicyMode`, `optimizerGatePolicyBlocked`, `optimizerGatePolicySource`, `optimizerGatePolicyFamily`, and `optimizerGatePolicySummary`, so diagnostic mode can record gate state without failing while strict modes explain the exact gate they enforce. Strict optimizer failures start with that compact policy explanation before the detailed nested report.

The gate explanation also exports grouped source counters through `optimizerGateSourceCounts` and `optimizerGateSourceCount.*`, so CI can distinguish the first blocking gate from the full blocking profile when, for example, auto-vectorization warnings and CSE skipped candidates are both present.

It also exports grouped family counters through `optimizerGateFamilyCounts` and `optimizerGateFamilyCount.*`, preserving concrete families such as `warning.alias`, `guard.memoryAddressSpace`, `rejection.UNSUPPORTED_LANE_COUNT`, or `cse.CONTROL_FLOW_BOUNDARY` for CI trend analysis.

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
