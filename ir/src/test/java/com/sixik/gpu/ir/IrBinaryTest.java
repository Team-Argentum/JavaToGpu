package com.sixik.gpu.ir;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Бинарный формат: round-trip, детерминизм, хэш без debug, устойчивость к повреждённым данным. */
class IrBinaryTest {

    private static IrModule readOk(byte[] bytes) {
        Result<IrModule> r = IrBinary.read(bytes);
        assertTrue(r.isOk(), () -> r.diagnostics().toString());
        return r.value();
    }

    @Test
    void roundTripPreservesBytes() {
        for (IrModule m : new IrModule[]{Fixtures.computeSine(), Fixtures.rich()}) {
            byte[] first = IrBinary.write(m);
            IrModule back = readOk(first);
            assertArrayEquals(first, IrBinary.write(back), "write(read(write(m))) must equal write(m)");
            assertArrayEquals(IrBinary.hash(m), IrBinary.hash(back));
            assertEquals(m.nodeCount(), back.nodeCount());
            assertEquals(m.functionCount(), back.functionCount());
            assertEquals(m.typeCount(), back.typeCount());
        }
    }

    @Test
    void moduleReadFromBytesStillValidates() {
        IrModule back = readOk(IrBinary.write(Fixtures.rich()));
        Validator.Report r = Validator.validate(back);
        assertTrue(r.diagnostics().isEmpty(), r.diagnostics().toString());
    }

    @Test
    void sameBuilderCallsGiveSameBytesAndHash() {
        IrModule a = Fixtures.rich();
        IrModule b = Fixtures.rich();
        assertArrayEquals(IrBinary.write(a), IrBinary.write(b));
        assertArrayEquals(IrBinary.hash(a), IrBinary.hash(b));
    }

    @Test
    void headerLayout() {
        byte[] bytes = IrBinary.write(Fixtures.computeSine());
        assertEquals('G', bytes[0]);
        assertEquals('I', bytes[1]);
        assertEquals('R', bytes[2]);
        assertEquals(0, bytes[3]);
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(IrBinary.FORMAT, bb.getShort(4));
        assertEquals(Builtins.VERSION, bb.getShort(6));
        assertEquals(0, bb.getInt(8), "flags are reserved");
    }

    @Test
    void hashIgnoresDebugSection() {
        IrModule withDebug = Fixtures.computeSine();
        IrModule noDebug = Fixtures.computeSineWithoutDebug();
        assertArrayEquals(IrBinary.hash(withDebug), IrBinary.hash(noDebug),
                "names and positions must not affect the cache hash");
        assertEquals(IrBinary.kernelId(withDebug), IrBinary.kernelId(noDebug));
        assertNotEquals(IrBinary.write(withDebug).length, IrBinary.write(noDebug).length,
                "while the file itself has a debug section");
    }

