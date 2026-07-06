# JavaToGpu Master Roadmap

This is the single working checklist for moving JavaToGpu forward.

It merges:

- production-readiness work
- TODO/backlog feature work
- low-level OpenCL parity gap work

Older files remain useful as detailed references, but this file is the primary execution plan.

## A. Production Core

### A1. Cross-device validation

- [ ] Run the existing validation suite repeatedly on real Intel OpenCL hardware.
  Deferred until Intel OpenCL hardware is available locally or through a stable runner.
- [x] Run the existing validation suite repeatedly on real NVIDIA OpenCL hardware.
  Current recorded pass on `NVIDIA CUDA / NVIDIA GeForce RTX 5070`, driver `595.97`, platform `OpenCL 3.0 CUDA 13.2.73`: five full `:processor:openClOperationalRoutine` evidence runs are green, including four explicit `--rerun-tasks` runs with vendor validation, integration smoke, workload-equivalence, long-running stability, benchmark, bucket-status, and validation-history artifacts all passing. Latest run completed at `2026-07-06T08:26:18Z`.
- [ ] Run the existing validation suite repeatedly on real AMD OpenCL hardware.
  Deferred until AMD OpenCL hardware is available locally or through a stable runner.
- [x] Establish NVIDIA-only interim operational validation mode.
  Until Intel/AMD cards are available, A1/A2 work should use repeated NVIDIA `:processor:openClOperationalRoutine` runs as the active production-confidence signal while keeping Intel/AMD as future cross-vendor promotion gates.
- [x] Record vendor quirks/regressions in a maintained device-quirks document.
  `docs/Device-Quirks.md` now records the current NVIDIA RTX 5070 clean baseline, the latest full operational evidence run, workload and long-running stability details, and explicit Intel/AMD `pending-hardware` gates for future cross-vendor promotion.
- [x] Promote vendor validation from "repo-local ready" to "operationally proven".
  The NVIDIA interim validation path is now treated as operationally proven for repo-local alpha confidence: five full RTX 5070 operational evidence runs are green, including four explicit `--rerun-tasks` passes. This does not close the Intel/AMD cross-vendor gates, which remain pending hardware.
- [x] Make validation bucket reporting failure-aware.
  Each Gradle validation bucket now records top-level suite status as `passed` / `failed` / `skipped` and finalizes with `:processor:openClValidationReport`, so failed bucket runs still leave a bucket-status matrix and Markdown report artifact for operational triage.
- [x] Harden vendor-matrix artifact capture before lane failure.
  The GitHub Actions vendor matrix now runs validation buckets in evidence-first mode, uploads the report bundle, and only then fails the lane if any validation/workload/stress bucket did not succeed.

### A2. Performance and stability validation

- [x] Add repo-local benchmark and stress test buckets.
- [x] Add runtime statistics for cold/warm cache behavior.
- [x] Add repeated lifecycle / repeated marshalling regression coverage.
- [x] Lock runtime-equivalence and stress artifact visibility in validation history.
  The report regression suite now verifies that workload-equivalence, long-running stability, and benchmark bucket markers stay visible in generated validation history artifacts, so operational runs keep enough evidence for A1/A2 triage.
- [x] Collect real hardware benchmark baselines on target devices.
  Latest post-I2 baseline captured on `NVIDIA CUDA / NVIDIA GeForce RTX 5070`, driver `595.97`, including workload-equivalence, long-running stability, benchmark, bucket-status, and validation-history summaries. See `docs-project-plan/nvidia-rtx5070-baselines.md`.
- [x] Re-run stress/benchmark/long-running validation buckets periodically against target vendor stacks.
  Current periodic target is the available NVIDIA stack; the latest full `:processor:openClOperationalRoutine --rerun-tasks` pass refreshed benchmark, stress, long-running, workload-equivalence, bucket-status, and validation-history evidence on `2026-07-06T08:26:18Z`. Intel/AMD repeats remain pending hardware.

### A3. Failure strategy and diagnostics

- [x] Capability prechecks for doubles, images, local memory, work-group limits.
- [x] Backend fallback / selection policy.
- [x] Non-throwing runtime selection path.
- [x] Compiler/runtime diagnostic quality pass.
- [x] Add another targeted runtime diagnostic slice for unsupported buffer upload/readback payloads.
  Runtime errors for unsupported OpenCL upload/readback array payloads now include concrete quick-fix hints for `@GPUStruct[]`, vector wrapper arrays like `Float2[]` / `UInt8[]`, and the diagnostics guide entry.
- [ ] Keep adding targeted diagnostics when new real failures appear.

### A4. ABI and runtime resource stability

- [x] Repeated lifecycle coverage for backend create/invoke/close flows.
- [x] Cache cleanup coverage.
- [x] Repeated marshalling coverage for structs, vectors, and images.
- [x] Add an additional mixed real-device repeated invoke/marshalling smoke slice.
  Repo-local integration coverage now also exercises repeated scalar-buffer and struct-array kernel invocations through the real OpenCL backend in one warm session, complementing the existing mock/stress buckets for lifecycle and marshalling reuse.
