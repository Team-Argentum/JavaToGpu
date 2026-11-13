package com.sixik.gpu.frontend.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Контракт {@link KernelAttributes#parse}: карта явно заданных элементов (её строят оба фронтенда), значения по
 * умолчанию, устаревшие элементы только при явном задании, ошибки и предупреждения.
 */
class KernelAttributesTest {

    /** Результат разбора и выданные коды. */
    private record Parsed(KernelAttributes attrs, List<String> codes) {
    }

    private static Parsed parse(Map<String, Object> kernel, Map<String, Object> workGroup) {
        List<String> codes = new ArrayList<>();
        KernelAttributes a = KernelAttributes.parse("k", kernel, workGroup, (code, args) -> codes.add(code));
        return new Parsed(a, codes);
    }

    @Test
    void emptyMapMeansAnnotationDefaults() {
        Parsed p = parse(Map.of(), null);
        assertNotNull(p.attrs());
        assertTrue(p.codes().isEmpty(), p.codes().toString());
    }

    @Test
    void workGroupSourcesConflict() {
        assertEquals(List.of("GPU0008"), parse(Map.of("workgroup", 64), Map.of("x", 8)).codes());
        assertNull(parse(Map.of("workgroup", 64), Map.of()).attrs(), "@WorkGroup with defaults still conflicts");
    }

    @Test
    void workGroupSizesMustBePositive() {
        assertEquals(List.of("GPU0013"), parse(Map.of("workgroup", -1), null).codes());
        assertEquals(List.of("GPU0013"), parse(Map.of(), Map.of("y", 0)).codes());
        assertTrue(parse(Map.of(), Map.of("x", 16)).codes().isEmpty(), "missing y and z default to 1");
    }

    @Test
    void propertiesProblemsGetTheKernelName() {
        List<Object[]> args = new ArrayList<>();
        KernelAttributes a = KernelAttributes.parse("myKernel", Map.of("compilationProperties", "opt=9"), null,
                (code, rest) -> args.add(rest));
        assertNull(a);
        assertEquals("myKernel", args.get(0)[0]);
    }

    @Test
    void legacyElementsCountOnlyWhenExplicit() {
        assertTrue(parse(Map.of("optLevel", 2, "inline", "NEVER"), null).codes().isEmpty());
        assertEquals(List.of("GPU0012"), parse(Map.of("optLevel", 7), null).codes());
        assertEquals(List.of("GPU0023"), parse(Map.of("compilationProperties", "opt=1", "optLevel", 1), null).codes());
        assertEquals(List.of("GPU0023"),
                parse(Map.of("compilationProperties", "inline=always", "inline", "ALWAYS"), null).codes());
    }

    @Test
    void unknownFeaturesAreReported() {
        assertEquals(List.of("GPU0009"), parse(Map.of("required", List.of("FP64", "WARP")), null).codes());
        assertNotNull(parse(Map.of("required", List.of("FP64", "FP16", "FP64")), null).attrs());
    }
}
