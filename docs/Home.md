# JavaToGpu Documentation

JavaToGpu lets you write a restricted Java method, mark it as a GPU kernel, and run it through the OpenCL runtime.

This documentation is written for users of the current alpha. It starts with practical setup and examples, then links to deeper reference pages when you need exact compiler/runtime rules.

## What You Can Do Today

- Write `@GPU` Java methods that operate on arrays, scalars, vectors, structs, pointers, images, and samplers.
- Use `GPU.*` builtins for OpenCL-style indexing, math, images, barriers, atomics, and low-level helper operations.
- Run generated kernels through `GpuRuntime.useOpenCl()` or `GpuRuntime.useOpenClSharedCache()`.
- Pass compile options and explicit launch sizes when you need lower-level control.
- Enable optional IR validation for stricter compiler diagnostics and CI reports.
- Smoke-test reconstructed `IrGpu` source through the opt-in review lane without changing the production default path.

## Start Here

1. [Getting Started](Getting-Started.md) - install the dependency, write a first kernel, and run it.
2. [Cookbook](Cookbook.md) - copy small patterns for common tasks.
3. [Runtime Guide](Runtime-Guide.md) - choose runtime scopes, launch sizes, compile options, and review-lane options.
4. [API Overview](API-Overview.md) - learn the public packages and the most-used types.
5. [Known Limitations](Known-Limitations.md) - understand the alpha boundaries before using it seriously.

## Practical Topics

- [OpenCL Data Model](OpenCL-Data-Model.md) - arrays, structs, vectors, pointers, packed blobs, and images.
- [Language Contract](Language-Contract.md) - what Java shapes are supported inside GPU kernels.
- [Troubleshooting](Troubleshooting.md) - common compile/runtime failures and fixes.
- [FAQ](FAQ.md) - short answers to common user questions.

## Advanced Topics

- [IR Validation](IR-Validation.md) - optional strict IR checks and read-only optimizer diagnostics.
- [Diagnostics Reference](Diagnostics-Reference.md) - diagnostic categories and how to interpret them.
- [Validation and Operations](Validation-and-Operations.md) - local validation routines and OpenCL evidence artifacts.
- [ASM Contract](ASM-Contract.md) - bytecode input guidance for advanced integrations.
- [OpenCL Runner Contract](OpenCL-Runner-Contract.md) - self-hosted runner expectations for vendor validation.
- [Device Quirks](Device-Quirks.md) - hardware/runtime-specific notes discovered from validation.

## Current Alpha Position

JavaToGpu is a public alpha / developer preview.

The NVIDIA OpenCL path is the current proven local validation path. Intel and AMD validation are planned, but not yet part of the same confidence baseline. Treat generated code shape and runtime APIs as evolving until beta.

## Maintainer Notes

Planning notes and historical implementation details are kept outside this user manual.
