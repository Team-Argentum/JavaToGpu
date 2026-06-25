package net.sixik.ga_utils.javatogpu.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuApiResourceLifecycleTest {

    @Test
    void borrowedSamplerCloseInvalidatesWrapperWithoutOwningNativeResource() {
        Sampler sampler = Sampler.borrowed(123L);

        assertTrue(sampler.isValid());
        assertTrue(!sampler.closed());
        assertFalse(sampler.owned());

        sampler.close();

        assertFalse(sampler.isValid());
        assertTrue(sampler.closed());

        sampler.close();

        assertFalse(sampler.isValid());
        assertTrue(sampler.closed());
    }

    @Test
    void borrowedImagesBecomeInvalidAfterCloseAcrossWrapperFamilies() {
        Image2DReadOnly image2d = Image2DReadOnly.borrowed(101L, 4, 2);
        Image2DMsaaReadOnly image2dMsaa = Image2DMsaaReadOnly.borrowed(111L, 4, 2, 4);
        Image3DWriteOnly image3d = Image3DWriteOnly.borrowed(202L, 4, 2, 3);
        Image1DBufferReadOnly image1dBuffer = Image1DBufferReadOnly.borrowed(303L, 8);

        image2d.close();
        image2dMsaa.close();
        image3d.close();
        image1dBuffer.close();

        assertTrue(image2d.closed());
        assertFalse(image2d.isValid());
        assertTrue(image2dMsaa.closed());
        assertFalse(image2dMsaa.isValid());
        assertTrue(image3d.closed());
        assertFalse(image3d.isValid());
        assertTrue(image1dBuffer.closed());
        assertFalse(image1dBuffer.isValid());
    }

    @Test
    void ownedWrapperWithZeroHandleStillTransitionsToClosedState() {
        Image2DWriteOnly image = Image2DWriteOnly.owned(0L, 1, 1);

        assertFalse(image.isValid());
        assertTrue(!image.closed());

        image.close();

        assertTrue(image.closed());
        assertFalse(image.isValid());
    }
}
