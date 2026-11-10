package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Разбор ресурсов ядра из лога сборки NVIDIA ({@code -cl-nv-verbose}). */
class DriverStatsTest {

    private static final String LOG = """
            ptxas info    : 0 bytes gmem
            ptxas info    : Compiling entry function 'other' for 'sm_120'
            ptxas info    : Function properties for other
                0 bytes stack frame, 0 bytes spill stores, 0 bytes spill loads
            ptxas info    : Used 12 registers, used 0 barriers, 360 bytes cmem[0]
            ptxas info    : Compiling entry function 'heavy' for 'sm_120'
            ptxas info    : Function properties for heavy
                48 bytes stack frame, 24 bytes spill stores, 16 bytes spill loads
            ptxas info    : Used 255 registers, used 1 barriers, 368 bytes cmem[0]
            """;

    @Test
    void parsesTheSectionOfTheRequestedEntry() {
        DriverStats s = DriverStats.parseNvidiaLog(LOG, "heavy");
        assertEquals(255, s.registers());
        assertEquals(48, s.stackBytes());
        assertEquals(24, s.spillStoreBytes());
        assertEquals(16, s.spillLoadBytes());
        assertTrue(s.spills());
        DriverStats o = DriverStats.parseNvidiaLog(LOG, "other");
        assertEquals(12, o.registers());
        assertFalse(o.spills());
    }

    @Test
    void unknownEntryFallsBackToTheFirstValuesAndEmptyLogIsUnknown() {
        assertEquals(12, DriverStats.parseNvidiaLog(LOG, "missing").registers());
        assertEquals(DriverStats.UNKNOWN, DriverStats.parseNvidiaLog("", "k"));
        assertEquals(-1, DriverStats.parseNvidiaLog("some other driver output", "k").registers());
    }

    @Test
    void ptxasLinesDoNotStickToTheLastWarning() {
        String log = "<kernel>:3:5: warning: unused variable 'x'\n    int x;\n" + LOG;
        List<Diagnostic> d = BuildLog.parse(log, 0, false);
        assertEquals(1, d.size());
        assertFalse(d.get(0).message().contains("ptxas"), d.get(0).message());
        assertFalse(d.get(0).message().contains("spill"), d.get(0).message());
    }
}
