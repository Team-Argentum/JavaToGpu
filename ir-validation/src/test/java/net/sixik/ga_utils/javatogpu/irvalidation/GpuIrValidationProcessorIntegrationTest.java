package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.processors.GpuCompilerProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrValidationProcessorIntegrationTest {
    @Test
    void diagnosticModeRunsThroughJavacProcessorWithoutFailingBuild() throws IOException {
        CompilationResult result = compileWithIrValidationMode("diagnostic", null);

        assertTrue(result.success(), result.diagnosticMessages());
        assertTrue(Files.exists(result.generatedOutputDir().resolve("javatogpu/sample/Demo/kernel.cl")));
        assertTrue(result.diagnosticMessages().contains("ir optimization validation method=kernel"));
        assertTrue(result.diagnosticMessages().contains("optimizerGateBlocked=true"));
        assertTrue(result.diagnosticMessages().contains("optimizerGateSource=autoVectorization"));
        assertTrue(result.diagnosticMessages().contains("optimizerGateFamily=rejection.UNSUPPORTED_LANE_COUNT"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRejections=1"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewriteReadiness=rejected"));
    }

    @Test
    void diagnosticModeReportsCompactRewritePlanGuardFamiliesThroughJavacProcessor() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                null,
                null,
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void guarded(int flag, @GPUGlobal int[] input, @GPUGlobal int[] output) {
                                if (flag != 0) {
                                    output[0] = output[0] + 1;
                                }
                                for (int i = 0; i < 4; i++) {
                                    output[i] = input[i];
                                }
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        assertTrue(result.diagnosticMessages().contains("ir optimization validation method=guarded"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePlanGuards=1"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewriteReadiness=blockedByGuard"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationCanApplyRewrite=false"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationHasPolicyBlockedRewrite=true"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePolicyCanRewrite=false"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePolicyBlockingGuards=1"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePlanGuardFamilies={controlFlowBoundary=1}"));
        assertFalse(result.diagnosticMessages().contains("firstRewritePlanGuard"));
    }

    @Test
    void diagnosticModeReportsCompactBackendVectorWidthGuardThroughJavacProcessor() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                null,
                null,
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void vector3(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                                for (int i = 0; i < 3; i++) {
                                    output[i] = input[i];
                                }
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        assertTrue(result.diagnosticMessages().contains("ir optimization validation method=vector3"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePlanGuards=1"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewriteReadiness=blockedByGuard"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePolicyCanRewrite=false"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePlanGuardFamilies={backendVectorWidth=1}"));
        assertFalse(result.diagnosticMessages().contains("firstRewritePlanGuard"));
    }

    @Test
    void diagnosticModeReportsCompactBackendDoubleVectorGuardThroughJavacProcessor() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                null,
                null,
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void doubleVector(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                                for (int i = 0; i < 4; i++) {
                                    output[i] = input[i];
                                }
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        assertTrue(result.diagnosticMessages().contains("ir optimization validation method=doubleVector"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePlanGuards=1"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewriteReadiness=blockedByGuard"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePlanGuardFamilies={backendDoubleVector=1}"));
        assertFalse(result.diagnosticMessages().contains("firstRewritePlanGuard"));
    }

    @Test
    void diagnosticModeReportsCompactMemoryAddressSpaceGuardThroughJavacProcessor() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                null,
                null,
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUConstant;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void constantSource(@GPUConstant int[] input, @GPUGlobal int[] output) {
                                for (int i = 0; i < 4; i++) {
                                    output[i] = input[i];
                                }
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        assertTrue(result.diagnosticMessages().contains("ir optimization validation method=constantSource"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePlanGuards=1"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewriteReadiness=blockedByGuard"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePlanGuardFamilies={memoryAddressSpace=1}"));
        assertFalse(result.diagnosticMessages().contains("firstRewritePlanGuard"));
    }

    @Test
    void quietDiagnosticPolicySuppressesJavacNotesWithoutFailingBuild() throws IOException {
        CompilationResult result = compileWithIrValidationMode("diagnostic", "quiet");

        assertTrue(result.success(), result.diagnosticMessages());
        assertTrue(Files.exists(result.generatedOutputDir().resolve("javatogpu/sample/Demo/kernel.cl")));
        assertFalse(result.diagnosticMessages().contains("ir optimization validation method=kernel"));
        assertFalse(result.diagnosticMessages().contains("autoVectorizationRejections=1"));
    }

    @Test
    void detailedDiagnosticPolicyReportsNestedOptimizerContextWithoutFailingBuild() throws IOException {
        CompilationResult result = compileWithIrValidationMode("diagnostic", "detailed");

        assertTrue(result.success(), result.diagnosticMessages());
        assertTrue(result.diagnosticMessages().contains("ir optimization validation method=kernel"));
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePolicy={"));
        assertTrue(result.diagnosticMessages().contains("auto-vectorization preview"));
        assertTrue(result.diagnosticMessages().contains("UNSUPPORTED_LANE_COUNT"));
    }

    @Test
    void strictOptimizerModeFailsThroughJavacProcessorOnOptimizerDiagnostics() throws IOException {
        CompilationResult result = compileWithIrValidationMode("strictOptimizer", null);

        assertFalse(result.success(), result.diagnosticMessages());
        assertTrue(result.diagnosticMessages().contains("IR optimization validation failed for kernel"));
        assertTrue(result.diagnosticMessages().contains("UNSUPPORTED_LANE_COUNT"));
    }

    @Test
    void strictOptimizerModeFailsThroughJavacProcessorOnRewritePlanGuards() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "strictOptimizer",
                null,
                null,
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void guarded(int flag, @GPUGlobal int[] input, @GPUGlobal int[] output) {
                                if (flag != 0) {
                                    output[0] = output[0] + 1;
                                }
                                for (int i = 0; i < 4; i++) {
                                    output[i] = input[i];
                                }
                            }
                        }
                        """
        );

        assertFalse(result.success(), result.diagnosticMessages());
        assertTrue(result.diagnosticMessages().contains("IR optimization validation failed for guarded"));
        assertTrue(result.diagnosticMessages().contains("optimizerDiagnostics="));
        assertTrue(result.diagnosticMessages().contains("controlFlowBoundary=1"));
        assertTrue(result.diagnosticMessages().contains("control-flow boundary"));
    }

    @Test
    void strictOptimizerModeFailsThroughJavacProcessorOnCseRewritePolicyOnly() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "strictOptimizer",
                null,
                null,
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void cseOnly(int z, @GPUGlobal int[] output) {
                                int first = z + 1;
                                z = 7;
                                int second = z + 1;
                                output[0] = first + second;
                            }
                        }
                        """
        );

        assertFalse(result.success(), result.diagnosticMessages());
        assertTrue(result.diagnosticMessages().contains("IR optimization validation failed for cseOnly"));
        assertTrue(result.diagnosticMessages().contains("optimizer gate policy mode=STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS blocked=true source=cseRewritePolicy family=cseRewritePolicy.blockedBySkippedCandidate"));
        assertTrue(result.diagnosticMessages().contains("optimizerGateSourceCounts={cseRewritePolicy=1}"));
        assertTrue(result.diagnosticMessages().contains("cseRewritePolicy={CSE rewrite policy method=cseOnly"));
        assertTrue(result.diagnosticMessages().contains("readiness=blockedBySkippedCandidate"));
        assertTrue(result.diagnosticMessages().contains("cseRewritePolicy.skipReason.MUTATED_BETWEEN_OCCURRENCES=1"));
        assertTrue(result.diagnosticMessages().contains("MUTATED_BETWEEN_OCCURRENCES"));
    }

    @Test
    void diagnosticModeWritesMachineReadableReportArtifact() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties"
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Path reportPath = result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties");
        assertTrue(Files.exists(reportPath));

        Properties report = new Properties();
        try (var reader = Files.newBufferedReader(reportPath)) {
            report.load(reader);
        }

        assertTrue("javatogpu.ir.validation.v1".equals(report.getProperty("format")));
        assertTrue("1".equals(report.getProperty("entry.count")));
        assertTrue("optimization-validation".equals(report.getProperty("entry.0.provider")));
        assertTrue("kernel".equals(report.getProperty("entry.0.methodName")));
        assertTrue("true".equals(report.getProperty("entry.0.entryPoint")));
        assertTrue("DIAGNOSTIC".equals(report.getProperty("entry.0.mode")));
        assertTrue("ok".equals(report.getProperty("entry.0.safety")));
        assertTrue("1".equals(report.getProperty("entry.0.optimizerDiagnostics")));
        assertTrue("false".equals(report.getProperty("entry.0.cseRewritePolicyCanRewrite")));
        assertTrue("none".equals(report.getProperty("entry.0.cseRewritePolicyReadiness")));
        assertTrue("0".equals(report.getProperty("entry.0.cseRewritePolicyBlockingSkippedCandidates")));
        assertEntryValue(report, "entry.0.cseControlFlowRegionReadiness", "clear");
        assertEntryValue(report, "entry.0.cseControlFlowRegionBlocked", "false");
        assertEntryValue(report, "entry.0.cseControlFlowRegionBlockedCandidates", "0");
        assertEntryValue(report, "entry.0.cseControlFlowRegionFirstRemainingWork", "none");
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRejections")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT")));
        assertTrue(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnostic").contains("UNSUPPORTED_LANE_COUNT"));
        assertTrue("rejection.UNSUPPORTED_LANE_COUNT".equals(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnosticFamily")));
        assertEntryValue(report, "entry.0.optimizerLayerReadinessBaselineCiMethod", "kernel");
        assertEntryValue(report, "entry.0.optimizerLayerReadinessBaselineCiOutcome", "unchanged");
        assertEntryValue(report, "entry.0.optimizerLayerReadinessBaselineCiDecision", "accepted/noRegression");
        assertEntryValue(report, "entry.0.optimizerLayerReadinessBaselineCiFailBuild", "false");
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineSnapshotMethod", "kernel");
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineSnapshotVerdict", "blocked");
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineSnapshotSource", report.getProperty("entry.0.optimizerBlockerSource"));
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineSnapshotFamily", report.getProperty("entry.0.optimizerBlockerFamily"));
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineSnapshotRemainingWork", report.getProperty("entry.0.optimizerBlockerRemainingWork"));
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineComparisonMethod", "kernel");
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineComparisonOutcome", "unchanged");
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineComparisonBaselineScore", "3");
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineComparisonCurrentScore", "3");
        assertEntryValue(report, "entry.0.optimizerBlockerBaselineComparisonScoreDelta", "0");
        assertEntryValue(
                report,
                "entry.0.optimizerBlockerBaselineComparisonSourceTransition",
                report.getProperty("entry.0.optimizerBlockerSource") + "->" + report.getProperty("entry.0.optimizerBlockerSource")
        );
        assertEntryValue(
                report,
                "entry.0.optimizerBlockerBaselineComparisonFamilyTransition",
                report.getProperty("entry.0.optimizerBlockerFamily") + "->" + report.getProperty("entry.0.optimizerBlockerFamily")
        );
        assertEntryValue(
                report,
                "entry.0.optimizerBlockerBaselineComparisonRemainingWorkTransition",
                report.getProperty("entry.0.optimizerBlockerRemainingWork") + "->" + report.getProperty("entry.0.optimizerBlockerRemainingWork")
        );
        assertEntryValue(report, "entry.0.optimizerProductionReadinessMethod", "kernel");
        assertEntryValue(report, "entry.0.optimizerProductionReadinessVerdict", "blocked/productionSwitchNotReady");
        assertEntryValue(report, "entry.0.optimizerProductionReadinessBlocked", "true");
        assertEntryValue(report, "entry.0.optimizerProductionReadinessFirstBlockingStage", "validationBundle");
        assertEntryValue(
                report,
                "entry.0.optimizerProductionReadinessStageVerdicts",
                "{bundle=notReady/cseBlocked,preflight=blocked/optimizerValidationBundleNotReady,switch=blocked/preflightNotReady,promotionConfidence=blocked/productionSwitchNotReady}"
        );
        assertEntryValue(report, "entry.0.optimizerProductionReadinessAcceptance.Accepted", "false");
        assertEntryValue(report, "entry.0.optimizerProductionReadinessAcceptance.Reason", "rejected/productionReadinessBlocked");
    }

    @Test
    void diagnosticModeReportCapturesCseRewritePolicyOnlyGate() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void cseOnly(int z, @GPUGlobal int[] output) {
                                int first = z + 1;
                                z = 7;
                                int second = z + 1;
                                output[0] = first + second;
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertTrue("1".equals(report.getProperty("entry.count")));
        assertTrue("cseOnly".equals(report.getProperty("entry.0.methodName")));
        assertTrue("1".equals(report.getProperty("entry.0.optimizerDiagnostics")));
        assertTrue("cseRewritePolicy".equals(report.getProperty("entry.0.optimizerGateSource")));
        assertTrue("cseRewritePolicy.blockedBySkippedCandidate".equals(report.getProperty("entry.0.optimizerGateFamily")));
        assertTrue("{cseRewritePolicy=1}".equals(report.getProperty("entry.0.optimizerGateSourceCounts")));
        assertTrue("1".equals(report.getProperty("entry.0.optimizerGateSourceCount.cseRewritePolicy")));
        assertTrue("false".equals(report.getProperty("entry.0.cseRewritePolicyCanRewrite")));
        assertTrue("blockedBySkippedCandidate".equals(report.getProperty("entry.0.cseRewritePolicyReadiness")));
        assertTrue("1".equals(report.getProperty("entry.0.cseRewritePolicyBlockingSkippedCandidates")));
        assertEntryValue(report, "entry.0.cseControlFlowRegionReadiness", "clear");
        assertEntryValue(report, "entry.0.cseControlFlowRegionBlocked", "false");
        assertEntryValue(report, "entry.0.cseControlFlowRegionBlockedCandidates", "0");
        assertTrue("{MUTATED_BETWEEN_OCCURRENCES=1}".equals(report.getProperty("entry.0.cseRewritePolicyBlockingSkipReasonCounts")));
        assertTrue("1".equals(report.getProperty("entry.0.cseRewritePolicyBlockingSkipReason.MUTATED_BETWEEN_OCCURRENCES")));
        assertTrue("{topLevelDownstreamReplacements=1}".equals(report.getProperty("entry.0.cseRewritePolicyBlockingDominanceStatusCounts")));
        assertTrue("1".equals(report.getProperty("entry.0.cseRewritePolicyBlockingDominanceStatus.topLevelDownstreamReplacements")));
        assertTrue("MUTATED_BETWEEN_OCCURRENCES".equals(report.getProperty("entry.0.cseRewritePolicyFirstBlockingSkippedReason")));
        assertTrue("topLevelDownstreamReplacements".equals(report.getProperty("entry.0.cseRewritePolicyFirstBlockingSkippedDominanceStatus")));
        assertTrue("0".equals(report.getProperty("entry.0.autoVectorizationCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.autoVectorizationRejections")));
        assertTrue("0".equals(report.getProperty("entry.0.autoVectorizationRewritePlanGuards")));
    }

    @Test
    void diagnosticModeReportCapturesNestedSimpleArithmeticCseReadiness() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void nestedArithmetic(int x, int y, int z, @GPUGlobal int[] output) {
                                int first = x + (y + z);
                                int second = (z + x) + y;
                                output[0] = first + second;
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertSingleMethodReport(report, "nestedArithmetic");
        assertNoOptimizerGate(report);
        assertRewriteReadySimpleArithmeticProof(report, "+");
        assertNoSimpleArithmeticNumericBoundary(report);
        assertNoSimpleArithmeticLiteralProofCandidates(report);
        assertCseCounts(report, "1", "1", "0");
        assertNoAutoVectorizationActivity(report);
    }

    @Test
    void diagnosticModeReportCapturesNestedSimpleMultiplicationCseReadiness() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void nestedMultiplication(int x, int y, int z, @GPUGlobal int[] output) {
                                int first = x * (y * z);
                                int second = (z * x) * y;
                                output[0] = first + second;
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertSingleMethodReport(report, "nestedMultiplication");
        assertNoOptimizerGate(report);
        assertRewriteReadySimpleArithmeticProof(report, "*");
        assertNoSimpleArithmeticNumericBoundary(report);
        assertNoSimpleArithmeticLiteralProofCandidates(report);
        assertCseCounts(report, "1", "1", "0");
        assertNoAutoVectorizationActivity(report);
    }

    @Test
    void diagnosticModeReportKeepsLiteralArithmeticOutOfSimpleCseReadiness() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void literalArithmetic(int x, int y, @GPUGlobal int[] output) {
                                int first = x + (y + 1);
                                int second = (1 + x) + y;
                                output[0] = first + second;
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertSingleMethodReport(report, "literalArithmetic");
        assertNoOptimizerGate(report);
        assertNoCseRewritePolicyPlan(report);
        assertNoReferenceOnlySimpleArithmeticProof(report);
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedCandidates")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryLiteralOperands")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryCastOperands")));
        assertTrue("true".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryHasBlockedCandidates")));
        assertTrue("{literalOperand=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedReasonCounts")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedReason.literalOperand")));
        assertTrue("stmt[0].initializer".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedLocation")));
        assertTrue("+".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedOperator")));
        assertTrue("literalOperand".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedReason")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedOperandTypes").contains("int"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedLiteralSources").contains("1"));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofCandidates")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedCandidates")));
        assertTrue("true".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofHasSafeCandidates")));
        assertTrue("safeIntLiteralNestedArithmetic".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofProofBoundary")));
        assertTrue("nonIntOrCastLiteralArithmetic".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedBoundary")));
        assertTrue("{}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedReasonCounts")));
        assertTrue("{plus:int,int,int=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts")));
        assertTrue("{}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCandidates")));
        assertTrue("true".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationHasCandidates")));
        assertTrue("preview".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationReadiness")));
        assertTrue("1".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys")));
        assertTrue("{plus:int,int,int=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationOperatorTypeCounts")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=1)=2"));
        assertTrue("literal_assoc_preview(plus:int,int,int;literals=1)".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationFirstCanonicalKey")));
        assertLiteralCanonicalizationGateBlockedPreview(report, "2", "1");
        assertTrue("stmt[0].initializer".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeLocation")));
        assertTrue("+".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeOperator")));
        assertTrue("plus:int,int,int".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeOperatorTypeKey")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeOperandTypes").contains("int"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeLiteralSources").contains("1"));
        assertCseCounts(report, "0", "0", "0");
        assertNoAutoVectorizationActivity(report);
    }

    @Test
    void diagnosticModeReportKeepsLiteralMultiplicationOutOfSimpleCseReadiness() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void literalMultiplication(int x, int y, @GPUGlobal int[] output) {
                                int first = x * (y * 2);
                                int second = (2 * x) * y;
                                output[0] = first + second;
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertSingleMethodReport(report, "literalMultiplication");
        assertNoOptimizerGate(report);
        assertNoCseRewritePolicyPlan(report);
        assertNoReferenceOnlySimpleArithmeticProof(report);
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedCandidates")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryLiteralOperands")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryCastOperands")));
        assertTrue("{literalOperand=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedReasonCounts")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofCandidates")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedCandidates")));
        assertTrue("true".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofHasSafeCandidates")));
        assertTrue("{times:int,int,int=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts")));
        assertTrue("{}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCandidates")));
        assertTrue("preview".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationReadiness")));
        assertTrue("1".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys")));
        assertTrue("{times:int,int,int=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationOperatorTypeCounts")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(times:int,int,int;literals=2)=2"));
        assertTrue("literal_assoc_preview(times:int,int,int;literals=2)".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationFirstCanonicalKey")));
        assertTrue("stmt[0].initializer".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeLocation")));
        assertTrue("*".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeOperator")));
        assertTrue("times:int,int,int".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeOperatorTypeKey")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeOperandTypes").contains("int"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstSafeLiteralSources").contains("2"));
        assertCseCounts(report, "0", "0", "0");
        assertNoAutoVectorizationActivity(report);
    }

    @Test
    void diagnosticModeReportKeepsMixedLiteralCanonicalizationPreviewKeysSeparate() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void mixedLiteralCanonicalization(int x, int y, @GPUGlobal int[] output) {
                                int plusFirst = x + (y + 1);
                                int plusSecond = (1 + x) + y;
                                int timesFirst = x * (y * 2);
                                int timesSecond = (2 * x) * y;
                                output[0] = plusFirst + plusSecond + timesFirst + timesSecond;
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertSingleMethodReport(report, "mixedLiteralCanonicalization");
        assertNoOptimizerGate(report);
        assertNoCseRewritePolicyPlan(report);
        assertNoReferenceOnlySimpleArithmeticProof(report);
        assertTrue("4".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedCandidates")));
        assertTrue("4".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryLiteralOperands")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryCastOperands")));
        assertTrue("{literalOperand=4}".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedReasonCounts")));
        assertTrue("4".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofCandidates")));
        assertTrue("4".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedCandidates")));
        assertTrue("true".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofHasSafeCandidates")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts").contains("plus:int,int,int=2"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts").contains("times:int,int,int=2"));
        assertTrue("{}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts")));
        assertTrue("4".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCandidates")));
        assertTrue("preview".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationReadiness")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationOperatorTypeCounts").contains("plus:int,int,int=2"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationOperatorTypeCounts").contains("times:int,int,int=2"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=1)=2"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(times:int,int,int;literals=2)=2"));
        assertCseCounts(report, "0", "0", "0");
        assertNoAutoVectorizationActivity(report);
    }

    @Test
    void diagnosticModeReportUsesLoweredLiteralSourcesInCanonicalizationPreviewKeys() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void loweredLiteralCanonicalization(int x, int y, @GPUGlobal int[] output) {
                                int decimalFirst = x + (y + 1_000);
                                int decimalSecond = (1_000 + x) + y;
                                int hexFirst = x + (y + 0x10);
                                int hexSecond = (0x10 + x) + y;
                                output[0] = decimalFirst + decimalSecond + hexFirst + hexSecond;
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertSingleMethodReport(report, "loweredLiteralCanonicalization");
        assertNoOptimizerGate(report);
        assertNoCseRewritePolicyPlan(report);
        assertNoReferenceOnlySimpleArithmeticProof(report);
        assertTrue("4".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCandidates")));
        assertTrue("preview".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationReadiness")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys")));
        assertTrue("{plus:int,int,int=4}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationOperatorTypeCounts")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=1000)=2"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=16)=2"));
        assertCseCounts(report, "0", "0", "0");
        assertNoAutoVectorizationActivity(report);
    }

    @Test
    void diagnosticModeReportKeepsCastArithmeticOutOfSimpleCseReadiness() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void castArithmetic(int x, int y, long z, long w, @GPUGlobal int[] output) {
                                int first = x + (y + (int) z);
                                int second = ((int) w + x) + y;
                                output[0] = first + second;
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertSingleMethodReport(report, "castArithmetic");
        assertNoOptimizerGate(report);
        assertNoCseRewritePolicyPlan(report);
        assertNoReferenceOnlySimpleArithmeticProof(report);
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryLiteralOperands")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryCastOperands")));
        assertTrue("true".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryHasBlockedCandidates")));
        assertTrue("{castOperand=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedReasonCounts")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedReason.castOperand")));
        assertTrue("stmt[0].initializer".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedLocation")));
        assertTrue("+".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedOperator")));
        assertTrue("castOperand".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedReason")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedOperandTypes").contains("int"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedCastTargets").contains("int"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryFirstBlockedCastSourceTypes").contains("long"));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeCandidates")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedCandidates")));
        assertTrue("false".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofHasSafeCandidates")));
        assertTrue("{castRequiresExplicitNumericProof=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedReasonCounts")));
        assertTrue("{}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts")));
        assertTrue("{plus:int,int,int=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedReason.castRequiresExplicitNumericProof")));
        assertTrue("castRequiresExplicitNumericProof".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedReason")));
        assertTrue("casts can change narrowing, widening, sign, or precision semantics and require explicit proof before canonicalization".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedExplanation")));
        assertTrue("plus:int,int,int".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedOperatorTypeKey")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedCastTargets").contains("int"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedCastSourceTypes").contains("long"));
        assertTrue("blocked".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersReadiness")));
        assertTrue("true".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersHasBlockers")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersTotalBlockedCandidates")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersLiteralProofBlockedCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersNumericSemanticsBlockedCandidates")));
        assertTrue("1".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersUniqueBlockerFamilies")));
        assertTrue("{castRequiresExplicitNumericProof=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersCombinedBlockerCounts")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersFamily.castRequiresExplicitNumericProof")));
        assertTrue("castRequiresExplicitNumericProof".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersFirstBlockerFamily")));
        assertTrue("casts require explicit narrowing, widening, sign, and precision proof before canonicalization".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersFirstBlockerExplanation")));
        assertCseCounts(report, "0", "0", "0");
        assertNoAutoVectorizationActivity(report);
    }

    @Test
    void diagnosticModeReportBlocksNonIntLiteralArithmeticProofs() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void nonIntLiteralArithmetic(long lx, long ly, float fx, float fy, double dx, double dy, @GPUGlobal long[] outputLong, @GPUGlobal float[] outputFloat, @GPUGlobal double[] outputDouble) {
                                long longValue = lx + (ly + 1L);
                                float floatValue = fx + (fy + 1.0f);
                                double doubleValue = dx + (dy + 1.0);
                                outputLong[0] = longValue;
                                outputFloat[0] = floatValue;
                                outputDouble[0] = doubleValue;
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertSingleMethodReport(report, "nonIntLiteralArithmetic");
        assertNoOptimizerGate(report);
        assertNoCseRewritePolicyPlan(report);
        assertTrue("3".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedCandidates")));
        assertTrue("3".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryLiteralOperands")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryCastOperands")));
        assertTrue("true".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryHasBlockedCandidates")));
        assertTrue("{literalOperand=3}".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedReasonCounts")));
        assertTrue("3".equals(report.getProperty("entry.0.cseSimpleArithmeticNumericBoundaryBlockedReason.literalOperand")));
        assertTrue("3".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeCandidates")));
        assertTrue("3".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedCandidates")));
        assertTrue("false".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofHasSafeCandidates")));
        assertTrue("{longLiteralOverflowSemanticsRequireProof=1,floatingLiteralSemanticsRequireProof=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedReasonCounts")));
        assertTrue("{}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts").contains("plus:long,long,long=1"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts").contains("plus:float,float,float=1"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts").contains("plus:double,double,double=1"));
        assertTrue("1".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedReason.longLiteralOverflowSemanticsRequireProof")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofBlockedReason.floatingLiteralSemanticsRequireProof")));
        assertTrue("longLiteralOverflowSemanticsRequireProof".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedReason")));
        assertTrue("long literal arithmetic has wider overflow semantics than the current int-only proof boundary".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedExplanation")));
        assertTrue("plus:long,long,long".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedOperatorTypeKey")));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedOperandTypes").contains("long"));
        assertTrue(report.getProperty("entry.0.cseSimpleArithmeticLiteralProofFirstBlockedLiteralSources").contains("1L"));
        assertTrue("blocked".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersReadiness")));
        assertTrue("true".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersHasBlockers")));
        assertTrue("3".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersTotalBlockedCandidates")));
        assertTrue("3".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersLiteralProofBlockedCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersNumericSemanticsBlockedCandidates")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersUniqueBlockerFamilies")));
        assertTrue("{longLiteralOverflowSemanticsRequireProof=1,floatingLiteralSemanticsRequireProof=2}".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersCombinedBlockerCounts")));
        assertTrue("1".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersFamily.longLiteralOverflowSemanticsRequireProof")));
        assertTrue("2".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersFamily.floatingLiteralSemanticsRequireProof")));
        assertTrue("longLiteralOverflowSemanticsRequireProof".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersFirstBlockerFamily")));
        assertTrue("long arithmetic requires explicit overflow and backend-equivalence proof".equals(report.getProperty("entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersFirstBlockerExplanation")));
        assertCseCounts(report, "0", "0", "0");
        assertNoAutoVectorizationActivity(report);
    }

    @Test
    void diagnosticModeWritesMultipleReportEntriesForMultipleGpuMethods() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void first(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                                for (int i = 0; i < 4; i++) {
                                    output[i] = input[i];
                                }
                            }

                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void second(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                                for (int i = 0; i < 5; i++) {
                                    output[i] = input[i];
                                }
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertTrue("2".equals(report.getProperty("entry.count")));
        assertTrue(reportContainsMethod(report, "first", "0"));
        assertTrue(reportContainsMethod(report, "second", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "cseRewritePolicyCanRewrite", "false"));
        assertTrue(reportContainsMethodCounter(report, "first", "cseRewritePolicyReadiness", "none"));
        assertTrue(reportContainsMethodCounter(report, "first", "cseRewritePolicyBlockingSkippedCandidates", "0"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationCandidates", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationCanApplyRewrite", "true"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationHasPolicyBlockedRewrite", "false"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanCandidates", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanInsertions", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanReplacements", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanOperations", "2"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanGuards", "0"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationProofRewritePlanKind", "rewritePlan"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationProofRewritePlanRewriteSafe", "true"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationProofRewritePlanDiagnostics", "0"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationProofBundleProofs", "2"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationProofBundleKinds", "rewritePlan,memoryLegality"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationProofBundleRewriteSafe", "true"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationProofBundleDiagnostics", "0"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationProofBundleUnsafeProofs", "0"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationProofBundleUnsafeProofKindCounts", "{}"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePolicyCanRewrite", "true"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePolicyReadiness", "ready"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePolicyPlannedOperations", "2"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePolicyBlockingGuards", "0"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationVectorType.int4", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRejections", "0"));
        assertTrue(reportContainsMethodCounter(report, "second", "cseRewritePolicyCanRewrite", "false"));
        assertTrue(reportContainsMethodCounter(report, "second", "cseRewritePolicyReadiness", "none"));
        assertTrue(reportContainsMethodCounter(report, "second", "cseRewritePolicyBlockingSkippedCandidates", "0"));
        assertTrue(reportContainsMethodCounter(report, "second", "autoVectorizationCandidates", "0"));
        assertTrue(reportContainsMethodCounter(report, "second", "autoVectorizationRewritePlanOperations", "0"));
        assertTrue(reportContainsMethodCounter(report, "second", "autoVectorizationRewritePlanGuards", "0"));
        assertTrue(reportContainsMethodCounter(report, "second", "autoVectorizationRejections", "1"));
        assertTrue(reportContainsMethodCounter(report, "second", "autoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT", "1"));
        assertTrue(reportContainsMethodCounterContaining(report, "second", "autoVectorizationFirstBlockingDiagnostic", "UNSUPPORTED_LANE_COUNT"));
        assertTrue(reportContainsMethodCounter(report, "second", "autoVectorizationFirstBlockingDiagnosticFamily", "rejection.UNSUPPORTED_LANE_COUNT"));
    }

    @Test
    void diagnosticModeReportCountsUnsupportedElementTypeRejections() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void mixed(@GPUGlobal int[] left, @GPUGlobal float[] right, @GPUGlobal int[] output) {
                                for (int i = 0; i < 4; i++) {
                                    output[i] = left[i] + (int) right[i];
                                }
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertTrue("1".equals(report.getProperty("entry.count")));
        assertTrue("mixed".equals(report.getProperty("entry.0.methodName")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRejections")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRejectionReason.UNSUPPORTED_ELEMENT_TYPE")));
        assertTrue(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnostic").contains("UNSUPPORTED_ELEMENT_TYPE"));
        assertTrue("rejection.UNSUPPORTED_ELEMENT_TYPE".equals(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnosticFamily")));
    }

    @Test
    void diagnosticModeReportCountsRewritePlanGuardFamilies() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void guarded(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                                output[0] = 1;
                                for (int i = 0; i < 4; i++) {
                                    output[i] = input[i];
                                }
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertTrue("1".equals(report.getProperty("entry.count")));
        assertTrue("guarded".equals(report.getProperty("entry.0.methodName")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationCandidates")));
        assertTrue("false".equals(report.getProperty("entry.0.autoVectorizationCanApplyRewrite")));
        assertTrue("true".equals(report.getProperty("entry.0.autoVectorizationHasPolicyBlockedRewrite")));
        assertTrue("0".equals(report.getProperty("entry.0.autoVectorizationRewritePlanOperations")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRewritePlanGuards")));
        assertTrue("false".equals(report.getProperty("entry.0.autoVectorizationRewritePolicyCanRewrite")));
        assertTrue("blockedByGuard".equals(report.getProperty("entry.0.autoVectorizationRewritePolicyReadiness")));
        assertTrue("2".equals(report.getProperty("entry.0.autoVectorizationRewritePolicyPlannedOperations")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRewritePolicyBlockingGuards")));
        assertTrue("neighborTargetWrite".equals(report.getProperty("entry.0.autoVectorizationRewritePolicyFirstBlockingGuardFamily")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRewritePlanGuardFamily.neighborTargetWrite")));
        assertTrue("rewritePlan".equals(report.getProperty("entry.0.autoVectorizationProofRewritePlanKind")));
        assertTrue("guarded".equals(report.getProperty("entry.0.autoVectorizationProofRewritePlanLocation")));
        assertTrue("false".equals(report.getProperty("entry.0.autoVectorizationProofRewritePlanRewriteSafe")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationProofRewritePlanDiagnostics")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationProofRewritePlanGuardFamily.neighborTargetWrite")));
        assertTrue(report.getProperty("entry.0.autoVectorizationProofRewritePlanSummary").contains("guardFamilies={neighborTargetWrite=1}"));
        assertTrue("4".equals(report.getProperty("entry.0.autoVectorizationProofBundleProofs")));
        assertTrue("rewritePlan,mutation,controlFlowBoundary,memoryLegality".equals(report.getProperty("entry.0.autoVectorizationProofBundleKinds")));
        assertTrue("false".equals(report.getProperty("entry.0.autoVectorizationProofBundleRewriteSafe")));
        assertTrue("2".equals(report.getProperty("entry.0.autoVectorizationProofBundleDiagnostics")));
        assertTrue("2".equals(report.getProperty("entry.0.autoVectorizationProofBundleUnsafeProofs")));
        assertTrue("{rewritePlan=1,mutation=1}".equals(report.getProperty("entry.0.autoVectorizationProofBundleUnsafeProofKindCounts")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationProofBundleUnsafeProofKind.rewritePlan")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationProofBundleUnsafeProofKind.mutation")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationProofBundleGuardFamily.neighborTargetWrite")));
        assertTrue(report.getProperty("entry.0.autoVectorizationProofBundleSummary").contains("guardFamilies={neighborTargetWrite=1}"));
        assertTrue(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnostic").contains("writes target array `output`"));
        assertTrue("guard.neighborTargetWrite".equals(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnosticFamily")));
    }

    @Test
    void diagnosticModeReportCountsControlFlowRewritePlanGuardFamilies() throws IOException {
        CompilationResult result = compileWithIrValidationMode(
                "diagnostic",
                "quiet",
                "reports/javatogpu-ir-validation.properties",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class Demo {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            void guarded(int flag, @GPUGlobal int[] input, @GPUGlobal int[] output) {
                                if (flag != 0) {
                                    output[0] = output[0] + 1;
                                }
                                for (int i = 0; i < 4; i++) {
                                    output[i] = input[i];
                                }
                            }
                        }
                        """
        );

        assertTrue(result.success(), result.diagnosticMessages());
        Properties report = loadReport(result.generatedOutputDir().resolve("reports/javatogpu-ir-validation.properties"));

        assertTrue("1".equals(report.getProperty("entry.count")));
        assertTrue("guarded".equals(report.getProperty("entry.0.methodName")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationCandidates")));
        assertTrue("0".equals(report.getProperty("entry.0.autoVectorizationRewritePlanOperations")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRewritePlanGuards")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRewritePlanGuardFamily.controlFlowBoundary")));
        assertTrue("false".equals(report.getProperty("entry.0.autoVectorizationProofRewritePlanRewriteSafe")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationProofRewritePlanDiagnostics")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationProofRewritePlanGuardFamily.controlFlowBoundary")));
        assertTrue("false".equals(report.getProperty("entry.0.autoVectorizationProofBundleRewriteSafe")));
        assertTrue("2".equals(report.getProperty("entry.0.autoVectorizationProofBundleDiagnostics")));
        assertTrue("2".equals(report.getProperty("entry.0.autoVectorizationProofBundleUnsafeProofs")));
        assertTrue("{rewritePlan=1,controlFlowBoundary=1}".equals(report.getProperty("entry.0.autoVectorizationProofBundleUnsafeProofKindCounts")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationProofBundleUnsafeProofKind.rewritePlan")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationProofBundleUnsafeProofKind.controlFlowBoundary")));
        assertTrue("2".equals(report.getProperty("entry.0.autoVectorizationProofBundleGuardFamily.controlFlowBoundary")));
        assertTrue(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnostic").contains("control-flow boundary"));
        assertTrue("guard.controlFlowBoundary".equals(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnosticFamily")));
    }

    private CompilationResult compileWithIrValidationMode(String mode, String diagnosticPolicy) throws IOException {
        return compileWithIrValidationMode(mode, diagnosticPolicy, null);
    }

    private CompilationResult compileWithIrValidationMode(
            String mode,
            String diagnosticPolicy,
            String reportPath
    ) throws IOException {
        return compileWithIrValidationMode(mode, diagnosticPolicy, reportPath, defaultSource());
    }

    private CompilationResult compileWithIrValidationMode(
            String mode,
            String diagnosticPolicy,
            String reportPath,
            String source
    ) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-ir-validation-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-ir-validation-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = new java.util.ArrayList<>(List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString(),
                    "-Ajavatogpu.irValidation=" + mode
            ));
            if (diagnosticPolicy != null) {
                options.add("-Ajavatogpu.irValidationDiagnostics=" + diagnosticPolicy);
            }
            if (reportPath != null) {
                options.add("-Ajavatogpu.irValidationReport=" + reportPath);
            }
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(new StringJavaFileObject("sample.Demo", source))
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));
            return new CompilationResult(Boolean.TRUE.equals(task.call()), generatedOutputDir, diagnosticMessages(diagnostics));
        }
    }

    private String defaultSource() {
        return """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        for (int i = 0; i < 5; i++) {
                            output[i] = input[i];
                        }
                    }
                }
                """;
    }

    private Properties loadReport(Path reportPath) throws IOException {
        assertTrue(Files.exists(reportPath));
        Properties report = new Properties();
        try (var reader = Files.newBufferedReader(reportPath)) {
            report.load(reader);
        }
        return report;
    }

    private boolean reportContainsMethod(Properties report, String methodName, String optimizerDiagnostics) {
        for (int index = 0; index < Integer.parseInt(report.getProperty("entry.count")); index++) {
            if (methodName.equals(report.getProperty("entry." + index + ".methodName"))
                    && optimizerDiagnostics.equals(report.getProperty("entry." + index + ".optimizerDiagnostics"))) {
                return true;
            }
        }
        return false;
    }

    private boolean reportContainsMethodCounter(
            Properties report,
            String methodName,
            String counterName,
            String expectedValue
    ) {
        for (int index = 0; index < Integer.parseInt(report.getProperty("entry.count")); index++) {
            if (methodName.equals(report.getProperty("entry." + index + ".methodName"))) {
                return expectedValue.equals(report.getProperty("entry." + index + "." + counterName));
            }
        }
        return false;
    }

    private boolean reportContainsMethodCounterContaining(
            Properties report,
            String methodName,
            String counterName,
            String expectedValueFragment
    ) {
        for (int index = 0; index < Integer.parseInt(report.getProperty("entry.count")); index++) {
            if (methodName.equals(report.getProperty("entry." + index + ".methodName"))) {
                String actualValue = report.getProperty("entry." + index + "." + counterName);
                return actualValue != null && actualValue.contains(expectedValueFragment);
            }
        }
        return false;
    }

    private void assertSingleMethodReport(Properties report, String methodName) {
        assertEntryValue(report, "entry.count", "1");
        assertEntryValue(report, "entry.0.methodName", methodName);
        assertEntryValue(report, "entry.0.optimizerDiagnostics", "0");
    }

    private void assertNoOptimizerGate(Properties report) {
        assertEntryValue(report, "entry.0.optimizerGateBlocked", "false");
    }

    private void assertRewriteReadySimpleArithmeticProof(Properties report, String operator) {
        assertEntryValue(report, "entry.0.cseRewritePolicyCanRewrite", "true");
        assertEntryValue(report, "entry.0.cseRewritePolicyReadiness", "ready");
        assertEntryValue(report, "entry.0.cseRewritePolicyPlans", "1");
        assertEntryValue(report, "entry.0.cseRewritePolicyInsertions", "1");
        assertEntryValue(report, "entry.0.cseRewritePolicyReplacements", "1");
        assertEntryValue(report, "entry.0.cseRewritePolicyBlockingSkippedCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofProvenCandidates", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofProvenInsertions", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofProvenReplacements", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofHasProofs", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofProofBoundary", "referenceOnlyNestedArithmetic");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofBlockedBoundary", "literalsAndCastsRequireTypedNumericProof");
        assertEntryValueStartsWith(report, "entry.0.cseSimpleArithmeticProofFirstProvenFingerprint", "binary_assoc_simple(" + operator);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofFirstProvenAnchor", "stmt[0].initializer");
    }

    private void assertNoCseRewritePolicyPlan(Properties report) {
        assertEntryValue(report, "entry.0.cseRewritePolicyCanRewrite", "false");
        assertEntryValue(report, "entry.0.cseRewritePolicyReadiness", "none");
        assertEntryValue(report, "entry.0.cseRewritePolicyPlans", "0");
        assertEntryValue(report, "entry.0.cseRewritePolicyInsertions", "0");
        assertEntryValue(report, "entry.0.cseRewritePolicyReplacements", "0");
        assertEntryValue(report, "entry.0.cseRewritePolicyBlockingSkippedCandidates", "0");
    }

    private void assertNoReferenceOnlySimpleArithmeticProof(Properties report) {
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofProvenCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofHasProofs", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofProofBoundary", "referenceOnlyNestedArithmetic");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticProofBlockedBoundary", "literalsAndCastsRequireTypedNumericProof");
    }

    private void assertNoSimpleArithmeticNumericBoundary(Properties report) {
        assertEntryValue(report, "entry.0.cseSimpleArithmeticNumericBoundaryBlockedCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticNumericBoundaryLiteralOperands", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticNumericBoundaryCastOperands", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticNumericBoundaryHasBlockedCandidates", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticNumericBoundaryBlockedReasonCounts", "{}");
    }

    private void assertNoSimpleArithmeticLiteralProofCandidates(Properties report) {
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralProofCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralProofSafeCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralProofBlockedCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralProofHasSafeCandidates", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralProofProofBoundary", "safeIntLiteralNestedArithmetic");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralProofBlockedBoundary", "nonIntOrCastLiteralArithmetic");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralProofSafeOperatorTypeCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralProofBlockedOperatorTypeCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationHasCandidates", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationUniqueCanonicalKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationOperatorTypeCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationCanonicalKeyCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofProvenCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofBlockedCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofFullyProven", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofProvenOperatorTypeCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofBlockedReasonCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersReadiness", "clear");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersHasBlockers", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersTotalBlockedCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersCombinedBlockerCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceSuccessful", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceEquivalent", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceInputCases", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceComparedOutputs", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceDiagnostics", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceHasDiagnostics", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalencePreviewCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceUniqueCanonicalKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceNumericSemanticsFullyProven", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceNumericSemanticsReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceCanonicalKeyCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceFirstDiagnostic", "literal canonicalization runtime equivalence not run");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateCanPromoteToFingerprint", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGatePreviewCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateUniqueCanonicalKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateBlocksRewriteReadiness", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateBlockingReasons", "[noPreviewCandidates]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateBlockingReasonCount", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateFirstBlockingReason", "noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionReadyForProduction", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionBlockedByProductionDisablementOnly", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionPreviewCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionUniqueCanonicalKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionNumericSemanticsFullyProven", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionRuntimeEquivalenceSuccessful", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionCanonicalizationGateAllowsPromotion", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionBlockingReasons", "[noPreviewCandidates]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionBlockingReasonCount", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionFirstBlockingReason", "noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityPreviewCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityUniquePreviewKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityProductionCandidateOverlaps", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityPreviewOnlyKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityHasPreviewOnlyKeys", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityReadyForProduction", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityBlockers", "[noPreviewCandidates,fingerprintDecisionNotReadyForProduction]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityBlockerCount", "2");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityFirstBlocker", "noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementVerdict", "notReady/noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementPreviewCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementUniqueCanonicalKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementNumericSemanticsFullyProven", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementRuntimeEquivalenceSuccessful", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementReadyForProduction", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementDecisionReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementParityReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementPreviewOnlyKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementHasPreviewOnlyKeys", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementBlockers", "[noPreviewCandidates,fingerprintDecisionNotReadyForProduction]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementBlockerCount", "2");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementFirstBlocker", "noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightEligibleCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightBlockedCandidates", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightUniqueCanonicalKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightNumericSemanticsFullyProven", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightRuntimeEquivalenceSuccessful", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightPreviewOnlyBlastRadius", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightEnablementVerdict", "notReady/noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightBlockerCounts", "{noPreviewCandidates=1}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightFirstBlockedLocation", "<method>");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightFirstBlockedCanonicalKey", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightFirstBlockedReason", "noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewEligibleOperations", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewBlockedOperations", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewUniqueCanonicalKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewHasEligibleOperations", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewHasBlockedOperations", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewOperationShapeCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewBlockedOperationShapeCounts", "{literalArithmeticCse(none)=1}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewBlockerCounts", "{noPreviewCandidates=1}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewFirstBlockedLocation", "<method>");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewFirstBlockedCanonicalKey", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewFirstBlockedShape", "literalArithmeticCse(none)");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewFirstBlockedReason", "noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistVerdict", "notReady/noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistReadyForProductionMutation", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistEligibleCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistBlockedCandidates", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistEligibleOperations", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistBlockedOperations", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistUniqueCanonicalKeys", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistReadyForProduction", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistEnablementVerdict", "notReady/noPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistPreflightReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistOperationPreviewReadiness", "none");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistRemainingWork", "[collectPreviewCandidates,proveTypedNumericSemantics,runRuntimeEquivalenceEvidence,clearRewritePreflightBlockers,clearRewriteOperationPreviewBlockers,enableProductionFingerprintIntegration]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistRemainingWorkCount", "6");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistFirstRemainingWork", "collectPreviewCandidates");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckVerdict", "consistent");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckConsistent", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckChecks", "12");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckFailedChecks", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckFailedCheckList", "[]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckFailedCheckCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckCiSummaryLine", "literal consistency check passed: 12 checks");
    }

    private void assertLiteralCanonicalizationGateBlockedPreview(Properties report, String previewCandidates, String uniqueCanonicalKeys) {
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateCanPromoteToFingerprint", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateReadiness", "blockedPreview");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGatePreviewCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateUniqueCanonicalKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateBlocksRewriteReadiness", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateBlockingReasons", "[runtimeEquivalenceNotProven,fingerprintIntegrationDisabled]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateBlockingReasonCount", "2");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralCanonicalizationGateFirstBlockingReason", "runtimeEquivalenceNotProven");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofProvenCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofBlockedCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofFullyProven", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralNumericSemanticsProofReadiness", "proven");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersReadiness", "clear");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersHasBlockers", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersTotalBlockedCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralTypedNumericBlockersCombinedBlockerCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceSuccessful", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceEquivalent", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceReadiness", "notProven");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceInputCases", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceComparedOutputs", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceDiagnostics", "1");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceHasDiagnostics", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalencePreviewCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceUniqueCanonicalKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceNumericSemanticsFullyProven", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceNumericSemanticsReadiness", "proven");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRuntimeEquivalenceFirstDiagnostic", "literal canonicalization runtime equivalence not run");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionReadyForProduction", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionReadiness", "blockedPreview");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionBlockedByProductionDisablementOnly", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionPreviewCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionUniqueCanonicalKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionNumericSemanticsFullyProven", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionRuntimeEquivalenceSuccessful", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionCanonicalizationGateAllowsPromotion", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionBlockingReasons", "[runtimeEquivalenceNotProven,productionFingerprintIntegrationDisabled]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionBlockingReasonCount", "2");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintDecisionFirstBlockingReason", "runtimeEquivalenceNotProven");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityReadiness", "previewOnly");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityPreviewCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityUniquePreviewKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityProductionCandidateOverlaps", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityPreviewOnlyKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityHasPreviewOnlyKeys", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityReadyForProduction", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityBlockers", "[previewOnlyKeysNotInProductionFingerprints,fingerprintDecisionNotReadyForProduction]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityBlockerCount", "2");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralFingerprintParityFirstBlocker", "previewOnlyKeysNotInProductionFingerprints");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementVerdict", "notReady/runtimeMissing");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementPreviewCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementUniqueCanonicalKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementNumericSemanticsFullyProven", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementRuntimeEquivalenceSuccessful", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementReadyForProduction", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementDecisionReadiness", "blockedPreview");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementParityReadiness", "previewOnly");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementPreviewOnlyKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementHasPreviewOnlyKeys", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementBlockers", "[runtimeEquivalenceNotProven,productionFingerprintIntegrationDisabled,previewOnlyKeysNotInProductionFingerprints,fingerprintDecisionNotReadyForProduction]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementBlockerCount", "4");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralEnablementFirstBlocker", "runtimeEquivalenceNotProven");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightReadiness", "blocked");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightEligibleCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightBlockedCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightUniqueCanonicalKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightNumericSemanticsFullyProven", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightRuntimeEquivalenceSuccessful", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightPreviewOnlyBlastRadius", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightEnablementVerdict", "notReady/runtimeMissing");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewritePreflightFirstBlockedReason", "runtimeEquivalenceNotProven");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewReadiness", "blockedPreview");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewEligibleOperations", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewBlockedOperations", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewUniqueCanonicalKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewHasEligibleOperations", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewHasBlockedOperations", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewOperationShapeCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewBlockedOperationShapeCounts", "{literalArithmeticCse(plus:int,int,int)=" + previewCandidates + "}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewFirstBlockedShape", "literalArithmeticCse(plus:int,int,int)");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralRewriteOperationPreviewFirstBlockedReason", "runtimeEquivalenceNotProven");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistVerdict", "notReady/evidenceIncomplete");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistReadyForProductionMutation", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistEligibleCandidates", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistBlockedCandidates", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistEligibleOperations", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistBlockedOperations", previewCandidates);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistUniqueCanonicalKeys", uniqueCanonicalKeys);
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistEvidenceComplete", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistReadyForProduction", "false");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistEnablementVerdict", "notReady/runtimeMissing");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistPreflightReadiness", "blocked");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistOperationPreviewReadiness", "blockedPreview");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistRemainingWorkCount", "5");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralPromotionChecklistFirstRemainingWork", "runRuntimeEquivalenceEvidence");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckVerdict", "consistent");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckConsistent", "true");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckChecks", "12");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckFailedChecks", "0");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckFailedCheckList", "[]");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckFailedCheckCounts", "{}");
        assertEntryValue(report, "entry.0.cseSimpleArithmeticLiteralConsistencyCheckCiSummaryLine", "literal consistency check passed: 12 checks");
    }

    private void assertCseCounts(Properties report, String insertions, String replacements, String skipped) {
        assertEntryValue(report, "entry.0.cseInsertions", insertions);
        assertEntryValue(report, "entry.0.cseReplacements", replacements);
        assertEntryValue(report, "entry.0.cseSkipped", skipped);
    }

    private void assertNoAutoVectorizationActivity(Properties report) {
        assertEntryValue(report, "entry.0.autoVectorizationCandidates", "0");
        assertEntryValue(report, "entry.0.autoVectorizationRejections", "0");
        assertEntryValue(report, "entry.0.autoVectorizationRewritePlanGuards", "0");
    }

    private void assertEntryValue(Properties report, String key, String expectedValue) {
        assertTrue(expectedValue.equals(report.getProperty(key)), () -> key + " expected=" + expectedValue + " actual=" + report.getProperty(key));
    }

    private void assertEntryValueStartsWith(Properties report, String key, String expectedPrefix) {
        String actualValue = report.getProperty(key);
        assertTrue(actualValue != null && actualValue.startsWith(expectedPrefix), () -> key + " expectedPrefix=" + expectedPrefix + " actual=" + actualValue);
    }

    private String diagnosticMessages(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getMessage(null))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private record CompilationResult(boolean success, Path generatedOutputDir, String diagnosticMessages) {
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        private StringJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
