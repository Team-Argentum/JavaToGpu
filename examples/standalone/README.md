# Самостоятельное приложение

Это отдельная Gradle-сборка: она не включена в корневой `settings.gradle`, не использует `project(...)`,
`includeBuild`, `flatDir` или TestKit plugin-classpath. Плагин, библиотеки и annotation processor загружаются
из Maven-репозитория, созданного задачей `publishLocal`. Текущая версия — `0.2.0-SNAPSHOT`.

Нужен JDK 25 **для JVM самого Gradle**, поскольку плагин и weaver скомпилированы для Java 25.
Toolchain выбирает компилятор и JVM приложения, но не меняет JVM, в которой уже запущен Gradle.

Из корня репозитория в PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\gradlew.bat publishLocal
.\gradlew.bat -p examples/standalone test runFallback installDist
.\gradlew.bat -p examples/standalone runDevice
```

На Linux/macOS задайте `JAVA_HOME` на JDK 25 и используйте `./gradlew` с теми же аргументами.
Wrapper берётся из корня только как исполняемый файл Gradle; это не связывает графы двух сборок.

- `test` проверяет опубликованный `test-support`, IR-ресурсы и подмену main/test-ядер, без устройства.
- `runFallback` исключает все устройства политикой выбора и требует результат `BACKEND JVM`.
- `runDevice` запрещает fallback и требует `BACKEND OPENCL`; без подходящего устройства команда завершается ошибкой.
- `run` выбирает путь автоматически.
- `installDist` создаёт `build/install/gpu-standalone`, включая библиотеки и стартовые скрипты с native-access.

Оба режима исполнения должны вывести:

```text
RESULT [3.0, 5.0, 7.0, 9.0]
```

`runFallback` также выводит предупреждение `RT-D01` — выбранная политика намеренно не допускает ни одного устройства.
Готовый скрипт запуска можно проверить командой:

```powershell
& .\examples\standalone\build\install\gpu-standalone\bin\gpu-standalone.bat device
```

Пример можно скопировать в любой каталог. Тогда передайте адрес локального Maven-репозитория:

```powershell
.\gradlew.bat -p C:\work\my-gpu-app '-PgpuRepository=file:///C:/work/JavaToGpu/build/local-maven' test runFallback
```

`gpuRepository` задаёт репозиторий и для plugin marker в `settings.gradle`, и для библиотек в `build.gradle`.
Maven Central нужен для ASM и JUnit. Внешняя публикация alpha пока не настроена.
После повторной публикации того же SNAPSHOT добавьте потребителю `--refresh-dependencies`, чтобы получить свежие артефакты.

Автоматическая проверка этого же примера:

```powershell
.\gradlew.bat :gradle-plugin:test --tests '*PublishedConsumerTest'
```

Тест копирует исходники во временный независимый проект и проверяет два режима разрешения зависимостей:
обычные Gradle metadata и только Maven POM. Оба проверяют повторную сборку без потери weaving.
