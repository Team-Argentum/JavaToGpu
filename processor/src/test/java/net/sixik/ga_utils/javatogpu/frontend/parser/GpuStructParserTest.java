package net.sixik.ga_utils.javatogpu.frontend.parser;

import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuStructParserTest {

    @Test
    void parsesPortablePackedAndAlignedStructAttributes() {
        String structSource = """
                @GPUPacked
                @GPUAligned(16)
                @GPUStruct
                class Pair {
                    float x;
                    float y;
                }
                """;

        GpuStructParser parser = new GpuStructParser();
        ParsedGpuStruct struct = parser.parseStruct(structSource);

        assertEquals(java.util.List.of("packed", "aligned(16)"), struct.openClAttributes());
    }

    @Test
    void parsesPortableAlignedStructFieldAttribute() {
        String structSource = """
                @GPUStruct
                class Pair {
                    @GPUAligned(8)
                    float x;
                    float y;
                }
                """;

        GpuStructParser parser = new GpuStructParser();
        ParsedGpuStruct struct = parser.parseStruct(structSource);

        assertEquals(java.util.List.of("aligned(8)"), struct.fields().get(0).openClAttributes());
        assertEquals(java.util.List.of(), struct.fields().get(1).openClAttributes());
    }
}
