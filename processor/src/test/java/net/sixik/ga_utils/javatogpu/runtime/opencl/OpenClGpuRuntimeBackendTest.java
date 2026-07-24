package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.images.Image1DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DBufferReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image1DBufferWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DArrayReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DArrayWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DMipmappedReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image2DMipmappedWriteOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image3DReadOnly;
import net.sixik.ga_utils.javatogpu.api.images.Image3DWriteOnly;
import net.sixik.ga_utils.javatogpu.api.types.floats.Float2;
import net.sixik.ga_utils.javatogpu.api.images.Sampler;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructFieldMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompiledKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendArtifactHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipeline;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelCompiler;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelPreparer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuOptimizationStrategyDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuOptimizationVendorBaseline;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionOperatorAcceptance;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionActivationTokenTestFixtures;
import net.sixik.ga_utils.javatogpu.runtime.GpuPromotionArtifactRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDump;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceOverride;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelectionException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactSnapshot;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendUnavailableException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapability;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCapabilityException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCallSiteResolver;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptionsException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceCaseEvidence;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceEvidence;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEvent;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventKind;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventListener;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPassReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizerRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeKernelCompilationException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeKernelExecutionException;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpenClGpuRuntimeBackendTest {

    private static final String SIMPLE_IRGPU_SOURCE_RESOURCE =
            "javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties";
    private static final String OPTIMIZER_FAMILY_PAYLOAD_FIXTURE_DIRECTORY_PROPERTY =
            "javatogpu.opencl.optimizerFamilyPayloadFixtureDirectory";

    @Test
    void runtimeCompileArtifactPathKeepsNestedArtifactsInsideOutputDirectory() throws Exception {
        Path artifactDirectory = Files.createTempDirectory("javatogpu-runtime-artifact-path");
        Path nested = OpenClGpuRuntimeBackend.runtimeCompileArtifactPath(
                artifactDirectory,
                "runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0/manifest.properties"
        );

        assertEquals(
                artifactDirectory.toAbsolutePath().normalize()
                        .resolve("runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0/manifest.properties"),
                nested
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> OpenClGpuRuntimeBackend.runtimeCompileArtifactPath(artifactDirectory, "../escape.properties")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> OpenClGpuRuntimeBackend.runtimeCompileArtifactPath(
                        artifactDirectory,
                        artifactDirectory.resolveSibling("outside.properties").toString()
                )
        );
    }

    @Test
    void runtimeCompileArtifactWriterCreatesNestedPayloadDirectories() throws Exception {
        Path artifactDirectory = Files.createTempDirectory("javatogpu-runtime-artifact-writer");
        String nestedArtifact =
                "runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0/manifest.properties";
        GpuRuntimeCompileArtifactDump dump = new GpuRuntimeCompileArtifactDump(
                java.util.Map.of(nestedArtifact, "status=recorded\n"),
                List.of(),
                net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp.from(
                        null,
                        GpuBackendModuleArtifact.unknown(),
                        null
                )
        );

        OpenClGpuRuntimeBackend.writeRuntimeCompileArtifactDump(artifactDirectory, dump);

        assertEquals("status=recorded\n", Files.readString(artifactDirectory.resolve(nestedArtifact)));
        GpuRuntimeCompileArtifactDump escapingDump = new GpuRuntimeCompileArtifactDump(
                java.util.Map.of("../escape.properties", "unsafe\n"),
                List.of(),
                net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp.from(
                        null,
                        GpuBackendModuleArtifact.unknown(),
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> OpenClGpuRuntimeBackend.writeRuntimeCompileArtifactDump(artifactDirectory, escapingDump)
        );
    }

    @Test
    void runtimeCompileArtifactDirectoryPropertyDumpsOriginalAndOptimizedIrArtifacts() throws Exception {
        Path artifactRoot = Files.createTempDirectory("javatogpu-runtime-ir-dump");
        String property = "javatogpu.opencl.runtimeCompileArtifactDirectory";
        String previousArtifactRoot = System.getProperty(property);
        try {
            System.setProperty(property, artifactRoot.toString());
            IrGpuArtifact original = testIrGpuArtifact("body\n  return original\n");
            IrGpuArtifact optimized = testIrGpuArtifact("body\n  return optimized\n");
            GpuRuntimeCompileRequest originalRequest = new GpuRuntimeCompileRequest(
                    intOutputDescriptor(),
                    GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                    GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                    Optional.of(original)
            );
            GpuRuntimeCompileRequest optimizedRequest = originalRequest.withIrGpuArtifact(Optional.of(optimized));
            GpuBackendModuleArtifact originalBackendArtifact = GpuBackendModuleArtifact.openClSource(
                    "__kernel void kernel(__global int* output) { output[0] = 0; }",
                    "javatogpu/sample/Demo/kernel-original.cl",
                    "test-lowerer-v1"
            );
            GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                    "__kernel void kernel(__global int* output) { output[0] = 1; }",
                    "javatogpu/sample/Demo/kernel.cl",
                    "test-lowerer-v1"
            );
            GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                    originalRequest,
                    optimizedRequest,
                    backendArtifact,
                    net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp.from(
                            optimizedRequest,
                            backendArtifact,
                            "optimizer:manual-dump-test"
                    ),
                    net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileProvenance.from(optimizedRequest),
                    new GpuRuntimeIrOptimizationReport(Optional.of(optimized), List.of())
            ).withBackendStageModuleArtifacts(originalBackendArtifact, backendArtifact);

            OpenClGpuRuntimeBackend.writeRuntimeCompileArtifactsIfConfigured(snapshot);

            Path artifactDirectory;
            try (java.util.stream.Stream<Path> directories = Files.list(artifactRoot)) {
                artifactDirectory = directories.filter(Files::isDirectory).findFirst().orElseThrow();
            }
            assertTrue(Files.readString(artifactDirectory.resolve("original.irgpu.properties")).contains("return original"));
            assertTrue(Files.readString(artifactDirectory.resolve("optimized.irgpu.properties")).contains("return optimized"));
            assertTrue(Files.readString(artifactDirectory.resolve("original.backend.opencl-c")).contains("output[0] = 0"));
            assertTrue(Files.readString(artifactDirectory.resolve("optimized.backend.opencl-c")).contains("output[0] = 1"));
            assertTrue(Files.readString(artifactDirectory.resolve("backend.opencl-c")).contains("__kernel void kernel"));
            assertTrue(Files.exists(artifactDirectory.resolve("runtime-ir-handoff.properties")));
            assertTrue(Files.exists(artifactDirectory.resolve("compile-provenance.properties")));
        } finally {
            if (previousArtifactRoot == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previousArtifactRoot);
            }
        }
    }

    @Test
    void runtimeCompileArtifactDirectoryDumpsMaterializedCandidateWithoutSelectingIt() throws Exception {
        Path artifactRoot = Files.createTempDirectory("javatogpu-runtime-ir-candidate-dump");
        Path classpathRoot = Files.createTempDirectory("javatogpu-runtime-ir-candidate-classpath");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, irGpuArtifactProperties("return original"));
        String property = "javatogpu.opencl.runtimeCompileArtifactDirectory";
        String previousArtifactRoot = System.getProperty(property);
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classpathRoot.toUri().toURL()},
                OpenClGpuRuntimeBackendTest.class.getClassLoader()
        )) {
            System.setProperty(property, artifactRoot.toString());
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    IrGpuArtifact originalArtifact = compileRequest.irGpuArtifact().orElseThrow();
                    IrGpuArtifact candidateArtifact = testIrGpuArtifact("body\n  return optimized candidate\n");
                    String originalIdentity = IrGpuArtifactIdentity.stableIdentity(originalArtifact);
                    String candidateIdentity = IrGpuArtifactIdentity.stableIdentity(candidateArtifact);
                    GpuRuntimeIrOptimizationPassReport passReport = new GpuRuntimeIrOptimizationPassReport(
                            net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                            "optimizer:proposal-only-candidate",
                            net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationOutcome.SKIPPED,
                            originalIdentity,
                            candidateIdentity,
                            "proposal-only",
                            "",
                            GpuRuntimeIrOptimizationProofArtifact.fromFields(
                                    "test.proposalOnly",
                                    "candidate-ready",
                                    java.util.Map.of("optimizedArtifactCandidate.status", "candidate-ready")
                            ),
                            List.of("candidate materialized but production mutation remains disabled")
                    );
                    return new GpuRuntimeIrOptimizationResult(
                            compileRequest.withIrGpuArtifact(Optional.of(candidateArtifact)),
                            new GpuRuntimeIrOptimizationReport(
                                    Optional.of(originalArtifact),
                                    Optional.of(candidateArtifact),
                                    List.of(passReport)
                            )
                    );
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    String body = compileRequest.irGpuArtifact()
                            .map(artifact -> artifact.module().methodBodies().get(0).body())
                            .orElse("");
                    boolean optimized = body.contains("optimized candidate");
                    return GpuBackendModuleArtifact.openClSource(
                            optimized
                                    ? "__kernel void kernel(__global int* output) { output[0] = 2; }"
                                    : "__kernel void kernel(__global int* output) { output[0] = 1; }",
                            optimized
                                    ? "runtime/lowered/kernel-optimized.cl"
                                    : "runtime/lowered/kernel-original.cl",
                            "test-lowerer-v1"
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptorWithIrGpuResource(),
                    new Object[]{new int[]{0}}
            ).withArtifactClassLoader(classLoader));

            GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
            String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
            String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
            assertNotEquals(originalIdentity, optimizedIdentity);
            assertEquals(originalIdentity, IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact()));
            assertEquals("original", snapshot.runtimeIrSelection().selectedStage());
            assertTrue(snapshot.runtimeIrSelection().transformed());
            assertFalse(snapshot.runtimeIrSelection().optimizedRejected());
            assertTrue(snapshot.originalBackendModuleArtifact().orElseThrow().source().contains("output[0] = 1"));
            assertTrue(snapshot.optimizedBackendModuleArtifact().orElseThrow().source().contains("output[0] = 2"));
            assertTrue(snapshot.backendModuleArtifact().source().contains("output[0] = 1"));

            Path artifactDirectory;
            try (java.util.stream.Stream<Path> directories = Files.list(artifactRoot)) {
                artifactDirectory = directories.filter(Files::isDirectory).findFirst().orElseThrow();
            }
            assertTrue(Files.readString(artifactDirectory.resolve("original.backend.opencl-c")).contains("output[0] = 1"));
            assertTrue(Files.readString(artifactDirectory.resolve("optimized.backend.opencl-c")).contains("output[0] = 2"));
            assertTrue(Files.readString(artifactDirectory.resolve("backend.opencl-c")).contains("output[0] = 1"));
            assertTrue(Files.readString(artifactDirectory.resolve("runtime-ir-handoff.properties")).contains("selectedStage=original"));
            assertTrue(Files.readString(artifactDirectory.resolve("runtime-ir-handoff.properties")).contains("optimizedDiffersFromOriginal=true"));
        } finally {
            if (previousArtifactRoot == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previousArtifactRoot);
            }
        }
    }

    @Test
    void experimentalIrOptimizerApplySelectsMaterializedCandidateForRuntimeCompile() throws Exception {
        Path artifactRoot = Files.createTempDirectory("javatogpu-runtime-ir-experimental-apply-dump");
        Path classpathRoot = Files.createTempDirectory("javatogpu-runtime-ir-experimental-apply-classpath");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, irGpuArtifactProperties("return original"));
        String property = "javatogpu.opencl.runtimeCompileArtifactDirectory";
        String previousArtifactRoot = System.getProperty(property);
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();
        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classpathRoot.toUri().toURL()},
                OpenClGpuRuntimeBackendTest.class.getClassLoader()
        )) {
            System.setProperty(property, artifactRoot.toString());
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    IrGpuArtifact originalArtifact = compileRequest.irGpuArtifact().orElseThrow();
                    IrGpuArtifact candidateArtifact = testIrGpuArtifact("body\n  return optimized candidate\n");
                    String originalIdentity = IrGpuArtifactIdentity.stableIdentity(originalArtifact);
                    String candidateIdentity = IrGpuArtifactIdentity.stableIdentity(candidateArtifact);
                    GpuRuntimeIrOptimizationPassReport passReport = new GpuRuntimeIrOptimizationPassReport(
                            net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                            "javatogpu.ir-optimizer:test-experimental-apply",
                            net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationOutcome.APPLIED,
                            originalIdentity,
                            candidateIdentity,
                            "optimized-selected",
                            "",
                            GpuRuntimeIrOptimizationProofArtifact.fromFields(
                                    "test.experimentalApply",
                                    "candidate-ready",
                                    java.util.Map.ofEntries(
                                            java.util.Map.entry("experimentalApply.requested", "true"),
                                            java.util.Map.entry("experimentalApply.enabled", "true"),
                                            java.util.Map.entry("experimentalApply.selected", "true"),
                                            java.util.Map.entry("optimizedArtifactCandidate.status", "candidate-ready"),
                                            java.util.Map.entry("optimizedArtifactCandidate.selectionReady", "true"),
                                            java.util.Map.entry("optimizedArtifactCandidate.selectionApplied", "true"),
                                            java.util.Map.entry("optimizedArtifactCandidate.selectedIrReplacement", "true"),
                                            java.util.Map.entry("optimizedArtifactCandidate.mutationAllowed", "true"),
                                            java.util.Map.entry("optimizedArtifactCandidate.firstBlocker", "none"),
                                            java.util.Map.entry("optimizedArtifactCandidate.selectionFirstBlocker", "none")
                                    )
                            ),
                            List.of("candidate materialized and selected by experimental apply mode")
                    );
                    return new GpuRuntimeIrOptimizationResult(
                            compileRequest.withIrGpuArtifact(Optional.of(candidateArtifact)),
                            new GpuRuntimeIrOptimizationReport(
                                    Optional.of(candidateArtifact),
                                    Optional.of(candidateArtifact),
                                    List.of(passReport)
                            )
                    );
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    String body = compileRequest.irGpuArtifact()
                            .map(artifact -> artifact.module().methodBodies().get(0).body())
                            .orElse("");
                    boolean optimized = body.contains("optimized candidate");
                    return GpuBackendModuleArtifact.openClSource(
                            optimized
                                    ? "__kernel void kernel(__global int* output) { output[0] = 2; }"
                                    : "__kernel void kernel(__global int* output) { output[0] = 1; }",
                            optimized
                                    ? "runtime/lowered/kernel-optimized.cl"
                                    : "runtime/lowered/kernel-original.cl",
                            "test-lowerer-v1"
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptorWithIrGpuResource(),
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openClIrOptimizerExperimentalApply(List.of(), "diagnostic")
            ).withArtifactClassLoader(classLoader));

            GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
            String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
            String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
            assertNotEquals(originalIdentity, optimizedIdentity);
            assertEquals(optimizedIdentity, IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact()));
            assertEquals("optimized", snapshot.runtimeIrSelection().selectedStage());
            assertTrue(snapshot.runtimeIrSelection().transformed());
            assertFalse(snapshot.runtimeIrSelection().optimizedRejected());
            assertTrue(snapshot.backendModuleArtifact().source().contains("output[0] = 2"));

            Path artifactDirectory;
            try (java.util.stream.Stream<Path> directories = Files.list(artifactRoot)) {
                artifactDirectory = directories.filter(Files::isDirectory).findFirst().orElseThrow();
            }
            assertTrue(Files.readString(artifactDirectory.resolve("backend.opencl-c")).contains("output[0] = 2"));
            assertTrue(Files.readString(artifactDirectory.resolve("runtime-ir-handoff.properties")).contains("selectedStage=optimized"));
            assertTrue(Files.readString(artifactDirectory.resolve("runtime-ir-optimizer-evidence.properties"))
                    .contains("experimentalApply.selected.count=1"));
            assertTrue(Files.readString(artifactDirectory.resolve("runtime-ir-optimizer-evidence.properties"))
                    .contains("optimizedArtifactCandidate.selectionApplied=true"));
        } finally {
            if (previousArtifactRoot == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previousArtifactRoot);
            }
        }
    }

    @Test
    void writesOptimizerFamilyPayloadFixtureArtifacts() throws Exception {
        String outputDirectory = System.getProperty(OPTIMIZER_FAMILY_PAYLOAD_FIXTURE_DIRECTORY_PROPERTY);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                outputDirectory != null && !outputDirectory.isBlank(),
                "Skipping optimizer-family payload fixture without configured output directory"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptorWithIrGpuResource(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                java.util.Optional.of(testIrGpuArtifact("body\n  return original\n"))
        );
        GpuRuntimeIrOptimizationPassReport csePayload = optimizerFamilyPayloadPass(
                "optimizer:cse-v1",
                "cse",
                "inputCases=3, comparedOutputs=1, outputNames=outA",
                "plans=1, insertions=1, skipped=0",
                "replacements=1, equivalent=true, successful=true",
                "mode=exact-int, diagnostics=0, diagnosticFamilies={}",
                "none"
        );
        GpuRuntimeIrOptimizationPassReport vectorPayload = optimizerFamilyPayloadPass(
                "optimizer:auto-vectorization-v1",
                "auto-vectorization",
                "inputCases=3, comparedOutputs=2, outputNames=out,mask",
                "method=kernel, appliedRewrites=1, families={laneCopy=1}",
                "equivalent=true, successful=true, comparedOutputs=2",
                "mode=exact-int-lane, diagnostics=0, diagnosticFamilies={}",
                "none"
        );
        IrGpuArtifact optimized = testIrGpuArtifact("body\n  return optimized\n");
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeIrOptimizationReport(
                java.util.Optional.of(optimized),
                List.of(csePayload, vectorPayload),
                productionBackedStrategyDecision()
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot
                .legacy(request.descriptor())
                .withOptimizationReport(optimizationReport)
                .withRuntimeEquivalenceEvidence(GpuRuntimeEquivalenceEvidence.passed(
                        request,
                        3,
                        3,
                        List.of("optimizer-family fixture outputs matched")
                ));
        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);
        Path fixtureDirectory = Path.of(outputDirectory);

        OpenClGpuRuntimeBackend.writeRuntimeCompileArtifactDump(fixtureDirectory, dump);

        java.util.Properties index = new java.util.Properties();
        try (java.io.Reader reader = Files.newBufferedReader(
                fixtureDirectory.resolve(GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD)
        )) {
            index.load(reader);
        }
        assertEquals("2", index.getProperty("family.count"));
        assertEquals("2", index.getProperty("family.complete.count"));
        assertEquals("true", index.getProperty("family.complete.all"));
        long durableFileCount = dump.artifacts().keySet().stream()
                .filter(name -> name.startsWith(
                        GpuRuntimeCompileArtifactDumper.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD_DIRECTORY + "/"
                ))
                .count();
        assertEquals(14L, durableFileCount);
        assertTrue(Files.isRegularFile(fixtureDirectory.resolve(
                "runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0/manifest.properties"
        )));
        assertTrue(Files.isRegularFile(fixtureDirectory.resolve(
                "runtime-optimizer-family-equivalence-payload/family-1-auto-vectorization/pass-0/diagnostics.properties"
        )));
        String cseCpuReference = Files.readString(fixtureDirectory.resolve(
                "runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0/cpu-reference.properties"
        ));
        String csePostOptimization = Files.readString(fixtureDirectory.resolve(
                "runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0/post-optimization-output.properties"
        ));
        String vectorPostOptimization = Files.readString(fixtureDirectory.resolve(
                "runtime-optimizer-family-equivalence-payload/family-1-auto-vectorization/pass-0/post-optimization-output.properties"
        ));
        assertTrue(cseCpuReference.contains("cseRuntimeEquivalencePayload.Case.0.Output.0.CpuReference"));
        assertTrue(csePostOptimization.contains("cseRuntimeEquivalencePayload.Case.0.Output.0.PostOptimization"));
        assertTrue(vectorPostOptimization.contains(
                "auto-vectorizationRuntimeEquivalencePayload.Case.0.Output.0.PostOptimization"
        ));
        Files.writeString(
                fixtureDirectory.resolve("fixture-summary.properties"),
                "status=passed\n"
                        + "scope=optimizer-family-payload-fixture\n"
                        + "family.count=2\n"
                        + "family.complete.count=2\n"
                        + "family.complete.all=true\n"
                        + "durable.file.count=" + durableFileCount + "\n"
                        + "structured.case.count=2\n"
                        + "productionSourceSwitching=disabled\n"
                        + "productionMutation=disabled\n"
        );
    }

    @Test
    void finalCompileSnapshotCarriesAvailableRuntimeDeviceSelection() {
        GpuRuntimeDeviceProfile profile = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-0",
                "Mock GPU",
                "Mock Vendor",
                "Mock Driver",
                "OpenCL 3.0 Mock",
                GpuDeviceClassTarget.DGPU,
                48L,
                8L * 1024L * 1024L * 1024L,
                65_536L,
                512L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        List.of(profile)
                )
        );
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
            @Override
            protected Optional<GpuRuntimeDeviceSelection> runtimeDeviceSelection() {
                return Optional.of(selection);
            }
        };

        backend.invoke(new GpuKernelInvocation(intOutputDescriptor(), new Object[]{new int[]{0}}));

        assertSame(selection, capturedSnapshot.get().deviceSelection().orElseThrow());
    }

    @Test
    void activeSessionRejectsRequestThatRequiresAnotherDevice() {
        GpuRuntimeDeviceProfile nvidia = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-0",
                "NVIDIA RTX",
                "NVIDIA",
                "driver",
                "OpenCL 3.0",
                GpuDeviceClassTarget.DGPU,
                48L,
                8L * 1024L * 1024L * 1024L,
                65_536L,
                512L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceProfile amd = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-1",
                "AMD Radeon",
                "AMD",
                "driver",
                "OpenCL 3.0",
                GpuDeviceClassTarget.DGPU,
                32L,
                8L * 1024L * 1024L * 1024L,
                65_536L,
                512L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceSelection activeSelection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        List.of(nvidia, amd)
                )
        );
        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(new AtomicReference<>()) {
            @Override
            protected Optional<GpuRuntimeDeviceSelection> runtimeDeviceSelection() {
                return Optional.of(activeSelection);
            }
        };
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .withDeviceOverride(GpuRuntimeDeviceOverride.byVendor("AMD"));

        GpuRuntimeDeviceSelectionException exception = assertThrows(
                GpuRuntimeDeviceSelectionException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        intOutputDescriptor(),
                        new Object[]{new int[]{0}},
                        options
                ))
        );

        assertTrue(exception.getMessage().contains("create a new runtime scope/backend instance"));
        assertEquals("AMD Radeon", exception.selection().selectedDevice().orElseThrow().deviceLabel());
    }

    @Test
    void irGpuArtifactIdentityIsStableAndChangesWithPayload() {
        IrGpuArtifact firstArtifact = testIrGpuArtifact("body\n  return output[0] + 1\n");
        IrGpuArtifact sameArtifact = testIrGpuArtifact("body\n  return output[0] + 1\n");
        IrGpuArtifact changedArtifact = testIrGpuArtifact("body\n  return output[0] + 2\n");

        assertEquals(IrGpuArtifactIdentity.stableHash(firstArtifact), IrGpuArtifactIdentity.stableHash(sameArtifact));
        assertEquals(IrGpuArtifactIdentity.stableIdentity(firstArtifact), IrGpuArtifactIdentity.stableIdentity(sameArtifact));
        assertNotEquals(IrGpuArtifactIdentity.stableHash(firstArtifact), IrGpuArtifactIdentity.stableHash(changedArtifact));
        assertNotEquals(IrGpuArtifactIdentity.stableIdentity(firstArtifact), IrGpuArtifactIdentity.stableIdentity(changedArtifact));
        assertTrue(IrGpuArtifactIdentity.stableIdentity(firstArtifact).startsWith("irgpu:sha256:"));
    }

    @Test
    void openClExecutionHandlesExposeBackendNeutralSpi() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        OpenClCompiledKernel compiledKernel = new OpenClCompiledKernel(descriptor, "compiled:spi");
        GpuBackendCompiledKernel backendCompiledKernel = compiledKernel;
        Map<String, String> compiledFields = backendCompiledKernel.artifactFields("compiled");
        GpuBackendKernelPreparer<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> preparer =
                new OpenClExecutionPreparer(null);
        OpenClPreparedExecution preparedExecution = preparer.prepare(
                compiledKernel,
                new OpenClExecutionPlan(List.of(), List.of(), List.of(), List.of())
        );
        GpuPreparedKernel preparedKernel = new OpenClPreparedExecution(
                preparedExecution.compiledKernel(),
                preparedExecution.bufferBindings(),
                preparedExecution.localBindings(),
                preparedExecution.scalarBindings(),
                preparedExecution.argumentBindings(),
                GpuExecutionConfig.oneDimensional(4L)
        );
        Map<String, String> preparedFields = preparedKernel.artifactFields("prepared");

        assertEquals(GpuBackendTarget.OPENCL, backendCompiledKernel.backendTarget());
        assertEquals("opencl-kernel", backendCompiledKernel.compiledKernelKind());
        assertEquals("opencl-c", backendCompiledKernel.moduleArtifact().format());
        assertEquals("true", compiledFields.get("runtime.backend.compiledKernel.present"));
        assertEquals("opencl-kernel", compiledFields.get("runtime.backend.compiledKernel.kind"));
        assertEquals("opencl-c", compiledFields.get("runtime.backend.compiledKernel.module.format"));
        assertEquals("OPENCL", compiledFields.get("runtime.backend.target"));
        assertEquals(GpuBackendTarget.OPENCL, preparer.backendTarget());
        assertSame(compiledKernel, preparedKernel.compiledKernel());
        assertEquals("opencl-kernel", preparedKernel.preparedKernelKind());
        assertEquals(0, preparedKernel.readbackRequiredCount());
        assertEquals(0, preparedKernel.bindingSummary().argumentBindingCount());
        assertEquals("true", preparedFields.get("runtime.backend.preparedKernel.present"));
        assertEquals("opencl-kernel", preparedFields.get("runtime.backend.preparedKernel.kind"));
        assertEquals("0", preparedFields.get("runtime.backend.preparedKernel.readback.required.count"));
        assertEquals("OPENCL", preparedFields.get("runtime.backend.target"));
        assertEquals("4", preparedKernel.explicitExecutionConfig().globalShape());

        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger invokeCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        "compiled:adapter-spi",
                        GpuRuntimeCompileArtifactSnapshot.from(compileRequest, compileRequest, moduleArtifact),
                        null,
                        null
                );
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                invokeCalls.incrementAndGet();
                assertEquals("compiled:adapter-spi", execution.compiledKernel().cacheKey());
                assertEquals("4", execution.explicitExecutionConfig().globalShape());
            }
        };
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL")
        );
        GpuBackendModuleArtifact moduleArtifact = GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource(),
                "test-opencl-spi"
        );
        GpuBackendLoweringResult loweringResult = GpuBackendLoweringResult.succeeded(
                moduleArtifact,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        GpuBackendTarget.OPENCL,
                        "opencl-c",
                        "OpenCL SPI test source"
                ),
                List.of("OpenCL SPI test module is lowered")
        );
        GpuBackendKernelCompiler<OpenClCompiledKernel> compiler = backend.kernelCompiler();
        GpuBackendKernelPreparer<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> backendPreparer =
                backend.kernelPreparer();
        GpuBackendKernelInvoker<OpenClPreparedExecution> invoker = backend.kernelInvoker();
        GpuBackendExecutionPipeline<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> pipeline =
                new GpuBackendExecutionPipeline<>(compiler, backendPreparer, invoker);
        GpuBackendExecutionPipelineResult<OpenClCompiledKernel, OpenClPreparedExecution> pipelineResult = pipeline.execute(
                compileRequest,
                loweringResult,
                moduleArtifact,
                new OpenClExecutionPlan(List.of(), List.of(), List.of(), List.of()),
                GpuExecutionConfig.oneDimensional(4L)
        );
        OpenClCompiledKernel compiledViaAdapter = pipelineResult.compiledKernel();
        Map<String, String> pipelineFields = pipelineResult.artifactFields("pipeline");

        assertEquals(GpuBackendTarget.OPENCL, compiler.backendTarget());
        assertEquals(GpuBackendTarget.OPENCL, backendPreparer.backendTarget());
        assertEquals(GpuBackendTarget.OPENCL, invoker.backendTarget());
        assertTrue(pipelineResult.succeeded());
        assertEquals("true", pipelineFields.get("runtime.backend.executionPipeline.succeeded"));
        assertEquals("true", pipelineFields.get("runtime.backend.compilation.present"));
        assertEquals("SUCCEEDED", pipelineFields.get("runtime.backend.compilation.status"));
        assertEquals("true", pipelineFields.get("runtime.backend.compilation.compiled"));
        assertEquals("compile", pipelineFields.get("runtime.backend.compilation.stage.key"));
        assertEquals("true", pipelineFields.get("runtime.backend.prepare.present"));
        assertEquals("SUCCEEDED", pipelineFields.get("runtime.backend.prepare.status"));
        assertEquals("true", pipelineFields.get("runtime.backend.prepare.prepared"));
        assertEquals("true", pipelineFields.get("runtime.backend.invoke.present"));
        assertEquals("SUCCEEDED", pipelineFields.get("runtime.backend.invoke.status"));
        assertEquals("true", pipelineFields.get("runtime.backend.invoke.invoked"));
        assertEquals("true", pipelineFields.get("runtime.backend.compiledKernel.present"));
        assertEquals("true", pipelineFields.get("runtime.backend.preparedKernel.present"));
        assertEquals(1, compileCalls.get());
        assertEquals(1, invokeCalls.get());
        assertEquals("compiled:adapter-spi", compiledViaAdapter.cacheKey());
        assertEquals("opencl-c", compiledViaAdapter.moduleArtifact().format());
    }

    @Test
    void preparedOpenClLauncherCompilesOnceAndReusesHotPath() {
        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        "compiled:prepared:" + compileCalls.incrementAndGet(),
                        GpuRuntimeCompileArtifactSnapshot.from(compileRequest, compileRequest, moduleArtifact),
                        null,
                        null
                );
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                assertEquals("compiled:prepared:1", execution.compiledKernel().cacheKey());
                int invocationIndex = executeCalls.incrementAndGet();
                for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
                    if (binding.binding().sourceArray() instanceof int[] values && values.length > 0) {
                        values[0] = invocationIndex;
                    }
                }
            }
        };

        GpuPreparedLauncher launcher = backend.prepare(new GpuKernelInvocation(
                intOutputDescriptor(),
                new Object[]{new int[]{0}}
        ));
        int compileCountAfterPrepare = compileCalls.get();
        int[] first = new int[]{0};
        int[] second = new int[]{0};

        launcher.invoke(first);
        launcher.invoke(second);

        assertEquals(1, compileCountAfterPrepare);
        assertEquals(compileCountAfterPrepare, compileCalls.get());
        assertEquals(2, executeCalls.get());
        assertArrayEquals(new int[]{1}, first);
        assertArrayEquals(new int[]{2}, second);
        assertEquals(intOutputDescriptor().kernelName(), launcher.descriptor().kernelName());
    }

    @Test
    void openClExecutionPipelineFactoryUsesBackendOwnedSpiComponents() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global float* values) { values[0] = values[0] + 1.0f; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("values", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL")
        );
        GpuBackendModuleArtifact moduleArtifact = GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource(),
                "test-opencl-provider-spi"
        );
        GpuBackendLoweringResult loweringResult = GpuBackendLoweringResult.succeeded(
                moduleArtifact,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        GpuBackendTarget.OPENCL,
                        "opencl-c",
                        "OpenCL provider SPI test source"
                ),
                List.of("OpenCL provider SPI test module is lowered")
        );
        float[] values = new float[]{1.0f, 2.0f, 3.0f, 4.0f};
        OpenClBufferBinding bufferBinding = new OpenClBufferBinding(
                OpenClArgumentKind.FLOAT_ARRAY,
                GpuKernelParameterAccess.READ_WRITE,
                values,
                values.length,
                true,
                true
        );
        OpenClExecutionPlan executionPlan = new OpenClExecutionPlan(
                List.of(bufferBinding),
                List.of(),
                List.of(),
                List.of(OpenClPlannedArgumentBinding.forBuffer(0, bufferBinding))
        );
        AtomicInteger compilerAccessorCalls = new AtomicInteger();
        AtomicInteger preparerAccessorCalls = new AtomicInteger();
        AtomicInteger invokerAccessorCalls = new AtomicInteger();
        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger prepareCalls = new AtomicInteger();
        AtomicInteger invokeCalls = new AtomicInteger();
        AtomicReference<OpenClPreparedExecution> preparedByBackendOwnedPreparer = new AtomicReference<>();
        AtomicReference<OpenClPreparedExecution> invokedByBackendOwnedInvoker = new AtomicReference<>();
        OpenClDeviceBufferRegistry backendOwnedRegistry = new OpenClDeviceBufferRegistry();
        GpuBackendKernelCompiler<OpenClCompiledKernel> backendOwnedCompiler = new GpuBackendKernelCompiler<>() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public OpenClCompiledKernel compile(
                    GpuRuntimeCompileRequest request,
                    GpuBackendModuleArtifact artifact
            ) {
                compileCalls.incrementAndGet();
                assertSame(compileRequest, request);
                assertSame(moduleArtifact, artifact);
                return new OpenClCompiledKernel(
                        request.descriptor(),
                        "compiled:provider-spi",
                        GpuRuntimeCompileArtifactSnapshot.from(request, request, artifact),
                        null,
                        null
                );
            }
        };
        GpuBackendKernelPreparer<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> backendOwnedPreparer =
                new GpuBackendKernelPreparer<>() {
                    private final OpenClExecutionPreparer delegate = new OpenClExecutionPreparer(backendOwnedRegistry);

                    @Override
                    public GpuBackendTarget backendTarget() {
                        return GpuBackendTarget.OPENCL;
                    }

                    @Override
                    public OpenClPreparedExecution prepare(
                            OpenClCompiledKernel compiledKernel,
                            OpenClExecutionPlan plan
                    ) {
                        prepareCalls.incrementAndGet();
                        assertEquals("compiled:provider-spi", compiledKernel.cacheKey());
                        assertSame(executionPlan, plan);
                        OpenClPreparedExecution preparedExecution = delegate.prepare(compiledKernel, plan);
                        preparedByBackendOwnedPreparer.set(preparedExecution);
                        return preparedExecution;
                    }
                };
        GpuBackendKernelInvoker<OpenClPreparedExecution> backendOwnedInvoker = new GpuBackendKernelInvoker<>() {
            @Override
            public GpuBackendTarget backendTarget() {
                return GpuBackendTarget.OPENCL;
            }

            @Override
            public void invoke(OpenClPreparedExecution preparedKernel, GpuExecutionConfig executionConfig) {
                invokeCalls.incrementAndGet();
                assertSame(preparedByBackendOwnedPreparer.get(), preparedKernel);
                assertEquals("8", executionConfig.globalShape());
                invokedByBackendOwnedInvoker.set(preparedKernel);
            }
        };
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected GpuBackendKernelCompiler<OpenClCompiledKernel> kernelCompiler() {
                compilerAccessorCalls.incrementAndGet();
                return backendOwnedCompiler;
            }

            @Override
            protected GpuBackendKernelPreparer<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> kernelPreparer() {
                preparerAccessorCalls.incrementAndGet();
                return backendOwnedPreparer;
            }

            @Override
            protected GpuBackendKernelInvoker<OpenClPreparedExecution> kernelInvoker() {
                invokerAccessorCalls.incrementAndGet();
                return backendOwnedInvoker;
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest request,
                    GpuBackendModuleArtifact artifact
            ) {
                throw new AssertionError("factory pipeline must use backend-owned kernelCompiler()");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                throw new AssertionError("factory pipeline must use backend-owned kernelInvoker()");
            }
        };

        OpenClBackendExecutionPipelineFactory factory = new OpenClBackendExecutionPipelineFactory();
        GpuBackendExecutionPipeline<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> pipeline =
                factory.createPipeline(backend);
        GpuBackendExecutionPipelineResult<OpenClCompiledKernel, OpenClPreparedExecution> pipelineResult = pipeline.execute(
                compileRequest,
                loweringResult,
                moduleArtifact,
                executionPlan,
                GpuExecutionConfig.oneDimensional(8L)
        );
        OpenClPreparedExecution preparedExecution = pipelineResult.preparedKernel();

        assertEquals(GpuBackendTarget.OPENCL, factory.backendTarget());
        assertEquals(GpuBackendTarget.OPENCL, pipeline.backendTarget());
        assertTrue(pipelineResult.succeeded());
        assertEquals(1, compilerAccessorCalls.get());
        assertEquals(1, preparerAccessorCalls.get());
        assertEquals(1, invokerAccessorCalls.get());
        assertEquals(1, compileCalls.get());
        assertEquals(1, prepareCalls.get());
        assertEquals(1, invokeCalls.get());
        assertEquals("compiled:provider-spi", pipelineResult.compiledKernel().cacheKey());
        assertSame(preparedExecution, preparedByBackendOwnedPreparer.get());
        assertSame(preparedExecution, invokedByBackendOwnedInvoker.get());
        assertEquals(1, preparedExecution.bufferBindings().size());
        assertEquals(1, preparedExecution.argumentBindings().size());
        assertSame(preparedExecution.bufferBindings().get(0), preparedExecution.argumentBindings().get(0).bufferBinding());
        assertEquals(1, backendOwnedRegistry.cacheSize());
    }

    @Test
    void cachesCompiledKernelAcrossInvocations() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();
        AtomicReference<OpenClCompiledKernel> firstCompiledKernel = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                OpenClCompiledKernel compiledKernel = new OpenClCompiledKernel(
                        kernelDescriptor,
                        "compiled:" + compileCalls.incrementAndGet()
                );
                firstCompiledKernel.compareAndSet(null, compiledKernel);
                return compiledKernel;
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                executeCalls.incrementAndGet();
                assertSame(firstCompiledKernel.get(), execution.compiledKernel());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(1, compileCalls.get());
        assertEquals(2, executeCalls.get());
        assertEquals(1, backend.cacheSize());
    }

    @Test
    void productionInvocationUsesSharedExecutionPipelineWithoutLosingCompileCache() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = output[0] + 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger prepareCalls = new AtomicInteger();
        AtomicInteger invokeCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                return GpuBackendModuleArtifact.openClSource(
                        compileRequest.descriptor().kernelSource(),
                        compileRequest.descriptor().kernelResource(),
                        "test-production-pipeline-lowerer"
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        "compiled:production-pipeline:" + compileCalls.incrementAndGet()
                );
            }

            @Override
            protected GpuBackendKernelPreparer<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> kernelPreparer() {
                GpuBackendKernelPreparer<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> delegate =
                        super.kernelPreparer();
                return new GpuBackendKernelPreparer<>() {
                    @Override
                    public GpuBackendTarget backendTarget() {
                        return GpuBackendTarget.OPENCL;
                    }

                    @Override
                    public OpenClPreparedExecution prepare(
                            OpenClCompiledKernel compiledKernel,
                            OpenClExecutionPlan executionPlan
                    ) {
                        prepareCalls.incrementAndGet();
                        assertTrue(calledFromBackendExecutionPipeline());
                        return delegate.prepare(compiledKernel, executionPlan);
                    }
                };
            }

            @Override
            protected GpuBackendKernelInvoker<OpenClPreparedExecution> kernelInvoker() {
                GpuBackendKernelInvoker<OpenClPreparedExecution> delegate = super.kernelInvoker();
                return new GpuBackendKernelInvoker<>() {
                    @Override
                    public GpuBackendTarget backendTarget() {
                        return GpuBackendTarget.OPENCL;
                    }

                    @Override
                    public void invoke(OpenClPreparedExecution preparedKernel, GpuExecutionConfig executionConfig) {
                        invokeCalls.incrementAndGet();
                        assertTrue(calledFromBackendExecutionPipeline());
                        delegate.invoke(preparedKernel, executionConfig);
                    }
                };
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op: this test verifies the production orchestration path, not native OpenCL execution.
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0, 1, 2, 3}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0, 1, 2, 3}}));

        OpenClRuntimeStatistics statistics = backend.statistics();
        assertEquals(1, compileCalls.get());
        assertEquals(2, prepareCalls.get());
        assertEquals(2, invokeCalls.get());
        assertEquals(2, statistics.invocationCount());
        assertEquals(1, statistics.compileCount());
        assertEquals(1, statistics.compileCacheHitCount());
        assertEquals(1, backend.cacheSize());
    }

    @Test
    void compileRequestCarriesDefaultOptionsAndDeviceProfile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<GpuRuntimeCompileRequest> capturedRequest = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuRuntimeDeviceProfile compileDeviceProfile() {
                return new GpuRuntimeDeviceProfile(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Mock GPU",
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock"
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                capturedRequest.set(compileRequest);
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileRequest request = capturedRequest.get();
        assertSame(descriptor, request.descriptor());
        assertEquals(GpuBackendTarget.OPENCL, request.options().backendTarget());
        assertTrue(request.options().compileArgs().isEmpty());
        assertEquals("off", request.options().optimizationProfile());
        assertEquals("Mock GPU", request.deviceProfile().deviceLabel());
        assertEquals("Mock Vendor", request.deviceProfile().vendor());
        assertEquals("OpenCL 3.0 Mock", request.deviceProfile().apiVersionText());
    }

    @Test
    void preselectedDiscoveryProvidesCompileProfileWithoutNativeCapabilityProbe() {
        GpuRuntimeDeviceProfile device = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-preflight-0",
                "Preflight OpenCL GPU",
                "NVIDIA",
                "preflight-driver",
                "OpenCL 3.0 Preflight",
                "NVIDIA CUDA",
                "OpenCL 3.0 CUDA",
                GpuDeviceClassTarget.DGPU,
                48,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                false,
                true,
                true,
                false
        );
        GpuRuntimeDeviceSelection selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        List.of(device)
                )
        );
        GpuRuntimeDeviceDiscoveryResult discovery = GpuRuntimeDeviceDiscoveryResult.available(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                List.of(device),
                selection
        );
        AtomicInteger nativeCapabilityCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                nativeCapabilityCalls.incrementAndGet();
                throw new AssertionError("preselected compile profile should not touch native capabilities");
            }
        };

        backend.preselectDevice(discovery);
        GpuRuntimeDeviceProfile compileProfile = backend.compileDeviceProfile();

        assertEquals("Preflight OpenCL GPU", compileProfile.deviceLabel());
        assertEquals("OpenCL", compileProfile.backendName());
        assertEquals("opencl-preflight-0", compileProfile.deviceId());
        assertEquals(0, nativeCapabilityCalls.get());
        assertSame(selection, backend.preselectedDeviceSelection().orElseThrow());
        assertSame(selection, backend.runtimeDeviceSelection().orElseThrow());
    }

    @Test
    void compileProfileCarriesOpenClCompilerVersionFromRuntimeCapabilities() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities(
                        "Compiler Version GPU",
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock",
                        "OpenCL C 3.0 Mock",
                        true,
                        true,
                        true,
                        32_768L,
                        256L,
                        12L,
                        1L,
                        true,
                        false
                );
            }
        };

        GpuRuntimeDeviceProfile profile = backend.compileDeviceProfile();

        assertEquals("OpenCL C 3.0 Mock", profile.compilerVersion());
        assertTrue(profile.supportsCapability(GpuRuntimeCapability.COMPILER_VERSION));
        assertEquals("true", profile.capabilityFacts().get("capability.compiler-version"));
        assertEquals("OpenCL C 3.0 Mock", profile.capabilityFacts().get("compilerVersion"));
    }

    @Test
    void publishesLifecycleEventsAroundCompileArtifactDumpInvocationAndClose() throws java.io.IOException {
        String property = "javatogpu.opencl.runtimeCompileArtifactDirectory";
        String previousArtifactRoot = System.getProperty(property);
        Path artifactRoot = Files.createTempDirectory("opencl-lifecycle-events");
        ArrayList<GpuRuntimeLifecycleEvent> events = new ArrayList<>();
        GpuRuntimeLifecycleEventListener listener = new GpuRuntimeLifecycleEventListener() {
            @Override
            public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
                events.add(event);
            }

            @Override
            public String extensionId() {
                return "test.opencl.lifecycle-listener";
            }

            @Override
            public String extensionVersion() {
                return "1";
            }
        };
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicBoolean compileCalledFromSharedPipeline = new AtomicBoolean();
        AtomicBoolean invokeCalledFromSharedPipeline = new AtomicBoolean();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend(
                OpenClGpuRuntimeBackend.CacheMode.INSTANCE,
                GpuRuntimeLifecycleEventBus.of(List.of(listener))
        ) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                return GpuBackendModuleArtifact.openClSource(
                        compileRequest.descriptor().kernelSource(),
                        compileRequest.descriptor().kernelResource(),
                        "test-lifecycle-lowerer"
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                compileCalledFromSharedPipeline.set(calledFromBackendExecutionPipeline());
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:lifecycle");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                invokeCalledFromSharedPipeline.set(calledFromBackendExecutionPipeline());
                // no-op: this test covers lifecycle dispatch, not native OpenCL execution.
            }
        };

        try {
            System.setProperty(property, artifactRoot.toString());
            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[4]}));
            backend.close();
        } finally {
            if (previousArtifactRoot == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previousArtifactRoot);
            }
        }

        List<GpuRuntimeLifecycleEventKind> kinds = events.stream().map(GpuRuntimeLifecycleEvent::kind).toList();
        assertEquals(List.of(
                GpuRuntimeLifecycleEventKind.IRGPU_LOAD_STARTED,
                GpuRuntimeLifecycleEventKind.IRGPU_LOAD_COMPLETED,
                GpuRuntimeLifecycleEventKind.VALIDATION_STARTED,
                GpuRuntimeLifecycleEventKind.VALIDATION_COMPLETED,
                GpuRuntimeLifecycleEventKind.DESCRIPTOR_DISCOVERY_STARTED,
                GpuRuntimeLifecycleEventKind.DESCRIPTOR_DISCOVERY_COMPLETED,
                GpuRuntimeLifecycleEventKind.VALIDATION_STARTED,
                GpuRuntimeLifecycleEventKind.VALIDATION_COMPLETED,
                GpuRuntimeLifecycleEventKind.VALIDATION_STARTED,
                GpuRuntimeLifecycleEventKind.VALIDATION_COMPLETED,
                GpuRuntimeLifecycleEventKind.OPTIMIZER_DISCOVERY_STARTED,
                GpuRuntimeLifecycleEventKind.OPTIMIZER_DISCOVERY_COMPLETED,
                GpuRuntimeLifecycleEventKind.OPTIMIZER_PASS_STARTED,
                GpuRuntimeLifecycleEventKind.OPTIMIZER_PASS_COMPLETED,
                GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_STARTED,
                GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_COMPLETED,
                GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_STARTED,
                GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_COMPLETED,
                GpuRuntimeLifecycleEventKind.FALLBACK_OR_ROLLBACK_SELECTED,
                GpuRuntimeLifecycleEventKind.SOURCE_SELECTION_DECIDED,
                GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_STARTED,
                GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED,
                GpuRuntimeLifecycleEventKind.ARTIFACT_DUMP_STARTED,
                GpuRuntimeLifecycleEventKind.ARTIFACT_DUMP_COMPLETED,
                GpuRuntimeLifecycleEventKind.INVOCATION_STARTED,
                GpuRuntimeLifecycleEventKind.INVOCATION_COMPLETED,
                GpuRuntimeLifecycleEventKind.RUNTIME_SHUTDOWN_STARTED,
                GpuRuntimeLifecycleEventKind.RUNTIME_SHUTDOWN_COMPLETED
        ), kinds);
        assertEquals("javatogpu/sample/Demo/kernel.cl", events.get(0).kernelResource());
        assertEquals("kernel", events.get(0).fields().get("runtime.kernel.name"));
        assertEquals("javatogpu/sample/Demo/kernel.cl", events.get(0).fields().get("runtime.kernel.resource"));
        assertEquals("OPENCL", events.get(0).fields().get("runtime.backend.target"));
        assertEquals("off", events.get(0).fields().get("runtime.compile.optimizationProfile"));
        assertEquals("primary", events.get(1).fields().get("loadRole"));
        assertEquals("false", events.get(1).fields().get("runtime.irgpu.present"));
        assertEquals("runtime-capabilities", events.get(8).fields().get("validation.stage"));
        assertEquals("kernel", events.get(8).fields().get("runtime.kernel.name"));
        assertEquals("succeeded", events.get(13).fields().get("status"));
        assertEquals("succeeded", events.get(13).fields().get("runtime.status"));
        assertEquals("0", events.get(13).fields().get("runtime.compile.arg.count"));
        assertEquals("test-lifecycle-lowerer", events.get(15).fields().get("module.lowererVersion"));
        assertEquals("test-lifecycle-lowerer", events.get(15).fields().get("runtime.module.lowererVersion"));
        assertEquals("missing", events.get(18).fields().get("status"));
        assertEquals("missing", events.get(18).fields().get("runtime.status"));
        assertEquals("missing", events.get(18).fields().get("runtime.ir.selectedStage"));
        assertEquals("none", events.get(18).fields().get("runtime.ir.fallbackDecision"));
        assertEquals("none", events.get(18).fields().get("runtime.fallback.decision"));
        assertEquals("true", events.get(18).fields().get("runtime.ir.productionGate.accepted"));
        assertEquals("descriptor-default", events.get(19).fields().get("status"));
        assertEquals("opencl-c", events.get(19).fields().get("runtime.module.format"));
        assertEquals("descriptor-default", events.get(19).fields().get("runtime.backend.source.status"));
        assertEquals("compile-descriptor-source", events.get(19).fields().get("runtime.backend.source.decision"));
        assertEquals("descriptor", events.get(19).fields().get("runtime.backend.source.selection"));
        assertEquals("false", events.get(19).fields().get("runtime.backend.source.irgpuRequested"));
        assertEquals("false", events.get(19).fields().get("runtime.backend.source.available"));
        assertEquals("opencl-source-compile", events.get(19).fields().get("runtime.backend.source.runtimeLoadMode"));
        assertEquals("succeeded", events.get(21).fields().get("status"));
        assertEquals("compiled:lifecycle", events.get(21).fields().get("cacheKey"));
        assertEquals("succeeded", events.get(21).fields().get("runtime.status"));
        assertEquals("OPENCL", events.get(21).fields().get("runtime.backend.target"));
        assertEquals("OpenCL", events.get(21).fields().get("runtime.backend.name"));
        assertEquals("Mock GPU", events.get(21).fields().get("runtime.device.label"));
        assertEquals("opencl-c", events.get(21).fields().get("runtime.module.format"));
        assertEquals("test-lifecycle-lowerer", events.get(21).fields().get("runtime.module.lowererVersion"));
        assertEquals("compiled:lifecycle", events.get(21).fields().get("runtime.cache.key"));
        assertEquals("true", events.get(21).fields().get("runtime.compilation.present"));
        assertEquals("true", events.get(21).fields().get("runtime.compilation.cacheKey.present"));
        assertEquals("true", events.get(21).fields().get("runtime.compilation.module.present"));
        assertEquals("opencl-c", events.get(21).fields().get("runtime.compilation.module.format"));
        assertEquals("false", events.get(21).fields().get("runtime.compilation.compileLog.present"));
        assertEquals("0", events.get(21).fields().get("runtime.compilation.binaryArtifact.count"));
        assertEquals("true", events.get(21).fields().get("runtime.backend.compilation.present"));
        assertEquals("SUCCEEDED", events.get(21).fields().get("runtime.backend.compilation.status"));
        assertEquals("true", events.get(21).fields().get("runtime.backend.compilation.compiled"));
        assertEquals("compile", events.get(21).fields().get("runtime.backend.compilation.stage.key"));
        assertEquals("SUCCEEDED", events.get(21).fields().get("runtime.backend.compilation.stage.status"));
        assertEquals("true", events.get(21).fields().get("runtime.backend.state.present"));
        assertEquals("INSTANCE", events.get(21).fields().get("runtime.backend.cache.mode"));
        assertEquals("1", events.get(21).fields().get("runtime.backend.compile.count"));
        assertEquals("0", events.get(21).fields().get("runtime.backend.cache.compileHit.count"));
        assertEquals("started", events.get(22).fields().get("runtime.status"));
        assertEquals("true", events.get(22).fields().get("runtime.artifactDump.present"));
        assertEquals("1", events.get(22).fields().get("runtime.artifactDump.directory.count"));
        assertEquals("succeeded", events.get(23).fields().get("runtime.status"));
        assertEquals("1", events.get(23).fields().get("runtime.artifactDump.directory.count"));
        assertTrue(Integer.parseInt(events.get(23).fields().get("runtime.artifactDump.artifact.count")) > 0);
        assertEquals("1", events.get(23).fields().get("directory.count"));
        assertEquals("4", events.get(24).fields().get("work.globalX"));
        assertEquals("4", events.get(24).fields().get("runtime.work.globalShape"));
        assertEquals("auto", events.get(24).fields().get("runtime.work.localShape"));
        assertEquals("true", events.get(24).fields().get("runtime.backend.prepare.present"));
        assertEquals("SUCCEEDED", events.get(24).fields().get("runtime.backend.prepare.status"));
        assertEquals("true", events.get(24).fields().get("runtime.backend.prepare.prepared"));
        assertEquals("opencl-kernel", events.get(24).fields().get("runtime.backend.prepare.kernel.kind"));
        assertEquals("true", events.get(24).fields().get("runtime.backend.invoke.present"));
        assertEquals("NOT_STARTED", events.get(24).fields().get("runtime.backend.invoke.status"));
        assertEquals("false", events.get(24).fields().get("runtime.backend.invoke.invoked"));
        assertEquals("invoke", events.get(24).fields().get("runtime.backend.invoke.stage.key"));
        assertEquals("true", events.get(24).fields().get("runtime.invocation.binding.present"));
        assertEquals("1", events.get(24).fields().get("runtime.invocation.binding.buffer.count"));
        assertEquals("0", events.get(24).fields().get("runtime.invocation.binding.local.count"));
        assertEquals("0", events.get(24).fields().get("runtime.invocation.binding.scalar.count"));
        assertEquals("1", events.get(24).fields().get("runtime.invocation.binding.argument.count"));
        assertEquals("1", events.get(24).fields().get("bufferBinding.count"));
        assertEquals("1", events.get(24).fields().get("argumentBinding.count"));
        assertEquals("true", events.get(24).fields().get("runtime.backend.state.present"));
        assertEquals("1", events.get(24).fields().get("runtime.backend.cache.compiledKernel.count"));
        assertEquals("1", events.get(24).fields().get("runtime.backend.invocation.count"));
        assertEquals("1", events.get(24).fields().get("runtime.backend.compile.count"));
        assertEquals("succeeded", events.get(25).fields().get("status"));
        assertEquals("SUCCEEDED", events.get(25).fields().get("runtime.backend.invoke.status"));
        assertEquals("true", events.get(25).fields().get("runtime.backend.invoke.invoked"));
        assertEquals("true", events.get(25).fields().get("runtime.backend.invoke.readback.complete"));
        assertEquals("opencl-c", events.get(25).fields().get("runtime.module.format"));
        assertEquals("INSTANCE", events.get(27).fields().get("cacheMode"));
        assertEquals("true", events.get(27).fields().get("runtime.backend.state.present"));
        assertEquals("INSTANCE", events.get(27).fields().get("runtime.backend.cache.mode"));
        assertEquals("1", events.get(27).fields().get("runtime.backend.invocation.count"));
        assertEquals("1", events.get(27).fields().get("runtime.backend.compile.count"));
        assertTrue(compileCalledFromSharedPipeline.get());
        assertTrue(invokeCalledFromSharedPipeline.get());
    }

    @Test
    void backendHookRegistryEnrichesOpenClCompilationAndInvocationLifecycleEvents() {
        ArrayList<GpuRuntimeLifecycleEvent> events = new ArrayList<>();
        GpuRuntimeLifecycleEventListener listener = new GpuRuntimeLifecycleEventListener() {
            @Override
            public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
                events.add(event);
            }

            @Override
            public String extensionId() {
                return "test.opencl.hook-lifecycle-listener";
            }
        };
        AtomicInteger loweringHookCalls = new AtomicInteger();
        AtomicInteger compilationHookCalls = new AtomicInteger();
        AtomicInteger invocationHookCalls = new AtomicInteger();
        AtomicInteger artifactHookCalls = new AtomicInteger();
        GpuBackendHookRegistry hookRegistry = GpuBackendHookRegistry.of(List.of(
                new GpuBackendLoweringHook() {
                    @Override
                    public String extensionId() {
                        return "test.opencl.lowering-hook";
                    }

                    @Override
                    public GpuBackendLoweringResult afterLowering(
                            GpuRuntimeCompileRequest compileRequest,
                            GpuBackendLoweringResult loweringResult
                    ) {
                        loweringHookCalls.incrementAndGet();
                        return loweringResult;
                    }
                },
                new GpuBackendCompilationHook() {
                    @Override
                    public String extensionId() {
                        return "test.opencl.compilation-hook";
                    }

                    @Override
                    public net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationResult afterCompilation(
                            GpuRuntimeCompileRequest compileRequest,
                            net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilationResult compilationResult
                    ) {
                        compilationHookCalls.incrementAndGet();
                        return compilationResult;
                    }
                },
                new GpuBackendInvocationHook() {
                    @Override
                    public String extensionId() {
                        return "test.opencl.invocation-hook";
                    }

                    @Override
                    public net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationResult afterInvocation(
                            GpuRuntimeCompileRequest compileRequest,
                            net.sixik.ga_utils.javatogpu.runtime.GpuBackendInvocationResult invocationResult
                    ) {
                        invocationHookCalls.incrementAndGet();
                        return invocationResult;
                    }
                },
                new GpuBackendArtifactHook() {
                    @Override
                    public String extensionId() {
                        return "test.opencl.artifact-hook";
                    }

                    @Override
                    public Map<String, String> contributeArtifactFields(
                            GpuRuntimeCompileRequest compileRequest,
                            Map<String, String> currentFields
                    ) {
                        artifactHookCalls.incrementAndGet();
                        return Map.of("test.opencl.hook.status", currentFields.getOrDefault("status", "missing"));
                    }
                }
        ));
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend(
                OpenClGpuRuntimeBackend.CacheMode.INSTANCE,
                GpuRuntimeLifecycleEventBus.of(List.of(listener)),
                hookRegistry
        ) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                return GpuBackendModuleArtifact.openClSource(
                        compileRequest.descriptor().kernelSource(),
                        compileRequest.descriptor().kernelResource(),
                        "test-hook-lowerer"
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:hook-lifecycle");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op: this test verifies hook lifecycle enrichment, not native OpenCL execution.
            }
        };

        backend.invoke(new GpuKernelInvocation(intOutputDescriptor(), new Object[]{new int[4]}));

        GpuRuntimeLifecycleEvent loweringCompleted = events.stream()
                .filter(event -> event.kind() == GpuRuntimeLifecycleEventKind.BACKEND_LOWERER_SELECTION_COMPLETED)
                .filter(event -> "succeeded".equals(event.fields().get("status")))
                .findFirst()
                .orElseThrow();
        GpuRuntimeLifecycleEvent compilationCompleted = events.stream()
                .filter(event -> event.kind() == GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED)
                .filter(event -> "succeeded".equals(event.fields().get("status")))
                .findFirst()
                .orElseThrow();
        GpuRuntimeLifecycleEvent invocationCompleted = events.stream()
                .filter(event -> event.kind() == GpuRuntimeLifecycleEventKind.INVOCATION_COMPLETED)
                .filter(event -> "succeeded".equals(event.fields().get("status")))
                .findFirst()
                .orElseThrow();

        assertEquals(1, loweringHookCalls.get());
        assertEquals(2, compilationHookCalls.get());
        assertEquals(2, invocationHookCalls.get());
        assertEquals(4, artifactHookCalls.get());
        assertEquals("1", loweringCompleted.fields().get("runtime.backend.hookExecution.lowering.hook.count"));
        assertEquals("1", loweringCompleted.fields().get("runtime.backend.hookExecution.lowering.applied.count"));
        assertEquals("test.opencl.lowering-hook", loweringCompleted.fields().get("runtime.backend.hookExecution.lowering.hook.0.id"));
        assertEquals("1", compilationCompleted.fields().get("runtime.backend.hookExecution.compilation.hook.count"));
        assertEquals("1", compilationCompleted.fields().get("runtime.backend.hookExecution.compilation.applied.count"));
        assertEquals("test.opencl.compilation-hook", compilationCompleted.fields().get("runtime.backend.hookExecution.compilation.hook.0.id"));
        assertEquals("test.opencl.hook.status", compilationCompleted.fields().get("runtime.backend.hookExecution.compilationArtifact.hook.0.contribution.0.key"));
        assertEquals("succeeded", compilationCompleted.fields().get("runtime.backend.hookExecution.compilationArtifact.hook.0.contribution.0.value"));
        assertEquals("1", invocationCompleted.fields().get("runtime.backend.hookExecution.invocation.hook.count"));
        assertEquals("1", invocationCompleted.fields().get("runtime.backend.hookExecution.invocation.applied.count"));
        assertEquals("test.opencl.invocation-hook", invocationCompleted.fields().get("runtime.backend.hookExecution.invocation.hook.0.id"));
        assertEquals("test.opencl.hook.status", invocationCompleted.fields().get("runtime.backend.hookExecution.invocationArtifact.hook.0.contribution.0.key"));
        assertEquals("succeeded", invocationCompleted.fields().get("runtime.backend.hookExecution.invocationArtifact.hook.0.contribution.0.value"));
    }

    @Test
    void compileCacheSeparatesVariantsByDeviceProfile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<String> currentDeviceLabel = new AtomicReference<>("Mock GPU A");
        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuRuntimeDeviceProfile compileDeviceProfile() {
                return new GpuRuntimeDeviceProfile(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        currentDeviceLabel.get(),
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock"
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        currentDeviceLabel.set("Mock GPU B");
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void compileCacheSeparatesVariantsByCompileOptions() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger compileCalls = new AtomicInteger();
        AtomicReference<GpuRuntimeCompileRequest> lastCompileRequest = new AtomicReference<>();
        GpuRuntimeCompileOptions fastOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of("-cl-fast-relaxed-math"),
                "fast"
        );
        GpuRuntimeCompileOptions preciseOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of("-cl-opt-disable"),
                "precise"
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                lastCompileRequest.set(compileRequest);
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, fastOptions));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, fastOptions));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, preciseOptions));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, preciseOptions));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
        assertEquals("precise", lastCompileRequest.get().options().optimizationProfile());
        assertEquals(java.util.List.of("-cl-opt-disable"), lastCompileRequest.get().options().compileArgs());
    }

    @Test
    void defaultCompilePathUsesBackendLowererBoundary() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger lowerCalls = new AtomicInteger();
        AtomicInteger moduleCompileCalls = new AtomicInteger();
        AtomicInteger legacyCompileCalls = new AtomicInteger();
        GpuBackendModuleArtifact loweredArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* output) { output[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                lowerCalls.incrementAndGet();
                return loweredArtifact;
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuBackendModuleArtifact moduleArtifact,
                    GpuKernelDescriptor kernelDescriptor
            ) {
                moduleCompileCalls.incrementAndGet();
                assertSame(loweredArtifact, moduleArtifact);
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:lowered");
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                legacyCompileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:legacy");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                assertEquals("compiled:lowered", execution.compiledKernel().cacheKey());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(1, lowerCalls.get());
        assertEquals(1, moduleCompileCalls.get());
        assertEquals(0, legacyCompileCalls.get());
    }

    @Test
    void compileCacheSeparatesVariantsByLoweredBackendModuleArtifact() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger lowerVariant = new AtomicInteger(1);
        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                int variant = lowerVariant.get();
                return GpuBackendModuleArtifact.openClSource(
                        "__kernel void kernel(__global int* output) { output[0] = " + variant + "; }",
                        "runtime/lowered/kernel-v" + variant + ".cl",
                        "test-lowerer-v" + variant
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        moduleArtifact.resource() + ":" + compileCalls.incrementAndGet()
                );
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        lowerVariant.set(2);
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void compileCacheSeparatesVariantsByOptimizedIrGpuArtifactIdentity() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger irVariant = new AtomicInteger(1);
        AtomicInteger compileCalls = new AtomicInteger();
        GpuBackendModuleArtifact loweredArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(testIrGpuArtifact(
                        "body\n  return variant " + irVariant.get() + "\n"
                )));
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                return loweredArtifact;
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        moduleArtifact.resource() + ":" + compileCalls.incrementAndGet()
                );
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        irVariant.set(2);
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void compileCacheSeparatesVariantsByBackendArtifactVersion() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger artifactVersion = new AtomicInteger(1);
        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                int version = artifactVersion.get();
                return new GpuBackendModuleArtifact(
                        GpuBackendTarget.OPENCL,
                        "source",
                        "opencl-c",
                        "__kernel void kernel(__global int* output) { output[0] = 1; }",
                        "runtime/lowered/kernel.cl",
                        "opencl:source:opencl-c:v" + version,
                        "test-lowerer"
                );
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        moduleArtifact.artifactVersion() + ":" + compileCalls.incrementAndGet()
                );
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        artifactVersion.set(2);
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void compileCacheSeparatesVariantsByOptimizerPipelineVersion() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<String> optimizerVersion = new AtomicReference<>("optimizer:test-v1");
        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected String optimizerPipelineVersion() {
                return optimizerVersion.get();
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        optimizerVersion.set("optimizer:test-v2");
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, backend.cacheSize());
    }

    @Test
    void rejectsUnsupportedCompileOptionBeforeRuntimeCapabilityLookup() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of("--cuda-fast-math"),
                "diagnostic"
        );
        AtomicInteger capabilityLookups = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                capabilityLookups.incrementAndGet();
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        GpuRuntimeCompileOptionsException exception = assertThrows(
                GpuRuntimeCompileOptionsException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, compileOptions))
        );

        assertEquals(0, capabilityLookups.get());
        assertTrue(exception.getMessage().contains("Unsupported OpenCL compile option"));
        assertTrue(exception.getMessage().contains("--cuda-fast-math"));
    }

    @Test
    void rejectsUnsupportedOpenClSourceSelectionBeforeRuntimeCapabilityLookup() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of(),
                "diagnostic",
                GpuBackendCompileOptions.openCl(
                        java.util.List.of(),
                        java.util.Map.of(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY, "compiled-binary")
                )
        );
        AtomicInteger capabilityLookups = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                capabilityLookups.incrementAndGet();
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        GpuRuntimeCompileOptionsException exception = assertThrows(
                GpuRuntimeCompileOptionsException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, compileOptions))
        );

        assertEquals(0, capabilityLookups.get());
        assertTrue(exception.getMessage().contains("Unsupported OpenCL source selection compile option"));
        assertTrue(exception.getMessage().contains("compiled-binary"));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_DESCRIPTOR));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU));
    }

    @Test
    void rejectsUnsupportedOpenClProductionSourceSwitchingBeforeRuntimeCapabilityLookup() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of(),
                "diagnostic",
                GpuBackendCompileOptions.openCl(
                        java.util.List.of(),
                        java.util.Map.of(
                                GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY,
                                "auto"
                        )
                )
        );
        AtomicInteger capabilityLookups = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                capabilityLookups.incrementAndGet();
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        GpuRuntimeCompileOptionsException exception = assertThrows(
                GpuRuntimeCompileOptionsException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, compileOptions))
        );

        assertEquals(0, capabilityLookups.get());
        assertTrue(exception.getMessage().contains("Unsupported OpenCL production source switching compile option"));
        assertTrue(exception.getMessage().contains("auto"));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_DISABLED));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED));
    }

    @Test
    void rejectsUnsupportedRuntimeIrOptimizerSelectionBeforeRuntimeCapabilityLookup() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.OPENCL,
                java.util.List.of(),
                "diagnostic",
                GpuBackendCompileOptions.openCl(
                        java.util.List.of(),
                        java.util.Map.of(
                                GpuBackendCompileOptions.RUNTIME_IR_OPTIMIZER_SELECTION_PROPERTY,
                                "auto"
                        )
                )
        );
        AtomicInteger capabilityLookups = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                capabilityLookups.incrementAndGet();
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        GpuRuntimeCompileOptionsException exception = assertThrows(
                GpuRuntimeCompileOptionsException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, compileOptions))
        );

        assertEquals(0, capabilityLookups.get());
        assertTrue(exception.getMessage().contains("Unsupported runtime IR optimizer selection compile option"));
        assertTrue(exception.getMessage().contains("auto"));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.RUNTIME_IR_OPTIMIZER_SELECTION_REVIEW_ONLY));
        assertTrue(exception.getMessage().contains(GpuBackendCompileOptions.RUNTIME_IR_OPTIMIZER_SELECTION_EXPERIMENTAL_APPLY));
    }

    @Test
    void rejectsCompileOptionsForOtherBackendBeforeRuntimeCapabilityLookup() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
                GpuBackendTarget.CUDA,
                java.util.List.of(),
                "diagnostic"
        );
        AtomicInteger capabilityLookups = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                capabilityLookups.incrementAndGet();
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuRuntimeCompileRequest compileRequest) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        GpuRuntimeCompileOptionsException exception = assertThrows(
                GpuRuntimeCompileOptionsException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}, compileOptions))
        );

        assertEquals(0, capabilityLookups.get());
        assertTrue(exception.getMessage().contains("OpenCL backend cannot use compile options for backend CUDA"));
    }

    @Test
    void runtimeIrOptimizerRunsBeforeLegacyKernelCompileHook() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicInteger optimizerCalls = new AtomicInteger();
        AtomicInteger legacyCompileCalls = new AtomicInteger();
        AtomicReference<GpuRuntimeCompileRequest> optimizerRequest = new AtomicReference<>();

        GpuRuntimeIrOptimizerRegistry optimizerRegistry = GpuRuntimeIrOptimizerRegistry.of(java.util.List.of(request -> {
            optimizerCalls.incrementAndGet();
            optimizerRequest.set(request.compileRequest());
            return request.artifact();
        }));

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend(
                OpenClGpuRuntimeBackend.CacheMode.INSTANCE,
                optimizerRegistry
        ) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                legacyCompileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        assertEquals(2, optimizerCalls.get());
        assertEquals(1, legacyCompileCalls.get());
        assertSame(descriptor, optimizerRequest.get().descriptor());
        assertEquals(GpuBackendTarget.OPENCL, optimizerRequest.get().options().backendTarget());
        assertEquals("off", optimizerRequest.get().options().optimizationProfile());
    }

    @Test
    void runtimeIrOptimizerReceivesClasspathIrGpuArtifact() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-irgpu-runtime-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, """
                # JavaToGpu backend-neutral IR artifact manifest
                backendOutput.0.backend=opencl
                backendOutput.0.format=opencl-c
                backendOutput.0.kind=source
                backendOutput.0.resource=javatogpu/sample/Demo/kernel.cl
                backendOutput.count=1
                compilerArtifact=JavaToGpu
                derived.opencl.resource=javatogpu/sample/Demo/kernel.cl
                entryEmittedName=jtg_kernel
                entryMethod=kernel
                format=javatogpu.irgpu.v1
                helper.count=0
                methodBody.0.body=method jtg_kernel source\\=kernel\\nhelpers -\\nbody\\n  return\\n
                methodBody.0.emittedName=jtg_kernel
                methodBody.0.format=ir-text-v1
                methodBody.0.helperDependency.count=0
                methodBody.0.name=kernel
                methodBody.0.role=entry
                methodBody.0.source.beginColumn=17
                methodBody.0.source.beginLine=4
                methodBody.0.source.endColumn=5
                methodBody.0.source.endLine=7
                methodBody.0.source.kind=java-source
                methodBody.0.source.methodName=kernel
                methodBody.0.source.ownerQualifiedName=sample.Demo
                methodBody.count=1
                runtime.defaultBackend=opencl
                runtime.optimizationProfile=off
                schemaVersion=1
                sourceFrontend=java-source
                struct.count=0
                """);

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        AtomicReference<IrGpuArtifact> capturedArtifact = new AtomicReference<>();
        AtomicReference<IrGpuArtifact> lowererArtifact = new AtomicReference<>();

        GpuRuntimeIrOptimizerRegistry optimizerRegistry = GpuRuntimeIrOptimizerRegistry.of(java.util.List.of(request -> {
            request.artifact().ifPresent(capturedArtifact::set);
            return request.artifact();
        }));

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend(
                    OpenClGpuRuntimeBackend.CacheMode.INSTANCE,
                    optimizerRegistry
            ) {
                @Override
                protected OpenClRuntimeCapabilities runtimeCapabilities() {
                    return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
                }

                @Override
                protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                    return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    compileRequest.irGpuArtifact().ifPresent(lowererArtifact::set);
                    return super.lowerBackendModule(compileRequest);
                }

                @Override
                protected void executeKernel(OpenClPreparedExecution execution) {
                    // no-op
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        IrGpuArtifact artifact = capturedArtifact.get();
        assertEquals("javatogpu.irgpu.v1", artifact.header().format());
        assertEquals("kernel", artifact.module().entryMethod());
        assertEquals("jtg_kernel", artifact.module().entryEmittedName());
        assertEquals(1, artifact.module().methodBodies().size());
        assertEquals("entry", artifact.module().methodBodies().get(0).role());
        assertEquals("ir-text-v1", artifact.module().methodBodies().get(0).format());
        assertTrue(artifact.module().methodBodies().get(0).body().contains("method jtg_kernel source=kernel"));
        assertSame(artifact, lowererArtifact.get());
        assertEquals(1, artifact.backendOutputs().size());
        assertEquals("opencl", artifact.backendOutputs().get(0).backend());
        assertEquals("javatogpu/sample/Demo/kernel.cl", artifact.derivedOpenClResource());
    }

    @Test
    void compiledKernelCarriesRuntimeArtifactSnapshot() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-irgpu-snapshot-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, """
                # JavaToGpu backend-neutral IR artifact manifest
                backendOutput.0.backend=opencl
                backendOutput.0.format=opencl-c
                backendOutput.0.kind=source
                backendOutput.0.resource=javatogpu/sample/Demo/kernel.cl
                backendOutput.count=1
                compilerArtifact=JavaToGpu
                derived.opencl.resource=javatogpu/sample/Demo/kernel.cl
                entryEmittedName=jtg_kernel
                entryMethod=kernel
                format=javatogpu.irgpu.v1
                helper.count=0
                methodBody.0.body=method jtg_kernel source\\=kernel\\nhelpers -\\nbody\\n  return original\\n
                methodBody.0.emittedName=jtg_kernel
                methodBody.0.format=ir-text-v1
                methodBody.0.helperDependency.count=0
                methodBody.0.name=kernel
                methodBody.0.role=entry
                methodBody.0.source.beginColumn=17
                methodBody.0.source.beginLine=4
                methodBody.0.source.endColumn=5
                methodBody.0.source.endLine=7
                methodBody.0.source.kind=java-source
                methodBody.0.source.methodName=kernel
                methodBody.0.source.ownerQualifiedName=sample.Demo
                methodBody.count=1
                runtime.defaultBackend=opencl
                runtime.optimizationProfile=off
                schemaVersion=1
                sourceFrontend=java-source
                struct.count=0
                """);

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact optimizedArtifact = testIrGpuArtifact("body\n  return optimized\n");
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        GpuBackendModuleArtifact loweredArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* output) { output[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
                @Override
                protected OpenClRuntimeCapabilities runtimeCapabilities() {
                    return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
                }

                @Override
                protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                    assertTrue(compileRequest.irGpuArtifact().orElseThrow()
                            .module()
                            .methodBodies()
                            .get(0)
                            .body()
                            .contains("return original"));
                    return compileRequest.withIrGpuArtifact(java.util.Optional.of(optimizedArtifact));
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    assertSame(optimizedArtifact, compileRequest.irGpuArtifact().orElseThrow());
                    return loweredArtifact;
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
                }

                @Override
                protected void executeKernel(OpenClPreparedExecution execution) {
                    capturedSnapshot.set(execution.compiledKernel().artifactSnapshot());
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertTrue(snapshot.originalIrGpuArtifact().orElseThrow()
                .module()
                .methodBodies()
                .get(0)
                .body()
                .contains("return original"));
        assertSame(optimizedArtifact, snapshot.optimizedIrGpuArtifact().orElseThrow());
        assertSame(loweredArtifact, snapshot.backendModuleArtifact());
        assertEquals("javatogpu.irgpu.v1", snapshot.invalidationStamp().irFormat());
        assertEquals(1, snapshot.invalidationStamp().irSchemaVersion());
        assertEquals("JavaToGpu", snapshot.invalidationStamp().compilerArtifact());
        assertEquals("java-source", snapshot.invalidationStamp().sourceFrontend());
        assertEquals(loweredArtifact.artifactVersion(), snapshot.invalidationStamp().backendArtifactVersion());
        assertEquals(loweredArtifact.lowererVersion(), snapshot.invalidationStamp().backendLowererVersion());
        assertEquals(GpuBackendTarget.OPENCL, snapshot.compileProvenance().backendTarget());
        assertEquals("off", snapshot.compileProvenance().optimizationProfile());
        assertTrue(snapshot.compileProvenance().compileArgs().isEmpty());
        assertEquals("none", snapshot.compileProvenance().fallbackDecision());
        assertTrue(snapshot.backendSourcePromotionGate().isPresent());
        assertEquals("blocked", snapshot.backendSourcePromotionGate().orElseThrow().status());
        assertTrue(snapshot.backendSourceSwitchingDecision().isPresent());
        assertEquals("descriptor-default", snapshot.backendSourceSwitchingDecision().orElseThrow().status());
        assertEquals("compile-descriptor-source", snapshot.backendSourceSwitchingDecision().orElseThrow().decision());
        assertFalse(snapshot.backendSourceSwitchingDecision().orElseThrow().irGpuSourceRequested());
        assertEquals(1, snapshot.sourceLocations().size());
        assertEquals("java-source", snapshot.sourceLocations().get(0).sourceKind());
        assertEquals("kernel", snapshot.sourceLocations().get(0).methodName());
        assertTrue(snapshot.compileLog().isBlank());
        assertTrue(snapshot.runtimeValidationEvidence().isEmpty());
    }

    @Test
    void runtimeEquivalenceDefaultsToNotRunWhenBackendDoesNotEnablePrePostExecution() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot);

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("not-run", snapshot.runtimeEquivalenceEvidence().status());
        assertFalse(snapshot.runtimeEquivalenceEvidence().executed());
        assertEquals("none", snapshot.fallbackEvidence().decision());
        assertEquals("not-requested", snapshot.productionOptimizerGate().status());
    }

    @Test
    void runtimeEquivalenceHookCanPersistPassedEvidenceForOptimizedIr() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeEquivalenceRequest> capturedRequest = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
            @Override
            protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                capturedRequest.set(request);
                return GpuRuntimeEquivalenceEvidence.passed(
                        request.optimizedCompileRequest(),
                        3,
                        1,
                        List.of("deterministic pre/post outputs matched")
                );
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertSame(descriptor, capturedRequest.get().originalCompileRequest().descriptor());
        assertSame(descriptor, capturedRequest.get().optimizedCompileRequest().descriptor());
        assertTrue(capturedRequest.get().hasInvocationContext());
        assertNotSame(capturedRequest.get().invocationArguments(), capturedRequest.get().invocationArguments());
        assertEquals(1, capturedRequest.get().invocationArguments().length);
        assertFalse(capturedRequest.get().hasOptimizedTransform());
        assertEquals("passed", snapshot.runtimeEquivalenceEvidence().status(), snapshot.runtimeEquivalenceEvidence().diagnostics().toString());
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertTrue(snapshot.runtimeEquivalenceEvidence().equivalent());
        assertEquals(3, snapshot.runtimeEquivalenceEvidence().inputCaseCount());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertEquals("none", snapshot.fallbackEvidence().decision());
    }

    @Test
    void runtimeEquivalencePreflightRunsIsolatedPrimitiveArrayComparison() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        IrGpuArtifact artifact = parityMatchedIrGpuArtifact();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        ArrayList<String> compiledResources = new ArrayList<>();
        ArrayList<String> compiledSources = new ArrayList<>();
        int[] productionOutput = new int[]{0};

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 7) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                compiledResources.add(moduleArtifact.resource());
                compiledSources.add(moduleArtifact.source());
                return new OpenClCompiledKernel(compileRequest.descriptor(), moduleArtifact.resource());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{productionOutput}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("passed", snapshot.runtimeEquivalenceEvidence().status(), snapshot.runtimeEquivalenceEvidence().diagnostics().toString());
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertTrue(snapshot.runtimeEquivalenceEvidence().equivalent());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().inputCaseCount());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparisonCases().size());
        GpuRuntimeEquivalenceCaseEvidence caseEvidence = snapshot.runtimeEquivalenceEvidence().comparisonCases().get(0);
        assertEquals("descriptor-source-vs-irgpu-reconstructed-source", caseEvidence.comparisonMode());
        assertEquals("[0]", caseEvidence.inputs().get("output"));
        assertEquals("[7]", caseEvidence.referenceOutputs().get("output"));
        assertEquals("[7]", caseEvidence.candidateOutputs().get("output"));
        assertEquals("exact-int-array", caseEvidence.tolerances().get("output"));
        assertTrue(caseEvidence.outputEquivalence().get("output"));
        assertTrue(snapshot.runtimeEquivalenceEvidence().diagnostics().contains(
                "descriptor and reconstructed OpenCL outputs matched for isolated array runtime-equivalence"
        ));
        assertTrue(snapshot.runtimeEquivalenceEvidence().toPropertiesText().contains(
                "comparison.case.0.output.0.reference=[7]"
        ));
        assertEquals(3, compiledResources.size());
        assertEquals("javatogpu/sample/Demo/kernel.cl", compiledResources.get(0));
        assertTrue(compiledResources.get(1).contains("#irgpu-reconstructed-equivalence-preflight"));
        assertEquals("javatogpu/sample/Demo/kernel.cl", compiledResources.get(2));
        assertEquals(descriptor.kernelSource(), compiledSources.get(0));
        assertEquals(canonicalOpenClSource(descriptor.kernelSource()), canonicalOpenClSource(compiledSources.get(1)));
        assertEquals(descriptor.kernelSource(), compiledSources.get(2));
        assertArrayEquals(new int[]{1}, productionOutput);
    }

    @Test
    void runtimeEquivalenceRunsCseFamilyOriginalVsOptimizedComparisonWithoutProductionMutation() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        IrGpuArtifact optimizedArtifact = parityMatchedIrGpuArtifact();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        ArrayList<String> compiledResources = new ArrayList<>();
        int[] productionOutput = new int[]{0};

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 7) {
            @Override
            protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                GpuRuntimeCompileRequest optimizedRequest = compileRequest.withIrGpuArtifact(java.util.Optional.of(optimizedArtifact));
                String originalIdentity = IrGpuArtifactIdentity.stableIdentity(compileRequest.irGpuArtifact());
                String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(optimizedRequest.irGpuArtifact());
                GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.applied(
                        "optimizer-family:cse:review-v1",
                        originalIdentity,
                        optimizedIdentity,
                        "cse-review-evidence-ready",
                        List.of("CSE family evidence captured for original-vs-optimized lane")
                ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "runtime.cse.review",
                        "review-ready",
                        java.util.Map.ofEntries(
                                java.util.Map.entry("optimizerFamily", "cse"),
                                java.util.Map.entry("firstBlocker", "none"),
                                java.util.Map.entry("runtimeEquivalencePayload.present", "true"),
                                java.util.Map.entry("runtimeEquivalencePayload.cpuReference.present", "true"),
                                java.util.Map.entry("runtimeEquivalencePayload.preOptimizationOutput.present", "true"),
                                java.util.Map.entry("runtimeEquivalencePayload.postOptimizationOutput.present", "true"),
                                java.util.Map.entry("runtimeEquivalencePayload.tolerance.present", "true"),
                                java.util.Map.entry("runtimeEquivalencePayload.failureFixture.present", "true")
                        )
                ));
                return new GpuRuntimeIrOptimizationResult(
                        optimizedRequest,
                        new GpuRuntimeIrOptimizationReport(java.util.Optional.of(optimizedArtifact), List.of(passReport))
                );
            }

            @Override
            protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.irGpuArtifact().isPresent()
                        ? GpuBackendModuleArtifact.openClSource(
                        "__kernel void kernel(__global int* output) { output[0] = 7; }",
                        "javatogpu/sample/Demo/kernel.cl#irgpu-" + compiledResources.size(),
                        "test-lowerer-v1",
                        "test-irgpu-source",
                        "test-irgpu-source-compile"
                )
                        : super.lowerBackendModule(compileRequest);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                compiledResources.add(moduleArtifact.resource());
                return new OpenClCompiledKernel(compileRequest.descriptor(), moduleArtifact.resource());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{productionOutput}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("passed", snapshot.runtimeEquivalenceEvidence().status(), snapshot.runtimeEquivalenceEvidence().diagnostics().toString());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparisonCases().size());
        GpuRuntimeEquivalenceCaseEvidence caseEvidence = snapshot.runtimeEquivalenceEvidence().comparisonCases().get(0);
        assertEquals("optimizer-family:cse:original-vs-optimized", caseEvidence.comparisonMode());
        assertEquals("[7]", caseEvidence.referenceOutputs().get("output"));
        assertEquals("[7]", caseEvidence.candidateOutputs().get("output"));
        assertTrue(snapshot.runtimeEquivalenceEvidence().diagnostics().contains(
                "optimizer family cse original and optimized OpenCL outputs matched"
        ));
        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);
        String payload = dump.artifact("runtime-optimizer-family-equivalence-payload.properties");
        assertTrue(payload.contains("familyBinding.status=bound"));
        assertTrue(payload.contains("familyBinding.eligible=true"));
        assertTrue(payload.contains("familyBinding.family=cse"));
        assertTrue(payload.contains("runtimeEquivalence.comparisonMode.summary={optimizer-family:cse:original-vs-optimized=1}"));
        assertArrayEquals(new int[]{1}, productionOutput);
    }

    @Test
    void runtimeEquivalenceFailsWhenIsolatedPrimitiveArrayOutputsDiffer() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        IrGpuArtifact artifact = parityMatchedIrGpuArtifact();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        ArrayList<String> compiledResources = new ArrayList<>();

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 9) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }

            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                compiledResources.add(moduleArtifact.resource());
                return new OpenClCompiledKernel(compileRequest.descriptor(), moduleArtifact.resource());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("failed", snapshot.runtimeEquivalenceEvidence().status());
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertFalse(snapshot.runtimeEquivalenceEvidence().equivalent());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().inputCaseCount());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparisonCases().size());
        GpuRuntimeEquivalenceCaseEvidence caseEvidence = snapshot.runtimeEquivalenceEvidence().comparisonCases().get(0);
        assertEquals("[7]", caseEvidence.referenceOutputs().get("output"));
        assertEquals("[9]", caseEvidence.candidateOutputs().get("output"));
        assertEquals("exact-int-array", caseEvidence.tolerances().get("output"));
        assertFalse(caseEvidence.outputEquivalence().get("output"));
        assertEquals(1, caseEvidence.diagnostics().size());
        assertTrue(snapshot.runtimeEquivalenceEvidence().diagnostics().contains(
                "runtime-equivalence output mismatch for parameter 'output' at argument 0"
        ));
        assertEquals("runtime-equivalence-failed", snapshot.fallbackEvidence().decision());
        assertTrue(compiledResources.get(1).contains("#irgpu-reconstructed-equivalence-preflight"));
    }

    @Test
    void runtimeEquivalenceSkipsUnsupportedPrimitiveArrayComparisonWithoutProductionMutation() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* input) { return; }",
                java.util.List.of(new GpuKernelParameterDescriptor("input", "int[]", GpuKernelParameterAccess.READ_ONLY))
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "kernel",
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "kernel",
                                "body\n  return\n",
                                java.util.List.of()
                        ))
                ),
                java.util.List.of(new net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter(
                        "input",
                        "int[]",
                        "GLOBAL",
                        false,
                        java.util.List.of()
                )),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.backendNeutralReady(),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        int[] input = new int[]{3};

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 7) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("not-run", snapshot.runtimeEquivalenceEvidence().status());
        assertFalse(snapshot.runtimeEquivalenceEvidence().executed());
        assertTrue(snapshot.runtimeEquivalenceEvidence().diagnostics().contains(
                "runtime-equivalence output comparison requires at least one READ_WRITE array output"
        ));
        assertArrayEquals(new int[]{3}, input);
    }

    @Test
    void runtimeEquivalenceComparesVectorArrayOutputsThroughPackedAbiBytes() {
        GpuKernelDescriptor descriptor = float2OutputDescriptor();
        IrGpuArtifact artifact = parityMatchedFloat2IrGpuArtifact();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        Float2[] output = new Float2[]{new Float2()};

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 7) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals(
                "passed",
                snapshot.runtimeEquivalenceEvidence().status(),
                snapshot.runtimeEquivalenceEvidence().diagnostics().toString()
        );
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertEquals(1.0f, output[0].x);
        assertEquals(2.0f, output[0].y);
    }

    @Test
    void runtimeEquivalenceComparesStructArrayOutputsThroughPackedAbiBytes() {
        GpuKernelDescriptor descriptor = structOutputDescriptor();
        IrGpuArtifact artifact = parityMatchedStructIrGpuArtifact();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        EquivalenceStructSample[] output = new EquivalenceStructSample[]{new EquivalenceStructSample()};

        OpenClGpuRuntimeBackend backend = new EquivalenceSimulatingBackend(capturedSnapshot, 7, 7) {
            @Override
            protected GpuRuntimeCompileRequest optimizeRuntimeIr(GpuRuntimeCompileRequest compileRequest) {
                return compileRequest.withIrGpuArtifact(java.util.Optional.of(artifact));
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals(
                "passed",
                snapshot.runtimeEquivalenceEvidence().status(),
                snapshot.runtimeEquivalenceEvidence().diagnostics().toString()
        );
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertEquals(1, snapshot.runtimeEquivalenceEvidence().comparedOutputCount());
        assertEquals(11, output[0].x);
        assertEquals(12.5f, output[0].y);
    }

    @Test
    void writesWorkloadSourcePromotionGateFromRuntimeSnapshotWhenConfigured() throws Exception {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        Path reportDirectory = Files.createTempDirectory("javatogpu-opencl-workload-source-promotion");
        Path gateFile = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        String previousGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", gateFile.toString());
            String compileLog = "Used 36 registers, 0 bytes spill stores, 0 bytes spill loads";

            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    return new OpenClCompiledKernel(
                            compileRequest.descriptor(),
                            "compiled:artifact-export",
                            GpuRuntimeCompileArtifactSnapshot.legacy(compileRequest.descriptor())
                                    .withCompileLog(compileLog),
                            null,
                            null
                    );
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

            String gateProperties = Files.readString(gateFile);
            assertEquals("javatogpu/sample/Demo/kernel.cl", capturedSnapshot.get().backendModuleArtifact().resource());
            assertTrue(gateProperties.contains("status=blocked"));
            assertTrue(gateProperties.contains("reviewReady=false"));
            assertTrue(gateProperties.contains("runtimeEquivalencePassed=false"));
            assertTrue(gateProperties.contains("scope=real-workload"));
            assertTrue(gateProperties.contains("productionSourceSwitching=false"));
            assertTrue(gateProperties.contains("realWorkloadEvidence=runtime-snapshot"));
            assertTrue(gateProperties.contains("kernel.count=1"));
            assertTrue(gateProperties.contains("kernel.0.sourceKernelResource=javatogpu/sample/Demo/kernel.cl"));
            assertTrue(gateProperties.contains("kernel.0.status=blocked"));
            assertTrue(gateProperties.contains("kernel.0.runtimeOptimizerDrift.status=recorded"));
            assertTrue(gateProperties.contains("kernel.0.runtimeOptimizerDrift.pass.count=0"));
            assertTrue(gateProperties.contains("kernel.0.runtimeOptimizerDrift.proofArtifact.count=0"));
            assertTrue(gateProperties.contains("kernel.0.diagnostic.count=5"));
            assertTrue(gateProperties.contains("kernel.0.diagnostic.0=backend source must be reconstructed from IrGpu before promotion review"));
            assertTrue(gateProperties.contains("blockerFamily.0.name=reconstruction"));
            assertTrue(gateProperties.contains("blockerFamily.0.count=2"));
            assertTrue(gateProperties.contains("blockerFamily.1.name=source-parity"));
            assertTrue(gateProperties.contains("blockerFamily.2.name=runtime-equivalence"));
            assertTrue(gateProperties.contains("kernel.0.blockerFamily.0.name=reconstruction"));

            Path compileArtifactRoot = reportDirectory.resolve("runtime-compile-artifacts");
            Path compileArtifactDirectory;
            try (java.util.stream.Stream<Path> directories = Files.list(compileArtifactRoot)) {
                compileArtifactDirectory = directories.filter(Files::isDirectory).findFirst().orElseThrow();
            }
            assertEquals(compileLog, Files.readString(compileArtifactDirectory.resolve("compile.log")));
            String compilerFeedback = Files.readString(
                    compileArtifactDirectory.resolve("backend-compiler-feedback.properties")
            );
            assertTrue(compilerFeedback.contains("status=recorded"));
            assertTrue(compilerFeedback.contains("selected.register.general=36"));
            assertTrue(Files.exists(compileArtifactDirectory.resolve("compile-provenance.properties")));
        } finally {
            if (previousGateFile == null) {
                System.clearProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
            } else {
                System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousGateFile);
            }
        }
    }

    @Test
    void aggregatesWorkloadSourcePromotionGateAcrossKernelResources() throws Exception {
        GpuKernelDescriptor firstDescriptor = intOutputDescriptor();
        GpuKernelDescriptor secondDescriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/other-kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 2; }",
                java.util.List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        Path gateFile = Files.createTempFile("javatogpu-opencl-workload-source-promotion-aggregate", ".properties");
        Files.deleteIfExists(gateFile);
        String previousGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", gateFile.toString());

            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot);

            backend.invoke(new GpuKernelInvocation(firstDescriptor, new Object[]{new int[]{0}}));
            backend.invoke(new GpuKernelInvocation(secondDescriptor, new Object[]{new int[]{0}}));

            String gateProperties = Files.readString(gateFile);
            assertTrue(gateProperties.contains("kernel.count=2"));
            assertTrue(gateProperties.contains("kernel.0.sourceKernelResource=javatogpu/sample/Demo/kernel.cl"));
            assertTrue(gateProperties.contains("kernel.1.sourceKernelResource=javatogpu/sample/Demo/other-kernel.cl"));
            assertTrue(gateProperties.contains("kernel.0.realWorkloadEvidence=runtime-snapshot"));
            assertTrue(gateProperties.contains("kernel.1.realWorkloadEvidence=runtime-snapshot"));
            assertTrue(gateProperties.contains("kernel.0.runtimeOptimizerDrift.status=recorded"));
            assertTrue(gateProperties.contains("kernel.1.runtimeOptimizerDrift.status=recorded"));
            assertTrue(gateProperties.contains("kernel.0.diagnostic.0=backend source must be reconstructed from IrGpu before promotion review"));
            assertTrue(gateProperties.contains("kernel.1.diagnostic.0=backend source must be reconstructed from IrGpu before promotion review"));
            assertTrue(gateProperties.contains("blockerFamily.0.name=reconstruction"));
            assertTrue(gateProperties.contains("blockerFamily.0.count=4"));
            assertTrue(gateProperties.contains("kernel.1.blockerFamily.2.name=runtime-equivalence"));
            assertTrue(gateProperties.contains("productionSourceSwitching=false"));
        } finally {
            if (previousGateFile == null) {
                System.clearProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
            } else {
                System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousGateFile);
            }
        }
    }

    @Test
    void runtimeEquivalenceFailureRejectsOptimizedIrInSnapshotArtifacts() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
            @Override
            protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                return GpuRuntimeEquivalenceEvidence.failed(
                        request.optimizedCompileRequest(),
                        2,
                        1,
                        List.of("case 1 output differs")
                );
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals("failed", snapshot.runtimeEquivalenceEvidence().status());
        assertTrue(snapshot.runtimeEquivalenceEvidence().executed());
        assertFalse(snapshot.runtimeEquivalenceEvidence().equivalent());
        assertEquals("runtime-equivalence-failed", snapshot.fallbackEvidence().decision());
        assertTrue(snapshot.fallbackEvidence().originalIrSelected());
        assertTrue(snapshot.fallbackEvidence().optimizedIrRejected());
        assertEquals("runtime-equivalence-failed", snapshot.compileProvenance().fallbackDecision());
        assertEquals("missing", snapshot.runtimeIrSelection().selectedStage());
        assertTrue(snapshot.runtimeIrSelection().optimizedRejected());
        assertEquals("runtime-equivalence-failed", snapshot.runtimeIrSelection().fallbackDecision());
    }

    @Test
    void runtimeEquivalenceFailureCompilesOriginalIrAfterSelectionFallback() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-irgpu-fallback-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, """
                # JavaToGpu backend-neutral IR artifact manifest
                backendOutput.0.backend=opencl
                backendOutput.0.format=opencl-c
                backendOutput.0.kind=source
                backendOutput.0.resource=javatogpu/sample/Demo/kernel.cl
                backendOutput.count=1
                compilerArtifact=JavaToGpu
                derived.opencl.resource=javatogpu/sample/Demo/kernel.cl
                entryEmittedName=jtg_kernel
                entryMethod=kernel
                format=javatogpu.irgpu.v1
                helper.count=0
                methodBody.0.body=method jtg_kernel source\\=kernel\\nhelpers -\\nbody\\n  return original\\n
                methodBody.0.emittedName=jtg_kernel
                methodBody.0.format=ir-text-v1
                methodBody.0.helperDependency.count=0
                methodBody.0.name=kernel
                methodBody.0.role=entry
                methodBody.count=1
                runtime.defaultBackend=opencl
                runtime.optimizationProfile=off
                schemaVersion=1
                sourceFrontend=java-source
                struct.count=0
                """);
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                java.util.List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    IrGpuArtifact optimizedArtifact = testIrGpuArtifact("optimized runtime body\n  return output[0] + 42\n");
                    GpuRuntimeCompileRequest optimizedRequest = compileRequest.withIrGpuArtifact(java.util.Optional.of(optimizedArtifact));
                    String originalIdentity = IrGpuArtifactIdentity.stableIdentity(compileRequest.irGpuArtifact());
                    String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(optimizedRequest.irGpuArtifact());
                    return new GpuRuntimeIrOptimizationResult(
                            optimizedRequest,
                            new net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationReport(
                                    java.util.Optional.of(optimizedArtifact),
                                    List.of(net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationPassReport.applied(
                                            "test-runtime-optimizer",
                                            originalIdentity,
                                            optimizedIdentity,
                                            "test-proof",
                                            List.of("test optimizer supplied transformed IR")
                                    ))
                            )
                    );
                }

                @Override
                protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                    return GpuRuntimeEquivalenceEvidence.failed(
                            request.optimizedCompileRequest(),
                            2,
                            1,
                            List.of("case 1 output differs")
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
        String finalCompileIdentity = IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact());
        assertNotEquals(originalIdentity, optimizedIdentity);
        assertEquals(originalIdentity, finalCompileIdentity);
        assertEquals("runtime-equivalence-failed", snapshot.compileProvenance().fallbackDecision());
        assertTrue(snapshot.fallbackEvidence().originalIrSelected());
        assertEquals("original", snapshot.runtimeIrSelection().selectedStage());
        assertEquals(originalIdentity, snapshot.runtimeIrSelection().selectedIdentity());
        assertTrue(snapshot.runtimeIrSelection().optimizedRejected());
    }

    @Test
    void blockedProductionGateCompilesOriginalIrAfterSelectionFallback() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-production-gate-blocked-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, irGpuArtifactProperties("return original"));
        GpuKernelDescriptor descriptor = descriptorWithIrGpuResource();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    return optimizedRuntimeResult(compileRequest, "production blocked optimized body\n  return output[0] + 42\n", GpuOptimizationStrategyDecision.advisory(
                            "strategy:blocked-production-fixture",
                            "test-vendor",
                            "vendor-tuned",
                            "blocked production fixture remains advisory",
                            GpuOptimizationVendorBaseline.missing("test-vendor"),
                            List.of("production fixture is intentionally blocked")
                    ));
                }

                @Override
                protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                    return GpuRuntimeEquivalenceEvidence.passed(
                            request.optimizedCompileRequest(),
                            2,
                            2,
                            List.of("blocked production fixture remains equivalent")
                    );
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 2; }",
                            "runtime/lowered/" + IrGpuArtifactIdentity.stableHash(compileRequest.irGpuArtifact().orElseThrow()) + ".cl",
                            "test-lowerer-v1"
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openCl(List.of(), "vendor-tuned")
            ));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
        String finalCompileIdentity = IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact());
        assertNotEquals(originalIdentity, optimizedIdentity);
        assertEquals(originalIdentity, finalCompileIdentity);
        assertEquals("blocked", snapshot.productionOptimizerGate().status());
        assertEquals("blocked", snapshot.runtimeIrSelection().productionIrGate().status());
        assertEquals("production-ir-gate-blocked", snapshot.runtimeIrSelection().fallbackDecision());
        assertEquals("original", snapshot.runtimeIrSelection().selectedStage());
        assertTrue(snapshot.runtimeIrSelection().optimizedRejected());
        assertTrue(snapshot.backendSourcePromotionGate().isPresent());
        assertEquals("blocked", snapshot.backendSourcePromotionGate().orElseThrow().status());
        assertTrue(snapshot.backendSourceSwitchingDecision().isPresent());
        assertEquals("descriptor-default", snapshot.backendSourceSwitchingDecision().orElseThrow().status());
        assertEquals("compile-descriptor-source", snapshot.backendSourceSwitchingDecision().orElseThrow().decision());
    }

    @Test
    void productionEnabledGateCompilesOptimizedIrAfterSelection() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-production-enabled-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, irGpuArtifactProperties("return original"));
        GpuKernelDescriptor descriptor = descriptorWithIrGpuResource();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    return optimizedRuntimeResultWithAcceptedProof(
                            compileRequest,
                            "production enabled optimized body\n  return output[0] + 42\n",
                            productionBackedStrategyDecision()
                    );
                }

                @Override
                protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                    return GpuRuntimeEquivalenceEvidence.passed(
                            request.optimizedCompileRequest(),
                            2,
                            2,
                            List.of("production enabled fixture remains equivalent")
                    );
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 2; }",
                            "runtime/lowered/" + IrGpuArtifactIdentity.stableHash(compileRequest.irGpuArtifact().orElseThrow()) + ".cl",
                            "test-lowerer-v1"
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                            .withProductionPromotionDecision(productionEnabledDecision())
                            .withProductionPromotionOperatorAccepted(true)
            ));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
        String finalCompileIdentity = IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact());
        assertNotEquals(originalIdentity, optimizedIdentity);
        assertEquals(optimizedIdentity, finalCompileIdentity);
        assertEquals("accepted", snapshot.productionOptimizerGate().status());
        assertEquals("production-enabled", snapshot.runtimeIrSelection().productionIrGate().status());
        assertEquals("optimized", snapshot.runtimeIrSelection().selectedStage());
        assertFalse(snapshot.runtimeIrSelection().optimizedRejected());
        assertEquals("none", snapshot.runtimeIrSelection().fallbackDecision());
        assertTrue(snapshot.backendSourcePromotionGate().isPresent());
        assertEquals("blocked", snapshot.backendSourcePromotionGate().orElseThrow().status());
        assertTrue(snapshot.backendSourceSwitchingDecision().isPresent());
        assertEquals("blocked", snapshot.backendSourceSwitchingDecision().orElseThrow().status());
        assertEquals("reject-irgpu-source-unavailable", snapshot.backendSourceSwitchingDecision().orElseThrow().decision());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().irGpuSourceRequested());
        assertFalse(snapshot.backendSourceSwitchingDecision().orElseThrow().sourceReady());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().productionProfileRequested());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().productionSourceSwitchingEnabled());
    }

    @Test
    void productionReadyExplainabilityWithoutAcceptedProofCompilesOriginalIr() throws Exception {
        Path classpathRoot = Files.createTempDirectory("javatogpu-production-proof-required-resource");
        Path artifactPath = classpathRoot.resolve("javatogpu/sample/Demo/kernel.irgpu.properties");
        Files.createDirectories(artifactPath.getParent());
        Files.writeString(artifactPath, irGpuArtifactProperties("return original"));
        GpuKernelDescriptor descriptor = descriptorWithIrGpuResource();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> finalCompileRequest = new AtomicReference<>();

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classpathRoot.toUri().toURL()}, previousClassLoader)) {
            Thread.currentThread().setContextClassLoader(classLoader);
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuRuntimeIrOptimizationResult optimizeRuntimeIrWithReport(GpuRuntimeCompileRequest compileRequest) {
                    return optimizedRuntimeResult(
                            compileRequest,
                            "production proof missing optimized body\n  return output[0] + 42\n",
                            productionBackedStrategyDecision()
                    );
                }

                @Override
                protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                    return GpuRuntimeEquivalenceEvidence.passed(
                            request.optimizedCompileRequest(),
                            2,
                            2,
                            List.of("production proof-required fixture remains equivalent")
                    );
                }

                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 2; }",
                            "runtime/lowered/" + IrGpuArtifactIdentity.stableHash(compileRequest.irGpuArtifact().orElseThrow()) + ".cl",
                            "test-lowerer-v1"
                    );
                }

                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    finalCompileRequest.set(compileRequest);
                    return super.compileKernel(compileRequest, moduleArtifact);
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                            .withProductionPromotionDecision(productionEnabledDecision())
                            .withProductionPromotionOperatorAccepted(true)
            ));
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.originalIrGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(snapshot.optimizedIrGpuArtifact());
        String finalCompileIdentity = IrGpuArtifactIdentity.stableIdentity(finalCompileRequest.get().irGpuArtifact());
        assertNotEquals(originalIdentity, optimizedIdentity);
        assertEquals(originalIdentity, finalCompileIdentity);
        assertEquals("blocked", snapshot.productionOptimizerGate().status());
        assertEquals("blocked", snapshot.runtimeIrSelection().productionIrGate().status());
        assertEquals("production-ir-gate-blocked", snapshot.runtimeIrSelection().fallbackDecision());
        assertEquals("original", snapshot.runtimeIrSelection().selectedStage());
        assertTrue(snapshot.runtimeIrSelection().optimizedRejected());
        assertTrue(snapshot.productionOptimizerGate().diagnostics().contains(
                "accepted optimizer proof artifact is required before production promotion"
        ));
    }

    @Test
    void productionPromotionExplainabilityFileFeedsRuntimeCompileOptions() throws Exception {
        Path explainabilityFile = Files.createTempFile("javatogpu-production-promotion-explainability", ".properties");
        writeProductionReadyExplainability(explainabilityFile);
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> capturedCompileRequest = new AtomicReference<>();
        String previousExplainabilityFile = System.getProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
        try {
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", explainabilityFile.toString());

            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    capturedCompileRequest.set(compileRequest);
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 1; }",
                            "javatogpu/sample/Demo/kernel.cl",
                            "test-lowerer-v1"
                    );
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
            ));
        } finally {
            if (previousExplainabilityFile == null) {
                System.clearProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
            } else {
                System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousExplainabilityFile);
            }
        }

        assertEquals(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                capturedCompileRequest.get().options().backendOptions().productionPromotionDecisionMode()
        );
        assertTrue(capturedSnapshot.get().backendSourceSwitchingDecision().isPresent());
        assertEquals(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                capturedSnapshot.get().backendSourceSwitchingDecision().orElseThrow().productionPromotionDecisionMode()
        );
    }

    @Test
    void productionReadyExplainabilityAndPackagedIrGpuCanReachProductionSourceSwitching() throws Exception {
        Path explainabilityFile = Files.createTempFile("javatogpu-production-source-switching-explainability", ".properties");
        writeProductionReadyExplainability(explainabilityFile);
        GpuKernelDescriptor descriptor = simpleIrGpuSourceDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        SnapshotCapturingBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
            @Override
            protected GpuRuntimeDeviceProfile compileDeviceProfile() {
                return new GpuRuntimeDeviceProfile(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Mock GPU",
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock"
                );
            }

            @Override
            protected GpuRuntimeEquivalenceEvidence executeRuntimeEquivalence(GpuRuntimeEquivalenceRequest request) {
                return GpuRuntimeEquivalenceEvidence.passed(
                        request.optimizedCompileRequest(),
                        1,
                        1,
                        List.of("packaged IrGpu source remained equivalent before production source switching")
                );
            }
        };
        GpuRuntimeDeviceProfile deviceProfile = backend.compileDeviceProfile();
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.openClProductionIrGpuSource(
                List.of(),
                "vendor-tuned"
        ).withProductionPromotionOperatorAcceptance(
                GpuProductionPromotionOperatorAcceptance.forContext(
                        "acceptance:test-packaged-irgpu",
                        GpuBackendTarget.OPENCL,
                        deviceProfile,
                        "vendor-tuned",
                        descriptor,
                        GpuProductionPromotionDecision.PRODUCTION_ENABLED
                )
        );
        options = options.withProductionActivationToken(
                GpuProductionActivationTokenTestFixtures.token(deviceProfile, descriptor.kernelResource())
        );
        String previousExplainabilityFile = System.getProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
        try {
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", explainabilityFile.toString());

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new float[]{1.0f}, 2.0f, new float[]{0.0f}},
                    options
            ));
        } finally {
            if (previousExplainabilityFile == null) {
                System.clearProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
            } else {
                System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousExplainabilityFile);
            }
        }

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertTrue(snapshot.backendSourceSwitchingDecision().isPresent());
        assertEquals(
                "production-switch-enabled",
                snapshot.backendSourceSwitchingDecision().orElseThrow().status(),
                snapshot.backendSourceSwitchingDecision().orElseThrow().toPropertiesText()
        );
        assertEquals("compile-irgpu-source-production", snapshot.backendSourceSwitchingDecision().orElseThrow().decision());
        assertEquals(
                descriptor.kernelResource() + "#irgpu-reconstructed",
                snapshot.backendSourceSwitchingDecision().orElseThrow().backendResource()
        );
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().sourceReady());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().sourceAvailable());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().sourceParityMatched());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().productionSourceSwitchingEnabled());
        assertTrue(snapshot.backendSourceSwitchingDecision().orElseThrow().productionPromotionOperatorAccepted());
        assertEquals(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                snapshot.backendSourceSwitchingDecision().orElseThrow().productionPromotionDecisionMode()
        );
    }

    @Test
    void missingProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly() throws Exception {
        Path missingExplainabilityFile = Files.createTempDirectory("javatogpu-missing-production-promotion")
                .resolve("missing-production-promotion-explainability.properties");

        assertProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly(missingExplainabilityFile);
    }

    @Test
    void invalidProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly() throws Exception {
        Path invalidExplainabilityFile = Files.createTempFile("javatogpu-invalid-production-promotion", ".properties");
        Files.writeString(invalidExplainabilityFile, "not a properties file with a valid production promotion contract\n");

        assertProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly(invalidExplainabilityFile);
    }

    @Test
    void requiresBufferArgumentToDeriveGlobalWorkSize() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeInvocationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                GpuRuntimeInvocationException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[0]))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL execution requires at least one buffer argument to derive global work size for kernel kernel"
        ));
        assertTrue(exception.getMessage().contains("launch with an explicit GpuExecutionConfig"));
        assertEquals(0, backend.cacheSize());
    }

    @Test
    void explicitGlobalWorkSizeAllowsBufferlessKernelLaunch() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        AtomicReference<net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig> executedConfig = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executedConfig.set(executionConfig);
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[0], 8L));

        assertEquals(8L, executedConfig.get().globalWorkSize());
    }

    @Test
    void explicitGlobalWorkSizeBypassesMismatchedBufferLengthRestriction() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("blob", "byte[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicReference<net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig> executedConfig = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                return new Object();
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executedConfig.set(executionConfig);
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new byte[64], new int[8]}, 8L));

        assertEquals(8L, executedConfig.get().globalWorkSize());
    }

    @Test
    void rejectsNonPositiveExplicitGlobalWorkSize() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[0], 0L))
        );

        assertTrue(exception.getMessage().contains("globalX must be positive: 0"));
    }

    @Test
    void explicitTwoDimensionalExecutionConfigReachesKernelEnqueuePath() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        AtomicReference<net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig> executedConfig = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executedConfig.set(executionConfig);
            }
        };

        backend.invoke(new GpuKernelInvocation(
                descriptor,
                new Object[0],
                net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.twoDimensional(16L, 8L, 4L, 2L)
        ));

        assertEquals(2, executedConfig.get().dimensions());
        assertEquals(16L, executedConfig.get().globalX());
        assertEquals(8L, executedConfig.get().globalY());
        assertEquals(4L, executedConfig.get().localX());
        assertEquals(2L, executedConfig.get().localY());
    }

    @Test
    void explicitThreeDimensionalExecutionConfigReachesKernelEnqueuePath() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of()
        );

        AtomicReference<net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig> executedConfig = new AtomicReference<>();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executedConfig.set(executionConfig);
            }
        };

        backend.invoke(new GpuKernelInvocation(
                descriptor,
                new Object[0],
                net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.threeDimensional(16L, 8L, 4L, 4L, 2L, 1L)
        ));

        assertEquals(3, executedConfig.get().dimensions());
        assertEquals(16L, executedConfig.get().globalX());
        assertEquals(8L, executedConfig.get().globalY());
        assertEquals(4L, executedConfig.get().globalZ());
        assertEquals(4L, executedConfig.get().localX());
        assertEquals(2L, executedConfig.get().localY());
        assertEquals(1L, executedConfig.get().localZ());
    }

    @Test
    void executesPreparedArgumentsInKernelOrderAndReadsBackOutputs() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        float[] input = new float[]{1.0f, 2.0f, 3.0f};
        float[] output = new float[]{0.0f, 0.0f, 0.0f};
        List<String> events = new ArrayList<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            private final HashMap<Object, float[]> nativeArrays = new HashMap<>();
            private final HashMap<Integer, Object> boundBuffers = new HashMap<>();
            private final HashMap<Integer, Object> boundScalars = new HashMap<>();

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                float[] deviceArray = new float[binding.length()];
                nativeArrays.put(deviceArray, deviceArray);
                events.add("alloc:" + binding.length());
                return deviceArray;
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                System.arraycopy((float[]) binding.sourceArray(), 0, (float[]) nativeBuffer, 0, binding.length());
                events.add("upload:" + binding.length());
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                boundBuffers.put(parameterIndex, nativeBuffer);
                events.add("bind-buffer:" + parameterIndex);
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                boundScalars.put(parameterIndex, binding.value());
                events.add("bind-scalar:" + parameterIndex);
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                float[] in = (float[]) boundBuffers.get(0);
                float scale = (Float) boundScalars.get(1);
                float[] out = (float[]) boundBuffers.get(2);
                for (int i = 0; i < executionConfig.globalWorkSize(); i++) {
                    out[i] = in[i] + scale;
                }
                events.add("enqueue:" + executionConfig.globalWorkSize());
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                System.arraycopy((float[]) nativeBuffer, 0, (float[]) binding.sourceArray(), 0, binding.length());
                events.add("readback:" + binding.length());
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{input, 2.5f, output}));

        assertEquals(
                Arrays.asList(
                        "alloc:3",
                        "upload:3",
                        "alloc:3",
                        "upload:3",
                        "bind-buffer:0",
                        "bind-scalar:1",
                        "bind-buffer:2",
                        "enqueue:3",
                        "readback:3"
                ),
                events
        );
        assertEquals(3.5f, output[0]);
        assertEquals(4.5f, output[1]);
        assertEquals(5.5f, output[2]);
    }

    @Test
    void rejectsMismatchedBufferLengthsBeforeKernelLaunch() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger executeCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                return new Object();
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op for length validation path
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                // no-op for length validation path
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                // no-op for length validation path
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                executeCalls.incrementAndGet();
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op for length validation path
            }
        };

        GpuRuntimeInvocationException exception = assertThrows(
                GpuRuntimeInvocationException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new float[]{1.0f, 2.0f}, new float[]{0.0f}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "Mismatched GPU array lengths for kernel kernel: expected 2 but found 1"
        ));
        assertTrue(exception.getMessage().contains("must share the same logical length"));
        assertEquals(0, executeCalls.get());
        assertEquals(0, backend.cacheSize());
    }

    @Test
    void unsupportedUploadTypesIncludeQuickFixHints() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "java.lang.Object[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                return new Object();
            }
        };

        GpuRuntimeInvocationException exception = assertThrows(
                GpuRuntimeInvocationException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new Object[]{new Object()}}))
        );

        assertTrue(exception.getMessage().contains("Failed to marshall parameter 'input':"));
        assertTrue(exception.getMessage().contains("Unsupported OpenCL argument type:"));
        assertTrue(exception.getMessage().contains("java.lang.Object"));
    }

    @Test
    void rejectsDoubleKernelWhenDeviceLacksFp64BeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Double/kernel.cl",
                "__kernel void kernel(__global double* input, __global double* output) { output[0] = input[0]; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "double[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "double[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", false, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new double[]{1.0d}, new double[]{0.0d}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not advertise fp64 support, but the kernel uses double precision"
        ));
        assertTrue(exception.getMessage().contains("float/fallback path"));
        assertEquals(0, compileCalls.get());
        assertEquals(0, backend.cacheSize());
    }

    @Test
    void rejectsImageKernelWhenDeviceLacksImageSupportBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Image/kernel.cl",
                "__kernel void kernel(read_only image2d_t inputImage, sampler_t sampler, __global int* output) { }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, false, false, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{Image2DReadOnly.borrowed(1L, 1, 1), Sampler.borrowed(2L), new int[]{0}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not support OpenCL images, but the kernel requires image/sampler parameters"
        ));
        assertTrue(exception.getMessage().contains("buffer-backed kernels"));
        assertEquals(0, compileCalls.get());
    }

    @Test
    void rejectsImage3dWriteKernelWhenDeviceLacks3dWriteSupportBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Image3d/kernel.cl",
                "__kernel void kernel(read_only image3d_t inputImage, write_only image3d_t outputImage, sampler_t sampler, __global int* output) { }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("outputImage", "Image3DWriteOnly", GpuKernelParameterAccess.READ_WRITE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, false, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{Image3DReadOnly.borrowed(1L, 1, 1, 1), Image3DWriteOnly.borrowed(2L, 1, 1, 1), Sampler.borrowed(3L), new int[]{0}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not support 3D image writes required by the kernel"
        ));
        assertTrue(exception.getMessage().contains("2D/buffer workflows"));
        assertEquals(0, compileCalls.get());
    }

    @Test
    void rejectsAtomicKernelWhenDeviceLacksAtomicSupportBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Atomic/kernel.cl",
                "__kernel void kernel(__global int* output) { atomic_add(output, 1); }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new int[]{0}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: device Fake GPU does not advertise int32 atomic support required by the kernel"
        ));
        assertTrue(exception.getMessage().contains("select a backend/device with atomics support"));
        assertEquals(0, compileCalls.get());
    }

    @Test
    void rejectsLocalMemoryRequestThatExceedsDeviceBudgetBeforeCompile() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Local/kernel.cl",
                "__kernel void kernel(__local float* scratch, __global float* output) { output[0] = scratch[0]; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 8L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new float[]{1.0f, 2.0f, 3.0f}, new float[]{0.0f}}
                ))
        );

        assertTrue(exception.getMessage().contains(
                "OpenCL capability precheck failed for kernel kernel: requested 12 bytes of local memory, but device Fake GPU exposes only 8 bytes"
        ));
        assertTrue(exception.getMessage().contains("reduce the local scratch size"));
        assertEquals(0, compileCalls.get());
    }

    @Test
    void rejectsExplicitLocalWorkGroupThatExceedsCompiledKernelLimit() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean();
        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(new AtomicReference<>()) {
            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:max-work-group")
                        .withKernelResourceInfo(new OpenClKernelResourceInfo(64L, 32L, 0L, 0L));
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                executed.set(true);
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new int[128]},
                        net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(128L, 128L)
                ))
        );

        assertFalse(executed.get());
        assertEquals("JTG-RUNTIME-CAPABILITY-001", exception.code());
        assertEquals("kernel", exception.context().kernelName());
        assertTrue(exception.getMessage().contains("requested local work-group size 128 (128)"));
        assertTrue(exception.getMessage().contains("compiled kernel limit is 64"));
        assertTrue(exception.getMessage().contains("leave it unspecified"));
    }

    @Test
    void rejectsThreeDimensionalLocalWorkGroupByTotalSize() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean();
        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(new AtomicReference<>()) {
            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:max-work-group-3d")
                        .withKernelResourceInfo(new OpenClKernelResourceInfo(64L, 32L, 0L, 0L));
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                executed.set(true);
            }
        };

        GpuRuntimeCapabilityException exception = assertThrows(
                GpuRuntimeCapabilityException.class,
                () -> backend.invoke(new GpuKernelInvocation(
                        descriptor,
                        new Object[]{new int[512]},
                        net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.threeDimensional(
                                8L,
                                8L,
                                8L,
                                4L,
                                4L,
                                8L
                        )
                ))
        );

        assertFalse(executed.get());
        assertTrue(exception.getMessage().contains("requested local work-group size 128 (4x4x8)"));
        assertTrue(exception.getMessage().contains("compiled kernel limit is 64"));
    }

    @Test
    void acceptsSupportedOrDriverSelectedLocalWorkGroupSize() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        java.util.concurrent.atomic.AtomicInteger executions = new java.util.concurrent.atomic.AtomicInteger();
        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(new AtomicReference<>()) {
            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:max-work-group-allowed")
                        .withKernelResourceInfo(new OpenClKernelResourceInfo(64L, 32L, 0L, 0L));
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                executions.incrementAndGet();
            }
        };

        backend.invoke(new GpuKernelInvocation(
                descriptor,
                new Object[]{new int[64]},
                net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(64L, 64L)
        ));
        backend.invoke(new GpuKernelInvocation(
                descriptor,
                new Object[]{new int[64]},
                net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(64L)
        ));

        assertEquals(2, executions.get());
    }

    @Test
    void recordsNonPreferredWorkGroupMultipleWithoutBlockingExecution() throws Exception {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean();
        Path reportDirectory = Files.createTempDirectory("javatogpu-opencl-launch-advisory");
        Path gateFile = reportDirectory.resolve("backend-source-promotion-workload-gate.properties");
        String property = "javatogpu.opencl.backendSourcePromotionWorkloadGateFile";
        String previousGateFile = System.getProperty(property);
        try {
            System.setProperty(property, gateFile.toString());
            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(new AtomicReference<>()) {
                @Override
                protected OpenClCompiledKernel compileKernel(
                        GpuRuntimeCompileRequest compileRequest,
                        GpuBackendModuleArtifact moduleArtifact
                ) {
                    return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:preferred-multiple")
                            .withKernelResourceInfo(new OpenClKernelResourceInfo(256L, 32L, 0L, 0L));
                }

                @Override
                protected void executeKernel(OpenClPreparedExecution execution) {
                    executed.set(true);
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[64]},
                    net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(64L, 48L)
            ));

            assertTrue(executed.get());
            Path compileArtifactRoot = reportDirectory.resolve("runtime-compile-artifacts");
            Path compileArtifactDirectory;
            try (java.util.stream.Stream<Path> directories = Files.list(compileArtifactRoot)) {
                compileArtifactDirectory = directories.filter(Files::isDirectory).findFirst().orElseThrow();
            }
            String advisory = Files.readString(
                    compileArtifactDirectory.resolve(OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME)
            );
            assertTrue(advisory.contains("status=non-preferred-multiple"));
            assertTrue(advisory.contains("blocking=false"));
            assertTrue(advisory.contains("requestedLocalWorkGroupShape=48"));
            assertTrue(advisory.contains("preferredWorkGroupSizeMultiple=32"));
            assertTrue(advisory.contains("preferredMultipleMatched=false"));

            assertThrows(
                    GpuRuntimeCapabilityException.class,
                    () -> backend.invoke(new GpuKernelInvocation(
                            descriptor,
                            new Object[]{new int[512]},
                            net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(512L, 512L)
                    ))
            );
            assertFalse(Files.exists(
                    compileArtifactDirectory.resolve(OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME)
            ));
        } finally {
            if (previousGateFile == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previousGateFile);
            }
        }
    }

    @Test
    void reportsUnavailableOpenClRuntimeClearly() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel() {}",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeSession createSession() {
                throw new UnsatisfiedLinkError("LWJGL OpenCL bindings are missing");
            }
        };

        GpuRuntimeBackendUnavailableException exception = org.junit.jupiter.api.Assertions.assertThrows(
                GpuRuntimeBackendUnavailableException.class,
                () -> backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new float[]{1.0f}}))
        );

        assertTrue(exception.getMessage().contains("OpenCL runtime is unavailable: LWJGL OpenCL bindings are missing"));
        assertTrue(exception.getMessage().contains("GpuRuntime.trySelect(...)"));
    }

    @Test
    void formatsKernelBuildFailuresWithKernelAndDeviceContext() throws java.io.IOException {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global float* output) { output[0] = 1.0f; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                throw new RuntimeException("driver build log: unknown type name 'half16'");
            }
        };

        String expression = "GpuShowcase.basicMath(input, output)";
        GpuRuntimeKernelCompilationException exception;
        try (URLClassLoader callSiteClassLoader = callSiteClassLoader(
                "formatsKernelBuildFailuresWithKernelAndDeviceContext",
                expression
        )) {
            exception = assertThrows(
                    GpuRuntimeKernelCompilationException.class,
                    () -> backend.invoke(new GpuKernelInvocation(
                            descriptor,
                            new Object[]{new float[]{0.0f}}
                    ).withArtifactClassLoader(callSiteClassLoader))
            );
        }

        assertTrue(exception.getMessage().contains(
                "OpenCL kernel build failed for kernel kernel on device Fake GPU [javatogpu/sample/Demo/kernel.cl]: driver build log: unknown type name 'half16'"
        ));
        assertTrue(exception.getMessage().contains("enable ABI debug"));
        assertTrue(exception.getMessage().contains("Device-Quirks.md"));
        assertEquals("JTG-RUNTIME-COMPILE-001", exception.code());
        assertTrue(exception.diagnosticText().startsWith("error[JTG-RUNTIME-COMPILE-001]:"));
        assertTrue(exception.diagnosticText().contains("--> OpenClGpuRuntimeBackendTest.java:1:1"));
        assertEquals("kernel", exception.context().kernelName());
        assertEquals("Fake GPU", exception.context().deviceLabel());
        assertEquals("compiler-index", exception.context().callSite().source());
        assertEquals(expression, exception.context().callSite().expression());
        assertTrue(exception.diagnosticText().contains(expression));
    }

    @Test
    void compilerLogFromCompiledKernelSurvivesFinalSnapshotAttachment() {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        String compileLog = "Used 40 registers, 8 bytes spill stores, 4 bytes spill loads";

        OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
            @Override
            protected OpenClCompiledKernel compileKernel(
                    GpuRuntimeCompileRequest compileRequest,
                    GpuBackendModuleArtifact moduleArtifact
            ) {
                return new OpenClCompiledKernel(
                        compileRequest.descriptor(),
                        "compiled:with-log",
                        GpuRuntimeCompileArtifactSnapshot.legacy(compileRequest.descriptor())
                                .withCompileLog(compileLog),
                        null,
                        null
                );
            }
        };

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));

        GpuRuntimeCompileArtifactSnapshot snapshot = capturedSnapshot.get();
        assertEquals(compileLog, snapshot.compileLog());
        net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDump dump =
                net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper.dump(snapshot);
        String feedback = dump.artifact(
                net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper.BACKEND_COMPILER_FEEDBACK_ARTIFACT
        );
        assertTrue(feedback.contains("status=recorded"));
        assertTrue(feedback.contains("selected.register.general=40"));
        assertTrue(feedback.contains("selected.spill.knownBytes=12"));
    }

    @Test
    void formatsKernelExecutionFailuresWithKernelAndDeviceContext() throws java.io.IOException {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global float* output) { output[0] = 1.0f; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:test");
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                throw new RuntimeException("clEnqueueNDRangeKernel failed: CL_OUT_OF_RESOURCES");
            }
        };

        String expression = "GpuShowcase.basicMath(input, output)";
        GpuRuntimeKernelExecutionException exception;
        try (URLClassLoader callSiteClassLoader = callSiteClassLoader(
                "formatsKernelExecutionFailuresWithKernelAndDeviceContext",
                expression
        )) {
            exception = assertThrows(
                    GpuRuntimeKernelExecutionException.class,
                    () -> backend.invoke(new GpuKernelInvocation(
                            descriptor,
                            new Object[]{new float[]{0.0f}}
                    ).withArtifactClassLoader(callSiteClassLoader))
            );
        }

        assertTrue(exception.getMessage().contains(
                "OpenCL kernel execution failed for kernel kernel on device Fake GPU: clEnqueueNDRangeKernel failed: CL_OUT_OF_RESOURCES"
        ));
        assertTrue(exception.getMessage().contains("fallback backend"));
        assertEquals("JTG-RUNTIME-EXECUTE-001", exception.code());
        assertTrue(exception.diagnosticText().startsWith("error[JTG-RUNTIME-EXECUTE-001]:"));
        assertEquals("compiler-index", exception.context().callSite().source());
        assertEquals(expression, exception.context().callSite().expression());
        assertTrue(exception.diagnosticText().contains(expression));
    }

    @Test
    void closeClearsBufferRegistryClosesTrackedBuffersAndAllowsReuse() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger trackedBufferCloseCalls = new AtomicInteger();
        AtomicInteger deviceBufferAllocations = new AtomicInteger();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                compileCalls.incrementAndGet();
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.get());
            }

            @Override
            protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                deviceBufferAllocations.incrementAndGet();
                return new AutoCloseable() {
                    private boolean closed;

                    @Override
                    public void close() {
                        if (!closed) {
                            closed = true;
                            trackedBufferCloseCalls.incrementAndGet();
                        }
                    }
                };
            }

            @Override
            protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op
            }

            @Override
            protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                // no-op
            }

            @Override
            protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                // no-op
            }

            @Override
            protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                // no-op
            }

            @Override
            protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                // no-op
            }
        };

        int[] output = new int[]{0};
        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        assertEquals(1, compileCalls.get());
        assertEquals(1, deviceBufferAllocations.get());
        assertEquals(1, backend.cacheSize());
        assertEquals(1, backend.bufferCacheSize());

        backend.close();

        assertEquals(1, trackedBufferCloseCalls.get());
        assertEquals(0, backend.cacheSize());
        assertEquals(0, backend.bufferCacheSize());

        backend.close();

        assertEquals(1, trackedBufferCloseCalls.get());

        backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{output}));

        assertEquals(2, compileCalls.get());
        assertEquals(2, deviceBufferAllocations.get());
        assertEquals(1, backend.cacheSize());
        assertEquals(1, backend.bufferCacheSize());
    }

    @Test
    void repeatedCreateInvokeCloseCyclesRemainStable() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();
        AtomicInteger deviceBufferAllocations = new AtomicInteger();
        AtomicInteger trackedBufferCloseCalls = new AtomicInteger();

        for (int iteration = 0; iteration < 25; iteration++) {
            OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
                @Override
                protected OpenClRuntimeCapabilities runtimeCapabilities() {
                    return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
                }

                @Override
                protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                    compileCalls.incrementAndGet();
                    return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.get());
                }

                @Override
                protected Object createDeviceBuffer(OpenClBufferBinding binding) {
                    deviceBufferAllocations.incrementAndGet();
                    return new AutoCloseable() {
                        private boolean closed;

                        @Override
                        public void close() {
                            if (!closed) {
                                closed = true;
                                trackedBufferCloseCalls.incrementAndGet();
                            }
                        }
                    };
                }

                @Override
                protected void uploadToDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                    // no-op
                }

                @Override
                protected void bindBufferArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, Object nativeBuffer) {
                    // no-op
                }

                @Override
                protected void bindScalarArgument(OpenClCompiledKernel compiledKernel, int parameterIndex, OpenClScalarBinding binding) {
                    // no-op
                }

                @Override
                protected void enqueueKernel(OpenClCompiledKernel compiledKernel, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {
                    // no-op
                }

                @Override
                protected void readBackFromDeviceBuffer(Object nativeBuffer, OpenClBufferBinding binding) {
                    // no-op
                }
            };

            backend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{iteration}}));

            assertEquals(1, backend.cacheSize());
            assertEquals(1, backend.bufferCacheSize());

            backend.close();

            assertEquals(0, backend.cacheSize());
            assertEquals(0, backend.bufferCacheSize());
        }

        assertEquals(25, compileCalls.get());
        assertEquals(25, deviceBufferAllocations.get());
        assertEquals(25, trackedBufferCloseCalls.get());
    }

    @Test
    void sharedCacheRetainsCompiledKernelAcrossBackendInstancesUntilExplicitShutdown() {
        OpenClGpuRuntimeBackend.shutdownSharedCache();

        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );

        AtomicInteger compileCalls = new AtomicInteger();

        OpenClGpuRuntimeBackend firstBackend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        firstBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        assertEquals(1, compileCalls.get());
        assertEquals(1, firstBackend.cacheSize());
        firstBackend.close();
        assertEquals(1, firstBackend.cacheSize());

        OpenClGpuRuntimeBackend secondBackend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        secondBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        assertEquals(1, compileCalls.get());
        assertEquals(1, secondBackend.cacheSize());
        secondBackend.close();

        OpenClGpuRuntimeBackend.shutdownSharedCache();

        OpenClGpuRuntimeBackend thirdBackend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClRuntimeCapabilities runtimeCapabilities() {
                return new OpenClRuntimeCapabilities("Fake GPU", "OpenCL 3.0 Fake GPU", true, true, true, 32_768L, 256L);
            }

            @Override
            protected OpenClCompiledKernel compileKernel(GpuKernelDescriptor kernelDescriptor) {
                return new OpenClCompiledKernel(kernelDescriptor, "compiled:" + compileCalls.incrementAndGet());
            }

            @Override
            protected void executeKernel(OpenClPreparedExecution execution) {
                // no-op
            }
        };

        thirdBackend.invoke(new GpuKernelInvocation(descriptor, new Object[]{new int[]{0}}));
        assertEquals(2, compileCalls.get());
        thirdBackend.close();
        OpenClGpuRuntimeBackend.shutdownSharedCache();
    }

    @Test
    void createsHighLevelFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgbaFloatImageInternal(int width, int height, float[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(rgba);
                return Image2DReadOnly.borrowed(777L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgbaFloatImage(2, 1, new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f});

        assertEquals(777L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRFloatImageInternal(int width, int height, float[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(778L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRFloatImage(2, 1, new float[]{1.0f, 2.0f});

        assertEquals(778L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRgFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgFloatImageInternal(int width, int height, float[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(779L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgFloatImage(2, 1, new float[]{1.0f, 2.0f, 3.0f, 4.0f});

        assertEquals(779L, image.handle());
        assertEquals(4, captured.get().length);
    }

    @Test
    void createsHighLevelDepthImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyDepthImageInternal(int width, int height, float[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(7791L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyDepthImage(2, 1, new float[]{0.25f, 0.75f});

        assertEquals(7791L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(780L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRIntImage(2, 1, new int[]{1, 2});

        assertEquals(780L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRgIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(781L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgIntImage(2, 1, new int[]{1, 2, 3, 4});

        assertEquals(781L, image.handle());
        assertEquals(4, captured.get().length);
    }

    @Test
    void createsHighLevelSamplerThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Sampler createSamplerInternal(boolean normalizedCoordinates, int addressingMode, int filterMode) {
                assertEquals(true, normalizedCoordinates);
                assertEquals(5, addressingMode);
                assertEquals(6, filterMode);
                return Sampler.borrowed(888L);
            }
        };

        Sampler sampler = backend.createSampler(true, 5, 6);

        assertEquals(888L, sampler.handle());
    }

    @Test
    void createsHighLevelRgba8ImageThroughProtectedHook() {
        AtomicReference<byte[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgba8ImageInternal(int width, int height, byte[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(rgba);
                return Image2DReadOnly.borrowed(999L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgba8Image(
                2,
                1,
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8}
        );

        assertEquals(999L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgbaUIntImageInternal(int width, int height, int[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(rgba);
                return Image2DReadOnly.borrowed(1001L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgbaUIntImage(
                2,
                1,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8}
        );

        assertEquals(1001L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRUIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(1002L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRUIntImage(2, 1, new int[]{1, 2});

        assertEquals(1002L, image.handle());
        assertEquals(2, captured.get().length);
    }

    @Test
    void createsHighLevelRgUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DReadOnly createReadOnlyRgUIntImageInternal(int width, int height, int[] values) {
                assertEquals(2, width);
                assertEquals(1, height);
                captured.set(values);
                return Image2DReadOnly.borrowed(1003L, width, height);
            }
        };

        Image2DReadOnly image = backend.createReadOnlyRgUIntImage(2, 1, new int[]{1, 2, 3, 4});

        assertEquals(1003L, image.handle());
        assertEquals(4, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaFloatImage3dThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image3DReadOnly createReadOnlyRgbaFloatImage3DInternal(int width, int height, int depth, float[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, depth);
                captured.set(rgba);
                return Image3DReadOnly.borrowed(1004L, width, height, depth);
            }
        };

        Image3DReadOnly image = backend.createReadOnlyRgbaFloatImage3D(
                2,
                1,
                2,
                new float[]{
                        1.0f, 0.0f, 0.0f, 1.0f,
                        0.0f, 1.0f, 0.0f, 1.0f,
                        0.0f, 0.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f, 1.0f
                }
        );

        assertEquals(1004L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaIntImage3dThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image3DReadOnly createReadOnlyRgbaIntImage3DInternal(int width, int height, int depth, int[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, depth);
                captured.set(rgba);
                return Image3DReadOnly.borrowed(1005L, width, height, depth);
            }
        };

        Image3DReadOnly image = backend.createReadOnlyRgbaIntImage3D(
                2,
                1,
                2,
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
        );

        assertEquals(1005L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage3dThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image3DReadOnly createReadOnlyRgbaUIntImage3DInternal(int width, int height, int depth, int[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, depth);
                captured.set(rgba);
                return Image3DReadOnly.borrowed(1006L, width, height, depth);
            }
        };

        Image3DReadOnly image = backend.createReadOnlyRgbaUIntImage3D(
                2,
                1,
                2,
                new int[]{101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116}
        );

        assertEquals(1006L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaFloatImage1dThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DReadOnly createReadOnlyRgbaFloatImage1DInternal(int width, float[] rgba) {
                assertEquals(2, width);
                captured.set(rgba);
                return Image1DReadOnly.borrowed(1007L, width);
            }
        };

        Image1DReadOnly image = backend.createReadOnlyRgbaFloatImage1D(2, new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f});

        assertEquals(1007L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage1dThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DReadOnly createReadOnlyRgbaUIntImage1DInternal(int width, int[] rgba) {
                assertEquals(2, width);
                captured.set(rgba);
                return Image1DReadOnly.borrowed(1008L, width);
            }
        };

        Image1DReadOnly image = backend.createReadOnlyRgbaUIntImage1D(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertEquals(1008L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage1dArrayThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DArrayReadOnly createReadOnlyRgbaUIntImage1DArrayInternal(int width, int layers, int[] rgba) {
                assertEquals(2, width);
                assertEquals(2, layers);
                captured.set(rgba);
                return Image1DArrayReadOnly.borrowed(1010L, width, layers);
            }
        };

        Image1DArrayReadOnly image = backend.createReadOnlyRgbaUIntImage1DArray(2, 2, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});

        assertEquals(1010L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaUIntImage1dBufferThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image1DBufferReadOnly createReadOnlyRgbaUIntImage1DBufferInternal(int width, int[] rgba) {
                assertEquals(2, width);
                captured.set(rgba);
                return Image1DBufferReadOnly.borrowed(1011L, width);
            }
        };

        Image1DBufferReadOnly image = backend.createReadOnlyRgbaUIntImage1DBuffer(2, new int[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertEquals(1011L, image.handle());
        assertEquals(8, captured.get().length);
    }

    @Test
    void createsHighLevelRgbaFloatImage2dArrayThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DArrayReadOnly createReadOnlyRgbaFloatImage2DArrayInternal(int width, int height, int layers, float[] rgba) {
                assertEquals(2, width);
                assertEquals(1, height);
                assertEquals(2, layers);
                captured.set(rgba);
                return Image2DArrayReadOnly.borrowed(1012L, width, height, layers);
            }
        };

        Image2DArrayReadOnly image = backend.createReadOnlyRgbaFloatImage2DArray(2, 1, 2, new float[]{1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1});

        assertEquals(1012L, image.handle());
        assertEquals(16, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedFloatImageThroughProtectedHook() {
        AtomicReference<float[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgbaFloatImageMipmappedInternal(int width, int height, int mipLevels, float[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10121L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgbaFloatImageMipmapped(4, 2, 2, new float[]{
                1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1,
                1, 0, 1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 0, 0, 0, 1,
                0.5f, 0.5f, 0.5f, 1.0f, 0.25f, 0.25f, 0.25f, 1.0f
        });

        assertEquals(10121L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedRgba8ImageThroughProtectedHook() {
        AtomicReference<byte[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgba8ImageMipmappedInternal(int width, int height, int mipLevels, byte[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10127L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgba8ImageMipmapped(4, 2, 2, new byte[]{
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 32,
                33, 34, 35, 36, 37, 38, 39, 40
        });

        assertEquals(10127L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgbaIntImageMipmappedInternal(int width, int height, int mipLevels, int[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10125L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgbaIntImageMipmapped(4, 2, 2, new int[]{
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 32,
                -1, -2, -3, -4, -5, -6, -7, -8
        });

        assertEquals(10125L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void createsHighLevelMipmappedUIntImageThroughProtectedHook() {
        AtomicReference<int[]> captured = new AtomicReference<>();

        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected Image2DMipmappedReadOnly createReadOnlyRgbaUIntImageMipmappedInternal(int width, int height, int mipLevels, int[] rgba) {
                assertEquals(4, width);
                assertEquals(2, height);
                assertEquals(2, mipLevels);
                captured.set(rgba);
                return Image2DMipmappedReadOnly.borrowed(10122L, width, height, mipLevels);
            }
        };

        Image2DMipmappedReadOnly image = backend.createReadOnlyRgbaUIntImageMipmapped(4, 2, 2, new int[]{
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31, 32,
                33, 34, 35, 36, 37, 38, 39, 40
        });

        assertEquals(10122L, image.handle());
        assertEquals(40, captured.get().length);
    }

    @Test
    void readsHighLevelFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImageInternal(Image2DWriteOnly image) {
                assertEquals(123L, image.handle());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage(Image2DWriteOnly.borrowed(123L, 1, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, rgba);
    }

    @Test
    void readsHighLevelRFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRFloatImageInternal(Image2DReadOnly image) {
                assertEquals(124L, image.handle());
                return new float[]{1.0f, 2.0f};
            }
        };

        float[] values = backend.readRFloatImage(Image2DReadOnly.borrowed(124L, 2, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f}, values);
    }

    @Test
    void readsHighLevelRgFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgFloatImageInternal(Image2DWriteOnly image) {
                assertEquals(125L, image.handle());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] values = backend.readRgFloatImage(Image2DWriteOnly.borrowed(125L, 2, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, values);
    }

    @Test
    void readsHighLevelDepthImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readDepthImageInternal(Image2DWriteOnly image) {
                assertEquals(1251L, image.handle());
                return new float[]{0.25f, 0.75f};
            }
        };

        float[] values = backend.readDepthImage(Image2DWriteOnly.borrowed(1251L, 2, 1));

        assertArrayEquals(new float[]{0.25f, 0.75f}, values);
    }

    @Test
    void readsHighLevelRIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRIntImageInternal(Image2DReadOnly image) {
                assertEquals(126L, image.handle());
                return new int[]{7, 8};
            }
        };

        int[] values = backend.readRIntImage(Image2DReadOnly.borrowed(126L, 2, 1));

        assertArrayEquals(new int[]{7, 8}, values);
    }

    @Test
    void readsHighLevelRgIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgIntImageInternal(Image2DWriteOnly image) {
                assertEquals(127L, image.handle());
                return new int[]{7, 8, 9, 10};
            }
        };

        int[] values = backend.readRgIntImage(Image2DWriteOnly.borrowed(127L, 2, 1));

        assertArrayEquals(new int[]{7, 8, 9, 10}, values);
    }

    @Test
    void readsHighLevelIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImageInternal(Image2DReadOnly image) {
                assertEquals(321L, image.handle());
                return new int[]{5, 6, 7, 8};
            }
        };

        int[] rgba = backend.readRgbaIntImage(Image2DReadOnly.borrowed(321L, 1, 1));

        assertArrayEquals(new int[]{5, 6, 7, 8}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImageInternal(Image2DReadOnly image) {
                assertEquals(741L, image.handle());
                return new int[]{11, 12, 13, 14};
            }
        };

        int[] rgba = backend.readRgbaUIntImage(Image2DReadOnly.borrowed(741L, 1, 1));

        assertArrayEquals(new int[]{11, 12, 13, 14}, rgba);
    }

    @Test
    void readsHighLevelRUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRUIntImageInternal(Image2DReadOnly image) {
                assertEquals(742L, image.handle());
                return new int[]{15, 16};
            }
        };

        int[] values = backend.readRUIntImage(Image2DReadOnly.borrowed(742L, 2, 1));

        assertArrayEquals(new int[]{15, 16}, values);
    }

    @Test
    void readsHighLevelRgUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgUIntImageInternal(Image2DWriteOnly image) {
                assertEquals(743L, image.handle());
                return new int[]{15, 16, 17, 18};
            }
        };

        int[] values = backend.readRgUIntImage(Image2DWriteOnly.borrowed(743L, 2, 1));

        assertArrayEquals(new int[]{15, 16, 17, 18}, values);
    }

    @Test
    void readsHighLevelRgba8ImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected byte[] readRgba8ImageInternal(Image2DWriteOnly image) {
                assertEquals(654L, image.handle());
                return new byte[]{9, 10, 11, 12};
            }
        };

        byte[] rgba = backend.readRgba8Image(Image2DWriteOnly.borrowed(654L, 1, 1));

        assertArrayEquals(new byte[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelRgbaFloatImage3dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImage3DInternal(Image3DWriteOnly image) {
                assertEquals(905L, image.handle());
                assertEquals(2, image.depth());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage3D(Image3DWriteOnly.borrowed(905L, 1, 1, 2));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f}, rgba);
    }

    @Test
    void readsHighLevelRgbaIntImage3dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImage3DInternal(Image3DWriteOnly image) {
                assertEquals(906L, image.handle());
                assertEquals(2, image.depth());
                return new int[]{1, 2, 3, 4, 5, 6, 7, 8};
            }
        };

        int[] rgba = backend.readRgbaIntImage3D(Image3DWriteOnly.borrowed(906L, 1, 1, 2));

        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImage3dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImage3DInternal(Image3DWriteOnly image) {
                assertEquals(907L, image.handle());
                assertEquals(2, image.depth());
                return new int[]{11, 12, 13, 14, 15, 16, 17, 18};
            }
        };

        int[] rgba = backend.readRgbaUIntImage3D(Image3DWriteOnly.borrowed(907L, 1, 1, 2));

        assertArrayEquals(new int[]{11, 12, 13, 14, 15, 16, 17, 18}, rgba);
    }

    @Test
    void readsHighLevelRgbaFloatImage1dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImage1DInternal(Image1DWriteOnly image) {
                assertEquals(908L, image.handle());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage1D(Image1DWriteOnly.borrowed(908L, 1));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImage1dThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImage1DInternal(Image1DWriteOnly image) {
                assertEquals(909L, image.handle());
                return new int[]{9, 10, 11, 12};
            }
        };

        int[] rgba = backend.readRgbaUIntImage1D(Image1DWriteOnly.borrowed(909L, 1));

        assertArrayEquals(new int[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelRgbaUIntImage1dArrayThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImage1DArrayInternal(Image1DArrayWriteOnly image) {
                assertEquals(910L, image.handle());
                assertEquals(2, image.layers());
                return new int[]{9, 10, 11, 12, 13, 14, 15, 16};
            }
        };

        int[] rgba = backend.readRgbaUIntImage1DArray(Image1DArrayWriteOnly.borrowed(910L, 1, 2));

        assertArrayEquals(new int[]{9, 10, 11, 12, 13, 14, 15, 16}, rgba);
    }

    @Test
    void readsHighLevelRgbaIntImage1dBufferThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImage1DBufferInternal(Image1DBufferWriteOnly image) {
                assertEquals(911L, image.handle());
                return new int[]{9, 10, 11, 12};
            }
        };

        int[] rgba = backend.readRgbaIntImage1DBuffer(Image1DBufferWriteOnly.borrowed(911L, 1));

        assertArrayEquals(new int[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelRgbaFloatImage2dArrayThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImage2DArrayInternal(Image2DArrayWriteOnly image) {
                assertEquals(912L, image.handle());
                assertEquals(2, image.layers());
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImage2DArray(Image2DArrayWriteOnly.borrowed(912L, 1, 1, 2));

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f}, rgba);
    }

    @Test
    void readsHighLevelMipmappedFloatImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected float[] readRgbaFloatImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10123L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new float[]{1.0f, 2.0f, 3.0f, 4.0f};
            }
        };

        float[] rgba = backend.readRgbaFloatImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10123L, 4, 2, 2), 1);

        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f, 4.0f}, rgba);
    }

    @Test
    void readsHighLevelMipmappedRgba8ImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected byte[] readRgba8ImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10128L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new byte[]{9, 10, 11, 12};
            }
        };

        byte[] rgba = backend.readRgba8ImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10128L, 4, 2, 2), 1);

        assertArrayEquals(new byte[]{9, 10, 11, 12}, rgba);
    }

    @Test
    void readsHighLevelMipmappedIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaIntImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10126L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new int[]{-9, -10, -11, -12};
            }
        };

        int[] rgba = backend.readRgbaIntImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10126L, 4, 2, 2), 1);

        assertArrayEquals(new int[]{-9, -10, -11, -12}, rgba);
    }

    @Test
    void readsHighLevelMipmappedUIntImageThroughProtectedHook() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend() {
            @Override
            protected int[] readRgbaUIntImageMipmappedInternal(Image2DMipmappedWriteOnly image, int mipLevel) {
                assertEquals(10124L, image.handle());
                assertEquals(2, image.mipLevels());
                assertEquals(1, mipLevel);
                return new int[]{9, 10, 11, 12};
            }
        };

        int[] rgba = backend.readRgbaUIntImageMipmapped(Image2DMipmappedWriteOnly.borrowed(10124L, 4, 2, 2), 1);

        assertArrayEquals(new int[]{9, 10, 11, 12}, rgba);
    }
    private static IrGpuArtifact testIrGpuArtifact(String body) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(IrGpuMethodBody.entry("kernel", "jtg_kernel", body, java.util.List.of()))
                ),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static String irGpuArtifactProperties(String returnExpression) {
        return """
                # JavaToGpu backend-neutral IR artifact manifest
                backendOutput.0.backend=opencl
                backendOutput.0.format=opencl-c
                backendOutput.0.kind=source
                backendOutput.0.resource=javatogpu/sample/Demo/kernel.cl
                backendOutput.count=1
                compilerArtifact=JavaToGpu
                derived.opencl.resource=javatogpu/sample/Demo/kernel.cl
                entryEmittedName=jtg_kernel
                entryMethod=kernel
                format=javatogpu.irgpu.v1
                helper.count=0
                methodBody.0.body=method jtg_kernel source\\=kernel\\nhelpers -\\nbody\\n  %s\\n
                methodBody.0.emittedName=jtg_kernel
                methodBody.0.format=ir-text-v1
                methodBody.0.helperDependency.count=0
                methodBody.0.name=kernel
                methodBody.0.role=entry
                methodBody.count=1
                runtime.defaultBackend=opencl
                runtime.optimizationProfile=off
                schemaVersion=1
                sourceFrontend=java-source
                struct.count=0
                """.formatted(returnExpression);
    }

    private static GpuKernelDescriptor descriptorWithIrGpuResource() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                java.util.List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static GpuKernelDescriptor simpleIrGpuSourceDescriptor() {
        return new GpuKernelDescriptor(
                "gpu_irgpu_entry",
                "inline://integration/simple-irgpu-source-kernel.cl",
                """
                        __kernel void gpu_irgpu_entry(__global const float* input, float scale, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + scale;
                        }
                        """,
                SIMPLE_IRGPU_SOURCE_RESOURCE,
                java.util.List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static OpenClGpuRuntimeBackend.GpuRuntimeIrOptimizationResult optimizedRuntimeResult(
            GpuRuntimeCompileRequest compileRequest,
            String optimizedBody,
            GpuOptimizationStrategyDecision strategyDecision
    ) {
        return optimizedRuntimeResult(compileRequest, optimizedBody, strategyDecision, false);
    }

    private static OpenClGpuRuntimeBackend.GpuRuntimeIrOptimizationResult optimizedRuntimeResultWithAcceptedProof(
            GpuRuntimeCompileRequest compileRequest,
            String optimizedBody,
            GpuOptimizationStrategyDecision strategyDecision
    ) {
        return optimizedRuntimeResult(compileRequest, optimizedBody, strategyDecision, true);
    }

    private static OpenClGpuRuntimeBackend.GpuRuntimeIrOptimizationResult optimizedRuntimeResult(
            GpuRuntimeCompileRequest compileRequest,
            String optimizedBody,
            GpuOptimizationStrategyDecision strategyDecision,
            boolean acceptedProof
    ) {
        IrGpuArtifact optimizedArtifact = testIrGpuArtifact(optimizedBody);
        GpuRuntimeCompileRequest optimizedRequest = compileRequest.withIrGpuArtifact(java.util.Optional.of(optimizedArtifact));
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(compileRequest.irGpuArtifact());
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(optimizedRequest.irGpuArtifact());
        GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.applied(
                "test-runtime-optimizer",
                originalIdentity,
                optimizedIdentity,
                "test-proof",
                List.of("test optimizer supplied transformed IR")
        );
        if (acceptedProof) {
            passReport = passReport.withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                    "test.productionProof",
                    "accepted",
                    java.util.Map.of("runtimeProductionProof", "accepted")
            ));
        }
        return new OpenClGpuRuntimeBackend.GpuRuntimeIrOptimizationResult(
                optimizedRequest,
                new GpuRuntimeIrOptimizationReport(
                        java.util.Optional.of(optimizedArtifact),
                        List.of(passReport),
                        strategyDecision
                )
        );
    }

    private static GpuRuntimeIrOptimizationPassReport optimizerFamilyPayloadPass(
            String optimizerVersion,
            String family,
            String cpuReference,
            String preOptimizationOutput,
            String postOptimizationOutput,
            String tolerance,
            String failureFixture
    ) {
        return GpuRuntimeIrOptimizationPassReport.applied(
                optimizerVersion,
                "irgpu:sha256:original",
                "irgpu:sha256:" + family,
                "proof:accepted",
                List.of(family + " runtime-equivalence payload captured")
        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "ir-validation",
                "accepted",
                java.util.Map.ofEntries(
                        java.util.Map.entry("optimizerFamily", family),
                        java.util.Map.entry("runtimeEquivalencePayload.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.cpuReference.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.preOptimizationOutput.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.postOptimizationOutput.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.tolerance.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.failureFixture.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.resource", "fixture://payload/" + family),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.cpuReference.resource",
                                "fixture://payload/" + family + "/cpu-reference"
                        ),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.preOptimizationOutput.resource",
                                "fixture://payload/" + family + "/pre-output"
                        ),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.postOptimizationOutput.resource",
                                "fixture://payload/" + family + "/post-output"
                        ),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.tolerance.resource",
                                "fixture://payload/" + family + "/tolerance"
                        ),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.failureFixture.resource",
                                "fixture://payload/" + family + "/failure-fixture"
                        ),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.CpuReference", cpuReference),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.PreOptimizationOutput", preOptimizationOutput),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.PostOptimizationOutput", postOptimizationOutput),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.Tolerance", tolerance),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.FailureFixture", failureFixture),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.ReferenceMode",
                                "cse".equals(family)
                                        ? "original-ir-interpreter"
                                        : "original-ir-array-interpreter"
                        ),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.Case.Count", "1"),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.Case.0.Name", "fixture-case"),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.Case.0.Successful", "true"),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.Case.0.Input.Count", "1"),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.Case.0.Input.0.Name",
                                "cse".equals(family) ? "x" : "left"
                        ),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.Case.0.Input.0.Value",
                                "cse".equals(family) ? "7" : "[7, -2, 13, 99]"
                        ),
                        java.util.Map.entry(family + "RuntimeEquivalencePayload.Case.0.Output.Count", "1"),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.Case.0.Output.0.Name",
                                "cse".equals(family) ? "outA" : "out"
                        ),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.Case.0.Output.0.CpuReference",
                                "cse".equals(family) ? "36" : "[8, 2, 10, 110]"
                        ),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.Case.0.Output.0.PreOptimization",
                                "cse".equals(family) ? "36" : "[8, 2, 10, 110]"
                        ),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.Case.0.Output.0.PostOptimization",
                                "cse".equals(family) ? "36" : "[8, 2, 10, 110]"
                        ),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.Case.0.Output.0.Tolerance",
                                "cse".equals(family) ? "exact-int" : "exact-int-lane"
                        ),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.Case.0.Output.0.Equivalent",
                                "true"
                        ),
                        java.util.Map.entry(
                                family + "RuntimeEquivalencePayload.Case.0.FailureFixture.Diagnostic.Count",
                                "0"
                        )
                )
        ));
    }

    private static GpuOptimizationStrategyDecision productionBackedStrategyDecision() {
        return new GpuOptimizationStrategyDecision(
                "strategy:production-fixture",
                "test-vendor",
                "vendor-tuned",
                false,
                true,
                "test fixture carries production evidence",
                new GpuOptimizationVendorBaseline(
                        "test-vendor",
                        "production-fixture",
                        true,
                        true,
                        "test-fixture",
                        List.of("test fixture is promotion-eligible")
                ),
                List.of("production fixture is evidence-backed")
        );
    }

    private static GpuProductionPromotionDecision productionEnabledDecision() {
        return new GpuProductionPromotionDecision(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                "production-ready",
                true,
                true,
                true,
                "none",
                "none",
                "production fixture enables runtime IR mutation"
        );
    }

    private static void writeProductionReadyExplainability(Path path) throws java.io.IOException {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("status", "production-ready");
        properties.setProperty("kernel.count", "1");
        properties.setProperty("i3ReviewReady.count", "1");
        properties.setProperty("i3Blocked.count", "0");
        properties.setProperty("i3SourceReady.count", "1");
        properties.setProperty("productionSourceSwitchingAllowed", "true");
        properties.setProperty("productionSourceSwitchingEnabled", "true");
        properties.setProperty("productionSourceSwitchingEnabled.count", "1");
        properties.setProperty("productionSourceSwitchingEnabled.all", "true");
        properties.setProperty("productionPromotionDecisionMode.productionEnabled.count", "1");
        properties.setProperty("productionPromotionDecisionMode.productionEnabled.all", "true");
        properties.setProperty("productionPromotionOperatorAccepted.count", "1");
        properties.setProperty("productionPromotionOperatorAccepted.all", "true");
        properties.setProperty("sourceSwitching.productionDecision.count", "1");
        properties.setProperty("sourceSwitching.productionDecision.all", "true");
        properties.setProperty("productionMutationAllowed", "true");
        properties.setProperty("productionMutationEnabled", "true");
        properties.setProperty("backendPromotionArtifactSupport.complete", "true");
        properties.setProperty("backendPromotionArtifactSupport.missing.count", "0");
        properties.setProperty("blocker.count", "0");
        try (java.io.Writer writer = Files.newBufferedWriter(path)) {
            properties.store(writer, "test production promotion explainability");
        }
    }

    private static void assertProductionPromotionExplainabilityFileKeepsRuntimeDiagnosticOnly(
            Path explainabilityFile
    ) throws java.io.IOException {
        GpuKernelDescriptor descriptor = intOutputDescriptor();
        AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot = new AtomicReference<>();
        AtomicReference<GpuRuntimeCompileRequest> capturedCompileRequest = new AtomicReference<>();
        String previousExplainabilityFile = System.getProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
        try {
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", explainabilityFile.toString());

            OpenClGpuRuntimeBackend backend = new SnapshotCapturingBackend(capturedSnapshot) {
                @Override
                protected GpuBackendModuleArtifact lowerBackendModule(GpuRuntimeCompileRequest compileRequest) {
                    capturedCompileRequest.set(compileRequest);
                    return GpuBackendModuleArtifact.openClSource(
                            "__kernel void kernel(__global int* output) { output[0] = 1; }",
                            "javatogpu/sample/Demo/kernel.cl",
                            "test-lowerer-v1"
                    );
                }
            };

            backend.invoke(new GpuKernelInvocation(
                    descriptor,
                    new Object[]{new int[]{0}},
                    GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
            ));
        } finally {
            if (previousExplainabilityFile == null) {
                System.clearProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
            } else {
                System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousExplainabilityFile);
            }
        }

        assertEquals(
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                capturedCompileRequest.get().options().backendOptions().productionPromotionDecisionMode()
        );
        assertTrue(capturedSnapshot.get().backendSourceSwitchingDecision().isPresent());
        assertEquals(
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                capturedSnapshot.get().backendSourceSwitchingDecision().orElseThrow().productionPromotionDecisionMode()
        );
        assertTrue(capturedSnapshot.get().backendSourceSwitchingDecision().orElseThrow().productionSourceSwitchingEnabled());
    }

    private static IrGpuArtifact parityMatchedIrGpuArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "kernel",
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "kernel",
                                "body\n  set output[0] = 1\n",
                                java.util.List.of()
                        ))
                ),
                java.util.List.of(new net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter(
                        "output",
                        "int[]",
                        "GLOBAL",
                        false,
                        java.util.List.of()
                )),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.backendNeutralReady(),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact parityMatchedFloat2IrGpuArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "kernel",
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "kernel",
                                "body\n  set output[0] = (float2)(1.0f, 2.0f)\n",
                                java.util.List.of()
                        ))
                ),
                java.util.List.of(new net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter(
                        "output",
                        "net.sixik.ga_utils.javatogpu.api.types.floats.Float2[]",
                        "GLOBAL",
                        false,
                        java.util.List.of()
                )),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.backendNeutralReady(),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact parityMatchedStructIrGpuArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "kernel",
                        java.util.List.of(),
                        java.util.List.of("typedef struct { int x; float y; } EquivalenceStructSample;"),
                        java.util.List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "kernel",
                                "body\n  set output[0].x = 11\n  set output[0].y = 12.5f\n",
                                java.util.List.of()
                        ))
                ),
                java.util.List.of(new net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter(
                        "output",
                        "EquivalenceStructSample[]",
                        "GLOBAL",
                        false,
                        java.util.List.of()
                )),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.backendNeutralReady(),
                java.util.List.of(new IrGpuStructMetadata(
                        EquivalenceStructSample.class.getName(),
                        "EquivalenceStructSample",
                        java.util.List.of(
                                new IrGpuStructFieldMetadata("x", "int", java.util.List.of()),
                                new IrGpuStructFieldMetadata("y", "float", java.util.List.of())
                        ),
                        java.util.List.of()
                )),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static String canonicalOpenClSource(String source) {
        return source.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean calledFromBackendExecutionPipeline() {
        return Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals(GpuBackendExecutionPipeline.class.getName())
                        && frame.getMethodName().equals("execute")
        );
    }

    private static GpuKernelDescriptor intOutputDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                java.util.List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static GpuKernelDescriptor float2OutputDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global float2* output) { output[0] = (float2)(1.0f, 2.0f); }",
                java.util.List.of(new GpuKernelParameterDescriptor(
                        "output",
                        "net.sixik.ga_utils.javatogpu.api.types.floats.Float2[]",
                        GpuKernelParameterAccess.READ_WRITE
                ))
        );
    }

    private static GpuKernelDescriptor structOutputDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "typedef struct{\n"
                        + "    int x;\n"
                        + "    float y;\n"
                        + "} EquivalenceStructSample;\n\n"
                        + "__kernel void kernel(__global EquivalenceStructSample* output) {\n"
                        + "    output[0].x = 11;\n"
                        + "    output[0].y = 12.5f;\n"
                        + "}",
                java.util.List.of(new GpuKernelParameterDescriptor(
                        "output",
                        "EquivalenceStructSample[]",
                        GpuKernelParameterAccess.READ_WRITE
                ))
        );
    }

    private static URLClassLoader callSiteClassLoader(
            String callerMethodName,
            String expression
    ) throws java.io.IOException {
        Path root = Files.createTempDirectory("javatogpu-runtime-call-site");
        Path resource = root.resolve(GpuRuntimeCallSiteResolver.resourcePath(
                OpenClGpuRuntimeBackendTest.class.getName()
        ));
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, String.join("\n",
                "format=javatogpu.call-sites.v1",
                "callSite.count=1",
                "callSite.0.callerClassName=" + OpenClGpuRuntimeBackendTest.class.getName(),
                "callSite.0.callerMethodName=" + callerMethodName,
                "callSite.0.sourceName=OpenClGpuRuntimeBackendTest.java",
                "callSite.0.line=1",
                "callSite.0.column=1",
                "callSite.0.endLine=1",
                "callSite.0.endColumn=" + expression.length(),
                "callSite.0.expression=" + expression,
                "callSite.0.targetOwnerName=unknown",
                "callSite.0.targetMethodName=unknown",
                ""
        ));
        return new URLClassLoader(
                new URL[]{root.toUri().toURL()},
                OpenClGpuRuntimeBackendTest.class.getClassLoader()
        );
    }

    private static class SnapshotCapturingBackend extends OpenClGpuRuntimeBackend {
        protected final AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot;

        private SnapshotCapturingBackend(AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot) {
            this.capturedSnapshot = capturedSnapshot;
        }

        @Override
        protected OpenClRuntimeCapabilities runtimeCapabilities() {
            return new OpenClRuntimeCapabilities("Mock GPU", "OpenCL 3.0 Mock", true, true, true, 32_768L, 256L);
        }

        @Override
        protected OpenClCompiledKernel compileKernel(
                GpuRuntimeCompileRequest compileRequest,
                GpuBackendModuleArtifact moduleArtifact
        ) {
            return new OpenClCompiledKernel(compileRequest.descriptor(), "compiled:test");
        }

        @Override
        protected void executeKernel(OpenClPreparedExecution execution) {
            capturedSnapshot.set(execution.compiledKernel().artifactSnapshot());
        }
    }

    private static class EquivalenceSimulatingBackend extends SnapshotCapturingBackend {
        private final int descriptorEquivalenceOutput;
        private final int reconstructedEquivalenceOutput;

        private EquivalenceSimulatingBackend(
                AtomicReference<GpuRuntimeCompileArtifactSnapshot> capturedSnapshot,
                int descriptorEquivalenceOutput,
                int reconstructedEquivalenceOutput
        ) {
            super(capturedSnapshot);
            this.descriptorEquivalenceOutput = descriptorEquivalenceOutput;
            this.reconstructedEquivalenceOutput = reconstructedEquivalenceOutput;
        }

        @Override
        protected void executeKernel(OpenClPreparedExecution execution) {
            for (OpenClPreparedBufferBinding binding : execution.bufferBindings()) {
                if (binding.access() != GpuKernelParameterAccess.READ_WRITE) {
                    continue;
                }
                mutateEquivalenceOutput(execution, binding.binding().sourceArray());
            }
            capturedSnapshot.set(execution.compiledKernel().artifactSnapshot());
        }

        private void mutateEquivalenceOutput(OpenClPreparedExecution execution, Object sourceArray) {
            if (sourceArray instanceof int[] values && values.length > 0) {
                if (execution.compiledKernel().cacheKey().contains("#irgpu-reconstructed-equivalence-preflight")) {
                    values[0] = reconstructedEquivalenceOutput;
                } else if (execution.compiledKernel().artifactSnapshot().runtimeEquivalenceEvidence().executed()) {
                    values[0] = 1;
                } else {
                    values[0] = descriptorEquivalenceOutput;
                }
                return;
            }
            if (sourceArray instanceof Float2[] values && values.length > 0) {
                values[0] = new Float2(1.0f, 2.0f);
                return;
            }
            if (sourceArray instanceof EquivalenceStructSample[] values && values.length > 0) {
                values[0] = new EquivalenceStructSample(11, 12.5f);
            }
        }
    }

    @GPUStruct
    static final class EquivalenceStructSample {
        int x;
        float y;

        EquivalenceStructSample() {
        }

        EquivalenceStructSample(int x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
