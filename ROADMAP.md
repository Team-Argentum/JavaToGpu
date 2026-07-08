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
  Current recorded lanes include `NVIDIA CUDA / NVIDIA GeForce RTX 3060` and `NVIDIA CUDA / NVIDIA GeForce RTX 5070`; both pass the full `:processor:openClOperationalRoutine` evidence flow with vendor validation, integration smoke, workload-equivalence, long-running stability, benchmark, bucket-status, and validation-history artifacts all passing.
- [x] Run the existing validation suite repeatedly on real AMD OpenCL hardware.
  Current recorded lane: `AMD Accelerated Parallel Processing / gfx1101` on AMD RX 7800 XT, platform `OpenCL 2.1 AMD-APP (3679.0)`, device version `OpenCL 2.0 AMD-APP (3679.0)`, with the full OpenCL operational evidence flow passing.
- [x] Establish NVIDIA/AMD interim operational validation mode.
  A1/A2 work can now use repeated NVIDIA and AMD `:processor:openClOperationalRoutine` runs as the active production-confidence signal while keeping Intel as the remaining future cross-vendor promotion gate.
- [x] Record vendor quirks/regressions in a maintained device-quirks document.
  `docs/Device-Quirks.md` now records clean baselines for NVIDIA RTX 3060, NVIDIA RTX 5070, and AMD RX 7800 XT, plus an explicit Intel pending-validation gate for future cross-vendor promotion.
- [x] Promote vendor validation from "repo-local ready" to "operationally proven".
  The NVIDIA and AMD interim validation paths are now treated as operationally proven for repo-local alpha confidence. This does not close the Intel cross-vendor gate, which remains pending hardware.
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
  Latest post-I2 baselines now include NVIDIA RTX 3060, NVIDIA RTX 5070, and AMD RX 7800 XT validation artifacts, including workload-equivalence, long-running stability, benchmark, bucket-status, and validation-history summaries.
- [x] Re-run stress/benchmark/long-running validation buckets periodically against target vendor stacks.
  Current periodic targets are the available NVIDIA and AMD stacks. Latest validation artifacts cover NVIDIA RTX 3060, NVIDIA RTX 5070, and AMD RX 7800 XT lanes with benchmark, stress, long-running, workload-equivalence, bucket-status, and validation-history evidence. Intel repeats remain pending hardware.

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
  Public docs now reflect the current alpha/release posture: NVIDIA RTX 3060, NVIDIA RTX 5070, and AMD RX 7800 XT are operationally validated for repo-local confidence, Intel remains the future cross-vendor gate, and Maven publishing covers both `javatogpu` and the optional `javatogpu-ir-validation` artifact with matching release-readiness checks.

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
  `GpuIrOptimizationValidationOptimizerPromotionConfidenceContract` now exports a read-only `optimizerPromotionConfidence*` gate above the production switch contract. It keeps selected rewrite promotion blocked until A1/A2 runtime-equivalence history, long-running operational stress, NVIDIA/AMD interim coverage, the remaining Intel promotion gate, and rollback evidence are stable.
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
  Export the I2 CI gate index into NVIDIA/AMD validation artifacts, store readiness baselines across runs, summarize first rejected optimizer gates, and keep the Intel promotion gate blocked until hardware validation exists.

### I3. Production IR pipeline and runtime backend optimization

Goal: make IR a first-class production pipeline artifact instead of using build-time OpenCL source as the only generated output. This is the bridge from the current read-only IR Validator to future runtime/device-specific optimization for OpenCL, CUDA, Vulkan/SPIR-V, and Metal.

Current rule: keep the existing OpenCL build-time source path working while I3 is introduced. New runtime IR compilation must be opt-in until A1/A2 runtime evidence, rollback, and cross-vendor gates are stable.

#### I3.1 Canonical `IrGpu` artifact format

- [x] Add the first backend-neutral `IrGpu` artifact model.
  Current artifact stores a versioned header, module identity, entry method, helper method metadata, and derived backend outputs without committing the runtime API to OpenCL-only storage.
- [x] Add an `IrGpu` version/header contract.
  Initial header records schema version, compiler version, source frontend, and compatibility version so runtime loaders can reject incompatible future artifacts deliberately.
- [x] Serialize `IrGpu` as a packaged build artifact/resource.
  Build output now packages `kernel.irgpu.properties` beside the existing OpenCL resource and exposes it through generated `GpuKernelDescriptor` metadata.
- [x] Keep generated OpenCL source as a derived artifact during transition.
  The current `kernel.cl` output remains the default runtime source path while `IrGpu` is introduced as the packaged canonical bridge artifact.
- [x] Add initial parser/serializer and compatibility tests for `IrGpu`.
  Covered by frontend and processor tests that verify `IrGpu` creation, packaged resources, generated descriptor wiring, and runtime classpath loading.
- [x] Add first `IrGpu` method-body payload layer beyond the manifest foundation.
  Entry/helper methods now carry serialized `ir-text-v1` body snapshots with helper dependencies in the packaged `kernel.irgpu.properties`, while OpenCL remains the derived compatibility output.
