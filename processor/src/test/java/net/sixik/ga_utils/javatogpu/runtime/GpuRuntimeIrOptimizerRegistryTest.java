package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeIrOptimizerRegistryTest {

    @Test
    void legacyOptimizerProducesAppliedReportWhenArtifactChanges() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return Optional.of(optimized);
            }

            @Override
            public String optimizerVersion() {
                return "optimizer:legacy-test";
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(optimized, report.artifact().orElseThrow());
        assertEquals(1, report.passReports().size());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationStage.TRANSFORM, passReport.stage());
        assertEquals("optimizer:legacy-test", passReport.optimizerVersion());
        assertEquals(GpuRuntimeIrOptimizationOutcome.APPLIED, passReport.outcome());
        assertEquals("legacy-optimizer-result", passReport.proofStatus());
        assertTrue(passReport.toLine().contains("optimizer used legacy Optional<IrGpuArtifact> API"));
    }

    @Test
    void structuredOptimizerCanReportRollbackWithoutChangingArtifact() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact attempted = artifact("body\n  return attempted\n");
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return request.artifact();
            }

            @Override
            public GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
                return new GpuRuntimeIrOptimizationReport(
                        request.artifact(),
                        List.of(GpuRuntimeIrOptimizationPassReport.rolledBack(
                                "optimizer:rollback-test",
                                GpuRuntimeIrOptimizerRegistry.identityOf(request.artifact()),
                                GpuRuntimeIrOptimizerRegistry.identityOf(Optional.of(attempted)),
                                "proof:runtime-equivalence-missing",
                                "runtime equivalence evidence is not available",
                                List.of("kept original IR for safety")
                        ))
                );
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals(2, report.passReports().size());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationStage.TRANSFORM, passReport.stage());
        assertEquals(GpuRuntimeIrOptimizationOutcome.ROLLED_BACK, passReport.outcome());
        assertEquals("proof:runtime-equivalence-missing", passReport.proofStatus());
        assertEquals("runtime equivalence evidence is not available", passReport.rollbackReason());
        assertEquals("registry-rollback-safety", report.passReports().get(1).proofStatus());
        assertTrue(report.toText().contains("kept original IR for safety"));
    }

    @Test
    void rolledBackPassCannotLeakMutatedArtifactToFollowingPasses() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact attempted = artifact("body\n  return attempted\n");
        GpuRuntimeIrOptimizer rollbackOptimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return request.artifact();
            }

            @Override
            public GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
                return new GpuRuntimeIrOptimizationReport(
                        Optional.of(attempted),
                        List.of(GpuRuntimeIrOptimizationPassReport.rolledBack(
                                "optimizer:unsafe-rollback-test",
                                GpuRuntimeIrOptimizerRegistry.identityOf(request.artifact()),
                                GpuRuntimeIrOptimizerRegistry.identityOf(Optional.of(attempted)),
                                "proof:rejected",
                                "post-transform validation rejected artifact",
                                List.of("attempted artifact must be discarded")
                        ))
                );
            }
        };
        GpuRuntimeIrOptimizer followingOptimizer = request -> {
            throw new AssertionError("rollback must stop the pipeline before the next optimizer");
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(
                rollbackOptimizer,
                followingOptimizer
        ));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals(2, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.ROLLED_BACK, report.passReports().get(0).outcome());
        assertEquals("registry-rollback-safety", report.passReports().get(1).proofStatus());
        assertTrue(report.toText().contains("runtime optimizer registry discarded pass artifact"));
    }

    @Test
    void passReportCarriesI2ArtifactFieldMapAsProofEvidence() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        Map<String, String> i2Fields = Map.of(
                "validationRulesPassed", "false",
                "validationRulesFirstBlockingRuleId", "cse.literalPromotionRuntimeEquivalenceGate",
                "optimizerProductionPreflightProductionMutationEnabled", "false"
        );
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return request.artifact();
            }

            @Override
            public GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
                GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.rolledBack(
                        "optimizer:i2-proof-test",
                        GpuRuntimeIrOptimizerRegistry.identityOf(request.artifact()),
                        GpuRuntimeIrOptimizerRegistry.identityOf(request.artifact()),
                        "i2-proof-blocked",
                        "I2 validation artifact rejected production mutation",
                        List.of("I2 proof fields are attached")
                ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "i2.validationRules",
                        "rejected/blockingResultsPresent",
                        i2Fields
                ));
                return new GpuRuntimeIrOptimizationReport(request.artifact(), List.of(passReport));
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(original, report.artifact().orElseThrow());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals("i2.validationRules", passReport.proofArtifact().source());
        assertEquals("rejected/blockingResultsPresent", passReport.proofArtifact().verdict());
        assertEquals("false", passReport.proofArtifact().fields().get("validationRulesPassed"));
        assertTrue(report.toText().contains("proofArtifact source=i2.validationRules"));
        assertTrue(report.toText().contains("validationRulesFirstBlockingRuleId:cse.literalPromotionRuntimeEquivalenceGate"));
    }

    @Test
    void optimizerFailureIsCapturedAsRollbackSafeReport() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                throw new IllegalStateException("optimizer exploded");
            }

            @Override
            public String optimizerVersion() {
                return "optimizer:failing-test";
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals(1, report.passReports().size());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationStage.TRANSFORM, passReport.stage());
        assertEquals("optimizer:failing-test", passReport.optimizerVersion());
        assertEquals(GpuRuntimeIrOptimizationOutcome.FAILED, passReport.outcome());
        assertEquals("failed-before-proof", passReport.proofStatus());
        assertEquals("IllegalStateException", passReport.rollbackReason());
        assertTrue(passReport.toLine().contains("optimizer exploded"));
    }

    @Test
    void optimizerRegistryExposesExplicitTransformPassesForLegacyHooks() {
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return request.artifact();
            }

            @Override
            public String optimizerVersion() {
                return "optimizer:pass-adapter-test";
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        List<GpuRuntimeIrOptimizationPass> passes = registry.optimizerPasses();

        assertEquals(1, passes.size());
        assertEquals(GpuRuntimeIrOptimizationStage.TRANSFORM, passes.get(0).stage());
        assertEquals("optimizer:pass-adapter-test", passes.get(0).passVersion());
    }

    @Test
    void optimizationReportCarriesAdvisoryStrategyDecision() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        GpuOptimizationStrategyDecision decision = GpuOptimizationStrategyDecision.advisory(
                "strategy:opencl-nvidia-advisory",
                "nvidia",
                "off",
                "NVIDIA remains scalar-safe until evidence-backed",
                List.of("strategy is diagnostic-only")
        );
        GpuRuntimeIrOptimizationRequest request = new GpuRuntimeIrOptimizationRequest(
                request(original).compileRequest(),
                Optional.of(original),
                decision
        );

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.noOp().optimizeWithReport(request);

        assertEquals(decision, report.strategyDecision());
        assertTrue(report.toText().contains("strategy:opencl-nvidia-advisory"));
        assertTrue(report.toText().contains("advisoryOnly=true"));
    }

    @Test
    void defaultVendorStrategiesStayAdvisoryUntilEvidenceBacked() {
        assertAdvisoryVendorStrategy(
                "NVIDIA Corporation",
                "strategy:opencl-nvidia-advisory",
                "nvidia"
        );
        assertAdvisoryVendorStrategy(
                "Advanced Micro Devices",
                "strategy:opencl-amd-advisory",
                "amd"
        );
        assertAdvisoryVendorStrategy(
                "Intel(R) Corporation",
                "strategy:opencl-intel-advisory",
                "intel"
        );
    }

    private static void assertAdvisoryVendorStrategy(
            String vendor,
            String expectedStrategyName,
            String expectedDeviceFamily
    ) {
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.openCl(
                        "OpenCL",
                        vendor + " GPU",
                        vendor,
                        "mock-driver",
                        "OpenCL 3.0 Mock",
                        64L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false
                ),
                Optional.of(artifact("body\n  return original\n"))
        );

        GpuOptimizationStrategyDecision decision = GpuOptimizationStrategy.advisoryDefault().select(compileRequest);

        assertEquals(expectedStrategyName, decision.strategyName());
        assertEquals(expectedDeviceFamily, decision.deviceFamily());
        assertEquals("vendor-tuned", decision.selectedProfile());
        assertTrue(decision.advisoryOnly());
        assertFalse(decision.evidenceBacked());
    }

    @Test
    void defaultVendorStrategiesExposePerVendorBaselineState() {
        GpuOptimizationStrategyDecision nvidia = vendorDecision("NVIDIA Corporation");
        GpuOptimizationStrategyDecision amd = vendorDecision("Advanced Micro Devices");
        GpuOptimizationStrategyDecision intel = vendorDecision("Intel(R) Corporation");

        assertTrue(nvidia.vendorBaseline().recorded());
        assertEquals("recorded-nvidia-only", nvidia.vendorBaseline().status());
        assertFalse(nvidia.vendorBaseline().promotionEligible());
        assertTrue(nvidia.toLine().contains("docs-project-plan/nvidia-rtx5070-baselines.md"));
        assertFalse(amd.vendorBaseline().recorded());
        assertEquals("pending-hardware", amd.vendorBaseline().status());
        assertFalse(amd.vendorBaseline().promotionEligible());
        assertFalse(intel.vendorBaseline().recorded());
        assertEquals("pending-hardware", intel.vendorBaseline().status());
        assertFalse(intel.vendorBaseline().promotionEligible());
    }

    private static GpuOptimizationStrategyDecision vendorDecision(String vendor) {
        return GpuOptimizationStrategy.advisoryDefault().select(new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.openCl(
                        "OpenCL",
                        vendor + " GPU",
                        vendor,
                        "mock-driver",
                        "OpenCL 3.0 Mock",
                        64L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false
                ),
                Optional.of(artifact("body\n  return original\n"))
        ));
    }

    private static GpuRuntimeIrOptimizationRequest request(IrGpuArtifact artifact) {
        return new GpuRuntimeIrOptimizationRequest(
                new GpuRuntimeCompileRequest(
                        descriptor(),
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                        Optional.of(artifact)
                ),
                Optional.of(artifact)
        );
    }

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static IrGpuArtifact artifact(String body) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry("kernel", "jtg_kernel", body, List.of()))
                ),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }
}