    @Test
    void hashChangesWhenSemanticsChange() {
        byte[] base = IrBinary.hash(Fixtures.computeSine());

        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        b.requireWorkGroup(256, 1, 1);
        int id = b.declareLocal(Types.I32);
        int n1 = b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0));
        int n2 = b.localDecl(id, n1);
        int load = b.load(b.elemPtr(b.param(0), b.localGet(id)));
        int value = b.binary(Op.ADD, b.intrinsic(Fixtures.SIN, load), b.constF32(3.0f));   // 3.0f вместо 2.0f
        int n13 = b.store(b.elemPtr(b.param(1), b.localGet(id)), value);
        b.endFunction(b.block(n2, n13));
        assertFalse(Arrays.equals(base, IrBinary.hash(b.build())), "another constant - another hash");
    }

    @Test
    void hashChangesWhenFunctionAttributeChanges() {
        IrBuilder b1 = new IrBuilder();
        b1.beginFunction(IrModule.KERNEL, Types.VOID);
        b1.requireWorkGroup(64, 1, 1);
        b1.endFunction(b1.block());
        IrBuilder b2 = new IrBuilder();
        b2.beginFunction(IrModule.KERNEL, Types.VOID);
        b2.requireWorkGroup(128, 1, 1);
        b2.endFunction(b2.block());
        assertFalse(Arrays.equals(IrBinary.hash(b1.build()), IrBinary.hash(b2.build())));
    }

    @Test
    void kernelIdIs64LowercaseHexCharacters() {
        String id = IrBinary.kernelId(Fixtures.computeSine());
        assertEquals(64, id.length());
        assertTrue(id.matches("[0-9a-f]{64}"));
    }

    @Test
    void debugInformationSurvivesRoundTrip() {
        IrModule back = readOk(IrBinary.write(Fixtures.computeSine()));
        DebugInfo d = back.debug();
        assertEquals("computeSine", d.functionName(0));
        assertEquals("input", d.paramName(0, 0));
        assertEquals("output", d.paramName(0, 1));
        assertEquals("id", d.localName(0, 0));
        assertEquals(3, d.line(13));
        assertEquals(5, d.col(13));
        assertEquals(0, d.line(2), "a node without a position gets 0");
    }

    @Test
    void structNamesSurviveRoundTrip() {
        IrModule original = Fixtures.rich();
        IrModule back = readOk(IrBinary.write(original));
        int structType = -1;
        for (int t = Types.FIRST_DYNAMIC; t < back.typeCount(); t++) {
            if (back.typeKind(t) == Types.KIND_STRUCT) {
                structType = t;
            }
        }
        assertEquals("GamePos", back.debug().structName(structType));
        assertEquals("v", back.debug().fieldName(structType, 2));
    }

    @Test
    void emptyDebugInformationAnswersNull() {
        IrModule back = readOk(IrBinary.write(Fixtures.computeSineWithoutDebug()));
        assertNull(back.debug().functionName(0));
        assertNull(back.debug().paramName(0, 0));
        assertNull(back.debug().localName(0, 0));
        assertEquals(0, back.debug().line(5));
    }

    @Test
    void functionAttributesSurviveRoundTrip() {
        IrModule back = readOk(IrBinary.write(Fixtures.rich()));
        int kernel = 2;
        assertEquals(IrModule.KERNEL, back.fKind(kernel));
        assertEquals(64, back.fReqWorkGroup(kernel, 0));
        assertEquals(IrModule.INLINE_ALWAYS, back.fInline(kernel));
        assertEquals(1, back.fRequiredCount(kernel));
        assertEquals("SIMD", back.string(back.fRequiredName(kernel, 0)));
    }

    // ----------------------------------------------------------- повреждённые данные -----------

    @Test
    void everyTruncationIsRejectedWithoutException() {
        byte[] bytes = IrBinary.write(Fixtures.rich());
        for (int len = 0; len < bytes.length; len++) {
            Result<IrModule> r = IrBinary.read(Arrays.copyOf(bytes, len));
            assertFalse(r.isOk(), "a file truncated to " + len + " bytes must not be readable");
            assertFalse(r.diagnostics().isEmpty());
        }
    }

    @Test
    void wrongMagicIsRejected() {
        byte[] bytes = IrBinary.write(Fixtures.computeSine());
        bytes[0] = 'X';
        Result<IrModule> r = IrBinary.read(bytes);
        assertFalse(r.isOk());
        assertEquals("IR-S06", r.diagnostics().get(0).code());
    }

    @Test
    void unsupportedFormatVersionGivesS05() {
        byte[] bytes = IrBinary.write(Fixtures.computeSine());
        bytes[4] = 99;
        Result<IrModule> r = IrBinary.read(bytes);
        assertFalse(r.isOk());
        assertEquals("IR-S05", r.diagnostics().get(0).code());
    }

    @Test
    void differentBuiltinsTableVersionGivesS05() {
        byte[] bytes = IrBinary.write(Fixtures.computeSine());
        bytes[6] = (byte) (Builtins.VERSION + 7);
        Result<IrModule> r = IrBinary.read(bytes);
        assertFalse(r.isOk());
        assertEquals("IR-S05", r.diagnostics().get(0).code());
    }

    @Test
    void trailingBytesAreRejected() {
        byte[] bytes = IrBinary.write(Fixtures.computeSine());
        byte[] longer = Arrays.copyOf(bytes, bytes.length + 3);
        Result<IrModule> r = IrBinary.read(longer);
        assertFalse(r.isOk());
        assertEquals("IR-S06", r.diagnostics().get(0).code());
    }

    @Test
    void hugeCountIsRejectedWithoutAllocation() {
        ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(new byte[]{'G', 'I', 'R', 0});
        bb.putShort((short) IrBinary.FORMAT);
        bb.putShort((short) Builtins.VERSION);
        bb.putInt(0);
        bb.put("TYPE".getBytes());
        bb.putInt(4);
        bb.putInt(0x7FFFFFFF);          // число строк таблицы типов
        Result<IrModule> r = IrBinary.read(Arrays.copyOf(bb.array(), bb.position()));
        assertFalse(r.isOk());
        assertNotNull(r.diagnostics());
    }

    /** Смещения и длины полезной нагрузки секций TYPE, STRS, CNST, NODE, LIST, FUNC (DBUG не трогаем). */
    private static int[][] sectionPayloads(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        int[][] out = new int[6][];
        for (int i = 0; i < 6; i++) {
            int len = bb.getInt(pos + 4);
            out[i] = new int[]{pos + 8, len};
            pos += 8 + len;
        }
        return out;
    }

    @Test
    void semanticCorruptionOfColumnsNeverThrowsInValidator() {
        byte[] original = IrBinary.write(Fixtures.rich());
        int[][] sections = sectionPayloads(original);
        int[] pool = {-1, 0, 1, 2, 3, 4, 5, 8, 11, 15, 16, 17, 18, 20, 27, 40, 100, 1000, Integer.MAX_VALUE, Integer.MIN_VALUE};
        Random rnd = new Random(7);
        int decoded = 0;
        for (int i = 0; i < 30000; i++) {
            byte[] bytes = original.clone();
            int edits = 1 + rnd.nextInt(3);
            for (int e = 0; e < edits; e++) {
                int[] sec = sections[rnd.nextInt(6)];
                if (sec[1] < 8) {
                    continue;
                }
                // пропускаем первое слово секции (счётчик): ломаем только данные, а не структуру контейнера
                int words = (sec[1] - 4) / 4;
                if (words <= 0) {
                    continue;
                }
                int at = sec[0] + 4 + 4 * rnd.nextInt(words);
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(at, pool[rnd.nextInt(pool.length)]);
            }
            Result<IrModule> r = IrBinary.read(bytes);
            if (r.isOk()) {
                decoded++;
                Validator.Report report = Validator.validate(r.value());
                assertNotNull(report.diagnostics());
                assertNotNull(report.functions());
            }
        }
        assertTrue(decoded > 15000, "the semantic fuzz must reach the validator, reached it " + decoded + " times");
    }

    @Test
    void randomCorruptionNeverThrowsInReaderOrValidator() {
        byte[] original = IrBinary.write(Fixtures.rich());
        Random rnd = new Random(42);
        int decoded = 0;
        for (int i = 0; i < 4000; i++) {
            byte[] bytes = original.clone();
            int flips = 1 + rnd.nextInt(4);
            for (int k = 0; k < flips; k++) {
                bytes[rnd.nextInt(bytes.length)] = (byte) rnd.nextInt(256);
            }
            Result<IrModule> r = IrBinary.read(bytes);
            if (r.isOk()) {
                decoded++;
                // читаемый, но семантически испорченный модуль: валидатор обязан отвечать диагностиками, а не падать
                Validator.Report report = Validator.validate(r.value());
                assertNotNull(report.diagnostics());
                IrBinary.write(r.value());
                IrBinary.hash(r.value());
            }
        }
        assertTrue(decoded > 100, "the fuzz must reach the validator often enough, reached it " + decoded + " times");
    }
}
