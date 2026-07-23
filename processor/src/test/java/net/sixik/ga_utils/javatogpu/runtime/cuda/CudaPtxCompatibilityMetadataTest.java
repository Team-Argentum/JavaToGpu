package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaPtxCompatibilityMetadataTest {

    @Test
    void parsesPtxVersionAndSmTarget() {
        CudaPtxCompatibilityMetadata metadata = CudaPtxCompatibilityMetadata.fromSource(
                ".version 8.0\n.target sm_86\n.address_size 64\n"
        );
        Map<String, String> fields = metadata.artifactFields("test.ptx");

        assertEquals("8.0", metadata.ptxVersion());
        assertEquals("sm", metadata.targetKind());
        assertEquals("sm_86", metadata.targetArchitecture());
        assertEquals("8.6", metadata.targetComputeCapability());
        assertEquals(8, metadata.targetComputeCapabilityMajor());
        assertEquals(6, metadata.targetComputeCapabilityMinor());
        assertTrue(metadata.targetComputeCapabilityKnown());
        assertEquals("sm_86", fields.get("runtime.cuda.ptxCompatibility.target.architecture"));
    }

    @Test
    void parsesFutureArchitectureSuffixWithoutLosingComputeCapability() {
        CudaPtxCompatibilityMetadata metadata = CudaPtxCompatibilityMetadata.fromSource(
                ".version 8.7\n.target sm_90a, texmode_independent\n.address_size 64\n"
        );

        assertEquals("8.7", metadata.ptxVersion());
        assertEquals("sm_90a", metadata.targetArchitecture());
        assertEquals("9.0", metadata.targetComputeCapability());
    }

    @Test
    void reportsUnknownWhenPtxHeaderIsMissing() {
        CudaPtxCompatibilityMetadata metadata = CudaPtxCompatibilityMetadata.fromSource("// no PTX header");

        assertEquals("unknown", metadata.ptxVersion());
        assertEquals("unknown", metadata.targetArchitecture());
        assertEquals("unknown", metadata.targetComputeCapability());
        assertFalse(metadata.targetComputeCapabilityKnown());
    }

    @Test
    void passesKnownPtxVersionWhenDriverApiIsNewEnough() {
        CudaPtxCompatibilityMetadata metadata = CudaPtxCompatibilityMetadata.fromSource(
                ".version 8.4\n.target sm_86\n.address_size 64\n"
        );

        CudaPtxDriverCompatibility compatibility = CudaPtxDriverCompatibility.evaluate(metadata, 12040);
        Map<String, String> fields = compatibility.artifactFields("test.ptxDriver");

        assertEquals("passed", compatibility.status());
        assertTrue(compatibility.blockers().isEmpty());
        assertEquals("12.4", compatibility.driverVersion());
        assertEquals("12.4", compatibility.requiredCudaRelease());
        assertEquals("passed", fields.get("runtime.cuda.ptxDriverCompatibility.status"));
    }

    @Test
    void failsKnownPtxVersionWhenDriverApiIsTooOld() {
        CudaPtxCompatibilityMetadata metadata = CudaPtxCompatibilityMetadata.fromSource(
                ".version 8.4\n.target sm_86\n.address_size 64\n"
        );

        CudaPtxDriverCompatibility compatibility = CudaPtxDriverCompatibility.evaluate(metadata, 12030);

        assertEquals("failed", compatibility.status());
        assertEquals("12.3", compatibility.driverVersion());
        assertEquals("12.4", compatibility.requiredCudaRelease());
        assertTrue(compatibility.blockers().contains(
                "cuda-driver-ptx-version-unsupported:ptx-8.4:driver-12.3:requires-12.4"
        ));
    }

    @Test
    void skipsUnknownFuturePtxVersionInsteadOfGuessing() {
        CudaPtxCompatibilityMetadata metadata = CudaPtxCompatibilityMetadata.fromSource(
                ".version 99.0\n.target sm_86\n.address_size 64\n"
        );

        CudaPtxDriverCompatibility compatibility = CudaPtxDriverCompatibility.evaluate(metadata, 13030);

        assertEquals("skipped", compatibility.status());
        assertTrue(compatibility.blockers().isEmpty());
        assertEquals("99.0", compatibility.ptxVersion());
    }
}