- [x] Add an additional repeated real-device image invoke/readback stability slice.
  Repo-local integration coverage now also exercises repeated image+sampler kernel invocations and output-image readback inside one warm OpenCL backend session, strengthening the runtime-resource regression net for image workflows.
- [x] Add an additional repeated real-device vector-array stability slice.
  Repo-local integration coverage now also exercises repeated vector-array kernel invocations and vector readback through one warm OpenCL backend session, complementing the scalar/struct/image repeated-path coverage.
- [x] Add deeper long-running real-device validation for ABI/resource stability.
  Repo-local coverage now includes a dedicated `:processor:openClLongRunningStabilityTest` bucket that runs a longer mixed scalar/struct/vector/image warm-session reuse scenario on a real OpenCL device with compile-cache and invocation-count assertions.

### A5. Production docs

- [x] Supported subset contract.
- [x] ASM contract.
- [x] Runtime configuration docs.
- [x] Troubleshooting/diagnostics docs.
- [x] Keep docs aligned as API/runtime behavior changes.
  Public docs now reflect the current alpha/release posture: NVIDIA RTX 5070 is operationally proven for repo-local validation, Intel/AMD remain future cross-vendor gates, and Maven publishing covers both `javatogpu` and the optional `javatogpu-ir-validation` artifact with matching release-readiness checks.

## B. Current Language/Core Coverage

### B1. Already done baseline

- [x] Java -> OpenCL source pipeline.
- [x] `@GPU` entry methods.
- [x] `@CCode`, `@CCode(code = "...")`, inline helpers.
- [x] `@GPUIntrinsic`, `@GPUIntrinsicLibrary`.
- [x] Compound assignments, `++`, `--`.
- [x] `if / else`, `for`, `while`, `do-while`, `switch`.
- [x] Pointer wrappers for primitive scalar pointees.
- [x] OpenCL address spaces: `@GPUGlobal`, `@GPUConstant`, `@GPULocal`.
- [x] `@GPUStruct`, nested structs, OpenCL attributes.
- [x] Vector value types and vector parameter/array support.
- [x] Struct parameter and struct array support.
- [x] ASM frontend/public compiler facade.

### B2. Remaining general language/runtime tasks

- [x] Finish the current repo-level ABI marshalling coverage pass around alignment/attributes.
  Runtime coverage now includes aligned struct fields, packed structs, nested aligned layouts, aligned vector-bearing structs, and readback/marshalling regression cases for those layouts. Deeper real-device validation still belongs to A4.
- [x] Add integration tests that go through Java source -> generated OpenCL -> runtime execution for structs/vectors/helpers.
- [x] Add smoke coverage for `examples-app`.
- [x] Formalize support matrix into a concise supported/unsupported/planned table.

## C. OpenCL Surface Coverage

### C1. `GPU.*` intrinsic surface

- [x] Expand `GPU.*` to a practical OpenCL builtin surface for current workloads.
  The current practical surface is closed for the selected workload classes: hand-written facade overloads cover source-reachable APIs, while `GpuIntrinsicFamilyRegistry` fills repetitive conversion and integer/common family registry gaps without replacing special facade mappings. Future additions should be driven by real workload demand rather than abstract OpenCL symmetry.
- [x] Finish missing integer/common helper groups systematically for the exposed surface.
  Current practical slices already added: `mul24`, `mad24`, `upsample`, `select`, `bitselect`, `hadd`, `rhadd`, `mul_hi`, `mad_hi`, `add_sat`, `sub_sat`, `mad_sat`, `mul_sat`, `abs_diff`, `rotate`, `clz`, and `popcount`, including unsigned wrapper overloads for the saturating arithmetic family, unsigned `abs_diff`, practical mask-style `select` / `bitselect` coverage across the exposed unsigned vector families, practical `rotate` coverage across the exposed unsigned vector families, and wide unsigned `clz` / `popcount` result coverage via `Int8` / `Int16`. Integer vector common coverage now also includes practical `mul24` / `mad24` overloads for `Int2/3/4`, practical `hadd` / `rhadd` / `mul_hi` / `mad_hi` overloads for `Int2/3/4` and `Long2/3/4`, practical signed saturating `add_sat` / `sub_sat` / `mul_sat` / `mad_sat` overloads for `Int2/3/4` and `Long2/3/4`, practical signed/unsigned `abs_diff` overloads across the currently exposed integer vector families, plus practical `min` / `max` / `clamp` overloads for `Byte*`, `Short*`, `Int*`, `Long*`, `UInt*`, `ULong*`, `UByte*`, and `UShort*` vector families across the currently exposed practical widths, including the unsigned `8/16` families. The remaining work here is mostly optional symmetry and wider-family completeness rather than a blocker for the current serious workloads.
