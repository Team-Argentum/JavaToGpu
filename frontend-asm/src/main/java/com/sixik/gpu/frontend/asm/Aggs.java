package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.Types;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Типы-агрегаты API на байткоде (шаг A5, 17.6, 17.11): векторы {@code com.sixik.gpu.api.types.Float4} и т. п.,
 * {@code @Struct}-классы пользователя, указатели ({@code FloatPtr}, {@code GlobalFloatPtr}, ...) и резидентные массивы
 * ({@code GpuFloatArray}). Распознавание - по внутреннему имени класса и аннотации {@code @Struct} в class-файле; это
 * аналог {@code Aggregates} из {@code frontend-java}, но без деревьев исходника.
 */
final class Aggs {

    /** Пакет типов API во внутренней форме. */
    static final String PKG = "com/sixik/gpu/api/types/";
    static final String STRUCT = "Lcom/sixik/gpu/api/annotations/Struct;";

    private static final Pattern VECTOR = Pattern.compile("(Float|Double|Int|Long)(2|3|4|8|16)");
    private static final Pattern POINTER = Pattern.compile("(Global|Local|Constant)?(Float|Double|Int|Long)Ptr");
    private static final Pattern RESIDENT = Pattern.compile("Gpu(Float|Double|Int|Long)Array");

    /**
     * Вектор.
     *
     * @param scalarKind вид компоненты ({@link Kinds})
     * @param lanes      число компонент
     */
    record Vec(int scalarKind, int lanes) {
    }

    /**
     * Указатель.
     *
     * @param elemKind вид элемента ({@link Kinds})
     * @param space    address space ({@code PRIVATE} у ссылки {@code FloatPtr})
     * @param ref      ссылка на приватную ячейку ({@code FloatPtr}), а не указатель в массив
     */
    record Ptr(int elemKind, int space, boolean ref) {
    }

    /**
     * Форма значения по дескриптору.
     *
     * @param kind     вид ({@link Kinds}; {@code K_AGG}, {@code K_PTR} для объектов API)
     * @param elemKind вид элемента массива или указателя, иначе {@code -1}
     * @param space    address space указателя, иначе {@code -1}
     * @param cls      внутреннее имя агрегата (у массива - элемента), иначе {@code null}
     * @param ref      указатель-ссылка {@code FloatPtr}
     */
    record Shape(int kind, int elemKind, int space, String cls, boolean ref) {
    }

    private final Function<String, ClassNode> classes;

    /** @param classes чтение class-файла по внутреннему имени ({@code null}, если класса нет) */
    Aggs(Function<String, ClassNode> classes) {
        this.classes = classes;
    }

    private static int scalarKind(String name) {
        return switch (name) {
            case "Float" -> Kinds.K_FLOAT;
            case "Double" -> Kinds.K_DOUBLE;
            case "Int" -> Kinds.K_INT;
            default -> Kinds.K_LONG;
        };
    }

    private static String simple(String internal) {
        return internal.substring(internal.lastIndexOf('/') + 1);
    }

    /** @return вектор или {@code null} */
    static Vec vector(String internal) {
        if (internal == null || !internal.startsWith(PKG)) {
            return null;
        }
        Matcher m = VECTOR.matcher(simple(internal));
        return m.matches() ? new Vec(scalarKind(m.group(1)), Integer.parseInt(m.group(2))) : null;
    }

    /** @return указатель или {@code null} */
    static Ptr pointer(String internal) {
        if (internal == null || !internal.startsWith(PKG)) {
            return null;
        }
        Matcher m = POINTER.matcher(simple(internal));
        if (!m.matches()) {
            return null;
        }
        int kind = scalarKind(m.group(2));
        if (m.group(1) == null) {
            return new Ptr(kind, AddressSpace.PRIVATE, true);
        }
        int space = switch (m.group(1)) {
            case "Global" -> AddressSpace.GLOBAL;
            case "Local" -> AddressSpace.LOCAL;
            default -> AddressSpace.CONSTANT;
        };
        return new Ptr(kind, space, false);
    }

    /** @return вид элемента резидентного массива или {@code -1} */
    static int resident(String internal) {
        if (internal == null || !internal.startsWith(PKG)) {
            return -1;
        }
        Matcher m = RESIDENT.matcher(simple(internal));
        return m.matches() ? scalarKind(m.group(1)) : -1;
    }

