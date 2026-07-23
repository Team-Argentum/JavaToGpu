package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.api.images.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaDriverImageSamplerObjectTest {

    @Test
    void imageSamplerObjectsAreOwnedByArgumentFrameAndDestroyedOnClose() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        CudaDriverImageSamplerObject texture = CudaDriverImageSamplerObject.texture(
                0,
                "inputImage",
                Image2DReadOnly.class.getName(),
                "cuda-array-2d",
                0xCAFE_5001L,
                0xCAFE_6001L,
                invoker
        );
        CudaDriverImageSamplerObject surface = CudaDriverImageSamplerObject.surface(
                1,
                "outputImage",
                Image2DWriteOnly.class.getName(),
                "cuda-array-2d",
                0xCAFE_5002L,
                0xCAFE_6002L,
                invoker
        );
        CudaKernelArgumentFrame frame = CudaKernelArgumentFrame.nativeBindings(
                "cuda-argument-binder:driver",
                new GpuRuntimeInvocationBindingSummary(0, 0, 0, 2),
                List.of(),
                null,
                List.of(),
                List.of(),
                0L,
                CudaLocalSharedMemoryLayout.empty(),
                List.of(texture, surface)
        );
        Map<String, String> fields = frame.artifactFields("test.cuda.argumentFrame");

        assertEquals(2, frame.imageSamplerObjectCount());
        assertEquals(2, frame.imageSamplerObjects().size());
        assertEquals("2", fields.get("runtime.cuda.argumentFrame.imageSamplerObject.count"));
        assertEquals("texture", fields.get("runtime.cuda.argumentFrame.imageSamplerObject.0.object.kind"));
        assertEquals("surface", fields.get("runtime.cuda.argumentFrame.imageSamplerObject.1.object.kind"));
        assertEquals("CUtexObject", fields.get("runtime.cuda.argumentFrame.imageSamplerObject.0.parameter.carrier"));
        assertEquals("CUsurfObject", fields.get("runtime.cuda.argumentFrame.imageSamplerObject.1.parameter.carrier"));
        assertEquals("true", fields.get("runtime.cuda.argumentFrame.imageSamplerObject.0.handle.present"));
        assertEquals("true", fields.get("runtime.cuda.argumentFrame.imageSamplerObject.1.destroyFunction.present"));

        frame.close();

        assertTrue(frame.closed());
        assertTrue(texture.closed());
        assertTrue(surface.closed());
        assertEquals(CudaDriverLibrary.CUDA_SUCCESS, texture.closeStatus());
        assertEquals(CudaDriverLibrary.CUDA_SUCCESS, surface.closeStatus());
        assertEquals(List.of(0xCAFE_5001L), invoker.destroyedTextures);
        assertEquals(List.of(0xCAFE_5002L), invoker.destroyedSurfaces);
    }

    @Test
    void imageSamplerObjectCloseFailsWithStableDestroyStatus() {
        FakeDriverApiInvoker invoker = new FakeDriverApiInvoker();
        invoker.textureDestroyStatus = 700;
        CudaDriverImageSamplerObject texture = CudaDriverImageSamplerObject.texture(
                0,
                "inputImage",
                Image2DReadOnly.class.getName(),
                "cuda-array-2d",
                0xCAFE_5003L,
                0xCAFE_6003L,
                invoker
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, texture::close);

        assertTrue(texture.closed());
        assertEquals(700, texture.closeStatus());
        assertEquals(List.of(0xCAFE_5003L), invoker.destroyedTextures);
        assertTrue(exception.getMessage().contains("CUDA texture object destroy failed with code 700"));
    }

    private static final class FakeDriverApiInvoker implements CudaDriverLibrary.DriverApiInvoker {
        private final List<Long> destroyedTextures = new ArrayList<>();
        private final List<Long> destroyedSurfaces = new ArrayList<>();
        private int textureDestroyStatus = CudaDriverLibrary.CUDA_SUCCESS;
        private int surfaceDestroyStatus = CudaDriverLibrary.CUDA_SUCCESS;

        @Override
        public int cuInit(int flags, long functionAddress) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuModuleLoadDataEx(
                long moduleOutAddress,
                long imageAddress,
                int optionCount,
                long optionsAddress,
                long optionValuesAddress,
                long functionAddress
        ) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuModuleGetFunction(
                long functionOutAddress,
                long moduleHandle,
                long kernelNameAddress,
                long functionAddress
        ) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuModuleUnload(long moduleHandle, long functionAddress) {
            return CudaDriverLibrary.CUDA_SUCCESS;
        }

        @Override
        public int cuTexObjectDestroy(long textureObjectHandle, long functionAddress) {
            destroyedTextures.add(textureObjectHandle);
            return textureDestroyStatus;
        }

        @Override
        public int cuSurfObjectDestroy(long surfaceObjectHandle, long functionAddress) {
            destroyedSurfaces.add(surfaceObjectHandle);
            return surfaceDestroyStatus;
        }
    }
}