- [ ] Expand `IrGpu` payload to full source-of-truth coverage.
  Initial ABI, launch, validation, feature, typed-body summary, regeneration-readiness, struct, constant, and constant-data metadata are now persisted through manifest fields: `entryParameter.*`, `launch.*`, `validation.*`, `feature.*`, `methodBody.*.bodyIndex.*`, `regeneration.*`, `structMetadata.*`, `constant.*`, and `constantData.*`. Remaining source-of-truth work still includes fully reconstructable entry/helper bodies, replacing the transitional derived OpenCL fallback, and direct OpenCL reconstruction from `IrGpu`.
- [x] Add OpenCL-from-`IrGpu` round-trip/parity tests for the current OpenCL workload slice.
  First parity guard is in place: OpenCL lowering checks that packaged `IrGpu` points at the same derived OpenCL source resource as the generated descriptor and fails fast on drift. The parity result also exposes `regeneration.*` readiness/blockers, making the current derived-source fallback explicit in lowering diagnostics. `OpenClIrGpuReconstructionPreview` adds a non-mutating reconstructability check that distinguishes transitional fallback artifacts from synthetic backend-neutral-ready artifacts without switching the default production source path.
- [x] Implement the first practical `ir-text-v1 -> OpenCL` source-reconstruction slice.
  `OpenClIrTextBodyParser` / `OpenClIrTextBodyEmitter` now cover flat `var`, `set`, `return`, expression statements, helper calls, OpenCL intrinsic calls, OpenCL vector constructors through `init<T>(...)`, OpenCL compound literals for `@GPUStruct` initializers, private pointer-like local storage values such as `FloatPtr ptr = input[id]`, native `@CCode(code=...)` helper bodies through `opencl-native-body-v1`, private local array declarations through `private-array`, escaped-line snapshot normalization, indented `if` / `else if` / `else`, simple `for init=(...) cond=... update=(...)` loops, `while` / `do-while` loops, `switch` / `case` / `default` blocks, and `break` / `continue` / `loop-break`, with non-throwing unsupported-line blockers plus compact unsupported-token diagnostics for anything outside that slice. `OpenClIrGpuSourceAssembler` can assemble entry/helper/struct artifacts with required metadata into diagnostic reconstructed OpenCL source; `examples-app` now has a permanent generated-artifact parity gate covering `28/28` reconstructed-vs-descriptor OpenCL matches, and `test-app` adds a second gate covering `5/5` generated workload artifacts including the Minecraft-like fixture.
- [x] Prove full-source reconstruction for control-flow and expression-heavy bodies.
  Full-source emission/reconstruction tests now prove control-flow and expression-heavy bodies (helpers, casts, vector initializers, intrinsic templates, and ternary expressions) can round-trip through `IrGpu -> OpenCL source` with `sourceParity.matched=true`.
- [x] Prove classpath-packaged `IrGpu` review readiness across runtime artifacts and reports.
  Classpath-packaged `*.irgpu.properties` fixtures reconstruct through the runtime artifact loader and match descriptor-source parity for the simple scalar and image/intrinsic OpenCL resource slices. Runtime dump artifacts expose packaged-source readiness through `backend-source-reconstruction.properties`, `backend-source-promotion-gate.properties`, and `i3-readiness-summary.properties` (`ready=true`, `reconstructed=true`, `sourceAvailable=true`, `sourceParity.matched=true`). Validation report/history fixtures surface the same packaged-source readiness at workload/report level, keeping ready kernels free of synthetic `irgpu-artifact-missing` reconstruction blockers while preserving production fail-closed blockers for kernels that are not source-ready.
- [x] Add a documented OpenCL `IrGpu` source review lane.
  OpenCL lowerer smoke coverage proves the same packaged `IrGpu` resource remains on descriptor source by default and only switches to `#irgpu-reconstructed` source when `opencl.sourceSelection=irgpu` is explicitly requested in review mode. The public runtime API exposes `GpuRuntimeCompileOptions.openClIrGpuSourceReview(...)` plus the named `OPENCL_IRGPU_SOURCE_REVIEW_PROFILE`, focused runtime/lowerer/dumper/report test call sites use the preset while artifact text still records the stable profile value, and public runtime/API docs show the review-lane entrypoint. Workload validation has reached parity/runtime-equivalence for the current five OpenCL workload kernels, including the image workload.
- [ ] Promote reconstructed `IrGpu` source from review lane to production source switching.
  This remains intentionally blocked. Production source switching now fail-closes unless reconstructed source is available, descriptor-source parity has matched, the backend source-promotion gate is `review-ready`, `opencl.productionSourceSwitching=enabled` is explicit, the runtime production-promotion decision is `production-enabled`, runtime-equivalence evidence passed, fallback/rollback evidence is clean, and A1/A2 validation confidence is accepted. `backend-source-switching-decision.properties` now records the first source-promotion blocker so production source-switching failures can be triaged without opening the full promotion gate artifact.

#### I3.2 Runtime compile request and compile options

- [x] Introduce a runtime `GpuRuntimeCompileRequest` / `GpuRuntimeCompileOptions` model.
  Current request carries backend selection, compile args, optimizer profile, device profile, descriptor, and classpath `IrGpu` artifact when available.
- [x] Support user-provided backend compile arguments through the public runtime API.
  Generated launchers, direct runtime calls, and reflection launcher helpers can pass compile args and optimizer profile without changing kernel argument ordering.
- [x] Add compile option provenance to artifacts.
  Runtime compile snapshots and dump bundles now record backend/device identity, compile args, optimization profile, and fallback decision placeholders so generated runtime evidence can explain how a backend artifact was produced.
