# IR Optimizer

The optional IR optimizer module is the future backend-neutral transform layer for `IrGpu` artifacts.

The current module is intentionally a skeleton. It contributes one ServiceLoader-discovered no-op pass that records optimizer participation without changing the selected IR. This keeps the default runtime behavior safe while establishing the module, packaging, and report boundary for future optimization families.

## Contract

- `ir-validation` remains read-only and must not mutate `IrGpu`.
- `ir-optimizer` owns backend-neutral optimizer contracts and general passes.
- Vendor/device-specific optimizers live behind `GpuIrVendorOptimizationProposalProvider` and are loaded explicitly through `GpuIrVendorOptimizationProposalRegistry`, not through the default backend-neutral proposal bridge.
- Optimizer passes must not mutate the input artifact in place.
- Real rewrites must return a distinct optimized artifact, diagnostics, proof links, and rollback evidence.
- Missing optimizer modules, missing approval, failed proof, or disabled optimization must leave the original IR selected.

## Current Skeleton

`GpuIrNoOpOptimizationPass` reports a skipped optimizer pass at candidate-discovery stage. It proves that projects can add the optional optimizer module without enabling production mutation or changing generated backend source.

The first real proposal provider is `GpuIrTextCanonicalizationProposalProvider`. It only canonicalizes transitional IR text bodies by normalizing line endings and stripping trailing spaces or tabs. It preserves typed IR bodies, backend outputs, metadata, and the original artifact object. By default it remains proposal-only; the validation sandwich selects the canonicalized artifact only when mutation is explicitly allowed.

`GpuIrHelperDependencyDeduplicationProposalProvider` is the first backend-neutral metadata cleanup pass. It removes duplicate helper-dependency entries from method bodies while preserving first-occurrence order and leaving text bodies, typed bodies, backend outputs, and the original artifact unchanged. It is proposal-only by default and exists to prove the real rewrite/proposal path without changing kernel semantics.

`GpuIrConstantFoldingPreviewProposalProvider` is a preview-only proof surface for future typed constant folding. It scans typed IR for simple binary expressions with plain decimal literal operands, records candidate counts, operator breakdown, numeric-kind breakdown, first folded value, skipped blocker counts, policy state, and required proof gates, and always returns `NO_CHANGE`. It deliberately does not produce an optimized artifact, even when mutation is allowed.

The preview records blockers such as divide-by-zero, non-even division, unsupported operators, non-literal operands, and non-plain literals. It also records that runtime-equivalence and approval are required before any future rewrite, and that integer-overflow / floating-point-rounding safety is not yet proven. This keeps constant folding useful as evidence while preventing it from silently becoming an applied transform.

`GpuIrSafeLocalCsePreviewProposalProvider` is a second preview-only proof surface for future safe local common-subexpression elimination. It scans typed IR for repeated local expressions, records expression/candidate/duplicate/equivalence-class counts, and reports blockers such as unsupported operators, impure operands, and control-flow boundaries. It always returns `NO_CHANGE`, marks the proof as preview-only, and records that dominance, side-effect freedom, runtime equivalence, and approval must be proven before any future rewrite can graduate.

`GpuIrTypedDeadCodePreviewProposalProvider` is a third preview-only proof surface for future typed dead-code cleanup. It walks typed-body roots, records reachable versus unreachable node counts, unreachable-kind breakdown, missing root / missing child-reference blockers, and side-effecting unreachable-node blockers. It always returns `NO_CHANGE`, marks the proof as preview-only, and records that runtime equivalence, approval, and side-effect freedom must be proven before any future cleanup rewrite can graduate.

