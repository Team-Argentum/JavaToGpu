# JavaToGpu

JavaToGpu lets you write a restricted Java method, mark it as a GPU kernel, and run it through an OpenCL runtime.

It is currently a public alpha / developer preview. Use it for experiments, examples, compiler/runtime integration, and early GPU-kernel prototyping. Do not treat the API or generated launcher shape as stable before beta.

## What You Can Do Today

- Write `@GPU` Java kernels over arrays, scalars, vectors, structs, pointers, images, and samplers.
- Use `GPU.*` builtins for OpenCL-style indexing, math, barriers, images, atomics, and low-level helpers.
- Run kernels through `GpuRuntime.useOpenCl()` or `GpuRuntime.useOpenClSharedCache()`.
- Pass explicit launch sizes and OpenCL compile options when needed.
- Add optional IR validation for stricter diagnostics and CI reports.
- Preflight intentionally generated ASM/bytecode artifacts for advanced compiler integrations.

JavaToGpu is not a "run any Java app on the GPU" system. GPU methods must stay inside the supported kernel subset.

## Install

Add JavaToGpu as both a dependency and an annotation processor:

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.2'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.2'
}
```

Optional stricter IR validation:

```groovy
dependencies {
    annotationProcessor 'io.github.deussixik:javatogpu-ir-validation:0.1.0-alpha.2'
}

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=diagnostic'
    options.compilerArgs += '-Ajavatogpu.irValidationDiagnostics=summary'
    options.compilerArgs += '-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties'
}
```

Start with `diagnostic` mode. Move to stricter modes only when you want CI to fail on validation diagnostics.

## First Kernel

```java
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

public final class DemoKernel {
    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void transform(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id] = GPU.sin(input[id]) + 2.0f;
    }
}
```

Run it through the OpenCL runtime:

```java
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;

try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

Use `GpuRuntime.useOpenCl()` for one-off calls. Use `GpuRuntime.useOpenClSharedCache()` for repeated calls so the OpenCL session and compile cache stay warm.

## Important Alpha Limits

- `@GPU` entry methods return `void`; write results to output buffers. For the narrow single-output case, generated launchers can expose `invokeReturningFirst*` helpers that allocate one primitive output array and return `output[0]`; `GpuGeneratedLauncherInvoker.launcher(...)`, typed `invokeReturningFirst*As(...)`, `returnValueConvenience(...)`, and compile-time notes explain whether that helper is available. Use `-Ajavatogpu.returnValueConvenienceDiagnostics=quiet` to suppress those notes.
- General object allocation, virtual dispatch, exceptions, recursion, monitors, and heap object graphs are not supported inside kernels.
- Arrays inside `@GPUStruct` fields are not supported in the current alpha.
- OpenCL is the active backend today. CUDA, Vulkan, and Metal are future directions.
- NVIDIA OpenCL and AMD OpenCL are the current validated baselines; Intel should still be validated on real hardware before broad cross-vendor claims.

See [Known Limitations](docs/Known-Limitations.md) before using JavaToGpu in a larger project.

## Documentation

Start here:

- [Docs Home](docs/Home.md)
- [Getting Started](docs/Getting-Started.md)
- [Cookbook](docs/Cookbook.md)
- [Method Tests](docs/Method-Tests.md)
- [Runtime Guide](docs/Runtime-Guide.md)
- [API Overview](docs/API-Overview.md)
- [Known Limitations](docs/Known-Limitations.md)
- [Troubleshooting](docs/Troubleshooting.md)
- [FAQ](docs/FAQ.md)

Advanced topics:

- [Language Contract](docs/Language-Contract.md)
- [OpenCL Data Model](docs/OpenCL-Data-Model.md)
- [IR Validation](docs/IR-Validation.md)
- [IR Optimizer](docs/IR-Optimizer.md)
- IR Vendor Optimizer is documented in the IR Optimizer guide as a separate optional provider artifact.
- [Validation and Operations](docs/Validation-and-Operations.md)
- [Diagnostics Reference](docs/Diagnostics-Reference.md)
- [ASM Contract](docs/ASM-Contract.md)
- [Device Quirks](docs/Device-Quirks.md)
- [OpenCL Runner Contract](docs/OpenCL-Runner-Contract.md)
- [Publishing Guide](docs/Publishing.md)