- [x] Finish conversion builtin coverage systematically for the exposed surface.
  Additional scalar conversion coverage is now in place for the practical signed-source slice around `byte` / `short` / `char` to `convert_int` / `convert_long` / `convert_float` / `convert_double`, signed-source `convert_uint` / `convert_ulong`, the matching signed-source saturating conversions for `convert_*_sat`, the practical signed-narrow source slice for regular narrow conversions into `convert_char` / `convert_uchar` / `convert_short` / `convert_ushort`, the practical unsigned-alias source slice for those same regular narrow conversions, the matching practical unsigned-alias source slice for saturating narrow conversions into `convert_char_sat` / `convert_uchar_sat` / `convert_short_sat` / `convert_ushort_sat`, the practical unsigned-alias source slice for saturating core-wide conversions into `convert_int_sat` / `convert_long_sat` / `convert_uint_sat` / `convert_ulong_sat`, a practical vector core-wide slice for `Float*` / `Double*` into `convert_int` / `convert_long` / `convert_uint` / `convert_ulong` across the exposed `2/3/4` families, the matching practical vector saturating core-wide slice for `Float*` / `Double*` into `convert_int_sat` / `convert_long_sat` / `convert_uint_sat` / `convert_ulong_sat` across those same families, a practical unsigned narrow vector slice for `Float*` / `Double*` into `convert_uchar` / `convert_ushort` plus `convert_uchar_sat` / `convert_ushort_sat` across the exposed `2/3/4` families, an initial signed narrow vector API slice via source-level `Byte2/3/4` and `Short2/3/4` wrappers plus practical `convert_char` / `convert_short` and `convert_char_sat` / `convert_short_sat` overloads across the exposed floating vector families, the matching reverse practical vector slice for integer/narrow/unsigned `2/3/4` wrappers into `convert_float` / `convert_double`, plus a practical wide integer conversion slice via `Int8` / `Int16` and wide unsigned integer vector conversions such as `UInt8/UInt16/ULong8/ULong16 -> Int8/Int16` and `Int8/Int16 -> UInt8/UInt16`. The remaining work here is broader API symmetry and optional wider-family completeness, not the practical conversion surface needed by the current serious workloads.
- [x] Finish geometric/common helper coverage systematically for the exposed surface.
  Additional practical vector-math coverage is now in place for element-wise `fabs` / `abs`, `floor`, `ceil`, `trunc`, `round`, `pow`, `pown`, `powr`, `rootn`, `fmod`, `remainder`, `nextafter`, `ldexp`, `mad`, `fma`, `rsqrt`, `fmin`, `fmax`, `minmag`, `maxmag`, `degrees`, `radians`, `copysign`, `sin`, `cos`, `tan`, `sinh`, `cosh`, `tanh`, `asin`, `acos`, `atan`, `atan2`, `sqrt`, `cbrt`, `hypot`, `exp`, `exp2`, `log`, `log2`, and `log10` across the exposed `Float*` / `Double*` vector families, plus practical scalar-broadcast common overloads such as `mix(vec, vec, vec)`, `step(scalar, vec)`, `smoothstep(scalar, scalar, vec)`, `fmin(vec, scalar)`, `fmax(vec, scalar)`, and `clamp(vec, scalar, scalar)`. Repo-local frontend regression coverage already locks this practical slice across dedicated geometric/common lowering tests. The remaining work here is mostly optional API symmetry, broader family completeness, or backend-specific expansion rather than a blocker for the current serious workloads.
- [x] Add `GPU.nan(...)` overloads for the scalar forms we want to support.
- [x] Add an initial saturating conversion slice covering narrow and core wide integer forms.
- [x] Add the regular narrow scalar conversion slice for `char/uchar/short/ushort`.
- [x] Add the core unsigned-alias conversion slice so `UByte/UShort/UInt/ULong` can flow through `convert_*` helpers directly.

### C2. Images and samplers

- [x] Basic image/sampler kernel API.
- [x] Core image object coverage: `image1d_t`, `image2d_t`, `image3d_t`.
- [x] Array/buffer image coverage: `image1d_array_t`, `image2d_array_t`, `image1d_buffer_t`.
- [x] Host-side image create/readback helpers for float/int/uint/RGBA8 base 2D workflows.
- [x] Samplerless reads and image metadata intrinsics.
- [x] Mipmapped 2D wrappers as real kernel parameter types.
- [x] Host-side 2D mipmapped `RGBA float` create/readback.
- [x] Host-side 2D mipmapped `RGBA int` create/readback.
- [x] Host-side 2D mipmapped `RGBA uint` create/readback.
- [x] Host-side 2D mipmapped `RGBA8` create/readback.
- [x] MSAA 2D wrappers as real kernel parameter types.
- [x] Kernel-side MSAA read/write coverage for exposed overloads.
- [x] Clarify the practical MSAA host/runtime contract.
  Current conclusion: `image2d_msaa_t` support in JavaToGpu is intentionally limited to kernel-parameter wrappers, query builtins, and exposed MSAA `read_image*` / `write_image*` overloads. Standalone host-side create/upload/readback helpers are not pursued as a normal OpenCL runtime slice because this image family is tied to `cl_khr_gl_msaa_sharing` style interop rather than the regular image workflow used by the rest of the runtime API.
- [x] Close broader extension-oriented mipmapped family coverage for current workloads.
  No current serious workload requires mipmapped families beyond the existing 2D RGBA float/int/uint/RGBA8 runtime path and kernel-parameter/read/write coverage. Future mipmapped expansion should reopen from a concrete workload requirement rather than remaining an open C-level blocker.

### C3. OpenCL qualifiers and attributes

