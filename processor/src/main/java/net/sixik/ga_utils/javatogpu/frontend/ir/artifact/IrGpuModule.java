package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

public record IrGpuModule(
        String entryMethod,
        String entryEmittedName,
        List<IrGpuModuleMethod> helperMethods,
        List<String> structs,
        List<IrGpuMethodBody> methodBodies
) {

    public IrGpuModule(
            String entryMethod,
            String entryEmittedName,
            List<IrGpuModuleMethod> helperMethods,
            List<String> structs
    ) {
        this(entryMethod, entryEmittedName, helperMethods, structs, List.of());
    }

    public IrGpuModule {
        helperMethods = helperMethods == null ? List.of() : List.copyOf(helperMethods);
        structs = structs == null ? List.of() : List.copyOf(structs);
        methodBodies = methodBodies == null ? List.of() : List.copyOf(methodBodies);
    }
}
