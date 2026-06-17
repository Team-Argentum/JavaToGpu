package net.sixik.ga_utils.javatogpu.frontend.intrinsics;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GpuIntrinsicDatabase {

    private final Map<String, List<GpuIntrinsic>> intrinsics;
    private final Set<String> allowedAllocationTypes;

    private GpuIntrinsicDatabase(Map<String, List<GpuIntrinsic>> intrinsics, Set<String> allowedAllocationTypes) {
        this.intrinsics = intrinsics;
        this.allowedAllocationTypes = allowedAllocationTypes;
    }

    public static GpuIntrinsicDatabase createDefault() {
        Map<String, List<GpuIntrinsic>> values = new HashMap<>();
        register(values, new GpuIntrinsic("GPU", "sin", 1, GpuIntrinsicKind.MATH, "sin", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "sin", 1, GpuIntrinsicKind.MATH, "sin", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "cos", 1, GpuIntrinsicKind.MATH, "cos", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "cos", 1, GpuIntrinsicKind.MATH, "cos", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "tan", 1, GpuIntrinsicKind.MATH, "tan", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "tan", 1, GpuIntrinsicKind.MATH, "tan", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "sqrt", 1, GpuIntrinsicKind.MATH, "sqrt", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "sqrt", 1, GpuIntrinsicKind.MATH, "sqrt", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "exp", 1, GpuIntrinsicKind.MATH, "exp", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "exp", 1, GpuIntrinsicKind.MATH, "exp", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "log", 1, GpuIntrinsicKind.MATH, "log", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "log", 1, GpuIntrinsicKind.MATH, "log", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "fabs", 1, GpuIntrinsicKind.MATH, "fabs", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "fabs", 1, GpuIntrinsicKind.MATH, "fabs", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "abs", 1, GpuIntrinsicKind.MATH, "fabs", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "abs", 1, GpuIntrinsicKind.MATH, "fabs", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "floor", 1, GpuIntrinsicKind.MATH, "floor", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "floor", 1, GpuIntrinsicKind.MATH, "floor", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "ceil", 1, GpuIntrinsicKind.MATH, "ceil", "float", List.of("float")));
        register(values, new GpuIntrinsic("GPU", "ceil", 1, GpuIntrinsicKind.MATH, "ceil", "double", List.of("double")));
        register(values, new GpuIntrinsic("GPU", "pow", 2, GpuIntrinsicKind.MATH, "pow", "float", List.of("float", "float")));
        register(values, new GpuIntrinsic("GPU", "pow", 2, GpuIntrinsicKind.MATH, "pow", "double", List.of("double", "double")));
        register(values, new GpuIntrinsic("GPU", "min", 2, GpuIntrinsicKind.MATH, "min", "float", List.of("float", "float")));
        register(values, new GpuIntrinsic("GPU", "min", 2, GpuIntrinsicKind.MATH, "min", "double", List.of("double", "double")));
        register(values, new GpuIntrinsic("GPU", "max", 2, GpuIntrinsicKind.MATH, "max", "float", List.of("float", "float")));
        register(values, new GpuIntrinsic("GPU", "max", 2, GpuIntrinsicKind.MATH, "max", "double", List.of("double", "double")));
        register(values, new GpuIntrinsic("GPU", "get_global_id", 1, GpuIntrinsicKind.BUILTIN_ID, "get_global_id", "int", List.of("int")));

        return new GpuIntrinsicDatabase(
                values.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                )),
                Set.of("BytePtr", "CharPtr", "ShortPtr", "IntPtr", "LongPtr", "FloatPtr", "DoublePtr")
        );
    }

    public GpuIntrinsic require(String owner, String javaName, int arity) {
        List<GpuIntrinsic> matches = intrinsics.get(key(owner, javaName, arity));
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown GPU intrinsic: " + owner + "." + javaName + "/" + arity);
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Ambiguous GPU intrinsic overload: " + owner + "." + javaName + "/" + arity);
        }
        return matches.get(0);
    }

    public GpuIntrinsic require(String owner, String javaName, List<String> argumentTypes) {
        List<GpuIntrinsic> matches = intrinsics.get(key(owner, javaName, argumentTypes.size()));
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown GPU intrinsic: " + owner + "." + javaName + "/" + argumentTypes.size());
        }

        return matches.stream()
                .filter(intrinsic -> intrinsic.argumentTypes().equals(argumentTypes))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown GPU intrinsic overload: " + owner + "." + javaName + argumentTypes
                ));
    }

    public boolean isAllowedOwner(String owner) {
        return intrinsics.keySet().stream().anyMatch(key -> key.startsWith(owner + "#"));
    }

    public boolean isAllowedAllocationType(String typeName) {
        return allowedAllocationTypes.contains(typeName);
    }

    private static void register(Map<String, List<GpuIntrinsic>> values, GpuIntrinsic intrinsic) {
        values.computeIfAbsent(key(intrinsic.owner(), intrinsic.javaName(), intrinsic.arity()), ignored -> new ArrayList<>())
                .add(intrinsic);
    }

    private static String key(String owner, String javaName, int arity) {
        return owner + "#" + javaName + "#" + arity;
    }
}