- [x] Make ASM and Java source frontends feed the same runtime compile request path.

  The shared runtime handoff helper now exists in `GpuRuntimeCompileRequestFactory`, and OpenCL runtime compile requests are built through it instead of directly constructing request records. Java-generated launchers and structured ASM converge on the same descriptor/options/device/`IrGpu` request construction path: `AsmFrontendService.compileStructured(...)` and `GpuProgramCompiler.compileStructuredAsmResult(...)` expose the same `GpuFrontendCompilationResult` shape used by Java source compilation, while `GpuFrontendCompilationResult.toKernelDescriptor(...)` / `toRuntimeCompileRequest(...)` build the runtime descriptor/request from that shared frontend result. `GpuFrontendResourcePaths` centralizes generated `kernel.cl` / `kernel.irgpu.properties` identities for Java and ASM paths, and `GpuFrontendArtifactWriter` writes the paired OpenCL + `IrGpu` artifacts through one reusable packaging sink so ASM can use the same resource emission contract as the annotation processor.
- [x] Add strict validation for unsafe or unsupported OpenCL compile args.
  OpenCL compile options are validated before device/session lookup, reject backend-mismatched options, and pass supported build flags into `buildProgram(source, options)` instead of silently ignoring them.
- [x] Add structured compile option surfaces for future backends.
  `GpuBackendCompileOptions` now gives OpenCL, CUDA, Vulkan/SPIR-V, and Metal separate typed option buckets while preserving legacy OpenCL `compileArgs` compatibility. Runtime compile provenance now records both the legacy OpenCL arg list and the structured backend option target/flags/properties, and OpenCL validation rejects mismatched structured backend options before compile/session work begins.
- [x] Add an explicit OpenCL source-selection option.
  `GpuBackendCompileOptions` now reserves `opencl.sourceSelection` with fail-closed values `descriptor` and `irgpu`. `GpuRuntimeCompileOptions.openClIrGpuSource(...)` is the public review/smoke opt-in entrypoint for compiling from reconstructed `IrGpu` source, while unsupported values are rejected before runtime/device lookup.
- [x] Add an explicit production source-switching gate.
  `GpuBackendCompileOptions` now reserves `opencl.productionSourceSwitching` with values `disabled` and `enabled`. Production-like profiles (`production`, `vendor-tuned`, `runtime-tuned`, `prod`) cannot compile from reconstructed `IrGpu` source unless `opencl.sourceSelection=irgpu`, `opencl.productionSourceSwitching=enabled`, and the runtime production-promotion decision is `production-enabled`, keeping review/smoke tests separate from future production rollout.
- [x] Add runtime dump evidence for backend source-switching decisions.
  Runtime artifact dumps now include `backend-source-switching-decision.properties`, which records the selected source mode, production-like profile detection, production source-switching flag, source-ready state, reconstruction availability, source-parity status, source-promotion status, first source-promotion blocker, and the resulting decision (`compile-descriptor-source`, `compile-irgpu-source-review`, `compile-irgpu-source-production`, `reject-irgpu-source-unavailable`, `reject-irgpu-source-parity`, or `reject-production-irgpu-source`). This keeps production rollout explanations separate from optimizer gates and workload promotion gates while fail-closing `opencl.sourceSelection=irgpu` when reconstructed source is missing, preview-ready but not assembled, parity has not matched, source-promotion evidence is not review-ready, or the production-promotion decision is not enabled.
- [x] Split backend source-switching decisions into a reusable runtime contract.
  `GpuBackendSourceSwitchingDecision` now owns the fail-closed descriptor-vs-`IrGpu` source decision outside artifact dump formatting. The contract records source-selection options, source-ready state, reconstruction availability, source parity, promotion status, production-profile detection, production source-switching state, and the final decision in one backend-neutral surface that OpenCL uses today and CUDA/Vulkan/Metal can reuse later.
- [x] Normalize backend source-switching options before the shared decision runs.
  `GpuBackendSourceSwitchingPolicy` now translates backend-specific option keys into a compact descriptor-vs-`IrGpu` source policy before `GpuBackendSourceSwitchingDecision` evaluates fail-closed promotion rules. OpenCL remains the first adapter for `opencl.sourceSelection` / `opencl.productionSourceSwitching`, while CUDA/Vulkan/Metal can add their own option adapters without leaking OpenCL property names into the shared runtime decision.
- [x] Route OpenCL lowering through the shared source-switching policy/decision.
  `OpenClBackendLowerer` now normalizes compile options through `GpuBackendSourceSwitchingPolicy`, preserves descriptor-source default behavior, and only assembles reconstructed `IrGpu` source after reconstruction/parity preflight passes. Final production source-switching remains enforced on the runtime compile snapshot after runtime-equivalence and fallback evidence exist, so preflight source assembly can feed validation without bypassing the strict production gate.
- [x] Carry backend source-switching decisions on runtime compile snapshots.
  `GpuRuntimeCompileArtifactSnapshot` can now carry an optional precomputed `GpuBackendSourceSwitchingDecision`, and `GpuRuntimeCompileArtifactDumper` uses it directly when present instead of recomputing. Existing snapshot builders still fall back to computed decisions, while future CUDA/Vulkan/Metal backends can hand off their own source-switching decision as part of the runtime artifact snapshot.
