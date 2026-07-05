package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;

import java.util.List;
import java.util.function.Function;

/**
 * Shared builders for opt-in validation-rule tests.
 */
final class GpuIrOptimizationValidationRuleTestFixtures {
    private GpuIrOptimizationValidationRuleTestFixtures() {
    }

    static GpuIrOptimizationValidationReport validate(GpuIrMethod irMethod) {
        return new GpuIrOptimizationValidationPipeline().validate(new GpuIrPassContext(
                method(irMethod),
                List.of(),
                List.of(),
                true
        ));
    }

    static GpuIrOptimizationValidationReport validationReport(String methodName) {
        return validate(new GpuIrMethod(methodName, List.of()));
    }

    static GpuIrForLoop fixedWidthLoop(int endExclusive, List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral(Integer.toString(endExclusive))),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                body
        );
    }

    static GpuIrOptimizationValidationRule rule(
            String id,
            Function<GpuIrOptimizationValidationRuleContext, GpuIrOptimizationValidationRuleResult> evaluator
    ) {
        return new GpuIrOptimizationValidationRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public GpuIrOptimizationValidationRuleResult evaluate(GpuIrOptimizationValidationRuleContext context) {
                return evaluator.apply(context);
            }
        };
    }

    static GpuIrCompiledMethod method(GpuIrMethod irMethod) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(
                        new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("left", "int[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("out", "int[]", GpuAddressSpace.GLOBAL, false, List.of())
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
        return new GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_" + irMethod.name(), List.of());
    }
}
