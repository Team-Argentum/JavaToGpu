# IR Optimizer Journal Example

This example shows how to opt into the optional IR optimizer module and write runtime artifacts for local inspection.

The important source files are:

```text
examples-app/build.gradle
examples-app/src/main/java/net/sixik/ga_utils/examples/OptimizationJournalExample.java
```

`examples-app/build.gradle` adds the optional optimizer module and registers a dedicated runnable task:

```groovy
implementation project(':ir-optimizer')
tasks.register('runOptimizationJournalExample', JavaExec) { ... }
```

`OptimizationJournalExample` enables a diagnostic optimization profile for one generated launcher invocation:

```java
GpuRuntimeCompileOptions.openCl(List.of(), "diagnostic")
```

To compile the optimized IR candidate during a local experiment, switch that call to the explicit apply preset:

```java
GpuRuntimeCompileOptions.openClIrOptimizerExperimentalApply(List.of(), "diagnostic")
```

This is intentionally separate from the default journal mode. `openCl(...)` dumps before/after artifacts while keeping `backend.opencl-c` on the original selected source. `openClIrOptimizerExperimentalApply(...)` lets the validated optimized artifact become the selected runtime IR for that invocation, so `backend.opencl-c` can match `optimized.backend.opencl-c` when runtime-equivalence and production gates do not reject it.

It also enables the runtime artifact journal by setting:

```text
javatogpu.opencl.runtimeCompileArtifactDirectory=<journal-dir>
```

## Run The Example

Run it from the repository root:

```powershell
.\gradlew.bat :examples-app:runOptimizationJournalExample --console=plain
```

Use a custom artifact directory:

```powershell
.\gradlew.bat :examples-app:runOptimizationJournalExample -Pjavatogpu.optimizerJournalDir=build\reports\my-ir-journal --console=plain
```

## Journal Files

Look for these files under the generated per-kernel subdirectory:

```text
backend.opencl-c
original.backend.opencl-c
optimized.backend.opencl-c
original.irgpu.properties
optimized.irgpu.properties
runtime-ir-handoff.properties
optimizer-report.txt
runtime-ir-optimizer-evidence.properties
```

`original.backend.opencl-c` and `optimized.backend.opencl-c` are the before/after backend source files for comparing generated OpenCL around the optimizer boundary. `optimized.backend.opencl-c` may come from an accumulated review-only materialized candidate, including backend-neutral source materialization metadata, the constrained integer constant-folding transform, safe-local-CSE materialization, the fast-math `mad/fma` peephole materializer, `clamp` materialization, `step` materialization, `mix` materialization, or fixed-width loop vectorization. Constant folding supports plain 32-bit integer literal unary `-`, binary `+`, `-`, `*`, exact `/`, nested safe literal patterns, and the current pure-symbolic identity slice (`x + 0`, `0 + x`, `x - 0`, `x * 1`, `1 * x`, `x / 1`, mixed chains such as `((x / 1) + 0)`, repeated identities across multiple roots, and multiple method bodies) over deterministic retained expressions made from named variables, plain int32 literal leaves, arithmetic binary nodes, and unary minus nodes. Safe-local-CSE materialization reuses existing local bindings in straight-line `ir-text-v1`, and can now introduce conservative local temporaries for repeated pure binary expressions such as `(value * 1.414F)` when all text/typed occurrences line up and no input variable is reassigned between uses. Mad/FMA materialization requires `@GPUOptimize(fastMath = true)` and rewrites reachable `a * b + c` / `c + a * b` text plus typed nodes to `intrinsic(mad template="" args=[a, b, c])`; it also rewrites reachable `a * b - c` to `intrinsic(mad template="" args=[a, b, (-c)])` in the optimized review artifact. `x * 0`, `1 / x`, broader division algebra, control-flow-spanning CSE, reordered clamp/minmax variants, non-mask ternaries, and mix forms outside the current canonical / expanded / MAD-expanded shapes remain excluded for now. `backend.opencl-c` is the selected backend source code that the OpenCL backend actually compiles. `original.irgpu.properties` and `optimized.irgpu.properties` are the matching before/after IR files. `runtime-ir-optimizer-evidence.properties` is the machine-readable guardrail summary that keeps the current alpha fail-closed; for materialized review candidates, check `backendNeutralSourceMaterialization.*`, `constantFoldingMaterialization.*`, `safeLocalCseMaterialization.*`, `madFmaMaterialization.*`, `clampMaterialization.*`, `stepMaterialization.*`, `mixMaterialization.*`, `loopVectorizationMaterialization.*`, `typedDeadCodeMaterialization.*`, `runtimeEquivalenceReview.*`, `approvalTemplate.runtimeEquivalencePayload*.count`, `pass.N.approvalTemplate.field.resourcePath`, `pass.N.approvalTemplate.field.approvalManifest.*`, `reviewPackage.approvalManifest.*`, and the durable `runtime-optimizer-family-equivalence-payload/` files to see source-ready counts, transformed-node counts, literal/identity rewrite counts, fixed-point-pass counts, existing-local and introduced-temporary CSE counts, mad/FMA fast-math policy, clamp/step/mix intrinsic counts, loop-vectorization counts, loop typed-body rebuild status via `loopVectorizationMaterialization.typedBody.rebuild.*`, body-text replacement counts, reachable-node scan / rewrite-granularity fields, per-case method/node identity, exact-int / symbolic-identity / local-CSE-reuse / fast-math-mad / intrinsic-materialization payload evidence, approval payload readiness, loaded/accepted manifest evidence, the review manifest location, and the remaining manual-review/approval blocker.

