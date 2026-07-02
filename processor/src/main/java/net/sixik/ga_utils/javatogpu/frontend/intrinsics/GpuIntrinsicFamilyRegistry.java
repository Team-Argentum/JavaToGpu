package net.sixik.ga_utils.javatogpu.frontend.intrinsics;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;
import java.util.Map;

/**
 * Deterministic registry for repetitive intrinsic families that would otherwise
 * require hundreds of hand-written declarations in the public facade.
 */
final class GpuIntrinsicFamilyRegistry {

    private static final String GPU_OWNER = "GPU";
    private static final String GPU_QUALIFIED_OWNER = "net.sixik.ga_utils.javatogpu.api.GPU";

    private static final List<String> SCALAR_CONVERSION_SOURCES = List.of(
            "byte", "short", "char", "int", "long", "float", "double",
            "UByte", "UShort", "UInt", "ULong"
    );

    private static final List<VectorFamily> INTEGER_VECTOR_FAMILIES = List.of(
            new VectorFamily("Byte", "char", List.of(2, 3, 4)),
            new VectorFamily("Short", "short", List.of(2, 3, 4)),
            new VectorFamily("Int", "int", List.of(2, 3, 4, 8, 16)),
            new VectorFamily("Long", "long", List.of(2, 3, 4)),
            new VectorFamily("UByte", "uchar", List.of(2, 3, 4, 8, 16)),
            new VectorFamily("UShort", "ushort", List.of(2, 3, 4, 8, 16)),
            new VectorFamily("UInt", "uint", List.of(2, 3, 4, 8, 16)),
            new VectorFamily("ULong", "ulong", List.of(2, 3, 4, 8, 16))
    );

    private static final List<VectorFamily> FLOATING_VECTOR_FAMILIES = List.of(
            new VectorFamily("Float", "float", List.of(2, 3, 4)),
            new VectorFamily("Double", "double", List.of(2, 3, 4))
    );

    private static final List<ConversionFamily> CONVERSION_FAMILIES = List.of(
            new ConversionFamily("convert_char", new ConversionTarget("byte", "char")),
            new ConversionFamily("convert_uchar", new ConversionTarget("UByte", "uchar")),
            new ConversionFamily("convert_short", new ConversionTarget("short", "short")),
            new ConversionFamily("convert_ushort", new ConversionTarget("UShort", "ushort")),
            new ConversionFamily("convert_int", new ConversionTarget("int", "int")),
            new ConversionFamily("convert_uint", new ConversionTarget("UInt", "uint")),
            new ConversionFamily("convert_long", new ConversionTarget("long", "long")),
            new ConversionFamily("convert_ulong", new ConversionTarget("ULong", "ulong")),
            new ConversionFamily("convert_float", new ConversionTarget("float", "float")),
            new ConversionFamily("convert_double", new ConversionTarget("double", "double")),
            new ConversionFamily("convert_char_sat", new ConversionTarget("byte", "char")),
            new ConversionFamily("convert_uchar_sat", new ConversionTarget("UByte", "uchar")),
            new ConversionFamily("convert_short_sat", new ConversionTarget("short", "short")),
            new ConversionFamily("convert_ushort_sat", new ConversionTarget("UShort", "ushort")),
            new ConversionFamily("convert_int_sat", new ConversionTarget("int", "int")),
            new ConversionFamily("convert_uint_sat", new ConversionTarget("UInt", "uint")),
            new ConversionFamily("convert_long_sat", new ConversionTarget("long", "long")),
            new ConversionFamily("convert_ulong_sat", new ConversionTarget("ULong", "ulong"))
    );

    private GpuIntrinsicFamilyRegistry() {
    }

    static void registerGeneratedFamilies(Map<String, List<GpuIntrinsic>> values, GpuBackendTarget backendTarget) {
        if (backendTarget != GpuBackendTarget.OPENCL) {
            return;
        }
        registerScalarConversionFamilies(values);
        registerFloatingVectorConversionFamilies(values);
        registerIntegerVectorCommonFamilies(values);
    }

    private static void registerScalarConversionFamilies(Map<String, List<GpuIntrinsic>> values) {
        for (ConversionFamily family : CONVERSION_FAMILIES) {
            String javaName = family.javaName();
            ConversionTarget target = family.target();
            for (String sourceType : SCALAR_CONVERSION_SOURCES) {
                register(values, intrinsic(javaName, javaName, target.javaType(), List.of(sourceType), code(javaName, 1)));
            }
        }
    }

