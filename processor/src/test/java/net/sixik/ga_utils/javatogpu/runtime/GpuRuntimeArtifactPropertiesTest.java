package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuRuntimeArtifactPropertiesTest {

    @Test
    void prefersPortableValueBeforeLegacyFallback() {
        Properties properties = new Properties();
        properties.setProperty("status", "legacy");
        properties.setProperty("runtime.production.candidateGate.status", "portable");

        assertEquals(
                "portable",
                GpuRuntimeArtifactProperties.portable(
                        properties,
                        GpuBackendSourcePromotionCandidateGate.PORTABLE_PREFIX,
                        "status",
                        "missing"
                )
        );
    }

    @Test
    void fallsBackToLegacyWhenPortableValueIsMissing() {
        Properties properties = new Properties();
        properties.setProperty("status", "legacy");

        assertEquals(
                "legacy",
                GpuRuntimeArtifactProperties.portable(properties, "runtime.production.candidateGate", "status", "missing")
        );
    }

    @Test
    void readsPrefixedKernelPortableValuesBeforeLegacyValues() {
        Properties properties = new Properties();
        properties.setProperty("kernel.0.resource", "legacy.cl");
        properties.setProperty("kernel.0.runtime.production.candidateGate.resource", "portable.cl");

        assertEquals(
                "portable.cl",
                GpuRuntimeArtifactProperties.prefixedPortable(
                        properties,
                        "kernel.0.",
                        GpuBackendSourcePromotionCandidateGate.PORTABLE_PREFIX,
                        "resource",
                        "missing"
                )
        );
    }

    @Test
    void appendsPortablePropertiesWithNormalizedPrefix() {
        StringBuilder builder = new StringBuilder();

        GpuRuntimeArtifactProperties.appendPortable(builder, "runtime.production.manifest", "status", "approved");

        assertEquals("runtime.production.manifest.status=approved\n", builder.toString());
    }

    @Test
    void putsPortableMapFieldsWithNormalizedPrefix() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();

        GpuRuntimeArtifactProperties.putPortable(fields, "runtime.backend.adapter", "present", true);

        assertEquals("true", fields.get("runtime.backend.adapter.present"));
    }

    @Test
    void putsPrefixedPortableMapFieldsWithOwnerPrefix() {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();

        GpuRuntimeArtifactProperties.putPrefixedPortable(
                fields,
                "kernel.0.",
                "runtime.production.activationGate",
                "ready",
                false
        );

        assertEquals("false", fields.get("kernel.0.runtime.production.activationGate.ready"));
    }

    @Test
    void setsPortablePropertiesWithNormalizedPrefix() {
        Properties properties = new Properties();

        GpuRuntimeArtifactProperties.setPortable(properties, "runtime.ir.productionMutation", "enabled", true);

        assertEquals("true", properties.getProperty("runtime.ir.productionMutation.enabled"));
    }

    @Test
    void setsPrefixedPortablePropertiesWithOwnerPrefix() {
        Properties properties = new Properties();

        GpuRuntimeArtifactProperties.setPrefixedPortable(
                properties,
                "kernel.0.",
                "runtime.backend.source",
                "decision",
                "compile-irgpu-source-review"
        );

        assertEquals(
                "compile-irgpu-source-review",
                properties.getProperty("kernel.0.runtime.backend.source.decision")
        );
    }
}
