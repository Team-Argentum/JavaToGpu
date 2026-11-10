package com.sixik.gpu.ir.optimizer;

import java.util.Arrays;

/**
 * Изменяемый контейнер одной функции в рабочем представлении оптимизатора: сигнатура, таблица локалей и дерево тела.
 *
 * <p>Таблица локалей растёт (CSE и LICM заводят временные локали) и сжимается при сборке: {@link Codec} выбрасывает
 * локали, на которые не осталось ссылок, и перенумеровывает остальные подряд. Тело - неизменяемое дерево; проходы
 * подменяют его целиком через {@link #body}.</p>
 */
final class FnTree {

    /** Вид функции: {@code IrModule.KERNEL} или {@code IrModule.DEVICE}. */
    int kind;
    /** Тип результата. */
    int ret;
    /** Типы параметров. */
    int[] params;
    /** Типы локалей (первые {@link #localCount} элементов значимы). */
    int[] locals = new int[8];
    /** Число локалей. */
    int localCount;
    /** Имена локалей (может быть {@code null}-элементом или короче таблицы). */
    String[] localNames = new String[8];
    /** Корневой блок тела. */
    Tree body;
    /** Обязательный размер work-group по осям. */
    int wgx, wgy, wgz;
    /** Политика inline. */
    int inline;
    /** Уровень оптимизации функции 0..3. */
    int opt;
    /** Имена требуемых возможностей. */
    String[] required = new String[0];
    /** Индекс fallback-функции в исходной нумерации либо {@code -1}. */
    int fallback = -1;
    /** Имя функции либо {@code null}. */
    String name;
    /** Имена параметров либо {@code null}. */
    String[] paramNames;
    /** Жива ли функция (недостижимые device-функции выбрасываются при сборке). */
    boolean alive = true;

    /**
     * Заводит новую локаль.
     *
     * @param type id типа
     * @param hint имя для отладочной информации либо {@code null}
     * @return индекс локали
     */
    int newLocal(int type, String hint) {
        if (localCount == locals.length) {
            locals = Arrays.copyOf(locals, localCount * 2);
        }
        if (localCount >= localNames.length) {
            localNames = Arrays.copyOf(localNames, Math.max(localCount + 1, localNames.length * 2));
        }
        locals[localCount] = type;
        localNames[localCount] = hint;
        return localCount++;
    }

    /**
     * @param l индекс локали
     * @return id типа локали
     */
    int localType(int l) {
        return locals[l];
    }

    /**
     * Глубокая копия контейнера (дерево неизменяемо и разделяется).
     *
     * @return копия
     */
    FnTree copy() {
        FnTree c = new FnTree();
        c.kind = kind;
        c.ret = ret;
        c.params = params.clone();
        c.locals = locals.clone();
        c.localCount = localCount;
        c.localNames = localNames.clone();
        c.body = body;
        c.wgx = wgx;
        c.wgy = wgy;
        c.wgz = wgz;
        c.inline = inline;
        c.opt = opt;
        c.required = required.clone();
        c.fallback = fallback;
        c.name = name;
        c.paramNames = paramNames == null ? null : paramNames.clone();
        c.alive = alive;
        return c;
    }
}
