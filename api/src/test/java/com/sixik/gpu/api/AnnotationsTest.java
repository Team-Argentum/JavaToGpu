package com.sixik.gpu.api;

import com.sixik.gpu.api.annotations.Constant;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Local;
import com.sixik.gpu.api.annotations.Struct;
import com.sixik.gpu.api.annotations.WorkGroup;
import com.sixik.gpu.api.enums.InlinePolicy;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Метаданные и значения по умолчанию аннотаций языка. */
class AnnotationsTest {

    private static final List<Class<? extends java.lang.annotation.Annotation>> ALL =
            List.of(Kernel.class, DeviceFunction.class, Struct.class, Global.class, Constant.class, WorkGroup.class,
                    Local.class);

    @Test
    void retainedInClassFilesForWeaver() {
        // weaver читает аннотации из байткода (ASM): SOURCE их потерял бы, RUNTIME не нужен
        for (var a : ALL) {
            assertEquals(RetentionPolicy.CLASS, a.getAnnotation(Retention.class).value(), a.getSimpleName());
        }
    }

    @Test
    void targets() {
        assertArrayEquals(new ElementType[]{ElementType.METHOD}, Kernel.class.getAnnotation(Target.class).value());
        assertArrayEquals(new ElementType[]{ElementType.METHOD}, DeviceFunction.class.getAnnotation(Target.class).value());
        assertArrayEquals(new ElementType[]{ElementType.METHOD}, WorkGroup.class.getAnnotation(Target.class).value());
        assertArrayEquals(new ElementType[]{ElementType.TYPE}, Struct.class.getAnnotation(Target.class).value());
        assertArrayEquals(new ElementType[]{ElementType.PARAMETER}, Global.class.getAnnotation(Target.class).value());
        assertArrayEquals(new ElementType[]{ElementType.FIELD}, Constant.class.getAnnotation(Target.class).value());
        assertEquals(java.util.Set.of(ElementType.LOCAL_VARIABLE, ElementType.PARAMETER),
                java.util.Set.of(Local.class.getAnnotation(Target.class).value()));
    }

    /** Образец применения {@link Local}: компилируется (локальный массив в ядре и {@code __local}-параметр). */
    @SuppressWarnings("unused")
    private static final class LocalUsage {
        @Kernel
        static void tiled(@Global float[] in, @Global float[] out, @Local float[] scratch) {
            @Local float[] tile = new float[256];
            tile[Gpu.getLocalId(0)] = in[Gpu.getGlobalId(0)];
            scratch[0] = tile[0];
            out[Gpu.getGlobalId(0)] = tile[Gpu.getLocalId(0)];
        }
    }

    @Test
    void defaults() throws NoSuchMethodException {
        assertEquals(InlinePolicy.DEFAULT, Kernel.class.getMethod("inline").getDefaultValue());
        assertEquals(0, Kernel.class.getMethod("optLevel").getDefaultValue());
        assertEquals(0, Kernel.class.getMethod("workgroup").getDefaultValue());
        assertArrayEquals(new String[0], (String[]) Kernel.class.getMethod("required").getDefaultValue());
        assertEquals("", Kernel.class.getMethod("fallback").getDefaultValue());
        assertEquals(0, Struct.class.getMethod("alignment").getDefaultValue());
        assertEquals(false, Global.class.getMethod("constant").getDefaultValue());
        for (String axis : new String[]{"x", "y", "z"}) {
            assertEquals(1, WorkGroup.class.getMethod(axis).getDefaultValue(), "WorkGroup." + axis);
        }
    }

    @Test
    void inlinePolicyOrderMatchesIrAttributeValues() {
        // в IR: INLINE_DEFAULT = 0, INLINE_ALWAYS = 1, INLINE_NEVER = 2 (IrModule); порядок констант - часть контракта
        assertEquals(0, InlinePolicy.DEFAULT.ordinal());
        assertEquals(1, InlinePolicy.ALWAYS.ordinal());
        assertEquals(2, InlinePolicy.NEVER.ordinal());
    }
}
