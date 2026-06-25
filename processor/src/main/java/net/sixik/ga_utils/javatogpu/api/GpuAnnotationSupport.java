package net.sixik.ga_utils.javatogpu.api;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Shared support for JavaToGpu canonical annotations.
 */
public final class GpuAnnotationSupport {

    public static final String CANONICAL_PACKAGE = "net.sixik.ga_utils.javatogpu.api.annotations.";

    public static final List<String> GPU_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPU");
    public static final List<String> CCODE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "CCode");
    public static final List<String> CCODE_LIBRARY_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "CCodeLibrary");
    public static final List<String> GPU_INTRINSIC_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUIntrinsic");
    public static final List<String> GPU_INTRINSIC_LIBRARY_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUIntrinsicLibrary");
    public static final List<String> GPU_CONSTANT_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUConstant");
    public static final List<String> GPU_GLOBAL_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUGlobal");
    public static final List<String> GPU_LOCAL_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPULocal");
    public static final List<String> GPU_STRUCT_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUStruct");
    public static final List<String> OPENCL_ATTRIBUTES_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "OpenCLAttributes");
    public static final List<String> OPENCL_QUALIFIERS_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "OpenCLQualifiers");
    public static final List<String> GPU_POINTER_TYPE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUPointerType");
    public static final List<String> GPU_VECTOR_TYPE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUVectorType");
    public static final List<String> GPU_SCALAR_ALIAS_TYPE_ANNOTATION_TYPES = List.of(CANONICAL_PACKAGE + "GPUScalarAliasType");

    private GpuAnnotationSupport() {
    }

    public static boolean hasAnnotation(Class<?> type, List<String> annotationTypeNames) {
        for (Annotation annotation : type.getAnnotations()) {
            if (annotationTypeNames.contains(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }
}
