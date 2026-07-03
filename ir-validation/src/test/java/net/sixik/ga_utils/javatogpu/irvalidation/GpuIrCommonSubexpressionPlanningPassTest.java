package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPass;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionPlanningPassTest {
    private final GpuIrCommonSubexpressionPlanningPass pass = new GpuIrCommonSubexpressionPlanningPass();

    @Test
    void moduleRegistersPlanningPassThroughServiceLoader() {
        List<GpuIrPass> passes = ServiceLoader.load(GpuIrPass.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertTrue(passes.stream().anyMatch(GpuIrCommonSubexpressionPlanningPass.class::isInstance));
    }

    @Test
    void runBuildsPlansWithoutMutatingIr() {
        GpuIrBinary firstExpression = new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"));
        GpuIrBinary secondExpression = new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"));
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", firstExpression),
                new GpuIrVariableDeclaration("int", "second", secondExpression)
        ));
        GpuIrPassContext context = context(method(irMethod));

        GpuIrCommonSubexpressionRewritePlanReport report = pass.plan(context);

        assertEquals(1, report.plans().size());
        assertEquals("__gpu_cse_0", report.plans().getFirst().temporaryName());
        assertEquals(irMethod.statements(), context.method().irMethod().statements());
        assertDoesNotThrow(() -> pass.run(context));
    }

    @Test
    void rewriteReturnsCompiledMethodCopyWithoutMutatingContext() {
        GpuIrBinary firstExpression = new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"));
        GpuIrBinary secondExpression = new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"));
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", firstExpression),
                new GpuIrVariableDeclaration("int", "second", secondExpression)
        ));
        GpuIrCompiledMethod compiledMethod = method(irMethod);
        GpuIrPassContext context = context(compiledMethod);

        GpuIrCompiledMethod rewritten = pass.rewrite(context);

        assertEquals(compiledMethod.parsedMethod(), rewritten.parsedMethod());
        assertEquals(compiledMethod.emittedName(), rewritten.emittedName());
        assertEquals(compiledMethod.helperDependencies(), rewritten.helperDependencies());
        assertEquals(3, rewritten.irMethod().statements().size());
        GpuIrVariableDeclaration temp = (GpuIrVariableDeclaration) rewritten.irMethod().statements().getFirst();
        assertEquals("__gpu_cse_0", temp.name());
        assertEquals(irMethod.statements(), context.method().irMethod().statements());
    }

    @Test
    void rewriteUsesParameterTypesForAssignmentValueTemporaryDeclarations() {
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        GpuIrPassContext context = context(method(irMethod));

        GpuIrCompiledMethod rewritten = pass.rewrite(context);

        assertEquals(3, rewritten.irMethod().statements().size());
        GpuIrVariableDeclaration temp = (GpuIrVariableDeclaration) rewritten.irMethod().statements().getFirst();
        assertEquals("int", temp.typeName());
        assertEquals("__gpu_cse_0", temp.name());
        assertEquals(irMethod.statements(), context.method().irMethod().statements());
    }

    @Test
    void rewriteAvoidsTemporaryNameCollisionsWithParameters() {
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")))
        ));
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(
                        new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("y", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("__gpu_cse_0", "int", GpuAddressSpace.PRIVATE, false, List.of())
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
        GpuIrPassContext context = context(new GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_kernel", List.of()));

        GpuIrCompiledMethod rewritten = pass.rewrite(context);

        GpuIrVariableDeclaration temp = (GpuIrVariableDeclaration) rewritten.irMethod().statements().getFirst();
        assertEquals("__gpu_cse_1", temp.name());
    }

    @Test
    void diagnosticModeDoesNotFailOnSkippedCandidates() {
        GpuIrPassContext context = context(method(methodWithUnstableCandidate()));

        assertDoesNotThrow(() -> pass.run(context));
    }

    @Test
    void strictModeFailsOnSkippedCandidates() {
        GpuIrCommonSubexpressionPlanningPass strictPass = new GpuIrCommonSubexpressionPlanningPass(
                GpuIrCommonSubexpressionScanner.optimizerFocused(),
                new GpuIrCommonSubexpressionRewritePlanner(),
                GpuIrCommonSubexpressionPlanningMode.STRICT_FAIL_ON_SKIPPED_CANDIDATES
        );
        GpuIrPassContext context = context(method(methodWithUnstableCandidate()));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> strictPass.run(context));

        assertTrue(exception.getMessage().contains("MUTATED_BETWEEN_OCCURRENCES"));
        assertTrue(exception.getMessage().contains("LOCAL_REUSE"));
        assertTrue(exception.getMessage().contains("STRAIGHT_LINE"));
        assertTrue(exception.getMessage().contains("stmt[0].initializer"));
        assertTrue(exception.getMessage().contains("stmt[2].initializer"));
    }

    private GpuIrMethod methodWithUnstableCandidate() {
        return new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1")))
        ));
    }

    private GpuIrPassContext context(GpuIrCompiledMethod method) {
        return new GpuIrPassContext(method, List.of(), List.of(), true);
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(
                        new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("y", "int", GpuAddressSpace.PRIVATE, false, List.of())
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
        return new GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_kernel", List.of());
    }
}
