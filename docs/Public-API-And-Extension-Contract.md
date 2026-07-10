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

Use `@GPUDeviceConstraint` when a kernel or helper is only valid on specific backends, vendors, device classes, or capabilities:

```java
@GPU
@GPUDeviceConstraint(
        backends = {GpuBackendTarget.OPENCL},
        vendors = {GpuVendorTarget.NVIDIA, GpuVendorTarget.AMD},
        deviceClasses = {GpuDeviceClassTarget.DGPU},
        requiredFeatures = {"fp64"}
)
static void doubleKernel(@GPUGlobal double[] output) {
    output[GPU.get_global_id(0)] = 1.0;
}
```

The compiler stores this as per-method `methodDeviceConstraint.*` metadata in `kernel.irgpu.properties`. Java and ASM compilation paths use the same normalized model. Before OpenCL creates a context, `GpuRuntimeMethodDeviceConstraintPolicy` rejects candidates with an unsupported backend, vendor, device class, or missing required feature. Current portable feature names are `fp64`, `images`, and `subgroups`; unknown required features fail closed.

Use `GpuRuntimeDeviceOverride` through compile options when a deployment requires a specific device identity, vendor, label, or class:

```java
GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
        .defaults(GpuBackendTarget.OPENCL)
        .withDeviceOverride(GpuRuntimeDeviceOverride.byVendor("NVIDIA"));

GpuKernelInvocation invocation = new GpuKernelInvocation(
        descriptor,
        arguments,
        options
);
```

Available selectors include `byDeviceId(...)`, `byVendor(...)`, `byDeviceLabel(...)`, and `byDeviceClass(...)`. A directly constructed override may combine selectors; all populated selectors must match. Device ids are exact, while vendor and device-label selectors use case-insensitive containment so stable tokens such as `NVIDIA`, `AMD`, `Intel`, or `RTX 5070` can match full driver labels.

Overrides and method constraints are required constraints, not advisory score hints. If no candidate matches, selection fails with `GpuRuntimeDeviceSelectionException`. The first OpenCL context is created from the concrete kernel descriptor, loaded IrGpu metadata, and compile options. A later request is re-evaluated against the original candidate set; if it requires another device, the same exception is raised and the caller must create a new runtime scope/backend instance. JavaToGpu does not silently execute the request on the already active but incompatible device.

Startup correctness evidence participates in the same deterministic policy pipeline. `GpuRuntimeDeviceSelfTestMode.AUTO` runs backend self-tests when multiple candidates are discovered, `DISABLED` bypasses self-test evidence, and `REQUIRED` rejects candidates without a passed result. OpenCL's runner compiles bounded kernels, validates deterministic readback, and then collects one warm-up plus five compute and transfer samples. A failed correctness result is a hard rejection, not a negative score hint.

Performance evidence uses a trimmed median: minimum and maximum samples are discarded and at least three samples must remain. `GpuRuntimeDeviceSelfTestProfile` selects bounded workload size, transfer model, noise threshold, and score caps from device class and unified-memory topology. dGPU uses `dgpu-balanced-v1`; iGPU uses `igpu-unified-v1` or `igpu-conservative-v1`; CPU OpenCL uses `cpu-opencl-conservative-v1`. iGPU and CPU profiles deliberately use smaller workloads and lower transfer score caps than dGPU.

Compute rates are normalized only against stable evidence with the same workload profile. Transfer rates are normalized only against stable evidence with the same transfer model, so unified-memory, dedicated-memory, integrated-memory, and host-memory round trips are not treated as equivalent measurements. Compute and transfer stability are independent: a noisy metric is exported as telemetry with zero score without suppressing the other stable metric. Score caps preserve the core dGPU over iGPU/CPU class preference.

`GpuRuntimeDeviceSelfTestIdentity` binds cached evidence to backend, device id/label/vendor, device class, unified-memory topology, driver, runtime API version, runner id/version, and compiler identity. The built-in cache is process-local and removes stale entries when the same runner is replaced by another runner/compiler identity. Persistent cache storage, expiry, real Intel/AMD iGPU validation, register-pressure buckets, and long-running stability evidence are intentionally separate contracts.

### Runtime IR analysis evidence

