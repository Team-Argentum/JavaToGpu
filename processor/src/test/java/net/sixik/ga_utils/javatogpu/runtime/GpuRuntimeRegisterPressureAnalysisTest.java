package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
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
        int variableId = returnId + 1;
        roots.add(returnId);
        nodes.add(new IrGpuTypedNode(returnId, "GpuIrReturn", Map.of(), Map.of("value", List.of(variableId))));
        nodes.add(new IrGpuTypedNode(
                variableId,
                "GpuIrVariableRef",
                Map.of("name", "value" + Math.max(0, localCount - 1)),
                Map.of()
        ));
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
                        new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "1.0f"), Map.of())
                )
        );
        return artifact(body);
    }

    private static IrGpuArtifact untypedArtifact() {
        return artifact(IrGpuTypedBody.none());
    }

    private static IrGpuArtifact artifact(IrGpuTypedBody typedBody) {
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
