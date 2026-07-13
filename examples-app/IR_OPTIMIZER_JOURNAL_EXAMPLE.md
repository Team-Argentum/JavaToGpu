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

`original.backend.opencl-c` and `optimized.backend.opencl-c` are the before/after backend source files for comparing generated OpenCL around the optimizer boundary. `backend.opencl-c` is the selected backend source code that the OpenCL backend actually compiles. `original.irgpu.properties` and `optimized.irgpu.properties` are the matching before/after IR files. `runtime-ir-optimizer-evidence.properties` is the machine-readable guardrail summary that keeps the current alpha fail-closed.

To tell whether `backend.opencl-c` came from optimized IR or from the original/pass-through IR, open `runtime-ir-handoff.properties` and check `selectedStage` plus `optimizedDiffersFromOriginal`.

Current alpha behavior is intentionally fail-closed: optimizer evidence and proposed artifacts can be dumped for review, but production mutation and selected-IR replacement remain disabled unless future proof, approval, and production gates explicitly allow them.
