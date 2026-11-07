package com.sixik.gpu.tools;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Дамп IR: golden-тесты и свойства формата. */
class IrDumpTest {

    @Test
    void computeSineMatchesGolden() {
        Golden.assertMatches("computeSine.ir.txt", IrDump.dump(Fixtures.computeSine()));
    }

    @Test
    void computeSineWithDebugMatchesGolden() {
        Golden.assertMatches("computeSine.ir.debug.txt", IrDump.dump(Fixtures.computeSine(), true));
    }

    @Test
    void richModuleMatchesGolden() {
        Golden.assertMatches("rich.ir.txt", IrDump.dump(Fixtures.rich()));
    }

    @Test
    void dumpIsDeterministic() {
        assertEquals(IrDump.dump(Fixtures.rich()), IrDump.dump(Fixtures.rich()));
    }

    @Test
    void releaseDumpDoesNotDependOnDebugSection() {
        assertEquals(IrDump.dump(Fixtures.computeSine()), IrDump.dump(Fixtures.computeSineWithoutDebug()),
                "дамп без отладки одинаков, как и хэш модуля");
    }

    @Test
    void debugDumpContainsNamesAndPositions() {
        String text = IrDump.dump(Fixtures.computeSine(), true);
        assertTrue(text.contains("\"computeSine\""));
        assertTrue(text.contains("\"input\""));
        assertTrue(text.contains("\"id\""));
        assertTrue(text.contains("@3:5"));
        assertFalse(IrDump.dump(Fixtures.computeSine()).contains("computeSine"));
    }

    @Test
    void typeNames() {
        IrModule m = Fixtures.computeSine();
        assertEquals("i32", IrDump.typeName(m, Types.I32));
        assertEquals("array<global,f32>", IrDump.typeName(m, 16));
        assertEquals("ptr<global,f32>", IrDump.typeName(m, 17));
        assertEquals("t?999", IrDump.typeName(m, 999));
    }
}
