package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;
import org.lwjgl.opencl.CL10;
import org.lwjgl.system.MemoryUtil;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClProgramBinaryReaderTest {

    @Test
    void capturesSelectedDeviceBinaryAndDetectsElfFormat() throws Exception {
        long device = 0xCAFE_BABEL;
        byte[] binary = new byte[]{0x7F, 'E', 'L', 'F', 1, 2, 3, 4};

        OpenClProgramBinaryReader.Result result = OpenClProgramBinaryReader.read(device, (
                parameter,
                valueSize,
                valueAddress,
                sizeAddress
        ) -> {
            if (parameter == CL10.CL_PROGRAM_NUM_DEVICES) {
                MemoryUtil.memPutInt(valueAddress, 1);
            } else if (parameter == CL10.CL_PROGRAM_DEVICES) {
                MemoryUtil.memPutAddress(valueAddress, device);
            } else if (parameter == CL10.CL_PROGRAM_BINARY_SIZES) {
                MemoryUtil.memPutAddress(valueAddress, binary.length);
            } else if (parameter == CL10.CL_PROGRAM_BINARIES) {
                long binaryAddress = MemoryUtil.memGetAddress(valueAddress);
                for (int index = 0; index < binary.length; index++) {
                    MemoryUtil.memPutByte(binaryAddress + index, binary[index]);
                }
            }
            return CL10.CL_SUCCESS;
        });

        assertTrue(result.captured());
        assertEquals(1, result.deviceCount());
        assertEquals(0, result.selectedDeviceIndex());
        assertEquals(binary.length, result.binarySize());
        assertEquals("elf", result.format());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binary)),
                result.sha256()
        );
        assertArrayEquals(binary, result.binary());
        assertTrue(result.artifact().isPresent());
    }

    @Test
    void reportsMissingSelectedDeviceWithoutReadingBinary() {
        OpenClProgramBinaryReader.Result result = OpenClProgramBinaryReader.read(22L, (
                parameter,
                valueSize,
                valueAddress,
                sizeAddress
        ) -> {
            if (parameter == CL10.CL_PROGRAM_NUM_DEVICES) {
                MemoryUtil.memPutInt(valueAddress, 1);
            } else if (parameter == CL10.CL_PROGRAM_DEVICES) {
                MemoryUtil.memPutAddress(valueAddress, 11L);
            }
            return CL10.CL_SUCCESS;
        });

        assertFalse(result.captured());
        assertEquals("device-not-found", result.status());
        assertEquals(-1, result.selectedDeviceIndex());
    }

    @Test
    void isolatesDriverQueryFailures() {
        OpenClProgramBinaryReader.Result result = OpenClProgramBinaryReader.read(
                1L,
                (parameter, valueSize, valueAddress, sizeAddress) -> CL10.CL_INVALID_VALUE
        );

        assertEquals("query-num-devices-failed", result.status());
        assertFalse(result.captured());
    }
}
