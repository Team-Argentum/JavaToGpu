# Runtime Guide

JavaToGpu runtime execution is controlled through `GpuRuntime`.

## Runtime Scopes

### Isolated OpenCL Backend

Use this for simple applications, tests, and one-off calls:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
    DemoKernel.transform(input, output);
}
```

### Shared OpenCL Cache

Use this for hot paths and repeated calls:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
    DemoKernel.transform(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

The shared cache keeps the OpenCL session and compiled kernels warm across calls.

## Runtime Selection

### Strict OpenCL

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
}
```

This fails fast if OpenCL cannot be selected.

### Fallback Policy

```java
GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
        .preferOpenClSharedCache()
        .preferFactory(MyCpuFallbackBackend::new)
        .build();

try (GpuRuntimeScope ignored = GpuRuntime.use(policy)) {
    DemoKernel.transform(input, output);
}
```

### Capability Precheck

```java
GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
        .requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.IMAGES)
        .preferOpenClSharedCache()
        .build();

GpuRuntimeSelectionResult result = GpuRuntime.trySelect(policy);
if (!result.matched()) {
    System.out.println("GPU path skipped: " + result.failureSummary());
    return;
}

try (GpuRuntimeScope ignored = result.install()) {
    DemoKernel.transform(input, output);
}
```

## Explicit Launch Sizes

Generated direct calls are convenient, but lower-level workloads sometimes need explicit launch sizes.

One-dimensional launch:

```java
GpuRuntime.invoke(
        GpuExecutionConfig.oneDimensional(itemCount),
        descriptor,
        input,
        output
);
```

Two-dimensional launch:

```java
GpuRuntime.invoke(
        GpuExecutionConfig.twoDimensional(width, height),
        descriptor,
        input,
        output
);
```

Three-dimensional launch:

```java
GpuRuntime.invoke(
        GpuExecutionConfig.threeDimensional(width, height, depth),
        descriptor,
        input,
        output
);
```

Explicit local sizes are also supported by the matching config factory overloads. After OpenCL compiles the selected kernel, JavaToGpu validates the total explicit local work-group size against that kernel's `CL_KERNEL_WORK_GROUP_SIZE` limit. For multidimensional launches, the validated size is the product of the local dimensions. An oversized explicit group fails before enqueue with `GpuRuntimeCapabilityException`. If no local size is specified, JavaToGpu leaves work-group selection to the OpenCL driver.

## Generated Launcher Helpers

For packed/blob workloads where logical item count does not match raw buffer length:

```java
GpuGeneratedLauncherInvoker.invokeWithGlobalWorkSize(
        OwnerClass.class,
        "kernel",
        itemCount,
        blob,
        view,
        output
);
```

For explicit multidimensional configs, use generated launcher config entry points or `GpuGeneratedLauncherInvoker.invokeWithConfig(...)` where applicable.

## Runtime Compile Options

Compile options are optional and keep the normal kernel arguments unchanged. Use them when you need backend-specific build flags or want to select a future runtime optimization profile explicitly.

```java
GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
        GpuBackendTarget.OPENCL,
        List.of("-cl-fast-relaxed-math"),
        "diagnostic"
);

GpuRuntime.invokeWithCompileOptions(
        GpuExecutionConfig.oneDimensional(itemCount),
        compileOptions,
        descriptor,
        input,
        output
);
```

Generated launchers expose the same path:

```java
OwnerClass_kernel_GpuLauncher.invokeWithConfigAndCompileOptions(
        GpuExecutionConfig.oneDimensional(itemCount),
        compileOptions,
        input,
        output
);
```

Reflection launcher helpers also support compile options for dynamically loaded/generated kernels:

```java
GpuGeneratedLauncherInvoker.invokeWithConfigAndCompileOptions(
        OwnerClass.class,
        "kernel",
        GpuExecutionConfig.oneDimensional(itemCount),
        compileOptions,
        input,
        output
);
```

The default optimization profile is `off`. Keep production code on `off` unless you are collecting diagnostics or testing an opt-in optimizer path.

OpenCL compile options are validated before the runtime touches the device. Supported options include common OpenCL build flags such as `-cl-fast-relaxed-math`, `-cl-mad-enable`, `-cl-opt-disable`, `-cl-std=...`, `-DNAME=VALUE`, and `-Ipath`. Backend-mismatched options fail early with a clear Java exception instead of being ignored by the runtime.