- [x] Populate runtime compile snapshots with OpenCL source-switching decisions.
  `OpenClGpuRuntimeBackend` now computes and attaches a `GpuBackendSourceSwitchingDecision` while building the final runtime compile snapshot, using the selected compile request, lowered module artifact, runtime-equivalence evidence, and fallback evidence. This makes OpenCL runtime artifacts carry the actual source-switching decision instead of relying on dump-time recomputation, while older/manual snapshots still retain the fallback computed path.
- [x] Carry backend source-promotion gates on runtime compile snapshots.
  `GpuRuntimeCompileArtifactSnapshot` can now carry an optional precomputed `GpuBackendSourcePromotionGate`, and `GpuRuntimeCompileArtifactDumper` uses it for backend-source promotion and I3 readiness artifacts when present. `OpenClGpuRuntimeBackend` now attaches the gate alongside the source-switching decision, so OpenCL runtime artifacts preserve the actual source-promotion state instead of forcing dump-time recomputation.
- [x] Centralize production-like runtime profile detection.
  `GpuRuntimeProductionProfiles` now owns the production profile classifier used by the OpenCL lowerer, backend source-switching dumps, and production optimizer gate, so future rollout profile names do not drift across runtime guards.
- [x] Add an opt-in OpenCL `IrGpu` source review lane.
  `openClIrGpuSourceReviewTest` runs a real-device reconstructed-`IrGpu` source smoke with `opencl.sourceSelection=irgpu` and writes `irgpu-source-review.properties`; NVIDIA/operational routines include it as a review artifact while the production workload gate remains descriptor-source and fail-closed.

#### I3.3 Backend lowering boundary

- [x] Define the first `GpuBackendLowerer` service boundary.
  Current contract is `GpuRuntimeCompileRequest -> GpuBackendModuleArtifact`, with the artifact carrying backend target, kind, format, source/resource identity, and lowerer version. Once full `IrGpu` bodies exist, the same boundary should switch from descriptor source to `IrGpu + compile request` without changing callers.
- [x] Move OpenCL source lowering behind that backend boundary.
  `OpenClBackendLowerer` is now the first lowerer implementation and preserves existing OpenCL source/runtime behavior while creating the runtime extension point for later backend modules.
- [x] Add backend module artifact metadata.
  `GpuBackendModuleArtifact` records backend id, generated code kind, format, source/resource identity, lowerer version, source origin, source/binary availability, optional compile-log/source-map resource identities, and runtime load mode.
- [x] Keep backend lowerers isolated from frontend validation.
  Lowerers currently consume runtime compile requests and generated kernel descriptors only; frontend Java/ASM validation remains outside backend-specific lowering.
- [x] Add no-op/stub lowerers for planned CUDA, Vulkan/SPIR-V, and Metal.
  `GpuBackendLowerers` exposes stable CUDA, VULKAN, and METAL entries that fail with explicit unsupported diagnostics until real lowerers are implemented. Their source-selection plans now report backend-specific unavailable selected-source labels, `irgpu-unlowered` payload format, unsupported runtime-load mode, and fail-closed blockers before any runtime compile attempt.
- [x] Extend OpenCL backend lowering to consume reconstructable `IrGpu` artifacts in opt-in review mode.
  OpenCL lowering still uses the existing generated descriptor source by default, but it now has an explicit opt-in reconstructed-`IrGpu` source path. A reconstruction plan records whether OpenCL can select backend-neutral `IrGpu` source or must stay on the transitional fallback, and `GpuBackendLowerer.sourceSelectionPlan(...)` exposes that backend-neutral source-selection decision before module lowering. `OpenClIrGpuReconstructionPreview` provides the non-mutating runtime contract for checking method-body count, entry emitted name, selected source path, and blockers before direct reconstruction is selected; runtime dumps preserve the same state in `opencl-irgpu-reconstruction-preview.properties`, `backend-source-reconstruction.properties`, and fail-closed `backend-source-promotion-gate.properties`. `IrGpuModuleMethod` now persists helper return/parameter metadata, `OpenClIrGpuSourceEmission` runs parser-backed inspection, `OpenClIrTextBodyEmitter` lowers flat `var` / `set` / `return` bodies, expression statements, helper-call expressions, OpenCL intrinsic calls, `init<T>(...)` vector constructors, indented `if` / `else`, simple `for init=(...) cond=... update=(...)` loops, `while` / `do-while` loops, `switch` / `case` / `default` blocks, and `break` / `continue` / `loop-break` into OpenCL-C body text. `OpenClIrGpuSourceAssembler` assembles helper functions, entry signatures, and emitted body text into a complete diagnostic OpenCL source for artifacts with required metadata, and `OpenClIrGpuSourceParityComparison` records normalized reconstructed-vs-descriptor source comparison diagnostics. `OpenClBackendLowerer` keeps descriptor-source compilation as the safe default, but when `opencl.sourceSelection=irgpu` is requested it compiles the reconstructed source only if source reconstruction exists and `sourceParity.matched=true`; otherwise it fails closed before backend compilation. Production-like profiles also require `opencl.productionSourceSwitching=enabled` and `productionPromotion.decisionMode=production-enabled`, so review/smoke `IrGpu` source tests cannot silently become production source switching. A real-device integration smoke now exercises this opt-in path with a simple reconstructed `IrGpu` source artifact. `openClBackendSourcePromotionGateReport` now writes a synthetic `GpuRuntimeCompileArtifactDumper`-backed contract fixture artifact, while `openClValidationReport` writes and reports a separate real workload source-promotion gate at `backend-source-promotion-workload-gate.properties`; CI can see the synthetic contract fixture as `review-ready` while the real workload gate remains fail-closed with production source switching disabled. `OpenClGpuRuntimeBackend` can now dump that workload gate from the actual `GpuRuntimeCompileArtifactSnapshot` produced during runtime invocation when `javatogpu.opencl.backendSourcePromotionWorkloadGateFile` is configured, and `openClWorkloadValidationTest` wires the property so real workload runs replace the placeholder with runtime-snapshot evidence. The workload gate is aggregated by kernel resource using `kernel.count` and `kernel.N.*` entries, including per-kernel diagnostic counts, first-blocker summaries, and backend-neutral blocker-family taxonomy from `GpuBackendSourcePromotionBlockerClassifier` (`reconstruction`, `source-parity`, `runtime-equivalence`, `fallback-clean`, `other`) with aggregate family counters, so OpenCL/CUDA/Vulkan/Metal gates can share one promotion-blocker vocabulary and report/history surfaces every workload kernel. Promotion remains blocked until reconstructed source is available, parity is checked and matched, runtime equivalence passes, fallback evidence is clean, production source switching is explicitly enabled, the production promotion decision is accepted, and real workload promotion evidence exists.
