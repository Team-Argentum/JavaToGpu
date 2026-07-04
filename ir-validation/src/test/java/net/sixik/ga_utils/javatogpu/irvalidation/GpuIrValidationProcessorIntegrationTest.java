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
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRejections=1"));
        assertFalse(result.diagnosticMessages().contains("UNSUPPORTED_LANE_COUNT"));
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
        assertTrue(result.diagnosticMessages().contains("autoVectorizationRewritePlanGuardFamilies={controlFlowBoundary=1}"));
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
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRejections")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT")));
        assertTrue(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnostic").contains("UNSUPPORTED_LANE_COUNT"));
        assertTrue("rejection.UNSUPPORTED_LANE_COUNT".equals(report.getProperty("entry.0.autoVectorizationFirstBlockingDiagnosticFamily")));
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
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationCandidates", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanCandidates", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanInsertions", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanReplacements", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanOperations", "2"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRewritePlanGuards", "0"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationVectorType.int4", "1"));
        assertTrue(reportContainsMethodCounter(report, "first", "autoVectorizationRejections", "0"));
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
        assertTrue("0".equals(report.getProperty("entry.0.autoVectorizationRewritePlanOperations")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRewritePlanGuards")));
        assertTrue("1".equals(report.getProperty("entry.0.autoVectorizationRewritePlanGuardFamily.neighborTargetWrite")));
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