### Startup Device Self-Tests

OpenCL performs a bounded compile, enqueue, and readback correctness smoke before selecting among multiple discovered devices. Passed evidence contributes to deterministic ranking; failed correctness evidence rejects the device before JavaToGpu creates the production OpenCL context.

The default mode is `AUTO`: self-tests run when multiple device candidates are present and are skipped for a single candidate. Override this per invocation when startup latency or strict deployment validation matters:

```java
GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
        .defaults(GpuBackendTarget.OPENCL)
        .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.REQUIRED);
```

- `AUTO` - run self-tests for multi-device discovery and reuse cached evidence.
- `DISABLED` - do not run or apply self-test evidence.
- `REQUIRED` - require passed evidence for every selectable candidate, including single-device systems.

Evidence is cached for the current process. The identity includes backend, device id/label/vendor, device class, unified-memory topology, driver version, runtime API version, self-test runner id/version, and JavaToGpu compiler identity. Changing any of those values invalidates the old entry.

After correctness passes, OpenCL performs one warm-up plus five measured compute and host/device round-trip samples. JavaToGpu discards the minimum and maximum sample and uses the median of the remaining three. The runtime selects a bounded workload profile from the detected device class:

| Device class | Profile | Compute workload | One-way transfer | Noise limit | Compute / transfer score caps |
| --- | --- | ---: | ---: | ---: | ---: |
| dGPU | `dgpu-balanced-v1` | 262,144 items x 128 iterations | 4 MiB | 300 permille | 3,000,000 / 1,000,000 |
| iGPU | `igpu-unified-v1` or `igpu-conservative-v1` | 131,072 items x 64 iterations | 2 MiB | 400 permille | 2,000,000 / 250,000 |
| CPU OpenCL | `cpu-opencl-conservative-v1` | 32,768 items x 32 iterations | 1 MiB | 500 permille | 500,000 / 100,000 |

An iGPU with unified memory uses the `unified-memory-round-trip` transfer model. Dedicated, integrated, unified, and host-memory rates are compared only with results from the same transfer model; compute rates are compared only inside the same workload profile. This prevents a small iGPU workload or shared-memory transfer path from being ranked directly against a dGPU-sized workload.

Compute and transfer stability are evaluated independently. Stable compute evidence may still affect ranking when transfer timing is noisy, and the reverse is also true. A noisy metric remains visible in selection artifacts but receives zero score. These measurements are a short startup ranking smoke, not a general GPU benchmark; real Intel/AMD iGPU validation, register-pressure stress, long-running stability, and persistent-cache policies remain roadmap work.

### Register-Pressure Analysis

The default runtime IR pipeline performs an advisory register-pressure analysis before transformation passes. It reads typed `IrGpu` method bodies and estimates the peak number of simultaneously required value slots from:

- kernel and helper parameters;
- local scalar and vector declarations;
- private arrays;
- nested expression evaluation pressure;
- branches, loops, and switch bodies.

The current `register-pressure-interprocedural:v4` model performs backward last-use analysis. Sequential locals are released after their final reference, unused parameters do not contribute to peak liveness, branch paths are merged at control-flow joins, and loops are iterated to a fixed point so loop-carried values remain live across the back-edge. Structured `break` and `continue` targets are modeled separately.

Typed `IrGpu` references currently store source names, so the analyzer first assigns a stable lexical identity to each parameter, local declaration, and private array. Nested declarations with the same source name remain separate live values. Unresolved references receive isolated conservative identities and add an advisory diagnostic instead of being merged with an unrelated declaration.

Helper calls are analyzed after all method-local estimates are available. A non-inline helper uses a separate frame, so caller and callee pressure are compared rather than blindly added. A helper marked inline may add its non-reusable frame pressure to values live at the call site. Source and emitted helper names are both resolved. Recursive call cycles are reported but are not expanded indefinitely, and unresolved helpers remain advisory edges.

The result uses `LOW`, `MODERATE`, `HIGH`, `CRITICAL`, or `UNAVAILABLE`. Device-class and vendor profiles provide conservative budgets, for example 64 value slots for the initial NVIDIA/AMD dGPU profile and 24 for the initial iGPU profile. These values are planning heuristics, not the physical register count reported by the GPU compiler. Driver compilers may allocate, merge, spill, or eliminate values differently.

