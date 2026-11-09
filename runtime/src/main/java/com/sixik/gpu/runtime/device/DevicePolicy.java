package com.sixik.gpu.runtime.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Политика выбора устройства - данные плюс одна функция выбора (REWORK_ARCHITECTURE.md, 9.4).
 *
 * <p>Порядок выбора один и задокументирован: сначала фильтры ({@link #kind}, {@link #vendor},
 * {@link #minGlobalMemory}, {@link #requiredFeatures}), затем из оставшихся - GPU раньше остальных, внутри
 * группы - порядок перечисления драйвера. Явный {@link #override} побеждает фильтры.</p>
 *
 * <p>Синтаксис {@code override} (он же значение системного свойства {@code javatogpu.device} или переменной окружения
 * {@code JAVATOGPU_DEVICE}):</p>
 * <ul>
 *   <li>{@code "2"} - устройство с порядковым номером 2 среди всех;</li>
 *   <li>{@code "nvidia"} - первое устройство, в имени или производителе которого есть подстрока (без учёта регистра);</li>
 *   <li>{@code "nvidia:1"} - второе из устройств, подходящих под подстроку.</li>
 * </ul>
 *
 * @param kind             допустимый тип устройства
 * @param vendor           подстрока производителя или имени ({@code null} - любой)
 * @param minGlobalMemory  минимальный объём глобальной памяти, байт
 * @param requiredFeatures маска {@link com.sixik.gpu.ir.Features}, которую обязано поддерживать устройство
 * @param override         явный выбор либо {@code null}
 */
public record DevicePolicy(Kind kind, String vendor, long minGlobalMemory, int requiredFeatures, String override) {

    /** Допустимые типы устройств. */
    public enum Kind {
        /** Любой тип. */
        ANY,
        /** Только GPU. */
        GPU,
        /** Только CPU. */
        CPU
    }

    /** Системное свойство явного выбора. */
    public static final String PROPERTY = "javatogpu.device";
    /** Переменная окружения явного выбора. */
    public static final String ENVIRONMENT = "JAVATOGPU_DEVICE";

    /** Без фильтров: любое устройство, GPU предпочтительнее. */
    public static final DevicePolicy DEFAULT = new DevicePolicy(Kind.ANY, null, 0, 0, null);

    /** Проверяет, что тип задан. */
    public DevicePolicy {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
    }

    /** @param kind тип @return копия с другим типом */
    public DevicePolicy withKind(Kind kind) {
        return new DevicePolicy(kind, vendor, minGlobalMemory, requiredFeatures, override);
    }

    /** @param vendor подстрока производителя или имени @return копия */
    public DevicePolicy withVendor(String vendor) {
        return new DevicePolicy(kind, vendor, minGlobalMemory, requiredFeatures, override);
    }

    /** @param bytes минимум памяти @return копия */
    public DevicePolicy withMinGlobalMemory(long bytes) {
        return new DevicePolicy(kind, vendor, bytes, requiredFeatures, override);
    }

    /** @param features маска возможностей @return копия */
    public DevicePolicy withRequiredFeatures(int features) {
        return new DevicePolicy(kind, vendor, minGlobalMemory, features, override);
    }

    /** @param override явный выбор @return копия */
    public DevicePolicy withOverride(String override) {
        return new DevicePolicy(kind, vendor, minGlobalMemory, requiredFeatures, override);
    }

    /**
     * Накладывает явный выбор из окружения: свойство {@link #PROPERTY}, иначе переменная {@link #ENVIRONMENT}.
     *
     * @return политика с {@code override} из окружения либо та же политика, если выбор не задан
     */
    public DevicePolicy withEnvironment() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(ENVIRONMENT);
        }
        return value == null || value.isBlank() ? this : withOverride(value.strip());
    }

    /**
     * Устройства, подходящие под фильтры, в порядке предпочтения.
     *
     * @param all все устройства в порядке перечисления
     * @return подходящие: GPU раньше остальных, внутри группы - порядок перечисления
     */
    public List<DeviceInfo> candidates(List<DeviceInfo> all) {
        List<DeviceInfo> gpu = new ArrayList<>();
        List<DeviceInfo> other = new ArrayList<>();
        for (DeviceInfo d : all) {
            if (!accepts(d)) {
                continue;
            }
            (d.isGpu() ? gpu : other).add(d);
        }
        gpu.addAll(other);
        return gpu;
    }

    private boolean accepts(DeviceInfo d) {
        if (kind == Kind.GPU && !d.isGpu()) {
            return false;
        }
        if (kind == Kind.CPU && !d.isCpu()) {
            return false;
        }
        if (vendor != null && !matches(d, vendor)) {
            return false;
        }
        return d.globalMemory() >= minGlobalMemory && d.supports(requiredFeatures);
    }

    /**
     * Выбирает устройство.
     *
     * @param all все устройства в порядке перечисления
     * @return выбранное устройство; пусто, если подходящего нет (или {@code override} ни на что не указывает)
     */
    public Optional<DeviceInfo> select(List<DeviceInfo> all) {
        if (override != null) {
            return resolveOverride(all);
        }
        List<DeviceInfo> c = candidates(all);
        return c.isEmpty() ? Optional.empty() : Optional.of(c.get(0));
    }

    private Optional<DeviceInfo> resolveOverride(List<DeviceInfo> all) {
        String spec = override.strip();
        int colon = spec.lastIndexOf(':');
        String text = colon >= 0 ? spec.substring(0, colon) : spec;
        Integer n = parseInt(colon >= 0 ? spec.substring(colon + 1) : (isNumber(spec) ? spec : null));
        if (colon < 0 && n != null) {
            return n >= 0 && n < all.size() ? Optional.of(all.get(n)) : Optional.empty();
        }
        int skip = n == null ? 0 : n;
        for (DeviceInfo d : all) {
            if (text.isEmpty() || matches(d, text)) {
                if (skip-- == 0) {
                    return Optional.of(d);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean matches(DeviceInfo d, String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return d.vendor().toLowerCase(Locale.ROOT).contains(t) || d.name().toLowerCase(Locale.ROOT).contains(t);
    }

    private static boolean isNumber(String s) {
        return parseInt(s) != null;
    }

    private static Integer parseInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            int v = Integer.parseInt(s.strip());
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
