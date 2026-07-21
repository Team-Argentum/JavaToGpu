# Backend Adapter Authoring

This guide is for library authors who want to add or preview a backend family such as CUDA, Vulkan/SPIR-V, Metal, or a company-specific runtime.

Start small. A backend provider should first be inspectable without opening native drivers, then discover devices, then lower source, and only then expose a production execution pipeline.

## Quick Path

| Stage | What you expose | What must be true |
| --- | --- | --- |
| Discovery-only | `GpuRuntimeBackendProvider` + adapter metadata | Catalogs can list the backend without native sessions. |
| Inventory | Device discovery | Fail-soft diagnostics explain missing drivers/devices. |
| Lowering-only | `GpuBackendLowerer` + module formats | Source/artifact metadata is visible, but execution remains unavailable. |
| Production pipeline | Compiler, preparer, invoker, pipeline factory | Compile/prepare/invoke receipts use the shared backend SPI. |
| Production validation | Lifecycle, artifacts, failures, tests | The backend behaves like OpenCL from the user's point of view. |

Do not jump straight to native execution. Most integration bugs are easier to catch while the backend is still discovery-only or lowering-only.

## Minimal Provider

Register a provider through ServiceLoader:

```text
src/main/resources/META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider
```

The file contains your implementation class:

```text
com.example.gpu.ExampleCudaBackendProvider
```

A discovery-only provider should be lightweight:

```java
package com.example.gpu;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendExecutionSupport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;

public final class ExampleCudaBackendProvider implements GpuRuntimeBackendProvider {
    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public String providerId() {
        return "example.cuda";
    }

    @Override
    public String providerVersion() {
        return "1";
    }

    @Override
    public int providerOrder() {
        return 1_000;
    }

    @Override
    public GpuRuntimeBackendAdapter createAdapter() {
        return new ExampleCudaBackendAdapter();
    }

    @Override
    public GpuRuntimeBackendExecutionSupport executionSupport() {
        return GpuRuntimeBackendExecutionSupport.discoveryOnly(
                backendTarget(),
                providerId(),
                "CUDA discovery is available, but kernel execution is not wired yet"
        );
    }
}
```

Rules:

- Keep `providerId()` stable and unique. Duplicate ids fail during provider loading.
- Do not create CUDA/OpenCL/Vulkan sessions in `createAdapter()` or metadata methods.
- Be honest in `executionSupport()`. If compile/prepare/invoke is not ready, report discovery-only or lowering-only.
- Put backend-specific facts under backend-specific fields, but keep common facts in portable `runtime.*` fields.

## Inspect It

Use the provider catalog to see what applications will see:

```java
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviderCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviders;

String markdown = GpuRuntimeBackendProviderCatalog
        .of(GpuRuntimeBackendProviders.loadWithServiceLoader())
        .toMarkdown();

System.out.println(markdown);
```

The catalog validates ordering, duplicate ids, execution readiness, provider fields, and Markdown output without opening a native runtime.

For the built-in OpenCL provider contract, run:

```powershell
.\gradlew.bat :processor:validateOpenClBackendSpiContract --console=plain
```

That task is metadata-only. It must not open an OpenCL platform, context, program, or kernel.

For the current built-in backend contract bundle, run:

```powershell
.\gradlew.bat :processor:validateBackendAdapterContracts --console=plain
```

This runs the OpenCL SPI contract, backend source/lowering contract, CUDA inventory contract, and CUDA
execution-readiness gate together.

Check only the shared source/lowering contract:

```powershell
.\gradlew.bat :processor:validateBackendSourceLoweringContract --console=plain
```

The expected state is OpenCL lowering successfully to `opencl-c`. CUDA is currently a hardware-free source-preview
lowerer: when a backend-neutral `IrGpu` artifact is present it can emit `cuda-c` for review/dump diagnostics, and when
that artifact is missing it returns a structured `UNSUPPORTED` receipt with `cuda-irgpu-artifact-missing`. Vulkan and
Metal still return structured `UNSUPPORTED` lower-stage receipts with explicit `*-lowerer-not-implemented` blockers. This
keeps future CUDA/PTX/SPIR-V work on the same `GpuBackendSourceSelectionPlan`, `GpuBackendLoweringResult`, and
`GpuBackendModuleArtifact` path without enabling CUDA execution early.