High and critical estimates add optimizer diagnostics with the hottest method, estimated value-register count, advisory budget, and suggested reductions such as fewer simultaneously live temporaries, smaller private arrays, narrower vectors, or less unrolling. The analysis never changes the selected IR and never counts as accepted optimizer proof.

Artifact dumps store the complete result in `runtime-ir-analysis.properties`. Per-method fields include total parameter/local/private-array storage, `peakLiveRegisters`, expression-temporary peak, scoped-variable count, shadowed-variable count, unresolved-reference count, budget, utilization, level, and typed-node counts. Per-call fields include resolution state, inline/recursive flags, caller-live values, argument/result pressure, callee pressure, additional frame pressure, and combined estimate.

OpenCL isolated runtime-equivalence checks write raw pipeline comparison cases into `runtime-equivalence.properties` when invocation arguments use supported array shapes. Each case records the comparison mode, original invocation inputs, descriptor-source reference outputs, reconstructed-source candidate outputs, exact tolerance metadata, per-output equivalence flags, and diagnostics. Primitive arrays are written as readable vectors, vector and struct arrays as deterministic packed Base64, scalar values as literals, and opaque image/sampler/runtime objects as stable type tags without process-specific handles. This evidence validates source reconstruction and remains separate from per-family optimizer proof.

Non-analysis optimizer passes with runtime-equivalence payload evidence also produce `runtime-optimizer-family-equivalence-payload.properties` plus durable files under `runtime-optimizer-family-equivalence-payload/family-<index>-<name>/pass-<index>/`. Each pass directory contains `manifest.properties`, CPU/reference, pre-optimization, post-optimization, tolerance, failure-fixture, and diagnostics properties files. When proof fields contain structured case evidence, the component files include deterministic indexed fields for case names, inputs, raw outputs, tolerances, equivalence flags, and diagnostics. Missing components are materialized with `status=not-recorded`; they are never inferred from another pass. Artifact paths are normalized and must remain inside the kernel's `runtime-compile-artifacts` directory. These files are diagnostic evidence only and cannot enable source switching or production mutation.

When the optional IR optimizer bridge participates, each kernel directory may also receive `runtime-ir-optimizer-evidence.properties`. The artifact records only proposal/provider evidence from stable `javatogpu.ir-optimizer` sources: pass counts, proposal-only versus selected optimized counts, rollback counts, IR identities, proof fields, approval-template applicability, and diagnostics. Constant-folding preview proof is also aggregated into `constantFoldingPreview.*` fields for pass count, candidate count, skipped blockers, required runtime-equivalence/approval gates, and unresolved integer-overflow / floating-point-rounding safety flags. Safe-local-CSE preview proof is aggregated into `safeLocalCsePreview.*` fields for pass count, expression/candidate/duplicate/equivalence-class counts, unsupported-operator / impure-operand / control-flow-boundary blockers, required runtime-equivalence/approval gates, and unresolved dominance / side-effect-freedom proof flags. Typed dead-code preview proof is aggregated into `typedDeadCodePreview.*` fields for pass count, typed/reachable/unreachable node counts, missing-root / missing-child-reference / side-effecting-unreachable blockers, required runtime-equivalence/approval gates, and unresolved side-effect-freedom proof flags. The artifact also records `previewReadiness.*` fields with aggregate status, recorded/candidate/blocked family counts, and a per-family status summary so CI can distinguish no-candidate previews from proof-blocked previews and review-ready runtime-equivalence candidates. `runtimeEquivalenceReview.*` records the next fail-closed review boundary: status, eligibility, requirement, first blocker, family summary, disabled production mutation, disabled selected-IR replacement, and manual-review-only state. `reviewPackage.*` records the manual-review package boundary above that review gate, including package status, requirement/completeness, first blocker, proposal-pass count, pending approval count, original/optimized IR and proof-summary requirements, manual-review-only state, and disabled production mutation / selected-IR replacement. `openClValidationReport` aggregates this into `Runtime IR Optimizer Evidence` for CI visibility, including constant-folding, safe-local-CSE, typed dead-code preview totals, preview-readiness status, runtime-equivalence review eligibility, and review-package status. `validateOpenClRuntimeIrOptimizerEvidence` then validates the raw runtime evidence files and fails CI if those fail-closed guardrails are missing, completed early, or production-enabling. The section and validator are evidence-only and do not participate in history, source switching, selected-IR replacement, or production mutation gates.