Maintainer planning notes live outside the public user manual.

## Build And Validate

Run the normal processor tests:

```powershell
.\gradlew.bat :processor:test --console=plain
```

Run real OpenCL validation on a GPU machine:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

OpenCL reports are written under:

```text
processor/build/reports/opencl/
```

Start with `validation-report.md` when checking a run.

Run the optional IR optimizer journal example:

```powershell
.\gradlew.bat :examples-app:runOptimizationJournalExample --console=plain
```

The example is documented in [examples-app/IR_OPTIMIZER_JOURNAL_EXAMPLE.md](examples-app/IR_OPTIMIZER_JOURNAL_EXAMPLE.md) and shows how to opt into the optimizer module while dumping original and optimized IR artifacts plus the optional ServiceLoader-backed runtime lifecycle event journal for review. It also includes a small custom `GpuRuntimeLifecycleService` example so downstream modules can add tracing or metrics without manual callback registration.

Show backend catalog and selection explanations without running a kernel:

```powershell
.\gradlew.bat :examples-app:runBackendSelectionExample --console=plain
```

This prints the standard backend catalog, `GpuRuntimeBackendProviderCatalog` execution availability, planned
CUDA/Vulkan/Metal diagnostics, and a combined backend/device selection explanation. The execution availability block
makes the current alpha boundary explicit: OpenCL has a shared compile/prepare/invoke runner, while CUDA/Vulkan/Metal are
inventory/planned-only until execution stages land. The multi-backend discovery catalog includes real OpenCL device
evidence plus explicit planned CUDA/Vulkan/Metal discovery states. OpenCL discovery is fail-soft: if OpenCL cannot be
queried on the current machine, the example prints the discovery blocker instead of running a kernel. When discovery
succeeds, it also shows OpenCL platform grouping, selected device ranking, and runtime self-test summary.
Catalog-backed selection explanations also include provider-declared module formats and capability vocabulary, so users
can see why OpenCL is executable today and why CUDA/PTX is currently inventory-only before any native session is opened.
Policies can require that metadata with helpers such as `requireDeclaredModuleFormat(...)`,
`requireDeclaredCapability(...)`, and `requireExecutionPipelineAvailable()` when an application wants to reject
inventory-only or wrong-artifact-family candidates explicitly.
Candidate explanations also include an audit-only backend score (`preference`, `metadataAdjustment`,
`runtimeAdjustment`, `policyAdjustment`, and `total`). Runtime adjustment currently reflects capability-report facts such
as API version, feature flags, local memory, max work-group size, and portable runtime capability count. Default selection
still respects explicit fallback order and hard requirements, but applications can opt into score-based candidate ordering
with `rankCandidatesByScore()` when they want the most ready candidate to win after hard checks pass. Advanced users can
attach explicit read-only workload evidence with `scoreCandidatesWith(...)`; those contributors fill the
`policyAdjustment` bucket and only affect the winner when score-based ranking is enabled. Built-in bridges can also
consume warmed cache-only `@GPUTest` evidence or precomputed compiler feedback through
`scoreCandidatesWithCachedMethodTestProbeEvidence()` and `scoreCandidatesWithCompilerFeedback(report)`. Applications can
describe workload intent with `GpuRuntimeWorkloadHints` and `.scoreCandidatesWithWorkloadHints(hints)` when placement
should consider expected parallelism, memory/arithmetic shape, preferred module formats, or portable capability needs.
For descriptor/IrGpu-driven placement, `.scoreCandidatesWithInferredWorkloadHints(...)` derives conservative hints from
method parameters, image/struct/local-memory usage, global/local/constant address-space usage, required IR features, and visible math density without compiling or
executing any backend candidate.

Preview the provider-authoring path for a future backend without touching native APIs:

```powershell
.\gradlew.bat :examples-app:runBackendProviderAuthoringExample --console=plain
```

