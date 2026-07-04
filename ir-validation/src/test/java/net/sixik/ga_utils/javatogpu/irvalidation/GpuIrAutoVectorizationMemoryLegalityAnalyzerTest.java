package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationMemoryLegalityAnalyzerTest {
    private final GpuIrAutoVectorizationMemoryLegalityAnalyzer analyzer = new GpuIrAutoVectorizationMemoryLegalityAnalyzer();

    @Test
    void reportsAliasWarningsAndMemoryAddressSpaceGuards() {
        GpuIrAutoVectorizationMemoryLegalityReport report = analyzer.analyze(
                "stmt[0]",
                orderedSet("out", "scratch"),
                orderedSet("left", "out"),
                name -> switch (name) {
                    case "out" -> Optional.of(parameter("out", GpuAddressSpace.GLOBAL, false));
                    case "scratch" -> Optional.of(parameter("scratch", GpuAddressSpace.LOCAL, false));
                    case "left" -> Optional.of(parameter("left", GpuAddressSpace.CONSTANT, false));
                    default -> Optional.empty();
                }
        );

        assertFalse(report.rewriteSafe());
        assertTrue(report.hasAliasWarnings());
        assertTrue(report.hasGuardDiagnostics());
        assertEquals(3, report.diagnosticCount());
        assertEquals(List.of("target array `out` is also read in the loop body"), report.aliasWarnings());
        assertEquals(2, report.guardDiagnostics().size());
        assertTrue(report.guardDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.summary().contains("target array `scratch` uses local memory address space")));
        assertTrue(report.guardDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.summary().contains("source array `left` uses constant memory address space")));
        assertEquals(
                Map.of(GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE, 2L),
                report.guardFamilyTypeCounts()
        );
        assertEquals(Map.of("memoryAddressSpace", 2L), report.guardFamilyCounts());
        assertEquals("memoryLegality", report.proofSummary().proofKind());
        assertEquals(3, report.proofSummary().diagnosticCount());
        assertEquals("memoryLegality", report.artifactFields().get("autoVectorizationProofMemoryLegalityKind"));
        assertEquals("false", report.artifactFields().get("autoVectorizationProofMemoryLegalityRewriteSafe"));
        assertEquals("3", report.artifactFields().get("autoVectorizationProofMemoryLegalityDiagnostics"));
        assertEquals("2", report.artifactFields().get("autoVectorizationProofMemoryLegalityGuardFamily.memoryAddressSpace"));
        assertTrue(report.summary().contains("rewriteSafe=false"));
        assertTrue(report.summary().contains("warnings=1"));
    }

    @Test
    void reportsSafeWhenNoAliasOrAddressSpaceGuardsExist() {
        GpuIrAutoVectorizationMemoryLegalityReport report = analyzer.analyze(
                "stmt[0]",
                orderedSet("out"),
                orderedSet("left"),
                name -> Optional.of(parameter(name, GpuAddressSpace.GLOBAL, false))
        );

        assertTrue(report.rewriteSafe());
        assertFalse(report.hasAliasWarnings());
        assertFalse(report.hasGuardDiagnostics());
        assertEquals(0, report.diagnosticCount());
        assertEquals(List.of(), report.aliasWarnings());
        assertEquals(List.of(), report.guardDiagnostics());
        assertEquals(Map.of(), report.guardFamilyCounts());
        assertTrue(report.proofSummary().rewriteSafe());
        assertEquals("true", report.artifactFields().get("autoVectorizationProofMemoryLegalityRewriteSafe"));
        assertEquals("0", report.artifactFields().get("autoVectorizationProofMemoryLegalityDiagnostics"));
    }

    @Test
    void treatsReadOnlyGlobalTargetsAsMemoryGuards() {
        GpuIrAutoVectorizationMemoryLegalityReport report = analyzer.analyze(
                "stmt[0]",
                orderedSet("out"),
                orderedSet("left"),
                name -> Optional.of(parameter(name, GpuAddressSpace.GLOBAL, "out".equals(name)))
        );

        assertFalse(report.rewriteSafe());
        assertEquals(1, report.guardDiagnostics().size());
        assertTrue(report.guardDiagnostics().get(0).summary()
                .contains("target array `out` uses read-only memory address space"));
    }

    @Test
    void returnsImmutableReportCollectionsAndRejectsInvalidInputs() {
        GpuIrAutoVectorizationMemoryLegalityReport report = analyzer.analyze(
                "stmt[0]",
                orderedSet("out"),
                orderedSet("left"),
                name -> Optional.of(parameter(name, GpuAddressSpace.GLOBAL, false))
        );

        assertThrows(UnsupportedOperationException.class, () -> report.targetArrays().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> report.guardDiagnostics().add(
                new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                        GpuIrAutoVectorizationRewriteGuardFamily.OTHER,
                        "stmt[0]",
                        "other"
                )
        ));
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(
                "",
                orderedSet("out"),
                orderedSet("left"),
                name -> Optional.empty()
        ));
        assertThrows(NullPointerException.class, () -> analyzer.aliasWarnings(null, orderedSet("left")));
    }

    private LinkedHashSet<String> orderedSet(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private ParsedGpuParameter parameter(String name, GpuAddressSpace addressSpace, boolean constant) {
        return new ParsedGpuParameter(name, "int[]", addressSpace, constant, List.of());
    }
}
