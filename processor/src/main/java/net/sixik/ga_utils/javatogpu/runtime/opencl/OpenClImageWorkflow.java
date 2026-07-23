package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.images.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Sampler;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFeature;

import java.util.Objects;

/**
 * Small host-side helpers for common OpenCL image workflows.
 *
 * <p>The helpers keep OpenCL image resources explicit, but bundle the repetitive create/read/close plumbing for common
 * examples and smoke tests.</p>
 */
public final class OpenClImageWorkflow {

    private OpenClImageWorkflow() {
    }

    /**
     * Creates a common 2D RGBA signed-int input, RGBA float output, and nearest clamp-to-edge sampler bundle.
     */
    public static RgbaIntToFloat2D rgbaIntToFloat2D(
            OpenClGpuRuntimeBackend backend,
            int width,
            int height,
            int[] rgbaInput
    ) {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(rgbaInput, "rgbaInput");
        requireImageSupport(backend);
        requireRgbaElementCount("rgbaInput", width, height, rgbaInput.length);

        Image2DReadOnly input = null;
        Image2DWriteOnly output = null;
        Sampler sampler = null;
        try {
            input = backend.createReadOnlyRgbaIntImage(width, height, rgbaInput);
            output = backend.createWriteOnlyRgbaFloatImage(width, height);
            sampler = backend.createNearestClampToEdgeSampler();
            return new RgbaIntToFloat2D(backend, width, height, input, output, sampler);
        } catch (RuntimeException exception) {
            closeAfterFailedCreate(sampler, exception);
            closeAfterFailedCreate(output, exception);
            closeAfterFailedCreate(input, exception);
            throw exception;
        }
    }

    /**
     * Fails fast when the selected OpenCL backend cannot use image objects.
     */
    public static void requireImageSupport(OpenClGpuRuntimeBackend backend) {
        Objects.requireNonNull(backend, "backend");
        GpuRuntimeBackendReport report = backend.describeCapabilities();
        if (!report.available()) {
            throw new UnsupportedOperationException(
                    "OpenCL image workflow requires an available backend"
                            + detailSuffix(report)
            );
        }
        if (!report.supports(GpuRuntimeFeature.IMAGES)) {
            throw new UnsupportedOperationException(
                    "OpenCL image workflow requires IMAGES support on the selected device"
                            + detailSuffix(report)
            );
        }
    }

    static int requireRgbaElementCount(String label, int width, int height, int actualLength) {
        String name = label == null || label.isBlank() ? "rgba" : label;
        int expectedLength = rgbaElementCount(name, width, height);
        if (actualLength != expectedLength) {
            throw new IllegalArgumentException(
                    name
                            + " must contain width * height * 4 RGBA elements: expected "
                            + expectedLength
                            + " but found "
                            + actualLength
            );
        }
        return expectedLength;
    }

    /**
     * Returns the number of pixels for a positive 2D image shape.
     */
    public static long pixelCount(int width, int height) {
        return pixelCount("image", width, height);
    }

    /**
     * Returns the Java array element count for an RGBA image backed by four scalar channels per pixel.
     */
    public static int rgbaElementCount(int width, int height) {
        return rgbaElementCount("rgba", width, height);
    }

    private static long pixelCount(String label, int width, int height) {
        String name = label == null || label.isBlank() ? "image" : label;
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    name + " dimensions must be positive: width=" + width + ", height=" + height
            );
        }
        return (long) width * height;
    }

    private static int rgbaElementCount(String label, int width, int height) {
        String name = label == null || label.isBlank() ? "rgba" : label;
        long pixelCount = pixelCount(name + " image", width, height);
        long expectedLength = pixelCount * 4L;
        if (expectedLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    name + " image is too large for a Java array-backed RGBA workflow: width="
                            + width
                            + ", height="
                            + height
                            + ", expectedElements="
                            + expectedLength
            );
        }
        return (int) expectedLength;
    }

    private static String detailSuffix(GpuRuntimeBackendReport report) {
        StringBuilder builder = new StringBuilder();
        if (report.deviceLabel() != null && !report.deviceLabel().isBlank()) {
            builder.append("; device=").append(report.deviceLabel());
        }
        if (report.detail() != null && !report.detail().isBlank()) {
            builder.append("; detail=").append(report.detail());
        }
        return builder.toString();
    }

    private static void closeAfterFailedCreate(AutoCloseable closeable, RuntimeException primary) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception closeException) {
            primary.addSuppressed(closeException);
        }
    }

    private static RuntimeException closeResource(AutoCloseable closeable, RuntimeException failure) {
        if (closeable == null) {
            return failure;
        }
        try {
            closeable.close();
            return failure;
        } catch (Exception exception) {
            RuntimeException closeFailure = exception instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new RuntimeException("Failed to close OpenCL image workflow resource", exception);
            if (failure == null) {
                return closeFailure;
            }
            failure.addSuppressed(closeFailure);
            return failure;
        }
    }

    /**
     * Owned resources for the common 2D RGBA int-input to RGBA float-output workflow.
     */
    public static final class RgbaIntToFloat2D implements AutoCloseable {

        private final OpenClGpuRuntimeBackend backend;
        private final int width;
        private final int height;
        private final Image2DReadOnly input;
        private final Image2DWriteOnly output;
        private final Sampler sampler;
        private boolean closed;

        private RgbaIntToFloat2D(
                OpenClGpuRuntimeBackend backend,
                int width,
                int height,
                Image2DReadOnly input,
                Image2DWriteOnly output,
                Sampler sampler
        ) {
            this.backend = backend;
            this.width = width;
            this.height = height;
            this.input = input;
            this.output = output;
            this.sampler = sampler;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public long pixelCount() {
            return OpenClImageWorkflow.pixelCount(width, height);
        }

        public int rgbaElementCount() {
            return OpenClImageWorkflow.rgbaElementCount(width, height);
        }

        /**
         * Returns the natural 2D launch shape for image kernels that process one work-item per pixel.
         */
        public GpuExecutionConfig executionConfig() {
            return GpuExecutionConfig.twoDimensional(width, height);
        }

        public String summary() {
            return "2D RGBA int->float image "
                    + width
                    + "x"
                    + height
                    + ", pixels="
                    + pixelCount()
                    + ", rgbaElements="
                    + rgbaElementCount();
        }

        public Image2DReadOnly input() {
            return input;
        }

        public Image2DWriteOnly output() {
            return output;
        }

        public Sampler sampler() {
            return sampler;
        }

        public float[] readOutputRgbaFloat() {
            return backend.readRgbaFloatImage(output);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            RuntimeException failure = null;
            failure = closeResource(sampler, failure);
            failure = closeResource(output, failure);
            failure = closeResource(input, failure);
            if (failure != null) {
                throw failure;
            }
        }
    }
}
