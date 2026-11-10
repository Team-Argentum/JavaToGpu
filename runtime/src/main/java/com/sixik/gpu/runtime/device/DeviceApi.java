package com.sixik.gpu.runtime.device;

import java.lang.foreign.MemorySegment;

/**
 * Тонкий слой над OpenCL: почти 1:1 список функций C API (REWORK_ARCHITECTURE.md, 9.2). Это граница портирования
 * рантайма на Rust и точка подмены в тестах без железа (фейковая реализация).
 *
 * <p>Правила слоя:</p>
 * <ul>
 *   <li>объекты OpenCL - числа ({@code long}, значение указателя; {@code 0} не бывает действительным объектом);
 *       владение и порядок освобождения - забота вызывающего ({@link #release});</li>
 *   <li>любая ошибка драйвера - {@link DeviceApiException} с кодом OpenCL и именем функции; слой не принимает решений
 *       и не логирует;</li>
 *   <li>слой <b>не потокобезопасен</b>: сериализацию вызовов делает {@code GpuContext} (9.1);</li>
 *   <li>передача данных синхронная (блокирующая): когда метод вернулся, память хоста можно использовать повторно.</li>
 * </ul>
 */
public interface DeviceApi extends AutoCloseable {

    // ---- коды запросов (значения cl.h) -----------------------------------------------------------
    /** {@code CL_PLATFORM_VERSION}. */
    int PLATFORM_VERSION = 0x0901;
    /** {@code CL_PLATFORM_NAME}. */
    int PLATFORM_NAME = 0x0902;
    /** {@code CL_PLATFORM_VENDOR}. */
    int PLATFORM_VENDOR = 0x0903;
    /** {@code CL_DEVICE_TYPE} (битовая маска). */
    int DEVICE_TYPE = 0x1000;
    /** {@code CL_DEVICE_MAX_COMPUTE_UNITS}. */
    int DEVICE_MAX_COMPUTE_UNITS = 0x1002;
    /** {@code CL_DEVICE_MAX_WORK_GROUP_SIZE}. */
    int DEVICE_MAX_WORK_GROUP_SIZE = 0x1004;
    /** {@code CL_DEVICE_MAX_MEM_ALLOC_SIZE}. */
    int DEVICE_MAX_MEM_ALLOC_SIZE = 0x1010;
    /** {@code CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE}. */
    int DEVICE_MAX_CONSTANT_BUFFER_SIZE = 0x1020;
    /** {@code CL_DEVICE_MAX_CONSTANT_ARGS}. */
    int DEVICE_MAX_CONSTANT_ARGS = 0x1021;
    /** {@code CL_DEVICE_GLOBAL_MEM_SIZE}. */
    int DEVICE_GLOBAL_MEM_SIZE = 0x101F;
    /** {@code CL_DEVICE_LOCAL_MEM_SIZE}. */
    int DEVICE_LOCAL_MEM_SIZE = 0x1023;
    /** {@code CL_DEVICE_NAME}. */
    int DEVICE_NAME = 0x102B;
    /** {@code CL_DEVICE_VENDOR}. */
    int DEVICE_VENDOR = 0x102C;
    /** {@code CL_DRIVER_VERSION}. */
    int DRIVER_VERSION = 0x102D;
    /** {@code CL_DEVICE_VERSION}. */
    int DEVICE_VERSION = 0x102F;
    /** {@code CL_DEVICE_EXTENSIONS}. */
    int DEVICE_EXTENSIONS = 0x1030;

    /** {@code CL_DEVICE_TYPE_CPU}. */
    long TYPE_CPU = 2;
    /** {@code CL_DEVICE_TYPE_GPU}. */
    long TYPE_GPU = 4;
    /** {@code CL_DEVICE_TYPE_ACCELERATOR}. */
    long TYPE_ACCELERATOR = 8;

    /** Виды освобождаемых объектов. */
    enum HandleKind {
        /** Контекст. */
        CONTEXT,
        /** Очередь команд. */
        QUEUE,
        /** Буфер памяти. */
        BUFFER,
        /** Программа. */
        PROGRAM,
        /** Ядро. */
        KERNEL
    }

    /**
     * Итог сборки программы.
     *
     * @param status код {@code clBuildProgram}; {@code 0} - успех
     * @param log    build log драйвера (пустая строка, если его нет)
     */
    record BuildOutcome(int status, String log) {
        /** @return {@code true}, если сборка успешна */
        public boolean ok() {
            return status == 0;
        }
    }

    // ---- платформы и устройства -----------------------------------------------------------------

    /** @return платформы; пустой массив, если драйверов нет */
    long[] platforms();

    /**
     * @param platform платформа
     * @param param    {@code PLATFORM_*}
     * @return строка свойства
     */
    String platformInfo(long platform, int param);

    /**
     * @param platform платформа
     * @return все устройства платформы (любого типа); пустой массив, если их нет
     */
    long[] devices(long platform);

    /**
     * @param device устройство
     * @param param  строковое свойство ({@code DEVICE_NAME}, {@code DEVICE_VENDOR}, {@code DRIVER_VERSION},
     *               {@code DEVICE_VERSION}, {@code DEVICE_EXTENSIONS})
     * @return значение
     */
    String deviceString(long device, int param);