- [ ] Promote backend lowering from opt-in `IrGpu` review mode to production source switching.
  This remains open by design. The current OpenCL path can reconstruct and smoke-test `IrGpu` source, but default runtime compilation still uses descriptor OpenCL source. Production source switching requires `opencl.productionSourceSwitching=enabled`, a `production-enabled` promotion decision, accepted production optimizer/source-promotion gates, clean runtime-equivalence evidence, no fallback/rollback selection, and A1/A2 validation confidence before reconstructed `IrGpu` source can become the normal production input. The lowerer/source-switching split now makes this explicit: OpenCL may assemble reconstructed source for validation, but the final production decision is attached only after runtime evidence is available.
- [x] Add backend compile log/source-map/runtime-load metadata.
  `GpuBackendModuleArtifact` now carries the first backend metadata slice: source origin, source/binary availability, optional compile-log resource, optional source-map resource, and runtime load mode. Runtime dumps emit `backend-module.properties`, `backend-source-map.properties`, and `backend-diagnostics.properties`, so CI can compare backend source availability, binary availability, compile-log/source-map identity, load-mode drift, current `IrGpu` source-location anchors, method-body role/name/emitted-name mappings, selected source path, and transitional blockers in one compact diagnostic surface. Deeper compile log/source-map integration remains open for real backend compiler outputs and future binary-capable CUDA/Vulkan/Metal paths.

#### I3.4 Runtime IR optimizer entrypoint

- [x] Add the first runtime IR optimizer entrypoint.
  `GpuRuntimeIrOptimizerRegistry` runs opt-in optimizer hooks with the runtime compile request, target profile, compile options, and loaded `IrGpu` artifact before backend compilation.
- [x] Split optimizer stages into explicit passes.
  Runtime optimization now has explicit stage/pass surfaces (`GpuRuntimeIrOptimizationStage`, `GpuRuntimeIrOptimizationPass`) and legacy optimizer hooks are adapted into staged transform passes for compatibility.
- [x] Keep every mutating pass rollback-safe.
  Runtime optimizer reports carry original/transformed IR identities, proof status, diagnostics, and rollback reasons; the registry now discards artifacts from failed or rolled-back passes and stops the pipeline on rollback.
- [x] Support optimizer profile plumbing.
  Compile options carry an `optimizationProfile`; default remains `off`, and tests verify it reaches runtime compile requests and cache identity.
- [x] Reuse I2 artifacts as the proof/report layer.
  Runtime optimizer pass reports now accept stable proof artifacts as field maps, so optional I2 validation outputs can be attached to proof status, rollback diagnostics, and artifact dumps without making the core runtime depend on the `ir-validation` module.
- [x] Add optimizer reports and rollback diagnostics.
  Runtime optimizer hooks now emit structured pass reports with original/transformed IR identities, proof status, rollback reasons, diagnostics, and failure-safe report capture. Runtime compile snapshots now carry the explicit selected-IR decision as part of the backend-neutral snapshot, while artifact dumps expose it as `optimizer-report.txt` and `runtime-ir-handoff.properties` with original/optimized/selected identities, pass-through vs transformed handoff, rollback/fallback rejection state, production IR acceptance state, and backend lowering target before the backend consumes the IR.

#### I3.5 Vendor/device-specific optimization strategy

- [x] Add initial target profile extraction from runtime devices.
  Runtime compile requests now include backend, vendor, device name, driver version, and runtime version; cache keys separate variants by device profile.
- [x] Expand target profile capability extraction.
  Runtime device profiles and compile provenance now carry compute units, local memory, max work-group size, preferred float vector width, double/image support, and subgroup-like capability flags. OpenCL sessions populate these where available while preserving unknown-safe fallbacks for mock and future backend profiles.
- [x] Add pluggable `GpuOptimizationStrategy` by backend/vendor/device family.
  Runtime optimization now has an advisory strategy hook and default OpenCL vendor-family decisions for NVIDIA, AMD, Intel, and unknown vendors. Strategies are diagnostic-only until evidence-backed promotion gates exist.
