# JavaToGpu Docs

JavaToGpu is an experimental Java-to-OpenCL compiler and runtime for GPU-safe Java kernels.

This documentation is the public user manual for the current alpha. It focuses on what works today, what is intentionally unsupported, and how to validate the runtime on real OpenCL hardware.

## Start Here

- [Getting Started](Getting-Started.md) - install the processor, write a first kernel, and run it.
- [Alpha Release Checklist](Alpha-Release-Checklist.md) - what should be true before publishing an alpha build.
- [Publishing Guide](Publishing.md) - Maven Central credentials, signing, local staging, and release commands.
- [API Overview](API-Overview.md) - public packages, annotations, wrappers, runtime APIs, and compiler entry points.
- [Language Contract](Language-Contract.md) - the supported Java subset and explicit non-goals.
- [Runtime Guide](Runtime-Guide.md) - OpenCL runtime scopes, fallback selection, explicit launch sizes, and ABI debug.
- [Validation and Operations](Validation-and-Operations.md) - local test buckets, operational routine, and evidence artifacts.

## Deeper Topics

- [OpenCL Data Model](OpenCL-Data-Model.md) - structs, vectors, pointer views, constant data, and packed blobs.
- [ASM Contract](ASM-Contract.md) - structured ASM frontend expectations.
- [OpenCL Runner Contract](OpenCL-Runner-Contract.md) - future Intel/NVIDIA/AMD runner requirements.
- [Device Quirks](Device-Quirks.md) - evidence-based vendor/runtime deviations.
- [Diagnostics Reference](Diagnostics-Reference.md) - diagnostic categories and quick fixes.
- [Troubleshooting](Troubleshooting.md) - common compile/runtime failures.
- [Known Limitations](Known-Limitations.md) - current alpha boundaries.
- [Cookbook](Cookbook.md) - small copyable patterns.
- [FAQ](FAQ.md) - short answers for common questions.

## Current Release Position

Recommended public label: `v0.1.0-alpha.1` or equivalent developer preview.

The current evidence set supports an alpha release on NVIDIA OpenCL. It does not yet prove broad production readiness across Intel and AMD OpenCL stacks.

## Maintainer Notes

Roadmaps, planning notes, historical design files, and execution checklists live in `docs-project-plan/`. Treat that folder as maintainer-facing material, not the public user manual.