Runtime peephole/InstCombine remains diagnostic-only. `GpuRuntimeIrPeepholeReplacementPlan` records read-only typed replacement plans for current rule families: `mad/fma`, `clamp`, `step`, `dot`, and `mix`. Built-in rules now use `GpuRuntimeIrTypedNodeGraph` as the shared read-only typed-node traversal surface, and each emitted plan is checked by `GpuRuntimeIrPeepholeReplacementPlanValidation` before proof fields are exported. Proof fields expose the candidate root node, covered nodes, input nodes, completeness, first blocker, and structural validation counters such as `replacementPlan.validation.invalid.count` / `rule.N.replacementPlan.validation.firstBlocker`. `runtime-optimizer-drift.properties` aggregates the complete/partial replacement-plan counts, plan-validation total/valid/invalid counts, first incomplete/invalid blockers, and compact `optimizerRule.*` summaries from existing `rule.N.*` proof fields so CI can detect planning drift while the rewrite engine remains absent. Workload gates, compact workload summaries, OpenCL validation Markdown/history, I3 summaries, and production-promotion explainability surface the same counters as evidence-only telemetry. These plans describe what a future structural rewrite would need to replace; they do not edit typed bodies, select optimized IR, or enable production mutation.

The same index records `familyBinding.*`. Binding remains `not-bound` unless exactly one optimizer family has passed runtime evidence whose comparison mode is explicitly family-specific (`optimizer-family:<name>:...`). Descriptor-versus-reconstructed-source evidence is pipeline validation and is deliberately rejected as optimizer-family proof.

Run `./gradlew :processor:openClOptimizerFamilyPayloadFixtureTest` to materialize the CI contract fixture under `processor/build/reports/opencl/optimizer-family-payload-fixture/`. The fixture contains complete CSE and auto-vectorization payload trees plus `fixture-summary.properties`, including one structured raw case per family. It validates artifact layout and upload behavior, not production rewrite readiness.

After a successful OpenCL program build, the runtime queries `CL_PROGRAM_BUILD_LOG` through the native program and device handles and stores any returned diagnostics in the compile snapshot. JavaToGpu then writes `backend-compiler-feedback.properties`. The built-in parser recognizes common NVIDIA/ptxas register and spill lines, AMD VGPR/SGPR/scratch fields, Intel/general register and spill fields, stack-frame bytes, local/shared-memory bytes, and occupancy percentages. SGPR and VGPR values remain separate; `effectiveRegisterCount` prefers a general register count, then VGPR, then SGPR, and never adds distinct register files together.

If both the heuristic estimate and a compiler register count exist, `runtime-ir-analysis.properties` adds the compiler provider, parsed metrics, compiler count, heuristic count, delta, and comparison status. This is calibration evidence only: it does not rewrite IR, change device selection, or enable production optimization. OpenCL permits an empty successful build log, and many drivers do not expose resource counts by default; a missing or unrecognized log leaves the heuristic analysis unchanged and marks compiler feedback unavailable. Failed build diagnostics remain available through `GpuRuntimeKernelCompilationException`.

The real-device `openClWorkloadValidationTest` also enables an isolated diagnostic rebuild after the production program has compiled. This second build cannot replace or invalidate the production program. NVIDIA devices use `-cl-nv-verbose` by default. AMD and other vendors remain fail-safe until a reviewed diagnostic option is supplied through `JTG_OPENCL_DIAGNOSTIC_COMPILE_ARGS` or the `javatogpu.opencl.compilerDiagnosticArgs` system property.

When compiler diagnostics are enabled, the runtime also queries `CL_PROGRAM_NUM_DEVICES`, `CL_PROGRAM_DEVICES`, `CL_PROGRAM_BINARY_SIZES`, and `CL_PROGRAM_BINARIES` while the selected program is alive. The diagnostic program binary is preferred; if no diagnostic program was built or its binary is unavailable, the successful production program is used as a fallback. The selected payload is written as `opencl-program.bin` in the kernel's `runtime-compile-artifacts` directory. Metadata includes capture status, source program, device count/index, byte size, SHA-256, coarse format (`ptx`, `elf`, `nvidia-fatbin`, `spir-v`, `llvm-bitcode`, `pe-coff`, or `unknown`), artifact name, and a diagnostic. Binary-query failures remain advisory and never invalidate successful execution.