- [x] Add strategy selection diagnostics.
  Optimizer reports and artifact dumps now include strategy name, device family, selected profile, advisory/evidence flags, reason, and diagnostics so runtime artifacts explain why a strategy was selected, skipped, or downgraded.
- [x] Keep vendor strategies advisory until evidence-backed.
  Default NVIDIA/AMD/Intel runtime strategies now explicitly report `advisoryOnly=true` and `evidenceBacked=false`; strategy decisions only annotate optimizer reports and cannot bypass proof, post-transform validation, or rollback gates.
- [x] Add per-vendor baselines.
  Strategy diagnostics now carry per-vendor baseline state: current NVIDIA RTX 3060/RTX 5070 and AMD RX 7800 XT baselines are recorded as development guidance, while Intel remains `pending-hardware` and all vendor baselines report `promotionEligible=false` until runtime-equivalence and broader cross-vendor evidence exists.

#### I3.6 Runtime-equivalence and promotion gates

- [x] Add pre/post runtime-equivalence execution for selected optimized IR.
  Runtime backends now have an opt-in `GpuRuntimeEquivalenceExecutor` / `GpuRuntimeEquivalenceRequest` hook that can compare original and optimized compile requests before promotion. The default OpenCL path remains fail-closed and records safe `not-run` evidence, while backend/test overrides can persist real `passed` / `failed` evidence that feeds fallback and production-gate artifacts.
- [x] Persist runtime-equivalence evidence per backend/vendor/profile.
  Runtime compile snapshots now carry a structured `runtime-equivalence.properties` artifact with status, backend, vendor, device, optimization profile, execution/equivalence flags, input/output counts, and diagnostics. The first implementation records safe `not-run` evidence until real pre/post execution is wired.
- [x] Add rollback-on-failure at runtime.
  Runtime compile snapshots now include `fallback.properties` with fallback decision, original/optimized selection flags, and diagnostics. Equivalence failures and optimizer rollback reports automatically mark optimized IR as rejected and keep the original IR path as the safe selected artifact until real production mutation is enabled.
- [x] Gate production optimizer profile behind A1/A2.
  Runtime compile artifacts now include a fail-closed `production-optimizer-gate.properties` diagnostic artifact plus `runtime-production-mutation-safety.properties`, which makes the user/CI-facing answer explicit: runtime IR can participate in diagnostics and backend handoff tracking while production mutation remains disabled unless the production gate is accepted and a transformed optimized IR is selected. Runtime IR selection now also enforces that contract directly: production-like profiles such as `production`, `vendor-tuned`, and `runtime-tuned` select the original IR when the production IR acceptance gate is blocked, report `fallbackDecision=production-ir-gate-blocked`, and mark the optimized IR as rejected instead of letting diagnostic optimized IR reach production lowering. A production-enabled fixture covers the opposite path: when runtime equivalence passes, fallback evidence is clean, the strategy is evidence-backed/non-advisory, the vendor baseline is promotion-eligible, production source switching is enabled, and `productionPromotion.decisionMode=production-enabled`, the selected runtime IR remains optimized and the artifacts report `productionMutationEnabled=true`. Real workload promotion gates and OpenCL validation report/history surface per-kernel production mutation state beside the selected runtime IR stage, so diagnostic handoff cannot be mistaken for enabled production mutation. Current NVIDIA/AMD/Intel strategies therefore stay diagnostic-only until A1/A2 evidence is strong enough for promotion.
- [x] Add CI artifact checks for runtime optimizer drift.
  Runtime artifact dumps now include `runtime-optimizer-drift.properties`, a compact CI-facing summary with optimizer pass counts, applied/skipped/rolled-back/failed counts, fallback decision, selected runtime IR stage/identity, optimized-IR rejection state, selected strategy/profile, vendor baseline status, promotion eligibility, and production gate status so validation history can detect optimizer behavior drift across runs. Real workload promotion gates now also carry per-kernel `runtimeIrHandoff.*` fields, and OpenCL validation report/history surfaces the selected runtime IR stage (`original` / `optimized`) beside source-switching evidence for each workload kernel.
- [x] Add an I3 readiness summary artifact.
  Runtime dumps now include `i3-readiness-summary.properties`, which aggregates backend source reconstruction/parity, source-ready state, runtime equivalence, source-promotion gate status, runtime IR handoff, production optimizer gate status, production mutation safety, and blockers into one compact CI-facing artifact. OpenCL validation reports also write `i3-readiness-workload-summary.properties`, a workload-level CI summary that counts `review-ready` / `blocked` / `production-enabled` kernels, records aggregate `sourceReady.count` / `sourceReady.all`, and keeps per-kernel runtime IR stage, source-ready state, source-promotion status, optimizer gate status, production-mutation flag, and first diagnostic for drift tracking across vendor runners.
- [x] Add I3 readiness workload baseline comparison tasks.
  Gradle now exposes `writeOpenClI3ReadinessWorkloadBaseline`, `validateOpenClI3ReadinessWorkloadBaseline`, and `validateOpenClI3ReadinessBaselineContract` so CI or a vendor runner can persist a reviewed `i3-readiness-workload-summary.properties` snapshot, compare later runs, fail on readiness/runtime-IR regressions or accidental production mutation, and allow forward progress such as `blocked -> review-ready` without baseline churn.