- [x] Basic support for kernel/helper/struct OpenCL attributes already used in the project.
- [x] Deepen qualifier/attribute coverage and validation.
  The current repo-level slice now covers method/struct/field attribute validation, duplicate/malformed attribute diagnostics, helper-only and kernel-only attribute rules, and practical qualifier validation for `const` / `restrict` / `volatile` including duplicate, unsupported, and non-pointer misuse cases.
- [x] Decide whether `restrict` should become a user-facing source concept, a low-level concept only, or remain unsupported.
  Current decision: keep `restrict` as an explicitly low-level user-facing concept via `@OpenCLQualifiers`, limited to pointer-like GPU parameters rather than elevating it into the normal high-level Java surface.

## D. Helper / Reuse Architecture

- [x] Lock down reusable helper architecture across classes/modules with explicit tests.
  Repo coverage now includes separate-compilation paths for reusable `@CCode`, `@CCodeLibrary`, qualified same-simple-name helper owners, reusable `@GPUIntrinsic` libraries, guarded `support + callback` helpers, and `native` helper bodies declared through `@CCode(code = "...")`.
- [x] Improve the registration path for `native` + `@CCode(code = "...")` helper bodies.
  The classpath metadata/export path is now covered explicitly so reusable native helper bodies keep working across separate compilation units.
- [x] Refine the long-term model for reusable helper kernels and `@CCode(inline = true)`.
  The repo now fixes the current reusable-helper contract explicitly: reusable helpers are named backend functions, `@CCodeLibrary` is the separate-compilation reuse boundary, owner-qualified calls are the stable source form, `inline = true` changes emitted helper form rather than performing call-site body substitution, and guarded `support + callback` helpers remain part of that same named-helper model.
- [ ] Keep non-`void` `@GPU` entry methods in backlog until the helper/reuse model is clearer.

## E. Low-level OpenCL Data Model

This section tracks the remaining gaps revealed by source-level ports of large OpenCL math workloads.

### E1. Constant/global embedded data

- [x] Add module-level constant data support for embedded primitive scalar constant tables.
- [x] Add support for externally linked constant symbols / externally provided constant blobs.
- [x] Define the Java authoring model for constant-address-space shared tables.

### E2. Typed address-space pointer/view model

- [x] Add a real typed pointer/view API beyond scalar `*Ptr` wrappers.
- [x] Add address-space-aware pointer types for `global`, `constant`, and `local` access.
- [x] Add pointer arithmetic / byte-offset movement in a controlled form.
- [x] Add reinterpret/view helpers for reading one packed blob as typed regions.
  Current practical scope now includes the base scalar pointer matrix for `global` / `constant` / `local`, byte-view reinterpret helpers, controlled `add/sub` pointer movement, and Java-valid bridge helpers such as `GPU.global(...)`, `GPU.constant(...)`, and `GPU.local(...)`.
- [x] Lock pointer/view API symmetry with compile-time consistency coverage so the exposed `global` / `constant` / `local` matrix and byte-view reinterpret surface do not silently drift.

### E3. Fixed-size private arrays

- [x] Add a source/frontend model for private fixed-size arrays inside kernels/helpers.
- [x] Define lowering rules and diagnostics for that array model.

### E4. Packed blob/view layouts

- [x] Add a packed blob/view layer for offset-driven schemas.
- [x] Support structs that carry offsets into larger root blobs.
- [x] Support typed accessors/views over packed backing memory.

### E5. Union / reinterpretation support

- [x] Decide whether to support general union-style authoring in source.
- [x] Current decision: do not support general union-style authoring in the source frontend.
- [x] Document union-heavy code as ASM/low-level-only.

### E6. Debug/trap low-level helpers

- [x] Decide whether `printf`-style debug support is worth exposing.
- [x] Current decision: do not expose `printf` as part of the normal source-level `GPU.*` contract.
- [x] Decide whether trap/unreachable intrinsics should exist in source API.
- [x] Current decision: keep trap/unreachable as low-level-only until they are validated as a deliberate portable contract.

## F. Large OpenCL Workload Readiness

This is the practical bridge from JavaToGpu today to large hand-written OpenCL kernels that rely on broad intrinsic families, packed data views, and offset-driven memory access.

- [x] Most numeric helper logic is already source-compatible in principle.
- [x] Kernel attributes used by the example already map well to current support.
- [x] Unsigned aliases and many vector forms already exist.
- [x] Add the initial `nan` helper slice needed by that style of code.
- [x] Add the initial saturating conversion slice needed by that style of code.
- [x] Finish the remaining conversion completeness needed by that style of code.
  For large OpenCL-style workloads, the remaining blocker is no longer missing `convert_*` coverage. The practical scalar/vector conversion slice used by that style of code is now covered; any future `8/16` floating-vector symmetry work is optional API expansion rather than a blocker for this roadmap item.
- [x] Add module-level constant data support needed by lookup tables and extern constants.
- [x] Add typed pointer/view support needed by offset-driven packed data.
- [x] Add fixed-size private arrays needed by some helper logic.
- [x] Add packed blob/view support needed by root-blob schemas.
- [x] Decide whether union-heavy parts should be source-level or low-level/ASM-level only.

