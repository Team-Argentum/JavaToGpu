package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuIrOptimizationProposalTest {

    @Test
    void proposedRewriteRejectsSameArtifactInstance() {
        IrGpuArtifact original = artifact("body\n  return original\n");

        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrOptimizationProposal.proposed(
                        "test.optimizer",
                        "1",
                        original,
                        original,
                        proof(),
                        List.of()
                )
        );
    }

    @Test
    void proposedRewriteRejectsUnchangedArtifactIdentity() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact unchangedCopy = artifact("body\n  return original\n");

        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrOptimizationProposal.proposed(
                        "test.optimizer",
                        "1",
                        original,
                        unchangedCopy,
                        proof(),
                        List.of()
                )
        );
    }

    private static GpuRuntimeIrOptimizationProofArtifact proof() {
        return GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "test.optimizer",
                "accepted",
                Map.of("proof", "test")
        );
    }

    private static IrGpuArtifact artifact(String body) {
        IrGpuMethodBody methodBody = IrGpuMethodBody.entry(
                "test.Kernel.run",
                "run",
                body,
                List.of()
        );
        IrGpuModule module = new IrGpuModule("test.Kernel.run", "run", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(IrGpuBackendOutput.openClSource("test.cl")),
                "opencl",
                "off"
        );
    }
}
