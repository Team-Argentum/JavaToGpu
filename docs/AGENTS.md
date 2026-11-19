# JavaToGpu для агентов: как это работает и как пользоваться фронтендом байткода

Документ для агента (или человека), который **встраивает JavaToGpu в свой проект**: пишет ядра на Java либо генерирует
байткод во время работы программы (генератор кода, мод Minecraft) и хочет исполнить его на GPU. Правила работы
**внутри** этого репозитория — в [`../AGENTS.md`](../AGENTS.md).

Связанные документы: [руководство пользователя](USER_GUIDE.md) (полный справочник по API и подмножеству Java),
[проект фронтенда байткода](FRONTEND_ASM_RUNTIME.md) (решения, замеры, история шагов B0–B7),
[архитектура](../REWORK_ARCHITECTURE.md), [таблица диагностик](../diagnostics/diagnostics.def).

Пример кода из раздела 5.10 — это тест `frontend-asm/src/test/java/com/sixik/gpu/frontend/asm/AgentGuideExampleTest.java`:
он собирается и проходит в каждой сборке.

---

## 1. Что делает JavaToGpu

Пишешь GPU-код на **подмножестве Java** — статические методы, примитивы, массивы, векторы, `@Struct`. Библиотека
переводит его в промежуточное представление (IR), оптимизирует, печатает **OpenCL C** и запускает на устройстве через
драйвер OpenCL. Если устройства нет, обычное ядро исполняется на JVM (fallback).

```
                 исходник .java                          class-файлы / ClassNode в памяти
                       │                                              │
        frontend-java (annotation processor javac)       frontend-asm (ASM tree API: AsmFrontend, AsmJit)
                       └──────────────────────┬───────────────────────┘
                                              ▼
                                ir: IrModule (плоский IR) + Validator
                                              │
                                ir-optimizer: Optimizer (opt = 0..3, бюджет времени)
                                              │
                         codegen-opencl: OpenClEmitter → OpenCL C, ClProgram (ABI ядер)
                                              │
       runtime: GpuContext (выбор устройства, сборка драйвером, кэш в памяти и на диске, запуск, fallback)
                                              │
        бэкенд драйвера: runtime-opencl-ffm (Java 25, FFM) | runtime-opencl-lwjgl (Java 17, LWJGL 3.3.3)
```

Ключевые свойства, на которые можно опираться:

- **Один IR на оба фронтенда.** `frontend-java` и `frontend-asm` дают побайтово одинаковый IR и одинаковый `kernelId`
  для одного и того же ядра.
- **Детерминизм.** Одинаковый вход даёт побайтово тот же IR и тот же `kernelId`/`moduleId` в любом запуске, поэтому
  дисковый кэш (IR → исходник → бинарь драйвера) попадает между запусками.
- **Точность по умолчанию как на JVM.** Без флагов `fp` оптимизатор не меняет результат вещественной арифметики.
  `exact = true` требует побитового совпадения с JVM. Приближённые builtins (`Gpu.sin`) — явный выбор автора.
- **Ошибки — диагностики, а не исключения.** Фронтенды отдают `GPU####` (код, сообщение, подсказка, позиция), IR и
  кодоген — `IR-*`/`CG-*`, рантайм — `RT-*`. Методы возвращают `Result<T>` (`isOk()`, `value()`, `diagnostics()`).
  Неверный код из корректного входа — ошибка библиотеки.
- **Эталон для тестов.** `ir-interpreter` исполняет IR на CPU по спецификации, включая барьеры и local-память. Им
  проверяют ядра без GPU.

## 2. Модули и уровни Java