### F1. Remaining blockers for full large-workload Java-source parity

The current large-workload dogfooding target is now mostly aligned with existing JavaToGpu concepts, but a full source-level Java port still needs a few focused pieces:

- [x] Add 3D runtime launch support end-to-end.
  The reference kernels use `get_global_id(2)` / `get_global_size(2)` and 3D NDRange layouts. `GpuExecutionConfig` now supports `globalZ` / `localZ`, the OpenCL backend can enqueue 3D NDRanges, generated launchers expose explicit config and 3D work-size overloads, and targeted regression coverage exercises the 3D runtime path.
- [x] Add or formalize the Java authoring path for union-style packed views.
  Some source workloads use C `union` overlays inside packed structs such as weighted lookup nodes and compact search-tree nodes. The source-level policy remains no general Java union support; the practical path is now an explicit packed/blob overlay pattern using byte-root pointer views and `read*At(byteOffset)` helpers, with overlay regression coverage for reading alternate `uint32_t` / `int16_t` views from the same packed node layout.
- [x] Improve generic root-blob pointer ergonomics for this style of code.
  Large packed-data kernels often use `global const void *`, `global void *`, pointer-shift helpers, typed reinterpret casts, `restrict`, and `const` combinations. Existing byte-root typed views now have direct `read*At(byteOffset)` and `*PtrAt(byteOffset)` helpers, so root-blob schemas can express offset-driven typed views without repeating `root.add(offset).asTypePtr()` boilerplate for every field.
- [x] Define the source-level policy for preprocessor/debug/trap constructs.
  The file relies on `#ifdef` / `#define` feature gates, `DEBUG`, `DF_COMPILE_*`, `printf`, and `__builtin_trap()`. The Java frontend policy is now explicit: preprocessor feature gates and debug printing remain build/configuration or low-level concerns, while source-level fail-fast paths use `GPU.trap()` and `GPU.unreachable()` as deliberate Java-side replacements for `__builtin_trap()` / `__builtin_unreachable()`.
- [x] Validate kernel attributes together with 3D launch semantics.
  Attribute support exists, and the reference file includes launch-sensitive attributes such as `reqd_work_group_size(8, 8, 1)`. Regression coverage now verifies that this attribute is preserved in generated OpenCL, generated launchers expose explicit 3D config entry points, and runtime invocation can carry matching explicit local sizes such as `8 x 8 x 1`.
- [x] Add a focused Java dogfooding slice for large packed/root-blob kernels.
  The focused source-level dogfooding slice now covers a representative 3D packed lookup kernel shape: 3D NDRange launch, `reqd_work_group_size(8, 8, 1)`, packed/root-blob offset views, typed reinterpret helpers, and CPU-vs-GPU validation on the available NVIDIA OpenCL stack. The serious workload report now tracks this as a dedicated 3D packed/root-blob workload artifact.

## G. Dogfooding and Real Workloads

- [x] Run JavaToGpu against one or more serious non-demo projects.
  Initial repo-local pass completed against the Perlin/improved-noise workload in `test-app/src/main/java/net/sixik/ga_utils/TestMinecraftMath.java`, which is materially closer to a real numeric workload than the small showcase kernels.
- [x] Capture authoring pain points from those projects.
  First findings are recorded in `docs-project-plan/dogfooding-testminecraftmath.md`, including the confirmed `@GPUStruct` array boundary and the distinction between helper-heavy numeric kernels vs packed/blob-style low-level OpenCL layouts.
- [x] Convert those pain points into regression tests and API cleanup.
  The repository now includes a representative compile regression for the Perlin/improved-noise workload shape so future frontend/emitter changes do not silently break this class of code.
- [x] Reprioritize this roadmap after the first serious dogfooding pass.
  The follow-up dogfooding work is now in place repo-locally: helper-heavy numeric, packed/blob offset-schema, mixed packed numeric, and image/sampler workload classes all participate in the real-device workload-validation bucket. Broad API symmetry and optional OpenCL surface expansion remain lower priority than repeated operational validation and diagnostics from real failures.

## H. Lower Priority

- [ ] CUDA backend.
- [ ] More aggressive OpenCL generation optimizations.
- [x] Annotation/metadata-driven intrinsic binding autogeneration.
  First metadata-hardening slice is done: reusable `@GPUIntrinsicLibrary(backends = ...)` owners now persist owner backend metadata during export, and separate-compilation CUDA-only intrinsic libraries are rejected correctly by the OpenCL processor path instead of being silently accepted from classpath metadata. Practical annotation-driven type slices are also done for scalar aliases, vectors, and pointer wrappers: `@GPUScalarAliasType` custom wrappers are registered from compiler metadata, `@GPUVectorType` declares default Java method to backend operator mappings through `@GPUVectorOperator`, `@GPUPointerType` declares pointer arithmetic mappings through `@GPUPointerOperator`, the processor registers classpath-visible custom scalar/vector/pointer types before frontend validation, and registered descriptors can auto-lower instance calls such as `left.add(right)` or `ptr.add(index)` without repeating `@GPUIntrinsic` on every wrapper method. The broader repetitive-family slice is now also closed for current priorities: `GpuIntrinsicFamilyRegistry` provides deterministic generated registrations for conversion and integer/common intrinsic families across the exposed scalar/vector surface, while source-reachable broad calls remain validated through the existing `GPU.java` facade. Any future work here should be treated as optional facade/API ergonomics or CUDA backend expansion rather than a blocker before production-core/runtime validation.

