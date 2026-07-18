# Public API And Extension Contract

This page explains the parts of JavaToGpu that are meant to be used, configured, or extended by applications and third-party modules.

It is intentionally practical: start with the quick-start tables, copy the small examples, and only use the deeper contracts when you are writing an extension or production gate.

JavaToGpu has one public Java GPU dialect: the `net.sixik.ga_utils.javatogpu.api.GPU` facade. It uses OpenCL-style names today because OpenCL is the first backend, but user code should stay backend-neutral. Future CUDA, Vulkan/SPIR-V, and Metal support should lower the same Java source dialect instead of introducing separate CUDA-style or Vulkan-style Java APIs.

## Quick Start

| I want to... | Use this | Start here |
| --- | --- | --- |
| Print runtime lifecycle logs locally | Built-in `GpuRuntimeLogService` console sink | `-Djavatogpu.runtime.log=system-out` |
| Route logs to Log4J, SLF4J, or another logger | `GpuRuntimeLogService` | Implement a ServiceLoader service |
| Observe compile/runtime stages | `GpuRuntimeLifecycleService` | Implement a read-only lifecycle service |
| Add a company IR validator | `GpuIrValidationProvider` | Emit read-only validation reports |
| Add an optimizer pass | `GpuRuntimeIrOptimizationPass` or `GpuRuntimeIrPeepholeRule` | Produce optimizer evidence first |
| Parse backend compiler resource logs | `GpuBackendCompilerFeedbackProvider` | Keep parsed metrics advisory |
| Influence backend/device selection | `GpuRuntimeDevicePolicy` | Add evidence or hard rejections |
| Add device-specific method variants | `@GPUFallbackVariant` | Keep the Java launch ABI identical |

For normal application code, you usually only need annotations, runtime options, and optional logging. Most SPI hooks are for libraries, tooling, CI, or backend integrations.

## Pick The Right Hook

| Need | Extension point | Permission model | Notes |
| --- | --- | --- | --- |
| Human logs | `GpuRuntimeLogService` | Read-only | Use for Log4J/SLF4J/System.out adapters. |
| Tracing and metrics | `GpuRuntimeLifecycleService` | Read-only | Observes immutable lifecycle events. |
| Extra IR checks | `GpuIrValidationProvider` | Read-only | Should report diagnostics, not mutate IR. |
| IR optimization proposal | `GpuRuntimeIrOptimizationPass` | Mutation proposal | Cannot bypass proof, equivalence, or production gates. |
| One peephole pattern | `GpuRuntimeIrPeepholeRule` | Mutation proposal | Runs inside the built-in peephole host. |
| Compiler log parser | `GpuBackendCompilerFeedbackProvider` | Read-only | Resource metrics are advisory. |
| Device ranking facts | `GpuRuntimeDevicePolicy` | Read-only | Rejections are hard; score changes are evidence. |
| Backend source generation | `GpuBackendLowerer` | Production-affecting | Must stay auditable and fail closed. |

If you are unsure, prefer the most restricted hook. Read-only services are easier to test, safer to ship, and less likely to be blocked by production rules.

## ServiceLoader Pattern

Runtime extensions should be installed as services, not by ad-hoc application callbacks like `Journal.listenerAdd(...)`.

1. Implement the service interface.
2. Add a file under `src/main/resources/META-INF/services/`.
3. Put the implementation class name in that file.
4. Put the module or jar on the runtime classpath.

Example service file for runtime logging:

```text
src/main/resources/META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogService
```

File contents:

```text
com.example.gpu.Log4jGpuRuntimeLogService
```

Every public extension also exposes common metadata through `GpuExtension`: `extensionId()`, `extensionVersion()`, `extensionCapabilities()`, `extensionPhase()`, `extensionPermission()`, and `extensionOrder()`. Use stable ids and versions because artifacts and diagnostics record them.

## Runtime Logging

For local diagnostics, enable the built-in console sink:

```powershell
./gradlew.bat :examples-app:runMethodTestProbeExample --console=plain "-Pjavatogpu.runtimeLog=system-out"
```

For application launches, the runtime property is:

```text
-Djavatogpu.runtime.log=system-out
```

Use `system-err` if you want logs on stderr.

For a real application logger, implement `GpuRuntimeLogService`:

```java
package com.example.gpu;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogRecord;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Log4jGpuRuntimeLogService implements GpuRuntimeLogService {
    private static final Logger LOGGER = LogManager.getLogger("JavaToGpu");

    @Override
    public void log(GpuRuntimeLogRecord record) {
        String message = record.fields().isEmpty()
                ? record.message()
                : record.message() + " " + record.fields();

        switch (record.level()) {
            case TRACE -> LOGGER.trace(message, record.throwable());
            case DEBUG -> LOGGER.debug(message, record.throwable());
            case INFO -> LOGGER.info(message, record.throwable());
            case WARN -> LOGGER.warn(message, record.throwable());
            case ERROR -> LOGGER.error(message, record.throwable());
        }
    }

    @Override
    public String extensionId() {
        return "example.log4j-runtime-log";
    }
}
```

