package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClKernel;
import dev.denismasterherobrine.packager.opencl.core.OpenClProgram;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Fail-safe reader for standard OpenCL kernel work-group resource metrics.
 */
final class OpenClKernelResourceInfoReader {

    private OpenClKernelResourceInfoReader() {
    }

    static OpenClKernelResourceInfo read(OpenClKernel kernel, OpenClProgram program) {
        if (kernel == null || program == null || kernel.handle() == 0L || program.device() == 0L) {
            return unavailable();
        }
        return read((parameter, value) -> CL10.clGetKernelWorkGroupInfo(
                kernel.handle(),
                program.device(),
                parameter,
                value,
                null
        ));
    }

    static OpenClKernelResourceInfo read(KernelInfoQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        long maxWorkGroupSize = readSizeT(query, CL10.CL_KERNEL_WORK_GROUP_SIZE);
        long preferredMultiple = readSizeT(query, CL11.CL_KERNEL_PREFERRED_WORK_GROUP_SIZE_MULTIPLE);
        long localMemory = readLong(query, CL10.CL_KERNEL_LOCAL_MEM_SIZE);
        long privateMemory = readLong(query, CL11.CL_KERNEL_PRIVATE_MEM_SIZE);
        return new OpenClKernelResourceInfo(
                maxWorkGroupSize,
                preferredMultiple,
                localMemory,
                privateMemory
        );
    }

    private static long readSizeT(KernelInfoQuery query, int parameter) {
        return readMetric(query, parameter, Pointer.POINTER_SIZE);
    }

    private static long readLong(KernelInfoQuery query, int parameter) {
        return readMetric(query, parameter, Long.BYTES);
    }

    private static long readMetric(KernelInfoQuery query, int parameter, int byteCount) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer value = stack.malloc(byteCount).order(ByteOrder.nativeOrder());
            if (query.query(parameter, value) != CL10.CL_SUCCESS) {
                return OpenClKernelResourceInfo.UNKNOWN;
            }
            return byteCount == Integer.BYTES
                    ? Integer.toUnsignedLong(value.getInt(0))
                    : value.getLong(0);
        } catch (RuntimeException | LinkageError ignored) {
            return OpenClKernelResourceInfo.UNKNOWN;
        }
    }

    private static OpenClKernelResourceInfo unavailable() {
        return OpenClKernelResourceInfo.unavailable();
    }

    @FunctionalInterface
    interface KernelInfoQuery {

        int query(int parameter, ByteBuffer value);
    }
}
