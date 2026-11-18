package com.sixik.gpu.runtime.device;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Выбор бэкенда драйвера среди {@link DeviceApiProvider} на classpath.
 *
 * <p>Порядок - по убыванию приоритета; поставщик, который не поддерживается этой JVM или не нашёл библиотеку OpenCL,
 * пропускается. Имя бэкенда можно задать явно свойством {@code javatogpu.backend} или переменной окружения
 * {@code JAVATOGPU_BACKEND} ({@code ffm}, {@code lwjgl}): тогда пробуется только он.</p>
 */
public final class DeviceApis {

    /** Свойство JVM с именем бэкенда. */
    public static final String PROPERTY = "javatogpu.backend";
    /** Переменная окружения с именем бэкенда. */
    public static final String ENVIRONMENT = "JAVATOGPU_BACKEND";

    private DeviceApis() {
    }

    /**
     * @return поставщики этой JVM по убыванию приоритета (неподдерживаемые и сломанные пропущены)
     */
    public static List<DeviceApiProvider> providers() {
        return providers(DeviceApis.class.getClassLoader());
    }

    /**
     * @param loader загрузчик, через который искать поставщиков
     * @return поставщики по убыванию приоритета
     */
    public static List<DeviceApiProvider> providers(ClassLoader loader) {
        List<DeviceApiProvider> out = new ArrayList<>();
        var it = ServiceLoader.load(DeviceApiProvider.class, loader).iterator();
        while (true) {
            DeviceApiProvider p;
            try {
                if (!it.hasNext()) {
                    break;
                }
                p = it.next();
            } catch (ServiceConfigurationError | LinkageError e) {
                // класс бэкенда собран под более новую Java или не нашёл зависимость: пропускаем его
                continue;
            }
            try {
                if (p.isSupported()) {
                    out.add(p);
                }
            } catch (RuntimeException | LinkageError e) {
                // неподдерживаемый бэкенд не должен ронять поиск остальных
            }
        }
        out.sort(Comparator.comparingInt(DeviceApiProvider::priority).reversed());
        return out;
    }

    /** @return имя бэкенда из настроек либо {@code null} */
    static String requested() {
        String v = System.getProperty(PROPERTY);
        if (v == null || v.isBlank()) {
            v = System.getenv(ENVIRONMENT);
        }
        return v == null || v.isBlank() ? null : v.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Загружает драйвер через первый подходящий бэкенд.
     *
     * @return слой доступа либо {@code null}: бэкендов нет или ни один не нашёл библиотеку OpenCL
     */
    public static DeviceApi load() {
        return load(providers(), requested());
    }

    static DeviceApi load(List<DeviceApiProvider> providers, String only) {
        for (DeviceApiProvider p : providers) {
            if (only != null && !only.equals(p.name())) {
                continue;
            }
            DeviceApi api;
            try {
                api = p.load();
            } catch (RuntimeException | LinkageError e) {
                api = null;
            }
            if (api != null) {
                return api;
            }
        }
        return null;
    }
}
