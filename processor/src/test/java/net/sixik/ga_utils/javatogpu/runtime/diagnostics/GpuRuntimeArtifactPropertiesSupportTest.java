package net.sixik.ga_utils.javatogpu.runtime.diagnostics;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeArtifactProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GpuRuntimeArtifactPropertiesSupportTest {

    @Test
    void rootArtifactPropertiesFacadeDelegatesPortableLookupToDiagnosticsSupport() {
        Properties properties = new Properties();
        properties.setProperty("status", "legacy");
        properties.setProperty("runtime.production.candidateGate.status", "portable");

        assertEquals(
                GpuRuntimeArtifactPropertiesSupport.portable(
                        properties,
                        "runtime.production.candidateGate",
                        "status",
                        "missing"
                ),
                GpuRuntimeArtifactProperties.portable(
                        properties,
                        "runtime.production.candidateGate",
                        "status",
                        "missing"
                )
        );
    }

    @Test
    void rootArtifactPropertiesFacadeDelegatesPortableWritesToDiagnosticsSupport() {
        LinkedHashMap<String, String> rootFields = new LinkedHashMap<>();
        LinkedHashMap<String, String> supportFields = new LinkedHashMap<>();

        GpuRuntimeArtifactProperties.putPrefixedPortable(
                rootFields,
                "kernel.0.",
                "runtime.backend.source",
                "decision",
                "compile-irgpu-source-review"
        );
        GpuRuntimeArtifactPropertiesSupport.putPrefixedPortable(
                supportFields,
                "kernel.0.",
                "runtime.backend.source",
                "decision",
                "compile-irgpu-source-review"
        );

        assertEquals(supportFields, rootFields);
        assertEquals(
                "compile-irgpu-source-review",
                rootFields.get("kernel.0.runtime.backend.source.decision")
        );
    }
}
