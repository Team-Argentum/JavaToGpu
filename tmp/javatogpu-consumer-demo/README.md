# JavaToGpu Consumer Demo

Минимальный внешний Gradle-проект, который показывает не только `@GPU` annotation, но и полный runtime-путь:

- `implementation io.github.deussixik:javatogpu` для API/runtime;
- `annotationProcessor io.github.deussixik:javatogpu` для генерации launcher/source artifacts;
- `runtimeOnly org.lwjgl:lwjgl::<native-classifier>` для OpenCL/LWJGL native library;
- `JavaToGpu.useOpenClSharedCache()` вокруг GPU-вызова;
- explicit global work size через generated launcher.

## Run

Из корня основного репозитория:

```powershell
.\gradlew.bat -p tmp\javatogpu-consumer-demo clean run --console=plain --no-daemon
```

Ожидаемый результат:

```text
input  = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]
output = [3.0, 5.0, 7.0, 9.0, 11.0, 13.0, 15.0, 17.0]
```

## Maven Central

В `settings.gradle` временно добавлен local staging repository:

```groovy
maven { url = uri('../../processor/build/maven-staging') }
```

Он нужен только пока свежая `0.1.0-alpha.4` синхронизируется в публичный Maven Central. В обычном проекте оставь только:

```groovy
repositories {
    mavenCentral()
}
```

## Why Generated Launcher

Прямой вызов `Kernels.scaleAndBias(input, output)` использует default launch config. Для массива в текущей alpha лучше явно указать размер работы:

```java
Main_Kernels_scaleAndBias_GpuLauncher.invokeWithGlobalWorkSize(input.length, input, output);
```

Если указать `@GPUWorkGroupSize(x = 64)`, то global size должен быть совместим с local size. Для первого smoke-примера local size не фиксируется, и драйвер выбирает его сам.