This shows the intended progression for a third-party backend provider: discovery-only, lowering-only, then production
pipeline with a shared compile/prepare/invoke runner and structured unsupported receipts for incomplete stages. External
providers use the same ServiceLoader-backed registry path as built-in providers and can be inspected without opening
native runtime sessions. The authoring flow is documented in [Backend Adapter Authoring](docs/Backend-Adapter-Authoring.md).

Print a compact backend contract readiness dashboard without touching native APIs:

```powershell
.\gradlew.bat :examples-app:runBackendContractReadinessExample --console=plain
```

This combines the OpenCL SPI contract, CUDA inventory contract, and CUDA execution-readiness gate in one output. It is
the quickest way to confirm that OpenCL is still the production reference while CUDA remains inventory-only before the
CUDA vertical slice.

Preview read-only backend hooks loaded through ServiceLoader without opening native APIs:

```powershell
.\gradlew.bat :examples-app:runBackendHookServiceLoaderExample --console=plain
```

Preview fail-closed backend hook authorization without enabling mutating hooks:

```powershell
.\gradlew.bat :examples-app:runBackendHookAuthorizationPreviewExample --console=plain
```

Run the same authorization check as a small classpath validator for CI:

```powershell
.\gradlew.bat :examples-app:runBackendHookAuthorizationValidatorExample --console=plain
```

Check IR validation providers against synthetic IR methods without running annotation processing or GPU code:

```powershell
.\gradlew.bat :examples-app:runIrValidationProviderHarnessExample --console=plain
```

Check lifecycle/log ServiceLoader extensions without opening a native runtime:

```powershell
.\gradlew.bat :examples-app:runRuntimeObservabilityServiceHarnessExample --console=plain
```

Check device-selection policies against synthetic CPU/iGPU/dGPU candidates without opening a native runtime:

```powershell
.\gradlew.bat :examples-app:runDevicePolicyHarnessExample --console=plain
```

Check compiler-feedback parsers against synthetic compiler logs without opening a backend compiler:

```powershell
.\gradlew.bat :examples-app:runCompilerFeedbackHarnessExample --console=plain
```

Run every hardware-free extension harness example in one pass:

```powershell
.\gradlew.bat :examples-app:runExtensionHarnessExamples --console=plain
```

Run the full metadata-only backend adapter contract gate without opening native GPU state:

```powershell
.\gradlew.bat :processor:validateBackendAdapterContracts --console=plain
```

This runs the OpenCL SPI contract, backend source/lowering contract, CUDA inventory contract, and CUDA
execution-readiness gate together.

Check only that the built-in OpenCL provider still exposes the expected backend SPI/provider contract without opening OpenCL:

```powershell
.\gradlew.bat :processor:validateOpenClBackendSpiContract --console=plain
```

Check that every built-in backend reports source selection and lowering through the same portable contract:

```powershell
.\gradlew.bat :processor:validateBackendSourceLoweringContract --console=plain
```

OpenCL should lower to `opencl-c`; CUDA should expose the hardware-free preview `IrGpu -> cuda-c` path; Vulkan and
Metal should stay structured `UNSUPPORTED` until their real lowerers exist. The output uses
`runtime.backend.sourceSelection.*` and `runtime.backend.lowering.*` fields so future CUDA/PTX/SPIR-V work does not
invent a second source-selection vocabulary.

Preview the current CUDA source lowering without opening CUDA, NVRTC, `nvcc`, or `nvidia-smi`:

```powershell
.\gradlew.bat :examples-app:runCudaSourcePreviewExample --console=plain
```

The example builds a tiny in-memory `IrGpu` artifact with one helper function and prints the generated `cuda-c` source.

Check the metadata-only CUDA inventory/provider contract:

```powershell
.\gradlew.bat :processor:validateCudaInventoryContract --console=plain
```

This verifies that CUDA is registered through the shared provider-backed adapter path, exposes `cuda-c` / `ptx`
metadata, keeps the catalog entry non-production, and reports the inventory-only lowerer sample as unavailable when no
`IrGpu` payload is present. This is separate from the preview source-lowering path above.

