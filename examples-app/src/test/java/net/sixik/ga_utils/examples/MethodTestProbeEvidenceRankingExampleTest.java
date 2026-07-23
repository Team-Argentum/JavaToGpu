package net.sixik.ga_utils.examples;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodTestProbeEvidenceRankingExampleTest {

    @Test
    void rendersPersistentProbeEvidenceRanking() throws IOException {
        Path cacheDirectory = Files.createTempDirectory("javatogpu-method-test-probe-evidence-test");

        String output = MethodTestProbeEvidenceRankingExample.renderEvidenceRanking(cacheDirectory);

        assertTrue(output.contains("Method test probe evidence ranking example"));
        assertTrue(output.contains("Warm-up status: passed"));
        assertTrue(output.contains("Recorded probe passed: true"));
        assertTrue(output.contains("Recorded probe cache hit: false"));
        assertTrue(output.contains("Selection helper status: selected"));
        assertTrue(output.contains("Runtime method-test probe mode: cache-only"));
        assertTrue(output.contains("Selected device: Intel Integrated"));
        assertTrue(output.contains("Policy status: active"));
        assertTrue(output.contains("Integrated evidence: passed"));
        assertTrue(output.contains("Discrete evidence: missing"));
        assertTrue(output.contains("Struct fixture example"));
        assertTrue(output.contains("Struct binding ready: true"));
        assertTrue(output.contains("Struct argument kind: java-struct-array"));
        assertTrue(output.contains("Struct output kind: java-struct-array"));
        assertTrue(output.contains("Struct reference passed: true"));
        assertTrue(output.contains("[0].x=2.0"));
        assertTrue(output.contains("[1].y=8.0"));
        assertTrue(output.contains("passed cached selection-probe evidence boosts"));
    }
}