For NVIDIA binaries, JavaToGpu optionally discovers `ptxas`, `cuobjdump`, and `nvdisasm` from explicit properties/environment variables, `CUDA_PATH`/`CUDA_HOME`, the standard Windows `%ProgramFiles%\\NVIDIA GPU Computing Toolkit\\CUDA\\v*\\bin` installations, or `PATH`. Captured PTX is assembled with `ptxas --verbose`; native cubin/fatbin payloads, or a cubin produced from PTX when further inspection is needed, use `cuobjdump` and then `nvdisasm`. Explicit overrides are `javatogpu.opencl.nvidiaPtxasPath`, `javatogpu.opencl.nvidiaCuobjdumpPath`, `javatogpu.opencl.nvidiaNvdisasmPath`, `JTG_OPENCL_PTXAS`, `JTG_OPENCL_CUOBJDUMP`, and `JTG_OPENCL_NVDISASM`. External inspection is isolated behind a timeout and temporary files. Parsed register, spill, and stack text is normalized for the existing compiler-feedback provider. Missing tools, unsupported binary formats, non-zero exits, and timeouts produce diagnostic statuses only.

`backend-compiler-feedback.properties` exposes `diagnosticCompilation.present`, `status`, `source`, `options`, and `diagnostic`, plus `diagnosticCompilation.binary.*` and `diagnosticCompilation.binaryInspection.*`. Expected build-log states are `recorded`, `completed-empty`, `failed`, and `skipped-no-options`. Binary inspection may additionally report `captured`, `tools-unavailable`, `skipped-non-nvidia`, `inspection-failed`, `timed-out`, or another explicit query/tool failure. These states are evidence about diagnostic availability, not kernel execution failures.

JavaToGpu also queries standard kernel resource information after every successful OpenCL kernel creation. This path does not depend on vendor build-log behavior and records maximum work-group size, preferred work-group multiple, compiler-reported local memory, and compiler-reported private memory. Drivers may still report zero or reject individual queries; each metric remains independent. When the kernel maximum is available, it is enforced for explicit local launch sizes. Preferred multiples and memory values remain advisory.

The standard values appear in `backend-compiler-feedback.properties` as `selected.localMemoryBytes` plus raw fields `privateMemoryBytes`, `maxWorkGroupSize`, and `preferredWorkGroupSizeMultiple`. They provide useful spill/private-memory and launch-shape evidence but are not a replacement for exact SGPR/VGPR or NVIDIA register counts.

When runtime artifact dumping is enabled, each kernel directory also receives `runtime-launch-advisory.properties` for the latest accepted launch. Its status is `aligned`, `non-preferred-multiple`, `driver-selected`, or `unavailable`. The artifact records the requested local shape and total size, kernel maximum, preferred multiple, whether a comparison was performed, and whether it matched. `blocking=false` is invariant: a non-preferred multiple remains a performance diagnostic and never rejects the launch. A launch rejected by the hard kernel maximum clears any older advisory so stale accepted-launch evidence is not retained.

`openClValidationReport` aggregates only the kernels listed by the real-workload promotion gate into the `Kernel Launch Advisories` section. It reports status counts and a per-kernel table, and writes the same compact fragment to `runtime-launch-advisory-summary.md` for CI step summaries.

Validation history stores the aggregate counts plus a versioned encoded per-kernel snapshot in `kernelLaunchAdvisoryStatus`. The snapshot retains resource, status, local shape/size, kernel maximum, preferred multiple, match result, and blocking state. The Markdown history table still displays only the compact aggregate summary.

When a compatible previous entry exists for the same backend, device, vendor, and lane, the reporter adds `Kernel Launch Advisory Drift`. It compares counts and matches per-kernel snapshots by resource even across driver versions. Individual status degradation, blocking activation, preferred-match loss, kernel-maximum reduction, or a missing resource is a regression even when aggregate counts are unchanged. Preferred-multiple and launch-shape changes are reported as non-blocking changes. Baselines created before snapshot support continue through aggregate fallback. The CI Markdown includes the previous driver, signed deltas, and a per-kernel change table when snapshots are available.

