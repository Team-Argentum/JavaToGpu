package com.sixik.gpu.tools;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.DebugInfo;
import com.sixik.gpu.ir.Features;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Operands;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;

/**
 * Человекочитаемый дамп {@link IrModule} для людей, отладки и golden-тестов (REWORK_ARCHITECTURE.md, 5.9, 5.11).
 *
 * <p>Дамп строится из тех же таблиц и <b>никогда не парсится обратно</b>: это только вывод. Он детерминирован:
 * один и тот же модуль даёт один и тот же текст. Режим без отладочных данных ({@code includeDebug = false})
 * не использует имена и позиции, поэтому его вывод стабилен относительно секции {@code debug} и годится как
 * golden; режим с отладкой добавляет имена функций, параметров, локалей, struct и позиции узлов.</p>
 *
 * <p>Формат узла: {@code n<id> <op> <тип> <операнды и непосредственные значения>}. Операнды-узлы печатаются
 * как {@code n<id>}, непосредственные значения - числами, локали - {@code l<i>}, параметры - {@code p<i>},
 * функции - {@code f<i>}.</p>
 */
public final class IrDump {

    private IrDump() {
    }

    /**
     * Дамп без отладочной информации (стабильный, для golden).
     *
     * @param m модуль
     * @return текст
     */
    public static String dump(IrModule m) {
        return dump(m, false);
    }

