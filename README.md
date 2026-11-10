# gpu

Пишешь GPU-код на Java (подмножество, не произвольный код) — библиотека преобразует его в корректный код под видеокарту и запускает.

**Статус: M0–M13 реализованы; M14 — подготовка документации и alpha.** Текущая версия сборки — `0.2.0-SNAPSHOT`;
доступна локальная Maven-публикация. Внешняя площадка и координаты alpha ещё не утверждены.
Прежняя реализация (`JavaToGpu 0.1.0-alpha.5`) сохранена в git-теге `legacy-alpha5`.
Архитектура, принятые решения и план — в [`REWORK_ARCHITECTURE.md`](REWORK_ARCHITECTURE.md). Исходный концепт языка — в [`OldCodeConcept`](OldCodeConcept).

[Руководство пользователя](docs/USER_GUIDE.md) · [Самостоятельный пример подключения](examples/standalone/README.md).
[Результаты проверки подготовки alpha](reports/alpha-readiness-2026-11-09.md).

## Модули

| Модуль | Роль |
|---|---|
| `api` | аннотации, типы (vec/ptr/struct), builtins `Gpu.*` |
| `ir` | плоский IR (SoA), типы, бинарная сериализация, валидатор |
| `ir-interpreter` | эталонный CPU-интерпретатор IR |
| `ir-optimizer` | оптимизатор IR: мёртвый код, математика, CSE/LICM под бюджетом регистров, оценка давления, разбиение ядер |
| `codegen-opencl` | IR → OpenCL C |
| `runtime` | `GpuContext`, устройства (FFM), `GpuBuffer`, кэш, запуск, JVM-fallback |
| `frontend-java` | annotation processor (javac Tree API) → IR |
| `weaver` | подмена тела `@Kernel`-метода (ASM) |
| `gradle-plugin` | Gradle-обёртка над `weaver` |
| `test-support` | JUnit-библиотека (`GpuAssert`, `@GPUTest`) |
| `tools` | дампы IR, отчёты |
| `app` | тестовое приложение, примеры ядер и интеграционные тесты |

Допустимые зависимости между модулями задаёт `build.gradle`; задача `verifyModuleBoundaries` (входит в `check`) проверяет граф и запрещённые импорты.

## Быстрый старт

Для проверки без внешней публикации выполните `./gradlew publishLocal`: библиотеки и marker Gradle-плагина
появятся в `build/local-maven`. Подключение обоих Maven-репозиториев (для плагина и библиотек) показано
в [самостоятельном примере](examples/standalone/README.md). Сам Gradle потребителя должен работать на **JDK 25**.

```groovy
plugins {
    id 'io.github.deussixik.javatogpu' version '0.2.0-SNAPSHOT'
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
В примере с `@WorkGroup(x = 256)` длина запуска должна быть кратна 256.
Подробности — `REWORK_ARCHITECTURE.md`, разделы 3.4–3.8 и 5.19.

## Сборка

Нужен **JDK 25**. Сборку самого репозитория можно запускать Gradle на 21+ с установленным JDK 25 для toolchain.
При подключении опубликованного Gradle-плагина **JVM Gradle тоже должна быть 25+**: плагин и weaver загружаются в неё.

```
./gradlew build
```

## Тестовое приложение

Модуль `app` использует библиотеки из этой сборки; публикация в Maven не требуется.
Ядра лежат в `app/src/main/java/com/sixik/gpu/app/Kernels.java`, точка входа — `Main.java`.
При сборке annotation processor создаёт IR, а weaver подменяет тела `@Kernel`-методов.

```powershell
.\gradlew.bat :app:run                       # экспериментальные ядра на 1024 элементах и проверка vectorAdd
.\gradlew.bat :app:run --args="65536"        # свой размер массивов
.\gradlew.bat :app:test                      # тесты без OpenCL-драйвера
.\gradlew.bat :app:deviceTest                # сверка JVM / IR / OpenCL, без устройства пропуск
.\gradlew.bat :app:installDist               # приложение со скриптами запуска и зависимостями
```

На Linux/macOS используйте `./gradlew`. Готовые скрипты находятся в `app/build/install/app/bin`.
Для готовых скриптов нужен JDK 25 в `JAVA_HOME` или `PATH`; задача `:app:run` выбирает его через toolchain.
При запуске приложение выводит выбранное устройство, факт выполнения ядра на нём либо причину
перехода на JVM. Если подходящего OpenCL-устройства нет, пример выполняется на JVM.
`Main` всегда включает дамп сгенерированных OpenCL-ядер в `build/opencl` относительно
рабочего каталога: при запуске через `:app:run` это `app/build/opencl/*.cl`.

JMH-сценарии CPU/GPU для многослойного 3D-шума и игрового цикла с видимостью
32 чанка и высотой 360 блоков: [описание и команды](app/benchmarks/README.md),
[результаты измерений](app/benchmarks/RESULTS.md).

Для дампа OpenCL из JMH добавьте `-PdumpOpencl`:

```powershell
.\gradlew.bat :app:jmh -PdumpOpencl '-PjmhArgs=TerrainBatchBenchmarks -p backend=GPU_PREPARED -p sampling=COARSE -p chunksPerCall=1'
```

Исходники сохраняются в `app/build/opencl/jmh/<ядро>-<хэш>.cl`.
Свой каталог: `'-PdumpOpencl=app/build/my-opencl'` (путь относительно корня проекта).
Отключение: `-PdumpOpencl=off`. Также поддерживаются `-Djavatogpu.dump=<каталог>`
и переменная окружения `JAVATOGPU_DUMP`; `-PdumpOpencl` имеет приоритет.
Настройка передаётся fork-процессам JMH; запись выполняется в `@Setup`, вне измерений.