    private static void registerFloatingVectorConversionFamilies(Map<String, List<GpuIntrinsic>> values) {
        List<VectorFamily> vectorSources = concat(INTEGER_VECTOR_FAMILIES, FLOATING_VECTOR_FAMILIES);
        for (ConversionFamily family : CONVERSION_FAMILIES) {
            String javaName = family.javaName();
            ConversionTarget target = family.target();
            for (VectorFamily source : vectorSources) {
                for (int width : source.widths()) {
                    if (!target.supportsVectorWidth(width)) {
                        continue;
                    }
                    String sourceType = source.javaType(width);
                    String resultType = target.vectorJavaType(width);
                    register(values, intrinsic(javaName, javaName, resultType, List.of(sourceType), code(javaName, 1)));
                }
            }
        }
    }

    private static void registerIntegerVectorCommonFamilies(Map<String, List<GpuIntrinsic>> values) {
        registerSameTypeBinary(values, "min");
        registerSameTypeBinary(values, "max");
        registerSameTypeTernary(values, "clamp");
        registerSameTypeBinary(values, "hadd");
        registerSameTypeBinary(values, "rhadd");
        registerSameTypeBinary(values, "mul_hi");
        registerSameTypeTernary(values, "mad_hi");
        registerSameTypeBinary(values, "add_sat");
        registerSameTypeBinary(values, "sub_sat");
        registerSameTypeBinary(values, "mul_sat");
        registerSameTypeTernary(values, "mad_sat");
        registerSameTypeBinary(values, "abs_diff");
        registerSameTypeBinary(values, "rotate");
        registerUnary(values, "clz");
        registerUnary(values, "popcount");
    }

    private static void registerSameTypeBinary(Map<String, List<GpuIntrinsic>> values, String name) {
        for (VectorFamily family : INTEGER_VECTOR_FAMILIES) {
            for (int width : family.widths()) {
                String type = family.javaType(width);
                register(values, intrinsic(name, name, type, List.of(type, type), code(name, 2)));
            }
        }
    }

    private static void registerSameTypeTernary(Map<String, List<GpuIntrinsic>> values, String name) {
        for (VectorFamily family : INTEGER_VECTOR_FAMILIES) {
            for (int width : family.widths()) {
                String type = family.javaType(width);
                register(values, intrinsic(name, name, type, List.of(type, type, type), code(name, 3)));
            }
        }
    }

    private static void registerUnary(Map<String, List<GpuIntrinsic>> values, String name) {
        for (VectorFamily family : INTEGER_VECTOR_FAMILIES) {
            for (int width : family.widths()) {
                String type = family.javaType(width);
                register(values, intrinsic(name, name, type, List.of(type), code(name, 1)));
            }
        }
    }

    private static GpuIntrinsic intrinsic(String javaName, String backendName, String resultType, List<String> argumentTypes, String codeTemplate) {
        return new GpuIntrinsic(
                GPU_OWNER,
                GPU_QUALIFIED_OWNER,
                javaName,
                argumentTypes.size(),
                false,
                inferKind(backendName),
                backendName,
                codeTemplate,
                resultType,
                argumentTypes
        );
    }

    private static GpuIntrinsicKind inferKind(String backendName) {
        if (List.of("min", "max", "clamp").contains(backendName)) {
            return GpuIntrinsicKind.COMMON;
        }
        return GpuIntrinsicKind.MATH;
    }

    private static String code(String backendName, int arity) {
        StringBuilder builder = new StringBuilder(backendName).append('(');
        for (int i = 0; i < arity; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('{').append(i).append('}');
        }
        return builder.append(')').toString();
    }

    private static void register(Map<String, List<GpuIntrinsic>> values, GpuIntrinsic intrinsic) {
        GpuIntrinsicDatabase.registerGenerated(values, intrinsic);
    }

    private static List<VectorFamily> concat(List<VectorFamily> left, List<VectorFamily> right) {
        java.util.ArrayList<VectorFamily> result = new java.util.ArrayList<>(left.size() + right.size());
        result.addAll(left);
        result.addAll(right);
        return List.copyOf(result);
    }

    private record ConversionFamily(String javaName, ConversionTarget target) {
    }

    private record ConversionTarget(String javaType, String backendType) {
        boolean supportsVectorWidth(int width) {
            return switch (backendType) {
                case "char", "short", "long", "float", "double" -> width == 2 || width == 3 || width == 4;
                case "uchar", "ushort", "int", "uint", "ulong" -> width == 2 || width == 3 || width == 4 || width == 8 || width == 16;
                default -> false;
            };
        }

        String vectorJavaType(int width) {
            return switch (javaType) {
                case "byte" -> "Byte" + width;
                case "short" -> "Short" + width;
                case "int" -> "Int" + width;
                case "long" -> "Long" + width;
                case "float" -> "Float" + width;
                case "double" -> "Double" + width;
                default -> javaType + width;
            };
        }
    }

    private record VectorFamily(String javaPrefix, String backendPrefix, List<Integer> widths) {
        String javaType(int width) {
            return javaPrefix + width;
        }
    }
}
