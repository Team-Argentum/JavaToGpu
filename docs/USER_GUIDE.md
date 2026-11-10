# Руководство пользователя gpu

Состояние: подготовка alpha, версия сборки `0.2.0-SNAPSHOT`. Поддерживаются Java 25 и javac;
единственный бэкенд v1 — OpenCL C. Реальное устройство в текущих проверках — NVIDIA GeForce RTX 5070;
результаты для AMD и Intel пока не подтверждены.

## Подключение

Сначала соберите локальный Maven-репозиторий:

```powershell
.\gradlew.bat publishLocal
```

Далее используйте [независимый Gradle-проект](../examples/standalone/README.md). Он содержит полные настройки
`pluginManagement`, репозитория библиотек, JDK и JUnit. Публикация пишет только в `build/local-maven`;
внешняя площадка и итоговые координаты alpha остаются открытым вопросом O8.

Потребителю нужны JDK 25 для приложения и **JDK 25 для запуска Gradle с плагином**. Сам репозиторий библиотеки
можно собирать Gradle на 21+ с установленным toolchain 25. Плагин подключает `api`, `ir`, `ir-optimizer`, `codegen-opencl`,
`runtime`, processor `frontend-java` и подменяет тела `@Kernel` после javac. Для `Test` и `JavaExec` он добавляет
`--enable-native-access=ALL-UNNAMED`; в `applicationDefaultJvmArgs` для скриптов `installDist` этот флаг задаётся отдельно.

## Первое ядро

```java
import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

public final class Kernels {
    @Kernel
    public static void scale(@Global float[] input, @Global float[] output, float factor) {
        int i = Gpu.getGlobalId(0);
        output[i] = input[i] * factor + 1f;
    }
}
```

Вызов `Kernels.scale(input, output, 2f)` выполняет ядро. По умолчанию global size — длина первого массива.
Остальные массивы должны вмещать все индексы, используемые ядром. Скаляр `factor` передаётся по значению.

Для двумерного запуска:

```java
import com.sixik.gpu.runtime.JavaToGpu;
import com.sixik.gpu.runtime.LaunchConfig;

try (var scope = JavaToGpu.launch(LaunchConfig.of2D(width, height))) {
    Kernels2D.compute(input, output);
}
```

Ядро само использует `Gpu.getGlobalId(0)` и `Gpu.getGlobalId(1)` для вычисления линейного индекса.
`@WorkGroup(x = 256)` задаёт обязательный размер группы: global size по соответствующей оси должен делиться на него.
Без явного размера группы на устройстве его выбирает драйвер.

## Подмножество Java

| Возможность | Поддержка v1 |
|---|---|
| Ядро | `static void`, без перегрузок имени ядра |
| Числа | арифметика `int`, `long`, `float`, `double`; `byte`, `short`, `char` — хранение и преобразования |
| Массивы | одномерные примитивные массивы, массивы поддерживаемых векторов и struct |
| Управление | `if`, `for`, `while`, `do-while`, statement-`switch`, обычные `break`, `continue`, `return` |
| Функции | статические `@DeviceFunction` из той же компиляции, без рекурсии |
| Векторы | неизменяемые `Float/Double/Int/Long × 2/3/4/8/16`, операции и покомпонентные builtins |
| Struct | `@Struct`, value-семантика, вложенные struct и векторы; без массивов и указателей внутри |
| Память | `@Global`, `@Global(constant = true)`, приватные массивы константного размера, `@Local` в теле ядра |
| Указатели | ссылки `FloatPtr` и аналоги; `Global/Local/Constant*Ptr` внутри ядра и в device-функциях |
| Синхронизация | work-group barriers, целочисленные атомики 32/64 бит при поддержке устройства |

Запрещены произвольные объекты и куча, виртуальные вызовы, исключения, `try`, мониторы, строки,
лямбды, рекурсия, `for-each`, labeled `break/continue`, switch-выражения и pattern matching.
Создание поддерживаемых векторов, struct и pointer-обёрток допускается по правилам этих типов.

Device-функции из JAR без исходников не связываются (`GPU0114`). Их исходники должны входить в ту же компиляцию.
Аннотация `@DeviceFunction` обязательна и для приватного helper-метода.

У struct открытые изменяемые поля и конструктор без аргументов для хоста; конструктор по полям сохраняет значения.
Чтобы получить самостоятельное значение в ядре, используйте `new`, глубокий `copy()` либо результат `@DeviceFunction`.
Простое присваивание struct-переменной другой переменной запрещено: JVM-ссылка и GPU-копия дали бы разное поведение.
Вектор `Float3` занимает 16 байт в OpenCL ABI.