## Add Lowering

When the backend can emit source or binary artifacts, add a `GpuBackendLowerer` and declare formats such as `cuda-c`, `ptx`, `spir-v`, or `opencl-c` through `GpuRuntimeBackendExecutionSupport`.

Use `GpuBackendModuleArtifact` instead of raw strings so source selection, artifact dumps, policy requirements, and diagnostics can reason about the output:

```java
GpuBackendModuleArtifact artifact = GpuBackendModuleArtifact.cudaSource(
        generatedCudaSource,
        "generated/MyKernel.cu",
        "example-cuda-lowerer:1"
);
```

At this stage execution can still be unavailable. Incomplete backends should return structured unsupported receipts instead of ad-hoc exceptions:

```java
provider.unsupportedExecutionResult(loweringResult);
```

That creates a `UNSUPPORTED / SKIPPED / SKIPPED` compile/prepare/invoke result that tools can display consistently.

## Add Execution

Production execution uses the shared compile -> prepare -> invoke runner:

- `GpuBackendCompiledKernel` is the backend-neutral compiled handle.
- `GpuPreparedKernel` is the backend-neutral prepared invocation handle.
- `GpuBackendKernelCompiler` compiles or loads the module artifact.
- `GpuBackendKernelPreparer` binds resources and produces a prepared handle.
- `GpuBackendKernelInvoker` launches the prepared handle and performs readback accounting.
- `GpuBackendExecutionPipelineFactory` creates the runner for a concrete backend instance.

Important rule: the factory must bind to the backend instance's real compiler/preparer/invoker. Do not create a second native session, cache, buffer registry, or hook path inside the factory. OpenCL's factory is the reference pattern: it creates the shared runner from the backend-owned components, so provider-created pipelines preserve the same behavior as production runtime calls.

For diagnostic tools, `GpuBackendExecutionPipeline.executeSafely(...)` converts stage exceptions into typed `FAILED` receipts and marks later stages as `SKIPPED`. Use strict `execute(...)` for production paths that should throw normally.

## Lifecycle And Artifacts

Backend adapters should emit portable fields first:

- `runtime.backend.target`
- `runtime.module.format`
- `runtime.backend.compilation.*`
- `runtime.backend.prepare.*`
- `runtime.backend.invoke.*`
- `runtime.compilation.*`
- `runtime.invocation.binding.*`
- `runtime.failure.*`

Backend-specific aliases can exist for compatibility, but new tools should be able to read the portable `runtime.*` vocabulary without knowing whether the backend is OpenCL, CUDA, Vulkan, or Metal.

The shared `GpuBackendExecutionPipeline` stays quiet by default. If a caller supplies a `GpuRuntimeLifecycleEventBus`,
the runner emits compile, module-load/prepare, and invocation lifecycle events around the same typed stage receipts that
`execute(...)` / `executeSafely(...)` return. CUDA uses this path for its non-production bridge receipts, so journal
consumers can observe `BACKEND_COMPILATION_*`, `MODULE_LOAD_*`, and `INVOCATION_*` without direct listener registration.

Use ServiceLoader services for observability:

- `GpuRuntimeLifecycleService` for lifecycle events and journals.
- `GpuRuntimeLogService` for framework-neutral logging.
- `GpuBackendCompilerFeedbackProvider` for compiler/resource diagnostics.
- `GpuRuntimeDevicePolicy` for device ranking or rejection.
- `GpuBackendHook` family for read-only backend-stage enrichment.

## Hardware-Free Checks

Run the provider authoring example:

```powershell
.\gradlew.bat :examples-app:runBackendProviderAuthoringExample --console=plain
```

It prints the intended progression:

1. Discovery-only provider.
2. Lowering-only provider.
3. Production pipeline provider.

Print the combined backend contract readiness dashboard:

```powershell
.\gradlew.bat :examples-app:runBackendContractReadinessExample --console=plain
```

This combines OpenCL SPI readiness, CUDA inventory readiness, and CUDA execution readiness in one hardware-free output.

Preview the current CUDA source lowering without opening CUDA, NVRTC, `nvcc`, or `nvidia-smi`:

```powershell
.\gradlew.bat :examples-app:runCudaSourcePreviewExample --console=plain
```

