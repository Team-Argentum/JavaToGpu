package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.tools.IrDump;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B1 (FRONTEND_ASM_RUNTIME.md, 18.7): {@link AsmJit} - тот же IR, что {@link AsmFrontend#compile}, для классов
 * из {@link ClassSource} и для сгенерированных деревьев {@link ClassNode}; точки входа без аннотаций и
 * device-функции сгенерированных классов без {@code @DeviceFunction}.
 */
class AsmJitTest {

    private static final ClassSource LOADER = ClassSource.of(AsmJitTest.class.getClassLoader());

    private static String desc(SampleKernels.Kernel k) {
        return MethodType.methodType(k.method().getReturnType(), k.method().getParameterTypes())
                .toMethodDescriptorString();
    }

    private static AsmJit.Entry entry(SampleKernels.Kernel k) {
        return AsmJit.Entry.of(k.owner().getName().replace('.', '/'), k.method().getName(), desc(k));
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        return cn;
    }

    private static byte[] bytes(Class<?> c) throws IOException {
        try (InputStream in = c.getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class")) {
            assertTrue(in != null, c.getName());
            return in.readAllBytes();
        }
    }

    /** IR и kernelId каждого ядра корпуса через {@link AsmFrontend#compile} (эталон). */
    private static Map<String, AsmFrontend.Kernel> reference() {
        Map<String, AsmFrontend.Kernel> r = new LinkedHashMap<>();
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            AsmFrontend.Result res = AsmFrontend.compile(LOADER, List.of(k.owner().getName()), AsmFrontend.Options.DEFAULT);
            String d = desc(k);
            AsmFrontend.Kernel mine = res.kernels().stream()
                    .filter(x -> x.entry().method().equals(k.method().getName()) && x.entry().descriptor().equals(d))
                    .findFirst().orElseThrow(() -> new AssertionError(k.label() + ": " + res.diagnostics()));
            r.put(k.label(), mine);
        }
        return r;
    }

    private static void sameAsReference(AsmJit.Result r, List<SampleKernels.Kernel> corpus) {
        assertTrue(r.ok(), r.diagnostics().toString());
        assertEquals(corpus.size(), r.kernels().size());
        Map<String, AsmFrontend.Kernel> ref = reference();
        for (int i = 0; i < corpus.size(); i++) {
            String label = corpus.get(i).label();
            AsmJit.Kernel k = r.kernels().get(i);
            assertEquals(corpus.get(i).method().getName(), k.entry().name(), label);
            assertEquals(IrDump.dump(ref.get(label).module(), false), IrDump.dump(k.module(), false), label);
            assertEquals(ref.get(label).entry().kernelId(), k.kernelId(), label + ": kernelId");
            assertEquals(IrBinary.kernelId(k.module()), k.kernelId(), label + ": kernelId of the module");
        }
    }

    /** Критерий B1: корпус 17.14 через {@code AsmJit} (классы из загрузчика) даёт тот же IR, что {@code compile}. */
    @Test
    void corpusFromClassSourceGivesTheSameIr() {
        List<SampleKernels.Kernel> corpus = SampleKernels.all();
        List<AsmJit.Entry> entries = corpus.stream().map(AsmJitTest::entry).toList();
        sameAsReference(AsmJit.compile(new AsmJit.Unit(LOADER, null, entries), AsmJit.Options.DEFAULT), corpus);
    }

    /** То же для классов корпуса, переданных деревьями {@link ClassNode} без сериализации. */
    @Test
    void corpusFromGeneratedNodesGivesTheSameIr() throws IOException {
        List<SampleKernels.Kernel> corpus = SampleKernels.all();
        Map<String, ClassNode> generated = new LinkedHashMap<>();
        for (SampleKernels.Kernel k : corpus) {
            String internal = k.owner().getName().replace('.', '/');
            if (!generated.containsKey(internal)) {
                generated.put(internal, node(bytes(k.owner())));
            }
        }
        List<AsmJit.Entry> entries = corpus.stream().map(AsmJitTest::entry).toList();
        sameAsReference(AsmJit.compile(new AsmJit.Unit(LOADER, generated, entries), AsmJit.Options.DEFAULT), corpus);
    }

    private static final String ANNOTATED = """
            package g;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.DeviceFunction;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.annotations.WorkGroup;

            public class Gen {
                @DeviceFunction
                static int inc(int x) {
                    return x + 1;
                }

                @Kernel(compilationProperties = "opt=2")
                @WorkGroup(x = 64)
                public static void k(@Global int[] a) {
                    int i = Gpu.getGlobalId(0);
                    a[i] = inc(a[i]) * 3;
                }
            }
            """;

    /** Тот же класс без аннотаций - как его породил бы генератор байткода. */
    private static final String PLAIN = """
            package g;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;

            public class Gen {
                static int inc(int x) {
                    return x + 1;
                }

                public static void k(@Global int[] a) {
                    int i = Gpu.getGlobalId(0);
                    a[i] = inc(a[i]) * 3;
                }
            }
            """;

    private static Map<String, byte[]> classes(String src) {
        JavacHarness.Outcome o = JavacHarness.compile(false, null, src);
        assertTrue(o.success(), o.log());
        return o.classes();
    }

    private static AsmJit.Entry plainEntry() {
        return new AsmJit.Entry("g/Gen", "k", "([I)V", Map.of("compilationProperties", "opt=2"), Map.of("x", 64));
    }

    /**
     * Точка входа без {@code @Kernel}: атрибуты из {@link AsmJit.Entry}; статический метод сгенерированного класса
     * вызывается без {@code @DeviceFunction}. IR совпадает с аннотированным исходником через {@code compile}.
     */
    @Test
    void unannotatedGeneratedClassMatchesTheAnnotatedOne() {
        AsmFrontend.Result ref = AsmFrontend.compile(ClassSource.of(classes(ANNOTATED)), List.of("g.Gen"),
                AsmFrontend.Options.DEFAULT);
        assertTrue(ref.ok(), ref.diagnostics().toString());
        AsmFrontend.Kernel want = ref.kernels().get(0);

        ClassNode gen = node(classes(PLAIN).get("g/Gen"));
        AsmJit.Result r = AsmJit.compile(new AsmJit.Unit(LOADER, Map.of("g/Gen", gen), List.of(plainEntry())),
                AsmJit.Options.DEFAULT);
        assertTrue(r.ok(), r.diagnostics().toString());
        AsmJit.Kernel got = r.kernels().get(0);
        assertEquals(IrDump.dump(want.module(), false), IrDump.dump(got.module(), false));
        assertEquals(want.entry().kernelId(), got.kernelId());
        int f = got.module().functionCount() - 1;
        assertEquals(64, got.module().fReqWorkGroup(f, 0), "@WorkGroup(x = 64) from the entry");
    }

    /** Класс из {@link ClassSource} (библиотека) по-прежнему требует {@code @DeviceFunction} у вызываемых методов. */
    @Test
    void libraryCalleeStillNeedsDeviceFunction() {
        AsmJit.Result r = AsmJit.compile(new AsmJit.Unit(ClassSource.chain(ClassSource.of(classes(PLAIN)), LOADER),
                null, List.of(plainEntry())), AsmJit.Options.DEFAULT);
        assertFalse(r.ok());
        assertTrue(r.diagnostics().stream().anyMatch(d -> d.code().equals("GPU0113")), r.diagnostics().toString());
        assertTrue(r.kernels().isEmpty());
    }

    /** Ошибки одной точки входа не мешают остальным; результат - в порядке точек входа. */
    @Test
    void badEntriesAreReportedAndSkipped() {
        ClassNode gen = node(classes(PLAIN).get("g/Gen"));
        List<AsmJit.Entry> entries = new ArrayList<>();
        entries.add(AsmJit.Entry.of("g/Gen", "missing", "()V"));
        entries.add(AsmJit.Entry.of("g/Absent", "k", "()V"));
        entries.add(plainEntry());
        AsmJit.Result r = AsmJit.compile(new AsmJit.Unit(LOADER, Map.of("g/Gen", gen), entries), AsmJit.Options.DEFAULT);
        assertEquals(List.of("GPU0311", "GPU0306"), r.diagnostics().stream().map(AsmDiagnostic::code).toList(),
                r.diagnostics().toString());
        assertEquals(1, r.kernels().size());
        assertEquals("k", r.kernels().get(0).entry().name());
    }
}
