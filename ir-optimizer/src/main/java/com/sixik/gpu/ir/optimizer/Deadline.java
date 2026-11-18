package com.sixik.gpu.ir.optimizer;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Срок оптимизации текущего потока (шаг B7, FRONTEND_ASM_RUNTIME.md, S9-2). {@link Optimizer} открывает срок на запуск
 * конвейера; проходы вызывают {@link #check()} между функциями и на итерациях неподвижных точек. Вне оптимизатора
 * (оценка давления из рантайма) срока нет и проверка ничего не делает.
 */
final class Deadline implements AutoCloseable {

    /** Срок истёк: запуск конвейера прерывается целиком и повторяется на уровне ниже. */
    static final class OverBudget extends RuntimeException {
        private static final long serialVersionUID = 1L;

        OverBudget() {
            super("optimizer time budget exceeded", null, false, false);
        }
    }

    private static final ThreadLocal<Deadline> CURRENT = new ThreadLocal<>();

    private final LongSupplier clock;
    private final long until;
    private final Deadline outer;

    private Deadline(LongSupplier clock, long until, Deadline outer) {
        this.clock = clock;
        this.until = until;
        this.outer = outer;
    }

    /**
     * Открывает срок для потока.
     *
     * @param clock  часы в наносекундах
     * @param budget длительность
     * @return срок; закрыть после запуска
     */
    static Deadline start(LongSupplier clock, Duration budget) {
        long now = clock.getAsLong();
        long nanos;
        try {
            nanos = budget.toNanos();
        } catch (ArithmeticException e) {
            nanos = Long.MAX_VALUE;
        }
        long until = now + nanos < now ? Long.MAX_VALUE : now + nanos;
        Deadline d = new Deadline(clock, until, CURRENT.get());
        CURRENT.set(d);
        return d;
    }

    /** @throws OverBudget если срок потока истёк */
    static void check() {
        Deadline d = CURRENT.get();
        if (d != null && d.clock.getAsLong() - d.until > 0) {
            throw new OverBudget();
        }
    }

    @Override
    public void close() {
        if (outer == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(outer);
        }
    }
}