| Модуль | Java | Нужен, когда |
|---|---|---|
| `api` | 17 | аннотации (`@Kernel`, `@DeviceFunction`, `@Global`...), `Gpu.*`, векторы, struct. Для исходников ядер |
| `ir` | 17 | всегда (транзитивно) |
| `ir-optimizer` | 17 | всегда (транзитивно) |
| `codegen-opencl` | 17 | всегда (транзитивно); напрямую — если запускаете OpenCL своим рантаймом (`ClProgram`) |
| `frontend-common` | 17 | транзитивно с фронтендами |
| `frontend-asm` | 17 | компиляция байткода: `AsmJit` (классы в памяти), `AsmFrontend` (каталог классов) |
| `frontend-java` | 25 | annotation processor; подключает Gradle-плагин |
| `runtime` | 17 | `GpuContext`, буферы, запуск, кэш, fallback |
| `runtime-opencl-ffm` | 25 | бэкенд драйвера через FFM. По умолчанию на JVM 25+ |
| `runtime-opencl-lwjgl` | 17 | бэкенд через `lwjgl-opencl` 3.3.3; ядро LWJGL с нативами даёт приложение (Minecraft) |
| `ir-interpreter` | 21 | тесты без GPU |
| `test-support` | 25 | JUnit: `GpuAssert`, `@GPUTest`, `KernelSample` |
| `weaver`, `gradle-plugin` | 25 | Gradle-плагин: подмена тел `@Kernel`-методов после компиляции |

Бэкенд выбирается через `ServiceLoader`. Если на classpath оба, на JVM 25+ берётся FFM, иначе LWJGL. Явный выбор —
`-Djavatogpu.backend=ffm|lwjgl` или `JAVATOGPU_BACKEND`. Для мода кладите в jar **один** бэкенд: сканер классов
загрузчика на Java 21 может упасть на class-файлах версии 69 (Java 25).

## 3. Подключение через JitPack

