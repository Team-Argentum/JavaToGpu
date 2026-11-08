package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.DeviceApi;
import com.sixik.gpu.runtime.device.DeviceApiException;
import com.sixik.gpu.runtime.device.OpenClApi;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Мелкие типы рантайма: {@link LaunchConfig}, {@link BuildLog}, {@link Marshal}, события, ошибки. */
class SmallTypesTest {

    // ---- LaunchConfig ---------------------------------------------------------------------------

    @Test
    void launchConfigFactories() {
        LaunchConfig c1 = LaunchConfig.of1D(100);
        assertEquals(1, c1.dim());
        assertEquals(100, c1.global(0));
        assertEquals(1, c1.global(1));
        assertFalse(c1.hasLocal());
        assertEquals(100, c1.workItems());
        LaunchConfig c2 = LaunchConfig.of2D(8, 4, 4, 2);
        assertEquals(2, c2.dim());
        assertEquals(32, c2.workItems());
        assertEquals(4, c2.local(0));
        assertEquals(2, c2.local(1));
        assertTrue(c2.hasLocal());
        LaunchConfig c3 = LaunchConfig.of3D(4, 4, 2, 2, 2, 1);
        assertEquals(3, c3.dim());
        assertEquals(1, c3.local(2));
        assertEquals(LaunchConfig.of1D(64, 8).local(0), 8);
        assertEquals(0, LaunchConfig.of2D(2, 2).local(0));
        assertFalse(LaunchConfig.of3D(1, 2, 3).hasLocal());
    }

    @Test
    void launchConfigRejectsBadSizes() {
        assertThrows(IllegalArgumentException.class, () -> LaunchConfig.of1D(0));
        assertThrows(IllegalArgumentException.class, () -> LaunchConfig.of1D(-5));
        assertThrows(IllegalArgumentException.class, () -> LaunchConfig.of2D(4, 0));
        assertThrows(IllegalArgumentException.class, () -> LaunchConfig.of1D(8, -1));
        assertThrows(IllegalArgumentException.class, () -> LaunchConfig.of3D(1, 1, -1));
    }

    @Test
    void launchConfigToString() {
        assertEquals("LaunchConfig[256]", LaunchConfig.of1D(256).toString());
        assertEquals("LaunchConfig[8x4 / 4x2]", LaunchConfig.of2D(8, 4, 4, 2).toString());
    }

    // ---- BuildLog -------------------------------------------------------------------------------

    @Test
    void buildLogParsesClangStyleMessages() {
        String log = "<kernel>:12:5: error: use of undeclared identifier 'foo'\n  foo(1);\n  ^\n"
                + "C:\\tmp\\k.cl:3:1: warning: unused function 'bar'\n"
                + "<kernel>:20:9: fatal error: too many errors\n";
        List<Diagnostic> d = BuildLog.parse(log, 7, true);
        assertEquals(3, d.size());
        assertEquals("RT-C01", d.get(0).code());
        assertEquals(7, d.get(0).function());
        assertEquals("строка 12:5: use of undeclared identifier 'foo'\n  foo(1);\n  ^", d.get(0).message());
        assertEquals("RT-C02", d.get(1).code());
        assertFalse(d.get(1).isError());
        assertEquals("строка 3:1: unused function 'bar'", d.get(1).message());
        assertEquals("RT-C01", d.get(2).code());
        assertTrue(d.get(2).message().startsWith("строка 20:9"));
    }

    @Test
    void buildLogWithoutRecognisedLinesIsKeptWhole() {
        List<Diagnostic> d = BuildLog.parse("ptxas fatal : unresolved extern function", 0, true);
        assertEquals(1, d.size());
        assertEquals("RT-C01", d.get(0).code());
        assertEquals("ptxas fatal : unresolved extern function", d.get(0).message());
    }

    @Test
    void buildLogFallbackIsTruncated() {
        List<Diagnostic> d = BuildLog.parse("x".repeat(10_000), 0, true);
        assertEquals(1, d.size());
        assertTrue(d.get(0).message().length() < 4100);
        assertTrue(d.get(0).message().endsWith("..."));
    }

    @Test
    void emptyBuildLog() {
        assertTrue(BuildLog.parse("", 0, false).isEmpty());
        assertTrue(BuildLog.parse(null, 0, false).isEmpty());
        assertTrue(BuildLog.parse("  \n ", 0, false).isEmpty());
        List<Diagnostic> failed = BuildLog.parse("", 0, true);
        assertEquals(1, failed.size());
        assertTrue(failed.get(0).message().contains("пуст"));
    }