## Контекст и fallback

При обычном вызове используется ленивый контекст по умолчанию. Настройки задаются до первого запуска:

```java
JavaToGpu.configureDefault(builder -> builder
        .fallback(com.sixik.gpu.runtime.GpuContext.Fallback.NEVER)
        .listener(event -> System.out.println(event.kind() + ": " + event.detail())));
```

Собственный контекст можно ограничить областью:

```java
try (var context = com.sixik.gpu.runtime.GpuContext.builder().build();
     var scope = JavaToGpu.use(context)) {
    Kernels.scale(input, output, 2f);
}
```

По умолчанию при отсутствии устройства либо отказе компиляции ядра используется JVM: последовательный цикл
по work-item поверх оригинального тела `$jvm`. Причина перехода записывается в предупреждение.
Ошибки аргументов и размеров запуска не маскируются fallback. `Fallback.NEVER` запрещает переход на JVM,
включая отсутствие устройства при создании контекста по умолчанию; `builder.build()` отдельно сообщает об отсутствии устройства.

**Ядрам с barrier или local memory требуется устройство** (`RT-F01`). Для их проверки без железа есть
IR-интерпретатор в `test-support`. Он не используется как production-fallback. Подготовленные запуски также требуют устройства.

Выбор устройства: `builder.device("nvidia:0")`, свойство `javatogpu.device` либо `JAVATOGPU_DEVICE`.
Явный выбор побеждает фильтры. По умолчанию предпочтительны GPU, затем действует порядок перечисления драйвера.
`GpuContext` потокобезопасен; обращения к одному устройству сериализуются. `GpuBuffer` и `PreparedLauncher`
пользователь не должен изменять конкурентно.

## Массивы, GpuBuffer и подготовленные запуски

Массивы удобны для единичных вызовов. Перед каждым запуском **все массивы целиком** загружаются на устройство,
после него обратно копируются только записываемые ядром. Так сохраняются элементы, которые ядро не изменило.
Один массив в двух параметрах соответствует одному буферу, права доступа объединяются.

Для повторных запусков:

```java
try (var context = com.sixik.gpu.runtime.GpuContext.builder().build();
     var scope = JavaToGpu.use(context);
     var prepared = JavaToGpu.prepare(Kernels.class, "scale", input, output, 2f)) {
    prepared.run(LaunchConfig.of1D(input.length));
    prepared.set(2, 3f);
    prepared.run(LaunchConfig.of1D(input.length));
}
```

`PreparedLauncher` переиспользует выделенную память, но массивы хоста всё равно передаёт целиком при каждом запуске.
Для цепочек ядер без передач на хост используйте `GpuBuffer`:

```java
try (var context = com.sixik.gpu.runtime.GpuContext.builder().build();
     var scope = JavaToGpu.use(context);
     var deviceInput = context.upload(input);
     var deviceOutput = context.allocate(com.sixik.gpu.ir.Types.F32, input.length);
     var prepared = JavaToGpu.prepare(Kernels.class, "scale", deviceInput, deviceOutput, 2f)) {
    prepared.run(LaunchConfig.of1D(input.length));
    deviceOutput.read(output);
}
```

Буферы принадлежат пользователю и закрываются им. Подготовленный запуск освобождает только собственные буферы.
`set` заменяет скаляр, значение агрегата или `GpuBuffer`; для замены массива хоста создайте новый запуск.
`GpuBuffer` массивов векторов/struct пока не поддержан (`RT-A02`).

## Числа и проверка результата

Целые операции следуют Java: переполнение с оборотом, маскирование счётчика сдвига, насыщающее float→int с NaN→0.
Деление целых на ноль и выход за границы на устройстве не имеют определённого результата; JVM бросает исключение,
а интерпретатор возвращает ошибку. Не используйте устройство для проверки результата такого запуска.

По умолчанию float strict: кодген запрещает слияние операций в FMA. `fastMath` включается явно и меняет ключ кэша.
Побитовое равенство JVM и OpenCL для всех вещественных вычислений не гарантируется.

| Builtin | f32, ULP | f64, ULP |
|---|---:|---:|
| `sin`, `cos` | 4 | 4 |
| `sqrt` | 3 | 0 |
| `fma` | 0 | 0 |

Это табличные допуски **отдельных операций**. Автодопуск `GpuAssert` — эвристический максимум допусков
достижимых операций фактически выбранного варианта, с учётом f32/f64; для деления и остатка он не меньше 3 ULP.
Он не доказывает границу ошибки всего ядра: при вычитании близких чисел и длинных вычислениях ошибка результата
может быть больше. Для таких образцов задайте обоснованный `KernelSample.ulps(...)`.

