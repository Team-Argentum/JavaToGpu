package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationPolicyTest {

    @Test
    void defaultPolicyIsProposalOnlyAndFailClosed() {
        GpuIrOptimizationPolicy policy = GpuIrOptimizationPolicy.off();

        assertEquals("off", policy.optimizerProfile());
        assertEquals("off", policy.optimizationLevel());
        assertTrue(policy.proposalOnly());
        assertFalse(policy.mutationAllowed());
        assertFalse(policy.fastMathAllowed());
        assertFalse(policy.vendorAdaptationAllowed());
        assertFalse(policy.registerPressureSplittingAllowed());
        assertTrue(policy.rollbackRequired());
        assertTrue(policy.proofRequired());
    }

    @Test
    void policyNormalizesLegacyAndExplicitContextFields() {
        GpuIrOptimizationPolicy policy = GpuIrOptimizationPolicy.fromContext(
                "diagnostic",
                false,
                Map.of(
                        "optimizationLevel", "level-2",
                        "mutationAllowed", "enabled",
                        "proposalOnly", "false",
                        "fastMathAllowed", "yes",
                        "vendorAdaptationAllowed", "allowed",
                        "registerPressureSplittingAllowed", "true",
                        "rollbackRequired", "required",
                        "proofRequired", "optional"
                )
        );

        assertEquals("diagnostic", policy.optimizerProfile());
        assertEquals("level-2", policy.optimizationLevel());
        assertFalse(policy.proposalOnly());
        assertTrue(policy.mutationAllowed());
        assertTrue(policy.fastMathAllowed());
        assertTrue(policy.vendorAdaptationAllowed());
        assertTrue(policy.registerPressureSplittingAllowed());
        assertTrue(policy.rollbackRequired());
        assertFalse(policy.proofRequired());
    }

    @Test
    void proposalRequestPreservesPolicyAsBackendNeutralContext() {
        GpuIrOptimizationPolicy policy = new GpuIrOptimizationPolicy(
                "diagnostic",
                "level-1",
                false,
                true,
                true,
                false,
                true,
                true,
                true
        );

        GpuIrOptimizationProposalRequest request = new GpuIrOptimizationProposalRequest(
                artifact(),
                policy,
                Map.of("backendTarget", "OPENCL")
        );

        assertEquals("diagnostic", request.optimizerProfile());
        assertTrue(request.mutationAllowed());
        assertEquals(policy, request.policy());
        assertEquals("OPENCL", request.contextFields().get("backendTarget"));
        assertEquals("diagnostic", request.contextFields().get(GpuIrOptimizationPolicy.OPTIMIZER_PROFILE_FIELD));
        assertEquals("true", request.contextFields().get(GpuIrOptimizationPolicy.FAST_MATH_ALLOWED_FIELD));
    }

    private static IrGpuArtifact artifact() {
        IrGpuMethodBody methodBody = IrGpuMethodBody.entry(
                "test.Kernel.run",
                "run",
                "body\n",
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
