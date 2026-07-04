package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuIrValidationModeTest {
    @Test
    void parsesDisabledAliases() {
        assertEquals(GpuIrValidationMode.OFF, GpuIrValidationMode.parse(null));
        assertEquals(GpuIrValidationMode.OFF, GpuIrValidationMode.parse(""));
        assertEquals(GpuIrValidationMode.OFF, GpuIrValidationMode.parse("off"));
        assertEquals(GpuIrValidationMode.OFF, GpuIrValidationMode.parse("disabled"));
    }

    @Test
    void parsesDiagnosticAliases() {
        assertEquals(GpuIrValidationMode.DIAGNOSTIC, GpuIrValidationMode.parse("diagnostic"));
        assertEquals(GpuIrValidationMode.DIAGNOSTIC, GpuIrValidationMode.parse("diagnostic-only"));
        assertEquals(GpuIrValidationMode.DIAGNOSTIC, GpuIrValidationMode.parse("enabled"));
    }

    @Test
    void parsesStrictAliases() {
        assertEquals(GpuIrValidationMode.STRICT_SAFETY, GpuIrValidationMode.parse("strictSafety"));
        assertEquals(GpuIrValidationMode.STRICT_SAFETY, GpuIrValidationMode.parse("strict-safety"));
        assertEquals(GpuIrValidationMode.STRICT_OPTIMIZER, GpuIrValidationMode.parse("strictOptimizer"));
        assertEquals(GpuIrValidationMode.STRICT_OPTIMIZER, GpuIrValidationMode.parse("strict-optimizer"));
    }

    @Test
    void rejectsUnknownModeWithSupportedValuesHint() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrValidationMode.parse("strict")
        );

        assertEquals(
                "Unsupported JavaToGpu IR validation mode: strict; expected off, diagnostic, strictSafety, or strictOptimizer",
                exception.getMessage()
        );
    }

    @Test
    void parsesDiagnosticPolicyAliases() {
        assertEquals(GpuIrValidationDiagnosticPolicy.SUMMARY, GpuIrValidationDiagnosticPolicy.parse(null));
        assertEquals(GpuIrValidationDiagnosticPolicy.SUMMARY, GpuIrValidationDiagnosticPolicy.parse("summary"));
        assertEquals(GpuIrValidationDiagnosticPolicy.SUMMARY, GpuIrValidationDiagnosticPolicy.parse("compact"));
        assertEquals(GpuIrValidationDiagnosticPolicy.QUIET, GpuIrValidationDiagnosticPolicy.parse("quiet"));
        assertEquals(GpuIrValidationDiagnosticPolicy.QUIET, GpuIrValidationDiagnosticPolicy.parse("none"));
        assertEquals(GpuIrValidationDiagnosticPolicy.DETAILED, GpuIrValidationDiagnosticPolicy.parse("detailed"));
        assertEquals(GpuIrValidationDiagnosticPolicy.DETAILED, GpuIrValidationDiagnosticPolicy.parse("verbose"));
    }

    @Test
    void rejectsUnknownDiagnosticPolicyWithSupportedValuesHint() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrValidationDiagnosticPolicy.parse("chatty")
        );

        assertEquals(
                "Unsupported JavaToGpu IR validation diagnostic policy: chatty; expected quiet, summary, or detailed",
                exception.getMessage()
        );
    }
}
