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
| Add or preview a backend family | `GpuRuntimeBackendProvider` | Start discovery-only, then add execution stages |
| Add backend-stage facts or hooks | `GpuBackendHook` family | Keep defaults read-only and fail-soft |
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
| Backend family registration | `GpuRuntimeBackendProvider` | Stage-specific | Discovery can be read-only; compile/invoke becomes production-affecting. |
| Backend policy/discovery/stage hooks | `GpuBackendPolicyContributor`, `GpuRuntimeBackendScoreContributor`, `GpuBackendDiscoveryContributor`, `GpuBackendLoweringHook`, `GpuBackendCompilationHook`, `GpuBackendInvocationHook`, `GpuBackendArtifactHook` | Read-only by default | Passive contracts for future ServiceLoader registries; production-affecting hooks must opt into stronger permission explicitly. |
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
For normal generated-launcher calls with `withStandardBackendDevicePreflight()`, lifecycle services can already observe
facade preflight start/completion, backend selection, device discovery, backend compilation, invocation, artifact dumps,
and shutdown where the active backend emits them.
For lifecycle tooling, prefer stable `runtime.*` fields such as `runtime.kernel.name`, `runtime.backend.target`,
`runtime.device.label`, `runtime.irgpu.present`, `runtime.module.format`, `runtime.ir.selectedStage`,
`runtime.ir.fallbackDecision`, `runtime.fallback.decision`, `runtime.work.globalShape`, and `runtime.status`;
backend-specific legacy fields may exist, but they are not the portable trace contract.
Compile, invocation, and shutdown events also expose portable backend state counters such as
`runtime.backend.cache.mode`, `runtime.backend.cache.compiledKernel.count`,
`runtime.backend.cache.compileHit.count`, `runtime.backend.compile.count`, and
`runtime.backend.invocation.count`; `runtime.backend.state.present` marks events that carry this typed runtime-state
summary. Backend-compilation events expose `runtime.compilation.*` summaries for cache-key presence, module
presence/format, compile-log presence, binary-artifact count, and validation-evidence count.
Invocation events additionally expose backend-neutral binding summaries under
`runtime.invocation.binding.*`, including `runtime.invocation.binding.buffer.count`,
`runtime.invocation.binding.local.count`, `runtime.invocation.binding.scalar.count`, and
`runtime.invocation.binding.argument.count`; OpenCL keeps older `bufferBinding.count` / `argumentBinding.count`
aliases only for compatibility. Artifact-dump events expose `runtime.artifactDump.*` summaries for planned output
directories and completed text/binary/source-location artifact counts.
Backend source-selection events expose portable `runtime.backend.source.*` fields such as
`runtime.backend.source.status`, `runtime.backend.source.decision`, `runtime.backend.source.selection`,
`runtime.backend.source.available`, `runtime.backend.source.promotionFirstBlocker`, and
`runtime.backend.source.runtimeLoadMode`. Use these fields to understand why a backend compiled descriptor source,
selected reconstructed `IrGpu` source, or failed closed before native compilation.
Workload source-promotion artifacts mirror the same vocabulary under `kernel.N.runtime.backend.source.*`, while legacy
`kernel.N.sourceSwitching.*` fields remain available for compatibility. Report and history readers prefer the portable
fields first, so new backend adapters can emit `kernel.N.runtime.backend.source.*` without copying OpenCL-specific
`sourceSwitching.*` keys.
Workload-level source-decision aggregates should use indexed `runtime.backend.source.decision.*` fields; compact CI
summaries expose the same aggregate as `runtime.backend.source.decisions` while retaining `sourceSwitching.decisions`
for older consumers. Compact summaries also expose `runtime.backend.source.promotionFirstBlockers` and
`runtime.backend.source.promotionFirstBlockerFamilies` alongside the unprefixed compatibility summaries.
Workload-level blocker aggregates should use `runtime.backend.source.promotionFirstBlocker.*` and
`runtime.backend.source.promotionFirstBlockerFamily.*`; legacy `sourceSwitching.sourcePromotionFirstBlocker.*` and
`sourceSwitching.sourcePromotionFirstBlockerFamily.*` keys are compatibility mirrors for older OpenCL tooling.
Aggregate production source-readiness evidence follows the same rule: emit
`runtime.backend.source.productionSwitchingEnabled.*`,
`runtime.backend.source.productionPromotionDecisionMode.productionEnabled.*`,
`runtime.backend.source.productionPromotionOperatorAccepted.*`, and
`runtime.backend.source.productionDecision.*` for workload/explainability artifacts. Keep
`productionSourceSwitchingEnabled.*`, `productionPromotionDecisionMode.productionEnabled.*`,
`productionPromotionOperatorAccepted.*`, and `sourceSwitching.productionDecision.*` only as compatibility mirrors, and
read the portable fields first when both are present.
Runtime IR production-mutation evidence uses `runtime.ir.productionMutation.*`. The most useful fields are
`runtime.ir.productionMutation.enabled`, `runtime.ir.productionMutation.status`,
`runtime.ir.productionMutation.productionGateStatus`, `runtime.ir.productionMutation.selectedStage`,
`runtime.ir.productionMutation.optimizedSelected`, `runtime.ir.productionMutation.optimizedDiffersFromOriginal`,
`runtime.ir.productionMutation.optimizedIrRejected`, `runtime.ir.productionMutation.fallbackDecision`, and
`runtime.ir.productionMutation.diagnostic`. New reports should read those fields before falling back to the older
`runtimeProductionMutationSafety.*` keys.
Controlled production validation evidence uses `runtime.production.*`. Source-switching readiness lives under
`runtime.production.sourceSwitching.controlled.*`, mutation-readiness lives under
`runtime.production.mutation.controlled.*`, activation-token smoke lives under
`runtime.production.activationToken.smoke.*`, and negative controls live under
`runtime.production.activationToken.negative.*`. Older `controlledProductionSourceSwitching.*`,
`controlledProductionMutation.*`, `controlledProductionActivationTokenSmoke.*`, and
`controlledProductionActivationTokenNegative.*` keys are compatibility mirrors.
Controlled activation-gate artifacts use `runtime.production.activationGate.*` for the gate status, readiness, scope,
safe default switches, approval/device identity, kernel coverage, operator acceptance, blockers, and diagnostics. The
runtime token loader reads those portable fields first and falls back to the older unprefixed activation-gate fields.
Production-candidate and manual-approval artifacts follow the same rule with `runtime.production.candidateGate.*` and
`runtime.production.manifest.*`; manifest, activation-gate, and validation-report readers prefer those portable fields
before falling back to legacy unprefixed, `binding.*`, or `authorization.*` keys.
New runtime artifact readers and writers should use `GpuRuntimeArtifactProperties` for portable-first lookup,
`StringBuilder` property writing, `Properties` artifacts, and map field writing instead of hand-rolling
backend-specific fallback order.
Automatic backend/device preflight events use that same vocabulary and add `runtime.backendDevicePreflight.*` plus
`runtime.failure.*` when the scoped preflight backend fails. Failure fields keep the legacy `type` / `message` keys and
also expose backend-neutral `code`, `phase`, `category`, `summary`, `catchable`, `cause.*`, `suppressed.count`, optional
`help.*`, and `context.*` facts when the failure is a `GpuRuntimeException`.
Selection/discovery events also expose portable fields such as `runtime.selection.status`,
`runtime.backend.selection.matched`, `runtime.device.discovery.available`, and selected `runtime.device.*` facts.
CUDA inventory-only discovery additionally exposes `runtime.device.cuda.runtimeVersion` and
`runtime.device.cuda.computeCapability` when those facts are available from `nvidia-smi`.
Backend adapter artifact maps use the same convention with `runtime.backend.adapter.*` and
`runtime.backend.lowerer.*`, allowing tools to inspect OpenCL, CUDA inventory-only, and planned adapters uniformly.
Backend selection, device discovery, and combined runtime-selection artifact maps carry those portable fields beside
their older prefixed compatibility keys.
Backend compile, invocation, runtime-state, and artifact-dump lifecycle events should be composed through
`GpuRuntimeLifecycleFields` so adapters share the same `runtime.cache.*`, `runtime.module.*`, `runtime.work.*`,
`runtime.compilation.*`, `runtime.invocation.binding.*`, `runtime.artifactDump.*`, `runtime.backend.state.*`,
`runtime.backend.*`, and `runtime.failure.*` contract while keeping any backend-specific compatibility aliases separate.
Lifecycle event reports preserve indexed event fields and additionally copy `runtime.*` fields to direct
`runtimeLifecycle.event.runtime.*` properties for simpler journal consumers.

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