    @Test
    void successfulBuildWithUnparsableLogProducesNothing() {
        assertTrue(BuildLog.parse("Build succeeded\n", 0, false).isEmpty());
    }

    // ---- Marshal --------------------------------------------------------------------------------

    @Test
    void marshalElementTypes() {
        assertEquals(Types.I8, Marshal.elementTypeOf(new byte[0]));
        assertEquals(Types.U8, Marshal.elementTypeOf(new boolean[0]));
        assertEquals(Types.I16, Marshal.elementTypeOf(new short[0]));
        assertEquals(Types.U16, Marshal.elementTypeOf(new char[0]));
        assertEquals(Types.I32, Marshal.elementTypeOf(new int[0]));
        assertEquals(Types.I64, Marshal.elementTypeOf(new long[0]));
        assertEquals(Types.F32, Marshal.elementTypeOf(new float[0]));
        assertEquals(Types.F64, Marshal.elementTypeOf(new double[0]));
        assertEquals(-1, Marshal.elementTypeOf("x"));
        assertEquals(-1, Marshal.elementTypeOf(null));
        assertEquals(-1, Marshal.elementTypeOf(new int[0][0]));
        assertEquals(8, Marshal.elementSize(Types.F64));
        assertEquals(1, Marshal.elementSize(Types.U8));
    }

    @Test
    void marshalAcceptsMatchesTheInterpreterTable() {
        assertTrue(Marshal.accepts(Types.I8, new byte[0]));
        assertFalse(Marshal.accepts(Types.I8, new boolean[0]));
        assertTrue(Marshal.accepts(Types.U8, new byte[0]));
        assertTrue(Marshal.accepts(Types.U8, new boolean[0]));
        assertTrue(Marshal.accepts(Types.U16, new char[0]));
        assertTrue(Marshal.accepts(Types.U16, new short[0]));
        assertFalse(Marshal.accepts(Types.I16, new char[0]));
        assertTrue(Marshal.accepts(Types.U32, new int[0]));
        assertFalse(Marshal.accepts(Types.I32, new float[0]));
        assertTrue(Marshal.accepts(Types.U64, new long[0]));
        assertTrue(Marshal.accepts(Types.F32, new float[0]));
        assertFalse(Marshal.accepts(Types.F32, new double[0]));
        assertFalse(Marshal.accepts(Types.F64, null));
        assertFalse(Marshal.accepts(Types.BOOL, new boolean[0]));
    }

    @Test
    void marshalBufferCompatibility() {
        assertTrue(Marshal.compatible(Types.I32, Types.I32));
        assertTrue(Marshal.compatible(Types.U32, Types.I32));
        assertTrue(Marshal.compatible(Types.F32, Types.F32));
        assertFalse(Marshal.compatible(Types.F32, Types.I32), "одинаковый размер, но разная природа");
        assertFalse(Marshal.compatible(Types.I32, Types.I64));
        assertTrue(Marshal.compatible(Types.I8, Types.U8));
    }

