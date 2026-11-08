package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.Validator;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Окружение компиляции (критерий приёмки M3 про обход прокси {@code ProcessingEnvironment}): Gradle и IDE передают
 * процессору обёртку над окружением javac, с которой {@code Trees.instance} не работает; фронтенд обязан её развернуть.
 * Здесь же - диагностики внутренних ошибок.
 */
class ProcessorEnvironmentTest {

    /** Обёртка «как в Gradle»: класс, делегирующий каждый вызов окружению из поля. */
    private static final class ClassWrapper implements ProcessingEnvironment {
        private final ProcessingEnvironment delegate;

        ClassWrapper(ProcessingEnvironment delegate) {
            this.delegate = delegate;
        }

        @Override
        public Map<String, String> getOptions() {
            return delegate.getOptions();
        }

        @Override
        public Messager getMessager() {
            return delegate.getMessager();
        }

        @Override
        public Filer getFiler() {
            return delegate.getFiler();
        }

        @Override
        public Elements getElementUtils() {
            return delegate.getElementUtils();
        }

        @Override
        public javax.lang.model.util.Types getTypeUtils() {
            return delegate.getTypeUtils();
        }

        @Override
        public SourceVersion getSourceVersion() {
            return delegate.getSourceVersion();
        }

        @Override
        public Locale getLocale() {
            return delegate.getLocale();
        }
    }

    /** Обработчик прокси, хранящий окружение в поле с произвольным именем. */
    private static final class Handler implements InvocationHandler {
        private final ProcessingEnvironment hiddenAway;

        Handler(ProcessingEnvironment env) {
            this.hiddenAway = env;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(hiddenAway, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    /** Обёртка, из которой окружение достать нельзя (оно спрятано за лямбдой). */
    private static final class OpaqueWrapper implements ProcessingEnvironment {
        private final Supplier<ProcessingEnvironment> hidden;

        OpaqueWrapper(ProcessingEnvironment env) {
            this.hidden = () -> env;
        }

        @Override
        public Map<String, String> getOptions() {
            return hidden.get().getOptions();
        }

        @Override
        public Messager getMessager() {
            return hidden.get().getMessager();
        }

        @Override
        public Filer getFiler() {
            return hidden.get().getFiler();
        }

        @Override
        public Elements getElementUtils() {
            return hidden.get().getElementUtils();
        }

        @Override
        public javax.lang.model.util.Types getTypeUtils() {
            return hidden.get().getTypeUtils();
        }

        @Override
        public SourceVersion getSourceVersion() {
            return hidden.get().getSourceVersion();
        }

        @Override
        public Locale getLocale() {
            return hidden.get().getLocale();
        }
    }

    /** Процессор, получающий окружение в обёртке, выбранной тестом. */
    private static final class Wrapped extends GpuProcessor {
        private final java.util.function.UnaryOperator<ProcessingEnvironment> wrap;
        boolean directTreesFails;

        Wrapped(java.util.function.UnaryOperator<ProcessingEnvironment> wrap) {
            this.wrap = wrap;
        }

        @Override
        public synchronized void init(ProcessingEnvironment env) {
            ProcessingEnvironment wrapped = wrap.apply(env);
            try {
                Trees.instance(wrapped);
            } catch (IllegalArgumentException e) {
                directTreesFails = true;
            }
            super.init(wrapped);
        }
    }

    private static void assertCompilesThrough(java.util.function.UnaryOperator<ProcessingEnvironment> wrap, boolean expectDirectFailure) {
        Wrapped p = new Wrapped(wrap);
        FrontendHarness.Outcome r = FrontendHarness.compile(p, List.of("-proc:only"), SmokeTest.COMPUTE_SINE);
        assertTrue(r.success(), r.describe());
        assertTrue(r.gpu().isEmpty(), r.describe());
        assertEquals(IrBinary.kernelId(Fixtures.computeSine()), IrBinary.kernelId(r.module()));
        assertEquals(expectDirectFailure, p.directTreesFails,
                "Trees.instance на обёртке должен " + (expectDirectFailure ? "" : "не ") + "падать");
    }

    @Test
    void gradleLikeClassWrapperIsUnwrapped() {
        assertCompilesThrough(ClassWrapper::new, true);
    }

    @Test
    void proxyWithAnyFieldNameIsUnwrapped() {
        assertCompilesThrough(env -> (ProcessingEnvironment) Proxy.newProxyInstance(
                ProcessorEnvironmentTest.class.getClassLoader(), new Class<?>[]{ProcessingEnvironment.class},
                new Handler(env)), true);
    }

    @Test
    void nestedWrappersAreUnwrapped() {
        assertCompilesThrough(env -> new ClassWrapper(new ClassWrapper(env)), true);
    }

    @Test
    void plainEnvironmentIsUsedAsIs() {
        assertCompilesThrough(env -> env, false);
    }

    @Test
    void unwrappableEnvironmentGivesGpu0201WithoutCrash() {
        Wrapped p = new Wrapped(OpaqueWrapper::new);
        FrontendHarness.Outcome r = FrontendHarness.compile(p, List.of("-proc:only"), SmokeTest.COMPUTE_SINE);
        assertFalse(r.success(), r.describe());
        assertEquals(List.of("GPU0201"), r.errorCodes(), r.describe());
        assertTrue(r.resources().isEmpty(), "при сбое окружения ничего не записывается");
    }

    @Test
    void unwrapReturnsOriginalWhenNothingWorks() {
        // чистая функция: без окружения javac развернуть нечего - возвращается то же окружение
        ProcessingEnvironment stub = new ClassWrapperStub();
        assertTrue(ProcessingEnvironments.unwrap(stub) == stub);
    }

    /** Окружение без делегата и без javac. */
    private static final class ClassWrapperStub implements ProcessingEnvironment {
        @Override
        public Map<String, String> getOptions() {
            return Map.of();
        }

        @Override
        public Messager getMessager() {
            return null;
        }

        @Override
        public Filer getFiler() {
            return null;
        }

        @Override
        public Elements getElementUtils() {
            return null;
        }

        @Override
        public javax.lang.model.util.Types getTypeUtils() {
            return null;
        }

        @Override
        public SourceVersion getSourceVersion() {
            return SourceVersion.latest();
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }
    }

    // ---- внутренние ошибки ---------------------------------------------------------------------------

    @Test
    void internalFailureBecomesGpu0902() {
        GpuProcessor failing = new GpuProcessor() {
            @Override
            ModuleCompiler.Compiled compileKernel(ExecutableElement kernel) {
                throw new IllegalStateException("boom");
            }
        };
        FrontendHarness.Outcome r = FrontendHarness.compile(failing, List.of("-proc:only"), SmokeTest.COMPUTE_SINE);
        assertFalse(r.success());
        assertEquals(List.of("GPU0902"), r.errorCodes(), r.describe());
        assertTrue(r.gpu().get(0).message().contains("boom"), r.describe());
        assertTrue(r.gpu().get(0).message().contains("computeSine"), r.describe());
    }

    /** Процессор, который кроме обычной работы проверяет выдачу диагностик по заведомо плохим и подозрительным модулям IR. */
    private static final class IrHook extends GpuProcessor {
        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (!round.processingOver()) {
                for (Element e : round.getElementsAnnotatedWith(Kernel.class)) {
                    TreePath path = trees().getPath(e);
                    moduleCompiler().validate(invalidModule(), path, "bad");
                    moduleCompiler().validate(warningModule(), path, "suspicious");
                }
            }
            return super.process(annotations, round);
        }
    }

    private static IrModule invalidModule() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, com.sixik.gpu.ir.Types.VOID);
        int l = b.declareLocal(com.sixik.gpu.ir.Types.I32);
        int decl = b.node(Op.LOCAL_DECL, com.sixik.gpu.ir.Types.VOID, l, -1, 0); // нет инициализатора: IR-V02
        b.endFunction(b.block(decl));
        return b.build();
    }

