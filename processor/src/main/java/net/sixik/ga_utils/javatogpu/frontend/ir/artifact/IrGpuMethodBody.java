package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

public record IrGpuMethodBody(
        String role,
        String name,
        String emittedName,
        String format,
        String body,
        List<String> helperDependencies,
        IrGpuSourceLocation sourceLocation
) {

    public IrGpuMethodBody(
            String role,
            String name,
            String emittedName,
            String format,
            String body,
            List<String> helperDependencies
    ) {
        this(role, name, emittedName, format, body, helperDependencies, IrGpuSourceLocation.unknown(name));
    }

    public IrGpuMethodBody {
        role = normalize(role, "helper");
        name = normalize(name, "");
        emittedName = normalize(emittedName, "");
        format = normalize(format, "ir-text-v1");
        body = body == null ? "" : body;
        helperDependencies = helperDependencies == null ? List.of() : List.copyOf(helperDependencies);
        sourceLocation = sourceLocation == null ? IrGpuSourceLocation.unknown(name) : sourceLocation;
    }

    public static IrGpuMethodBody entry(String name, String emittedName, String body, List<String> helperDependencies) {
        return entry(name, emittedName, body, helperDependencies, IrGpuSourceLocation.unknown(name));
    }

    public static IrGpuMethodBody entry(
            String name,
            String emittedName,
            String body,
            List<String> helperDependencies,
            IrGpuSourceLocation sourceLocation
    ) {
        return new IrGpuMethodBody("entry", name, emittedName, "ir-text-v1", body, helperDependencies, sourceLocation);
    }

    public static IrGpuMethodBody helper(String name, String emittedName, String body, List<String> helperDependencies) {
        return helper(name, emittedName, body, helperDependencies, IrGpuSourceLocation.unknown(name));
    }

    public static IrGpuMethodBody helper(
            String name,
            String emittedName,
            String body,
            List<String> helperDependencies,
            IrGpuSourceLocation sourceLocation
    ) {
        return new IrGpuMethodBody("helper", name, emittedName, "ir-text-v1", body, helperDependencies, sourceLocation);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
