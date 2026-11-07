package com.sixik.gpu.ir;

/** Доступ тестов модуля {@code ir} к закрытым для пакета конструкторам (для негативных тестов). */
final class TestAccess {

    private TestAccess() {
    }

    /** Копия модуля с другой версией таблицы builtins (для проверки {@code IR-S05}). */
    static IrModule withBuiltinsVersion(IrModule m, int version) {
        return new IrModule(new IrModule.Parts(version, m.typeColumns(), m.stringTable(), m.constTable(),
                m.nodeColumns(), m.listPool(), m.funcColumns(), m.debug()));
    }
}
