package com.sixik.gpu.frontend.java;

import com.sun.source.util.Trees;

import javax.annotation.processing.ProcessingEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * Обход обёрток {@link ProcessingEnvironment}.
 *
 * <p>Javac Tree API ({@code Trees.instance(env)}) принимает только настоящее окружение javac. Gradle (и IDE) подают
 * процессору обёртку над ним: либо класс с полем-делегатом, либо {@link Proxy}. JDK сам разворачивает лишь один вид
 * прокси (IntelliJ), поэтому здесь реализован общий поиск: у обёртки (или обработчика прокси) перебираются поля,
 * и значение типа {@code ProcessingEnvironment} становится следующим кандидатом; поиск повторяется на небольшую
 * глубину. Рефлексия применяется только к классам обёрток (не к внутренним классам JDK): поле, к которому нет доступа,
 * пропускается.</p>
 */
final class ProcessingEnvironments {

    private static final int MAX_DEPTH = 8;

    private ProcessingEnvironments() {
    }

    /**
     * Находит окружение, с которым работает {@code Trees.instance}.
     *
     * @param env окружение, полученное процессором (возможно, обёрнутое)
     * @return окружение, для которого {@code Trees.instance} успешно; если такого найти не удалось - исходное
     * {@code env} (вызывающий получит от {@code Trees.instance} понятное исключение и выдаст {@code GPU0201})
     */
    static ProcessingEnvironment unwrap(ProcessingEnvironment env) {
        ProcessingEnvironment cur = env;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            if (usable(cur)) {
                return cur;
            }
            ProcessingEnvironment next = delegateOf(cur);
            if (next == null || next == cur) {
                return env;
            }
            cur = next;
        }
        return env;
    }

    private static boolean usable(ProcessingEnvironment env) {
        try {
            Trees.instance(env);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static ProcessingEnvironment delegateOf(ProcessingEnvironment env) {
        Object holder = env;
        if (Proxy.isProxyClass(env.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(env);
            holder = h;
        }
        for (Class<?> c = holder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object value = f.get(holder);
                    if (value instanceof ProcessingEnvironment pe && pe != env) {
                        return pe;
                    }
                } catch (RuntimeException | IllegalAccessException e) {
                    // поле недоступно (модульная инкапсуляция): пробуем следующее
                }
            }
        }
        return null;
    }
}