## Backend Provider SPI

Use `GpuRuntimeBackendProvider` when a module wants to register a backend family such as CUDA, Vulkan/SPIR-V, Metal, or
a company-specific runtime adapter. A provider is intentionally lightweight: it reports a stable provider id/version,
backend target, deterministic order, and creates a `GpuRuntimeBackendAdapter` without opening native sessions during
catalog inspection.

Register it with ServiceLoader:

```text
META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider
```

Minimal discovery-only provider shape:

```java
package com.example.gpu;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapter;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProvider;

public final class ExampleBackendProvider implements GpuRuntimeBackendProvider {
    @Override
    public GpuBackendTarget backendTarget() {
        return GpuBackendTarget.CUDA;
    }

    @Override
    public String providerId() {
        return "example.cuda-provider";
    }

    @Override
    public String providerVersion() {
        return "1";
    }

    @Override
    public int providerOrder() {
        return 1_000;
    }

    @Override
    public GpuRuntimeBackendAdapter createAdapter() {
        return new ExampleCudaDiscoveryOnlyAdapter();
    }
}
```

For a runnable checklist of the full provider path, use:

```powershell
.\gradlew.bat :examples-app:runBackendProviderAuthoringExample --console=plain
```

It prints three local providers that model the expected progression: discovery-only, lowering-only, and production
pipeline with a shared runner factory.

