package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Компиляция образцов GPU-кода javac in-process с процессором {@link GpuProcessor}: возвращает диагностики
 * (код {@code GPU####}, строка, столбец) и записанные процессором ресурсы.
 *
 * <p>Classpath образцов берётся из системного свойства {@code gpu.test.classpath} (его задаёт Gradle): так результат не
 * зависит от того, как воркер тестов собирает {@code java.class.path}.</p>
 */
final class FrontendHarness {

    private static final Pattern CODE = Pattern.compile("^(GPU\\d{4}):");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE = Pattern.compile("(?:class|interface|enum|record)\\s+(\\w+)");

    private FrontendHarness() {
    }

    /**
     * Диагностика компилятора.
     *
     * @param kind    ERROR, WARNING или NOTE
     * @param code    код {@code GPU####} либо пустая строка для диагностик javac
     * @param line    строка (1-based) либо -1
     * @param column  столбец (1-based) либо -1
     * @param message полный текст
     */
    record Diag(Diagnostic.Kind kind, String code, long line, long column, String message) {
        boolean isGpu() {
            return !code.isEmpty();
        }
    }

    /**
     * Итог компиляции.
     *
     * @param success     компиляция завершилась без ошибок
     * @param diagnostics все диагностики
     * @param resources   записанные ресурсы (путь -> байты)
     */
    record Outcome(boolean success, List<Diag> diagnostics, Map<String, byte[]> resources) {

        /** @return диагностики {@code GPU####} (ошибки и предупреждения) */
        List<Diag> gpu() {
            return diagnostics.stream().filter(Diag::isGpu).toList();
        }

        /** @return коды ошибок {@code GPU####} по порядку выдачи */
        List<String> errorCodes() {
            return diagnostics.stream().filter(d -> d.isGpu() && d.kind() == Diagnostic.Kind.ERROR)
                    .map(Diag::code).toList();
        }

        /** @return все ядра, записанные как IR-ресурсы, в порядке путей */
        List<IrModule> modules() {
            List<IrModule> r = new ArrayList<>();
            for (var e : resources.entrySet()) {
                if (e.getKey().startsWith(KernelResources.IR_DIR) && e.getKey().endsWith(KernelResources.IR_EXT)) {
                    Result<IrModule> read = IrBinary.read(e.getValue());
                    if (!read.isOk()) {
                        throw new AssertionError("повреждён записанный IR " + e.getKey() + ": " + read.diagnostics());
                    }
                    r.add(read.value());
                }
            }
            return r;
        }

        /** @return единственный записанный модуль */
        IrModule module() {
            List<IrModule> ms = modules();
            if (ms.size() != 1) {
                throw new AssertionError("ожидался один модуль, записано " + ms.size() + "; диагностики: " + diagnostics);
            }
            return ms.get(0);
        }

        /** @return текст диагностик для сообщений об ошибках тестов */
        String describe() {
            StringBuilder sb = new StringBuilder();
            for (Diag d : diagnostics) {
                sb.append(d.kind()).append(' ').append(d.line()).append(':').append(d.column()).append(' ')
                        .append(d.message()).append('\n');
            }
            return sb.toString();
        }
    }

    /**
     * Компилирует исходники процессором по умолчанию без генерации class-файлов ({@code -proc:only}).
     *
     * @param sources тексты исходных файлов (имя файла берётся из первого объявленного типа)
     * @return итог
     */
    static Outcome compile(String... sources) {
        return compile(new GpuProcessor(), List.of("-proc:only"), sources);
    }

    /**
     * Компилирует исходники, оставляя javac самому найти процессор на processor path (через {@code ServiceLoader}).
     *
     * @param sources тексты исходных файлов
     * @return итог
     */
    static Outcome compileDiscovering(String... sources) {
        return compile(null, List.of("-proc:only"), sources);
    }

    /**
     * Компилирует исходники заданным процессором и параметрами javac.
     *
     * @param processor процессор либо {@code null} - тогда его ищет javac
     * @param options   дополнительные параметры javac
     * @param sources   тексты исходных файлов
     * @return итог
     */
    static Outcome compile(Processor processor, List<String> options, String... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        Capturing manager = new Capturing(compiler.getStandardFileManager(collector, Locale.ROOT, null));
        List<JavaFileObject> units = new ArrayList<>();
        for (String s : sources) {
            units.add(new Source(s));
        }
        List<String> opts = new ArrayList<>(options);
        opts.add("-classpath");
        opts.add(System.getProperty("gpu.test.classpath"));
        if (processor == null) {
            // как у настоящего javac: процессор ищется ServiceLoader'ом по META-INF/services на processor path
            opts.add("-processorpath");
            opts.add(System.getProperty("gpu.test.classpath"));
        }
        JavaCompiler.CompilationTask task = compiler.getTask(null, manager, collector, opts, null, units);
        if (processor != null) {
            task.setProcessors(List.of(processor));
        }
        boolean ok = task.call();
        List<Diag> diags = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : collector.getDiagnostics()) {
            String msg = d.getMessage(Locale.ROOT);
            Matcher m = CODE.matcher(msg);
            diags.add(new Diag(d.getKind(), m.find() ? m.group(1) : "", d.getLineNumber(), d.getColumnNumber(), msg));
        }
        return new Outcome(ok, diags, new TreeMap<>(manager.files));
    }

    /** Исходный файл из строки; имя файла выводится из пакета и первого объявленного типа. */
    private static final class Source extends SimpleJavaFileObject {
        private final String text;

        Source(String text) {
            super(URI.create("string:///" + fileName(text)), Kind.SOURCE);
            this.text = text;
        }

        private static String fileName(String text) {
            Matcher pm = PACKAGE.matcher(text);
            String pkg = pm.find() ? pm.group(1).replace('.', '/') + "/" : "";
            Matcher tm = TYPE.matcher(text);
            if (!tm.find()) {
                throw new IllegalArgumentException("в исходнике нет объявления типа");
            }
            return pkg + tm.group(1) + ".java";
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }

    /** Файловый менеджер, перехватывающий всё, что процессор и javac пишут в CLASS_OUTPUT. */
    private static final class Capturing extends ForwardingJavaFileManager<StandardJavaFileManager> {
        final Map<String, byte[]> files = new TreeMap<>();

        Capturing(StandardJavaFileManager delegate) {
            super(delegate);
        }

        private JavaFileObject memory(String path) {
            return new SimpleJavaFileObject(URI.create("mem:///" + path), JavaFileObject.Kind.OTHER) {
                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream() {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            files.put(path, toByteArray());
                        }
                    };
                }
            };
        }

        @Override
        public FileObject getFileForOutput(Location location, String packageName, String relativeName,
                                           FileObject sibling) throws IOException {
            if (location == StandardLocation.CLASS_OUTPUT) {
                String dir = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
                return memory(dir + relativeName);
            }
            return super.getFileForOutput(location, packageName, relativeName, sibling);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                                                   FileObject sibling) throws IOException {
            if (location == StandardLocation.CLASS_OUTPUT) {
                return memory(className.replace('.', '/') + kind.extension);
            }
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }

        @Override
        public boolean isSameFile(FileObject a, FileObject b) {
            return a.toUri().equals(b.toUri());
        }

        @Override
        public boolean hasLocation(JavaFileManager.Location location) {
            return super.hasLocation(location);
        }
    }
}
