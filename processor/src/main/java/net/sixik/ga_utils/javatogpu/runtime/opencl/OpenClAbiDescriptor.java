package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

record OpenClAbiDescriptor(
        String typeName,
        OpenClAbiKind kind,
        int size,
        int alignment,
        List<OpenClAbiFieldDescriptor> fields
) {
}