## I. Future Compiler Intelligence

These are major post-production-core architecture tracks. They should not block the current A1/A2 runtime-validation push, but they define the next generation of the compiler once the runtime is operationally stable.

### I1. General ASM ingestion frontier

- [ ] Build an improved ASM parser/frontend that can ingest arbitrary JVM bytecode shapes instead of requiring GPU-friendly ASM.
  The long-term target is to accept normal Java compiler output and progressively classify methods into supported, transformable, or rejected regions with useful diagnostics. This likely needs CFG reconstruction, stack-state simulation, local-variable/type recovery, exception/monitor/control-flow detection, and a lowering boundary that can isolate GPU-safe regions without forcing source authors to hand-shape bytecode for the GPU pipeline.
- [ ] Add diagnostics for non-GPU-safe bytecode patterns discovered by the general ASM frontend.
  Arbitrary ASM should fail with concrete reasons and rewrite hints rather than opaque frontend errors, especially for object allocation, virtual dispatch, exceptions, synchronization, recursion, unsupported library calls, and heap/object graph access.

### I2. IR validation and optimization pipeline

I2 is closed for the current scope: the read-only IR Validator foundation is ready to be exercised on real examples. Deeper production optimizer/rewrite work remains intentionally out of I2 and is tracked as post-I2 frontier work instead of blocking validator dogfooding.

- [x] Establish the optional `ir-validation` module and ServiceLoader-based validation provider.
  Strict builds can opt into diagnostic, strict-safety, or strict-optimizer validation without changing default compiler behavior.
- [x] Build the reusable read-only validation report pipeline.
  `GpuIrOptimizationValidationPipeline` now aggregates safety validation, CSE planning preview, auto-vectorization preview, dry-run validation, resolved rewrite operations, optimizer-gate summaries, and CI artifact fields.
- [x] Add stable rule-registry artifacts for optimizer validation tooling.
  `GpuIrOptimizationValidationRule*`, artifact reports, acceptance decisions, consistency checks, runtime-evidence registries, and contract tests now provide the `validationRules*` / `validationRulesAcceptance*` surfaces.
- [x] Add optimizer-readiness handoff and conservative enablement policy layers.
  `GpuIrOptimizationValidationOptimizerReadinessHandoffReport` and `GpuIrOptimizationValidationOptimizerEnablementPolicyDecision` make clean artifacts reviewable while keeping production mutation disabled.
- [x] Add typed optimizer enablement artifact and detached smoke runner.
  `GpuIrOptimizationValidationOptimizerEnablementArtifact` and `GpuIrOptimizationValidationOptimizerEnablementArtifactRunner` package rule artifact, acceptance, handoff, and policy into one opt-in tooling path.
- [x] Add aggregate optimizer enablement gate.
  `GpuIrOptimizationValidationOptimizerEnablementGateReport` combines CSE literal-promotion readiness, auto-vectorization readiness, and conservative policy state into one `optimizerEnablementGate*` verdict.
- [x] Add typed optimizer validation bundle for future A1/A2 production-enable checks.
  `GpuIrOptimizationValidationOptimizerValidationBundle` packages the validation report, enablement artifact, gate, and production preflight decision as one read-only object.
- [x] Add final read-only production-enable preflight decision.
  `GpuIrOptimizationValidationProductionEnablementPreflightDecision` exports `optimizerProductionPreflight*` fields with blocked/review-ready/future-ready states, first blocker, remaining work, and production-mutation flags.
- [x] Pin stable CI contracts for the new enablement stack.
  Contract tests now cover `optimizerEnablementGate*`, `optimizerValidationBundle*`, and `optimizerProductionPreflight*` so future tooling can rely on those keys. The current L2 CI surface is also pinned with direct tests for `cseLayerReadinessCi*`, `autoVectorizationReadinessCi*`, `optimizerLayerReadinessCi*`, `optimizerGateConsistency*`, `optimizerGateAcceptance*`, `optimizerCiGateIndexConsistency*`, `optimizerCiGateIndexAcceptance*`, regression/baseline CI summaries, and nested artifact-map composition, keeping current, regression, and stored-baseline CI artifacts collision-free and fail-closed.
- [x] Wire the bundle/preflight into an explicit A1/A2 production-enable readiness runner.
  `GpuIrOptimizationValidationProductionEnablementReadinessRunner` now accepts a concrete validation report and returns one production-enable preflight decision or full bundle field map without mutating IR.
- [x] Add successful runtime-equivalence-backed review-ready fixtures.
  Runtime-equivalence-backed CSE literal evidence now proves the `evidenceCompleteButProductionDisabled` path as `cseReadyForEnablementReview=true`, allowing the aggregate gate and A1/A2 preflight to report `reviewReady/productionMutationDisabled` when auto-vectorization readiness and the conservative policy are otherwise clear, while production mutation remains disabled.