OpenCL validation aggregates these proof fields into `constantFoldingPreview.*`, `safeLocalCsePreview.*`, and `typedDeadCodePreview.*` summary fields inside `runtime-ir-optimizer-evidence.properties`. The same artifact also records `previewReadiness.*` fields that summarize preview-family status across constant folding, safe-local CSE, and typed dead-code cleanup as `not-recorded`, `no-candidates`, `candidates-recorded`, `blocked-by-proof`, or `ready-for-runtime-equivalence-review`. `runtimeEquivalenceReview.*` then exposes a fail-closed review gate over that aggregate: it can mark a preview family set as `review-ready`, but production mutation and selected-IR replacement remain disabled/manual-review-only. `reviewPackage.*` sits above that gate as the manual-review bundle boundary: it records whether a package is required, whether it is complete, the first blocker, proposal-pass and pending-approval counts, and repeats the disabled production mutation / selected-IR replacement guardrails. The generated `Runtime IR Optimizer Evidence` Markdown section shows constant-folding preview pass/candidate/blocker counts, safe-local-CSE pass/candidate-expression/duplicate-expression/blocker counts, typed dead-code pass/unreachable-node/blocker counts, aggregate preview-readiness status, runtime-equivalence review eligibility, and review-package status. These fields are report-only and do not participate in source switching, selected-IR replacement, or production mutation gates.

## Immutable Proposal Flow

The optimizer contract is intentionally split into proposal and selection:

1. Build a `GpuIrOptimizationProposalRequest` from the original `IrGpu` artifact and `GpuIrOptimizationPolicy` context.
2. Run a `GpuIrOptimizationProposalProvider` that returns `NO_CHANGE`, `REJECTED`, or `PROPOSED`.
3. Run the validation sandwich through `GpuIrOptimizationSandwichRunner`.
4. Select the optimized artifact only when the original artifact validates, the optimized artifact validates, and mutation is explicitly allowed.

If mutation is disabled, a valid optimized artifact is reported as `PROPOSAL_ONLY` and the original IR remains selected. If post-validation fails, the runner reports `OPTIMIZED_INVALID_ROLLED_BACK` and keeps the original IR.

## Policy Controls

`GpuIrOptimizationPolicy` is the backend-neutral control surface for optimizer providers and runtime adapters. It currently covers:

- `optimizerProfile` - high-level profile such as `off`, `diagnostic`, or future production profiles.
- `optimizationLevel` - shared optimization intensity hint independent of OpenCL/CUDA/Vulkan/Metal flags.
- `proposalOnly` - forces evidence collection without optimized IR selection.
- `mutationAllowed` - explicit opt-in required before the validation sandwich can select an optimized artifact.
- `fastMathAllowed` - explicit permission for future math-reassociation or precision-sensitive rewrites.
- `vendorAdaptationAllowed` - explicit permission for vendor/device-specific proposals to participate.
- `registerPressureSplittingAllowed` - explicit permission for future register-pressure/resource-shaping rewrites.
- `rollbackRequired` - records whether a proposal must provide rollback/fallback evidence.
- `proofRequired` - records whether proof/runtime-equivalence evidence is required for the proposal class.

`GpuIrOptimizationProposalRequest` preserves the older constructor shape for compatibility, but it now exposes a normalized `policy()` view and serializes `policy.*` fields into context metadata. Provider authors should read the policy instead of inventing backend-specific context keys. Defaults are fail-closed: proposal-only, mutation disabled, fast math disabled, vendor adaptation disabled, register-pressure splitting disabled, rollback required, and proof required.

## Runtime Bridge

`GpuIrOptimizationProposalRegistry` owns backend-neutral provider discovery. It loads `GpuIrOptimizationProposalProvider` implementations through ServiceLoader, orders providers deterministically, and rejects duplicate extension ids before runtime adaptation.

`GpuIrProposalRuntimeBridgePass` is the runtime adapter between the existing runtime optimizer pipeline and the immutable proposal-provider contract. It consumes providers from `GpuIrOptimizationProposalRegistry`, runs each provider through the validation sandwich, and reports the result as a runtime optimizer pass report.

The default bridge is intentionally proposal-only. It can surface canonicalization proposals and proof metadata in runtime optimizer reports, but it does not select optimized IR unless constructed with explicit mutation permission. This lets `ir-optimizer` participate in runtime evidence collection without enabling production mutation.

