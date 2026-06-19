package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.api.anotations.OpenCLAttributes;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClAbiSupportTest {

    @Test
    void describesVectorTypeLayout() {
        OpenClAbiDescriptor descriptor = OpenClAbiSupport.describeVectorType("Float3");

        assertEquals(OpenClAbiKind.VECTOR, descriptor.kind());
        assertEquals(16, descriptor.size());
        assertEquals(16, descriptor.alignment());
        assertEquals(3, descriptor.fields().size());
        assertEquals("x", descriptor.fields().get(0).name());
        assertEquals(0, descriptor.fields().get(0).offset());
        assertEquals(4, descriptor.fields().get(1).offset());
        assertEquals(8, descriptor.fields().get(2).offset());
    }

    @Test
    void describesStructTypeLayoutAndDebugString() {
        OpenClAbiDescriptor descriptor = OpenClAbiSupport.describeStructType(PackedSample.class);
        String debug = OpenClAbiSupport.debugDescriptor(descriptor);

        assertEquals(OpenClAbiKind.STRUCT, descriptor.kind());
        assertEquals(16, descriptor.size());
        assertEquals(8, descriptor.alignment());
        assertEquals(3, descriptor.fields().size());
        assertEquals("inner", descriptor.fields().get(0).name());
        assertEquals(0, descriptor.fields().get(0).offset());
        assertEquals(8, descriptor.fields().get(1).offset());
        assertEquals(12, descriptor.fields().get(2).offset());
        assertTrue(debug.contains("PackedSample [STRUCT] size=16, align=8"));
        assertTrue(debug.contains("bias @8 size=4, align=8"));
        assertTrue(debug.contains("InnerSample [STRUCT] size=8, align=4"));
    }

    @Test
    void describesInvocationArgumentsForAbiDebug() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "inline://debug",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("sample", PackedSample.class.getName(), GpuKernelParameterAccess.VALUE)
                )
        );

        String debug = OpenClAbiDebug.describeInvocation(descriptor, new Object[]{new PackedSample()});

        assertTrue(debug.contains("OpenCL ABI debug for kernel kernel"));
        assertTrue(debug.contains("sample : " + PackedSample.class.getName()));
        assertTrue(debug.contains("PackedSample [STRUCT] size=16, align=8"));
    }

    @Test
    void rejectsStructArrayReadbackWhenElementTypeHasNoNoArgConstructor() {
        NoDefaultConstructorSample[] target = new NoDefaultConstructorSample[1];
        ByteBuffer buffer = OpenClValuePacker.packStructArray(new NoDefaultConstructorSample[]{
                new NoDefaultConstructorSample(1.5f)
        });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClValuePacker.unpackStructArray(buffer, target)
        );

        assertTrue(exception.getMessage().contains(
                "Type requires an accessible no-arg constructor for OpenCL readback: "
                        + NoDefaultConstructorSample.class.getName()
        ));
    }

    @GPUStruct
    static final class InnerSample {
        int x;
        float y;

        InnerSample() {
        }
    }

    @GPUStruct
    @OpenCLAttributes({"packed"})
    static final class PackedSample {
        InnerSample inner;
        @OpenCLAttributes({"aligned(8)"})
        float bias;
        int count;

        PackedSample() {
        }
    }

    @GPUStruct
    static final class NoDefaultConstructorSample {
        float value;

        NoDefaultConstructorSample(float value) {
            this.value = value;
        }
    }
}
