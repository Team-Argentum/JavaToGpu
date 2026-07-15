package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactSerializer;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodTestVectorMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeMethodTestProbesTest {

    @Test
    void loadsTestVectorProbePlanFromDescriptorIrGpuResource() throws Exception {
        String irGpuResource = "javatogpu/sample/Demo/kernel.irgpu.properties";
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "selection-smoke",
                List.of("fixtures/selection-smoke.inputs.json"),
                List.of("fixtures/selection-smoke.outputs.json"),
                "abs=1e-5",
                List.of("selection", "smoke"),
                true,
                "GPUTest"
        )));

        Path root = Files.createTempDirectory("javatogpu-method-test-probes");
        Path manifestPath = root.resolve(irGpuResource);
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, IrGpuArtifactSerializer.serialize(artifact));
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/selection-smoke.inputs.json"), "{\"input\":[1.0]}");
        Files.writeString(root.resolve("fixtures/selection-smoke.outputs.json"), "{\"output\":[2.0]}");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor(irGpuResource), classLoader);

            assertTrue(plan.metadataReady());
            assertTrue(plan.hasSelectionProbes());
            assertEquals("none", plan.firstBlocker());
            assertEquals(1, plan.testVectors().size());
            assertEquals("selection-smoke", plan.testVectors().get(0).testId());
            assertEquals(List.of("fixtures/selection-smoke.inputs.json"), plan.testVectors().get(0).inputRefs());
            assertEquals(List.of("fixtures/selection-smoke.outputs.json"), plan.testVectors().get(0).expectedOutputRefs());
            assertEquals("abs=1e-5", plan.testVectors().get(0).tolerance());

            Map<String, String> fields = plan.artifactFields("methodTests");
            assertEquals("true", fields.get("methodTests.metadataReady"));
            assertEquals("1", fields.get("methodTests.testVector.count"));
            assertEquals("1", fields.get("methodTests.selectionProbe.count"));
            assertEquals("selection-smoke", fields.get("methodTests.testVector.0.testId"));
            assertEquals("fixtures/selection-smoke.inputs.json", fields.get("methodTests.testVector.0.inputRef.0"));
            assertTrue(plan.toMarkdown().contains("Method test probe plan: metadata-ready"));
            assertTrue(plan.toMarkdown().contains("selection-smoke"));

            GpuRuntimeMethodTestFixtureReadiness readiness = GpuRuntimeMethodTestProbes.fixtureReadiness(plan, classLoader);
            assertTrue(readiness.fixtureResourcesReady());
            assertTrue(readiness.selectionProbeResourcesReady());
            assertEquals("none", readiness.firstBlocker());
            assertEquals(2, readiness.resources().size());
            assertEquals(2, readiness.availableResourceCount());
            assertEquals(0, readiness.missingResourceCount());
            assertTrue(readiness.fixturePayloadPreviewsReady());
            assertEquals(2, readiness.payloadPreviewReadyCount());
            assertEquals(0, readiness.payloadPreviewBlockedCount());
            assertTrue(readiness.resources().get(0).sizeBytes() > 0);
            assertEquals(64, readiness.resources().get(0).sha256().length());
            assertTrue(readiness.resources().get(0).payloadPreview().schemaReady());
            assertEquals("json-object-v1", readiness.resources().get(0).payloadPreview().format());
            assertEquals("input", readiness.resources().get(0).payloadPreview().primaryField());
            assertEquals("array", readiness.resources().get(0).payloadPreview().primaryValueKind());
            assertEquals(1, readiness.resources().get(0).payloadPreview().primaryItemCount());
            assertTrue(readiness.resources().get(1).sizeBytes() > 0);
            assertEquals(64, readiness.resources().get(1).sha256().length());
            assertTrue(readiness.resources().get(1).payloadPreview().schemaReady());
            assertEquals("output", readiness.resources().get(1).payloadPreview().primaryField());
            Map<String, String> readinessFields = readiness.artifactFields("methodTestFixtures");
            assertEquals("true", readinessFields.get("methodTestFixtures.fixtureResourcesReady"));
            assertEquals("true", readinessFields.get("methodTestFixtures.selectionProbeResourcesReady"));
            assertEquals("true", readinessFields.get("methodTestFixtures.fixturePayloadPreviewsReady"));
            assertEquals("2", readinessFields.get("methodTestFixtures.resource.available.count"));
            assertEquals("fixtures/selection-smoke.inputs.json", readinessFields.get("methodTestFixtures.resource.0.resourceRef"));
            assertTrue(readinessFields.get("methodTestFixtures.resource.0.location").contains("selection-smoke.inputs.json"));
            assertEquals(Long.toString(readiness.resources().get(0).sizeBytes()), readinessFields.get("methodTestFixtures.resource.0.sizeBytes"));
            assertEquals(readiness.resources().get(0).sha256(), readinessFields.get("methodTestFixtures.resource.0.sha256"));
            assertEquals("json-object-v1", readinessFields.get("methodTestFixtures.resource.0.payload.format"));
            assertEquals("true", readinessFields.get("methodTestFixtures.resource.0.payload.schemaReady"));
            assertEquals("input", readinessFields.get("methodTestFixtures.resource.0.payload.primaryField"));
            assertEquals("1", readinessFields.get("methodTestFixtures.resource.0.payload.primaryItem.count"));
            assertTrue(readiness.toMarkdown().contains("Method test fixture readiness: ready"));
            assertTrue(readiness.toMarkdown().contains("sha256="));
            assertTrue(readiness.toMarkdown().contains("Fixture payload previews ready: 2/2"));
            assertTrue(readiness.toMarkdown().contains("payload=json-object-v1 input[1]"));
        }
    }

    @Test
    void returnsBlockedPlanWhenDescriptorHasNoIrGpuResource() {
        GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel(__global float* output) { output[0] = 1.0f; }",
                List.of(new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE))
        ));

        assertFalse(plan.metadataReady());
        assertEquals("irgpu-resource-missing", plan.firstBlocker());
        assertEquals("false", plan.artifactFields("methodTests").get("methodTests.metadataReady"));
        assertTrue(plan.toMarkdown().contains("First blocker: irgpu-resource-missing"));
    }

    @Test
    void reportsMetadataWithoutSelectionProbeVectors() {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "slow-edge",
                List.of("fixtures/slow.inputs.json"),
                List.of("fixtures/slow.outputs.json"),
                "",
                List.of("slow"),
                false,
                "GPUTest"
        )));

        GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor("demo.irgpu.properties"), artifact);

        assertTrue(plan.metadataReady());
        assertFalse(plan.hasSelectionProbes());
        assertEquals("selection-probe-vectors-missing", plan.firstBlocker());
        assertEquals("0", plan.artifactFields("methodTests").get("methodTests.selectionProbe.count"));
        assertTrue(plan.toMarkdown().contains("Selection probes: 0"));
    }

    @Test
    void reportsMissingFixtureResourcesBeforeProbeExecution() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "missing-output",
                List.of("fixtures/missing-output.inputs.json"),
                List.of("fixtures/missing-output.outputs.json"),
                "",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-missing-fixtures");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/missing-output.inputs.json"), "{\"input\":[1.0]}");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor("demo.irgpu.properties"), artifact);
            GpuRuntimeMethodTestFixtureReadiness readiness = GpuRuntimeMethodTestProbes.fixtureReadiness(plan, classLoader);

            assertFalse(readiness.fixtureResourcesReady());
            assertFalse(readiness.selectionProbeResourcesReady());
            assertEquals("fixture-resource-not-found", readiness.firstBlocker());
            assertEquals(2, readiness.resources().size());
            assertEquals(1, readiness.availableResourceCount());
            assertEquals(1, readiness.missingResourceCount());
            Map<String, String> fields = readiness.artifactFields("methodTestFixtures");
            assertEquals("false", fields.get("methodTestFixtures.fixtureResourcesReady"));
            assertEquals("1", fields.get("methodTestFixtures.resource.missing.count"));
            assertEquals("fixtures/missing-output.outputs.json", fields.get("methodTestFixtures.resource.1.resourceRef"));
            assertEquals("false", fields.get("methodTestFixtures.resource.1.available"));
            assertEquals("-1", fields.get("methodTestFixtures.resource.1.sizeBytes"));
            assertEquals("none", fields.get("methodTestFixtures.resource.1.sha256"));
            assertTrue(readiness.resources().get(0).sizeBytes() > 0);
            assertEquals(64, readiness.resources().get(0).sha256().length());
            assertTrue(readiness.resources().get(0).payloadPreview().schemaReady());
            assertEquals(-1L, readiness.resources().get(1).sizeBytes());
            assertEquals("none", readiness.resources().get(1).sha256());
            assertFalse(readiness.fixturePayloadPreviewsReady());
            assertEquals(1, readiness.payloadPreviewReadyCount());
            assertEquals(1, readiness.payloadPreviewBlockedCount());
            assertEquals("fixture-resource-not-found", readiness.resources().get(1).payloadPreview().blocker());
            assertTrue(readiness.toMarkdown().contains("First blocker: fixture-resource-not-found"));
        }
    }

    @Test
    void reportsInvalidFixturePayloadSchemaBeforeProbeExecution() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "invalid-json",
                List.of("fixtures/invalid-json.inputs.json"),
                List.of("fixtures/invalid-json.outputs.json"),
                "",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-invalid-fixtures");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/invalid-json.inputs.json"), "not-json");
        Files.writeString(root.resolve("fixtures/invalid-json.outputs.json"), "{\"output\":[2.0]}");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor("demo.irgpu.properties"), artifact);
            GpuRuntimeMethodTestFixtureReadiness readiness = GpuRuntimeMethodTestProbes.fixtureReadiness(plan, classLoader);

            assertFalse(readiness.fixtureResourcesReady());
            assertFalse(readiness.selectionProbeResourcesReady());
            assertFalse(readiness.fixturePayloadPreviewsReady());
            assertEquals("fixture-payload-schema-not-ready", readiness.firstBlocker());
            assertEquals(2, readiness.resources().size());
            assertEquals(2, readiness.availableResourceCount());
            assertEquals(0, readiness.missingResourceCount());
            assertEquals(1, readiness.payloadPreviewReadyCount());
            assertEquals(1, readiness.payloadPreviewBlockedCount());
            assertFalse(readiness.resources().get(0).payloadPreview().schemaReady());
            assertEquals("fixture-payload-json-invalid", readiness.resources().get(0).payloadPreview().blocker());
            assertTrue(readiness.resources().get(1).payloadPreview().schemaReady());

            Map<String, String> fields = readiness.artifactFields("methodTestFixtures");
            assertEquals("false", fields.get("methodTestFixtures.fixturePayloadPreviewsReady"));
            assertEquals("fixture-payload-json-invalid", fields.get("methodTestFixtures.resource.0.payload.blocker"));
            assertTrue(readiness.toMarkdown().contains("payloadBlocker=fixture-payload-json-invalid"));
        }
    }

    @Test
    void bindsSimpleNumericFixtureValuesToDescriptorParametersWithoutExecution() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "bind-smoke",
                List.of("fixtures/bind-smoke.inputs.json"),
                List.of("fixtures/bind-smoke.outputs.json"),
                "abs=1e-5",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-value-bindings");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/bind-smoke.inputs.json"), "{\"input\":[1.0,2.0],\"scale\":2.5}");
        Files.writeString(root.resolve("fixtures/bind-smoke.outputs.json"), "{\"output\":[2.5,5.0]}");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithInputScaleOutput("demo.irgpu.properties");
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    plan,
                    classLoader
            );

            assertTrue(bindings.bindingsReady());
            assertEquals("none", bindings.firstBlocker());
            assertEquals(3, bindings.bindings().size());
            assertEquals(3, bindings.bindingReadyCount());
            assertEquals(0, bindings.bindingBlockedCount());
            assertEquals("input", bindings.bindings().get(0).parameterName());
            assertEquals("float[]", bindings.bindings().get(0).javaType());
            assertEquals("numeric-array", bindings.bindings().get(0).valueKind());
            assertEquals(List.of("1.0", "2.0"), bindings.bindings().get(0).numericValues());
            assertEquals("scale", bindings.bindings().get(1).parameterName());
            assertEquals("float", bindings.bindings().get(1).javaType());
            assertEquals("numeric-scalar", bindings.bindings().get(1).valueKind());
            assertEquals(List.of("2.5"), bindings.bindings().get(1).numericValues());
            assertEquals("output", bindings.bindings().get(2).parameterName());
            assertEquals("expectedOutput", bindings.bindings().get(2).kind());

            Map<String, String> fields = bindings.artifactFields("methodTestValueBindings");
            assertEquals("true", fields.get("methodTestValueBindings.bindingsReady"));
            assertEquals("3", fields.get("methodTestValueBindings.binding.ready.count"));
            assertEquals("input", fields.get("methodTestValueBindings.binding.0.parameterName"));
            assertEquals("2", fields.get("methodTestValueBindings.binding.0.item.count"));
            assertEquals("1.0", fields.get("methodTestValueBindings.binding.0.numericValue.preview.0"));
            assertTrue(bindings.toMarkdown().contains("Method test fixture value bindings: ready"));
            assertTrue(bindings.toMarkdown().contains("input:float[] ready=true numeric-array[2]"));

            GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                    GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings);

            assertTrue(materialization.materializationReady());
            assertEquals("none", materialization.firstBlocker());
            assertEquals(1, materialization.invocations().size());
            GpuRuntimeMethodTestInvocationMaterialization invocation = materialization.invocations().get(0);
            assertTrue(invocation.invocationReady());
            assertEquals(3, invocation.arguments().size());
            assertEquals(3, invocation.argumentReadyCount());
            assertEquals(1, invocation.expectedOutputReadyCount());
            Object[] invocationArguments = invocation.invocationArguments();
            assertEquals(3, invocationArguments.length);
            assertArrayEquals(new float[]{1.0f, 2.0f}, (float[]) invocationArguments[0], 0.0001f);
            assertEquals(2.5f, (Float) invocationArguments[1], 0.0001f);
            assertArrayEquals(new float[]{0.0f, 0.0f}, (float[]) invocationArguments[2], 0.0001f);
            assertArrayEquals(
                    new float[]{2.5f, 5.0f},
                    (float[]) invocation.arguments().get(2).expectedOutputValue(),
                    0.0001f
            );
            assertEquals("java-float-array", invocation.arguments().get(0).argumentKind());
            assertEquals("java-float", invocation.arguments().get(1).argumentKind());
            assertEquals("zero-filled-float-array", invocation.arguments().get(2).argumentKind());
            assertEquals("java-float-array", invocation.arguments().get(2).expectedOutputKind());

            GpuRuntimeMethodTestReferenceComparisonPlan referenceComparison = GpuRuntimeMethodTestProbes.compareWithReference(
                    materialization,
                    plan,
                    referenceArguments -> {
                        float[] input = (float[]) referenceArguments[0];
                        float scale = (Float) referenceArguments[1];
                        float[] output = (float[]) referenceArguments[2];
                        for (int index = 0; index < input.length; index++) {
                            output[index] = input[index] * scale;
                        }
                    }
            );

            assertTrue(referenceComparison.referenceComparisonReady());
            assertTrue(referenceComparison.referenceComparisonPassed());
            assertEquals("passed", referenceComparison.status());
            assertEquals(1, referenceComparison.comparisons().size());
            assertEquals("none", referenceComparison.firstBlocker());
            assertEquals("none", referenceComparison.firstFailure());
            assertEquals("java-float-array", referenceComparison.comparisons().get(0).actualKind());
            assertEquals("2.5", referenceComparison.comparisons().get(0).actualNumericValues().get(0));
            assertEquals(1.0e-5d, referenceComparison.comparisons().get(0).absoluteTolerance(), 0.0d);
            assertTrue(referenceComparison.toMarkdown().contains("Method test reference comparison: passed"));

            GpuRuntimeBackend previousBackend = GpuRuntime.backend();
            java.util.concurrent.atomic.AtomicReference<GpuKernelInvocation> capturedInvocation =
                    new java.util.concurrent.atomic.AtomicReference<>();
            GpuRuntime.setBackend(invocationRequest -> {
                capturedInvocation.set(invocationRequest);
                float[] input = (float[]) invocationRequest.arguments()[0];
                float scale = (Float) invocationRequest.arguments()[1];
                float[] output = (float[]) invocationRequest.arguments()[2];
                for (int index = 0; index < input.length; index++) {
                    output[index] = input[index] * scale;
                }
            });
            GpuRuntimeMethodTestGpuProbePlan gpuProbe;
            try {
                gpuProbe = GpuRuntimeMethodTestProbes.executeGpuProbe(descriptor, materialization, plan,
                        GpuRuntimeMethodTestGpuProbeOptions.defaults());
            } finally {
                GpuRuntime.setBackend(previousBackend);
            }

            assertEquals("passed", gpuProbe.status());
            assertTrue(gpuProbe.gpuProbeReady());
            assertTrue(gpuProbe.gpuProbePassed());
            assertEquals(1, gpuProbe.executions().size());
            assertEquals(1, gpuProbe.executionPassedCount());
            assertEquals(1, gpuProbe.comparisonPassedCount());
            assertEquals(2L, gpuProbe.executions().get(0).executionConfig().globalWorkSize());
            assertSame(descriptor, capturedInvocation.get().descriptor());
            assertEquals(2L, capturedInvocation.get().executionConfig().globalWorkSize());
            assertEquals("java-float-array", gpuProbe.executions().get(0).comparisons().get(0).actualKind());
            assertEquals(64, gpuProbe.executions().get(0).evidenceKey().stableHash().length());
            assertTrue(gpuProbe.executions().get(0).evidenceKey().stableKey().contains("test=bind-smoke"));
            assertTrue(gpuProbe.toMarkdown().contains("Method test GPU probe plan: passed"));

            GpuRuntimeMethodTestGpuProbeCache cache = new GpuRuntimeMethodTestGpuProbeCache();
            java.util.concurrent.atomic.AtomicInteger cachedBackendCalls = new java.util.concurrent.atomic.AtomicInteger();
            GpuRuntime.setBackend(invocationRequest -> {
                cachedBackendCalls.incrementAndGet();
                float[] input = (float[]) invocationRequest.arguments()[0];
                float scale = (Float) invocationRequest.arguments()[1];
                float[] output = (float[]) invocationRequest.arguments()[2];
                for (int index = 0; index < input.length; index++) {
                    output[index] = input[index] * scale;
                }
            });
            GpuRuntimeMethodTestGpuProbePlan firstCachedProbe;
            GpuRuntimeMethodTestGpuProbePlan secondCachedProbe;
            try {
                GpuRuntimeMethodTestGpuProbeOptions cachedOptions = GpuRuntimeMethodTestGpuProbeOptions.defaults()
                        .withCache(cache);
                firstCachedProbe = GpuRuntimeMethodTestProbes.executeGpuProbe(descriptor, materialization, plan, cachedOptions);
                secondCachedProbe = GpuRuntimeMethodTestProbes.executeGpuProbe(descriptor, materialization, plan, cachedOptions);
            } finally {
                GpuRuntime.setBackend(previousBackend);
            }

            assertEquals(1, cachedBackendCalls.get());
            assertEquals(1, cache.size());
            assertTrue(firstCachedProbe.gpuProbePassed());
            assertTrue(secondCachedProbe.gpuProbePassed());
            assertFalse(firstCachedProbe.executions().get(0).cacheHit());
            assertTrue(secondCachedProbe.executions().get(0).cacheHit());
            assertEquals(
                    firstCachedProbe.executions().get(0).evidenceKey().stableHash(),
                    secondCachedProbe.executions().get(0).evidenceKey().stableHash()
            );

            Map<String, String> materializationFields = materialization.artifactFields("methodTestInvocations");
            assertEquals("true", materializationFields.get("methodTestInvocations.materializationReady"));
            assertEquals("1", materializationFields.get("methodTestInvocations.invocation.ready.count"));
            assertEquals("zero-filled-float-array", materializationFields.get("methodTestInvocations.invocation.0.argument.2.argument.kind"));
            assertEquals("java-float-array", materializationFields.get("methodTestInvocations.invocation.0.argument.2.expectedOutput.kind"));
            assertTrue(materialization.toMarkdown().contains("Method test invocation materialization plan: ready"));
            assertTrue(materialization.toMarkdown().contains("output:float[] ready=true zero-filled-float-array[2] expected=java-float-array[2]"));

            Map<String, String> comparisonFields = referenceComparison.artifactFields("methodTestReference");
            assertEquals("passed", comparisonFields.get("methodTestReference.status"));
            assertEquals("true", comparisonFields.get("methodTestReference.referenceComparisonPassed"));
            assertEquals("java-float-array", comparisonFields.get("methodTestReference.comparison.0.actual.kind"));
            assertEquals("2.5", comparisonFields.get("methodTestReference.comparison.0.actual.numericValue.preview.0"));

            Map<String, String> gpuProbeFields = gpuProbe.artifactFields("methodTestGpuProbe");
            assertEquals("passed", gpuProbeFields.get("methodTestGpuProbe.status"));
            assertEquals("true", gpuProbeFields.get("methodTestGpuProbe.gpuProbePassed"));
            assertEquals("2", gpuProbeFields.get("methodTestGpuProbe.execution.0.executionConfig.globalX"));
            assertEquals("true", gpuProbeFields.get("methodTestGpuProbe.execution.0.evidenceKey.present"));
            assertEquals(
                    gpuProbe.executions().get(0).evidenceKey().stableHash(),
                    gpuProbeFields.get("methodTestGpuProbe.execution.0.evidenceKey.stableHash")
            );
            assertEquals("java-float-array", gpuProbeFields.get("methodTestGpuProbe.execution.0.comparison.0.actual.kind"));
        }
    }

    @Test
    void reportsReferenceOutputMismatchWithoutInvokingGpu() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "reference-mismatch",
                List.of("fixtures/reference-mismatch.inputs.json"),
                List.of("fixtures/reference-mismatch.outputs.json"),
                "abs=1e-5",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-reference-mismatch");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/reference-mismatch.inputs.json"), "{\"input\":[1.0,2.0],\"scale\":2.5}");
        Files.writeString(root.resolve("fixtures/reference-mismatch.outputs.json"), "{\"output\":[2.5,5.0]}");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithInputScaleOutput("demo.irgpu.properties");
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    plan,
                    classLoader
            );
            GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                    GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings);

            GpuRuntimeMethodTestReferenceComparisonPlan referenceComparison = GpuRuntimeMethodTestProbes.compareWithReference(
                    materialization,
                    plan,
                    referenceArguments -> {
                        float[] output = (float[]) referenceArguments[2];
                        output[0] = 2.5f;
                        output[1] = 4.0f;
                    }
            );

            assertTrue(referenceComparison.referenceComparisonReady());
            assertFalse(referenceComparison.referenceComparisonPassed());
            assertEquals("failed", referenceComparison.status());
            assertEquals(1, referenceComparison.comparisonFailedCount());
            assertEquals("fixture-reference-output-mismatch", referenceComparison.firstFailure());
            assertTrue(referenceComparison.diagnostics().get(0).contains("Mismatch at item 1"));
            assertTrue(referenceComparison.toMarkdown().contains("Method test reference comparison: failed"));
            assertTrue(referenceComparison.toMarkdown().contains("failure=fixture-reference-output-mismatch"));
        }
    }

    @Test
    void reportsGpuProbeOutputMismatchThroughCurrentRuntimeBackend() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "gpu-probe-mismatch",
                List.of("fixtures/gpu-probe-mismatch.inputs.json"),
                List.of("fixtures/gpu-probe-mismatch.outputs.json"),
                "abs=1e-5",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-gpu-probe-mismatch");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/gpu-probe-mismatch.inputs.json"), "{\"input\":[1.0,2.0],\"scale\":2.5}");
        Files.writeString(root.resolve("fixtures/gpu-probe-mismatch.outputs.json"), "{\"output\":[2.5,5.0]}");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithInputScaleOutput("demo.irgpu.properties");
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    plan,
                    classLoader
            );
            GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                    GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings);

            GpuRuntimeBackend previousBackend = GpuRuntime.backend();
            GpuRuntime.setBackend(invocationRequest -> {
                float[] output = (float[]) invocationRequest.arguments()[2];
                output[0] = 2.5f;
                output[1] = 4.0f;
            });
            GpuRuntimeMethodTestGpuProbePlan gpuProbe;
            try {
                gpuProbe = GpuRuntimeMethodTestProbes.executeGpuProbe(descriptor, materialization, plan,
                        GpuRuntimeMethodTestGpuProbeOptions.defaults());
            } finally {
                GpuRuntime.setBackend(previousBackend);
            }

            assertTrue(gpuProbe.gpuProbeReady());
            assertFalse(gpuProbe.gpuProbePassed());
            assertEquals("failed", gpuProbe.status());
            assertEquals(1, gpuProbe.executionFailedCount());
            assertEquals("fixture-gpu-probe-output-mismatch", gpuProbe.firstFailure());
            assertTrue(gpuProbe.diagnostics().get(0).contains("Mismatch at item 1"));
            assertTrue(gpuProbe.toMarkdown().contains("Method test GPU probe plan: failed"));
            assertTrue(gpuProbe.toMarkdown().contains("failure=fixture-gpu-probe-output-mismatch"));
        }
    }

    @Test
    void materializesStructArrayFixtureValuesAndComparesReferenceOutputs() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "struct-array-smoke",
                List.of("fixtures/struct-array-smoke.inputs.json"),
                List.of("fixtures/struct-array-smoke.outputs.json"),
                "abs=1e-5",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-struct-fixtures");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/struct-array-smoke.inputs.json"), """
                {"points":[{"x":1.0,"y":2.0},{"x":3.0,"y":4.0}],"scale":2.0}
                """);
        Files.writeString(root.resolve("fixtures/struct-array-smoke.outputs.json"), """
                {"output":[{"x":2.0,"y":4.0},{"x":6.0,"y":8.0}]}
                """);

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithStructInputScaleOutput("demo.irgpu.properties");
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    plan,
                    classLoader
            );

            assertTrue(bindings.bindingsReady());
            assertEquals(3, bindings.bindings().size());
            assertEquals("struct-array", bindings.bindings().get(0).valueKind());
            assertEquals(2, bindings.bindings().get(0).itemCount());
            assertEquals(List.of("[0].x=1.0", "[0].y=2.0", "[1].x=3.0", "[1].y=4.0"),
                    bindings.bindings().get(0).numericValues());

            GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                    GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings);

            assertTrue(materialization.materializationReady());
            GpuRuntimeMethodTestInvocationMaterialization invocation = materialization.invocations().get(0);
            Object[] invocationArguments = invocation.invocationArguments();
            assertEquals(3, invocationArguments.length);
            StructPoint[] points = (StructPoint[]) invocationArguments[0];
            StructPoint[] output = (StructPoint[]) invocationArguments[2];
            assertEquals(1.0f, points[0].x, 0.0001f);
            assertEquals(4.0f, points[1].y, 0.0001f);
            assertEquals(0.0f, output[0].x, 0.0001f);
            assertEquals("java-struct-array", invocation.arguments().get(0).argumentKind());
            assertEquals("zero-filled-struct-array", invocation.arguments().get(2).argumentKind());
            assertEquals("java-struct-array", invocation.arguments().get(2).expectedOutputKind());
            assertEquals(2, invocation.arguments().get(2).expectedOutputItemCount());

            GpuRuntimeMethodTestReferenceComparisonPlan referenceComparison = GpuRuntimeMethodTestProbes.compareWithReference(
                    materialization,
                    plan,
                    referenceArguments -> {
                        StructPoint[] input = (StructPoint[]) referenceArguments[0];
                        float scale = (Float) referenceArguments[1];
                        StructPoint[] actualOutput = (StructPoint[]) referenceArguments[2];
                        for (int index = 0; index < input.length; index++) {
                            actualOutput[index].x = input[index].x * scale;
                            actualOutput[index].y = input[index].y * scale;
                        }
                    }
            );

            assertTrue(referenceComparison.referenceComparisonReady());
            assertTrue(referenceComparison.referenceComparisonPassed());
            assertEquals("java-struct-array", referenceComparison.comparisons().get(0).actualKind());
            assertEquals("[0].x=2.0", referenceComparison.comparisons().get(0).actualNumericValues().get(0));
            assertEquals("[1].y=8.0", referenceComparison.comparisons().get(0).expectedNumericValues().get(3));
        }
    }

    @Test
    void blocksStructFixtureWhenStructContainsArrayField() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "struct-array-field-blocked",
                List.of("fixtures/struct-array-field-blocked.inputs.json"),
                List.of("fixtures/struct-array-field-blocked.outputs.json"),
                "",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-struct-array-field-fixtures");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/struct-array-field-blocked.inputs.json"), """
                {"blob":{"values":[1.0,2.0]}}
                """);
        Files.writeString(root.resolve("fixtures/struct-array-field-blocked.outputs.json"), """
                {"output":[1.0]}
                """);

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithUnsupportedStructInput("demo.irgpu.properties");
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    plan,
                    classLoader
            );

            assertFalse(bindings.bindingsReady());
            assertEquals("fixture-value-struct-field-type-unsupported", bindings.firstBlocker());
            assertEquals("fixture-value-struct-field-type-unsupported", bindings.bindings().get(0).blocker());
        }
    }

    @Test
    void blocksGpuProbeWhenInferredLaunchSizeExceedsBound() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "gpu-probe-too-large",
                List.of("fixtures/gpu-probe-too-large.inputs.json"),
                List.of("fixtures/gpu-probe-too-large.outputs.json"),
                "",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-gpu-probe-too-large");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/gpu-probe-too-large.inputs.json"), "{\"input\":[1.0,2.0],\"scale\":2.5}");
        Files.writeString(root.resolve("fixtures/gpu-probe-too-large.outputs.json"), "{\"output\":[2.5,5.0]}");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithInputScaleOutput("demo.irgpu.properties");
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    plan,
                    classLoader
            );
            GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                    GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings);
            GpuRuntimeMethodTestGpuProbeOptions options = GpuRuntimeMethodTestGpuProbeOptions.defaults()
                    .withMaxGlobalWorkItems(1L);

            GpuRuntimeMethodTestGpuProbePlan gpuProbe = GpuRuntimeMethodTestProbes.executeGpuProbe(
                    descriptor,
                    materialization,
                    plan,
                    options
            );

            assertFalse(gpuProbe.gpuProbeReady());
            assertEquals("blocked", gpuProbe.status());
            assertEquals("fixture-gpu-probe-launch-size-exceeds-limit", gpuProbe.firstBlocker());
            assertTrue(gpuProbe.toMarkdown().contains("First blocker: fixture-gpu-probe-launch-size-exceeds-limit"));
        }
    }

    @Test
    void publishesLifecycleEventsForMethodTestReferenceGpuProbeAndCache() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "lifecycle-smoke",
                List.of("fixtures/lifecycle-smoke.inputs.json"),
                List.of("fixtures/lifecycle-smoke.outputs.json"),
                "abs=1e-5",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-lifecycle");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/lifecycle-smoke.inputs.json"), "{\"input\":[1.0,2.0],\"scale\":2.5}");
        Files.writeString(root.resolve("fixtures/lifecycle-smoke.outputs.json"), "{\"output\":[2.5,5.0]}");
        ArrayList<GpuRuntimeLifecycleEvent> events = new ArrayList<>();
        GpuRuntimeLifecycleEventBus lifecycleBus = GpuRuntimeLifecycleEventBus.of(List.of(events::add));

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithInputScaleOutput("demo.irgpu.properties");
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact, lifecycleBus);
            GpuRuntimeMethodTestFixtureReadiness readiness = GpuRuntimeMethodTestProbes.fixtureReadiness(
                    plan,
                    classLoader,
                    lifecycleBus
            );
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    plan,
                    classLoader,
                    lifecycleBus
            );
            GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                    GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings, lifecycleBus);
            GpuRuntimeMethodTestReferenceComparisonPlan referenceComparison = GpuRuntimeMethodTestProbes.compareWithReference(
                    materialization,
                    plan,
                    referenceArguments -> {
                        float[] input = (float[]) referenceArguments[0];
                        float scale = (Float) referenceArguments[1];
                        float[] output = (float[]) referenceArguments[2];
                        for (int index = 0; index < input.length; index++) {
                            output[index] = input[index] * scale;
                        }
                    },
                    lifecycleBus
            );

            GpuRuntimeBackend previousBackend = GpuRuntime.backend();
            java.util.concurrent.atomic.AtomicInteger backendCalls = new java.util.concurrent.atomic.AtomicInteger();
            GpuRuntime.setBackend(invocationRequest -> {
                backendCalls.incrementAndGet();
                float[] input = (float[]) invocationRequest.arguments()[0];
                float scale = (Float) invocationRequest.arguments()[1];
                float[] output = (float[]) invocationRequest.arguments()[2];
                for (int index = 0; index < input.length; index++) {
                    output[index] = input[index] * scale;
                }
            });
            GpuRuntimeMethodTestGpuProbePlan firstProbe;
            GpuRuntimeMethodTestGpuProbePlan secondProbe;
            try {
                GpuRuntimeMethodTestGpuProbeOptions cachedOptions = GpuRuntimeMethodTestGpuProbeOptions.defaults()
                        .withCache(new GpuRuntimeMethodTestGpuProbeCache());
                firstProbe = GpuRuntimeMethodTestProbes.executeGpuProbe(
                        descriptor,
                        materialization,
                        plan,
                        cachedOptions,
                        lifecycleBus
                );
                secondProbe = GpuRuntimeMethodTestProbes.executeGpuProbe(
                        descriptor,
                        materialization,
                        plan,
                        cachedOptions,
                        lifecycleBus
                );
            } finally {
                GpuRuntime.setBackend(previousBackend);
            }

            assertTrue(readiness.fixtureResourcesReady());
            assertTrue(bindings.bindingsReady());
            assertTrue(materialization.materializationReady());
            assertTrue(referenceComparison.referenceComparisonPassed());
            assertTrue(firstProbe.gpuProbePassed());
            assertTrue(secondProbe.gpuProbePassed());
            assertEquals(1, backendCalls.get());

            List<GpuRuntimeLifecycleEventKind> kinds = events.stream().map(GpuRuntimeLifecycleEvent::kind).toList();
            assertTrue(kinds.contains(GpuRuntimeLifecycleEventKind.METHOD_TEST_METADATA_STARTED));
            assertTrue(kinds.contains(GpuRuntimeLifecycleEventKind.METHOD_TEST_METADATA_COMPLETED));
            assertTrue(kinds.contains(GpuRuntimeLifecycleEventKind.METHOD_TEST_FIXTURE_READINESS_COMPLETED));
            assertTrue(kinds.contains(GpuRuntimeLifecycleEventKind.METHOD_TEST_VALUE_BINDING_COMPLETED));
            assertTrue(kinds.contains(GpuRuntimeLifecycleEventKind.METHOD_TEST_INVOCATION_MATERIALIZATION_COMPLETED));
            assertTrue(kinds.contains(GpuRuntimeLifecycleEventKind.METHOD_TEST_REFERENCE_COMPARISON_COMPLETED));
            assertTrue(kinds.contains(GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_COMPLETED));
            assertEquals(
                    2,
                    kinds.stream()
                            .filter(kind -> kind == GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_CACHE_LOOKUP_COMPLETED)
                            .count()
            );

            List<GpuRuntimeLifecycleEvent> cacheCompletedEvents = events.stream()
                    .filter(event -> event.kind() == GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_CACHE_LOOKUP_COMPLETED)
                    .toList();
            assertEquals("false", cacheCompletedEvents.get(0).fields().get("cache.hit"));
            assertEquals("true", cacheCompletedEvents.get(1).fields().get("cache.hit"));
            assertEquals(
                    cacheCompletedEvents.get(0).fields().get("evidenceKey.stableHash"),
                    cacheCompletedEvents.get(1).fields().get("evidenceKey.stableHash")
            );
            assertEquals("passed", lastEvent(events, GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_COMPLETED)
                    .fields().get("status"));
            assertEquals("passed", lastEvent(events, GpuRuntimeLifecycleEventKind.METHOD_TEST_REFERENCE_COMPARISON_COMPLETED)
                    .fields().get("status"));
        }
    }

    @Test
    void persistsGpuProbeCacheAcrossCacheInstances() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "persistent-cache-smoke",
                List.of("fixtures/persistent-cache-smoke.inputs.json"),
                List.of("fixtures/persistent-cache-smoke.outputs.json"),
                "abs=1e-5",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-persistent-cache-fixtures");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/persistent-cache-smoke.inputs.json"), "{\"input\":[1.0,2.0],\"scale\":2.5}");
        Files.writeString(root.resolve("fixtures/persistent-cache-smoke.outputs.json"), "{\"output\":[2.5,5.0]}");
        Path cacheDirectory = Files.createTempDirectory("javatogpu-method-test-persistent-cache");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithInputScaleOutput("demo.irgpu.properties");
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    plan,
                    classLoader
            );
            GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                    GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings);

            GpuRuntimeBackend previousBackend = GpuRuntime.backend();
            java.util.concurrent.atomic.AtomicInteger backendCalls = new java.util.concurrent.atomic.AtomicInteger();
            GpuRuntime.setBackend(invocationRequest -> {
                backendCalls.incrementAndGet();
                float[] input = (float[]) invocationRequest.arguments()[0];
                float scale = (Float) invocationRequest.arguments()[1];
                float[] output = (float[]) invocationRequest.arguments()[2];
                for (int index = 0; index < input.length; index++) {
                    output[index] = input[index] * scale;
                }
            });
            GpuRuntimeMethodTestGpuProbePlan firstProbe;
            GpuRuntimeMethodTestGpuProbePlan secondProbe;
            try {
                firstProbe = GpuRuntimeMethodTestProbes.executeGpuProbe(
                        descriptor,
                        materialization,
                        plan,
                        GpuRuntimeMethodTestGpuProbeOptions.persistentCached(cacheDirectory)
                );
                secondProbe = GpuRuntimeMethodTestProbes.executeGpuProbe(
                        descriptor,
                        materialization,
                        plan,
                        GpuRuntimeMethodTestGpuProbeOptions.persistentCached(cacheDirectory)
                );
            } finally {
                GpuRuntime.setBackend(previousBackend);
            }

            String stableHash = firstProbe.executions().get(0).evidenceKey().stableHash();
            assertEquals(1, backendCalls.get());
            assertTrue(firstProbe.gpuProbePassed());
            assertTrue(secondProbe.gpuProbePassed());
            assertFalse(firstProbe.executions().get(0).cacheHit());
            assertTrue(secondProbe.executions().get(0).cacheHit());
            assertEquals(stableHash, secondProbe.executions().get(0).evidenceKey().stableHash());
            assertEquals(1, secondProbe.comparisonPassedCount());
            assertTrue(Files.isRegularFile(cacheDirectory.resolve(stableHash + ".properties")));
        }
    }

    @Test
    void warmsPersistentSelectionProbeEvidenceBeforeCacheOnlyRanking() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "warmup-cache-smoke",
                List.of("fixtures/warmup-cache-smoke.inputs.json"),
                List.of("fixtures/warmup-cache-smoke.outputs.json"),
                "abs=1e-5",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-warmup-fixtures");
        Files.writeString(root.resolve("demo.irgpu.properties"), IrGpuArtifactSerializer.serialize(artifact));
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/warmup-cache-smoke.inputs.json"), "{\"input\":[1.0,2.0],\"scale\":2.5}");
        Files.writeString(root.resolve("fixtures/warmup-cache-smoke.outputs.json"), "{\"output\":[2.5,5.0]}");
        Path cacheDirectory = Files.createTempDirectory("javatogpu-method-test-warmup-cache");
        ArrayList<GpuRuntimeLifecycleEvent> events = new ArrayList<>();
        GpuRuntimeLifecycleEventBus lifecycleBus = GpuRuntimeLifecycleEventBus.of(List.of(events::add));

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithInputScaleOutput("demo.irgpu.properties");
            GpuRuntimeDeviceProfile integrated = syntheticOpenClDevice(
                    "opencl-igpu",
                    "Intel Integrated",
                    "Intel",
                    GpuDeviceClassTarget.IGPU,
                    8,
                    true
            );
            GpuRuntimeDeviceProfile discrete = syntheticOpenClDevice(
                    "opencl-dgpu",
                    "NVIDIA RTX",
                    "NVIDIA",
                    GpuDeviceClassTarget.DGPU,
                    48,
                    false
            );
            GpuRuntimeCompileOptions baseOptions = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                    .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.DISABLED);
            java.util.concurrent.atomic.AtomicInteger backendCalls = new java.util.concurrent.atomic.AtomicInteger();
            GpuRuntimeBackend referenceBackend = invocationRequest -> {
                backendCalls.incrementAndGet();
                float[] input = (float[]) invocationRequest.arguments()[0];
                float scale = (Float) invocationRequest.arguments()[1];
                float[] output = (float[]) invocationRequest.arguments()[2];
                for (int index = 0; index < input.length; index++) {
                    output[index] = input[index] * scale;
                }
            };

            GpuRuntimeMethodTestProbeEvidenceWarmupPlan warmup =
                    GpuRuntimeMethodTestProbeEvidenceWarmup.warmSelectionProbeEvidence(
                            descriptor,
                            classLoader,
                            List.of(GpuRuntimeMethodTestProbeEvidenceWarmupCandidate.borrowed(integrated, referenceBackend)),
                            GpuRuntimeMethodTestGpuProbeOptions.persistentCached(cacheDirectory).withCompileOptions(baseOptions),
                            lifecycleBus
                    );
            GpuRuntimeMethodTestGpuProbeExecution execution = warmup.candidateResults().get(0)
                    .gpuProbePlan()
                    .executions()
                    .get(0);
            ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
            GpuRuntimeDeviceSelection selection;
            Thread.currentThread().setContextClassLoader(classLoader);
            try {
                selection = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                        new GpuRuntimeDevicePolicyContext(
                                descriptor,
                                baseOptions.withPersistentMethodTestProbeEvidenceRanking(cacheDirectory),
                                List.of(integrated, discrete),
                                java.util.Optional.of(artifact)
                        )
                );
            } finally {
                Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            }

            assertTrue(warmup.warmupPassed());
            assertEquals("passed", warmup.status());
            assertEquals(1, warmup.candidatePassedCount());
            assertEquals(1, backendCalls.get());
            assertFalse(execution.cacheHit());
            assertTrue(Files.isRegularFile(cacheDirectory.resolve(execution.evidenceKey().stableHash() + ".properties")));
            assertEquals(integrated, selection.selectedDevice().orElseThrow());
            assertTrue(warmup.toMarkdown().contains("Method test probe evidence warm-up: passed"));
            assertEquals("passed", warmup.artifactFields("warmup").get("warmup.status"));

            List<GpuRuntimeLifecycleEventKind> kinds = events.stream().map(GpuRuntimeLifecycleEvent::kind).toList();
            assertTrue(kinds.contains(GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_WARMUP_STARTED));
            assertTrue(kinds.contains(GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_WARMUP_COMPLETED));
            assertEquals("passed", lastEvent(events, GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_WARMUP_COMPLETED)
                    .fields().get("status"));
        }
    }

    @Test
    void blocksFixtureValueBindingWhenJsonShapeDoesNotMatchDescriptorType() throws Exception {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "shape-mismatch",
                List.of("fixtures/shape-mismatch.inputs.json"),
                List.of("fixtures/shape-mismatch.outputs.json"),
                "",
                List.of("selection"),
                true,
                "GPUTest"
        )));
        Path root = Files.createTempDirectory("javatogpu-method-test-value-binding-shape");
        Files.createDirectories(root.resolve("fixtures"));
        Files.writeString(root.resolve("fixtures/shape-mismatch.inputs.json"), "{\"input\":1.0}");
        Files.writeString(root.resolve("fixtures/shape-mismatch.outputs.json"), "{\"output\":[2.0]}");

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()})) {
            GpuKernelDescriptor descriptor = descriptorWithInputOutput("demo.irgpu.properties");
            GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor, artifact);
            GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                    descriptor,
                    plan,
                    classLoader
            );

            assertFalse(bindings.bindingsReady());
            assertEquals("fixture-value-shape-mismatch", bindings.firstBlocker());
            assertEquals(2, bindings.bindings().size());
            assertFalse(bindings.bindings().get(0).bindingReady());
            assertEquals("fixture-value-shape-mismatch", bindings.bindings().get(0).blocker());
            assertTrue(bindings.bindings().get(1).bindingReady());
            assertTrue(bindings.toMarkdown().contains("First blocker: fixture-value-shape-mismatch"));

            GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                    GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings);

            assertFalse(materialization.materializationReady());
            assertEquals("fixture-value-shape-mismatch", materialization.firstBlocker());
            assertEquals(1, materialization.invocations().size());
            assertFalse(materialization.invocations().get(0).invocationReady());
            assertEquals(0, materialization.invocations().get(0).invocationArguments().length);
            assertEquals("fixture-value-shape-mismatch", materialization.invocations().get(0).arguments().get(0).blocker());
        }
    }

    @Test
    void blocksSelectionProbeReadinessWithoutExpectedOutputs() {
        IrGpuArtifact artifact = artifact(List.of(new IrGpuMethodTestVectorMetadata(
                "kernel",
                "jtg_kernel",
                "no-expected-output",
                List.of("fixtures/input.json"),
                List.of(),
                "",
                List.of("selection"),
                true,
                "GPUTest"
        )));

        GpuRuntimeMethodTestProbePlan plan = GpuRuntimeMethodTestProbes.plan(descriptor("demo.irgpu.properties"), artifact);
        GpuRuntimeMethodTestFixtureReadiness readiness = GpuRuntimeMethodTestProbes.fixtureReadiness(plan);

        assertFalse(readiness.fixtureResourcesReady());
        assertFalse(readiness.selectionProbeResourcesReady());
        assertEquals("expected-output-fixture-ref-missing", readiness.firstBlocker());
        assertTrue(readiness.diagnostics().get(0).contains("no expected output fixture refs"));
    }

    private static GpuKernelDescriptor descriptor(String irGpuResource) {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel(__global float* output) { output[0] = 1.0f; }",
                irGpuResource,
                List.of(new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static GpuKernelDescriptor descriptorWithInputOutput(String irGpuResource) {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel(__global const float* input, __global float* output) { output[0] = input[0]; }",
                irGpuResource,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static GpuKernelDescriptor descriptorWithInputScaleOutput(String irGpuResource) {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel(__global const float* input, float scale, __global float* output) { output[0] = input[0] * scale; }",
                irGpuResource,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static GpuKernelDescriptor descriptorWithStructInputScaleOutput(String irGpuResource) {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel(__global const StructPoint* points, float scale, __global StructPoint* output) { }",
                irGpuResource,
                List.of(
                        new GpuKernelParameterDescriptor("points", StructPoint.class.getName() + "[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", StructPoint.class.getName() + "[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static GpuKernelDescriptor descriptorWithUnsupportedStructInput(String irGpuResource) {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel(UnsupportedStructBlob blob, __global float* output) { }",
                irGpuResource,
                List.of(
                        new GpuKernelParameterDescriptor("blob", UnsupportedStructBlob.class.getName(), GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static IrGpuArtifact artifact(List<IrGpuMethodTestVectorMetadata> testVectors) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "jtg_kernel", List.of(), List.of(), List.of()),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        ).withMethodTestVectors(testVectors);
    }

    private static GpuRuntimeDeviceProfile syntheticOpenClDevice(
            String deviceId,
            String label,
            String vendor,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            boolean unifiedMemory
    ) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL synthetic",
                deviceId,
                label,
                vendor,
                "example-driver",
                "OpenCL 3.0 Example",
                deviceClass,
                computeUnits,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                unifiedMemory,
                true,
                true,
                false
        );
    }

    private static GpuRuntimeLifecycleEvent lastEvent(
            List<GpuRuntimeLifecycleEvent> events,
            GpuRuntimeLifecycleEventKind kind
    ) {
        for (int index = events.size() - 1; index >= 0; index--) {
            GpuRuntimeLifecycleEvent event = events.get(index);
            if (event.kind() == kind) {
                return event;
            }
        }
        throw new AssertionError("Missing lifecycle event: " + kind);
    }

    @GPUStruct
    public static class StructPoint {
        public float x;
        public float y;

        public StructPoint() {
        }
    }

    @GPUStruct
    public static class UnsupportedStructBlob {
        public float[] values;

        public UnsupportedStructBlob() {
        }
    }
}
