# 18. `frontend-asm` как компилятор сгенерированного байткода (сценарий Generator Accelerator 2)  `[PROPOSED 2026-11-18]`

Продолжение раздела 17. Раздел 17 сделал фронтенд **шагом сборки** над class-файлами javac. Здесь описано, что нужно
добавить, чтобы тот же фронтенд принимал байткод, который **генерирует программа во время работы**. Первый потребитель —
компилятор Density Functions в GA2 (`dfc`) и его GPU-путь (`gpu`). Решения 18.6 пока только предложены.

## 18.0 Исходное состояние

**JavaToGpu.** `frontend-asm` (A0–A6) читает class-файлы через `ClassSource` и строит тот же IR, что `frontend-java`.
Он работает только как шаг сборки. Ядра он находит по аннотациям и пишет ресурсы `META-INF/gpu/**`. Для него нужны LVT
(`GPU0301`) и javac-формы. Модуль IR содержит одно ядро: ядро — последняя функция, и один артефакт кодогена соответствует
одному ядру. `F_INLINE` пока только хранится, кодоген его не учитывает. `getstatic` запрещён (`GPU0115`). `spec_const`
зарезервирован под 9A. Рантайм работает на FFM и требует JDK 25.

**GA2 (`dev/1.21.1/2.0`).**

```
DensityFunction (объекты) ──► dfc/ir/Node (интернированное дерево; BytecodeLifter для модовых DF)
                                   │
              ┌────────────────────┴────────────────────┐
   dfc/codegen/Emitter (ASM → hidden class, CPU)   gpu/codegen/ClEmitter (Node → OpenCL C через StringBuilder)
                                                         + gpu/surface/SurfaceSpec, gpu/light/LightProgram
                                                         │
                                         gpu/runtime (LWJGL OpenCL через ClApi, ProgramCache, ProgramParts)
```

- `ClEmitter` — 4082 строки. Он повторяет `Emitter` операция за операцией: 7 контекстов вычисления (`FLAT`, `HOIST`,
  `CORNER`, `BLOCK`, `VEIN`, `APOINT`, `XZBLOCK`), точности `F64`/`F32` и отдельно «certified» f32 с границами ошибки и
  перепроверкой в double. Около 1090 строк — сырой OpenCL в text-блоках (аквифер, жилы руд, поиск по R-дереву климата,
  Xoroshiro, упаковка секций, heightmap, double-float `ff`). Ещё ~400 строк в `SurfaceSpec` и ~120 в `LightProgram`.
- Около 35 ядер (`k_flat`, `k_hoist`, `k_corner[32][_pc]`, `k_block[32]`, `k_status`, `k_biome`, `k_pack`, …) живут в
  **одной** программе, функции у них общие. Переключатели (`aquifers`, `veins`, `biomes`, `fluidLava`, `tables`,
  `noinline`, `certify`, …) включают и выключают куски текста. `NI` — это `__attribute__((noinline))`. Внешние ссылки
  в тексте подставляет `resolveExterns`.
- Сопровождать это тяжело: два эмиттера одной семантики, варианты точности написаны руками, OpenCL-текст не проходит
  проверку типов, не исполняется на JVM и не тестируется по частям, оптимизации (hoist, memo, noinline) тоже ручные.
  Бэкенд один — OpenCL.

## 18.1 Цель и границы

**Цель.** Генератор GA2 выпускает через ASM (тем же инструментом, что и CPU-путь) **байткод GPU-подмножества**:
статические методы, примитивы, массивы, `Gpu.*`. Неизменные части (шум, сплайны, аквифер, RNG, R-дерево, упаковка)
пишутся на обычной Java как `@DeviceFunction`/`@Kernel` и лежат в jar мода. Дальше работает JavaToGpu:

```
GpuEmitter GA2 (ASM, ClassNode в памяти) ─┐
библиотека GA2 в jar (javac -g)          ─┼─► frontend-asm (в памяти) ─► IR (много точек входа)
привязки констант, таблица связей        ─┘        ─► ir-optimizer ─► codegen-opencl ─► OpenCL C + ABI ядер
                                                                              │
                                     runtime (Java 17) + runtime-opencl-lwjgl — Minecraft 1.18–1.21.x
                                     runtime + runtime-opencl-ffm (Java 25)    — Minecraft 26.x
```

**Что это даёт GA2.** Остаётся один обходчик `Node` вместо двух. Сырой OpenCL становится Java, которая тестируется на
JVM и проверяется `ir-interpreter`. Оптимизации, учёт регистров, inline-политику и деление ядер берёт на себя
библиотека. CUDA и другие бэкенды достаются без переписывания GA2.

**Не цели:**

- байткод CPU-пути GA2 (`CompiledProgram`: поля-объекты, `NoiseSpec`, интерфейсные `compute`) — он не GPU-подмножество;
  для GPU генератор выпускает отдельный байткод;
