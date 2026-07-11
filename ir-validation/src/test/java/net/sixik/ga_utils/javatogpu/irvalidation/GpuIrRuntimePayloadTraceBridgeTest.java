package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuOptimizationStrategyDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuPromotionArtifactRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDump;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileProvenance;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceEvidence;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPassReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrRuntimePayloadTraceBridgeTest {
    @Test
    void runtimeDumperConsumesRealCsePrePostPayloadArtifactFields() {
        GpuIrCommonSubexpressionPrePostRuntimeEquivalenceReport i2Report = new GpuIrCommonSubexpressionPrePostRuntimeEquivalenceRunner().run(
                compiledMethod(),
                List.of(
                        inputCase("case-a", Map.of("x", 7, "y", 11, "outA", 0, "outB", 0)),
                        inputCase("case-b", Map.of("x", -4, "y", 13, "outA", 3, "outB", -9))
                ),
                List.of("outA", "outB", "return")
        );
        Map<String, String> proofFields = new LinkedHashMap<>(i2Report.artifactFields());
        proofFields.put("optimizerFamily", "cse");

        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.applied(
                "optimizer:cse-pre-post-v1",
                "irgpu:sha256:original",
                "irgpu:sha256:cse",
                "proof:accepted",
                List.of("real I2 CSE pre/post payload fields attached")
        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "i2.cse.prePostRuntimeEquivalence",
                "accepted/runtimeEquivalenceSuccessful",
                proofFields
        ));
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                GpuBackendModuleArtifact.openClSource(
                        "__kernel void kernel(__global int* out) { out[0] = 2; }",
                        "runtime/lowered/kernel.cl",
                        "test-lowerer-v1"
                ),
                GpuRuntimeCompileInvalidationStamp.from(request, GpuBackendModuleArtifact.unknown(), "optimizer:cse-pre-post-v1"),
                GpuRuntimeCompileProvenance.from(request),
                new GpuRuntimeIrOptimizationReport(
                        Optional.of(optimized),
                        List.of(passReport),
                        GpuOptimizationStrategyDecision.none(request)
                ),
                GpuRuntimeEquivalenceEvidence.passed(request, 2, 3, List.of("real I2 payload bridge matched"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);
        String payload = dump.artifact(GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD);

        assertTrue(payload.contains("family.0.name=cse"));
        assertTrue(payload.contains("family.0.runtimePayload.present=true"));
        assertTrue(payload.contains("family.0.cpuReference.present=true"));
        assertTrue(payload.contains("family.0.preOptimizationOutput.present=true"));
        assertTrue(payload.contains("family.0.postOptimizationOutput.present=true"));
        assertTrue(payload.contains("family.0.tolerance.present=true"));
        assertTrue(payload.contains("family.0.proof.source.summary=i2.cse.prePostRuntimeEquivalence=1"));
        assertTrue(payload.contains("family.0.pass.0.payload.resource=i2://pre-post-runtime-equivalence/csePrePostRuntimeEquivalence"));
        assertTrue(payload.contains("family.0.pass.0.cpuReference.payload=inputCases=2, comparedOutputs=3, outputNames=outA,outB,return"));
        assertTrue(payload.contains("family.0.pass.0.preOptimizationOutput.payload=plans=1, insertions=1, skipped=0"));
        assertTrue(payload.contains("family.0.pass.0.postOptimizationOutput.payload=replacements=1, equivalent=true, successful=true"));
        assertTrue(payload.contains("family.0.pass.0.tolerance.payload=mode=exact-int, diagnostics=0, diagnosticFamilies={}"));
        assertTrue(payload.contains("family.0.pass.0.failureFixture.payload=none"));
        assertTrue(payload.contains("family.complete.count=1"));
    }

    @Test
    void runtimeDumperConsumesRealAutoVectorizationPrePostPayloadArtifactFields() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationCandidateScanner()
                .scan(autoVectorizationCompiledMethod(method))
                .preview();
        GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport i2Report =
                new GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceRunner().run(
                        method,
                        preview,
                        List.of(inputCase(
                                "case-a",
                                new int[]{7, -2, 13, 99},
                                new int[]{1, 4, -3, 11},
                                new int[]{0, 0, 0, 0}
                        )),
                        List.of("out")
                );
        Map<String, String> proofFields = new LinkedHashMap<>(i2Report.artifactFields());
        proofFields.put("optimizerFamily", "auto-vectorization");

        IrGpuArtifact optimized = artifact("body\n  return vectorized\n");
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.applied(
                "optimizer:auto-vectorization-pre-post-v1",
                "irgpu:sha256:original",
                "irgpu:sha256:auto-vectorized",
                "proof:accepted",
                List.of("real I2 auto-vectorization pre/post payload fields attached")
        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "i2.autoVectorization.prePostRuntimeEquivalence",
                "accepted/runtimeEquivalenceSuccessful",
                proofFields
        ));
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                GpuBackendModuleArtifact.openClSource(
                        "__kernel void kernel(__global int* out) { out[0] = 2; }",
                        "runtime/lowered/kernel.cl",
                        "test-lowerer-v1"
                ),
                GpuRuntimeCompileInvalidationStamp.from(
                        request,
                        GpuBackendModuleArtifact.unknown(),
                        "optimizer:auto-vectorization-pre-post-v1"
                ),
                GpuRuntimeCompileProvenance.from(request),
                new GpuRuntimeIrOptimizationReport(
                        Optional.of(optimized),
                        List.of(passReport),
                        GpuOptimizationStrategyDecision.none(request)
                ),
                GpuRuntimeEquivalenceEvidence.passed(request, 1, 1, List.of("real I2 auto-vectorization payload bridge matched"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);
        String payload = dump.artifact(GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD);

        assertTrue(payload.contains("family.0.name=auto-vectorization"));
        assertTrue(payload.contains("family.0.runtimePayload.present=true"));
        assertTrue(payload.contains("family.0.cpuReference.present=true"));
        assertTrue(payload.contains("family.0.preOptimizationOutput.present=true"));
        assertTrue(payload.contains("family.0.postOptimizationOutput.present=true"));
        assertTrue(payload.contains("family.0.tolerance.present=true"));
        assertTrue(payload.contains("family.0.proof.source.summary=i2.autoVectorization.prePostRuntimeEquivalence=1"));
        assertTrue(payload.contains("family.0.pass.0.payload.resource=i2://pre-post-runtime-equivalence/autoVectorizationPrototypePrePostRuntimeEquivalence"));
        assertTrue(payload.contains("family.0.pass.0.cpuReference.payload=inputCases=1, comparedOutputs=1, outputNames=out"));
        assertTrue(payload.contains("family.0.pass.0.preOptimizationOutput.payload=method=kernel, appliedRewrites=1"));
        assertTrue(payload.contains("family.0.pass.0.postOptimizationOutput.payload=equivalent=true, successful=true, comparedOutputs=1"));
        assertTrue(payload.contains("family.0.pass.0.tolerance.payload=mode=exact-int-lane, diagnostics=0, diagnosticFamilies={}"));
        assertTrue(payload.contains("family.0.pass.0.failureFixture.payload=none"));
        assertTrue(payload.contains("family.complete.count=1"));
    }

    private static GpuIrCommonSubexpressionInputCase inputCase(String name, Map<String, Integer> values) {
        return new GpuIrCommonSubexpressionInputCase(name, values);
    }

    private static GpuIrAutoVectorizationPrototypeInputCase inputCase(
            String name,
            int[] left,
            int[] right,
            int[] out
    ) {
        return new GpuIrAutoVectorizationPrototypeInputCase(name, Map.of(
                "left", left,
                "right", right,
                "out", out
        ));
    }

    private static GpuIrForLoop fixedWidthLoop(int endExclusive, List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral(Integer.toString(endExclusive))),
                new GpuIrAssignment(
                        new GpuIrVariableRef("i"),
                        new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))
                ),
                body
        );
    }

    private static GpuIrCompiledMethod compiledMethod() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("*",
                        new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")),
                        new GpuIrLiteral("2"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("-",
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")),
                        new GpuIrLiteral("3"))),
                new GpuIrReturn(new GpuIrBinary("+", new GpuIrVariableRef("outA"), new GpuIrVariableRef("outB")))
        ));
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                method.name(),
                "void",
                List.of(parameter("x"), parameter("y"), parameter("outA"), parameter("outB")),
                List.of(),
                List.of(),
                null,
                false,
                List.of(),
                "",
                "",
                "",
                false
        );
        return new GpuIrCompiledMethod(parsedMethod, method, "jtg_kernel", List.of());
    }

    private static GpuIrCompiledMethod autoVectorizationCompiledMethod(GpuIrMethod method) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                method.name(),
                "void",
                List.of(parameter("left", "int[]"), parameter("out", "int[]"), parameter("right", "int[]")),
                List.of(),
                List.of(),
                null,
                false,
                List.of(),
                "",
                "",
                "",
                false
        );
        return new GpuIrCompiledMethod(parsedMethod, method, "jtg_kernel", List.of());
    }

    private static ParsedGpuParameter parameter(String name) {
        return new ParsedGpuParameter(name, "int", GpuAddressSpace.GLOBAL, false, List.of());
    }

    private static ParsedGpuParameter parameter(String name, String type) {
        return new ParsedGpuParameter(name, type, GpuAddressSpace.GLOBAL, false, List.of());
    }

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static IrGpuArtifact artifact(String body) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "jtg_kernel",
                                body,
                                List.of(),
                                new IrGpuSourceLocation("java-source", "sample.Demo", "kernel", 4, 17, 7, 5)
                        ))
                ),
                List.of(),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }
}
