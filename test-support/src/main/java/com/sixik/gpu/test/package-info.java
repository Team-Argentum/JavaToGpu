/**
 * Тестовая библиотека поверх JUnit (REWORK_ARCHITECTURE.md, 5.25, 9.6, 13): одно ядро - один результат на JVM, в
 * IR-интерпретаторе и на устройстве.
 *
 * <ul>
 *   <li>{@link com.sixik.gpu.test.GpuAssert#sameResult(Class, String, Object...)} - главное утверждение;</li>
 *   <li>{@link com.sixik.gpu.test.KernelSample} - образец: аргументы (свежие на каждый вызов), размер запуска,
 *       ожидаемый результат, допуск в ULP, ветви; собирается кодом или загружается из JSON;</li>
 *   <li>{@link com.sixik.gpu.test.GPUTest} - {@code @Test} с проверкой устройства и параметром {@code GpuContext}.</li>
 * </ul>
 *
 * <p>Тесты на реальном устройстве помечайте тегом {@code device}: обычная сборка их пропускает.</p>
 */
package com.sixik.gpu.test;
