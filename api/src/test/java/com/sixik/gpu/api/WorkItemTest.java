package com.sixik.gpu.api;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Привязка контекста к потоку и id-builtins {@code Gpu.get*}. */
class WorkItemTest {

    @Test
    void outsideLaunchIsAnError() {
        assertFalse(WorkItem.isBound());
        assertThrows(IllegalStateException.class, WorkItem::current);
        assertThrows(IllegalStateException.class, () -> Gpu.getGlobalId(0));
        assertThrows(IllegalStateException.class, Gpu::getWorkDim);
    }

    @Test
    void idBuiltinsReadCurrentWorkItem() {
        var ctx = new WorkItemContext(2, new int[]{8, 4, 1}, new int[]{4, 2, 1});
        WorkItem.run(ctx, () -> {
            assertTrue(WorkItem.isBound());
            assertSame(ctx, WorkItem.current());
            ctx.setGlobalId(5, 3, 0);
            assertEquals(5, Gpu.getGlobalId(0));
            assertEquals(3, Gpu.getGlobalId(1));
            assertEquals(0, Gpu.getGlobalId(2));
            assertEquals(1, Gpu.getLocalId(0));
            assertEquals(1, Gpu.getLocalId(1));
            assertEquals(1, Gpu.getGroupId(0));
            assertEquals(1, Gpu.getGroupId(1));
            assertEquals(8, Gpu.getGlobalSize(0));
            assertEquals(4, Gpu.getGlobalSize(1));
            assertEquals(1, Gpu.getGlobalSize(2));
            assertEquals(4, Gpu.getLocalSize(0));
            assertEquals(2, Gpu.getLocalSize(1));
            assertEquals(2, Gpu.getNumGroups(0));
            assertEquals(2, Gpu.getNumGroups(1));
            assertEquals(2, Gpu.getWorkDim());
        });
    }

    @Test
    void bindingEndsAfterRunEvenOnException() {
        var ctx = WorkItemContext.of1D(4, 1);
        assertThrows(IllegalStateException.class, () -> WorkItem.run(ctx, () -> {
            throw new IllegalStateException("kernel failure");
        }));
        assertFalse(WorkItem.isBound(), "binding cleared after the exception");
    }

    @Test
    void nestedRunReplacesContextAndRestoresOuter() {
        var outer = WorkItemContext.of1D(10, 1);
        var inner = WorkItemContext.of1D(20, 1);
        WorkItem.run(outer, () -> {
            assertEquals(10, Gpu.getGlobalSize(0));
            WorkItem.run(inner, () -> assertEquals(20, Gpu.getGlobalSize(0)));
            assertEquals(10, Gpu.getGlobalSize(0), "outer context restored");
        });
    }

    @Test
    void contextIsPerThread() throws InterruptedException {
        var ctx = WorkItemContext.of1D(4, 1);
        var otherThreadBound = new AtomicInteger(-1);
        WorkItem.run(ctx, () -> {
            Thread t = new Thread(() -> otherThreadBound.set(WorkItem.isBound() ? 1 : 0));
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertEquals(0, otherThreadBound.get(), "binding is not inherited by another thread");
    }

    @Test
    void sequentialLoopOverWorkItemsLikeFallback() {
        var ctx = WorkItemContext.of1D(6, 3);
        int[] out = new int[6];
        WorkItem.run(ctx, () -> {
            for (int i = 0; i < 6; i++) {
                ctx.setGlobalId(i, 0, 0);
                out[Gpu.getGlobalId(0)] = Gpu.getGroupId(0) * 100 + Gpu.getLocalId(0);
            }
        });
        assertEquals(java.util.Arrays.toString(new int[]{0, 1, 2, 100, 101, 102}), java.util.Arrays.toString(out));
    }
}
