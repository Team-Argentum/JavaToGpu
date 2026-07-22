package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeArtifactPropertiesSupport;

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
        return GpuRuntimeArtifactPropertiesSupport.first(properties, fallback, keys);
    }

    public static String portable(Properties properties, String portablePrefix, String key, String fallback) {
        return GpuRuntimeArtifactPropertiesSupport.portable(properties, portablePrefix, key, fallback);
    }

    public static String prefixedPortable(
            Properties properties,
            String ownerPrefix,
            String portablePrefix,
            String key,
            String fallback
    ) {
        return GpuRuntimeArtifactPropertiesSupport.prefixedPortable(
                properties,
                ownerPrefix,
                portablePrefix,
                key,
                fallback
        );
    }

    public static void appendPortable(StringBuilder builder, String portablePrefix, String key, Object value) {
        GpuRuntimeArtifactPropertiesSupport.appendPortable(builder, portablePrefix, key, value);
    }

    public static void putPortable(Map<String, String> fields, String portablePrefix, String key, Object value) {
        GpuRuntimeArtifactPropertiesSupport.putPortable(fields, portablePrefix, key, value);
    }

    public static void setPortable(Properties properties, String portablePrefix, String key, Object value) {
        GpuRuntimeArtifactPropertiesSupport.setPortable(properties, portablePrefix, key, value);
    }

    public static void putPrefixedPortable(
            Map<String, String> fields,
            String ownerPrefix,
            String portablePrefix,
            String key,
            Object value
    ) {
        GpuRuntimeArtifactPropertiesSupport.putPrefixedPortable(fields, ownerPrefix, portablePrefix, key, value);
    }

    public static void setPrefixedPortable(
            Properties properties,
            String ownerPrefix,
            String portablePrefix,
            String key,
            Object value
    ) {
        GpuRuntimeArtifactPropertiesSupport.setPrefixedPortable(
                properties,
                ownerPrefix,
                portablePrefix,
                key,
                value
        );
    }

    public static void appendPrefixedPortable(
            StringBuilder builder,
            String ownerPrefix,
            String portablePrefix,
            String key,
            Object value
    ) {
        GpuRuntimeArtifactPropertiesSupport.appendPrefixedPortable(
                builder,
                ownerPrefix,
                portablePrefix,
                key,
                value
        );
    }
}