- [x] Add production-promotion explainability artifacts.
  OpenCL validation reports now write `production-promotion-explainability.properties`, a compact workload-level artifact that explains why production source switching and production mutation remain disabled, including source-promotion gate state, I3 readiness counts, aggregate `i3SourceReady.*` evidence, production source-switching state, production mutation state, ordered blockers, and a first diagnostic. Incomplete source readiness now has its own fail-closed blocker (`i3-source-readiness-not-complete`) instead of being hidden behind generic I3 readiness. The workload source-promotion gate and final explainability artifact now also record aggregate production source-switching evidence (`productionSourceSwitchingEnabled.*`, `productionPromotionDecisionMode.productionEnabled.*`, and `sourceSwitching.productionDecision.*`). Production explainability only reports source switching as allowed when every workload kernel is review-ready, parity/equivalence passed, production source switching is enabled, the promotion decision is `production-enabled`, and the source-switching decision compiled reconstructed `IrGpu` source for production; the contract validator rejects `production-ready` artifacts missing those count/all guarantees.
- [x] Add production-promotion explainability contract gates.
  `GpuProductionPromotionExplainabilityValidation` now owns the backend-neutral production-promotion contract in runtime code, with `GpuProductionPromotionExplainabilityValidatorCli` providing the CI entrypoint. Gradle exposes `validateOpenClProductionPromotionExplainability` and `validateOpenClProductionPromotionExplainabilityContract` as thin wrappers so CI can enforce the same workload-level contract OpenCL/CUDA/Vulkan/Metal can reuse later. Blocked artifacts must stay fail-closed with source switching and production mutation disabled plus at least one blocker, while a future `production-ready` artifact must have zero blockers, all workload kernels I3 review-ready, and explicit source-switching / mutation toggles enabled. Generated `production-promotion-explainability.properties` now also carries `contract.status`, `contract.valid`, and `contract.violation.*` fields so CI can read the contract result without parsing markdown or validation history.
- [x] Add runtime-facing production-promotion decision states.
  `GpuProductionPromotionDecision` translates production-promotion explainability into the small set of runtime-facing modes needed by the future production IR pipeline: `diagnostic-only`, `review-ready`, and `production-enabled`. Generated explainability artifacts now include `decision.*` fields so runtime and CI callers can consume the promotion mode directly instead of reinterpreting every gate field.

#### I3.7 Storage and cache model

- [x] Replace descriptor-only runtime compile cache identity with compile-request-aware keys.
  Cache identity now includes descriptor metadata, compile options, optimizer profile, target device profile, and lowered backend module artifact identity so backend/optimizer variants do not collide.
- [x] Add backend-lowerer/module identity to runtime compile cache keys.
  Different lowered OpenCL module artifacts for the same descriptor are cached separately, which prevents future optimized/unoptimized IR outputs from reusing the wrong compiled kernel.
- [x] Make cache keys aware of stable `IrGpu` artifact identity.
  Cache identity now includes a deterministic SHA-256 identity for the optimized `IrGpu` artifact, so runtime IR rewrites cannot accidentally reuse a compiled kernel produced from another IR payload.
- [x] Add explicit backend artifact and lowerer version fields to cache keys.
  Cache identity now carries both backend artifact format version and backend lowerer version explicitly, so future source/binary artifact upgrades can invalidate compiled kernels without relying only on full artifact equality.
- [x] Store original and optimized artifacts separately.
  Runtime compile artifacts now keep a backend-neutral snapshot with original `IrGpu`, optimized `IrGpu`, explicit selected `IrGpu` handoff state, lowered backend module, compile log placeholder, and runtime validation evidence placeholder attached to compiled kernels. Snapshot mutators refresh the stored runtime selection when fallback, equivalence, optimizer report, or compile provenance changes, so dump artifacts and backend lowering consume the same source-of-truth decision.
- [x] Add cache invalidation for compiler/backend/optimizer upgrades.
  Runtime cache identity now includes an invalidation stamp with IR format/schema, compiler artifact, source frontend, backend artifact version, backend lowerer version, and optimizer pipeline version, preventing stale compiled kernels from being reused after those pipeline pieces change.
- [x] Preserve debug/source maps through runtime compilation.
  `IrGpu` method bodies now carry source-location metadata, manifests serialize/parse it with backward-compatible defaults, and runtime compile snapshots preserve the source anchors alongside original/optimized IR and lowered backend artifacts.
- [x] Add tooling to dump `IrGpu`, optimized `IrGpu`, and backend output.
  `GpuRuntimeCompileArtifactDumper` now turns runtime compile snapshots into comparable text artifacts for original `IrGpu`, optimized `IrGpu`, selected runtime `IrGpu`, lowered backend source, compile logs, runtime validation evidence, source anchors, invalidation stamps, and the explicit `runtime-ir-handoff.properties` contract that explains which IR artifact is handed to backend lowering and why production IR source selection remains accepted, review-only, or blocked.

#### I3.8 Migration plan from current OpenCL build output

- [x] Phase 1: dual output.
  Build produces current OpenCL source and new `IrGpu`; runtime still uses OpenCL source by default.
- [x] Phase 2: OpenCL-from-`IrGpu` parity for the current workload slice.
  The runtime guard validates the transitional `IrGpu` -> derived OpenCL source-resource contract before OpenCL lowering. The current OpenCL workload slice can reconstruct source, match descriptor parity, pass runtime-equivalence checks for all five validation kernels, and an explicit `opencl.sourceSelection=irgpu` smoke can compile reconstructed source on a real OpenCL device. Full production source switching remains intentionally open until `opencl.productionSourceSwitching=enabled` is deliberately set and A1/A2 evidence is strong enough.
