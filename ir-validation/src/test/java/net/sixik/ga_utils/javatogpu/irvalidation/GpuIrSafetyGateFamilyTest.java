package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrSafetyGateFamilyTest {
    @Test
    void classifiesKnownHelperSafetyMessages() {
        assertFamily(
                "mutable helper argument target for jtg_write_helper must reference declared storage directly",
                GpuIrSafetyGateFamily.HELPER_MUTABLE_STORAGE_OPAQUE_ARGUMENT,
                "safety.helperMutableStorageOpaqueArgument"
        );
        assertFamily(
                "read-only storage cannot be used as mutable helper argument target for jtg_write_helper: input",
                GpuIrSafetyGateFamily.HELPER_MUTABLE_STORAGE_READ_ONLY_ARGUMENT,
                "safety.helperMutableStorageReadOnlyArgument"
        );
        assertFamily(
                "helper call argument count mismatch for jtg_write_helper: expected 1 but got 0",
                GpuIrSafetyGateFamily.HELPER_ARGUMENT_COUNT_MISMATCH,
                "safety.helperArgumentCountMismatch"
        );
        assertFamily(
                "type mismatch in helper argument flag for jtg_flag_helper: expected boolean but got int",
                GpuIrSafetyGateFamily.HELPER_ARGUMENT_TYPE_MISMATCH,
                "safety.helperArgumentTypeMismatch"
        );
    }

    @Test
    void fallsBackForUnclassifiedSafetyMessages() {
        GpuIrSafetyGateFamily family = GpuIrSafetyGateFamily.fromSafetyError("unknown variable reference: missing");

        assertEquals(GpuIrSafetyGateFamily.GENERIC_ERROR, family);
        assertEquals("safety.error", family.artifactValue());
        assertFalse(family.isTyped());
    }

    @Test
    void rejectsBlankDescriptorsAndMessages() {
        assertThrows(NullPointerException.class, () -> GpuIrSafetyGateFamily.fromSafetyError(null));
        assertThrows(IllegalArgumentException.class, () -> GpuIrSafetyGateFamily.fromSafetyError(" "));
    }

    private void assertFamily(String message, GpuIrSafetyGateFamily expectedFamily, String expectedArtifactValue) {
        GpuIrSafetyGateFamily family = GpuIrSafetyGateFamily.fromSafetyError(message);

        assertEquals(expectedFamily, family);
        assertEquals(expectedArtifactValue, family.artifactValue());
        assertTrue(family.isTyped());
    }
}
