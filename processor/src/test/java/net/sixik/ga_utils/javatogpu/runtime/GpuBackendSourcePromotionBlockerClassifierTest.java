package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuBackendSourcePromotionBlockerClassifierTest {

    @Test
    void classifiesPromotionDiagnosticsIntoBackendNeutralFamilies() {
        assertEquals(
                GpuBackendSourcePromotionBlockerClassifier.RECONSTRUCTION,
                GpuBackendSourcePromotionBlockerClassifier.classify(
                        "backend source must be reconstructed from IrGpu before promotion review"
                )
        );
        assertEquals(
                GpuBackendSourcePromotionBlockerClassifier.RECONSTRUCTION,
                GpuBackendSourcePromotionBlockerClassifier.classify(
                        "backend source payload must be available before promotion review"
                )
        );
        assertEquals(
                GpuBackendSourcePromotionBlockerClassifier.SOURCE_PARITY,
                GpuBackendSourcePromotionBlockerClassifier.classify(
                        "reconstructed source must match descriptor source before promotion review"
                )
        );
        assertEquals(
                GpuBackendSourcePromotionBlockerClassifier.SOURCE_PARITY,
                GpuBackendSourcePromotionBlockerClassifier.classify(
                        "source parity must be checked before promotion review"
                )
        );
        assertEquals(
                GpuBackendSourcePromotionBlockerClassifier.RUNTIME_EQUIVALENCE,
                GpuBackendSourcePromotionBlockerClassifier.classify(
                        "runtime equivalence must execute and pass before backend source promotion"
                )
        );
        assertEquals(
                GpuBackendSourcePromotionBlockerClassifier.FALLBACK_CLEAN,
                GpuBackendSourcePromotionBlockerClassifier.classify(
                        "fallback evidence must be clean before backend source promotion"
                )
        );
        assertEquals(
                GpuBackendSourcePromotionBlockerClassifier.OTHER,
                GpuBackendSourcePromotionBlockerClassifier.classify("unmapped vendor diagnostic")
        );
    }

    @Test
    void nullAndBlankDiagnosticsFallBackToOther() {
        assertEquals(
                GpuBackendSourcePromotionBlockerClassifier.OTHER,
                GpuBackendSourcePromotionBlockerClassifier.classify(null)
        );
        assertEquals(
                GpuBackendSourcePromotionBlockerClassifier.OTHER,
                GpuBackendSourcePromotionBlockerClassifier.classify("  ")
        );
    }
}
