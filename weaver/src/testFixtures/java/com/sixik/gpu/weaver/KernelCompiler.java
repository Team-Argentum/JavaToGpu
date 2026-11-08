package com.sixik.gpu.weaver;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Компиляция исходников с {@code @Kernel}-методами javac in-process, как это делает сборка пользователя: processor
 * {@code frontend-java} ищется javac через {@code ServiceLoader} на processor path, class-файлы и ресурсы
 * ({@code META-INF/gpu/kernels.idx}, {@code META-INF/gpu/ir/*.gir}) записываются в один каталог.
 *
 * <p>Classpath берётся из системного свойства {@code gpu.test.classpath} (его задаёт Gradle): результат не зависит от
 * того, как воркер тестов собирает {@code java.class.path}. Нужен тестам {@code weaver}, {@code runtime} и
 * {@code gradle-plugin}.</p>
 */
public final class KernelCompiler {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE = Pattern.compile("(?:class|interface|enum|record)\\s+(\\w+)");

    private KernelCompiler() {
    }

    /**
     * Итог компиляции.
     *
     * @param success     компиляция без ошибок
     * @param diagnostics текст диагностик javac и процессора
     * @param classes     каталог с class-файлами и ресурсами
     */
    public record Compiled(boolean success, String diagnostics, Path classes) {
    }

    /**
     * @return classpath тестов (системное свойство {@code gpu.test.classpath})
     * @throws IllegalStateException если свойство не задано
     */
    public static String classpath() {
        String cp = System.getProperty("gpu.test.classpath");
        if (cp == null) {
            throw new IllegalStateException("не задано системное свойство gpu.test.classpath (см. build.gradle модуля)");
        }
        return cp;
    }

    /**
     * Компилирует исходники в каталог.
     *
     * @param out     каталог вывода (должен существовать)
     * @param sources тексты исходных файлов; имя файла берётся из первого объявленного типа
     * @return итог
     */
    public static Compiled compile(Path out, String... sources) {
        return run(out, false, sources);
    }

    /**
     * Компилирует исходники обычным javac без процессора: получаются class-файлы без IR-ресурсов (для тестов
     * ошибочных случаев, которые процессор отверг бы).
     *
     * @param out     каталог вывода
     * @param sources тексты исходных файлов
     * @return итог
     */
    public static Compiled compileWithoutProcessing(Path out, String... sources) {
        return run(out, true, sources);
    }

    private static Compiled run(Path out, boolean noProcessing, String... sources) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        List<JavaFileObject> units = new ArrayList<>();
        for (String s : sources) {
            units.add(new Source(nameOf(s), s));
        }
        try (StandardJavaFileManager fm = javac.getStandardFileManager(diags, null, null)) {
            fm.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(out));
            String cp = classpath();
            List<String> options = noProcessing
                    ? List.of("-classpath", cp, "-proc:none")
                    : List.of("-classpath", cp, "-processorpath", cp, "-Xlint:-processing", "-proc:full");
            boolean ok = javac.getTask(null, fm, diags, options, null, units).call();
            StringBuilder sb = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
                sb.append(d.getKind()).append(' ').append(d.getLineNumber()).append(": ")
                        .append(d.getMessage(null)).append('\n');
            }
            return new Compiled(ok, sb.toString(), out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String nameOf(String source) {
        Matcher p = PACKAGE.matcher(source);
        Matcher t = TYPE.matcher(source);
        if (!t.find()) {
            throw new IllegalArgumentException("в исходнике нет объявления типа");
        }
        return (p.find() ? p.group(1) + "." : "") + t.group(1);
    }

    private static final class Source extends SimpleJavaFileObject {
        private final String text;

        Source(String qualifiedName, String text) {
            super(URI.create("string:///" + qualifiedName.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
