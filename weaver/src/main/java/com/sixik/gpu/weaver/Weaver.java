package com.sixik.gpu.weaver;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Подмена тела {@code @Kernel}-метода в байткоде (REWORK_ARCHITECTURE.md, 3.6).
 *
 * <p>Для каждого статического {@code void}-метода с аннотацией {@code @Kernel}, у которого в индексе ядер есть
 * {@code kernelId}:</p>
 * <ol>
 *   <li>исходное тело (инструкции, локали, обработчики исключений, номера строк) переезжает в новый метод
 *       {@code <имя>$jvm} с той же сигнатурой и теми же флагами доступа, но без аннотаций: это эталон для JVM-fallback
 *       и {@code @GPUTest} (то же ядро, исполняемое на JVM);</li>
 *   <li>исходный метод остаётся (имя, сигнатура, аннотации, аннотации параметров), а тело заменяется делегатом:
 *       {@code JavaToGpu.invoke(Owner.class, "имя", "<kernelId>", new Object[]{арг0, арг1, ...})}, скаляры
 *       упаковываются в обёртки.</li>
 * </ol>
 *
 * <p>Функция чистая: байты класса и индекс на входе, байты класса и список замечаний на выходе. Подмена
 * идемпотентна (класс с уже существующим {@code <имя>$jvm} не трогается), поэтому повторный запуск weave-шага
 * безопасен. Таблицы стековых кадров исходного тела сохраняются как есть; делегат ветвлений не содержит, кадры ему не
 * нужны, поэтому загрузка классов при записи не требуется.</p>
 */
public final class Weaver {

    /** Дескриптор аннотации {@code @Kernel}. */
    public static final String KERNEL_ANNOTATION = "Lcom/sixik/gpu/api/annotations/Kernel;";
    /** Суффикс метода с исходным телом. */
    public static final String JVM_SUFFIX = "$jvm";
    /** Внутреннее имя класса хост-фасада, который вызывает делегат. */
    public static final String FACADE = "com/sixik/gpu/runtime/JavaToGpu";
    /** Имя метода фасада, который вызывает делегат. */
    public static final String FACADE_METHOD = "invoke";
    /** Дескриптор метода фасада: владелец, имя метода, kernelId, аргументы. */
    public static final String FACADE_DESCRIPTOR =
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V";

    private Weaver() {
    }

    /**
     * Итог подмены одного класса.
     *
     * @param bytes    байты класса: новые, если подменено хотя бы одно ядро, иначе те же
     * @param woven    число подменённых ядер
     * @param skipped  число ядер, уже подменённых ранее (есть {@code <имя>$jvm})
     * @param problems замечания: ядро нельзя подменить (не static, не void, нет в индексе, abstract/native)
     */
    public record Outcome(byte[] bytes, int woven, int skipped, List<String> problems) {
    }

    /**
     * Итог подмены дерева class-файлов.
     *
     * @param classes  число переписанных class-файлов
     * @param kernels  число подменённых ядер
     * @param skipped  число ядер, подменённых ранее
     * @param problems замечания по всем классам
     */
    public record Report(int classes, int kernels, int skipped, List<String> problems) {
    }

    /**
     * Подменяет ядра в одном классе.
     *
     * @param classBytes байты class-файла
     * @param index      индекс ядер
     * @return итог
     */
    public static Outcome weave(byte[] classBytes, KernelIndex index) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode cls = new ClassNode();
        reader.accept(cls, 0);
        String binaryName = cls.name.replace('/', '.');

