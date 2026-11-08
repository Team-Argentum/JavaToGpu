package com.sixik.gpu.ir.interpreter;

import java.util.concurrent.Semaphore;

/**
 * Передача управления между work-item-ами одной группы (M10): сопрограммы поверх потоков.
 *
 * <p>Каждый work-item исполняется в своём потоке, но <b>в любой момент работает ровно один</b>: поток ждёт на своём
 * семафоре {@code go}, планировщик отдаёт ему ход ({@link #resume}) и ждёт на общем семафоре {@code back}, пока тот не
 * дойдёт до барьера ({@link #arrive}) или не завершится ({@link #finish}). Каждая передача - пара
 * {@code release/acquire}, то есть отношение «происходит-до», поэтому разделяемые массивы интерпретатора не требуют
 * синхронизации, а порядок исполнения детерминирован: внутри фазы work-item-ы идут по возрастанию номера.</p>
 *
 * <p>Класс не знает про IR: он хранит лишь «где остановился» (номер узла барьера) и «закончил ли».</p>
 */
final class Coop {

    /** Остановка потока work-item-а, когда группа прекращена из-за нарушения другого work-item-а. */
    static final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Abort() {
            super(null, null, false, false);
        }
    }

    private final Semaphore[] go;
    private final Semaphore back = new Semaphore(0);
    private final int[] waitingAt;
    private final boolean[] finished;
    private volatile boolean aborted;

    /** @param count число work-item-ов группы */
    Coop(int count) {
        go = new Semaphore[count];
        waitingAt = new int[count];
        finished = new boolean[count];
        for (int i = 0; i < count; i++) {
            go[i] = new Semaphore(0);
            waitingAt[i] = -1;
        }
    }

    /** Поток work-item-а ждёт своего первого хода. */
    void awaitFirstTurn(int index) {
        go[index].acquireUninterruptibly();
        if (aborted) {
            throw new Abort();
        }
    }

    /**
     * Work-item дошёл до барьера: отдаёт ход планировщику и ждёт следующего.
     *
     * @param index номер work-item-а
     * @param node  узел барьера
     * @throws Abort если за время ожидания группа была остановлена
     */
    void arrive(int index, int node) {
        waitingAt[index] = node;
        back.release();
        go[index].acquireUninterruptibly();
        if (aborted) {
            throw new Abort();
        }
    }

    /** Work-item закончил (успешно, с нарушением или остановлен): возвращает ход планировщику. */
    void finish(int index) {
        finished[index] = true;
        waitingAt[index] = -1;
        back.release();
    }

    /** Планировщик: даёт ход work-item-у и ждёт, пока тот остановится на барьере либо завершится. */
    void resume(int index) {
        go[index].release();
        back.acquireUninterruptibly();
    }

    /** @return {@code true}, если work-item завершился */
    boolean isFinished(int index) {
        return finished[index];
    }

    /** @return узел барьера, на котором стоит work-item (имеет смысл, пока он не завершился) */
    int waitingAt(int index) {
        return waitingAt[index];
    }

    /** Останавливает группу: все ждущие work-item-ы получают {@link Abort} и завершаются. */
    void abort() {
        aborted = true;
        for (int i = 0; i < go.length; i++) {
            if (!finished[i]) {
                go[i].release();
            }
        }
    }
}
