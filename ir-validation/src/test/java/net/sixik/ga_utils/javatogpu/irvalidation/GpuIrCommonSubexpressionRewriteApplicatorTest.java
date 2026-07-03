package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionRewriteApplicatorTest {
    private final GpuIrCommonSubexpressionRewritePlanner planner = new GpuIrCommonSubexpressionRewritePlanner();
    private final GpuIrCommonSubexpressionRewriteApplicator applicator = new GpuIrCommonSubexpressionRewriteApplicator();
    private final GpuIrSafetyValidator safetyValidator = new GpuIrSafetyValidator();

    @Test
    void returnsOriginalMethodWhenThereAreNoRewritePlans() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1"))
        ));

        GpuIrMethod rewritten = applicator.apply(method, new GpuIrCommonSubexpressionRewritePlanReport(List.of(), List.of()));

        assertSame(method, rewritten);
    }

    @Test
    void insertsTemporaryDeclarationAndReplacesDownstreamInitializer() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7"))
        ));
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(method, report);

        GpuIrMethod rewritten = applicator.apply(method, planReport);

        assertEquals(4, rewritten.statements().size());
        GpuIrVariableDeclaration temp = assertInstanceOf(GpuIrVariableDeclaration.class, rewritten.statements().get(0));
        assertEquals("int", temp.typeName());
        assertEquals("__gpu_cse_0", temp.name());
        assertInstanceOf(GpuIrBinary.class, temp.initializer());

        GpuIrVariableDeclaration first = assertInstanceOf(GpuIrVariableDeclaration.class, rewritten.statements().get(1));
        assertEquals("first", first.name());
        assertInstanceOf(GpuIrBinary.class, first.initializer());

        GpuIrVariableDeclaration second = assertInstanceOf(GpuIrVariableDeclaration.class, rewritten.statements().get(2));
        GpuIrVariableRef replacement = assertInstanceOf(GpuIrVariableRef.class, second.initializer());
        assertEquals("__gpu_cse_0", replacement.name());
    }

    @Test
    void replacesDownstreamAssignmentValues() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrAssignment(new GpuIrVariableRef("out"), new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(method, report);

        GpuIrMethod rewritten = applicator.apply(method, planReport);

        assertEquals(3, rewritten.statements().size());
        GpuIrVariableDeclaration temp = assertInstanceOf(GpuIrVariableDeclaration.class, rewritten.statements().get(0));
        assertEquals("__gpu_cse_0", temp.name());

        GpuIrAssignment assignment = assertInstanceOf(GpuIrAssignment.class, rewritten.statements().get(2));
        GpuIrVariableRef replacement = assertInstanceOf(GpuIrVariableRef.class, assignment.value());
        assertEquals("__gpu_cse_0", replacement.name());
    }

    @Test
    void replacesNestedDownstreamInitializerExpressions() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("*",
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")),
                        new GpuIrLiteral("2")))
        ));
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(method, report);

        GpuIrMethod rewritten = applicator.apply(method, planReport);

        assertEquals(3, rewritten.statements().size());
        GpuIrVariableDeclaration temp = assertInstanceOf(GpuIrVariableDeclaration.class, rewritten.statements().get(0));
        assertEquals("__gpu_cse_0", temp.name());

        GpuIrVariableDeclaration second = assertInstanceOf(GpuIrVariableDeclaration.class, rewritten.statements().get(2));
        GpuIrBinary outer = assertInstanceOf(GpuIrBinary.class, second.initializer());
        GpuIrVariableRef replacement = assertInstanceOf(GpuIrVariableRef.class, outer.left());
        assertEquals("__gpu_cse_0", replacement.name());
        assertInstanceOf(GpuIrLiteral.class, outer.right());
    }

    @Test
    void insertsTemporaryDeclarationForAssignmentValueAnchors() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "x", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "y", new GpuIrLiteral("2")),
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(method, report);

        GpuIrMethod rewritten = applicator.apply(method, planReport);

        assertEquals(5, rewritten.statements().size());
        GpuIrVariableDeclaration temp = assertInstanceOf(GpuIrVariableDeclaration.class, rewritten.statements().get(2));
        assertEquals("int", temp.typeName());
        assertEquals("__gpu_cse_0", temp.name());
        assertInstanceOf(GpuIrBinary.class, temp.initializer());

        GpuIrAssignment first = assertInstanceOf(GpuIrAssignment.class, rewritten.statements().get(3));
        assertInstanceOf(GpuIrBinary.class, first.value());

        GpuIrAssignment second = assertInstanceOf(GpuIrAssignment.class, rewritten.statements().get(4));
        GpuIrVariableRef replacement = assertInstanceOf(GpuIrVariableRef.class, second.value());
        assertEquals("__gpu_cse_0", replacement.name());
    }

    @Test
    void insertsTemporaryDeclarationForReturnValueAnchors() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "x", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "y", new GpuIrLiteral("2")),
                new GpuIrReturn(new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrReturn(new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(method, report);

        GpuIrMethod rewritten = applicator.apply(method, planReport);

        assertEquals(5, rewritten.statements().size());
        GpuIrVariableDeclaration temp = assertInstanceOf(GpuIrVariableDeclaration.class, rewritten.statements().get(2));
        assertEquals("int", temp.typeName());
        assertEquals("__gpu_cse_0", temp.name());

        GpuIrReturn firstReturn = assertInstanceOf(GpuIrReturn.class, rewritten.statements().get(3));
        assertInstanceOf(GpuIrBinary.class, firstReturn.value());

        GpuIrReturn secondReturn = assertInstanceOf(GpuIrReturn.class, rewritten.statements().get(4));
        GpuIrVariableRef replacement = assertInstanceOf(GpuIrVariableRef.class, secondReturn.value());
        assertEquals("__gpu_cse_0", replacement.name());
    }

    @Test
    void extractsNestedArgumentAnchorExpressions() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrStructInit("Pair", List.of(
                        new GpuIrLiteral("0"),
                        new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))
                ))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("*",
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")),
                        new GpuIrLiteral("2")))
        ));
        GpuIrCommonSubexpressionRewritePlanReport planReport = new GpuIrCommonSubexpressionRewritePlanReport(List.of(
                new GpuIrCommonSubexpressionRewritePlan(
                        "__gpu_cse_0",
                        "binary(+,var(x),var(y))",
                        1,
                        0,
                        "stmt[0].initializer.arg[1]",
                        List.of("stmt[0].initializer.arg[1]", "stmt[1].initializer.left")
                )
        ), List.of());

        GpuIrMethod rewritten = applicator.apply(method, planReport);

        GpuIrVariableDeclaration temp = assertInstanceOf(GpuIrVariableDeclaration.class, rewritten.statements().get(0));
        assertEquals("int", temp.typeName());
        assertEquals("__gpu_cse_0", temp.name());
        assertInstanceOf(GpuIrBinary.class, temp.initializer());
    }

    @Test
    void extractsIntrinsicReceiverAndHelperArgumentAnchorExpressions() {
        GpuIrMethod receiverMethod = new GpuIrMethod("receiverKernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrIntrinsicCall(
                        new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")),
                        "abs",
                        "abs($receiver)",
                        "int",
                        List.of()
                )),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        GpuIrCommonSubexpressionRewritePlanReport receiverPlan = new GpuIrCommonSubexpressionRewritePlanReport(List.of(
                new GpuIrCommonSubexpressionRewritePlan(
                        "__gpu_cse_0",
                        "binary(+,var(x),var(y))",
                        1,
                        0,
                        "stmt[0].initializer.receiver",
                        List.of("stmt[0].initializer.receiver", "stmt[1].initializer")
                )
        ), List.of());

        GpuIrMethod rewrittenReceiver = applicator.apply(receiverMethod, receiverPlan);

        GpuIrVariableDeclaration receiverTemp = assertInstanceOf(GpuIrVariableDeclaration.class, rewrittenReceiver.statements().get(0));
        assertEquals("int", receiverTemp.typeName());
        assertInstanceOf(GpuIrBinary.class, receiverTemp.initializer());

        GpuIrMethod helperMethod = new GpuIrMethod("helperKernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrHelperCall("mix", "int", List.of(
                        new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))
                ))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        GpuIrCommonSubexpressionRewritePlanReport helperPlan = new GpuIrCommonSubexpressionRewritePlanReport(List.of(
                new GpuIrCommonSubexpressionRewritePlan(
                        "__gpu_cse_0",
                        "binary(+,var(x),var(y))",
                        1,
                        0,
                        "stmt[0].initializer.arg[0]",
                        List.of("stmt[0].initializer.arg[0]", "stmt[1].initializer")
                )
        ), List.of());

        GpuIrMethod rewrittenHelper = applicator.apply(helperMethod, helperPlan);

        GpuIrVariableDeclaration helperTemp = assertInstanceOf(GpuIrVariableDeclaration.class, rewrittenHelper.statements().get(0));
        assertEquals("int", helperTemp.typeName());
        assertInstanceOf(GpuIrBinary.class, helperTemp.initializer());
    }

    @Test
    void rejectsUnsupportedAnchorStatementShapes() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+", new GpuIrLiteral("1"), new GpuIrLiteral("2"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("+", new GpuIrLiteral("1"), new GpuIrLiteral("2")))
        ));
        GpuIrCommonSubexpressionRewritePlanReport planReport = new GpuIrCommonSubexpressionRewritePlanReport(List.of(
                new GpuIrCommonSubexpressionRewritePlan(
                        "__gpu_cse_0",
                        "binary(+,literal(1),literal(2))",
                        1,
                        0,
                        "stmt[0].target",
                        List.of("stmt[0].target", "stmt[1].value")
                )
        ), List.of());

        assertThrows(IllegalArgumentException.class, () -> applicator.apply(method, planReport));
    }

    @Test
    void rejectsUnsupportedNestedAnchorPaths() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrLiteral("1"), new GpuIrLiteral("2"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrLiteral("1"), new GpuIrLiteral("2")))
        ));
        GpuIrCommonSubexpressionRewritePlanReport planReport = new GpuIrCommonSubexpressionRewritePlanReport(List.of(
                new GpuIrCommonSubexpressionRewritePlan(
                        "__gpu_cse_0",
                        "literal(1)",
                        1,
                        0,
                        "stmt[0].initializer.foo",
                        List.of("stmt[0].initializer.foo", "stmt[1].initializer.left")
                )
        ), List.of());

        assertThrows(IllegalArgumentException.class, () -> applicator.apply(method, planReport));
    }

    @Test
    void rejectsPlansWithUnappliedReplacementLocations() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        GpuIrCommonSubexpressionRewritePlanReport planReport = new GpuIrCommonSubexpressionRewritePlanReport(List.of(
                new GpuIrCommonSubexpressionRewritePlan(
                        "__gpu_cse_0",
                        "binary(+,var(x),var(y))",
                        1,
                        0,
                        "stmt[0].initializer",
                        List.of("stmt[0].initializer", "stmt[1].initializer.left.left")
                )
        ), List.of());

        assertThrows(IllegalArgumentException.class, () -> applicator.apply(method, planReport));
    }

    @Test
    void rewrittenIrPassesSafetyValidation() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                new GpuIrReturn(null)
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(compiledMethod, report);

        GpuIrMethod rewritten = applicator.apply(compiledMethod, planReport);
        GpuIrCompiledMethod rewrittenCompiledMethod = new GpuIrCompiledMethod(
                compiledMethod.parsedMethod(),
                rewritten,
                compiledMethod.emittedName(),
                compiledMethod.helperDependencies()
        );

        assertDoesNotThrow(() -> safetyValidator.run(new GpuIrPassContext(rewrittenCompiledMethod, List.of(), List.of(), true)));
    }

    @Test
    void rewritePreservesSimpleStraightLineIntegerSemantics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("*",
                        new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")),
                        new GpuIrLiteral("2"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("-",
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")),
                        new GpuIrLiteral("3"))),
                new GpuIrReturn(new GpuIrBinary("+", new GpuIrVariableRef("outA"), new GpuIrVariableRef("outB")))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(compiledMethod, report);
        GpuIrMethod rewritten = applicator.apply(compiledMethod, planReport);
        List<Map<String, Integer>> inputCases = List.of(
                Map.of("x", 7, "y", 11, "outA", 0, "outB", 0),
                Map.of("x", -4, "y", 13, "outA", 0, "outB", 0),
                Map.of("x", 0, "y", -9, "outA", 123, "outB", -456)
        );

        assertTrue(planReport.hasPlans());
        for (Map<String, Integer> inputValues : inputCases) {
            ExecutionResult originalResult = execute(method, inputValues);
            ExecutionResult rewrittenResult = execute(rewritten, inputValues);

            assertEquals(originalResult.returnValue(), rewrittenResult.returnValue());
            assertEquals(originalResult.valueOf("outA"), rewrittenResult.valueOf("outA"));
            assertEquals(originalResult.valueOf("outB"), rewrittenResult.valueOf("outB"));
        }
    }

    @Test
    void rewritePreservesReturnValueExtractionSemantics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "offset", new GpuIrLiteral("5")),
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+",
                        new GpuIrBinary("*", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")),
                        new GpuIrVariableRef("offset"))),
                new GpuIrReturn(new GpuIrBinary("-",
                        new GpuIrBinary("*", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")),
                        new GpuIrVariableRef("offset")))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(compiledMethod, report);
        GpuIrMethod rewritten = applicator.apply(compiledMethod, planReport);
        List<Map<String, Integer>> inputCases = List.of(
                Map.of("x", 3, "y", 4, "outA", 0, "outB", 0),
                Map.of("x", -6, "y", 7, "outA", 100, "outB", 0),
                Map.of("x", 11, "y", -2, "outA", -100, "outB", 0)
        );

        assertTrue(planReport.hasPlans());
        for (Map<String, Integer> inputValues : inputCases) {
            ExecutionResult originalResult = execute(method, inputValues);
            ExecutionResult rewrittenResult = execute(rewritten, inputValues);

            assertEquals(originalResult.returnValue(), rewrittenResult.returnValue());
            assertEquals(originalResult.valueOf("outA"), rewrittenResult.valueOf("outA"));
        }
    }

    @Test
    void rewritePreservesBitwiseIntegerSemantics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("|",
                        new GpuIrBinary("&", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")),
                        new GpuIrLiteral("8"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("^",
                        new GpuIrBinary("&", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")),
                        new GpuIrLiteral("3"))),
                new GpuIrReturn(new GpuIrBinary("+", new GpuIrVariableRef("outA"), new GpuIrVariableRef("outB")))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(compiledMethod, report);
        GpuIrMethod rewritten = applicator.apply(compiledMethod, planReport);
        List<Map<String, Integer>> inputCases = List.of(
                Map.of("x", 0b1010, "y", 0b1100, "outA", 0, "outB", 0),
                Map.of("x", -1, "y", 0x55, "outA", 7, "outB", -9),
                Map.of("x", 0x1234, "y", 0x00FF, "outA", -100, "outB", 100)
        );

        assertTrue(planReport.hasPlans());
        for (Map<String, Integer> inputValues : inputCases) {
            ExecutionResult originalResult = execute(method, inputValues);
            ExecutionResult rewrittenResult = execute(rewritten, inputValues);

            assertEquals(originalResult.returnValue(), rewrittenResult.returnValue());
            assertEquals(originalResult.valueOf("outA"), rewrittenResult.valueOf("outA"));
            assertEquals(originalResult.valueOf("outB"), rewrittenResult.valueOf("outB"));
        }
    }

    @Test
    void rewritePreservesUnaryCastAndTernaryIntegerSemantics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrUnary("-",
                        new GpuIrCast("int", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrTernary(
                        new GpuIrVariableRef("x"),
                        new GpuIrCast("int", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                        new GpuIrUnary("-", new GpuIrLiteral("1"))
                )),
                new GpuIrReturn(new GpuIrBinary("+", new GpuIrVariableRef("outA"), new GpuIrVariableRef("outB")))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrCommonSubexpressionReport report = GpuIrCommonSubexpressionScanner.optimizerFocused().scan(method);
        GpuIrCommonSubexpressionRewritePlanReport planReport = planner.planReport(compiledMethod, report);
        GpuIrMethod rewritten = applicator.apply(compiledMethod, planReport);
        List<Map<String, Integer>> inputCases = List.of(
                Map.of("x", 5, "y", 9, "outA", 0, "outB", 0),
                Map.of("x", -3, "y", 12, "outA", 10, "outB", -10),
                Map.of("x", 0, "y", 7, "outA", 100, "outB", 200)
        );

        assertTrue(planReport.hasPlans());
        for (Map<String, Integer> inputValues : inputCases) {
            ExecutionResult originalResult = execute(method, inputValues);
            ExecutionResult rewrittenResult = execute(rewritten, inputValues);

            assertEquals(originalResult.returnValue(), rewrittenResult.returnValue());
            assertEquals(originalResult.valueOf("outA"), rewrittenResult.valueOf("outA"));
            assertEquals(originalResult.valueOf("outB"), rewrittenResult.valueOf("outB"));
        }
    }

    private GpuIrCompiledMethod compiledMethod(GpuIrMethod method) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                method.name(),
                "void",
                List.of(
                        new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("y", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("outA", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("outB", "int", GpuAddressSpace.PRIVATE, false, List.of())
                ),
                List.of(),
                List.of(),
                null,
                false,
                List.of(),
                null,
                "",
                null,
                false
        );
        return new GpuIrCompiledMethod(parsedMethod, method, "jtg_kernel", List.of());
    }

    private ExecutionResult execute(GpuIrMethod method, Map<String, Integer> inputValues) {
        Map<String, Integer> values = new HashMap<>(inputValues);
        Integer returnValue = null;
        for (var statement : method.statements()) {
            if (statement instanceof GpuIrVariableDeclaration declaration) {
                values.put(declaration.name(), evaluate(declaration.initializer(), values));
            } else if (statement instanceof GpuIrAssignment assignment && assignment.target() instanceof GpuIrVariableRef target) {
                values.put(target.name(), evaluate(assignment.value(), values));
            } else if (statement instanceof GpuIrReturn gpuReturn && gpuReturn.value() != null) {
                returnValue = evaluate(gpuReturn.value(), values);
            }
        }
        return new ExecutionResult(values, returnValue);
    }

    private int evaluate(net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression expression, Map<String, Integer> values) {
        if (expression instanceof GpuIrLiteral literal) {
            return Integer.parseInt(literal.sourceText().replace("L", ""));
        }
        if (expression instanceof GpuIrVariableRef variableRef) {
            return values.get(variableRef.name());
        }
        if (expression instanceof GpuIrBinary binary) {
            int left = evaluate(binary.left(), values);
            int right = evaluate(binary.right(), values);
            return switch (binary.operator()) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*" -> left * right;
                case "/" -> left / right;
                case "&" -> left & right;
                case "|" -> left | right;
                case "^" -> left ^ right;
                default -> throw new IllegalArgumentException("Unsupported test operator: " + binary.operator());
            };
        }
        if (expression instanceof GpuIrUnary unary) {
            int operand = evaluate(unary.operand(), values);
            return switch (unary.operator()) {
                case "+" -> operand;
                case "-" -> -operand;
                case "~" -> ~operand;
                case "!" -> operand == 0 ? 1 : 0;
                default -> throw new IllegalArgumentException("Unsupported test unary operator: " + unary.operator());
            };
        }
        if (expression instanceof GpuIrCast cast) {
            return evaluate(cast.expression(), values);
        }
        if (expression instanceof GpuIrTernary ternary) {
            return evaluate(ternary.condition(), values) != 0
                    ? evaluate(ternary.whenTrue(), values)
                    : evaluate(ternary.whenFalse(), values);
        }
        throw new IllegalArgumentException("Unsupported test expression: " + expression);
    }

    private record ExecutionResult(Map<String, Integer> values, Integer returnValue) {
        int valueOf(String name) {
            return values.get(name);
        }
    }
}
