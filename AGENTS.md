# AGENTS.md — работа в репозитории JavaToGpu

**Подключаете JavaToGpu к своему проекту** (ядра на Java, генерация байткода, `AsmJit`, JitPack) — читайте
[`docs/AGENTS.md`](docs/AGENTS.md). Этот файл — для агента, который **меняет сам JavaToGpu**.

## Карта

- `README.md` — обзор и модули; `docs/USER_GUIDE.md` — справочник пользователя; `docs/FRONTEND_ASM_RUNTIME.md` —
  проект и история фронтенда байткода и рантайма для Java 17 (шаги B0–B9, решения 18.6);
  `REWORK_ARCHITECTURE.md` — архитектура, IR (раздел 5), правила модулей (разделы 4, 11).
- Конвейер: `frontend-java` / `frontend-asm` → `ir` (`IrBuilder`, `Validator`, `IrBinary`) → `ir-optimizer`
  (`Optimizer`, проходы на деревьях `Tree`/`Codec`) → `codegen-opencl` (`OpenClEmitter`, `FunctionEmitter`) →
  `runtime` (`GpuContext`) → бэкенд `runtime-opencl-ffm` / `runtime-opencl-lwjgl`. Эталон — `ir-interpreter`
  (`Interpreter`, `Machine`).
- Коды диагностик фронтендов — `diagnostics/diagnostics.def` (единый источник; коды стабильны, номера не переиспользуются).

## Сборка и проверки

Gradle 9.8 запускается на JDK 21+. Toolchain — JDK 25 (если его нет, скачивает foojay). Java-17-модули (`api`, `ir`,
`ir-optimizer`, `codegen-opencl`, `frontend-common`, `frontend-asm`, `runtime`, `runtime-opencl-lwjgl`) собираются с
`--release 17`, `ir-interpreter` — с `--release 21`. Тесты всех модулей собираются под 25.

| Команда | Что проверяет |
|---|---|
| `./gradlew build` | компиляция, все тесты без устройства, `verifyModuleBoundaries` (граф модулей и запрещённые импорты) |
| `./gradlew :codegen-opencl:deviceTest :runtime:deviceTest :test-support:deviceTest --rerun` | тесты с тегом `device` на настоящем OpenCL; без устройства пропускаются |
| `./gradlew :runtime:deviceTestLwjgl` | device-тесты рантайма через бэкенд LWJGL |
| `./gradlew :runtime-opencl-lwjgl:jdk17Smoke` | путь мода на JVM 17: `frontend-asm`, `ClProgram`, `compileProgram` |
| `./gradlew :frontend-asm:test -PasmVersion=9.7` | фронтенд байткода на ASM 9.7 (корпус собирается под Java 21) |
| `./gradlew :frontend-asm:test --tests '*GeneratedScaleTest' -Dprobe.gen=true -Dprobe.sizes=1024,4096` | замер масштаба (печатает таблицу; `-Dprobe.jfr=<файл>` — профиль) |
| `./gradlew publishLocal` | публикация в `build/local-maven`; `examples/standalone` — проверка потребителя |

CI (`.github/workflows/ci.yml`): `build`, `jdk17Smoke` на JDK 17 с pocl, `frontend-asm` на ASM 9.7. Device-тестов в
CI нет: проверяйте их локально, если трогали кодоген, рантайм или семантику IR. Регистры ядер на NVIDIA сообщает
только драйвер (`-cl-nv-verbose` и `CUDA_CACHE_DISABLE=1`), иначе `-1`.

## Соглашения

- **Язык.** Комментарии и Javadoc — по-русски. Сообщения об ошибках, диагностики, логи, тексты `assert` и
  сообщения коммитов — по-английски.
- **Файлы** с переводами строк LF. Java-код правьте редактором (Edit/Write); heredoc с Java в shell ненадёжен.
- **Коммиты.** Каждый законченный шаг — отдельный коммит с понятным сообщением (`feat:`, `fix:`, `test:`, `docs:`).
- **Тесты.** Новое поведение — тест JUnit. Семантику IR сверяйте с интерпретатором. Кодоген — golden-файлами и
  device-тестом, который сравнивает устройство с интерпретатором. В device-тестах выходной массив ядра ставьте
  первым параметром (по нему берётся размер запуска по умолчанию).
- **Детерминизм.** Не итерируйте `HashMap`/`IdentityHashMap` там, где порядок попадает в IR или текст. `kernelId`
  должен быть стабилен между запусками.
- **Глубина.** Фронтенды, оптимизатор, кодоген и интерпретатор работают на потоке `DeepStack`, но новые алгоритмы
  по вложенности пишите итеративно и без квадратов по глубине (замер — `GeneratedScaleTest`).
- **Границы модулей** — в корневом `build.gradle` (`allowedProjectDeps`, `forbiddenByModule`). `ir`,
  `ir-optimizer`, `codegen-opencl` — чистые функции без файлового ввода-вывода.

## Чек-лист: новый узел IR (на примере `Op.CONST_TABLE`)

1. `ir/Op.java` — номер и `name(...)`; `IrBuilder` — конструктор узла; `Validator` — типы и структура (коды `IR-*`).
   Если узел добавляет данные модуля, по возможности держите их в существующих секциях (`IrBinary` не меняется).
2. `ir-interpreter/Machine` — исполнение.
3. `ir-optimizer`: `Codec` (lift/lower `Tree`), правила `Simplify`, учёт в проходах, которые перечисляют опкоды.
4. `codegen-opencl/FunctionEmitter` (и `OpenClEmitter`, если нужны объявления уровня программы).
5. `tools/IrDump` — текст узла; фикстура `Fixtures.rich()` (тест покрытия каталога узлов) и golden `rich.ir.txt`.
6. Фронтенды: `frontend-asm` (`Ex`, `MethodLowerer`, `Emitter`) и, если нужно, `frontend-java`.
7. Рантайм, если узел влияет на ресурсы устройства (`GpuContext`: пределы, ключи кэша).
8. Тесты на каждом уровне и device-тест; документация (`USER_GUIDE.md`, `FRONTEND_ASM_RUNTIME.md`).

## Релиз

`jitpack.yml` + корневой `build.gradle`: на JitPack (`JITPACK=true`) группа — `com.github.Team-Argentum.JavaToGpu`,
версия — тег. Релиз — git-тег `vX.Y.Z` на зелёном `master`. Затем откройте `https://jitpack.io/#Team-Argentum/JavaToGpu`
и запросите сборку тега. Подробности и проверка публикации локально — `docs/AGENTS.md`, раздел 3.
