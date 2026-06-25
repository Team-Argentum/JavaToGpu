package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.Float3;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes;
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
        assertTrue(exception.getMessage().contains("add a default constructor"));
    }

    @Test
    void rejectsNonStructAbiTypeWithQuickFixHint() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClAbiSupport.describeStructType(String.class)
        );

        assertTrue(exception.getMessage().contains("Unsupported OpenCL struct argument type: java.lang.String"));
        assertTrue(exception.getMessage().contains("mark the type with @GPUStruct"));
    }

    @Test
    void rejectsUnsupportedStructFieldTypeWithQuickFixHint() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClAbiSupport.describeStructType(UnsupportedFieldSample.class)
        );

        assertTrue(exception.getMessage().contains("Unsupported OpenCL field type for ABI marshalling: java.lang.String"));
        assertTrue(exception.getMessage().contains("primitive fields, supported vector fields, or nested @GPUStruct values"));
    }

    @Test
    void rejectsUnsupportedStructArrayTypeWithQuickFixHint() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClAbiSupport.structArrayByteSize(new String[]{"bad"})
        );

        assertTrue(exception.getMessage().contains("Unsupported OpenCL struct array type"));
        assertTrue(exception.getMessage().contains("use @GPUStruct[] arrays"));
    }

    @Test
    void rejectsUnsupportedVectorArrayTypeWithQuickFixHint() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClAbiSupport.vectorArrayByteSize(new String[]{"bad"})
        );

        assertTrue(exception.getMessage().contains("Unsupported OpenCL vector array type"));
        assertTrue(exception.getMessage().contains("Float2, Int4, or UInt16"));
    }

    @Test
    void describesAlignedStructWithVectorFieldLayout() {
        OpenClAbiDescriptor descriptor = OpenClAbiSupport.describeStructType(AlignedVectorSample.class);
        String debug = OpenClAbiSupport.debugDescriptor(descriptor);

        assertEquals(OpenClAbiKind.STRUCT, descriptor.kind());
        assertEquals(32, descriptor.size());
        assertEquals(32, descriptor.alignment());
        assertEquals(2, descriptor.fields().size());
        assertEquals("normal", descriptor.fields().get(0).name());
        assertEquals(0, descriptor.fields().get(0).offset());
        assertEquals(16, descriptor.fields().get(0).size());
        assertEquals("weight", descriptor.fields().get(1).name());
        assertEquals(16, descriptor.fields().get(1).offset());
        assertEquals(8, descriptor.fields().get(1).size());
        assertTrue(debug.contains("AlignedVectorSample [STRUCT] size=32, align=32"));
        assertTrue(debug.contains("normal @0 size=16, align=16, type=" + Float3.class.getName()));
        assertTrue(debug.contains("weight @16 size=8, align=16"));
    }

    @Test
    void describesCanonicalStructAnnotationPackageForAbiCompatibility() {
        OpenClAbiDescriptor descriptor = OpenClAbiSupport.describeStructType(CanonicalStructSample.class);

        assertEquals(OpenClAbiKind.STRUCT, descriptor.kind());
        assertEquals(8, descriptor.size());
        assertEquals(4, descriptor.alignment());
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

    @GPUStruct
    @OpenCLAttributes({"aligned(32)"})
    static final class AlignedVectorSample {
        Float3 normal;
        @OpenCLAttributes({"aligned(16)"})
        double weight;

        AlignedVectorSample() {
        }
    }

    @GPUStruct
    static final class UnsupportedFieldSample {
        String label;

        UnsupportedFieldSample() {
        }
    }

    @net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct
    static final class CanonicalStructSample {
        int x;
        float y;

        CanonicalStructSample() {
        }
    }
}
