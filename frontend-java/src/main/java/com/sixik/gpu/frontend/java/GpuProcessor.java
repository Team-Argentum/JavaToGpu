package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.annotations.Constant;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Kernel;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor Java-фронтенда: переводит методы с {@code @Kernel} в IR на этапе компиляции
 * (REWORK_ARCHITECTURE.md, 3.A).
 *
 * <p>Для каждого ядра строится один модуль IR (ядро и его device-функции), проверяется валидатором {@code ir} и
 * записывается как ресурс {@code META-INF/gpu/ir/<kernelId>.gir}; в последнем раунде пишется индекс
 * {@code META-INF/gpu/kernels.idx} для weaver'а (см. {@link KernelResources}). Исходные методы остаются нетронутыми.
 * Нарушения подмножества Java выдаются как ошибки компиляции {@code GPU####} с точной позицией (см. {@link Diag}).</p>
 *
 * <p>Процессор работает только с javac: ему нужен {@code Trees} из {@code ProcessingEnvironment}, а обёртки окружения
 * (Gradle, IDE) разворачивает {@link ProcessingEnvironments}. Класс не предназначен для наследования; не-final он
 * только ради тестов, подменяющих сборку ядра и окружение.</p>
 */
public class GpuProcessor extends AbstractProcessor {

    private Trees trees;
    private Reporter rep;
    private ModuleCompiler compiler;
    private boolean disabled;
    private final List<KernelResources.IndexEntry> index = new ArrayList<>();
    private final Set<String> written = new HashSet<>();

    /** Создаёт процессор; инициализацию выполняет javac вызовом {@link #init(ProcessingEnvironment)}. */
    public GpuProcessor() {
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        try {
            ProcessingEnvironment javac = ProcessingEnvironments.unwrap(env);
            trees = Trees.instance(javac);
            rep = new Reporter(trees, env.getMessager());
            compiler = new ModuleCompiler(trees, rep);
        } catch (RuntimeException e) {
            disabled = true;
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, Diag.format("GPU0201", env.getClass().getName()));
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Kernel.class.getName(), DeviceFunction.class.getName(), Constant.class.getName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (disabled) {
            return false;
        }
        if (round.processingOver()) {
            writeIndex();
            return false;
        }
        for (Element e : round.getElementsAnnotatedWith(Constant.class)) {
            if (e.getKind() == ElementKind.FIELD) {
                checkConstantField((VariableElement) e);
            }
        }
        for (Element e : round.getElementsAnnotatedWith(DeviceFunction.class)) {
            if (e instanceof ExecutableElement ee) {
                guarded(ee, () -> compiler.check(ee));
            }
        }
        for (Element e : round.getElementsAnnotatedWith(Kernel.class)) {
            if (e instanceof ExecutableElement ee) {
                guarded(ee, () -> compileAndWrite(ee));
            }
        }
        return false;
    }

    /** @return API деревьев javac (для тестовых подклассов) */
    Trees trees() {
        return trees;
    }

    /** @return компилятор модулей (для тестовых подклассов) */
    ModuleCompiler moduleCompiler() {
        return compiler;
    }

    /**
     * Строит ядро. Вынесено для тестов, которые подставляют сбой, чтобы проверить диагностику внутренней ошибки.
     *
     * @param kernel метод с {@code @Kernel}
     * @return готовое ядро либо {@code null}, если найдены ошибки (диагностики уже выданы)
     */
    ModuleCompiler.Compiled compileKernel(ExecutableElement kernel) {
        return compiler.compile(kernel);
    }

    /** Любой сбой фронтенда превращается в диагностику {@code GPU0902}, а не в падение javac с трассировкой. */
    private void guarded(ExecutableElement e, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            rep.report(e, "GPU0902", e.getSimpleName(), ex);
        }
    }

    private void checkConstantField(VariableElement field) {
        boolean ok = field.getModifiers().contains(Modifier.STATIC) && field.getModifiers().contains(Modifier.FINAL)
                && JavaTypes.isPrimitive(JavaTypes.kindOf(field.asType())) && field.getConstantValue() != null;
        if (!ok) {
            TreePath path = trees.getPath(field);
            if (path != null) {
                rep.report(path, "GPU0015", field.getSimpleName());
            } else {
                rep.report(field, "GPU0015", field.getSimpleName());
            }
        }
    }

    private void compileAndWrite(ExecutableElement kernel) {
        ModuleCompiler.Compiled c = compileKernel(kernel);
        if (c == null) {
            return;
        }
        String path = KernelResources.irPath(c.kernelId());
        try {
            if (written.add(path)) {
                FileObject fo = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", path, kernel);
                try (OutputStream os = fo.openOutputStream()) {
                    os.write(c.bytes());
                }
            }
        } catch (IOException ex) {
            rep.report(kernel, "GPU0902", kernel.getSimpleName(), ex);
            return;
        }
        TypeElement owner = (TypeElement) kernel.getEnclosingElement();
        index.add(new KernelResources.IndexEntry(processingEnv.getElementUtils().getBinaryName(owner).toString(),
                kernel.getSimpleName().toString(), KernelResources.descriptor(kernel), c.kernelId()));
    }

    private void writeIndex() {
        if (index.isEmpty()) {
            return;
        }
        try {
            FileObject fo = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
                    KernelResources.INDEX);
            try (OutputStream os = fo.openOutputStream()) {
                os.write(KernelResources.formatIndex(index).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    Diag.format("GPU0902", KernelResources.INDEX, ex));
        }
    }
}
