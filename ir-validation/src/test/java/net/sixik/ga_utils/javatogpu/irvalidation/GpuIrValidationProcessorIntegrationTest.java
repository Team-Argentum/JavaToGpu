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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrValidationProcessorIntegrationTest {
    @Test
    void diagnosticModeRunsThroughJavacProcessorWithoutFailingBuild() throws IOException {
        CompilationResult result = compileWithIrValidationMode("diagnostic");

        assertTrue(result.success(), result.diagnosticMessages());
        assertTrue(Files.exists(result.generatedOutputDir().resolve("javatogpu/sample/Demo/kernel.cl")));
    }

    @Test
    void strictOptimizerModeFailsThroughJavacProcessorOnOptimizerDiagnostics() throws IOException {
        CompilationResult result = compileWithIrValidationMode("strictOptimizer");

        assertFalse(result.success(), result.diagnosticMessages());
        assertTrue(result.diagnosticMessages().contains("IR optimization validation failed for kernel"));
        assertTrue(result.diagnosticMessages().contains("UNSUPPORTED_LANE_COUNT"));
    }

    private CompilationResult compileWithIrValidationMode(String mode) throws IOException {
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
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString(),
                    "-Ajavatogpu.irValidation=" + mode
            );
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
