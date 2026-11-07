package com.sixik.gpu.ir;

/**
 * Модуль IR: неизменяемый набор плоских таблиц (REWORK_ARCHITECTURE.md, разд. 5-6).
 *
 * <p>Все данные - массивы примитивов (SoA), ссылки - целочисленные индексы:</p>
 * <ul>
 *   <li>{@code types}: колонки {@code kind, a, b, c, d}; строки {@code 0..15} - предустановленные скаляры;</li>
 *   <li>{@code nodes}: колонки {@code op, type, a, b, c}; id узла = индекс строки, операнды всегда с меньшим id;</li>
 *   <li>{@code lists}: общий пул {@code int} для списков переменной длины (аргументы, дети блоков, поля);</li>
 *   <li>{@code consts}: 64-битные константы (сырые биты); константы до 32 бит лежат прямо в колонке {@code a};</li>
 *   <li>{@code strings}: семантические строки (имена требуемых возможностей); имена и позиции - в {@link DebugInfo};</li>
 *   <li>{@code funcs}: колонки функций (см. константы {@code F_*}).</li>
 * </ul>
 *
 * <p>Класс не содержит логики кроме доступа к данным; семантику проверяет {@link Validator}, формат пишет
 * {@link IrBinary}. Объект создаётся только через {@link IrBuilder} либо {@link IrBinary#read(byte[])}.</p>
 */
public final class IrModule {

    // ---- колонки таблицы типов -------------------------------------------------------------------
    static final int T_KIND = 0;
    static final int T_A = 1;
    static final int T_B = 2;
    static final int T_C = 3;
    static final int T_D = 4;
    static final int T_COLS = 5;

    // ---- колонки таблицы узлов -------------------------------------------------------------------
    static final int N_OP = 0;
    static final int N_TYPE = 1;
    static final int N_A = 2;
    static final int N_B = 3;
    static final int N_C = 4;
    static final int N_COLS = 5;

    // ---- колонки таблицы функций -----------------------------------------------------------------
    static final int F_KIND = 0;
    static final int F_RET = 1;
    static final int F_PARAMS_OFF = 2;
    static final int F_PARAM_COUNT = 3;
    static final int F_LOCALS_OFF = 4;
    static final int F_LOCAL_COUNT = 5;
    static final int F_BODY = 6;
    static final int F_WG_X = 7;
    static final int F_WG_Y = 8;
    static final int F_WG_Z = 9;
    static final int F_INLINE = 10;
    static final int F_OPT = 11;
    static final int F_REQUIRED_OFF = 12;
    static final int F_REQUIRED_COUNT = 13;
    static final int F_BODY_KIND = 14;
    static final int F_FALLBACK = 15;
    static final int F_TUNE_OFF = 16;
    static final int F_TUNE_COUNT = 17;
    static final int F_COLS = 18;

    /** Вид функции: точка входа (kernel). */
    public static final int KERNEL = 0;
    /** Вид функции: вспомогательная device-функция. */
    public static final int DEVICE = 1;

    /** Политика inline: по умолчанию. */
    public static final int INLINE_DEFAULT = 0;
    /** Политика inline: всегда. */
    public static final int INLINE_ALWAYS = 1;
    /** Политика inline: никогда. */
    public static final int INLINE_NEVER = 2;

    /** Тип тела функции: IR. */
    public static final int BODY_IR = 0;
    /** Тип тела функции: нативное (зарезервировано под {@code @GpuNative}, не v1). */
    public static final int BODY_NATIVE = 1;

    private final int builtinsVersion;
    private final int[][] types;
    private final String[] strings;
    private final long[] consts;
    private final int[][] nodes;
    private final int[] lists;
    private final int[][] funcs;
    private final DebugInfo debug;