Check the metadata-only CUDA execution green-light gate before starting CUDA kernel execution work:

```powershell
.\gradlew.bat :processor:validateCudaExecutionReadiness --console=plain
```

This verifies that OpenCL remains the production SPI reference, CUDA is visible as an inventory/provider candidate with
`cuda-c` / `ptx` metadata, and CUDA compile/prepare/invoke still returns structured unsupported/skipped receipts until
the real CUDA vertical slice is deliberately enabled. The output also includes machine-readable `checklist.*` lines,
including `cuda-execution-disabled-before-vertical-slice` and `cuda-unsupported-receipt-structured`, so CI can detect
whether CUDA execution was enabled accidentally instead of as part of the planned vertical slice.

This shows example `GpuBackendDiscoveryContributor`, `GpuBackendLoweringHook`, `GpuBackendCompilationHook`,
`GpuBackendInvocationHook`, and `GpuBackendArtifactHook` implementations registered under `META-INF/services`. The hooks
observe discovery/lowering/compile/invoke/artifact receipts, contribute namespaced metadata, and keep production results
unchanged. Extension modules can test the same behavior directly with `GpuBackendHookTestHarness`, which builds synthetic
receipts without requiring OpenCL/CUDA hardware and reports hook contract diagnostics such as authorization-required
non-read-only hooks. Harness reports now include stage-by-stage authorization fields for discovery, lowering,
compilation, invocation, and artifact hooks, including first-blocker summaries for CI output. `GpuBackendHookRegistry.authorizationCatalog(...)`
gives the same fail-closed authorization view across the standard backend hook stages, while `authorizationReport(...)`
targets one stage. `GpuBackendHookAuthorizationValidator.validateReadOnlyClasspath(...)` wraps that catalog into a
pass/fail result with a recommended process exit code, so extension modules can reject unexpected production-affecting
hooks before native runtime startup. Read-only hooks are executable today; stronger hooks can be preview-authorized for
review, but remain `AUTHORIZED_BUT_EXECUTION_DISABLED` until a separate production-affecting runner exists.

Backend authors should use the shared `GpuBackendModuleFormat` and `GpuRuntimeCapability` vocabulary for module formats
and device facts, and declare that vocabulary through `GpuRuntimeBackendExecutionSupport`. This keeps OpenCL-C, CUDA-C,
PTX, SPIR-V, and future backend diagnostics comparable in reports and CI while still leaving CUDA execution disabled
until its compile/prepare/invoke stages exist. The same metadata flows into catalog entries and backend-selection
candidate explanations when a policy is built from the catalog.

Inspect `@GPUTest` metadata and fixture readiness without running a kernel:

```powershell
.\gradlew.bat :examples-app:runMethodTestProbeExample --console=plain
```

