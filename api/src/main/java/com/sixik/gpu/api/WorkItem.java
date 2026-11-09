package com.sixik.gpu.api;

/**
 * Привязка {@link WorkItemContext} к потоку на время выполнения ядра на JVM (JVM-fallback, эталон
 * {@code @GPUTest}, REWORK_ARCHITECTURE.md, O6). Через неё JVM-реализации id-builtins ({@code Gpu.getGlobalId}
 * и др.) узнают положение текущего work-item.
 *
 * <p>Привязка действует только внутри {@link #run(WorkItemContext, Runnable)} и снимается автоматически, в том
 * числе при исключении (на {@code ScopedValue}); вложенный запуск временно заменяет контекст внешнего. Чтение
 * вне запуска - ошибка программиста: результат «по умолчанию» молча скрыл бы вызов device-кода вне ядра.</p>
 */
public final class WorkItem {

    private static final ScopedValue<WorkItemContext> CURRENT = ScopedValue.newInstance();

    private WorkItem() {
    }

    /**
     * Выполняет {@code body} с привязкой контекста к текущему потоку. Тело обычно содержит цикл по work-item'ам,
     * который переставляет положение вызовами {@link WorkItemContext#setGlobalId(int, int, int)}.
     *
     * @param ctx  контекст запуска
     * @param body выполняемое действие
     */
    public static void run(WorkItemContext ctx, Runnable body) {
        ScopedValue.where(CURRENT, ctx).run(body);
    }

    /**
     * Проверяет, есть ли у текущего потока привязанный контекст.
     *
     * @return {@code true}, если поток выполняется внутри {@link #run}
     */
    public static boolean isBound() {
        return CURRENT.isBound();
    }

    /**
     * Возвращает контекст текущего запуска.
     *
     * @return контекст текущего запуска
     * @throws IllegalStateException если вызвано вне {@link #run}
     */
    public static WorkItemContext current() {
        if (!CURRENT.isBound()) {
            throw new IllegalStateException(
                    "id builtin called outside a kernel launch: no work-item context (WorkItem.run)");
        }
        return CURRENT.get();
    }

    static int globalId(int dim) {
        return current().globalId(dim);
    }

    static int localId(int dim) {
        return current().localId(dim);
    }

    static int groupId(int dim) {
        return current().groupId(dim);
    }

    static int globalSize(int dim) {
        return current().globalSize(dim);
    }

    static int localSize(int dim) {
        return current().localSize(dim);
    }

    static int numGroups(int dim) {
        return current().numGroups(dim);
    }

    static int workDim() {
        return current().workDim();
    }
}
