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
    }

    private CompilationResult compileWithIrValidationMode(String mode, String diagnosticPolicy) throws IOException {
        return compileWithIrValidationMode(mode, diagnosticPolicy, null);
    }

    private CompilationResult compileWithIrValidationMode(
            String mode,
            String diagnosticPolicy,
            String reportPath
    ) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-ir-validation-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-ir-validation-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
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
