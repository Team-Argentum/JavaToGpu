# Тур по JavaToGpu

Двенадцать коротких уроков: каждый — самостоятельная программа с подробными комментариями в исходнике.
Лучше всего читать урок, запускать его, менять код и запускать снова. В комментарии каждого урока есть раздел
«Попробуйте» — что поменять, чтобы увидеть, как это работает (или как ломается).

## Запуск

Нужен JDK 25 **для самого Gradle**: плагин и weaver собраны под Java 25. Из корня репозитория (PowerShell):

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\gradlew.bat publishLocal                          # один раз: библиотека в build/local-maven
.\gradlew.bat -p examples/tour run                  # список уроков
.\gradlew.bat -p examples/tour run --args=hello     # один урок
.\gradlew.bat -p examples/tour runAll               # все подряд
.\gradlew.bat -p examples/tour test                 # как проверять свои ядра (test-support)
```

На Linux/macOS — `./gradlew` с теми же аргументами. После каждого урока печатается, где реально исполнялись ядра.
Устройство не обязательно: без OpenCL почти все уроки идут через JVM-fallback, а те, которым нужен GPU (барьеры,
явный контекст), говорят об этом. Проверить тур без GPU на машине с GPU: `$env:JAVATOGPU_DEVICE = 'none:0'`.

После правки библиотеки и повторного `publishLocal` добавьте `--refresh-dependencies`.

## Уроки

| Урок | Исходник | О чём |
|---|---|---|
| `hello` | [Hello.java](src/main/java/tour/hello/Hello.java) | `@Kernel`, `Gpu.getGlobalId`, вызов ядра как обычного метода, global size = длина первого массива |
| `grid` | [Grid.java](src/main/java/tour/grid/Grid.java) | 2D-запуск `JavaToGpu.launch(LaunchConfig.of2D(w, h))`, множество Мандельброта в ASCII, выход без загрузки на устройство |
| `types` | [Particles.java](src/main/java/tour/types/Particles.java) | векторы `Float4`, `@Struct` и массивы структур, value-семантика (`copy()`) |
| `functions` | [Functions.java](src/main/java/tour/functions/Functions.java) | `@DeviceFunction`, выходные параметры `FloatPtr`, `GlobalFloatPtr` внутрь массива, `@Global(constant = true)` |
| `local` | [BlockSum.java](src/main/java/tour/local/BlockSum.java) | `@WorkGroup`, `@Local`, `Gpu.barrier()`, `getLocalId`/`getGroupId` — сумма группы деревом |
| `atomics` | [Histogram.java](src/main/java/tour/atomics/Histogram.java) | `Gpu.atomicAdd`/`atomicMax` через `GlobalIntPtr` — гистограмма |
| `resident` | [Resident.java](src/main/java/tour/resident/Resident.java) | `GpuFloatArray`: данные живут на устройстве, цепочка из 100 шагов без передач — в десятки раз быстрее `float[]` |
| `reduce` | [Reduce.java](src/main/java/tour/reduce/Reduce.java) | `@Reduction`: сумма, min/max за один проход в `@Struct`, несколько результатов разных типов, вектор-аккумулятор |
| `fusion` | [Fusion.java](src/main/java/tour/fusion/Fusion.java) | автоматическое слияние `terrain → classify → count` в одно ядро; события показывают, что отложено, что слито и когда исполняется |
| `buffers` | [Buffers.java](src/main/java/tour/buffers/Buffers.java) | явный API: `GpuContext`, `compile`/`launch` со статистикой, `GpuBuffer`, `PreparedLauncher`, `readView`/`writeView` |
| `events` | [Events.java](src/main/java/tour/events/Events.java) | `EventListener`, `Timings`, выгрузка сгенерированного OpenCL C (`dumpSources`), кэш компиляции |
| `context` | [Context.java](src/main/java/tour/context/Context.java) | перечисление устройств, `DevicePolicy`, JVM-fallback и `Fallback.NEVER`, `JavaToGpu.warmUp` |
| тесты | [TourKernelsTest.java](src/test/java/tour/TourKernelsTest.java) | `GpuAssert.sameResult`: JVM, интерпретатор IR и устройство; ядро с барьером — на интерпретаторе без железа |

Порядок не обязателен, но первые четыре урока — основа языка ядер, `resident`, `reduce` и `fusion` — то, что
делает код быстрым, а `buffers`, `events` и `context` — управление, когда автоматики мало.

## Что ещё полезно знать

- Что можно писать в ядре (подмножество Java), коды ошибок `GPU####` и `RT-*` — в
  [руководстве](../../docs/USER_GUIDE.md). Ошибки подмножества — это ошибки компиляции с позицией и подсказкой:
  сделайте что-нибудь запрещённое в уроке (строку, лямбду, `new Object()`) и посмотрите на сообщение.
- Переменные окружения без правки кода: `JAVATOGPU_DUMP=<каталог>` (выгрузка OpenCL C), `JAVATOGPU_DEVICE=nvidia:0`
  (выбор устройства), `JAVATOGPU_FUSION=off` (без слияния ядер), `JAVATOGPU_OPTIMIZE=0..3|off` (оптимизатор IR),
  `JAVATOGPU_CACHE=off` (без дискового кэша).
- Свой проект начните с копии этой сборки или [минимальной](../standalone/README.md): там те же `settings.gradle`
  и `build.gradle`; адрес репозитория библиотеки — свойство `-PgpuRepository=file:///.../build/local-maven`.