To inspect what an application will actually see after built-ins and ServiceLoader providers are merged, use the
provider catalog:

```java
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendProviderCatalog;

String report = GpuRuntimeBackendProviderCatalog
        .standardWithPlannedBackends()
        .toMarkdown();

System.out.println(report);
```

The catalog does not create native runtime sessions. It validates provider ids/orders, keeps deterministic ordering,
reports execution availability for each provider, and exposes artifact fields for CI or diagnostics.

Provider ids must be unique. Built-in OpenCL, CUDA inventory, Vulkan placeholder, and Metal placeholder providers use
the same registry path as external providers, and duplicate ids fail fast with a clear diagnostic.

Every provider should also be honest about execution readiness through `executionSupport()`:

| Provider state | What to return | Meaning |
| --- | --- | --- |
| Discovery-only | `GpuRuntimeBackendExecutionSupport.discoveryOnly(...)` | The backend can appear in catalogs/device reports, but must not execute kernels. |
| Lowering-only | `GpuRuntimeBackendExecutionSupport.loweringOnly(...)` | The backend can produce native source/artifacts, but compile/invoke is not ready. |
| Production pipeline | `GpuRuntimeBackendExecutionSupport.productionPipeline(...)` | The backend can compile, prepare, invoke, read back, and clean up through the shared stage vocabulary. |

The same support card should declare the backend-family module formats and capability vocabulary it can report or consume.
That metadata is safe to inspect without opening native sessions:

```java
return GpuRuntimeBackendExecutionSupport.discoveryOnly(
        GpuBackendTarget.CUDA,
        "example.cuda-provider",
        Set.of(GpuBackendModuleFormat.CUDA_C, GpuBackendModuleFormat.PTX),
        Set.of(
                GpuRuntimeCapability.DRIVER_VERSION,
                GpuRuntimeCapability.RUNTIME_VERSION,
                GpuRuntimeCapability.COMPUTE_CAPABILITY,
                GpuRuntimeCapability.GLOBAL_MEMORY
        ),
        "CUDA discovery is available, execution is not enabled yet"
);
```

