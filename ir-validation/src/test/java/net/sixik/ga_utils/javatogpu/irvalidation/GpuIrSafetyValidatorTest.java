package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPass;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrSafetyValidatorTest {
    private final GpuIrSafetyValidator validator = new GpuIrSafetyValidator();

    @Test
    void moduleRegistersValidatorThroughServiceLoader() {
        List<GpuIrPass> passes = ServiceLoader.load(GpuIrPass.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertTrue(passes.stream().anyMatch(GpuIrSafetyValidator.class::isInstance));
    }

    @Test
    void acceptsValidVariableArrayAndLoopReferences() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "id", new GpuIrLiteral("0")),
                new GpuIrForLoop(
                        new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                        new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral("4")),
                        new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                        List.of(
                                new GpuIrAssignment(
                                        new GpuIrArrayAccess("output", new GpuIrVariableRef("i")),
                                        new GpuIrArrayAccess("input", new GpuIrVariableRef("i"))
                                ),
                                new GpuIrContinue()
                        )
                ),
                new GpuIrReturn(null)
        )));

        assertDoesNotThrow(() -> validator.run(context(method)));
    }

    @Test
    void rejectsUnknownVariableReferences() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrArrayAccess("output", new GpuIrLiteral("0")), new GpuIrVariableRef("missing"))
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("unknown variable reference: missing"));
    }

    @Test
    void rejectsDuplicateDeclarationsInSameScope() {
        GpuIrCompiledMethod method = method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("2"))
        )));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> validator.run(context(method)));

        assertTrue(exception.getMessage().contains("duplicate local declaration: value"));
    }

    @Test
    void rejectsContinueAndBreakOutsideValidControlFlow() {
        GpuIrCompiledMethod continueMethod = method(new GpuIrMethod("continueKernel", List.of(new GpuIrContinue())));
        GpuIrCompiledMethod breakMethod = method(new GpuIrMethod("breakKernel", List.of(new GpuIrBreak())));

        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(continueMethod)))
                .getMessage().contains("continue used outside loop"));
        assertTrue(assertThrows(GpuIrPassException.class, () -> validator.run(context(breakMethod)))
                .getMessage().contains("break used outside loop or switch"));
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
                        new ParsedGpuParameter("input", "float[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("output", "float[]", GpuAddressSpace.GLOBAL, false, List.of())
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