```java
var sample = KernelSample.named("scale")
        .args(() -> new Object[]{new float[]{1, 2}, new float[2], 2f})
        .expected(null, new float[]{3, 5}, null)
        .build();
GpuAssert.sameResult(Kernels.class, "scale", sample);
```

Зависимость тестов — `io.github.deussixik:test-support:0.2.0-SNAPSHOT`, JUnit Jupiter и platform launcher;
полный пример находится в standalone-сборке. `Report` показывает выполненные и пропущенные ветви. Для
детерминированной проверки без устройства используйте `.without(KernelSample.Leg.DEVICE)`.
Для группового ядра задайте `expected`, независимо посчитанный на хосте.
При допуске 0 различается знак нуля; все NaN считаются равными. При ненулевом допуске ±0 совпадают,
а бесконечность по текущему правилу сравнения соседствует с максимальным конечным значением.

## Диагностика и отладка

Фронтенд выдаёт `GPU####` с позицией исходника и подсказкой, нарушение подмножества останавливает сборку.
Полная таблица — [`diagnostics.def`](../diagnostics/diagnostics.def).

| Код | Типичная причина |
|---|---|
| `GPU0113` / `GPU0114` | helper без `@DeviceFunction` / нет исходника device-функции |
| `GPU0122` | запись в constant-массив |
| `GPU0130` / `GPU0131` | потенциально расходящийся barrier; предупреждение / strict-ошибка |
| `GPU0193` | struct-значение может разделять ссылку с другим объектом |
| `RT-D01` / `RT-D02` | нет подходящего устройства / нет требуемых возможностей |
| `RT-A02` / `RT-L01` | неверный аргумент / несовместимые размеры запуска |
| `RT-F01` / `RT-F02` / `RT-F03` | fallback невозможен / отсутствует IR / отсутствует `$jvm` |

Для строгой проверки барьеров: `gpu { strictBarriers = true }`.
`Gpu.assumeUniform(x)` — утверждение автора ядра, а не синхронизация work-item.
Дамп исходника: `builder.dumpSources(path)`, `-Djavatogpu.dump=...` или `JAVATOGPU_DUMP`.

## Оптимизатор IR

Перед генерацией OpenCL C контекст прогоняет IR через `ir-optimizer`: свёртка констант и алгебра, удаление мёртвого
кода, протяжка значений по локалям, общие подвыражения и вынос инвариантов из циклов. По умолчанию
(`OptimizeOptions.DEFAULT`, уровень 2) все преобразования точные: результат ядра побитово тот же, что без оптимизатора.
В режиме `EmitOptions.DEBUG` оптимизатор не запускается, чтобы исходник совпадал с Java-кодом.

```java
GpuContext ctx = GpuContext.builder()
        .optimize(OptimizeOptions.DEFAULT.withRegisterBudget(48)) // бюджет регистров для CSE/LICM
        .build();
```

`OptimizeOptions.NONE` выключает оптимизатор; `-Djavatogpu.optimize=0..3|off` или `JAVATOGPU_OPTIMIZE` меняют уровень
без пересборки. `withFastMath(true)` разрешает преобразования, меняющие вещественный результат (`x/c → x*(1/c)`, слияние в
`fma`, `x*0 → 0`). Если оценка давления на регистры ядра выше бюджета, контекст пишет предупреждение в `EventListener`;
`KernelSplitter` умеет разрезать такое ядро на последовательные запуски (запуск стадий рантаймом пока не подключён).

## Кэш и ограничения

Кэш в памяти хранит результат компиляции; дисковый кэш — бинарь драйвера. Учитываются IR без debug,
кодген, исходник, опции и устройство с версией драйвера. Повреждённый или отвергнутый образ пересобирается.
Дисковый предел по умолчанию — 512 МиБ. Каталоги:

- Windows: `%LOCALAPPDATA%\javatogpu`;
- Linux: `$XDG_CACHE_HOME/javatogpu` либо `~/.cache/javatogpu`;
- macOS: `~/Library/Caches/javatogpu`.

Управление: `builder.diskCache(path)`, `noDiskCache()`, `diskCacheLimit(bytes)`, свойство `javatogpu.cache`
или `JAVATOGPU_CACHE` (путь либо `off`). Явная настройка builder имеет приоритет.

В v1 одно активное устройство на контекст и синхронные запуски. Указатели и динамические `__local`-аргументы
в сигнатуре ядра не поддерживаются. Images, async API, AutoTune, multi-GPU, оптимизатор e-graph,
Rust и CUDA/SPIR-V — после v1. `inline` и `optLevel` хранятся как атрибуты и пока не оптимизируют код.