Runtime artifact dumping records bridge/provider evidence in `runtime-ir-optimizer-evidence.properties`. The artifact is filtered by the stable `javatogpu.ir-optimizer` provider prefix and includes pass counts, outcomes, proposal-only/selected counts, original/transformed IR identities, proof artifact fields, and diagnostics. This is an evidence surface only; it does not authorize optimized IR selection or production mutation by itself.

Runtime peephole/InstCombine proof is also kept read-only. `GpuRuntimeIrPeepholeReplacementPlan` records future structural replacement intent, including root node, covered node ids, input node ids, replacement kind, completeness, and first blocker. `GpuRuntimeIrTypedNodeGraph` is the shared read-only traversal view for typed peephole rules, and `GpuRuntimeIrPeepholeReplacementPlanValidation` checks each emitted plan against that graph before it is surfaced. `GpuRuntimeIrPeepholeTypedRewriteVisitor` is the shared traversal preflight above validated plans: it records deterministic visit order, visitor-ready/blocked counts, first visitor blocker, `visitorImplemented=true`, and disabled replacement-builder / transformed-IR / mutation / selected-IR flags. `GpuRuntimeIrPeepholeReplacementBlueprint` sits above visitor-ready plans as a read-only target-node contract: it records intended intrinsic-call node kind, target operation, argument node ids/roles, blueprint-ready/blocked counts, first blueprint blocker, and disabled replacement-builder / transformed-IR / mutation / selected-IR flags. `GpuRuntimeIrPeepholeRewriteTransactionPreflight` then describes the future graph transaction shape: replaced root ids, removed covered ids, retained input ids, planned added-node count, `plannedAddedNodeIds=not-allocated`, and disabled node-id allocator / graph-rewrite / transformed-IR / mutation / selected-IR flags. `GpuRuntimeIrPeepholeNodeIdAllocationPreflight` sits above transaction-ready plans as a diagnostic-only allocation preview: it records current graph max node id, deterministic candidate node ids, allocation-ready/blocked counts, first blocker, and disabled id reservation / allocator application / graph-rewrite / transformed-IR / mutation / selected-IR guardrails. `GpuRuntimeIrPeepholeReplacementNodePreflight` then pairs that candidate id with the intrinsic-call blueprint as a diagnostic-only replacement-node construction preview, keeping `replacementNodeBuilt=false`, `replacementBuilderImplemented=false`, and graph-rewrite / transformed-IR / mutation / selected-IR disabled. `GpuRuntimeIrPeepholeGraphPatchPreflight` records the next read-only patch preview: replacement node id, replaced/removed/retained/inserted node sets, graph-patch ready/blocked counts, first blocker, and disabled graph-patch application / graph-rewrite / transformed-IR / mutation / selected-IR guardrails. `GpuRuntimeIrPeepholeTransformedGraphPreflight` adds the materialization boundary above that patch: original IR identity, deterministic materialization key, `transformedGraphIdentity=not-built`, materialization-ready/blocked counts, first blocker, and disabled transformed-graph build / transformed-IR / graph-patch application / graph-rewrite / mutation / selected-IR guardrails. `GpuRuntimeIrPeepholeRewriteSketch` remains the mutation-free rewrite skeleton: a complete, valid replacement plan can become `sketch-ready`, but the sketch still records `rewriteBuilderImplemented=false`, `mutationAllowed=false`, and `selectedIrReplacement=false`; runtime equivalence and approval remain required. `GpuRuntimeIrPeepholeRewriteSelectionReadiness` now sits above ready/blocked sketches as a read-only selection preflight: it reports sketch counts, conflict counts, status, first blocker, missing rewrite-builder/conflict-resolution/proof/approval gates, and disabled mutation/selection flags without choosing any optimized IR. `GpuRuntimeIrPeepholeRewriteProofReadiness` then records the next fail-closed proof boundary: proof required/accepted state, runtime-equivalence payload presence/completeness, rollback evidence, approval acceptance, original IR identity, absent transformed IR identity, and disabled mutation / selected-IR replacement. `GpuRuntimeIrPeepholeRewriteReviewPackage` sits above proof readiness as the manual-review package boundary: it records required/complete state, conflict count, proof acceptance, runtime-equivalence payload, rollback, approval, manual-review-only state, and disabled mutation / selection / selected-IR replacement. The pass emits aggregate `rewriteVisitor.*`, `replacementBlueprint.*`, `rewriteTransaction.*`, `nodeIdAllocation.*`, `replacementNode.*`, `graphPatch.*`, `transformedGraph.*`, `rewriteSelection.*`, `rewriteProof.*`, and `rewriteReviewPackage.*` plus matching per-rule fields, so CI can identify which matcher family is blocked by graph validation, traversal, blueprint construction, transaction shaping, id-allocation preview, replacement-node preview, graph-patch preview, transformed-graph materialization, conflict resolution, missing runtime-equivalence payloads, missing rollback evidence, missing approval, or incomplete review packaging. Validation fields fail closed on missing roots, uncovered roots, or missing covered/input nodes; visitor fields prove only original-node traversal; blueprint fields describe only a future intrinsic-call shape; transaction fields describe only planned replace/remove/retain/add effects; allocation fields preview candidate ids only and keep `nodeIdsReserved=false` and `nodeIdAllocatorApplied=false`; replacement-node fields preview the synthetic node shape only and keep `replacementNodeBuilt=false`; graph-patch fields preview the future diff only and keep `graphPatchApplied=false`; transformed-graph fields preview materialization only and keep `transformedGraphBuilt=false`. They still do not reserve node ids, build replacement nodes, apply graph patches, build transformed graphs, rewrite graphs, or authorize mutation. The current matcher set covers `mad/fma` for `a*b+c` / `c+a*b`, `clamp` for `min(max(x, lo), hi)`, `step` for simple `x < edge ? 0 : 1` / `x >= edge ? 1 : 0` ternaries, `dot` for additive multiply trees such as `(a0*b0)+(a1*b1)`, and `mix` for `a + t * (b - a)` / `t * (b - a) + a`. `runtime-optimizer-drift.properties` aggregates complete/partial replacement-plan counts, structural plan-validation total/valid/invalid counts, rewrite-visitor ready/blocked counts, replacement-blueprint ready/blocked counts, rewrite-transaction ready/blocked counts, node-id allocation ready/blocked counts, replacement-node preview ready/blocked counts, graph-patch preview ready/blocked counts, transformed-graph materialization ready/blocked counts, rewrite-sketch ready/blocked counts, overlap/conflict counters, aggregate and per-rule `rewriteSelection.*` status/first-blocker guardrails, aggregate and per-rule `rewriteProof.*` proof/runtime-equivalence/rollback blockers, aggregate and per-rule `rewriteReviewPackage.*` package-completeness blockers, and compact `optimizerRule.*` summaries for CI drift detection. Workload gates carry those visitor/blueprint/transaction/allocation/replacement-node/graph-patch/transformed-graph fields forward with the rest of the drift payload, while validation reports/history, I3 summaries, and production-promotion explainability treat the optimizer state as evidence-only telemetry. The pass still reports `rewrite-engine-not-implemented` and never mutates the selected IR.

`GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight` now sits above transformed-graph materialization as the future optimized-artifact boundary. It records the original IR identity, absent transformed graph and optimized artifact identities, deterministic envelope key, proof anchor, rollback anchor, ready/blocked counts, first blocker, and disabled artifact build / optimized-artifact build / transformed-IR / graph-patch / graph-rewrite / mutation / selected-IR flags. The pass emits aggregate and `rule.N.irArtifactEnvelope.*` fields, `runtime-optimizer-drift.properties` aggregates them, and workload gates carry them forward as evidence-only telemetry; no optimized artifact is constructed or selected.

`GpuRuntimeIrPeepholeArtifactProofBindingPreflight` records the next fail-closed proof-binding boundary between that artifact envelope and proof/review evidence. It reports binding counts, proof and review blockers, runtime-equivalence payload completeness, rollback evidence, approval state, and disabled proof/rollback/approval binding flags. Binding remains blocked until proof is accepted, runtime-equivalence payloads are complete, rollback evidence is clean, approval is accepted, and the manual-review package is complete; even then this preflight does not build optimized IR or select it.

`GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight` records the final read-only selection boundary above proof binding. It reports whether proof binding is ready, whether production gate and mutation policy would still block selection, and keeps optimized-artifact selection, selection application, mutation, and selected-IR replacement disabled. This makes the future handoff to runtime IR selection auditable without changing the selected artifact.

