package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReportTest {
    @Test
    void reportsLiteralBoundariesForNestedArithmetic() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrLiteral("1"))
                )),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+",
                        new GpuIrBinary("+", new GpuIrLiteral("1"), new GpuIrVariableRef("x")),
                        new GpuIrVariableRef("y")
                ))
        ));

        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport report =
                GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.from(compiledMethod(method));
        Map<String, String> fields = report.artifactFields("cseBoundary");

        assertTrue(report.hasBlockedCandidates());
        assertEquals(2, report.blockedCandidateCount());
        assertEquals(2, report.literalOperandCount());
        assertEquals(0, report.castOperandCount());
        assertEquals(Map.of("literalOperand", 2L), report.blockedReasonCounts());
        assertEquals("2", fields.get("cseBoundaryBlockedCandidates"));
        assertEquals("2", fields.get("cseBoundaryLiteralOperands"));
        assertEquals("0", fields.get("cseBoundaryCastOperands"));
        assertEquals("{literalOperand=2}", fields.get("cseBoundaryBlockedReasonCounts"));
        assertEquals("literalOperand", fields.get("cseBoundaryFirstBlockedReason"));
        assertEquals("int,int,int", fields.get("cseBoundaryFirstBlockedOperandTypes"));
        assertEquals("1", fields.get("cseBoundaryFirstBlockedLiteralSources"));
        assertTrue(report.summary().contains("literalOperands=2"));
    }

    @Test
    void reportsCastBoundariesWithSourceAndTargetTypes() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrCast("int", new GpuIrVariableRef("z")))
                )),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+",
                        new GpuIrBinary("+", new GpuIrCast("int", new GpuIrVariableRef("w")), new GpuIrVariableRef("x")),
                        new GpuIrVariableRef("y")
                ))
        ));

        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport report =
                GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.from(compiledMethod(method));
        Map<String, String> fields = report.artifactFields("cseBoundary");

        assertEquals(2, report.blockedCandidateCount());
        assertEquals(0, report.literalOperandCount());
        assertEquals(2, report.castOperandCount());
        assertEquals(Map.of("castOperand", 2L), report.blockedReasonCounts());
        assertEquals("castOperand", fields.get("cseBoundaryFirstBlockedReason"));
        assertEquals("int", fields.get("cseBoundaryFirstBlockedCastTargets"));
        assertEquals("long", fields.get("cseBoundaryFirstBlockedCastSourceTypes"));
        assertTrue(fields.get("cseBoundaryFirstBlockedSummary").contains("castSourceTypes=long"));
    }

    @Test
    void reportsMixedLiteralAndCastBoundariesAsOneReason() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrLiteral("1"), new GpuIrCast("int", new GpuIrVariableRef("z")))
                )),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+",
                        new GpuIrBinary("+", new GpuIrCast("int", new GpuIrVariableRef("w")), new GpuIrLiteral("2")),
                        new GpuIrVariableRef("y")
                ))
        ));

        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport report =
                GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.from(compiledMethod(method));
        Map<String, String> fields = report.artifactFields("cseBoundary");

        assertEquals(2, report.blockedCandidateCount());
        assertEquals(2, report.literalOperandCount());
        assertEquals(2, report.castOperandCount());
        assertEquals(Map.of("literalAndCastOperands", 2L), report.blockedReasonCounts());
        assertEquals("literalAndCastOperands", fields.get("cseBoundaryFirstBlockedReason"));
        assertEquals("1", fields.get("cseBoundaryFirstBlockedLiteralSources"));
        assertEquals("int", fields.get("cseBoundaryFirstBlockedCastTargets"));
        assertEquals("long", fields.get("cseBoundaryFirstBlockedCastSourceTypes"));
        assertTrue(fields.get("cseBoundaryFirstBlockedSummary").contains("literalSources=1"));
        assertTrue(fields.get("cseBoundaryFirstBlockedSummary").contains("castTargets=int"));
    }

    @Test
    void reportsNoBoundaryForReferenceOnlyNestedArithmetic() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("z"))
                ))
        ));

        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport report =
                GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.from(compiledMethod(method));

        assertFalse(report.hasBlockedCandidates());
        assertEquals(0, report.blockedCandidateCount());
        assertEquals("{}", report.artifactFields().get("cseSimpleArithmeticNumericBoundaryBlockedReasonCounts"));
        assertThrows(UnsupportedOperationException.class, () -> report.artifactFields().put("x", "y"));
    }

    private GpuIrCompiledMethod compiledMethod(GpuIrMethod irMethod) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(
                        new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("y", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("z", "long", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("w", "long", GpuAddressSpace.PRIVATE, false, List.of())
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
