package net.sixik.ga_utils.javatogpu.runtime.opencl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClImageWorkflowTest {

    @Test
    void validatesRgbaElementCount() {
        assertEquals(
                8,
                OpenClImageWorkflow.requireRgbaElementCount("rgbaInput", 2, 1, 8)
        );
    }

    @Test
    void rejectsInvalidImageDimensions() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClImageWorkflow.requireRgbaElementCount("rgbaInput", 0, 1, 0)
        );

        assertTrue(exception.getMessage().contains("dimensions must be positive"), exception.getMessage());
    }

    @Test
    void rejectsMismatchedRgbaElementCount() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OpenClImageWorkflow.requireRgbaElementCount("rgbaInput", 2, 1, 7)
        );

        assertTrue(exception.getMessage().contains("expected 8 but found 7"), exception.getMessage());
    }
}
