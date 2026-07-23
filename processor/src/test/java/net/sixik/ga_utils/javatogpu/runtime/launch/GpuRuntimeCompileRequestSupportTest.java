package net.sixik.ga_utils.javatogpu.runtime.launch;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequestFactory;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class GpuRuntimeCompileRequestSupportTest {

    @Test
    void rootCompileRequestFactoryDelegatesDescriptorConstructionToLaunchSupport() {
        GpuKernelDescriptor descriptor = descriptor();
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL);
        GpuRuntimeDeviceProfile profile = profile();

        GpuRuntimeCompileRequest rootRequest = GpuRuntimeCompileRequestFactory.fromDescriptor(
                descriptor,
                options,
                profile
        );
        GpuRuntimeCompileRequest supportRequest = GpuRuntimeCompileRequestSupport.fromDescriptor(
                descriptor,
                options,
                profile
        );

        assertSame(descriptor, rootRequest.descriptor());
        assertSame(options, rootRequest.options());
        assertSame(profile, rootRequest.deviceProfile());
        assertEquals(supportRequest, rootRequest);
    }

    @Test
    void invocationConstructionDefaultsOptionsFromDeviceProfileWhenMissing() {
        GpuKernelDescriptor descriptor = descriptor();
        GpuRuntimeDeviceProfile profile = profile();
        GpuKernelInvocation invocation = new GpuKernelInvocation(
                descriptor,
                new Object[]{new float[4]},
                (GpuRuntimeCompileOptions) null
        );

        GpuRuntimeCompileRequest request = GpuRuntimeCompileRequestSupport.fromInvocation(invocation, profile);

        assertSame(descriptor, request.descriptor());
        assertSame(profile, request.deviceProfile());
        assertEquals(GpuBackendTarget.OPENCL, request.options().backendTarget());
    }

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel(__global float* output) {}",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                List.of()
        );
    }

    private static GpuRuntimeDeviceProfile profile() {
        return new GpuRuntimeDeviceProfile(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "Fake GPU",
                "NVIDIA",
                "mock-driver",
                "OpenCL 3.0"
        );
    }
}
