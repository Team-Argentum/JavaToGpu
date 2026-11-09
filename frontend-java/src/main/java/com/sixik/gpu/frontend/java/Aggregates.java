package com.sixik.gpu.frontend.java;

import com.sixik.gpu.api.annotations.Struct;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.Types;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Векторные типы и {@code @Struct}-классы Java как значения IR (REWORK_ARCHITECTURE.md, 3.2, 5.20).
 *
 * <p><b>Вектор</b> - генерируемый класс {@code api.types.Float4, Int2, Double8, Long16 ...}: неизменяемый, компоненты
 * ({@code x,y,z,w} либо {@code s0..sF}) читаются полями, операции - методами. <b>Struct</b> - класс с
 * {@code @Struct} и открытыми нестатическими полями ({@code int, long, float, double}, вектор или другой struct);
 * порядок объявления полей - порядок полей в IR и в памяти устройства.</p>
 *
 * <p><b>Value-семантика (3.2).</b> На GPU struct - значение, на JVM - объект, и чтобы результат не зависел от
 * исполнителя, фронтенд принимает struct только так, где ссылка не может «просочиться»:</p>
 * <ul>
 *   <li>значение struct получается как {@code new P(все поля)}, {@code x.copy()} или результат device-функции - но не
 *       присваиванием из переменной или элемента массива;</li>
 *   <li>параметры-struct читаются, но не изменяются: поле параметра присваивать нельзя;</li>
 *   <li>локальные массивы объектов запрещены (их элементы на JVM были бы {@code null}, а на GPU - нулями);</li>
 *   <li>конструктор по полям и {@code copy()} должны делать ровно то, что делает IR: проверяется синтаксически по
 *       исходнику класса.</li>
 * </ul>
 *
 * <p>Для хоста у struct обязателен открытый конструктор без аргументов ({@code HostObjects} создаёт объекты при
 * возврате результата), но в GPU-коде {@code new P()} запрещён: поля-объекты были бы {@code null}.</p>
 *
 * <p>Экземпляр хранит типы IR, созданные в одном {@link IrBuilder} (на модуль).</p>
 */
final class Aggregates {

    /** Пакет сгенерированных векторных типов. */
    static final String VECTOR_PACKAGE = "com.sixik.gpu.api.types.";

    private static final Pattern VECTOR = Pattern.compile("(Float|Double|Int|Long)(2|3|4|8|16)");

    /**
     * Описание векторного типа.
     *
     * @param scalar скалярный тип IR компоненты
     * @param lanes  число компонент
     */
    record Vec(int scalar, int lanes) {
    }

    private final Trees trees;
    private final IrBuilder b;
    private final Map<String, Integer> cache = new HashMap<>();
    private final Map<Integer, TypeMirror[]> fieldMirrors = new HashMap<>();
    private final Map<Integer, Vec> vecs = new HashMap<>();

    Aggregates(Trees trees, IrBuilder b) {
        this.trees = trees;
        this.b = b;
    }

    // =============================================================================================
    // распознавание типов Java
    // =============================================================================================

    private static TypeElement element(TypeMirror t) {
        if (t != null && t.getKind() == TypeKind.DECLARED && ((DeclaredType) t).asElement() instanceof TypeElement te) {
            return te;
        }
        return null;
    }

    /**
     * @param t тип Java
     * @return описание вектора либо {@code null}, если это не векторный тип {@code api}
     */
    static Vec vector(TypeMirror t) {
        TypeElement te = element(t);
        if (te == null || !te.getQualifiedName().toString().startsWith(VECTOR_PACKAGE)) {
            return null;
        }
        Matcher m = VECTOR.matcher(te.getSimpleName());
        if (!m.matches()) {
            return null;
        }
        int scalar = switch (m.group(1)) {
            case "Float" -> Types.F32;
            case "Double" -> Types.F64;
            case "Int" -> Types.I32;
            default -> Types.I64;
        };
        return new Vec(scalar, Integer.parseInt(m.group(2)));
    }

    /**
     * Тип выражения. {@code Trees.getTypeMirror} возвращает {@code null} для ещё не атрибутированного узла (javac
     * атрибутирует тело метода по частям, когда к нему обращаются через {@code getElement}), поэтому при {@code null}
     * узел сначала «будится» запросом элемента, а для {@code new} тип берётся из класса конструктора.
     *
     * @param trees API деревьев
     * @param p     путь к выражению
     * @return тип либо {@code null}, если определить не удалось
     */
    static TypeMirror typeOf(Trees trees, com.sun.source.util.TreePath p) {
        TypeMirror t = trees.getTypeMirror(p);
        if (t != null) {
            return t;
        }
        Element e = trees.getElement(p);
        if (p.getLeaf() instanceof NewClassTree && e instanceof ExecutableElement ctor
                && ctor.getEnclosingElement() instanceof TypeElement owner) {
            return owner.asType();
        }
        if (e != null && e.getKind() != ElementKind.CONSTRUCTOR) {
            return e.asType();
        }
        return trees.getTypeMirror(p);
    }

