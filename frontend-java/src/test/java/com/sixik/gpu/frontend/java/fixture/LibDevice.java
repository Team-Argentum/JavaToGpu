package com.sixik.gpu.frontend.java.fixture;

import com.sixik.gpu.api.annotations.DeviceFunction;

/**
 * Библиотека device-функций, доступная тестам только в виде class-файла (без исходников): так проверяется
 * диагностика {@code GPU0114}.
 */
public final class LibDevice {

    private LibDevice() {
    }

    /**
     * @param a первое слагаемое
     * @param b второе слагаемое
     * @return сумма
     */
    @DeviceFunction
    public static int libAdd(int a, int b) {
        return a + b;
    }
}
