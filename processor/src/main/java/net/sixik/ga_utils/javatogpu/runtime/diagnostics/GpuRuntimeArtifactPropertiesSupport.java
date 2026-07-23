package net.sixik.ga_utils.javatogpu.runtime.diagnostics;

import java.util.Map;
import java.util.Properties;

/**
 * Domain implementation support for backend-neutral runtime artifact property maps.
 */
public final class GpuRuntimeArtifactPropertiesSupport {

    private GpuRuntimeArtifactPropertiesSupport() {
    }

    /**
     * Returns the first non-blank property value for the supplied keys, or the fallback when none match.
     */
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

    /**
     * Reads a portable {@code prefix.key} property before falling back to the legacy key.
     */
    public static String portable(Properties properties, String portablePrefix, String key, String fallback) {
        return first(properties, fallback, portableKey(portablePrefix, key), key);
    }

    /**
     * Reads an owner-prefixed portable property before falling back to the owner-prefixed legacy key.
     */
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

    /**
     * Appends a portable key/value property line.
     */
    public static void appendPortable(StringBuilder builder, String portablePrefix, String key, Object value) {
        builder.append(portableKey(portablePrefix, key)).append('=').append(value).append('\n');
    }

    /**
     * Puts a portable key/value field into a map.
     */
    public static void putPortable(Map<String, String> fields, String portablePrefix, String key, Object value) {
        fields.put(portableKey(portablePrefix, key), String.valueOf(value));
    }

    /**
     * Sets a portable key/value property.
     */
    public static void setPortable(Properties properties, String portablePrefix, String key, Object value) {
        properties.setProperty(portableKey(portablePrefix, key), String.valueOf(value));
    }

    /**
     * Puts an owner-prefixed portable key/value field into a map.
     */
    public static void putPrefixedPortable(
            Map<String, String> fields,
            String ownerPrefix,
            String portablePrefix,
            String key,
            Object value
    ) {
        fields.put((ownerPrefix == null ? "" : ownerPrefix) + portableKey(portablePrefix, key), String.valueOf(value));
    }

    /**
     * Sets an owner-prefixed portable key/value property.
     */
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

    /**
     * Appends an owner-prefixed portable key/value property line.
     */
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
