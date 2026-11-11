package com.sixik.gpu.runtime;

import com.sixik.gpu.runtime.device.DeviceApi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Буфер в памяти устройства с явным владением и временем жизни (REWORK_ARCHITECTURE.md, 9: «первоклассный объект -
 * {@code GpuBuffer}»). Данные остаются на устройстве между запусками, поэтому цепочка ядер не гоняет их через хост.
 *
 * <p>Создаётся {@link GpuContext#allocate} или {@link GpuContext#upload}; закрывается {@link #close()} (либо вместе с
 * контекстом). Объект <b>не потокобезопасен</b>: один буфер нельзя использовать конкурентно из разных потоков.
 * Массивы хоста в вызовах ядра - сахар над временными буферами; явный буфер нужен, когда данные переживают вызов.</p>
 */
public final class GpuBuffer implements AutoCloseable {

    private final GpuContext owner;
    private final long handle;
    private final int elementType;
    private final long length;
    private boolean closed;
    /** Закреплённая память вида {@link #readView()} либо {@code null} (под замком контекста). */
    private DeviceApi.Pinned viewPinned;
    /** Область жизни вида: закрывается раньше освобождения памяти, чтобы старый вид не стал висячим указателем. */
    private Arena viewScope;
    /** Память вида, привязанная к {@link #viewScope}. */
    private MemorySegment view;

    GpuBuffer(GpuContext owner, long handle, int elementType, long length) {
        this.owner = owner;
        this.handle = handle;
        this.elementType = elementType;
        this.length = length;
    }

    /** @return дескриптор OpenCL (для рантайма) */
    long handle() {
        return handle;
    }

    /** @return контекст-владелец */
    GpuContext owner() {
        return owner;
    }

    /** @return тип элемента IR, с которым создан буфер */
    public int elementType() {
        return elementType;
    }

    /** @return число элементов */
    public long length() {
        return length;
    }

    /** @return размер в байтах */
    public long byteSize() {
        return length * Marshal.elementSize(elementType);
    }

    /** @return {@code true}, если буфер освобождён */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Копирует массив хоста в начало буфера.
     *
     * @param hostArray Java-массив примитивов того же вида, что элемент буфера, не длиннее буфера
     * @return этот буфер
     * @throws GpuException если буфер закрыт, вид или длина массива не подходят, либо драйвер вернул ошибку
     */
    public GpuBuffer write(Object hostArray) {
        owner.write(this, hostArray);
        return this;
    }

    /**
     * Копирует начало буфера в массив хоста (столько элементов, какова длина массива).
     *
     * @param hostArray Java-массив примитивов того же вида, что элемент буфера, не длиннее буфера
     * @return этот буфер
     * @throws GpuException если буфер закрыт, вид или длина массива не подходят, либо драйвер вернул ошибку
     */
    public GpuBuffer read(Object hostArray) {
        owner.read(this, hostArray);
        return this;
    }

    /**
     * Читает содержимое буфера в закреплённую память хоста и возвращает вид на неё <b>без копирования в Java-массив</b>.
     *
     * <p>Для крупных результатов это вдвое быстрее {@link #read}: остаётся только передача по шине (DMA из закреплённой
     * памяти), а копирование в массив, которое стоит столько же, исчезает. Значения читаются из вида напрямую, например
     * {@code view.getAtIndex(ValueLayout.JAVA_FLOAT, i)}.</p>
     *
     * <p>Закреплённая память выделяется при первом вызове и переиспользуется. Вид:</p>
     * <ul>
     *   <li>только для чтения ({@link MemorySegment#isReadOnly()});</li>
     *   <li>отражает состояние на момент вызова: следующий {@code readView()} перезаписывает ту же память;</li>
     *   <li>действителен до {@link #close()} буфера или контекста; обращение после закрытия бросает
     *       {@link IllegalStateException}, а не обращается к освобождённой памяти;</li>
     *   <li>доступен из любого потока (но сам буфер, как и прежде, не потокобезопасен).</li>
     * </ul>
     *
     * @return вид на содержимое буфера длиной {@link #byteSize()} байт
     * @throws GpuException если буфер или контекст закрыт либо драйвер вернул ошибку
     */
    public MemorySegment readView() {
        return owner.readView(this);
    }

