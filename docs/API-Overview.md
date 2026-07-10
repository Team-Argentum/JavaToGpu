# API Overview

This page gives a practical map of the JavaToGpu API for normal application code.

## Packages You Will Use

- `net.sixik.ga_utils.javatogpu.api` - kernel-facing helpers such as `GPU`, vectors, pointer views, image wrappers, samplers, and unsigned aliases.
- `net.sixik.ga_utils.javatogpu.api.annotations` - annotations for marking kernels, parameters, structs, helpers, intrinsics, attributes, and qualifiers.
- `net.sixik.ga_utils.javatogpu.runtime` - runtime scopes, backend selection, launch configs, descriptors, compile options, and invocation helpers.

## Kernel Annotations

Use these in source code that should compile to GPU code.

- `@GPU` marks a static Java method as a GPU kernel entry point.
- `@GPUGlobal`, `@GPUConstant`, and `@GPULocal` choose the OpenCL address space for array or pointer-like parameters.
- `@GPUWorkGroupSize` declares a portable required work-group size for the kernel.
- `@GPUOptimize` records method-level optimizer policy such as `fastMath`; the default remains strict.
- `@GPUDeviceConstraint` restricts a method to supported backends, vendors, device classes, and required runtime features.
- `@GPUFallbackVariant` groups ABI-compatible implementations that runtime may choose for different devices.
- `@GPUStruct` marks a Java class as a value type that can be marshalled to OpenCL struct layout.
- `@CCode` marks a reusable helper method that should be emitted as GPU helper code.
- `@CCodeLibrary` groups reusable helper methods.
- `@GPUIntrinsic` maps a Java method to a backend intrinsic instead of a normal helper call.
- `@GPUAttribute` is the backend-aware raw escape hatch for metadata JavaToGpu does not model portably yet.
- `@OpenCLAttributes` and `@OpenCLQualifiers` remain OpenCL-only compatibility annotations for existing code.

Prefer portable annotations first. Use raw attributes only for backend-specific code that cannot be expressed through the normal API.

```java
@GPU
@GPUWorkGroupSize(x = 8, y = 8, z = 1)
@GPUOptimize(fastMath = false)
static void kernel(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = 1.0f;
}
```

Use a device constraint when a method requires specific hardware capabilities:

```java
@GPU
@GPUDeviceConstraint(
        vendors = {GpuVendorTarget.NVIDIA, GpuVendorTarget.AMD},
        deviceClasses = {GpuDeviceClassTarget.DGPU},
        requiredFeatures = {"fp64"}
)
static void doubleKernel(@GPUGlobal double[] output) {
    output[GPU.get_global_id(0)] = 1.0;
}
```

If no discovered device satisfies the constraint, runtime selection throws `GpuRuntimeDeviceSelectionException` before kernel compilation.

Use fallback variants when the same logical operation needs different implementations for dGPU and iGPU devices:

```java
@GPU
@GPUFallbackVariant(group = "noise", id = "dgpu", priority = 100)
@GPUDeviceConstraint(deviceClasses = {GpuDeviceClassTarget.DGPU})
static void noiseDiscrete(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = expensiveScalarPath();
}

@GPU
@GPUFallbackVariant(group = "noise", id = "igpu", priority = 10)
@GPUDeviceConstraint(deviceClasses = {GpuDeviceClassTarget.IGPU})
static void noiseIntegrated(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = memoryFriendlyPath();
}
```

All methods in a fallback group must have the same parameter count, Java parameter types, and GPU access modes. Runtime first chooses the best compatible device, then uses variant priority and stable ids as tie-breakers. Explicit device overrides remain mandatory constraints. An active OpenCL scope does not switch to another device; create a new scope/backend when a later call requires different hardware.

Fallback variants may live in another library or Gradle module. The annotation processor generates a `GpuRuntimeMethodVariantProvider` and `META-INF/services` registration for every owner that declares fallback variants. Runtime discovers those providers through the generated launcher's classloader, so a dependency JAR can contribute another implementation without the application importing or calling that implementation directly. Conflicting group/variant ids fail closed.

When using strict named JPMS modules, add an explicit `provides GpuRuntimeMethodVariantProvider with ...` bridge if the generated service resource is not visible through the module layer. Normal classpath and automatic-module usage requires no additional configuration.

## Startup Device Self-Tests

OpenCL can validate discovered devices before final selection. Configure the behavior through `GpuRuntimeCompileOptions`:

```java
GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
        .defaults(GpuBackendTarget.OPENCL)
        .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.REQUIRED);
```

- `AUTO` runs the self-test when multiple devices are discovered.
- `DISABLED` skips self-test execution and cached evidence.
- `REQUIRED` rejects any candidate without passed correctness evidence.

The runtime chooses smaller workloads for iGPU and CPU OpenCL devices. Unified-memory iGPUs use a separate transfer model, and performance rates are compared only with compatible workload and transfer profiles. Compute and transfer timing are scored independently, so noise in one metric does not erase stable evidence from the other. Use these results for deterministic startup ranking, not as a general benchmark.

