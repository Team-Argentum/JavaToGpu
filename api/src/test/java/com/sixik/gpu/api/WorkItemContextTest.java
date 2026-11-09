package com.sixik.gpu.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Контекст work-item: формулы group/local id, оси вне workDim, проверки аргументов. */
class WorkItemContextTest {

    @Test
    void indicesAreDerivedFromGlobalId() {
        // NDRange 8x4, группа 4x2: work-item (5,3) лежит в группе (1,1) на позиции (1,1)
        var ctx = new WorkItemContext(2, new int[]{8, 4, 1}, new int[]{4, 2, 1});
        ctx.setGlobalId(5, 3, 0);
        assertEquals(5, ctx.globalId(0));
        assertEquals(3, ctx.globalId(1));
        assertEquals(1, ctx.groupId(0));
        assertEquals(1, ctx.groupId(1));
        assertEquals(1, ctx.localId(0));
        assertEquals(1, ctx.localId(1));
        assertEquals(8, ctx.globalSize(0));
        assertEquals(4, ctx.localSize(0));
        assertEquals(2, ctx.numGroups(0));
        assertEquals(2, ctx.numGroups(1));
        assertEquals(2, ctx.workDim());
        // тождество globalId = groupId * localSize + localId
        for (int d = 0; d < 2; d++) {
            assertEquals(ctx.globalId(d), ctx.groupId(d) * ctx.localSize(d) + ctx.localId(d));
        }
    }

    @Test
    void axesOutsideWorkDimFollowOpenClRules() {
        var ctx = WorkItemContext.of1D(100, 10);
        ctx.setGlobalId(42, 0, 0);
        assertEquals(0, ctx.globalId(1));
        assertEquals(0, ctx.localId(2));
        assertEquals(0, ctx.groupId(1));
        assertEquals(1, ctx.globalSize(1));
        assertEquals(1, ctx.localSize(2));
        assertEquals(1, ctx.numGroups(1));
        // оси вне 0..2 (в т.ч. отрицательные) - тоже «за пределами»
        assertEquals(0, ctx.globalId(-1));
        assertEquals(0, ctx.globalId(3));
        assertEquals(1, ctx.globalSize(7));
        assertEquals(1, ctx.workDim());
        assertEquals(4, ctx.groupId(0));
        assertEquals(2, ctx.localId(0));
    }

    @Test
    void initialPositionIsZero() {
        var ctx = new WorkItemContext(3, new int[]{4, 4, 4}, new int[]{2, 2, 2});
        for (int d = 0; d < 3; d++) {
            assertEquals(0, ctx.globalId(d));
            assertEquals(0, ctx.localId(d));
            assertEquals(0, ctx.groupId(d));
        }
        assertEquals(3, ctx.workDim());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new WorkItemContext(0, new int[]{1, 1, 1}, new int[]{1, 1, 1}));
        assertThrows(IllegalArgumentException.class, () -> new WorkItemContext(4, new int[]{1, 1, 1}, new int[]{1, 1, 1}));
        assertThrows(IllegalArgumentException.class, () -> new WorkItemContext(1, new int[]{1, 1}, new int[]{1, 1, 1}));
        assertThrows(IllegalArgumentException.class, () -> WorkItemContext.of1D(0, 1), "size 0");
        assertThrows(IllegalArgumentException.class, () -> WorkItemContext.of1D(10, 0), "local size 0");
        assertThrows(IllegalArgumentException.class, () -> WorkItemContext.of1D(10, 4), "not a multiple");
        assertThrows(IllegalArgumentException.class,
                () -> new WorkItemContext(1, new int[]{4, 2, 1}, new int[]{2, 1, 1}), "axis outside workDim is greater than 1");
    }

    @Test
    void rejectsGlobalIdOutsideNdRange() {
        var ctx = WorkItemContext.of1D(10, 5);
        assertThrows(IllegalArgumentException.class, () -> ctx.setGlobalId(10, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ctx.setGlobalId(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ctx.setGlobalId(0, 1, 0), "axis 1 has size 1");
        ctx.setGlobalId(9, 0, 0);
        assertEquals(9, ctx.globalId(0), "last valid index");
    }

    @Test
    void constructorCopiesSizeArrays() {
        int[] global = {4, 1, 1};
        var ctx = new WorkItemContext(1, global, new int[]{2, 1, 1});
        global[0] = 100;
        assertEquals(4, ctx.globalSize(0), "modifying the caller's array does not affect the context");
    }
}