- замена `BytecodeLifter` GA2 (подъём модовых DF в `Node`) — он остаётся (будущее — 18.9);
- «произвольный байткод». Подмножество задаёт контракт 18.3.

## 18.2 Сценарии

Формат: что нужно GA2 → что уже есть → чего не хватает → проект.

### S1. Компиляция сгенерированных классов в памяти

- **Нужно:** при загрузке мира или после смены роутера скомпилировать классы, которых нет на диске.
- **Есть:** `AsmFrontend.compile(ClassSource, List<String>, Options)` — чистая функция; `ClassSource.of(Map)`,
  `ClassSource.of(ClassLoader)`.
- **Не хватает:** входа деревом `ClassNode`, явного списка точек входа (без сканирования аннотаций и `$jvm`), результата
  в виде модуля без ресурсов.
- **Проект:** `AsmJit.compile(Unit, Options) → Result` (18.4). Внутри фронтенд и так работает с `ClassNode`, поэтому вход
  деревом — это кэш `ClassNode` по имени перед `ClassReader`. Вход деревом снимает три ограничения: не нужен
  `ClassWriter` (нет лимита 64 КБ на метод и 65535 записей пула констант), не нужны `COMPUTE_FRAMES`, не нужна
  сериализация.

### S2. Связь сгенерированной части с библиотекой в jar

- **Нужно:** сгенерированные корни вызывают библиотеку (`perlinSum`, `spline`, `clampedMap`). Библиотека вызывает
  сгенерированное: аквифер читает `ap_erosion`, `ap_depth` и т. п. роутера, которого при сборке jar ещё нет.
- **Есть:** вызов `@DeviceFunction` другого класса и из jar (A4). Обратной связи нет.
- **Проект — таблица связей `Links`.** В библиотеке объявляется заглушка:

```java
public final class RouterLinks {
    @DeviceFunction @Extern               // новая аннотация api, retention CLASS
    public static double erosion(int bx, int by, int bz, double[] mv, byte[] mk /* … */) {
        throw new IllegalStateException("linked at runtime");   // тело фронтенд не читает
    }
}
```

`Links.redirect("ga2/RouterLinks", "erosion", desc, "gen/Router$7", "ap_erosion", desc)`: при разрешении `invokestatic`
вызов заглушки заменяется вызовом цели. Дескрипторы должны совпадать (`GPU0309`). Если `@Extern` вызывается без связи,
выдаётся `GPU0309`. Это то же, что `resolveExterns`, но по символам, а не по тексту.

### S3. Параметры времени компиляции

- **Нужно:** вместо `if (aquifers) src.append(...)` писать обычный `if (Ga2Flags.AQUIFERS)` в Java-библиотеке. Сюда же
  относятся константы раскладки (`cellWidth`, `height`, `minY`, `cellsY`) и маленькие таблицы (`pickerIntervals`).
- **Есть:** `GPU0115` на любой `getstatic`. В байткоде javac константы времени компиляции уже подставлены.
- **Проект — привязки `Bindings`.** `bind(owner, field, value)`: `getstatic` привязанного `static` (не обязательно
  `final`) поля примитивного типа превращается в `const_*`. Оптимизатор сворачивает условия и удаляет мёртвые ветки
  (`Simplify` + DCE уже есть). Тип значения не совпадает с дескриптором — `GPU0308`. Непривязанный `getstatic` по-прежнему
  даёт `GPU0115`. Привязка примитивного массива — решение 18.6-5. На JVM-пути поле читается как обычно, поэтому
  библиотеку можно тестировать JUnit'ом, выставив поля.

### S4. Одна программа — много точек входа

- **Нужно:** около 35 ядер с общими функциями собираются драйвером **один раз**. Холодная сборка — главная цена для GA2:
  ради неё есть `ProgramParts`, сборка в отдельных процессах, `nonce` для замеров и дисковый кэш бинарей.
- **Есть:** модуль = одно ядро (последняя функция); «одно ядро — один артефакт».
- **Проект:**
  1. **IR.** В модуле может быть несколько функций вида `KERNEL`. Правило «ядро последнее» заменяется правилом
     «вызываемые раньше вызывающих». Порядок точек входа хранит таблица `entries`. У каждой точки входа свой `kernelId`
     — хэш её достижимого замыкания (кэш одиночных ядер не меняется). У модуля свой `moduleId`. Версия `IrBinary`
     повышается. Валидатор: имена точек входа уникальны (`IR-M05`).
  2. **Кодоген:** каждая функция печатается один раз, все точки входа — `__kernel`. Флаги `fp` у функции — пересечение
     флагов всех точек входа, которые её достигают (правило уже есть для device-функций).
  3. **inline-политика** начинает работать: `INLINE_NEVER` → `__attribute__((noinline))`, `INLINE_ALWAYS` → оптимизатор
     встраивает сам. В `api`: `@DeviceFunction(inline = Inline.NEVER)`. `frontend-java` читает тот же элемент.
  4. **Рантайм:** `GpuContext.compileProgram(IrModule) → CompiledProgram { CompiledKernel kernel(String) }` — одна
     сборка драйвером на все точки входа, общий дисковый кэш.

