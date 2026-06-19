package net.sixik.ga_utils.javatogpu.processors;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuGeneratedLauncherInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackend;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.DiagnosticCollector;
import javax.tools.Diagnostic;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuCompilerProcessorTest {

    @Test
    void generatesOpenClKernelFileDuringCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-processor-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-processor-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal(constant = true) float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        float value = input[id];
                        output[id] = GPU.sin(value) * GPU.cos(value);
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global const float* input, __global float* output) {
                    int id = get_global_id(0);
                    float value = input[id];
                    output[id] = (sin(value) * cos(value));
                }""", Files.readString(kernelPath));

        Path launcherSourcePath = generatedOutputDir.resolve("sample/generated/Demo_kernel_GpuLauncher.java");
        assertTrue(Files.exists(launcherSourcePath));
        String launcherSource = Files.readString(launcherSourcePath);
        assertTrue(launcherSource.contains("public final class Demo_kernel_GpuLauncher"));
        assertTrue(launcherSource.contains("public static final String KERNEL_NAME = \"jtg_kernel\";"));
        assertTrue(launcherSource.contains("public static final String KERNEL_RESOURCE = \"javatogpu/sample/Demo/kernel.cl\";"));
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"input\", \"float[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_ONLY)"));
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"output\", \"float[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_WRITE)"));
        assertTrue(launcherSource.contains("public static void invoke(float[] input, float[] output)"));

        Path launcherClassPath = classOutputDir.resolve("sample/generated/Demo_kernel_GpuLauncher.class");
        assertTrue(Files.exists(launcherClassPath));

        AtomicReference<GpuKernelInvocation> capturedInvocation = new AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classOutputDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> launcherClass = Class.forName("sample.generated.Demo_kernel_GpuLauncher", true, classLoader);
            Class<?> ownerClass = Class.forName("sample.Demo", true, classLoader);
            float[] input = new float[]{1.0f, 2.0f};
            float[] output = new float[]{0.0f, 0.0f};
            launcherClass.getMethod("invoke", float[].class, float[].class).invoke(null, input, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            GpuKernelDescriptor descriptor = invocation.descriptor();
            assertEquals("jtg_kernel", descriptor.kernelName());
            assertEquals("javatogpu/sample/Demo/kernel.cl", descriptor.kernelResource());
            assertEquals(Files.readString(kernelPath), descriptor.kernelSource());
            assertEquals(2, descriptor.parameterDescriptors().size());
            assertEquals(GpuKernelParameterAccess.READ_ONLY, descriptor.parameterDescriptors().get(0).access());
            assertEquals(GpuKernelParameterAccess.READ_WRITE, descriptor.parameterDescriptors().get(1).access());
            assertTrue(Arrays.equals(new Object[]{input, output}, invocation.arguments()));

            capturedInvocation.set(null);
            GpuGeneratedLauncherInvoker.invoke(ownerClass, "kernel", input, output);
            GpuKernelInvocation reflectedInvocation = capturedInvocation.get();
            assertEquals("jtg_kernel", reflectedInvocation.descriptor().kernelName());
            assertTrue(Arrays.equals(new Object[]{input, output}, reflectedInvocation.arguments()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke generated launcher reflectively", exception);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatesLauncherForNestedGpuOwnerWithoutInvalidPackage() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-nested-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-nested-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Outer {
                    static class Inner {
                        @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                        static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                            output[0] = GPU.sin(input[0]);
                        }
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Outer", source);
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null, List.of(sourceFile));
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(task.call());
        }

        Path launcherSourcePath = generatedOutputDir.resolve("sample/generated/Outer_Inner_kernel_GpuLauncher.java");
        assertTrue(Files.exists(launcherSourcePath));

        AtomicReference<GpuKernelInvocation> capturedInvocation = new AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classOutputDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> innerClass = Class.forName("sample.Outer$Inner", true, classLoader);
            float[] input = new float[]{1.0f};
            float[] output = new float[]{0.0f};

            GpuGeneratedLauncherInvoker.invoke(innerClass, "kernel", input, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals("jtg_kernel", invocation.descriptor().kernelName());
            assertTrue(Arrays.equals(new Object[]{input, output}, invocation.arguments()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke generated nested launcher reflectively", exception);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void rejectsNonVoidGpuMethodDuringCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-nonvoid-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-nonvoid-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    float kernel(@GPUGlobal float[] input) {
                        return input[0];
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Demo", source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(task.call());
        }

        assertTrue(
                diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                        String.valueOf(diagnostic.getMessage(null)).contains(
                                "Non-void @GPU methods are not supported in the current pipeline; use a void method with output buffers"
                        )
                )
        );
    }

    @Test
    void generatesKernelWithIfElseBranching() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-if-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-if-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        if (input[id] > 0.0f) {
                            output[id] = GPU.sin(input[id]);
                        } else {
                            output[id] = GPU.cos(input[id]);
                        }
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    if ((input[id] > 0.0F)) {
                        output[id] = sin(input[id]);
                    } else {
                        output[id] = cos(input[id]);
                    }
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithLogicalConditionBranching() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-logical-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-logical-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        if ((input[id] > 0.0f && input[id] < 10.0f) || !(input[id] > 100.0f)) {
                            output[id] = GPU.sin(input[id]);
                        } else {
                            output[id] = GPU.cos(input[id]);
                        }
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    if ((((input[id] > 0.0F) && (input[id] < 10.0F)) || (!(input[id] > 100.0F)))) {
                        output[id] = sin(input[id]);
                    } else {
                        output[id] = cos(input[id]);
                    }
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithElseIfBranching() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-elseif-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-elseif-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        if (input[id] > 10.0f) {
                            output[id] = GPU.sin(input[id]);
                        } else if (input[id] > 0.0f) {
                            output[id] = GPU.cos(input[id]);
                        } else {
                            output[id] = input[id];
                        }
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    if ((input[id] > 10.0F)) {
                        output[id] = sin(input[id]);
                    } else {
                        if ((input[id] > 0.0F)) {
                            output[id] = cos(input[id]);
                        } else {
                            output[id] = input[id];
                        }
                    }
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithTernaryExpression() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-ternary-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-ternary-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = input[id] > 0.0f ? GPU.sin(input[id]) : GPU.cos(input[id]);
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = ((input[id] > 0.0F) ? sin(input[id]) : cos(input[id]));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithDivisionModuloAndUnaryMinus() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-arith-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-arith-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        int value = -input[id];
                        output[id] = (value / 2) % 3;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    int value = (-input[id]);
                    output[id] = ((value / 2) % 3);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithBitwiseIntegerOperations() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-bitwise-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-bitwise-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = ((~input[id]) << 1) ^ ((input[id] >> 1) | (input[id] & 7));
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    output[id] = (((~input[id]) << 1) ^ ((input[id] >> 1) | (input[id] & 7)));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithPrimitiveCastExpression() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-cast-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-cast-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int i = 1;
                        output[0] = GPU.sin((float) i);
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* output) {
                    int i = 1;
                    output[0] = sin(((float) i));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithCCodeHelperMethod() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-ccode-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-ccode-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @CCode(inline = true)
                    float square(float value) {
                        return value * value;
                    }

                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = square(input[id]);
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                inline float jtg_fn_Demo_square_float(float value);

                inline float jtg_fn_Demo_square_float(float value) {
                    return (value * value);
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_Demo_square_float(input[id]);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithExternalInlineCCodeHelperMethod() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-external-ccode-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-external-ccode-generated");

        String helperSource = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;

                public class KernelMath {
                    @CCode(inline = true)
                    public static float square(float value) {
                        return value * value;
                    }
                }
                """;

        String kernelSource = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = KernelMath.square(input[id]);
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject helperFile = new StringJavaFileObject("sample.KernelMath", helperSource);
            JavaFileObject kernelFile = new StringJavaFileObject("sample.Demo", kernelSource);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(helperFile, kernelFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(task.call());
        }

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                inline float jtg_fn_KernelMath_square_float(float value);

                inline float jtg_fn_KernelMath_square_float(float value) {
                    return (value * value);
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_KernelMath_square_float(input[id]);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithScopedExternalHelperWhenAnotherOwnerSharesSignature() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-scoped-ccode-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-scoped-ccode-generated");

        String kernelMathSource = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;

                public class KernelMath {
                    @CCode(inline = true)
                    public static float square(float value) {
                        return value * value;
                    }
                }
                """;

        String fastMathSource = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;

                public class FastMath {
                    @CCode(inline = true)
                    public static float square(float value) {
                        return value + value;
                    }
                }
                """;

        String kernelSource = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = KernelMath.square(input[id]);
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject kernelMathFile = new StringJavaFileObject("sample.KernelMath", kernelMathSource);
            JavaFileObject fastMathFile = new StringJavaFileObject("sample.FastMath", fastMathSource);
            JavaFileObject kernelFile = new StringJavaFileObject("sample.Demo", kernelSource);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(kernelMathFile, fastMathFile, kernelFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(task.call());
        }

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        String kernel = Files.readString(kernelPath);
        assertTrue(kernel.contains("inline float jtg_fn_KernelMath_square_float(float value);"));
        assertFalse(kernel.contains("jtg_fn_FastMath_square_float"));
        assertTrue(kernel.contains("output[id] = jtg_fn_KernelMath_square_float(input[id]);"));
    }

    @Test
    void generatesKernelWithTransitiveHelperAndOmitsUnusedHelper() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-transitive-ccode-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-transitive-ccode-generated");

        String helperSource = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;

                public class KernelMath {
                    @CCode(inline = true)
                    public static float square(float value) {
                        return value * value;
                    }

                    @CCode(inline = true)
                    public static float cube(float value) {
                        return square(value) * value;
                    }

                    @CCode(inline = true)
                    public static float unused(float value) {
                        return value + 1.0f;
                    }
                }
                """;

        String kernelSource = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = KernelMath.cube(input[id]);
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject helperFile = new StringJavaFileObject("sample.KernelMath", helperSource);
            JavaFileObject kernelFile = new StringJavaFileObject("sample.Demo", kernelSource);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(helperFile, kernelFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(task.call());
        }

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        String kernel = Files.readString(kernelPath);
        assertTrue(kernel.contains("inline float jtg_fn_KernelMath_square_float(float value);"));
        assertTrue(kernel.contains("inline float jtg_fn_KernelMath_cube_float(float value);"));
        assertTrue(kernel.contains("return (jtg_fn_KernelMath_square_float(value) * value);"));
        assertTrue(kernel.contains("output[id] = jtg_fn_KernelMath_cube_float(input[id]);"));
        assertFalse(kernel.contains("jtg_fn_KernelMath_unused_float"));
    }

    @Test
    void generatesKernelWithFloatPtrHelperMutation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-floatptr-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-floatptr-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.FloatPtr;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @CCode
                    static void mutate(FloatPtr ptr) {
                        ptr.value = 50.0f;
                    }

                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        FloatPtr ptr = new FloatPtr();
                        ptr.value = input[id];
                        mutate(ptr);
                        output[id] = GPU.tan(ptr.value);
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                void jtg_fn_Demo_mutate_FloatPtr(float* ptr);

                void jtg_fn_Demo_mutate_FloatPtr(float* ptr) {
                    (*ptr) = 50.0F;
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float ptr = 0.0f;
                    ptr = input[id];
                    jtg_fn_Demo_mutate_FloatPtr((&ptr));
                    output[id] = tan(ptr);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithNativeCCodeHelpers() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-nativeccode-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-nativeccode-generated");

        String source = "package sample;\n"
                + "\n"
                + "import net.sixik.ga_utils.javatogpu.api.FloatPtr;\n"
                + "import net.sixik.ga_utils.javatogpu.api.GPU;\n"
                + "import net.sixik.ga_utils.javatogpu.api.anotations.CCode;\n"
                + "import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;\n"
                + "\n"
                + "public class Demo {\n"
                + "    @CCode(code = \"\"\"\n"
                + "            return a + b * 50.0f;\n"
                + "            \"\"\")\n"
                + "    static native float myMath(float a, float b);\n"
                + "\n"
                + "    @CCode(code = \"\"\"\n"
                + "            return (*a) + (*b) * 50.0f;\n"
                + "            \"\"\")\n"
                + "    static native float myMathPtr(FloatPtr a, FloatPtr b);\n"
                + "\n"
                + "    @net.sixik.ga_utils.javatogpu.api.anotations.GPU\n"
                + "    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {\n"
                + "        int id = GPU.get_global_id(0);\n"
                + "        FloatPtr ptr = new FloatPtr();\n"
                + "        ptr.value = input[id];\n"
                + "        output[id] = myMath(input[id], 2.0f) + myMathPtr(ptr, ptr);\n"
                + "    }\n"
                + "}\n";

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                float jtg_fn_Demo_myMath_float_float(float a, float b);
                float jtg_fn_Demo_myMathPtr_FloatPtr_FloatPtr(float* a, float* b);

                float jtg_fn_Demo_myMath_float_float(float a, float b) {
                    return a + b * 50.0f;
                }
                float jtg_fn_Demo_myMathPtr_FloatPtr_FloatPtr(float* a, float* b) {
                    return (*a) + (*b) * 50.0f;
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float ptr = 0.0f;
                    ptr = input[id];
                    output[id] = (jtg_fn_Demo_myMath_float_float(input[id], 2.0F) + jtg_fn_Demo_myMathPtr_FloatPtr_FloatPtr((&ptr), (&ptr)));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithCCodeHelperScalarWideningArguments() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-widening-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-widening-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @CCode
                    static float c_code(float a, float b, float t) {
                        return a - (b + a) * t;
                    }

                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = c_code(id * 0.75f, 10, id);
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                float jtg_fn_Demo_c_code_float_float_float(float a, float b, float t);

                float jtg_fn_Demo_c_code_float_float_float(float a, float b, float t) {
                    return (a - ((b + a) * t));
                }
                __kernel void jtg_kernel(__global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_Demo_c_code_float_float_float((id * 0.75F), 10, id);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithClassicSwitchCaseNestedBlock() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-switchblock-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-switchblock-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                        int index = input[0] < 20.0 ? 1 : 0;
                        double value = input[0];
                        switch (index) {
                            case 1: {
                                value = 17 * (1 / value);
                            }
                            default:
                                break;
                        }
                        output[0] = value;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global double* input, __global double* output) {
                    int index = ((input[0] < 20.0) ? 1 : 0);
                    double value = input[0];
                    switch (index) {
                        case 1:
                            value = (17 * (1 / value));
                        default:
                            break;
                    }
                    output[0] = value;
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithFloatPtrConstructorInitializer() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-floatptrctor-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-floatptrctor-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.FloatPtr;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        FloatPtr ptr = new FloatPtr(input[id]);
                        output[id] = GPU.tan(ptr.value);
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float ptr = input[id];
                    output[id] = tan(ptr);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithSameOwnerStaticFinalConstant() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-constant-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-constant-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    static final float SCALE = 0.5f;

                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = input[id] * SCALE;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = (input[id] * 0.5F);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithRawLongLiterals() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-longliteral-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-longliteral-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal long[] input, @GPUGlobal long[] output) {
                        long value = input[0] + 1L;
                        output[0] = value * 17L;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global long* input, __global long* output) {
                    long value = (input[0] + 1L);
                    output[0] = (value * 17L);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithByteShortAndCharLiteralArithmetic() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-charliteral-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-charliteral-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        short step = 2;
                        byte delta = 3;
                        output[0] = input[0] + step + delta + 'A';
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    short step = 2;
                    char delta = 3;
                    output[0] = (((input[0] + step) + delta) + 65);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelUsingReusableCCodeHelperFromSeparateCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path helperClassOutputDir = Files.createTempDirectory("javatogpu-helperlib-classes");
        Path helperGeneratedOutputDir = Files.createTempDirectory("javatogpu-helperlib-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-consumer-generated");

        String helperSource = """
                package lib;

                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.anotations.CCodeLibrary;

                @CCodeLibrary
                public class ReusableMath {
                    static final float SCALE = 0.5f;

                    @CCode(inline = true)
                    public static float square(float value) {
                        return (value * value) * SCALE;
                    }

                    @CCode
                    public static float norm(float value) {
                        return square(value) + 1.0f;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", helperClassOutputDir.toString(),
                    "-s", helperGeneratedOutputDir.toString()
            );
            JavaFileObject helperFile = new StringJavaFileObject("lib.ReusableMath", helperSource);
            JavaCompiler.CompilationTask helperTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(helperFile)
            );
            helperTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(helperTask.call());
        }
        Path helperJar = createClasspathJar(helperClassOutputDir, "javatogpu-helperlib");

        String consumerSource = """
                package sample;

                import lib.ReusableMath;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = ReusableMath.norm(input[id]);
                    }
                }
                """;

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        try {
            configureCompilationClasspath(fileManager, helperClassOutputDir, helperJar);
            List<String> options = List.of(
                    "-classpath", buildCompilationClasspath(helperClassOutputDir, helperJar),
                    "-d", consumerClassOutputDir.toString(),
                    "-s", consumerGeneratedOutputDir.toString()
            );
            JavaFileObject consumerFile = new StringJavaFileObject("sample.Demo", consumerSource);
            JavaCompiler.CompilationTask consumerTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(consumerFile)
            );
            consumerTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(consumerTask.call());
        } finally {
            closeFileManager(fileManager);
        }

        Path kernelPath = consumerGeneratedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        String kernel = Files.readString(kernelPath);
        assertTrue(kernel.contains("float jtg_fn_ReusableMath_norm_float(float value);"));
        assertTrue(kernel.contains("inline float jtg_fn_ReusableMath_square_float(float value);"));
        assertTrue(kernel.contains("return (jtg_fn_ReusableMath_square_float(value) + 1.0F);"));
        assertTrue(kernel.contains("value * value"));
        assertTrue(kernel.contains("0.5"));
        assertTrue(kernel.contains("output[id] = jtg_fn_ReusableMath_norm_float(input[id]);"));
    }

    @Test
    void generatesKernelUsingQualifiedReusableHelpersWithSameSimpleNameFromSeparateCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path helperClassOutputDir = Files.createTempDirectory("javatogpu-qualified-helperlib-classes");
        Path helperGeneratedOutputDir = Files.createTempDirectory("javatogpu-qualified-helperlib-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-qualified-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-qualified-consumer-generated");

        String helperOneSource = """
                package lib.one;

                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;

                @net.sixik.ga_utils.javatogpu.api.anotations.CCodeLibrary
                public class ReusableMath {
                    @CCode
                    public static float norm(float value) {
                        return value + 1.0f;
                    }
                }
                """;

        String helperTwoSource = """
                package lib.two;

                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;

                @net.sixik.ga_utils.javatogpu.api.anotations.CCodeLibrary
                public class ReusableMath {
                    @CCode
                    public static float norm(float value) {
                        return value + 2.0f;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", helperClassOutputDir.toString(),
                    "-s", helperGeneratedOutputDir.toString()
            );
            JavaFileObject helperOneFile = new StringJavaFileObject("lib.one.ReusableMath", helperOneSource);
            JavaFileObject helperTwoFile = new StringJavaFileObject("lib.two.ReusableMath", helperTwoSource);
            JavaCompiler.CompilationTask helperTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(helperOneFile, helperTwoFile)
            );
            helperTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(helperTask.call());
        }
        Path helperJar = createClasspathJar(helperClassOutputDir, "javatogpu-qualified-helperlib");

        String consumerSource = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = lib.two.ReusableMath.norm(input[id]);
                    }
                }
                """;

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        try {
            configureCompilationClasspath(fileManager, helperClassOutputDir, helperJar);
            List<String> options = List.of(
                    "-classpath", buildCompilationClasspath(helperClassOutputDir, helperJar),
                    "-d", consumerClassOutputDir.toString(),
                    "-s", consumerGeneratedOutputDir.toString()
            );
            JavaFileObject consumerFile = new StringJavaFileObject("sample.Demo", consumerSource);
            JavaCompiler.CompilationTask consumerTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(consumerFile)
            );
            consumerTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(consumerTask.call());
        } finally {
            closeFileManager(fileManager);
        }

        Path kernelPath = consumerGeneratedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        String kernel = Files.readString(kernelPath);
        assertTrue(kernel.contains("float jtg_fn_ReusableMath_norm_float(float value);"));
        assertTrue(kernel.contains("return (value + 2.0F);"));
        assertTrue(kernel.contains("output[id] = jtg_fn_ReusableMath_norm_float(input[id]);"));
    }

    @Test
    void rejectsReusableHelperFromSeparateCompilationWhenOwnerIsNotAnnotatedAsCCodeLibrary() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path helperClassOutputDir = Files.createTempDirectory("javatogpu-missinglib-classes");
        Path helperGeneratedOutputDir = Files.createTempDirectory("javatogpu-missinglib-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-missinglib-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-missinglib-consumer-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String helperSource = """
                package lib;

                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;

                public class ReusableMath {
                    @CCode
                    public static float norm(float value) {
                        return value + 1.0f;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", helperClassOutputDir.toString(),
                    "-s", helperGeneratedOutputDir.toString()
            );
            JavaFileObject helperFile = new StringJavaFileObject("lib.ReusableMath", helperSource);
            JavaCompiler.CompilationTask helperTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(helperFile)
            );
            helperTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(helperTask.call());
        }
        Path helperJar = createClasspathJar(helperClassOutputDir, "javatogpu-missinglib");

        String consumerSource = """
                package sample;

                import lib.ReusableMath;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = ReusableMath.norm(input[id]);
                    }
                }
                """;

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        try {
            configureCompilationClasspath(fileManager, helperClassOutputDir, helperJar);
            List<String> options = List.of(
                    "-classpath", buildCompilationClasspath(helperClassOutputDir, helperJar),
                    "-d", consumerClassOutputDir.toString(),
                    "-s", consumerGeneratedOutputDir.toString()
            );
            JavaFileObject consumerFile = new StringJavaFileObject("sample.Demo", consumerSource);
            JavaCompiler.CompilationTask consumerTask = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(consumerFile)
            );
            consumerTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(consumerTask.call());
        } finally {
            closeFileManager(fileManager);
        }

        assertTrue(diagnostics.getDiagnostics().stream().map(diagnostic -> diagnostic.getMessage(null)).anyMatch(message ->
                String.valueOf(message).contains("must be annotated with @CCodeLibrary")
        ));
    }

    @Test
    void rejectsHelperLibraryWhenItTargetsDifferentBackend() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-cudahelper-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-cudahelper-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.anotations.CCodeLibrary;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                @CCodeLibrary(backends = {GpuBackendTarget.CUDA})
                class MyCudaHelpers {
                    @CCode(backends = {GpuBackendTarget.CUDA})
                    static float norm(float value) {
                        return value + 1.0f;
                    }
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = MyCudaHelpers.norm(input[id]);
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Demo", source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(task.call());
        }

        assertTrue(diagnostics.getDiagnostics().stream().map(diagnostic -> diagnostic.getMessage(null)).anyMatch(message ->
                String.valueOf(message).contains("does not target backend OPENCL")
        ));
    }

    @Test
    void generatesKernelWithBooleanLocal() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-boollocal-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-boollocal-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        boolean enabled = input[0] > 0.0f;
                        if (enabled) {
                            output[0] = 1.0f;
                        }
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    bool enabled = (input[0] > 0.0F);
                    if (enabled) {
                        output[0] = 1.0F;
                    }
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithCompoundAssignmentsAndDecrement() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-compound-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-compound-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        int value = input[id];
                        value += 2;
                        value <<= 1;
                        for (int i = 3; i > 0; i--) {
                            output[id] += i;
                        }
                        value--;
                        output[id] = value;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    int value = input[id];
                    value = (value + 2);
                    value = (value << 1);
                    for (int i = 3; (i > 0); i = (i - 1)) {
                        output[id] = (output[id] + i);
                    }
                    value = (value - 1);
                    output[id] = value;
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithDoubleMathIntrinsics() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-doublemath-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-doublemath-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        double value = GPU.sqrt(input[id]) + GPU.pow(input[id], 2.0);
                        output[id] = GPU.max(value, GPU.log(input[id]));
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global double* input, __global double* output) {
                    int id = get_global_id(0);
                    double value = (sqrt(input[id]) + pow(input[id], 2.0));
                    output[id] = max(value, log(input[id]));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithOpenClBuiltinsAndBarrier() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-openclbuiltins-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-openclbuiltins-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int gid = GPU.get_global_id(0);
                        int lid = GPU.get_local_id(0);
                        int size = GPU.get_global_size(0);
                        int dim = GPU.get_work_dim();
                        GPU.barrier(GPU.CLK_LOCAL_MEM_FENCE | GPU.CLK_GLOBAL_MEM_FENCE);
                        output[gid] = gid + lid + size + dim;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    int gid = get_global_id(0);
                    int lid = get_local_id(0);
                    int size = get_global_size(0);
                    int dim = get_work_dim();
                    barrier((1 | 2));
                    output[gid] = (((gid + lid) + size) + dim);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithGpuStructAndOpenClAttributes() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-struct-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-struct-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;
                import net.sixik.ga_utils.javatogpu.api.anotations.OpenCLAttributes;

                @GPUStruct
                @OpenCLAttributes({"packed"})
                class Vec2 {
                    float x;
                    @OpenCLAttributes({"aligned(8)"})
                    float y;

                    Vec2(float x, float y) {
                        this.x = x;
                        this.y = y;
                    }
                }

                public class Demo {
                    @OpenCLAttributes({"reqd_work_group_size(16, 1, 1)"})
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Vec2 point = new Vec2(input[id], input[id] * 2.0f);
                        output[id] = point.x + point.y;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                typedef struct __attribute__((packed)) {
                    float x;
                    float y __attribute__((aligned(8)));
                } Vec2;

                __attribute__((reqd_work_group_size(16, 1, 1))) __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    Vec2 point = (Vec2){input[id], (input[id] * 2.0F)};
                    output[id] = (point.x + point.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithNestedGpuStructsAndStructConstants() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-nested-struct-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-nested-struct-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;

                @GPUStruct
                class Vec2 {
                    static final float BIAS = 1.0f;
                    float x;
                    float y;

                    Vec2(float x, float y) {
                        this.x = x;
                        this.y = y;
                    }
                }

                @GPUStruct
                class Line {
                    static final float SCALE = 0.5f;
                    Vec2 start;
                    Vec2 end;

                    Line(Vec2 start, Vec2 end) {
                        this.start = start;
                        this.end = end;
                    }
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Vec2 base = new Vec2(input[id], input[id] + Vec2.BIAS);
                        Line line = new Line(base, new Vec2(input[id] * Line.SCALE, input[id] * 4.0f));
                        output[id] = line.start.x + line.end.y;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                typedef struct{
                    float x;
                    float y;
                } Vec2;
                typedef struct{
                    Vec2 start;
                    Vec2 end;
                } Line;

                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    Vec2 base = (Vec2){input[id], (input[id] + 1.0F)};
                    Line line = (Line){base, (Vec2){(input[id] * 0.5F), (input[id] * 4.0F)}};
                    output[id] = (line.start.x + line.end.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void rejectsOpenClAttributesWhenValuesAreNotStringLiterals() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-struct-attr-error-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-struct-attr-error-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;
                import net.sixik.ga_utils.javatogpu.api.anotations.OpenCLAttributes;

                class Attrs {
                    static final String PACKED = "packed";
                }

                @GPUStruct
                @OpenCLAttributes(Attrs.PACKED)
                class Vec2 {
                    float x;
                    float y;
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(Vec2 point, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = point.x + point.y;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Demo", source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(task.call());
        }

        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                String.valueOf(diagnostic.getMessage(null)).contains("OpenCLAttributes values must be string literals")
        ));
    }

    @Test
    void rejectsAmbiguousGpuStructTypeAliases() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-struct-alias-error-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-struct-alias-error-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;

                public class Demo {
                    static class Left {
                        @GPUStruct
                        static class Point {
                            float x;
                        }
                    }

                    static class Right {
                        @GPUStruct
                        static class Point {
                            float y;
                        }
                    }

                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(Left.Point point, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = point.x + id;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Demo", source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(task.call());
        }

        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                String.valueOf(diagnostic.getMessage(null)).contains("Ambiguous GPU struct type alias: Point")
        ));
    }

    @Test
    void generatesKernelWithVectorHelpers() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vector-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vector-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @CCode
                    static Float2 add(Float2 left, Float2 right) {
                        return new Float2(left.x + right.x, left.y + right.y);
                    }

                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 base = new Float2(input[id], input[id] * 2.0f);
                        Float2 bias = new Float2(1.0f);
                        Float2 shifted = add(base, bias);
                        output[id] = shifted.x + shifted.y;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                float2 jtg_fn_Demo_add_Float2_Float2(float2 left, float2 right);

                float2 jtg_fn_Demo_add_Float2_Float2(float2 left, float2 right) {
                    return (float2)((left.x + right.x), (left.y + right.y));
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float2 base = (float2)(input[id], (input[id] * 2.0F));
                    float2 bias = (float2)(1.0F);
                    float2 shifted = jtg_fn_Demo_add_Float2_Float2(base, bias);
                    output[id] = (shifted.x + shifted.y);
                }""", Files.readString(kernelPath));

        AtomicReference<GpuKernelInvocation> capturedInvocation = new AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classOutputDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> launcherClass = Class.forName("sample.generated.Demo_kernel_GpuLauncher", true, classLoader);
            float[] input = new float[]{1.0f, 2.0f};
            float[] output = new float[]{0.0f, 0.0f};
            launcherClass.getMethod("invoke", float[].class, float[].class).invoke(null, input, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals("jtg_kernel", invocation.descriptor().kernelName());
            assertEquals(2, invocation.descriptor().parameterDescriptors().size());
            assertEquals(GpuKernelParameterAccess.READ_WRITE, invocation.descriptor().parameterDescriptors().get(0).access());
            assertEquals(GpuKernelParameterAccess.READ_WRITE, invocation.descriptor().parameterDescriptors().get(1).access());
            assertTrue(Arrays.equals(new Object[]{input, output}, invocation.arguments()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke generated launcher reflectively", exception);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatesKernelWithVectorKernelParameter() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vector-param-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vector-param-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(Float2 bias, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = bias.x + bias.y + id;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(float2 bias, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = ((bias.x + bias.y) + id);
                }""", Files.readString(kernelPath));

        Path launcherSourcePath = generatedOutputDir.resolve("sample/generated/Demo_kernel_GpuLauncher.java");
        assertTrue(Files.exists(launcherSourcePath));
        String launcherSource = Files.readString(launcherSourcePath);
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"bias\", \"net.sixik.ga_utils.javatogpu.api.Float2\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.VALUE)"));
        assertTrue(launcherSource.contains("public static void invoke(net.sixik.ga_utils.javatogpu.api.Float2 bias, float[] output)"));

        AtomicReference<GpuKernelInvocation> capturedInvocation = new AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classOutputDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> launcherClass = Class.forName("sample.generated.Demo_kernel_GpuLauncher", true, classLoader);
            Class<?> ownerClass = Class.forName("sample.Demo", true, classLoader);
            Object bias = Class.forName("net.sixik.ga_utils.javatogpu.api.Float2", true, classLoader)
                    .getConstructor(float.class, float.class)
                    .newInstance(1.0f, 2.0f);
            float[] output = new float[]{0.0f, 0.0f};
            launcherClass.getMethod("invoke", bias.getClass(), float[].class).invoke(null, bias, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals("jtg_kernel", invocation.descriptor().kernelName());
            assertEquals(2, invocation.descriptor().parameterDescriptors().size());
            assertEquals(GpuKernelParameterAccess.VALUE, invocation.descriptor().parameterDescriptors().get(0).access());
            assertEquals(GpuKernelParameterAccess.READ_WRITE, invocation.descriptor().parameterDescriptors().get(1).access());
            assertTrue(Arrays.equals(new Object[]{bias, output}, invocation.arguments()));

            capturedInvocation.set(null);
            GpuGeneratedLauncherInvoker.invoke(ownerClass, "kernel", bias, output);
            GpuKernelInvocation reflectedInvocation = capturedInvocation.get();
            assertEquals("jtg_kernel", reflectedInvocation.descriptor().kernelName());
            assertTrue(Arrays.equals(new Object[]{bias, output}, reflectedInvocation.arguments()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke generated vector-parameter launcher reflectively", exception);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatesKernelWithStructKernelParameter() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-struct-param-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-struct-param-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;

                @GPUStruct
                class Sample {
                    float x;
                    float y;
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(Sample sample, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = sample.x + sample.y + id;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                typedef struct{
                    float x;
                    float y;
                } Sample;

                __kernel void jtg_kernel(Sample sample, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = ((sample.x + sample.y) + id);
                }""", Files.readString(kernelPath));

        Path launcherSourcePath = generatedOutputDir.resolve("sample/generated/Demo_kernel_GpuLauncher.java");
        assertTrue(Files.exists(launcherSourcePath));
        String launcherSource = Files.readString(launcherSourcePath);
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"sample\", \"sample.Sample\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.VALUE)"));
        assertTrue(launcherSource.contains("public static void invoke(java.lang.Object sample, float[] output)"));
    }

    @Test
    void generatesKernelWithStructArrayKernelParameter() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-struct-array-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-struct-array-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;

                @GPUStruct
                class Sample {
                    float x;
                    float y;
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal Sample[] input, @GPUGlobal Sample[] output) {
                        int id = GPU.get_global_id(0);
                        output[id].x = input[id].x + 1.0f;
                        output[id].y = input[id].y + 2.0f;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                typedef struct{
                    float x;
                    float y;
                } Sample;

                __kernel void jtg_kernel(__global Sample* input, __global Sample* output) {
                    int id = get_global_id(0);
                    output[id].x = (input[id].x + 1.0F);
                    output[id].y = (input[id].y + 2.0F);
                }""", Files.readString(kernelPath));

        Path launcherSourcePath = generatedOutputDir.resolve("sample/generated/Demo_kernel_GpuLauncher.java");
        assertTrue(Files.exists(launcherSourcePath));
        String launcherSource = Files.readString(launcherSourcePath);
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"input\", \"sample.Sample[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_WRITE)"));
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"output\", \"sample.Sample[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_WRITE)"));
        assertTrue(launcherSource.contains("public static void invoke(java.lang.Object[] input, java.lang.Object[] output)"));
    }

    @Test
    void generatesKernelWithVectorArrayKernelParameter() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vector-array-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vector-array-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal Float2[] input, @GPUGlobal Float2[] output) {
                        int id = GPU.get_global_id(0);
                        output[id].x = input[id].x + 1.0f;
                        output[id].y = input[id].y + 2.0f;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float2* input, __global float2* output) {
                    int id = get_global_id(0);
                    output[id].x = (input[id].x + 1.0F);
                    output[id].y = (input[id].y + 2.0F);
                }""", Files.readString(kernelPath));

        Path launcherSourcePath = generatedOutputDir.resolve("sample/generated/Demo_kernel_GpuLauncher.java");
        assertTrue(Files.exists(launcherSourcePath));
        String launcherSource = Files.readString(launcherSourcePath);
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"input\", \"net.sixik.ga_utils.javatogpu.api.Float2[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_WRITE)"));
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"output\", \"net.sixik.ga_utils.javatogpu.api.Float2[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_WRITE)"));
        assertTrue(launcherSource.contains("public static void invoke(net.sixik.ga_utils.javatogpu.api.Float2[] input, net.sixik.ga_utils.javatogpu.api.Float2[] output)"));
    }

    @Test
    void generatesKernelWithWhileDoWhileAndSwitch() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-controlflow-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-controlflow-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        int i = 0;
                        while (i < 4) {
                            if ((i % 2) == 0) {
                                i++;
                                continue;
                            }
                            output[i] = input[i];
                            i++;
                        }
                        do {
                            i--;
                        } while (i > 0);
                        switch (input[0] & 3) {
                            case 0:
                                output[0] = 1;
                                break;
                            case 1:
                            case 2:
                                output[0] = 2;
                                break;
                            default:
                                output[0] = 3;
                        }
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int i = 0;
                    while ((i < 4)) {
                        if (((i % 2) == 0)) {
                            i = (i + 1);
                            continue;
                        }
                        output[i] = input[i];
                        i = (i + 1);
                    }
                    do {
                        i = (i - 1);
                    } while ((i > 0));
                    switch ((input[0] & 3)) {
                        case 0:
                            output[0] = 1;
                            break;
                        case 1:
                        case 2:
                            output[0] = 2;
                            break;
                        default:
                            output[0] = 3;
                    }
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithRuleStyleSwitch() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-switchrule-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-switchrule-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        switch (input[0] & 3) {
                            case 0 -> output[0] = 1;
                            case 1 -> {
                                output[0] = 2;
                            }
                            default -> output[0] = 3;
                        }
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    switch ((input[0] & 3)) {
                        case 0:
                            output[0] = 1;
                            break;
                        case 1:
                            output[0] = 2;
                            break;
                        default:
                            output[0] = 3;
                            break;
                    }
                }""", Files.readString(kernelPath));
    }

    @Test
    void rejectsBooleanParameterDuringCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-boolean-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-boolean-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(boolean enabled, @GPUGlobal float[] output) {
                        output[0] = 1.0f;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Demo", source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(task.call());
        }

        assertTrue(
                diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                        String.valueOf(diagnostic.getMessage(null)).contains("Unsupported GPU parameter type: boolean")
                )
        );
    }

    @Test
    void rejectsArrayParameterWithoutGlobalAnnotationDuringCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-arrayparam-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-arrayparam-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    void kernel(float[] input, @GPUGlobal float[] output) {
                        output[0] = input[0];
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Demo", source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(task.call());
        }

        assertTrue(
                diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                        String.valueOf(diagnostic.getMessage(null)).contains(
                                "Array parameters must be annotated with @GPUGlobal, @GPUConstant, or @GPULocal in the current pipeline: float[]"
                        )
                )
        );
    }

    @Test
    void generatesKernelWithConstantAndLocalAddressSpaces() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-address-spaces-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-address-spaces-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUConstant;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPULocal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUConstant float[] lookup, @GPULocal float[] scratch, @GPUGlobal float[] output) {
                        int lid = GPU.get_local_id(0);
                        scratch[lid] = lookup[lid] * 2.0f;
                        GPU.barrier(GPU.CLK_LOCAL_MEM_FENCE);
                        output[lid] = scratch[lid];
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__constant float* lookup, __local float* scratch, __global float* output) {
                    int lid = get_local_id(0);
                    scratch[lid] = (lookup[lid] * 2.0F);
                    barrier(1);
                    output[lid] = scratch[lid];
                }""", Files.readString(kernelPath));

        Path launcherSourcePath = generatedOutputDir.resolve("sample/generated/Demo_kernel_GpuLauncher.java");
        assertTrue(Files.exists(launcherSourcePath));
        String launcherSource = Files.readString(launcherSourcePath);
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"lookup\", \"float[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_ONLY)"));
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"scratch\", \"float[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.LOCAL)"));
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"output\", \"float[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_WRITE)"));

        AtomicReference<GpuKernelInvocation> capturedInvocation = new AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classOutputDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> launcherClass = Class.forName("sample.generated.Demo_kernel_GpuLauncher", true, classLoader);
            float[] lookup = new float[]{1.0f, 2.0f};
            float[] scratch = new float[2];
            float[] output = new float[2];
            launcherClass.getMethod("invoke", float[].class, float[].class, float[].class).invoke(null, lookup, scratch, output);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals(3, invocation.descriptor().parameterDescriptors().size());
            assertEquals(GpuKernelParameterAccess.READ_ONLY, invocation.descriptor().parameterDescriptors().get(0).access());
            assertEquals(GpuKernelParameterAccess.LOCAL, invocation.descriptor().parameterDescriptors().get(1).access());
            assertEquals(GpuKernelParameterAccess.READ_WRITE, invocation.descriptor().parameterDescriptors().get(2).access());
            assertTrue(Arrays.equals(new Object[]{lookup, scratch, output}, invocation.arguments()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke generated launcher reflectively", exception);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatesKernelWithIntrinsicLibraryFromSameCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-intrinsiclib-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-intrinsiclib-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsicLibrary;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                @GPUIntrinsicLibrary
                class MyIntrinsics {
                    static final float SCALE = 0.25f;

                    @GPUIntrinsic(code = "(({0}) * 2.0f)")
                    static float twice(float value) {
                        return value * 2.0f;
                    }
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = MyIntrinsics.twice(input[id]) + MyIntrinsics.SCALE;
                    }
                }
                """;

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

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = (((input[id]) * 2.0f) + 0.25F);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithReusableIntrinsicLibraryFromSeparateCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path intrinsicClassOutputDir = Files.createTempDirectory("javatogpu-intrinsiclib-separate-classes");
        Path intrinsicGeneratedOutputDir = Files.createTempDirectory("javatogpu-intrinsiclib-separate-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-intrinsiclib-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-intrinsiclib-consumer-generated");

        String intrinsicSource = """
                package lib;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsicLibrary;

                @GPUIntrinsicLibrary
                public class ReusableIntrinsics {
                    public static final float SCALE = 0.25f;

                    @GPUIntrinsic(code = "(({0}) * 2.0f)")
                    public static float twice(float value) {
                        return value * 2.0f;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", intrinsicClassOutputDir.toString(),
                    "-s", intrinsicGeneratedOutputDir.toString()
            );
            JavaFileObject intrinsicFile = new StringJavaFileObject("lib.ReusableIntrinsics", intrinsicSource);
            JavaCompiler.CompilationTask intrinsicTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(intrinsicFile)
            );
            intrinsicTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(intrinsicTask.call());
        }
        Path intrinsicJar = createClasspathJar(intrinsicClassOutputDir, "javatogpu-reusableintrinsic");

        String consumerSource = """
                package sample;

                import lib.ReusableIntrinsics;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = ReusableIntrinsics.twice(input[id]) + ReusableIntrinsics.SCALE;
                    }
                }
                """;

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        try {
            configureCompilationClasspath(fileManager, intrinsicClassOutputDir, intrinsicJar);
            List<String> options = List.of(
                    "-classpath", buildCompilationClasspath(intrinsicClassOutputDir, intrinsicJar),
                    "-d", consumerClassOutputDir.toString(),
                    "-s", consumerGeneratedOutputDir.toString()
            );
            JavaFileObject consumerFile = new StringJavaFileObject("sample.Demo", consumerSource);
            JavaCompiler.CompilationTask consumerTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(consumerFile)
            );
            consumerTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(consumerTask.call());
        } finally {
            closeFileManager(fileManager);
        }

        Path kernelPath = consumerGeneratedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));
        assertEquals("""
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = (((input[id]) * 2.0f) + 0.25F);
                }""", Files.readString(kernelPath));
    }

    @Test
    void rejectsReusableIntrinsicFromSeparateCompilationWhenOwnerIsNotAnnotatedAsGpuIntrinsicLibrary() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path intrinsicClassOutputDir = Files.createTempDirectory("javatogpu-missingintrinsiclib-classes");
        Path intrinsicGeneratedOutputDir = Files.createTempDirectory("javatogpu-missingintrinsiclib-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-missingintrinsiclib-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-missingintrinsiclib-consumer-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String intrinsicSource = """
                package lib;

                import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;

                public class ReusableIntrinsics {
                    @GPUIntrinsic(code = "(({0}) * 2.0f)")
                    public static float twice(float value) {
                        return value * 2.0f;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", intrinsicClassOutputDir.toString(),
                    "-s", intrinsicGeneratedOutputDir.toString()
            );
            JavaFileObject intrinsicFile = new StringJavaFileObject("lib.ReusableIntrinsics", intrinsicSource);
            JavaCompiler.CompilationTask intrinsicTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(intrinsicFile)
            );
            intrinsicTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(intrinsicTask.call());
        }
        Path intrinsicJar = createClasspathJar(intrinsicClassOutputDir, "javatogpu-missingintrinsiclib");

        String consumerSource = """
                package sample;

                import lib.ReusableIntrinsics;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = ReusableIntrinsics.twice(input[id]);
                    }
                }
                """;

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        try {
            configureCompilationClasspath(fileManager, intrinsicClassOutputDir, intrinsicJar);
            List<String> options = List.of(
                    "-classpath", buildCompilationClasspath(intrinsicClassOutputDir, intrinsicJar),
                    "-d", consumerClassOutputDir.toString(),
                    "-s", consumerGeneratedOutputDir.toString()
            );
            JavaFileObject consumerFile = new StringJavaFileObject("sample.Demo", consumerSource);
            JavaCompiler.CompilationTask consumerTask = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(consumerFile)
            );
            consumerTask.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(consumerTask.call());
        } finally {
            closeFileManager(fileManager);
        }

        assertTrue(diagnostics.getDiagnostics().stream().map(diagnostic -> diagnostic.getMessage(null)).anyMatch(message ->
                String.valueOf(message).contains("must be annotated with @GPUIntrinsicLibrary")
        ));
    }

    @Test
    void rejectsIntrinsicLibraryWhenItTargetsDifferentBackend() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-cudaintrinsic-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-cudaintrinsic-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsicLibrary;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

                @GPUIntrinsicLibrary(backends = {GpuBackendTarget.CUDA})
                class MyCudaIntrinsics {
                    @GPUIntrinsic(code = "(({0}) * 2.0f)", backends = {GpuBackendTarget.CUDA})
                    static float twice(float value) {
                        return value * 2.0f;
                    }
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = MyCudaIntrinsics.twice(input[id]);
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Demo", source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(task.call());
        }

        assertTrue(diagnostics.getDiagnostics().stream().map(diagnostic -> diagnostic.getMessage(null)).anyMatch(message ->
                String.valueOf(message).contains("does not target backend OPENCL")
        ));
    }

    @Test
    void emitsAbiHintNotesWhenDebugAbiProcessorOptionIsEnabled() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-debugabi-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-debugabi-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;

                public class Demo {
                    @GPUStruct
                    static class Point {
                        float x;
                        float y;
                    }

                    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
                    static void kernel(Point point, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = point.x + point.y;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-Ajavatogpu.debugAbi=true",
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject("sample.Demo", source);
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertTrue(task.call());
        }

        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.getKind() == Diagnostic.Kind.NOTE
                        && String.valueOf(diagnostic.getMessage(null)).contains("OpenCL ABI hints for sample.Demo#kernel:")
        ));
        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.getKind() == Diagnostic.Kind.NOTE
                        && String.valueOf(diagnostic.getMessage(null)).contains("point :")
                        && String.valueOf(diagnostic.getMessage(null)).contains("[PRIVATE]")
        ));
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

    private static void configureCompilationClasspath(StandardJavaFileManager fileManager, Path... firstEntries) throws IOException {
        java.util.ArrayList<java.io.File> classPathEntries = new java.util.ArrayList<>();
        for (Path entry : firstEntries) {
            classPathEntries.add(entry.toFile());
        }
        String currentClassPath = System.getProperty("java.class.path", "");
        if (!currentClassPath.isBlank()) {
            for (String entry : currentClassPath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
                if (!entry.isBlank()) {
                    classPathEntries.add(new java.io.File(entry));
                }
            }
        }
        fileManager.setLocation(javax.tools.StandardLocation.CLASS_PATH, classPathEntries);
    }

    private static String buildCompilationClasspath(Path... firstEntries) {
        String prefix = java.util.Arrays.stream(firstEntries)
                .map(Path::toString)
                .reduce((left, right) -> left + java.io.File.pathSeparator + right)
                .orElse("");
        String currentClassPath = System.getProperty("java.class.path", "");
        if (currentClassPath.isBlank()) {
            return prefix;
        }
        if (prefix.isBlank()) {
            return currentClassPath;
        }
        return prefix + java.io.File.pathSeparator + currentClassPath;
    }

    private static Path createClasspathJar(Path classesDirectory, String prefix) throws IOException {
        Path jarPath = Files.createTempFile(prefix, ".jar");
        try (java.util.jar.JarOutputStream jarStream = new java.util.jar.JarOutputStream(Files.newOutputStream(jarPath))) {
            try (java.util.stream.Stream<Path> paths = Files.walk(classesDirectory)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String entryName = classesDirectory.relativize(path).toString().replace('\\', '/');
                    jarStream.putNextEntry(new java.util.jar.JarEntry(entryName));
                    Files.copy(path, jarStream);
                    jarStream.closeEntry();
                }
            }
        }
        return jarPath;
    }

    private static void closeFileManager(StandardJavaFileManager fileManager) throws IOException {
        if (fileManager == null) {
            return;
        }
        try {
            fileManager.close();
        } catch (java.nio.file.AccessDeniedException ignored) {
        }
    }
}