This still does not mean every device supports every optional feature. Device-specific truth stays on
`GpuRuntimeDeviceProfile` and runtime capability checks. Provider metadata answers a different question: which module
formats and capability facts this backend family knows how to expose through the shared API.

When a policy is built from `GpuRuntimeBackendCatalog` entries, this provider metadata is attached to every backend
candidate as `GpuRuntimeBackendCandidateMetadata`. The selection explanation keeps the old compact candidate summary,
then adds readable lines such as `moduleFormats: cuda-c,ptx`, `capabilityVocabulary: compute-capability,...`, and
`executionPipeline: available=false`. Artifact maps expose the same facts under candidate-local
`*.executionSupport.*` fields, so CLIs, CI reports, and future backend scoring can reason about backend readiness
without opening a native runtime session.

Applications can also turn that metadata into hard selection checks:

```java
GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
        .requireExecutionPipelineAvailable()
        .requireDeclaredModuleFormat(GpuBackendModuleFormat.OPENCL_C)
        .preferStandardBackendsWithPlannedDiagnostics()
        .build();
```

Use `requireDeclaredModuleFormat(...)` to keep a policy on `opencl-c`, `cuda-c`, `ptx`, `spir-v`, or another declared
artifact family. Use `requireDeclaredCapability(...)` when the backend family must know how to report a portable fact
such as compute capability or local memory. Use `requireExecutionPipelineAvailable()` when inventory-only backends should
be rejected before a native compile path is attempted.

Selection explanations also attach `GpuRuntimeBackendCandidateScore` to every candidate. The score records fallback
preference order, provider metadata readiness, runtime report facts, and a reserved policy-adjustment slot, then renders
those values under `*.score.*` artifact fields and a compact Markdown line. Runtime facts currently include API version,
portable runtime capability count, legacy feature flags, local memory, and max work-group size. By default the selector
still uses explicit fallback order and selects the first candidate that passes hard checks. Applications that want
score-based ordering must opt in:

```java
GpuRuntimeBackendPolicy rankedPolicy = GpuRuntimeBackendPolicy.builder()
        .rankCandidatesByScore()
        .preferStandardBackendsWithPlannedDiagnostics()
        .build();
```

In score mode, all candidates are evaluated, hard failures still reject candidates, and the highest total score among
passing candidates wins. Owned candidates that pass hard checks but lose ranking are closed automatically. This mode is
intended for deliberate backend selection experiments and future workload-aware placement; it is not enabled silently.

For workload-specific evidence, attach read-only score contributors explicitly:

```java
GpuRuntimeBackendScoreContributor cudaEvidence = new GpuRuntimeBackendScoreContributor() {
    @Override
    public GpuRuntimeBackendScoreContribution scoreCandidate(GpuRuntimeBackendScoreContext context) {
        if (context.report().backendTarget() != GpuBackendTarget.CUDA) {
            return GpuRuntimeBackendScoreContribution.none();
        }
        return GpuRuntimeBackendScoreContribution.of(25_000, "cached method probe favored CUDA +25000");
    }
};

GpuRuntimeBackendPolicy rankedPolicy = GpuRuntimeBackendPolicy.builder()
        .rankCandidatesByScore()
        .scoreCandidatesWith(cudaEvidence)
        .scoreCandidatesForCompileOptions(GpuRuntimeCompileOptions.cuda(List.of(), Map.of(), "probe-ranked"))
        .preferStandardBackendsWithPlannedDiagnostics()
        .build();
```

Score contributors are not hard gates. They fill the `policyAdjustment` bucket, add diagnostics to the candidate score,
and remain fail-soft. Use `require...` helpers for mandatory backend constraints.

For warmed `@GPUTest` selection-probe evidence, use the built-in cache-only bridge instead of writing a custom
contributor:

```java
GpuRuntimeBackendPolicy rankedPolicy = GpuRuntimeBackendPolicy.builder()
        .rankCandidatesByScore()
        .scoreCandidatesForCompileRequest(request)
        .scoreCandidatesWithCachedMethodTestProbeEvidence()
        .preferStandardBackendsWithPlannedDiagnostics()
        .build();
```

