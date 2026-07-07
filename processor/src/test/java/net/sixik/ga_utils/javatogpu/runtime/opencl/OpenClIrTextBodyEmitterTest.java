package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClIrTextBodyEmitterTest {

    @Test
    void emitsSimpleFlatOpenClStatements() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  var int id = get_global_id(0)
                  set output[id] = input[id]
                  return
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(emission.emitted());
        assertTrue(emission.blockers().isEmpty());
        assertEquals("""
                    int id = get_global_id(0);
                    output[id] = input[id];
                    return;
                """, emission.body());
    }

    @Test
    void blocksEmissionWhenParsingFailed() {
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                """);

        OpenClIrTextBodyEmissionResult emission = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);

        assertTrue(!emission.emitted());
        assertTrue(emission.blockers().contains("ir-text-body-not-parsed"));
    }
}