The same validation history now stores a versioned per-kernel compiler-resource snapshot from `backend-compiler-feedback.properties`. Each entry retains resource identity, availability, provider, inspection tool, effective register count, spill store/load bytes, and stack-frame bytes. The report adds `Compiler Resource Metrics` and `Compiler Resource Drift`, while `runtime-compiler-resource-drift.properties` provides the machine-readable comparison used by CI.

Compiler-resource drift is fail-closed for meaningful regressions without treating small allocation noise as a failure. Losing a previously available metric, losing a kernel, adding or increasing spill bytes, or adding/increasing a stack frame is a regression. Register growth is a regression only when it exceeds `max(2, 10% of the baseline register count)` for that kernel; smaller changes remain visible as `changed`. Register reductions, spill/stack reductions, and newly available metrics are improvements. A history cache created before compiler snapshots reports `no-baseline` for one successful run and is then upgraded through the normal immutable cache staging flow.

The reporter also writes `runtime-launch-advisory-drift.properties`. `validateOpenClKernelLaunchAdvisoryDrift` accepts `stable`, `changed`, `improved`, `no-baseline`, and `unavailable`, but fails on `regressed`, a missing artifact, or an unsupported status. The vendor workflow captures artifacts and then fails the lane when this validator fails.

GitHub Actions restores an immutable per-lane/per-branch validation-history cache before the workload runs. The reporter seeds the current history from that baseline but always computes drift from the separate baseline file, so repeated report generation inside one workflow cannot hide a cross-run regression. After a successful validation and drift gate, the updated history is staged and saved under a unique run key for the next run; regressed or failed runs do not replace the previous baseline.

Manual `workflow_dispatch` runs can enable `launch_advisory_negative_fixture`. This mode requires a baseline containing per-kernel snapshots, increases one kernel maximum only in the restored baseline, and expects the normal drift validator to reject the resulting current-vs-baseline reduction. History staging and cache save are disabled unconditionally in this mode. The workflow succeeds only when fixture preparation succeeds, the drift step fails, and both cache steps remain skipped.

The same dispatch form exposes `validation_lane=all|nvidia|amd`. Selecting one vendor builds the matrix with only that self-hosted runner, so an offline AMD or NVIDIA machine does not leave the unrelated validation run queued.

## Runtime Failures And Fallbacks

All structured runtime failures extend `GpuRuntimeException`. Use the base type when every GPU failure should take the same fallback path:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} catch (GpuRuntimeException exception) {
    System.err.println(exception.diagnosticText());
    CpuFallback.transform(input, output);
}
```

Use a specific subtype when recovery differs by phase:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} catch (GpuRuntimeDeviceSelectionException | GpuRuntimeMethodVariantSelectionException exception) {
    CpuFallback.transform(input, output);
} catch (GpuRuntimeKernelCompilationException exception) {
    reportBrokenKernel(exception.context(), exception.getCause());
    throw exception;
}
```

The public hierarchy includes:

- `GpuRuntimeDeviceSelectionException` - no compatible device or active-session mismatch.
- `GpuRuntimeMethodVariantSelectionException` - no compatible fallback method implementation.
- `GpuRuntimeBackendUnavailableException` - native backend/session initialization failed.
- `GpuRuntimeCompileOptionsException` - backend compile arguments are invalid.
- `GpuRuntimeInvocationException` - Java arguments or execution configuration do not match the kernel ABI.
- `GpuRuntimeCapabilityException` - the selected device lacks a required capability or resource budget.
- `GpuRuntimeKernelCompilationException` - driver/backend kernel build failed.
- `GpuRuntimeKernelExecutionException` - binding, enqueue, synchronization, or readback failed.

Every exception provides:

- `code()` - stable identifier such as `JTG-RUNTIME-COMPILE-001`.
- `phase()` - a `GpuRuntimeFailurePhase` value.
- `summary()` - concise failure description.
- `context()` - kernel, resource, backend, device, compile args, optimization profile, GPU method location, and original Java call site.
- `diagnosticText()` - pre-rendered Rust-like diagnostic suitable for logs.
- `getCause()` - the original backend/driver failure when one exists.