Typed dead-code materialization is also review-only. When it participates, check `typedDeadCodeMaterialization.*` for typed/unreachable/removed node counts, side-effect-freedom proof, status, first blocker, and payload pass counts. It removes only unreachable pure typed nodes in the optimized review artifact, leaves method text bodies unchanged, and does not make `backend.opencl-c` switch to the optimized source.

Safe-local-CSE materialization is review-only too. When it participates, check `safeLocalCseMaterialization.*` for existing local binding count, introduced temporary count, transformed-node count, body-text replacement count, fixed-point pass count, status, first blocker, and payload pass counts. It can make `optimized.backend.opencl-c` visibly different by reusing or introducing local values, but it still does not make `backend.opencl-c` switch to the optimized source.

Mad/FMA materialization is review-only as well. When it participates, check `madFmaMaterialization.*` for candidate/transformed-node counts, body-text replacement count, fixed-point pass count, skipped fast-math policy count, `fastMathAllowed`, status, first blocker, and payload pass counts. The raw pass proof fields still include `optimizerFamily=mad-fma-materialization`, `targetIntrinsic=mad`, and per-case `runtimeEquivalencePayload.Case.*`; subtract cases report `RewriteKind=multiply-subtract-to-opencl-mad`. It can make `optimized.backend.opencl-c` show `mad(...)`, but `backend.opencl-c` remains the selected source unless future proof/approval/selection gates explicitly allow otherwise.

Clamp, step, and mix materialization are review-only as well. In this example, `computeVoxelDensity` intentionally contains `min(max(wave1, -1.0f), 1.0f)`, a strict ternary mask `blend > threshold ? 1.0f : 0.0f`, canonical interpolation, and expanded weighted interpolation shapes. When the optional optimizer bridge participates, `optimized.backend.opencl-c` can show `clamp(...)`, `step(...)`, and `mix(...)` candidates while `backend.opencl-c` remains the selected source. Check `clampMaterialization.*`, `stepMaterialization.*`, and `mixMaterialization.*` for candidate/transformed-node counts, direct versus inverted step counts, canonical/expanded/MAD-expanded mix counts, fast-math requirements, body-text replacement counts, status, first blocker, and payload pass counts.

To tell whether `backend.opencl-c` came from optimized IR or from the original/pass-through IR, open `runtime-ir-handoff.properties` and check `selectedStage` plus `optimizedDiffersFromOriginal`.

Current alpha behavior is intentionally fail-closed by default: optimizer evidence and proposed artifacts can be dumped for review, but production mutation and selected-IR replacement remain disabled unless explicit apply/proof/approval/production gates allow them. If you validate an experimental-apply journal with the raw evidence CLI, use `validateOpenClRuntimeIrOptimizerEvidence --allow-experimental-apply <artifact-or-directory>`; without that flag, the validator rejects applied optimized-artifact selection on purpose.