`GpuRuntimeRegisterPressureAnalysisPass` is a built-in analysis-only runtime pass at `TARGET_PROFILE_ANALYSIS`. `register-pressure-interprocedural:v4` resolves parameters, locals, and private arrays to stable lexical identities, performs backward last-use analysis over structured typed-`IrGpu`, and then resolves helper-call edges through source/emitted method aliases. Inline helpers add non-reusable callee pressure to caller-live values; non-inline helpers remain separate frames and contribute through a maximum-frame estimate. Recursive and unresolved edges are diagnostic-only. `GpuRuntimeRegisterPressureBudget` selects the initial advisory budget from backend-neutral device class and vendor data. The current budget is not a promise about physical registers or occupancy.

Analysis-only passes store `analysisOnly=true` in their field maps. Core must exclude those reports from accepted/blocking optimizer proof counts, optimizer-family promotion readiness, runtime-equivalence family payload requirements, and production mutation authorization. They may still appear in `optimizer-report.txt` and must be persisted in `runtime-ir-analysis.properties` for diagnostics, CI comparison, and future adaptive planning.

The register-pressure artifact contract includes analysis/model version, budget source, availability, aggregate level, hottest method/call, utilization, per-method parameter/local/private-array/peak-live/expression/scoped/shadowed/unresolved counts, and per-call resolution/inline/recursion/frame fields. General CFG recovery and actual backend compiler inlining/frame reconstruction remain separate extensions to this contract.

`GpuBackendCompilerFeedbackProvider` is the read-only post-compilation SPI for resource diagnostics. Providers receive `GpuBackendCompilerFeedbackRequest` with backend target, format, resource, and compile log, and may return `GpuBackendCompilerFeedback` with general, vector, and scalar register counts, spill store/load bytes, stack-frame bytes, local-memory bytes, occupancy, raw fields, and diagnostics. Distinct register files must remain separate; a provider must not add AMD SGPR and VGPR counts into a synthetic total.

`GpuBackendCompilerFeedbackRegistry` validates the `BACKEND_COMPILER_FEEDBACK` phase, `COMPILER_FEEDBACK` capability, and `READ_ONLY` permission, rejects duplicate extension ids, orders providers deterministically, and isolates parser failures with `FAILED_CONTINUED`. Backend-specific providers may use a lower extension order than the built-in generic parser. Providers are discovered through `META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackProvider`.

`OpenClRuntimeSession` queries `CL_PROGRAM_BUILD_LOG` after a successful native program build and attaches the result to `GpuRuntimeCompileArtifactSnapshot`. When compiler diagnostics are enabled, it also captures the selected program binary through `CL_PROGRAM_NUM_DEVICES`, `CL_PROGRAM_DEVICES`, `CL_PROGRAM_BINARY_SIZES`, and `CL_PROGRAM_BINARIES`. `GpuRuntimeCompileArtifactSnapshot` carries backend-neutral `GpuRuntimeBinaryArtifact` values, and artifact dumping writes them without converting the payload to text. Build-log and binary lookups are fail-safe: unavailable queries, empty logs, and unsupported binary formats cannot invalidate an otherwise successful compilation. Failed builds preserve their driver diagnostics through the structured runtime compilation exception path.

CI hardware workloads may enable `OpenClCompilerDiagnosticCapture`, which performs a second isolated build after production compilation. Configuration is supplied by `javatogpu.opencl.compilerDiagnostics`, `javatogpu.opencl.compilerDiagnosticArgs`, `JTG_OPENCL_COMPILER_DIAGNOSTICS`, or `JTG_OPENCL_DIAGNOSTIC_COMPILE_ARGS`. NVIDIA has a built-in verbose option; vendors without a reviewed safe default are recorded as `skipped-no-options`. The diagnostic program binary is preferred, with the production program binary as a fallback. NVIDIA binary inspection may use optional `ptxas`, `cuobjdump`, and `nvdisasm` paths from dedicated properties/environment variables, CUDA installation roots, or `PATH`; PTX is assembled before cubin/fatbin disassembly, and all process failures and timeouts remain diagnostic-only. Diagnostic failures are captured as evidence and must not affect the production program.

