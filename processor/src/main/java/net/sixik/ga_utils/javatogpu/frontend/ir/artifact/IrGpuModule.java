package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

public record IrGpuModule(
        String entryMethod,
        String entryEmittedName,
        List<String> entryOpenClAttributes,
        List<IrGpuAttributeMetadata> entryAttributeMetadata,
        List<IrGpuModuleMethod> helperMethods,
        List<String> structs,
        List<IrGpuMethodBody> methodBodies
) {

    public IrGpuModule(
            String entryMethod,
            String entryEmittedName,
            List<String> entryOpenClAttributes,
            List<IrGpuModuleMethod> helperMethods,
            List<String> structs,
            List<IrGpuMethodBody> methodBodies
    ) {
        this(entryMethod, entryEmittedName, entryOpenClAttributes, List.of(), helperMethods, structs, methodBodies);
    }

    public IrGpuModule(
            String entryMethod,
            String entryEmittedName,
            List<IrGpuModuleMethod> helperMethods,
            List<String> structs,
            List<IrGpuMethodBody> methodBodies
    ) {
        this(entryMethod, entryEmittedName, List.of(), List.of(), helperMethods, structs, methodBodies);
    }

    public IrGpuModule(
            String entryMethod,
            String entryEmittedName,
            List<IrGpuModuleMethod> helperMethods,
            List<String> structs
    ) {
        this(entryMethod, entryEmittedName, List.of(), List.of(), helperMethods, structs, List.of());
    }

    public IrGpuModule {
        entryOpenClAttributes = entryOpenClAttributes == null ? List.of() : List.copyOf(entryOpenClAttributes);
        entryAttributeMetadata = entryAttributeMetadata == null ? List.of() : List.copyOf(entryAttributeMetadata);
        helperMethods = helperMethods == null ? List.of() : List.copyOf(helperMethods);
        structs = structs == null ? List.of() : List.copyOf(structs);
        methodBodies = methodBodies == null ? List.of() : List.copyOf(methodBodies);
    }
}
