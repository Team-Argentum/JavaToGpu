package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuIrPrePostRuntimeEquivalenceArtifactFieldsTest {
    @Test
    void writesSharedPrePostRuntimeEquivalenceFields() {
        Map<String, String> fields = new LinkedHashMap<>();

        GpuIrPrePostRuntimeEquivalenceArtifactFields.putCommonFields(
                fields,
                "prePost.",
                false,
                false,
                1,
                List.of("case case-a output out differs expected=1 actual=2"),
                "summary text",
                Map.of("prePost.Artifact.Successful", "false")
        );

        assertEquals("false", fields.get("prePost.Successful"));
        assertEquals("false", fields.get("prePost.RuntimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("prePost.RuntimeEquivalenceDiagnostics"));
        assertEquals("{outputDiffers=1}", fields.get("prePost.RuntimeEquivalenceDiagnosticFamilyCounts"));
        assertEquals("1", fields.get("prePost.RuntimeEquivalenceDiagnosticFamily.outputDiffers"));
        assertEquals("summary text", fields.get("prePost.Summary"));
        assertEquals("false", fields.get("prePost.Artifact.Successful"));
    }
}