    /**
     * @param device устройство
     * @param param  числовое свойство ({@code DEVICE_TYPE}, {@code DEVICE_GLOBAL_MEM_SIZE} и др.)
     * @return значение
     */
    long deviceLong(long device, int param);

    // ---- контекст и очередь ----------------------------------------------------------------------

    /**
     * @param device устройство
     * @return контекст с одним устройством
     */
    long createContext(long device);

    /**
     * @param context контекст
     * @param device  устройство
     * @return упорядоченная очередь команд
     */
    long createQueue(long context, long device);

    // ---- буферы ----------------------------------------------------------------------------------

    /**
     * @param context контекст
     * @param size    размер в байтах ({@code > 0})
     * @return буфер чтения-записи
     */
    long createBuffer(long context, long size);

    /**
     * Блокирующая запись с хоста на устройство.
     *
     * @param queue  очередь
     * @param buffer буфер
     * @param offset смещение в буфере
     * @param size   число байт
     * @param src    память хоста, не короче {@code size}
     */
    void writeBuffer(long queue, long buffer, long offset, long size, MemorySegment src);

    /**
     * Блокирующее чтение с устройства на хост.
     *
     * @param queue  очередь
     * @param buffer буфер
     * @param offset смещение в буфере
     * @param size   число байт
     * @param dst    память хоста, не короче {@code size}
     */
    void readBuffer(long queue, long buffer, long offset, long size, MemorySegment dst);

    // ---- программа и ядро ------------------------------------------------------------------------

    /**
     * @param context контекст
     * @param source  исходник OpenCL C
     * @return программа (ещё не собранная)
     */
    long createProgram(long context, String source);

    /**
     * Создаёт программу из бинарного образа, полученного ранее от {@link #programBinary} <b>для того же устройства и
     * драйвера</b> (ключ дискового кэша это гарантирует). Как и программу из исходника, её нужно собрать {@link #build}.
     * Образ, который драйвер не принимает (повреждён, от другой версии), - {@link DeviceApiException}: вызывающий
     * обязан тогда собрать программу из исходника.
     *
     * @param context контекст
     * @param device  устройство
     * @param binary  образ
     * @return программа (ещё не собранная)
     */
    long createProgramWithBinary(long context, long device, byte[] binary);

    /**
     * Бинарный образ собранной программы для дискового кэша.
     *
     * @param program собранная программа контекста с одним устройством
     * @return образ либо {@code null}, если драйвер образы не отдаёт
     */
    byte[] programBinary(long program);

    /**
     * Собирает программу. Неудачная сборка - не исключение, а {@link BuildOutcome} с логом: ошибка исходника -
     * обычное, ожидаемое событие.
     *
     * @param program программа
     * @param device  устройство
     * @param options опции {@code clBuildProgram}
     * @return код и build log
     */
    BuildOutcome build(long program, long device, String options);

    /**
     * @param program собранная программа
     * @param name    имя {@code __kernel}-функции
     * @return ядро
     */
    long createKernel(long program, String name);

    /**
     * @param kernel ядро
     * @param device устройство
     * @return наибольший размер work-group, допустимый именно для этого ядра на устройстве
     */
    long kernelMaxWorkGroupSize(long kernel, long device);

    /**
     * Объём private-памяти ядра на work-item ({@code CL_KERNEL_PRIVATE_MEM_SIZE}): стек и «пролитые» значения.
     *
     * @param kernel ядро
     * @param device устройство
     * @return байты либо {@code -1}, если слой доступа этого не сообщает
     */
    default long kernelPrivateMemSize(long kernel, long device) {
        return -1;
    }

    // ---- аргументы и запуск ----------------------------------------------------------------------

    /**
     * @param kernel ядро
     * @param index  номер аргумента
     * @param buffer буфер
     */
    void setArgBuffer(long kernel, int index, long buffer);

    /**
     * Скалярный аргумент по значению.
     *
     * @param kernel ядро
     * @param index  номер аргумента
     * @param size   размер значения в байтах: 4 или 8
     * @param bits   биты значения (для 4 байт - младшие 32)
     */
    void setArgScalar(long kernel, int index, int size, long bits);

    /**
     * Аргумент по значению произвольного размера: вектор или struct, уже упакованные по раскладке ABI.
     *
     * @param kernel ядро
     * @param index  номер аргумента
     * @param value  байты значения (память хоста, не короче {@code size})
     * @param size   размер значения в байтах
     */
    void setArgBytes(long kernel, int index, MemorySegment value, long size);

    /**
     * Ставит ядро в очередь (не ждёт завершения).
     *
     * @param queue  очередь
     * @param kernel ядро
     * @param dim    размерность 1..3
     * @param global размеры NDRange (длина {@code dim})
     * @param local  размеры work-group (длина {@code dim}) либо {@code null} - выбирает драйвер
     */
    void enqueue(long queue, long kernel, int dim, long[] global, long[] local);

    /**
     * Ждёт выполнения всех команд очереди.
     *
     * @param queue очередь
     */
    void finish(long queue);

    // ---- освобождение ----------------------------------------------------------------------------

    /**
     * Освобождает объект; ошибки освобождения не пробрасываются (его вызывают при закрытии).
     *
     * @param kind   вид объекта
     * @param handle объект
     */
    void release(HandleKind kind, long handle);

    /**
     * Закрывает слой (выгружать библиотеку не требуется). Повторный вызов безопасен.
     */
    @Override
    void close();
}