The annotation processor records calls to local and dependency-provided `@GPU` methods in `META-INF/javatogpu/call-sites/<binary-class-name>.properties`. At runtime, JavaToGpu matches the active caller stack frame against this index and prefers the exact original Java invocation expression in `diagnosticText()`. The `IrGpu` method source location remains available separately in `context().sourceLocation()`, while `context().callSite()` exposes the caller class/method, file, range, expression, target method, and whether the anchor came from the compiler index or stack-trace fallback.

Method-body rewriting preserves the caller's original exception table. A rewritten GPU call remains inside the same user-authored `try/catch` region, so `catch (GpuRuntimeException exception)` can reliably invoke a CPU, device, method-variant, or backend fallback.

### IrGpu Source Review Lane

`IrGpu` is the backend-neutral artifact that JavaToGpu is moving toward as the runtime source of truth. The normal runtime path still compiles the generated descriptor OpenCL source by default, even when an `IrGpu` artifact is packaged beside it.

Use the explicit review preset when you want to smoke-test reconstructed `IrGpu -> OpenCL` source without enabling production source switching:

```java
GpuRuntimeCompileOptions compileOptions =
        GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of());

GpuRuntime.invokeWithCompileOptions(
        GpuExecutionConfig.oneDimensional(itemCount),
        compileOptions,
        descriptor,
        input,
        output
);
```

The preset enables the reconstructed-source review mode. Production runs keep using the normal generated OpenCL source unless you explicitly opt into future source-switching features.

### Production Source Acceptance

Production `IrGpu -> OpenCL` source switching requires more than `opencl.productionSourceSwitching=enabled` and a `production-enabled` promotion decision. The operator acceptance must be identity-bound to the exact backend, device vendor and label, driver version, optimization profile, kernel resource, and decision mode.

```java
GpuRuntimeCompileOptions compileOptions =
        GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                .withProductionPromotionDecision(promotionDecision);

GpuProductionPromotionOperatorAcceptance acceptance =
        GpuProductionPromotionOperatorAcceptance.forContext(
                "acceptance:rtx5070-reviewed-2026-07-10",
                GpuBackendTarget.OPENCL,
                reviewedDeviceProfile,
                compileOptions.optimizationProfile(),
                descriptor,
                promotionDecision.mode()
        );

compileOptions = compileOptions.withProductionPromotionOperatorAcceptance(acceptance);
```

The legacy `withProductionPromotionOperatorAccepted(true)` flag is retained for compatibility and diagnostics, but it does not authorize the OpenCL production source path without the matching acceptance fields. Acceptance alone is also insufficient: the controlled runtime path requires a matching `GpuProductionActivationToken`. A device, driver, profile, kernel, backend, decision-mode, activation-scope, or token mismatch fails closed.

Hardware validation joins the real-workload promotion evidence with controlled identity-bound acceptance in `backend-source-promotion-candidate-gate.properties`. The candidate gate is `review-ready` only when every workload kernel is review-ready, source-parity matched, runtime-equivalent, present in the controlled source-switching run, operator-accepted, and bound to the recorded device identity.

This artifact is a review boundary, not a runtime enablement switch. A review-ready candidate still records `defaultProductionSourceSwitching=disabled` and `productionMutation=disabled`; normal application execution continues to use the default generated OpenCL source.

### Manual Promotion Manifest

After reviewing a candidate artifact, generate a pending manifest template bound to its exact bytes, Git commit, device identity, driver, and kernel resources:

```powershell
.\gradlew.bat :processor:writeOpenClBackendSourcePromotionManifestTemplate `
  -PopenClPromotionGitSha=<full-git-sha> `
  --console=plain --no-daemon
```

The template is written to `processor/build/reports/opencl/backend-source-promotion-manifest-template.properties`. Change `status` to `approved` and fill `approval.id`, `approval.approvedBy`, and `approval.approvedAtUtc`; do not alter any `binding.*` or `authorization.*` fields.

Validate the reviewed manifest with:

```powershell
.\gradlew.bat :processor:validateOpenClBackendSourcePromotionManifest `
  -PopenClPromotionManifestFile=<manifest-path> `
  -PopenClPromotionGitSha=<candidate-run-full-git-sha> `
  --console=plain --no-daemon