`OpenClKernelResourceInfoReader` captures the standard post-build kernel resource contract through independent `clGetKernelWorkGroupInfo` queries. It exports max/preferred work-group values and compiler-reported local/private memory into the compiler feedback payload. Query failures are isolated per field. The OpenCL backend retains the resource snapshot with the compiled kernel and enforces an available `CL_KERNEL_WORK_GROUP_SIZE` limit against explicit local launch sizes before enqueue. Multidimensional limits apply to the product of local dimensions; an unspecified local size remains driver-selected. `OpenClKernelLaunchAdvisory` compares accepted explicit launches with `CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE` and emits a non-blocking per-kernel artifact when runtime dumping is configured. Preferred-multiple mismatches and memory metrics remain advisory because their exact performance interpretation is vendor-dependent.

Artifact dumping always emits `backend-compiler-feedback.properties`. When metrics are available, the artifact records every provider execution and parsed result plus the selected result. Binary capture and inspection metadata is exported under `diagnosticCompilation.binary.*` and `diagnosticCompilation.binaryInspection.*`; a captured payload is written as `opencl-program.bin`. `runtime-ir-analysis.properties` additionally records the heuristic/compiler register delta and comparison status. Compiler feedback remains advisory and cannot satisfy optimizer proof or production-promotion requirements by itself.

Vendor validation may aggregate those artifacts into `OpenClCompilerResourceSummary` and retain a versioned per-kernel snapshot in `OpenClValidationHistoryEntry.compilerResourceStatus`. `OpenClCompilerResourceDrift` compares only compatible backend/device/vendor/lane history entries. Spill or stack growth, metric loss, and missing kernels are regressions; register growth uses an absolute/relative tolerance of `max(2, 10%)`. `OpenClCompilerResourceDriftValidatorCli` is the dedicated CI gate and does not participate in runtime compilation or execution decisions.

### Runtime failure hierarchy

Public runtime failures extend `GpuRuntimeException`. The base contract exposes a stable error code, `GpuRuntimeFailurePhase`, concise summary, immutable `GpuRuntimeDiagnosticContext`, Rust-like `diagnosticText()`, help messages, and the original cause. Backend implementations should translate low-level driver/runtime failures once and must not double-wrap an existing `GpuRuntimeException`.

The OpenCL backend currently maps device selection, fallback variant selection, backend initialization, compile-option validation, argument marshalling/execution configuration, capability validation, kernel compilation, and kernel execution into dedicated public subclasses. `GpuRuntimeDiagnosticContext` records kernel and resource identity, the `IrGpu` GPU-method source anchor, the compiler-indexed Java call site, backend, selected device, compile args, and optimization profile. Its `artifactFields(...)` method provides a properties-compatible representation for future failure artifacts and telemetry.

Generated launchers do not catch these exceptions. They propagate them to the user's call site so application code can implement an explicit fallback:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} catch (GpuRuntimeException exception) {
    CpuFallback.transform(input, output);
}
```

The annotation processor emits deterministic caller indexes under `META-INF/javatogpu/call-sites/`, including the exact invocation expression, source range, caller class/method, and target GPU method. Runtime resolves these records through the generated launcher's artifact classloader and attaches the selected record to `GpuRuntimeDiagnosticContext.callSite()`. If compiler metadata is unavailable, runtime falls back to a stack-trace anchor without hiding the failure. The method-body rewriter does not alter the caller's exception table, so structured failures remain catchable in the original user-authored `try/catch` region.

### Method fallback variants

Use `@GPUFallbackVariant` when one logical operation has multiple device-specific implementations:

```java
@GPU
@GPUFallbackVariant(group = "noise", id = "dgpu", priority = 100)
@GPUDeviceConstraint(deviceClasses = {GpuDeviceClassTarget.DGPU})
static void noiseDiscrete(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = discreteGpuPath();
}

