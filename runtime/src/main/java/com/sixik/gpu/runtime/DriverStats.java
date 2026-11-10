package com.sixik.gpu.runtime;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ресурсы ядра по данным драйвера: сколько регистров и private-памяти он реально выделил и сколько «пролил».
 *
 * <p>Стандарт OpenCL число регистров не сообщает. NVIDIA печатает его в логе сборки при опции {@code -cl-nv-verbose}
 * (вывод {@code ptxas}):</p>
 * <pre>
 * ptxas info    : Compiling entry function 'k' for 'sm_120'
 * ptxas info    : Function properties for k
 *     16 bytes stack frame, 8 bytes spill stores, 8 bytes spill loads
 * ptxas info    : Used 40 registers, used 0 barriers, 368 bytes cmem[0]
 * </pre>
 * <p>Объём private-памяти ({@code CL_KERNEL_PRIVATE_MEM_SIZE}) - стандартный запрос и доступен на любом драйвере.
 * Неизвестные значения - {@code -1}.</p>
 *
 * @param registers        32-битных регистров на work-item либо {@code -1}
 * @param spillStoreBytes  байт записей spill на work-item либо {@code -1}
 * @param spillLoadBytes   байт чтений spill на work-item либо {@code -1}
 * @param stackBytes       байт стекового кадра на work-item либо {@code -1}
 * @param privateBytes     {@code CL_KERNEL_PRIVATE_MEM_SIZE} либо {@code -1}
 */
public record DriverStats(int registers, int spillStoreBytes, int spillLoadBytes, int stackBytes, long privateBytes) {

    /** Ничего не известно. */
    public static final DriverStats UNKNOWN = new DriverStats(-1, -1, -1, -1, -1);

    /** Опция сборки NVIDIA, включающая вывод ресурсов ядра в лог. */
    public static final String NVIDIA_VERBOSE = "-cl-nv-verbose";

    private static final Pattern ENTRY = Pattern.compile("Compiling entry function '([^']+)'");
    private static final Pattern REGISTERS = Pattern.compile("Used (\\d+) registers");
    private static final Pattern FRAME = Pattern.compile(
            "(\\d+) bytes stack frame, (\\d+) bytes spill stores, (\\d+) bytes spill loads");

    /** @return {@code true}, если драйвер сообщил, что значения проливаются в память */
    public boolean spills() {
        return spillStoreBytes > 0 || spillLoadBytes > 0;
    }

    /**
     * @param privateBytes значение {@code CL_KERNEL_PRIVATE_MEM_SIZE}
     * @return копия с заданным объёмом private-памяти
     */
    DriverStats withPrivateBytes(long privateBytes) {
        return new DriverStats(registers, spillStoreBytes, spillLoadBytes, stackBytes, privateBytes);
    }

    /**
     * Разбирает лог сборки NVIDIA. Если в программе несколько точек входа, берутся строки после
     * {@code Compiling entry function '<entry>'}; если имени в логе нет - первые найденные значения.
     *
     * @param log   лог {@code clBuildProgram}
     * @param entry имя точки входа ядра
     * @return статистика (поля, которых нет в логе, - {@code -1})
     */
    static DriverStats parseNvidiaLog(String log, String entry) {
        if (log == null || log.isBlank()) {
            return UNKNOWN;
        }
        String section = log;
        Matcher e = ENTRY.matcher(log);
        while (e.find()) {
            if (e.group(1).equals(entry)) {
                int start = e.end();
                Matcher next = ENTRY.matcher(log);
                int end = next.find(start) ? next.start() : log.length();
                section = log.substring(start, end);
                break;
            }
        }
        int regs = -1;
        Matcher r = REGISTERS.matcher(section);
        if (r.find()) {
            regs = Integer.parseInt(r.group(1));
        }
        int stack = -1;
        int stores = -1;
        int loads = -1;
        Matcher f = FRAME.matcher(section);
        if (f.find()) {
            stack = Integer.parseInt(f.group(1));
            stores = Integer.parseInt(f.group(2));
            loads = Integer.parseInt(f.group(3));
        }
        return new DriverStats(regs, stores, loads, stack, -1);
    }
}
