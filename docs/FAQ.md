# FAQ

## Can JavaToGpu run arbitrary Java code on the GPU?

No. It compiles a restricted GPU-safe Java subset to OpenCL C. It is closer to a Java-authored kernel DSL than to transparent whole-application acceleration.

## Is JavaToGpu ready for public use?

Yes, as a public alpha / developer preview. It is useful for experimentation, early feedback, compiler integration, and GPU-kernel prototyping. It is not stable or cross-vendor production-ready yet.

## Which backend is supported today?

OpenCL is the active backend.

## Is CUDA supported?

Not yet. CUDA is planned for later.

## Which GPU vendors are validated?

The current local evidence is NVIDIA OpenCL, and the RTX 5070 path is operationally proven for repo-local alpha validation through repeated full operational routine passes. Intel and AMD validation are future promotion gates and should not be implied until tested on real hardware.

## Can `@GPU` methods return values?

Not in the current contract. Use output buffers.

## Can I use normal Java objects inside kernels?

No. Use supported primitives, arrays, vector wrappers, structs, pointer wrappers, images, samplers, and explicit helper patterns.

## Can I put arrays inside `@GPUStruct`?

No. Keep arrays as kernel parameters or model packed layouts with explicit offset schemas.

## Can I feed arbitrary JVM bytecode into the ASM frontend?

No. The ASM frontend expects a canonical supported subset emitted intentionally by tooling you control.

If you are integrating an external bytecode generator, run `GpuProgramCompiler.reportStructuredAsm(...)`, `reportStructuredAsmClass(...)`, or `reportStructuredAsmArtifact(...)` first. It reports unsupported bytecode families and machine-readable `asmFailure.*` metadata without attempting to compile the method. Artifact preflight auto-detects directories, `.class` files, and `.jar` files. In CI, use `writeAndRequireStructuredAsmArtifactReport(...)` so the `.properties` report is saved before the build fails on unsupported bytecode. For broader ingestion triage, `reportStructuredAsmArtifactReadiness(...)` classifies the artifact as `supported`, `rewriteRequired`, or `rejected` and groups migration work into buckets such as `arrayMetadata`, `staticDispatchModel`, `objectModel`, and `typeSignatureModel`. For parser planning, `inventoryStructuredAsmArtifact(...)` exports risky bytecode shape counts such as virtual dispatch, field access, object allocation, array metadata, and exception/synchronization usage. If your CI wants one archived artifact, use `reportStructuredAsmArtifactSnapshot(...)` or `writeStructuredAsmArtifactSnapshot(...)` to combine preflight failures, readiness, and shape inventory under `asmArtifactReport.*` fields.

## How do I handle optional GPU execution?

Use runtime selection policies and `GpuRuntime.trySelect(...)` so unsupported environments can skip or fall back without exception-driven control flow.

## What should I run before publishing results?

Run:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

Then inspect `processor/build/reports/opencl/validation-report.md`.