### S5. Состояние work-item: контекст, memo, RNG

- **Нужно:** `Ctx* c` в GA2 — это координаты, базы таблиц, memo (`R mv[NM]; uchar mk[NM]`, ленивое заполнение при первом
  использовании) и около 40 полей. Каждая `NI`-функция принимает `c`. Xoroshiro изменяет состояние через `&r`.
- **Есть:** private-массивы константного размера, их передача в device-функции и запись в них; `@Struct` с
  value-семантикой (запись в поле параметра — `GPU0194`, массивы и указатели в struct запрещены).
- **Этап 1 (работает уже сейчас, без изменений IR):** контекст разворачивается в параметры. Memo — это
  `double[] mv, byte[] mk`, которые объявлены в ядре и передаются вниз. Таблицы — параметры `@Global(constant = true)`.
  RNG — `long[] st = new long[2]`. Генератору всё равно, сколько у функции параметров.
- **Этап 2 (`@Ref`, решение 18.6-2):** параметр-ссылка на struct — `Ptr<private, S>` (тот же механизм, что у
  `FloatPtr`-ссылок, 5.21). Добавляется вид struct «только private»: поля-указатели `Global*Ptr` и поля-массивы
  `@Size(n)`. Такой struct не пересекает ABI хоста и не копируется (присваивание и `copy()` — `GPU0198`). Семантика на
  JVM совпадает: там это та же ссылка на объект. Нужен этот этап, только если замер B8 покажет, что передача параметров в
  `noinline`-вызовах стоит заметно больше одного указателя.

### S6. Побитовая точность F64 и certified F32

- **Нужно:** F64-вариант даёт побитово то же, что `NoiseChunk` GA2. Certified F32 опирается на точный `fma`, `nextafter`,
  `as_int`/`as_long`, отсутствие FMA-слияния и отсутствие переассоциации.
- **Есть:** strict по умолчанию (без FMA-слияния, `Math.min/max`-обёртки с NaN и `-0.0`), насыщающее float→int, оборот
  целых, правильно округлённое деление и `sqrt` при `CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT`.
- **Проект:**
  1. **Свойство `exact`** в `compilationProperties`. Валидатор фронтенда обходит достижимые операции; builtin с допуском
     больше 0 ULP (`sin`, `exp`, …) или флаг `fp` — ошибка `GPU0310` с именем операции. Для f32-деления и `sqrt` ядро
     требует `CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT` (иначе `RT-D02`).
  2. **Таблица JDK-интринсиков** (`frontend-common`, общая с `frontend-java`; сейчас это `GPU0112`). В неё входят **только
     побитово точные**: `Math.floor/ceil/rint/abs/sqrt/min/max/fma/nextUp/nextDown/nextAfter/copySign/signum`,
     `Math.floorDiv/floorMod` (раскрытие в IR), `Double.doubleToRawLongBits/longBitsToDouble`,
     `Float.floatToRawIntBits/intBitsToFloat` (`bitcast`), `Integer/Long.rotateLeft/rotateRight/numberOfLeadingZeros/
     numberOfTrailingZeros/bitCount`, `Long.compareUnsigned`, `Math.multiplyHigh`. `Math.sin` и подобные в таблицу не
     входят: молча подменять семантику нельзя.
  3. **Оптимизатор не спекулирует.** CSE/LICM не вводят вычисление на путь, где его не было (ни вызов, ни цикл). Для GA2
     это важно и для скорости: ленивость `Ap2 MUL/MIN/MAX` и `RangeChoice` — главная экономия. Проверка: golden-тест
     «вызов из ветки не поднят» и счётчик вызовов в интерпретаторе.

### S7. Синтетический байткод: без LVT и строк

- **Нужно:** генератор не пишет LVT и `LineNumberTable`. Строки позиций ему лучше заменить идентификаторами узлов `Node`.
- **Есть:** `GPU0301`. Веб переменной строится union-find'ом записей LVT (`MethodLowerer.webParent`), шаг `for`
  определяется по строке.