```

The candidate Git SHA is the `binding.gitSha` written by the template run, not the later commit that adds the approved manifest to the repository. Validation writes `backend-source-promotion-manifest-validation.properties` before failing closed on any mismatch. The candidate-artifact SHA-256 prevents the later validation commit from changing source-promotion evidence while retaining the original reviewed SHA binding.

An approved manifest remains device- and driver-specific and records `manual-review-only`, `defaultProductionSourceSwitching=disabled`, and `productionMutation=disabled`. It is auditable approval evidence for a later activation design, not runtime authorization by itself.

### Controlled Activation Gate

The next operational boundary combines the approved manifest validation, the production candidate gate, and controlled identity-bound source-switching evidence:

```powershell
.\gradlew.bat :processor:validateOpenClBackendSourcePromotionActivationGate `
  -PopenClPromotionManifestFile=<manifest-path> `
  -PopenClPromotionGitSha=<candidate-run-full-git-sha> `
  --console=plain --no-daemon
```

The task writes `backend-source-promotion-activation-gate.properties` and `backend-source-promotion-activation-gate.properties.sha256`. A successful result is `controlled-activation-ready` with full kernel coverage and accepted/bound operator evidence. It explicitly records `activationScope=controlled-opt-in-only`, `defaultRuntimeActivation=false`, `defaultProductionSourceSwitching=disabled`, and `productionMutation=disabled`.

This gate does not modify `GpuRuntimeCompileOptions`, does not enable the default source path, and is not consumed automatically by application runtime code. A controlled caller must load the exact artifact and expected digest, then attach the resulting token alongside the identity-bound operator acceptance:

```java
Path activationArtifact = Path.of(
        "processor/build/reports/opencl/backend-source-promotion-activation-gate.properties"
);
String expectedSha256 = Files.readString(
        activationArtifact.resolveSibling(activationArtifact.getFileName() + ".sha256")
).trim();

GpuProductionActivationToken activationToken =
        GpuProductionActivationToken.fromArtifact(activationArtifact, expectedSha256);

compileOptions = compileOptions
        .withProductionPromotionOperatorAcceptance(acceptance)
        .withProductionActivationToken(activationToken);
```

The OpenCL production lowerer validates the token against the runtime backend, device vendor/label, driver, activation scope, and kernel resource. Any mismatch rejects the production source path while normal application execution remains on the default generated OpenCL source.

Run the end-to-end hardware check with the same manifest and candidate SHA used for activation:

```powershell
.\gradlew.bat :processor:openClProductionActivationTokenSmokeTest `
  -PopenClPromotionManifestFile=<manifest-path> `
  -PopenClPromotionGitSha=<candidate-run-full-git-sha> `
  --console=plain --no-daemon
```

The task depends on the activation gate, loads its exact artifact and sidecar, and executes every approved real workload kernel: Perlin, packed blob, packed numeric, synthetic 3D packed grid, and image. It writes per-kernel status to `production-activation-token-smoke.properties` and still records all default runtime and production mutation switches as disabled.

Run the hardware negative controls against the same activation artifact:

```powershell
.\gradlew.bat :processor:openClProductionActivationTokenNegativeTest `
  -PopenClPromotionManifestFile=<manifest-path> `
  -PopenClPromotionGitSha=<candidate-run-full-git-sha> `
  --console=plain --no-daemon
```

This task verifies that `GpuProductionActivationToken.fromArtifact(...)` rejects a mismatched SHA-256 and that a valid token rejects an unapproved kernel resource before GPU output changes. It writes `production-activation-token-negative.properties` with the rejection and safe-default states.

The OpenCL validation reporter includes both activation-token artifacts in `production-promotion-explainability.properties` and its compact CI summary. Separate readiness items require full real-workload coverage and successful negative controls. Neither item sets `productionSourceSwitchingAllowed`, enables the default source path, or authorizes production mutation.

## ABI Debug

Enable ABI diagnostics with:

```java
System.setProperty("javatogpu.opencl.debugAbi", "true");
```

Use ABI debug when diagnosing struct layout, vector array, image, or readback issues.

## Related Documents

- [Validation and Operations](Validation-and-Operations.md)
- [OpenCL Data Model](OpenCL-Data-Model.md)
- [Troubleshooting](Troubleshooting.md)
