package net.sixik.ga_utils.examples;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizationJournalExampleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesCustomJournalRootFromFirstArgument() {
        Path custom = temporaryDirectory.resolve("custom-journal");

        assertEquals(custom, OptimizationJournalExample.resolveJournalRoot(new String[]{custom.toString()}));
    }

    @Test
    void listsOnlyRelevantJournalArtifacts() throws Exception {
        Path kernelDirectory = temporaryDirectory.resolve("kernel-a");
        Files.createDirectories(kernelDirectory);
        Files.writeString(kernelDirectory.resolve("backend.opencl-c"), "__kernel void kernel() {}\n");
        Files.writeString(kernelDirectory.resolve("original.backend.opencl-c"), "__kernel void kernel_original() {}\n");
        Files.writeString(kernelDirectory.resolve("optimized.backend.opencl-c"), "__kernel void kernel_optimized() {}\n");
        Files.writeString(kernelDirectory.resolve("original.irgpu.properties"), "original\n");
        Files.writeString(kernelDirectory.resolve("optimized.irgpu.properties"), "optimized\n");
        Files.writeString(kernelDirectory.resolve("runtime-ir-handoff.properties"), "handoff\n");
        Files.writeString(kernelDirectory.resolve("unrelated.properties"), "ignored\n");

        List<Path> files = OptimizationJournalExample.interestingJournalFiles(temporaryDirectory);

        assertEquals(6, files.size());
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("backend.opencl-c")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("original.backend.opencl-c")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("optimized.backend.opencl-c")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("original.irgpu.properties")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("optimized.irgpu.properties")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("runtime-ir-handoff.properties")));
    }
}
