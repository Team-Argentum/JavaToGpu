package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Достижимость функций и проверка поддержанного подмножества. */
class SupportTest {

    private static IrModule chain() {
        IrBuilder b = new IrBuilder();
        int f0 = b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.param(0))));
        b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32); // f1 не вызывается
        b.endFunction(b.block(b.returnStmt(b.param(0))));
        int f2 = b.beginFunction(IrModule.DEVICE, Types.I32, Types.I32);
        b.endFunction(b.block(b.returnStmt(b.call(f0, b.param(0)))));
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.call(f2, b.constI32(1)))));
        return b.build();
    }

    @Test
    void reachableFollowsCallsTransitively() {
        boolean[] r = Support.reachable(chain(), 3);
        assertArrayEquals(new boolean[]{true, false, true, true}, r);
    }

    @Test
    void reachableFromDeviceFunctionStartsThere() {
        assertArrayEquals(new boolean[]{true, false, true, false}, Support.reachable(chain(), 2));
    }

    @Test
    void checkAcceptsBaselineModules() {
        IrModule m = HandModules.control();
        assertTrue(Support.check(m, 0, Support.reachable(m, 0)).isEmpty());
        IrModule s = Fixtures.computeSine();
        assertTrue(Support.check(s, 0, Support.reachable(s, 0)).isEmpty());
    }

    @Test
    void checkReportsEveryUnsupportedNodeWithItsLocation() {
        IrModule m = Fixtures.rich();
        int kernel = m.functionCount() - 1;
        List<Diagnostic> d = Support.check(m, kernel, Support.reachable(m, kernel));
        assertTrue(d.size() > 3);
        for (Diagnostic x : d) {
            assertEquals("CG-U01", x.code());
            assertTrue(x.isError());
            assertTrue(x.function() >= 0);
        }
        assertTrue(d.stream().anyMatch(x -> x.node() >= 0), "есть привязка к узлу");
        assertTrue(d.stream().anyMatch(x -> x.node() < 0), "и к сигнатуре функции (pointer-параметр)");
    }
}