    /** @return номер компоненты {@code x y z w} ({@code s0..sF} у 8 и 16) либо {@code -1} */
    static int laneIndex(Vec v, String name) {
        for (int i = 0; i < v.lanes(); i++) {
            String lane = v.lanes() <= 4 ? String.valueOf("xyzw".charAt(i)) : "s" + Integer.toHexString(i).toUpperCase();
            if (lane.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** @return класс помечен {@code @Struct} */
    boolean isStruct(String internal) {
        ClassNode cn = internal == null || internal.startsWith("java/") ? null : classes.apply(internal);
        return cn != null && (cn.access & Opcodes.ACC_INTERFACE) == 0 && struct(cn) != null;
    }

    private static AnnotationNode struct(ClassNode cn) {
        AnnotationNode a = Annotations.find(cn.invisibleAnnotations, STRUCT);
        return a != null ? a : Annotations.find(cn.visibleAnnotations, STRUCT);
    }

    /** @return вектор или {@code @Struct} */
    boolean isAggregate(String internal) {
        return vector(internal) != null || isStruct(internal);
    }

    /**
     * @param desc дескриптор типа
     * @return форма значения этого типа
     */
    Shape shape(String desc) {
        int k = Kinds.ofDescriptor(desc);
        if (k == Kinds.K_ARRAY) {
            String elem = desc.substring(1);
            int ek = Kinds.ofDescriptor(elem);
            if (ek == Kinds.K_OTHER) {
                String c = Type.getType(elem).getInternalName();
                return isAggregate(c) ? new Shape(k, Kinds.K_AGG, -1, c, false) : new Shape(k, Kinds.K_OTHER, -1, null, false);
            }
            return new Shape(k, ek, -1, null, false);
        }
        if (k != Kinds.K_OTHER) {
            return new Shape(k, -1, -1, null, false);
        }
        String c = Type.getType(desc).getInternalName();
        if (isAggregate(c)) {
            return new Shape(Kinds.K_AGG, -1, -1, c, false);
        }
        Ptr p = pointer(c);
        if (p != null) {
            return new Shape(Kinds.K_PTR, p.elemKind(), p.space(), c, p.ref());
        }
        int r = resident(c);
        if (r >= 0) {
            return new Shape(Kinds.K_ARRAY, r, AddressSpace.GLOBAL, null, false);
        }
        return new Shape(Kinds.K_OTHER, -1, -1, c, false);
    }

    /**
     * Поля {@code @Struct} в порядке объявления (javac пишет поля class-файла в порядке исходника): public, не static.
     *
     * @param internal внутреннее имя struct
     * @return поля
     */
    List<FieldNode> fields(String internal) {
        List<FieldNode> out = new ArrayList<>();
        ClassNode cn = classes.apply(internal);
        if (cn == null) {
            return out;
        }
        for (FieldNode f : cn.fields) {
            if ((f.access & Opcodes.ACC_PUBLIC) != 0 && (f.access & Opcodes.ACC_STATIC) == 0) {
                out.add(f);
            }
        }
        return out;
    }

    /** @return номер поля {@code name} struct либо {@code -1} */
    int fieldIndex(String internal, String name) {
        List<FieldNode> fs = fields(internal);
        for (int i = 0; i < fs.size(); i++) {
            if (fs.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    // =============================================================================================
    // Проверка объявления @Struct (GPU0191) - по байткоду конструкторов и copy()
    // =============================================================================================

    /**
     * Нарушения объявления {@code @Struct} (аналог {@code Aggregates.problems} в frontend-java): поля, конструктор без
     * аргументов, конструкторы «по всем полям» и {@code copy()} - их тела сверяются с единственной допустимой формой
     * байткода, иначе вызов на GPU означал бы не то же, что на JVM.
     *
     * @param internal внутреннее имя struct
     * @return тексты нарушений (пусто - объявление годно)
     */
    List<String> problems(String internal) {
        List<String> out = new ArrayList<>();
        ClassNode cn = classes.apply(internal);
        if (cn == null) {
            return out;
        }
        String name = simpleName(internal);
        if (cn.signature != null && cn.signature.startsWith("<")) {
            out.add(name + " must not be generic");
        }
        if (cn.innerClasses != null) {
            for (InnerClassNode ic : cn.innerClasses) {
                if (ic.name.equals(internal) && ic.outerName != null && (ic.access & Opcodes.ACC_STATIC) == 0) {
                    out.add(name + " is an inner (non-static) class");
                }
            }
        }
        List<FieldNode> fs = fields(internal);
        if (fs.isEmpty()) {
            out.add(name + " has no public non-static fields");
        }
        for (FieldNode f : fs) {
            if ((f.access & Opcodes.ACC_FINAL) != 0) {
                out.add("field " + f.name + " is declared final: the host writes the result into fields");
            }
            Shape sh = shape(f.desc);
            boolean ok = sh.kind() == Kinds.K_INT || sh.kind() == Kinds.K_LONG || sh.kind() == Kinds.K_FLOAT
                    || sh.kind() == Kinds.K_DOUBLE || sh.kind() == Kinds.K_AGG;
            if (!ok) {
                out.add("field " + f.name + " of type " + Type.getType(f.desc).getClassName()
                        + ": allowed are int, long, float, double, vectors and other @Struct");
            } else if (internal.equals(sh.cls())) {
                out.add("struct contains itself in field " + f.name);
            }
        }
        boolean noArg = false;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("<init>")) {
                if (m.desc.equals("()V")) {
                    noArg |= (m.access & Opcodes.ACC_PUBLIC) != 0;
                } else {
                    String why = fieldwiseProblem(cn, name, m, fs);
                    if (why != null) {
                        out.add(why);
                    }
                }
            } else if (m.name.equals("copy") && m.desc.startsWith("()")) {
                String why = copyProblem(cn, name, m, fs);
                if (why != null) {
                    out.add(why);
                }
            }
        }
        if (!noArg) {
            out.add("a public no-arg constructor is required (the host creates objects when returning a result)");
        }
        return out;
    }

    /** Значимые инструкции метода (без меток, строк и кадров). */
    private static List<AbstractInsnNode> code(MethodNode m) {
        List<AbstractInsnNode> r = new ArrayList<>();
        for (AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
            if (in.getOpcode() >= 0) {
                r.add(in);
            }
        }
        return r;
    }

    private static int loadOpcode(String desc) {
        return switch (desc.charAt(0)) {
            case 'J' -> Opcodes.LLOAD;
            case 'F' -> Opcodes.FLOAD;
            case 'D' -> Opcodes.DLOAD;
            case 'L', '[' -> Opcodes.ALOAD;
            default -> Opcodes.ILOAD;
        };
    }

    private static boolean isVar(AbstractInsnNode in, int op, int slot) {
        return in.getOpcode() == op && ((VarInsnNode) in).var == slot;
    }

    private static boolean isField(AbstractInsnNode in, int op, String owner, FieldNode f) {
        return in.getOpcode() == op && in instanceof FieldInsnNode fi && fi.owner.equals(owner)
                && fi.name.equals(f.name) && fi.desc.equals(f.desc);
    }

    private static boolean isCall(AbstractInsnNode in, int op, String owner, String name, String desc) {
        return in.getOpcode() == op && in instanceof MethodInsnNode mi && mi.owner.equals(owner)
                && mi.name.equals(name) && mi.desc.equals(desc);
    }

    /** Конструктор с аргументами: только {@code this.f_i = p_i} по всем полям по порядку (после {@code super()}). */
    private String fieldwiseProblem(ClassNode cn, String name, MethodNode m, List<FieldNode> fs) {
        Type[] ps = Type.getArgumentTypes(m.desc);
        if (ps.length != fs.size()) {
            return "constructor " + name + " with " + ps.length + " parameters: only a constructor over all " + fs.size()
                    + " fields is allowed";
        }
        for (int i = 0; i < ps.length; i++) {
            if (!ps[i].getDescriptor().equals(fs.get(i).desc)) {
                return "parameter " + (i + 1) + " of constructor " + name + " must have the field type " + fs.get(i).name;
            }
        }
        List<AbstractInsnNode> c = code(m);
        String bad = "constructor " + name + " must only assign parameters to fields in order";
        if (c.size() != 3 + 3 * fs.size() || !isVar(c.get(0), Opcodes.ALOAD, 0)
                || !isCall(c.get(1), Opcodes.INVOKESPECIAL, cn.superName, "<init>", "()V")) {
            return bad;
        }
        int k = 2;
        int slot = 1;
        for (int i = 0; i < fs.size(); i++) {
            if (!isVar(c.get(k++), Opcodes.ALOAD, 0) || !isVar(c.get(k++), loadOpcode(ps[i].getDescriptor()), slot)
                    || !isField(c.get(k++), Opcodes.PUTFIELD, cn.name, fs.get(i))) {
                return "constructor " + name + ": statement " + (i + 1) + " must be 'this." + fs.get(i).name
                        + " = <parameter " + (i + 1) + ">;'";
            }
            slot += ps[i].getSize();
        }
        return c.get(k).getOpcode() == Opcodes.RETURN ? null : bad;
    }

    /** {@code copy()}: только {@code return new S(this.f_i, ...)}; поле-struct копируется глубоко ({@code .copy()}). */
    private String copyProblem(ClassNode cn, String name, MethodNode m, List<FieldNode> fs) {
        String bad = name + ".copy() must be 'return new " + name
                + "(this.field, ...)' over all fields in declaration order (struct fields are copied as this.field.copy())";
        List<AbstractInsnNode> c = code(m);
        if (c.size() < 4 || c.get(0).getOpcode() != Opcodes.NEW || !((TypeInsnNode) c.get(0)).desc.equals(cn.name)
                || c.get(1).getOpcode() != Opcodes.DUP) {
            return bad;
        }
        int k = 2;
        StringBuilder desc = new StringBuilder("(");
        for (FieldNode f : fs) {
            if (k + 1 >= c.size() || !isVar(c.get(k++), Opcodes.ALOAD, 0) || !isField(c.get(k++), Opcodes.GETFIELD, cn.name, f)) {
                return bad;
            }
            Shape sh = shape(f.desc);
            if (sh.kind() == Kinds.K_AGG && isStruct(sh.cls())) {
                if (k >= c.size() || !isCall(c.get(k++), Opcodes.INVOKEVIRTUAL, sh.cls(), "copy", "()L" + sh.cls() + ";")) {
                    return bad;
                }
            }
            desc.append(f.desc);
        }
        desc.append(")V");
        if (c.size() != k + 2 || !isCall(c.get(k), Opcodes.INVOKESPECIAL, cn.name, "<init>", desc.toString())
                || c.get(k + 1).getOpcode() != Opcodes.ARETURN) {
            return bad;
        }
        return null;
    }

    /** @return простое имя класса (у вложенного - из {@code InnerClasses}) */
    String simpleName(String internal) {
        ClassNode cn = classes.apply(internal);
        if (cn != null && cn.innerClasses != null) {
            for (InnerClassNode ic : cn.innerClasses) {
                if (ic.name.equals(internal) && ic.innerName != null) {
                    return ic.innerName;
                }
            }
        }
        return simple(internal);
    }

    private int alignment(String internal) {
        ClassNode cn = classes.apply(internal);
        AnnotationNode a = cn == null ? null : struct(cn);
        Object v = a == null ? null : Annotations.explicitValues(a).get("alignment");
        return v instanceof Integer i ? i : 0;
    }

    /**
     * Типы IR агрегатов одного модуля: вектор - общий тип, struct - номинальный, один на класс в модуле (как кэш
     * {@code Aggregates.irType}).
     */
    final class IrTypes {
        private final IrBuilder b;
        private final Map<String, Integer> cache = new HashMap<>();
        private final Map<Integer, String> classOf = new HashMap<>();

        IrTypes(IrBuilder b) {
            this.b = b;
        }

        /** @return тип IR агрегата {@code internal} (создаётся при первом обращении, поля struct - раньше него) */
        int of(String internal) {
            Integer known = cache.get(internal);
            if (known != null) {
                return known;
            }
            int id;
            Vec v = vector(internal);
            if (v != null) {
                id = b.vec(Kinds.irType(v.scalarKind()), v.lanes());
            } else {
                List<FieldNode> fs = fields(internal);
                int[] ft = new int[fs.size()];
                String[] names = new String[fs.size()];
                for (int i = 0; i < ft.length; i++) {
                    names[i] = fs.get(i).name;
                    Shape s = shape(fs.get(i).desc);
                    ft[i] = s.kind() == Kinds.K_AGG ? of(s.cls()) : Kinds.irType(s.kind());
                }
                id = b.struct(ft, alignment(internal));
                b.nameStruct(id, simpleName(internal), names);
            }
            cache.put(internal, id);
            classOf.put(id, internal);
            return id;
        }

        /** @return тип IR значения формы {@code kind/elemKind/space/cls} */
        int value(int kind, int elemKind, int space, String cls) {
            return switch (kind) {
                case Kinds.K_AGG -> of(cls);
                case Kinds.K_PTR -> b.ptr(space, Kinds.storageType(elemKind));
                case Kinds.K_ARRAY -> b.array(space, elemKind == Kinds.K_AGG ? of(cls) : Kinds.storageType(elemKind));
                case Kinds.K_VOID -> Types.VOID;
                default -> Kinds.irType(kind);
            };
        }
    }
}
