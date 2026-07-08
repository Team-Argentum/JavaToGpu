package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuIrRuntimeEquivalenceDiagnosticFamiliesTest {
    @Test
    void classifiesRuntimeEquivalenceDiagnosticFamilies() {
        assertEquals("executionFailed", GpuIrRuntimeEquivalenceDiagnosticFamilies.family(
                "case case-a execution failed: Unsupported expression"
        ));
        assertEquals("missingOutput", GpuIrRuntimeEquivalenceDiagnosticFamilies.family(
                "case case-a output out is missing"
        ));
        assertEquals("outputDiffers", GpuIrRuntimeEquivalenceDiagnosticFamilies.family(
                "case case-a output out differs expected=1 actual=2"
        ));
        assertEquals("other", GpuIrRuntimeEquivalenceDiagnosticFamilies.family(
                "literal canonicalization runtime equivalence not run"
        ));
    }

    @Test
    void countsFamiliesInEncounterOrder() {
        Map<String, Long> counts = GpuIrRuntimeEquivalenceDiagnosticFamilies.counts(List.of(
                "case case-a output out differs expected=1 actual=2",
                "case case-b output mask is missing",
                "case case-c execution failed: Unsupported expression",
                "case case-d output second differs expected=3 actual=4",
                "literal canonicalization runtime equivalence not run"
        ));

        assertEquals(4, counts.size());
        assertEquals(2L, counts.get("outputDiffers"));
        assertEquals(1L, counts.get("missingOutput"));
        assertEquals(1L, counts.get("executionFailed"));
        assertEquals(1L, counts.get("other"));
        assertEquals("{outputDiffers=2,missingOutput=1,executionFailed=1,other=1}",
                GpuIrRuntimeEquivalenceDiagnosticFamilies.countsSummary(counts));
    }

    @Test
    void writesStableArtifactFields() {
        Map<String, String> fields = new LinkedHashMap<>();

        GpuIrRuntimeEquivalenceDiagnosticFamilies.putArtifactFields(fields, "runtime.", List.of(
                "case case-a execution failed: Unsupported expression",
                "case case-b output out is missing",
                "case case-c output out differs expected=1 actual=2"
        ));

        assertEquals("{executionFailed=1,missingOutput=1,outputDiffers=1}",
                fields.get("runtime.DiagnosticFamilyCounts"));
        assertEquals("1", fields.get("runtime.DiagnosticFamily.executionFailed"));
        assertEquals("1", fields.get("runtime.DiagnosticFamily.missingOutput"));
        assertEquals("1", fields.get("runtime.DiagnosticFamily.outputDiffers"));
    }

    @Test
    void writesEmptyFamilySummaryForSuccessfulArtifacts() {
        Map<String, String> fields = new LinkedHashMap<>();

        GpuIrRuntimeEquivalenceDiagnosticFamilies.putArtifactFields(fields, "runtime.", List.of());

        assertEquals("{}", fields.get("runtime.DiagnosticFamilyCounts"));
        assertEquals(1, fields.size());
    }

    @Test
    void writesRollupArtifactFields() {
        Map<String, String> fields = new LinkedHashMap<>();

        GpuIrRuntimeEquivalenceDiagnosticFamilies.putRollupArtifactFields(fields, "runtimeEquivalence", List.of(
                "case case-a output out is missing",
                "case case-b output out differs expected=1 actual=2"
        ));

        assertEquals("2", fields.get("runtimeEquivalenceDiagnostics"));
        assertEquals("true", fields.get("hasRuntimeEquivalenceDiagnostics"));
        assertEquals("{missingOutput=1,outputDiffers=1}", fields.get("runtimeEquivalenceDiagnosticFamilyCounts"));
        assertEquals("1", fields.get("runtimeEquivalenceDiagnosticFamily.missingOutput"));
        assertEquals("1", fields.get("runtimeEquivalenceDiagnosticFamily.outputDiffers"));
        assertEquals("{missingOutput=1,outputDiffers=1}",
                GpuIrRuntimeEquivalenceDiagnosticFamilies.countsSummary(List.of(
                        "case case-a output out is missing",
                        "case case-b output out differs expected=1 actual=2"
                )));
    }
}