    private static IrModule warningModule() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, com.sixik.gpu.ir.Types.VOID);
        int gid = b.intrinsic(Builtins.idOf("getGlobalId"), b.constI32(0));
        int cond = b.binary(Op.EQ, gid, b.constI32(0));
        int branch = b.ifStmt(cond, b.block(b.barrier(1)), -1); // барьер под расходящимся условием: IR-B01 (предупреждение)
        b.endFunction(b.block(branch));
        return b.build();
    }

    @Test
    void irValidatorFindingsBecomeGpu0901AndGpu0130() {
        assertTrue(Validator.validate(invalidModule()).hasErrors());
        assertFalse(Validator.validate(warningModule()).hasErrors());
        assertFalse(Validator.validate(warningModule()).diagnostics().isEmpty(), "предупреждение IR-B01 ожидается");

        FrontendHarness.Outcome r = FrontendHarness.compile(new IrHook(), List.of("-proc:only"), SmokeTest.COMPUTE_SINE);
        assertFalse(r.success(), r.describe());
        List<FrontendHarness.Diag> gpu = r.gpu();
        assertTrue(gpu.stream().anyMatch(d -> d.code().equals("GPU0901") && d.kind() == Diagnostic.Kind.ERROR
                && d.message().contains("IR-V02") && d.message().contains("bad")), r.describe());
        assertTrue(gpu.stream().anyMatch(d -> d.code().equals("GPU0130") && d.kind() == Diagnostic.Kind.WARNING
                && d.message().contains("расходящимся") && d.message().contains("suspicious")), r.describe());
    }
}
