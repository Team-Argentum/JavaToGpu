package com.sixik.gpu.frontend.asm;

import org.objectweb.asm.tree.LocalVariableNode;

import java.util.List;

/**
 * Стейтмент фронтенда (дерево до эмиссии в {@code IrBuilder}).
 *
 * <p>Простые стейтменты ({@link #DECL} .. {@link #ARRAY_DECL}) несут выражения {@code a/b/c}; структурные
 * ({@link #IF} .. {@link #BLOCK}) - вложенные списки. {@code group} - номер исходного стейтмента: стейтменты,
 * появившиеся при непустом стеке (временные локали правила эффектов), делят группу со стейтментом, который их
 * потребовал (эмиттер объявляет именованные локали группы раньше её временных, как {@code frontend-java}).</p>
 */
final class St {

    static final int DECL = 0;
    static final int SET = 1;
    static final int STORE = 2;
    static final int EXPR = 3;
    static final int BARRIER = 4;
    static final int RETURN = 5;
    static final int ARRAY_DECL = 6;
    /** {@code if (a) body else alt}; {@code alt == null} - без {@code else}. */
    static final int IF = 7;
    /**
     * {@code loop(a, body, update)}; {@code a == null} - {@code loop(true, ...)}. {@code pre} - стейтменты условия
     * с эффектами (форма {@code loop(true, {pre; if (!a) break; body})}). Цикл {@code for} лежит в
     * {@link #BLOCK} {@code { init; loop }}.
     */
    static final int LOOP = 8;
    /** {@code do body while (a)}. */
    static final int DO_WHILE = 9;
    /** {@code switch (a)}: {@code cases} - тела по порядку, {@code values}/{@code defaults} - метки. */
    static final int SWITCH = 10;
    static final int BREAK = 11;
    static final int CONTINUE = 12;
    static final int BLOCK = 13;
    /**
     * {@code &&}/{@code ||} с эффектами во втором операнде ({@code shortCircuit} в {@code frontend-java}):
     * {@code var = a; if (var) { body; var = b; }} ({@code aux == 1}) либо {@code if (!var)} ({@code aux == 0});
     * {@code body} - стейтменты второго операнда.
     */
    static final int SHORT = 14;

    final int kind;
    Var var;
    final Ex a;
    final Ex b;
    final Ex c;
    final int aux;
    final boolean ordered;
    final int line;
    /** Номер исходного стейтмента (условное выражение-значение получает номер стейтмента-потребителя). */
    int group;
    List<St> body;
    List<St> alt;
    List<St> update;
    List<St> pre;
    List<List<St>> cases;
    int[] values;
    boolean[] defaults;
    /** Число меток у каждого тела {@code switch} (метки группы, кроме последней, получают пустое тело). */
    int[] sizes;
    /**
     * Значения стека на момент появления стейтмента (без массивов): их узлы эмиттер создаёт раньше узлов стейтмента
     * ({@code Emitter.materialize}); {@code null} - стек был пуст.
     */
    List<Ex> ready;
    /** Запись LVT, диапазон которой начинается сразу за этой записью в локаль (объявляющая запись). */
    LocalVariableNode lvt;

    St(int kind, Var var, Ex a, Ex b, Ex c, int aux, boolean ordered, int line, int group) {
        this.kind = kind;
        this.var = var;
        this.a = a;
        this.b = b;
        this.c = c;
        this.aux = aux;
        this.ordered = ordered;
        this.line = line;
        this.group = group;
    }

    static St of(int kind, int line, int group) {
        return new St(kind, null, null, null, null, 0, false, line, group);
    }

    static St withCond(int kind, Ex cond, int line, int group) {
        return new St(kind, null, cond, null, null, 0, false, line, group);
    }

    int kind() {
        return kind;
    }

    Var var() {
        return var;
    }

    Ex a() {
        return a;
    }

    Ex b() {
        return b;
    }

    Ex c() {
        return c;
    }

    int aux() {
        return aux;
    }

    boolean ordered() {
        return ordered;
    }

    int line() {
        return line;
    }

    int group() {
        return group;
    }

    /** @return {@code true} для стейтментов со вложенными списками */
    boolean structured() {
        return kind >= IF && kind != BREAK && kind != CONTINUE;
    }

    /** @return вложенные списки стейтмента (без {@code null}) */
    List<List<St>> children() {
        java.util.ArrayList<List<St>> r = new java.util.ArrayList<>();
        if (pre != null) {
            r.add(pre);
        }
        if (body != null) {
            r.add(body);
        }
        if (alt != null) {
            r.add(alt);
        }
        if (update != null) {
            r.add(update);
        }
        if (cases != null) {
            r.addAll(cases);
        }
        return r;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case DECL -> "decl " + var;
            case SET -> "set " + var;
            case STORE -> "store";
            case EXPR -> "expr";
            case BARRIER -> "barrier";
            case RETURN -> "return";
            case ARRAY_DECL -> "array " + var;
            case IF -> "if " + body + (alt == null ? "" : " else " + alt);
            case LOOP -> "loop " + pre + body + update;
            case DO_WHILE -> "do " + body;
            case SWITCH -> "switch " + cases;
            case BREAK -> "break";
            case CONTINUE -> "continue";
            default -> "block " + body;
        };
    }
}
