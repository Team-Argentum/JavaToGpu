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
        assertEquals("true", fields.get("runtimeEquivalencePayload.present"));
        assertEquals("false", fields.get("runtimeEquivalencePayload.cpuReference.present"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.preOptimizationOutput.present"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.postOptimizationOutput.present"));
        assertEquals("false", fields.get("runtimeEquivalencePayload.tolerance.present"));
        assertEquals("true", fields.get("runtimeEquivalencePayload.failureFixture.present"));
        assertEquals("i2://pre-post-runtime-equivalence/prePost", fields.get("runtimeEquivalencePayload.resource"));
        assertEquals(
                "i2://pre-post-runtime-equivalence/prePost/cpu-reference",
                fields.get("runtimeEquivalencePayload.cpuReference.resource")
        );
        assertEquals(
                "i2://pre-post-runtime-equivalence/prePost/pre-output",
                fields.get("runtimeEquivalencePayload.preOptimizationOutput.resource")
        );
        assertEquals(
                "i2://pre-post-runtime-equivalence/prePost/post-output",
                fields.get("runtimeEquivalencePayload.postOptimizationOutput.resource")
        );
        assertEquals(
                "i2://pre-post-runtime-equivalence/prePost/tolerance",
                fields.get("runtimeEquivalencePayload.tolerance.resource")
        );
        assertEquals(
                "i2://pre-post-runtime-equivalence/prePost/failure-fixture",
                fields.get("runtimeEquivalencePayload.failureFixture.resource")
        );
        assertEquals("false", fields.get("prePost.Artifact.Successful"));
    }
}