- **Проект:** `Options.lvt = REQUIRED | SYNTHESIZE`. В режиме `SYNTHESIZE` проход `LvtSynth` **до** понижения строит
  `LocalVariableNode` и вставляет в `MethodNode`: веб — по достигающим определениям, тип — по семейству опкода
  (`istore`→`I`, `dstore`→`D`, `astore` — тип массива из абстрактной интерпретации), диапазон — от определения до
  последнего использования. Остальной конвейер не меняется. `boolean/byte/short/char` в таком режиме не выводятся (это
  `int`), и контракт 18.3 их запрещает в локалях. Слот веба с несовместимыми типами — `GPU0307`. Без строк циклы
  восстанавливаются как `while` (поведение то же, 17.9-4).
- **Позиции:** генератор пишет `visitLineNumber(nodeId, label)` и `SourceFile = "router/<dimension>"`. Диагностика
  выглядит как `router/overworld:4711: GPU0xxx …`, а GA2 переводит 4711 в узел `Node`.

### S8. Встраивание в Minecraft 1.21.1 (Java 21)

- **Факт:** Minecraft 1.21.1 требует Java 21. FFM финальный только с Java 22, а рантайм JavaToGpu был собран под 25.
- **Сделано 2026-11-18 — бэкенды драйвера (решение 18.6-1 принято):**

  | Модуль | Java | Содержание |
  |---|---|---|
  | `runtime` | `--release 17` | `GpuContext`, кэш, запуск, fallback; память хоста — `HostMemory`/`HostArena`, драйвер — SPI `DeviceApiProvider` |
  | `runtime-opencl-ffm` | 25 | прежний `OpenClApi` (FFM), `FfmMemory` (мост `HostMemory` ↔ `MemorySegment`) |
  | `runtime-opencl-lwjgl` | `--release 17` | `LwjglOpenClApi` поверх `lwjgl-opencl` 3.3.3 (`ncl*` с адресами), `NativeMemory` на `MemoryUtil` |

  Компиляторная часть (`api`, `ir`, `ir-optimizer`, `codegen-opencl`, `frontend-common`, `frontend-asm`) тоже
  собирается с `--release 17`; `ScopedValue` в `api.WorkItem` заменён `ThreadLocal` (JMH `ApiBenchmarks`: не медленнее —
  `fallbackLoop` 13.9 против 15.0 нс/op). `ir-interpreter` (только тесты и инструменты) — `--release 21` ради
  виртуальных потоков групп с барьерами.
- **2026-11-18 — нижняя граница опущена с 21 до 17** (Minecraft 1.18–1.20.4 тоже на Java 17). Мешали только switch по
  шаблонам в `runtime` и бэкенде LWJGL (заменены цепочками `instanceof`) и `Thread.ofPlatform()` в `JavaToGpu.warmUp`.
  Проверено: сборка и все тесты; device-тесты на RTX 5070 через FFM и LWJGL совпадают (`:runtime:deviceTestLwjgl`);
  ядро на JVM 17 (Zulu 17.0.20): выбран LWJGL, FFM-jar на classpath пропущен, `computeSine`, передачи 16 МБ и виды
  памяти работают. Не проверено: ядро LWJGL из игр до 1.20.5 (старше 3.3.3).
  Выбор бэкенда — `ServiceLoader`, по убыванию приоритета (FFM 200 на JVM 25+, LWJGL 100), явно —
  `-Djavatogpu.backend` / `JAVATOGPU_BACKEND`; бэкенд, чьи классы не грузятся в этой JVM, пропускается. Плагин:
  `gpu { backend = 'ffm' | 'lwjgl' | 'none' }`. Мод выбирает модуль при сборке под свою версию Minecraft: сканер классов
  загрузчика на 1.21.1 может упасть на class-файлах версии 69, поэтому оба бэкенда в один jar мода не кладутся.
- **Проверено** (pocl 5.0, CPU OpenCL): тесты `runtime` на фейке и на устройстве — с FFM на JDK 25 и с LWJGL на JDK 21 —
  дают тот же результат, что до разделения; расходится с интерпретатором одно и то же ядро (`IndexKernels.ids`, особенность
  pocl) на обоих бэкендах.
- **Компиляторный выход для чужого рантайма** остаётся полезным (GA2 может сначала оставить свой `ClApi`):
  `OpenClCodegen.program(IrModule, EmitOptions) → ClProgram(String source, String buildOptions, List<KernelAbi> kernels)`,
  где `KernelAbi(name, params[i] = {kind: BUFFER|SCALAR|LOCAL, elem, space, access, size})`.
- **ASM в загрузчике мода (решение 18.6-7):** `compileOnly` на ASM, используется ASM загрузчика (NeoForge/Fabric
  поставляют 9.7+). Только API `Opcodes.ASM9` и tree API. Если загрузчик дал ASM старше 9.7 — `GPU0306` с версией.

### S9. Масштаб, время и кэш

- **Нужно:** роутер overworld с модами даёт тысячи узлов. Разделение на методы по 8 КБ на GPU-пути не нужно, поэтому
  методы будут длинными. Вложенность `Select`/`RangeChoice`/сплайнов глубокая.
