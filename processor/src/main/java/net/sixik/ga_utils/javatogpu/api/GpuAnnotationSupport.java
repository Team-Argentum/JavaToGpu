package net.sixik.ga_utils.javatogpu.api;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Processor-facing support for JavaToGpu canonical annotation names.
 *
 * <p>This class is public because the annotation processor, runtime ABI helpers, and optional extension modules need a
 * single source of truth for canonical annotation names. Normal application code should import concrete annotations
 * from {@code net.sixik.ga_utils.javatogpu.api.annotations} instead of depending on this support class directly.
 */
public final class GpuAnnotationSupport {

    /**
     * Canonical package that contains JavaToGpu source annotations.
     */
    public static final String CANONICAL_PACKAGE = "net.sixik.ga_utils.javatogpu.api.annotations.";

    public static final List<String> GPU_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPU");
    public static final List<String> CCODE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "CCode");
    public static final List<String> CCODE_LIBRARY_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "CCodeLibrary");
    public static final List<String> GPU_INTRINSIC_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUIntrinsic");
    public static final List<String> GPU_INTRINSIC_LIBRARY_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUIntrinsicLibrary");
    public static final List<String> GPU_CONSTANT_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUConstant");
    public static final List<String> GPU_CONSTANT_DATA_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUConstantData");
    public static final List<String> GPU_EXTERN_CONSTANT_DATA_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUExternConstantData");
    public static final List<String> GPU_GLOBAL_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUGlobal");
    public static final List<String> GPU_LOCAL_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPULocal");
    public static final List<String> GPU_STRUCT_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUStruct");
    public static final List<String> GPU_ATTRIBUTE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUAttribute");
    public static final List<String> GPU_ATTRIBUTES_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUAttributes");
    public static final List<String> GPU_ALIGNED_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUAligned");
    public static final List<String> GPU_ALWAYS_INLINE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUAlwaysInline");
    public static final List<String> GPU_PACKED_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUPacked");
    public static final List<String> GPU_VECTOR_TYPE_HINT_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUVectorTypeHint");
    public static final List<String> GPU_WORK_GROUP_SIZE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUWorkGroupSize");
    public static final List<String> GPU_WORK_GROUP_SIZE_HINT_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUWorkGroupSizeHint");
    public static final List<String> GPU_OPTIMIZE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUOptimize");
    public static final List<String> OPENCL_ATTRIBUTES_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "OpenCLAttributes");
    public static final List<String> OPENCL_QUALIFIERS_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "OpenCLQualifiers");
    public static final List<String> GPU_POINTER_TYPE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUPointerType");
    public static final List<String> GPU_VECTOR_TYPE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUVectorType");
    public static final List<String> GPU_SCALAR_ALIAS_TYPE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUScalarAliasType");

    private GpuAnnotationSupport() {
    }

    /**
     * Returns {@code true} when {@code type} has at least one annotation whose fully qualified name is listed in
     * {@code annotationTypeNames}.
     *
     * <p>Name-based matching keeps processor/runtime support resilient when optional modules are compiled against the
     * public annotation contract but loaded through different class paths.
     */
    public static boolean hasAnnotation(Class<?> type, List<String> annotationTypeNames) {
        for (Annotation annotation : type.getAnnotations()) {
            if (annotationTypeNames.contains(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }
}