- [x] Design the future production mutation switch.
  `GpuIrOptimizationValidationProductionMutationSwitchContract` now exports a read-only `optimizerProductionSwitch*` contract above the A1/A2 preflight. It defines the future switch boundary, required runtime evidence, rollback requirement, cross-vendor runtime coverage, and feature-flag work while keeping production mutation disabled.
- [x] Promote selected optimizer rewrites only after A1/A2 runtime confidence is stable.
  `GpuIrOptimizationValidationOptimizerPromotionConfidenceContract` now exports a read-only `optimizerPromotionConfidence*` gate above the production switch contract. It keeps selected rewrite promotion blocked until A1/A2 runtime-equivalence history, long-running operational stress, NVIDIA interim coverage, cross-vendor promotion gates, and rollback evidence are stable.
- [x] Add a unified production-readiness artifact above the preflight/switch/promotion stack.
  `GpuIrOptimizationValidationProductionReadinessArtifact` now packages the validation bundle, A1/A2 production preflight, future production mutation switch contract, and promotion-confidence contract into one `optimizerProductionReadiness*` CI surface. It exposes the aggregate verdict, blocker list, remaining work, production-mutation state, consistency self-check fields, acceptance-decision fields, nested preflight/switch/promotion fields, and detached runner APIs while keeping production mutation disabled.
- [x] Add optimizer-layer readiness baseline lifecycle surfaces for CI.
  `GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot`, baseline comparison, compact baseline CI summary, fail-closed invalid-baseline handling, method-mismatch diagnostics, and `.properties` save/load helpers now let CI persist `optimizerLayerReadinessBaselineSnapshot*` fields, compare later runs, and fail only on real regressions or damaged baselines while production mutation remains disabled.

- [x] Add the first opt-in IR validator foundation as a separate module.
  The `ir-validation` module now contributes an optional `GpuIrValidationProvider` through Java `ServiceLoader`, so strict builds can add it to the annotation-processor path and explicitly enable it with `-Ajavatogpu.irValidation=diagnostic`, `strictSafety`, or `strictOptimizer` without making the main compiler/runtime artifact heavier or changing default builds. Diagnostic mode now supports `-Ajavatogpu.irValidationDiagnostics=quiet|summary|detailed`, defaulting to compact javac `NOTE` summaries for the unified read-only validation report, including proof-bundle rewrite-safety, diagnostic, and unsafe-proof counters such as `autoVectorizationProofBundleRewriteSafe`, `autoVectorizationProofBundleDiagnostics`, `autoVectorizationProofBundleUnsafeProofs`, and `autoVectorizationProofBundleFirstUnsafeProof`; detailed diagnostics also include nested `autoVectorizationProofBundle={...}` context. `-Ajavatogpu.irValidationReport=<relative-path>` writes a machine-readable `.properties` CI artifact with safety/CSE/vectorization counters plus auto-vectorization vector-shape, rewrite-readiness, rewrite-blocked, rewrite-plan, rewrite-policy, rewrite-dry-run, resolved-rewrite, rewrite-guard, rewrite-plan proof-summary fields such as `autoVectorizationProofRewritePlanRewriteSafe`, `autoVectorizationProofRewritePlanDiagnostics`, and `autoVectorizationProofRewritePlanGuardFamily.*`, proof-bundle fields such as `autoVectorizationProofBundleProofs`, `autoVectorizationProofBundleKinds`, `autoVectorizationProofBundleKindCounts`, `autoVectorizationProofBundleRewriteSafe`, `autoVectorizationProofBundleDiagnostics`, `autoVectorizationProofBundleUnsafeProofs`, `autoVectorizationProofBundleFirstUnsafeProof*`, and `autoVectorizationProofBundleGuardFamily.*`, warning-family, rejection-reason, first-blocking-diagnostic, and first-blocking-family fields. Strict modes still fail at their configured safety/optimizer boundaries with detailed nested optimizer context. The first slices validate lowered IR variable declarations/references, assignment targets, loop/switch break/continue legality, duplicate local declarations, helper-call reachability/result metadata, helper dependency metadata consistency, intrinsic template placeholders, basic struct-initializer metadata, return-shape consistency, non-blank type/operator metadata, positive literal sizes for private arrays, conservative expression-statement side effects, and void-helper result metadata before OpenCL emission. Purity/effect classification has also been split into reusable `GpuIrExpressionClassifier` / `GpuIrExpressionEffect` types so the validator and future CSE/canonicalization passes share one conservative source of truth. A unified read-only `GpuIrOptimizationValidationPipeline` now combines safety validation, CSE planning preview, auto-vectorization preview, dry-run rewrite validation, and resolved rewrite-operation metadata into one report with diagnostic-only, strict-safety, and strict-optimizer modes plus typed aggregate counters for optimizer diagnostics, giving processor integration one stable feature-flagged entrypoint before any production rewrite is enabled.