- **Проект:**
  1. Алгоритмы `Cfg`/`Structurer`/`Scopes` — итеративные, без рекурсии по глубине вложенности (сейчас часть рекурсивна —
     проверить в B0), почти линейные по числу блоков.
  2. **Бюджет времени:** `Options.budget` (по умолчанию 2 с на модуль для фронтенда и оптимизатора). При превышении
     оптимизатор понижает уровень (`opt`), а не падает. Факт пишется в диагностику-предупреждение.
  3. **Детерминизм:** одинаковый вход — побайтово одинаковый IR, поэтому `moduleId` стабилен и дисковый кэш
     (IR → исходник → бинарь драйвера) попадает между запусками. В режиме `tables` (по умолчанию в GA2) смещения шума
     лежат в буферах, и IR зависит только от структуры роутера, а не от сида: одна сборка на набор модов, а не на мир.

### S10. Проверка

Три уровня, каждый ловит свой класс ошибок:

1. **Генератор:** сгенерированный GPU-байткод загружается как hidden class и исполняется на JVM по точкам. Результат
   сравнивается с CPU-программой GA2 (`LiftValidator`/`ChunkCompare`). Так ошибка генератора видна без GPU.
2. **Фронтенд и оптимизатор:** `ir-interpreter` по IR сравнивается с п. 1 побитово (F64, `exact`).
3. **Устройство:** исполнение на GPU сравнивается с интерпретатором (`GpuAssert`), а итог — с текущим `ClEmitter` на
   `GpuVerify` GA2 до момента, когда старый путь удалят.

## 18.3 Контракт сгенерированного байткода

Это то, что гарантирует генератор. Нарушение даёт диагностику, а не неверный код.

| Правило | Причина |
|---|---|
| Методы `static`. Точки входа — `void`, `@Kernel` в `invisibleAnnotations` либо в `Unit.entries` | 17.4 |
| Типы локалей `I J F D` и массивы. `Z B S C` — только как элементы массивов | S7: тип выводится по опкоду |
| Массивы: параметры (`@Global`, `@Global(constant = true)`, `@Local`) либо `newarray` константной длины в локаль | `GPU0117` |
| **Операнд, нужный внутри ветки, сохраняется в локаль до перехода. На стыке на стеке лежит только результат ромба** | 17.8: ромбы, `GPU0305` |
| Без `dup*` значений с эффектами (вызовы, запись memo) — только через локаль | 17.8, иначе спилл и другой порядок |
| Поток приводимый, циклы естественные, `break/continue` — только ближайшего цикла | `GPU0304`, `GPU0104` |
| `getstatic` — только привязанные поля (S3) | `GPU0115` |
| Вызовы: `Gpu.*`, JDK-интринсики (S6), `@DeviceFunction` библиотеки, сгенерированные методы, `@Extern` через `Links` | 17.11, S2 |
| Нет исключений, мониторов, объектов, кроме API-типов (`Float2` для `ff` и т. п.) | 17.6 |

Пример: `Ap2 MUL` с пропуском второго операнда (vanilla: `d0 == 0.0 ? 0.0 : d0 * b`) в форме контракта:

```java
ev(a); mv.visitVarInsn(DSTORE, t);                 // операнд ветки — в локаль
mv.visitVarInsn(DLOAD, t); mv.visitInsn(DCONST_0); mv.visitInsn(DCMPL);
Label mul = new Label(), end = new Label();
mv.visitJumpInsn(IFNE, mul);                       // NaN: dcmpl = -1 → ветка mul, как в vanilla
mv.visitInsn(DCONST_0); mv.visitJumpInsn(GOTO, end);
mv.visitLabel(mul); mv.visitVarInsn(DLOAD, t); ev(b); mv.visitInsn(DMUL);
mv.visitLabel(end);                                // на стыке только результат → select / temp
```

Memo-узел (сейчас `sX_id` в `ClEmitter`) в эквивалентной Java, этап 1:

```java
@DeviceFunction(inline = Inline.NEVER)
static double sC_12(double[] mv, byte[] mk, int bx, int by, int bz,
                    byte[] pm, int[] oi, double[] od, float[] of) {
    if (mk[MB_CORNER + 12] != 0) return mv[MB_CORNER + 12];
    double r = DfNoise.nn(pm, oi, od, of, 3, bx * 0.25, 0.0, bz * 0.25) * 4.0;   // библиотека GA2
    mv[MB_CORNER + 12] = r; mk[MB_CORNER + 12] = 1;
    return r;
}
```

## 18.4 API