Релизы собирает [JitPack](https://jitpack.io/#Team-Argentum/JavaToGpu) по git-тегу. Координаты:
`com.github.Team-Argentum.JavaToGpu:<модуль>:<версия>`. Версия — тег (`v0.2.0` и т. п.), хэш коммита или
`master-SNAPSHOT` (последний коммит ветки). Ниже `<TAG>` — выбранная версия.

### 3.1. Библиотеки напрямую (фронтенд байткода, мод, свой конвейер)

```groovy
repositories {
    mavenCentral()
    maven { url = 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Team-Argentum.JavaToGpu:frontend-asm:<TAG>'
    implementation 'com.github.Team-Argentum.JavaToGpu:runtime:<TAG>'
    implementation 'com.github.Team-Argentum.JavaToGpu:runtime-opencl-ffm:<TAG>'      // JVM 25+
    // implementation 'com.github.Team-Argentum.JavaToGpu:runtime-opencl-lwjgl:<TAG>' // Java 17-24, Minecraft
    // api нужен, если в вашем исходнике есть @DeviceFunction, Gpu.* или @Global (библиотека device-функций)
    compileOnly 'com.github.Team-Argentum.JavaToGpu:api:<TAG>'

    // AsmJit принимает org.objectweb.asm.tree.ClassNode: дерево ASM нужно и при компиляции.
    // В моде - compileOnly и ASM загрузчика (NeoForge/Fabric дают 9.7+), иначе - implementation.
    compileOnly 'org.ow2.asm:asm-tree:9.7'
}
```

У `frontend-asm` ASM (`asm`, `asm-tree`) — зависимость времени выполнения версии 9.11.1. Если ASM даёт загрузчик,
исключите её (`exclude group: 'org.ow2.asm'`), чтобы не было двух копий. Подойдёт любая 9.7+. ASM 9.7 читает
class-файлы **до Java 23**: библиотеку device-функций, которую читает фронтенд, собирайте не новее `--release 23`
(у модов 1.21.1 — 21). Сгенерированные `ClassNode` через `ClassReader` не проходят, и версия для них не важна.

### 3.2. Gradle-плагин (ядра в исходниках, обычный вызов `Kernels.scale(...)`)

Плагин опубликован как модуль `gradle-plugin`. Его маркер может не находиться по id через JitPack, поэтому id плагина
сопоставляется модулю явно:

```groovy
// settings.gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = 'https://jitpack.io' }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'io.github.deussixik.javatogpu') {
                useModule("com.github.Team-Argentum.JavaToGpu:gradle-plugin:${requested.version}")
            }
        }
    }
}
```

```groovy
// build.gradle
plugins {
    id 'io.github.deussixik.javatogpu' version '<TAG>'
}
repositories {
    mavenCentral()
    maven { url = 'https://jitpack.io' }
}
gpu {
    frontend = 'java'   // 'asm' - IR из class-файлов после компиляции (device-функции из jar без исходников)
    backend = 'ffm'     // 'lwjgl' для Java 17-24; 'none' - бэкенд добавите сами
}
```

Плагин сам подключает библиотеки той же группы и версии, с которыми собран (у сборки JitPack —
`com.github.Team-Argentum.JavaToGpu`, версия — тег). Переопределение — `gpu { group = ...; version = ... }`.
**Gradle с плагином должен работать на JDK 25** (плагин и weaver собраны под 25). Toolchain приложения — 25 для
`frontend = 'java'`.

Как работает плагин: процессор `frontend-java` (или `frontend-asm` в `doLast` компиляции) пишет IR ядер в ресурсы
`META-INF/gpu/**`; weaver заменяет тело каждого `@Kernel`-метода вызовом рантайма, а исходное тело сохраняет как
`<имя>$jvm` для fallback. `test`/`run` получают `--enable-native-access=ALL-UNNAMED`.

### 3.3. Локальная проверка публикации

`./gradlew publishLocal` пишет все модули в `build/local-maven` (группа `io.github.deussixik`).
Публикацию с координатами JitPack можно воспроизвести так:

```powershell
$env:JITPACK = 'true'; $env:GROUP = 'com.github.Team-Argentum'; $env:ARTIFACT = 'JavaToGpu'; $env:VERSION = 'test'
.\gradlew.bat publishToMavenLocal "-Dmaven.repo.local=$env:TEMP\jp-m2"
```

То же делает `jitpack.yml` (свойства `-PpublishGroup=... -PpublishVersion=...` работают и без переменных окружения).

## 4. Путь A: ядра в исходниках (аннотации)

```java
import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.*;

public final class Kernels {
    @DeviceFunction
    static float sq(float x) { return x * x; }

    @Kernel
    @WorkGroup(x = 256, y = 1, z = 1)
    public static void scale(@Global float[] in, @Global float[] out, float k) {
        int i = Gpu.getGlobalId(0);
        out[i] = sq(in[i]) * k;
    }
}
// вызов: Kernels.scale(in, out, 2f);  global size по умолчанию = длина первого массива
```

Правила подмножества (кратко; полностью — USER_GUIDE, «Подмножество Java»): ядро `static void`; `int long float
double`, а `byte short char boolean` — для хранения; одномерные массивы примитивов, векторов и struct; `if/for/while/
do-while/switch`; статические `@DeviceFunction` без рекурсии; без объектов, исключений, строк, лямбд, `for-each`,
меток. Свойства ядра: `@Kernel(compilationProperties = "fp=contract,opt=2")`, ключи `fp`, `opt`, `inline`, `exact`.

## 5. Путь B: фронтенд байткода `frontend-asm`

### 5.1. Когда он нужен

- **Генерация во время работы.** Программа строит байткод (`ClassNode`) по данным — роутер плотности мира, формула,
  граф узлов — и хочет его исполнить на GPU без javac и без файлов: **`AsmJit`**.
- **Готовые class-файлы.** Ядра собраны не javac или device-функции лежат в jar без исходников: **`AsmFrontend`**
  (его вызывает плагин при `gpu { frontend = 'asm' }`).

`AsmJit` — чистая функция: без файлов и глобального состояния. Работа идёт на собственном потоке с большим стеком
(`DeepStack`), поэтому глубокая вложенность не роняет вызывающий поток. Вызывать можно из любого потока.

### 5.2. Вход: `AsmJit.Unit`

```java
new AsmJit.Unit(
        ClassSource classes,              // библиотека: где искать вызываемые классы по внутреннему имени
        Map<String, ClassNode> generated, // сгенерированные классы: "gen/Router" -> ClassNode; ищутся раньше classes
        List<AsmJit.Entry> entries,       // точки входа (ядра) в порядке результата
        Bindings bindings,                // привязки static-полей (null - нет)
        Links links)                      // связи заглушек @Extern (null - нет)
// короткая форма: new AsmJit.Unit(classes, generated, entries)
```

`ClassSource` — функция «внутреннее имя → байты class-файла или `null`»:

| Фабрика | Что даёт |
|---|---|
| `ClassSource.of(ClassLoader)` | классы загрузчика (`getResourceAsStream(name + ".class")`) — jar мода, classpath |
| `ClassSource.of(Map<String, byte[]>)` | готовые байты |
| `ClassSource.chain(a, b, ...)` | первый, кто нашёл |
| `ClassSource.empty()` | ничего |

`Gpu.*`, JDK-интринсики (`Math.abs/min/max/floor/fma/sqrt`, `Float.floatToRawIntBits`, ...) и API-типы фронтенд знает
по дескрипторам: модуль `api` в `ClassSource` не нужен.

### 5.3. Точки входа: `AsmJit.Entry`

```java
AsmJit.Entry.of("gen/Router", "k", "([F[F)V")                 // атрибуты из @Kernel метода либо по умолчанию
new AsmJit.Entry("gen/Router", "k", "([F[F)V",
        Map.of("compilationProperties", "opt=2,fp=contract",   // как @Kernel(compilationProperties = ...)
               "required", List.of("FP64")),                    // как @Kernel(required = ...)
        Map.of("x", 64))                                        // как @WorkGroup(x = 64): reqd_work_group_size
```

- `owner` — внутреннее имя (`pkg/Name`, через `/`), `desc` — дескриптор метода. Метод должен быть `static void`.
- Атрибуты задаются **либо** аннотацией метода, **либо** картами точки входа, иначе `GPU0311`. Несуществующий метод —
  `GPU0311`, ненайденный класс — `GPU0306`.
- Ошибочная точка входа пропускается, остальные строятся (`Result.kernels()` — только построенные).
- Параметры-массивы без аннотаций — `__global`. `@Global(constant = true)` и `@Local` задаются аннотацией параметра;
  `@Private` — у параметра device-функции, которой передают массив из тела (`new int[N]`), иначе `GPU0121`.
- Указатели `Global/Local/Constant{Float,Double,Int,Long,Byte,Short}Ptr` и ссылки `{...}Ptr`: для `uchar` —
  `GlobalBytePtr`/`LocalBytePtr` и `p.get(i) & 0xFF`.
- С `reqd_work_group_size` (`Map.of("x", 64)`) глобальный размер запуска должен делиться на 64: иначе `IN-L01` в
  интерпретаторе и `RT-L01` на устройстве.

### 5.4. Контракт сгенерированного байткода

Это гарантирует генератор. Нарушение даёт диагностику, а не неверный код (FRONTEND_ASM_RUNTIME.md, 18.3):

| Правило | Иначе |
|---|---|
| Методы `static`; точки входа `void` | `GPU0311` |
| Типы локалей `I J F D` и массивы; `Z B S C` — только элементы массивов | `GPU0307` (без LVT) |
| Массивы — параметры либо `newarray` константной длины, записанный в локаль; массив-локаль не переприсваивается | `GPU0116`, `GPU0117` |
| **Операнд, нужный внутри ветки, сохраняется в локаль до перехода.** На стыке ветвей на стеке лежит только результат «ромба» (`?:`) | `GPU0305` |
| Без `dup*` значений с эффектами (вызовы, запись в память) — только через локаль | порядок вычисления |
| Поток управления приводимый, циклы естественные, `break/continue` — только ближайшего цикла | `GPU0304`, `GPU0104` |
| `getstatic` — только привязанные поля (5.6) | `GPU0115` |
| Вызовы: `Gpu.*`, JDK-интринсики, методы сгенерированных классов, `@DeviceFunction` библиотеки, `@Extern` через `Links` | `GPU0112`, `GPU0113`, `GPU0309` |
| Без исключений, мониторов, `new` объектов (кроме векторов, `@Struct`, указателей API), строк, `invokevirtual` на своих классах | `GPU01xx` |

Удобства генератора:

- **Без лимитов class-файла.** Сгенерированные классы не сериализуются: нет предела 64 КБ на метод и 65535 записей
  пула констант, не нужны `COMPUTE_FRAMES`/`COMPUTE_MAXS` (`visitMaxs` можно заполнить приблизительно).
- **Device-функции без аннотаций.** Статические методы сгенерированных классов вызываются как device-функции без
  `@DeviceFunction`. Методы библиотеки из `ClassSource` по-прежнему требуют аннотации (`GPU0113`).
- **Без LVT** — `AsmJit.Options.DEFAULT.synthesizeLvt(true)` (5.5).
- **Позиции — узлы генератора.** `visitLineNumber(nodeId, label)` и `visitSource("router/overworld", null)` дают
  диагностику `router/overworld:4711`: `AsmJit.position(diagnostic, unit)`.
- **Встраивание.** `@DeviceFunction(inline = InlinePolicy.ALWAYS)` у библиотечной функции — оптимизатор (`opt ≥ 1`)
  подставит тело в место вызова, если это не меняет смысла (FRONTEND_ASM_RUNTIME.md, S4). `NEVER` →
  `__attribute__((noinline))`. Методы сгенерированных классов аннотаций не несут: их политика — `DEFAULT`.

### 5.5. Настройки: `AsmJit.Options`

| Поле | По умолчанию | Смысл |
|---|---|---|
| `strictBarriers` | `false` | барьер под расходящимся управлением: `true` — ошибка `GPU0131`, иначе предупреждение `GPU0130` |
| `lvt` | `REQUIRED` | метод без `LocalVariableTable`: `REQUIRED` — `GPU0301`; `SYNTHESIZE` (`synthesizeLvt(true)`) — фронтенд выводит переменные сам |
| `budget` | 2 с | бюджет фронтенда на единицу; превышение — предупреждение `GPU0312`, IR при этом полный (`withBudget(null)` — без предела) |

В режиме `SYNTHESIZE` переменная — связная группа записей слота (достигающие определения), тип — по опкоду
(`istore` → `int`, у `astore` — по источнику значения). Поэтому `boolean/byte/short/char` в локалях становятся `int`,
а один слот с несовместимыми типами в связанных записях — `GPU0307`. Без `LineNumberTable` циклы восстанавливаются
как `while` (поведение то же). Метод со своей LVT не меняется.

### 5.6. Параметры времени компиляции: `Bindings`

```java
Bindings b = Bindings.none()
        .constant("gen/Flags", "AQUIFERS", false)                 // boolean -> константа, ветка сворачивается
        .constant("gen/Layout", "HEIGHT", 384)                    // Integer для int/short/byte/char (с проверкой диапазона)
        .table("gen/Tables", "PERM", new int[]{5, 3, 7, 1});      // массив -> __constant-таблица
```

- `constant(owner, field, value)`: `getstatic` привязанного `static` поля (не обязательно `final`) становится
  константой IR. Значения: `Integer` (также `Short/Byte/Character`), `Boolean`, `Long`, `Float`, `Double`. Тип не
  совпадает с дескриптором поля — `GPU0308`. Оптимизатор сворачивает условия и удаляет мёртвые ветки.
- `table(owner, field, array)`: поле-массив `int[] long[] float[] double[] short[] byte[] char[]` (непустой).
  Массив **копируется** при привязке. Ядро индексирует таблицу и берёт `length`. В OpenCL это один
  инициализированный `__constant T tblN[len] = {...}` на программу. Одинаковые таблицы печатаются один раз. Чтение по
  константному индексу сворачивается. Запись в таблицу или сохранение её в локаль (`int[] t = Tables.PERM`) —
  `GPU0116`. Суммарный размер таблиц ограничен `CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE` (обычно 64 КБ): иначе `RT-D04` при
  сборке. Большие таблицы передавайте параметром `@Global(constant = true)`.
- Привязки — часть IR: разные значения дают разный `kernelId` (и отдельную запись кэша).
- На JVM-пути поля читаются как обычно: библиотеку можно тестировать JUnit'ом, выставив поля.
- Непривязанный `getstatic` — `GPU0115`.

### 5.7. Библиотека вызывает сгенерированный код: `Links` и `@Extern`

Библиотека в jar объявляет заглушку и вызывает её. Генератор связывает заглушку со своим методом:

```java
// в библиотеке (исходник, собирается javac):
@DeviceFunction @Extern
public static int erosion(int x) { throw new IllegalStateException("linked at runtime"); }

// при компиляции единицы:
Links links = Links.none().redirect("lib/RouterLinks", "erosion", "(I)I",   // заглушка
                                    "gen/Router", "ap_erosion", "(I)I");    // цель с тем же дескриптором
```

Вызов заглушки без связи или связь с другим дескриптором — `GPU0309`. `frontend-java` заглушки не связывает:
вызов из ядра на исходниках — тоже `GPU0309`.

### 5.8. Результат: `AsmJit.Result`

| Член | Что это |
|---|---|
| `ok()` | нет ошибок среди `diagnostics()` (предупреждения допустимы) |
| `diagnostics()` | `AsmDiagnostic(code, error, message, className, method, line)`; позиция — `AsmJit.position(d, unit)` |
| `module()` | **общий** `IrModule` всех построенных точек: каждая функция один раз, ядра в порядке точек входа |
| `moduleId()` | хэш общего модуля (64 hex) — ключ вашего кэша |
| `kernels()` | `Kernel(entry, module, kernelId, function)`: отдельный модуль одного ядра (ядро — последняя функция), его `kernelId` и индекс функции в общем модуле |
| `elapsed()`, `remaining(options)` | время фронтенда и остаток бюджета для оптимизатора |

### 5.9. Что делать с IR дальше

**(а) Запустить через рантайм JavaToGpu** — программа драйвера собирается один раз на модуль:

```java
try (GpuContext ctx = GpuContext.builder().build()) {           // tryBuild() - Result без исключения
    CompiledProgram program = ctx.compileProgram(jit.module()).value();
    CompiledKernel k = program.kernel(jit.kernels().get(0).function());   // или program.kernel("k")
    Result<LaunchStats> r = ctx.launch(k, LaunchConfig.of1D(n), out, in);  // аргументы - в порядке параметров
}
```

`compileProgram` сам оптимизирует модуль (уровень — `opt` ядер, иначе контекста), печатает OpenCL C, собирает
драйвером, кэширует в памяти и на диске (ключ — хэш модуля и устройства). Аргументы `launch`: Java-массивы (копируются
на устройство и обратно по доступу, который вывел валидатор), `GpuBuffer` (данные живут на устройстве, `ctx.upload`/
`ctx.allocate`) и скаляры. Одиночное ядро — `ctx.compile(kernel.module())`. Программа требует устройства: у ядра из
`AsmJit` нет `$jvm`-тела для fallback. Нет устройства — `tryBuild()` вернёт ошибку (`RT-D01`), устройство не умеет
нужного (FP64, атомики 64) — `RT-D02`.

**(б) Свой рантайм OpenCL** (например, если в проекте уже есть `ClApi`):

```java
Result<ClProgram> cl = OpenClEmitter.program(Optimizer.optimize(jit.module(),
        OptimizeOptions.DEFAULT.withTimeBudget(jit.remaining(options))).module(), EmitOptions.RELEASE);
String source = cl.value().source();              // OpenCL C
String opts = cl.value().buildOptions();          // опции clBuildProgram
for (ClProgram.KernelAbi k : cl.value().kernels()) {
    // k.name() - имя __kernel (в RELEASE: k0, k1, ...), k.debugName() - имя метода, k.reqdWorkGroup()
    // k.params(): порядок clSetKernelArg; kind - ClProgram.Param.BUFFER | LENGTH (int-длина предыдущего
    //             буфера в элементах) | SCALAR; type (тип IR), size (байт по AbiLayout), space, reads()/writes()
    // k.localMemoryBytes() - байт __local-массивов ядра (сравните с CL_DEVICE_LOCAL_MEM_SIZE)
}
long constants = cl.value().constantBytes();      // таблицы __constant программы (CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE)
```

Таблицы `Bindings.table` уже внутри текста программы и аргументов не требуют.

**(в) Проверить без GPU** — интерпретатор (`ir-interpreter`, тестовая зависимость):

```java
var run = Interpreter.run(jit.kernels().get(0).module(), NdRange.of1D(n), out, in);
assertTrue(run.isOk(), run.diagnostics().toString());
```

### 5.10. Полный пример

Генератор строит класс `gen/Router` без LVT. Эквивалент на Java:

```java
static float twice(float v) { return v * 2f; }
static void k(float[] out, float[] in) {
    int i = Gpu.getGlobalId(0);
    float x = in[i];
    if (Flags.SCALE) { x = x * Tables.W[i & 3]; }   // флаг и таблица - привязки
    out[i] = twice(x);
}
```

```java
ClassNode cn = new ClassNode();
cn.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "gen/Router", null, "java/lang/Object", null);
cn.visitSource("router/demo", null);                          // позиции диагностик: router/demo:<узел>

MethodVisitor tw = cn.visitMethod(ACC_PUBLIC | ACC_STATIC, "twice", "(F)F", null, null);
tw.visitCode();
tw.visitVarInsn(FLOAD, 0); tw.visitInsn(FCONST_2); tw.visitInsn(FMUL); tw.visitInsn(FRETURN);
tw.visitMaxs(2, 1); tw.visitEnd();

MethodVisitor mv = cn.visitMethod(ACC_PUBLIC | ACC_STATIC, "k", "([F[F)V", null, null);
mv.visitCode();
line(mv, 100);                                                // visitLabel + visitLineNumber(100, label)
mv.visitInsn(ICONST_0);
mv.visitMethodInsn(INVOKESTATIC, "com/sixik/gpu/api/Gpu", "getGlobalId", "(I)I", false);
mv.visitVarInsn(ISTORE, 2);                                   // int i
line(mv, 101);
mv.visitVarInsn(ALOAD, 1); mv.visitVarInsn(ILOAD, 2); mv.visitInsn(FALOAD);
mv.visitVarInsn(FSTORE, 3);                                   // float x
line(mv, 102);
Label skip = new Label();
mv.visitFieldInsn(GETSTATIC, "gen/Flags", "SCALE", "Z");
mv.visitJumpInsn(IFEQ, skip);
mv.visitVarInsn(FLOAD, 3);
mv.visitFieldInsn(GETSTATIC, "gen/Tables", "W", "[F");
mv.visitVarInsn(ILOAD, 2); mv.visitInsn(ICONST_3); mv.visitInsn(IAND); mv.visitInsn(FALOAD);
mv.visitInsn(FMUL);
mv.visitVarInsn(FSTORE, 3);
mv.visitLabel(skip);
line(mv, 103);
mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ILOAD, 2); mv.visitVarInsn(FLOAD, 3);
mv.visitMethodInsn(INVOKESTATIC, "gen/Router", "twice", "(F)F", false);
mv.visitInsn(FASTORE);
mv.visitInsn(RETURN);
mv.visitMaxs(4, 4); mv.visitEnd();
cn.visitEnd();

Bindings bindings = Bindings.none()
        .constant("gen/Flags", "SCALE", true)
        .table("gen/Tables", "W", new float[]{0.5f, 1f, 2f, 4f});
AsmJit.Entry entry = new AsmJit.Entry("gen/Router", "k", "([F[F)V",
        Map.of("compilationProperties", "opt=2"), Map.of("x", 64));
AsmJit.Unit unit = new AsmJit.Unit(ClassSource.of(getClass().getClassLoader()),
        Map.of("gen/Router", cn), List.of(entry), bindings, Links.none());
AsmJit.Options options = AsmJit.Options.DEFAULT.synthesizeLvt(true);

AsmJit.Result jit = AsmJit.compile(unit, options);
if (!jit.ok()) {
    for (AsmDiagnostic d : jit.diagnostics()) {
        System.err.println(AsmJit.position(d, unit) + ": " + d.code() + " " + d.message());
    }
    return;
}
// дальше - 5.9: ctx.compileProgram(jit.module()) / OpenClEmitter.program(...) / Interpreter.run(...)
```

Без привязки `gen/Flags.SCALE` та же единица даёт `GPU0115` в позиции `router/demo:102`.

### 5.11. `AsmFrontend`: готовые class-файлы

```java
AsmFrontend.Result r = AsmFrontend.compile(ClassSource.of(loader), List.of("pkg.Kernels"), AsmFrontend.Options.DEFAULT);
// ядра - методы с @Kernel (аннотация с retention CLASS видна в байткоде); r.kernels(): module, entry.kernelId(), bytes
```

`AsmFrontend.compileTree(classesDir, classpath, options)` — то, что делает плагин: строит IR всех ядер каталога и
пишет ресурсы `META-INF/gpu/**`. LVT обязательна (`javac -g`, Gradle и Maven делают это по умолчанию).

### 5.12. Масштаб и время

Фронтенд и оптимизатор почти линейны по размеру метода. Глубокая вложенность сверхлинейна. Замеры: фронтенд +
`opt=2`, мс, машина разработки:

| форма | 1024 | 4096 | 8192 |
|---|---|---|---|
| синтетический роутер (узлов) | 100 + 60 | 685 + 167 | 2508 + 451 |
| вложенный `if` | 68 + 73 | 201 + 1246 | 437 + 3679 |
| вложенный `while` | 198 + 192 | 1565 + 1858 | 5770 + 7899 |
| вложенный `?:` | 316 + 94 | 1352 + 989 | 6714 + 3958 |

Превысив бюджет, оптимизатор понижает `opt` до 1 и затем 0. Предупреждение рантайма — `... compiled at opt=N`.
Частичный результат не берётся, поэтому IR детерминирован при выбранном уровне. Огромный роутер лучше делить на
несколько единиц или методов: каждый метод — отдельная device-функция.

## 6. Диагностики, которые встречаются чаще всего

| Код | Причина | Что делать |
|---|---|---|
| `GPU0115` | `getstatic` непривязанного поля | `Bindings.constant/table` либо передайте значение параметром |
| `GPU0116` | массив переприсвоен, таблица записана или сохранена в локаль | индексируйте `Tables.X[i]` прямо, массив-локаль создавайте один раз |
| `GPU0112` | метод не поддержан (в том числе `Math.sin`) | `Gpu.*` или свой `@DeviceFunction` |
| `GPU0113` | метод библиотеки без `@DeviceFunction` | пометьте аннотацией (сгенерированным классам она не нужна) |
| `GPU0121` | адресное пространство массива не совпало с параметром функции | массив из тела (`new T[N]`) — параметр `@Private`, local — `@Local` |
| `GPU0123` | `@Private` у параметра ядра, у скаляра или вместе с `@Local`/`@Global` | только у параметров-массивов `@DeviceFunction` |
| `GPU0301` | нет LVT | `synthesizeLvt(true)` или `javac -g` |
| `GPU0303` | класс вызываемого метода не найден | добавьте его в `ClassSource` |
| `GPU0304` | неприводимый поток управления | структурные `if/loop`, `break/continue` ближайшего цикла |
| `GPU0305` | на стыке на стеке лежит не результат ромба | сохраните операнд в локаль до перехода (контракт 5.4) |
| `GPU0306` | класс не читается (версия class-файла новее ASM) | собирайте библиотеку под `--release ≤ 23` для ASM 9.7 |
| `GPU0307` | LVT не синтезируется: разные типы в слоте, чтение до записи | один тип на переменную, запись до чтения |
| `GPU0308` | значение привязки не того типа, пустая таблица | тип значения = тип поля |
| `GPU0309` | `@Extern` без связи | `Links.redirect(...)` с тем же дескриптором |
| `GPU0311` | точка входа не найдена или атрибуты заданы дважды | проверьте `owner/name/desc` |
| `GPU0312` | фронтенд вышел за бюджет | предупреждение; IR полный |
| `GPU0902` | внутренняя ошибка фронтенда | ошибка библиотеки — пришлите пример |
| `RT-D01`/`RT-D02` | нет устройства / устройство не умеет нужного | проверьте драйвер OpenCL, `required` ядра |
| `RT-D04` | `local`- или `__constant`-память больше предела устройства | уменьшите таблицы или local-массивы |
| `RT-L01`, `IN-L01` | размер запуска не делится на размер группы | глобальный размер кратен `reqd_work_group_size` |
| `RT-C01` | драйвер не собрал исходник | ошибка кодогена — пришлите `dumpSources` |

Отладка: `GpuContext.builder().dumpSources(path)` (или `-Djavatogpu.dump=...`) пишет OpenCL C каждого ядра,
`com.sixik.gpu.tools.IrDump.dump(module, false)` — текст IR (модуль `tools`, в репозитории).

## 7. Что проверить перед тем, как считать интеграцию готовой

1. `jit.ok()` и нет неожиданных предупреждений (`GPU0130` — барьер под расходящимся условием, `GPU0312` — бюджет).
2. Результат интерпретатора совпадает с эталоном на JVM. Для `exact`-ядер — побитово.
3. На устройстве результат совпадает с интерпретатором; размер запуска кратен группе.
4. `moduleId` стабилен между запусками для одинакового входа: это проверка, что генератор детерминирован.
5. Для мода: один бэкенд в jar, ASM загрузчика 9.7+, библиотека device-функций под `--release ≤ 23`.
