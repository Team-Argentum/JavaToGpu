package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Map;
import java.util.Properties;

/**
 * Shared helpers for backend-neutral runtime artifact property maps.
 *
 * <p>New artifact readers should prefer portable {@code runtime.*} keys first and keep legacy keys only as a
 * compatibility fallback. Centralizing that lookup keeps OpenCL, CUDA, and future backend tooling on the same spelling
 * rules instead of copying small {@code firstProperty(...)} variants across reports and gates.</p>
 */
public final class GpuRuntimeArtifactProperties {

    private GpuRuntimeArtifactProperties() {
    }

    public static String first(Properties properties, String fallback, String... keys) {
        if (properties == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = properties.getProperty(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    public static String portable(Properties properties, String portablePrefix, String key, String fallback) {
        return first(properties, fallback, portableKey(portablePrefix, key), key);
    }

    public static String prefixedPortable(
            Properties properties,
            String ownerPrefix,
            String portablePrefix,
            String key,
            String fallback
    ) {
        String prefix = ownerPrefix == null ? "" : ownerPrefix;
        return first(properties, fallback, prefix + portableKey(portablePrefix, key), prefix + key);
    }

    public static void appendPortable(StringBuilder builder, String portablePrefix, String key, Object value) {
        builder.append(portableKey(portablePrefix, key)).append('=').append(value).append('\n');
    }

    public static void putPortable(Map<String, String> fields, String portablePrefix, String key, Object value) {
        fields.put(portableKey(portablePrefix, key), String.valueOf(value));
    }

    public static void setPortable(Properties properties, String portablePrefix, String key, Object value) {
        properties.setProperty(portableKey(portablePrefix, key), String.valueOf(value));
    }

    public static void putPrefixedPortable(
            Map<String, String> fields,
            String ownerPrefix,
            String portablePrefix,
            String key,
            Object value
    ) {
        fields.put((ownerPrefix == null ? "" : ownerPrefix) + portableKey(portablePrefix, key), String.valueOf(value));
    }

    public static void setPrefixedPortable(
            Properties properties,
            String ownerPrefix,
            String portablePrefix,
            String key,
            Object value
    ) {
        properties.setProperty(
                (ownerPrefix == null ? "" : ownerPrefix) + portableKey(portablePrefix, key),
                String.valueOf(value)
        );
    }

    public static void appendPrefixedPortable(
            StringBuilder builder,
            String ownerPrefix,
            String portablePrefix,
            String key,
            Object value
    ) {
        builder.append(ownerPrefix == null ? "" : ownerPrefix)
                .append(portableKey(portablePrefix, key))
                .append('=')
                .append(value)
                .append('\n');
    }

    private static String portableKey(String portablePrefix, String key) {
        String prefix = portablePrefix == null || portablePrefix.isBlank() ? "" : portablePrefix;
        if (!prefix.isEmpty() && !prefix.endsWith(".")) {
            prefix += ".";
        }
        return prefix + (key == null ? "" : key);
    }
}
