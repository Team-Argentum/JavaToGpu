package com.sixik.gpu.ir;

/**
 * Отладочная секция IR: имена и позиции в исходнике (REWORK_ARCHITECTURE.md, 5.9).
 *
 * <p>Секция <b>не входит в хэш</b> модуля: правка комментария, пробела или имени локали не должна
 * инвалидировать скомпилированное ядро. Все индексы - в собственную таблицу строк {@link #strings()},
 * не связанную с семантической таблицей строк модуля. Любая колонка может быть пустой (имён/позиций нет).</p>
 */
public final class DebugInfo {

    /** Пустая отладочная информация. */
    public static final DebugInfo EMPTY = new DebugInfo(
            new String[0], new int[0], new int[0], new int[0], new int[0], new int[0], new int[0],
            new int[0], new int[0]);

    private final String[] strings;
    private final int[] funcName;
    private final int[] paramNameOff;
    private final int[] localNameOff;
    private final int[] typeName;
    private final int[] typeFieldsOff;
    private final int[] pool;
    private final int[] nodeLine;
    private final int[] nodeCol;

    /**
     * Создаёт отладочную секцию. Массивы не копируются: владение переходит к объекту.
     *
     * @param strings       таблица строк отладки
     * @param funcName      по функции: id строки имени либо {@code -1}
     * @param paramNameOff  по функции: смещение в {@code pool} имён параметров либо {@code -1}
     * @param localNameOff  по функции: смещение в {@code pool} имён локалей либо {@code -1}
     * @param typeName      по id типа: id строки имени struct либо {@code -1}
     * @param typeFieldsOff по id типа: смещение в {@code pool} имён полей struct либо {@code -1}
     * @param pool          пул id строк
     * @param nodeLine      по узлу: строка исходника (1-based) либо {@code 0}
     * @param nodeCol       по узлу: столбец исходника (1-based) либо {@code 0}
     */
    DebugInfo(String[] strings, int[] funcName, int[] paramNameOff, int[] localNameOff, int[] typeName,
              int[] typeFieldsOff, int[] pool, int[] nodeLine, int[] nodeCol) {
        this.strings = strings;
        this.funcName = funcName;
        this.paramNameOff = paramNameOff;
        this.localNameOff = localNameOff;
        this.typeName = typeName;
        this.typeFieldsOff = typeFieldsOff;
        this.pool = pool;
        this.nodeLine = nodeLine;
        this.nodeCol = nodeCol;
    }

    String[] strings() {
        return strings;
    }

    int[] funcNameColumn() {
        return funcName;
    }

    int[] paramNameOffColumn() {
        return paramNameOff;
    }

    int[] localNameOffColumn() {
        return localNameOff;
    }

    int[] typeNameColumn() {
        return typeName;
    }

    int[] typeFieldsOffColumn() {
        return typeFieldsOff;
    }

    int[] poolColumn() {
        return pool;
    }

    int[] nodeLineColumn() {
        return nodeLine;
    }

    int[] nodeColColumn() {
        return nodeCol;
    }

    private String str(int id) {
        return id >= 0 && id < strings.length ? strings[id] : null;
    }

    private String pooled(int[] offs, int index, int k) {
        if (index < 0 || index >= offs.length || offs[index] < 0) {
            return null;
        }
        int at = offs[index] + k;
        return at >= 0 && at < pool.length ? str(pool[at]) : null;
    }

    /**
     * @param f индекс функции
     * @return имя функции либо {@code null}
     */
    public String functionName(int f) {
        return f >= 0 && f < funcName.length ? str(funcName[f]) : null;
    }

    /**
     * @param f индекс функции
     * @param i индекс параметра
     * @return имя параметра либо {@code null}
     */
    public String paramName(int f, int i) {
        return pooled(paramNameOff, f, i);
    }

    /**
     * @param f индекс функции
     * @param l индекс локали
     * @return имя локали либо {@code null}
     */
    public String localName(int f, int l) {
        return pooled(localNameOff, f, l);
    }

    /**
     * @param t id struct-типа
     * @return имя struct либо {@code null}
     */
    public String structName(int t) {
        return t >= 0 && t < typeName.length ? str(typeName[t]) : null;
    }

    /**
     * @param t id struct-типа
     * @param i индекс поля
     * @return имя поля либо {@code null}
     */
    public String fieldName(int t, int i) {
        return pooled(typeFieldsOff, t, i);
    }

    /**
     * @param n id узла
     * @return строка исходника (1-based) либо {@code 0}, если позиции нет
     */
    public int line(int n) {
        return n >= 0 && n < nodeLine.length ? nodeLine[n] : 0;
    }

    /**
     * @param n id узла
     * @return столбец исходника (1-based) либо {@code 0}, если позиции нет
     */
    public int col(int n) {
        return n >= 0 && n < nodeCol.length ? nodeCol[n] : 0;
    }
}
