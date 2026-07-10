package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClBuffer;
import dev.denismasterherobrine.packager.opencl.core.OpenClCommandQueue;
import dev.denismasterherobrine.packager.opencl.core.OpenClContext;
import dev.denismasterherobrine.packager.opencl.core.OpenClDevice;
import dev.denismasterherobrine.packager.opencl.core.OpenClKernel;
import dev.denismasterherobrine.packager.opencl.core.OpenClProgram;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestPerformance;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestRunner;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelfTestSampleSummary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.lwjgl.opencl.CL10;

/**
 * Executes a bounded OpenCL compile, enqueue, and readback correctness smoke on each discovered device.
 */
final class OpenClRuntimeDeviceSelfTestRunner implements GpuRuntimeDeviceSelfTestRunner {

    static final String RUNNER_ID = "javatogpu.opencl.correctness-smoke";
    static final String RUNNER_VERSION = "3";

    private static final int ELEMENT_COUNT = 64;
    private static final int PASS_SCORE_ADJUSTMENT = 100_000;
    private static final int COMPUTE_OPERATIONS_PER_ITERATION = 4;
    private static final String CORRECTNESS_KERNEL_NAME = "jtg_device_self_test";
    private static final String COMPUTE_KERNEL_NAME = "jtg_device_compute_smoke";
    private static final String KERNEL_SOURCE = """
            __kernel void jtg_device_self_test(__global int* output) {
                int id = (int)get_global_id(0);
                output[id] = id * 31 + 7;
            }

            __kernel void jtg_device_compute_smoke(__global uint* output, uint seed, uint iterationCount) {
                int id = (int)get_global_id(0);
                uint value = seed ^ (uint)id;
                for (uint iteration = 0; iteration < iterationCount; iteration++) {
                    value = value * 1664525u + 1013904223u;
                    value ^= value >> 13;
                }
                output[id] = value;
            }
            """;

    private final Map<String, OpenClDevice> devicesByKey;

    OpenClRuntimeDeviceSelfTestRunner(
            List<OpenClDevice> devices,
            List<GpuRuntimeDeviceProfile> profiles
    ) {
        if (devices == null || profiles == null || devices.size() != profiles.size()) {
            throw new IllegalArgumentException("OpenCL self-test devices and profiles must have equal sizes");
        }
        LinkedHashMap<String, OpenClDevice> indexed = new LinkedHashMap<>();
        for (int index = 0; index < devices.size(); index++) {
            indexed.put(GpuRuntimeDevicePolicyContext.deviceKey(profiles.get(index)), devices.get(index));
        }
        devicesByKey = Map.copyOf(indexed);
    }

