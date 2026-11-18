package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.tools.IrDump;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B2 (FRONTEND_ASM_RUNTIME.md, 18.7): синтез LVT ({@link LvtSynth}, {@link AsmJit.Lvt#SYNTHESIZE}) на корпусе 17.14
 * без отладочной информации и на байткоде {@code ClassWriter}, которого javac не порождает.
 */
class LvtSynthTest {

    private static final ClassSource LOADER = ClassSource.of(LvtSynthTest.class.getClassLoader());
    private static final AsmJit.Options SYNTH = AsmJit.Options.DEFAULT.synthesizeLvt(true);

    /**
     * Ядра, у которых IR без LVT (строки сохранены) отличается от IR по настоящей LVT, и почему:
     * {@code SmallTypeKernels.*} - локали {@code boolean/byte/short/char} без LVT становятся {@code int} (контракт 18.3;
     * поведение то же: усечение уже в байткоде, {@code i2b/i2s/i2c}); {@code ControlKernels.effects} - пункт (г) 17.14-3,
     * как и с LVT ({@code CorpusTest.UNINITIALIZED_DECLS}).
     */
    private static final Set<String> IR_DIFFERS = Set.of("ControlKernels.effects", "SmallTypeKernels.compound",
            "SmallTypeKernels.storage");

    private static String desc(SampleKernels.Kernel k) {
        return MethodType.methodType(k.method().getReturnType(), k.method().getParameterTypes())
                .toMethodDescriptorString();
    }

    private static byte[] bytes(Class<?> c) throws IOException {
        try (InputStream in = c.getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class")) {
            assertTrue(in != null, c.getName());
            return in.readAllBytes();
        }
    }

    /** Классы корпуса деревьями: {@code SKIP_DEBUG} - без LVT и строк, иначе только без LVT. */
    private static Map<String, ClassNode> corpusNodes(boolean skipDebug) throws IOException {
        Map<String, ClassNode> r = new LinkedHashMap<>();
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            String internal = k.owner().getName().replace('.', '/');
            if (r.containsKey(internal)) {
                continue;
            }
            ClassNode cn = new ClassNode();
            new ClassReader(bytes(k.owner())).accept(cn, skipDebug ? ClassReader.SKIP_DEBUG : 0);
            for (MethodNode mn : cn.methods) {
                mn.localVariables = null;
            }
            r.put(internal, cn);
        }
        return r;
    }

    private static AsmJit.Kernel jit(Map<String, ClassNode> nodes, SampleKernels.Kernel k) {
        AsmJit.Unit u = new AsmJit.Unit(LOADER, nodes,
                List.of(AsmJit.Entry.of(k.owner().getName().replace('.', '/'), k.method().getName(), desc(k))));
        AsmJit.Result r = AsmJit.compile(u, SYNTH);
        assertTrue(r.ok(), k.label() + ": " + r.diagnostics());
        return r.kernels().get(0);
    }

    private static AsmFrontend.Kernel reference(SampleKernels.Kernel k) {
        AsmFrontend.Result r = AsmFrontend.compile(LOADER, List.of(k.owner().getName()), AsmFrontend.Options.DEFAULT);
        String d = desc(k);
        return r.kernels().stream()
                .filter(x -> x.entry().method().equals(k.method().getName()) && x.entry().descriptor().equals(d))
                .findFirst().orElseThrow();
    }

    /** Критерий B2 (поведение): корпус без LVT и строк исполняется интерпретатором побитово как IR_java. */
    @Test
    void corpusWithoutDebugInfoBehavesTheSame() throws IOException {
        Map<String, ClassNode> nodes = corpusNodes(true);
        int checked = 0;
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            AsmJit.Kernel mine = jit(nodes, k);
            for (long seed : new long[]{1, 42}) {
                Object[] a = CorpusTest.args(k, seed);
                Object[] j = CorpusTest.args(k, seed);
                NdRange range = CorpusTest.RANGES.getOrDefault(k.label(), NdRange.of1D(16, 16));
                String rj = CorpusTest.outcome(k.module(), range, j);
                assertEquals(rj, CorpusTest.outcome(mine.module(), range, a), k.label() + ": interpreter outcome");
                assertEquals("ok", rj, k.label());
                assertTrue(CorpusTest.same(j, a), k.label() + ": arguments after the run differ (seed " + seed + ")");
            }
            checked++;
        }
        assertEquals(SampleKernels.all().size(), checked);
    }

    /**
     * Критерий B2 (IR): без LVT, но со строками IR совпадает с IR по настоящей LVT, кроме закрытого списка
     * {@link #IR_DIFFERS}.
     */
    @Test
    void corpusWithoutLvtGivesTheSameIr() throws IOException {
        Map<String, ClassNode> nodes = corpusNodes(false);
        Set<String> differs = new TreeSet<>();
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            String want = IrDump.dump(reference(k).module(), false);
            String got = IrDump.dump(jit(nodes, k).module(), false);
            if (!want.equals(got)) {
                differs.add(k.label());
                if (System.getProperty("probe.diff") != null && k.label().equals(System.getProperty("probe.diff"))) {
                    System.out.println("=== java\n" + want + "\n=== synth\n" + got);
                }
            }
        }
        assertEquals(new TreeSet<>(IR_DIFFERS), differs);
    }

    /** Метод со своей LVT в режиме {@code SYNTHESIZE} не меняется: тот же IR, что {@code compile}. */
    @Test
    void methodsWithLvtAreUntouched() {
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            AsmJit.Result r = AsmJit.compile(new AsmJit.Unit(LOADER, null,
                    List.of(AsmJit.Entry.of(k.owner().getName().replace('.', '/'), k.method().getName(), desc(k)))), SYNTH);
            assertTrue(r.ok(), k.label() + ": " + r.diagnostics());
            assertEquals(reference(k).entry().kernelId(), r.kernels().get(0).kernelId(), k.label());
        }
    }

    // ---- байткод ClassWriter -----------------------------------------------------------------------

    /** Класс {@code g/Syn} с ядром {@code static void k(int[] a, int p)} без LVT; {@code SourceFile} - {@code file}. */
    private static ClassNode kernel(String file, int maxLocals, Consumer<MethodVisitor> body) {
        ClassNode cn = new ClassNode();
        cn.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "g/Syn", null, "java/lang/Object", null);
        if (file != null) {
            cn.visitSource(file, null);
        }
        MethodVisitor mv = cn.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "k", "([II)V", null, null);
        mv.visitCode();
        body.accept(mv);
        mv.visitMaxs(6, maxLocals);
        mv.visitEnd();
        cn.visitEnd();
        return cn;
    }

    private static AsmJit.Result jit(ClassNode cn, AsmJit.Options o) {
        return AsmJit.compile(new AsmJit.Unit(LOADER, Map.of("g/Syn", cn), List.of(AsmJit.Entry.of("g/Syn", "k", "([II)V"))), o);
    }

    private static List<String> codes(AsmJit.Result r) {
        return r.diagnostics().stream().map(AsmDiagnostic::code).toList();
    }

    private static int[] run(AsmJit.Result r, int p) {
        assertTrue(r.ok(), r.diagnostics().toString());
        int[] a = new int[2];
        assertEquals("ok", CorpusTest.outcome(r.kernels().get(0).module(), NdRange.of1D(1), new Object[]{a, p}));
        return a;
    }

    /**
     * Слот 2 - переменная {@code int v} на весь метод, а внутри ветки - ещё и {@code float f} (генератор переиспользовал
     * слот): диапазоны пересекаются, {@code f} получает новый слот. {@code v = 5; if (p != 0) { f = 2.5f;
     * a[1] = (int) (f * 2); v = 7; } a[0] = v;}
     */
    private static ClassNode slotReuse() {
        return kernel(null, 3, mv -> {
            Label skip = new Label();
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitJumpInsn(Opcodes.IFEQ, skip);
            mv.visitLdcInsn(2.5f);
            mv.visitVarInsn(Opcodes.FSTORE, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.FLOAD, 2);
            mv.visitInsn(Opcodes.FCONST_2);
            mv.visitInsn(Opcodes.FMUL);
            mv.visitInsn(Opcodes.F2I);
            mv.visitInsn(Opcodes.IASTORE);
            mv.visitIntInsn(Opcodes.BIPUSH, 7);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitLabel(skip);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.IASTORE);
            mv.visitInsn(Opcodes.RETURN);
        });
    }

    @Test
    void reusedSlotWithOverlappingWebsGetsANewSlot() {
        assertEquals(List.of(5, 0), toList(run(jit(slotReuse(), SYNTH), 0)));
        assertEquals(List.of(7, 5), toList(run(jit(slotReuse(), SYNTH), 1)));
    }

    /** Режим {@code REQUIRED} (по умолчанию): метод с локалями без LVT - {@code GPU0301}, как у {@code compile}. */
    @Test
    void requiredModeStillRejectsMissingLvt() {
        AsmJit.Result r = jit(slotReuse(), AsmJit.Options.DEFAULT);
        assertTrue(codes(r).contains("GPU0301"), r.diagnostics().toString());
    }

    /** Ссылочная локаль: тип - по источнику значения ({@code newarray}). {@code int[] t = new int[2]; ...} */
    @Test
    void referenceLocalsTakeTheTypeOfTheirSource() {
        ClassNode cn = kernel(null, 4, mv -> {
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
            mv.visitVarInsn(Opcodes.ASTORE, 2);           // int[] t = new int[2]
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitInsn(Opcodes.IASTORE);                // t[0] = p
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitInsn(Opcodes.IALOAD);
            mv.visitInsn(Opcodes.ICONST_3);
            mv.visitInsn(Opcodes.IMUL);
            mv.visitInsn(Opcodes.IASTORE);                // a[1] = t[0] * 3
            mv.visitInsn(Opcodes.RETURN);
        });
        assertEquals(List.of(0, 12), toList(run(jit(cn, SYNTH), 4)));
    }

    /** {@code GPU0307}: один веб с {@code int} и {@code float} (записи в ветках, чтение после стыка). */
    @Test
    void mixedTypesInOneWebAreRejected() {
        ClassNode cn = kernel(null, 3, mv -> {
            Label other = new Label();
            Label join = new Label();
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitJumpInsn(Opcodes.IFEQ, other);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitVarInsn(Opcodes.ISTORE, 2);
            mv.visitJumpInsn(Opcodes.GOTO, join);
            mv.visitLabel(other);
            mv.visitInsn(Opcodes.FCONST_1);
            mv.visitVarInsn(Opcodes.FSTORE, 2);
            mv.visitLabel(join);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.IASTORE);
            mv.visitInsn(Opcodes.RETURN);
        });
        AsmJit.Result r = jit(cn, SYNTH);
        assertEquals(List.of("GPU0307"), codes(r), r.diagnostics().toString());
        assertTrue(r.diagnostics().get(0).message().contains("int and float"), r.diagnostics().toString());
    }

    /** {@code GPU0307}: чтение слота до записи и ссылка неизвестного типа ({@code aconst_null}). */
    @Test
    void readBeforeWriteAndUnknownReferenceAreRejected() {
        ClassNode early = kernel(null, 3, mv -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitInsn(Opcodes.IASTORE);
            mv.visitInsn(Opcodes.RETURN);
        });
        AsmJit.Result r = jit(early, SYNTH);
        assertEquals(List.of("GPU0307"), codes(r), r.diagnostics().toString());
        assertTrue(r.diagnostics().get(0).message().contains("read before"), r.diagnostics().toString());

        ClassNode unknown = kernel(null, 3, mv -> {
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitVarInsn(Opcodes.ASTORE, 2);
            mv.visitInsn(Opcodes.RETURN);
        });
        AsmJit.Result u = jit(unknown, SYNTH);
        assertTrue(codes(u).contains("GPU0307"), u.diagnostics().toString());
        assertTrue(u.diagnostics().stream().anyMatch(d -> d.message().contains("unknown")), u.diagnostics().toString());
    }

    /** Позиции-узлы: {@code SourceFile = router/overworld}, строка - идентификатор узла генератора. */
    @Test
    void nodeIdsArePositions() {
        ClassNode cn = kernel("router/overworld", 2, mv -> {
            Label l = new Label();
            mv.visitLabel(l);
            mv.visitLineNumber(4711, l);
            mv.visitLdcInsn("text");
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
        });
        AsmJit.Unit unit = new AsmJit.Unit(LOADER, Map.of("g/Syn", cn), List.of(AsmJit.Entry.of("g/Syn", "k", "([II)V")));
        AsmJit.Result r = AsmJit.compile(unit, SYNTH);
        assertFalse(r.ok());
        AsmDiagnostic d = r.diagnostics().stream().filter(x -> x.code().equals("GPU0106")).findFirst().orElseThrow();
        assertEquals("router/overworld:4711", AsmJit.position(d, unit));
    }

    /** Сгенерированное дерево вызывающего не меняется: LVT синтезируется в копии. */
    @Test
    void generatedTreeIsNotModified() {
        ClassNode cn = slotReuse();
        int size = cn.methods.get(0).instructions.size();
        run(jit(cn, SYNTH), 1);
        assertTrue(cn.methods.get(0).localVariables == null || cn.methods.get(0).localVariables.isEmpty());
        assertEquals(size, cn.methods.get(0).instructions.size());
    }

    private static List<Integer> toList(int[] a) {
        List<Integer> r = new ArrayList<>();
        for (int x : a) {
            r.add(x);
        }
        return r;
    }
}
