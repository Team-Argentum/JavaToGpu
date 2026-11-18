package com.sixik.gpu.gradle;

import com.sixik.gpu.frontend.asm.AsmFrontend;
import com.sixik.gpu.weaver.Weaver;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gradle-плагин {@code io.github.deussixik.javatogpu} (REWORK_ARCHITECTURE.md, 3.6): вместо двух ручных блоков в
 * {@code build.gradle} сам делает четыре вещи.
 *
 * <ol>
 *   <li><b>Зависимости.</b> Подключает библиотеки ({@code api, ir, ir-optimizer, codegen-opencl, runtime}) и annotation processor
 *       {@code frontend-java} (с его {@code frontend-common}, {@code api}, {@code ir} и {@code ir-optimizer}) к
 *       {@code main} и {@code test}. Отключается {@code gpu { autoDependencies = false }}.</li>
 *   <li><b>Фронтенд.</b> По умолчанию IR пишет annotation processor ({@code frontend = 'java'}). При
 *       {@code gpu { frontend = 'asm' }} процессора нет: в {@code doLast} задачи компиляции {@code frontend-asm} строит IR
 *       из готовых class-файлов ({@link AsmFrontend#compileTree}) - с device-функциями из jar на classpath; ошибки
 *       роняют задачу.</li>
 *   <li><b>Weave-шаг.</b> После каждой задачи {@code JavaCompile} подменяет тела {@code @Kernel}-методов
 *       ({@link Weaver#weaveTree}): оригинал уходит в {@code <имя>$jvm}, метод становится вызовом хост-фасада. Класс-файлы
 *       переписываются на месте внутри задачи компиляции, поэтому up-to-date и кэш сборки видят уже подменённый результат,
 *       а повторный запуск идемпотентен. Отключается {@code gpu { weave = false }}.</li>
 *   <li><b>Доступ к нативному коду.</b> Добавляет {@code --enable-native-access=ALL-UNNAMED} задачам {@code Test} и
 *       {@code JavaExec}: рантайм обращается к {@code libOpenCL} через {@code java.lang.foreign}.</li>
 * </ol>
 *
 * <p>Плагин тонкий: вся логика подмены - в модуле {@code weaver} (чистая функция над байткодом). Только Gradle; Maven и
 * Java-агент позже, и они не меняют {@code weaver}.</p>
 */
public final class JavaToGpuPlugin implements Plugin<Project> {

    /** Идентификатор плагина. */
    public static final String ID = "io.github.deussixik.javatogpu";
    /** Имя блока настроек. */
    public static final String EXTENSION = "gpu";
    /** Аргумент JVM, разрешающий FFM-доступ к нативному коду. */
    public static final String NATIVE_ACCESS = "--enable-native-access=ALL-UNNAMED";
    /** Группа координат библиотек по умолчанию. */
    public static final String DEFAULT_GROUP = "io.github.deussixik";

    /** Модули, которые попадают в classpath приложения. */
    static final List<String> RUNTIME_MODULES = List.of("api", "ir", "ir-optimizer", "codegen-opencl", "runtime");
    /** Значения {@code gpu.frontend}. */
    static final String FRONTEND_JAVA = "java";
    static final String FRONTEND_ASM = "asm";

    /** Модули processor path. */
    static final List<String> PROCESSOR_MODULES = List.of("frontend-java", "frontend-common", "api", "ir", "ir-optimizer");

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        JavaToGpuExtension ext = project.getExtensions().create(EXTENSION, JavaToGpuExtension.class);
        ext.getVersion().convention(defaultVersion());
        ext.getGroup().convention(DEFAULT_GROUP);
        ext.getAutoDependencies().convention(true);
        ext.getWeave().convention(true);
        ext.getStrictBarriers().convention(false);
        ext.getExtraJvmArgs().convention(List.of());
        ext.getFrontend().convention(FRONTEND_JAVA);

        project.afterEvaluate(p -> {
            boolean asm = asm(ext);
            if (ext.getAutoDependencies().get()) {
                addDependencies(p, ext, asm);
            }
            if (ext.getStrictBarriers().get() && !asm) {
                p.getTasks().withType(JavaCompile.class).configureEach(compile ->
                        compile.getOptions().getCompilerArgs().add("-Ajavatogpu.strictBarriers=true"));
            }
        });

        project.getTasks().withType(JavaCompile.class).configureEach(compile ->
                compile.doLast("javatogpu-weave", task -> {
                    // фронтенд читает исходные тела до подмены; weaver берёт только что записанный индекс (17.3)
                    if (asm(ext)) {
                        frontend((JavaCompile) task, ext);
                    }
                    if (ext.getWeave().get()) {
                        weave((JavaCompile) task);
                    }
                }));
        project.getTasks().withType(Test.class).configureEach(test ->
                test.doFirst("javatogpu-jvm-args", task -> {
                    test.jvmArgs(jvmArgs(ext));
                }));
        project.getTasks().withType(JavaExec.class).configureEach(exec ->
                exec.doFirst("javatogpu-jvm-args", task -> {
                    exec.jvmArgs(jvmArgs(ext));
                }));
    }

    /** Версия плагина из манифеста его jar; в тестах (классы из каталога) её нет, версию задаёт {@code gpu.version}. */
    private static String defaultVersion() {
        String v = JavaToGpuPlugin.class.getPackage().getImplementationVersion();
        return v == null ? "0.2.0-SNAPSHOT" : v;
    }

    private static List<String> jvmArgs(JavaToGpuExtension ext) {
        List<String> args = new java.util.ArrayList<>();
        args.add(NATIVE_ACCESS);
        args.addAll(ext.getExtraJvmArgs().get());
        return args;
    }

    /** @return выбран фронтенд байткода; неизвестное значение - ошибка конфигурации */
    private static boolean asm(JavaToGpuExtension ext) {
        String f = ext.getFrontend().get();
        if (!f.equals(FRONTEND_JAVA) && !f.equals(FRONTEND_ASM)) {
            throw new GradleException("gpu.frontend = '" + f + "': expected '" + FRONTEND_JAVA + "' or '" + FRONTEND_ASM + "'");
        }
        return f.equals(FRONTEND_ASM);
    }

    private static void addDependencies(Project project, JavaToGpuExtension ext, boolean asm) {
        String prefix = ext.getGroup().get() + ":";
        String suffix = ":" + ext.getVersion().get();
        for (String module : RUNTIME_MODULES) {
            project.getDependencies().add("implementation", prefix + module + suffix);
        }
        if (asm) {
            return; // IR строит шаг сборки: annotation processor не нужен
        }
        for (String module : PROCESSOR_MODULES) {
            project.getDependencies().add("annotationProcessor", prefix + module + suffix);
            project.getDependencies().add("testAnnotationProcessor", prefix + module + suffix);
        }
    }

    /** {@code frontend-asm} над выходом компиляции: ресурсы IR в каталог классов, диагностики в журнал. */
    private static void frontend(JavaCompile compile, JavaToGpuExtension ext) {
        Path classes = compile.getDestinationDirectory().get().getAsFile().toPath();
        List<Path> classpath = new ArrayList<>();
        for (File f : compile.getClasspath().getFiles()) {
            classpath.add(f.toPath());
        }
        AsmFrontend.Report report = AsmFrontend.compileTree(classes, classpath,
                new AsmFrontend.Options(ext.getStrictBarriers().get()));
        for (int i = 0; i < report.diagnostics().size(); i++) {
            if (report.diagnostics().get(i).error()) {
                compile.getLogger().error("[javatogpu] {}", report.messages().get(i));
            } else {
                compile.getLogger().warn("[javatogpu] {}", report.messages().get(i));
            }
        }
        if (!report.ok()) {
            throw new GradleException("[javatogpu] frontend-asm found errors in GPU code, see above");
        }
        compile.getLogger().info("[javatogpu] frontend-asm: {} kernels, {} stale IR files removed", report.kernels(),
                report.removed());
    }

    private static void weave(JavaCompile compile) {
        Path classes = compile.getDestinationDirectory().get().getAsFile().toPath();
        Weaver.Report report = Weaver.weaveTree(classes);
        for (String problem : report.problems()) {
            compile.getLogger().warn("[javatogpu] {}", problem);
        }
        if (report.kernels() > 0) {
            compile.getLogger().info("[javatogpu] kernels replaced: {} in {} classes", report.kernels(), report.classes());
        }
    }
}
