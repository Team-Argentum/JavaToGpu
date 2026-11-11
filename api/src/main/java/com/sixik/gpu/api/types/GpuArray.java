package com.sixik.gpu.api.types;

import com.sixik.gpu.api.internal.GpuArrayAccess;

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
 * <p>Состояние синхронизации с устройством меняет только рантайм, через служебный {@link GpuArrayAccess} (модуль
 * {@code api} не зависит от рантайма).</p>
 */
public abstract sealed class GpuArray permits GpuFloatArray, GpuIntArray, GpuLongArray, GpuDoubleArray {

    static {
        GpuArrayAccess.install(new Access());
    }

    private GpuArrayAccess.Residency residency;
    private Object residence;
    private boolean hostStale;
    private boolean deviceStale = true;

    GpuArray() {
    }

    /** @return число элементов */
    public abstract int length();

    /** @return примитивный массив Java с данными хоста (без синхронизации) */
    abstract Object hostData();

    /** Реализация служебного протокола с рантаймом ({@link GpuArrayAccess}). */
    private static final class Access extends GpuArrayAccess {
        @Override
        public Object hostArray(GpuArray a) {
            return a.hostData();
        }

        @Override
        public Object residence(GpuArray a) {
            return a.residence;
        }

        @Override
        public void attach(GpuArray a, Residency residency, Object residence) {
            a.residency = residency;
            a.residence = residence;
        }

        @Override
        public void detach(GpuArray a) {
            a.residency = null;
            a.residence = null;
            a.hostStale = false;
            a.deviceStale = true;
        }

        @Override
        public boolean hostStale(GpuArray a) {
            return a.hostStale;
        }

        @Override
        public boolean deviceStale(GpuArray a) {
            return a.deviceStale;
        }

        @Override
        public void markWrittenOnDevice(GpuArray a) {
            a.hostStale = true;
            a.deviceStale = false;
        }

        @Override
        public void markInSync(GpuArray a) {
            a.hostStale = false;
            a.deviceStale = false;
        }
    }

    // ---- для подклассов -------------------------------------------------------------------------

    /** Перед чтением хоста: подтянуть свежие данные с устройства, если они там. */
    final void syncHost() {
        if (hostStale) {
            GpuArrayAccess.Residency r = residency;
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
