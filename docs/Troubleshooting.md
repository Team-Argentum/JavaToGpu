# Troubleshooting

Use this page when a kernel does not compile, does not launch, or behaves differently than expected.

## First Checks

- Make sure the method is `static` and marked with `@GPU`.
- Keep the `@GPU` method return type as `void` and write results into output buffers.
- Add explicit parameter annotations such as `@GPUGlobal`, `@GPUConstant`, or `@GPULocal` for array-like inputs.
- Replace ordinary Java library calls inside kernels with supported `GPU.*` builtins.
- Confirm your OpenCL driver is installed and visible on the machine running the test.

## Unknown `@CCode` Helper

This usually means the compiler found a helper call but could not match it to a known GPU helper.

Try this:

- Put the helper in the same compilation input set as the kernel.
- Check the helper owner class and method name.
- Check the parameter and return types exactly match the call site.
- Add `@CCode` to reusable GPU helper methods.

## Unsupported Parameter Type

This usually means the host method signature is too Java-like for the GPU boundary.

Try this:

- Add `@GPUGlobal`, `@GPUConstant`, or `@GPULocal` to array parameters.
- Use supported vector wrappers for vector data.
- Use `@GPUStruct` for small value objects.
- Use pointer views only when you really need packed or low-level buffer access.

## Unsupported Struct Field

This usually means a struct contains a field the current ABI cannot safely marshal.

Try this:

- Use primitive fields, vector fields, or nested `@GPUStruct` fields.
- Move arrays out of structs and pass them as separate kernel parameters.
- Keep structs small and explicit.

## OpenCL Build Failure

If JavaToGpu generated source but OpenCL rejected it, inspect the generated OpenCL source and the validation report.

Good next steps:

- Enable ABI debug with `javatogpu.opencl.debugAbi=true`.
- Check whether the kernel needs capabilities such as double precision or images.
- Remove custom compile flags and retry with defaults.
- Compare the error with [Device Quirks](Device-Quirks.md).

## Runtime Selection Failure

If OpenCL cannot be selected, your application can fall back cleanly instead of failing hard.

Use `GpuRuntime.trySelect(...)`:

```java
GpuRuntimeSelectionResult result = GpuRuntime.trySelect(policy);
if (!result.matched()) {
    cpuFallback(input, output);
    return;
}

try (GpuRuntimeScope ignored = result.install()) {
    DemoKernel.transform(input, output);
}
```

## Wrong Output Values

Start with correctness before performance:

- Compare against a CPU reference implementation.
- Test tiny input sizes first, such as 1, 2, 3, and 17 elements.
- Check global work size versus buffer length.
- Avoid reading or writing outside output buffers.
- Avoid fast-math compile flags until the default path is correct.

## Vendor-Specific Issue

If the same kernel works on one GPU stack and fails on another, capture the validation report and driver/device details. Treat it as a possible device quirk only after the kernel also passes the basic checks above.

## Reports To Keep

When debugging CI or a real GPU machine, keep these files if they exist:

```text
processor/build/reports/opencl/validation-report.md
processor/build/reports/opencl/validation-history.md
processor/build/reports/opencl/backend-source-promotion-gate.properties
processor/build/test-results/
```