The same evidence artifact now records `approvalTemplate.*` fields for each optimizer pass. Real proposal rewrites with distinct original/optimized identities and accepted proof metadata are marked `pending`; preview and no-change proposals are marked `not-applicable` with a first blocker such as `proposal-decision-not-proposed`.

## Vendor Optimizer SPI

`GpuIrVendorOptimizationProposalProvider` is the optional vendor/device-specific contract. It receives a `GpuIrVendorOptimizationProposalRequest` with backend target, vendor, device id/label, driver/API text, device class, optimizer profile, mutation flag, and all original context fields. The provider still returns the same immutable `GpuIrOptimizationProposal`, so vendor proposals go through the same validation sandwich and rollback semantics as backend-neutral proposals.

`GpuIrVendorOptimizationProposalRegistry` loads vendor providers deterministically, rejects duplicate extension ids, and exposes an adapter back to `GpuIrOptimizationProposalProvider`. The default runtime bridge does not auto-load vendor providers; projects or future vendor modules must opt into that registry explicitly. This keeps `ir-optimizer` backend-neutral while leaving room for a separate `ir-vendor-optimizer` artifact.

## Packaging External Providers

Backend-neutral optimizer providers should depend on `javatogpu-ir-optimizer`, implement `GpuIrOptimizationProposalProvider`, and register the implementation in:

```text
META-INF/services/net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrOptimizationProposalProvider
```

Vendor/device-specific optimizer providers should depend on `javatogpu-ir-vendor-optimizer` or `javatogpu-ir-optimizer`, implement `GpuIrVendorOptimizationProposalProvider`, and register the implementation in:

```text
META-INF/services/net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrVendorOptimizationProposalProvider
```

Do not register a vendor provider in the backend-neutral provider descriptor unless it is intentionally safe for every backend/device context. The default runtime bridge loads only backend-neutral proposal providers. Vendor providers must be loaded explicitly through `GpuIrVendorOptimizationProposalRegistry`, adapted into the common proposal contract, and then passed through the same validation sandwich.

Provider authors must expose stable `extensionId`, `extensionVersion`, and `extensionOrder` values. Duplicate ids are rejected by the registry. A provider that cannot prove a safe rewrite should return `NO_CHANGE` or `REJECTED`; it should not return a proposed artifact without proof fields, diagnostics, and rollback/equivalence evidence.

## Approval Manifest

`GpuIrOptimizationApprovalManifest` writes a review-only approval template for one immutable optimizer proposal. The template is bound to the optimizer id/version, original and optimized `IrGpu` identities, backend target, optimization profile, optional device vendor/label context, proof source/verdict, and rollback requirement. It is intended for packaging under:

```text
META-INF/javatogpu/ir-optimization-approvals/
```

Validation requires `status=approved`, explicit approval id/author/timestamp, matching proposal identities, matching backend/device context, matching proof metadata, `binding.rollback.required=true`, and `authorization.productionMutation=disabled`. A valid manifest is still `manual-review-only`: it proves that the proposal was reviewed, but it does not select optimized IR or grant production mutation by itself.

`GpuIrOptimizationApprovalTemplateFormatter` is the bridge between proposal evidence and approval artifacts. It returns a pending template only for a real `PROPOSED` rewrite with distinct original/optimized identities and accepted proof metadata. Preview or `NO_CHANGE` proposals return `not-applicable` with a first blocker such as `proposal-decision-not-proposed`, so report tooling can expose why no approval template should be packaged yet.

## Read Next

- [IR Validation](IR-Validation.md)
- [Runtime Guide](Runtime-Guide.md)
- [Public API and Extension Contract](Public-API-And-Extension-Contract.md)
