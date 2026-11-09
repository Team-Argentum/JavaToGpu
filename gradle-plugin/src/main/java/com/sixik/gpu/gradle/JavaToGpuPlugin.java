package com.sixik.gpu.gradle;

import com.sixik.gpu.weaver.Weaver;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

import java.nio.file.Path;
import java.util.List;

/**
 * Gradle-плагин {@code io.github.deussixik.javatogpu} (REWORK_ARCHITECTURE.md, 3.6): вместо двух ручных блоков в
 * {@code build.gradle} сам делает три вещи.
 *
 * <ol>
 *   <li><b>Зависимости.</b> Подключает библиотеки ({@code api, ir, codegen-opencl, runtime}) и annotation processor
 *       {@code frontend-java} (с его {@code api} и {@code ir}) к {@code main} и {@code test}. Отключается
 *       {@code gpu { autoDependencies = false }}.</li>
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
    static final List<String> RUNTIME_MODULES = List.of("api", "ir", "codegen-opencl", "runtime");
    /** Модули processor path. */
    static final List<String> PROCESSOR_MODULES = List.of("frontend-java", "api", "ir");

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

        project.afterEvaluate(p -> {
            if (ext.getAutoDependencies().get()) {
                addDependencies(p, ext);
            }
            if (ext.getStrictBarriers().get()) {
                p.getTasks().withType(JavaCompile.class).configureEach(compile ->
                        compile.getOptions().getCompilerArgs().add("-Ajavatogpu.strictBarriers=true"));
            }
        });

        project.getTasks().withType(JavaCompile.class).configureEach(compile ->
                compile.doLast("javatogpu-weave", task -> {
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

    private static void addDependencies(Project project, JavaToGpuExtension ext) {
        String prefix = ext.getGroup().get() + ":";
        String suffix = ":" + ext.getVersion().get();
        for (String module : RUNTIME_MODULES) {
            project.getDependencies().add("implementation", prefix + module + suffix);
        }
        for (String module : PROCESSOR_MODULES) {
            project.getDependencies().add("annotationProcessor", prefix + module + suffix);
            project.getDependencies().add("testAnnotationProcessor", prefix + module + suffix);
        }
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
