package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuIrAutoVectorizationNoCandidateShapeAnalyzerTest {
    private final GpuIrAutoVectorizationNoCandidateShapeAnalyzer analyzer =
            new GpuIrAutoVectorizationNoCandidateShapeAnalyzer();

    @Test
    void bucketsScalarOnlyMethodsSeparatelyFromArrayWork() {
        GpuIrMethod method = new GpuIrMethod("scalar", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrLiteral("1")),
                new GpuIrAssignment(new GpuIrVariableRef("value"), new GpuIrLiteral("2"))
        ));

        assertEquals(List.of("scalarOnlyMethod"), analyzer.buckets(method));

        GpuIrAutoVectorizationNoCandidateShapeReport report = analyzer.report(method);
        assertEquals("scalarOnlyMethod", report.firstExample().orElseThrow().bucket());
        assertEquals("stmt[0]", report.firstExample().orElseThrow().location());
        assertEquals("scalar@stmt[0]: method only contains scalar statements and no array lane work",
                report.firstExample().orElseThrow().artifactValue());
    }

    @Test
    void bucketsArrayWorkWithoutAnyLoop() {
        GpuIrMethod method = new GpuIrMethod("arrayWork", List.of(
                new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrLiteral("0")),
                        new GpuIrArrayAccess("input", new GpuIrLiteral("0"))
                )
        ));

        assertEquals(List.of("arrayWorkWithoutLoop"), analyzer.buckets(method));

        GpuIrAutoVectorizationNoCandidateShapeReport report = analyzer.report(method);
        assertEquals("arrayWorkWithoutLoop", report.firstExample().orElseThrow().bucket());
        assertEquals("stmt[0].target", report.firstExample().orElseThrow().location());
    }

    @Test
    void bucketsWhileOnlyLoopsAsNoForLoop() {
        GpuIrMethod method = new GpuIrMethod("whileOnly", List.of(new GpuIrWhileLoop(
                new GpuIrVariableRef("running"),
                List.of(new GpuIrAssignment(new GpuIrVariableRef("x"), new GpuIrLiteral("1")))
        )));

        assertEquals(List.of("noForLoop"), analyzer.buckets(method));

        GpuIrAutoVectorizationNoCandidateShapeReport report = analyzer.report(method);
        assertEquals("noForLoop", report.firstExample().orElseThrow().bucket());
        assertEquals("stmt[0]", report.firstExample().orElseThrow().location());
    }

    @Test
    void bucketsUnsupportedForLoopShapeBeforeLaneCountChecks() {
        GpuIrMethod method = new GpuIrMethod("unsupportedLoop", List.of(new GpuIrForLoop(
                new GpuIrVariableDeclaration("long", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral("4")),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("input", new GpuIrVariableRef("i"))
                ))
        )));

        assertEquals(List.of("unsupportedLoopShape"), analyzer.buckets(method));

        GpuIrAutoVectorizationNoCandidateShapeReport report = analyzer.report(method);
        assertEquals("unsupportedLoopShape", report.firstExample().orElseThrow().bucket());
        assertEquals("stmt[0]", report.firstExample().orElseThrow().location());
    }

    @Test
    void bucketsFixedLoopsWithUnsupportedLaneCounts() {
        GpuIrMethod method = new GpuIrMethod("unsupportedLaneCount", List.of(fixedWidthLoop(5, List.of(
                new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("input", new GpuIrVariableRef("i"))
                )
        ))));

        assertEquals(List.of("noFixedWidthLaneLoop"), analyzer.buckets(method));

        GpuIrAutoVectorizationNoCandidateShapeReport report = analyzer.report(method);
        assertEquals("noFixedWidthLaneLoop", report.firstExample().orElseThrow().bucket());
        assertEquals("stmt[0]", report.firstExample().orElseThrow().location());
    }

    @Test
    void bucketsSupportedLaneLoopsWithoutLaneArrayAssignment() {
        GpuIrMethod method = new GpuIrMethod("noLaneArrayAssignment", List.of(fixedWidthLoop(4, List.of(
                new GpuIrAssignment(new GpuIrVariableRef("sum"), new GpuIrBinary("+", new GpuIrVariableRef("sum"), new GpuIrVariableRef("i")))
        ))));

        assertEquals(List.of("noLaneArrayAssignment"), analyzer.buckets(method));

        GpuIrAutoVectorizationNoCandidateShapeReport report = analyzer.report(method);
        assertEquals("noLaneArrayAssignment", report.firstExample().orElseThrow().bucket());
        assertEquals("stmt[0]", report.firstExample().orElseThrow().location());
    }

    private GpuIrForLoop fixedWidthLoop(int endExclusive, List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral(Integer.toString(endExclusive))),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                body
        );
    }
}