```java
package com.sixik.gpu.frontend.asm;

public final class AsmJit {
    /** Чистая функция: без файлов, без потоков, без глобального состояния. */
    public static Result compile(Unit unit, Options options);
}

public record Unit(ClassSource classes,                 // библиотека: ClassSource.of(Ga2.class.getClassLoader())
                   Map<String, ClassNode> generated,    // сгенерированное, деревом (S1)
                   List<Entry> entries,                 // точки входа модуля в этом порядке (S4)
                   Bindings bindings,                   // S3
                   Links links) {}                      // S2

public record Entry(String owner, String name, String desc,
                    Map<String, Object> attributes) {}  // явные элементы @Kernel, если аннотации нет; → KernelAttributes.parse

public record Options(boolean strictBarriers, Lvt lvt, Duration budget) {
    public enum Lvt { REQUIRED, SYNTHESIZE }
}

public record Result(IrModule module,                   // много KERNEL-функций
                     List<String> entryNames,
                     List<AsmDiagnostic> diagnostics) { public boolean ok(); }

// ClassSource: + chain(ClassSource...), + ofNodes(Map<String, ClassNode>)
// Bindings:    constant(owner, field, Object boxedPrimitive), table(owner, field, Object primitiveArray)  // table — 18.6-5
// Links:       redirect(owner, name, desc, toOwner, toName, toDesc)
```

```java
// codegen-opencl, компиляторный артефакт (S8)
public record ClProgram(String source, String buildOptions, List<KernelAbi> kernels) {}
public static Result<ClProgram> program(IrModule module, EmitOptions options);
```

**Новые диагностики** (группа `GPU03xx`, `diagnostics.def`):

| Код | Сообщение |
|---|---|
| `GPU0307` | Slot {0} of {1} holds incompatible types {2} in one variable (synthesized LVT) |
| `GPU0308` | Binding of {0} has type {1}, the field is {2} |
| `GPU0309` | Call to {0}: no link, or the link target {1} has another descriptor |
| `GPU0310` | Kernel {0} is `exact`, but uses {1} (tolerance {2} ULP or fp flag {3}) |

**Изменения IR, кодогена и `api`:** несколько `KERNEL` в модуле и таблица `entries`, `kernelId`/`moduleId`, версия
`IrBinary` (S4); печать `F_INLINE` (S4); `@DeviceFunction(inline)`, `@Extern` (S2, S4); этап 2 — `Ptr<private, Struct>`
и struct «только private» (S5).

## 18.5 Что из `ClEmitter` куда переезжает

| Сейчас (OpenCL-текст) | Станет |
|---|---|
| `prelude`: `jmind/jmaxd`, `jlfloor`, `jfloordiv`, `jwrap`, `jclamp`, `jclampedMap`, `jsqueeze` | Java-библиотека `Jm`: `Math.min/max/floorDiv` — интринсики (S6), остальное — обычные `@DeviceFunction` |
| `tableNoiseFunctions`: `perlin_sum`, `nn`, `bn` | `DfNoise` (`@DeviceFunction`, таблицы — параметры) |
| `splineFunctions`: `sfindg`, вычисление | `DfSpline` |
| `Fn` / `CFn`: обход `Node` по 7 контекстам, memo-слоты, `reset_*` | **единственная генерируемая часть**: `GpuEmitter` GA2 на ASM, общий с CPU `Emitter` обход, другой «нижний слой» |
| `kernels()` / `batchKernels()` | тонкие сгенерированные точки входа; тяжёлые тела — в библиотеке |
| `decisionCode`: `compute_fluid`, `pressure`, `aquifer_decide`, `vein_rule`, `picker` | библиотека: флаги через `Bindings`, корни `ap_*` через `Links` |
| R-дерево климата (`node_dist`, поиск) | библиотека, таблицы — буферы |
| Xoroshiro (`xo_at`, `xo_nextInt`) | библиотека, состояние `long[2]` (этап 1) |
| `packKernels`, heightmap | библиотечные `@Kernel`, размеры через `Bindings` |
| certified: `ff_*`, `rerr`, `lat2`, `*32` | библиотека на `Float2` + `Math.fma/nextAfter/floatToRawIntBits` |
| `#define NI` | `@DeviceFunction(inline = NEVER)` |
| `resolveExterns` | `Links` |
| `extra` (`gpu.surface`) | вторая сгенерированная `Unit`-часть в том же модуле |
| `biomes=false` и т. п. (выбрасывание ядер) | состав `entries` + DCE |

`SurfaceSpec` и `LightProgram` переводятся так же, после пилота.

## 18.6 Решения (предложены)

1. **[DECIDED 2026-11-18] Рантайм JavaToGpu сразу, через бэкенды драйвера** (S8): `runtime` под Java 17 (было 21, опущено 2026-11-18) +
   `runtime-opencl-lwjgl` для Minecraft 1.21.x, `runtime-opencl-ffm` для Java 25. *Отклонено:* «только компилятор» —
   GA2 держал бы свою копию запуска, кэша и ABI; свой JNI вместо LWJGL — нативы под 6 платформ без выигрыша в скорости.