    @Override
    public GpuRuntimeDeviceSelfTestResult run(GpuRuntimeDeviceSelfTestRequest request) {
        GpuRuntimeDeviceProfile profile = request.deviceProfile();
        OpenClDevice device = devicesByKey.get(GpuRuntimeDevicePolicyContext.deviceKey(profile));
        if (device == null) {
            return GpuRuntimeDeviceSelfTestResult.skipped(
                    request.identity(),
                    List.of("OpenCL device handle is not available for the discovered profile")
            );
        }

        GpuRuntimeDeviceSelfTestProfile selfTestProfile = GpuRuntimeDeviceSelfTestProfile.forDevice(profile);
        long startedAt = System.nanoTime();
        try (OpenClContext context = OpenClContext.create(device);
             OpenClCommandQueue queue = context.createQueue(true);
             OpenClProgram program = context.buildProgram(KERNEL_SOURCE);
             OpenClKernel correctnessKernel = program.createKernel(CORRECTNESS_KERNEL_NAME);
             OpenClKernel computeKernel = program.createKernel(COMPUTE_KERNEL_NAME);
             OpenClBuffer correctnessOutput = context.createReadWriteBuffer((long) ELEMENT_COUNT * Integer.BYTES);
             OpenClBuffer computeOutput = context.createReadWriteBuffer(
                     (long) selfTestProfile.computeWorkItems() * Integer.BYTES
             );
             OpenClBuffer transferBuffer = context.createReadWriteBuffer(
                     selfTestProfile.transferBytesOneWay()
             )) {
            correctnessKernel.setArg(0, correctnessOutput);
            enqueueAndFinish(correctnessKernel, queue, ELEMENT_COUNT);

            IntBuffer values = ByteBuffer.allocateDirect(ELEMENT_COUNT * Integer.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
            queue.readBuffer(correctnessOutput, true, 0L, values, null, null);
            queue.finish();
            String mismatch = firstMismatch(values);
            if (mismatch != null) {
                return GpuRuntimeDeviceSelfTestResult.failed(
                        request.identity(),
                        System.nanoTime() - startedAt,
                        List.of(mismatch)
                );
            }

            GpuRuntimeDeviceSelfTestPerformance performance = measurePerformance(
                    queue,
                    computeKernel,
                    computeOutput,
                    transferBuffer,
                    selfTestProfile,
                    profile.unifiedMemory()
            );
            long durationNanos = System.nanoTime() - startedAt;
            ArrayList<String> diagnostics = new ArrayList<>();
            diagnostics.add("OpenCL compile, enqueue, and readback correctness smoke passed");
            diagnostics.add("self-test profile="
                    + selfTestProfile.profileId()
                    + " transferModel="
                    + selfTestProfile.transferModel());
            diagnostics.add("bounded compute smoke median="
                    + performance.computeSamples().medianNanos()
                    + "ns noise="
                    + performance.computeSamples().noisePermille()
                    + "permille");
            diagnostics.add("bounded transfer smoke median="
                    + performance.transferSamples().medianNanos()
                    + "ns noise="
                    + performance.transferSamples().noisePermille()
                    + "permille");
            return GpuRuntimeDeviceSelfTestResult.passed(
                    request.identity(),
                    durationNanos,
                    PASS_SCORE_ADJUSTMENT,
                    performance,
                    diagnostics
            );
        }
    }

    @Override
    public boolean supports(GpuRuntimeDeviceProfile profile) {
        return profile != null
                && profile.backendTarget() == GpuBackendTarget.OPENCL
                && devicesByKey.containsKey(GpuRuntimeDevicePolicyContext.deviceKey(profile));
    }

    @Override
    public String runnerId() {
        return RUNNER_ID;
    }

    @Override
    public String runnerVersion() {
        return RUNNER_VERSION;
    }

    static String firstMismatch(IntBuffer values) {
        for (int index = 0; index < ELEMENT_COUNT; index++) {
            int expected = index * 31 + 7;
            int actual = values.get(index);
            if (actual != expected) {
                return "OpenCL correctness smoke mismatch at index "
                        + index
                        + ": expected "
                        + expected
                        + ", got "
                        + actual;
            }
        }
        return null;
    }

    private static GpuRuntimeDeviceSelfTestPerformance measurePerformance(
            OpenClCommandQueue queue,
            OpenClKernel computeKernel,
            OpenClBuffer computeOutput,
            OpenClBuffer transferBuffer,
            GpuRuntimeDeviceSelfTestProfile selfTestProfile,
            boolean unifiedMemory
    ) {
        computeKernel.setArg(0, computeOutput);
        computeKernel.setArgInt(1, 0x13579BDF);
        computeKernel.setArgInt(2, selfTestProfile.computeIterations());
        enqueueAndFinish(computeKernel, queue, selfTestProfile.computeWorkItems());

        ArrayList<Long> computeSamples = new ArrayList<>(selfTestProfile.sampleCount());
        for (int index = 0; index < selfTestProfile.sampleCount(); index++) {
            computeKernel.setArgInt(1, 0x13579BDF + index);
            long startedAt = System.nanoTime();
            enqueueAndFinish(computeKernel, queue, selfTestProfile.computeWorkItems());
            computeSamples.add(System.nanoTime() - startedAt);
        }

        IntBuffer writeValues = ByteBuffer.allocateDirect(selfTestProfile.transferBytesOneWay())
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        IntBuffer readValues = ByteBuffer.allocateDirect(selfTestProfile.transferBytesOneWay())
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        for (int index = 0; index < writeValues.capacity(); index++) {
            writeValues.put(index, index * 17 + 3);
        }
        transferRoundTrip(queue, transferBuffer, writeValues, readValues);

        ArrayList<Long> transferSamples = new ArrayList<>(selfTestProfile.sampleCount());
        for (int index = 0; index < selfTestProfile.sampleCount(); index++) {
            long startedAt = System.nanoTime();
            transferRoundTrip(queue, transferBuffer, writeValues, readValues);
            transferSamples.add(System.nanoTime() - startedAt);
        }

        return new GpuRuntimeDeviceSelfTestPerformance(
                selfTestProfile.profileId(),
                selfTestProfile.transferModel(),
                unifiedMemory,
                selfTestProfile.computeScoreCap(),
                selfTestProfile.transferScoreCap(),
                selfTestProfile.computeOperations(COMPUTE_OPERATIONS_PER_ITERATION),
                GpuRuntimeDeviceSelfTestSampleSummary.summarize(
                        computeSamples,
                        selfTestProfile.maxNoisePermille()
                ),
                selfTestProfile.transferBytesRoundTrip(),
                GpuRuntimeDeviceSelfTestSampleSummary.summarize(
                        transferSamples,
                        selfTestProfile.maxNoisePermille()
                )
        );
    }

    private static void transferRoundTrip(
            OpenClCommandQueue queue,
            OpenClBuffer transferBuffer,
            IntBuffer writeValues,
            IntBuffer readValues
    ) {
        writeValues.position(0);
        readValues.clear();
        queue.writeBuffer(transferBuffer, true, 0L, writeValues, null, null);
        queue.readBuffer(transferBuffer, true, 0L, readValues, null, null);
        queue.finish();
    }

    private static void enqueueAndFinish(
            OpenClKernel kernel,
            OpenClCommandQueue queue,
            long globalWorkSize
    ) {
        long event = kernel.enqueue1D(queue, globalWorkSize, null);
        try {
            queue.finish();
        } finally {
            if (event != 0L) {
                CL10.clReleaseEvent(event);
            }
        }
    }
}
