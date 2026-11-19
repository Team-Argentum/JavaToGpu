package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Таблицы констант ({@code Op.CONST_TABLE}, решение 18.6-5): валидатор, двоичный формат, интерпретатор и свёртка
 * чтения по константному индексу в оптимизаторе.
 */
class ConstTableTest {

    private static final long[] TABLE = {7, -2, 11, 5, 0, -9, 3, 100};

    /** {@code a[i] = T[a[i] & 7] + T[3] * len(T)} над {@code int[] a}. */
    static IrModule module() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int id = b.declareLocal(Types.I32);
        int decl = b.localDecl(id, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int byData = b.load(b.elemPtr(b.constTable(Types.I32, TABLE),
                b.binary(Op.AND, b.load(b.elemPtr(b.param(0), b.localGet(id))), b.constI32(7))));
        int fixed = b.binary(Op.MUL, b.load(b.elemPtr(b.constTable(Types.I32, TABLE), b.constI32(3))),
                b.arrayLen(b.constTable(Types.I32, TABLE)));
        b.endFunction(b.block(decl, b.store(b.elemPtr(b.param(0), b.localGet(id)), b.binary(Op.ADD, byData, fixed))));
        return b.build();
    }

    private static int[] run(IrModule m) {
        assertFalse(Validator.validate(m).hasErrors(), Validator.validate(m).diagnostics().toString());
        int[] a = new int[16];
        for (int i = 0; i < a.length; i++) {
            a[i] = i * 3;
        }
        Result<Interpreter.Execution> r = Interpreter.run(m, 0, NdRange.of1D(a.length),
                Interpreter.Options.DEFAULT, new Object[]{a});
        assertTrue(r.isOk(), r.diagnostics().toString());
        return a;
    }

    private static int count(IrModule m, int op) {
        int c = 0;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == op) {
                c++;
            }
        }
        return c;
    }

    @Test
    void interpreterReadsTheTable() {
        int[] a = run(module());
        for (int i = 0; i < a.length; i++) {
            assertEquals((int) TABLE[(i * 3) & 7] + 5 * 8, a[i], "i=" + i);
        }
    }

    @Test
    void sameTableIsStoredOnce() {
        IrModule m = module();
        int first = -1;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == Op.CONST_TABLE) {
                assertTrue(first < 0 || m.a(n) == first, "the same table must reuse its constants");
                first = m.a(n);
            }
        }
        assertTrue(first >= 0);
    }

    @Test
    void binaryRoundTripKeepsTheTable() {
        IrModule m = module();
        Result<IrModule> back = IrBinary.read(IrBinary.write(m));
        assertTrue(back.isOk(), back.diagnostics().toString());
        assertArrayEquals(run(m), run(back.value()));
    }

    @Test
    void constantIndexAndLengthAreFolded() {
        IrModule m = module();
        int[] expected = run(m);
        for (int level = 1; level <= 2; level++) {
            OptimizeResult r = Optimizer.optimize(m, OptimizeOptions.DEFAULT.withLevel(level).withTimeBudget(null));
            assertTrue(r.failures().isEmpty(), r.failures().toString());
            IrModule out = r.module();
            assertArrayEquals(expected, run(out), "opt=" + level);
            assertEquals(1, count(out, Op.CONST_TABLE), "only the data-dependent read keeps the table, opt=" + level);
            assertEquals(0, count(out, Op.ARRAY_LEN), "opt=" + level);
        }
    }

    @Test
    void builderRejectsBadTables() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        assertThrows(RuntimeException.class, () -> b.constTable(Types.BOOL, new long[]{1}));
        assertThrows(RuntimeException.class, () -> b.constTable(Types.I32, new long[0]));
    }

    @Test
    void storeIntoATableIsRejected() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block(b.store(b.elemPtr(b.constTable(Types.F32, new long[]{Float.floatToIntBits(1f)}),
                b.constI32(0)), b.constF32(2f))));
        assertTrue(Validator.validate(b.build()).hasErrors(), "a __constant table is read-only");
    }
}