## Runtime IR Analysis

The built-in runtime pipeline analyzes typed `IrGpu` before optimization. Register-pressure analysis uses scope-qualified variable identities, backward last-use dataflow, branch joins, loop fixed points, and helper call-frame summaries to estimate peak live values separately from total declared storage. Inline helpers may increase caller pressure; non-inline helpers retain separate frames. The analysis is advisory and does not rewrite code or satisfy production optimizer proof requirements.

When an estimate is close to or above the current device-profile budget, `optimizer-report.txt` contains a diagnostic and `runtime-ir-analysis.properties` contains machine-readable per-method fields. Treat the values as conservative planning evidence.

Successful OpenCL program builds query `CL_PROGRAM_BUILD_LOG` and retain any returned diagnostics in the runtime compile snapshot. Artifact dumping produces `backend-compiler-feedback.properties`; common NVIDIA, AMD, and Intel/general resource lines are parsed into separate general/vector/scalar register counts, spill bytes, stack-frame bytes, local-memory bytes, and occupancy. When both values exist, `runtime-ir-analysis.properties` compares the selected compiler register count with the heuristic estimate. Empty driver logs remain valid and simply produce unavailable feedback. This comparison is diagnostic and does not enable an optimization automatically.

Hardware workload validation performs an additional fail-safe diagnostic build. NVIDIA uses `-cl-nv-verbose`; custom or AMD options can be provided with `JTG_OPENCL_DIAGNOSTIC_COMPILE_ARGS`. The diagnostic program is discarded after its log is captured and never replaces the production program.

The runtime additionally uses standard `clGetKernelWorkGroupInfo` queries to capture maximum work-group size, preferred multiple, local memory, and private memory for both NVIDIA and AMD. These values remain available even when the compiler build log is empty. An explicit local launch size is checked against the compiled kernel's maximum before enqueue; multidimensional local sizes are validated by their total product. Unspecified local sizes remain driver-selected. Runtime artifact dumps write `runtime-launch-advisory.properties` with `aligned`, `non-preferred-multiple`, `driver-selected`, or `unavailable` status. Preferred-multiple mismatches are diagnostic only and never reject execution.

Vendor validation aggregates workload-kernel advisory files into `runtime-launch-advisory-summary.md` and the main validation report. GitHub Actions appends that generated Markdown directly instead of reparsing properties in shell code.

The validation history properties retain a compact launch-advisory count summary and a versioned per-kernel snapshot; the Markdown table keeps the short aggregate view.

The reporter compares current counts and kernel snapshots with the latest compatible history entry. Per-kernel matching uses the kernel resource and detects status degradation, reduced kernel maximum, preferred-match loss, blocking activation, and missing resources even when aggregate counts remain equal. Older histories without snapshots use aggregate fallback.

`validateOpenClKernelLaunchAdvisoryDrift` is the CI gate: only `regressed` blocks a valid drift artifact, while changed/no-baseline states remain advisory.

The vendor workflow exposes an opt-in `launch_advisory_negative_fixture` dispatch input. It mutates only the restored validation-history baseline, verifies the real per-kernel validator rejects the synthetic kernel-maximum regression, and prevents cache staging or save for the fixture run.

The vendor workflow persists validation history through a per-lane cache and keeps the restored baseline immutable for the duration of the run, preventing same-run report regeneration from masking cross-run drift.

If you need a backend-specific hint that JavaToGpu does not expose yet, use `@GPUAttribute` and declare the target explicitly:

```java
@GPUAttribute(backend = GpuBackendTarget.OPENCL, value = "vec_type_hint(float4)")
@GPU
static void openClOnlyKernel(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = 1.0f;
}
```

## `GPU.*` Builtins

Use `GPU.*` inside kernels for operations the compiler knows how to lower.

`GPU.*` is intentionally OpenCL-style. Even when CUDA, Vulkan/SPIR-V, or Metal lowerers are added, user code should keep the same `GPU` facade instead of switching to backend-specific Java dialects.

Common groups:

- Work-item indexing: `get_global_id`, `get_local_id`, `get_group_id`, `get_global_size`, `get_local_size`.
- Math: `sin`, `cos`, `tan`, `sqrt`, `pow`, `exp`, `log`, `clamp`, `mix`, `smoothstep`, and related helpers.
- Synchronization: `barrier` and memory fence constants.
- Images and samplers: image reads, writes, and metadata queries.
- Pointer/view bridging: `GPU.global(...)`, `GPU.constant(...)`, `GPU.local(...)`.

Prefer `GPU.*` over ordinary Java library calls inside kernels.

## Data Types

### Scalars

Supported scalar shapes include the common Java primitives used by the current subset: `byte`, `short`, `int`, `long`, `float`, `double`, and `char`.