2. **Контекст:** этап 1 — развёрнутые параметры (S5), `@Ref` — только по итогам замера B8. *Альтернатива:* сразу `@Ref`.
3. **Библиотека перепонижается из байткода при каждой сборке** (стоимость мала, результат кэшируется по хэшу).
   *Альтернатива:* предсобранный IR библиотеки в ресурсах и линковщик `IrLinker`.
4. **Варианты точности выпускает генератор** (два прохода с `D` и `F`). *Альтернатива:* проход IR `retype(F64 → F32)`
   с исключениями — проще для GA2, но легко сломать решётку шума, которая остаётся в double.
5. **Таблицы:** буферы `@Global(constant = true)`, как сейчас. *Альтернатива:* `Bindings.table` → инициализированные
   `__constant`-массивы (новая секция данных модуля). Это экономит аргументы, но у `__constant` лимит около 64 КБ на
   устройство.
6. **LVT:** синтез во фронтенде (`SYNTHESIZE`). *Альтернатива:* генератор пишет LVT сам — дешевле для JavaToGpu, дороже и
   хрупче для каждого генератора.
7. **ASM:** `compileOnly` и ASM загрузчика. *Альтернатива:* shade с релокацией (+~120 КБ, своя версия, без конфликта).

## 18.7 План

| Шаг | Содержание | Готово, когда |
|---|---|---|
| **B0** | ✅ частично (18.8a): масштаб фронтенда, исправление `chain`, ASM 9.7. Осталось: роутеры GA2, решения 18.6. Замеры до кода: размер узлов и методов роутеров vanilla, Terralith и Tectonic в форме контракта (генератор-прототип вне репозитория); время и глубина рекурсии `Cfg/Structurer/Scopes`; проверка `frontend-asm --release 17` с ASM 9.7 | цифры и найденные рекурсии записаны в 18.8; решения 18.6 приняты |
| **B1** | `AsmJit`, `Unit`, вход `ClassNode`, `ClassSource.chain/ofNodes`, явные `entries` | корпус 17.14 через `AsmJit` даёт тот же IR, что `compile` |
| **B2** | `LvtSynth` (`SYNTHESIZE`), `GPU0307`, позиции-узлы | корпус со стёртыми LVT и строками: то же поведение; IR — с пунктами (а)(г) 17.14-3 |
| **B3** | `Bindings` (константы), `Links`, `@Extern`, `GPU0308/0309` | тесты на `ClassWriter`: свёртка флага убирает ветку; библиотека ↔ сгенерированное в обе стороны |
| **B4** | Модуль с многими точками входа: IR, валидатор, `IrBinary`, кодоген, `F_INLINE`, `@DeviceFunction(inline)` | модуль из 3 ядер с общей функцией: одна копия функции в исходнике; `noinline` в выводе; `kernelId` одиночного ядра не изменился |
| **B5** | `exact`, JDK-интринсики (общие с `frontend-java`), тест неспекулятивности оптимизатора, `GPU0310` | F64-образцы: интерпретатор = JVM побитово на `opt=0..3`; `Math.sin` в `exact` даёт `GPU0310` |
| **B6** | ✅ частично: `runtime` и компиляторная часть под `--release 17`, бэкенды `runtime-opencl-ffm/-lwjgl`, `:runtime:deviceTestLwjgl`. Осталось: CI на JDK 17, `ClProgram`/`KernelAbi`, проверка в dev-клиенте | моды NeoForge 1.21.1, Fabric и версия на Java 17 (1.20.1) запускают ядро через `runtime-opencl-lwjgl` |
| **B7** | Масштаб: итеративные алгоритмы, `budget`, детерминизм | роутер B0 укладывается в бюджет; два прогона дают одинаковый `moduleId` |
| **B8** | **Пилот GA2:** `DfNoise`, `DfSpline`, `Jm` на Java; `GpuEmitter` для `FLAT`, `HOIST`, `CORNER` в F64; ядра `k_flat`, `k_hoist`, `k_corner` | критерии 18.8 |
| **B9** | Остальные контексты, аквифер, жилы, биомы, упаковка, certified F32; затем `SurfaceSpec`, `LightProgram`; удаление `ClEmitter` | `GpuVerify` GA2 зелёный без `ClEmitter` |

Шаги B1–B7 — работа в JavaToGpu, B8–B9 — в GA2 вместе с автором `dfc`.

## 18.8 Критерии приёмки пилота (B8)

1. **Точность:** выходы `k_flat`, `k_hoist`, `k_corner` (F64) побитово совпадают с текущей программой `ClEmitter` на
   `ChunkCompare` для 3 сидов × 1024 чанка × vanilla/Terralith.
