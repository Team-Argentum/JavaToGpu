# Public API and Extension Contract

JavaToGpu has one public source dialect: the `net.sixik.ga_utils.javatogpu.api.GPU` facade uses OpenCL-style naming for work-item queries, math intrinsics, vector helpers, and memory concepts. Future CUDA, Vulkan/SPIR-V, and Metal support should lower the same Java source dialect to different backend targets instead of adding CUDA-style, Vulkan-style, or Metal-style Java facades.

## Backend metadata

Portable intent should be represented by backend-neutral annotations and `IrGpu` metadata whenever possible. Backend-specific raw metadata is allowed only as an explicit escape hatch:

```java
@GPU
@GPUWorkGroupSize(x = 8, y = 8, z = 1)
static void kernel(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = 1.0f;
}
```

Use `@GPUAttribute` only when no portable annotation exists yet. It supports repeated usage on the same method, field, type, or parameter:

```java
@GPUAttribute(backend = GpuBackendTarget.OPENCL, value = "reqd_work_group_size(8, 8, 1)")
@GPUAttribute(backend = GpuBackendTarget.OPENCL, value = "vec_type_hint(float4)")
@GPU
static void kernel(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = 1.0f;
}
```

For vendor or device-specific metadata, narrow the selector explicitly:

```java
@GPUAttribute(
        backend = GpuBackendTarget.OPENCL,
        vendor = GpuVendorTarget.NVIDIA,
        deviceClass = GpuDeviceClassTarget.DGPU,
        value = "some_vendor_hint"
)
```

`@GPUAttribute` is not portable by itself. A backend lowerer may consume it only when the selected backend, vendor, and device class match the annotation selectors; otherwise it should reject or ignore it fail-closed with a diagnostic. Existing `@OpenCLAttributes` and `@OpenCLQualifiers` remain compatibility surfaces for OpenCL-only code, but new backend-specific metadata should prefer `@GPUAttribute`. Generic OpenCL emission should not apply vendor/device-specific raw attributes until device-aware lowering is available.

## Runtime selection

Runtime device selection should be deterministic by default. The runtime should prefer supported discrete GPUs over integrated GPUs or CPU OpenCL devices, apply known-good validation evidence when available, and still allow explicit user overrides for advanced deployments.

## Optimizer evidence

Method-level optimizer intent should be declared with `@GPUOptimize`. The default is strict floating-point behavior:

```java
@GPU
@GPUOptimize(fastMath = false)
static void strictKernel(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = 1.0f;
}
```

`fastMath = true` only records permission for future proof-backed rewrites. It does not bypass runtime-equivalence,
rollback, or production-promotion gates.

Any mutating runtime optimizer, peephole pass, vendor rewrite, or third-party optimization hook must produce evidence before it can affect production code:

- original and optimized `IrGpu` identities;
- strictness or tolerance policy;
- correctness result before/after optimization;
- performance result or an explicit `not-run` value;
- rollback/fallback decision and diagnostics.

Without accepted evidence, optimizer passes may run as diagnostics but must not change the selected production IR.

## Extension hooks

JavaToGpu should expose deliberate SPI hooks rather than requiring downstream forks. Extension points should be phase-specific and permission-aware: read-only validation, diagnostics, optimizer proposals, backend lowering, device policy, runtime-equivalence execution, promotion gates, and artifact writing should not share one unrestricted callback model.

Third-party extensions must be auditable. Runtime/build artifacts should record extension ids, versions, phases, decisions, diagnostics, and whether each extension was advisory or production-affecting.

The shared metadata foundation is available through:

- `GpuExtension` for stable extension identity, version, declared capabilities, and deterministic load order;
- `GpuExtensionCapability` for the public capability taxonomy;
- `GpuExtensionPhase` for the exact pipeline phase where an extension participates;
- `GpuExtensionPermission` for read-only, mutation-proposal, or production-affecting authority;
- `GpuExtensionDescriptor` for validated immutable metadata;
- `GpuExtensionRegistry` for duplicate-id rejection, deterministic audit ordering, and properties-compatible field export.

`GpuRuntimeIrOptimizationPass` declares the `RUNTIME_IR_OPTIMIZATION` phase, `IR_OPTIMIZATION_PROPOSAL` capability, and `MUTATION_PROPOSAL` permission. `GpuIrValidationProvider` declares the `IR_VALIDATION` phase and capability with `READ_ONLY` permission. Both remain functional interfaces. Explicitly supplied pass/provider lists retain caller order because execution order is semantic; `ServiceLoader` discovery is sorted by extension order, id, and version before execution.

Extension ids must be unique inside a pipeline. Blank metadata, whitespace/control characters in ids or versions, missing capabilities, null entries, duplicate ids, wrong phases, excessive permissions, and missing required capabilities are rejected before execution.

Runtime optimizer extensions may declare `PRODUCTION_AFFECTING`, but production profiles will not execute them without a matching `GpuProductionExtensionAuthorizationDecision`. Authorization requires an accepted production optimizer gate, accepted proof artifact, passed runtime equivalence, clean fallback and rollback state, declared rollback support, a valid `production-enabled` promotion decision with mutation allowed, and explicit operator acceptance.

Authorization is bound to extension id/version, backend target, device vendor and label, optimization profile, and original `IrGpu` identity. A decision produced for one kernel, device, version, or profile cannot authorize another runtime context. Review/non-production profiles may still run the pass to collect candidate IR and evidence. Extension failure isolation and persistence of authorization fields into production artifacts remain separate roadmap work.

