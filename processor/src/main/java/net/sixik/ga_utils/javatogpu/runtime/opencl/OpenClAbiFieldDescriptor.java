package net.sixik.ga_utils.javatogpu.runtime.opencl;

record OpenClAbiFieldDescriptor(
        String name,
        String typeName,
        int offset,
        int size,
        int alignment,
        OpenClAbiDescriptor nestedDescriptor
) {
}