`scoreCandidatesWithCachedMethodTestProbeEvidence()` reads only the configured method-test probe cache from the compile
options stored in `request`. It never compiles or executes probes during backend selection.

For compiler-resource evidence that was already produced by a compile artifact/log, use the built-in compiler feedback
bridge:

```java
GpuBackendCompilerFeedbackReport compilerFeedback =
        GpuBackendCompilerFeedbackRegistry.loadWithBuiltIns().inspect(snapshot);

GpuRuntimeBackendPolicy rankedPolicy = GpuRuntimeBackendPolicy.builder()
        .rankCandidatesByScore()
        .scoreCandidatesWithCompilerFeedback(compilerFeedback)
        .preferStandardBackendsWithPlannedDiagnostics()
        .build();
```

`scoreCandidatesWithCompilerFeedback(report)` is read-only and precomputed. It does not compile candidate backends during
selection, applies only to matching backend targets unless the report target is `UNKNOWN`, and contributes bounded
resource diagnostics to `policyAdjustment`. Use it for ranking hints such as lower register pressure, no spills, no stack
frame, known local-memory usage, and occupancy. It is not a hard requirement and intentionally has much lower weight than
cached `@GPUTest` correctness evidence.

If a backend has a real compile -> prepare -> invoke slice, expose it with `executionPipelineFactory()`. OpenCL already
does this through an `OpenClBackendExecutionPipelineFactory`. CUDA currently stays inventory-only, so its provider does
not publish a pipeline factory yet. This is deliberate: future CUDA work can add execution stage-by-stage without
pretending the backend is production-ready before it is.

For catalog UIs, examples, and CI reports, call `executionAvailability()` on the provider. It returns a compact
`GpuRuntimeBackendExecutionAvailability` card with status, summary, blockers, diagnostics, artifact fields, and Markdown.
For discovery-only providers, the same card can produce `unsupportedExecutionResult(...)`, a typed non-success pipeline
receipt that says exactly which execution stages are missing.

Backend execution should be added stage-by-stage: discovery first, then lowering, compilation, preparation/binding,
invocation, readback, and cleanup. Until a stage exists, return explicit unsupported/skipped diagnostics and portable
`runtime.failure.*` fields instead of throwing backend-specific exceptions.

Backend stages use the shared `GpuBackendPipelineStage` vocabulary: `DISCOVER`, `SELECT`, `LOWER`, `COMPILE`, `LOAD`,
`PREPARE`, `BIND`, `INVOKE`, `READBACK`, and `CLOSE`. Stage results should report a `GpuBackendStageStatus` such as
`SUCCEEDED`, `SKIPPED`, `UNSUPPORTED`, or `FAILED`, then render portable artifact fields through `GpuBackendStageResult`.
Use the typed wrappers as the public receipt for each stage: `GpuBackendLoweringResult`, `GpuBackendCompilationResult`,
`GpuBackendPreparationResult`, and `GpuBackendInvocationResult`. They keep the backend target, status, blockers,
diagnostics, module format, binding summary, launch shape, and readback facts under stable `runtime.*` keys, so tools can
read one journal shape for OpenCL now and CUDA/Vulkan/Metal later. Lowerers can already expose the first wrapper through
`GpuBackendLowerer.lowerWithStageResult(...)` while keeping their existing `lower(...)` implementation intact.

OpenCL already emits the compile/prepare/invoke receipts into its lifecycle events. Adapter authors should mirror that
shape: keep compatibility aliases if an existing backend has them, but always add the typed receipt fields so journal
readers can depend on `runtime.backend.compilation.*`, `runtime.backend.prepare.*`, and `runtime.backend.invoke.*`.

### Execution Handles

Keep native objects private, but expose the shared handle contracts:

