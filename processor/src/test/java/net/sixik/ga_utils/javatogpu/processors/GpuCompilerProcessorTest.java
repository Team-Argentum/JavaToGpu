package net.sixik.ga_utils.javatogpu.processors;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactParser;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuGeneratedLauncherInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

        Path irGpuPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        assertTrue(Files.exists(irGpuPath));
        String irGpuManifest = Files.readString(irGpuPath);
        assertTrue(irGpuManifest.contains("format=javatogpu.irgpu.v1"));
        assertTrue(irGpuManifest.contains("schemaVersion=1"));
        assertTrue(irGpuManifest.contains("compilerArtifact=JavaToGpu"));
        assertTrue(irGpuManifest.contains("sourceFrontend=java-source"));
        assertTrue(irGpuManifest.contains("entryMethod=kernel"));
        assertTrue(irGpuManifest.contains("entryEmittedName=jtg_kernel"));
        assertTrue(irGpuManifest.contains("helper.count=0"));
        assertTrue(irGpuManifest.contains("struct.count=0"));
        assertTrue(irGpuManifest.contains("backendOutput.count=1"));
        assertTrue(irGpuManifest.contains("backendOutput.0.backend=opencl"));
        assertTrue(irGpuManifest.contains("backendOutput.0.kind=source"));
        assertTrue(irGpuManifest.contains("backendOutput.0.format=opencl-c"));
        assertTrue(irGpuManifest.contains("backendOutput.0.resource=javatogpu/sample/Demo/kernel.cl"));
        assertTrue(irGpuManifest.contains("derived.opencl.resource=javatogpu/sample/Demo/kernel.cl"));
        assertTrue(irGpuManifest.contains("runtime.defaultBackend=opencl"));
        assertTrue(irGpuManifest.contains("runtime.optimizationProfile=off"));
        assertTrue(irGpuManifest.contains("methodBody.count=1"));
        assertTrue(irGpuManifest.contains("methodBody.0.role=entry"));
        assertTrue(irGpuManifest.contains("methodBody.0.name=kernel"));
        assertTrue(irGpuManifest.contains("methodBody.0.emittedName=jtg_kernel"));
        assertTrue(irGpuManifest.contains("methodBody.0.format=ir-text-v1"));

        IrGpuArtifact irGpuArtifact = IrGpuArtifactParser.parse(irGpuManifest);
        assertEquals(1, irGpuArtifact.module().methodBodies().size());
        assertEquals("entry", irGpuArtifact.module().methodBodies().get(0).role());
        assertEquals("kernel", irGpuArtifact.module().methodBodies().get(0).name());
        assertEquals("jtg_kernel", irGpuArtifact.module().methodBodies().get(0).emittedName());
        assertTrue(irGpuArtifact.module().methodBodies().get(0).body().contains("method jtg_kernel source=kernel"));
        assertTrue(irGpuArtifact.module().methodBodies().get(0).body().contains("var int id = intrinsic(get_global_id"));
        assertTrue(irGpuArtifact.module().methodBodies().get(0).body().contains("set output[id] = (intrinsic(sin"));

        Path launcherSourcePath = generatedOutputDir.resolve("sample/generated/Demo_kernel_GpuLauncher.java");
        assertTrue(Files.exists(launcherSourcePath));
        String launcherSource = Files.readString(launcherSourcePath);
        assertTrue(launcherSource.contains("public final class Demo_kernel_GpuLauncher"));
        assertTrue(launcherSource.contains("public static final String KERNEL_NAME = \"jtg_kernel\";"));
        assertTrue(launcherSource.contains("public static final String KERNEL_RESOURCE = \"javatogpu/sample/Demo/kernel.cl\";"));
        assertTrue(launcherSource.contains("public static final String IRGPU_RESOURCE = \"javatogpu/sample/Demo/kernel.irgpu.properties\";"));
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"input\", \"float[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_ONLY)"));
        assertTrue(launcherSource.contains("new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor(\"output\", \"float[]\", net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess.READ_WRITE)"));
        assertTrue(launcherSource.contains("public static void invoke(float[] input, float[] output)"));
        assertTrue(launcherSource.contains("public static void invokeWithGlobalWorkSize(long globalWorkSize, float[] input, float[] output)"));
        assertTrue(launcherSource.contains("public static void invokeWithConfig(net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, float[] input, float[] output)"));
        assertTrue(launcherSource.contains("public static void invokeWithCompileOptions(net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, float[] input, float[] output)"));
        assertTrue(launcherSource.contains("public static void invokeWithGlobalWorkSizeAndCompileOptions(long globalWorkSize, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, float[] input, float[] output)"));
        assertTrue(launcherSource.contains("public static void invokeWithConfigAndCompileOptions(net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, float[] input, float[] output)"));
        assertTrue(launcherSource.contains("public static void invokeWith3DWorkSize(long globalX, long globalY, long globalZ, float[] input, float[] output)"));

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
            assertEquals("javatogpu/sample/Demo/kernel.irgpu.properties", descriptor.irGpuResource());
            assertEquals(2, descriptor.parameterDescriptors().size());
            assertEquals(GpuKernelParameterAccess.READ_ONLY, descriptor.parameterDescriptors().get(0).access());
            assertEquals(GpuKernelParameterAccess.READ_WRITE, descriptor.parameterDescriptors().get(1).access());
            assertTrue(Arrays.equals(new Object[]{input, output}, invocation.arguments()));
            assertEquals(null, invocation.globalWorkSize());

            capturedInvocation.set(null);
            launcherClass.getMethod("invokeWithGlobalWorkSize", long.class, float[].class, float[].class)
                    .invoke(null, 7L, input, output);
            GpuKernelInvocation explicitInvocation = capturedInvocation.get();
            assertEquals(7L, explicitInvocation.globalWorkSize());
            assertTrue(Arrays.equals(new Object[]{input, output}, explicitInvocation.arguments()));

            capturedInvocation.set(null);
            launcherClass.getMethod(
                            "invokeWithConfig",
                            net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.class,
                            float[].class,
                            float[].class
                    )
                    .invoke(null, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.threeDimensional(4L, 3L, 2L), input, output);
            GpuKernelInvocation explicitConfigInvocation = capturedInvocation.get();
            assertEquals(3, explicitConfigInvocation.executionConfig().dimensions());
            assertEquals(4L, explicitConfigInvocation.executionConfig().globalX());
            assertEquals(3L, explicitConfigInvocation.executionConfig().globalY());
            assertEquals(2L, explicitConfigInvocation.executionConfig().globalZ());
            assertTrue(Arrays.equals(new Object[]{input, output}, explicitConfigInvocation.arguments()));

            GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                    net.sixik.ga_utils.javatogpu.api.GpuBackendTarget.OPENCL,
                    java.util.List.of("-cl-fast-relaxed-math"),
                    "fast"
            );
            capturedInvocation.set(null);
            launcherClass.getMethod(
                            "invokeWithConfigAndCompileOptions",
                            net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.class,
                            GpuRuntimeCompileOptions.class,
                            float[].class,
                            float[].class
                    )
                    .invoke(null, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(6L), compileOptions, input, output);
            GpuKernelInvocation compileOptionsInvocation = capturedInvocation.get();
            assertEquals(6L, compileOptionsInvocation.globalWorkSize());
            assertEquals("fast", compileOptionsInvocation.compileOptions().optimizationProfile());
            assertEquals(java.util.List.of("-cl-fast-relaxed-math"), compileOptionsInvocation.compileOptions().compileArgs());
            assertTrue(Arrays.equals(new Object[]{input, output}, compileOptionsInvocation.arguments()));

            capturedInvocation.set(null);
            launcherClass.getMethod("invokeWith3DWorkSize", long.class, long.class, long.class, float[].class, float[].class)
                    .invoke(null, 5L, 4L, 3L, input, output);
            GpuKernelInvocation explicit3DInvocation = capturedInvocation.get();
            assertEquals(3, explicit3DInvocation.executionConfig().dimensions());
            assertEquals(5L, explicit3DInvocation.executionConfig().globalX());
            assertEquals(4L, explicit3DInvocation.executionConfig().globalY());
            assertEquals(3L, explicit3DInvocation.executionConfig().globalZ());
            assertTrue(Arrays.equals(new Object[]{input, output}, explicit3DInvocation.arguments()));

            capturedInvocation.set(null);
            GpuGeneratedLauncherInvoker.invoke(ownerClass, "kernel", input, output);
            GpuKernelInvocation reflectedInvocation = capturedInvocation.get();
            assertEquals("jtg_kernel", reflectedInvocation.descriptor().kernelName());
            assertTrue(Arrays.equals(new Object[]{input, output}, reflectedInvocation.arguments()));

            capturedInvocation.set(null);
            GpuGeneratedLauncherInvoker.invokeWithGlobalWorkSize(ownerClass, "kernel", 9L, input, output);
            GpuKernelInvocation reflectedExplicitInvocation = capturedInvocation.get();
            assertEquals(9L, reflectedExplicitInvocation.globalWorkSize());
            assertTrue(Arrays.equals(new Object[]{input, output}, reflectedExplicitInvocation.arguments()));

            capturedInvocation.set(null);
            GpuGeneratedLauncherInvoker.invokeWithConfig(
                    ownerClass,
                    "kernel",
                    net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(10L),
                    input,
                    output
            );
            GpuKernelInvocation reflectedConfigInvocation = capturedInvocation.get();
            assertEquals(10L, reflectedConfigInvocation.globalWorkSize());
            assertTrue(Arrays.equals(new Object[]{input, output}, reflectedConfigInvocation.arguments()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke generated launcher reflectively", exception);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatesOpenClKernelFileDuringCompilationWithCanonicalAnnotations() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-processor-classes-canonical");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-processor-generated-canonical");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

        Path kernelFile = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelFile));
        String kernelSource = Files.readString(kernelFile);
        assertTrue(kernelSource.contains("sin(value) * cos(value)"));
    }

    @Test
    void generatesLauncherForNestedGpuOwnerWithoutInvalidPackage() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-nested-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-nested-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Outer {
                    static class Inner {
                        @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @CCode(inline = true)
                    float square(float value) {
                        return value * value;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;

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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;

                public class KernelMath {
                    @CCode(inline = true)
                    public static float square(float value) {
                        return value * value;
                    }
                }
                """;

        String fastMathSource = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;

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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;

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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @CCode
                    static void mutate(FloatPtr ptr) {
                        ptr.value = 50.0f;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                + "import net.sixik.ga_utils.javatogpu.api.annotations.CCode;\n"
                + "import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;\n"
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
                + "    @net.sixik.ga_utils.javatogpu.api.annotations.GPU\n"
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
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @CCode
                    static float c_code(float a, float b, float t) {
                        return a - (b + a) * t;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    static final float SCALE = 0.5f;

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
    void generatesKernelWithEmbeddedGpuConstantDataArray() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-constantdata-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-constantdata-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUConstantData;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @GPUConstantData
                    static final float[] LOOKUP = {0.25f, 0.5f, 0.25f};

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = input[id] * LOOKUP[id];
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
        String kernel = Files.readString(kernelPath);
        assertTrue(kernel.contains("LOOKUP[]"));
        assertTrue(kernel.contains("0.25f") || kernel.contains("0.25F"));
        assertTrue(kernel.contains("0.5f") || kernel.contains("0.5F"));
        assertTrue(kernel.contains("output[id] = (input[id] * LOOKUP[id]);"));
    }

    @Test
    void generatesKernelWithRawLongLiterals() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-longliteral-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-longliteral-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary;

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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;

                @net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary
                public class ReusableMath {
                    @CCode
                    public static float norm(float value) {
                        return value + 1.0f;
                    }
                }
                """;

        String helperTwoSource = """
                package lib.two;

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;

                @net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;

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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                @CCodeLibrary(backends = {GpuBackendTarget.CUDA})
                class MyCudaHelpers {
                    @CCode(backends = {GpuBackendTarget.CUDA})
                    static float norm(float value) {
                        return value + 1.0f;
                    }
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
    void generatesKernelUsingReusableNativeCCodeHelperFromSeparateCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path helperClassOutputDir = Files.createTempDirectory("javatogpu-native-helperlib-classes");
        Path helperGeneratedOutputDir = Files.createTempDirectory("javatogpu-native-helperlib-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-native-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-native-consumer-generated");

        String helperSource = """
                package lib;

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary;

                @CCodeLibrary
                public class ReusableNativeMath {
                    @CCode(code = "return a + b * 50.0f;")
                    public static native float blend(float a, float b);
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", helperClassOutputDir.toString(),
                    "-s", helperGeneratedOutputDir.toString()
            );
            JavaFileObject helperFile = new StringJavaFileObject("lib.ReusableNativeMath", helperSource);
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
        Path helperJar = createClasspathJar(helperClassOutputDir, "javatogpu-native-helperlib");

        String consumerSource = """
                package sample;

                import lib.ReusableNativeMath;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = ReusableNativeMath.blend(input[id], 0.5f);
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
        assertTrue(kernel.contains("float jtg_fn_ReusableNativeMath_blend_float_float(float a, float b);"));
        assertTrue(kernel.contains("return a + b * 50.0f;"));
        assertTrue(kernel.contains("output[id] = jtg_fn_ReusableNativeMath_blend_float_float(input[id], 0.5F);"));
    }

    @Test
    void generatesKernelUsingReusableGuardedCCodeHelperFromSeparateCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path helperClassOutputDir = Files.createTempDirectory("javatogpu-guarded-helperlib-classes");
        Path helperGeneratedOutputDir = Files.createTempDirectory("javatogpu-guarded-helperlib-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-guarded-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-guarded-consumer-generated");

        String helperSource = """
                package lib;

                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary;

                @CCodeLibrary
                public class ReusableGuardedMath {
                    @CCode(support = "OpenCL_3", callback = "fallbackAdd")
                    public static float preferredAdd(float a, float b) {
                        return a - b;
                    }

                    @CCode
                    public static float fallbackAdd(float a, float b) {
                        return a + b;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", helperClassOutputDir.toString(),
                    "-s", helperGeneratedOutputDir.toString()
            );
            JavaFileObject helperFile = new StringJavaFileObject("lib.ReusableGuardedMath", helperSource);
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
        Path helperJar = createClasspathJar(helperClassOutputDir, "javatogpu-guarded-helperlib");

        String consumerSource = """
                package sample;

                import lib.ReusableGuardedMath;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = ReusableGuardedMath.preferredAdd(input[id], 2.0f);
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
        assertTrue(kernel.contains("#if defined(__OPENCL_C_VERSION__) && (__OPENCL_C_VERSION__ >= 300)"));
        assertTrue(kernel.contains("return (a - b);"));
        assertTrue(kernel.contains("return jtg_fn_ReusableGuardedMath_fallbackAdd_float_float(a, b);"));
        assertTrue(kernel.contains("output[id] = jtg_fn_ReusableGuardedMath_preferredAdd_float_float(input[id], 2.0F);"));
    }

    @Test
    void generatesKernelWithBooleanLocal() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-boollocal-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-boollocal-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
    void generatesKernelWithExplicitTrapAndUnreachableBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-trapbuiltins-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-trapbuiltins-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        if (input[id] < 0) {
                            GPU.trap();
                            GPU.unreachable();
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
                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    if ((input[id] < 0)) {
                        __builtin_trap();
                        __builtin_unreachable();
                    } else {
                        output[id] = input[id];
                    }
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAdditionalOpenClIntegerBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-openclintbuiltins-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-openclintbuiltins-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        int offset = GPU.get_global_offset(0);
                        int value = GPU.min(input[id], 64);
                        value = GPU.max(value, offset + 8);
                        value = GPU.clamp(value, 4, 32);
                        value = GPU.rotate(value, 1);
                        output[id] = GPU.popcount(value) + GPU.clz(value);
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
                    int offset = get_global_offset(0);
                    int value = min(input[id], 64);
                    value = max(value, (offset + 8));
                    value = clamp(value, 4, 32);
                    value = rotate(value, 1);
                    output[id] = (popcount(value) + clz(value));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithRoundAndSignBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-roundsign-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-roundsign-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        float value = GPU.round(input[id]) + GPU.sign(input[id] - 1.0f);
                        output[id] = GPU.abs(GPU.fract(value) - 0.5f);
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
                    float value = (round(input[id]) + ((((input[id] - 1.0F)) > 0.0f) ? 1.0f : ((((input[id] - 1.0F)) < 0.0f) ? -1.0f : 0.0f)));
                    output[id] = fabs((((value) - floor(value)) - 0.5F));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAngleAndConversionBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-angleconvert-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-angleconvert-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        float angle = GPU.degrees(GPU.atan2(input[id], 1.0f));
                        float limited = GPU.copysign(GPU.trunc(GPU.radians(angle)), input[id] - 2.0f);
                        int bits = GPU.as_int(limited);
                        output[id] = GPU.convert_int(limited) + GPU.popcount(bits);
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
                __kernel void jtg_kernel(__global float* input, __global int* output) {
                    int id = get_global_id(0);
                    float angle = degrees(atan2(input[id], 1.0F));
                    float limited = copysign(trunc(radians(angle)), (input[id] - 2.0F));
                    int bits = as_int(limited);
                    output[id] = (convert_int(limited) + popcount(bits));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithDoubleConversionBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-doubleconvert-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-doubleconvert-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        double angle = GPU.acos(input[id]) + GPU.asin(input[id]) + GPU.atan(input[id]);
                        long bits = GPU.as_long(angle);
                        output[id] = GPU.as_double(bits) + GPU.convert_double(GPU.convert_long(angle));
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
                    double angle = ((acos(input[id]) + asin(input[id])) + atan(input[id]));
                    long bits = as_long(angle);
                    output[id] = (as_double(bits) + convert_double(convert_long(angle)));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorFloatDoubleConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorfloatdoubleconverts-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorfloatdoubleconverts-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Byte3;
                import net.sixik.ga_utils.javatogpu.api.Double3;
                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.ULong3;
                import net.sixik.ga_utils.javatogpu.api.UShort2;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 a = new Int2(3, 5);
                        UShort2 b = new UShort2((short) 7, (short) 9);
                        Byte3 c = new Byte3((byte) 1, (byte) 2, (byte) 3);
                        ULong3 d = new ULong3(11L, 13L, 17L);
                        Float2 f0 = GPU.convert_float(a);
                        Float2 f1 = GPU.convert_float(b);
                        Double3 g0 = GPU.convert_double(c);
                        Double3 g1 = GPU.convert_double(d);
                        output[id] = f0.x + f0.y + f1.x + f1.y + g0.x + g0.y + g0.z + g1.x + g1.y + g1.z;
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
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    int2 a = (int2)(3, 5);
                    ushort2 b = (ushort2)(((short) 7), ((short) 9));
                    char3 c = (char3)(((char) 1), ((char) 2), ((char) 3));
                    ulong3 d = (ulong3)(11L, 13L, 17L);
                    float2 f0 = convert_float(a);
                    float2 f1 = convert_float(b);
                    double3 g0 = convert_double(c);
                    double3 g1 = convert_double(d);
                    output[id] = (((((((((f0.x + f0.y) + f1.x) + f1.y) + g0.x) + g0.y) + g0.z) + g1.x) + g1.y) + g1.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithLongBitBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-longbits-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-longbits-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        long rotated = GPU.rotate(input[id], 3L);
                        output[id] = GPU.popcount(rotated) + GPU.clz(rotated);
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
                __kernel void jtg_kernel(__global long* input, __global int* output) {
                    int id = get_global_id(0);
                    long rotated = rotate(input[id], 3L);
                    output[id] = (popcount(rotated) + clz(rotated));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedConversionAndBitcastBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-unsignedconvert-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-unsignedconvert-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.ULong;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal double[] input2, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UInt bits = GPU.as_uint(input[id]);
                        ULong wide = GPU.as_ulong(input2[id]);
                        UInt narrowed = GPU.convert_uint(input2[id]);
                        ULong converted = GPU.convert_ulong(input[id]);
                        float restoredFloat = GPU.as_float(bits);
                        double restoredDouble = GPU.as_double(wide);
                        output[id] = GPU.convert_int(restoredFloat + (float) restoredDouble) + narrowed.value + GPU.convert_int((double) converted.value);
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
                __kernel void jtg_kernel(__global float* input, __global double* input2, __global int* output) {
                    int id = get_global_id(0);
                    uint bits = as_uint(input[id]);
                    ulong wide = as_ulong(input2[id]);
                    uint narrowed = ((uint) (input2[id]));
                    ulong converted = ((ulong) (input[id]));
                    float restoredFloat = as_float(bits.value);
                    double restoredDouble = as_double(wide.value);
                    output[id] = ((convert_int((restoredFloat + ((float) restoredDouble))) + narrowed) + convert_int(((double) converted)));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithNanAndSaturatingConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-nansat-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-nansat-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UShort;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        short narrow = GPU.convert_short_sat(input[id] * 1000.0f);
                        UShort wide = GPU.convert_ushort_sat(input[id] * 1000.0f);
                        output[id] = GPU.nan(id) + narrow + wide.value;
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
                    short narrow = convert_short_sat((input[id] * 1000.0F));
                    ushort wide = convert_ushort_sat((input[id] * 1000.0F));
                    output[id] = ((nan(((uint) (id))) + narrow) + wide);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithWideSaturatingConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-widesat-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-widesat-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.ULong;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal double[] input, @GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        int signedValue = GPU.convert_int_sat(input[id]);
                        UInt unsignedValue = GPU.convert_uint_sat(input[id]);
                        long wideSigned = GPU.convert_long_sat(input[id]);
                        ULong wideUnsigned = GPU.convert_ulong_sat(input[id]);
                        output[id] = wideSigned + signedValue + unsignedValue.value + wideUnsigned.value;
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
                __kernel void jtg_kernel(__global double* input, __global long* output) {
                    int id = get_global_id(0);
                    int signedValue = convert_int_sat(input[id]);
                    uint unsignedValue = convert_uint_sat(input[id]);
                    long wideSigned = convert_long_sat(input[id]);
                    ulong wideUnsigned = convert_ulong_sat(input[id]);
                    output[id] = (((wideSigned + signedValue) + unsignedValue) + wideUnsigned);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithRegularNarrowConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-regularnarrow-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-regularnarrow-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte;
                import net.sixik.ga_utils.javatogpu.api.UShort;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal double[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        byte a = GPU.convert_char(input[id]);
                        UByte b = GPU.convert_uchar(input[id]);
                        short c = GPU.convert_short(input[id]);
                        UShort d = GPU.convert_ushort(input[id]);
                        output[id] = a + b.value + c + d.value;
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
                __kernel void jtg_kernel(__global double* input, __global int* output) {
                    int id = get_global_id(0);
                    char a = convert_char(input[id]);
                    uchar b = convert_uchar(input[id]);
                    short c = convert_short(input[id]);
                    ushort d = convert_ushort(input[id]);
                    output[id] = (((a + b) + c) + d);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedAliasConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-unsignedaliasconvert-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-unsignedaliasconvert-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte;
                import net.sixik.ga_utils.javatogpu.api.UShort;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.ULong;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        UByte a = new UByte((byte) 7);
                        UShort b = new UShort((short) 9);
                        UInt c = new UInt(11);
                        ULong d = new ULong(13L);
                        UInt c2 = GPU.convert_uint(a);
                        ULong d2 = GPU.convert_ulong(b);
                        output[id] = GPU.convert_int(c) + GPU.convert_int(d) + GPU.convert_long(c2) + GPU.convert_long(d2) + GPU.convert_int(GPU.convert_float(b)) + GPU.convert_int(GPU.convert_double(a));
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    uchar a = ((uchar) ((char) 7));
                    ushort b = ((ushort) ((short) 9));
                    uint c = ((uint) 11);
                    ulong d = ((ulong) 13L);
                    uint c2 = ((uint) (a));
                    ulong d2 = ((ulong) (b));
                    output[id] = (((((convert_int(c) + convert_int(d)) + convert_long(c2)) + convert_long(d2)) + convert_int(convert_float(b))) + convert_int(convert_double(a)));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedAliasNarrowConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-unsignedaliasnarrow-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-unsignedaliasnarrow-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte;
                import net.sixik.ga_utils.javatogpu.api.UShort;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.ULong;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UByte a = new UByte((byte) 7);
                        UShort b = new UShort((short) 9);
                        UInt c = new UInt(11);
                        ULong d = new ULong(13L);
                        byte c0 = GPU.convert_char(a);
                        UByte c1 = GPU.convert_uchar(b);
                        short c2 = GPU.convert_short(c);
                        UShort c3 = GPU.convert_ushort(d);
                        output[id] = c0 + c1.value + c2 + c3.value;
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
                    int id = get_global_id(0);
                    uchar a = ((uchar) ((char) 7));
                    ushort b = ((ushort) ((short) 9));
                    uint c = ((uint) 11);
                    ulong d = ((ulong) 13L);
                    char c0 = convert_char(a);
                    uchar c1 = convert_uchar(b);
                    short c2 = convert_short(c);
                    ushort c3 = convert_ushort(d);
                    output[id] = (((c0 + c1) + c2) + c3);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedAliasNarrowSaturatingConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-unsignedaliasnarrowsat-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-unsignedaliasnarrowsat-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte;
                import net.sixik.ga_utils.javatogpu.api.UShort;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.ULong;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UByte a = new UByte((byte) 7);
                        UShort b = new UShort((short) 320);
                        UInt c = new UInt(11);
                        ULong d = new ULong(13L);
                        byte c0 = GPU.convert_char_sat(a);
                        UByte c1 = GPU.convert_uchar_sat(b);
                        short c2 = GPU.convert_short_sat(c);
                        UShort c3 = GPU.convert_ushort_sat(d);
                        output[id] = c0 + c1.value + c2 + c3.value;
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
                    int id = get_global_id(0);
                    uchar a = ((uchar) ((char) 7));
                    ushort b = ((ushort) ((short) 320));
                    uint c = ((uint) 11);
                    ulong d = ((ulong) 13L);
                    char c0 = convert_char_sat(a);
                    uchar c1 = convert_uchar_sat(b);
                    short c2 = convert_short_sat(c);
                    ushort c3 = convert_ushort_sat(d);
                    output[id] = (((c0 + c1) + c2) + c3);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedAliasCoreWideSaturatingConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-unsignedaliascorewidesat-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-unsignedaliascorewidesat-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte;
                import net.sixik.ga_utils.javatogpu.api.UShort;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.ULong;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        UByte a = new UByte((byte) 7);
                        UShort b = new UShort((short) 9);
                        UInt c = new UInt(11);
                        ULong d = new ULong(13L);
                        output[id] = GPU.convert_int_sat(a) + GPU.convert_int_sat(b) + GPU.convert_int_sat(c) + GPU.convert_int_sat(d)
                                + GPU.convert_long_sat(a) + GPU.convert_long_sat(b) + GPU.convert_long_sat(c) + GPU.convert_long_sat(d)
                                + GPU.convert_uint_sat(a).value + GPU.convert_uint_sat(b).value + GPU.convert_uint_sat(c).value + GPU.convert_uint_sat(d).value
                                + GPU.convert_ulong_sat(a).value + GPU.convert_ulong_sat(b).value + GPU.convert_ulong_sat(c).value + GPU.convert_ulong_sat(d).value;
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    uchar a = ((uchar) ((char) 7));
                    ushort b = ((ushort) ((short) 9));
                    uint c = ((uint) 11);
                    ulong d = ((ulong) 13L);
                    output[id] = (((((((((((((((convert_int_sat(a) + convert_int_sat(b)) + convert_int_sat(c)) + convert_int_sat(d)) + convert_long_sat(a)) + convert_long_sat(b)) + convert_long_sat(c)) + convert_long_sat(d)) + convert_uint_sat(a)) + convert_uint_sat(b)) + convert_uint_sat(c)) + convert_uint_sat(d)) + convert_ulong_sat(a)) + convert_ulong_sat(b)) + convert_ulong_sat(c)) + convert_ulong_sat(d));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithSignedNarrowSourceRegularConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-signednarrowregular-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-signednarrowregular-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte;
                import net.sixik.ga_utils.javatogpu.api.UShort;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        byte a = (byte) 7;
                        short b = (short) 9;
                        char c = 'D';
                        byte c0 = GPU.convert_char(a);
                        UByte c1 = GPU.convert_uchar(b);
                        short c2 = GPU.convert_short(c);
                        UShort c3 = GPU.convert_ushort(a);
                        output[id] = c0 + c1.value + c2 + c3.value;
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
                    int id = get_global_id(0);
                    char a = ((char) 7);
                    short b = ((short) 9);
                    ushort c = 68;
                    char c0 = convert_char(a);
                    uchar c1 = convert_uchar(b);
                    short c2 = convert_short(c);
                    ushort c3 = convert_ushort(a);
                    output[id] = (((c0 + c1) + c2) + c3);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAdditionalSignedAndUnsignedScalarConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-moreconverts-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-moreconverts-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.ULong;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        byte a = (byte) 7;
                        short b = (short) 9;
                        char c = 'B';
                        UInt u0 = GPU.convert_uint(a);
                        UInt u1 = GPU.convert_uint(b);
                        UInt u2 = GPU.convert_uint(c);
                        ULong l0 = GPU.convert_ulong(a);
                        ULong l1 = GPU.convert_ulong(b);
                        ULong l2 = GPU.convert_ulong(c);
                        output[id] = GPU.convert_int(a) + GPU.convert_int(b) + GPU.convert_int(c)
                                + GPU.convert_long(a) + GPU.convert_long(b) + GPU.convert_long(c)
                                + GPU.convert_long(u0) + GPU.convert_long(u1) + GPU.convert_long(u2)
                                + GPU.convert_long(l0) + GPU.convert_long(l1) + GPU.convert_long(l2);
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    char a = ((char) 7);
                    short b = ((short) 9);
                    ushort c = 66;
                    uint u0 = ((uint) (a));
                    uint u1 = ((uint) (b));
                    uint u2 = ((uint) (c));
                    ulong l0 = ((ulong) (a));
                    ulong l1 = ((ulong) (b));
                    ulong l2 = ((ulong) (c));
                    output[id] = (((((((((((convert_int(a) + convert_int(b)) + convert_int(c)) + convert_long(a)) + convert_long(b)) + convert_long(c)) + convert_long(u0)) + convert_long(u1)) + convert_long(u2)) + convert_long(l0)) + convert_long(l1)) + convert_long(l2));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAdditionalSaturatingScalarConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-moresatconverts-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-moresatconverts-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        byte a = (byte) -3;
                        short b = (short) 320;
                        char c = 'C';
                        output[id] = GPU.convert_int_sat(a) + GPU.convert_int_sat(b) + GPU.convert_int_sat(c)
                                + GPU.convert_long_sat(a) + GPU.convert_long_sat(b) + GPU.convert_long_sat(c)
                                + GPU.convert_char_sat(b) + GPU.convert_uchar_sat(a).value
                                + GPU.convert_short_sat(c) + GPU.convert_ushort_sat(c).value
                                + GPU.convert_uint_sat(c).value + GPU.convert_ulong_sat(c).value;
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    char a = ((char) (-3));
                    short b = ((short) 320);
                    ushort c = 67;
                    output[id] = (((((((((((convert_int_sat(a) + convert_int_sat(b)) + convert_int_sat(c)) + convert_long_sat(a)) + convert_long_sat(b)) + convert_long_sat(c)) + convert_char_sat(b)) + convert_uchar_sat(a)) + convert_short_sat(c)) + convert_ushort_sat(c)) + convert_uint_sat(c)) + convert_ulong_sat(c));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorCoreWideConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorcoreconverts-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorcoreconverts-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Double3;
                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.Long3;
                import net.sixik.ga_utils.javatogpu.api.UInt2;
                import net.sixik.ga_utils.javatogpu.api.ULong3;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 a = new Float2(1.5f, 2.5f);
                        Double3 b = new Double3(3.5, 4.5, 5.5);
                        Int2 i = GPU.convert_int(a);
                        UInt2 u = GPU.convert_uint(a);
                        Long3 l = GPU.convert_long(b);
                        ULong3 ul = GPU.convert_ulong(b);
                        output[id] = i.x + i.y + u.x + u.y + l.x + l.y + l.z + ul.x + ul.y + ul.z;
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    float2 a = (float2)(1.5F, 2.5F);
                    double3 b = (double3)(3.5, 4.5, 5.5);
                    int2 i = convert_int(a);
                    uint2 u = convert_uint(a);
                    long3 l = convert_long(b);
                    ulong3 ul = convert_ulong(b);
                    output[id] = (((((((((i.x + i.y) + u.x) + u.y) + l.x) + l.y) + l.z) + ul.x) + ul.y) + ul.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorCoreWideSaturatingConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorcoresatconverts-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorcoresatconverts-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Double3;
                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.Long3;
                import net.sixik.ga_utils.javatogpu.api.UInt2;
                import net.sixik.ga_utils.javatogpu.api.ULong3;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 a = new Float2(-1.5f, 2.5f);
                        Double3 b = new Double3(3.5, 4.5, 5.5);
                        Int2 i = GPU.convert_int_sat(a);
                        UInt2 u = GPU.convert_uint_sat(a);
                        Long3 l = GPU.convert_long_sat(b);
                        ULong3 ul = GPU.convert_ulong_sat(b);
                        output[id] = i.x + i.y + u.x + u.y + l.x + l.y + l.z + ul.x + ul.y + ul.z;
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    float2 a = (float2)((-1.5F), 2.5F);
                    double3 b = (double3)(3.5, 4.5, 5.5);
                    int2 i = convert_int_sat(a);
                    uint2 u = convert_uint_sat(a);
                    long3 l = convert_long_sat(b);
                    ulong3 ul = convert_ulong_sat(b);
                    output[id] = (((((((((i.x + i.y) + u.x) + u.y) + l.x) + l.y) + l.z) + ul.x) + ul.y) + ul.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedNarrowVectorConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-unarrowvectorconverts-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-unarrowvectorconverts-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Double3;
                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte2;
                import net.sixik.ga_utils.javatogpu.api.UByte3;
                import net.sixik.ga_utils.javatogpu.api.UShort2;
                import net.sixik.ga_utils.javatogpu.api.UShort3;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 a = new Float2(7.5f, 320.25f);
                        Double3 b = new Double3(-2.0, 500.0, 70000.0);
                        UByte2 c0 = GPU.convert_uchar(a);
                        UShort2 c1 = GPU.convert_ushort(a);
                        UByte3 c2 = GPU.convert_uchar_sat(b);
                        UShort3 c3 = GPU.convert_ushort_sat(b);
                        output[id] = c0.x + c0.y + c1.x + c1.y + c2.x + c2.y + c2.z + c3.x + c3.y + c3.z;
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
                    int id = get_global_id(0);
                    float2 a = (float2)(7.5F, 320.25F);
                    double3 b = (double3)((-2.0), 500.0, 70000.0);
                    uchar2 c0 = convert_uchar(a);
                    ushort2 c1 = convert_ushort(a);
                    uchar3 c2 = convert_uchar_sat(b);
                    ushort3 c3 = convert_ushort_sat(b);
                    output[id] = (((((((((c0.x + c0.y) + c1.x) + c1.y) + c2.x) + c2.y) + c2.z) + c3.x) + c3.y) + c3.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithSignedNarrowVectorConversions() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-snarrowvectorconverts-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-snarrowvectorconverts-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Byte2;
                import net.sixik.ga_utils.javatogpu.api.Byte3;
                import net.sixik.ga_utils.javatogpu.api.Double3;
                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Short2;
                import net.sixik.ga_utils.javatogpu.api.Short3;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 a = new Float2(7.5f, 320.25f);
                        Double3 b = new Double3(-2.0, 500.0, 70000.0);
                        Byte2 c0 = GPU.convert_char(a);
                        Short2 c1 = GPU.convert_short(a);
                        Byte3 c2 = GPU.convert_char_sat(b);
                        Short3 c3 = GPU.convert_short_sat(b);
                        output[id] = c0.x + c0.y + c1.x + c1.y + c2.x + c2.y + c2.z + c3.x + c3.y + c3.z;
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
                    int id = get_global_id(0);
                    float2 a = (float2)(7.5F, 320.25F);
                    double3 b = (double3)((-2.0), 500.0, 70000.0);
                    char2 c0 = convert_char(a);
                    short2 c1 = convert_short(a);
                    char3 c2 = convert_char_sat(b);
                    short3 c3 = convert_short_sat(b);
                    output[id] = (((((((((c0.x + c0.y) + c1.x) + c1.y) + c2.x) + c2.y) + c2.z) + c3.x) + c3.y) + c3.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithHaddMulHiAndMadHiBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-integercommon2-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-integercommon2-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        int a = GPU.hadd(input[id], 3);
                        int b = GPU.mul_hi(input[id], 5);
                        long c = GPU.rhadd((long) input[id], 7L);
                        long d = GPU.mad_hi((long) input[id], 9L, 11L);
                        output[id] = a + b + c + d;
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
                __kernel void jtg_kernel(__global int* input, __global long* output) {
                    int id = get_global_id(0);
                    int a = hadd(input[id], 3);
                    int b = mul_hi(input[id], 5);
                    long c = rhadd(((long) input[id]), 7L);
                    long d = mad_hi(((long) input[id]), 9L, 11L);
                    output[id] = (((a + b) + c) + d);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithSaturatingArithmeticBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-satarith-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-satarith-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        int a = GPU.add_sat(input[id], 100);
                        int b = GPU.mad_sat(input[id], 3, 7);
                        long c = GPU.sub_sat((long) input[id], 9L);
                        output[id] = a + b + c;
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
                __kernel void jtg_kernel(__global int* input, __global long* output) {
                    int id = get_global_id(0);
                    int a = add_sat(input[id], 100);
                    int b = mad_sat(input[id], 3, 7);
                    long c = sub_sat(((long) input[id]), 9L);
                    output[id] = ((a + b) + c);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedSaturatingArithmeticBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-usatarith-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-usatarith-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte;
                import net.sixik.ga_utils.javatogpu.api.UShort;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.ULong;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        UByte a = new UByte((byte) 10);
                        UShort b = new UShort((short) 20);
                        UInt c = new UInt(30);
                        ULong d = new ULong(40L);
                        UByte x = GPU.add_sat(a, new UByte((byte) 11));
                        UShort y = GPU.mad_sat(b, new UShort((short) 2), new UShort((short) 3));
                        UInt z = GPU.sub_sat(c, new UInt(5));
                        ULong w = GPU.add_sat(d, new ULong(6L));
                        output[id] = x.value + y.value + z.value + w.value;
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    uchar a = ((uchar) ((char) 10));
                    ushort b = ((ushort) ((short) 20));
                    uint c = ((uint) 30);
                    ulong d = ((ulong) 40L);
                    uchar x = add_sat(a, ((uchar) ((char) 11)));
                    ushort y = mad_sat(b, ((ushort) ((short) 2)), ((ushort) ((short) 3)));
                    uint z = sub_sat(c, ((uint) 5));
                    ulong w = add_sat(d, ((ulong) 6L));
                    output[id] = (((x + y) + z) + w);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithMulSatBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-mulsat-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-mulsat-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        int a = GPU.mul_sat(input[id], 13);
                        UInt b = GPU.mul_sat(new UInt(7), new UInt(8));
                        output[id] = a + b.value;
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
                __kernel void jtg_kernel(__global int* input, __global long* output) {
                    int id = get_global_id(0);
                    int a = mul_sat(input[id], 13);
                    uint b = mul_sat(((uint) 7), ((uint) 8));
                    output[id] = (a + b);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedAbsDiffBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-uabsdiff-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-uabsdiff-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte;
                import net.sixik.ga_utils.javatogpu.api.UShort;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.ULong;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        UByte a = GPU.abs_diff(new UByte((byte) 10), new UByte((byte) 3));
                        UShort b = GPU.abs_diff(new UShort((short) 20), new UShort((short) 4));
                        UInt c = GPU.abs_diff(new UInt(30), new UInt(5));
                        ULong d = GPU.abs_diff(new ULong(40L), new ULong(6L));
                        output[id] = a.value + b.value + c.value + d.value;
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    uchar a = abs_diff(((uchar) ((char) 10)), ((uchar) ((char) 3)));
                    ushort b = abs_diff(((ushort) ((short) 20)), ((ushort) ((short) 4)));
                    uint c = abs_diff(((uint) 30), ((uint) 5));
                    ulong d = abs_diff(((ulong) 40L), ((ulong) 6L));
                    output[id] = (((a + b) + c) + d);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorGeometricBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorgeo-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorgeo-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float3 a = new Float3(1.0f, 2.0f, 3.0f);
                        Float3 b = new Float3(4.0f, 5.0f, 6.0f);
                        Float3 normal = GPU.normalize(a);
                        Float3 c = GPU.cross(a, b);
                        output[id] = GPU.dot(a, b) + GPU.distance(a, b) + GPU.length(c) + normal.x;
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
                    int id = get_global_id(0);
                    float3 a = (float3)(1.0F, 2.0F, 3.0F);
                    float3 b = (float3)(4.0F, 5.0F, 6.0F);
                    float3 normal = normalize(a);
                    float3 c = cross(a, b);
                    output[id] = (((dot(a, b) + length((a) - (b))) + sqrt(dot(c, c))) + normal.x);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithDoubleVectorGeometricBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-doublevectorgeo-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-doublevectorgeo-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Double2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        Double2 a = new Double2(1.0, 2.0);
                        Double2 b = new Double2(3.0, 4.0);
                        Double2 n = GPU.normalize(a);
                        output[id] = GPU.dot(a, b) + GPU.distance(a, b) + GPU.length(n);
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
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double2 a = (double2)(1.0, 2.0);
                    double2 b = (double2)(3.0, 4.0);
                    double2 n = normalize(a);
                    output[id] = ((dot(a, b) + length((a) - (b))) + sqrt(dot(n, n)));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAdditionalIntegerCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-intcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-intcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        int mul = GPU.mul24(input[id], 3);
                        int madd = GPU.mad24(mul, 2, 7);
                        int bits = GPU.bitselect(madd, 255, 15);
                        int packed = GPU.upsample((short) 1, (short) bits);
                        output[id] = GPU.select(bits, packed, bits > 0);
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
                    int mul = mul24(input[id], 3);
                    int madd = mad24(mul, 2, 7);
                    int bits = bitselect(madd, 255, 15);
                    int packed = upsample(((short) 1), ((short) bits));
                    output[id] = (((bits > 0)) ? (packed) : (bits));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorIntegerCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorintcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorintcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int3;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int3 a = new Int3(2, 3, 4);
                        Int3 b = new Int3(5, 6, 7);
                        Int3 c = new Int3(1, 2, 3);
                        Int3 mul = GPU.mul24(a, b);
                        Int3 madd = GPU.mad24(mul, b, c);
                        output[id] = madd.x + madd.y + madd.z;
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
                    int id = get_global_id(0);
                    int3 a = (int3)(2, 3, 4);
                    int3 b = (int3)(5, 6, 7);
                    int3 c = (int3)(1, 2, 3);
                    int3 mul = mul24(a, b);
                    int3 madd = mad24(mul, b, c);
                    output[id] = ((madd.x + madd.y) + madd.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorIntegerHighHalfBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorhighhalf-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorhighhalf-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.Long2;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 a = new Int2(10, 20);
                        Int2 b = new Int2(3, 5);
                        Int2 i = GPU.hadd(a, b).add(GPU.mul_hi(a, b));
                        Long2 x = new Long2(11L, 21L);
                        Long2 y = new Long2(7L, 9L);
                        Long2 l = GPU.rhadd(x, y).add(GPU.mad_hi(x, y, new Long2(1L, 2L)));
                        output[id] = i.x + i.y + l.x + l.y;
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    int2 a = (int2)(10, 20);
                    int2 b = (int2)(3, 5);
                    int2 i = (hadd(a, b) + mul_hi(a, b));
                    long2 x = (long2)(11L, 21L);
                    long2 y = (long2)(7L, 9L);
                    long2 l = (rhadd(x, y) + mad_hi(x, y, (long2)(1L, 2L)));
                    output[id] = (((i.x + i.y) + l.x) + l.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorSaturatingArithmeticBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorsatarith-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorsatarith-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.Long2;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 a = new Int2(10, 20);
                        Int2 b = new Int2(3, 4);
                        Int2 c = GPU.add_sat(a, b).add(GPU.mad_sat(a, b, new Int2(1, 2)));
                        Long2 x = new Long2(11L, 21L);
                        Long2 y = new Long2(5L, 7L);
                        Long2 z = GPU.sub_sat(x, y).add(GPU.mul_sat(x, y));
                        output[id] = c.x + c.y + z.x + z.y;
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    int2 a = (int2)(10, 20);
                    int2 b = (int2)(3, 4);
                    int2 c = (add_sat(a, b) + mad_sat(a, b, (int2)(1, 2)));
                    long2 x = (long2)(11L, 21L);
                    long2 y = (long2)(5L, 7L);
                    long2 z = (sub_sat(x, y) + mul_sat(x, y));
                    output[id] = (((c.x + c.y) + z.x) + z.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorAbsDiffBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorabsdiff-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorabsdiff-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int3;
                import net.sixik.ga_utils.javatogpu.api.UInt8;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        Int3 a = new Int3(10, 3, -4);
                        Int3 b = new Int3(2, 9, 5);
                        Int3 signed = GPU.abs_diff(a, b);
                        UInt8 c = new UInt8(30, 40, 50, 60, 70, 80, 90, 100);
                        UInt8 d = new UInt8(10, 15, 20, 25, 30, 35, 40, 45);
                        UInt8 unsigned = GPU.abs_diff(c, d);
                        output[id] = signed.x + signed.y + signed.z + unsigned.s0 + unsigned.s7;
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
                __kernel void jtg_kernel(__global long* output) {
                    int id = get_global_id(0);
                    int3 a = (int3)(10, 3, (-4));
                    int3 b = (int3)(2, 9, 5);
                    int3 signed = abs_diff(a, b);
                    uint8 c = (uint8)(30, 40, 50, 60, 70, 80, 90, 100);
                    uint8 d = (uint8)(10, 15, 20, 25, 30, 35, 40, 45);
                    uint8 unsigned = abs_diff(c, d);
                    output[id] = ((((signed.x + signed.y) + signed.z) + unsigned.s0) + unsigned.s7);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float4;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float4 a = new Float4(1.0f, 2.0f, 3.0f, 4.0f);
                        Float4 b = new Float4(5.0f, 6.0f, 7.0f, 8.0f);
                        Float4 mixed = GPU.mix(a, b, 0.25f);
                        Float4 clipped = GPU.clamp(mixed, new Float4(0.0f), new Float4(6.0f));
                        Float4 stepped = GPU.step(new Float4(2.0f), clipped);
                        Float4 smoothed = GPU.smoothstep(new Float4(0.0f), new Float4(6.0f), clipped);
                        output[id] = stepped.x + smoothed.y;
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
                    int id = get_global_id(0);
                    float4 a = (float4)(1.0F, 2.0F, 3.0F, 4.0F);
                    float4 b = (float4)(5.0F, 6.0F, 7.0F, 8.0F);
                    float4 mixed = mix(a, b, 0.25F);
                    float4 clipped = clamp(mixed, (float4)(0.0F), (float4)(6.0F));
                    float4 stepped = step((float4)(2.0F), clipped);
                    float4 smoothed = smoothstep((float4)(0.0F), (float4)(6.0F), clipped);
                    output[id] = (stepped.x + smoothed.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithDoubleVectorCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-doublevectorcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-doublevectorcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Double3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        Double3 a = new Double3(1.0, 2.0, 3.0);
                        Double3 b = new Double3(4.0, 5.0, 6.0);
                        Double3 mixed = GPU.mix(a, b, 0.5);
                        Double3 clipped = GPU.clamp(mixed, new Double3(1.5), new Double3(5.0));
                        Double3 stepped = GPU.step(new Double3(2.0), clipped);
                        Double3 smoothed = GPU.smoothstep(new Double3(1.5), new Double3(5.0), clipped);
                        output[id] = stepped.x + smoothed.z;
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
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double3 a = (double3)(1.0, 2.0, 3.0);
                    double3 b = (double3)(4.0, 5.0, 6.0);
                    double3 mixed = mix(a, b, 0.5);
                    double3 clipped = clamp(mixed, (double3)(1.5), (double3)(5.0));
                    double3 stepped = step((double3)(2.0), clipped);
                    double3 smoothed = smoothstep((double3)(1.5), (double3)(5.0), clipped);
                    output[id] = (stepped.x + smoothed.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithBroadcastVectorCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-broadcastvectorcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-broadcastvectorcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 a = new Float2(1.0f, 4.0f);
                        Float2 b = new Float2(3.0f, 8.0f);
                        Float2 mixed = GPU.mix(a, b, new Float2(0.25f, 0.75f));
                        Float2 stepped = GPU.step(2.0f, mixed);
                        Float2 smoothed = GPU.smoothstep(0.0f, 6.0f, mixed);
                        output[id] = stepped.x + stepped.y + smoothed.x + smoothed.y;
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
                    int id = get_global_id(0);
                    float2 a = (float2)(1.0F, 4.0F);
                    float2 b = (float2)(3.0F, 8.0F);
                    float2 mixed = mix(a, b, (float2)(0.25F, 0.75F));
                    float2 stepped = step(2.0F, mixed);
                    float2 smoothed = smoothstep(0.0F, 6.0F, mixed);
                    output[id] = (((stepped.x + stepped.y) + smoothed.x) + smoothed.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithScalarBroadcastVectorMinMaxClampBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-broadcastvectorminmax-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-broadcastvectorminmax-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float3 value = new Float3(-2.0f, 1.5f, 9.0f);
                        Float3 bounded = GPU.fmin(GPU.fmax(value, 0.5f), 6.0f);
                        Float3 clipped = GPU.clamp(bounded, 1.0f, 5.0f);
                        output[id] = clipped.x + clipped.y + clipped.z;
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
                    int id = get_global_id(0);
                    float3 value = (float3)((-2.0F), 1.5F, 9.0F);
                    float3 bounded = fmin(fmax(value, 0.5F), 6.0F);
                    float3 clipped = clamp(bounded, 1.0F, 5.0F);
                    output[id] = ((clipped.x + clipped.y) + clipped.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorMadBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectormad-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectormad-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float3 a = new Float3(1.0f, 2.0f, 3.0f);
                        Float3 b = new Float3(4.0f, 5.0f, 6.0f);
                        Float3 c = new Float3(0.5f, 1.0f, 1.5f);
                        Float3 value = GPU.mad(a, b, c);
                        output[id] = value.x + value.y + value.z;
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
                    int id = get_global_id(0);
                    float3 a = (float3)(1.0F, 2.0F, 3.0F);
                    float3 b = (float3)(4.0F, 5.0F, 6.0F);
                    float3 c = (float3)(0.5F, 1.0F, 1.5F);
                    float3 value = mad(a, b, c);
                    output[id] = ((value.x + value.y) + value.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorFmaBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorfma-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorfma-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Double3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        Double3 a = new Double3(1.0, 2.0, 3.0);
                        Double3 b = new Double3(4.0, 5.0, 6.0);
                        Double3 c = new Double3(0.5, 1.0, 1.5);
                        Double3 value = GPU.fma(a, b, c);
                        output[id] = value.x + value.y + value.z;
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
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double3 a = (double3)(1.0, 2.0, 3.0);
                    double3 b = (double3)(4.0, 5.0, 6.0);
                    double3 c = (double3)(0.5, 1.0, 1.5);
                    double3 value = fma(a, b, c);
                    output[id] = ((value.x + value.y) + value.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAdditionalScalarCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-scalarcommonplus-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-scalarcommonplus-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        float a = GPU.minmag(input[id], -2.0f);
                        float b = GPU.maxmag(input[id], 0.5f);
                        float c = GPU.saturate(a + b);
                        output[id] = GPU.abs_diff((int) (a * 10.0f), (int) (b * 10.0f)) + GPU.convert_int(c * 5.0f);
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
                __kernel void jtg_kernel(__global float* input, __global int* output) {
                    int id = get_global_id(0);
                    float a = minmag(input[id], (-2.0F));
                    float b = maxmag(input[id], 0.5F);
                    float c = saturate((a + b));
                    output[id] = (abs_diff(((int) (a * 10.0F)), ((int) (b * 10.0F))) + convert_int((c * 5.0F)));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAdditionalVectorCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorcommonplus-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorcommonplus-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float3 value = new Float3(-0.25f, 0.5f, 1.75f);
                        Float3 s = GPU.sign(value);
                        Float3 f = GPU.fract(value);
                        Float3 sat = GPU.saturate(value);
                        output[id] = s.x + f.y + sat.z;
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
                    int id = get_global_id(0);
                    float3 value = (float3)((-0.25F), 0.5F, 1.75F);
                    float3 s = sign(value);
                    float3 f = fract(value);
                    float3 sat = saturate(value);
                    output[id] = ((s.x + f.y) + sat.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithIntegerVectorCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-intvectorcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-intvectorcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int4 a = new Int4(1, 8, 3, 10);
                        Int4 b = new Int4(5, 2, 7, 4);
                        Int4 lo = GPU.min(a, b);
                        Int4 hi = GPU.max(a, b);
                        Int4 clipped = GPU.clamp(hi, new Int4(2), new Int4(6));
                        output[id] = lo.x + lo.y + clipped.z + clipped.w;
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
                    int id = get_global_id(0);
                    int4 a = (int4)(1, 8, 3, 10);
                    int4 b = (int4)(5, 2, 7, 4);
                    int4 lo = min(a, b);
                    int4 hi = max(a, b);
                    int4 clipped = clamp(hi, (int4)(2), (int4)(6));
                    output[id] = (((lo.x + lo.y) + clipped.z) + clipped.w);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedIntegerVectorCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-uintvectorcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-uintvectorcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UInt3;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UInt3 a = new UInt3(1, 8, 3);
                        UInt3 b = new UInt3(5, 2, 7);
                        UInt3 hi = GPU.max(a, b);
                        UInt3 clipped = GPU.clamp(hi, new UInt3(2), new UInt3(6));
                        UInt3 lo = GPU.min(clipped, new UInt3(5));
                        output[id] = lo.x + lo.y + lo.z;
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
                    int id = get_global_id(0);
                    uint3 a = (uint3)(1, 8, 3);
                    uint3 b = (uint3)(5, 2, 7);
                    uint3 hi = max(a, b);
                    uint3 clipped = clamp(hi, (uint3)(2), (uint3)(6));
                    uint3 lo = min(clipped, (uint3)(5));
                    output[id] = ((lo.x + lo.y) + lo.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithSmallUnsignedVectorCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-usmallvectorcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-usmallvectorcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UByte3;
                import net.sixik.ga_utils.javatogpu.api.UShort4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UByte3 a = new UByte3((byte) 1, (byte) 8, (byte) 3);
                        UByte3 b = new UByte3((byte) 5, (byte) 2, (byte) 7);
                        UByte3 lo = GPU.min(a, b);
                        UShort4 c = new UShort4((short) 9, (short) 1, (short) 12, (short) 4);
                        UShort4 d = new UShort4((short) 2, (short) 5, (short) 7, (short) 10);
                        UShort4 hi = GPU.max(c, d);
                        UShort4 clipped = GPU.clamp(hi, new UShort4((short) 3), new UShort4((short) 8));
                        output[id] = lo.x + lo.y + lo.z + clipped.w;
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
                    int id = get_global_id(0);
                    uchar3 a = (uchar3)(((char) 1), ((char) 8), ((char) 3));
                    uchar3 b = (uchar3)(((char) 5), ((char) 2), ((char) 7));
                    uchar3 lo = min(a, b);
                    ushort4 c = (ushort4)(((short) 9), ((short) 1), ((short) 12), ((short) 4));
                    ushort4 d = (ushort4)(((short) 2), ((short) 5), ((short) 7), ((short) 10));
                    ushort4 hi = max(c, d);
                    ushort4 clipped = clamp(hi, (ushort4)(((short) 3)), (ushort4)(((short) 8)));
                    output[id] = (((lo.x + lo.y) + lo.z) + clipped.w);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithSignedNarrowVectorCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-snarrowvectorcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-snarrowvectorcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Byte3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Short4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Byte3 a = new Byte3((byte) 1, (byte) 8, (byte) 3);
                        Byte3 b = new Byte3((byte) 5, (byte) 2, (byte) 7);
                        Byte3 lo = GPU.min(a, b);
                        Short4 c = new Short4((short) 9, (short) 1, (short) 12, (short) 4);
                        Short4 d = new Short4((short) 2, (short) 5, (short) 7, (short) 10);
                        Short4 hi = GPU.max(c, d);
                        Short4 clipped = GPU.clamp(hi, new Short4((short) 3), new Short4((short) 8));
                        output[id] = lo.x + lo.y + lo.z + clipped.w;
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
                    int id = get_global_id(0);
                    char3 a = (char3)(((char) 1), ((char) 8), ((char) 3));
                    char3 b = (char3)(((char) 5), ((char) 2), ((char) 7));
                    char3 lo = min(a, b);
                    short4 c = (short4)(((short) 9), ((short) 1), ((short) 12), ((short) 4));
                    short4 d = (short4)(((short) 2), ((short) 5), ((short) 7), ((short) 10));
                    short4 hi = max(c, d);
                    short4 clipped = clamp(hi, (short4)(((short) 3)), (short4)(((short) 8)));
                    output[id] = (((lo.x + lo.y) + lo.z) + clipped.w);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithWideUnsignedVectorCommonBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-uwidevectorcommon-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-uwidevectorcommon-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UInt8;
                import net.sixik.ga_utils.javatogpu.api.UShort16;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UInt8 a = new UInt8(1, 8, 3, 9, 2, 7, 4, 6);
                        UInt8 b = new UInt8(5, 2, 7, 4, 6, 1, 9, 3);
                        UInt8 hi = GPU.max(a, b);
                        UInt8 clipped = GPU.clamp(hi, new UInt8(2), new UInt8(6));
                        UShort16 c = new UShort16((short) 9, (short) 1, (short) 12, (short) 4, (short) 8, (short) 3, (short) 15, (short) 2, (short) 5, (short) 11, (short) 6, (short) 13, (short) 7, (short) 10, (short) 14, (short) 16);
                        UShort16 d = new UShort16((short) 2, (short) 5, (short) 7, (short) 10, (short) 1, (short) 8, (short) 6, (short) 9, (short) 3, (short) 4, (short) 12, (short) 11, (short) 13, (short) 14, (short) 15, (short) 0);
                        UShort16 lo = GPU.min(c, d);
                        output[id] = clipped.s0 + clipped.s1 + clipped.s2 + lo.s0 + lo.s1 + lo.sf;
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
                    int id = get_global_id(0);
                    uint8 a = (uint8)(1, 8, 3, 9, 2, 7, 4, 6);
                    uint8 b = (uint8)(5, 2, 7, 4, 6, 1, 9, 3);
                    uint8 hi = max(a, b);
                    uint8 clipped = clamp(hi, (uint8)(2), (uint8)(6));
                    ushort16 c = (ushort16)(((short) 9), ((short) 1), ((short) 12), ((short) 4), ((short) 8), ((short) 3), ((short) 15), ((short) 2), ((short) 5), ((short) 11), ((short) 6), ((short) 13), ((short) 7), ((short) 10), ((short) 14), ((short) 16));
                    ushort16 d = (ushort16)(((short) 2), ((short) 5), ((short) 7), ((short) 10), ((short) 1), ((short) 8), ((short) 6), ((short) 9), ((short) 3), ((short) 4), ((short) 12), ((short) 11), ((short) 13), ((short) 14), ((short) 15), ((short) 0));
                    ushort16 lo = min(c, d);
                    output[id] = (((((clipped.s0 + clipped.s1) + clipped.s2) + lo.s0) + lo.s1) + lo.sf);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAdditionalVectorMathBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectormathplus-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectormathplus-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 a = new Float2(-1.25f, 2.75f);
                        Float2 b = new Float2(2.0f, 3.0f);
                        Float2 abs = GPU.fabs(a);
                        Float2 floored = GPU.floor(a);
                        Float2 ceiled = GPU.ceil(a);
                        Float2 rounded = GPU.round(a);
                        Float2 powered = GPU.pow(abs, b);
                        Float2 limited = GPU.fmin(GPU.fmax(powered, new Float2(1.0f)), new Float2(8.0f));
                        Float2 inv = GPU.rsqrt(limited);
                        output[id] = floored.x + ceiled.y + rounded.x + inv.y;
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
                    int id = get_global_id(0);
                    float2 a = (float2)((-1.25F), 2.75F);
                    float2 b = (float2)(2.0F, 3.0F);
                    float2 abs = fabs(a);
                    float2 floored = floor(a);
                    float2 ceiled = ceil(a);
                    float2 rounded = round(a);
                    float2 powered = pow(abs, b);
                    float2 limited = fmin(fmax(powered, (float2)(1.0F)), (float2)(8.0F));
                    float2 inv = rsqrt(limited);
                    output[id] = (((floored.x + ceiled.y) + rounded.x) + inv.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAdditionalVectorAngleBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorangleplus-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorangleplus-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float3 degrees = new Float3(90.0f, -180.0f, 45.0f);
                        Float3 radians = GPU.radians(degrees);
                        Float3 roundTrip = GPU.degrees(radians);
                        Float3 signed = GPU.copysign(roundTrip, new Float3(-1.0f, 1.0f, -1.0f));
                        output[id] = signed.x + signed.y + signed.z;
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
                    int id = get_global_id(0);
                    float3 degrees = (float3)(90.0F, (-180.0F), 45.0F);
                    float3 radians = radians(degrees);
                    float3 roundTrip = degrees(radians);
                    float3 signed = copysign(roundTrip, (float3)((-1.0F), 1.0F, (-1.0F)));
                    output[id] = ((signed.x + signed.y) + signed.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorMinMagMaxMagBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectormagplus-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectormagplus-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float4;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float4 a = new Float4(-1.0f, 2.5f, -4.0f, 0.25f);
                        Float4 b = new Float4(3.0f, -1.5f, 2.0f, -0.5f);
                        Float4 lo = GPU.minmag(a, b);
                        Float4 hi = GPU.maxmag(a, b);
                        output[id] = lo.x + lo.y + hi.z + hi.w;
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
                    int id = get_global_id(0);
                    float4 a = (float4)((-1.0F), 2.5F, (-4.0F), 0.25F);
                    float4 b = (float4)(3.0F, (-1.5F), 2.0F, (-0.5F));
                    float4 lo = minmag(a, b);
                    float4 hi = maxmag(a, b);
                    output[id] = (((lo.x + lo.y) + hi.z) + hi.w);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorTranscendentalBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectortrigplus-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectortrigplus-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 angle = new Float2(0.5f, 1.0f);
                        Float2 trig = GPU.sin(angle).add(GPU.cos(angle));
                        Float2 rooted = GPU.sqrt(GPU.exp(new Float2(1.0f, 4.0f)));
                        Float2 scaled = GPU.log2(GPU.exp(new Float2(2.0f, 8.0f)));
                        output[id] = trig.x + rooted.y + scaled.x + GPU.tan(angle).y;
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
                    int id = get_global_id(0);
                    float2 angle = (float2)(0.5F, 1.0F);
                    float2 trig = (sin(angle) + cos(angle));
                    float2 rooted = sqrt(exp((float2)(1.0F, 4.0F)));
                    float2 scaled = log2(exp((float2)(2.0F, 8.0F)));
                    output[id] = (((trig.x + rooted.y) + scaled.x) + tan(angle).y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorHyperbolicBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorhyperbolic-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorhyperbolic-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 value = new Float2(0.25f, 0.5f);
                        Float2 hyper = GPU.sinh(value).add(GPU.cosh(value));
                        Float2 limited = GPU.tanh(value);
                        output[id] = hyper.x + hyper.y + limited.x + limited.y;
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
                    int id = get_global_id(0);
                    float2 value = (float2)(0.25F, 0.5F);
                    float2 hyper = (sinh(value) + cosh(value));
                    float2 limited = tanh(value);
                    output[id] = (((hyper.x + hyper.y) + limited.x) + limited.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAdditionalVectorTranscendentalBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectortranscendental2-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectortranscendental2-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 value = new Float2(2.0f, 100.0f);
                        Float2 raised = GPU.exp2(new Float2(3.0f, 4.0f));
                        Float2 logged = GPU.log10(value);
                        output[id] = raised.x + raised.y + logged.x + logged.y;
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
                    int id = get_global_id(0);
                    float2 value = (float2)(2.0F, 100.0F);
                    float2 raised = exp2((float2)(3.0F, 4.0F));
                    float2 logged = log10(value);
                    output[id] = (((raised.x + raised.y) + logged.x) + logged.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorCbrtBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorcbrt-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorcbrt-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 value = new Float2(8.0f, 27.0f);
                        Float2 rooted = GPU.cbrt(value);
                        output[id] = rooted.x + rooted.y;
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
                    int id = get_global_id(0);
                    float2 value = (float2)(8.0F, 27.0F);
                    float2 rooted = cbrt(value);
                    output[id] = (rooted.x + rooted.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorHypotBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorhypot-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorhypot-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 x = new Float2(3.0f, 5.0f);
                        Float2 y = new Float2(4.0f, 12.0f);
                        Float2 value = GPU.hypot(x, y);
                        output[id] = value.x + value.y;
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
                    int id = get_global_id(0);
                    float2 x = (float2)(3.0F, 5.0F);
                    float2 y = (float2)(4.0F, 12.0F);
                    float2 value = hypot(x, y);
                    output[id] = (value.x + value.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorRemainderBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorremainder-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorremainder-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Double2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        Double2 a = new Double2(5.5, 9.0);
                        Double2 b = new Double2(2.0, 4.0);
                        Double2 mod = GPU.fmod(a, b);
                        Double2 rem = GPU.remainder(a, b);
                        output[id] = mod.x + mod.y + rem.x + rem.y;
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
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double2 a = (double2)(5.5, 9.0);
                    double2 b = (double2)(2.0, 4.0);
                    double2 mod = fmod(a, b);
                    double2 rem = remainder(a, b);
                    output[id] = (((mod.x + mod.y) + rem.x) + rem.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorNextafterLdexpBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectornextafterldexp-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectornextafterldexp-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 start = new Float2(1.0f, 2.0f);
                        Float2 direction = new Float2(2.0f, 1.0f);
                        Float2 nudged = GPU.nextafter(start, direction);
                        Int2 exponents = new Int2(1, 2);
                        Float2 scaled = GPU.ldexp(nudged, exponents);
                        output[id] = scaled.x + scaled.y;
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
                    int id = get_global_id(0);
                    float2 start = (float2)(1.0F, 2.0F);
                    float2 direction = (float2)(2.0F, 1.0F);
                    float2 nudged = nextafter(start, direction);
                    int2 exponents = (int2)(1, 2);
                    float2 scaled = ldexp(nudged, exponents);
                    output[id] = (scaled.x + scaled.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorPownBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorpown-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorpown-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Double3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int3;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        Double3 value = new Double3(2.0, 3.0, 4.0);
                        Int3 exponent = new Int3(3, 2, 1);
                        Double3 powered = GPU.pown(value, exponent);
                        output[id] = powered.x + powered.y + powered.z;
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
                __kernel void jtg_kernel(__global double* output) {
                    int id = get_global_id(0);
                    double3 value = (double3)(2.0, 3.0, 4.0);
                    int3 exponent = (int3)(3, 2, 1);
                    double3 powered = pown(value, exponent);
                    output[id] = ((powered.x + powered.y) + powered.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorRootnBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorrootn-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorrootn-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int3;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float3 value = new Float3(8.0f, 27.0f, 16.0f);
                        Int3 exponent = new Int3(3, 3, 2);
                        Float3 rooted = GPU.rootn(value, exponent);
                        output[id] = rooted.x + rooted.y + rooted.z;
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
                    int id = get_global_id(0);
                    float3 value = (float3)(8.0F, 27.0F, 16.0F);
                    int3 exponent = (int3)(3, 3, 2);
                    float3 rooted = rootn(value, exponent);
                    output[id] = ((rooted.x + rooted.y) + rooted.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorPowrBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorpowr-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorpowr-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 value = new Float2(4.0f, 9.0f);
                        Float2 exponent = new Float2(0.5f, 0.5f);
                        Float2 powered = GPU.powr(value, exponent);
                        output[id] = powered.x + powered.y;
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
                    int id = get_global_id(0);
                    float2 value = (float2)(4.0F, 9.0F);
                    float2 exponent = (float2)(0.5F, 0.5F);
                    float2 powered = powr(value, exponent);
                    output[id] = (powered.x + powered.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithVectorInverseTrigBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vectorinvtrig-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vectorinvtrig-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Float2 input = new Float2(0.25f, 0.5f);
                        Float2 arcs = GPU.asin(input).add(GPU.acos(input));
                        Float2 dirs = GPU.atan(input).add(GPU.atan2(input, new Float2(1.0f, 2.0f)));
                        output[id] = arcs.x + arcs.y + dirs.x + dirs.y;
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
                    int id = get_global_id(0);
                    float2 input = (float2)(0.25F, 0.5F);
                    float2 arcs = (asin(input) + acos(input));
                    float2 dirs = (atan(input) + atan2(input, (float2)(1.0F, 2.0F)));
                    output[id] = (((arcs.x + arcs.y) + dirs.x) + dirs.y);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAtomicBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-atomics-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-atomics-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUGlobal int[] data, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        int previous = GPU.atomic_add(data, id, 2);
                        previous += GPU.atomic_sub(data, id, 1);
                        previous += GPU.atomic_inc(data, id);
                        previous += GPU.atomic_dec(data, id);
                        previous += GPU.atomic_cmpxchg(data, id, 5, 9);
                        previous += GPU.atomic_min(data, id, 3);
                        previous += GPU.atomic_max(data, id, 7);
                        previous += GPU.atomic_and(data, id, 15);
                        previous += GPU.atomic_or(data, id, 16);
                        output[id] = previous + GPU.atomic_xor(data, id, 31);
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
                __kernel void jtg_kernel(__global int* data, __global int* output) {
                    int id = get_global_id(0);
                    int previous = atomic_add(&((data)[id]), 2);
                    previous = (previous + atomic_sub(&((data)[id]), 1));
                    previous = (previous + atomic_inc(&((data)[id])));
                    previous = (previous + atomic_dec(&((data)[id])));
                    previous = (previous + atomic_cmpxchg(&((data)[id]), 5, 9));
                    previous = (previous + atomic_min(&((data)[id]), 3));
                    previous = (previous + atomic_max(&((data)[id]), 7));
                    previous = (previous + atomic_and(&((data)[id]), 15));
                    previous = (previous + atomic_or(&((data)[id]), 16));
                    output[id] = (previous + atomic_xor(&((data)[id]), 31));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithLocalMemoryHelpers() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-localhelpers-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-localhelpers-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUConstant;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPULocal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(@GPUConstant float[] lookup, @GPULocal float[] scratch, @GPUGlobal float[] output) {
                        int gid = GPU.get_global_id(0);
                        int lid = GPU.get_local_id(0);
                        scratch[lid] = lookup[lid];
                        GPU.local_mem_fence();
                        GPU.local_barrier();
                        output[gid] = scratch[lid];
                        GPU.global_mem_fence();
                        GPU.global_barrier();
                        GPU.all_mem_fence();
                        GPU.all_barrier();
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
                    int gid = get_global_id(0);
                    int lid = get_local_id(0);
                    scratch[lid] = lookup[lid];
                    mem_fence(1);
                    barrier(1);
                    output[gid] = scratch[lid];
                    mem_fence(2);
                    barrier(2);
                    mem_fence((1 | 2));
                    barrier((1 | 2));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithImageAndSamplerBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-imageapi-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-imageapi-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float4;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.Int4;
                import net.sixik.ga_utils.javatogpu.api.Sampler;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DReadOnly inputImage, Image2DWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 coords = new Int2(id, 0);
                        Int4 pixel = GPU.read_imagei(inputImage, sampler, coords);
                        output[id] = pixel.x + GPU.get_image_width(inputImage) + GPU.get_image_height(outputImage);
                        GPU.write_imagef(outputImage, coords, new Float4(1.0f, 0.0f, 0.0f, 1.0f));
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
                __kernel void jtg_kernel(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    int4 pixel = read_imagei(inputImage, sampler, coords);
                    output[id] = ((pixel.x + get_image_width(inputImage)) + get_image_height(outputImage));
                    write_imagef(outputImage, coords, (float4)(1.0F, 0.0F, 0.0F, 1.0F));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedImageBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-imageui-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-imageui-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.Sampler;
                import net.sixik.ga_utils.javatogpu.api.UInt4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DReadOnly inputImage, Image2DWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 coords = new Int2(id, 0);
                        UInt4 pixel = GPU.read_imageui(inputImage, sampler, coords);
                        output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                        GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
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
                __kernel void jtg_kernel(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    uint4 pixel = read_imageui(inputImage, sampler, coords);
                    output[id] = (((pixel.x + pixel.y) + pixel.z) + pixel.w);
                    write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithImage3dBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image3d-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image3d-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float4;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int4;
                import net.sixik.ga_utils.javatogpu.api.Sampler;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image3DReadOnly inputImage, Image3DWriteOnly outputImage, Sampler sampler, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Int4 coords = new Int4(id, 0, 0, 0);
                        Float4 pixel = GPU.read_imagef(inputImage, sampler, coords);
                        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_depth(inputImage);
                        GPU.write_imagef(outputImage, coords, new Float4(0.25f, 0.5f, 0.75f, 1.0f));
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
                __kernel void jtg_kernel(read_only image3d_t inputImage, write_only image3d_t outputImage, sampler_t sampler, __global float* output) {
                    int id = get_global_id(0);
                    int4 coords = (int4)(id, 0, 0, 0);
                    float4 pixel = read_imagef(inputImage, sampler, coords);
                    output[id] = ((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_depth(inputImage));
                    write_imagef(outputImage, coords, (float4)(0.25F, 0.5F, 0.75F, 1.0F));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedImage3dBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image3dui-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image3dui-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int4;
                import net.sixik.ga_utils.javatogpu.api.Sampler;
                import net.sixik.ga_utils.javatogpu.api.UInt4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image3DReadOnly inputImage, Image3DWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int4 coords = new Int4(id, 0, 0, 0);
                        UInt4 pixel = GPU.read_imageui(inputImage, sampler, coords);
                        output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                        GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
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
                __kernel void jtg_kernel(read_only image3d_t inputImage, write_only image3d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int4 coords = (int4)(id, 0, 0, 0);
                    uint4 pixel = read_imageui(inputImage, sampler, coords);
                    output[id] = (((pixel.x + pixel.y) + pixel.z) + pixel.w);
                    write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithSamplerlessImageBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image-nosampler-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image-nosampler-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.UInt4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DReadOnly inputImage, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 coords = new Int2(id, 0);
                        UInt4 pixel = GPU.read_imageui(inputImage, coords);
                        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage);
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
                __kernel void jtg_kernel(read_only image2d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    uint4 pixel = read_imageui(inputImage, coords);
                    output[id] = ((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_width(inputImage));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithSamplerlessImage3dBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image3d-nosampler-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image3d-nosampler-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float4;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Int4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image3DReadOnly inputImage, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        Int4 coords = new Int4(id, 0, 0, 0);
                        Float4 pixel = GPU.read_imagef(inputImage, coords);
                        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_depth(inputImage);
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
                __kernel void jtg_kernel(read_only image3d_t inputImage, __global float* output) {
                    int id = get_global_id(0);
                    int4 coords = (int4)(id, 0, 0, 0);
                    float4 pixel = read_imagef(inputImage, coords);
                    output[id] = ((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_depth(inputImage));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithImageMetadataBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image-meta-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image-meta-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DReadOnly inputImage, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        int channelOrder = GPU.get_image_channel_order(inputImage);
                        int channelType = GPU.get_image_channel_data_type(inputImage);
                        output[id] = channelOrder == GPU.CL_RGBA && channelType == GPU.CL_UNSIGNED_INT32 ? 1 : 0;
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
                __kernel void jtg_kernel(read_only image2d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int channelOrder = get_image_channel_order(inputImage);
                    int channelType = get_image_channel_data_type(inputImage);
                    output[id] = (((channelOrder == %d) && (channelType == %d)) ? 1 : 0);
                }""".formatted(org.lwjgl.opencl.CL10.CL_RGBA, org.lwjgl.opencl.CL10.CL_UNSIGNED_INT32), Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithImage3dMetadataBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image3d-meta-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image3d-meta-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image3DReadOnly inputImage, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        int channelOrder = GPU.get_image_channel_order(inputImage);
                        int channelType = GPU.get_image_channel_data_type(inputImage);
                        output[id] = channelOrder == GPU.CL_RGBA && channelType == GPU.CL_FLOAT ? GPU.get_image_depth(inputImage) : 0;
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
                __kernel void jtg_kernel(read_only image3d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int channelOrder = get_image_channel_order(inputImage);
                    int channelType = get_image_channel_data_type(inputImage);
                    output[id] = (((channelOrder == %d) && (channelType == %d)) ? get_image_depth(inputImage) : 0);
                }""".formatted(org.lwjgl.opencl.CL10.CL_RGBA, org.lwjgl.opencl.CL10.CL_FLOAT), Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithExtendedImageMetadataBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image-meta-extended-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image-meta-extended-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DReadOnly inputImage, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        int mipLevels = GPU.get_image_num_mip_levels(inputImage);
                        int sampleCount = GPU.get_image_num_samples(inputImage);
                        output[id] = mipLevels + sampleCount + GPU.get_image_width(inputImage);
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
                __kernel void jtg_kernel(read_only image2d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int mipLevels = get_image_num_mip_levels(inputImage);
                    int sampleCount = get_image_num_samples(inputImage);
                    output[id] = ((mipLevels + sampleCount) + get_image_width(inputImage));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithMipmappedImageMetadataBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image-meta-mipmapped-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image-meta-mipmapped-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedReadOnly;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DMipmappedReadOnly inputImage, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        int mipLevels = GPU.get_image_num_mip_levels(inputImage);
                        int sampleCount = GPU.get_image_num_samples(inputImage);
                        output[id] = mipLevels + sampleCount + GPU.get_image_width(inputImage) + GPU.get_image_height(inputImage);
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
                __kernel void jtg_kernel(read_only image2d_t inputImage, __global int* output) {
                    int id = get_global_id(0);
                    int mipLevels = get_image_num_mip_levels(inputImage);
                    int sampleCount = get_image_num_samples(inputImage);
                    output[id] = (((mipLevels + sampleCount) + get_image_width(inputImage)) + get_image_height(inputImage));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithMipmappedFloatImageBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image-mipmapped-float-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image-mipmapped-float-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float4;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.Sampler;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DMipmappedReadOnly inputImage, Image2DMipmappedWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 coords = new Int2(id, 0);
                        Float4 pixel = GPU.read_imagef(inputImage, sampler, coords);
                        output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                        GPU.write_imagef(outputImage, coords, new Float4(1.0f, 0.5f, 0.25f, 1.0f));
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
                __kernel void jtg_kernel(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    float4 pixel = read_imagef(inputImage, sampler, coords);
                    output[id] = ((int) (((pixel.x + pixel.y) + pixel.z) + pixel.w));
                    write_imagef(outputImage, coords, (float4)(1.0F, 0.5F, 0.25F, 1.0F));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithMsaaFloatImageBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image-msaa-float-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image-msaa-float-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float4;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DMsaaReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image2DMsaaWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DMsaaReadOnly inputImage, Image2DMsaaWriteOnly outputImage, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 coords = new Int2(id, 0);
                        int sampleCount = GPU.get_image_num_samples(inputImage);
                        int sampleIndex = sampleCount > 1 ? 1 : 0;
                        Float4 pixel = GPU.read_imagef(inputImage, coords, sampleIndex);
                        output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage) + GPU.get_image_height(inputImage));
                        GPU.write_imagef(outputImage, coords, sampleIndex, new Float4(1.0f, 0.5f, 0.25f, 1.0f));
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
                __kernel void jtg_kernel(read_only image2d_msaa_t inputImage, write_only image2d_msaa_t outputImage, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    int sampleCount = get_image_num_samples(inputImage);
                    int sampleIndex = ((sampleCount > 1) ? 1 : 0);
                    float4 pixel = read_imagef(inputImage, coords, sampleIndex);
                    output[id] = ((int) (((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_width(inputImage)) + get_image_height(inputImage)));
                    write_imagef(outputImage, coords, sampleIndex, (float4)(1.0F, 0.5F, 0.25F, 1.0F));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithMipmappedUIntImageBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image-mipmapped-uint-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image-mipmapped-uint-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.Sampler;
                import net.sixik.ga_utils.javatogpu.api.UInt4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DMipmappedReadOnly inputImage, Image2DMipmappedWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 coords = new Int2(id, 0);
                        UInt4 pixel = GPU.read_imageui(inputImage, sampler, coords);
                        output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                        GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
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
                __kernel void jtg_kernel(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    uint4 pixel = read_imageui(inputImage, sampler, coords);
                    output[id] = (((pixel.x + pixel.y) + pixel.z) + pixel.w);
                    write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithUnsignedScalarAliasValues() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-uint-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-uint-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UInt;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(UInt bias, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UInt limited = GPU.clamp(GPU.max(bias, new UInt(4)), new UInt(4), new UInt(32));
                        UInt result = GPU.min(limited, new UInt(17));
                        output[id] = result.value;
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
                __kernel void jtg_kernel(uint bias, __global int* output) {
                    int id = get_global_id(0);
                    uint limited = clamp(max(bias, ((uint) 4)), ((uint) 4), ((uint) 32));
                    uint result = min(limited, ((uint) 17));
                    output[id] = result;
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithImage1dBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image1d-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image1d-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image1DWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Sampler;
                import net.sixik.ga_utils.javatogpu.api.UInt4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image1DReadOnly inputImage, Image1DWriteOnly outputImage, Sampler sampler, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UInt4 pixel = GPU.read_imageui(inputImage, sampler, id);
                        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage);
                        GPU.write_imageui(outputImage, id, new UInt4(9, 10, 11, 12));
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
                __kernel void jtg_kernel(read_only image1d_t inputImage, write_only image1d_t outputImage, sampler_t sampler, __global int* output) {
                    int id = get_global_id(0);
                    uint4 pixel = read_imageui(inputImage, sampler, id);
                    output[id] = ((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_width(inputImage));
                    write_imageui(outputImage, id, (uint4)(9, 10, 11, 12));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithImage1dArrayBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image1d-array-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image1d-array-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image1DArrayReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image1DArrayWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int2;
                import net.sixik.ga_utils.javatogpu.api.UInt4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image1DArrayReadOnly inputImage, Image1DArrayWriteOnly outputImage, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int2 coords = new Int2(id, 0);
                        UInt4 pixel = GPU.read_imageui(inputImage, coords);
                        output[id] = pixel.x + pixel.y + pixel.z + pixel.w + GPU.get_image_width(inputImage) + GPU.get_image_array_size(inputImage);
                        GPU.write_imageui(outputImage, coords, new UInt4(9, 10, 11, 12));
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
                __kernel void jtg_kernel(read_only image1d_array_t inputImage, write_only image1d_array_t outputImage, __global int* output) {
                    int id = get_global_id(0);
                    int2 coords = (int2)(id, 0);
                    uint4 pixel = read_imageui(inputImage, coords);
                    output[id] = (((((pixel.x + pixel.y) + pixel.z) + pixel.w) + get_image_width(inputImage)) + get_image_array_size(inputImage));
                    write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithImage1dBufferBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image1d-buffer-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image1d-buffer-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image1DBufferReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image1DBufferReadOnly inputImage, Image1DBufferWriteOnly outputImage, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int4 pixel = GPU.read_imagei(inputImage, id);
                        output[id] = pixel.x + GPU.get_image_width(inputImage);
                        GPU.write_imagei(outputImage, id, new Int4(9, 10, 11, 12));
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
                __kernel void jtg_kernel(read_only image1d_buffer_t inputImage, write_only image1d_buffer_t outputImage, __global int* output) {
                    int id = get_global_id(0);
                    int4 pixel = read_imagei(inputImage, id);
                    output[id] = (pixel.x + get_image_width(inputImage));
                    write_imagei(outputImage, id, (int4)(9, 10, 11, 12));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithImage2dArrayBuiltins() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-image2d-array-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-image2d-array-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Float4;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Image2DArrayReadOnly;
                import net.sixik.ga_utils.javatogpu.api.Image2DArrayWriteOnly;
                import net.sixik.ga_utils.javatogpu.api.Int4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    void kernel(Image2DArrayReadOnly inputImage, Image2DArrayWriteOnly outputImage, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int4 coords = new Int4(id, 0, 0, 0);
                        Float4 pixel = GPU.read_imagef(inputImage, coords);
                        output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w) + GPU.get_image_height(inputImage) + GPU.get_image_array_size(inputImage);
                        GPU.write_imagef(outputImage, coords, new Float4(0.25f, 0.5f, 0.75f, 1.0f));
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
                __kernel void jtg_kernel(read_only image2d_array_t inputImage, write_only image2d_array_t outputImage, __global int* output) {
                    int id = get_global_id(0);
                    int4 coords = (int4)(id, 0, 0, 0);
                    float4 pixel = read_imagef(inputImage, coords);
                    output[id] = ((((int) (((pixel.x + pixel.y) + pixel.z) + pixel.w)) + get_image_height(inputImage)) + get_image_array_size(inputImage));
                    write_imagef(outputImage, coords, (float4)(0.25F, 0.5F, 0.75F, 1.0F));
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;

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
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
    void generatedLauncherPreserves3DRequiredWorkGroupAttributeAndExplicitLocalSize() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-3d-attribute-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-3d-attribute-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;

                public class Demo {
                    @OpenCLAttributes({"reqd_work_group_size(8, 8, 1)"})
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal int[] output, int sizeX, int sizeZ) {
                        int x = GPU.get_global_id(0);
                        int z = GPU.get_global_id(1);
                        int y = GPU.get_global_id(2);
                        output[(y * sizeZ + z) * sizeX + x] = x + z + y;
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
                __attribute__((reqd_work_group_size(8, 8, 1))) __kernel void jtg_kernel(__global int* output, int sizeX, int sizeZ) {
                    int x = get_global_id(0);
                    int z = get_global_id(1);
                    int y = get_global_id(2);
                    output[((((y * sizeZ) + z) * sizeX) + x)] = ((x + z) + y);
                }""", Files.readString(kernelPath));

        Path launcherSourcePath = generatedOutputDir.resolve("sample/generated/Demo_kernel_GpuLauncher.java");
        assertTrue(Files.exists(launcherSourcePath));
        String launcherSource = Files.readString(launcherSourcePath);
        assertTrue(launcherSource.contains("public static void invokeWithConfig(net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, int[] output, int sizeX, int sizeZ)"));
        assertTrue(launcherSource.contains("public static void invokeWith3DWorkSize(long globalX, long globalY, long globalZ, int[] output, int sizeX, int sizeZ)"));

        AtomicReference<GpuKernelInvocation> capturedInvocation = new AtomicReference<>();
        GpuRuntimeBackend previousBackend = GpuRuntime.backend();
        GpuRuntime.setBackend(capturedInvocation::set);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classOutputDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> launcherClass = Class.forName("sample.generated.Demo_kernel_GpuLauncher", true, classLoader);
            int[] output = new int[8 * 8 * 2];
            net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig =
                    net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.threeDimensional(8L, 8L, 2L, 8L, 8L, 1L);

            launcherClass.getMethod(
                            "invokeWithConfig",
                            net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.class,
                            int[].class,
                            int.class,
                            int.class
                    )
                    .invoke(null, executionConfig, output, 8, 8);

            GpuKernelInvocation invocation = capturedInvocation.get();
            assertEquals("jtg_kernel", invocation.descriptor().kernelName());
            assertEquals(Files.readString(kernelPath), invocation.descriptor().kernelSource());
            assertEquals(3, invocation.executionConfig().dimensions());
            assertEquals(8L, invocation.executionConfig().globalX());
            assertEquals(8L, invocation.executionConfig().globalY());
            assertEquals(2L, invocation.executionConfig().globalZ());
            assertEquals(8L, invocation.executionConfig().localX());
            assertEquals(8L, invocation.executionConfig().localY());
            assertEquals(1L, invocation.executionConfig().localZ());
            assertTrue(Arrays.equals(new Object[]{output, 8, 8}, invocation.arguments()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke generated 3D attribute launcher reflectively", exception);
        } finally {
            GpuRuntime.setBackend(previousBackend);
        }
    }

    @Test
    void generatesKernelWithNestedGpuStructsAndStructConstants() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-nested-struct-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-nested-struct-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

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
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;

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
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
    void rejectsMalformedKernelOpenClAttributesDuringCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-kernel-attr-error-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-kernel-attr-error-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;

                public class Demo {
                    @OpenCLAttributes({"reqd_work_group_size(16, 0, 1)", "vec_type_hint(float4)", "vec_type_hint(int4)"})
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] output) {
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

        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                String.valueOf(diagnostic.getMessage(null)).contains("reqd_work_group_size(...) requires exactly three positive integer arguments")
                        || String.valueOf(diagnostic.getMessage(null)).contains("Duplicate OpenCL attribute: vec_type_hint")
        ));
    }

    @Test
    void generatesKernelWithOpenClParameterQualifiers() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-qualifier-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-qualifier-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLQualifiers;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(
                            @OpenCLQualifiers({"restrict"}) @GPUGlobal(constant = true) float[] input,
                            @OpenCLQualifiers({"restrict"}) @GPUGlobal float[] output
                    ) {
                        int id = GPU.get_global_id(0);
                        output[id] = input[id] * 2.0f;
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
                __kernel void jtg_kernel(__global const float* restrict input, __global float* restrict output) {
                    int id = get_global_id(0);
                    output[id] = (input[id] * 2.0F);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithConstOpenClQualifierOnHelperPointer() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-constqual-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-constqual-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.FloatPtr;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLQualifiers;

                public class Demo {
                    @CCode
                    static float read(@OpenCLQualifiers({"const"}) FloatPtr ptr) {
                        return ptr.value;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        FloatPtr ptr = new FloatPtr(input[id]);
                        output[id] = read(ptr);
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
                float jtg_fn_Demo_read_FloatPtr(const float* ptr);

                float jtg_fn_Demo_read_FloatPtr(const float* ptr) {
                    return (*ptr);
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    float ptr = input[id];
                    output[id] = jtg_fn_Demo_read_FloatPtr((&ptr));
                }""", Files.readString(kernelPath));
    }

    @Test
    void rejectsUnsupportedOpenClQualifierDuringCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-badqual-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-badqual-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLQualifiers;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@OpenCLQualifiers({"coherent"}) @GPUGlobal float[] output) {
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

        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                String.valueOf(diagnostic.getMessage(null)).contains("Unsupported OpenCL parameter qualifier: coherent")
        ));
    }

    @Test
    void rejectsDuplicateOpenClQualifierDuringCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-dupqual-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-dupqual-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLQualifiers;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@OpenCLQualifiers({"restrict", "restrict"}) @GPUGlobal float[] output) {
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

        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                String.valueOf(diagnostic.getMessage(null)).contains("Duplicate OpenCL parameter qualifier: restrict")
        ));
    }

    @Test
    void rejectsOpenClQualifierOnNonPointerParameterDuringCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-nonptrqual-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-nonptrqual-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLQualifiers;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@OpenCLQualifiers({"const"}) float scale, @GPUGlobal float[] output) {
                        output[0] = scale;
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
                String.valueOf(diagnostic.getMessage(null)).contains("OpenCLQualifiers are only supported on pointer-like GPU parameters in the current pipeline: float")
        ));
    }

    @Test
    void generatesKernelWithAddressSpacePointerSubExpression() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-globalsub-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-globalsub-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GlobalFloatPtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @CCode
                    static float readBack(GlobalFloatPtr ptr, int index) {
                        return ptr.add(index + 1).sub(1).value;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        GlobalFloatPtr ptr = GPU.global(input);
                        output[id] = readBack(ptr, id);
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
                float jtg_fn_Demo_readBack_GlobalFloatPtr_int(__global float* ptr, int index);

                float jtg_fn_Demo_readBack_GlobalFloatPtr_int(__global float* ptr, int index) {
                    return (*((((ptr) + ((index + 1)))) - (1)));
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    __global float* ptr = (input);
                    output[id] = jtg_fn_Demo_readBack_GlobalFloatPtr_int((&ptr), id);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAnnotatedPointerTypeOperatorWithoutMethodIntrinsicAnnotations() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path pointerClassOutputDir = Files.createTempDirectory("javatogpu-annotated-pointer-classes");
        Path pointerGeneratedOutputDir = Files.createTempDirectory("javatogpu-annotated-pointer-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-annotated-pointer-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-annotated-pointer-consumer-generated");

        String pointerSource = """
                package custom;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType;

                @GPUPointerType(valueType = "float")
                public final class MyFloatPtr {
                    public float value;

                    public MyFloatPtr() {
                    }

                    public MyFloatPtr(float value) {
                        this.value = value;
                    }

                    public MyFloatPtr add(int elements) {
                        return this;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", pointerClassOutputDir.toString(),
                    "-s", pointerGeneratedOutputDir.toString()
            );
            JavaFileObject pointerFile = new StringJavaFileObject("custom.MyFloatPtr", pointerSource);
            JavaCompiler.CompilationTask pointerTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(pointerFile)
            );

            assertTrue(pointerTask.call());
        }
        Path pointerJar = createClasspathJar(pointerClassOutputDir, "javatogpu-annotated-pointer");

        String consumerSource = """
                package sample;

                import custom.MyFloatPtr;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @CCode
                    static float read(MyFloatPtr ptr, int index) {
                        return ptr.add(index).value;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] output) {
                        MyFloatPtr ptr = new MyFloatPtr(3.0f);
                        output[0] = read(ptr, 0);
                    }
                }
                """;

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        try {
            configureCompilationClasspath(fileManager, pointerClassOutputDir, pointerJar);
            List<String> options = List.of(
                    "-classpath", buildCompilationClasspath(pointerClassOutputDir, pointerJar),
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

        String kernelSource = Files.readString(kernelPath);
        assertTrue(kernelSource.contains("float jtg_fn_Demo_read_MyFloatPtr_int(float* ptr, int index)"));
        assertTrue(kernelSource.contains("return (*((ptr) + (index)));"));
        assertFalse(kernelSource.contains(".add("));
    }

    @Test
    void generatesKernelWithAnnotatedScalarAliasTypeFromCompilerMetadata() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path aliasClassOutputDir = Files.createTempDirectory("javatogpu-annotated-alias-classes");
        Path aliasGeneratedOutputDir = Files.createTempDirectory("javatogpu-annotated-alias-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-annotated-alias-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-annotated-alias-consumer-generated");

        String aliasSource = """
                package custom;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUScalarAliasType;

                @GPUScalarAliasType(backendType = "uint", valueType = "int")
                public final class MyUInt {
                    public int value;

                    public MyUInt() {
                    }

                    public MyUInt(int value) {
                        this.value = value;
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", aliasClassOutputDir.toString(),
                    "-s", aliasGeneratedOutputDir.toString()
            );
            JavaFileObject aliasFile = new StringJavaFileObject("custom.MyUInt", aliasSource);
            JavaCompiler.CompilationTask aliasTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(aliasFile)
            );

            assertTrue(aliasTask.call());
        }
        Path aliasJar = createClasspathJar(aliasClassOutputDir, "javatogpu-annotated-alias");

        String consumerSource = """
                package sample;

                import custom.MyUInt;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(MyUInt bias, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        MyUInt local = new MyUInt(17);
                        output[id] = bias.value + local.value;
                    }
                }
                """;

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        try {
            configureCompilationClasspath(fileManager, aliasClassOutputDir, aliasJar);
            List<String> options = List.of(
                    "-classpath", buildCompilationClasspath(aliasClassOutputDir, aliasJar),
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

        String kernelSource = Files.readString(kernelPath);
        assertTrue(kernelSource.contains("__kernel void jtg_kernel(uint bias, __global int* output)"));
        assertTrue(kernelSource.contains("uint local = ((uint) 17);"));
        assertTrue(kernelSource.contains("output[id] = (bias + local);"));
    }

    @Test
    void generatesKernelWithReinterpretedConstantByteView() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-constantbyteview-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-constantbyteview-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.ConstantBytePtr;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUConstant;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUConstant byte[] blob, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        ConstantBytePtr ptr = GPU.constant(blob);
                        output[id] = ptr.add(id * 4).asIntPtr().value;
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
                __kernel void jtg_kernel(__constant char* blob, __global int* output) {
                    int id = get_global_id(0);
                    __constant char* ptr = (blob);
                    output[id] = (*((__constant int*) (((ptr) + ((id * 4))))));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithReinterpretedGlobalByteViewAsChar() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-globalcharview-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-globalcharview-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal byte[] blob, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        GlobalBytePtr ptr = GPU.global(blob);
                        output[id] = ptr.add(id * 2).asCharPtr().value;
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
                __kernel void jtg_kernel(__global char* blob, __global int* output) {
                    int id = get_global_id(0);
                    __global char* ptr = (blob);
                    output[id] = (*((__global char*) (((ptr) + ((id * 2))))));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithReinterpretedConstantByteViewAsShort() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-constantshortview-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-constantshortview-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.ConstantBytePtr;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUConstant;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUConstant byte[] blob, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        ConstantBytePtr ptr = GPU.constant(blob);
                        output[id] = ptr.add(id * 2).asShortPtr().value;
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
                __kernel void jtg_kernel(__constant char* blob, __global int* output) {
                    int id = get_global_id(0);
                    __constant char* ptr = (blob);
                    output[id] = (*((__constant short*) (((ptr) + ((id * 2))))));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithReinterpretedLocalByteView() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-localbyteview-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-localbyteview-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.LocalBytePtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPULocal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPULocal byte[] blob, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        LocalBytePtr ptr = GPU.local(blob);
                        output[id] = ptr.add(id * 4).asIntPtr().value;
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
                __kernel void jtg_kernel(__local char* blob, __global int* output) {
                    int id = get_global_id(0);
                    __local char* ptr = (blob);
                    output[id] = (*((__local int*) (((ptr) + ((id * 4))))));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithReinterpretedLocalByteViewAsLong() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-locallongview-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-locallongview-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.LocalBytePtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPULocal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPULocal byte[] blob, @GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        LocalBytePtr ptr = GPU.local(blob);
                        output[id] = ptr.add(id * 8).asLongPtr().value;
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
                __kernel void jtg_kernel(__local char* blob, __global long* output) {
                    int id = get_global_id(0);
                    __local char* ptr = (blob);
                    output[id] = (*((__local long*) (((ptr) + ((id * 8))))));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithPackedOverlayOffsetReads() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-packedoverlay-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-packedoverlay-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal byte[] nodes, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        GlobalBytePtr node = GPU.global(nodes).add(id * 32);
                        int state = node.readIntAt(0);
                        int child = node.readIntAt(4 + id * 4);
                        int max = node.readShortAt(4 + id * 2);
                        int min = node.readShortAt(18 + id * 2);
                        output[id] = state + child + max - min;
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
                __kernel void jtg_kernel(__global char* nodes, __global int* output) {
                    int id = get_global_id(0);
                    __global char* node = (((nodes)) + ((id * 32)));
                    int state = (*((__global int*) (((node) + (0)))));
                    int child = (*((__global int*) (((node) + ((4 + (id * 4)))))));
                    int max = (*((__global short*) (((node) + ((4 + (id * 2)))))));
                    int min = (*((__global short*) (((node) + ((18 + (id * 2)))))));
                    output[id] = (((state + child) + max) - min);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithRootBlobOffsetPointerHelpers() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-rootblobptr-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-rootblobptr-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                import net.sixik.ga_utils.javatogpu.api.GlobalDoublePtr;
                import net.sixik.ga_utils.javatogpu.api.GlobalIntPtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                public class Demo {
                    @GPUStruct
                    static class RootBlobView {
                        int samplerOffset;
                        int densityOffset;
                        int biasOffset;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal byte[] blob, RootBlobView view, @GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        GlobalBytePtr root = GPU.global(blob);
                        GlobalIntPtr sampler = root.intPtrAt(view.samplerOffset + id * 4);
                        GlobalDoublePtr density = root.doublePtrAt(view.densityOffset + id * 8);
                        double bias = root.doublePtrAt(view.biasOffset).value;
                        output[id] = (double) sampler.value + density.value + bias;
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
                    int samplerOffset;
                    int densityOffset;
                    int biasOffset;
                } RootBlobView;

                __kernel void jtg_kernel(__global char* blob, RootBlobView view, __global double* output) {
                    int id = get_global_id(0);
                    __global char* root = (blob);
                    __global int* sampler = ((__global int*) (((root) + ((view.samplerOffset + (id * 4))))));
                    __global double* density = ((__global double*) (((root) + ((view.densityOffset + (id * 8))))));
                    double bias = (*((__global double*) (((root) + (view.biasOffset)))));
                    output[id] = ((((double) (*sampler)) + (*density)) + bias);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithBridgeHelpersForAdditionalScalarPointerTypes() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-pointer-bridges-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-pointer-bridges-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.ConstantShortPtr;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GlobalIntPtr;
                import net.sixik.ga_utils.javatogpu.api.LocalLongPtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUConstant;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPULocal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal int[] input, @GPUConstant short[] lookup, @GPULocal long[] scratch, @GPUGlobal long[] output) {
                        int id = GPU.get_global_id(0);
                        GlobalIntPtr inputPtr = GPU.global(input);
                        ConstantShortPtr lookupPtr = GPU.constant(lookup);
                        LocalLongPtr scratchPtr = GPU.local(scratch);
                        scratchPtr.add(id).value = (long) inputPtr.add(id).value + (long) lookupPtr.add(id).value;
                        output[id] = scratchPtr.add(id).value;
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
                __kernel void jtg_kernel(__global int* input, __constant short* lookup, __local long* scratch, __global long* output) {
                    int id = get_global_id(0);
                    __global int* inputPtr = (input);
                    __constant short* lookupPtr = (lookup);
                    __local long* scratchPtr = (scratch);
                    (*((scratchPtr) + (id))) = (((long) (*((inputPtr) + (id)))) + ((long) (*((lookupPtr) + (id)))));
                    output[id] = (*((scratchPtr) + (id)));
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithHelperMethodAttributes() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-helper-attr-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-helper-attr-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = Helpers.doubleValue(input[id]);
                    }
                }

                class Helpers {
                    @OpenCLAttributes({"always_inline"})
                    @CCode
                    static float doubleValue(float value) {
                        return value * 2.0f;
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
                __attribute__((always_inline)) float jtg_fn_Helpers_doubleValue_float(float value);

                __attribute__((always_inline)) float jtg_fn_Helpers_doubleValue_float(float value) {
                    return (value * 2.0F);
                }
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    int id = get_global_id(0);
                    output[id] = jtg_fn_Helpers_doubleValue_float(input[id]);
                }""", Files.readString(kernelPath));
    }

    @Test
    void acceptsQualifiedGpuStructTypeWhenSimpleAliasesCollide() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-struct-alias-error-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-struct-alias-error-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

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

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

            assertTrue(task.call());
        }

        Path kernelPath = generatedOutputDir.resolve("javatogpu/sample/Demo/kernel.cl");
        assertTrue(Files.exists(kernelPath));

        String kernelSource = Files.readString(kernelPath);
        assertTrue(kernelSource.contains("float x;"));
        assertFalse(kernelSource.contains("float y;"));
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
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @CCode
                    static Float2 add(Float2 left, Float2 right) {
                        return new Float2(left.x + right.x, left.y + right.y);
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
    void generatesKernelWithAnnotatedUInt3VectorType() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-uint3-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-uint3-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UInt3;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(UInt3 bias, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UInt3 local = new UInt3(1, 2, 3);
                        output[id] = bias.x + bias.y + bias.z + local.x + local.y + local.z;
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
                __kernel void jtg_kernel(uint3 bias, __global int* output) {
                    int id = get_global_id(0);
                    uint3 local = (uint3)(1, 2, 3);
                    output[id] = (((((bias.x + bias.y) + bias.z) + local.x) + local.y) + local.z);
                }""", Files.readString(kernelPath));
    }

    @Test
    void generatesKernelWithAnnotatedUInt16VectorType() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-uint16-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-uint16-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.UInt16;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(UInt16 bias, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UInt16 local = new UInt16(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
                        output[id] = bias.sa + bias.sf + local.s0 + local.sf;
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

        String kernelSource = Files.readString(kernelPath);
        assertTrue(kernelSource.contains("__kernel void jtg_kernel(uint16 bias, __global int* output)"));
        assertTrue(kernelSource.contains("uint16 local = (uint16)(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);"));
        assertTrue(kernelSource.contains("bias.sa"));
        assertTrue(kernelSource.contains("bias.sf"));
        assertTrue(kernelSource.contains("local.s0"));
        assertTrue(kernelSource.contains("local.sf"));
    }

    @Test
    void generatesKernelWithSourceReachableBroadIntrinsicFamilies() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-broad-family-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-broad-family-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int8;
                import net.sixik.ga_utils.javatogpu.api.UInt16;
                import net.sixik.ga_utils.javatogpu.api.ULong8;
                import net.sixik.ga_utils.javatogpu.api.UShort16;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(UInt16 value, ULong8 bits, UShort16 amount, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        UInt16 clipped = GPU.clamp(value, new UInt16(1), new UInt16(255));
                        Int8 counts = GPU.popcount(bits);
                        UShort16 rotated = GPU.rotate(amount, new UShort16((short) 3));
                        output[id] = clipped.s0 + clipped.sf + counts.s0 + counts.s7 + rotated.s0 + rotated.sf;
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

        String kernelSource = Files.readString(kernelPath);
        assertTrue(kernelSource.contains("uint16 clipped = clamp(value, (uint16)(1), (uint16)(255));"));
        assertTrue(kernelSource.contains("int8 counts = popcount(bits);"));
        assertTrue(kernelSource.contains("ushort16 rotated = rotate(amount, (ushort16)(((short) 3)));"));
    }

    @Test
    void generatesKernelWithInstanceVectorOperatorIntrinsic() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-vector-operator-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-vector-operator-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int4;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(Int4 left, Int4 right, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        Int4 sum = left.add(right);
                        output[id] = sum.x + sum.y + sum.z + sum.w;
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

        String kernelSource = Files.readString(kernelPath);
        assertTrue(kernelSource.contains("int4 sum = (left + right);"));
        assertFalse(kernelSource.contains(".add("));
    }

    @Test
    void generatesKernelWithAnnotatedVectorTypeOperatorWithoutMethodIntrinsicAnnotations() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path vectorClassOutputDir = Files.createTempDirectory("javatogpu-annotated-vector-classes");
        Path vectorGeneratedOutputDir = Files.createTempDirectory("javatogpu-annotated-vector-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-annotated-vector-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-annotated-vector-consumer-generated");

        String vectorSource = """
                package custom;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType;

                @GPUVectorType(openClType = "float2", componentType = "float", fields = {"x", "y"})
                public class MyFloat2 {
                    public float x;
                    public float y;

                    public MyFloat2() {
                    }

                    public MyFloat2(float x, float y) {
                        this.x = x;
                        this.y = y;
                    }

                    public MyFloat2 add(MyFloat2 other) {
                        return new MyFloat2(x + other.x, y + other.y);
                    }
                }
                """;

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", vectorClassOutputDir.toString(),
                    "-s", vectorGeneratedOutputDir.toString()
            );
            JavaFileObject vectorFile = new StringJavaFileObject("custom.MyFloat2", vectorSource);
            JavaCompiler.CompilationTask vectorTask = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    List.of(vectorFile)
            );

            assertTrue(vectorTask.call());
        }
        Path vectorJar = createClasspathJar(vectorClassOutputDir, "javatogpu-annotated-vector");

        String consumerSource = """
                package sample;

                import custom.MyFloat2;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(MyFloat2 left, MyFloat2 right, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        MyFloat2 sum = left.add(right);
                        output[id] = sum.x + sum.y;
                    }
                }
                """;

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        try {
            configureCompilationClasspath(fileManager, vectorClassOutputDir, vectorJar);
            List<String> options = List.of(
                    "-classpath", buildCompilationClasspath(vectorClassOutputDir, vectorJar),
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

        String kernelSource = Files.readString(kernelPath);
        assertTrue(kernelSource.contains("__kernel void jtg_kernel(float2 left, float2 right, __global float* output)"));
        assertTrue(kernelSource.contains("float2 sum = (left + right);"));
        assertFalse(kernelSource.contains(".add("));
    }

    @Test
    void generatesKernelWithGuardedCCodeCallbackFallback() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-ccode-guard-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-ccode-guard-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @CCode(support = "OpenCL_3", callback = "fallbackAdd")
                    static float preferredAdd(float a, float b) {
                        return a - b;
                    }

                    @CCode
                    static float fallbackAdd(float a, float b) {
                        return a + b;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = preferredAdd(input[id], 2.0f);
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
        String kernel = Files.readString(kernelPath);
        assertTrue(kernel.contains("#if defined(__OPENCL_C_VERSION__) && (__OPENCL_C_VERSION__ >= 300)"));
        assertTrue(kernel.contains("return (a - b);"));
        assertTrue(kernel.contains("return jtg_fn_Demo_fallbackAdd_float_float(a, b);"));
        assertTrue(kernel.contains("output[id] = jtg_fn_Demo_preferredAdd_float_float(input[id], 2.0F);"));
    }

    @Test
    void generatesKernelWithStructKernelParameter() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-struct-param-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-struct-param-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                @GPUStruct
                class Sample {
                    float x;
                    float y;
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
    void rejectsStructFieldArraysBeforeRuntimeAbiMarshalling() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-struct-array-field-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-struct-array-field-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                @GPUStruct
                class Child {
                    public double value;
                }

                @GPUStruct
                class Parent {
                    public Child[] children;
                    public double[] amplitudes;
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(Parent parent, @GPUGlobal double[] output) {
                        output[0] = 1.0;
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
                String.valueOf(message).contains("Unsupported @GPUStruct field type: Child[]; arrays are not supported inside @GPUStruct fields in the current OpenCL ABI")
                        && String.valueOf(message).contains("move the array to a kernel parameter or flatten it")
                        && String.valueOf(message).contains("docs/Troubleshooting.md")
        ));
    }

    @Test
    void generatesKernelWithStructArrayKernelParameter() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-struct-array-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-struct-array-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                @GPUStruct
                class Sample {
                    float x;
                    float y;
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                        String.valueOf(diagnostic.getMessage(null)).contains("Unsupported GPU parameter type: boolean; use int or byte masks for kernel parameters")
                                && String.valueOf(diagnostic.getMessage(null)).contains("docs/Troubleshooting.md")
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

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                                "Array parameters must be annotated with @GPUGlobal, @GPUConstant, or @GPULocal in the current pipeline: float[]; for example: @GPUGlobal float[] input"
                        )
                                && String.valueOf(diagnostic.getMessage(null)).contains("move buffer-backed data out of private locals")
                                && String.valueOf(diagnostic.getMessage(null)).contains("docs/Troubleshooting.md")
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUConstant;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPULocal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsicLibrary;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                @GPUIntrinsicLibrary
                class MyIntrinsics {
                    static final float SCALE = 0.25f;

                    @GPUIntrinsic(code = "(({0}) * 2.0f)")
                    static float twice(float value) {
                        return value * 2.0f;
                    }
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
    void rejectsGpuConstantDataOnUnsupportedArrayType() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-constantdata-invalid-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-constantdata-invalid-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Float2;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUConstantData;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @GPUConstantData
                    static final Float2[] LOOKUP = {new Float2(1.0f, 2.0f)};

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = input[id];
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
                    diagnostics,
                    options,
                    null,
                    List.of(sourceFile)
            );
            task.setProcessors(List.of(new GpuCompilerProcessor()));

            assertFalse(task.call());
        }

        assertTrue(
                diagnostics.getDiagnostics().stream().anyMatch(diagnostic -> {
                    String message = String.valueOf(diagnostic.getMessage(null));
                    return message.contains("Failed to compile @GPU method:")
                            && message.contains("@GPUConstantData currently supports only primitive scalar arrays")
                            && message.contains("Float2[]");
                })
        );
    }

    @Test
    void generatesKernelWithExternGpuConstantDataDuringCompilation() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-extern-constantdata-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-extern-constantdata-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUExternConstantData;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @GPUExternConstantData
                    static final int[] LOOKUP = null;

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = input[id] + LOOKUP[id];
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
                extern __constant int LOOKUP[];

                __kernel void jtg_kernel(__global int* input, __global int* output) {
                    int id = get_global_id(0);
                    output[id] = (input[id] + LOOKUP[id]);
                }""", Files.readString(kernelPath));
    }

    @Test
    void rejectsExternGpuConstantDataWithoutNullInitializer() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-extern-constantdata-invalid-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-extern-constantdata-invalid-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUExternConstantData;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @GPUExternConstantData
                    static final int[] LOOKUP = {1, 2, 3};

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = input[id];
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
                diagnostics.getDiagnostics().stream().anyMatch(diagnostic -> {
                    String message = String.valueOf(diagnostic.getMessage(null));
                    return message.contains("Failed to compile @GPU method:")
                            && message.contains("@GPUExternConstantData field must declare a null initializer")
                            && message.contains("LOOKUP");
                })
        );
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

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsicLibrary;

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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

                import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;

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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsicLibrary;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                @GPUIntrinsicLibrary(backends = {GpuBackendTarget.CUDA})
                class MyCudaIntrinsics {
                    @GPUIntrinsic(code = "(({0}) * 2.0f)", backends = {GpuBackendTarget.CUDA})
                    static float twice(float value) {
                        return value * 2.0f;
                    }
                }

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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
    void rejectsSeparateCompilationIntrinsicLibraryWhenItTargetsDifferentBackend() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path intrinsicClassOutputDir = Files.createTempDirectory("javatogpu-cudaintrinsiclib-classes");
        Path intrinsicGeneratedOutputDir = Files.createTempDirectory("javatogpu-cudaintrinsiclib-generated");
        Path consumerClassOutputDir = Files.createTempDirectory("javatogpu-cudaintrinsiclib-consumer-classes");
        Path consumerGeneratedOutputDir = Files.createTempDirectory("javatogpu-cudaintrinsiclib-consumer-generated");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        String intrinsicSource = """
                package lib;

                import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsicLibrary;

                @GPUIntrinsicLibrary(backends = {GpuBackendTarget.CUDA})
                public class ReusableCudaIntrinsics {
                    @GPUIntrinsic(code = "(({0}) * 2.0f)", backends = {GpuBackendTarget.CUDA})
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
            JavaFileObject intrinsicFile = new StringJavaFileObject("lib.ReusableCudaIntrinsics", intrinsicSource);
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
        Path intrinsicJar = createClasspathJar(intrinsicClassOutputDir, "javatogpu-cudaonlyintrinsiclib");

        String consumerSource = """
                package sample;

                import lib.ReusableCudaIntrinsics;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                public class Demo {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                        int id = GPU.get_global_id(0);
                        output[id] = ReusableCudaIntrinsics.twice(input[id]);
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
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                public class Demo {
                    @GPUStruct
                    static class Point {
                        float x;
                        float y;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
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

    @Test
    void compilesRepresentativePerlinNoiseWorkload() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-perlinworkload-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-perlinworkload-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.Double3;
                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.Int3;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                public class Demo {

                    @GPUStruct
                    static class PerlinNoiseInfo {
                        int firstOctave;
                        int noiseLevelCount;
                        double lowestFreqValueFactor;
                        double lowestFreqInputFactor;
                        double maxValue;
                        Int3 levelActive;
                        Double3 levelXo;
                        Double3 levelYo;
                        Double3 levelZo;
                        Double3 amplitudes;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(PerlinNoiseInfo noise,
                                       @GPUGlobal byte[] permutation0,
                                       @GPUGlobal byte[] permutation1,
                                       @GPUGlobal byte[] permutation2,
                                       @GPUGlobal double[] outValues) {
                        int id = GPU.get_global_id(0);
                        double x = (id & 15) * 0.125;
                        double z = (id >> 4) * 0.125;
                        outValues[id] = NoiseMath.perlinValue(noise, permutation0, permutation1, permutation2, x, 0.0, z);
                    }

                    static final class NoiseMath {
                        private NoiseMath() {
                        }

                        @CCode
                        static double perlinValue(
                                PerlinNoiseInfo noise,
                                @GPUGlobal byte[] permutation0,
                                @GPUGlobal byte[] permutation1,
                                @GPUGlobal byte[] permutation2,
                                double x,
                                double y,
                                double z
                        ) {
                            double value = 0.0;
                            double inputFactor0 = noise.lowestFreqInputFactor;
                            double valueFactor0 = noise.lowestFreqValueFactor;
                            double inputFactor1 = inputFactor0 * 2.0;
                            double valueFactor1 = valueFactor0 * 0.5;
                            double inputFactor2 = inputFactor1 * 2.0;
                            double valueFactor2 = valueFactor1 * 0.5;

                            if (noise.levelActive.x != 0) {
                                value += noise.amplitudes.x * improvedNoise(
                                        permutation0,
                                        noise.levelXo.x,
                                        noise.levelYo.x,
                                        noise.levelZo.x,
                                        wrap(x * inputFactor0),
                                        wrap(y * inputFactor0),
                                        wrap(z * inputFactor0),
                                        0.0,
                                        0.0
                                ) * valueFactor0;
                            }

                            if (noise.levelActive.y != 0) {
                                value += noise.amplitudes.y * improvedNoise(
                                        permutation1,
                                        noise.levelXo.y,
                                        noise.levelYo.y,
                                        noise.levelZo.y,
                                        wrap(x * inputFactor1),
                                        wrap(y * inputFactor1),
                                        wrap(z * inputFactor1),
                                        0.0,
                                        0.0
                                ) * valueFactor1;
                            }

                            if (noise.levelActive.z != 0) {
                                value += noise.amplitudes.z * improvedNoise(
                                        permutation2,
                                        noise.levelXo.z,
                                        noise.levelYo.z,
                                        noise.levelZo.z,
                                        wrap(x * inputFactor2),
                                        wrap(y * inputFactor2),
                                        wrap(z * inputFactor2),
                                        0.0,
                                        0.0
                                ) * valueFactor2;
                            }

                            return value;
                        }

                        @CCode(inline = true)
                        static double improvedNoise(
                                @GPUGlobal byte[] permutations,
                                double xo,
                                double yo,
                                double zo,
                                double x,
                                double y,
                                double z,
                                double step,
                                double limit
                        ) {
                            double shiftedX = x + xo;
                            double shiftedY = y + yo;
                            double shiftedZ = z + zo;
                            int floorX = floorToInt(shiftedX);
                            int floorY = floorToInt(shiftedY);
                            int floorZ = floorToInt(shiftedZ);
                            double localX = shiftedX - floorX;
                            double localY = shiftedY - floorY;
                            double localZ = shiftedZ - floorZ;
                            double snappedY = 0.0;

                            if (step != 0.0) {
                                double clampedY = limit >= 0.0 && limit < localY ? limit : localY;
                                snappedY = floorToLong(clampedY / step + 1.0E-7) * step;
                            }

                            return sampleAndLerp(permutations, floorX, floorY, floorZ, localX, localY - snappedY, localZ, localY);
                        }

                        @CCode(inline = true)
                        static double sampleAndLerp(
                                @GPUGlobal byte[] permutations,
                                int x,
                                int y,
                                int z,
                                double localX,
                                double localY,
                                double localZ,
                                double smoothYInput
                        ) {
                            int px0 = permutation(permutations, x);
                            int px1 = permutation(permutations, x + 1);
                            int py00 = permutation(permutations, px0 + y);
                            int py01 = permutation(permutations, px0 + y + 1);
                            int py10 = permutation(permutations, px1 + y);
                            int py11 = permutation(permutations, px1 + y + 1);

                            double g000 = gradDot(permutation(permutations, py00 + z), localX, localY, localZ);
                            double g100 = gradDot(permutation(permutations, py10 + z), localX - 1.0, localY, localZ);
                            double g010 = gradDot(permutation(permutations, py01 + z), localX, localY - 1.0, localZ);
                            double g110 = gradDot(permutation(permutations, py11 + z), localX - 1.0, localY - 1.0, localZ);
                            double g001 = gradDot(permutation(permutations, py00 + z + 1), localX, localY, localZ - 1.0);
                            double g101 = gradDot(permutation(permutations, py10 + z + 1), localX - 1.0, localY, localZ - 1.0);
                            double g011 = gradDot(permutation(permutations, py01 + z + 1), localX, localY - 1.0, localZ - 1.0);
                            double g111 = gradDot(permutation(permutations, py11 + z + 1), localX - 1.0, localY - 1.0, localZ - 1.0);

                            double smoothX = smoothstep(localX);
                            double smoothY = smoothstep(smoothYInput);
                            double smoothZ = smoothstep(localZ);
                            return lerp3(smoothX, smoothY, smoothZ, g000, g100, g010, g110, g001, g101, g011, g111);
                        }

                        @CCode(inline = true)
                        static int permutation(@GPUGlobal byte[] permutations, int index) {
                            byte value = permutations[index & 255];
                            return value < 0 ? value + 256 : value;
                        }

                        @CCode(inline = true)
                        static double gradDot(int gradient, double x, double y, double z) {
                            switch (gradient & 15) {
                                case 0:
                                    return x + y;
                                case 1:
                                    return -x + y;
                                case 2:
                                    return x - y;
                                case 3:
                                    return -x - y;
                                case 4:
                                    return x + z;
                                case 5:
                                    return -x + z;
                                case 6:
                                    return x - z;
                                case 7:
                                    return -x - z;
                                case 8:
                                    return y + z;
                                case 9:
                                    return -y + z;
                                case 10:
                                    return y - z;
                                case 11:
                                    return -y - z;
                                case 12:
                                    return x + y;
                                case 13:
                                    return -y + z;
                                case 14:
                                    return -x + y;
                                default:
                                    return -y - z;
                            }
                        }

                        @CCode(inline = true)
                        static double smoothstep(double value) {
                            return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
                        }

                        @CCode(inline = true)
                        static double lerp(double delta, double start, double end) {
                            return start + delta * (end - start);
                        }

                        @CCode(inline = true)
                        static double lerp2(double dx, double dy, double x0y0, double x1y0, double x0y1, double x1y1) {
                            return lerp(dy, lerp(dx, x0y0, x1y0), lerp(dx, x0y1, x1y1));
                        }

                        @CCode(inline = true)
                        static double lerp3(
                                double dx,
                                double dy,
                                double dz,
                                double x0y0z0,
                                double x1y0z0,
                                double x0y1z0,
                                double x1y1z0,
                                double x0y0z1,
                                double x1y0z1,
                                double x0y1z1,
                                double x1y1z1
                        ) {
                            return lerp(
                                    dz,
                                    lerp2(dx, dy, x0y0z0, x1y0z0, x0y1z0, x1y1z0),
                                    lerp2(dx, dy, x0y0z1, x1y0z1, x0y1z1, x1y1z1)
                            );
                        }

                        @CCode(inline = true)
                        static double wrap(double value) {
                            return value - (double) floorToLong(value / 3.3554432E7 + 0.5) * 3.3554432E7;
                        }

                        @CCode(inline = true)
                        static long floorToLong(double value) {
                            long whole = (long) value;
                            return value < whole ? whole - 1L : whole;
                        }

                        @CCode(inline = true)
                        static int floorToInt(double value) {
                            return (int) floorToLong(value);
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
        String kernel = Files.readString(kernelPath);
        System.out.println(kernel);
        assertTrue(kernel.contains("typedef struct{"));
        assertTrue(kernel.contains("} PerlinNoiseInfo;"));
        assertTrue(kernel.contains("double jtg_fn_NoiseMath_perlinValue_PerlinNoiseInfo_bytearrarr_bytearrarr_bytearrarr_double_double_double("));
        assertTrue(kernel.contains("inline double jtg_fn_NoiseMath_improvedNoise_bytearrarr_double_double_double_double_double_double_double_double("));
        assertTrue(kernel.contains("switch ((gradient & 15))"));
        assertTrue(kernel.contains("char value = permutations[(index & 255)];"));
        assertTrue(kernel.contains("outValues[id] = jtg_fn_NoiseMath_perlinValue_PerlinNoiseInfo_bytearrarr_bytearrarr_bytearrarr_double_double_double("));
    }

    @Test
    void compilesRepresentativePackedBlobOffsetViewWorkload() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classOutputDir = Files.createTempDirectory("javatogpu-packedblob-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-packedblob-generated");

        String source = """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                public class Demo {
                    @GPUStruct
                    static class PackedNoiseView {
                        int samplerOffset;
                        int densityOffset;
                    }

                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    static void kernel(@GPUGlobal byte[] blob, PackedNoiseView view, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        GlobalBytePtr root = GPU.global(blob);
                        int sampler = root.add(view.samplerOffset + id * 4).asIntPtr().value;
                        int density = root.add(view.densityOffset + id * 4).asIntPtr().value;
                        output[id] = sampler + density;
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
        String kernel = Files.readString(kernelPath);
        assertTrue(kernel.contains("typedef struct{"));
        assertTrue(kernel.contains("} PackedNoiseView;"));
        assertTrue(kernel.contains("view.samplerOffset"));
        assertTrue(kernel.contains("view.densityOffset"));
        assertTrue(kernel.contains("__global int*)"));
        assertTrue(kernel.contains("id * 4"));
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