    /** @param t тип Java @return {@code true} для класса с {@code @Struct} */
    static boolean isStruct(TypeMirror t) {
        TypeElement te = element(t);
        return te != null && te.getKind() == ElementKind.CLASS && te.getAnnotation(Struct.class) != null;
    }

    /** @param t тип Java @return {@code true} для вектора или struct */
    static boolean isAggregate(TypeMirror t) {
        return vector(t) != null || isStruct(t);
    }

    private static final Pattern POINTER = Pattern.compile("(Global|Local|Constant)?(Float|Double|Int|Long)Ptr");

    /**
     * Описание pointer-типа (M9).
     *
     * @param elemKind вид Java скалярного pointee ({@code K_FLOAT, K_DOUBLE, K_INT, K_LONG})
     * @param space    пространство адресов указателя: у {@code FloatPtr} и других ссылок - {@code private}
     * @param ref      {@code true} для ссылки на скаляр ({@code FloatPtr ...}: поле {@code value}, создаётся через
     *                 {@code new}); {@code false} для явного указателя ({@code GlobalFloatPtr ...}: {@code of/get/set/plus})
     */
    record Ptr(int elemKind, int space, boolean ref) {
    }

    /**
     * @param t тип Java
     * @return описание pointer-типа {@code api} либо {@code null}, если тип не указатель
     */
    static Ptr pointer(TypeMirror t) {
        TypeElement te = element(t);
        if (te == null || !te.getQualifiedName().toString().startsWith(VECTOR_PACKAGE)) {
            return null;
        }
        Matcher m = POINTER.matcher(te.getSimpleName());
        if (!m.matches()) {
            return null;
        }
        int kind = switch (m.group(2)) {
            case "Float" -> JavaTypes.K_FLOAT;
            case "Double" -> JavaTypes.K_DOUBLE;
            case "Int" -> JavaTypes.K_INT;
            default -> JavaTypes.K_LONG;
        };
        if (m.group(1) == null) {
            return new Ptr(kind, com.sixik.gpu.ir.AddressSpace.PRIVATE, true);
        }
        int space = switch (m.group(1)) {
            case "Global" -> com.sixik.gpu.ir.AddressSpace.GLOBAL;
            case "Local" -> com.sixik.gpu.ir.AddressSpace.LOCAL;
            default -> com.sixik.gpu.ir.AddressSpace.CONSTANT;
        };
        return new Ptr(kind, space, false);
    }

