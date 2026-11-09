package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.runtime.device.FakeDeviceApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Local-память ядра против {@code CL_DEVICE_LOCAL_MEM_SIZE} (M10): отказ до обращения к драйверу ({@code RT-D04}). */
class LocalMemoryLimitTest {

    private static IrModule kernelWithLocal(int elemType, int length) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int tile = b.declareLocal(b.array(AddressSpace.LOCAL, elemType));
        b.endFunction(b.block(b.localArrayDecl(tile, b.constI32(length)), b.barrier(1),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.constI32(1))));
        return b.build();
    }

    @Test
    void sizeIsLengthTimesElementSizeOfTheAbi() {
        assertEquals(256 * 4L, GpuContext.localMemoryBytes(kernelWithLocal(Types.I32, 256), 0));
        assertEquals(10 * 8L, GpuContext.localMemoryBytes(kernelWithLocal(Types.F64, 10), 0));
        IrBuilder b = new IrBuilder();
        int v3 = b.vec(Types.F32, 3);
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int tile = b.declareLocal(b.array(AddressSpace.LOCAL, v3));
        b.endFunction(b.block(b.localArrayDecl(tile, b.constI32(4))));
        assertEquals(4 * 16L, GpuContext.localMemoryBytes(b.build(), 0), "float3 takes 16 bytes, as in OpenCL C");
    }

    @Test
    void kernelWithoutLocalArraysNeedsNoLocalMemory() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block());
        assertEquals(0, GpuContext.localMemoryBytes(b.build(), 0));
    }

    @Test
    void kernelWithMoreLocalMemoryThanTheDeviceHasIsRejectedBeforeTheDriver() {
        FakeDeviceApi fake = FakeDeviceApi.withGpu();
        try (GpuContext ctx = GpuContext.builder().api(fake).ignoreEnvironment().build()) {
            Result<CompiledKernel> tooBig = ctx.compile(kernelWithLocal(Types.I32, 16 * 1024)); // 64 КиБ при 48 КиБ у фейка
            assertTrue(!tooBig.isOk());
            assertEquals("RT-D04", tooBig.diagnostics().get(0).code());
            assertTrue(tooBig.diagnostics().get(0).message().contains("65536"), tooBig.diagnostics().toString());
            assertTrue(tooBig.diagnostics().get(0).message().contains("49152"), tooBig.diagnostics().toString());
            assertEquals(0, fake.builds(), "the driver was not called");

            Result<CompiledKernel> fits = ctx.compile(kernelWithLocal(Types.I32, 12 * 1024)); // ровно 48 КиБ
            assertTrue(fits.isOk(), fits.diagnostics().toString());
            assertEquals(1, fake.builds());
        }
    }
}
