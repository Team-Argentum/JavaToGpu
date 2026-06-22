package net.sixik.ga_utils.javatogpu.frontend.parser;

import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuMethodParserTest {

    @Test
    void parsesAnnotatedMethodMetadata() {
        String methodSource = """
                @GPU
                void my_gpu_function_math(
                    @GPUGlobal(constant = true) float[] input,
                    @GPUGlobal float[] output
                ) {
                    int id = GPU.get_global_id(0);
                    float value = input[id];
                    output[id] = GPU.sin(value) * GPU.cos(value);
                }
                """;

        GpuMethodParser parser = new GpuMethodParser();
        ParsedGpuMethod method = parser.parseMethod(methodSource);

        assertEquals("my_gpu_function_math", method.name());
        assertEquals("void", method.returnType());
        assertEquals(2, method.parameters().size());
        assertEquals("input", method.parameters().get(0).name());
        assertEquals("float[]", method.parameters().get(0).javaType());
        assertEquals(GpuAddressSpace.GLOBAL, method.parameters().get(0).addressSpace());
        assertTrue(method.parameters().get(0).constant());
        assertEquals("", method.ownerSimpleName());
        assertEquals("", method.ownerQualifiedName());
    }

    @Test
    void parsesCCodeInlineFlagAndOwnerMetadata() {
        String methodSource = """
                @CCode(inline = true)
                float square(float value) {
                    return value * value;
                }
                """;

        GpuMethodParser parser = new GpuMethodParser();
        ParsedGpuMethod method = parser.parseMethod(methodSource, "Helpers", "sample.Helpers");

        assertEquals("Helpers", method.ownerSimpleName());
        assertEquals("sample.Helpers", method.ownerQualifiedName());
        assertEquals("square", method.name());
        assertEquals("float", method.returnType());
        assertTrue(method.inline());
        assertEquals(0, method.openClAttributes().size());
    }

    @Test
    void parsesNativeCCodeBodyMetadata() {
        String methodSource = "@CCode(code = \"\"\"\n"
                + "        return (*a) + (*b) * 50.0f;\n"
                + "        \"\"\")\n"
                + "native float myMath(FloatPtr a, FloatPtr b);";

        GpuMethodParser parser = new GpuMethodParser();
        ParsedGpuMethod method = parser.parseMethod(methodSource, "Helpers", "sample.Helpers");

        assertTrue(method.nativeDeclaration());
        assertEquals("return (*a) + (*b) * 50.0f;\n", method.nativeCode());
        assertEquals("myMath", method.name());
    }

    @Test
    void parsesOpenClMethodAttributes() {
        String methodSource = """
                @OpenCLAttributes({"reqd_work_group_size(16, 1, 1)", "vec_type_hint(float4)"})
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = 1.0f;
                }
                """;

        GpuMethodParser parser = new GpuMethodParser();
        ParsedGpuMethod method = parser.parseMethod(methodSource);

        assertEquals(2, method.openClAttributes().size());
        assertEquals("reqd_work_group_size(16, 1, 1)", method.openClAttributes().get(0));
        assertEquals("vec_type_hint(float4)", method.openClAttributes().get(1));
    }

    @Test
    void parsesConstantAndLocalAddressSpaces() {
        String methodSource = """
                @GPU
                void kernel(@GPUConstant float[] lookup, @GPULocal float[] scratch, @GPUGlobal float[] output) {
                    output[0] = lookup[0] + scratch[0];
                }
                """;

        GpuMethodParser parser = new GpuMethodParser();
        ParsedGpuMethod method = parser.parseMethod(methodSource);

        assertEquals(GpuAddressSpace.CONSTANT, method.parameters().get(0).addressSpace());
        assertEquals(GpuAddressSpace.LOCAL, method.parameters().get(1).addressSpace());
        assertEquals(GpuAddressSpace.GLOBAL, method.parameters().get(2).addressSpace());
    }

    @Test
    void parsesOpenClParameterQualifiers() {
        String methodSource = """
                @GPU
                void kernel(@OpenCLQualifiers({"const", "restrict", "volatile"}) @GPUGlobal float[] output) {
                    output[0] = 1.0f;
                }
                """;

        GpuMethodParser parser = new GpuMethodParser();
        ParsedGpuMethod method = parser.parseMethod(methodSource);

        assertEquals(java.util.List.of("const", "restrict", "volatile"), method.parameters().get(0).openClQualifiers());
    }

    @Test
    void rejectsMultipleAddressSpaceAnnotationsOnSingleParameter() {
        String methodSource = """
                @GPU
                void kernel(@GPUConstant @GPULocal float[] values) {
                }
                """;

        GpuMethodParser parser = new GpuMethodParser();

        assertThrows(IllegalArgumentException.class, () -> parser.parseMethod(methodSource));
    }
}
