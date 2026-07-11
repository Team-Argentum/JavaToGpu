package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClNvidiaBinaryInspectorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesCuobjdumpResourceUsageIntoGenericCompilerFeedbackText() {
        OpenClNvidiaBinaryInspector.Result result = OpenClNvidiaBinaryInspector.parse(
                "cuobjdump",
                Path.of("C:/CUDA/bin/cuobjdump.exe"),
                "Function : kernel\n REG:44 STACK:16 SHARED:0 LOCAL:0\n8 bytes spill stores, 4 bytes spill loads",
                "inspection tool exited with code 0"
        );

        assertEquals("recorded", result.status());
        assertEquals(44, result.registers());
        assertEquals(8, result.spillStoreBytes());
        assertEquals(4, result.spillLoadBytes());
        assertTrue(result.normalizedFeedback().contains("Used 44 registers"));
        assertTrue(result.normalizedFeedback().contains("16 bytes stack frame"));
    }

    @Test
    void parsesPtxasVerboseOutput() {
        OpenClNvidiaBinaryInspector.Result result = OpenClNvidiaBinaryInspector.parse(
                "ptxas",
                Path.of("C:/CUDA/bin/ptxas.exe"),
                "ptxas         .     0 bytes stack frame, 0 bytes spill stores, 0 bytes spill loads\n"
                        + "ptxas info    : Used 38 registers, used 0 barriers",
                "inspection tool exited with code 0"
        );

        assertEquals(38, result.registers());
        assertEquals(0, result.spillStoreBytes());
        assertEquals(0, result.spillLoadBytes());
        assertTrue(result.normalizedFeedback().contains("Used 38 registers"));
    }

    @Test
    void discoversNewestStandardWindowsCudaToolkitInstallation() throws Exception {
        Path cudaRoot = temporaryDirectory.resolve("NVIDIA GPU Computing Toolkit").resolve("CUDA");
        Path older = cudaRoot.resolve("v12.8").resolve("bin").resolve("ptxas.exe");
        Path newer = cudaRoot.resolve("v13.3").resolve("bin").resolve("ptxas.exe");
        Files.createDirectories(older.getParent());
        Files.createDirectories(newer.getParent());
        Files.writeString(older, "older");
        Files.writeString(newer, "newer");

        List<Path> candidates = OpenClNvidiaBinaryInspector.defaultCudaInstallationCandidates(
                temporaryDirectory.toString(),
                "ptxas.exe"
        );

        assertEquals(List.of(newer, older), candidates);
    }

    @Test
    void normalizesOpenClPtxAndReadsTargetForPtxas() {
        byte[] binary = ".version 8.8\n.target sm_120, texmode_independent\n\0\0"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        byte[] normalized = OpenClNvidiaBinaryInspector.normalizePtx(binary);

        assertArrayEquals(
                ".version 8.8\n.target sm_120, texmode_independent\n"
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                normalized
        );
        assertEquals("sm_120", OpenClNvidiaBinaryInspector.ptxTarget(normalized).orElseThrow());
    }
}
