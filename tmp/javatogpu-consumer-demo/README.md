# JavaToGpu Consumer Demo

Minimal external Gradle project that consumes JavaToGpu from public Maven Central.

It demonstrates the full user path:

- `implementation io.github.deussixik:javatogpu` for the API/runtime facade.
- `annotationProcessor io.github.deussixik:javatogpu` for generated launcher/resources.
- `runtimeOnly org.lwjgl:lwjgl::<native-classifier>` for the OpenCL/LWJGL native library.
- `JavaToGpu.useOpenClSharedCache()` around the GPU call.
- Explicit global work size through the generated launcher.

## Run

From the repository root:

```powershell
.\gradlew.bat -p tmp\javatogpu-consumer-demo clean run --console=plain --no-daemon
```

Expected output:

```text
input  = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]
output = [3.0, 5.0, 7.0, 9.0, 11.0, 13.0, 15.0, 17.0]
```

## Maven Central

This demo intentionally uses only public Maven Central:

```groovy
repositories {
    mavenCentral()
}
```

No Maven Central credentials or private repository tokens are required for consumers.

## Why Generated Launcher

A direct call such as `Kernels.scaleAndBias(input, output)` uses the default launch config. For array workloads in this alpha, prefer an explicit global size:

```java
Main_Kernels_scaleAndBias_GpuLauncher.invokeWithGlobalWorkSize(input.length, input, output);
```

If `@GPUWorkGroupSize(x = 64)` is set, the global size must be compatible with the local size. This smoke example does not fix a local size, so the driver chooses it.