    /**
     * Возвращает вид для записи на закреплённую память хоста этого буфера - вход заполняется <b>без Java-массива</b>, а
     * {@link #commitView()} передаёт его на устройство прямым DMA.
     *
     * <p>Для крупных входов это вдвое быстрее {@link #write}: копирование из массива в закреплённую память, которое стоит
     * столько же, сколько сама передача, исчезает. Значения пишутся в вид напрямую, например
     * {@code view.setAtIndex(ValueLayout.JAVA_FLOAT, i, x)}.</p>
     *
     * <p>Это та же память, что у {@link #readView()} (выделяется при первом обращении к любому из видов). Поэтому:</p>
     * <ul>
     *   <li>сам вызов ничего не передаёт; содержимое вида - то, что в нём осталось (после {@link #readView()} - прочитанное
     *       содержимое буфера, у только что выделенной памяти - не определено);</li>
     *   <li>{@link #readView()} до {@link #commitView()} перезаписывает незафиксированные изменения;</li>
     *   <li>вид действителен до {@link #close()} буфера или контекста, обращение после закрытия бросает
     *       {@link IllegalStateException}.</li>
     * </ul>
     *
     * @return изменяемый вид длиной {@link #byteSize()} байт
     * @throws GpuException если буфер или контекст закрыт
     */
    public MemorySegment writeView() {
        return owner.writeView(this);
    }

    /**
     * Передаёт содержимое вида ({@link #writeView()}) в буфер устройства. Вызов блокирующий: после возврата вид можно
     * снова изменять, на переданные данные это не влияет.
     *
     * @return этот буфер
     * @throws GpuException если буфер или контекст закрыт, вид ещё не создан либо драйвер вернул ошибку
     */
    public GpuBuffer commitView() {
        owner.commitView(this);
        return this;
    }

    /** @return память вида либо {@code null}, если вид ещё не создан (вызывается под замком контекста) */
    MemorySegment existingView() {
        return view;
    }

    /** @return память вида, выделяя её при первом обращении (вызывается под замком контекста) */
    MemorySegment viewMemory(DeviceApi api, long context, long queue) {
        if (view == null) {
            long bytes = Math.max(byteSize(), 8);
            Arena scope = Arena.ofShared();
            DeviceApi.Pinned pinned = null;
            try {
                pinned = api.createPinned(context, queue, bytes);
            } catch (RuntimeException e) {
                // нет закреплённой памяти - обычная память тоже даёт вид без копирования в массив
                pinned = null;
            }
            view = pinned != null ? pinned.memory().reinterpret(bytes, scope, null) : scope.allocate(bytes, 64);
            viewPinned = pinned;
            viewScope = scope;
        }
        return view;
    }

    /**
     * Освобождает память вида: сначала закрывает область (старые виды перестают быть доступны), затем память драйвера.
     *
     * @param api         слой доступа
     * @param queue       очередь
     * @param driverAlive {@code false}, если контекст драйвера уже освобождается целиком
     */
    void releaseView(DeviceApi api, long queue, boolean driverAlive) {
        if (viewScope != null) {
            viewScope.close();
        }
        if (viewPinned != null && driverAlive) {
            api.releasePinned(queue, viewPinned);
        }
        viewScope = null;
        viewPinned = null;
        view = null;
    }

    /** Освобождает память устройства; повторный вызов безопасен. */
    @Override
    public void close() {
        owner.free(this);
    }

    void markClosed() {
        closed = true;
    }

    @Override
    public String toString() {
        return "GpuBuffer[" + length + " x t" + elementType + (closed ? ", closed" : "") + "]";
    }
}