    /**
     * Дамп модуля.
     *
     * @param m            модуль
     * @param includeDebug {@code true} - добавить имена и позиции из секции debug
     * @return текст; строки разделены {@code '\n'}
     */
    public static String dump(IrModule m, boolean includeDebug) {
        StringBuilder sb = new StringBuilder(1024);
        DebugInfo d = includeDebug ? m.debug() : DebugInfo.EMPTY;
        Validator.Report report = Validator.validate(m);

        sb.append("module format=").append(IrBinary.FORMAT).append(" builtins=").append(m.builtinsVersion())
                .append(" nodes=").append(m.nodeCount()).append(" functions=").append(m.functionCount()).append('\n');

        for (int t = Types.FIRST_DYNAMIC; t < m.typeCount(); t++) {
            sb.append("type t").append(t).append(" = ").append(typeDescription(m, t, d)).append('\n');
        }

        for (int f = 0; f < m.functionCount(); f++) {
            dumpFunction(sb, m, d, report, f);
        }

        sb.append("nodes:\n");
        for (int n = 0; n < m.nodeCount(); n++) {
            sb.append("  n").append(n).append(' ').append(Op.nameOf(m.op(n))).append(' ')
                    .append(typeName(m, m.type(n))).append(' ').append(operandsText(m, n));
            if (includeDebug && d.line(n) > 0) {
                sb.append("  @").append(d.line(n)).append(':').append(d.col(n));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void dumpFunction(StringBuilder sb, IrModule m, DebugInfo d, Validator.Report report, int f) {
        sb.append("func f").append(f);
        String name = d.functionName(f);
        if (name != null) {
            sb.append(" \"").append(name).append('"');
        }
        sb.append(m.fKind(f) == IrModule.KERNEL ? " kernel" : " device");
        sb.append(" ret=").append(typeName(m, m.fRet(f)));
        if (m.fReqWorkGroup(f, 0) != 0) {
            sb.append(" reqd_wg=(").append(m.fReqWorkGroup(f, 0)).append(',').append(m.fReqWorkGroup(f, 1))
                    .append(',').append(m.fReqWorkGroup(f, 2)).append(')');
        }
        if (m.fInline(f) != IrModule.INLINE_DEFAULT) {
            sb.append(" inline=").append(m.fInline(f) == IrModule.INLINE_ALWAYS ? "always" : "never");
        }
        if (m.fOpt(f) != 0) {
            sb.append(" opt=").append(m.fOpt(f));
        }
        if (m.fRequiredCount(f) > 0) {
            sb.append(" required=[");
            for (int i = 0; i < m.fRequiredCount(f); i++) {
                sb.append(i == 0 ? "" : ",").append(m.string(m.fRequiredName(f, i)));
            }
            sb.append(']');
        }
        if (m.fFallback(f) >= 0) {
            sb.append(" fallback=f").append(m.fFallback(f));
        }
        Validator.FunctionInfo info = report.functions()[f];
        sb.append(" features=").append(Features.describe(info.requiredFeatures()));
        sb.append(" group_sync=").append(info.usesGroupSync() ? "yes" : "no");
        sb.append('\n');
        for (int i = 0; i < m.fParamCount(f); i++) {
            sb.append("  param p").append(i);
            String pn = d.paramName(f, i);
            if (pn != null) {
                sb.append(" \"").append(pn).append('"');
            }
            sb.append(" : ").append(typeName(m, m.fParamType(f, i)))
                    .append("  access=").append(accessText(info.paramAccess(), i)).append('\n');
        }
        for (int i = 0; i < m.fLocalCount(f); i++) {
            sb.append("  local l").append(i);
            String ln = d.localName(f, i);
            if (ln != null) {
                sb.append(" \"").append(ln).append('"');
            }
            sb.append(" : ").append(typeName(m, m.fLocalType(f, i))).append('\n');
        }
        sb.append("  body = n").append(m.fBody(f)).append('\n');
    }

    private static String accessText(int[] access, int i) {
        int a = i < access.length ? access[i] : 0;
        return switch (a) {
            case Validator.ACCESS_READ -> "READ";
            case Validator.ACCESS_WRITE -> "WRITE";
            case Validator.ACCESS_READ | Validator.ACCESS_WRITE -> "READ_WRITE";
            default -> "NONE";
        };
    }

    /**
     * Короткое имя типа: {@code i32}, {@code f32x4}, {@code ptr<global,f32>}, {@code array<global,f32>},
     * {@code struct#18}.
     *
     * @param m модуль
     * @param t id типа
     * @return имя типа
     */
    public static String typeName(IrModule m, int t) {
        if (t < 0 || t >= m.typeCount()) {
            return "t?" + t;
        }
        if (t < Types.FIRST_DYNAMIC) {
            return switch (t) {
                case Types.VOID -> "void";
                case Types.BOOL -> "bool";
                case Types.I8 -> "i8";
                case Types.U8 -> "u8";
                case Types.I16 -> "i16";
                case Types.U16 -> "u16";
                case Types.I32 -> "i32";
                case Types.U32 -> "u32";
                case Types.I64 -> "i64";
                case Types.U64 -> "u64";
                case Types.F32 -> "f32";
                case Types.F64 -> "f64";
                default -> "t" + t;
            };
        }
        return switch (m.typeKind(t)) {
            case Types.KIND_VEC -> typeName(m, m.typeA(t)) + "x" + m.typeB(t);
            case Types.KIND_PTR -> "ptr<" + AddressSpace.name(m.typeA(t)) + "," + typeName(m, m.typeB(t)) + ">";
            case Types.KIND_ARRAY -> "array<" + AddressSpace.name(m.typeA(t)) + "," + typeName(m, m.typeB(t)) + ">";
            case Types.KIND_STRUCT -> "struct#" + t;
            default -> "t" + t;
        };
    }

    private static String typeDescription(IrModule m, int t, DebugInfo d) {
        if (m.typeKind(t) != Types.KIND_STRUCT) {
            return typeName(m, t);
        }
        StringBuilder sb = new StringBuilder("struct");
        String name = d.structName(t);
        if (name != null) {
            sb.append(" \"").append(name).append('"');
        }
        if (m.typeD(t) != 0) {
            sb.append(" align=").append(m.typeD(t));
        }
        sb.append(" {");
        for (int i = 0; i < m.structFieldCount(t); i++) {
            sb.append(i == 0 ? " " : ", ");
            String fn = d.fieldName(t, i);
            if (fn != null) {
                sb.append(fn).append(':');
            }
            sb.append(typeName(m, m.structFieldType(t, i)));
        }
        return sb.append(" }").toString();
    }

    private static String list(IrModule m, int off, int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "" : ", ").append('n').append(m.list(off + i));
        }
        return sb.append(']').toString();
    }

    private static String intList(IrModule m, int off, int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "" : ", ").append(m.list(off + i));
        }
        return sb.append(']').toString();
    }

    private static String operandsText(IrModule m, int n) {
        int a = m.a(n);
        int b = m.b(n);
        int c = m.c(n);
        return switch (m.op(n)) {
            case Op.CONST_INT -> intConstant(m, n);
            case Op.CONST_FLOAT -> floatConstant(m, n);
            case Op.CONST_BOOL -> a != 0 ? "true" : "false";
            case Op.SPEC_CONST -> "#" + a;
            case Op.PARAM -> "p" + a;
            case Op.NEG, Op.NOT, Op.CONVERT, Op.BITCAST, Op.LOAD, Op.ARRAY_LEN, Op.SPLAT, Op.EXPR_STMT -> "n" + a;
            case Op.SELECT -> "n" + a + " ? n" + b + " : n" + c;
            case Op.LOCAL_DECL, Op.LOCAL_SET -> "l" + a + " = n" + b;
            case Op.LOCAL_GET, Op.LOCAL_ADDR -> "l" + a;
            case Op.ELEM_PTR -> "n" + a + "[n" + b + "]";
            case Op.FIELD_PTR, Op.FIELD_GET -> "n" + a + "." + b;
            case Op.STORE -> "n" + a + " = n" + b;
            case Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL -> "l" + a + " len n" + b;
            case Op.EXTRACT -> "n" + a + "[" + b + "]";
            case Op.INSERT -> "n" + a + "[" + b + "] = n" + c;
            case Op.SHUFFLE -> "n" + a + " " + intList(m, b, c);
            case Op.BUILD, Op.STRUCT_BUILD -> list(m, a, b);
            case Op.FIELD_SET -> "n" + a + "." + b + " = n" + c;
            case Op.CALL -> "f" + a + list(m, b, c);
            case Op.INTRINSIC -> (Builtins.isValid(a) ? Builtins.name(a) : "#" + a) + list(m, b, c);
            case Op.BARRIER -> "flags=" + a;
            case Op.BLOCK -> list(m, a, b);
            case Op.IF -> "n" + a + " then n" + b + (c >= 0 ? " else n" + c : "");
            case Op.LOOP -> "cond n" + a + " body n" + b + (c >= 0 ? " update n" + c : "");
            case Op.DO_WHILE -> "body n" + a + " cond n" + b;
            case Op.SWITCH -> switchText(m, n);
            case Op.RETURN -> a >= 0 ? "n" + a : "";
            case Op.BREAK, Op.CONTINUE -> "";
            default -> binaryText(m, n);
        };
    }

    private static String binaryText(IrModule m, int n) {
        int cnt = Operands.count(m, n);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < cnt; k++) {
            sb.append(k == 0 ? "" : " ").append('n').append(Operands.get(m, n, k));
        }
        return sb.toString();
    }

    private static String switchText(IrModule m, int n) {
        StringBuilder sb = new StringBuilder("key n").append(m.a(n));
        for (int i = 0; i < m.c(n); i++) {
            int base = m.b(n) + 3 * i;
            sb.append(" | ").append(m.list(base) == 1 ? "default" : "case " + m.list(base + 1))
                    .append(" -> n").append(m.list(base + 2));
        }
        return sb.toString();
    }

    private static String intConstant(IrModule m, int n) {
        int t = m.type(n);
        long value = Types.bits(t) == 64 ? m.constant(m.a(n)) : m.a(n);
        if (t == Types.U32) {
            value &= 0xFFFFFFFFL;
        }
        return t == Types.U64 ? Long.toUnsignedString(value) : Long.toString(value);
    }

    private static String floatConstant(IrModule m, int n) {
        if (m.type(n) == Types.F32) {
            return Float.toString(Float.intBitsToFloat(m.a(n))) + "f";
        }
        return Double.toString(Double.longBitsToDouble(m.constant(m.a(n))));
    }
}