The example builds tiny in-memory `IrGpu` artifacts, prints the generated preview `cuda-c` source, and shows the runtime
dump sidecar names for before/after CUDA preview files while keeping CUDA execution disabled.

Run all hardware-free extension examples:

```powershell
.\gradlew.bat :examples-app:runExtensionHarnessExamples --console=plain
```

Check the built-in CUDA inventory contract without running `nvidia-smi` or opening native state:

```powershell
.\gradlew.bat :processor:validateCudaInventoryContract --console=plain
```

The expected CUDA provider state is `status=ready`, `catalogProductionAdapter=false`,
`executionPipelineAvailable=true`, `executionPipelineFactoryPresent=true`, `moduleFormats=cubin,cuda-c,fatbin,ptx`, and
`lowererSelectedSource=cuda-irgpu-source-unavailable` for the sample that has no `IrGpu` payload. CUDA can now lower
simple loaded `IrGpu` entry/helper bodies into preview `cuda-c` source and publish a non-production shared pipeline
skeleton. The compile stage can produce a typed CUDA compile-preview artifact, and the opt-in driver bridge path now has
first slices for PTX/CUBIN/FATBIN module loading, driver-version/PTX metadata receipts, PTX ISA-vs-driver preflight, PTX
target-vs-device preflight, primitive/vector/struct array plus scalar argument binding, `cuLaunchKernel` submission, and
primitive/vector/struct array readback.
Production execution still stays fail-closed until the CUDA execution vertical slice is hardware-validated.

The optional native compiler bridge is deliberately opt-in. Use `GpuRuntimeCompileOptions.cudaNvcc(...)` or set
`cuda.compilerBridge=nvcc` in CUDA backend properties to let the compile stage invoke `nvcc --ptx`, `--cubin`, or `--fatbin` and return a typed
CUDA module artifact. This does not make CUDA production-ready: native module loading, argument binding, launch, and readback
remain separately opt-in, limited, and hardware-validation gated.

The module/function loading boundary is also opt-in. Add `.withCudaDriverModuleLoader()` or set
`cuda.moduleLoader=driver` to enter the built-in CUDA Driver API module loader. This built-in bridge checks whether the
driver library can be loaded, resolves required module-loader symbols, calls `cuInit`, reads `cuDriverGetVersion`, parses
PTX `.version` / `.target`, rejects `cuda-driver-ptx-version-unsupported:*` when a known PTX ISA version needs a newer
CUDA driver API, rejects `cuda-ptx-target-too-new:*` when the selected CUDA device is older than the PTX target, loads
compatible PTX or binary CUBIN/FATBIN payloads with `cuModuleLoadDataEx`, resolves the entry function with `cuModuleGetFunction`, and owns cleanup through
`CudaDriverLoadedModule.close()` / `cuModuleUnload`. PTX compatibility preflight applies only to PTX; CUBIN/FATBIN payloads skip it as backend-native binaries. Typical blockers are `cuda-driver-library-unavailable`,
`cuda-driver-symbol-missing:*`, `cuda-driver-cuDriverGetVersion-failed:*`,
`cuda-driver-ptx-version-unsupported:*`, `cuda-ptx-target-too-new:*`, `cuda-driver-cuModuleLoadDataEx-failed:*`, or
`cuda-driver-cuModuleGetFunction-failed:*`. Successful receipts expose `runtime.cuda.loadedModule.driver.version.*`,
`runtime.cuda.ptxCompatibility.*`, `runtime.cuda.ptxDriverCompatibility.*`, and
`runtime.cuda.ptxDeviceCompatibility.*`. Invoke remains fail-closed unless argument binding, launcher, and execution
config are explicitly requested.

