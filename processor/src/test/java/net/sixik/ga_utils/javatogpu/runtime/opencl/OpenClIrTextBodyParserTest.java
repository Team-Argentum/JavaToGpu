package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClIrTextBodyParserTest {

    @Test
    void parsesSimpleFlatStatements() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  var int id = get_global_id(0)
                  set output[id] = input[id]
                  return
                """);

        assertTrue(result.parsed());
        assertTrue(result.blockers().isEmpty());
        assertEquals(3, result.statements().size());
        assertEquals(OpenClIrTextStatement.Kind.VARIABLE, result.statements().get(0).kind());
        assertEquals("int", result.statements().get(0).typeName());
        assertEquals("id", result.statements().get(0).target());
        assertEquals("get_global_id(0)", result.statements().get(0).expression());
        assertEquals(OpenClIrTextStatement.Kind.ASSIGNMENT, result.statements().get(1).kind());
        assertEquals("output[id]", result.statements().get(1).target());
        assertEquals("input[id]", result.statements().get(1).expression());
        assertEquals(OpenClIrTextStatement.Kind.RETURN, result.statements().get(2).kind());
    }

    @Test
    void reportsUnsupportedStatementsAsBlockers() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set output[i] = input[i]
                """);

        assertTrue(!result.parsed());
        assertTrue(result.blockers().contains("ir-text-line-2-unsupported-for"));
        assertEquals(1, result.statements().size());
        assertEquals(OpenClIrTextStatement.Kind.ASSIGNMENT, result.statements().get(0).kind());
    }

    @Test
    void reportsMissingBodyHeader() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("return output[0]\n");

        assertTrue(!result.parsed());
        assertTrue(result.blockers().contains("ir-text-body-header-missing"));
    }
}
