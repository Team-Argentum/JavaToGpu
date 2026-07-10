package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClProgram;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Reads OpenCL compiler diagnostics from a successfully built native program.
 */
final class OpenClProgramBuildLogReader {

    private OpenClProgramBuildLogReader() {
    }

    static String read(OpenClProgram program) {
        if (program == null || program.handle() == 0L || program.device() == 0L) {
            return "";
        }
        try {
            return read((value, size) -> CL10.clGetProgramBuildInfo(
                    program.handle(),
                    program.device(),
                    CL10.CL_PROGRAM_BUILD_LOG,
                    value,
                    size
            ));
        } catch (RuntimeException | LinkageError ignored) {
            // Compiler diagnostics are advisory and must never invalidate a successful build.
            return "";
        }
    }

    static String read(BuildInfoQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer size = stack.mallocPointer(1);
            if (query.query(null, size) != CL10.CL_SUCCESS) {
                return "";
            }
            long byteCount = size.get(0);
            if (byteCount <= 1L || byteCount > Integer.MAX_VALUE) {
                return "";
            }

            ByteBuffer buffer = MemoryUtil.memAlloc((int) byteCount);
            try {
                if (query.query(buffer, size) != CL10.CL_SUCCESS) {
                    return "";
                }
                int length = (int) Math.min(byteCount, size.get(0));
                while (length > 0 && buffer.get(length - 1) == 0) {
                    length--;
                }
                if (length == 0) {
                    return "";
                }
                byte[] bytes = new byte[length];
                for (int index = 0; index < length; index++) {
                    bytes[index] = buffer.get(index);
                }
                return new String(bytes, StandardCharsets.UTF_8).strip();
            } finally {
                MemoryUtil.memFree(buffer);
            }
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    @FunctionalInterface
    interface BuildInfoQuery {

        int query(ByteBuffer value, PointerBuffer size);
    }
}