Register it in:

```text
META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogService
```

The core runtime emits immutable `GpuRuntimeLogRecord` values and does not depend on Log4J, SLF4J, java.util.logging, or any concrete logging framework.

## Runtime Lifecycle

Use `GpuRuntimeLifecycleService` when you want a journal, metrics, tracing, or test instrumentation around runtime stages.

```java
package com.example.gpu;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEvent;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleService;

public final class TraceLifecycleService implements GpuRuntimeLifecycleService {
    @Override
    public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
        System.out.println(event.kind() + " " + event.message() + " " + event.fields());
    }

    @Override
    public String extensionId() {
        return "example.lifecycle-trace";
    }
}
```

Register it in:

```text
META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleService
```

Lifecycle services are observers. They should not mutate runtime state, depend on execution order for correctness, or throw exceptions as control flow. The event bus isolates listener failures so diagnostics do not break ordinary runtime execution.

## Backend Metadata

Prefer portable annotations first. They become backend-neutral `IrGpu` metadata, and each backend can lower that metadata into its native representation.

```java
@GPU
@GPUWorkGroupSize(x = 8, y = 8, z = 1)
@GPUWorkGroupSizeHint(x = 8, y = 8, z = 1)
static void kernel(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = 1.0f;
}
```

| Use | When |
| --- | --- |
| `@GPUWorkGroupSize` | Required work-group size. |
| `@GPUWorkGroupSizeHint` | Preferred work-group size. |
| `@GPUVectorTypeHint` | Backend vectorization hint. |
| `@GPUPacked` / `@GPUAligned` | Struct/layout intent. |
| `@GPUAlwaysInline` | Portable inlining intent. |
| `@GPUAttribute` | Last-resort backend-specific escape hatch. |

Use `@GPUAttribute` only when no portable annotation exists yet:

```java
@GPU
@GPUAttribute(backend = GpuBackendTarget.OPENCL, value = "vec_type_hint(float4)")
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

`@OpenCLAttributes` and `@OpenCLQualifiers` remain compatibility surfaces for OpenCL-only expert code. New code should prefer portable annotations or `@GPUAttribute` with explicit selectors.

## Runtime Selection

Runtime device selection is deterministic by default. JavaToGpu prefers supported discrete GPUs, applies validation evidence when available, and allows explicit user overrides for advanced deployments.

Use `@GPUDeviceConstraint` when a kernel is only valid on specific backends, vendors, device classes, or capabilities:

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

Current portable feature names are `fp64`, `images`, and `subgroups`. Unknown required features fail closed.

Use `GpuRuntimeDeviceOverride` when a deployment requires a specific device identity, vendor, label, or class:

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

Available override helpers include `byDeviceId(...)`, `byVendor(...)`, `byDeviceLabel(...)`, and `byDeviceClass(...)`. Constraints and overrides are required filters, not soft hints. If no candidate matches, selection fails with `GpuRuntimeDeviceSelectionException`.

## Device Policies

Use `GpuRuntimeDevicePolicy` when you want to add device-ranking evidence, capability facts, vendor quirks, or compile-option diagnostics.

Device policies do not directly pick the final device. They return a `GpuRuntimeDevicePolicyDecision` with score adjustments, rejected candidate identities, capability facts, quirks, diagnostics, and machine-readable fields. The registry then resolves conflicts deterministically:

- built-in and ServiceLoader policies are ordered by extension order, policy id, and version;
- duplicate ids or invalid metadata are rejected;
- rejection wins over positive score adjustments;
- equal scores are resolved by stable device identity;
- advisory failures are isolated, while production-profile failures stop selection.

This lets future CUDA, Vulkan, Metal, or multi-GPU backends plug into the same policy model without changing user-facing annotations.

## Method Fallback Variants

Use `@GPUFallbackVariant` when one logical operation has multiple implementations for different hardware classes.

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

Fallback methods must expose the same Java launch ABI: same parameter count, Java parameter types, and `GpuKernelParameterAccess` values. Parameter names may differ.

OpenCL evaluates variants through the normal device policy pipeline. Hard constraints still apply: backend compatibility, explicit device override, `@GPUDeviceConstraint`, and required features. Runtime may select another implementation for the active device, but it will not silently switch an already-created runtime session to different hardware.

## Runtime Failures

Public runtime failures extend `GpuRuntimeException`. Generated launchers do not catch these exceptions; they propagate to the user's call site so application code can choose an explicit fallback.

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} catch (GpuRuntimeException exception) {
    CpuFallback.transform(input, output);
}
```

