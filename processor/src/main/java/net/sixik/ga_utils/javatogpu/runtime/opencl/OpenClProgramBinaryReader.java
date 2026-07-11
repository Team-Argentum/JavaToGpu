package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClProgram;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBinaryArtifact;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Reads a successfully built OpenCL program binary without affecting runtime execution.
 */
final class OpenClProgramBinaryReader {

    static final String ARTIFACT_NAME = "opencl-program.bin";
    private static final int MAX_PROGRAM_DEVICES = 1_024;

    private OpenClProgramBinaryReader() {
    }

    static Result read(OpenClProgram program) {
        if (program == null || program.handle() == 0L) {
            return Result.unavailable("invalid-program", "OpenCL program handle is unavailable");
        }
        try {
            return read(
                    program.device(),
                    (parameter, valueSize, valueAddress, sizeAddress) -> CL10.nclGetProgramInfo(
                            program.handle(),
                            parameter,
                            valueSize,
                            valueAddress,
                            sizeAddress
                    )
            );
        } catch (RuntimeException | LinkageError exception) {
            return Result.unavailable(
                    "failed",
                    "OpenCL program binary query failed: " + oneLine(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }
    }

    static Result read(long targetDevice, ProgramInfoQuery query) {
        java.util.Objects.requireNonNull(query, "query");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer countBuffer = stack.callocInt(1);
            int countResult = query.query(
                    CL10.CL_PROGRAM_NUM_DEVICES,
                    Integer.BYTES,
                    MemoryUtil.memAddress(countBuffer),
                    0L
            );
            if (countResult != CL10.CL_SUCCESS) {
                return Result.unavailable("query-num-devices-failed", "clGetProgramInfo(CL_PROGRAM_NUM_DEVICES) returned " + countResult);
            }

            int deviceCount = countBuffer.get(0);
            if (deviceCount <= 0 || deviceCount > MAX_PROGRAM_DEVICES) {
                return Result.unavailable("invalid-device-count", "OpenCL program reported device count " + deviceCount);
            }

            PointerBuffer devices = stack.callocPointer(deviceCount);
            int devicesResult = query.query(
                    CL10.CL_PROGRAM_DEVICES,
                    (long) deviceCount * Pointer.POINTER_SIZE,
                    devices.address(),
                    0L
            );
            if (devicesResult != CL10.CL_SUCCESS) {
                return Result.unavailable("query-devices-failed", "clGetProgramInfo(CL_PROGRAM_DEVICES) returned " + devicesResult);
            }

            int selectedDeviceIndex = selectedDeviceIndex(devices, targetDevice);
            if (selectedDeviceIndex < 0) {
                return new Result(
                        "device-not-found",
                        deviceCount,
                        -1,
                        0L,
                        "",
                        "unknown",
                        new byte[0],
                        "target OpenCL device is not present in the program device list"
                );
            }

            PointerBuffer binarySizes = stack.callocPointer(deviceCount);
            int sizesResult = query.query(
                    CL10.CL_PROGRAM_BINARY_SIZES,
                    (long) deviceCount * Pointer.POINTER_SIZE,
                    binarySizes.address(),
                    0L
            );
            if (sizesResult != CL10.CL_SUCCESS) {
                return Result.unavailable("query-binary-sizes-failed", "clGetProgramInfo(CL_PROGRAM_BINARY_SIZES) returned " + sizesResult);
            }

            long binarySize = binarySizes.get(selectedDeviceIndex);
            if (binarySize <= 0L) {
                return new Result(
                        "empty",
                        deviceCount,
                        selectedDeviceIndex,
                        Math.max(0L, binarySize),
                        "",
                        "unknown",
                        new byte[0],
                        "OpenCL driver reported an empty program binary"
                );
            }
            if (binarySize > Integer.MAX_VALUE) {
                return new Result(
                        "too-large",
                        deviceCount,
                        selectedDeviceIndex,
                        binarySize,
                        "",
                        "unknown",
                        new byte[0],
                        "OpenCL program binary exceeds the supported in-memory artifact size"
                );
            }

            ByteBuffer binaryBuffer = MemoryUtil.memAlloc((int) binarySize);
            try {
                PointerBuffer binaryPointers = stack.callocPointer(deviceCount);
                binaryPointers.put(selectedDeviceIndex, MemoryUtil.memAddress(binaryBuffer));
                int binaryResult = query.query(
                        CL10.CL_PROGRAM_BINARIES,
                        (long) deviceCount * Pointer.POINTER_SIZE,
                        binaryPointers.address(),
                        0L
                );
                if (binaryResult != CL10.CL_SUCCESS) {
                    return new Result(
                            "query-binaries-failed",
                            deviceCount,
                            selectedDeviceIndex,
                            binarySize,
                            "",
                            "unknown",
                            new byte[0],
                            "clGetProgramInfo(CL_PROGRAM_BINARIES) returned " + binaryResult
                    );
                }

                byte[] binary = new byte[(int) binarySize];
                for (int index = 0; index < binary.length; index++) {
                    binary[index] = binaryBuffer.get(index);
                }
                return new Result(
                        "captured",
                        deviceCount,
                        selectedDeviceIndex,
                        binary.length,
                        sha256(binary),
                        detectFormat(binary),
                        binary,
                        "OpenCL program binary captured for diagnostic inspection"
                );
            } finally {
                MemoryUtil.memFree(binaryBuffer);
            }
        } catch (RuntimeException | LinkageError exception) {
            return Result.unavailable(
                    "failed",
                    "OpenCL program binary query failed: " + oneLine(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }
    }

    private static int selectedDeviceIndex(PointerBuffer devices, long targetDevice) {
        for (int index = 0; index < devices.remaining(); index++) {
            if (devices.get(index) == targetDevice) {
                return index;
            }
        }
        return devices.remaining() == 1 && targetDevice == 0L ? 0 : -1;
    }

    private static String sha256(byte[] binary) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binary));
        } catch (NoSuchAlgorithmException exception) {
            return "unavailable";
        }
    }

    private static String detectFormat(byte[] binary) {
        if (startsWith(binary, 0x7F, 'E', 'L', 'F')) {
            return "elf";
        }
        if (startsWith(binary, 'M', 'Z')) {
            return "pe-coff";
        }
        if (startsWith(binary, 0x03, 0x02, 0x23, 0x07)) {
            return "spir-v";
        }
        if (startsWith(binary, 'B', 'C', 0xC0, 0xDE)) {
            return "llvm-bitcode";
        }
        if (startsWith(binary, 0x50, 0xED, 0x55, 0xBA) || startsWith(binary, 0xB1, 0x43, 0x62, 0x46)) {
            return "nvidia-fatbin";
        }
        String prefix = new String(binary, 0, Math.min(binary.length, 256), StandardCharsets.US_ASCII).stripLeading();
        if (prefix.startsWith(".version") || prefix.startsWith("//") && prefix.contains(".target")) {
            return "ptx";
        }
        return "unknown";
    }

    private static boolean startsWith(byte[] binary, int... prefix) {
        if (binary.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((binary[index] & 0xFF) != (prefix[index] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    private static String mediaType(String format) {
        return switch (format) {
            case "elf" -> "application/x-elf";
            case "pe-coff" -> "application/vnd.microsoft.portable-executable";
            case "spir-v" -> "application/x-spirv";
            case "llvm-bitcode" -> "application/x-llvm-bitcode";
            case "ptx" -> "text/x-ptx";
            default -> "application/octet-stream";
        };
    }

    private static String oneLine(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    @FunctionalInterface
    interface ProgramInfoQuery {

        int query(int parameter, long valueSize, long valueAddress, long sizeAddress);
    }

    record Result(
            String status,
            int deviceCount,
            int selectedDeviceIndex,
            long binarySize,
            String sha256,
            String format,
            byte[] binary,
            String diagnostic
    ) {

        Result {
            status = status == null || status.isBlank() ? "unknown" : status;
            deviceCount = Math.max(0, deviceCount);
            binarySize = Math.max(0L, binarySize);
            sha256 = sha256 == null || sha256.isBlank() ? "none" : sha256;
            format = format == null || format.isBlank() ? "unknown" : format;
            binary = binary == null ? new byte[0] : binary.clone();
            diagnostic = oneLine(diagnostic, "none");
        }

        @Override
        public byte[] binary() {
            return binary.clone();
        }

        boolean captured() {
            return "captured".equals(status) && binary.length > 0;
        }

        Optional<GpuRuntimeBinaryArtifact> artifact() {
            if (!captured()) {
                return Optional.empty();
            }
            return Optional.of(new GpuRuntimeBinaryArtifact(ARTIFACT_NAME, mediaType(format), binary));
        }

        static Result unavailable(String status, String diagnostic) {
            return new Result(status, 0, -1, 0L, "", "unknown", new byte[0], diagnostic);
        }
    }
}
