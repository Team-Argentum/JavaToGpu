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

Use `@GPUAttribute` only when no portable annotation exists yet:

```java
@GPUAttribute(backend = GpuBackendTarget.OPENCL, value = "reqd_work_group_size(8, 8, 1)")
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

`@GPUAttribute` is not portable by itself. A backend lowerer may consume it only when the selected backend, vendor, and device class match the annotation selectors; otherwise it should reject or ignore it fail-closed with a diagnostic. Existing `@OpenCLAttributes` and `@OpenCLQualifiers` remain compatibility surfaces for OpenCL-only code, but new backend-specific metadata should prefer `@GPUAttribute`.

## Runtime selection

Runtime device selection should be deterministic by default. The runtime should prefer supported discrete GPUs over integrated GPUs or CPU OpenCL devices, apply known-good validation evidence when available, and still allow explicit user overrides for advanced deployments.

## Optimizer evidence

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