`GpuRuntimeException` carries a stable error code, phase, summary, diagnostic context, help messages, and the original cause. Backend implementations should translate low-level driver/runtime failures once and should not double-wrap an existing `GpuRuntimeException`.

## Optimizer Extensions

Use `@GPUOptimize` to declare optimizer intent at the method level:

```java
@GPU
@GPUOptimize(fastMath = false, enabledFamilies = {"clamp", "step", "mix"})
static void strictKernel(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = 1.0f;
}
```

`@GPUOptimize` records policy hints: enablement, optimization profile, strictness, enabled or disabled families, journal/artifact-dump intent, production intent, vendor adaptation, vectorization preference, and resource-shaping intent.

Important safety rules:

- `fastMath = true` gives permission for proof-backed non-strict rewrites; it does not prove them.
- `productionIntent = true` records intent; it does not bypass production gates.
- Optimizer passes may run as diagnostics without changing production IR.
- Mutating passes need accepted evidence, runtime equivalence, rollback safety, and production authorization before they can affect production code.

Use `GpuRuntimeIrOptimizationPass` for a full staged optimizer pass. Use `GpuRuntimeIrPeepholeRule` for one typed-IR pattern hosted by the built-in peephole pass. Peephole rules currently participate through the same evidence and policy model as larger passes.

## Validation And Feedback

Use `GpuIrValidationProvider` for read-only IR validation. A provider should report `GpuIrValidationReportEntry` values with a stable rule id, severity, source anchor, method identity, and machine-readable fields.

Register providers in:

```text
META-INF/services/net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProvider
```

Use `GpuBackendCompilerFeedbackProvider` to parse backend compiler diagnostics such as registers, spills, stack frame bytes, local memory, or occupancy.

Register providers in:

```text
META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackProvider
```

Compiler feedback is advisory. It can explain performance, resource drift, and CI regressions, but it cannot satisfy optimizer proof or production-promotion requirements by itself. Distinct register files must remain separate; for example, an AMD parser must not merge SGPR and VGPR counts into a fake total.

## Runtime Artifacts

When artifact dumping is enabled, runtime and optimizer stages write properties-style evidence that can be used by CI, reports, and manual review.

| Artifact | Purpose |
| --- | --- |
| `runtime-device-selection.properties` | Selected device, ranked candidates, policy decisions, diagnostics. |
| `runtime-ir-analysis.properties` | Analysis-only IR evidence such as register-pressure estimates. |
| `backend-compiler-feedback.properties` | Parsed compiler feedback and selected resource metrics. |
| `runtime-equivalence.properties` | Pipeline-level reference/pre/post comparison evidence. |
| `optimizer-report.txt` | Human-readable optimizer diagnostics. |
| `runtime-optimizer-family-equivalence-payload.properties` | Per-family optimizer proof payload index. |

Analysis-only passes must be marked as analysis-only and cannot count as accepted optimizer proof. Missing proof payload components should remain explicit `not-recorded` artifacts instead of being hidden.

## Production Safety

Production-affecting extensions fail closed by design. A production optimizer, backend source switch, or lowerer mutation must not become active just because it exists on the classpath.

Before production mutation or source switching, the pipeline needs matching identity-bound evidence:

- accepted optimizer or source-promotion gate;
- accepted proof artifact;
- passed runtime equivalence;
- clean fallback and rollback state;
- declared rollback support;
- production promotion decision with mutation allowed;
- explicit operator or manifest acceptance bound to the exact backend, device, driver, kernel, profile, and artifact digest;
- activation token where controlled runtime activation is required.

Default runtime source switching and production mutation must remain disabled unless a reviewed activation path explicitly enables them for the exact identity being executed.

## Extension Rules

These rules apply to all public extension points:

- Use unique, stable extension ids and versions.
- Declare the correct phase, capability, and permission.
- Keep read-only hooks read-only.
- Prefer deterministic ordering through `extensionOrder()` instead of relying on classpath order.
- Report diagnostics and machine-readable fields when possible.
- Do not use exceptions for normal control flow.
- Fail closed for strict or production profiles.
- Treat advisory failures as isolated diagnostics when the pipeline allows it.

Registries reject blank metadata, whitespace/control characters in ids or versions, missing capabilities, null entries, duplicate ids, wrong phases, excessive permissions, and missing required capabilities before execution.

## Read Next

- `docs/Runtime-Guide.md` - running kernels, runtime scopes, logging, and artifacts.
- `docs/Method-Tests.md` - fixture-based `@GPUTest` checks.
- `docs/IR-Validation.md` - IR validator concepts and reports.
- `docs/IR-Optimizer.md` - optimizer profiles, journals, and dump files.
- `docs/API-Overview.md` - public annotations and facade overview.