@GPU
@GPUFallbackVariant(group = "noise", id = "igpu", priority = 10)
@GPUDeviceConstraint(deviceClasses = {GpuDeviceClassTarget.IGPU})
static void noiseIntegrated(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = integratedGpuPath();
}
```

The compiler stores group id, variant id, priority, compatibility note, source method, and emitted method identity as `methodFallbackVariant.*` metadata in each `kernel.irgpu.properties` artifact. Generated launchers pass variants from the same annotation-processing run to runtime as immutable fallback descriptors. Alternative OpenCL source is loaded lazily from the generated resource only if that variant is selected.

Fallback methods must expose the same launch ABI: parameter count, Java parameter types, and `GpuKernelParameterAccess` values must match. Parameter names may differ. Runtime rejects an ABI-incompatible variant before backend compilation.

OpenCL evaluates each variant through the normal device policy pipeline. Hard constraints include backend compatibility, explicit device override, `@GPUDeviceConstraint`, and required features. Accepted pairs are ordered by device score, then fallback priority, variant id, resource, and kernel name. This preserves the default dGPU preference instead of allowing a high-priority iGPU fallback to pull work away from a stronger compatible dGPU. Selection diagnostics name the chosen group/variant and explain skipped variants.

When an OpenCL session already exists, fallback planning considers only the active device. It may select another compatible method implementation for that device, but it cannot switch the session to different hardware. If no variant supports the active device, runtime throws `GpuRuntimeMethodVariantSelectionException`; callers must open a new runtime scope/backend to permit another device selection.

Fallback implementations may be distributed across separately compiled modules or libraries. For every owner that declares fallback variants, the annotation processor generates a `GpuRuntimeMethodVariantProvider` implementation and registers it through `META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantProvider`.

`GpuRuntimeMethodVariantRegistry` loads providers with the generated launcher's classloader, validates provider ids and versions, rejects null or malformed registrations, and fails closed when two providers export different descriptors for the same `groupId + variantId`. Registrations are sorted by group, variant id, kernel resource, and kernel name before selection. The runtime then merges them with launcher-local descriptors and validates the canonical `IrGpu` fallback metadata before considering a variant.

Third-party libraries may implement `GpuRuntimeMethodVariantProvider` directly when their kernels are produced outside the normal annotation processor, but each descriptor must point to a runtime-visible kernel source and `IrGpu` resource. Generated providers are the preferred path because they keep descriptor ABI, resource paths, and method metadata synchronized automatically.

Classpath and automatic-module packaging work through the generated service resource. Strict named JPMS modules may require an explicit `provides GpuRuntimeMethodVariantProvider with ...` declaration because Java annotation processors cannot safely modify an existing `module-info.java`.

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

JavaToGpu should expose deliberate SPI hooks rather than requiring downstream forks. Extension points should be phase-specific and permission-aware: read-only validation, diagnostics, optimizer proposals, backend lowering, backend compiler feedback, device policy, runtime-equivalence execution, promotion gates, and artifact writing should not share one unrestricted callback model.

Third-party extensions must be auditable. Runtime/build artifacts should record extension ids, versions, phases, decisions, diagnostics, and whether each extension was advisory or production-affecting.

The shared metadata foundation is available through:

- `GpuExtension` for stable extension identity, version, declared capabilities, and deterministic load order;
- `GpuExtensionCapability` for the public capability taxonomy;
- `GpuExtensionPhase` for the exact pipeline phase where an extension participates;
- `GpuExtensionPermission` for read-only, mutation-proposal, or production-affecting authority;
- `GpuExtensionDescriptor` for validated immutable metadata;
- `GpuExtensionRegistry` for duplicate-id rejection, deterministic audit ordering, and properties-compatible field export.

`GpuRuntimeIrOptimizationPass` declares the `RUNTIME_IR_OPTIMIZATION` phase, `IR_OPTIMIZATION_PROPOSAL` capability, and `MUTATION_PROPOSAL` permission. `GpuIrValidationProvider` declares the `IR_VALIDATION` phase and capability with `READ_ONLY` permission. `GpuBackendCompilerFeedbackProvider` declares the `BACKEND_COMPILER_FEEDBACK` phase and `COMPILER_FEEDBACK` capability with `READ_ONLY` permission. Explicitly supplied pass/provider lists retain caller order where execution order is semantic; ServiceLoader registries sort by extension order, id, and version before execution.

Extension ids must be unique inside a pipeline. Blank metadata, whitespace/control characters in ids or versions, missing capabilities, null entries, duplicate ids, wrong phases, excessive permissions, and missing required capabilities are rejected before execution.

Runtime optimizer extensions may declare `PRODUCTION_AFFECTING`, but production profiles will not execute them without a matching `GpuProductionExtensionAuthorizationDecision`. Authorization requires an accepted production optimizer gate, accepted proof artifact, passed runtime equivalence, clean fallback and rollback state, declared rollback support, a valid `production-enabled` promotion decision with mutation allowed, and explicit operator acceptance.

Authorization is bound to extension id/version, backend target, device vendor and label, optimization profile, and original `IrGpu` identity. A decision produced for one kernel, device, version, or profile cannot authorize another runtime context. Review/non-production profiles may still run the pass to collect candidate IR and evidence. Extension failure isolation and persistence of authorization fields into production artifacts remain separate roadmap work.

OpenCL production source switching applies the same fail-closed principle through `GpuProductionPromotionOperatorAcceptance`. The acceptance is bound to backend target, device vendor and label, driver version, optimization profile, kernel resource, and promotion decision mode. The legacy boolean operator-accepted flag remains readable for compatibility, but the production OpenCL lowerer and runtime source-switching decision require a matching identity-bound acceptance manifest.

`GpuBackendSourcePromotionCandidateGate` is the operational join between real-workload source-parity/runtime-equivalence evidence and controlled identity-bound acceptance. It requires complete per-resource evidence and fails closed when a workload, acceptance, or identity binding is absent. Its `review-ready` status authorizes review of a production candidate only; it does not authorize default source switching or production mutation.

`GpuBackendSourcePromotionManifest` adds a separate human approval boundary. The manifest is valid only when its Git SHA, candidate-artifact SHA-256, backend target, device vendor/label, driver, kernel count, and ordered kernel resources match the reviewed candidate exactly. Approval metadata must be explicit and timestamped. A valid manifest remains `manual-review-only`; runtime code must not interpret it as permission to enable default source switching or production mutation.

`GpuBackendSourcePromotionActivationGate` is an operational readiness join, not an automatically applied runtime permission object. It requires an approved manifest validation, a review-ready candidate, matching device identity, and passed identity-bound controlled source-switching evidence for every candidate kernel. Its only positive status is `controlled-activation-ready`; the artifact must continue to record `defaultRuntimeActivation=false`, disabled default source switching, and disabled production mutation.

`GpuProductionActivationToken` is the explicit controlled runtime opt-in derived from one exact activation-gate artifact and its expected SHA-256. Loading rejects blocked artifacts, digest mismatches, incomplete coverage, missing identity fields, enabled defaults, or enabled production mutation. The OpenCL production lowerer requires both matching identity-bound operator acceptance and a matching activation token for the runtime backend, device vendor/label, driver, activation scope, and kernel resource. Neither object changes the default runtime path.

The production-promotion explainability artifact records controlled activation-token hardware smoke as operational readiness evidence. It exposes whether the token loaded, an approved workload kernel executed, and safe defaults remained intact. This evidence improves auditability but is not itself a production decision and cannot change source-switching or mutation authorization.

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

Backend adapters prepare correctness evidence through `GpuRuntimeDeviceSelfTestRunner` and `GpuRuntimeDeviceSelfTests.prepare(...)`, then `GpuRuntimeDeviceSelfTestPolicy` consumes the shared cache as a normal read-only device policy. This separates native backend execution from core ranking: future CUDA, Vulkan, and Metal adapters can provide their own bounded runner without changing the selection algorithm or artifact format.

OpenCL now enumerates all platforms/devices through Packager before creating a context. It builds profiles with runtime device ids, vendor/driver/version data, device class, compute units, global/local memory, work-group limits, vector width, unified-memory state, and supported feature flags. The registry selects a profile, OpenCL maps that exact profile back to the native device, and only then calls `OpenClContext.create(...)`.

The live OpenCL session retains the complete selection result and compile requests reuse the selected profile. Runtime compile snapshots carry the selection through the backend-neutral `GpuRuntimeCompileArtifactSnapshot` contract. Artifact dumps emit `runtime-device-selection.properties` with selected-device metadata, ranked candidates, policy decisions, capability facts, quirks, compile-option diagnostics, extension execution outcomes, failure policies, blockers, and final diagnostics.

Normalized persistence of all non-device extension phases, automatic multi-session switching, and equivalent CUDA/Vulkan/SPIR-V/Metal discovery remain open work.
