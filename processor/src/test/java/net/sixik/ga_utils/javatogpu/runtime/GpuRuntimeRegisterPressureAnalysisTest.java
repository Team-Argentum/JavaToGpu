package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeRegisterPressureAnalysisTest {

    @Test
    void reportsLowPressureForSmallScalarDgpuBody() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                localArtifact(4, "float"),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureMethodEstimate estimate = report.hottestMethod().orElseThrow();
        assertEquals(64, report.budget().valueRegisterBudget());
        assertEquals(GpuRuntimeRegisterPressureLevel.LOW, estimate.level());
        assertEquals(4, estimate.localRegisters());
        assertTrue(estimate.estimatedValueRegisters() <= 32);
    }

    @Test
    void reportsModeratePressureForVectorHeavyDgpuBody() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                localArtifact(10, "Float4"),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureMethodEstimate estimate = report.hottestMethod().orElseThrow();
        assertEquals(40, estimate.localRegisters());
        assertEquals(GpuRuntimeRegisterPressureLevel.MODERATE, estimate.level());
        assertTrue(estimate.utilizationPermille() > 500);
        assertTrue(estimate.utilizationPermille() <= 750);
    }

    @Test
    void reportsCriticalPressureForIgpuPrivateArrayBody() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                privateArrayArtifact(32),
                device(GpuDeviceClassTarget.IGPU, "Intel")
        );

        GpuRuntimeRegisterPressureMethodEstimate estimate = report.hottestMethod().orElseThrow();
        assertEquals(24, report.budget().valueRegisterBudget());
        assertEquals(32, estimate.privateArrayRegisters());
        assertEquals(GpuRuntimeRegisterPressureLevel.CRITICAL, estimate.level());
        assertTrue(report.diagnostics().get(0).contains("exceeds advisory budget"));
    }

    @Test
    void releasesSequentialLocalsAfterTheirLastUse() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                sequentialReuseArtifact(40),
                device(GpuDeviceClassTarget.IGPU, "Intel")
        );

        GpuRuntimeRegisterPressureMethodEstimate estimate = report.hottestMethod().orElseThrow();
        assertEquals(40, estimate.localRegisters());
        assertEquals(1, estimate.peakLiveRegisters());
        assertTrue(estimate.estimatedValueRegisters() <= 2);
        assertEquals(GpuRuntimeRegisterPressureLevel.LOW, estimate.level());
    }

    @Test
    void doesNotTreatUnusedKernelParametersAsLiveValues() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                unusedParameterArtifact(20),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureMethodEstimate estimate = report.hottestMethod().orElseThrow();
        assertEquals(80, estimate.parameterRegisters());
        assertEquals(0, estimate.peakLiveRegisters());
        assertEquals(1, estimate.estimatedValueRegisters());
        assertEquals(GpuRuntimeRegisterPressureLevel.LOW, estimate.level());
    }

    @Test
    void mergesBranchLivenessAtTheControlFlowJoin() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                branchJoinArtifact(),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureMethodEstimate estimate = report.hottestMethod().orElseThrow();
        assertEquals(32, estimate.peakLiveRegisters());
        assertTrue(estimate.estimatedValueRegisters() >= 32);
    }

    @Test
    void keepsLoopCarriedValuesLiveAcrossTheBackEdge() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                loopCarriedArtifact(),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureMethodEstimate estimate = report.hottestMethod().orElseThrow();
        assertTrue(estimate.peakLiveRegisters() >= 25);
        assertTrue(estimate.estimatedValueRegisters() >= estimate.peakLiveRegisters());
    }

    @Test
    void resolvesShadowedLocalsToDistinctLexicalIdentities() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                shadowedLocalArtifact(),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureMethodEstimate estimate = report.hottestMethod().orElseThrow();
        assertEquals(5, estimate.localRegisters());
        assertEquals(2, estimate.scopedVariableCount());
        assertEquals(1, estimate.shadowedVariableCount());
        assertEquals(0, estimate.unresolvedReferenceCount());
        assertEquals(5, estimate.peakLiveRegisters());
    }

    @Test
    void surfacesUnresolvedTypedIrReferencesWithoutFailingAnalysis() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                unresolvedReferenceArtifact(),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureMethodEstimate estimate = report.hottestMethod().orElseThrow();
        assertEquals(1, estimate.unresolvedReferenceCount());
        assertTrue(report.diagnostics().stream().anyMatch(message ->
                message.contains("unresolved references")
        ));
        assertEquals(
                "1",
                report.artifactFields().get("registerPressure.unresolvedReference.count")
        );
    }

    @Test
    void keepsNonInlineHelperPressureInASeparateCallFrame() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                interproceduralArtifact(false, false),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureCallEstimate call = report.calls().stream()
                .filter(value -> "kernel".equals(value.callerMethod()))
                .findFirst()
                .orElseThrow();
        int callerEstimate = methodEstimate(report, "kernel").estimatedValueRegisters();
        int helperEstimate = methodEstimate(report, "helper").estimatedValueRegisters();
        assertTrue(call.resolved());
        assertTrue(!call.inline());
        assertTrue(!call.recursive());
        assertEquals(helperEstimate, call.additionalFrameRegisters());
        assertEquals(Math.max(callerEstimate, helperEstimate), call.combinedEstimatedRegisters());
    }

    @Test
    void addsInlineHelperFrameToCallerLivePressure() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                interproceduralArtifact(true, false),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureCallEstimate call = report.calls().stream()
                .filter(value -> "kernel".equals(value.callerMethod()))
                .findFirst()
                .orElseThrow();
        int callerEstimate = methodEstimate(report, "kernel").estimatedValueRegisters();
        int reusableArguments = Math.min(call.calleeParameterRegisters(), call.argumentRegisters());
        int expectedAdditional = Math.max(0, call.calleeEstimatedRegisters() - reusableArguments);
        int expectedCombined = Math.max(
                callerEstimate,
                call.callerLiveRegisters() + expectedAdditional + call.resultRegisters()
        );
        assertTrue(call.inline());
        assertEquals(expectedAdditional, call.additionalFrameRegisters());
        assertEquals(expectedCombined, call.combinedEstimatedRegisters());
        assertTrue(call.combinedEstimatedRegisters() >= callerEstimate);
    }

    @Test
    void reportsUnresolvedHelperCallsAsAdvisoryEdges() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                localArtifact(2, "float"),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureCallEstimate call = report.calls().get(0);
        assertTrue(!call.resolved());
        assertEquals("consume", call.helperName());
        assertTrue(report.diagnostics().stream().anyMatch(message ->
                message.contains("helper call is unresolved")
        ));
    }

    @Test
    void detectsRecursiveHelperCyclesWithoutExpandingFrames() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                interproceduralArtifact(false, true),
                device(GpuDeviceClassTarget.DGPU, "NVIDIA")
        );

        GpuRuntimeRegisterPressureCallEstimate recursiveCall = report.calls().stream()
                .filter(GpuRuntimeRegisterPressureCallEstimate::recursive)
                .findFirst()
                .orElseThrow();
        assertEquals("helper", recursiveCall.callerMethod());
        assertEquals("jtg_helper", recursiveCall.helperName());
        assertTrue(report.diagnostics().stream().anyMatch(message ->
                message.contains("helper recursion detected")
        ));
    }

    @Test
    void reportsUnavailableWhenTypedIrBodyIsMissing() {
        GpuRuntimeRegisterPressureReport report = GpuRuntimeRegisterPressureAnalyzer.analyze(
                untypedArtifact(),
                device(GpuDeviceClassTarget.DGPU, "AMD")
        );

        assertTrue(!report.available());
        assertEquals(GpuRuntimeRegisterPressureLevel.UNAVAILABLE, report.level());
        assertTrue(report.diagnostics().get(0).contains("no typed IrGpu method bodies"));
    }

    @Test
    void builtInPassPublishesAnalysisWithoutBecomingOptimizerProof() {
        IrGpuArtifact artifact = privateArrayArtifact(32);
        GpuRuntimeCompileRequest request = request(
                artifact,
                device(GpuDeviceClassTarget.IGPU, "Intel"),
                "production"
        );
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeRegisterPressureAnalysisPass().run(
                new GpuRuntimeIrOptimizationRequest(request, Optional.of(artifact))
        );
        GpuRuntimeIrOptimizationPassReport passReport = optimizationReport.passReports().get(0);

        assertTrue(passReport.analysisOnly());
        assertEquals(GpuRuntimeIrOptimizationStage.TARGET_PROFILE_ANALYSIS, passReport.stage());
        assertEquals("critical", passReport.proofArtifact().fields().get("registerPressure.level"));

        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global float* output) { output[0] = 1.0f; }",
                "javatogpu/test/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                optimizationReport
        );
        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);
        String analysisArtifact = dump.artifact("runtime-ir-analysis.properties");
        GpuRuntimeOptimizerDriftArtifact drift = GpuRuntimeOptimizerDriftArtifact.from(snapshot);

        assertTrue(analysisArtifact.contains("analysis.0.field.registerPressure.level=critical"));
        assertTrue(analysisArtifact.contains("analysis.0.field.registerPressure.budget=24"));
        assertTrue(analysisArtifact.contains(
                "analysis.0.field.registerPressure.analysisVersion=register-pressure-interprocedural:v4"
        ));
        assertTrue(analysisArtifact.contains("peakLiveRegisters"));
        assertEquals(0, drift.passCount());
        assertEquals(0, drift.proofArtifactCount());
        assertEquals(0, drift.optimizerFamilyCount());
        assertTrue(snapshot.productionOptimizerGate().diagnostics().contains(
                "accepted optimizer proof artifact is required before production promotion"
        ));
    }

    @Test
    void defaultRegistryIncludesRegisterPressureAnalysisBeforeLoadedPasses() {
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.loadFromServiceLoader();

        assertTrue(registry.optimizerPasses().stream().anyMatch(pass ->
                GpuRuntimeRegisterPressureAnalysisPass.PASS_ID.equals(pass.extensionId())
        ));
        assertEquals(
                GpuRuntimeIrOptimizationStage.TARGET_PROFILE_ANALYSIS,
                registry.optimizerPasses().stream()
                        .filter(pass -> GpuRuntimeRegisterPressureAnalysisPass.PASS_ID.equals(pass.extensionId()))
                        .findFirst()
                        .orElseThrow()
                        .stage()
        );
    }

    private static IrGpuArtifact localArtifact(int localCount, String typeName) {
        ArrayList<Integer> roots = new ArrayList<>();
        ArrayList<IrGpuTypedNode> nodes = new ArrayList<>();
        for (int index = 0; index < localCount; index++) {
            int declarationId = nodes.size();
            int literalId = declarationId + 1;
            roots.add(declarationId);
            nodes.add(new IrGpuTypedNode(
                    declarationId,
                    "GpuIrVariableDeclaration",
                    Map.of("typeName", typeName, "name", "value" + index),
                    Map.of("initializer", List.of(literalId))
            ));
            nodes.add(new IrGpuTypedNode(
                    literalId,
                    "GpuIrLiteral",
                    Map.of("sourceText", "1.0f"),
                    Map.of()
            ));
        }
        int returnId = nodes.size();
        int callId = returnId + 1;
        roots.add(returnId);
        nodes.add(new IrGpuTypedNode(returnId, "GpuIrReturn", Map.of(), Map.of("value", List.of(callId))));
        ArrayList<Integer> argumentIds = new ArrayList<>();
        for (int index = 0; index < localCount; index++) {
            argumentIds.add(callId + 1 + index);
        }
        nodes.add(new IrGpuTypedNode(
                callId,
                "GpuIrHelperCall",
                Map.of("helperName", "consume", "resultType", "float"),
                Map.of("arguments", argumentIds)
        ));
        for (int index = 0; index < localCount; index++) {
            nodes.add(new IrGpuTypedNode(
                    callId + 1 + index,
                    "GpuIrVariableRef",
                    Map.of("name", "value" + index),
                    Map.of()
            ));
        }
        return artifact(new IrGpuTypedBody(IrGpuTypedBody.FORMAT, roots, nodes));
    }

    private static IrGpuArtifact privateArrayArtifact(int size) {
        IrGpuTypedBody body = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0, 2),
                List.of(
                        new IrGpuTypedNode(
                                0,
                                "GpuIrPrivateArrayDeclaration",
                                Map.of("elementType", "float", "name", "scratch"),
                                Map.of("size", List.of(1))
                        ),
                        new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", Integer.toString(size)), Map.of()),
                        new IrGpuTypedNode(2, "GpuIrReturn", Map.of(), Map.of("value", List.of(3))),
                        new IrGpuTypedNode(
                                3,
                                "GpuIrArrayAccess",
                                Map.of("arrayName", "scratch"),
                                Map.of("index", List.of(4))
                        ),
                        new IrGpuTypedNode(4, "GpuIrLiteral", Map.of("sourceText", "0"), Map.of())
                )
        );
        return artifact(body);
    }

    private static IrGpuArtifact sequentialReuseArtifact(int localCount) {
        ArrayList<Integer> roots = new ArrayList<>();
        ArrayList<IrGpuTypedNode> nodes = new ArrayList<>();
        for (int index = 0; index < localCount; index++) {
            int declarationId = nodes.size();
            int literalId = declarationId + 1;
            int expressionStatementId = declarationId + 2;
            int variableId = declarationId + 3;
            String variableName = "value" + index;
            roots.add(declarationId);
            roots.add(expressionStatementId);
            nodes.add(new IrGpuTypedNode(
                    declarationId,
                    "GpuIrVariableDeclaration",
                    Map.of("typeName", "float", "name", variableName),
                    Map.of("initializer", List.of(literalId))
            ));
            nodes.add(new IrGpuTypedNode(
                    literalId,
                    "GpuIrLiteral",
                    Map.of("sourceText", "1.0f"),
                    Map.of()
            ));
            nodes.add(new IrGpuTypedNode(
                    expressionStatementId,
                    "GpuIrExpressionStatement",
                    Map.of(),
                    Map.of("expression", List.of(variableId))
            ));
            nodes.add(new IrGpuTypedNode(
                    variableId,
                    "GpuIrVariableRef",
                    Map.of("name", variableName),
                    Map.of()
            ));
        }
        return artifact(new IrGpuTypedBody(IrGpuTypedBody.FORMAT, roots, nodes));
    }

    private static IrGpuArtifact branchJoinArtifact() {
        ArrayList<Integer> roots = new ArrayList<>();
        ArrayList<IrGpuTypedNode> nodes = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            int declarationId = nodes.size();
            int literalId = declarationId + 1;
            roots.add(declarationId);
            nodes.add(new IrGpuTypedNode(
                    declarationId,
                    "GpuIrVariableDeclaration",
                    Map.of("typeName", "Float4", "name", "branchValue" + index),
                    Map.of("initializer", List.of(literalId))
            ));
            nodes.add(new IrGpuTypedNode(
                    literalId,
                    "GpuIrLiteral",
                    Map.of("sourceText", "1.0f"),
                    Map.of()
            ));
        }
        int ifId = nodes.size();
        int conditionId = ifId + 1;
        int thenStatementId = ifId + 2;
        int thenCallId = ifId + 3;
        int thenVariableStart = ifId + 4;
        int elseStatementId = thenVariableStart + 4;
        int elseCallId = elseStatementId + 1;
        int elseVariableStart = elseCallId + 1;
        roots.add(ifId);
        nodes.add(new IrGpuTypedNode(
                ifId,
                "GpuIrIf",
                Map.of(),
                Map.of(
                        "condition", List.of(conditionId),
                        "thenBranch", List.of(thenStatementId),
                        "elseBranch", List.of(elseStatementId)
                )
        ));
        nodes.add(new IrGpuTypedNode(conditionId, "GpuIrLiteral", Map.of("sourceText", "true"), Map.of()));
        nodes.add(new IrGpuTypedNode(
                thenStatementId,
                "GpuIrExpressionStatement",
                Map.of(),
                Map.of("expression", List.of(thenCallId))
        ));
        nodes.add(new IrGpuTypedNode(
                thenCallId,
                "GpuIrHelperCall",
                Map.of("helperName", "consumeThen", "resultType", "void"),
                Map.of("arguments", List.of(
                        thenVariableStart,
                        thenVariableStart + 1,
                        thenVariableStart + 2,
                        thenVariableStart + 3
                ))
        ));
        for (int index = 0; index < 4; index++) {
            nodes.add(new IrGpuTypedNode(
                    thenVariableStart + index,
                    "GpuIrVariableRef",
                    Map.of("name", "branchValue" + index),
                    Map.of()
            ));
        }
        nodes.add(new IrGpuTypedNode(
                elseStatementId,
                "GpuIrExpressionStatement",
                Map.of(),
                Map.of("expression", List.of(elseCallId))
        ));
        nodes.add(new IrGpuTypedNode(
                elseCallId,
                "GpuIrHelperCall",
                Map.of("helperName", "consumeElse", "resultType", "void"),
                Map.of("arguments", List.of(
                        elseVariableStart,
                        elseVariableStart + 1,
                        elseVariableStart + 2,
                        elseVariableStart + 3
                ))
        ));
        for (int index = 0; index < 4; index++) {
            nodes.add(new IrGpuTypedNode(
                    elseVariableStart + index,
                    "GpuIrVariableRef",
                    Map.of("name", "branchValue" + (index + 4)),
                    Map.of()
            ));
        }
        return artifact(new IrGpuTypedBody(IrGpuTypedBody.FORMAT, roots, nodes));
    }

    private static IrGpuArtifact loopCarriedArtifact() {
        ArrayList<Integer> roots = new ArrayList<>();
        ArrayList<IrGpuTypedNode> nodes = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            int declarationId = nodes.size();
            int literalId = declarationId + 1;
            roots.add(declarationId);
            nodes.add(new IrGpuTypedNode(
                    declarationId,
                    "GpuIrVariableDeclaration",
                    Map.of("typeName", "Float4", "name", "loopValue" + index),
                    Map.of("initializer", List.of(literalId))
            ));
            nodes.add(new IrGpuTypedNode(
                    literalId,
                    "GpuIrLiteral",
                    Map.of("sourceText", "1.0f"),
                    Map.of()
            ));
        }
        int counterDeclarationId = nodes.size();
        int counterLiteralId = counterDeclarationId + 1;
        int loopId = counterDeclarationId + 2;
        int conditionId = counterDeclarationId + 3;
        int bodyStatementId = counterDeclarationId + 4;
        int bodyCallId = counterDeclarationId + 5;
        int bodyVariableStart = counterDeclarationId + 6;
        roots.add(counterDeclarationId);
        roots.add(loopId);
        nodes.add(new IrGpuTypedNode(
                counterDeclarationId,
                "GpuIrVariableDeclaration",
                Map.of("typeName", "int", "name", "counter"),
                Map.of("initializer", List.of(counterLiteralId))
        ));
        nodes.add(new IrGpuTypedNode(counterLiteralId, "GpuIrLiteral", Map.of("sourceText", "1"), Map.of()));
        nodes.add(new IrGpuTypedNode(
                loopId,
                "GpuIrWhileLoop",
                Map.of(),
                Map.of("condition", List.of(conditionId), "body", List.of(bodyStatementId))
        ));
        nodes.add(new IrGpuTypedNode(
                conditionId,
                "GpuIrVariableRef",
                Map.of("name", "counter"),
                Map.of()
        ));
        nodes.add(new IrGpuTypedNode(
                bodyStatementId,
                "GpuIrExpressionStatement",
                Map.of(),
                Map.of("expression", List.of(bodyCallId))
        ));
        ArrayList<Integer> arguments = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            arguments.add(bodyVariableStart + index);
        }
        nodes.add(new IrGpuTypedNode(
                bodyCallId,
                "GpuIrHelperCall",
                Map.of("helperName", "consumeLoop", "resultType", "void"),
                Map.of("arguments", arguments)
        ));
        for (int index = 0; index < 6; index++) {
            nodes.add(new IrGpuTypedNode(
                    bodyVariableStart + index,
                    "GpuIrVariableRef",
                    Map.of("name", "loopValue" + index),
                    Map.of()
            ));
        }
        return artifact(new IrGpuTypedBody(IrGpuTypedBody.FORMAT, roots, nodes));
    }

    private static IrGpuArtifact shadowedLocalArtifact() {
        IrGpuTypedBody body = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0, 2, 8),
                List.of(
                        new IrGpuTypedNode(
                                0,
                                "GpuIrVariableDeclaration",
                                Map.of("typeName", "Float4", "name", "value"),
                                Map.of("initializer", List.of(1))
                        ),
                        new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "1.0f"), Map.of()),
                        new IrGpuTypedNode(
                                2,
                                "GpuIrIf",
                                Map.of(),
                                Map.of(
                                        "condition", List.of(3),
                                        "thenBranch", List.of(4, 6),
                                        "elseBranch", List.of()
                                )
                        ),
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "true"), Map.of()),
                        new IrGpuTypedNode(
                                4,
                                "GpuIrVariableDeclaration",
                                Map.of("typeName", "float", "name", "value"),
                                Map.of("initializer", List.of(5))
                        ),
                        new IrGpuTypedNode(5, "GpuIrLiteral", Map.of("sourceText", "2.0f"), Map.of()),
                        new IrGpuTypedNode(
                                6,
                                "GpuIrExpressionStatement",
                                Map.of(),
                                Map.of("expression", List.of(7))
                        ),
                        new IrGpuTypedNode(7, "GpuIrVariableRef", Map.of("name", "value"), Map.of()),
                        new IrGpuTypedNode(8, "GpuIrReturn", Map.of(), Map.of("value", List.of(9))),
                        new IrGpuTypedNode(9, "GpuIrVariableRef", Map.of("name", "value"), Map.of())
                )
        );
        return artifact(body);
    }

    private static IrGpuArtifact unresolvedReferenceArtifact() {
        return artifact(new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrVariableRef", Map.of("name", "missingValue"), Map.of())
                )
        ));
    }

    private static IrGpuArtifact interproceduralArtifact(boolean inline, boolean recursive) {
        List<IrGpuEntryParameter> helperParameters = List.of(
                parameter("p0"),
                parameter("p1"),
                parameter("p2"),
                parameter("p3")
        );
        IrGpuModuleMethod helperMetadata = new IrGpuModuleMethod(
                "helper",
                "jtg_helper",
                "float",
                helperParameters,
                List.of(),
                inline
        );
        IrGpuMethodBody entryBody = pressureMethodBody(
                "entry",
                "kernel",
                "jtg_kernel",
                List.of(),
                4,
                "entryValue",
                "GpuIrHelperCall",
                "jtg_helper"
        );
        IrGpuMethodBody helperBody = pressureMethodBody(
                "helper",
                "helper",
                "jtg_helper",
                List.of("p0", "p1", "p2", "p3"),
                3,
                "helperValue",
                recursive ? "GpuIrHelperCall" : "GpuIrIntrinsicCall",
                recursive ? "jtg_helper" : "native_mix"
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(helperMetadata),
                        List.of(),
                        List.of(entryBody, helperBody)
                ),
                List.of(),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuOptimizerPolicyMetadata.defaultStrict(),
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/test/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuMethodBody pressureMethodBody(
            String role,
            String methodName,
            String emittedName,
            List<String> parameterNames,
            int localCount,
            String localPrefix,
            String terminalKind,
            String targetName
    ) {
        ArrayList<Integer> roots = new ArrayList<>();
        ArrayList<IrGpuTypedNode> nodes = new ArrayList<>();
        for (int index = 0; index < localCount; index++) {
            int declarationId = nodes.size();
            int literalId = declarationId + 1;
            roots.add(declarationId);
            nodes.add(new IrGpuTypedNode(
                    declarationId,
                    "GpuIrVariableDeclaration",
                    Map.of("typeName", "Float4", "name", localPrefix + index),
                    Map.of("initializer", List.of(literalId))
            ));
            nodes.add(new IrGpuTypedNode(
                    literalId,
                    "GpuIrLiteral",
                    Map.of("sourceText", "1.0f"),
                    Map.of()
            ));
        }
        int returnId = nodes.size();
        int callId = returnId + 1;
        roots.add(returnId);
        nodes.add(new IrGpuTypedNode(returnId, "GpuIrReturn", Map.of(), Map.of("value", List.of(callId))));
        ArrayList<String> argumentNames = new ArrayList<>(parameterNames);
        for (int index = 0; index < localCount; index++) {
            argumentNames.add(localPrefix + index);
        }
        ArrayList<Integer> argumentIds = new ArrayList<>();
        for (int index = 0; index < argumentNames.size(); index++) {
            argumentIds.add(callId + 1 + index);
        }
        Map<String, String> callAttributes = "GpuIrHelperCall".equals(terminalKind)
                ? Map.of("helperName", targetName, "resultType", "float")
                : Map.of(
                        "backendName", targetName,
                        "codeTemplate", targetName + "({0})",
                        "resultType", "float"
                );
        nodes.add(new IrGpuTypedNode(
                callId,
                terminalKind,
                callAttributes,
                Map.of("arguments", argumentIds)
        ));
        for (int index = 0; index < argumentNames.size(); index++) {
            nodes.add(new IrGpuTypedNode(
                    callId + 1 + index,
                    "GpuIrVariableRef",
                    Map.of("name", argumentNames.get(index)),
                    Map.of()
            ));
        }
        IrGpuTypedBody typedBody = new IrGpuTypedBody(IrGpuTypedBody.FORMAT, roots, nodes);
        return new IrGpuMethodBody(
                role,
                methodName,
                emittedName,
                "ir-text-v1",
                "body\n  return call\n",
                typedBody,
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown(methodName)
        );
    }

    private static IrGpuEntryParameter parameter(String name) {
        return new IrGpuEntryParameter(name, "Float4", "PRIVATE", false, List.of());
    }

    private static GpuRuntimeRegisterPressureMethodEstimate methodEstimate(
            GpuRuntimeRegisterPressureReport report,
            String methodName
    ) {
        return report.methods().stream()
                .filter(method -> methodName.equals(method.methodName()))
                .findFirst()
                .orElseThrow();
    }

    private static IrGpuArtifact untypedArtifact() {
        return artifact(IrGpuTypedBody.none());
    }

    private static IrGpuArtifact unusedParameterArtifact(int parameterCount) {
        IrGpuTypedBody body = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "1.0f"), Map.of())
                )
        );
        ArrayList<IrGpuEntryParameter> parameters = new ArrayList<>();
        for (int index = 0; index < parameterCount; index++) {
            parameters.add(new IrGpuEntryParameter(
                    "input" + index,
                    "Float4",
                    "PRIVATE",
                    false,
                    List.of()
            ));
        }
        return artifact(body, parameters);
    }

    private static IrGpuArtifact artifact(IrGpuTypedBody typedBody) {
        return artifact(typedBody, List.of());
    }

    private static IrGpuArtifact artifact(
            IrGpuTypedBody typedBody,
            List<IrGpuEntryParameter> entryParameters
    ) {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "jtg_kernel",
                "ir-text-v1",
                "body\n  return 1.0f\n",
                typedBody,
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("kernel")
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "jtg_kernel", List.of(), List.of(), List.of(methodBody)),
                entryParameters,
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuOptimizerPolicyMetadata.defaultStrict(),
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/test/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static GpuRuntimeCompileRequest request(
            IrGpuArtifact artifact,
            GpuRuntimeDeviceProfile deviceProfile,
            String optimizationProfile
    ) {
        return new GpuRuntimeCompileRequest(
                new GpuKernelDescriptor(
                        "kernel",
                        "javatogpu/test/kernel.cl",
                        "__kernel void kernel(__global float* output) { output[0] = 1.0f; }",
                        "javatogpu/test/kernel.irgpu.properties",
                        List.of()
                ),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), optimizationProfile),
                deviceProfile,
                Optional.of(artifact)
        );
    }

    private static GpuRuntimeDeviceProfile device(GpuDeviceClassTarget deviceClass, String vendor) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-0",
                vendor + " test device",
                vendor,
                "driver-test",
                "OpenCL 3.0 Test",
                deviceClass,
                16,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                deviceClass == GpuDeviceClassTarget.IGPU,
                true,
                true,
                true
        );
    }
}
