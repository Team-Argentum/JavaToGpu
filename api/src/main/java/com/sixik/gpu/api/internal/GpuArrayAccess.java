package com.sixik.gpu.api.internal;

import com.sixik.gpu.api.types.GpuArray;

/**
 * Служебный протокол между {@link GpuArray} и рантаймом: состояние синхронизации массива с устройством. Пакет
 * {@code com.sixik.gpu.api.internal} - не публичный API: его классы могут меняться без предупреждения, пользовательскому
 * коду они не нужны. Изменение этого состояния в обход рантайма рассинхронизирует данные хоста и устройства.
 *
 * <p>Единственный экземпляр создаёт {@link GpuArray} при инициализации своего класса; рантайм получает его через
 * {@link #get()}.</p>
 */
public abstract class GpuArrayAccess {

    private static volatile GpuArrayAccess instance;

    /** Синхронизация с устройством, которую предоставляет рантайм. */
    public interface Residency {
        /**
         * Переносит свежие данные с устройства в память хоста массива и снимает отметку {@link GpuArrayAccess#hostStale}.
         *
         * @param array массив, у которого свежие данные на устройстве
         */
        void toHost(GpuArray array);
    }

    /** Только для {@link GpuArray}: подклассы создаются ровно один раз, см. {@link #install}. */
    protected GpuArrayAccess() {
    }

    /**
     * Регистрирует реализацию протокола. Вызывается один раз из инициализатора класса {@link GpuArray}.
     *
     * @param access реализация
     * @throws IllegalStateException если реализация уже зарегистрирована
     */
    public static synchronized void install(GpuArrayAccess access) {
        if (instance != null) {
            throw new IllegalStateException("GpuArrayAccess is already installed");
        }
        instance = access;
    }

    /** @return реализация протокола (класс {@link GpuArray} инициализируется при необходимости) */
    public static GpuArrayAccess get() {
        GpuArrayAccess a = instance;
        if (a == null) {
            try {
                Class.forName(GpuArray.class.getName(), true, GpuArray.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            a = instance;
        }
        return a;
    }

    /**
     * Память хоста массива. Перед чтением рантайм обязан убедиться, что {@link #hostStale} ложно.
     *
     * @param array массив
     * @return примитивный массив Java с данными хоста
     */
    public abstract Object hostArray(GpuArray array);

    /**
     * @param array массив
     * @return состояние, которое рантайм связал с массивом (буфер устройства), либо {@code null}
     */
    public abstract Object residence(GpuArray array);

    /**
     * Связывает массив с копией на устройстве.
     *
     * @param array     массив
     * @param residency синхронизация, вызываемая при обращении хоста к устаревшим данным
     * @param residence состояние рантайма (буфер устройства)
     */
    public abstract void attach(GpuArray array, Residency residency, Object residence);

    /**
     * Разрывает связь с устройством; данные хоста должны быть свежими.
     *
     * @param array массив
     */
    public abstract void detach(GpuArray array);

    /**
     * @param array массив
     * @return {@code true}, если свежие данные только на устройстве
     */
    public abstract boolean hostStale(GpuArray array);

    /**
     * @param array массив
     * @return {@code true}, если копия на устройстве устарела или её нет
     */
    public abstract boolean deviceStale(GpuArray array);

    /**
     * Ядро записало массив: свежие данные на устройстве.
     *
     * @param array массив
     */
    public abstract void markWrittenOnDevice(GpuArray array);

    /**
     * Копия на устройстве совпадает с хостом (после загрузки либо чтения).
     *
     * @param array массив
     */
    public abstract void markInSync(GpuArray array);
}