Unsigned aliases include `UByte`, `UShort`, `UInt`, and `ULong`.

### Vectors

OpenCL-style vector wrapper families include:

- `Float2`, `Float3`, `Float4`
- `Int2`, `Int3`, `Int4`
- `UInt2`, `UInt3`, `UInt4`
- `Double2`, `Double3`, `Double4`

Vectors can be locals, helper parameters/returns, kernel parameters, and buffer element types where supported.

### Structs

Use `@GPUStruct` for small value objects with GPU-compatible fields.

Currently supported field categories:

- primitive scalar fields
- vector fields
- nested `@GPUStruct` fields

Arrays inside struct fields are not supported in the current alpha.

### Pointer Views

Pointer wrappers and address-space views are useful for low-level helpers and packed/blob data.

Examples:

- helper pointer wrappers: `FloatPtr`, `IntPtr`, `DoublePtr`
- address-space views: `GlobalBytePtr`, `GlobalIntPtr`, `GlobalFloatPtr`, `ConstantBytePtr`, `LocalFloatPtr`

Use these only when simple typed arrays are not enough.

### Images And Samplers

Image wrappers model OpenCL image parameters. Typical shapes include read-only images, write-only images, and `Sampler` arguments.

Use image APIs when you need OpenCL image memory, filtering, channel metadata, or pixel read/write operations.

## Runtime API

The usual runtime entry point is `GpuRuntime`.

Common calls:

- `GpuRuntime.useOpenCl()` for a simple scoped OpenCL runtime.
- `GpuRuntime.useOpenClSharedCache()` for repeated calls with a warm session and compile cache.
- `GpuRuntime.use(policy)` for custom fallback policies.
- `GpuRuntime.trySelect(policy)` for prechecking whether a GPU path is available.
- `GpuRuntime.invoke(...)` for descriptor-based direct invocation.
- `GpuExecutionConfig.oneDimensional(...)`, `twoDimensional(...)`, and `threeDimensional(...)` for explicit launch sizes.
- Generated launcher methods for normal `@GPU` calls.
- Automatic method-variant selection for generated launchers that declare `@GPUFallbackVariant`.
- `GpuRuntimeCompileOptions.withDeviceSelfTestMode(...)` for `AUTO`, `DISABLED`, or strict `REQUIRED` startup correctness evidence.
- `GpuRuntimeCompileOptions.openClIrGpuSourceReview(...)` for opt-in reconstructed-`IrGpu` source smoke/review runs without changing the production default source path.

Runtime failures share the public `GpuRuntimeException` base type. Catch a specific subtype when recovery depends on the phase, or catch the base type for a general CPU/backend fallback:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} catch (GpuRuntimeDeviceSelectionException exception) {
    CpuFallback.transform(input, output);
} catch (GpuRuntimeException exception) {
    System.err.println(exception.diagnosticText());
    CpuFallback.transform(input, output);
}
```

Important subtypes include `GpuRuntimeMethodVariantSelectionException`, `GpuRuntimeBackendUnavailableException`, `GpuRuntimeCompileOptionsException`, `GpuRuntimeInvocationException`, `GpuRuntimeCapabilityException`, `GpuRuntimeKernelCompilationException`, and `GpuRuntimeKernelExecutionException`. Every structured exception exposes `code()`, `phase()`, `summary()`, `context()`, `diagnosticText()`, and the original `getCause()`.

Build-time call-site indexes let `diagnosticText()` point at the exact Java expression that invoked a local or dependency-provided `@GPU` method. The same `GpuRuntimeDiagnosticContext` keeps the GPU method location, selected backend/device, compile arguments, and optimization profile. Bytecode rewriting preserves the caller's original `try/catch` region, so the fallback example above remains valid after the annotated method body is replaced with its generated launcher invocation.

When OpenCL discovers multiple devices, the default `AUTO` startup policy runs a small compile/enqueue/readback correctness kernel on each candidate before final selection. Failed devices are rejected; passed evidence is cached by hardware, driver, runtime, runner, and compiler identity. Passed devices also receive bounded compute and host/device transfer measurements. Only trimmed-median samples inside the noise limit affect ranking; noisy measurements remain telemetry. Use `DISABLED` when startup latency matters more than validation, or `REQUIRED` when even a single-device deployment must provide passed evidence.

## Programmatic Compiler API

Use `GpuProgramCompiler` when another compiler or tool wants to call JavaToGpu directly.

Main entry points:

- `GpuProgramCompiler.createDefault()`
- `compileSource(...)`
- `compileStructuredAsm(...)`

Use normal Java source for application kernels. Use the structured ASM path only when you are building tooling that intentionally emits supported bytecode.

## Recommended Reading

- [Getting Started](Getting-Started.md)
- [Cookbook](Cookbook.md)
- [Runtime Guide](Runtime-Guide.md)
- [OpenCL Data Model](OpenCL-Data-Model.md)
- [Known Limitations](Known-Limitations.md)
