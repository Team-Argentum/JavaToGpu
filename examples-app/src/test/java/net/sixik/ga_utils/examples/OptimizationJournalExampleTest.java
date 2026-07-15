package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactParser;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrClampMaterializationProposalProvider;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrMixMaterializationProposalProvider;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrOptimizationSandwichRunner;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrProposalRuntimeBridgePass;
import net.sixik.ga_utils.javatogpu.iroptimizer.GpuIrStepMaterializationProposalProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationRequest;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuSourceEmission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizationJournalExampleTest {

    private static final String DENSITY_IRGPU_RESOURCE =
            "javatogpu/net/sixik/ga_utils/examples/OptimizationJournalExample/computeVoxelDensity.irgpu.properties";

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesCustomJournalRootFromFirstArgument() {
        Path custom = temporaryDirectory.resolve("custom-journal");

        assertEquals(custom, OptimizationJournalExample.resolveJournalRoot(new String[]{custom.toString()}));
    }

    @Test
    void resolvesLifecycleJournalFileUnderJournalRoot() {
        Path custom = temporaryDirectory.resolve("custom-journal");

        assertEquals(custom.resolve("runtime-lifecycle.jsonl"), OptimizationJournalExample.resolveLifecycleJournalFile(custom));
    }

    @Test
    void resolvesExampleLifecycleTraceFileUnderJournalRoot() {
        Path custom = temporaryDirectory.resolve("custom-journal");

        assertEquals(custom.resolve("example-lifecycle-service.trace"), OptimizationJournalExample.resolveExampleLifecycleTraceFile(custom));
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
        Files.writeString(kernelDirectory.resolve("optimizer-report.txt"), "report\n");
        Files.writeString(kernelDirectory.resolve("runtime-ir-optimizer-evidence.properties"), "evidence\n");
        Files.writeString(temporaryDirectory.resolve("runtime-lifecycle.jsonl"), "{}\n");
        Files.writeString(temporaryDirectory.resolve("example-lifecycle-service.trace"), "trace\n");
        Files.writeString(kernelDirectory.resolve("unrelated.properties"), "ignored\n");

        List<Path> files = OptimizationJournalExample.interestingJournalFiles(temporaryDirectory);

        assertEquals(10, files.size());
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("backend.opencl-c")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("original.backend.opencl-c")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("optimized.backend.opencl-c")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("original.irgpu.properties")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("optimized.irgpu.properties")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("runtime-ir-handoff.properties")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("optimizer-report.txt")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("runtime-ir-optimizer-evidence.properties")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("runtime-lifecycle.jsonl")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("example-lifecycle-service.trace")));
    }

    @Test
    void exposesExpectedReviewMarkersForIntrinsicMaterializers() {
        assertTrue(OptimizationJournalExample.expectedOptimizedSourceMarkers().contains("clamp("));
        assertTrue(OptimizationJournalExample.expectedOptimizedSourceMarkers().contains("step("));
        assertTrue(OptimizationJournalExample.expectedOptimizedSourceMarkers().contains("mix("));

        assertTrue(OptimizationJournalExample.expectedEvidenceMarkers().contains("clampMaterialization.status"));
        assertTrue(OptimizationJournalExample.expectedEvidenceMarkers().contains("stepMaterialization.status"));
        assertTrue(OptimizationJournalExample.expectedEvidenceMarkers().contains("mixMaterialization.status"));
    }

    @Test
    void optimizerBridgeProducesReviewSourceWithIntrinsicMaterializersForDensityExample() throws Exception {
        IrGpuArtifact original = loadGeneratedArtifact(DENSITY_IRGPU_RESOURCE);
        GpuRuntimeCompileRequest compileRequest = compileRequest(original);

        GpuRuntimeIrOptimizationReport report = new GpuIrProposalRuntimeBridgePass(
                List.of(
                        new GpuIrClampMaterializationProposalProvider(),
                        new GpuIrStepMaterializationProposalProvider(),
                        new GpuIrMixMaterializationProposalProvider()
                ),
                GpuIrOptimizationSandwichRunner.alwaysValid(),
                false
        ).run(new GpuRuntimeIrOptimizationRequest(compileRequest, Optional.of(original)));

        assertEquals(original, report.artifact().orElseThrow());
        IrGpuArtifact candidate = report.candidateArtifact().orElseThrow();
        OpenClIrGpuSourceEmission emission = OpenClIrGpuSourceEmission.inspect(candidate);
        assertTrue(emission.sourceGenerated(), () -> String.join(System.lineSeparator(), emission.blockers()));
        String source = emission.source();
        assertTrue(source.contains("clamp("), source);
        assertTrue(source.contains("step("), source);
        assertTrue(source.contains("mix("), source);
    }

    private static IrGpuArtifact loadGeneratedArtifact(String resource) throws Exception {
        try (InputStream stream = OptimizationJournalExampleTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new AssertionError("Missing generated IrGpu resource: " + resource);
            }
            return IrGpuArtifactParser.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static GpuRuntimeCompileRequest compileRequest(IrGpuArtifact artifact) {
        GpuRuntimeDeviceProfile profile = GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "examples-test-device");
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        artifact.module().entryEmittedName(),
                        artifact.derivedOpenClResource(),
                        "",
                        DENSITY_IRGPU_RESOURCE,
                        List.of()
                ),
                GpuRuntimeCompileOptions.openCl(List.of(), "diagnostic"),
                profile,
                Optional.of(artifact)
        );
    }
}
