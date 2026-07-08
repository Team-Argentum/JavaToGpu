package net.sixik.ga_utils.javatogpu.frontend.asm;

import net.sixik.ga_utils.javatogpu.api.BytePtr;
import net.sixik.ga_utils.javatogpu.api.CharPtr;
import net.sixik.ga_utils.javatogpu.api.DoublePtr;
import net.sixik.ga_utils.javatogpu.api.FloatPtr;
import net.sixik.ga_utils.javatogpu.api.GPU;
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
import net.sixik.ga_utils.javatogpu.api.Image2DMsaaReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DMsaaWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.IntPtr;
import net.sixik.ga_utils.javatogpu.api.LongPtr;
import net.sixik.ga_utils.javatogpu.api.Sampler;
import net.sixik.ga_utils.javatogpu.api.ShortPtr;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;
import org.objectweb.asm.Type;

import java.util.Set;

/**
 * Shared GPU-safe ASM type and owner rules used by validation and diagnostics.
 */
final class AsmGpuTypeRules {

    static final String GPU_OWNER = Type.getInternalName(GPU.class);

    private static final Set<String> BUILTIN_CONSTRUCTOR_OWNERS = Set.of(
            Type.getInternalName(BytePtr.class),
            Type.getInternalName(CharPtr.class),
            Type.getInternalName(ShortPtr.class),
            Type.getInternalName(IntPtr.class),
            Type.getInternalName(LongPtr.class),
            Type.getInternalName(FloatPtr.class),
            Type.getInternalName(DoublePtr.class)
    );

    private static final Set<String> BUILTIN_VALUE_OWNERS = Set.of(
            Type.getInternalName(BytePtr.class),
            Type.getInternalName(CharPtr.class),
            Type.getInternalName(ShortPtr.class),
            Type.getInternalName(IntPtr.class),
            Type.getInternalName(LongPtr.class),
            Type.getInternalName(FloatPtr.class),
            Type.getInternalName(DoublePtr.class),
            Type.getInternalName(Image1DReadOnly.class),
            Type.getInternalName(Image1DWriteOnly.class),
            Type.getInternalName(Image1DArrayReadOnly.class),
            Type.getInternalName(Image1DArrayWriteOnly.class),
            Type.getInternalName(Image1DBufferReadOnly.class),
            Type.getInternalName(Image1DBufferWriteOnly.class),
            Type.getInternalName(Image2DReadOnly.class),
            Type.getInternalName(Image2DWriteOnly.class),
            Type.getInternalName(Image2DMipmappedReadOnly.class),
            Type.getInternalName(Image2DMipmappedWriteOnly.class),
            Type.getInternalName(Image2DMsaaReadOnly.class),
            Type.getInternalName(Image2DMsaaWriteOnly.class),
            Type.getInternalName(Image2DArrayReadOnly.class),
            Type.getInternalName(Image2DArrayWriteOnly.class),
            Type.getInternalName(Image3DReadOnly.class),
            Type.getInternalName(Image3DWriteOnly.class),
            Type.getInternalName(Sampler.class)
    );

    private AsmGpuTypeRules() {
    }

    static boolean isAllowedConstructorOwner(String ownerInternalName, AsmValidationConfig config) {
        return BUILTIN_CONSTRUCTOR_OWNERS.contains(ownerInternalName)
                || isBuiltInPointerOrScalarAliasOwner(ownerInternalName)
                || isBuiltInVectorOwner(ownerInternalName)
                || config.allowedStructOwners().contains(ownerInternalName);
    }

    static boolean isAllowedFieldOwner(String ownerInternalName, AsmValidationConfig config) {
        return isAllowedConstructorOwner(ownerInternalName, config);
    }

    static boolean isSupportedValueType(Type type, AsmValidationConfig config, boolean allowArrays) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE -> true;
            case Type.ARRAY -> allowArrays && isSupportedArrayType(type, config);
            case Type.OBJECT -> isSupportedObjectType(type.getInternalName(), config);
            default -> false;
        };
    }

    static boolean isSupportedArrayType(Type arrayType, AsmValidationConfig config) {
        if (arrayType.getDimensions() != 1) {
            return false;
        }
        Type elementType = arrayType.getElementType();
        return switch (elementType.getSort()) {
            case Type.BYTE, Type.CHAR, Type.SHORT, Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE -> true;
            case Type.OBJECT -> isAllowedArrayObjectElementOwner(elementType.getInternalName(), config);
            default -> false;
        };
    }

    static boolean isSupportedObjectType(String ownerInternalName, AsmValidationConfig config) {
        return BUILTIN_VALUE_OWNERS.contains(ownerInternalName)
                || isBuiltInPointerOrScalarAliasOwner(ownerInternalName)
                || isBuiltInVectorOwner(ownerInternalName)
                || config.allowedStructOwners().contains(ownerInternalName);
    }

    static boolean isAllowedArrayObjectElementOwner(String ownerInternalName, AsmValidationConfig config) {
        return BUILTIN_CONSTRUCTOR_OWNERS.contains(ownerInternalName)
                || isBuiltInPointerOrScalarAliasOwner(ownerInternalName)
                || isBuiltInVectorOwner(ownerInternalName)
                || config.allowedStructOwners().contains(ownerInternalName);
    }

    private static boolean isBuiltInPointerOrScalarAliasOwner(String ownerInternalName) {
        String className = ownerInternalName.replace('/', '.');
        return GpuTypeSupport.isSupportedPointerClassName(className)
                || GpuTypeSupport.isSupportedScalarAliasClassName(className);
    }

    private static boolean isBuiltInVectorOwner(String ownerInternalName) {
        return GpuTypeSupport.isSupportedVectorClassName(ownerInternalName.replace('/', '.'));
    }
}
