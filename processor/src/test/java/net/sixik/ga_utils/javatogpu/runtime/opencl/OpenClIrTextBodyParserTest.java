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
    void parsesOptionalMethodHeaderBeforeBody() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                method jtg_kernel source=kernel
                body
                  var int id = get_global_id(0)
                  set output[id] = input[id]
                  return
                """);

        assertTrue(result.parsed());
        assertTrue(result.blockers().isEmpty());
        assertEquals(3, result.statements().size());
        assertEquals(OpenClIrTextStatement.Kind.VARIABLE, result.statements().get(0).kind());
        assertEquals(OpenClIrTextStatement.Kind.ASSIGNMENT, result.statements().get(1).kind());
        assertEquals(OpenClIrTextStatement.Kind.RETURN, result.statements().get(2).kind());
    }

    @Test
    void parsesOptionalMethodAndHelpersHeadersBeforeBody() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                method jtg_kernel source=kernel
                helpers jtg_fn_square_float
                body
                  var int id = get_global_id(0)
                  set output[id] = helper(jtg_fn_square_float args=[input[id]])
                  return
                """);

        assertTrue(result.parsed());
        assertTrue(result.blockers().isEmpty());
        assertEquals(3, result.statements().size());
        assertEquals(OpenClIrTextStatement.Kind.VARIABLE, result.statements().get(0).kind());
        assertEquals(OpenClIrTextStatement.Kind.ASSIGNMENT, result.statements().get(1).kind());
        assertEquals(OpenClIrTextStatement.Kind.RETURN, result.statements().get(2).kind());
    }

    @Test
    void parsesSimpleSwitchBlocks() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  switch selector
                    case 0,1
                      set output[0] = 1
                      break
                    default
                      set output[0] = 0
                  return
                """);

        assertTrue(result.parsed());
        assertTrue(result.blockers().isEmpty());
        assertEquals(2, result.statements().size());
        OpenClIrTextStatement switchStatement = result.statements().get(0);
        assertEquals(OpenClIrTextStatement.Kind.SWITCH, switchStatement.kind());
        assertEquals("selector", switchStatement.expression());
        assertEquals(2, switchStatement.switchCases().size());
        assertEquals(java.util.List.of("0", "1"), switchStatement.switchCases().get(0).labels());
        assertEquals(2, switchStatement.switchCases().get(0).statements().size());
        assertTrue(switchStatement.switchCases().get(1).defaultCase());
        assertEquals(1, switchStatement.switchCases().get(1).statements().size());
        assertEquals(OpenClIrTextStatement.Kind.RETURN, result.statements().get(1).kind());
    }

    @Test
    void reportsMissingBodyHeader() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("return output[0]\n");

        assertTrue(!result.parsed());
        assertTrue(result.blockers().contains("ir-text-body-header-missing"));
    }

    @Test
    void parsesSimpleIfElseBlocks() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  if (value > 0)
                    set output[0] = value
                  else
                    set output[0] = 0
                  return
                """);

        assertTrue(result.parsed());
        assertEquals(2, result.statements().size());
        OpenClIrTextStatement ifStatement = result.statements().get(0);
        assertEquals(OpenClIrTextStatement.Kind.IF, ifStatement.kind());
        assertEquals("(value > 0)", ifStatement.expression());
        assertEquals(1, ifStatement.thenStatements().size());
        assertEquals(1, ifStatement.elseStatements().size());
        assertEquals(OpenClIrTextStatement.Kind.RETURN, result.statements().get(1).kind());
    }

    @Test
    void parsesSimpleForLoopBlocks() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  for init=(var int i = 0) cond=(i < 4) update=(set i = (i + 1))
                    set output[i] = input[i]
                  return
                """);

        assertTrue(result.parsed());
        assertEquals(2, result.statements().size());
        OpenClIrTextStatement forStatement = result.statements().get(0);
        assertEquals(OpenClIrTextStatement.Kind.FOR, forStatement.kind());
        assertEquals("var int i = 0", forStatement.typeName());
        assertEquals("(i < 4)", forStatement.expression());
        assertEquals("set i = (i + 1)", forStatement.target());
        assertEquals(1, forStatement.thenStatements().size());
        assertEquals(OpenClIrTextStatement.Kind.RETURN, result.statements().get(1).kind());
    }

    @Test
    void parsesSimpleWhileLoopBlocks() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  while (i < count)
                    set i = (i + 1)
                  return
                """);

        assertTrue(result.parsed());
        assertEquals(2, result.statements().size());
        OpenClIrTextStatement whileStatement = result.statements().get(0);
        assertEquals(OpenClIrTextStatement.Kind.WHILE, whileStatement.kind());
        assertEquals("(i < count)", whileStatement.expression());
        assertEquals(1, whileStatement.thenStatements().size());
        assertEquals(OpenClIrTextStatement.Kind.RETURN, result.statements().get(1).kind());
    }

    @Test
    void parsesSimpleDoWhileLoopBlocks() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  do
                    set i = (i + 1)
                  while (i < count)
                  return
                """);

        assertTrue(result.parsed());
        assertEquals(2, result.statements().size());
        OpenClIrTextStatement doWhileStatement = result.statements().get(0);
        assertEquals(OpenClIrTextStatement.Kind.DO_WHILE, doWhileStatement.kind());
        assertEquals("(i < count)", doWhileStatement.expression());
        assertEquals(1, doWhileStatement.thenStatements().size());
        assertEquals(OpenClIrTextStatement.Kind.RETURN, result.statements().get(1).kind());
    }

    @Test
    void parsesBreakContinueAndLoopBreakStatements() {
        OpenClIrTextBodyParseResult result = OpenClIrTextBodyParser.INSTANCE.parse("""
                body
                  while (i < count)
                    if (i == 4)
                      break
                    if (i == 2)
                      continue
                    loop-break
                  return
                """);

        assertTrue(result.parsed());
        OpenClIrTextStatement whileStatement = result.statements().get(0);
        assertEquals(OpenClIrTextStatement.Kind.WHILE, whileStatement.kind());
        assertEquals(3, whileStatement.thenStatements().size());
        assertEquals(OpenClIrTextStatement.Kind.BREAK, whileStatement.thenStatements().get(0).thenStatements().get(0).kind());
        assertEquals(OpenClIrTextStatement.Kind.CONTINUE, whileStatement.thenStatements().get(1).thenStatements().get(0).kind());
        assertEquals(OpenClIrTextStatement.Kind.BREAK, whileStatement.thenStatements().get(2).kind());
    }
}
