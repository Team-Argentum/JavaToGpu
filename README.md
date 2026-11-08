# gpu

Пишешь GPU-код на Java (подмножество, не произвольный код) — библиотека преобразует его в корректный код под видеокарту и запускает.

**Статус: переразработка с нуля (M0).** Прежняя реализация (`JavaToGpu 0.1.0-alpha.5`) сохранена в git-теге `legacy-alpha5`.
Архитектура, принятые решения и план — в [`REWORK_ARCHITECTURE.md`](REWORK_ARCHITECTURE.md). Исходный концепт языка — в [`OldCodeConcept`](OldCodeConcept).

## Модули

| Модуль | Роль |
|---|---|
| `api` | аннотации, типы (vec/ptr/struct), builtins `Gpu.*` |
| `ir` | плоский IR (SoA), типы, бинарная сериализация, валидатор |
| `ir-interpreter` | эталонный CPU-интерпретатор IR |
| `codegen-opencl` | IR → OpenCL C |
| `runtime` | `GpuContext`, устройства (FFM), `GpuBuffer`, кэш, запуск, JVM-fallback |
| `frontend-java` | annotation processor (javac Tree API) → IR |
| `weaver` | подмена тела `@Kernel`-метода (ASM) |
| `gradle-plugin` | Gradle-обёртка над `weaver` |
| `test-support` | JUnit-библиотека (`GpuAssert`, `@GPUTest`) |
| `tools` | дампы IR, отчёты |

Допустимые зависимости между модулями задаёт `build.gradle`; задача `verifyModuleBoundaries` (входит в `check`) проверяет граф и запрещённые импорты.

## Быстрый старт

```groovy
plugins {
    id 'io.github.deussixik.javatogpu'
}
```

Плагин подключает библиотеки и annotation processor, подменяет тела `@Kernel`-методов после компиляции и выдаёт задачам `test`/`run`
`--enable-native-access=ALL-UNNAMED`. После этого ядро вызывается обычным вызовом Java-метода:

```java
public class Sine {
    @Kernel
    @WorkGroup(x = 256, y = 1, z = 1)
    public static void computeSine(@Global float[] input, @Global float[] output) {
        int id = Gpu.getGlobalId(0);
        output[id] = Gpu.sin(input[id]) + 2.0f;
    }
}

Sine.computeSine(input, output);   // OpenCL-устройство, а если его нет или драйвер отверг ядро - JVM
```

Размер запуска по умолчанию — длина первого массива; другой задаётся блоком
`try (var s = JavaToGpu.launch(LaunchConfig.of2D(w, h))) { ... }`, свой контекст — `JavaToGpu.use(ctx)`.
Подробности — `REWORK_ARCHITECTURE.md`, разделы 3.4–3.8 и 5.19.

## Сборка

Нужен **JDK 25** (Gradle подберёт его через toolchain; сам Gradle можно запускать на 21+).

```
./gradlew build
```
