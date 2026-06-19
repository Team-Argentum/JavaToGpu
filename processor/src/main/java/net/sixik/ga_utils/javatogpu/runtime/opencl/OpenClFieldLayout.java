package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.lang.reflect.Field;

record OpenClFieldLayout(
        Field field,
        int offset,
        int storageSize,
        int alignment,
        OpenClValueLayout layout
) {
}
