package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClException;
import net.sixik.ga_utils.javatogpu.api.Float2;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMipmappedWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.UInt;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;
import net.sixik.ga_utils.javatogpu.processors.GpuCompilerProcessor;
import net.sixik.ga_utils.javatogpu.runtime.GpuGeneratedLauncherInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFeature;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionOperatorAcceptance;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestCache;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestMode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestOutcome;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestProfile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Random;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClGpuRuntimeBackendIntegrationTest {

    private static final String LONG_RUNNING_PROPERTY = "javatogpu.opencl.longRunning";
    private static final String LONG_RUNNING_SUMMARY_FILE_PROPERTY = "javatogpu.opencl.longRunningSummaryFile";
    private static final String WORKLOAD_VALIDATION_PROPERTY = "javatogpu.opencl.workloadValidation";
    private static final String WORKLOAD_SUMMARY_FILE_PROPERTY = "javatogpu.opencl.workloadSummaryFile";
    private static final String BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY =
            "javatogpu.opencl.backendSourcePromotionWorkloadGateFile";
    private static final String IRGPU_SOURCE_REVIEW_PROPERTY = "javatogpu.opencl.irGpuSourceReview";
    private static final String IRGPU_SOURCE_REVIEW_FILE_PROPERTY = "javatogpu.opencl.irGpuSourceReviewFile";
    private static final String PRODUCTION_SOURCE_SWITCHING_VALIDATION_PROPERTY = "javatogpu.opencl.productionSourceSwitchingValidation";
    private static final String PRODUCTION_SOURCE_SWITCHING_VALIDATION_FILE_PROPERTY = "javatogpu.opencl.productionSourceSwitchingValidationFile";
    private static final String IMAGE_KERNEL_IRGPU_RESOURCE = "javatogpu/runtime/opencl/integration/image-kernel.irgpu.properties";
    private static final String SIMPLE_IRGPU_SOURCE_RESOURCE = "javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties";
    private static final String DUAL_BUFFER_INT_IRGPU_RESOURCE = "javatogpu/runtime/opencl/integration/dual-buffer-int-kernel.irgpu.properties";
    private static final String PERLIN_KERNEL_RESOURCE = "javatogpu/sample/PerlinWorkload/kernel.cl";

    @Test
    void runsRequiredStartupDeviceSelfTestOnAvailableOpenClDevice() {
        assumeOpenClAvailable();
        GpuRuntimeDeviceSelfTestCache cache = new GpuRuntimeDeviceSelfTestCache();
        GpuRuntimeDevicePolicyRegistry registry = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns(cache);
        GpuRuntimeCompileOptions compileOptions = GpuRuntimeCompileOptions.defaults(
                net.sixik.ga_utils.javatogpu.api.GpuBackendTarget.OPENCL
        ).withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.REQUIRED);

        try (OpenClRuntimeSession session = OpenClRuntimeSession.createDefault(
                registry,
                null,
                compileOptions
        )) {
            List<net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestResult> results =
                    cache.resultsFor(session.deviceProfile());
            assertEquals(1, results.size());
            assertEquals(GpuRuntimeDeviceSelfTestOutcome.PASSED, results.get(0).outcome());
            assertEquals(OpenClRuntimeDeviceSelfTestRunner.RUNNER_ID, results.get(0).identity().runnerId());
            GpuRuntimeDeviceSelfTestProfile expectedProfile =
                    GpuRuntimeDeviceSelfTestProfile.forDevice(session.deviceProfile());
            assertEquals(expectedProfile.profileId(), results.get(0).performance().workloadProfile());
            assertEquals(expectedProfile.transferModel(), results.get(0).performance().transferModel());
            assertTrue(results.get(0).performance().available());
            assertEquals(5, results.get(0).performance().computeSamples().sampleCount());
            assertEquals(5, results.get(0).performance().transferSamples().sampleCount());
            assertTrue(results.get(0).performance().computeOperationsPerSecond() > 0L);
            assertTrue(results.get(0).performance().transferBytesPerSecond() > 0L);
            Map<String, String> fields = session.deviceSelection().artifactFields("device");
            assertTrue(fields.values().contains("accepted"));
            assertTrue(fields.values().stream().anyMatch(value ->
                    value.endsWith("performance.computeOperationsPerSecond")
            ));
        }
    }

    @Test
    void runsGeneratedLauncherHelperPipelineOnAvailableOpenClDevice() throws Exception {
        assumeOpenClAvailable();

        CompiledGpuSource compiled = compileGpuSource(
                "sample.HelperPipeline",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.GPU;
                        import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class HelperPipeline {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            public static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                                int id = GPU.get_global_id(0);
                                output[id] = Helpers.square(input[id]) + 1.0f;
                            }
                        }

                        class Helpers {
                            @CCode(inline = true)
                            static float square(float value) {
                                return value * value;
                            }
                        }
                        """
        );

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.HelperPipeline", true, classLoader);
            float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

            GpuGeneratedLauncherInvoker.invoke(ownerClass, "kernel", input, output);

            assertArrayEquals(new float[]{2.0f, 5.0f, 10.0f, 17.0f}, output);
        }
    }

    @Test
    void runsGeneratedLauncherStructPipelineOnAvailableOpenClDevice() throws Exception {
        assumeOpenClAvailable();

        CompiledGpuSource compiled = compileGpuSource(
                "sample.StructPipeline",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.GPU;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                        public class StructPipeline {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            public static void kernel(@GPUGlobal Sample[] input, @GPUGlobal float[] output) {
                                int id = GPU.get_global_id(0);
                                output[id] = input[id].x + input[id].y + input[id].count;
                            }

                            @GPUStruct
                            public static final class Sample {
                                public float x;
                                public float y;
                                public int count;

                                public Sample() {
                                }

                                public Sample(float x, float y, int count) {
                                    this.x = x;
                                    this.y = y;
                                    this.count = count;
                                }
                            }
                        }
                        """
        );

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.StructPipeline", true, classLoader);
            Class<?> sampleClass = Class.forName("sample.StructPipeline$Sample", true, classLoader);

            Object sample0 = sampleClass.getConstructor(float.class, float.class, int.class).newInstance(1.0f, 2.0f, 3);
            Object sample1 = sampleClass.getConstructor(float.class, float.class, int.class).newInstance(4.0f, 5.0f, 6);
            Object input = java.lang.reflect.Array.newInstance(sampleClass, 2);
            java.lang.reflect.Array.set(input, 0, sample0);
            java.lang.reflect.Array.set(input, 1, sample1);
            float[] output = new float[]{0.0f, 0.0f};

            GpuGeneratedLauncherInvoker.invoke(ownerClass, "kernel", input, output);

            assertArrayEquals(new float[]{6.0f, 15.0f}, output);
        }
    }

    @Test
    void runsGeneratedLauncherVectorPipelineOnAvailableOpenClDevice() throws Exception {
        assumeOpenClAvailable();

        CompiledGpuSource compiled = compileGpuSource(
                "sample.VectorPipeline",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.Float2;
                        import net.sixik.ga_utils.javatogpu.api.GPU;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

                        public class VectorPipeline {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            public static void kernel(Float2 bias, @GPUGlobal float[] output) {
                                int id = GPU.get_global_id(0);
                                output[id] = bias.x + bias.y + id;
                            }
                        }
                        """
        );

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.VectorPipeline", true, classLoader);
            float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

            GpuGeneratedLauncherInvoker.invoke(ownerClass, "kernel", new Float2(1.5f, 2.0f), output);

            assertArrayEquals(new float[]{3.5f, 4.5f, 5.5f, 6.5f}, output);
        }
    }

    @Test
    void comparesGeneratedLauncherPerlinWorkloadAgainstCpuReferenceOnAvailableOpenClDevice() throws Exception {
        assumeOpenClAvailable();
        assumeOpenClFp64Available("Skipping Perlin workload integration test: no fp64 support");

        CompiledGpuSource compiled = compileGpuSource("sample.PerlinWorkload", perlinWorkloadSource());

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.PerlinWorkload", true, classLoader);
            Class<?> fixtureClass = Class.forName("sample.PerlinWorkload$Fixture", true, classLoader);

            Object fixture = ownerClass.getMethod("createDefaultFixture", long.class).invoke(null, 255L);
            Object noise = fixtureClass.getField("info").get(fixture);
            byte[] permutation0 = (byte[]) fixtureClass.getField("permutation0").get(fixture);
            byte[] permutation1 = (byte[]) fixtureClass.getField("permutation1").get(fixture);
            byte[] permutation2 = (byte[]) fixtureClass.getField("permutation2").get(fixture);

            double[] cpuOutput = new double[288];
            double[] gpuOutput = new double[288];

            ownerClass.getMethod("cpuKernel", noise.getClass(), byte[].class, byte[].class, byte[].class, double[].class)
                    .invoke(null, noise, permutation0, permutation1, permutation2, cpuOutput);

            clearLaunchAdvisoryIfConfigured(PERLIN_KERNEL_RESOURCE);
            GpuGeneratedLauncherInvoker.invokeWithConfig(
                    ownerClass,
                    "kernel",
                    net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(288L, 48L),
                    noise,
                    permutation0,
                    permutation1,
                    permutation2,
                    gpuOutput
            );

            for (int i = 0; i < cpuOutput.length; i++) {
                org.junit.jupiter.api.Assertions.assertEquals(cpuOutput[i], gpuOutput[i], 1.0e-9, "Mismatch at index " + i);
            }
            assertNonBlockingLaunchAdvisoryIfConfigured(PERLIN_KERNEL_RESOURCE, 48L);
        }
    }

    @Test
    void comparesGeneratedLauncherPackedBlobWorkloadAgainstCpuReferenceOnAvailableOpenClDevice() throws Exception {
        assumeOpenClAvailable();

        CompiledGpuSource compiled = compileGpuSource(
                "sample.PackedBlobWorkload",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.GPU;
                        import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                        import java.nio.ByteBuffer;
                        import java.nio.ByteOrder;

                        public class PackedBlobWorkload {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            public static void kernel(@GPUGlobal byte[] blob, PackedNoiseView view, @GPUGlobal int[] output) {
                                int id = GPU.get_global_id(0);
                                GlobalBytePtr root = GPU.global(blob);
                                int sampler = root.add(view.samplerOffset + id * 4).asIntPtr().value;
                                int density = root.add(view.densityOffset + id * 4).asIntPtr().value;
                                output[id] = sampler + density;
                            }

                            public static void cpuKernel(byte[] blob, PackedNoiseView view, int[] output) {
                                ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                                for (int id = 0; id < output.length; id++) {
                                    int sampler = buffer.getInt(view.samplerOffset + id * 4);
                                    int density = buffer.getInt(view.densityOffset + id * 4);
                                    output[id] = sampler + density;
                                }
                            }

                            @GPUStruct
                            public static class PackedNoiseView {
                                public int samplerOffset;
                                public int densityOffset;

                                public PackedNoiseView() {
                                }

                                public PackedNoiseView(int samplerOffset, int densityOffset) {
                                    this.samplerOffset = samplerOffset;
                                    this.densityOffset = densityOffset;
                                }
                            }

                            public static final class Fixture {
                                public final byte[] blob;
                                public final PackedNoiseView view;

                                public Fixture(byte[] blob, PackedNoiseView view) {
                                    this.blob = blob;
                                    this.view = view;
                                }
                            }

                            public static Fixture createFixture() {
                                int[] samplerValues = new int[]{7, 14, 21, 28, 35, 42, 49, 56};
                                int[] densityValues = new int[]{3, 6, 9, 12, 15, 18, 21, 24};
                                int samplerOffset = 0;
                                int densityOffset = samplerValues.length * 4;
                                byte[] blob = new byte[(samplerValues.length + densityValues.length) * 4];
                                ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                                for (int i = 0; i < samplerValues.length; i++) {
                                    buffer.putInt(samplerOffset + i * 4, samplerValues[i]);
                                    buffer.putInt(densityOffset + i * 4, densityValues[i]);
                                }
                                return new Fixture(blob, new PackedNoiseView(samplerOffset, densityOffset));
                            }
                        }
                        """
        );

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.PackedBlobWorkload", true, classLoader);
            Class<?> fixtureClass = Class.forName("sample.PackedBlobWorkload$Fixture", true, classLoader);

            Object fixture = ownerClass.getMethod("createFixture").invoke(null);
            byte[] blob = (byte[]) fixtureClass.getField("blob").get(fixture);
            Object view = fixtureClass.getField("view").get(fixture);

            int[] cpuOutput = new int[8];
            int[] gpuOutput = new int[8];

            ownerClass.getMethod("cpuKernel", byte[].class, view.getClass(), int[].class)
                    .invoke(null, blob, view, cpuOutput);

            GpuGeneratedLauncherInvoker.invokeWithGlobalWorkSize(ownerClass, "kernel", 8L, blob, view, gpuOutput);

            assertArrayEquals(cpuOutput, gpuOutput);
        }
    }

    @Test
    void comparesGeneratedLauncherPackedNumericWorkloadAgainstCpuReferenceOnAvailableOpenClDevice() throws Exception {
        assumeOpenClAvailable();
        assumeOpenClFp64Available("Skipping packed numeric workload integration test: no fp64 support");

        CompiledGpuSource compiled = compileGpuSource(
                "sample.PackedNumericWorkload",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.GPU;
                        import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                        import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                        import java.nio.ByteBuffer;
                        import java.nio.ByteOrder;

                        public class PackedNumericWorkload {
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            public static void kernel(@GPUGlobal byte[] blob, PackedNumericView view, @GPUGlobal double[] output) {
                                int id = GPU.get_global_id(0);
                                GlobalBytePtr root = GPU.global(blob);
                                double sampler = root.add(view.samplerOffset + id * 8).asDoublePtr().value;
                                double density = root.add(view.densityOffset + id * 8).asDoublePtr().value;
                                int octave = root.add(view.octaveOffset + id * 4).asIntPtr().value;
                                double bias = root.add(view.biasOffset).asDoublePtr().value;
                                output[id] = NumericMath.mix(sampler, density, octave, bias);
                            }

                            public static double cpuMix(double sampler, double density, int octave, double bias) {
                                return (sampler * 0.75) + (density * 1.25) + octave * bias;
                            }

                            public static void cpuKernel(byte[] blob, PackedNumericView view, double[] output) {
                                ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                                for (int id = 0; id < output.length; id++) {
                                    double sampler = buffer.getDouble(view.samplerOffset + id * 8);
                                    double density = buffer.getDouble(view.densityOffset + id * 8);
                                    int octave = buffer.getInt(view.octaveOffset + id * 4);
                                    double bias = buffer.getDouble(view.biasOffset);
                                    output[id] = cpuMix(sampler, density, octave, bias);
                                }
                            }

                            @GPUStruct
                            public static class PackedNumericView {
                                public int samplerOffset;
                                public int densityOffset;
                                public int octaveOffset;
                                public int biasOffset;

                                public PackedNumericView() {
                                }

                                public PackedNumericView(int samplerOffset, int densityOffset, int octaveOffset, int biasOffset) {
                                    this.samplerOffset = samplerOffset;
                                    this.densityOffset = densityOffset;
                                    this.octaveOffset = octaveOffset;
                                    this.biasOffset = biasOffset;
                                }
                            }

                            public static final class NumericMath {
                                private NumericMath() {
                                }

                                @CCode(inline = true)
                                public static double mix(double sampler, double density, int octave, double bias) {
                                    return (sampler * 0.75) + (density * 1.25) + octave * bias;
                                }
                            }

                            public static final class Fixture {
                                public final byte[] blob;
                                public final PackedNumericView view;

                                public Fixture(byte[] blob, PackedNumericView view) {
                                    this.blob = blob;
                                    this.view = view;
                                }
                            }

                            public static Fixture createFixture() {
                                double[] samplerValues = new double[]{1.5, 2.5, 3.5, 4.5, 5.5, 6.5};
                                double[] densityValues = new double[]{0.25, 0.5, 0.75, 1.0, 1.25, 1.5};
                                int[] octaveValues = new int[]{1, 2, 3, 4, 5, 6};
                                double bias = 0.125;

                                int samplerOffset = 0;
                                int densityOffset = samplerValues.length * 8;
                                int octaveOffset = densityOffset + densityValues.length * 8;
                                int biasOffset = octaveOffset + octaveValues.length * 4;
                                byte[] blob = new byte[biasOffset + 8];
                                ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);

                                for (int i = 0; i < samplerValues.length; i++) {
                                    buffer.putDouble(samplerOffset + i * 8, samplerValues[i]);
                                    buffer.putDouble(densityOffset + i * 8, densityValues[i]);
                                    buffer.putInt(octaveOffset + i * 4, octaveValues[i]);
                                }
                                buffer.putDouble(biasOffset, bias);
                                return new Fixture(blob, new PackedNumericView(samplerOffset, densityOffset, octaveOffset, biasOffset));
                            }
                        }
                        """
        );

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.PackedNumericWorkload", true, classLoader);
            Class<?> fixtureClass = Class.forName("sample.PackedNumericWorkload$Fixture", true, classLoader);

            Object fixture = ownerClass.getMethod("createFixture").invoke(null);
            byte[] blob = (byte[]) fixtureClass.getField("blob").get(fixture);
            Object view = fixtureClass.getField("view").get(fixture);

            double[] cpuOutput = new double[6];
            double[] gpuOutput = new double[6];

            ownerClass.getMethod("cpuKernel", byte[].class, view.getClass(), double[].class)
                    .invoke(null, blob, view, cpuOutput);

            GpuGeneratedLauncherInvoker.invokeWithGlobalWorkSize(ownerClass, "kernel", 6L, blob, view, gpuOutput);

            for (int i = 0; i < cpuOutput.length; i++) {
                org.junit.jupiter.api.Assertions.assertEquals(cpuOutput[i], gpuOutput[i], 1.0e-9, "Mismatch at index " + i);
            }
        }
    }

    @Test
    void comparesGeneratedLauncherSynthetic3DPackedGridWorkloadAgainstCpuReferenceOnAvailableOpenClDevice() throws Exception {
        assumeOpenClAvailable();

        CompiledGpuSource compiled = compileGpuSource(
                "sample.Synthetic3DPackedGridWorkload",
                """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.GPU;
                        import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                        import net.sixik.ga_utils.javatogpu.api.GlobalIntPtr;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
                        import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;

                        import java.nio.ByteBuffer;
                        import java.nio.ByteOrder;

                        public class Synthetic3DPackedGridWorkload {
                            @OpenCLAttributes({"reqd_work_group_size(8, 8, 1)"})
                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            public static void kernel(@GPUGlobal byte[] blob, PackedGridLayout layout, @GPUGlobal int[] output) {
                                int relX = GPU.get_global_id(0);
                                int relZ = GPU.get_global_id(1);
                                int relY = GPU.get_global_id(2);
                                int idx = (relY * layout.depth + relZ) * layout.width + relX;
                                GlobalBytePtr root = GPU.global(blob);
                                int primary = root.intPtrAt(layout.primaryOffset + idx * 4).value;
                                int secondary = root.readIntAt(layout.secondaryOffset + idx * 4);
                                GlobalIntPtr adjustment = root.intPtrAt(layout.adjustmentOffset);
                                output[idx] = primary + secondary + adjustment.value + relX - relZ + relY;
                            }

                            public static void cpuKernel(byte[] blob, PackedGridLayout layout, int[] output) {
                                ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                                for (int relY = 0; relY < layout.height; relY++) {
                                    for (int relZ = 0; relZ < layout.depth; relZ++) {
                                        for (int relX = 0; relX < layout.width; relX++) {
                                            int idx = (relY * layout.depth + relZ) * layout.width + relX;
                                            int primary = buffer.getInt(layout.primaryOffset + idx * 4);
                                            int secondary = buffer.getInt(layout.secondaryOffset + idx * 4);
                                            int adjustment = buffer.getInt(layout.adjustmentOffset);
                                            output[idx] = primary + secondary + adjustment + relX - relZ + relY;
                                        }
                                    }
                                }
                            }

                            @GPUStruct
                            public static class PackedGridLayout {
                                public int primaryOffset;
                                public int secondaryOffset;
                                public int adjustmentOffset;
                                public int width;
                                public int depth;
                                public int height;

                                public PackedGridLayout() {
                                }

                                public PackedGridLayout(int primaryOffset, int secondaryOffset, int adjustmentOffset, int width, int depth, int height) {
                                    this.primaryOffset = primaryOffset;
                                    this.secondaryOffset = secondaryOffset;
                                    this.adjustmentOffset = adjustmentOffset;
                                    this.width = width;
                                    this.depth = depth;
                                    this.height = height;
                                }
                            }

                            public static final class Fixture {
                                public final byte[] blob;
                                public final PackedGridLayout layout;

                                public Fixture(byte[] blob, PackedGridLayout layout) {
                                    this.blob = blob;
                                    this.layout = layout;
                                }
                            }

                            public static Fixture createFixture() {
                                int width = 8;
                                int depth = 8;
                                int height = 2;
                                int count = width * depth * height;
                                int primaryOffset = 0;
                                int secondaryOffset = primaryOffset + count * 4;
                                int adjustmentOffset = secondaryOffset + count * 4;
                                byte[] blob = new byte[adjustmentOffset + 4];
                                ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                                for (int i = 0; i < count; i++) {
                                    buffer.putInt(primaryOffset + i * 4, i * 3 + 7);
                                    buffer.putInt(secondaryOffset + i * 4, 1000 - i * 5);
                                }
                                buffer.putInt(adjustmentOffset, 13);
                                return new Fixture(blob, new PackedGridLayout(primaryOffset, secondaryOffset, adjustmentOffset, width, depth, height));
                            }
                        }
                        """
        );

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.Synthetic3DPackedGridWorkload", true, classLoader);
            Class<?> fixtureClass = Class.forName("sample.Synthetic3DPackedGridWorkload$Fixture", true, classLoader);

            Object fixture = ownerClass.getMethod("createFixture").invoke(null);
            byte[] blob = (byte[]) fixtureClass.getField("blob").get(fixture);
            Object layout = fixtureClass.getField("layout").get(fixture);

            int[] cpuOutput = new int[8 * 8 * 2];
            int[] gpuOutput = new int[8 * 8 * 2];

            ownerClass.getMethod("cpuKernel", byte[].class, layout.getClass(), int[].class)
                    .invoke(null, blob, layout, cpuOutput);

            GpuGeneratedLauncherInvoker.invokeWithConfig(
                    ownerClass,
                    "kernel",
                    net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.threeDimensional(8L, 8L, 2L, 8L, 8L, 1L),
                    blob,
                    layout,
                    gpuOutput
            );

            assertArrayEquals(cpuOutput, gpuOutput);
        }
    }

    @Test
    void comparesImageSamplerWorkloadAgainstCpuReferenceOnAvailableOpenClDevice() throws Exception {
        assumeOpenClAvailable();

        int[] expectedSums = new int[]{10, 26};
        float[] expectedWritten = new float[]{
                1.0f, 0.5f, 0.25f, 1.0f,
                1.0f, 0.5f, 0.25f, 1.0f
        };

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_entry",
                "inline://integration/image-kernel.cl",
                """
                        __kernel void gpu_image_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            int4 pixel = read_imagei(inputImage, sampler, coords);
                            output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                            write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                        }""",
                IMAGE_KERNEL_IRGPU_RESOURCE,
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping image workload integration test");

        int[] gpuOutput = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaIntImage(
                     2,
                     1,
                     new int[]{1, 2, 3, 4, 5, 6, 7, 8}
             );
             Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImage(2, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, gpuOutput}));
            float[] gpuWritten = backend.readRgbaFloatImage(outputImage);

            assertArrayEquals(expectedSums, gpuOutput);
            assertArrayEquals(expectedWritten, gpuWritten);
        }
    }

    @Test
    void seriousWorkloadBucketRemainsEquivalentOnAvailableOpenClDevice() throws Exception {
        assumeWorkloadValidationEnabled();
        assumeOpenClAvailable();

        String perlinStatus = "not run";
        String packedBlobStatus = "not run";
        String packedNumericStatus = "not run";
        String packedGrid3dStatus = "not run";
        String imageStatus = "not run";

        try {
            runPerlinWorkloadComparison();
            perlinStatus = "passed";
        } catch (org.opentest4j.TestAbortedException aborted) {
            perlinStatus = "skipped";
        }

        try {
            runPackedBlobWorkloadComparison();
            packedBlobStatus = "passed";
        } catch (org.opentest4j.TestAbortedException aborted) {
            packedBlobStatus = "skipped";
        }

        try {
            runPackedNumericWorkloadComparison();
            packedNumericStatus = "passed";
        } catch (org.opentest4j.TestAbortedException aborted) {
            packedNumericStatus = "skipped";
        }

        try {
            runPackedGrid3dWorkloadComparison();
            packedGrid3dStatus = "passed";
        } catch (org.opentest4j.TestAbortedException aborted) {
            packedGrid3dStatus = "skipped";
        }

        try {
            runImageWorkloadComparison();
            imageStatus = "passed";
        } catch (org.opentest4j.TestAbortedException aborted) {
            imageStatus = "skipped";
        }

        String overallStatus = ("passed".equals(perlinStatus) || "skipped".equals(perlinStatus))
                && ("passed".equals(packedBlobStatus) || "skipped".equals(packedBlobStatus))
                && ("passed".equals(packedNumericStatus) || "skipped".equals(packedNumericStatus))
                && ("passed".equals(packedGrid3dStatus) || "skipped".equals(packedGrid3dStatus))
                && ("passed".equals(imageStatus) || "skipped".equals(imageStatus))
                ? "passed"
                : "failed";
        writeWorkloadSummary(new OpenClWorkloadValidationSummary(
                Instant.now(),
                overallStatus,
                perlinStatus,
                packedBlobStatus,
                packedNumericStatus,
                packedGrid3dStatus,
                imageStatus
        ));
        assertTrue(!"failed".equals(overallStatus));
    }

    @Test
    void runsSimpleKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_entry",
                "inline://integration/kernel.cl",
                """
                        __kernel void gpu_entry(__global const float* input, float scale, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + scale;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, 2.5f, output}));
        }

        assertArrayEquals(new float[]{3.5f, 4.5f, 5.5f, 6.5f}, output);
    }

    @Test
    void runsSimpleKernelFromOptInIrGpuSourceOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        runSimpleIrGpuSourceKernelReviewCase();
    }

    @Test
    void irGpuSourceReviewLaneRunsOptInSourceOnAvailableOpenClDevice() {
        assumeIrGpuSourceReviewEnabled();
        assumeOpenClAvailable();

        String status = "not run";
        try {
            runSimpleIrGpuSourceKernelReviewCase();
            status = "passed";
        } catch (org.opentest4j.TestAbortedException aborted) {
            status = "skipped";
            throw aborted;
        } finally {
            writeIrGpuSourceReviewSummary(status);
        }
    }

    @Test
    void productionSourceSwitchingLaneRunsPackagedIrGpuSourceOnAvailableOpenClDevice() throws Exception {
        assumeProductionSourceSwitchingValidationEnabled();
        assumeOpenClAvailable();

        String status = "not run";
        try {
            runSimpleIrGpuSourceKernelProductionSwitchingCase();
            runImageWorkloadProductionSwitchingCase();
            runDualBufferIntWorkloadProductionSwitchingCase();
            runGeneratedPerlinWorkloadProductionSwitchingCase();
            runGeneratedPackedBlobWorkloadProductionSwitchingCase();
            runGeneratedPackedNumericWorkloadProductionSwitchingCase();
            runGeneratedSynthetic3DPackedGridWorkloadProductionSwitchingCase();
            status = "passed";
        } catch (org.opentest4j.TestAbortedException aborted) {
            status = "skipped";
            throw aborted;
        } finally {
            writeProductionSourceSwitchingValidationSummary(status);
        }
    }

    private void runSimpleIrGpuSourceKernelReviewCase() {
        GpuKernelDescriptor descriptor = simpleIrGpuSourceKernelDescriptor();
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{input, 2.5f, output},
                    GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of())
            ));
        }

        assertArrayEquals(new float[]{3.5f, 4.5f, 5.5f, 6.5f}, output);
    }

    private void runSimpleIrGpuSourceKernelProductionSwitchingCase() {
        GpuKernelDescriptor descriptor = simpleIrGpuSourceKernelDescriptor();
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{input, 2.5f, output},
                    productionSourceSwitchingOptions(backend, descriptor.kernelResource())
            ));
        }

        assertArrayEquals(new float[]{3.5f, 4.5f, 5.5f, 6.5f}, output);
    }

    private void runImageWorkloadProductionSwitchingCase() {
        int[] expectedSums = new int[]{10, 26};
        float[] expectedWritten = new float[]{
                1.0f, 0.5f, 0.25f, 1.0f,
                1.0f, 0.5f, 0.25f, 1.0f
        };
        GpuKernelDescriptor descriptor = imageWorkloadDescriptor();
        assumeKernelCompiles(descriptor, "Skipping controlled production source-switching image workload smoke test");

        int[] gpuOutput = new int[]{0, 0};
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaIntImage(
                     2,
                     1,
                     new int[]{1, 2, 3, 4, 5, 6, 7, 8}
             );
             Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImage(2, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{inputImage, outputImage, sampler, gpuOutput},
                    productionSourceSwitchingOptions(backend, descriptor.kernelResource())
            ));
            float[] gpuWritten = backend.readRgbaFloatImage(outputImage);

            assertArrayEquals(expectedSums, gpuOutput);
            assertArrayEquals(expectedWritten, gpuWritten);
        }
    }

    private void runDualBufferIntWorkloadProductionSwitchingCase() {
        GpuKernelDescriptor descriptor = dualBufferIntWorkloadDescriptor();
        int[] left = new int[]{1, 10, 100, 1000};
        int[] right = new int[]{2, 20, 200, 2000};
        int[] output = new int[]{0, 0, 0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{left, right, output},
                    productionSourceSwitchingOptions(backend, descriptor.kernelResource())
            ));
        }

        assertArrayEquals(new int[]{3, 30, 300, 3000}, output);
    }

    private void runGeneratedPerlinWorkloadProductionSwitchingCase() throws Exception {
        assumeOpenClFp64Available("Skipping controlled production source-switching generated Perlin workload smoke test");

        CompiledGpuSource compiled = compileGpuSource("sample.PerlinWorkload", perlinWorkloadSource());

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.PerlinWorkload", true, classLoader);
            Class<?> fixtureClass = Class.forName("sample.PerlinWorkload$Fixture", true, classLoader);

            Object fixture = ownerClass.getMethod("createDefaultFixture", long.class).invoke(null, 255L);
            Object noise = fixtureClass.getField("info").get(fixture);
            byte[] permutation0 = (byte[]) fixtureClass.getField("permutation0").get(fixture);
            byte[] permutation1 = (byte[]) fixtureClass.getField("permutation1").get(fixture);
            byte[] permutation2 = (byte[]) fixtureClass.getField("permutation2").get(fixture);

            double[] cpuOutput = new double[256];
            double[] gpuOutput = new double[256];

            ownerClass.getMethod("cpuKernel", noise.getClass(), byte[].class, byte[].class, byte[].class, double[].class)
                    .invoke(null, noise, permutation0, permutation1, permutation2, cpuOutput);

            GpuGeneratedLauncherInvoker.invokeWithGlobalWorkSizeAndCompileOptions(
                    ownerClass,
                    "kernel",
                    256L,
                    productionSourceSwitchingOptions("javatogpu/sample/PerlinWorkload/kernel.cl"),
                    noise,
                    permutation0,
                    permutation1,
                    permutation2,
                    gpuOutput
            );

            for (int i = 0; i < cpuOutput.length; i++) {
                org.junit.jupiter.api.Assertions.assertEquals(cpuOutput[i], gpuOutput[i], 1.0e-9, "Mismatch at index " + i);
            }
        }
    }

    private void runGeneratedPackedNumericWorkloadProductionSwitchingCase() throws Exception {
        assumeOpenClFp64Available("Skipping controlled production source-switching generated packed numeric workload smoke test");

        CompiledGpuSource compiled = compileGpuSource("sample.PackedNumericWorkload", packedNumericWorkloadSource());

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.PackedNumericWorkload", true, classLoader);
            Class<?> fixtureClass = Class.forName("sample.PackedNumericWorkload$Fixture", true, classLoader);

            Object fixture = ownerClass.getMethod("createFixture").invoke(null);
            byte[] blob = (byte[]) fixtureClass.getField("blob").get(fixture);
            Object view = fixtureClass.getField("view").get(fixture);

            double[] cpuOutput = new double[6];
            double[] gpuOutput = new double[6];

            ownerClass.getMethod("cpuKernel", byte[].class, view.getClass(), double[].class)
                    .invoke(null, blob, view, cpuOutput);

            GpuGeneratedLauncherInvoker.invokeWithGlobalWorkSizeAndCompileOptions(
                    ownerClass,
                    "kernel",
                    6L,
                    productionSourceSwitchingOptions("javatogpu/sample/PackedNumericWorkload/kernel.cl"),
                    blob,
                    view,
                    gpuOutput
            );

            for (int i = 0; i < cpuOutput.length; i++) {
                org.junit.jupiter.api.Assertions.assertEquals(cpuOutput[i], gpuOutput[i], 1.0e-9, "Mismatch at index " + i);
            }
        }
    }

    private void runGeneratedPackedBlobWorkloadProductionSwitchingCase() throws Exception {
        CompiledGpuSource compiled = compileGpuSource("sample.PackedBlobWorkload", packedBlobWorkloadSource());

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.PackedBlobWorkload", true, classLoader);
            Class<?> fixtureClass = Class.forName("sample.PackedBlobWorkload$Fixture", true, classLoader);

            Object fixture = ownerClass.getMethod("createFixture").invoke(null);
            byte[] blob = (byte[]) fixtureClass.getField("blob").get(fixture);
            Object view = fixtureClass.getField("view").get(fixture);

            int[] cpuOutput = new int[8];
            int[] gpuOutput = new int[8];

            ownerClass.getMethod("cpuKernel", byte[].class, view.getClass(), int[].class)
                    .invoke(null, blob, view, cpuOutput);

            GpuGeneratedLauncherInvoker.invokeWithGlobalWorkSizeAndCompileOptions(
                    ownerClass,
                    "kernel",
                    8L,
                    productionSourceSwitchingOptions("javatogpu/sample/PackedBlobWorkload/kernel.cl"),
                    blob,
                    view,
                    gpuOutput
            );

            assertArrayEquals(cpuOutput, gpuOutput);
        }
    }

    private void runGeneratedSynthetic3DPackedGridWorkloadProductionSwitchingCase() throws Exception {
        CompiledGpuSource compiled = compileGpuSource(
                "sample.Synthetic3DPackedGridWorkload",
                synthetic3DPackedGridWorkloadSource()
        );

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{compiled.classOutputDir().toUri().toURL()}, getClass().getClassLoader());
             GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            Class<?> ownerClass = Class.forName("sample.Synthetic3DPackedGridWorkload", true, classLoader);
            Class<?> fixtureClass = Class.forName("sample.Synthetic3DPackedGridWorkload$Fixture", true, classLoader);

            Object fixture = ownerClass.getMethod("createFixture").invoke(null);
            byte[] blob = (byte[]) fixtureClass.getField("blob").get(fixture);
            Object layout = fixtureClass.getField("layout").get(fixture);

            int[] cpuOutput = new int[8 * 8 * 2];
            int[] gpuOutput = new int[8 * 8 * 2];

            ownerClass.getMethod("cpuKernel", byte[].class, layout.getClass(), int[].class)
                    .invoke(null, blob, layout, cpuOutput);

            GpuGeneratedLauncherInvoker.invokeWithConfigAndCompileOptions(
                    ownerClass,
                    "kernel",
                    net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.threeDimensional(8L, 8L, 2L, 8L, 8L, 1L),
                    productionSourceSwitchingOptions("javatogpu/sample/Synthetic3DPackedGridWorkload/kernel.cl"),
                    blob,
                    layout,
                    gpuOutput
            );

            assertArrayEquals(cpuOutput, gpuOutput);
        }
    }

    private GpuKernelDescriptor simpleIrGpuSourceKernelDescriptor() {
        return new GpuKernelDescriptor(
                "gpu_irgpu_entry",
                "inline://integration/simple-irgpu-source-kernel.cl",
                """
                        __kernel void gpu_irgpu_entry(__global const float* input, float scale, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + scale;
                        }""",
                SIMPLE_IRGPU_SOURCE_RESOURCE,
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    @Test
    void runsLongKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_long_entry",
                "inline://integration/long-kernel.cl",
                """
                        __kernel void gpu_long_entry(__global const long* input, long offset, __global long* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + offset;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "long[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("offset", "long", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "long[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        long[] input = new long[]{10L, 20L, 30L, 40L};
        long[] output = new long[]{0L, 0L, 0L, 0L};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, 5L, output}));
        }

        assertArrayEquals(new long[]{15L, 25L, 35L, 45L}, output);
    }

    @Test
    void runsDoubleKernelWhenDeviceSupportsFp64() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_double_entry",
                "inline://integration/double-kernel.cl",
                """
                        #pragma OPENCL EXTENSION cl_khr_fp64 : enable
                        __kernel void gpu_double_entry(__global const double* input, double scale, __global double* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] * scale;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "double[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "double", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "double[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping fp64 integration smoke test");

        double[] input = new double[]{1.5d, 2.5d, 3.5d};
        double[] output = new double[]{0.0d, 0.0d, 0.0d};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, 2.0d, output}));
        }

        assertArrayEquals(new double[]{3.0d, 5.0d, 7.0d}, output);
    }

    @Test
    void runsBitwiseIntKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_bitwise_entry",
                "inline://integration/bitwise-kernel.cl",
                """
                        __kernel void gpu_bitwise_entry(__global const int* input, __global int* output) {
                            int id = get_global_id(0);
                            output[id] = ((~input[id]) << 1) ^ ((input[id] >> 1) | (input[id] & 7));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "int[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        int[] input = new int[]{1, 2, 7, 16};
        int[] output = new int[]{0, 0, 0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, output}));
        }

        assertArrayEquals(
                new int[]{
                        ((~1) << 1) ^ ((1 >> 1) | (1 & 7)),
                        ((~2) << 1) ^ ((2 >> 1) | (2 & 7)),
                        ((~7) << 1) ^ ((7 >> 1) | (7 & 7)),
                        ((~16) << 1) ^ ((16 >> 1) | (16 & 7))
                },
                output
        );
    }

    @Test
    void runsFloat2ParameterKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_vector_entry",
                "inline://integration/vector-kernel.cl",
                """
                        __kernel void gpu_vector_entry(float2 bias, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = bias.x + bias.y + (float) id;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("bias", "Float2", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping vector parameter integration smoke test");

        float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new Float2(1.5f, 2.0f), output}));
        }

        assertArrayEquals(new float[]{3.5f, 4.5f, 5.5f, 6.5f}, output);
    }

    @Test
    void runsStructParameterKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_struct_entry",
                "inline://integration/struct-kernel.cl",
                """
                        typedef struct __attribute__((packed)) {
                            float x;
                            float y __attribute__((aligned(8)));
                            int count;
                        } Sample;

                        __kernel void gpu_struct_entry(Sample sample, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = sample.x + sample.y + sample.count + (float) id;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("sample", "sample.Sample", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping struct parameter integration smoke test");

        float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new Sample(1.25f, 2.5f, 3), output}));
        }

        assertArrayEquals(new float[]{6.75f, 7.75f, 8.75f, 9.75f}, output);
    }

    @Test
    void runsStructArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_struct_array_entry",
                "inline://integration/struct-array-kernel.cl",
                """
                        typedef struct{
                            float x;
                            float y;
                        } StructArraySample;

                        __kernel void gpu_struct_array_entry(__global StructArraySample* input, __global StructArraySample* output) {
                            int id = get_global_id(0);
                            output[id].x = input[id].x + 1.0f;
                            output[id].y = input[id].y + 2.0f;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "sample.StructArraySample[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "sample.StructArraySample[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping struct array integration smoke test");

        StructArraySample[] input = new StructArraySample[]{
                new StructArraySample(1.0f, 2.0f),
                new StructArraySample(3.0f, 4.0f)
        };
        StructArraySample[] output = new StructArraySample[]{new StructArraySample(), new StructArraySample()};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, output}));
        }

        assertArrayEquals(new float[]{2.0f, 4.0f}, new float[]{output[0].x, output[1].x});
        assertArrayEquals(new float[]{4.0f, 6.0f}, new float[]{output[0].y, output[1].y});
    }

    @Test
    void runsNestedAlignedStructArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_nested_struct_array_entry",
                "inline://integration/nested-struct-array-kernel.cl",
                """
                        typedef struct{
                            float x;
                            float y;
                        } InnerPoint;

                        typedef struct __attribute__((aligned(16))) {
                            InnerPoint point;
                            float bias __attribute__((aligned(8)));
                            int count;
                        } ComplexStructArraySample;

                        __kernel void gpu_nested_struct_array_entry(__global ComplexStructArraySample* input, __global ComplexStructArraySample* output) {
                            int id = get_global_id(0);
                            output[id].point.x = input[id].point.x + 1.0f;
                            output[id].point.y = input[id].point.y + 2.0f;
                            output[id].bias = input[id].bias + 3.0f;
                            output[id].count = input[id].count + 4;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "sample.ComplexStructArraySample[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "sample.ComplexStructArraySample[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping nested aligned struct array integration smoke test");

        ComplexStructArraySample[] input = new ComplexStructArraySample[]{
                new ComplexStructArraySample(new InnerPoint(1.0f, 2.0f), 3.0f, 4),
                new ComplexStructArraySample(new InnerPoint(5.0f, 6.0f), 7.0f, 8)
        };
        ComplexStructArraySample[] output = new ComplexStructArraySample[]{
                new ComplexStructArraySample(new InnerPoint(), 0.0f, 0),
                new ComplexStructArraySample(new InnerPoint(), 0.0f, 0)
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, output}));
        }

        assertArrayEquals(new float[]{2.0f, 6.0f}, new float[]{output[0].point.x, output[1].point.x});
        assertArrayEquals(new float[]{4.0f, 8.0f}, new float[]{output[0].point.y, output[1].point.y});
        assertArrayEquals(new float[]{6.0f, 10.0f}, new float[]{output[0].bias, output[1].bias});
        assertArrayEquals(new int[]{8, 12}, new int[]{output[0].count, output[1].count});
    }

    @Test
    void runsVectorArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_vector_array_entry",
                "inline://integration/vector-array-kernel.cl",
                """
                        __kernel void gpu_vector_array_entry(__global float2* input, __global float2* output) {
                            int id = get_global_id(0);
                            output[id].x = input[id].x + 1.0f;
                            output[id].y = input[id].y + 2.0f;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "net.sixik.ga_utils.javatogpu.api.Float2[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "net.sixik.ga_utils.javatogpu.api.Float2[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping vector array integration smoke test");

        Float2[] input = new Float2[]{
                new Float2(1.0f, 2.0f),
                new Float2(3.0f, 4.0f)
        };
        Float2[] output = new Float2[]{new Float2(), new Float2()};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, output}));
        }

        assertArrayEquals(new float[]{2.0f, 4.0f}, new float[]{output[0].x, output[1].x});
        assertArrayEquals(new float[]{4.0f, 6.0f}, new float[]{output[0].y, output[1].y});
    }

    @Test
    void repeatedVectorArrayInvocationsRemainStableOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_vector_array_repeat_entry",
                "inline://integration/vector-array-repeat-kernel.cl",
                """
                        __kernel void gpu_vector_array_repeat_entry(__global float2* input, __global float2* output) {
                            int id = get_global_id(0);
                            output[id].x = input[id].x + 1.0f;
                            output[id].y = input[id].y + 2.0f;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "net.sixik.ga_utils.javatogpu.api.Float2[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "net.sixik.ga_utils.javatogpu.api.Float2[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping repeated vector array integration stability test");

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            for (int iteration = 0; iteration < 40; iteration++) {
                Float2[] input = new Float2[]{
                        new Float2(1.0f + iteration, 2.0f + iteration),
                        new Float2(3.0f + iteration, 4.0f + iteration)
                };
                Float2[] output = new Float2[]{new Float2(), new Float2()};

                backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, output}));

                assertArrayEquals(
                        new float[]{2.0f + iteration, 4.0f + iteration},
                        new float[]{output[0].x, output[1].x}
                );
                assertArrayEquals(
                        new float[]{4.0f + iteration, 6.0f + iteration},
                        new float[]{output[0].y, output[1].y}
                );
            }
        }
    }

    @Test
    void repeatedMixedScalarAndStructArrayInvocationsRemainStableOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor scalarDescriptor = new GpuKernelDescriptor(
                "gpu_scalar_repeat_entry",
                "inline://integration/scalar-repeat-kernel.cl",
                """
                        __kernel void gpu_scalar_repeat_entry(__global const float* input, float scale, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + scale;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuKernelDescriptor structDescriptor = new GpuKernelDescriptor(
                "gpu_struct_repeat_entry",
                "inline://integration/struct-repeat-kernel.cl",
                """
                        typedef struct{
                            float x;
                            float y;
                        } StructArraySample;

                        __kernel void gpu_struct_repeat_entry(__global StructArraySample* input, __global StructArraySample* output) {
                            int id = get_global_id(0);
                            output[id].x = input[id].x + 1.0f;
                            output[id].y = input[id].y + 2.0f;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "sample.StructArraySample[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "sample.StructArraySample[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(scalarDescriptor, "Skipping repeated mixed scalar/struct integration stability test");
        assumeKernelCompiles(structDescriptor, "Skipping repeated mixed scalar/struct integration stability test");

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            for (int iteration = 0; iteration < 40; iteration++) {
                float[] input = new float[]{1.0f + iteration, 2.0f + iteration, 3.0f + iteration, 4.0f + iteration};
                float[] output = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

                backend.invoke(new GpuKernelInvocation(scalarDescriptor, new Object[]{input, 2.5f, output}));

                assertArrayEquals(
                        new float[]{3.5f + iteration, 4.5f + iteration, 5.5f + iteration, 6.5f + iteration},
                        output
                );

                StructArraySample[] structInput = new StructArraySample[]{
                        new StructArraySample(1.0f + iteration, 2.0f + iteration),
                        new StructArraySample(3.0f + iteration, 4.0f + iteration)
                };
                StructArraySample[] structOutput = new StructArraySample[]{new StructArraySample(), new StructArraySample()};

                backend.invoke(new GpuKernelInvocation(structDescriptor, new Object[]{structInput, structOutput}));

                assertArrayEquals(
                        new float[]{2.0f + iteration, 4.0f + iteration},
                        new float[]{structOutput[0].x, structOutput[1].x}
                );
                assertArrayEquals(
                        new float[]{4.0f + iteration, 6.0f + iteration},
                        new float[]{structOutput[0].y, structOutput[1].y}
                );
            }
        }
    }

    @Test
    void runsImageAndSamplerKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_entry",
                "inline://integration/image-kernel.cl",
                """
                        __kernel void gpu_image_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            int4 pixel = read_imagei(inputImage, sampler, coords);
                            output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                            write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping image/sampler integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             );
             Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImage(2, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            float[] written = backend.readRgbaFloatImage(outputImage);

            assertArrayEquals(new int[]{10, 26}, output);
            assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void repeatedImageKernelInvocationsRemainStableOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_repeat_entry",
                "inline://integration/image-repeat-kernel.cl",
                """
                        __kernel void gpu_image_repeat_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            int4 pixel = read_imagei(inputImage, sampler, coords);
                            output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                            write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping repeated image integration stability test");

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            for (int iteration = 0; iteration < 30; iteration++) {
                int[] output = new int[]{0, 0};

                try (Image2DReadOnly inputImage = backend.createReadOnlyRgbaIntImage(
                        2,
                        1,
                        new int[]{1 + iteration, 2, 3, 4, 5 + iteration, 6, 7, 8}
                );
                     Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImage(2, 1)) {
                    backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));

                    assertArrayEquals(new int[]{10 + iteration, 26 + iteration}, output);

                    float[] written = backend.readRgbaFloatImage(outputImage);
                    assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[0], written[1], written[2], written[3]});
                    assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[4], written[5], written[6], written[7]});
                }
            }
        }
    }

    @Test
    void longRunningMixedAbiAndImageInvocationsRemainStableOnAvailableOpenClDevice() {
        assumeLongRunningOpenClValidationEnabled();
        assumeOpenClAvailable();

        GpuKernelDescriptor scalarDescriptor = new GpuKernelDescriptor(
                "gpu_scalar_repeat_entry",
                "inline://integration/scalar-repeat-kernel.cl",
                """
                        __kernel void gpu_scalar_repeat_entry(__global const float* input, float scale, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + scale;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuKernelDescriptor structDescriptor = new GpuKernelDescriptor(
                "gpu_struct_repeat_entry",
                "inline://integration/struct-repeat-kernel.cl",
                """
                        typedef struct{
                            float x;
                            float y;
                        } StructArraySample;

                        __kernel void gpu_struct_repeat_entry(__global StructArraySample* input, __global StructArraySample* output) {
                            int id = get_global_id(0);
                            output[id].x = input[id].x + 1.0f;
                            output[id].y = input[id].y + 2.0f;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "sample.StructArraySample[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "sample.StructArraySample[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuKernelDescriptor vectorDescriptor = new GpuKernelDescriptor(
                "gpu_vector_array_repeat_entry",
                "inline://integration/vector-array-repeat-kernel.cl",
                """
                        __kernel void gpu_vector_array_repeat_entry(__global float2* input, __global float2* output) {
                            int id = get_global_id(0);
                            output[id].x = input[id].x + 1.0f;
                            output[id].y = input[id].y + 2.0f;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "net.sixik.ga_utils.javatogpu.api.Float2[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "net.sixik.ga_utils.javatogpu.api.Float2[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuKernelDescriptor imageDescriptor = new GpuKernelDescriptor(
                "gpu_image_repeat_entry",
                "inline://integration/image-repeat-kernel.cl",
                """
                        __kernel void gpu_image_repeat_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            int4 pixel = read_imagei(inputImage, sampler, coords);
                            output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                            write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        assumeKernelCompiles(scalarDescriptor, "Skipping long-running mixed OpenCL stability test");
        assumeKernelCompiles(structDescriptor, "Skipping long-running mixed OpenCL stability test");
        assumeKernelCompiles(vectorDescriptor, "Skipping long-running mixed OpenCL stability test");
        assumeKernelCompiles(imageDescriptor, "Skipping long-running mixed OpenCL stability test");

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            final int iterations = 150;
            for (int iteration = 0; iteration < iterations; iteration++) {
                float[] scalarInput = new float[]{1.0f + iteration, 2.0f + iteration, 3.0f + iteration, 4.0f + iteration};
                float[] scalarOutput = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
                backend.invoke(new GpuKernelInvocation(scalarDescriptor, new Object[]{scalarInput, 2.5f, scalarOutput}));
                assertArrayEquals(
                        new float[]{3.5f + iteration, 4.5f + iteration, 5.5f + iteration, 6.5f + iteration},
                        scalarOutput
                );

                StructArraySample[] structInput = new StructArraySample[]{
                        new StructArraySample(1.0f + iteration, 2.0f + iteration),
                        new StructArraySample(3.0f + iteration, 4.0f + iteration)
                };
                StructArraySample[] structOutput = new StructArraySample[]{new StructArraySample(), new StructArraySample()};
                backend.invoke(new GpuKernelInvocation(structDescriptor, new Object[]{structInput, structOutput}));
                assertArrayEquals(
                        new float[]{2.0f + iteration, 4.0f + iteration},
                        new float[]{structOutput[0].x, structOutput[1].x}
                );
                assertArrayEquals(
                        new float[]{4.0f + iteration, 6.0f + iteration},
                        new float[]{structOutput[0].y, structOutput[1].y}
                );

                Float2[] vectorInput = new Float2[]{
                        new Float2(1.0f + iteration, 2.0f + iteration),
                        new Float2(3.0f + iteration, 4.0f + iteration)
                };
                Float2[] vectorOutput = new Float2[]{new Float2(), new Float2()};
                backend.invoke(new GpuKernelInvocation(vectorDescriptor, new Object[]{vectorInput, vectorOutput}));
                assertArrayEquals(
                        new float[]{2.0f + iteration, 4.0f + iteration},
                        new float[]{vectorOutput[0].x, vectorOutput[1].x}
                );
                assertArrayEquals(
                        new float[]{4.0f + iteration, 6.0f + iteration},
                        new float[]{vectorOutput[0].y, vectorOutput[1].y}
                );

                int[] imageOutput = new int[]{0, 0};
                try (Image2DReadOnly inputImage = backend.createReadOnlyRgbaIntImage(
                        2,
                        1,
                        new int[]{1 + iteration, 2, 3, 4, 5 + iteration, 6, 7, 8}
                );
                     Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImage(2, 1)) {
                    backend.invoke(new GpuKernelInvocation(imageDescriptor, new Object[]{inputImage, outputImage, sampler, imageOutput}));
                    assertArrayEquals(new int[]{10 + iteration, 26 + iteration}, imageOutput);

                    float[] written = backend.readRgbaFloatImage(outputImage);
                    assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[0], written[1], written[2], written[3]});
                    assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[4], written[5], written[6], written[7]});
                }
            }

            OpenClRuntimeStatistics statistics = backend.statistics();
            assertTrue(statistics.invocationCount() >= 600L);
            assertTrue(statistics.compileCount() >= 4L);
            assertTrue(statistics.compileCacheHitCount() > statistics.compileCount());
            writeLongRunningSummary(new OpenClLongRunningValidationSummary(
                    Instant.now(),
                    "passed",
                    iterations,
                    statistics
            ));
        }
    }

    @Test
    void roundTripsRgba8ImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        byte[] source = new byte[]{
                0, 127, (byte) 255, 64,
                5, 10, 15, 20
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgba8Image(2, 1, source)) {
            assertArrayEquals(source, backend.readRgba8Image(inputImage));
        }
    }

    @Test
    void roundTripsRFloatImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{1.25f, 2.5f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRFloatImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRFloatImage(inputImage));
        }
    }

    @Test
    void roundTripsRgFloatImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{1.0f, 2.0f, 3.0f, 4.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgFloatImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRgFloatImage(inputImage));
        }
    }

    @Test
    void roundTripsDepthImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{0.125f, 0.875f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyDepthImage(2, 1, source)) {
            assertArrayEquals(source, backend.readDepthImage(inputImage));
        }
    }

    @Test
    void roundTripsMipmappedRgbaFloatImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 0.0f, 1.0f, 1.0f,
                0.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 0.0f, 1.0f,
                0.5f, 0.5f, 0.5f, 1.0f,
                0.25f, 0.25f, 0.25f, 1.0f
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaFloatImageMipmapped(4, 2, 2, source)) {
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 0, 32), backend.readRgbaFloatImageMipmapped(inputImage, 0));
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 32, 40), backend.readRgbaFloatImageMipmapped(inputImage, 1));
        }
    }

    @Test
    void roundTripsMipmappedRgba8ImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        byte[] source = new byte[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16,
                17, 18, 19, 20,
                21, 22, 23, 24,
                25, 26, 27, 28,
                29, 30, 31, 32,
                33, 34, 35, 36,
                37, 38, 39, 40
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgba8ImageMipmapped(4, 2, 2, source)) {
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 0, 32), backend.readRgba8ImageMipmapped(inputImage, 0));
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 32, 40), backend.readRgba8ImageMipmapped(inputImage, 1));
        }
    }

    @Test
    void roundTripsMipmappedRgbaIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{
                -1, -2, -3, -4,
                -5, -6, -7, -8,
                -9, -10, -11, -12,
                -13, -14, -15, -16,
                17, 18, 19, 20,
                21, 22, 23, 24,
                25, 26, 27, 28,
                29, 30, 31, 32,
                -33, -34, -35, -36,
                -37, -38, -39, -40
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaIntImageMipmapped(4, 2, 2, source)) {
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 0, 32), backend.readRgbaIntImageMipmapped(inputImage, 0));
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 32, 40), backend.readRgbaIntImageMipmapped(inputImage, 1));
        }
    }

    @Test
    void roundTripsMipmappedRgbaUIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16,
                17, 18, 19, 20,
                21, 22, 23, 24,
                25, 26, 27, 28,
                29, 30, 31, 32,
                33, 34, 35, 36,
                37, 38, 39, 40
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaUIntImageMipmapped(4, 2, 2, source)) {
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 0, 32), backend.readRgbaUIntImageMipmapped(inputImage, 0));
            assertArrayEquals(java.util.Arrays.copyOfRange(source, 32, 40), backend.readRgbaUIntImageMipmapped(inputImage, 1));
        }
    }

    @Test
    void roundTripsRIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{11, 22};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRIntImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRIntImage(inputImage));
        }
    }

    @Test
    void roundTripsRgIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{11, 22, 33, 44};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgIntImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRgIntImage(inputImage));
        }
    }

    @Test
    void roundTripsRUIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{101, 202};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRUIntImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRUIntImage(inputImage));
        }
    }

    @Test
    void roundTripsRgUIntImagesOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{101, 202, 303, 404};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgUIntImage(2, 1, source)) {
            assertArrayEquals(source, backend.readRgUIntImage(inputImage));
        }
    }

    @Test
    void runsUnsignedImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_uint_entry",
                "inline://integration/image-uint-kernel.cl",
                """
                        __kernel void gpu_image_uint_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            uint4 pixel = read_imageui(inputImage, sampler, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             );
             Image2DWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage(2, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            int[] written = backend.readRgbaUIntImage(outputImage);

            assertArrayEquals(new int[]{10, 26}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsDepthImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_depth_entry",
                "inline://integration/image-depth-kernel.cl",
                """
                        __kernel void gpu_image_depth_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global float* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            float4 pixel = read_imagef(inputImage, sampler, coords);
                            output[id] = pixel.x + (float) get_image_width(inputImage);
                            write_imagef(outputImage, coords, (float4)(pixel.x * 0.5f, 0.0f, 0.0f, 1.0f));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping depth image integration smoke test");

        float[] output = new float[]{0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyDepthImage(2, 1, new float[]{0.25f, 0.75f});
             Image2DWriteOnly outputImage = backend.createWriteOnlyDepthImage(2, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            float[] written = backend.readDepthImage(outputImage);

            assertArrayEquals(new float[]{2.25f, 2.75f}, output);
            assertArrayEquals(new float[]{0.125f, 0.375f}, written);
        }
    }

    @Test
    void roundTripsRgbaFloatImage3dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage3D(2, 1, 2, source)) {
            assertArrayEquals(source, backend.readRgbaFloatImage3D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaIntImage3dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaIntImage3D(2, 1, 2, source)) {
            assertArrayEquals(source, backend.readRgbaIntImage3D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaUIntImage3dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage3D(2, 1, 2, source)) {
            assertArrayEquals(source, backend.readRgbaUIntImage3D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaFloatImage1dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage1D(2, source)) {
            assertArrayEquals(source, backend.readRgbaFloatImage1D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaUIntImage1dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{1, 2, 3, 4, 5, 6, 7, 8};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1D(2, source)) {
            assertArrayEquals(source, backend.readRgbaUIntImage1D(inputImage));
        }
    }

    @Test
    void runsUnsignedImage1dKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image1d_uint_entry",
                "inline://integration/image1d-uint-kernel.cl",
                """
                        __kernel void gpu_image1d_uint_entry(read_only image1d_t inputImage, write_only image1d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            uint4 pixel = read_imageui(inputImage, sampler, id);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + get_image_width(inputImage));
                            write_imageui(outputImage, id, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image1DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image1DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned 1D image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1D(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
             Image1DWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage1D(2);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            int[] written = backend.readRgbaUIntImage1D(outputImage);

            assertArrayEquals(new int[]{12, 28}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void roundTripsRgbaIntImage1dOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{1, 2, 3, 4, 5, 6, 7, 8};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DReadOnly inputImage = backend.createReadOnlyRgbaIntImage1D(2, source)) {
            assertArrayEquals(source, backend.readRgbaIntImage1D(inputImage));
        }
    }

    @Test
    void roundTripsRgbaUIntImage1dArrayOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DArrayReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1DArray(2, 2, source)) {
            assertArrayEquals(source, backend.readRgbaUIntImage1DArray(inputImage));
        }
    }

    @Test
    void runsUnsignedImage1dArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image1d_array_uint_entry",
                "inline://integration/image1d-array-uint-kernel.cl",
                """
                        __kernel void gpu_image1d_array_uint_entry(read_only image1d_array_t inputImage, write_only image1d_array_t outputImage, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            uint4 pixel = read_imageui(inputImage, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + get_image_array_size(inputImage));
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image1DArrayReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image1DArrayWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned 1D array image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DArrayReadOnly inputImage = backend.createReadOnlyRgbaUIntImage1DArray(2, 2, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
             Image1DArrayWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage1DArray(2, 2)) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, output}));
            int[] written = backend.readRgbaUIntImage1DArray(outputImage);

            assertArrayEquals(new int[]{12, 28}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void roundTripsRgbaFloatImage2dArrayOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        float[] source = new float[]{
                1.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
        };

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DArrayReadOnly inputImage = backend.createReadOnlyRgbaFloatImage2DArray(2, 1, 2, source)) {
            assertArrayEquals(source, backend.readRgbaFloatImage2DArray(inputImage));
        }
    }

    @Test
    void runsUnsignedImage2dArrayKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image2d_array_uint_entry",
                "inline://integration/image2d-array-uint-kernel.cl",
                """
                        __kernel void gpu_image2d_array_uint_entry(read_only image2d_array_t inputImage, write_only image2d_array_t outputImage, __global int* output) {
                            int id = get_global_id(0);
                            int4 coords = (int4)(id, 0, 0, 0);
                            uint4 pixel = read_imageui(inputImage, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + get_image_array_size(inputImage));
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DArrayReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DArrayWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned 2D array image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DArrayReadOnly inputImage = backend.createReadOnlyRgbaUIntImage2DArray(2, 1, 2, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
             Image2DArrayWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage2DArray(2, 1, 2)) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, output}));
            int[] written = backend.readRgbaUIntImage2DArray(outputImage);

            assertArrayEquals(new int[]{12, 28}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void roundTripsRgbaIntImage1dBufferOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        int[] source = new int[]{1, 2, 3, 4, 5, 6, 7, 8};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DBufferReadOnly inputImage = backend.createReadOnlyRgbaIntImage1DBuffer(2, source)) {
            assertArrayEquals(source, backend.readRgbaIntImage1DBuffer(inputImage));
        }
    }

    @Test
    void runsIntImage1dBufferKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image1d_buffer_int_entry",
                "inline://integration/image1d-buffer-int-kernel.cl",
                """
                        __kernel void gpu_image1d_buffer_int_entry(read_only image1d_buffer_t inputImage, write_only image1d_buffer_t outputImage, __global int* output) {
                            int id = get_global_id(0);
                            int4 pixel = read_imagei(inputImage, id);
                            output[id] = pixel.x + get_image_width(inputImage);
                            write_imagei(outputImage, id, (int4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image1DBufferReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image1DBufferWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping 1D buffer image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image1DBufferReadOnly inputImage = backend.createReadOnlyRgbaIntImage1DBuffer(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
             Image1DBufferWriteOnly outputImage = backend.createWriteOnlyRgbaIntImage1DBuffer(2)) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, output}));
            int[] written = backend.readRgbaIntImage1DBuffer(outputImage);

            assertArrayEquals(new int[]{3, 7}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsUnsignedImage3dKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image3d_uint_entry",
                "inline://integration/image3d-uint-kernel.cl",
                """
                        __kernel void gpu_image3d_uint_entry(read_only image3d_t inputImage, write_only image3d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int4 coords = (int4)(id, 0, 0, 0);
                            uint4 pixel = read_imageui(inputImage, sampler, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image3DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned 3D image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             net.sixik.ga_utils.javatogpu.api.Image3DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage3D(
                     2,
                     1,
                     2,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8,
                             9, 10, 11, 12,
                             13, 14, 15, 16
                     }
             );
             net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImage3D(2, 1, 2);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            int[] written = backend.readRgbaUIntImage3D(outputImage);

            assertArrayEquals(new int[]{10, 26}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsSamplerlessImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_nosampler_entry",
                "inline://integration/image-nosampler-kernel.cl",
                """
                        __kernel void gpu_image_nosampler_entry(read_only image2d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            uint4 pixel = read_imageui(inputImage, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w + get_image_width(inputImage));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping samplerless image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{12, 28}, output);
        }
    }

    @Test
    void runsSamplerlessImage3dKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image3d_nosampler_entry",
                "inline://integration/image3d-nosampler-kernel.cl",
                """
                        __kernel void gpu_image3d_nosampler_entry(read_only image3d_t inputImage, __global float* output) {
                            int id = get_global_id(0);
                            int4 coords = (int4)(id, 0, 0, 0);
                            float4 pixel = read_imagef(inputImage, coords);
                            output[id] = pixel.x + pixel.y + pixel.z + pixel.w + get_image_depth(inputImage);
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping samplerless 3D image integration smoke test");

        float[] output = new float[]{0.0f, 0.0f};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage3D(
                     2,
                     1,
                     2,
                     new float[]{
                             1.0f, 0.0f, 0.0f, 1.0f,
                             0.0f, 1.0f, 0.0f, 1.0f,
                             0.0f, 0.0f, 1.0f, 1.0f,
                             1.0f, 1.0f, 1.0f, 1.0f
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new float[]{4.0f, 4.0f}, output);
        }
    }

    @Test
    void runsImageMetadataKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_meta_entry",
                "inline://integration/image-meta-kernel.cl",
                """
                        __kernel void gpu_image_meta_entry(read_only image2d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int channelOrder = get_image_channel_order(inputImage);
                            int channelType = get_image_channel_data_type(inputImage);
                            output[id] = ((channelOrder == %d) && (channelType == %d)) ? 1 : 0;
                        }""".formatted(org.lwjgl.opencl.CL10.CL_RGBA, org.lwjgl.opencl.CL10.CL_UNSIGNED_INT32),
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping image metadata integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{1, 1}, output);
        }
    }

    @Test
    void runsImage3dMetadataKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image3d_meta_entry",
                "inline://integration/image3d-meta-kernel.cl",
                """
                        __kernel void gpu_image3d_meta_entry(read_only image3d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int channelOrder = get_image_channel_order(inputImage);
                            int channelType = get_image_channel_data_type(inputImage);
                            output[id] = ((channelOrder == %d) && (channelType == %d)) ? get_image_depth(inputImage) : 0;
                        }""".formatted(org.lwjgl.opencl.CL10.CL_RGBA, org.lwjgl.opencl.CL10.CL_FLOAT),
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping 3D image metadata integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image3DReadOnly inputImage = backend.createReadOnlyRgbaFloatImage3D(
                     2,
                     1,
                     2,
                     new float[]{
                             1.0f, 0.0f, 0.0f, 1.0f,
                             0.0f, 1.0f, 0.0f, 1.0f,
                             0.0f, 0.0f, 1.0f, 1.0f,
                             1.0f, 1.0f, 1.0f, 1.0f
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{2, 2}, output);
        }
    }

    @Test
    void runsExtendedImageMetadataKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_meta_extended_entry",
                "inline://integration/image-meta-extended-kernel.cl",
                """
                        __kernel void gpu_image_meta_extended_entry(read_only image2d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int mipLevels = get_image_num_mip_levels(inputImage);
                            int sampleCount = get_image_num_samples(inputImage);
                            output[id] = mipLevels + sampleCount + get_image_width(inputImage);
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping extended image metadata integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DReadOnly inputImage = backend.createReadOnlyRgbaUIntImage(
                     2,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{3, 3}, output);
        }
    }

    @Test
    void runsMipmappedImageMetadataKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_meta_mipmapped_entry",
                "inline://integration/image-meta-mipmapped-kernel.cl",
                """
                        __kernel void gpu_image_meta_mipmapped_entry(read_only image2d_t inputImage, __global int* output) {
                            int id = get_global_id(0);
                            int mipLevels = get_image_num_mip_levels(inputImage);
                            int sampleCount = get_image_num_samples(inputImage);
                            output[id] = mipLevels + sampleCount + get_image_width(inputImage) + get_image_height(inputImage);
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DMipmappedReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping mipmapped image metadata integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaUIntImageMipmapped(
                     4,
                     2,
                     2,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8,
                             9, 10, 11, 12,
                             13, 14, 15, 16,
                             17, 18, 19, 20,
                             21, 22, 23, 24,
                             25, 26, 27, 28,
                             29, 30, 31, 32,
                             33, 34, 35, 36,
                             37, 38, 39, 40
                     }
             )) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, output}));

            assertArrayEquals(new int[]{9, 9}, output);
        }
    }

    @Test
    void runsMipmappedFloatImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_mipmapped_float_entry",
                "inline://integration/image-mipmapped-float-kernel.cl",
                """
                        __kernel void gpu_image_mipmapped_float_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            float4 pixel = read_imagef(inputImage, sampler, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                            write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DMipmappedReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DMipmappedWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping mipmapped float image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaFloatImageMipmapped(
                     2,
                     1,
                     1,
                     new float[]{
                             1.0f, 0.5f, 0.25f, 1.0f,
                             0.25f, 0.25f, 0.25f, 1.0f
                     }
             );
             Image2DMipmappedWriteOnly outputImage = backend.createWriteOnlyRgbaFloatImageMipmapped(2, 1, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            float[] written = backend.readRgbaFloatImageMipmapped(outputImage, 0);

            assertArrayEquals(new int[]{2, 1}, output);
            assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new float[]{1.0f, 0.5f, 0.25f, 1.0f}, new float[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsMipmappedUIntImageKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image_mipmapped_uint_entry",
                "inline://integration/image-mipmapped-uint-kernel.cl",
                """
                        __kernel void gpu_image_mipmapped_uint_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            uint4 pixel = read_imageui(inputImage, sampler, coords);
                            output[id] = (int) (pixel.x + pixel.y + pixel.z + pixel.w);
                            write_imageui(outputImage, coords, (uint4)(9, 10, 11, 12));
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DMipmappedReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DMipmappedWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping mipmapped uint image integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
             Image2DMipmappedReadOnly inputImage = backend.createReadOnlyRgbaUIntImageMipmapped(
                     2,
                     1,
                     1,
                     new int[]{
                             1, 2, 3, 4,
                             5, 6, 7, 8
                     }
             );
             Image2DMipmappedWriteOnly outputImage = backend.createWriteOnlyRgbaUIntImageMipmapped(2, 1, 1);
             Sampler sampler = backend.createNearestClampToEdgeSampler()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{inputImage, outputImage, sampler, output}));
            int[] written = backend.readRgbaUIntImageMipmapped(outputImage, 0);

            assertArrayEquals(new int[]{10, 26}, output);
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[0], written[1], written[2], written[3]});
            assertArrayEquals(new int[]{9, 10, 11, 12}, new int[]{written[4], written[5], written[6], written[7]});
        }
    }

    @Test
    void runsUnsignedScalarAliasKernelOnAvailableOpenClDevice() {
        assumeOpenClAvailable();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_uint_entry",
                "inline://integration/uint-kernel.cl",
                """
                        __kernel void gpu_uint_entry(uint bias, __global int* output) {
                            int id = get_global_id(0);
                            uint limited = clamp(max(bias, 4u), 4u, 32u);
                            uint result = min(limited, 17u);
                            output[id] = (int) result;
                        }""",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("bias", "UInt", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        assumeKernelCompiles(descriptor, "Skipping unsigned scalar alias integration smoke test");

        int[] output = new int[]{0, 0};

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new UInt(41), output}));
            assertArrayEquals(new int[]{17, 17}, output);
        }
    }

    @GPUStruct
    @OpenCLAttributes({"packed"})
    static final class Sample {
        float x;
        @OpenCLAttributes({"aligned(8)"})
        float y;
        int count;

        Sample(float x, float y, int count) {
            this.x = x;
            this.y = y;
            this.count = count;
        }
    }

    @GPUStruct
    static final class StructArraySample {
        float x;
        float y;

        StructArraySample() {
        }

        StructArraySample(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    @GPUStruct
    static final class InnerPoint {
        float x;
        float y;

        InnerPoint() {
        }

        InnerPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    @GPUStruct
    @OpenCLAttributes({"aligned(16)"})
    static final class ComplexStructArraySample {
        InnerPoint point;
        @OpenCLAttributes({"aligned(8)"})
        float bias;
        int count;

        ComplexStructArraySample() {
        }

        ComplexStructArraySample(InnerPoint point, float bias, int count) {
            this.point = point;
            this.bias = bias;
            this.count = count;
        }
    }

    private static void assumeOpenClAvailable() {
        try (OpenClRuntimeSession ignored = OpenClRuntimeSession.createDefault()) {
            // Session creation is enough for this smoke test to know OpenCL is reachable.
        } catch (UnsatisfiedLinkError | IllegalStateException exception) {
            Assumptions.assumeTrue(false, "Skipping OpenCL integration smoke test: " + exception.getMessage());
        }
    }

    private static void assumeLongRunningOpenClValidationEnabled() {
        Assumptions.assumeTrue(
                Boolean.getBoolean(LONG_RUNNING_PROPERTY),
                "Skipping long-running OpenCL stability test: set -D" + LONG_RUNNING_PROPERTY + "=true"
        );
    }

    private static void assumeWorkloadValidationEnabled() {
        Assumptions.assumeTrue(
                Boolean.getBoolean(WORKLOAD_VALIDATION_PROPERTY),
                "Skipping workload validation test: set -D" + WORKLOAD_VALIDATION_PROPERTY + "=true"
        );
    }

    private static void assumeIrGpuSourceReviewEnabled() {
        Assumptions.assumeTrue(
                Boolean.getBoolean(IRGPU_SOURCE_REVIEW_PROPERTY),
                "Skipping IrGpu source review test: set -D" + IRGPU_SOURCE_REVIEW_PROPERTY + "=true"
        );
    }

    private GpuKernelDescriptor imageWorkloadDescriptor() {
        return new GpuKernelDescriptor(
                "gpu_image_entry",
                "inline://integration/image-kernel.cl",
                """
                        __kernel void gpu_image_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            int4 pixel = read_imagei(inputImage, sampler, coords);
                            output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                            write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                        }""",
                IMAGE_KERNEL_IRGPU_RESOURCE,
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private GpuKernelDescriptor dualBufferIntWorkloadDescriptor() {
        return new GpuKernelDescriptor(
                "gpu_dual_buffer_int_entry",
                "inline://integration/dual-buffer-int-kernel.cl",
                """
                        __kernel void gpu_dual_buffer_int_entry(__global const int* left, __global const int* right, __global int* output) {
                            int id = get_global_id(0);
                            output[id] = left[id] + right[id];
                        }""",
                DUAL_BUFFER_INT_IRGPU_RESOURCE,
                java.util.List.of(
                        new GpuKernelParameterDescriptor("left", "int[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("right", "int[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static void assumeProductionSourceSwitchingValidationEnabled() {
        Assumptions.assumeTrue(
                Boolean.getBoolean(PRODUCTION_SOURCE_SWITCHING_VALIDATION_PROPERTY),
                "Skipping production source-switching validation test: set -D"
                        + PRODUCTION_SOURCE_SWITCHING_VALIDATION_PROPERTY
                        + "=true"
        );
    }

    private static void writeLongRunningSummary(OpenClLongRunningValidationSummary summary) {
        String outputPath = System.getProperty(LONG_RUNNING_SUMMARY_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            OpenClLongRunningValidationSummaryIO.write(Path.of(outputPath), summary);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write long-running OpenCL validation summary", exception);
        }
    }

    private static void writeWorkloadSummary(OpenClWorkloadValidationSummary summary) {
        String outputPath = System.getProperty(WORKLOAD_SUMMARY_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            OpenClWorkloadValidationSummaryIO.write(Path.of(outputPath), summary);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write OpenCL workload validation summary", exception);
        }
    }

    private static void clearLaunchAdvisoryIfConfigured(String kernelResource) throws IOException {
        Path artifactRoot = configuredRuntimeCompileArtifactRoot();
        if (artifactRoot == null || !Files.isDirectory(artifactRoot)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(artifactRoot)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(candidate -> OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME.equals(
                            candidate.getFileName().toString()
                    ))
                    .filter(candidate -> propertyEquals(candidate, "kernelResource", kernelResource))
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void assertNonBlockingLaunchAdvisoryIfConfigured(
            String kernelResource,
            long requestedLocalSize
    ) throws IOException {
        Path artifactRoot = configuredRuntimeCompileArtifactRoot();
        if (artifactRoot == null) {
            return;
        }
        assertTrue(Files.isDirectory(artifactRoot), "Runtime compile artifact directory is missing");

        Path advisoryFile;
        try (java.util.stream.Stream<Path> paths = Files.walk(artifactRoot)) {
            advisoryFile = paths
                    .filter(Files::isRegularFile)
                    .filter(candidate -> OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME.equals(
                            candidate.getFileName().toString()
                    ))
                    .filter(candidate -> propertyEquals(candidate, "kernelResource", kernelResource))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Launch advisory is missing for kernel resource " + kernelResource
                    ));
        }

        java.util.Properties properties = loadProperties(advisoryFile);
        assertEquals("false", properties.getProperty("blocking"));
        assertEquals("true", properties.getProperty("explicitLocalSize"));
        assertEquals(Long.toString(requestedLocalSize), properties.getProperty("requestedLocalWorkGroupSize"));
        assertEquals("true", properties.getProperty("comparisonPerformed"));

        long preferredMultiple = Long.parseLong(properties.getProperty("preferredWorkGroupSizeMultiple"));
        assertTrue(preferredMultiple > 0L, "Preferred work-group size multiple is unavailable");
        boolean matched = requestedLocalSize % preferredMultiple == 0L;
        assertEquals(matched ? "aligned" : "non-preferred-multiple", properties.getProperty("status"));
        assertEquals(Boolean.toString(matched), properties.getProperty("preferredMultipleMatched"));
    }

    private static Path configuredRuntimeCompileArtifactRoot() {
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            return null;
        }
        Path reportDirectory = Path.of(gatePath).getParent();
        return reportDirectory == null ? null : reportDirectory.resolve("runtime-compile-artifacts");
    }

    private static boolean propertyEquals(Path path, String key, String expectedValue) {
        try {
            return expectedValue.equals(loadProperties(path).getProperty(key));
        } catch (IOException exception) {
            return false;
        }
    }

    private static java.util.Properties loadProperties(Path path) throws IOException {
        java.util.Properties properties = new java.util.Properties();
        try (java.io.Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static void writeIrGpuSourceReviewSummary(String status) {
        String outputPath = System.getProperty(IRGPU_SOURCE_REVIEW_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        String normalizedStatus = status == null || status.isBlank() ? "unknown" : status;
        String properties = "status=" + normalizedStatus + "\n"
                + "reviewReady=" + "passed".equals(normalizedStatus) + "\n"
                + "completedAtUtc=" + Instant.now() + "\n"
                + "scope=opt-in-irgpu-source-review\n"
                + "productionSourceSwitching=false\n"
                + "sourceSelection=irgpu\n"
                + "optimizationProfile=" + GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE + "\n"
                + "kernel.count=1\n"
                + "kernel.0.name=gpu_irgpu_entry\n"
                + "kernel.0.resource=inline://integration/simple-irgpu-source-kernel.cl\n"
                + "kernel.0.irGpuResource=" + SIMPLE_IRGPU_SOURCE_RESOURCE + "\n"
                + "kernel.0.status=" + normalizedStatus + "\n"
                + "diagnostic.count=1\n"
                + "diagnostic.0=opt-in IrGpu source review lane does not alter production workload gate\n";
        try {
            Path path = Path.of(outputPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, properties, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write OpenCL IrGpu source review summary", exception);
        }
    }

    private static void writeProductionSourceSwitchingValidationSummary(String status) {
        String outputPath = System.getProperty(PRODUCTION_SOURCE_SWITCHING_VALIDATION_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        String normalizedStatus = status == null || status.isBlank() ? "unknown" : status;
        String properties = "status=" + normalizedStatus + "\n"
                + "reviewReady=" + "passed".equals(normalizedStatus) + "\n"
                + "completedAtUtc=" + Instant.now() + "\n"
                + "scope=controlled-production-source-switching-smoke\n"
                + "productionSourceSwitching=enabled\n"
                + "sourceSelection=irgpu\n"
                + "optimizationProfile=vendor-tuned\n"
                + "productionPromotionDecisionMode=" + GpuProductionPromotionDecision.PRODUCTION_ENABLED + "\n"
                + "kernel.count=7\n"
                + "kernel.0.name=gpu_irgpu_entry\n"
                + "kernel.0.resource=inline://integration/simple-irgpu-source-kernel.cl\n"
                + "kernel.0.irGpuResource=" + SIMPLE_IRGPU_SOURCE_RESOURCE + "\n"
                + "kernel.0.status=" + normalizedStatus + "\n"
                + "kernel.1.name=gpu_image_entry\n"
                + "kernel.1.resource=inline://integration/image-kernel.cl\n"
                + "kernel.1.irGpuResource=" + IMAGE_KERNEL_IRGPU_RESOURCE + "\n"
                + "kernel.1.status=" + normalizedStatus + "\n"
                + "kernel.2.name=gpu_dual_buffer_int_entry\n"
                + "kernel.2.resource=inline://integration/dual-buffer-int-kernel.cl\n"
                + "kernel.2.irGpuResource=" + DUAL_BUFFER_INT_IRGPU_RESOURCE + "\n"
                + "kernel.2.status=" + normalizedStatus + "\n"
                + "kernel.3.name=gpu_kernel\n"
                + "kernel.3.resource=javatogpu/sample/PerlinWorkload/kernel.cl\n"
                + "kernel.3.irGpuResource=javatogpu/sample/PerlinWorkload/kernel.irgpu.properties\n"
                + "kernel.3.status=" + normalizedStatus + "\n"
                + "kernel.4.name=gpu_kernel\n"
                + "kernel.4.resource=javatogpu/sample/PackedBlobWorkload/kernel.cl\n"
                + "kernel.4.irGpuResource=javatogpu/sample/PackedBlobWorkload/kernel.irgpu.properties\n"
                + "kernel.4.status=" + normalizedStatus + "\n"
                + "kernel.5.name=gpu_kernel\n"
                + "kernel.5.resource=javatogpu/sample/PackedNumericWorkload/kernel.cl\n"
                + "kernel.5.irGpuResource=javatogpu/sample/PackedNumericWorkload/kernel.irgpu.properties\n"
                + "kernel.5.status=" + normalizedStatus + "\n"
                + "kernel.6.name=gpu_kernel\n"
                + "kernel.6.resource=javatogpu/sample/Synthetic3DPackedGridWorkload/kernel.cl\n"
                + "kernel.6.irGpuResource=javatogpu/sample/Synthetic3DPackedGridWorkload/kernel.irgpu.properties\n"
                + "kernel.6.status=" + normalizedStatus + "\n"
                + "diagnostic.count=1\n"
                + "diagnostic.0=controlled production source-switching lane uses explicit production-enabled evidence only\n";
        try {
            Path path = Path.of(outputPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, properties, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write OpenCL production source-switching validation summary", exception);
        }
    }

    private static GpuProductionPromotionDecision productionEnabledDecision() {
        return new GpuProductionPromotionDecision(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                "production-ready",
                true,
                true,
                true,
                "none",
                "none",
                "controlled OpenCL source-switching smoke enables production IrGpu source compilation"
        );
    }

    private static GpuRuntimeCompileOptions productionSourceSwitchingOptions(String kernelResource) {
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            return productionSourceSwitchingOptions(backend, kernelResource);
        }
    }

    private static GpuRuntimeCompileOptions productionSourceSwitchingOptions(
            OpenClGpuRuntimeBackend backend,
            String kernelResource
    ) {
        GpuRuntimeDeviceProfile deviceProfile = backend.compileDeviceProfile();
        GpuProductionPromotionDecision decision = productionEnabledDecision();
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
                .openClProductionIrGpuSource(List.of(), "vendor-tuned")
                .withProductionPromotionDecision(decision);
        GpuKernelDescriptor bindingDescriptor = new GpuKernelDescriptor(
                "gpu_kernel",
                kernelResource,
                "",
                List.of()
        );
        return options.withProductionPromotionOperatorAcceptance(
                GpuProductionPromotionOperatorAcceptance.forContext(
                        "acceptance:controlled-production-source-switching:" + kernelResource,
                        deviceProfile.backendTarget(),
                        deviceProfile,
                        options.optimizationProfile(),
                        bindingDescriptor,
                        decision.mode()
                )
        );
    }

    private static String perlinWorkloadSource() {
        return """
                        package sample;

                        import net.sixik.ga_utils.javatogpu.api.Double3;
                        import net.sixik.ga_utils.javatogpu.api.GPU;
                        import net.sixik.ga_utils.javatogpu.api.Int3;
                        import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                        import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                        import java.util.Random;

                        public class PerlinWorkload {
                            private static final int IMPROVED_NOISE_PERMUTATION_SIZE = 256;

                            @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                            public static void kernel(PerlinNoiseInfo noise,
                                                      @GPUGlobal byte[] permutation0,
                                                      @GPUGlobal byte[] permutation1,
                                                      @GPUGlobal byte[] permutation2,
                                                      @GPUGlobal double[] outValues) {
                                int id = GPU.get_global_id(0);
                                double x = (id & 15) * 0.125;
                                double z = (id >> 4) * 0.125;
                                outValues[id] = NoiseMath.perlinValue(noise, permutation0, permutation1, permutation2, x, 0.0, z);
                            }

                            public static void cpuKernel(PerlinNoiseInfo noise,
                                                         byte[] permutation0,
                                                         byte[] permutation1,
                                                         byte[] permutation2,
                                                         double[] outValues) {
                                for (int id = 0; id < outValues.length; id++) {
                                    double x = (id & 15) * 0.125;
                                    double z = (id >> 4) * 0.125;
                                    outValues[id] = NoiseMath.perlinValue(noise, permutation0, permutation1, permutation2, x, 0.0, z);
                                }
                            }

                            @GPUStruct
                            public static class PerlinNoiseInfo {
                                public int firstOctave;
                                public int noiseLevelCount;
                                public double lowestFreqValueFactor;
                                public double lowestFreqInputFactor;
                                public double maxValue;
                                public Int3 levelActive;
                                public Double3 levelXo;
                                public Double3 levelYo;
                                public Double3 levelZo;
                                public Double3 amplitudes;

                                public PerlinNoiseInfo() {
                                }
                            }

                            public static final class NoiseMath {
                                private NoiseMath() {
                                }

                                @CCode
                                public static double perlinValue(
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
                                public static double improvedNoise(
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
                                public static double sampleAndLerp(
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
                                public static int permutation(@GPUGlobal byte[] permutations, int index) {
                                    byte value = permutations[index & 255];
                                    return value < 0 ? value + 256 : value;
                                }

                                @CCode(inline = true)
                                public static double gradDot(int gradient, double x, double y, double z) {
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
                                public static double smoothstep(double value) {
                                    return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
                                }

                                @CCode(inline = true)
                                public static double lerp(double delta, double start, double end) {
                                    return start + delta * (end - start);
                                }

                                @CCode(inline = true)
                                public static double lerp2(double dx, double dy, double x0y0, double x1y0, double x0y1, double x1y1) {
                                    return lerp(dy, lerp(dx, x0y0, x1y0), lerp(dx, x0y1, x1y1));
                                }

                                @CCode(inline = true)
                                public static double lerp3(
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
                                public static double wrap(double value) {
                                    return value - (double) floorToLong(value / 3.3554432E7 + 0.5) * 3.3554432E7;
                                }

                                @CCode(inline = true)
                                public static long floorToLong(double value) {
                                    long whole = (long) value;
                                    return value < whole ? whole - 1L : whole;
                                }

                                @CCode(inline = true)
                                public static int floorToInt(double value) {
                                    return (int) floorToLong(value);
                                }
                            }

                            public static final class Fixture {
                                public final PerlinNoiseInfo info;
                                public final byte[] permutation0;
                                public final byte[] permutation1;
                                public final byte[] permutation2;

                                public Fixture(PerlinNoiseInfo info, byte[] permutation0, byte[] permutation1, byte[] permutation2) {
                                    this.info = info;
                                    this.permutation0 = permutation0;
                                    this.permutation1 = permutation1;
                                    this.permutation2 = permutation2;
                                }
                            }

                            public static Fixture createDefaultFixture(long seed) {
                                Random random = new Random(seed);
                                PerlinNoiseInfo noise = new PerlinNoiseInfo();

                                noise.firstOctave = -3;
                                double[] amplitudes = new double[]{1.0, 1.0, 0.0};
                                noise.levelActive = new Int3(0, 0, 0);
                                noise.levelXo = new Double3(0.0, 0.0, 0.0);
                                noise.levelYo = new Double3(0.0, 0.0, 0.0);
                                noise.levelZo = new Double3(0.0, 0.0, 0.0);
                                noise.amplitudes = new Double3(amplitudes[0], amplitudes[1], amplitudes[2]);

                                byte[] permutation0 = new byte[IMPROVED_NOISE_PERMUTATION_SIZE];
                                byte[] permutation1 = new byte[IMPROVED_NOISE_PERMUTATION_SIZE];
                                byte[] permutation2 = new byte[IMPROVED_NOISE_PERMUTATION_SIZE];
                                int zeroOctaveIndex = -noise.firstOctave;

                                createDiscardedImprovedNoise(random);
                                if (zeroOctaveIndex >= 0 && zeroOctaveIndex < amplitudes.length && amplitudes[zeroOctaveIndex] != 0.0) {
                                    storeImprovedNoise(random, zeroOctaveIndex, noise, permutation0, permutation1, permutation2);
                                }

                                for (int k = zeroOctaveIndex - 1; k >= 0; k--) {
                                    if (k < amplitudes.length) {
                                        double amplitude = amplitudes[k];
                                        if (amplitude != 0.0) {
                                            storeImprovedNoise(random, k, noise, permutation0, permutation1, permutation2);
                                        } else {
                                            skipOctave(random, 262);
                                        }
                                    } else {
                                        skipOctave(random, 262);
                                    }
                                }

                                noise.noiseLevelCount = amplitudes.length;
                                noise.lowestFreqInputFactor = Math.pow(2.0, -zeroOctaveIndex);
                                noise.lowestFreqValueFactor = Math.pow(2.0, amplitudes.length - 1) / (Math.pow(2.0, amplitudes.length) - 1.0);
                                noise.maxValue = edgeValue(noise.levelActive, noise.amplitudes, noise.lowestFreqValueFactor, 2.0);

                                return new Fixture(noise, permutation0, permutation1, permutation2);
                            }

                            private static double edgeValue(Int3 noiseLevelActive, Double3 amplitudes, double lowestFreqValueFactor, double d) {
                                double e = 0.0;
                                double f = lowestFreqValueFactor;
                                if (noiseLevelActive.x != 0) {
                                    e += amplitudes.x * d * f;
                                }
                                f *= 0.5;
                                if (noiseLevelActive.y != 0) {
                                    e += amplitudes.y * d * f;
                                }
                                f *= 0.5;
                                if (noiseLevelActive.z != 0) {
                                    e += amplitudes.z * d * f;
                                }
                                return e;
                            }

                            private static void skipOctave(Random random, int value) {
                                for (int i = 0; i < value; i++) {
                                    random.nextInt();
                                }
                            }

                            private static void createDiscardedImprovedNoise(Random random) {
                                createImprovedNoise(random, null, null, null, null, -1);
                            }

                            private static void storeImprovedNoise(Random random,
                                                                   int levelIndex,
                                                                   PerlinNoiseInfo noise,
                                                                   byte[] permutation0,
                                                                   byte[] permutation1,
                                                                   byte[] permutation2) {
                                createImprovedNoise(random, noise, permutation0, permutation1, permutation2, levelIndex);
                            }

                            private static void createImprovedNoise(Random random,
                                                                    PerlinNoiseInfo noise,
                                                                    byte[] permutation0,
                                                                    byte[] permutation1,
                                                                    byte[] permutation2,
                                                                    int levelIndex) {
                                double xo = random.nextDouble() * 256.0;
                                double yo = random.nextDouble() * 256.0;
                                double zo = random.nextDouble() * 256.0;

                                byte[] p = new byte[256];
                                for (int i = 0; i < p.length; i++) {
                                    p[i] = (byte) i;
                                }

                                for (int i = 0; i < 256; ++i) {
                                    int j = random.nextInt(256 - i);
                                    byte b = p[i];
                                    p[i] = p[i + j];
                                    p[i + j] = b;
                                }

                                if (levelIndex >= 0) {
                                    switch (levelIndex) {
                                        case 0 -> {
                                            noise.levelActive.x = 1;
                                            noise.levelXo.x = xo;
                                            noise.levelYo.x = yo;
                                            noise.levelZo.x = zo;
                                            System.arraycopy(p, 0, permutation0, 0, p.length);
                                        }
                                        case 1 -> {
                                            noise.levelActive.y = 1;
                                            noise.levelXo.y = xo;
                                            noise.levelYo.y = yo;
                                            noise.levelZo.y = zo;
                                            System.arraycopy(p, 0, permutation1, 0, p.length);
                                        }
                                        case 2 -> {
                                            noise.levelActive.z = 1;
                                            noise.levelXo.z = xo;
                                            noise.levelYo.z = yo;
                                            noise.levelZo.z = zo;
                                            System.arraycopy(p, 0, permutation2, 0, p.length);
                                        }
                                        default -> {
                                        }
                                    }
                                }
                            }
                        }
                """;
    }
    private static String packedBlobWorkloadSource() {
        return """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                import java.nio.ByteBuffer;
                import java.nio.ByteOrder;

                public class PackedBlobWorkload {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    public static void kernel(@GPUGlobal byte[] blob, PackedNoiseView view, @GPUGlobal int[] output) {
                        int id = GPU.get_global_id(0);
                        GlobalBytePtr root = GPU.global(blob);
                        int sampler = root.add(view.samplerOffset + id * 4).asIntPtr().value;
                        int density = root.add(view.densityOffset + id * 4).asIntPtr().value;
                        output[id] = sampler + density;
                    }

                    public static void cpuKernel(byte[] blob, PackedNoiseView view, int[] output) {
                        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                        for (int id = 0; id < output.length; id++) {
                            int sampler = buffer.getInt(view.samplerOffset + id * 4);
                            int density = buffer.getInt(view.densityOffset + id * 4);
                            output[id] = sampler + density;
                        }
                    }

                    @GPUStruct
                    public static class PackedNoiseView {
                        public int samplerOffset;
                        public int densityOffset;

                        public PackedNoiseView() {
                        }

                        public PackedNoiseView(int samplerOffset, int densityOffset) {
                            this.samplerOffset = samplerOffset;
                            this.densityOffset = densityOffset;
                        }
                    }

                    public static final class Fixture {
                        public final byte[] blob;
                        public final PackedNoiseView view;

                        public Fixture(byte[] blob, PackedNoiseView view) {
                            this.blob = blob;
                            this.view = view;
                        }
                    }

                    public static Fixture createFixture() {
                        int[] samplerValues = new int[]{7, 14, 21, 28, 35, 42, 49, 56};
                        int[] densityValues = new int[]{3, 6, 9, 12, 15, 18, 21, 24};
                        int samplerOffset = 0;
                        int densityOffset = samplerValues.length * 4;
                        byte[] blob = new byte[(samplerValues.length + densityValues.length) * 4];
                        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                        for (int i = 0; i < samplerValues.length; i++) {
                            buffer.putInt(samplerOffset + i * 4, samplerValues[i]);
                            buffer.putInt(densityOffset + i * 4, densityValues[i]);
                        }
                        return new Fixture(blob, new PackedNoiseView(samplerOffset, densityOffset));
                    }
                }
                """;
    }

    private static String packedNumericWorkloadSource() {
        return """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;

                import java.nio.ByteBuffer;
                import java.nio.ByteOrder;

                public class PackedNumericWorkload {
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    public static void kernel(@GPUGlobal byte[] blob, PackedNumericView view, @GPUGlobal double[] output) {
                        int id = GPU.get_global_id(0);
                        GlobalBytePtr root = GPU.global(blob);
                        double sampler = root.add(view.samplerOffset + id * 8).asDoublePtr().value;
                        double density = root.add(view.densityOffset + id * 8).asDoublePtr().value;
                        int octave = root.add(view.octaveOffset + id * 4).asIntPtr().value;
                        double bias = root.add(view.biasOffset).asDoublePtr().value;
                        output[id] = NumericMath.mix(sampler, density, octave, bias);
                    }

                    public static double cpuMix(double sampler, double density, int octave, double bias) {
                        return (sampler * 0.75) + (density * 1.25) + octave * bias;
                    }

                    public static void cpuKernel(byte[] blob, PackedNumericView view, double[] output) {
                        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                        for (int id = 0; id < output.length; id++) {
                            double sampler = buffer.getDouble(view.samplerOffset + id * 8);
                            double density = buffer.getDouble(view.densityOffset + id * 8);
                            int octave = buffer.getInt(view.octaveOffset + id * 4);
                            double bias = buffer.getDouble(view.biasOffset);
                            output[id] = cpuMix(sampler, density, octave, bias);
                        }
                    }

                    @GPUStruct
                    public static class PackedNumericView {
                        public int samplerOffset;
                        public int densityOffset;
                        public int octaveOffset;
                        public int biasOffset;

                        public PackedNumericView() {
                        }

                        public PackedNumericView(int samplerOffset, int densityOffset, int octaveOffset, int biasOffset) {
                            this.samplerOffset = samplerOffset;
                            this.densityOffset = densityOffset;
                            this.octaveOffset = octaveOffset;
                            this.biasOffset = biasOffset;
                        }
                    }

                    public static final class NumericMath {
                        private NumericMath() {
                        }

                        @CCode(inline = true)
                        public static double mix(double sampler, double density, int octave, double bias) {
                            return (sampler * 0.75) + (density * 1.25) + octave * bias;
                        }
                    }

                    public static final class Fixture {
                        public final byte[] blob;
                        public final PackedNumericView view;

                        public Fixture(byte[] blob, PackedNumericView view) {
                            this.blob = blob;
                            this.view = view;
                        }
                    }

                    public static Fixture createFixture() {
                        double[] samplerValues = new double[]{1.5, 2.5, 3.5, 4.5, 5.5, 6.5};
                        double[] densityValues = new double[]{0.25, 0.5, 0.75, 1.0, 1.25, 1.5};
                        int[] octaveValues = new int[]{1, 2, 3, 4, 5, 6};
                        double bias = 0.125;

                        int samplerOffset = 0;
                        int densityOffset = samplerValues.length * 8;
                        int octaveOffset = densityOffset + densityValues.length * 8;
                        int biasOffset = octaveOffset + octaveValues.length * 4;
                        byte[] blob = new byte[biasOffset + 8];
                        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);

                        for (int i = 0; i < samplerValues.length; i++) {
                            buffer.putDouble(samplerOffset + i * 8, samplerValues[i]);
                            buffer.putDouble(densityOffset + i * 8, densityValues[i]);
                            buffer.putInt(octaveOffset + i * 4, octaveValues[i]);
                        }
                        buffer.putDouble(biasOffset, bias);
                        return new Fixture(blob, new PackedNumericView(samplerOffset, densityOffset, octaveOffset, biasOffset));
                    }
                }
                """;
    }

    private static String synthetic3DPackedGridWorkloadSource() {
        return """
                package sample;

                import net.sixik.ga_utils.javatogpu.api.GPU;
                import net.sixik.ga_utils.javatogpu.api.GlobalBytePtr;
                import net.sixik.ga_utils.javatogpu.api.GlobalIntPtr;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
                import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
                import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;

                import java.nio.ByteBuffer;
                import java.nio.ByteOrder;

                public class Synthetic3DPackedGridWorkload {
                    @OpenCLAttributes({"reqd_work_group_size(8, 8, 1)"})
                    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
                    public static void kernel(@GPUGlobal byte[] blob, PackedGridLayout layout, @GPUGlobal int[] output) {
                        int relX = GPU.get_global_id(0);
                        int relZ = GPU.get_global_id(1);
                        int relY = GPU.get_global_id(2);
                        int idx = (relY * layout.depth + relZ) * layout.width + relX;
                        GlobalBytePtr root = GPU.global(blob);
                        int primary = root.intPtrAt(layout.primaryOffset + idx * 4).value;
                        int secondary = root.readIntAt(layout.secondaryOffset + idx * 4);
                        GlobalIntPtr adjustment = root.intPtrAt(layout.adjustmentOffset);
                        output[idx] = primary + secondary + adjustment.value + relX - relZ + relY;
                    }

                    public static void cpuKernel(byte[] blob, PackedGridLayout layout, int[] output) {
                        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                        for (int relY = 0; relY < layout.height; relY++) {
                            for (int relZ = 0; relZ < layout.depth; relZ++) {
                                for (int relX = 0; relX < layout.width; relX++) {
                                    int idx = (relY * layout.depth + relZ) * layout.width + relX;
                                    int primary = buffer.getInt(layout.primaryOffset + idx * 4);
                                    int secondary = buffer.getInt(layout.secondaryOffset + idx * 4);
                                    int adjustment = buffer.getInt(layout.adjustmentOffset);
                                    output[idx] = primary + secondary + adjustment + relX - relZ + relY;
                                }
                            }
                        }
                    }

                    @GPUStruct
                    public static class PackedGridLayout {
                        public int primaryOffset;
                        public int secondaryOffset;
                        public int adjustmentOffset;
                        public int width;
                        public int depth;
                        public int height;

                        public PackedGridLayout() {
                        }

                        public PackedGridLayout(int primaryOffset, int secondaryOffset, int adjustmentOffset, int width, int depth, int height) {
                            this.primaryOffset = primaryOffset;
                            this.secondaryOffset = secondaryOffset;
                            this.adjustmentOffset = adjustmentOffset;
                            this.width = width;
                            this.depth = depth;
                            this.height = height;
                        }
                    }

                    public static final class Fixture {
                        public final byte[] blob;
                        public final PackedGridLayout layout;

                        public Fixture(byte[] blob, PackedGridLayout layout) {
                            this.blob = blob;
                            this.layout = layout;
                        }
                    }

                    public static Fixture createFixture() {
                        int width = 8;
                        int depth = 8;
                        int height = 2;
                        int count = width * depth * height;
                        int primaryOffset = 0;
                        int secondaryOffset = primaryOffset + count * 4;
                        int adjustmentOffset = secondaryOffset + count * 4;
                        byte[] blob = new byte[adjustmentOffset + 4];
                        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
                        for (int i = 0; i < count; i++) {
                            buffer.putInt(primaryOffset + i * 4, i * 3 + 7);
                            buffer.putInt(secondaryOffset + i * 4, 1000 - i * 5);
                        }
                        buffer.putInt(adjustmentOffset, 13);
                        return new Fixture(blob, new PackedGridLayout(primaryOffset, secondaryOffset, adjustmentOffset, width, depth, height));
                    }
                }
                """;
    }

    private void runPerlinWorkloadComparison() throws Exception {
        comparesGeneratedLauncherPerlinWorkloadAgainstCpuReferenceOnAvailableOpenClDevice();
    }

    private void runPackedBlobWorkloadComparison() throws Exception {
        comparesGeneratedLauncherPackedBlobWorkloadAgainstCpuReferenceOnAvailableOpenClDevice();
    }

    private void runPackedNumericWorkloadComparison() throws Exception {
        comparesGeneratedLauncherPackedNumericWorkloadAgainstCpuReferenceOnAvailableOpenClDevice();
    }

    private void runPackedGrid3dWorkloadComparison() throws Exception {
        comparesGeneratedLauncherSynthetic3DPackedGridWorkloadAgainstCpuReferenceOnAvailableOpenClDevice();
    }

    private void runImageWorkloadComparison() throws Exception {
        comparesImageSamplerWorkloadAgainstCpuReferenceOnAvailableOpenClDevice();
    }

    private static void assumeOpenClFp64Available(String messagePrefix) {
        GpuRuntimeBackendReport report;
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            report = backend.describeCapabilities();
        } catch (RuntimeException exception) {
            Assumptions.assumeTrue(false, messagePrefix + ": " + exception.getMessage());
            return;
        }

        Assumptions.assumeTrue(report.available(), messagePrefix + ": " + report.detail());
        Assumptions.assumeTrue(report.supports(GpuRuntimeFeature.DOUBLE_PRECISION), messagePrefix + ": no fp64 support");
    }

    private static void assumeKernelCompiles(GpuKernelDescriptor descriptor, String messagePrefix) {
        try (OpenClRuntimeSession session = OpenClRuntimeSession.createDefault();
             OpenClCompiledKernel ignored = session.compileKernel(descriptor)) {
            // Compilation succeeded, so the runtime test can proceed.
        } catch (UnsatisfiedLinkError | IllegalStateException | OpenClException exception) {
            Assumptions.assumeTrue(false, messagePrefix + ": " + exception.getMessage());
        }
    }

    private static CompiledGpuSource compileGpuSource(String className, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler is not available in the current test runtime");
        }

        Path classOutputDir = Files.createTempDirectory("javatogpu-runtime-pipeline-classes");
        Path generatedOutputDir = Files.createTempDirectory("javatogpu-runtime-pipeline-generated");

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classOutputDir.toString(),
                    "-s", generatedOutputDir.toString()
            );
            JavaFileObject sourceFile = new StringJavaFileObject(className, source);
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

        return new CompiledGpuSource(classOutputDir, generatedOutputDir);
    }

    private record CompiledGpuSource(Path classOutputDir, Path generatedOutputDir) {
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        private StringJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
