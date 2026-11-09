package com.sixik.gpu.runtime.device;

/**
 * Ошибка вызова OpenCL ({@link DeviceApi} не принимает решений, поэтому сообщает об ошибке исключением; решение -
 * забота {@code GpuContext}, который превращает его в диагностику).
 */
public final class DeviceApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String function;

    /**
     * @param status   код ошибки OpenCL ({@code CL_*}, отрицательный) либо {@code -1}, если вызов не дошёл до драйвера
     * @param function имя вызванной функции OpenCL
     * @param message  пояснение
     */
    public DeviceApiException(int status, String function, String message) {
        super(function + ": " + message + " (code " + status + ")");
        this.status = status;
        this.function = function;
    }

    /** @return код ошибки OpenCL */
    public int status() {
        return status;
    }

    /** @return имя функции OpenCL */
    public String function() {
        return function;
    }
}