Extension execution is reported through `GpuExtensionExecutionReport` with a stable outcome and failure policy:

- `SUCCEEDED` and `SKIPPED` describe normal execution decisions;
- `FAILED_CONTINUED` means an advisory failure was isolated and the pipeline continued;
- `FAILED_CLOSED` means strict or production execution stopped;
- `CONTINUE`, `STOP_PIPELINE`, and `THROW` record the applied failure policy.

Advisory runtime optimizer failures are converted into non-mutating skipped pass reports, so later passes may still run and the aggregate report is not incorrectly marked rollback-required. Production-profile optimizer failures stop the optimizer pipeline and remain rollback-required. Diagnostic IR validation records and reports unexpected provider failures but continues to later providers; strict validation throws `GpuExtensionExecutionException`. Existing compiler-domain exceptions such as `GpuIrPassException` are still reported but preserve their original exception type for compatibility.

Third-party IR validators use `GpuIrValidationProvider` and emit `GpuIrValidationReportEntry` values. Each entry has:

- a backward-compatible provider alias;
- extension id and version;
- rule id and `INFO` / `WARNING` / `ERROR` severity;
- Java or ASM source anchor;
- method and entry-point identity;
- arbitrary machine-readable provider fields.

The runner enriches legacy four-argument report entries with the actual loaded extension metadata and compiler-derived source anchor. The annotation processor writes these fields as `entry.N.extensionId`, `extensionVersion`, `ruleId`, `severity`, and `sourceAnchor` beside the existing `provider`, `methodName`, `entryPoint`, and provider-specific fields. This allows company validators and domain-specific rule packs to participate in the same report artifact without losing attribution or breaking older consumers.

Third-party optimizer integrations have two levels:

- `GpuRuntimeIrOptimizationPass` for a complete staged optimizer pass;
- `GpuRuntimeIrPeepholeRule` for one typed-IR pattern rule hosted by the built-in peephole pass.

A peephole rule receives `GpuRuntimeIrPeepholeRuleContext`, including the compile request, complete `IrGpu` artifact, current method body, typed body, and indexed typed nodes. It returns `GpuRuntimeIrPeepholeRuleReport` with rule id/version, method, candidate and proposal counts, mutation-proposal state, proof status, diagnostics, and custom fields.

`GpuRuntimeIrPeepholeRuleRegistry` loads built-in and ServiceLoader rules deterministically, rejects duplicate rule or extension ids, validates phase/capability/permission metadata, and records rule-level execution outcomes. Advisory rule failures are isolated so later rules can still report candidates; production-profile rule failures stop analysis. The packaged MAD/FMA matcher is implemented through the same SPI as external rules.

Peephole rules are currently proposal-only. The host pass does not apply structural mutations yet, and a custom rule cannot bypass `@GPUOptimize(fastMath = true)`, proof artifacts, production-extension authorization, rollback, runtime-equivalence evidence, or final production IR selection.

### Backend and device policy extensions

Backend lowerers participate in the common extension contract through `GpuBackendLowerer`. A lowerer exposes a stable extension id derived from its backend target, its lowerer version, the `BACKEND_LOWERING` capability and phase, and `PRODUCTION_AFFECTING` permission. This metadata makes backend participation auditable without allowing a lowerer to bypass source-selection, proof, or production-promotion gates.

Device-selection extensions implement `GpuRuntimeDevicePolicy`. A policy receives `GpuRuntimeDevicePolicyContext`, which contains compile options, an optional kernel descriptor, and the complete list of discovered `GpuRuntimeDeviceProfile` candidates. The descriptor is absent during backend-wide discovery and present when selection is re-evaluated for a concrete compile request. A policy returns a `GpuRuntimeDevicePolicyDecision` containing:

- score adjustments keyed by stable device identity;
- rejected candidate identities;
- capability facts;
- vendor-quirk diagnostics;
- compile-option validity and diagnostics;
- additional machine-readable fields.

Policies provide evidence and constraints, not the final answer. `GpuRuntimeDevicePolicyRegistry` owns deterministic conflict resolution:

- built-in and ServiceLoader policies are ordered by extension order, policy id, and version;
- duplicate policy or extension ids and invalid phase/capability/permission metadata are rejected;
- rejection takes precedence over positive score adjustments;
- core computes the base capability score and applies all policy adjustments;
- equal scores are resolved by stable device identity;
- advisory failures are isolated, while production-profile failures stop selection;
- invalid compile options or a failed-closed policy produce no selected device.

OpenCL now enumerates all platforms/devices through Packager before creating a context. It builds profiles with runtime device ids, vendor/driver/version data, device class, compute units, global/local memory, work-group limits, vector width, unified-memory state, and supported feature flags. The registry selects a profile, OpenCL maps that exact profile back to the native device, and only then calls `OpenClContext.create(...)`.

The live OpenCL session retains the complete selection result and compile requests reuse the selected profile. Runtime compile snapshots carry the selection through the backend-neutral `GpuRuntimeCompileArtifactSnapshot` contract. Artifact dumps emit `runtime-device-selection.properties` with selected-device metadata, ranked candidates, policy decisions, capability facts, quirks, compile-option diagnostics, extension execution outcomes, failure policies, blockers, and final diagnostics.

Explicit user overrides, request-specific re-evaluation for method compatibility or non-default compile options, normalized persistence of all non-device extension phases, and equivalent CUDA/Vulkan/SPIR-V/Metal discovery remain open work.
