package com.sixik.gpu.frontend.common;

/**
 * Правила аннотаций параметров, общие для {@code frontend-java} и {@code frontend-asm}: оба фронтенда сообщают одну
 * и ту же причину в одной диагностике.
 */
public final class ParamRules {

    private ParamRules() {
    }

    /**
     * Причина, по которой {@code @Private} на параметре недопустима ({@code GPU0123}), либо {@code null}, если
     * аннотация уместна: private-массив бывает только параметром device-функции (правило IR-M04 запрещает его у ядра).
     *
     * @param kernel     параметр ядра
     * @param javaArray  параметр - Java-массив
     * @param otherSpace на параметре есть и {@code @Local} или {@code @Global}
     * @return текст причины или {@code null}
     */
    public static String privateMisuse(boolean kernel, boolean javaArray, boolean otherSpace) {
        if (kernel) {
            return "a kernel parameter cannot be in private memory";
        }
        if (!javaArray) {
            return "only an array parameter can be private";
        }
        if (otherSpace) {
            return "@Private conflicts with @Local or @Global";
        }
        return null;
    }
}