| Contract | Purpose |
| --- | --- |
| `GpuBackendCompiledKernel` | A compiled backend module/entrypoint plus cache key and artifact snapshot. |
| `GpuPreparedKernel` | A compiled kernel after arguments, buffers, locals, and scalars are prepared. |
| `GpuBackendKernelCompiler` | The stage that turns a lowered module artifact into a compiled kernel handle. |
| `GpuBackendKernelPreparer` | The stage that binds backend resources before launch. |
| `GpuBackendKernelInvoker` | The stage that launches a prepared kernel and reports readback completion. |
| `GpuRuntimeBackendExecutionSupport` | Provider-level metadata saying which stages are available. |
| `GpuRuntimeBackendExecutionAvailability` | User-facing provider card for execution status, blockers, artifacts, and Markdown. |
| `GpuRuntimeBackendProviderCatalog` | Merged provider catalog for built-ins plus ServiceLoader entries, without native runtime creation. |
| `GpuRuntimeBackendCandidateMetadata` | Catalog/provider metadata copied onto one backend-selection candidate. |
| `GpuBackendExecutionPipelineFactory` | Provider-level factory that binds a shared runner to a concrete backend instance. |
| `GpuBackendExecutionPipeline` | A small runner for compile -> prepare -> invoke when a backend can execute those stages as one slice. |
| `GpuBackendExecutionPipelineResult` | The combined stage receipt returned by the runner. |

OpenCL already adapts its existing `OpenClCompiledKernel`, `OpenClPreparedExecution`, and `OpenClExecutionPreparer` to
these contracts. Its backend routes compile, prepare, and invoke through `kernelCompiler()`, `kernelPreparer()`, and
`kernelInvoker()` accessors backed by small OpenCL adapters, while preserving older protected hooks. New backends should
follow the same pattern: keep CUDA/OpenCL/Vulkan handles strongly typed internally, but expose the portable handle
interfaces so lifecycle, diagnostics, fallback, cache reporting, and future policy logic can read one shape.

Use `GpuBackendExecutionPipeline` when a backend wants the shared runner to perform the simple compile -> prepare ->
invoke sequence and return `GpuBackendCompilationResult`, `GpuBackendPreparationResult`, and `GpuBackendInvocationResult`
together. A backend may still keep richer orchestration around it for cache lookup, lifecycle events, validation,
artifact dumping, and fallback decisions.

There are two runner modes:

| Method | Behavior |
| --- | --- |
| `execute(...)` | Strict mode. Stage exceptions propagate to the caller, which is useful inside an already-owned production runtime. |
| `executeSafely(...)` | Diagnostic mode. Compile, prepare, or invoke exceptions become typed `FAILED` receipts, and later stages become `SKIPPED`. |

For planned or discovery-only backends, use `GpuBackendExecutionPipelineResult.unsupported(...)` to render a complete
non-success result with `COMPILE=UNSUPPORTED`, `PREPARE=SKIPPED`, and `INVOKE=SKIPPED`. This gives CI, lifecycle journals,
and adapter bring-up tools a clear answer like "CUDA execution is not implemented yet" without pretending that a CUDA
compiler or invoker exists.

### Backend Hook Contracts

Backend hooks are the passive ServiceLoader-facing contracts for A8 adapter hardening. They are not invoked by the
production runtime yet; this is intentional so the hook vocabulary can stabilize before any OpenCL behavior changes.

All backend hooks extend `GpuBackendHook`, which provides:

| Contract field | Meaning |
| --- | --- |
| `backendTargets()` | Empty means all backend families; otherwise the hook is filtered to explicit targets such as `OPENCL` or `CUDA`. |
| `failurePolicy()` | Defaults to `CONTINUE`; future registries can use this to decide whether hook failures are isolated or fail the stage. |
| `artifactFields(...)` / `lifecycleFields(...)` | Stable metadata for catalogs, CI receipts, and lifecycle journals. |
| `extensionOrder()` | Deterministic ordering when hooks are loaded through ServiceLoader. |

The concrete hook families are:

