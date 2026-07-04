package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationMode;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProvider;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRequest;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRunner;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationProviderTest {
    private final GpuIrOptimizationValidationProvider provider = new GpuIrOptimizationValidationProvider();

    @Test
    void moduleRegistersValidationProviderThroughServiceLoader() {
        List<GpuIrValidationProvider> providers = ServiceLoader.load(GpuIrValidationProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertTrue(providers.stream().anyMatch(GpuIrOptimizationValidationProvider.class::isInstance));
    }

    @Test
    void validationRunnerLoadsProviderThroughServiceLoader() {
        GpuIrValidationRunner runner = GpuIrValidationRunner.loadFromServiceLoader(GpuIrValidationMode.STRICT_SAFETY);

        GpuIrPassException exception = assertThrows(
                GpuIrPassException.class,
                () -> runner.run(brokenMethod(), List.of(), List.of())
        );

        assertTrue(exception.getMessage().contains("unknown variable reference: missing"));
    }

    @Test
    void diagnosticModeDoesNotFailBuildForSafetyDiagnostics() {
        GpuIrValidationRequest request = request(GpuIrValidationMode.DIAGNOSTIC, brokenMethod());

        assertDoesNotThrow(() -> provider.validate(request));
    }

    @Test
    void strictSafetyModeFailsBuildForSafetyDiagnostics() {
        GpuIrValidationRequest request = request(GpuIrValidationMode.STRICT_SAFETY, brokenMethod());

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> provider.validate(request));

        assertTrue(exception.getMessage().contains("unknown variable reference: missing"));
    }

    @Test
    void offModeDoesNotRunValidation() {
        GpuIrValidationRequest request = request(GpuIrValidationMode.OFF, brokenMethod());

        assertDoesNotThrow(() -> provider.validate(request));
    }

    private GpuIrCompiledMethod brokenMethod() {
        return method(new GpuIrMethod("broken", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing")),
                new GpuIrAssignment(new GpuIrVariableRef("value"), new GpuIrLiteral("1"))
        )));
    }

    private GpuIrValidationRequest request(GpuIrValidationMode mode, GpuIrCompiledMethod method) {
        return new GpuIrValidationRequest(method, List.of(), List.of(), true, mode);
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod) {
        return new GpuIrCompiledMethod(parsedMethod(irMethod.name()), irMethod, "jtg_" + irMethod.name(), List.of());
    }

    private ParsedGpuMethod parsedMethod(String name) {
        return new ParsedGpuMethod(
                "Owner",
                "test.Owner",
                name,
                "void",
                List.of(),
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
    }
}