    @Test
    void marshalScalars() {
        assertTrue(Marshal.scalarMatches(Types.I32, 5));
        assertTrue(Marshal.scalarMatches(Types.U32, 5));
        assertFalse(Marshal.scalarMatches(Types.I32, 5L));
        assertTrue(Marshal.scalarMatches(Types.I64, 5L));
        assertTrue(Marshal.scalarMatches(Types.F32, 1f));
        assertFalse(Marshal.scalarMatches(Types.F32, 1.0));
        assertTrue(Marshal.scalarMatches(Types.F64, 1.0));
        assertFalse(Marshal.scalarMatches(Types.I32, null));
        assertEquals(-1L, Marshal.scalarBits(-1));
        assertEquals(Float.floatToRawIntBits(1.5f), Marshal.scalarBits(1.5f));
        assertEquals(Double.doubleToRawLongBits(-2.5), Marshal.scalarBits(-2.5));
        assertEquals(Long.MIN_VALUE, Marshal.scalarBits(Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> Marshal.scalarBits("x"));
        assertEquals(4, Marshal.scalarSize(Types.F32));
        assertEquals(8, Marshal.scalarSize(Types.I64));
        assertEquals("int[]", Marshal.describe(new int[0]));
        assertEquals("Integer", Marshal.describe(1));
        assertEquals("null", Marshal.describe(null));
    }

    @Test
    void marshalRoundTripsEveryArrayKind() {
        Object[] originals = {
                new byte[]{-128, -1, 0, 127}, new boolean[]{true, false, true}, new short[]{Short.MIN_VALUE, -1, 7},
                new char[]{0, 'a', 65535}, new int[]{Integer.MIN_VALUE, 0, 42}, new long[]{Long.MIN_VALUE, -1, 5},
                new float[]{-0f, Float.NaN, 1.5f}, new double[]{Double.MAX_VALUE, -0d, 0.1}};
        Object[] fresh = {new byte[4], new boolean[3], new short[3], new char[3], new int[3], new long[3],
                new float[3], new double[3]};
        try (Arena arena = Arena.ofConfined()) {
            for (int i = 0; i < originals.length; i++) {
                MemorySegment seg = Marshal.toNative(arena, originals[i]);
                Marshal.fromNative(seg, fresh[i]);
                assertTrue(same(originals[i], fresh[i]), "вид " + i);
            }
        }
    }

    @Test
    void marshalLaysOutLittleEndianDensely() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = Marshal.toNative(arena, new short[]{0x0102, 0x0304});
            assertEquals(4, Marshal.byteSize(new short[2]));
            assertEquals(0x02, seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 0));
            assertEquals(0x01, seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 1));
            assertEquals(0x04, seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 2));
            MemorySegment bools = Marshal.toNative(arena, new boolean[]{true, false, true});
            assertEquals(1, bools.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 0));
            assertEquals(0, bools.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 1));
            assertEquals(3, Marshal.byteSize(new boolean[3]));
        }
    }

    /** Поэлементное равенство через {@code equals} обёрток: различает {@code -0.0} и {@code 0.0}, равны ли NaN. */
    private static boolean same(Object a, Object b) {
        int n = java.lang.reflect.Array.getLength(a);
        if (n != java.lang.reflect.Array.getLength(b)) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (!java.lang.reflect.Array.get(a, i).equals(java.lang.reflect.Array.get(b, i))) {
                return false;
            }
        }
        return true;
    }

    // ---- события, статистика, ошибки ------------------------------------------------------------

    @Test
    void eventListenerNoneAcceptsAnything() {
        EventListener.NONE.onEvent(new GpuEvent(GpuEvent.Kind.WARNING, "x", 1, 2, "y"));
        GpuEvent e = new GpuEvent(GpuEvent.Kind.UPLOAD, "buf", 5, 64, "");
        assertEquals(64, e.bytes());
        assertEquals(GpuEvent.Kind.UPLOAD, e.kind());
        assertEquals(8, GpuEvent.Kind.values().length);
    }

    @Test
    void statsRecords() {
        LaunchStats s = new LaunchStats(1, 2, 3, 4, 5);
        assertEquals(2, s.kernelNanos());
        assertEquals(new CacheStats(1, 2, 3), new CacheStats(1, 2, 3));
    }

    @Test
    void gpuExceptionCarriesDiagnostics() {
        Diagnostic a = new Diagnostic("RT-X", Diagnostic.Severity.ERROR, -1, -1, "первая");
        Diagnostic b = new Diagnostic("RT-Y", Diagnostic.Severity.ERROR, -1, -1, "вторая");
        GpuException e = new GpuException(List.of(a, b));
        assertEquals(List.of(a, b), e.diagnostics());
        assertEquals("RT-X: первая\nRT-Y: вторая", e.getMessage());
        assertEquals("ошибка рантайма", new GpuException(List.of()).getMessage());
    }

    // ---- слой доступа ---------------------------------------------------------------------------

    @Test
    void deviceApiExceptionCarriesStatusAndFunction() {
        DeviceApiException e = new DeviceApiException(-5, "clFinish", "сбой");
        assertEquals(-5, e.status());
        assertEquals("clFinish", e.function());
        assertTrue(e.getMessage().contains("clFinish") && e.getMessage().contains("-5"));
        assertTrue(new DeviceApi.BuildOutcome(0, "").ok());
        assertFalse(new DeviceApi.BuildOutcome(-11, "x").ok());
    }

    @Test
    void loadingTheSystemLibraryNeverThrows() {
        DeviceApi api = OpenClApi.load(); // null, если драйвера нет
        if (api != null) {
            long[] platforms = api.platforms();
            assertTrue(platforms.length >= 0);
            api.close();
            api.close();
            assertTrue(((OpenClApi) api).isClosed());
        }
    }
}