2. **Время компиляции:** `AsmJit` + оптимизатор + кодоген не дольше 10 % от сборки драйвера той же программы (порог
   уточнить в B0).
3. **Сборка драйвером:** не дольше исходника `ClEmitter` больше чем на 10 % (холодный кэш, `nonce`).
4. **Скорость ядер:** не медленнее `ClEmitter` больше чем на 5 % (RTX 5070 + одно устройство AMD или Intel).
5. **Сопровождаемость:** библиотечные функции пилота покрыты JUnit на JVM; генерируемая часть — это только обход `Node`.

## 18.8a Итог B0 (2026-11-18, частично)

Замер — `ScaleProbeTest` (`frontend-asm`): ядро генерируется исходником, javac `-g` → `AsmFrontend.compile`. Обычный
прогон проверяет глубину 64 для каждой формы; `-Dprobe.scale=true` или `-Dprobe.sizes=...` печатают шкалу,
`-Dprobe.jfr=<файл>` пишет профиль. Время — `asm ms` на машине разработки (Windows, JDK 25), без прогрева.

| Форма | 64 | 128 | 256 | 512 | 1024 |
|---|---|---|---|---|---|
| вложенные `if/else` | 12 мс → 3 | 47 → 11 | 528 → 17 | javac не собирает | — |
| вложенные `?:` | 4 → 3 | 43 → 3 | 482 → 6 | 7089 → 9 | `StackOverflowError` → javac не собирает |
| вложенные `for` | 18 | 110 | 845 | javac не собирает | — |
| цепочка `&&` | 1 | 2 | 4 | 12 | 24 |
| прямой код (×8 операторов) | 4 | 5 | 8 | 14 | javac: `code too large` |

(«было → стало» — до и после исправления ниже; где одно число, исправление на форму не влияет.)

- **Найдена и исправлена ошибка A3.** `Structurer.chain` втягивал в цепочку `&&`/`||` любой условный блок на той же
  строке. Вложенный `if/else` или `?:`, записанный в одну строку с внешним (`if (a) { x = …; if (b) … } else …`),
  принимался за `a && b`, цепочка не сворачивалась, и ядро отвергалось `GPU0304` уже на глубине 2. Для GA2 это важно:
  при позициях-узлах (S7) у генератора на одной «строке» будет много операторов. Теперь по строке втягивается только
  условие, которое ведёт на уже существующий выход цепочки; цепочка, которая всё же не сворачивается, укорачивается до
  сворачивающейся (остаток — обычный вложенный регион). Попутно ушла кубическая стоимость `if`/`?:`.
- **Для B7 осталось:** вложенные циклы растут примерно кубически. Профиль: `MethodLowerer.buildWebs` (потоки данных
  на `BitSet` по всем блокам и переменным) и `Scopes.touches` (размещение объявлений — обход дерева на каждую
  переменную). Структуризатор рекурсивен по глубине вложенности (`region → statement → conditional → ifRegion → region`):
  до исправления `?:` глубиной 1024 исчерпывал стек. javac глубже 512 не собирает, поэтому предел по стеку для
  сгенерированного байткода надо мерить классами из `ClassWriter` (B7).
- **ASM 9.7** (`-PasmVersion=9.7` задаёт версию для всех конфигураций модуля): `frontend-asm` компилируется против
  API 9.7 без изменений. Тесты падают только `GPU0306` «Unsupported class file major version 69»: ASM 9.7 читает
  class-файлы до Java 23, а тестовый корпус собран под 25. Для решения 18.6-7 это значит: с ASM загрузчика библиотека
  мода должна быть собрана не новее Java 23 (у модов 1.21.1 — 21); сгенерированное в B1 приходит деревом `ClassNode` и
  `ClassReader` не проходит. Поведенческая проверка на 9.7 требует корпуса, собранного под ≤ 23.
- **Не сделано:** замеры роутеров vanilla/Terralith/Tectonic — нужен генератор-прототип GA2 вне репозитория; решения
  18.6 принимает владелец проекта.

## 18.9 Риски и дальнейшее

- **Дрейф точности** из-за «полезных» преобразований оптимизатора. Защита: `exact` + S10-2 на `opt=0..3` в CI.
- **Зрелость:** JavaToGpu в alpha. Пилот на трёх ядрах показывает выигрыш или проигрыш до того, как переписывать всё.
- **Владелец кода:** GA2 — проект Дениса. Контракт 18.3 и `GpuEmitter` нужно согласовать с автором `dfc` до B8.
- **Дальше:** CUDA через тот же IR (8); `spec_const` для параметров, которые меняются без пересборки (9A); фронтенд с
  привязкой получателя (`getfield this.f` → константа или таблица) — тогда модовые DF можно поднимать прямо в
  device-функции вместо `BytecodeLifter`.
