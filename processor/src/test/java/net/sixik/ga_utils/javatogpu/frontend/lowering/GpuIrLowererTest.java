package net.sixik.ga_utils.javatogpu.frontend.lowering;

import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GpuIrLowererTest {

    @Test
    void lowersSimpleGpuMathMethodIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    float value = input[id];
                    output[id] = GPU.sin(value) * GPU.cos(value);
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        assertEquals("kernel", irMethod.name());
        assertEquals(3, irMethod.statements().size());

        GpuIrStatement first = irMethod.statements().get(0);
        GpuIrVariableDeclaration idDeclaration = assertInstanceOf(GpuIrVariableDeclaration.class, first);
        assertEquals("int", idDeclaration.typeName());
        assertEquals("id", idDeclaration.name());
        GpuIrIntrinsicCall globalIdCall = assertInstanceOf(GpuIrIntrinsicCall.class, idDeclaration.initializer());
        assertEquals("get_global_id", globalIdCall.backendName());
        assertEquals("int", globalIdCall.resultType());

        GpuIrStatement second = irMethod.statements().get(1);
        GpuIrVariableDeclaration valueDeclaration = assertInstanceOf(GpuIrVariableDeclaration.class, second);
        assertEquals("float", valueDeclaration.typeName());
        GpuIrArrayAccess loadInput = assertInstanceOf(GpuIrArrayAccess.class, valueDeclaration.initializer());
        assertEquals("input", loadInput.arrayName());
        GpuIrVariableRef loadIndex = assertInstanceOf(GpuIrVariableRef.class, loadInput.index());
        assertEquals("id", loadIndex.name());

        GpuIrStatement third = irMethod.statements().get(2);
        GpuIrAssignment assignment = assertInstanceOf(GpuIrAssignment.class, third);
        GpuIrArrayAccess storeOutput = assertInstanceOf(GpuIrArrayAccess.class, assignment.target());
        assertEquals("output", storeOutput.arrayName());
        GpuIrBinary multiply = assertInstanceOf(GpuIrBinary.class, assignment.value());
        assertEquals("*", multiply.operator());
        assertIntrinsicCall(multiply.left(), "sin");
        assertIntrinsicCall(multiply.right(), "cos");
    }

    private static void assertIntrinsicCall(GpuIrExpression expression, String backendName) {
        GpuIrIntrinsicCall call = assertInstanceOf(GpuIrIntrinsicCall.class, expression);
        assertEquals(backendName, call.backendName());
        assertEquals("float", call.resultType());
    }

    @Test
    void lowersClassicForLoopIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    for (int i = 0; i < 4; i++) {
                        output[i] = GPU.sin(input[i]);
                    }
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        assertEquals(1, irMethod.statements().size());

        GpuIrForLoop loop = assertInstanceOf(GpuIrForLoop.class, irMethod.statements().get(0));
        GpuIrVariableDeclaration initializer = assertInstanceOf(GpuIrVariableDeclaration.class, loop.initializer());
        assertEquals("int", initializer.typeName());
        assertEquals("i", initializer.name());
        GpuIrLiteral initialValue = assertInstanceOf(GpuIrLiteral.class, initializer.initializer());
        assertEquals("0", initialValue.sourceText());

        GpuIrBinary condition = assertInstanceOf(GpuIrBinary.class, loop.condition());
        assertEquals("<", condition.operator());

        GpuIrAssignment update = assertInstanceOf(GpuIrAssignment.class, loop.update());
        GpuIrVariableRef updateTarget = assertInstanceOf(GpuIrVariableRef.class, update.target());
        assertEquals("i", updateTarget.name());
        GpuIrBinary increment = assertInstanceOf(GpuIrBinary.class, update.value());
        assertEquals("+", increment.operator());

        assertEquals(1, loop.body().size());
        GpuIrAssignment assignment = assertInstanceOf(GpuIrAssignment.class, loop.body().get(0));
        GpuIrArrayAccess target = assertInstanceOf(GpuIrArrayAccess.class, assignment.target());
        assertEquals("output", target.arrayName());
        GpuIrIntrinsicCall call = assertInstanceOf(GpuIrIntrinsicCall.class, assignment.value());
        assertEquals("sin", call.backendName());
    }

    @Test
    void lowersFloatLiteralsAndParenthesizedExpressions() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    output[0] = GPU.sin((1.0f + 2.0f));
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrAssignment assignment = assertInstanceOf(GpuIrAssignment.class, irMethod.statements().get(0));
        GpuIrIntrinsicCall call = assertInstanceOf(GpuIrIntrinsicCall.class, assignment.value());
        GpuIrBinary sum = assertInstanceOf(GpuIrBinary.class, call.arguments().get(0));
        GpuIrLiteral left = assertInstanceOf(GpuIrLiteral.class, sum.left());
        GpuIrLiteral right = assertInstanceOf(GpuIrLiteral.class, sum.right());
        assertEquals("1.0f", left.sourceText());
        assertEquals("2.0f", right.sourceText());
    }

    @Test
    void lowersPrimitiveCastsIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] output) {
                    int i = 1;
                    output[0] = GPU.sin((float) i);
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrAssignment assignment = assertInstanceOf(GpuIrAssignment.class, irMethod.statements().get(1));
        GpuIrIntrinsicCall call = assertInstanceOf(GpuIrIntrinsicCall.class, assignment.value());
        GpuIrCast cast = assertInstanceOf(GpuIrCast.class, call.arguments().get(0));
        assertEquals("float", cast.targetType());
        GpuIrVariableRef ref = assertInstanceOf(GpuIrVariableRef.class, cast.expression());
        assertEquals("i", ref.name());
    }

    @Test
    void lowersDoubleMathIntrinsicsIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
                    int id = GPU.get_global_id(0);
                    double value = GPU.sqrt(input[id]) + GPU.pow(input[id], 2.0);
                    output[id] = GPU.max(value, GPU.log(input[id]));
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrVariableDeclaration valueDeclaration = assertInstanceOf(GpuIrVariableDeclaration.class, irMethod.statements().get(1));
        GpuIrBinary sum = assertInstanceOf(GpuIrBinary.class, valueDeclaration.initializer());
        GpuIrIntrinsicCall sqrt = assertInstanceOf(GpuIrIntrinsicCall.class, sum.left());
        assertEquals("sqrt", sqrt.backendName());
        assertEquals("double", sqrt.resultType());
        GpuIrIntrinsicCall pow = assertInstanceOf(GpuIrIntrinsicCall.class, sum.right());
        assertEquals("pow", pow.backendName());
        assertEquals("double", pow.resultType());

        GpuIrAssignment assignment = assertInstanceOf(GpuIrAssignment.class, irMethod.statements().get(2));
        GpuIrIntrinsicCall max = assertInstanceOf(GpuIrIntrinsicCall.class, assignment.value());
        assertEquals("max", max.backendName());
        assertEquals("double", max.resultType());
        GpuIrIntrinsicCall log = assertInstanceOf(GpuIrIntrinsicCall.class, max.arguments().get(1));
        assertEquals("log", log.backendName());
        assertEquals("double", log.resultType());
    }

    @Test
    void lowersBooleanLocalsIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    boolean enabled = input[0] > 0.0f;
                    if (enabled) {
                        output[0] = 1.0f;
                    }
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrVariableDeclaration enabled = assertInstanceOf(GpuIrVariableDeclaration.class, irMethod.statements().get(0));
        assertEquals("boolean", enabled.typeName());
        GpuIrBinary comparison = assertInstanceOf(GpuIrBinary.class, enabled.initializer());
        assertEquals(">", comparison.operator());
    }

    @Test
    void lowersIfElseIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    if (input[id] > 0.0f) {
                        output[id] = GPU.sin(input[id]);
                    } else {
                        output[id] = GPU.cos(input[id]);
                    }
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        assertEquals(2, irMethod.statements().size());
        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, irMethod.statements().get(1));
        GpuIrBinary condition = assertInstanceOf(GpuIrBinary.class, ifStatement.condition());
        assertEquals(">", condition.operator());
        assertEquals(1, ifStatement.thenBranch().size());
        assertEquals(1, ifStatement.elseBranch().size());
    }

    @Test
    void lowersLogicalOperatorsIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    if ((input[id] > 0.0f && input[id] < 10.0f) || !(input[id] > 100.0f)) {
                        output[id] = GPU.sin(input[id]);
                    } else {
                        output[id] = GPU.cos(input[id]);
                    }
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrIf ifStatement = assertInstanceOf(GpuIrIf.class, irMethod.statements().get(1));
        GpuIrBinary or = assertInstanceOf(GpuIrBinary.class, ifStatement.condition());
        assertEquals("||", or.operator());
        GpuIrBinary and = assertInstanceOf(GpuIrBinary.class, or.left());
        assertEquals("&&", and.operator());
        GpuIrUnary not = assertInstanceOf(GpuIrUnary.class, or.right());
        assertEquals("!", not.operator());
    }

    @Test
    void lowersElseIfChainIntoNestedIfIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    if (input[id] > 10.0f) {
                        output[id] = GPU.sin(input[id]);
                    } else if (input[id] > 0.0f) {
                        output[id] = GPU.cos(input[id]);
                    } else {
                        output[id] = input[id];
                    }
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrIf outerIf = assertInstanceOf(GpuIrIf.class, irMethod.statements().get(1));
        assertEquals(1, outerIf.elseBranch().size());
        GpuIrIf nestedIf = assertInstanceOf(GpuIrIf.class, outerIf.elseBranch().get(0));
        assertEquals(1, nestedIf.thenBranch().size());
        assertEquals(1, nestedIf.elseBranch().size());
    }

    @Test
    void lowersTernaryExpressionIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = input[id] > 0.0f ? GPU.sin(input[id]) : GPU.cos(input[id]);
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrAssignment assignment = assertInstanceOf(GpuIrAssignment.class, irMethod.statements().get(1));
        GpuIrTernary ternary = assertInstanceOf(GpuIrTernary.class, assignment.value());
        GpuIrBinary condition = assertInstanceOf(GpuIrBinary.class, ternary.condition());
        assertEquals(">", condition.operator());
        assertIntrinsicCall(ternary.whenTrue(), "sin");
        assertIntrinsicCall(ternary.whenFalse(), "cos");
    }

    @Test
    void lowersDivisionModuloAndUnaryMinusIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int value = -input[id];
                    output[id] = (value / 2) % 3;
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrVariableDeclaration declaration = assertInstanceOf(GpuIrVariableDeclaration.class, irMethod.statements().get(1));
        GpuIrUnary minus = assertInstanceOf(GpuIrUnary.class, declaration.initializer());
        assertEquals("-", minus.operator());

        GpuIrAssignment assignment = assertInstanceOf(GpuIrAssignment.class, irMethod.statements().get(2));
        GpuIrBinary modulo = assertInstanceOf(GpuIrBinary.class, assignment.value());
        assertEquals("%", modulo.operator());
        GpuIrBinary division = assertInstanceOf(GpuIrBinary.class, modulo.left());
        assertEquals("/", division.operator());
    }

    @Test
    void lowersBitwiseIntegerExpressionsIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    output[id] = ((~input[id]) << 1) ^ ((input[id] >> 1) | (input[id] & 7));
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrAssignment assignment = assertInstanceOf(GpuIrAssignment.class, irMethod.statements().get(1));
        GpuIrBinary xor = assertInstanceOf(GpuIrBinary.class, assignment.value());
        assertEquals("^", xor.operator());

        GpuIrBinary shiftLeft = assertInstanceOf(GpuIrBinary.class, xor.left());
        assertEquals("<<", shiftLeft.operator());
        GpuIrUnary bitwiseNot = assertInstanceOf(GpuIrUnary.class, shiftLeft.left());
        assertEquals("~", bitwiseNot.operator());

        GpuIrBinary or = assertInstanceOf(GpuIrBinary.class, xor.right());
        assertEquals("|", or.operator());
        GpuIrBinary shiftRight = assertInstanceOf(GpuIrBinary.class, or.left());
        assertEquals(">>", shiftRight.operator());
        GpuIrBinary and = assertInstanceOf(GpuIrBinary.class, or.right());
        assertEquals("&", and.operator());
    }

    @Test
    void lowersCompoundAssignmentsAndDecrementIntoIrAssignments() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int id = GPU.get_global_id(0);
                    int value = input[id];
                    value += 2;
                    value <<= 1;
                    for (int i = 3; i > 0; i--) {
                        output[id] += i;
                    }
                    value--;
                    output[id] = value;
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrAssignment plusAssign = assertInstanceOf(GpuIrAssignment.class, irMethod.statements().get(2));
        GpuIrBinary plus = assertInstanceOf(GpuIrBinary.class, plusAssign.value());
        assertEquals("+", plus.operator());

        GpuIrAssignment shiftAssign = assertInstanceOf(GpuIrAssignment.class, irMethod.statements().get(3));
        GpuIrBinary shift = assertInstanceOf(GpuIrBinary.class, shiftAssign.value());
        assertEquals("<<", shift.operator());

        GpuIrForLoop loop = assertInstanceOf(GpuIrForLoop.class, irMethod.statements().get(4));
        GpuIrAssignment decrement = assertInstanceOf(GpuIrAssignment.class, loop.update());
        GpuIrBinary minus = assertInstanceOf(GpuIrBinary.class, decrement.value());
        assertEquals("-", minus.operator());

        GpuIrAssignment bodyAssign = assertInstanceOf(GpuIrAssignment.class, loop.body().get(0));
        GpuIrBinary bodyPlus = assertInstanceOf(GpuIrBinary.class, bodyAssign.value());
        assertEquals("+", bodyPlus.operator());

        GpuIrAssignment trailingDecrement = assertInstanceOf(GpuIrAssignment.class, irMethod.statements().get(5));
        GpuIrBinary trailingMinus = assertInstanceOf(GpuIrBinary.class, trailingDecrement.value());
        assertEquals("-", trailingMinus.operator());
    }

    @Test
    void lowersWhileDoWhileSwitchBreakAndContinueIntoIr() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    int i = 0;
                    while (i < 4) {
                        if ((i % 2) == 0) {
                            i++;
                            continue;
                        }
                        output[i] = input[i];
                        i++;
                    }
                    do {
                        i--;
                    } while (i > 0);
                    switch (input[0] & 3) {
                        case 0:
                            output[0] = 1;
                            break;
                        case 1:
                        case 2:
                            output[0] = 2;
                            break;
                        default:
                            output[0] = 3;
                    }
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrWhileLoop whileLoop = assertInstanceOf(GpuIrWhileLoop.class, irMethod.statements().get(1));
        GpuIrBinary whileCondition = assertInstanceOf(GpuIrBinary.class, whileLoop.condition());
        assertEquals("<", whileCondition.operator());
        GpuIrIf nestedIf = assertInstanceOf(GpuIrIf.class, whileLoop.body().get(0));
        assertInstanceOf(GpuIrContinue.class, nestedIf.thenBranch().get(1));

        GpuIrDoWhileLoop doWhileLoop = assertInstanceOf(GpuIrDoWhileLoop.class, irMethod.statements().get(2));
        GpuIrBinary doWhileCondition = assertInstanceOf(GpuIrBinary.class, doWhileLoop.condition());
        assertEquals(">", doWhileCondition.operator());

        GpuIrSwitch switchStatement = assertInstanceOf(GpuIrSwitch.class, irMethod.statements().get(3));
        assertEquals(4, switchStatement.cases().size());
        assertEquals(1, switchStatement.cases().get(0).labels().size());
        assertEquals(1, switchStatement.cases().get(1).labels().size());
        assertEquals(0, switchStatement.cases().get(1).statements().size());
        assertEquals(1, switchStatement.cases().get(2).labels().size());
        assertInstanceOf(GpuIrBreak.class, switchStatement.cases().get(0).statements().get(1));
        assertInstanceOf(GpuIrBreak.class, switchStatement.cases().get(2).statements().get(1));
        assertEquals(true, switchStatement.cases().get(3).defaultCase());
    }

    @Test
    void lowersRuleStyleSwitchIntoBreakTerminatedCases() {
        String methodSource = """
                @GPU
                void kernel(@GPUGlobal int[] input, @GPUGlobal int[] output) {
                    switch (input[0] & 3) {
                        case 0 -> output[0] = 1;
                        case 1 -> {
                            output[0] = 2;
                        }
                        default -> output[0] = 3;
                    }
                }
                """;

        ParsedGpuMethod method = new GpuMethodParser().parseMethod(methodSource);
        GpuIrMethod irMethod = new GpuIrLowerer(GpuIntrinsicDatabase.createDefault()).lower(method);

        GpuIrSwitch switchStatement = assertInstanceOf(GpuIrSwitch.class, irMethod.statements().get(0));
        assertEquals(3, switchStatement.cases().size());
        assertInstanceOf(GpuIrBreak.class, switchStatement.cases().get(0).statements().get(1));
        assertInstanceOf(GpuIrBreak.class, switchStatement.cases().get(1).statements().get(1));
        assertInstanceOf(GpuIrBreak.class, switchStatement.cases().get(2).statements().get(1));
    }
}
