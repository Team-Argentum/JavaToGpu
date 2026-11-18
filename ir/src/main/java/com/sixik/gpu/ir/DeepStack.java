package com.sixik.gpu.ir;

import java.util.function.Supplier;

/**
 * Запуск на потоке с большим стеком (шаг B7, FRONTEND_ASM_RUNTIME.md, S9-1). Структуризатор фронтенда байткода,
 * понижение выражений, валидатор, оптимизатор и генератор рекурсивны по глубине вложенности (так проще и ближе к
 * структуре IR), а генератор роутера GA2 строит вложенность глубже, чем выдерживает стек потока по умолчанию (1 МБ -
 * примерно 500 уровней {@code if} или {@code ?:}). Вместо переписывания всех проходов на явные стеки тяжёлая работа
 * идёт на отдельном потоке со стеком {@link #STACK_BYTES}: память стека резервируется, а занимается по мере роста.
 *
 * <p>Вложенный вызов (уже на таком потоке) выполняется на месте. Исключения и ошибки тела пробрасываются вызывающему
 * как есть; {@link StackOverflowError} - тоже, вызывающий превращает его в диагностику.</p>
 */
public final class DeepStack {

    /** Резерв стека: 512 МБ (порядка 100 тысяч уровней вложенности). */
    public static final long STACK_BYTES = 512L << 20;

    private static final ThreadLocal<Boolean> DEEP = ThreadLocal.withInitial(() -> false);

    private DeepStack() {
    }

    /**
     * Выполняет {@code body} на потоке с большим стеком и ждёт результат.
     *
     * @param name имя потока (для профилировщика и дампов)
     * @param body работа
     * @param <T>  тип результата
     * @return результат {@code body}
     */
    public static <T> T call(String name, Supplier<T> body) {
        if (DEEP.get()) {
            return body.get();
        }
        Object[] result = new Object[1];
        Throwable[] failure = new Throwable[1];
        Thread t = new Thread(null, () -> {
            DEEP.set(true);
            try {
                result[0] = body.get();
            } catch (Throwable e) {
                failure[0] = e;
            }
        }, name, STACK_BYTES);
        t.setDaemon(true);
        t.start();
        boolean interrupted = false;
        while (true) {
            try {
                t.join();
                break;
            } catch (InterruptedException e) {
                // работа короткая и без внешних ресурсов: дожидаемся её, флаг прерывания восстанавливаем
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure[0] instanceof RuntimeException e) {
            throw e;
        }
        if (failure[0] instanceof Error e) {
            throw e;
        }
        if (failure[0] != null) {
            throw new IllegalStateException(failure[0]);
        }
        @SuppressWarnings("unchecked")
        T r = (T) result[0];
        return r;
    }
}
