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

`GpuIrConstantFoldingPreviewProposalProvider` is a preview-only proof surface for future typed constant folding. It scans typed IR for simple binary expressions with plain decimal literal operands, records candidate counts, operator breakdown, numeric-kind breakdown, and the first folded value in proof fields, and always returns `NO_CHANGE`. It deliberately does not produce an optimized artifact, even when mutation is allowed.

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
