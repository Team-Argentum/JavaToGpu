package net.sixik.ga_utils.javatogpu.frontend.opencl;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuAttributeMetadata;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAttributeMetadata;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Projects backend-neutral JavaToGpu attribute metadata to OpenCL attribute strings.
 */
public final class OpenClAttributeProjection {

    private OpenClAttributeProjection() {
    }

    public static List<String> project(
            List<String> legacyOpenClAttributes,
            List<GpuAttributeMetadata> attributeMetadata
    ) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        for (GpuAttributeMetadata metadata : safeModelMetadata(attributeMetadata)) {
            project(metadata.kind(), metadata.value()).forEach(attributes::add);
        }
        attributes.addAll(safeStrings(legacyOpenClAttributes));
        return List.copyOf(attributes);
    }

    public static List<String> projectIr(
            List<String> legacyOpenClAttributes,
            List<IrGpuAttributeMetadata> attributeMetadata
    ) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        for (IrGpuAttributeMetadata metadata : safeIrMetadata(attributeMetadata)) {
            project(metadata.kind(), metadata.value()).forEach(attributes::add);
        }
        attributes.addAll(safeStrings(legacyOpenClAttributes));
        return List.copyOf(attributes);
    }

    private static List<String> project(String kind, String value) {
        String normalizedKind = kind == null ? "" : kind;
        String normalizedValue = value == null ? "" : value;
        return switch (normalizedKind) {
            case "packed" -> List.of("packed");
            case "aligned" -> List.of("aligned(" + normalizedValue + ")");
            case "required-work-group-size" -> List.of("reqd_work_group_size(" + dimensions(normalizedValue) + ")");
            case "work-group-size-hint" -> List.of("work_group_size_hint(" + dimensions(normalizedValue) + ")");
            case "vector-type-hint" -> normalizedValue.isBlank() ? List.of() : List.of("vec_type_hint(" + normalizedValue + ")");
            case "always-inline" -> List.of("always_inline");
            default -> List.of();
        };
    }

    private static String dimensions(String value) {
        String[] parts = value.split(",", -1);
        ArrayList<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                normalized.add(part.strip());
            }
        }
        return String.join(", ", normalized);
    }

    private static List<String> safeStrings(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static List<GpuAttributeMetadata> safeModelMetadata(List<GpuAttributeMetadata> values) {
        return values == null ? List.of() : values;
    }

    private static List<IrGpuAttributeMetadata> safeIrMetadata(List<IrGpuAttributeMetadata> values) {
        return values == null ? List.of() : values;
    }
}