    /**
     * Номер компоненты вектора по имени поля.
     *
     * @param v    вектор
     * @param name имя поля: {@code x,y,z,w} либо {@code s0..sF}
     * @return номер компоненты либо -1
     */
    static int laneIndex(Vec v, String name) {
        for (int i = 0; i < v.lanes(); i++) {
            String lane = v.lanes() <= 4 ? String.valueOf("xyzw".charAt(i)) : "s" + Integer.toHexString(i).toUpperCase();
            if (lane.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Вид Java для скалярного типа компоненты вектора.
     *
     * @param scalar {@code F32, F64, I32, I64}
     * @return {@code K_FLOAT, K_DOUBLE, K_INT, K_LONG}
     */
    static int kindOfScalar(int scalar) {
        return switch (scalar) {
            case Types.F32 -> JavaTypes.K_FLOAT;
            case Types.F64 -> JavaTypes.K_DOUBLE;
            case Types.I32 -> JavaTypes.K_INT;
            default -> JavaTypes.K_LONG;
        };
    }

    // =============================================================================================
    // struct: поля и проверка объявления
    // =============================================================================================

    /**
     * Открытые нестатические поля struct в порядке объявления.
     *
     * @param te класс
     * @return поля
     */
    static List<VariableElement> fields(TypeElement te) {
        List<VariableElement> out = new ArrayList<>();
        for (Element e : te.getEnclosedElements()) {
            if (e.getKind() == ElementKind.FIELD && e.getModifiers().contains(Modifier.PUBLIC)
                    && !e.getModifiers().contains(Modifier.STATIC)) {
                out.add((VariableElement) e);
            }
        }
        return out;
    }

    /**
     * @param t    тип struct
     * @param name имя поля
     * @return номер поля или -1
     */
    static int fieldIndex(TypeMirror t, String name) {
        List<VariableElement> fs = fields(element(t));
        for (int i = 0; i < fs.size(); i++) {
            if (fs.get(i).getSimpleName().contentEquals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean fieldKindOk(TypeMirror ft) {
        int k = JavaTypes.kindOf(ft);
        return k == JavaTypes.K_INT || k == JavaTypes.K_LONG || k == JavaTypes.K_FLOAT || k == JavaTypes.K_DOUBLE
                || k == JavaTypes.K_AGG;
    }

    /**
     * Проверяет объявление struct. Результат кэшируется вызывающим: одно объявление проверяется один раз.
     *
     * @param te класс с {@code @Struct}
     * @return причины, по которым класс нельзя использовать как struct; пусто, если всё допустимо
     */
    List<String> problems(TypeElement te) {
        List<String> out = new ArrayList<>();
        String name = te.getSimpleName().toString();
        if (!te.getTypeParameters().isEmpty()) {
            out.add(name + " must not be generic");
        }
        if (te.getNestingKind().isNested() && !te.getModifiers().contains(Modifier.STATIC)) {
            out.add(name + " is an inner (non-static) class");
        }
        List<VariableElement> fs = fields(te);
        if (fs.isEmpty()) {
            out.add(name + " has no public non-static fields");
        }
        for (VariableElement f : fs) {
            if (f.getModifiers().contains(Modifier.FINAL)) {
                out.add("field " + f.getSimpleName() + " is declared final: the host writes the result into fields");
            }
            if (!fieldKindOk(f.asType())) {
                out.add("field " + f.getSimpleName() + " of type " + f.asType()
                        + ": allowed are int, long, float, double, vectors and other @Struct");
            } else if (isStruct(f.asType()) && element(f.asType()) == te) {
                out.add("struct contains itself in field " + f.getSimpleName());
            }
        }
        boolean noArg = false;
        List<ExecutableElement> ctors = new ArrayList<>();
        for (Element e : te.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                ctors.add((ExecutableElement) e);
            }
        }
        if (ctors.isEmpty()) {
            noArg = true;
        }
        for (ExecutableElement c : ctors) {
            if (c.getParameters().isEmpty() && c.getModifiers().contains(Modifier.PUBLIC)) {
                noArg = true;
            }
        }
        if (!noArg) {
            out.add("a public no-arg constructor is required (the host creates objects when returning a result)");
        }
        for (ExecutableElement c : ctors) {
            if (!c.getParameters().isEmpty()) {
                String why = fieldwiseConstructorProblem(te, c, fs);
                if (why != null) {
                    out.add(why);
                }
            }
        }
        for (Element e : te.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD && e.getSimpleName().contentEquals("copy")
                    && ((ExecutableElement) e).getParameters().isEmpty()) {
                String why = copyProblem(te, (ExecutableElement) e, fs);
                if (why != null) {
                    out.add(why);
                }
            }
        }
        return out;
    }

    /** Конструктор с аргументами обязан присваивать ровно свои параметры полям по порядку объявления. */
    private String fieldwiseConstructorProblem(TypeElement te, ExecutableElement c, List<VariableElement> fs) {
        String name = te.getSimpleName().toString();
        List<? extends VariableElement> ps = c.getParameters();
        if (ps.size() != fs.size()) {
            return "constructor " + name + " with " + ps.size() + " parameters: only a constructor over all " + fs.size() + " fields is allowed";
        }
        for (int i = 0; i < ps.size(); i++) {
            if (!sameType(ps.get(i).asType(), fs.get(i).asType())) {
                return "parameter " + (i + 1) + " of constructor " + name + " must have the field type " + fs.get(i).getSimpleName();
            }
        }
        MethodTree mt = trees.getTree(c);
        if (mt == null || mt.getBody() == null) {
            return "source of the constructor " + name + " is unavailable: it cannot be checked";
        }
        List<? extends StatementTree> body = mt.getBody().getStatements();
        // допускается явный вызов super() первым стейтментом
        int from = !body.isEmpty() && body.get(0).toString().replace(" ", "").startsWith("super(") ? 1 : 0;
        if (body.size() - from != fs.size()) {
            return "constructor " + name + " must only assign parameters to fields in order";
        }
        for (int i = 0; i < fs.size(); i++) {
            if (!isFieldAssign(body.get(from + i), fs.get(i).getSimpleName().toString(), ps.get(i).getSimpleName().toString())) {
                return "constructor " + name + ": statement " + (i + 1) + " must be 'this." + fs.get(i).getSimpleName()
                        + " = " + ps.get(i).getSimpleName() + ";'";
            }
        }
        return null;
    }

    private String copyProblem(TypeElement te, ExecutableElement m, List<VariableElement> fs) {
        String name = te.getSimpleName().toString();
        MethodTree mt = trees.getTree(m);
        if (mt == null || mt.getBody() == null) {
            return "source of " + name + ".copy() is unavailable: it cannot be checked";
        }
        List<? extends StatementTree> body = mt.getBody().getStatements();
        String bad = name + ".copy() must be 'return new " + name + "(this.field, ...)' over all fields in declaration order";
        if (body.size() != 1 || !(body.get(0) instanceof ReturnTree rt) || rt.getExpression() == null) {
            return bad;
        }
        ExpressionTree e = rt.getExpression();
        while (e instanceof ParenthesizedTree pt) {
            e = pt.getExpression();
        }
        if (!(e instanceof NewClassTree nc) || nc.getArguments().size() != fs.size()) {
            return bad;
        }
        for (int i = 0; i < fs.size(); i++) {
            ExpressionTree arg = nc.getArguments().get(i);
            String field = fs.get(i).getSimpleName().toString();
            // вложенный struct копируется глубоко: поле-ссылка делило бы объект между оригиналом и копией
            boolean ok = isStruct(fs.get(i).asType()) ? isOwnFieldCopy(arg, field) : isOwnField(arg, field);
            if (!ok) {
                return bad + " (struct fields are copied as this.field.copy())";
            }
        }
        return null;
    }

    /** {@code this.f.copy()} либо {@code f.copy()}. */
    private static boolean isOwnFieldCopy(ExpressionTree e, String field) {
        return e instanceof com.sun.source.tree.MethodInvocationTree mit && mit.getArguments().isEmpty()
                && mit.getMethodSelect() instanceof MemberSelectTree ms && ms.getIdentifier().contentEquals("copy")
                && isOwnField(ms.getExpression(), field);
    }

    private static boolean sameType(TypeMirror a, TypeMirror b) {
        return a.toString().equals(b.toString());
    }

    private static boolean isFieldAssign(StatementTree s, String field, String param) {
        if (!(s instanceof ExpressionStatementTree es) || !(es.getExpression() instanceof AssignmentTree at)) {
            return false;
        }
        return isOwnField(at.getVariable(), field)
                && at.getExpression() instanceof IdentifierTree id && id.getName().contentEquals(param);
    }

    /** {@code this.f} либо просто {@code f}. */
    private static boolean isOwnField(ExpressionTree e, String field) {
        if (e instanceof IdentifierTree id) {
            return id.getName().contentEquals(field);
        }
        return e instanceof MemberSelectTree ms && ms.getIdentifier().contentEquals(field)
                && ms.getExpression() instanceof IdentifierTree q && q.getName().contentEquals("this");
    }

    // =============================================================================================
    // типы IR
    // =============================================================================================

    /**
     * Тип IR для вектора или struct (создаётся один раз на модуль).
     *
     * @param t тип Java
     * @return id типа в {@link IrBuilder}
     */
    int irType(TypeMirror t) {
        String key = t.toString();
        Integer known = cache.get(key);
        if (known != null) {
            return known;
        }
        int id;
        Vec v = vector(t);
        if (v != null) {
            id = b.vec(v.scalar(), v.lanes());
            vecs.put(id, v);
        } else {
            TypeElement te = element(t);
            List<VariableElement> fs = fields(te);
            int[] ft = new int[fs.size()];
            TypeMirror[] mirrors = new TypeMirror[fs.size()];
            String[] names = new String[fs.size()];
            for (int i = 0; i < ft.length; i++) {
                TypeMirror m = fs.get(i).asType();
                mirrors[i] = m;
                names[i] = fs.get(i).getSimpleName().toString();
                int k = JavaTypes.kindOf(m);
                ft[i] = k == JavaTypes.K_AGG ? irType(m) : JavaTypes.irType(k);
            }
            Struct ann = te.getAnnotation(Struct.class);
            id = b.struct(ft, ann == null ? 0 : ann.alignment());
            b.nameStruct(id, te.getSimpleName().toString(), names);
            fieldMirrors.put(id, mirrors);
        }
        cache.put(key, id);
        return id;
    }

    /** @param irType id вектора @return его описание либо {@code null} */
    Vec vecOf(int irType) {
        return vecs.get(irType);
    }

    /** @param structIr id struct-типа @return типы полей Java либо {@code null} */
    TypeMirror[] fieldTypes(int structIr) {
        return fieldMirrors.get(structIr);
    }
}