The argument-binding boundary is separately opt-in. Add `.withCudaDriverArgumentBinder()` or set
`cuda.argumentBinder=driver` to enter the built-in driver argument-binding preflight after module loading. It requires a
real driver module/function handle, prepares an empty `CudaKernelArgumentFrame` for zero-argument kernels, receives
shallow-copied Java invocation values through `CudaExecutionPlan`, and returns explicit unsupported blockers for missing
payloads, argument-count mismatch, unsupported buffer shapes, unsupported scalar types/mismatches, or missing `LOCAL`
payloads. The current native slice supports non-empty primitive, GPU vector array, and `@GPUStruct[]` `READ_ONLY` / `READ_WRITE`
arguments by allocating CUDA device memory and supports primitive scalar `VALUE` arguments by storing native-order host
scalar slots in the kernel parameter table. It uploads host buffer values, packs vector arrays using declared storage
width, packs struct arrays with the same primitive/vector/nested-struct field layout as the OpenCL ABI slice, prepares a host-side kernel parameter table, maps primitive array `LOCAL` arguments to one dynamic shared-memory
layout, and frees allocations/native argument slots with the argument frame. One `LOCAL` stays outside the parameter
table; multiple `LOCAL` slices add hidden unsigned byte-offset slots after visible non-`LOCAL` parameters. A successful preflight updates `runtime.backend.prepare.binding.*`,
`runtime.cuda.argumentBinding.*`, `runtime.cuda.argumentFrame.*`, and `runtime.cuda.executionPlan.*`.

The kernel-launch boundary is also separately opt-in. Add `.withCudaDriverKernelLauncher()` or set
`cuda.kernelLauncher=driver` to use the built-in Driver API launcher. It resolves `cuLaunchKernel`, requires a real
`CudaDriverLoadedModule`, a successful native argument binding result, a prepared argument frame, and an explicit
`GpuExecutionConfig`, then computes CUDA grid/block dimensions from the portable global/local work shape and forwards any
prepared dynamic shared-memory byte size to `cuLaunchKernel`. Backend authors should keep this boundary fail-closed:
driver launch requires explicit local sizing, rejects non-divisible global/local shapes, validates block/shared-memory
limits, and records actual `runtime.cuda.kernelLaunch.launchShape.*` fields. A successful launcher updates
`runtime.cuda.kernelLaunch.*` and `runtime.backend.invoke.*`; host output copying remains a separate
readback stage.

The readback boundary is the final staged CUDA execution SPI in this alpha path. Add `.withCudaDriverReadback()` or set
`cuda.readback=driver` to use the built-in Driver API readback bridge after launch. It resolves `cuMemcpyDtoH_v2`, copies
`READ_WRITE` primitive/vector/struct array allocations back into the original Java arrays, marks allocation readback receipts, and updates
`runtime.cuda.readback.*` plus `runtime.backend.invoke.readback.*`. It still stays opt-in and does not turn the built-in
CUDA backend into a production driver backend by itself.
The same staged CUDA binder/readback path supports `GpuMemorySlice.of(array, offset, length)` for contiguous primitive,
vector, and `@GPUStruct[]` subranges. Backend authors should treat slice handling as an explicit memory-view contract:
upload/readback must use the selected host range and artifact fields should expose offset/end-exclusive evidence rather
than silently treating the backing array as a full buffer.

Use the opt-in real-driver smoke gate when you want to validate that staged CUDA execution actually works on a machine:

```powershell
.\gradlew.bat :processor:integrationCudaSmokeTest --console=plain
.\gradlew.bat :processor:integrationCudaPtxSmokeTest --console=plain
.\gradlew.bat :processor:integrationCudaCubinSmokeTest --console=plain
.\gradlew.bat :processor:integrationCudaFatbinSmokeTest --console=plain
```

The gate runs direct `GpuBackendExecutionPipeline.executeSafely(...)` coverage with `nvcc`, PTX/CUBIN/FATBIN module loading, driver
argument binding, `cuLaunchKernel`, and `cuMemcpyDtoH_v2` readback. It covers primitive buffers/scalars, `Float2[]`,
simple `@GPUStruct[]`, `GpuMemorySlice` primitive subranges, multi-`LOCAL` shared-memory offsets, and launch-shape
artifact fields. By default it skips cleanly when CUDA is not installed;
set `JTG_CUDA_SMOKE_REQUIRED=true` for a dedicated CUDA CI lane that must fail on an incomplete staged run.
The default companion summary artifact is `processor/build/reports/cuda/integration-cuda-smoke-summary.properties`; the
explicit PTX/CUBIN/FATBIN lanes write sibling `integration-cuda-*-smoke-summary.properties` files. Adapter CI can key off
`status`, `realDriverExecutionEvidence`, `realDriverExecutionEvidence.rich`, `evidence.*`, `nvcc.outputFormat`, and
`firstBlocker` without scraping Gradle console output. The summary gate rejects a `passed` smoke run unless every executed
test has rich real-driver evidence for module/context handles, launch submission, and readback.
It also records `nvcc.outputFormat`; `ptx`, `cubin`, and `fatbin` are accepted staged CUDA module formats. CUBIN/FATBIN
payloads are carried as binary artifacts into `cuModuleLoadDataEx`, while production CUDA support still requires promotion
policy and broader coverage before promotion.
Treat `cuda-driver-ptx-version-unsupported:*` as an environment/toolchain blocker, not as proof that argument binding or
launch/readback failed. CUDA Toolkit 13.3, for example, emits PTX 9.3, which a driver exposing CUDA Driver API 13.2 cannot
load. For real execution evidence, use a matching/newer driver, an older compatible `nvcc` selected through
`JTG_CUDA_NVCC`, or a staged CUBIN/fatbin smoke lane. On Windows, `cuda-nvcc-host-compiler-missing:cl.exe` means the
CUDA frontend was found but the Visual C++ host compiler is not visible to `nvcc`.

