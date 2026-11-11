package com.sixik.gpu.api.types;

/**
 * Базовый класс массивов, данные которых могут жить на устройстве между вызовами ядер ({@link GpuFloatArray},
 * {@link GpuIntArray}, {@link GpuLongArray}, {@link GpuDoubleArray}).
 *
 * <p>Обычный {@code float[]} не сообщает, когда хост его читает или меняет, поэтому рантайм обязан загружать его перед
 * каждым запуском ядра и читать обратно после. Массив этого семейства знает, где лежат свежие данные:</p>
 * <ul>
 *   <li>ядро, которое записало массив, оставляет свежие данные на устройстве; на хост они читаются лениво, при первом
 *       обращении {@code get}/{@code toArray}/{@code copyTo};</li>
 *   <li>запись с хоста ({@code set}, {@code fill}, {@code copyFrom}) помечает копию устройства устаревшей: следующий
 *       запуск ядра загрузит массив заново;</li>
 *   <li>цепочка ядер, передающих массив друг другу, не гоняет данные через шину вовсе.</li>
 * </ul>
 *
 * <p>В ядре ({@code @Kernel}, {@code @DeviceFunction}) массив пишется как {@code a.get(i)}, {@code a.set(i, v)},
 * {@code a.length()} и компилируется в то же, что {@code a[i]} и {@code a.length} для обычного массива. Если ядро
 * выполняется на JVM (нет устройства), это обычные методы над данными хоста.</p>
 *
 * <p>Объект <b>не потокобезопасен</b>, как и {@code float[]}: один массив нельзя менять конкурентно из разных потоков.</p>
 *
 * <p>Методы, помеченные «для рантайма», - служебный протокол между этим классом и рантаймом (модуль {@code api} не
 * зависит от рантайма); пользовательскому коду они не нужны.</p>
 */
public abstract sealed class GpuArray permits GpuFloatArray, GpuIntArray, GpuLongArray, GpuDoubleArray {

    /**
     * Синхронизация с устройством, которую предоставляет рантайм (для рантайма).
     */
    public interface Residency {
        /**
         * Переносит свежие данные с устройства в память хоста массива и снимает отметку {@link #hostStale()}.
         *
         * @param array массив, у которого свежие данные на устройстве
         */
        void toHost(GpuArray array);
    }

    private Residency residency;
    private Object residence;
    private boolean hostStale;
    private boolean deviceStale = true;

    GpuArray() {
    }

    /** @return число элементов */
    public abstract int length();

    // ---- служебный протокол с рантаймом ---------------------------------------------------------

    /**
     * Память хоста массива (для рантайма). Перед чтением рантайм обязан убедиться, что {@link #hostStale()} ложно.
     *
     * @return примитивный массив Java с данными хоста
     */
    public abstract Object hostArray();

    /** @return состояние, которое рантайм связал с массивом (буфер устройства), либо {@code null} (для рантайма) */
    public final Object residence() {
        return residence;
    }

    /**
     * Связывает массив с копией на устройстве (для рантайма).
     *
     * @param residency синхронизация, вызываемая при обращении хоста к устаревшим данным
     * @param residence состояние рантайма (буфер устройства)
     */
    public final void attach(Residency residency, Object residence) {
        this.residency = residency;
        this.residence = residence;
    }

    /** Разрывает связь с устройством; данные хоста должны быть свежими (для рантайма). */
    public final void detach() {
        this.residency = null;
        this.residence = null;
        this.hostStale = false;
        this.deviceStale = true;
    }

    /** @return {@code true}, если свежие данные только на устройстве (для рантайма) */
    public final boolean hostStale() {
        return hostStale;
    }

    /** @return {@code true}, если копия на устройстве устарела или её нет (для рантайма) */
    public final boolean deviceStale() {
        return deviceStale;
    }

    /** Ядро записало массив: свежие данные на устройстве (для рантайма). */
    public final void markWrittenOnDevice() {
        hostStale = true;
        deviceStale = false;
    }

    /** Копия на устройстве совпадает с хостом (после загрузки либо чтения) (для рантайма). */
    public final void markInSync() {
        hostStale = false;
        deviceStale = false;
    }

    // ---- для подклассов -------------------------------------------------------------------------

    /** Перед чтением хоста: подтянуть свежие данные с устройства, если они там. */
    final void syncHost() {
        if (hostStale) {
            Residency r = residency;
            if (r == null) {
                throw new IllegalStateException("the array data is on a device that is no longer available");
            }
            r.toHost(this);
        }
    }

    /** После записи хоста: копия на устройстве больше не актуальна. */
    final void hostWritten() {
        deviceStale = true;
    }

    /** Хост перезаписал массив целиком: данные устройства не нужны, даже если были свежее. */
    final void markHostOverwritten() {
        hostStale = false;
        deviceStale = true;
    }
}
