package net.sixik.ga_utils.javatogpu.frontend.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuDiagnosticRendererTest {
    private final GpuDiagnosticRenderer renderer = new GpuDiagnosticRenderer();

    @Test
    void rendersRustLikeDiagnosticWithPrimaryAndSecondaryLabels() {
        GpuSourceDiagnostic diagnostic = new GpuSourceDiagnostic(
                "JTG-INTRINSIC-001",
                "unsupported intrinsic expression shape",
                new GpuSourceSpan("Demo.java", 3, 19, 3, 26),
                List.of(
                        GpuDiagnosticLabel.primary(
                                new GpuSourceSpan("Demo.java", 3, 19, 3, 26),
                                "intrinsic call is valid"
                        ),
                        GpuDiagnosticLabel.secondary(
                                new GpuSourceSpan("Demo.java", 3, 30, 3, 34),
                                "surrounding division is not supported here yet"
                        )
                ),
                List.of("move the division into a supported helper or keep this validation in diagnostic mode")
        );

        String rendered = renderer.render(diagnostic, List.of(
                "class Demo {",
                "    void kernel(float x) {",
                "        float v = GPU.sin(x) / 2.0f;",
                "    }",
                "}"
        ));

        assertTrue(rendered.contains("error[JTG-INTRINSIC-001]: unsupported intrinsic expression shape"));
        assertTrue(rendered.contains("--> Demo.java:3:19"));
        assertTrue(rendered.contains("3 |         float v = GPU.sin(x) / 2.0f;"));
        assertTrue(rendered.contains("^^^^^^^^ intrinsic call is valid"));
        assertTrue(rendered.contains("----- surrounding division is not supported here yet"));
        assertTrue(rendered.contains("= help: move the division into a supported helper"));
    }

    @Test
    void validatesSpansAndDiagnosticMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuSourceSpan("Demo.java", 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> GpuSourceDiagnostic.error(
                "",
                "message",
                GpuSourceSpan.point("Demo.java", 1, 1),
                "label",
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuDiagnosticLabel(
                GpuSourceSpan.point("Demo.java", 1, 1),
                "",
                true
        ));
    }

    @Test
    void exposesStableLocationAndArtifactFriendlyValues() {
        GpuSourceSpan span = new GpuSourceSpan("Demo.java", 7, 11, 7, 14);

        assertEquals("Demo.java:7:11", span.location());
        assertEquals(4, span.singleLineLength());
        assertEquals(Map.of("location", "Demo.java:7:11").get("location"), span.location());
    }
}