| Hook | Default phase | Default permission | Purpose |
| --- | --- | --- | --- |
| `GpuBackendPolicyContributor` | `BACKEND_POLICY` | `READ_ONLY` | Add backend-selection requirements or policy facts. |
| `GpuRuntimeBackendScoreContributor` | `BACKEND_POLICY` | `READ_ONLY` | Add explainable score adjustments for one candidate. |
| `GpuBackendDiscoveryContributor` | `BACKEND_DISCOVERY` | `READ_ONLY` | Observe or contribute backend/device discovery facts. |
| `GpuBackendLoweringHook` | `BACKEND_LOWERING` | `READ_ONLY` | Observe lowering results before future registries decide whether mutation is allowed. |
| `GpuBackendCompilationHook` | `BACKEND_COMPILATION` | `READ_ONLY` | Observe compilation receipts and compiler-stage facts. |
| `GpuBackendInvocationHook` | `BACKEND_INVOCATION` | `READ_ONLY` | Observe invocation/readback receipts. |
| `GpuBackendArtifactHook` | `ARTIFACT_EMISSION` | `READ_ONLY` | Add backend-specific artifact fields. |

Default hook methods are no-ops. If a future hook implementation wants to alter discovery, lowering, compilation,
invocation, or artifact output, it should override `extensionPermission()` to a production-affecting permission and be
accepted by the future stage registry explicitly. This keeps the current OpenCL path stable while giving adapter authors
a compile-time SPI target.

`GpuBackendHookRegistry` is the inspection layer for these hooks. It can load services registered under either the base
`GpuBackendHook` interface or the concrete hook interfaces, applies deterministic ordering, validates duplicate
extension ids through `GpuExtensionRegistry`, filters by backend target/capability, and renders artifact fields or
Markdown. It is a catalog/validation helper only; it does not wire hooks into OpenCL compilation or invocation.

Example service file for a policy hook:

```text
META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuBackendPolicyContributor
```

Example service file for a score hook that should appear in hook catalogs:

```text
META-INF/services/net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendScoreContributor
```

Catalog inspection:

```java
GpuBackendHookRegistry registry = GpuBackendHookRegistry.loadWithServiceLoader();
System.out.println(registry.toMarkdown());
```

## Module Formats And Capabilities

Backend adapters should avoid inventing new free-form names for common module formats and device facts. Use the shared
vocabulary first, then add backend-specific details only when the portable field is not enough.

For backend source/binary artifacts, keep `GpuBackendModuleArtifact.format()` as the stable artifact string and use
`GpuBackendModuleArtifact.moduleFormat()` when code needs the typed meaning:

```java
GpuBackendModuleArtifact module = GpuBackendModuleArtifact.cudaSource(
        "extern \"C\" __global__ void kernel() {}",
        "generated/kernel.cu",
        "example-cuda-lowerer:1"
);

System.out.println(module.format());        // cuda-c
System.out.println(module.moduleFormat());  // CUDA_C
```

The current canonical module-format keys are:

| Key | Meaning |
| --- | --- |
| `opencl-c` | OpenCL C source. |
| `cuda-c` | CUDA C source. |
| `ptx` | NVIDIA PTX text/intermediate module. |
| `spir-v` | SPIR-V binary/intermediate module. |
| `metal-shading-language` | Metal shader source. |
| `native-binary` | Backend-owned binary format. |
| `unknown` | Format was not reported or is not recognized yet. |

For device/backend facts, use `GpuRuntimeCapability` for portable checks. Existing `GpuRuntimeFeature` values still work,
but `GpuRuntimeCapability` is the broader vocabulary intended for backend selection, diagnostics, and future CUDA/PTX/
SPIR-V policy work.

```java
boolean hasFp64 = report.supports(GpuRuntimeCapability.FP64);
boolean hasLocalMemory = profile.supportsCapability(GpuRuntimeCapability.LOCAL_MEMORY);
Map<String, String> facts = profile.capabilityFacts();
```

Device profiles now expose indexed capability fields such as `runtime.device.capability.0=fp64` and direct flags such as
`runtime.device.capability.local-memory=true`. Keep backend-specific facts namespaced, for example
`runtime.device.cuda.computeCapability`, rather than replacing the portable capability field.

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
| `runtime-production-mutation-safety.properties` | Fail-closed answer for whether optimized runtime IR may affect production code. |
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