This prints the generated `IrGpu` test-vector metadata, selection-probe count, classpath fixture-resource readiness,
fixture byte size/SHA-256 evidence, a top-level JSON payload-shape preview, numeric and `@GPUStruct` value bindings to
descriptor parameters, read-only Java invocation argument materialization, explicit CPU-reference comparison, and first blocker or
failure if a fixture reference is missing, malformed, or mismatched. The example stays preflight-only and does not
allocate GPU buffers or invoke OpenCL. Runtime tooling also exposes an opt-in bounded GPU probe executor for callers
that have installed a backend and want to run the same materialized fixtures through the real runtime path; pass a
separate CPU reference callback because rewritten `@GPU` methods may already route to the GPU launcher instead of
retaining their original CPU body. GPU probe executions expose a stable evidence-key hash so later selection caches can
avoid rerunning unchanged method/device/fixture combinations. `GpuRuntimeMethodTestGpuProbeOptions.cached()` enables the
current process-local cache; `persistentCached(path)` enables an opt-in disk-backed cache with fail-closed
corrupt/mismatch/expiry handling. The method-test pipeline also publishes ServiceLoader-friendly lifecycle events,
including GPU probe cache hit/miss events, so tracing or journal services can observe the preflight and probe flow
without manual listener registration. Device selection can also opt into cache-only method-test probe evidence ranking
with `GpuRuntimeCompileOptions.withPersistentMethodTestProbeEvidenceRanking(path)`: passed cached evidence boosts a
candidate, failed cached evidence rejects it, missing evidence stays neutral, and the optional `maxEntryAge` lowers the
score weight for old-but-still-valid entries before they expire into misses. Runtime compile artifact dumps also write
`runtime-method-test-evidence.properties`; the OpenCL validation report aggregates those artifacts under
`Method Test Evidence` so CI archives show which kernels carried `@GPUTest` metadata and whether cached probe evidence
participated in device ranking.
This runtime path is explicitly `GpuRuntimeMethodTestProbeMode.CACHE_ONLY`: it reads warmed evidence only and never runs
method-test GPU probes during device selection.
Custom `GpuRuntimeDevicePolicy` services can be smoke-tested with `runDevicePolicyHarnessExample`, which feeds synthetic
OpenCL CPU/iGPU/dGPU candidates through the same core policy registry and prints the selected device, policy count,
execution count, and first blocker without touching OpenCL/CUDA.
Backend selection can consume the same warmed cache through the opt-in backend score bridge:
`GpuRuntimeBackendPolicy.builder().rankCandidatesByScore().scoreCandidatesForCompileRequest(request)` plus
`.scoreCandidatesWithCachedMethodTestProbeEvidence()`. This fills the backend `policyAdjustment` score bucket from
cached `@GPUTest` evidence and still never executes probes during selection. When ranking options include `maxEntryAge`,
the backend score diagnostics include freshness and age-limit facts so stale cache entries are explainably down-weighted.
Precomputed compiler feedback can participate in the same score bucket with
`.scoreCandidatesWithCompilerFeedback(report)`. This is advisory resource evidence only: it reads an already-created
feedback report, never compiles candidates during selection, and remains lower priority than method-test correctness
evidence.
Custom `GpuBackendCompilerFeedbackProvider` services can be smoke-tested with `runCompilerFeedbackHarnessExample`, which
feeds synthetic compiler logs through the same registry and prints the selected parser, parsed register/local-memory/
occupancy metrics, and provider execution count without invoking a backend compiler.
Workload intent can participate through `.scoreCandidatesWithWorkloadHints(hints)`. This is also advisory: it lets the
selector explain preferences such as `PTX` output, high arithmetic intensity, large work-groups, or required portable
capabilities without turning those preferences into hard rejection gates.
If the application already has a generated descriptor or loaded `IrGpu` artifact, use
`.scoreCandidatesWithInferredWorkloadHints(descriptor, artifact)` to get the same score path from conservative
method-derived evidence, including address-space requirements from descriptor source and `IrGpu` entry parameters. The inferred bridge is read-only, fail-soft, and still changes the selected backend only when
`rankCandidatesByScore()` is enabled.
Applications that want a single explicit operation can use
`GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelect(...)`: it runs the caller-approved warm-up first, then invokes
normal device selection with cache-only evidence ranking and returns one markdown/artifact-friendly report.

For the same flow with `@GPUStruct[]` fixtures, run:

```powershell
.\gradlew.bat :examples-app:runMethodTestStructProbeExample --console=plain
```

This uses `Vec2[]` JSON object arrays for input and expected output fixtures, materializes Java struct arrays, and
compares flattened field paths such as `[0].x` and `[0].y` in the CPU-reference preflight.

Run the portable cache-only ranking walkthrough:

```powershell
.\gradlew.bat :examples-app:runMethodTestProbeEvidenceRankingExample --console=plain
```

This example uses `GpuRuntimeMethodTestProbeEvidenceWarmup.warmSelectionProbeEvidence(...)` to record one `@GPUTest`
GPU-probe result into a persistent cache through a synthetic reference backend, then reruns backend/device selection
through the higher-level `GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelect(...)` helper. It does not require a
real OpenCL device: the point is to show that warm-up is an explicit opt-in step, while the ranking policy remains
`cache-only`. Passed cached selection
evidence boosts one candidate; missing evidence for another candidate stays neutral. Pass
`-Pjavatogpu.methodTestProbeEvidenceCacheDir=...` to choose the cache folder.