        List<MethodNode> kernels = new ArrayList<>();
        for (MethodNode m : cls.methods) {
            if (isKernel(m)) {
                kernels.add(m);
            }
        }
        List<String> problems = new ArrayList<>();
        int woven = 0;
        int skipped = 0;
        for (MethodNode m : kernels) {
            String where = binaryName + "." + m.name + m.desc;
            if (hasMethod(cls, m.name + JVM_SUFFIX, m.desc)) {
                skipped++;
                continue;
            }
            if ((m.access & Opcodes.ACC_STATIC) == 0) {
                problems.add(where + ": the kernel must be static");
                continue;
            }
            if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                problems.add(where + ": the kernel has no body");
                continue;
            }
            if (Type.getReturnType(m.desc).getSort() != Type.VOID) {
                problems.add(where + ": the kernel must return void");
                continue;
            }
            String id = index.kernelId(binaryName, m.name, m.desc);
            if (id == null) {
                problems.add(where + ": not in kernels.idx (the frontend did not build this kernel)");
                continue;
            }
            cls.methods.add(moveBody(m));
            installDelegate(m, cls.name, id);
            woven++;
        }
        if (woven == 0) {
            return new Outcome(classBytes, 0, skipped, List.copyOf(problems));
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return new Outcome(writer.toByteArray(), woven, skipped, List.copyOf(problems));
    }

    /**
     * Подменяет ядра во всех классах, перечисленных в индексе {@code META-INF/gpu/kernels.idx} под {@code root}.
     * Файлы переписываются на месте (во временный файл, затем переименование). Нет индекса - ничего не делается.
     *
     * @param root корень каталога class-файлов
     * @return отчёт
     * @throws UncheckedIOException при ошибке ввода-вывода
     */
    public static Report weaveTree(Path root) {
        Path idxFile = root.resolve(KernelIndex.PATH);
        if (!Files.isRegularFile(idxFile)) {
            return new Report(0, 0, 0, List.of());
        }
        try {
            KernelIndex index = KernelIndex.parse(Files.readString(idxFile, StandardCharsets.UTF_8));
            int classes = 0;
            int kernels = 0;
            int skipped = 0;
            List<String> problems = new ArrayList<>();
            for (String className : index.classes()) {
                Path file = root.resolve(className.replace('.', '/') + ".class");
                if (!Files.isRegularFile(file)) {
                    problems.add(className + ": class file " + file + " not found next to the index");
                    continue;
                }
                Outcome o = weave(Files.readAllBytes(file), index);
                problems.addAll(o.problems());
                skipped += o.skipped();
                if (o.woven() > 0) {
                    Path tmp = file.resolveSibling(file.getFileName() + ".weave");
                    Files.write(tmp, o.bytes());
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                    classes++;
                    kernels += o.woven();
                }
            }
            return new Report(classes, kernels, skipped, List.copyOf(problems));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- внутреннее --------------------------------------------------------------------------

    private static boolean isKernel(MethodNode m) {
        return hasAnnotation(m.invisibleAnnotations) || hasAnnotation(m.visibleAnnotations);
    }

    private static boolean hasAnnotation(List<AnnotationNode> list) {
        if (list != null) {
            for (AnnotationNode a : list) {
                if (KERNEL_ANNOTATION.equals(a.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMethod(ClassNode cls, String name, String desc) {
        for (MethodNode m : cls.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return true;
            }
        }
        return false;
    }

    /** Копия метода под именем {@code <имя>$jvm}: тело и отладочные данные те же, аннотаций нет. */
    private static MethodNode moveBody(MethodNode original) {
        String[] exceptions = original.exceptions == null ? null : original.exceptions.toArray(new String[0]);
        MethodNode jvm = new MethodNode(Opcodes.ASM9, original.access, original.name + JVM_SUFFIX, original.desc,
                original.signature, exceptions);
        original.accept(jvm);
        jvm.visibleAnnotations = null;
        jvm.invisibleAnnotations = null;
        jvm.visibleTypeAnnotations = null;
        jvm.invisibleTypeAnnotations = null;
        jvm.visibleParameterAnnotations = null;
        jvm.invisibleParameterAnnotations = null;
        return jvm;
    }

    /** Заменяет тело исходного метода вызовом хост-фасада. */
    private static void installDelegate(MethodNode m, String owner, String kernelId) {
        Type[] args = Type.getArgumentTypes(m.desc);
        InsnList code = new InsnList();
        code.add(new LdcInsnNode(Type.getObjectType(owner)));
        code.add(new LdcInsnNode(m.name));
        code.add(new LdcInsnNode(kernelId));
        code.add(pushInt(args.length));
        code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        int slot = 0;
        for (int i = 0; i < args.length; i++) {
            code.add(new InsnNode(Opcodes.DUP));
            code.add(pushInt(i));
            code.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD), slot));
            box(code, args[i]);
            code.add(new InsnNode(Opcodes.AASTORE));
            slot += args[i].getSize();
        }
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FACADE, FACADE_METHOD, FACADE_DESCRIPTOR, false));
        code.add(new InsnNode(Opcodes.RETURN));

        m.instructions = code;
        m.tryCatchBlocks = new ArrayList<>();
        m.localVariables = null;
        m.visibleLocalVariableAnnotations = null;
        m.invisibleLocalVariableAnnotations = null;
        m.maxStack = 0;
        m.maxLocals = 0;
    }

    private static org.objectweb.asm.tree.AbstractInsnNode pushInt(int v) {
        if (v >= -1 && v <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + v);
        }
        if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, v);
        }
        return new IntInsnNode(Opcodes.SIPUSH, v);
    }

    private static void box(InsnList code, Type t) {
        String wrapper = switch (t.getSort()) {
            case Type.BOOLEAN -> "java/lang/Boolean";
            case Type.BYTE -> "java/lang/Byte";
            case Type.CHAR -> "java/lang/Character";
            case Type.SHORT -> "java/lang/Short";
            case Type.INT -> "java/lang/Integer";
            case Type.LONG -> "java/lang/Long";
            case Type.FLOAT -> "java/lang/Float";
            case Type.DOUBLE -> "java/lang/Double";
            default -> null;
        };
        if (wrapper != null) {
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, wrapper, "valueOf",
                    "(" + t.getDescriptor() + ")L" + wrapper + ";", false));
        }
    }
}