    /**
     * Контейнер колонок для сборки модуля. Массивы не копируются.
     *
     * @param builtinsVersion версия таблицы builtins, по которой собран модуль
     * @param types           колонки типов ({@link #T_COLS} штук, включая 16 предустановленных строк)
     * @param strings         семантические строки
     * @param consts          64-битные константы
     * @param nodes           колонки узлов ({@link #N_COLS} штук)
     * @param lists           пул списков
     * @param funcs           колонки функций ({@link #F_COLS} штук)
     * @param debug           отладочная секция (не {@code null}; {@link DebugInfo#EMPTY} если нет)
     */
    record Parts(int builtinsVersion, int[][] types, String[] strings, long[] consts, int[][] nodes,
                 int[] lists, int[][] funcs, DebugInfo debug) {
    }

    IrModule(Parts p) {
        this.builtinsVersion = p.builtinsVersion();
        this.types = p.types();
        this.strings = p.strings();
        this.consts = p.consts();
        this.nodes = p.nodes();
        this.lists = p.lists();
        this.funcs = p.funcs();
        this.debug = p.debug();
    }

    /**
     * Создаёт колонки таблицы типов с 16 предустановленными строками (скаляры и зарезервированные id).
     *
     * @param capacity ожидаемое число строк
     * @return {@code T_COLS} колонок с заполненными первыми 16 строками и длиной {@code max(capacity, 16)}
     */
    static int[][] presetTypeColumns(int capacity) {
        int n = Math.max(capacity, Types.FIRST_DYNAMIC);
        int[][] cols = new int[T_COLS][n];
        for (int t = 0; t < Types.FIRST_DYNAMIC; t++) {
            cols[T_KIND][t] = t <= Types.F64 ? Types.KIND_SCALAR : Types.KIND_RESERVED;
        }
        return cols;
    }

    // ---- общее -----------------------------------------------------------------------------------

    /**
     * @return версия таблицы builtins, по которой собран модуль
     */
    public int builtinsVersion() {
        return builtinsVersion;
    }

    /**
     * @return отладочная секция (не {@code null})
     */
    public DebugInfo debug() {
        return debug;
    }

    // ---- типы ------------------------------------------------------------------------------------

    /** @return число строк таблицы типов (включая 16 предустановленных) */
    public int typeCount() {
        return types[T_KIND].length;
    }

    /** @param t id типа @return вид типа ({@code Types.KIND_*}) */
    public int typeKind(int t) {
        return types[T_KIND][t];
    }

    /** @param t id типа @return колонка {@code a} типа */
    public int typeA(int t) {
        return types[T_A][t];
    }

    /** @param t id типа @return колонка {@code b} типа */
    public int typeB(int t) {
        return types[T_B][t];
    }

    /** @param t id типа @return колонка {@code c} типа */
    public int typeC(int t) {
        return types[T_C][t];
    }

    /** @param t id типа @return колонка {@code d} типа */
    public int typeD(int t) {
        return types[T_D][t];
    }

    /**
     * @param t id struct-типа
     * @param i индекс поля
     * @return id типа поля
     */
    public int structFieldType(int t, int i) {
        return lists[types[T_B][t] + i];
    }

    /**
     * @param t id struct-типа
     * @return число полей
     */
    public int structFieldCount(int t) {
        return types[T_C][t];
    }

    // ---- строки и константы ----------------------------------------------------------------------

    /** @return число семантических строк */
    public int stringCount() {
        return strings.length;
    }

    /** @param i id строки @return строка */
    public String string(int i) {
        return strings[i];
    }

    /** @return число 64-битных констант */
    public int constCount() {
        return consts.length;
    }

    /** @param i индекс константы @return сырые биты константы */
    public long constant(int i) {
        return consts[i];
    }

    // ---- узлы ------------------------------------------------------------------------------------

    /** @return число узлов */
    public int nodeCount() {
        return nodes[N_OP].length;
    }

    /** @param n id узла @return опкод ({@link Op}) */
    public int op(int n) {
        return nodes[N_OP][n];
    }

    /** @param n id узла @return id типа результата */
    public int type(int n) {
        return nodes[N_TYPE][n];
    }