Run the same boundary against real discovered OpenCL devices:

```powershell
.\gradlew.bat :examples-app:runOpenClMethodTestProbeEvidenceSelectionExample --console=plain
```

This example calls `GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelectOpenCl(...)`, which discovers OpenCL,
builds owned warm-up candidates for discovered GPU devices, runs tiny `@GPUTest` selection probes only during the
explicit warm-up phase, and then prints the cache-only selection report. Pass
`-Pjavatogpu.methodTestProbeOpenClEvidenceCacheDir=...` to choose the persistent evidence cache and
`-Pjavatogpu.methodTestProbeOpenClWarmupLimit=1` to cap how many discovered devices are warmed.
The helper also emits service-based lifecycle events for discovery, candidate selection, warm-up, and final placement,
so a `GpuRuntimeLifecycleService` can journal the flow without manual listener wiring.
By default the runnable OpenCL example writes `runtime-lifecycle.jsonl` and `opencl-evidence-selection.trace` next to the
method-test evidence cache, then prints a short `warmAndSelectOpenCl(...)` trace preview. Compact trace lines include
`summary=` when portable runtime-state, compilation, invocation-binding, or artifact-dump fields are available. Override those paths with
`-Pjavatogpu.lifecycleJournalFile=...` and `-Pjavatogpu.exampleLifecycleTraceFile=...` when needed.

For a curated end-to-end OpenCL walkthrough that combines backend/device explanation, portable `@GPUTest` preflight,
real OpenCL evidence warm-up, cache-only placement, launch-shape guidance, vector/struct/packed-root-blob/image workload
smoke, generated return-first launcher convenience, image-helper guidance, optimizer artifact review guidance, and lifecycle
trace output, run:

```powershell
.\gradlew.bat :examples-app:runOpenClPracticalReleaseExample --console=plain
```

Use `-Pjavatogpu.practicalOpenClEvidenceCacheDir=...` to choose the evidence/journal directory. The walkthrough also
shows the common `OpenClImageWorkflow.rgbaIntToFloat2D(...)` host helper, then points to
`runOptimizationJournalExample` and the before/after files to compare: `original.backend.opencl-c`,
`optimized.backend.opencl-c`, selected `backend.opencl-c`, and `runtime-ir-optimizer-evidence.properties`.
Use `"-Pjavatogpu.runtimeLog=system-out"` or provide a `GpuRuntimeLogService` through ServiceLoader to route lifecycle
logs into System.out, Log4J, SLF4J, or another application logging backend without manual listener registration.
The examples app also registers `ExampleRuntimeLogTraceService`, which stays quiet unless
`javatogpu.examples.runtimeLogTraceFile` is set, and can be checked together with lifecycle services through
`runRuntimeObservabilityServiceHarnessExample`.

## Project Layout

- `processor` - annotation processor, compiler, OpenCL emitter, runtime, launchers, tests, and validation buckets.
- `ir-validation` - optional stricter IR validation module.
- `ir-optimizer` - optional backend-neutral IR optimizer skeleton and future transform module.
- `ir-vendor-optimizer` - optional vendor-specific IR optimizer provider skeleton; it plugs into the vendor proposal SPI and is not loaded by the default runtime optimizer bridge.
- `examples-app` - example kernels and usage patterns.
- `test-app` - consumer-style sample application.
- `docs` - public documentation.

## Publishing

Published artifacts:

```text
io.github.deussixik:javatogpu
io.github.deussixik:javatogpu-ir-validation
io.github.deussixik:javatogpu-ir-optimizer
io.github.deussixik:javatogpu-ir-vendor-optimizer
```

Publishing is configured for the main processor artifact, optional IR validation artifact, optional backend-neutral IR optimizer artifact, and optional vendor optimizer provider artifact. Keep Maven Central credentials and signing keys outside the repository. See [Publishing Guide](docs/Publishing.md).

## License

See [LICENSE](LICENSE).