- [x] Phase 3 foundation: public runtime compile options.
  Users can pass OpenCL-targeted compile args and optimizer profile through direct runtime calls, generated launchers, and reflection launcher helpers. The same option surface now includes the fail-closed `opencl.sourceSelection` switch for descriptor-source default compilation versus opt-in reconstructed `IrGpu` source compilation, plus the separate `opencl.productionSourceSwitching` gate for future production rollout.
- [x] Phase 4 foundation: opt-in prototype optimization infrastructure.
  Runtime can run proof-backed optimization passes with rollback, stores original/optimized `IrGpu` separately, emits optimizer reports, runtime-equivalence evidence, fallback evidence, runtime IR handoff, optimizer drift, and production mutation safety artifacts, and remains disabled/fail-closed for production mutation by default.
- [ ] Phase 4 promotion: real optimizer families with runtime-equivalence proof.
  This remains open until concrete optimizer transforms graduate from read-only/diagnostic evidence to repeatedly passing runtime-equivalence checks under A1/A2 validation.
- [ ] Phase 5: multi-backend lowerer expansion.
  Add CUDA/Vulkan/Metal lowerer stubs first, then implement real backend support behind the same `IrGpu` contract.
- [ ] Phase 6: evidence-backed production optimization.
  Selected optimizer families can graduate only after A1/A2 evidence, backend-specific baselines, and rollback confidence are stable.

## Recommended Execution Order

If the goal is to move forward pragmatically from the current state, the best order is now:

1. `A1/A2 NVIDIA/AMD interim operational validation`
   Keep repeating `:processor:openClOperationalRoutine --rerun-tasks` on the available RTX 3060, RTX 5070, and RX 7800 XT stacks until Intel hardware exists, preserving validation history, workload summaries, long-running summaries, and benchmark output.
2. `A3/A4 Diagnostics and runtime-stability fixes from real failures`
   Treat any repeated NVIDIA/AMD failure, skipped workload, resource leak, or diagnostic gap as the next concrete implementation target.
3. `I3 Production IR pipeline foundation`
   Start with the non-mutating foundation: canonical `IrGpu` artifact, dual output beside current OpenCL source, OpenCL-from-`IrGpu` parity, runtime compile request/options, backend-lowering boundary, and source/ASM frontends feeding the same IR storage path. Keep runtime optimization profile `off` by default.
4. `A1 Intel runner bring-up when hardware exists`
   The remaining cross-vendor production gate stays open until an Intel OpenCL stack can run the same bucket set.
5. `Improved ASM parser/frontend`
   Broader ASM ingestion should lower into `IrGpu` first, then reuse the same runtime compile request, validation, backend lowering, and future optimizer path instead of growing a separate pipeline. The first broader-ASM diagnostics layer is already in place through `asmFailure.*` metadata and public preflight APIs.
6. `I3 prototype runtime optimization`
   After `IrGpu` storage and OpenCL parity are stable, add opt-in prototype optimization with rollback, pre/post runtime-equivalence artifacts, and NVIDIA/AMD evidence first. Keep production mutation disabled until A1/A2 confidence and the future Intel gate are satisfied.
7. `H Lower-priority backend/optimization work`

## Current Working Conclusion

JavaToGpu is already beyond the "toy compiler" stage.

The broad repetitive intrinsic-family generation detour is now closed for current priorities, so the active focus returns to production-core/runtime validation rather than expanding optional API symmetry.

After the first serious dogfooding pass, the main repo-local language/runtime gaps for the selected workload classes are no longer the active blocker. Section C and F1 are closed for current practical workload coverage: 3D launch config, union-style packed views, root-blob ergonomics, launch-sensitive attributes, and the focused packed/root-blob dogfooding slice are all covered. Operational confidence now includes NVIDIA and AMD lanes; Intel remains the remaining hardware validation gap. The next major architecture frontier is the I3 Production IR pipeline: `IrGpu` should become the stored backend-neutral artifact, while OpenCL/CUDA/Vulkan/Metal become backend lowerers selected at runtime from compile options and device profile. Broader ASM ingestion should feed that same `IrGpu` path instead of creating a second compiler pipeline.

Latest I2/L2 reconciliation: the current CI contract layer is closed for present priorities. Direct contract coverage now pins current readiness CI summaries, optimizer-gate consistency/acceptance, optimizer CI gate-index consistency/acceptance, regression/baseline CI summaries, nested artifact-field composition, and opt-in real-example dogfooding reports for `examples-app` / `test-app`. The dogfooding contract keeps safety fail-fast while treating optimizer readiness as a read-only stability artifact: production mutation must remain disabled, rewrite applicability must stay false, known CSE policy blockers are counted, and no-candidate CSE literal-promotion / auto-vectorization blockers are accepted as the current baseline. The remaining I2 items are intentionally broader frontier work: deeper transformation-safety proofs, stronger canonicalization/common-computation detection, runtime-equivalence evidence for selected optimizer families, and eventual production rewrite promotion after A1/A2 runtime confidence is stable. These should stay open until backed by runtime evidence rather than being marked complete from read-only artifact coverage alone.














