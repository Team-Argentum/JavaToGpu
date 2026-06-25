package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.processors.GpuCompilerProcessor;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuMethodBodyRewriterTest {

    @Test
    void rewritesAnnotatedMethodToGeneratedLauncherInvocation() throws IOException {
        assertRewrittenKernelInvocation(
                "net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal",
                "net.sixik.ga_utils.javatogpu.api.annotations.GPU"
        );
    }

    private static void assertRewrittenKernelInvocation(String globalAnnotationImport, String gpuAnnotationName) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-rewriter-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-rewriter-generated");

        String source = String.format("""
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import %s;

                public class Demo {
                    @%s
                    public static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        output[0] = 123.0f;
                    }
                }
                """, globalAnnotationImport, gpuAnnotationName);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Demo", source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));
            assertTrue(task.call());
        }

        Path ownerClassFile = classOutputDir.resolve("sample/Demo.class");
        assertTrue(Files.exists(ownerClassFile));
        assertTrue(new GpuMethodBodyRewriter().rewriteClassFile(ownerClassFile));

        AtomicReference<GpuKernelInvocation> capturedInvocation = new AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classOutputDir.toUri().toURL()},
                GpuMethodBodyRewriterTest.class.getClassLoader()
        )) {
            Class<?> ownerClass = Class.forName("sample.Demo", true, classLoader);
            float[] input = new float[]{1.0f, 2.0f};
            float[] output = new float[]{0.0f, 0.0f};
            ownerClass.getMethod("kernel", float[].class, float[].class).invoke(null, input, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals("jtg_kernel", invocation.descriptor().kernelName());
            assertTrue(Arrays.equals(new Object[]{input, output}, invocation.arguments()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke rewritten GPU owner method reflectively", exception);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
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
