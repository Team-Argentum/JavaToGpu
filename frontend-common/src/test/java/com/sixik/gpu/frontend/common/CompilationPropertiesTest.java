package com.sixik.gpu.frontend.common;

import com.sixik.gpu.ir.IrModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Разбор строки {@code compilationProperties}: синтаксис, значения, ошибки и подсказки. */
class CompilationPropertiesTest {

    private static List<String> codes(CompilationProperties p) {
        return p.problems.stream().map(CompilationProperties.Problem::code).toList();
    }

    @Test
    void emptyStringMeansDefaults() {
        for (String s : List.of("", "   ", ";", " ; ; ")) {
            CompilationProperties p = CompilationProperties.parse(s);
            assertTrue(p.problems.isEmpty(), s);
            assertEquals(0, p.fp);
            assertFalse(p.hasOpt());
            assertFalse(p.hasInline());
        }
    }

    @Test
    void valuesAndWhitespace() {
        CompilationProperties p = CompilationProperties.parse(" fp = contract ,reciprocal,finite, nsz ;opt=0; inline = always ;");
        assertTrue(p.problems.isEmpty(), p.problems::toString);
        assertEquals(IrModule.FP_CONTRACT | IrModule.FP_RECIPROCAL | IrModule.FP_FINITE | IrModule.FP_NO_SIGNED_ZERO, p.fp);
        assertEquals(0, p.opt, "opt=0 is a real level, not 'inherit'");
        assertTrue(p.hasOpt());
        assertEquals(IrModule.INLINE_ALWAYS, p.inline);
    }

    @Test
    void repeatedFlagsAndRelaxedAreIdempotent() {
        assertEquals(IrModule.FP_ALL, CompilationProperties.parse("fp=relaxed,native,contract").fp);
        assertEquals(IrModule.FP_NATIVE, CompilationProperties.parse("fp=native,native").fp);
    }

    @Test
    void malformedSegments() {
        for (String s : List.of("fp", "=contract", "fp=", "fp==x", "fp=a=b", "f p=contract")) {
            assertEquals(List.of("GPU0019"), codes(CompilationProperties.parse(s)), s);
        }
    }

    @Test
    void unknownKeyWithSuggestion() {
        CompilationProperties p = CompilationProperties.parse("FP=contract");
        assertEquals(List.of("GPU0020"), codes(p));
        assertEquals("; did you mean 'fp'?", p.problems.get(0).args()[1]);
        assertEquals("", CompilationProperties.parse("unroll=4").problems.get(0).args()[1], "no near match - no hint");
    }

    @Test
    void invalidValues() {
        CompilationProperties fp = CompilationProperties.parse("fp=contract,natve");
        assertEquals(List.of("GPU0021"), codes(fp));
        assertTrue(fp.problems.get(0).args()[2].toString().endsWith("did you mean 'native'?"), fp.problems::toString);
        assertEquals(List.of("GPU0021"), codes(CompilationProperties.parse("opt=4")));
        assertEquals(List.of("GPU0021"), codes(CompilationProperties.parse("opt=-1")));
        assertEquals(List.of("GPU0021"), codes(CompilationProperties.parse("inline=sometimes")));
        assertEquals(List.of("GPU0021"), codes(CompilationProperties.parse("fp=contract,")), "an empty list item");
    }

    @Test
    void everyProblemIsReported() {
        CompilationProperties p = CompilationProperties.parse("fp=x; speed=max; opt=1; opt=2; oops");
        assertEquals(List.of("GPU0021", "GPU0020", "GPU0022", "GPU0019"), codes(p));
    }
}
