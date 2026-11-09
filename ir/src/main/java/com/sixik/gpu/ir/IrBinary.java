package com.sixik.gpu.ir;

import com.sixik.gpu.ir.Diagnostic.Severity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Бинарный колоночный формат IR (REWORK_ARCHITECTURE.md, 5.9) - артефакт на диске и контракт Java-Rust.
 *
 * <p>Раскладка (все числа little-endian):</p>
 * <pre>
 * "GIR\0" | u16 format | u16 builtinsVersion | u32 flags
 * секция* : 4-байтовый тег | u32 длина | полезная нагрузка
 *   TYPE  rows, затем 5 колонок динамических типов (строки с id >= 16)
 *   STRS  count, затем (u32 длина, UTF-8) * count
 *   CNST  count, затем i64 * count
 *   NODE  count, затем 5 колонок
 *   LIST  count, затем i32 * count
 *   FUNC  count, затем 18 колонок
 *   DBUG  отладочная секция (имена, позиции)
 * </pre>
 *
 * <p>Секции идут в фиксированном порядке, поэтому один и тот же модуль всегда даёт одни и те же байты.
 * {@link #hash(IrModule)} считается от заголовка и всех секций <b>кроме</b> {@code DBUG}: правка имён и
 * позиций не меняет хэш и не инвалидирует кэш скомпилированных ядер.</p>
 *
 * <p>Чтение никогда не бросает исключений на некорректных данных: все проблемы возвращаются как
 * {@link Diagnostic} ({@code IR-S05} - несовместимая версия, {@code IR-S06} - повреждённый контейнер).</p>
 */
public final class IrBinary {

    /** Версия формата контейнера. */
    public static final int FORMAT = 1;

    private static final byte[] MAGIC = {'G', 'I', 'R', 0};
    private static final int TAG_TYPE = tag("TYPE");
    private static final int TAG_STRS = tag("STRS");
    private static final int TAG_CNST = tag("CNST");
    private static final int TAG_NODE = tag("NODE");
    private static final int TAG_LIST = tag("LIST");
    private static final int TAG_FUNC = tag("FUNC");
    private static final int TAG_DBUG = tag("DBUG");

    private IrBinary() {
    }

    private static int tag(String s) {
        return (s.charAt(0)) | (s.charAt(1) << 8) | (s.charAt(2) << 16) | (s.charAt(3) << 24);
    }

    // =============================================================================================
    // Запись
    // =============================================================================================

    /** Растущий буфер little-endian. */
    private static final class Sink {
        private byte[] buf = new byte[256];
        private int pos;

        private void ensure(int extra) {
            if (pos + extra > buf.length) {
                buf = java.util.Arrays.copyOf(buf, Math.max(buf.length * 2, pos + extra));
            }
        }

        void putByte(int v) {
            ensure(1);
            buf[pos++] = (byte) v;
        }

        void putShort(int v) {
            ensure(2);
            buf[pos++] = (byte) v;
            buf[pos++] = (byte) (v >>> 8);
        }

        void putInt(int v) {
            ensure(4);
            buf[pos++] = (byte) v;
            buf[pos++] = (byte) (v >>> 8);
            buf[pos++] = (byte) (v >>> 16);
            buf[pos++] = (byte) (v >>> 24);
        }

        void putLong(long v) {
            putInt((int) v);
            putInt((int) (v >>> 32));
        }

        void putBytes(byte[] b) {
            ensure(b.length);
            System.arraycopy(b, 0, buf, pos, b.length);
            pos += b.length;
        }

        void putColumn(int[] col, int from) {
            for (int i = from; i < col.length; i++) {
                putInt(col[i]);
            }
        }

        byte[] toBytes() {
            return java.util.Arrays.copyOf(buf, pos);
        }
    }

    private static void putSection(Sink out, int tag, Sink payload) {
        out.putInt(tag);
        out.putInt(payload.pos);
        out.putBytes(payload.toBytes());
    }

    private static void putStrings(Sink s, String[] strings) {
        s.putInt(strings.length);
        for (String str : strings) {
            byte[] b = str.getBytes(StandardCharsets.UTF_8);
            s.putInt(b.length);
            s.putBytes(b);
        }
    }

    /** Заголовок и все секции, влияющие на хэш (без DBUG). */
    private static byte[] semanticBytes(IrModule m) {
        Sink out = new Sink();
        out.putBytes(MAGIC);
        out.putShort(FORMAT);
        out.putShort(m.builtinsVersion());
        out.putInt(0);

        Sink type = new Sink();
        int[][] tc = m.typeColumns();
        int rows = tc[0].length - Types.FIRST_DYNAMIC;
        type.putInt(rows);
        for (int[] col : tc) {
            type.putColumn(col, Types.FIRST_DYNAMIC);
        }
        putSection(out, TAG_TYPE, type);

        Sink strs = new Sink();
        putStrings(strs, m.stringTable());
        putSection(out, TAG_STRS, strs);

        Sink cnst = new Sink();
        long[] consts = m.constTable();
        cnst.putInt(consts.length);
        for (long c : consts) {
            cnst.putLong(c);
        }
        putSection(out, TAG_CNST, cnst);

        Sink node = new Sink();
        int[][] nc = m.nodeColumns();
        node.putInt(nc[0].length);
        for (int[] col : nc) {
            node.putColumn(col, 0);
        }
        putSection(out, TAG_NODE, node);

        Sink list = new Sink();
        int[] pool = m.listPool();
        list.putInt(pool.length);
        list.putColumn(pool, 0);
        putSection(out, TAG_LIST, list);

        Sink func = new Sink();
        int[][] fc = m.funcColumns();
        func.putInt(fc[0].length);
        for (int[] col : fc) {
            func.putColumn(col, 0);
        }
        putSection(out, TAG_FUNC, func);
        return out.toBytes();
    }

    private static void putCounted(Sink s, int[] col, int from) {
        s.putInt(Math.max(0, col.length - from));
        s.putColumn(col, from);
    }

    private static byte[] debugSection(IrModule m) {
        DebugInfo d = m.debug();
        Sink payload = new Sink();
        putStrings(payload, d.strings());
        putCounted(payload, d.funcNameColumn(), 0);
        putCounted(payload, d.paramNameOffColumn(), 0);
        putCounted(payload, d.localNameOffColumn(), 0);
        putCounted(payload, d.typeNameColumn(), Types.FIRST_DYNAMIC);
        putCounted(payload, d.typeFieldsOffColumn(), Types.FIRST_DYNAMIC);
        putCounted(payload, d.poolColumn(), 0);
        putCounted(payload, d.nodeLineColumn(), 0);
        putCounted(payload, d.nodeColColumn(), 0);
        Sink out = new Sink();
        putSection(out, TAG_DBUG, payload);
        return out.toBytes();
    }

    /**
     * Сериализует модуль целиком (семантические секции + отладочная).
     *
     * @param m модуль
     * @return байты контейнера
     */
    public static byte[] write(IrModule m) {
        byte[] sem = semanticBytes(m);
        byte[] dbg = debugSection(m);
        byte[] all = new byte[sem.length + dbg.length];
        System.arraycopy(sem, 0, all, 0, sem.length);
        System.arraycopy(dbg, 0, all, sem.length, dbg.length);
        return all;
    }

    /**
     * Хэш модуля (SHA-256) от заголовка и семантических секций; секция {@code DBUG} не участвует.
     * Служит ключом кэша скомпилированных ядер ({@code kernelId}).
     *
     * @param m модуль
     * @return 32 байта
     */
    public static byte[] hash(IrModule m) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(semanticBytes(m));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable in the JDK", e);
        }
    }

    /**
     * @param bytes произвольные байты
     * @return строка из строчных шестнадцатеричных цифр
     */
    public static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[2 * i] = digits[(bytes[i] >> 4) & 0xF];
            out[2 * i + 1] = digits[bytes[i] & 0xF];
        }
        return new String(out);
    }

    /**
     * Идентификатор ядра: шестнадцатеричная запись {@link #hash(IrModule)}.
     *
     * @param m модуль
     * @return 64 символа
     */
    public static String kernelId(IrModule m) {
        return hex(hash(m));
    }

    // =============================================================================================
    // Чтение
    // =============================================================================================

    /** Сигнал о повреждённых данных; ловится внутри {@link #read(byte[])} и превращается в диагностику. */
    private static final class Corrupt extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Corrupt(String message) {
            super(message, null, false, false);
        }
    }

    private static void need(ByteBuffer b, long bytes, String what) {
        if (bytes < 0 || bytes > b.remaining()) {
            throw new Corrupt(what + ": need " + bytes + " bytes, remaining " + b.remaining());
        }
    }

    private static int count(ByteBuffer b, int elemBytes, String what) {
        need(b, 4, what);
        int n = b.getInt();
        if (n < 0 || (long) n * elemBytes > b.remaining()) {
            throw new Corrupt(what + ": element count " + n + " does not fit in the data");
        }
        return n;
    }

    private static int[] readInts(ByteBuffer b, int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = b.getInt();
        }
        return a;
    }

    private static String[] readStrings(ByteBuffer b, String what) {
        int n = count(b, 4, what);
        String[] s = new String[n];
        for (int i = 0; i < n; i++) {
            need(b, 4, what);
            int len = b.getInt();
            need(b, len, what);
            byte[] raw = new byte[len];
            b.get(raw);
            s[i] = new String(raw, StandardCharsets.UTF_8);
        }
        return s;
    }

    /** Читает «counted»-колонку и дополняет её {@code lead} значениями {@code -1} спереди. */
    private static int[] readCounted(ByteBuffer b, String what, int lead) {
        int n = count(b, 4, what);
        int[] a = new int[lead + n];
        java.util.Arrays.fill(a, 0, lead, -1);
        for (int i = 0; i < n; i++) {
            a[lead + i] = b.getInt();
        }
        return a;
    }

    private static ByteBuffer section(ByteBuffer file, int expectedTag, String name) {
        need(file, 8, "section " + name);
        int tag = file.getInt();
        int len = file.getInt();
        if (tag != expectedTag) {
            throw new Corrupt("expected section " + name);
        }
        need(file, len, "section " + name);
        ByteBuffer slice = file.slice(file.position(), len).order(ByteOrder.LITTLE_ENDIAN);
        file.position(file.position() + len);
        return slice;
    }

    private static void drained(ByteBuffer b, String name) {
        if (b.hasRemaining()) {
            throw new Corrupt("in section " + name + " has extra data: " + b.remaining() + " bytes");
        }
    }

    /**
     * Читает модуль из байтов. Не бросает исключений: при ошибке возвращает диагностики.
     *
     * @param bytes содержимое файла
     * @return модуль либо диагностики {@code IR-S05} (версия) / {@code IR-S06} (повреждение)
     */
    public static Result<IrModule> read(byte[] bytes) {
        try {
            return Result.ok(decode(bytes));
        } catch (Corrupt e) {
            String code = e.getMessage() != null && e.getMessage().startsWith("version") ? "IR-S05" : "IR-S06";
            return Result.failed(List.of(new Diagnostic(code, Severity.ERROR, -1, -1, e.getMessage())));
        }
    }

    private static IrModule decode(byte[] bytes) {
        ByteBuffer file = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        need(file, 12, "header");
        for (byte mb : MAGIC) {
            if (file.get() != mb) {
                throw new Corrupt("invalid file signature (expected GIR)");
            }
        }
        int format = file.getShort() & 0xFFFF;
        int builtins = file.getShort() & 0xFFFF;
        file.getInt();
        if (format != FORMAT) {
            throw new Corrupt("version of the format " + format + " is not supported (expected " + FORMAT + ")");
        }
        if (builtins != Builtins.VERSION) {
            throw new Corrupt("version of the builtins table " + builtins + " does not match " + Builtins.VERSION);
        }

        ByteBuffer ty = section(file, TAG_TYPE, "TYPE");
        int dynamic = count(ty, 0, "TYPE");
        if ((long) dynamic * IrModule.T_COLS * 4 > ty.remaining()) {
            throw new Corrupt("TYPE: string count " + dynamic + " does not fit in the data");
        }
        int[][] types = IrModule.presetTypeColumns(Types.FIRST_DYNAMIC + dynamic);
        for (int c = 0; c < IrModule.T_COLS; c++) {
            for (int i = 0; i < dynamic; i++) {
                types[c][Types.FIRST_DYNAMIC + i] = ty.getInt();
            }
        }
        drained(ty, "TYPE");

        ByteBuffer st = section(file, TAG_STRS, "STRS");
        String[] strings = readStrings(st, "STRS");
        drained(st, "STRS");

        ByteBuffer cn = section(file, TAG_CNST, "CNST");
        int cc = count(cn, 8, "CNST");
        long[] consts = new long[cc];
        for (int i = 0; i < cc; i++) {
            consts[i] = cn.getLong();
        }
        drained(cn, "CNST");

        ByteBuffer nd = section(file, TAG_NODE, "NODE");
        int nn = count(nd, 0, "NODE");
        if ((long) nn * IrModule.N_COLS * 4 > nd.remaining()) {
            throw new Corrupt("NODE: node count " + nn + " does not fit in the data");
        }
        int[][] nodes = new int[IrModule.N_COLS][];
        for (int c = 0; c < IrModule.N_COLS; c++) {
            nodes[c] = readInts(nd, nn);
        }
        drained(nd, "NODE");

        ByteBuffer li = section(file, TAG_LIST, "LIST");
        int ln = count(li, 4, "LIST");
        int[] lists = readInts(li, ln);
        drained(li, "LIST");

        ByteBuffer fu = section(file, TAG_FUNC, "FUNC");
        int fn = count(fu, 0, "FUNC");
        if ((long) fn * IrModule.F_COLS * 4 > fu.remaining()) {
            throw new Corrupt("FUNC: function count " + fn + " does not fit in the data");
        }
        int[][] funcs = new int[IrModule.F_COLS][];
        for (int c = 0; c < IrModule.F_COLS; c++) {
            funcs[c] = readInts(fu, fn);
        }
        drained(fu, "FUNC");

        ByteBuffer db = section(file, TAG_DBUG, "DBUG");
        String[] dstr = readStrings(db, "DBUG");
        int[] funcName = readCounted(db, "DBUG.funcName", 0);
        int[] paramOff = readCounted(db, "DBUG.paramNameOff", 0);
        int[] localOff = readCounted(db, "DBUG.localNameOff", 0);
        int[] typeName = readCounted(db, "DBUG.typeName", Types.FIRST_DYNAMIC);
        int[] typeFields = readCounted(db, "DBUG.typeFieldsOff", Types.FIRST_DYNAMIC);
        int[] pool = readCounted(db, "DBUG.pool", 0);
        int[] line = readCounted(db, "DBUG.nodeLine", 0);
        int[] col = readCounted(db, "DBUG.nodeCol", 0);
        drained(db, "DBUG");
        if (file.hasRemaining()) {
            throw new Corrupt("extra data after the last section: " + file.remaining() + " bytes");
        }
        DebugInfo debug = new DebugInfo(dstr, funcName, paramOff, localOff, typeName, typeFields, pool, line, col);
        return new IrModule(new IrModule.Parts(builtins, types, strings, consts, nodes, lists, funcs, debug));
    }
}