- [x] Close I2 for real-example IR Validator validation.
  I2 is now treated as complete for its intended scope: optional module wiring, strict/diagnostic modes, safety validation, read-only optimizer evidence, CI artifacts, fail-closed gate contracts, baseline comparison helpers, production-readiness preflight surfaces, and real-example dogfooding contracts are in place. The opt-in `validateIrValidationExampleReports` task now compiles `examples-app` and `test-app` with the validator enabled, fails on any safety regression, verifies the read-only optimizer-readiness artifact shape, rejects accidental production-rewrite enablement, and summarizes expected blockers. Current dogfooding evidence is clean for safety (`examples-app`: `41/41`, `test-app`: `20/20`) while optimizer readiness remains intentionally blocked by read-only CSE literal-promotion / auto-vectorization gates plus the known CSE skipped-candidate policy blockers (`examples-app`: `3`, `test-app`: `3`). Future work should use those real reports to drive post-I2 optimizer frontier improvements instead of reopening the I2 foundation.

Post-I2 optimizer frontier, not blocking I2 closure:

- [ ] Tighten transformation-safety proof coverage before any future production rewrite.
  Add dominance, side-effect/evaluation-order, alias/memory-space, and backend/device proof checks; keep mutating paths blocked unless all proof layers are present and consistent.
- [ ] Expand canonicalization and common-computation detection beyond the current conservative slices.
  Extend numeric canonicalization, helper/intrinsic candidate grouping, and proof-ready vs blocked candidate artifacts only when Java semantics and runtime evidence are clear.
- [ ] Grow auto-vectorization beyond current fixed-width loop detection.
  Add broader coordinate/noise/math candidate families, richer layout blockers, backend-sensitive vector decisions, and prototype-only rewrite artifacts while production `apply(...)` remains fail-closed/no-op.
- [ ] Add runtime-equivalence proof artifacts for every optimizer family selected for promotion.
  Require CPU/reference and pre/post optimization evidence, machine-readable diagnostics, and failed-evidence regression tests before any production-readiness gate can move from blocked to ready.
- [ ] Wire optimizer evidence into A1/A2 operational validation.
  Export the I2 CI gate index into NVIDIA validation artifacts, store readiness baselines across runs, summarize first rejected optimizer gates, and keep Intel/AMD promotion gates blocked until hardware validation exists.
## Recommended Execution Order

If the goal is to move forward pragmatically from the current state, the best order is now:

1. `A1/A2 NVIDIA-only interim operational validation`
   Keep repeating `:processor:openClOperationalRoutine --rerun-tasks` on the available RTX 5070 stack until Intel/AMD hardware exists, preserving validation history, workload summaries, long-running summaries, and benchmark output.
2. `A3/A4 Diagnostics and runtime-stability fixes from real failures`
   Treat any repeated NVIDIA failure, skipped workload, resource leak, or diagnostic gap as the next concrete implementation target.
3. `A1 Intel/AMD runner bring-up when hardware exists`
   The cross-vendor production gate remains open until Intel and AMD OpenCL stacks can run the same bucket set.
4. `Improved ASM parser/frontend`
   F1 source-parity blockers and the read-only IR enablement stack are closed for current priorities, so the next major compiler-quality leap is the general ASM ingestion frontier. The first broader-ASM diagnostics layer is now in place: `AsmFrontendFailureMetadata`, `AsmFrontendFailureReport`, `AsmFrontendFailureReporter`, `AsmUnsupportedPatternScanner`, Rust-like `AsmFrontendDiagnosticAdapter`, and public `GpuProgramCompiler.reportStructuredAsm(...)` preflight APIs expose stable `asmFailure.*` metadata without expanding the accepted bytecode subset.
5. `H Lower-priority backend/optimization work`

## Current Working Conclusion

JavaToGpu is already beyond the "toy compiler" stage.

The broad repetitive intrinsic-family generation detour is now closed for current priorities, so the active focus returns to production-core/runtime validation rather than expanding optional API symmetry.

After the first serious dogfooding pass, the main repo-local language/runtime gaps for the selected workload classes are no longer the active blocker. Section C and F1 are closed for current practical workload coverage: 3D launch config, union-style packed views, root-blob ergonomics, launch-sensitive attributes, and the focused packed/root-blob dogfooding slice are all covered. Operational confidence remains NVIDIA-only until Intel/AMD hardware is available, and the next major compiler-quality frontier is broader ASM ingestion.

Latest I2/L2 reconciliation: the current CI contract layer is closed for present priorities. Direct contract coverage now pins current readiness CI summaries, optimizer-gate consistency/acceptance, optimizer CI gate-index consistency/acceptance, regression/baseline CI summaries, nested artifact-field composition, and opt-in real-example dogfooding reports for `examples-app` / `test-app`. The dogfooding contract keeps safety fail-fast while treating optimizer readiness as a read-only stability artifact: production mutation must remain disabled, rewrite applicability must stay false, known CSE policy blockers are counted, and no-candidate CSE literal-promotion / auto-vectorization blockers are accepted as the current baseline. The remaining I2 items are intentionally broader frontier work: deeper transformation-safety proofs, stronger canonicalization/common-computation detection, runtime-equivalence evidence for selected optimizer families, and eventual production rewrite promotion after A1/A2 runtime confidence is stable. These should stay open until backed by runtime evidence rather than being marked complete from read-only artifact coverage alone.














