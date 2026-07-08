package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

/**
 * Backend-neutral metadata for one helper method referenced by an IrGpu module.
 */
public record IrGpuModuleMethod(
        String name,
        String emittedName,
        String returnType,
        List<IrGpuEntryParameter> parameters,
        List<String> openClAttributes,
        boolean inline
) {

    public IrGpuModuleMethod(String name, String emittedName) {
        this(name, emittedName, "unknown", List.of(), List.of(), false);
    }

    public IrGpuModuleMethod(
            String name,
            String emittedName,
            String returnType,
            List<IrGpuEntryParameter> parameters
    ) {
        this(name, emittedName, returnType, parameters, List.of(), false);
    }

    public IrGpuModuleMethod(
            String name,
            String emittedName,
            String returnType,
            List<IrGpuEntryParameter> parameters,
            List<String> openClAttributes
    ) {
        this(name, emittedName, returnType, parameters, openClAttributes, false);
    }

    public IrGpuModuleMethod {
        name = normalize(name, "");
        emittedName = normalize(emittedName, "");
        returnType = normalize(returnType, "unknown");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        openClAttributes = openClAttributes == null ? List.of() : List.copyOf(openClAttributes);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
