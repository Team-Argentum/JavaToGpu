package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProvider;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationReportEntry;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRequest;

import java.util.Map;

/**
 * Read-only IR validation provider used by the hardware-free examples-app harness.
 */
public final class ExampleIrValidationProvider implements GpuIrValidationProvider {
    @Override
    public void validate(GpuIrValidationRequest request) {
        request.reportEntry(new GpuIrValidationReportEntry(
                "examples.ir-validation.synthetic",
                request.method().parsedMethod().name(),
                request.entryPoint(),
                Map.of(
                        "method", request.method().parsedMethod().name(),
                        "entryPoint", Boolean.toString(request.entryPoint()),
                        "statementCount", Integer.toString(request.method().irMethod().statements().size())
                )
        ));
    }

    @Override
    public String extensionId() {
        return "examples.ir-validation.synthetic";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 500;
    }
}
