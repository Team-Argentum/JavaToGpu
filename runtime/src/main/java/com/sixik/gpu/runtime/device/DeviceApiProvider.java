package com.sixik.gpu.runtime.device;

/**
 * Поставщик {@link DeviceApi} для {@link java.util.ServiceLoader}: модуль бэкенда регистрирует реализацию в
 * {@code META-INF/services/com.sixik.gpu.runtime.device.DeviceApiProvider}.
 *
 * <p>Поставщиков может быть несколько ({@code runtime-opencl-ffm} и {@code runtime-opencl-lwjgl} на одном classpath):
 * {@link DeviceApis#load()} пробует их по убыванию {@link #priority()} и берёт первый, который загрузил драйвер.
 * Поставщик обязан быть безопасен для загрузки на любой поддерживаемой JVM: проверка «могу ли я работать здесь»
 * ({@link #isSupported()}) не должна касаться классов, которых в этой JVM нет.</p>
 */
public interface DeviceApiProvider {

    /** @return короткое имя для журнала и выбора по имени ({@code ffm}, {@code lwjgl}) */
    String name();

    /** @return приоритет: больше - пробуется раньше */
    int priority();

    /**
     * @return {@code true}, если бэкенд может работать в этой JVM (версия Java, наличие библиотек); ничего не загружает
     */
    boolean isSupported();

    /**
     * Загружает драйвер.
     *
     * @return слой доступа либо {@code null}, если библиотеки OpenCL нет (это не ошибка: «устройств нет»)
     */
    DeviceApi load();
}
