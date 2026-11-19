package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.frontend.common.KernelResources;
import com.sixik.gpu.frontend.java.GpuProcessor;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * javac в памяти для тестов {@code frontend-asm} (17.15, A4): исходники компилируются с {@code -g} (LVT нужна
 * фронтенду байткода, 17.5) и, по желанию, с процессором {@code frontend-java}. Один проход даёт class-файлы (вход
 * {@code frontend-asm}) и ресурсы IR_java - эталон для сравнения.
 */
final class JavacHarness {

    private static final Pattern CODE = Pattern.compile("^(GPU\\d{4}):");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE = Pattern.compile("(?:class|interface|enum|record)\\s+(\\w+)");

    private JavacHarness() {
    }

    /**
     * Итог компиляции.
     *
     * @param success   javac завершился без ошибок
     * @param gpuErrors коды ошибок {@code GPU####} процессора по порядку
     * @param classes   class-файлы: внутреннее имя → байты
     * @param resources прочие записанные файлы: путь → байты
     * @param log       текст диагностик javac (для сообщений тестов)
     */
    record Outcome(boolean success, List<String> gpuErrors, Map<String, byte[]> classes, Map<String, byte[]> resources,
                   String log) {

        /**
         * @param className двоичное имя класса ядра
         * @param method    имя метода ядра
         * @return IR_java ядра из ресурсов процессора
         */
        IrModule module(String className, String method) {
            byte[] idx = resources.get(KernelResources.INDEX);
            if (idx == null) {
                throw new AssertionError("no kernel index written; " + log);
            }
            for (KernelResources.IndexEntry e : KernelResources.parseIndex(new String(idx, StandardCharsets.UTF_8))) {
                if (e.className().equals(className) && e.method().equals(method)) {
                    Result<IrModule> r = IrBinary.read(resources.get(KernelResources.irPath(e.kernelId())));
                    if (!r.isOk()) {
                        throw new AssertionError("corrupted IR of " + method + ": " + r.diagnostics());
                    }
                    return r.value();
                }
            }
            throw new AssertionError("kernel " + className + "." + method + " is not in the index; " + log);
        }
    }

    /**
     * @param processor с процессором {@code frontend-java} (иначе только class-файлы)
     * @param extra     дополнительный classpath (каталоги, jar) либо {@code null}
     * @param sources   тексты исходных файлов
     * @return итог
     */
    static Outcome compile(boolean processor, Path extra, String... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        Capturing manager = new Capturing(compiler.getStandardFileManager(collector, Locale.ROOT, null));
        List<JavaFileObject> units = new ArrayList<>();
        for (String s : sources) {
            units.add(new Source(s));
        }
        String cp = System.getProperty("gpu.test.classpath");
        if (extra != null) {
            cp = extra + java.io.File.pathSeparator + cp;
        }
        List<String> opts = new ArrayList<>(List.of("-g", "-classpath", cp));
        // прогон под ASM загрузчика (-PasmVersion=9.7): class-файлы не новее Java 21, как у библиотеки мода
        String release = System.getProperty("gpu.test.release");
        if (release != null) {
            opts.addAll(List.of("--release", release));
        }
        if (!processor) {
            opts.add("-proc:none");
        }
        JavaCompiler.CompilationTask task = compiler.getTask(null, manager, collector, opts, null, units);
        if (processor) {
            task.setProcessors(List.of(new GpuProcessor()));
        }
        boolean ok = task.call();
        List<String> codes = new ArrayList<>();
        StringBuilder log = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> d : collector.getDiagnostics()) {
            String msg = d.getMessage(Locale.ROOT);
            log.append(d.getKind()).append(' ').append(d.getLineNumber()).append(": ").append(msg).append('\n');
            Matcher m = CODE.matcher(msg);
            if (m.find() && d.getKind() == Diagnostic.Kind.ERROR) {
                codes.add(m.group(1));
            }
        }
        Map<String, byte[]> classes = new HashMap<>();
        Map<String, byte[]> resources = new TreeMap<>();
        manager.files.forEach((path, bytes) -> {
            if (path.endsWith(".class")) {
                classes.put(path.substring(0, path.length() - ".class".length()), bytes);
            } else {
                resources.put(path, bytes);
            }
        });
        return new Outcome(ok, codes, classes, resources, log.toString());
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
                throw new IllegalArgumentException("no type declaration in the source");
            }
            return pkg + tm.group(1) + ".java";
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }

    /** Файловый менеджер, перехватывающий всё, что javac и процессор пишут в CLASS_OUTPUT. */
    private static final class Capturing extends ForwardingJavaFileManager<StandardJavaFileManager> {
        final Map<String, byte[]> files = new TreeMap<>();

        Capturing(StandardJavaFileManager delegate) {
            super(delegate);
        }

        private JavaFileObject memory(String path, JavaFileObject.Kind kind) {
            return new SimpleJavaFileObject(URI.create("mem:///" + path), kind) {
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
                return memory(dir + relativeName, JavaFileObject.Kind.OTHER);
            }
            return super.getFileForOutput(location, packageName, relativeName, sibling);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                                                   FileObject sibling) throws IOException {
            if (location == StandardLocation.CLASS_OUTPUT) {
                return memory(className.replace('.', '/') + kind.extension, kind);
            }
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }

        @Override
        public boolean isSameFile(FileObject a, FileObject b) {
            return a.toUri().equals(b.toUri());
        }
    }
}