Before enabling CUDA kernel execution, run the metadata-only CUDA green-light gate:

```powershell
.\gradlew.bat :processor:validateCudaExecutionReadiness --console=plain
```

The expected pre-native-execution state is `status=ready`, `cudaPipelineAvailable=true`,
`cudaPipelineFactoryPresent=true`, `productionExecution=false`, and an unsupported receipt of
`compile:SUCCEEDED, prepare:UNSUPPORTED, invoke:SKIPPED`. When the real CUDA native bridge starts, this gate should be
deliberately updated alongside the new execution tests rather than accidentally bypassed.

The report also emits a machine-readable checklist. In the current pre-CUDA-execution state every item should be
`ready`, including:

- `opencl-spi-contract-ready`
- `opencl-shared-runner-visible`
- `cuda-provider-present`
- `cuda-provider-identity-stable`
- `cuda-vertical-slice-skeleton-present`
- `cuda-native-bridge-fail-closed`
- `cuda-module-formats-declared`
- `cuda-capability-vocabulary-declared`
- `cuda-unsupported-receipt-structured`

When CUDA native execution work begins, update this checklist and its tests in the same change that adds the first
native CUDA vertical-slice test. Do not simply remove the fail-closed native-bridge checks.

Use the dedicated harnesses before native execution:

| Need | Harness |
| --- | --- |
| Backend hooks | `GpuBackendHookTestHarness` |
| Hook authorization | `GpuBackendHookAuthorizationValidator` |
| Lifecycle/log services | `GpuRuntimeObservabilityServiceHarness` |
| Device policies | `GpuRuntimeDevicePolicyHarness` |
| Compiler feedback parsers | `GpuBackendCompilerFeedbackHarness` |
| IR validation providers | `GpuIrValidationProviderHarness` |

## Release Checklist

Before a backend can be treated as production-ready, it should satisfy these checks:

- Provider metadata is stable, unique, and visible in `GpuRuntimeBackendProviderCatalog`.
- Discovery fails softly when native drivers are missing.
- Module formats and capability vocabulary are declared honestly.
- Lowering returns `GpuBackendModuleArtifact` values, not untyped strings.
- Unsupported execution returns structured receipts until execution exists.
- CUDA execution readiness is explicit through `validateCudaExecutionReadiness` before CUDA kernels are enabled.
- The pipeline factory uses backend-owned compiler/preparer/invoker state.
- Compile, prepare, invoke, readback, and close stages emit portable receipts.
- Lifecycle and artifact fields use portable `runtime.*` names first.
- Hardware-free examples pass before native validation.
- Native validation has real-device evidence for the target vendor/device class.

## What Not To Do

- Do not fork the OpenCL backend to create CUDA. Implement the shared provider/adapter contracts instead.
- Do not hide unavailable execution behind generic `RuntimeException` failures. Use unsupported/skipped receipts.
- Do not run native discovery during provider catalog inspection.
- Do not let selection policies depend on raw vendor strings when a portable capability exists.
- Do not enable mutating hooks or optimizer-selected IR just because a backend exists. Those gates are separate.

## Related Pages

- [Public API And Extension Contract](Public-API-And-Extension-Contract.md)
- [Runtime Guide](Runtime-Guide.md)
- [Validation and Operations](Validation-and-Operations.md)
- [IR Optimizer](IR-Optimizer.md)
