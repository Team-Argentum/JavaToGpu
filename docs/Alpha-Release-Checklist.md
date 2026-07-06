# Alpha Release Checklist

Use this checklist before publishing a public alpha build.

## Release Label

Recommended first public version:

```text
v0.1.0-alpha.1
```

Recommended positioning:

```text
Public alpha / developer preview for experimental Java-to-OpenCL kernels.
```

Avoid calling the project stable, production-ready, or cross-vendor proven until Intel and AMD OpenCL validation are also green on real hardware.

## Must Be True

- `README.md` clearly says this is an alpha/developer preview.
- `docs/` explains the supported subset, runtime setup, validation flow, and known limitations.
- The normal processor test suite is green.
- The OpenCL operational routine is green on the currently available hardware.
- The latest OpenCL validation report is generated and can be referenced in release notes.
- Known unsupported features are documented instead of implied to work.
- Public docs do not include private planning-only notes as user-facing promises.

## Recommended Validation Command

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

Expected artifact folder:

```text
processor/build/reports/opencl/
```

Key files:

- `validation-report.md`
- `validation-history.md`
- `bucket-status.properties`
- `workload-summary.properties`
- `long-running-summary.properties`

## Release Notes Template

```markdown
# JavaToGpu v0.1.0-alpha.1

This is the first public alpha of JavaToGpu, an experimental Java-to-OpenCL compiler and runtime for GPU-safe Java kernels.

## Validated Environment

- Backend: OpenCL
- Current proven local device: NVIDIA GeForce RTX 5070
- NVIDIA status: operationally proven for repo-local alpha validation through five full operational routine passes
- Cross-vendor status: Intel and AMD validation pending

## Highlights

- Java source kernels through `@GPU`
- OpenCL runtime dispatch through `GpuRuntime`
- Practical `GPU.*` builtin surface
- Struct, vector, pointer, image, sampler, and packed-data support
- Operational validation report bundle

## Known Limitations

- Not arbitrary Java-to-GPU execution
- No stable API guarantee before beta
- CUDA backend not implemented
- Structured ASM frontend expects canonical supported bytecode
```

## Good Alpha Promise

The right promise for this stage is: useful for experiments, compiler integrations, GPU-kernel prototyping, and feedback from early adopters.

The current NVIDIA OpenCL path is operationally proven for repo-local alpha validation, but this is not a cross-vendor production-ready claim.

The wrong promise is: automatic acceleration for arbitrary Java applications.