    /** @param n id узла @return колонка {@code a} */
    public int a(int n) {
        return nodes[N_A][n];
    }

    /** @param n id узла @return колонка {@code b} */
    public int b(int n) {
        return nodes[N_B][n];
    }

    /** @param n id узла @return колонка {@code c} */
    public int c(int n) {
        return nodes[N_C][n];
    }

    // ---- списки ----------------------------------------------------------------------------------

    /** @return длина пула списков */
    public int listSize() {
        return lists.length;
    }

    /** @param i индекс в пуле @return значение */
    public int list(int i) {
        return lists[i];
    }

    // ---- функции ---------------------------------------------------------------------------------

    /** @return число функций */
    public int functionCount() {
        return funcs[F_KIND].length;
    }

    /** @param f индекс функции @return {@link #KERNEL} или {@link #DEVICE} */
    public int fKind(int f) {
        return funcs[F_KIND][f];
    }

    /** @param f индекс функции @return id типа результата */
    public int fRet(int f) {
        return funcs[F_RET][f];
    }

    /** @param f индекс функции @return число параметров */
    public int fParamCount(int f) {
        return funcs[F_PARAM_COUNT][f];
    }

    /**
     * @param f индекс функции
     * @param i индекс параметра
     * @return id типа параметра
     */
    public int fParamType(int f, int i) {
        return lists[funcs[F_PARAMS_OFF][f] + i];
    }

    /** @param f индекс функции @return число локалей */
    public int fLocalCount(int f) {
        return funcs[F_LOCAL_COUNT][f];
    }

    /**
     * @param f индекс функции
     * @param l индекс локали
     * @return id типа локали
     */
    public int fLocalType(int f, int l) {
        return lists[funcs[F_LOCALS_OFF][f] + l];
    }

    /** @param f индекс функции @return id корневого узла тела (BLOCK) либо {@code -1} для нативного тела */
    public int fBody(int f) {
        return funcs[F_BODY][f];
    }

    /**
     * @param f    индекс функции
     * @param axis 0, 1 или 2
     * @return обязательный размер work-group по оси ({@code reqd_work_group_size}) либо 0
     */
    public int fReqWorkGroup(int f, int axis) {
        return funcs[F_WG_X + axis][f];
    }

    /** @param f индекс функции @return политика inline ({@code INLINE_*}) */
    public int fInline(int f) {
        return funcs[F_INLINE][f];
    }

    /** @param f индекс функции @return уровень оптимизации 0..3 */
    public int fOpt(int f) {
        return funcs[F_OPT][f];
    }

    /** @param f индекс функции @return число объявленных требований ({@code required}) */
    public int fRequiredCount(int f) {
        return funcs[F_REQUIRED_COUNT][f];
    }

    /**
     * @param f индекс функции
     * @param i номер требования
     * @return id семантической строки с именем требования
     */
    public int fRequiredName(int f, int i) {
        return lists[funcs[F_REQUIRED_OFF][f] + i];
    }

    /** @param f индекс функции @return {@link #BODY_IR} или {@link #BODY_NATIVE} */
    public int fBodyKind(int f) {
        return funcs[F_BODY_KIND][f];
    }

    /** @param f индекс функции @return индекс fallback-варианта либо {@code -1} */
    public int fFallback(int f) {
        return funcs[F_FALLBACK][f];
    }

    /** @param f индекс функции @return число кандидатов подстройки (зарезервировано, всегда 0 в v1) */
    public int fTuneCount(int f) {
        return funcs[F_TUNE_COUNT][f];
    }

    // ---- доступ для сериализации (пакет) ---------------------------------------------------------

    int[][] typeColumns() {
        return types;
    }

    String[] stringTable() {
        return strings;
    }

    long[] constTable() {
        return consts;
    }

    int[][] nodeColumns() {
        return nodes;
    }

    int[] listPool() {
        return lists;
    }

    int[][] funcColumns() {
        return funcs;
    }
}
