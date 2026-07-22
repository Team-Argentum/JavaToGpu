package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuAnnotationSupport;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Public helper for inspecting method-level {@code @GPUTest} metadata at runtime.
 */
public final class GpuRuntimeMethodTestProbes {

    private GpuRuntimeMethodTestProbes() {
    }

    public static GpuRuntimeMethodTestProbePlan plan(GpuKernelDescriptor descriptor) {
        return plan(descriptor, (ClassLoader) null, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestProbePlan plan(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader
    ) {
        return plan(descriptor, preferredClassLoader, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestProbePlan plan(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_METADATA_STARTED,
                descriptor,
                null,
                "@GPUTest metadata probe started",
                Map.of("status", "started")
        );
        GpuRuntimeMethodTestProbePlan result = planInternal(descriptor, preferredClassLoader);
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_METADATA_COMPLETED,
                result.kernelName(),
                result.kernelResource(),
                result.irGpuResource(),
                null,
                "@GPUTest metadata probe completed",
                metadataFields(result)
        );
        return result;
    }

    private static GpuRuntimeMethodTestProbePlan planInternal(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader
    ) {
        if (descriptor == null) {
            return blocked(null, false, List.of("kernel-descriptor-missing"), List.of());
        }
        if (descriptor.irGpuResource() == null || descriptor.irGpuResource().isBlank()) {
            return blocked(descriptor, false, List.of("irgpu-resource-missing"), List.of(
                    "Generated descriptor does not reference an IrGpu artifact, so @GPUTest metadata cannot be loaded"
            ));
        }

        Optional<IrGpuArtifact> artifact;
        try {
            artifact = GpuRuntimeIrArtifactLoader.load(descriptor, preferredClassLoader);
        } catch (RuntimeException exception) {
            return blocked(descriptor, false, List.of("irgpu-artifact-load-failed"), List.of(
                    exception.getClass().getSimpleName() + ": " + normalize(exception.getMessage(), "failed to load IrGpu artifact")
            ));
        }
        if (artifact.isEmpty()) {
            return blocked(descriptor, false, List.of("irgpu-artifact-not-found"), List.of(
                    "IrGpu artifact resource was not found on the provided, context, or runtime classloaders: "
                            + descriptor.irGpuResource()
            ));
        }
        return planInternal(descriptor, artifact.orElseThrow());
    }

    public static GpuRuntimeMethodTestProbePlan plan(
            GpuKernelDescriptor descriptor,
            IrGpuArtifact artifact
    ) {
        return plan(descriptor, artifact, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestProbePlan plan(
            GpuKernelDescriptor descriptor,
            IrGpuArtifact artifact,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_METADATA_STARTED,
                descriptor,
                null,
                "@GPUTest metadata probe started",
                Map.of("status", "started", "artifact.source", "direct")
        );
        GpuRuntimeMethodTestProbePlan result = planInternal(descriptor, artifact);
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_METADATA_COMPLETED,
                result.kernelName(),
                result.kernelResource(),
                result.irGpuResource(),
                null,
                "@GPUTest metadata probe completed",
                metadataFields(result)
        );
        return result;
    }

    private static GpuRuntimeMethodTestProbePlan planInternal(
            GpuKernelDescriptor descriptor,
            IrGpuArtifact artifact
    ) {
        if (descriptor == null) {
            return blocked(null, artifact != null, List.of("kernel-descriptor-missing"), List.of());
        }
        if (artifact == null) {
            return blocked(descriptor, false, List.of("irgpu-artifact-missing"), List.of());
        }

        List<GpuRuntimeMethodTestVectorPlan> vectors = artifact.entryTestVectors().stream()
                .map(GpuRuntimeMethodTestVectorPlan::from)
                .toList();
        ArrayList<String> blockers = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        if (vectors.isEmpty()) {
            blockers.add("method-test-vectors-missing");
            diagnostics.add("IrGpu artifact loaded, but no @GPUTest metadata matched the descriptor entry method");
        } else if (vectors.stream().noneMatch(GpuRuntimeMethodTestVectorPlan::selectionProbe)) {
            blockers.add("selection-probe-vectors-missing");
            diagnostics.add("@GPUTest metadata exists, but every vector has selectionProbe=false");
        }
        return new GpuRuntimeMethodTestProbePlan(
                descriptor.kernelName(),
                descriptor.kernelResource(),
                descriptor.irGpuResource(),
                true,
                vectors,
                blockers,
                diagnostics
        );
    }

    public static GpuRuntimeMethodTestFixtureReadiness fixtureReadiness(GpuKernelDescriptor descriptor) {
        return fixtureReadiness(descriptor, null, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestFixtureReadiness fixtureReadiness(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader
    ) {
        return fixtureReadiness(descriptor, preferredClassLoader, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestFixtureReadiness fixtureReadiness(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        return fixtureReadiness(plan(descriptor, preferredClassLoader, eventBus), preferredClassLoader, eventBus);
    }

    public static GpuRuntimeMethodTestFixtureReadiness fixtureReadiness(
            GpuRuntimeMethodTestProbePlan plan
    ) {
        return fixtureReadiness(plan, null, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestFixtureReadiness fixtureReadiness(
            GpuRuntimeMethodTestProbePlan plan,
            ClassLoader preferredClassLoader
    ) {
        return fixtureReadiness(plan, preferredClassLoader, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestFixtureReadiness fixtureReadiness(
            GpuRuntimeMethodTestProbePlan plan,
            ClassLoader preferredClassLoader,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        GpuRuntimeMethodTestProbePlan eventPlan = plan == null
                ? blocked(null, false, List.of("method-test-probe-plan-missing"), List.of())
                : plan;
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_FIXTURE_READINESS_STARTED,
                eventPlan.kernelName(),
                eventPlan.kernelResource(),
                eventPlan.irGpuResource(),
                null,
                "@GPUTest fixture readiness probe started",
                Map.of("status", "started")
        );
        GpuRuntimeMethodTestFixtureReadiness result = fixtureReadinessInternal(plan, preferredClassLoader);
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_FIXTURE_READINESS_COMPLETED,
                result.probePlan().kernelName(),
                result.probePlan().kernelResource(),
                result.probePlan().irGpuResource(),
                null,
                "@GPUTest fixture readiness probe completed",
                fixtureReadinessFields(result)
        );
        return result;
    }

    private static GpuRuntimeMethodTestFixtureReadiness fixtureReadinessInternal(
            GpuRuntimeMethodTestProbePlan plan,
            ClassLoader preferredClassLoader
    ) {
        GpuRuntimeMethodTestProbePlan resolvedPlan = plan == null
                ? blocked(null, false, List.of("method-test-probe-plan-missing"), List.of())
                : plan;
        ArrayList<GpuRuntimeMethodTestFixtureResourceStatus> resources = new ArrayList<>();
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        ArrayList<String> diagnostics = new ArrayList<>(resolvedPlan.diagnostics());

        if (!"none".equals(resolvedPlan.firstBlocker())) {
            blockers.add(resolvedPlan.firstBlocker());
        }
        if (resolvedPlan.testVectors().isEmpty()) {
            blockers.add("method-test-vectors-missing");
            diagnostics.add("No @GPUTest vectors are available for fixture resource lookup");
        }

        for (GpuRuntimeMethodTestVectorPlan vector : resolvedPlan.testVectors()) {
            if (vector.expectedOutputRefs().isEmpty()) {
                blockers.add("expected-output-fixture-ref-missing");
                diagnostics.add("Test vector " + vector.testId() + " has no expected output fixture refs");
            }
            addResourceStatuses(resources, blockers, diagnostics, vector, "input", vector.inputRefs(), preferredClassLoader);
            addResourceStatuses(resources, blockers, diagnostics, vector, "expectedOutput", vector.expectedOutputRefs(), preferredClassLoader);
        }

        if (resolvedPlan.hasSelectionProbes() && resolvedPlan.selectionProbeVectors().stream()
                .noneMatch(vector -> selectionVectorResourcesReady(vector, resources))) {
            blockers.add("selection-probe-fixture-resources-not-ready");
        }

        return new GpuRuntimeMethodTestFixtureReadiness(
                resolvedPlan,
                resources,
                List.copyOf(blockers),
                diagnostics
        );
    }

    public static GpuRuntimeMethodTestFixtureValueBindingPlan fixtureValueBindings(GpuKernelDescriptor descriptor) {
        return fixtureValueBindings(descriptor, null, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestFixtureValueBindingPlan fixtureValueBindings(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader
    ) {
        return fixtureValueBindings(descriptor, preferredClassLoader, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestFixtureValueBindingPlan fixtureValueBindings(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        return fixtureValueBindings(descriptor, plan(descriptor, preferredClassLoader, eventBus), preferredClassLoader, eventBus);
    }

    public static GpuRuntimeMethodTestFixtureValueBindingPlan fixtureValueBindings(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestProbePlan plan,
            ClassLoader preferredClassLoader
    ) {
        return fixtureValueBindings(descriptor, plan, preferredClassLoader, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestFixtureValueBindingPlan fixtureValueBindings(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestProbePlan plan,
            ClassLoader preferredClassLoader,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_VALUE_BINDING_STARTED,
                descriptor,
                null,
                "@GPUTest fixture value binding started",
                Map.of("status", "started")
        );
        GpuRuntimeMethodTestFixtureValueBindingPlan result = fixtureValueBindingsInternal(
                descriptor,
                plan,
                preferredClassLoader
        );
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_VALUE_BINDING_COMPLETED,
                result.kernelName(),
                "",
                result.irGpuResource(),
                null,
                "@GPUTest fixture value binding completed",
                valueBindingFields(result)
        );
        return result;
    }

    private static GpuRuntimeMethodTestFixtureValueBindingPlan fixtureValueBindingsInternal(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestProbePlan plan,
            ClassLoader preferredClassLoader
    ) {
        if (descriptor == null) {
            return new GpuRuntimeMethodTestFixtureValueBindingPlan(
                    "unknown",
                    "",
                    List.of(),
                    List.of("kernel-descriptor-missing"),
                    List.of()
            );
        }

        GpuRuntimeMethodTestProbePlan resolvedPlan = plan == null
                ? blocked(descriptor, false, List.of("method-test-probe-plan-missing"), List.of())
                : plan;
        ArrayList<GpuRuntimeMethodTestFixtureValueBinding> bindings = new ArrayList<>();
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        ArrayList<String> diagnostics = new ArrayList<>(resolvedPlan.diagnostics());

        if (!"none".equals(resolvedPlan.firstBlocker())) {
            blockers.add(resolvedPlan.firstBlocker());
        }
        if (resolvedPlan.testVectors().isEmpty()) {
            blockers.add("method-test-vectors-missing");
            diagnostics.add("No @GPUTest vectors are available for fixture value binding");
        }
        List<GpuKernelParameterDescriptor> parameters = descriptor.parameterDescriptors() == null
                ? List.of()
                : descriptor.parameterDescriptors();
        if (parameters.isEmpty()) {
            blockers.add("kernel-parameters-missing");
            diagnostics.add("Generated descriptor has no kernel parameter descriptors for fixture value binding");
        }

        for (GpuRuntimeMethodTestVectorPlan vector : resolvedPlan.testVectors()) {
            addValueBindings(bindings, blockers, diagnostics, vector, "input", vector.inputRefs(), parameters, preferredClassLoader);
            addValueBindings(bindings, blockers, diagnostics, vector, "expectedOutput", vector.expectedOutputRefs(), parameters, preferredClassLoader);
        }

        if (bindings.isEmpty() && !resolvedPlan.testVectors().isEmpty()) {
            blockers.add("fixture-value-bindings-missing");
            diagnostics.add("No supported fixture value bindings were produced for @GPUTest vectors");
        }

        return new GpuRuntimeMethodTestFixtureValueBindingPlan(
                descriptor.kernelName(),
                descriptor.irGpuResource(),
                bindings,
                List.copyOf(blockers),
                diagnostics
        );
    }

    public static GpuRuntimeMethodTestInvocationMaterializationPlan fixtureInvocationMaterialization(
            GpuKernelDescriptor descriptor
    ) {
        return fixtureInvocationMaterialization(descriptor, (ClassLoader) null, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestInvocationMaterializationPlan fixtureInvocationMaterialization(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader
    ) {
        return fixtureInvocationMaterialization(descriptor, preferredClassLoader, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestInvocationMaterializationPlan fixtureInvocationMaterialization(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        return fixtureInvocationMaterialization(
                descriptor,
                fixtureValueBindings(descriptor, preferredClassLoader, eventBus),
                eventBus
        );
    }

    public static GpuRuntimeMethodTestInvocationMaterializationPlan fixtureInvocationMaterialization(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestFixtureValueBindingPlan bindingPlan
    ) {
        return fixtureInvocationMaterialization(descriptor, bindingPlan, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestInvocationMaterializationPlan fixtureInvocationMaterialization(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestFixtureValueBindingPlan bindingPlan,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_INVOCATION_MATERIALIZATION_STARTED,
                descriptor,
                null,
                "@GPUTest invocation materialization started",
                Map.of("status", "started")
        );
        GpuRuntimeMethodTestInvocationMaterializationPlan result = fixtureInvocationMaterializationInternal(
                descriptor,
                bindingPlan
        );
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_INVOCATION_MATERIALIZATION_COMPLETED,
                result.kernelName(),
                "",
                result.irGpuResource(),
                null,
                "@GPUTest invocation materialization completed",
                materializationFields(result)
        );
        return result;
    }

    private static GpuRuntimeMethodTestInvocationMaterializationPlan fixtureInvocationMaterializationInternal(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestFixtureValueBindingPlan bindingPlan
    ) {
        if (descriptor == null) {
            return new GpuRuntimeMethodTestInvocationMaterializationPlan(
                    "unknown",
                    "",
                    List.of(),
                    List.of("kernel-descriptor-missing"),
                    List.of()
            );
        }
        GpuRuntimeMethodTestFixtureValueBindingPlan resolvedBindingPlan = bindingPlan == null
                ? new GpuRuntimeMethodTestFixtureValueBindingPlan(
                descriptor.kernelName(),
                descriptor.irGpuResource(),
                List.of(),
                List.of("fixture-value-binding-plan-missing"),
                List.of()
        )
                : bindingPlan;
        List<GpuKernelParameterDescriptor> parameters = descriptor.parameterDescriptors() == null
                ? List.of()
                : descriptor.parameterDescriptors();
        LinkedHashSet<String> blockers = new LinkedHashSet<>(resolvedBindingPlan.blockers());
        ArrayList<String> diagnostics = new ArrayList<>(resolvedBindingPlan.diagnostics());
        if (parameters.isEmpty()) {
            blockers.add("kernel-parameters-missing");
            diagnostics.add("Generated descriptor has no kernel parameter descriptors for fixture invocation materialization");
        }

        LinkedHashMap<String, List<GpuRuntimeMethodTestFixtureValueBinding>> bindingsByTestId = bindingsByTestId(
                resolvedBindingPlan.bindings()
        );
        ArrayList<GpuRuntimeMethodTestInvocationMaterialization> invocations = new ArrayList<>();
        for (Map.Entry<String, List<GpuRuntimeMethodTestFixtureValueBinding>> entry : bindingsByTestId.entrySet()) {
            invocations.add(materializeInvocation(entry.getKey(), entry.getValue(), parameters));
        }
        if (invocations.isEmpty()) {
            blockers.add("fixture-invocation-materialization-missing");
            diagnostics.add("No fixture value bindings were available for invocation argument materialization");
        }
        for (GpuRuntimeMethodTestInvocationMaterialization invocation : invocations) {
            blockers.addAll(invocation.blockers());
            diagnostics.addAll(invocation.diagnostics());
        }

        return new GpuRuntimeMethodTestInvocationMaterializationPlan(
                descriptor.kernelName(),
                descriptor.irGpuResource(),
                invocations,
                List.copyOf(blockers),
                diagnostics
        );
    }

    public static GpuRuntimeMethodTestReferenceComparisonPlan compareWithReference(
            GpuRuntimeMethodTestInvocationMaterializationPlan materializationPlan,
            GpuRuntimeMethodTestReferenceInvoker referenceInvoker
    ) {
        return compareWithReference(materializationPlan, null, referenceInvoker, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestReferenceComparisonPlan compareWithReference(
            GpuRuntimeMethodTestInvocationMaterializationPlan materializationPlan,
            GpuRuntimeMethodTestProbePlan probePlan,
            GpuRuntimeMethodTestReferenceInvoker referenceInvoker
    ) {
        return compareWithReference(materializationPlan, probePlan, referenceInvoker, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestReferenceComparisonPlan compareWithReference(
            GpuRuntimeMethodTestInvocationMaterializationPlan materializationPlan,
            GpuRuntimeMethodTestProbePlan probePlan,
            GpuRuntimeMethodTestReferenceInvoker referenceInvoker,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_REFERENCE_COMPARISON_STARTED,
                materializationPlan == null ? "unknown" : materializationPlan.kernelName(),
                "",
                materializationPlan == null ? "" : materializationPlan.irGpuResource(),
                null,
                "@GPUTest reference comparison started",
                Map.of("status", "started")
        );
        GpuRuntimeMethodTestReferenceComparisonPlan result = compareWithReferenceInternal(
                materializationPlan,
                probePlan,
                referenceInvoker
        );
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_REFERENCE_COMPARISON_COMPLETED,
                result.kernelName(),
                "",
                result.irGpuResource(),
                null,
                "@GPUTest reference comparison completed",
                referenceComparisonFields(result)
        );
        return result;
    }

    private static GpuRuntimeMethodTestReferenceComparisonPlan compareWithReferenceInternal(
            GpuRuntimeMethodTestInvocationMaterializationPlan materializationPlan,
            GpuRuntimeMethodTestProbePlan probePlan,
            GpuRuntimeMethodTestReferenceInvoker referenceInvoker
    ) {
        if (materializationPlan == null) {
            return new GpuRuntimeMethodTestReferenceComparisonPlan(
                    "unknown",
                    "",
                    List.of(),
                    List.of("fixture-invocation-materialization-plan-missing"),
                    List.of()
            );
        }

        LinkedHashSet<String> blockers = new LinkedHashSet<>(materializationPlan.blockers());
        ArrayList<String> diagnostics = new ArrayList<>(materializationPlan.diagnostics());
        ArrayList<GpuRuntimeMethodTestReferenceComparison> comparisons = new ArrayList<>();
        if (referenceInvoker == null) {
            blockers.add("fixture-reference-invoker-missing");
            diagnostics.add("No CPU/reference invoker was supplied for @GPUTest comparison");
        }
        if (!materializationPlan.materializationReady()) {
            String blocker = "none".equals(materializationPlan.firstBlocker())
                    ? "fixture-invocation-materialization-not-ready"
                    : materializationPlan.firstBlocker();
            blockers.add(blocker);
            diagnostics.add("Fixture invocation materialization is not ready for reference comparison: " + blocker);
        }

        Map<String, ReferenceTolerance> tolerances = referenceTolerancesByTestId(probePlan, blockers, diagnostics);
        if (!blockers.isEmpty()) {
            return new GpuRuntimeMethodTestReferenceComparisonPlan(
                    materializationPlan.kernelName(),
                    materializationPlan.irGpuResource(),
                    comparisons,
                    List.copyOf(blockers),
                    diagnostics
            );
        }

        for (GpuRuntimeMethodTestInvocationMaterialization invocation : materializationPlan.invocations()) {
            List<GpuRuntimeMethodTestInvocationArgument> expectedOutputs = invocation.arguments().stream()
                    .filter(GpuRuntimeMethodTestInvocationArgument::expectedOutputReady)
                    .toList();
            if (expectedOutputs.isEmpty()) {
                blockers.add("fixture-reference-expected-output-missing");
                diagnostics.add("Test vector " + invocation.testId()
                        + " has no materialized expected outputs to compare against");
                continue;
            }

            Object[] invocationArguments = invocation.invocationArguments();
            try {
                referenceInvoker.invoke(invocationArguments);
            } catch (Exception exception) {
                blockers.add("fixture-reference-invocation-failed");
                diagnostics.add("Test vector " + invocation.testId()
                        + " reference invoker failed: " + exception.getClass().getSimpleName()
                        + ": " + normalize(exception.getMessage(), "reference invocation failed"));
                continue;
            }

            ReferenceTolerance tolerance = tolerances.getOrDefault(invocation.testId(), ReferenceTolerance.zero());
            for (GpuRuntimeMethodTestInvocationArgument expectedOutput : expectedOutputs) {
                GpuRuntimeMethodTestReferenceComparison comparison = compareOutput(
                        expectedOutput,
                        invocationArguments,
                        tolerance,
                        "fixture-reference",
                        "reference"
                );
                comparisons.add(comparison);
                if (!comparison.comparisonReady() && !"none".equals(comparison.blocker())) {
                    blockers.add(comparison.blocker());
                }
                if (!"none".equals(comparison.diagnostic())) {
                    diagnostics.add(comparison.diagnostic());
                }
            }
        }

        return new GpuRuntimeMethodTestReferenceComparisonPlan(
                materializationPlan.kernelName(),
                materializationPlan.irGpuResource(),
                comparisons,
                List.copyOf(blockers),
                diagnostics
        );
    }

    public static GpuRuntimeMethodTestGpuProbePlan executeGpuProbe(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestInvocationMaterializationPlan materializationPlan
    ) {
        return executeGpuProbe(
                descriptor,
                materializationPlan,
                null,
                GpuRuntimeMethodTestGpuProbeOptions.defaults(),
                defaultLifecycleEventBus()
        );
    }

    public static GpuRuntimeMethodTestGpuProbePlan executeGpuProbe(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestInvocationMaterializationPlan materializationPlan,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return executeGpuProbe(
                descriptor,
                materializationPlan,
                null,
                GpuRuntimeMethodTestGpuProbeOptions.defaults(),
                lifecycleEventBus
        );
    }

    public static GpuRuntimeMethodTestGpuProbePlan executeGpuProbe(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestInvocationMaterializationPlan materializationPlan,
            GpuRuntimeMethodTestProbePlan probePlan,
            GpuRuntimeMethodTestGpuProbeOptions options
    ) {
        return executeGpuProbe(descriptor, materializationPlan, probePlan, options, defaultLifecycleEventBus());
    }

    public static GpuRuntimeMethodTestGpuProbePlan executeGpuProbe(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestInvocationMaterializationPlan materializationPlan,
            GpuRuntimeMethodTestProbePlan probePlan,
            GpuRuntimeMethodTestGpuProbeOptions options,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeMethodTestGpuProbeOptions resolvedOptions = options == null
                ? GpuRuntimeMethodTestGpuProbeOptions.defaults()
                : options;
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_STARTED,
                descriptor,
                resolvedOptions.compileOptions(),
                "@GPUTest GPU probe started",
                gpuProbeStartedFields(resolvedOptions)
        );
        GpuRuntimeMethodTestGpuProbePlan result = executeGpuProbeInternal(
                descriptor,
                materializationPlan,
                probePlan,
                resolvedOptions,
                eventBus
        );
        publishMethodTestEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_COMPLETED,
                descriptor,
                resolvedOptions.compileOptions(),
                "@GPUTest GPU probe completed",
                gpuProbeFields(result, resolvedOptions)
        );
        return result;
    }

    private static GpuRuntimeMethodTestGpuProbePlan executeGpuProbeInternal(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestInvocationMaterializationPlan materializationPlan,
            GpuRuntimeMethodTestProbePlan probePlan,
            GpuRuntimeMethodTestGpuProbeOptions resolvedOptions,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        if (descriptor == null) {
            return new GpuRuntimeMethodTestGpuProbePlan(
                    "unknown",
                    "",
                    List.of(),
                    List.of("kernel-descriptor-missing"),
                    List.of()
            );
        }
        if (materializationPlan == null) {
            return new GpuRuntimeMethodTestGpuProbePlan(
                    descriptor.kernelName(),
                    descriptor.irGpuResource(),
                    List.of(),
                    List.of("fixture-invocation-materialization-plan-missing"),
                List.of()
            );
        }
        LinkedHashSet<String> blockers = new LinkedHashSet<>(materializationPlan.blockers());
        ArrayList<String> diagnostics = new ArrayList<>(materializationPlan.diagnostics());
        if (!materializationPlan.materializationReady()) {
            String blocker = "none".equals(materializationPlan.firstBlocker())
                    ? "fixture-invocation-materialization-not-ready"
                    : materializationPlan.firstBlocker();
            blockers.add(blocker);
            diagnostics.add("Fixture invocation materialization is not ready for GPU probe execution: " + blocker);
        }

        Map<String, ReferenceTolerance> tolerances = referenceTolerancesByTestId(probePlan, blockers, diagnostics);
        if (!blockers.isEmpty()) {
            return new GpuRuntimeMethodTestGpuProbePlan(
                    materializationPlan.kernelName(),
                    materializationPlan.irGpuResource(),
                    List.of(),
                    List.copyOf(blockers),
                    diagnostics
            );
        }

        GpuRuntimeBackendReport backendReport = safeBackendReport(diagnostics);
        ArrayList<GpuRuntimeMethodTestGpuProbeExecution> executions = new ArrayList<>();
        for (GpuRuntimeMethodTestInvocationMaterialization invocation : materializationPlan.invocations()) {
            executions.add(executeGpuProbeInvocation(
                    descriptor,
                    invocation,
                    tolerances.getOrDefault(invocation.testId(), ReferenceTolerance.zero()),
                    resolvedOptions,
                    backendReport,
                    lifecycleEventBus
            ));
        }
        if (executions.isEmpty()) {
            blockers.add("fixture-gpu-probe-execution-missing");
            diagnostics.add("No materialized invocations were available for GPU probe execution");
        }
        for (GpuRuntimeMethodTestGpuProbeExecution execution : executions) {
            blockers.addAll(execution.blockers());
            diagnostics.addAll(execution.diagnostics());
        }

        return new GpuRuntimeMethodTestGpuProbePlan(
                materializationPlan.kernelName(),
                materializationPlan.irGpuResource(),
                executions,
                List.copyOf(blockers),
                diagnostics
        );
    }

    private static GpuRuntimeMethodTestGpuProbeExecution executeGpuProbeInvocation(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestInvocationMaterialization invocation,
            ReferenceTolerance tolerance,
            GpuRuntimeMethodTestGpuProbeOptions options,
            GpuRuntimeBackendReport backendReport,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        LinkedHashSet<String> blockers = new LinkedHashSet<>(invocation.blockers());
        ArrayList<String> diagnostics = new ArrayList<>(invocation.diagnostics());
        ArrayList<GpuRuntimeMethodTestReferenceComparison> comparisons = new ArrayList<>();
        List<GpuRuntimeMethodTestInvocationArgument> expectedOutputs = invocation.arguments().stream()
                .filter(GpuRuntimeMethodTestInvocationArgument::expectedOutputReady)
                .toList();
        if (!invocation.invocationReady()) {
            String blocker = "none".equals(invocation.firstBlocker())
                    ? "fixture-invocation-not-ready"
                    : invocation.firstBlocker();
            blockers.add(blocker);
            diagnostics.add("Test vector " + invocation.testId() + " is not ready for GPU probe execution: " + blocker);
        }
        if (expectedOutputs.isEmpty()) {
            blockers.add("fixture-gpu-probe-expected-output-missing");
            diagnostics.add("Test vector " + invocation.testId()
                    + " has no materialized expected outputs for GPU probe comparison");
        }

        GpuProbeExecutionConfigResolution configResolution = resolveGpuProbeExecutionConfig(expectedOutputs, options);
        if (!configResolution.ready()) {
            blockers.add(configResolution.blocker());
            diagnostics.add("Test vector " + invocation.testId()
                    + " cannot resolve a bounded GPU probe launch: " + configResolution.blocker());
        }
        GpuRuntimeMethodTestGpuProbeEvidenceKey evidenceKey = GpuRuntimeMethodTestGpuProbeEvidenceKey.from(
                descriptor,
                invocation,
                configResolution.executionConfig(),
                options.compileOptions(),
                backendReport,
                options.deviceProfile()
        );
        if (!blockers.isEmpty()) {
            return new GpuRuntimeMethodTestGpuProbeExecution(
                    invocation.testId(),
                    evidenceKey,
                    false,
                    false,
                    false,
                    configResolution.executionConfig(),
                    comparisons,
                    List.copyOf(blockers),
                    diagnostics
            );
        }

        if (options.cache() != null) {
            publishGpuProbeCacheLookupEvent(
                    lifecycleEventBus,
                    GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_CACHE_LOOKUP_STARTED,
                    descriptor,
                    options,
                    invocation.testId(),
                    evidenceKey,
                    null
            );
            GpuRuntimeMethodTestGpuProbeCacheEntry cacheEntry = options.cache().getOrRun(
                    evidenceKey,
                    () -> executeGpuProbeInvocationUncached(
                            descriptor,
                            invocation,
                            tolerance,
                            options,
                            configResolution.executionConfig(),
                            evidenceKey
                    )
            );
            publishGpuProbeCacheLookupEvent(
                    lifecycleEventBus,
                    GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_CACHE_LOOKUP_COMPLETED,
                    descriptor,
                    options,
                    invocation.testId(),
                    evidenceKey,
                    cacheEntry
            );
            return cacheEntry.execution();
        }

        return executeGpuProbeInvocationUncached(
                descriptor,
                invocation,
                tolerance,
                options,
                configResolution.executionConfig(),
                evidenceKey
        );
    }

    private static GpuRuntimeMethodTestGpuProbeExecution executeGpuProbeInvocationUncached(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestInvocationMaterialization invocation,
            ReferenceTolerance tolerance,
            GpuRuntimeMethodTestGpuProbeOptions options,
            GpuExecutionConfig executionConfig,
            GpuRuntimeMethodTestGpuProbeEvidenceKey evidenceKey
    ) {
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        ArrayList<GpuRuntimeMethodTestReferenceComparison> comparisons = new ArrayList<>();
        Object[] invocationArguments = invocation.invocationArguments();
        try {
            if (options.compileOptions() == null) {
                GpuRuntime.invoke(executionConfig, descriptor, invocationArguments);
            } else {
                GpuRuntime.invokeWithCompileOptions(
                        executionConfig,
                        options.compileOptions(),
                        descriptor,
                        invocationArguments
                );
            }
        } catch (RuntimeException exception) {
            blockers.add("fixture-gpu-probe-execution-failed");
            diagnostics.add("Test vector " + invocation.testId()
                    + " GPU probe execution failed: " + exception.getClass().getSimpleName()
                    + ": " + normalize(exception.getMessage(), "GPU probe execution failed"));
            return new GpuRuntimeMethodTestGpuProbeExecution(
                    invocation.testId(),
                    evidenceKey,
                    false,
                    false,
                    false,
                    executionConfig,
                    comparisons,
                    List.copyOf(blockers),
                    diagnostics
            );
        }

        List<GpuRuntimeMethodTestInvocationArgument> expectedOutputs = invocation.arguments().stream()
                .filter(GpuRuntimeMethodTestInvocationArgument::expectedOutputReady)
                .toList();
        for (GpuRuntimeMethodTestInvocationArgument expectedOutput : expectedOutputs) {
            GpuRuntimeMethodTestReferenceComparison comparison = compareOutput(
                    expectedOutput,
                    invocationArguments,
                    tolerance,
                    "fixture-gpu-probe",
                    "GPU probe"
            );
            comparisons.add(comparison);
            if (!comparison.comparisonReady() && !"none".equals(comparison.blocker())) {
                blockers.add(comparison.blocker());
            }
            if (!"none".equals(comparison.diagnostic())) {
                diagnostics.add(comparison.diagnostic());
            }
        }
        boolean executionPassed = !comparisons.isEmpty() && blockers.isEmpty() && comparisons.stream()
                .allMatch(GpuRuntimeMethodTestReferenceComparison::passed);
        return new GpuRuntimeMethodTestGpuProbeExecution(
                invocation.testId(),
                evidenceKey,
                false,
                blockers.isEmpty(),
                executionPassed,
                executionConfig,
                comparisons,
                List.copyOf(blockers),
                diagnostics
        );
    }

    private static GpuProbeExecutionConfigResolution resolveGpuProbeExecutionConfig(
            List<GpuRuntimeMethodTestInvocationArgument> expectedOutputs,
            GpuRuntimeMethodTestGpuProbeOptions options
    ) {
        GpuRuntimeMethodTestGpuProbeOptions resolvedOptions = options == null
                ? GpuRuntimeMethodTestGpuProbeOptions.defaults()
                : options;
        if (resolvedOptions.executionConfig() != null) {
            if (!globalWorkItemsWithinLimit(resolvedOptions.executionConfig(), resolvedOptions.maxGlobalWorkItems())) {
                return GpuProbeExecutionConfigResolution.blocked(
                        resolvedOptions.executionConfig(),
                        "fixture-gpu-probe-launch-size-exceeds-limit"
                );
            }
            return GpuProbeExecutionConfigResolution.ready(resolvedOptions.executionConfig());
        }
        if (expectedOutputs.isEmpty()) {
            return GpuProbeExecutionConfigResolution.blocked(null, "fixture-gpu-probe-launch-size-missing");
        }

        int inferredItemCount = -1;
        for (GpuRuntimeMethodTestInvocationArgument expectedOutput : expectedOutputs) {
            int itemCount = expectedOutput.expectedOutputItemCount();
            if (itemCount <= 0) {
                return GpuProbeExecutionConfigResolution.blocked(null, "fixture-gpu-probe-launch-size-missing");
            }
            if (inferredItemCount < 0) {
                inferredItemCount = itemCount;
            } else if (inferredItemCount != itemCount) {
                return GpuProbeExecutionConfigResolution.blocked(null, "fixture-gpu-probe-launch-size-ambiguous");
            }
        }
        if (inferredItemCount > resolvedOptions.maxGlobalWorkItems()) {
            return GpuProbeExecutionConfigResolution.blocked(null, "fixture-gpu-probe-launch-size-exceeds-limit");
        }
        return GpuProbeExecutionConfigResolution.ready(GpuExecutionConfig.oneDimensional(inferredItemCount));
    }

    private static boolean globalWorkItemsWithinLimit(GpuExecutionConfig executionConfig, long maxGlobalWorkItems) {
        long max = maxGlobalWorkItems <= 0L
                ? GpuRuntimeMethodTestGpuProbeOptions.DEFAULT_MAX_GLOBAL_WORK_ITEMS
                : maxGlobalWorkItems;
        long product = executionConfig.globalX();
        if (executionConfig.dimensions() >= 2) {
            if (product > max / executionConfig.globalY()) {
                return false;
            }
            product *= executionConfig.globalY();
        }
        if (executionConfig.dimensions() == 3) {
            if (product > max / executionConfig.globalZ()) {
                return false;
            }
            product *= executionConfig.globalZ();
        }
        return product <= max;
    }

    private static GpuRuntimeBackendReport safeBackendReport(ArrayList<String> diagnostics) {
        try {
            return GpuRuntime.backend().describeCapabilities();
        } catch (RuntimeException exception) {
            diagnostics.add("GPU probe backend capability report is unavailable: "
                    + exception.getClass().getSimpleName()
                    + ": " + normalize(exception.getMessage(), "capability report failed"));
            return GpuRuntimeBackendReport.unavailable(
                    null,
                    GpuRuntime.backend().getClass().getName(),
                    "capability report failed"
            );
        }
    }

    private static GpuRuntimeLifecycleEventBus defaultLifecycleEventBus() {
        return GpuRuntimeLifecycleEventBus.loadFromServiceLoader();
    }

    private static GpuRuntimeLifecycleEventBus lifecycleEventBus(GpuRuntimeLifecycleEventBus lifecycleEventBus) {
        return lifecycleEventBus == null ? GpuRuntimeLifecycleEventBus.empty() : lifecycleEventBus;
    }

    private static void publishMethodTestEvent(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeLifecycleEventKind kind,
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            String message,
            Map<String, String> fields
    ) {
        publishMethodTestEvent(
                lifecycleEventBus,
                kind,
                descriptor == null ? "unknown" : descriptor.kernelName(),
                descriptor == null ? "unknown" : descriptor.kernelResource(),
                descriptor == null ? "" : descriptor.irGpuResource(),
                compileOptions,
                message,
                fields
        );
    }

    private static void publishMethodTestEvent(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeLifecycleEventKind kind,
            String kernelName,
            String kernelResource,
            String irGpuResource,
            GpuRuntimeCompileOptions compileOptions,
            String message,
            Map<String, String> fields
    ) {
        LinkedHashMap<String, String> eventFields = new LinkedHashMap<>();
        eventFields.put("pipeline", "method-test");
        eventFields.put("kernelName", normalize(kernelName, "unknown"));
        eventFields.put("irGpuResource", normalize(irGpuResource, ""));
        if (fields != null) {
            fields.forEach((key, value) -> eventFields.put(key, normalize(value, "")));
        }
        String eventResource = normalize(kernelResource, normalize(irGpuResource, "unknown"));
        lifecycleEventBus(lifecycleEventBus).publish(new GpuRuntimeLifecycleEvent(
                kind,
                backendTarget(compileOptions),
                eventResource,
                optimizationProfile(compileOptions),
                message,
                eventFields
        ));
    }

    private static void publishGpuProbeCacheLookupEvent(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeLifecycleEventKind kind,
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestGpuProbeOptions options,
            String testId,
            GpuRuntimeMethodTestGpuProbeEvidenceKey evidenceKey,
            GpuRuntimeMethodTestGpuProbeCacheEntry cacheEntry
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", cacheEntry == null ? "started" : "completed");
        fields.put("testId", normalize(testId, "unknown"));
        fields.put("cache.enabled", Boolean.toString(options != null && options.cache() != null));
        fields.put("cache.persistent", Boolean.toString(options != null && options.cache() != null && options.cache().persistent()));
        fields.put("cache.hit", cacheEntry == null ? "unknown" : Boolean.toString(cacheEntry.cacheHit()));
        fields.put("evidenceKey.stableHash", evidenceKey == null ? "none" : evidenceKey.stableHash());
        if (cacheEntry != null) {
            GpuRuntimeMethodTestGpuProbeExecution execution = cacheEntry.execution();
            fields.put("executionReady", Boolean.toString(execution.executionReady()));
            fields.put("executionPassed", Boolean.toString(execution.executionPassed()));
            fields.put("firstBlocker", execution.firstBlocker());
            fields.put("firstFailure", execution.firstFailure());
        }
        publishMethodTestEvent(
                lifecycleEventBus,
                kind,
                descriptor,
                options == null ? null : options.compileOptions(),
                cacheEntry == null ? "@GPUTest GPU probe cache lookup started" : "@GPUTest GPU probe cache lookup completed",
                fields
        );
    }

    private static Map<String, String> metadataFields(GpuRuntimeMethodTestProbePlan plan) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", plan.metadataReady() ? "metadata-ready" : "blocked");
        fields.put("artifactLoaded", Boolean.toString(plan.artifactLoaded()));
        fields.put("metadataReady", Boolean.toString(plan.metadataReady()));
        fields.put("testVector.count", Integer.toString(plan.testVectors().size()));
        fields.put("selectionProbe.count", Integer.toString(plan.selectionProbeVectors().size()));
        fields.put("firstBlocker", plan.firstBlocker());
        return fields;
    }

    private static Map<String, String> fixtureReadinessFields(GpuRuntimeMethodTestFixtureReadiness readiness) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", readiness.fixtureResourcesReady() ? "ready" : "blocked");
        fields.put("fixtureResourcesReady", Boolean.toString(readiness.fixtureResourcesReady()));
        fields.put("selectionProbeResourcesReady", Boolean.toString(readiness.selectionProbeResourcesReady()));
        fields.put("fixturePayloadPreviewsReady", Boolean.toString(readiness.fixturePayloadPreviewsReady()));
        fields.put("resource.count", Integer.toString(readiness.resources().size()));
        fields.put("resource.available.count", Integer.toString(readiness.availableResourceCount()));
        fields.put("resource.missing.count", Integer.toString(readiness.missingResourceCount()));
        fields.put("firstBlocker", readiness.firstBlocker());
        return fields;
    }

    private static Map<String, String> valueBindingFields(GpuRuntimeMethodTestFixtureValueBindingPlan plan) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", plan.bindingsReady() ? "ready" : "blocked");
        fields.put("bindingsReady", Boolean.toString(plan.bindingsReady()));
        fields.put("binding.count", Integer.toString(plan.bindings().size()));
        fields.put("binding.ready.count", Integer.toString(plan.bindingReadyCount()));
        fields.put("binding.blocked.count", Integer.toString(plan.bindingBlockedCount()));
        fields.put("firstBlocker", plan.firstBlocker());
        return fields;
    }

    private static Map<String, String> materializationFields(GpuRuntimeMethodTestInvocationMaterializationPlan plan) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", plan.materializationReady() ? "ready" : "blocked");
        fields.put("materializationReady", Boolean.toString(plan.materializationReady()));
        fields.put("invocation.count", Integer.toString(plan.invocations().size()));
        fields.put("invocation.ready.count", Integer.toString(plan.invocationReadyCount()));
        fields.put("invocation.blocked.count", Integer.toString(plan.invocationBlockedCount()));
        fields.put("firstBlocker", plan.firstBlocker());
        return fields;
    }

    private static Map<String, String> referenceComparisonFields(GpuRuntimeMethodTestReferenceComparisonPlan plan) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", plan.status());
        fields.put("referenceComparisonReady", Boolean.toString(plan.referenceComparisonReady()));
        fields.put("referenceComparisonPassed", Boolean.toString(plan.referenceComparisonPassed()));
        fields.put("comparison.count", Integer.toString(plan.comparisons().size()));
        fields.put("comparison.passed.count", Integer.toString(plan.comparisonPassedCount()));
        fields.put("comparison.failed.count", Integer.toString(plan.comparisonFailedCount()));
        fields.put("firstBlocker", plan.firstBlocker());
        fields.put("firstFailure", plan.firstFailure());
        return fields;
    }

    private static Map<String, String> gpuProbeStartedFields(GpuRuntimeMethodTestGpuProbeOptions options) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", "started");
        fields.put("cache.enabled", Boolean.toString(options != null && options.cache() != null));
        fields.put("cache.persistent", Boolean.toString(options != null && options.cache() != null && options.cache().persistent()));
        fields.put("compileOptions.present", Boolean.toString(options != null && options.compileOptions() != null));
        fields.put("deviceProfile.present", Boolean.toString(options != null && options.deviceProfile() != null));
        fields.put("maxGlobalWorkItems", Long.toString(options == null
                ? GpuRuntimeMethodTestGpuProbeOptions.DEFAULT_MAX_GLOBAL_WORK_ITEMS
                : options.maxGlobalWorkItems()));
        return fields;
    }

    private static Map<String, String> gpuProbeFields(
            GpuRuntimeMethodTestGpuProbePlan plan,
            GpuRuntimeMethodTestGpuProbeOptions options
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", plan.status());
        fields.put("gpuProbeReady", Boolean.toString(plan.gpuProbeReady()));
        fields.put("gpuProbePassed", Boolean.toString(plan.gpuProbePassed()));
        fields.put("execution.count", Integer.toString(plan.executions().size()));
        fields.put("execution.ready.count", Integer.toString(plan.executionReadyCount()));
        fields.put("execution.passed.count", Integer.toString(plan.executionPassedCount()));
        fields.put("execution.failed.count", Integer.toString(plan.executionFailedCount()));
        fields.put("comparison.count", Integer.toString(plan.comparisonCount()));
        fields.put("comparison.passed.count", Integer.toString(plan.comparisonPassedCount()));
        fields.put("firstBlocker", plan.firstBlocker());
        fields.put("firstFailure", plan.firstFailure());
        fields.put("cache.enabled", Boolean.toString(options != null && options.cache() != null));
        fields.put("cache.persistent", Boolean.toString(options != null && options.cache() != null && options.cache().persistent()));
        return fields;
    }

    private static GpuBackendTarget backendTarget(GpuRuntimeCompileOptions compileOptions) {
        return compileOptions == null ? GpuBackendTarget.UNKNOWN : compileOptions.backendTarget();
    }

    private static String optimizationProfile(GpuRuntimeCompileOptions compileOptions) {
        return compileOptions == null ? "off" : compileOptions.optimizationProfile();
    }

    private static GpuRuntimeMethodTestReferenceComparison compareOutput(
            GpuRuntimeMethodTestInvocationArgument expectedOutput,
            Object[] invocationArguments,
            ReferenceTolerance tolerance,
            String family,
            String actualLabel
    ) {
        if (expectedOutput.parameterIndex() < 0 || expectedOutput.parameterIndex() >= invocationArguments.length) {
            return blockedReferenceComparison(
                    expectedOutput,
                    family + "-argument-index-out-of-range",
                    capitalize(actualLabel) + " invocation did not expose argument index " + expectedOutput.parameterIndex()
            );
        }

        NumericSnapshot actual = numericSnapshot(invocationArguments[expectedOutput.parameterIndex()], family);
        if (!actual.comparable()) {
            return blockedReferenceComparison(expectedOutput, actual.blocker(), "Actual " + actualLabel + " output is not comparable");
        }
        NumericSnapshot expected = numericSnapshot(expectedOutput.expectedOutputValue(), family);
        if (!expected.comparable()) {
            return blockedReferenceComparison(expectedOutput, expected.blocker(), "Expected fixture output is not comparable");
        }

        if (actual.itemCount() != expected.itemCount()) {
            return failedReferenceComparison(
                    expectedOutput,
                    actual,
                    expected,
                    tolerance,
                    family + "-output-shape-mismatch",
                    "Expected " + expected.itemCount() + " item(s), actual " + actualLabel + " output has " + actual.itemCount()
                            + " item(s)"
            );
        }

        for (int index = 0; index < actual.values().length; index++) {
            double actualValue = actual.values()[index];
            double expectedValue = expected.values()[index];
            if (!withinTolerance(actualValue, expectedValue, tolerance)) {
                double delta = Math.abs(actualValue - expectedValue);
                double allowed = allowedTolerance(expectedValue, tolerance);
                return failedReferenceComparison(
                        expectedOutput,
                        actual,
                        expected,
                        tolerance,
                        family + "-output-mismatch",
                        "Mismatch at item " + index + ": expected=" + expected.numericValues().get(index)
                                + ", actual=" + actual.numericValues().get(index)
                                + ", delta=" + delta
                                + ", allowed=" + allowed
                );
            }
        }

        return new GpuRuntimeMethodTestReferenceComparison(
                expectedOutput.testId(),
                expectedOutput.parameterIndex(),
                expectedOutput.parameterName(),
                expectedOutput.javaType(),
                expectedOutput.access(),
                true,
                true,
                actual.kind(),
                actual.itemCount(),
                actual.numericValues(),
                expected.kind(),
                expected.itemCount(),
                expected.numericValues(),
                tolerance.absolute(),
                tolerance.relative(),
                "none",
                "none",
                "none"
        );
    }

    private static GpuRuntimeMethodTestReferenceComparison blockedReferenceComparison(
            GpuRuntimeMethodTestInvocationArgument expectedOutput,
            String blocker,
            String diagnostic
    ) {
        return new GpuRuntimeMethodTestReferenceComparison(
                expectedOutput.testId(),
                expectedOutput.parameterIndex(),
                expectedOutput.parameterName(),
                expectedOutput.javaType(),
                expectedOutput.access(),
                false,
                false,
                "none",
                -1,
                List.of(),
                expectedOutput.expectedOutputKind(),
                expectedOutput.expectedOutputItemCount(),
                expectedOutput.expectedOutputNumericValues(),
                0.0d,
                0.0d,
                blocker,
                "none",
                diagnostic
        );
    }

    private static GpuRuntimeMethodTestReferenceComparison failedReferenceComparison(
            GpuRuntimeMethodTestInvocationArgument expectedOutput,
            NumericSnapshot actual,
            NumericSnapshot expected,
            ReferenceTolerance tolerance,
            String failure,
            String diagnostic
    ) {
        return new GpuRuntimeMethodTestReferenceComparison(
                expectedOutput.testId(),
                expectedOutput.parameterIndex(),
                expectedOutput.parameterName(),
                expectedOutput.javaType(),
                expectedOutput.access(),
                true,
                false,
                actual.kind(),
                actual.itemCount(),
                actual.numericValues(),
                expected.kind(),
                expected.itemCount(),
                expected.numericValues(),
                tolerance.absolute(),
                tolerance.relative(),
                "none",
                failure,
                diagnostic
        );
    }

    private static boolean withinTolerance(double actual, double expected, ReferenceTolerance tolerance) {
        if (Double.isNaN(actual) || Double.isNaN(expected)) {
            return Double.isNaN(actual) && Double.isNaN(expected);
        }
        if (Double.isInfinite(actual) || Double.isInfinite(expected)) {
            return Double.compare(actual, expected) == 0;
        }
        return Math.abs(actual - expected) <= allowedTolerance(expected, tolerance);
    }

    private static double allowedTolerance(double expected, ReferenceTolerance tolerance) {
        return tolerance.absolute() + tolerance.relative() * Math.abs(expected);
    }

    private static Map<String, ReferenceTolerance> referenceTolerancesByTestId(
            GpuRuntimeMethodTestProbePlan probePlan,
            LinkedHashSet<String> blockers,
            ArrayList<String> diagnostics
    ) {
        if (probePlan == null) {
            return Map.of();
        }
        LinkedHashMap<String, ReferenceTolerance> tolerances = new LinkedHashMap<>();
        for (GpuRuntimeMethodTestVectorPlan vector : probePlan.testVectors()) {
            tolerances.put(vector.testId(), parseReferenceTolerance(vector.testId(), vector.tolerance(), blockers, diagnostics));
        }
        return Map.copyOf(tolerances);
    }

    private static ReferenceTolerance parseReferenceTolerance(
            String testId,
            String tolerance,
            LinkedHashSet<String> blockers,
            ArrayList<String> diagnostics
    ) {
        String normalizedTolerance = normalize(tolerance, "");
        if (normalizedTolerance.isBlank()) {
            return ReferenceTolerance.zero();
        }
        double absolute = 0.0d;
        double relative = 0.0d;
        for (String token : normalizedTolerance.split("[,;\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            int equalsIndex = token.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex == token.length() - 1) {
                blockers.add("fixture-reference-tolerance-invalid");
                diagnostics.add("Test vector " + testId + " has invalid tolerance token: " + token);
                return ReferenceTolerance.zero();
            }
            String key = token.substring(0, equalsIndex).trim();
            String value = token.substring(equalsIndex + 1).trim();
            try {
                double parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed) || parsed < 0.0d) {
                    throw new NumberFormatException("tolerance must be finite and non-negative");
                }
                if ("abs".equals(key) || "absolute".equals(key)) {
                    absolute = parsed;
                } else if ("rel".equals(key) || "relative".equals(key)) {
                    relative = parsed;
                } else {
                    blockers.add("fixture-reference-tolerance-invalid");
                    diagnostics.add("Test vector " + testId + " has unknown tolerance key: " + key);
                    return ReferenceTolerance.zero();
                }
            } catch (NumberFormatException exception) {
                blockers.add("fixture-reference-tolerance-invalid");
                diagnostics.add("Test vector " + testId + " has invalid tolerance value for " + key + ": " + value);
                return ReferenceTolerance.zero();
            }
        }
        return new ReferenceTolerance(absolute, relative);
    }

    private static NumericSnapshot numericSnapshot(Object value, String family) {
        if (value == null) {
            return NumericSnapshot.blocked(family + "-output-missing");
        }
        if (isGpuStructArrayValue(value)) {
            return structArrayNumericSnapshot(value, family);
        }
        if (isGpuStructType(value.getClass())) {
            return structNumericSnapshot("java-struct-object", value, family);
        }
        if (value instanceof float[] values) {
            return numericSnapshot("java-float-array", values);
        }
        if (value instanceof double[] values) {
            return numericSnapshot("java-double-array", values);
        }
        if (value instanceof int[] values) {
            return numericSnapshot("java-int-array", values);
        }
        if (value instanceof long[] values) {
            return numericSnapshot("java-long-array", values);
        }
        if (value instanceof short[] values) {
            return numericSnapshot("java-short-array", values);
        }
        if (value instanceof byte[] values) {
            return numericSnapshot("java-byte-array", values);
        }
        if (value instanceof Float numericValue) {
            return numericSnapshot("java-float", numericValue.doubleValue(), Float.toString(numericValue));
        }
        if (value instanceof Double numericValue) {
            return numericSnapshot("java-double", numericValue, Double.toString(numericValue));
        }
        if (value instanceof Integer numericValue) {
            return numericSnapshot("java-int", numericValue.doubleValue(), Integer.toString(numericValue));
        }
        if (value instanceof Long numericValue) {
            return numericSnapshot("java-long", numericValue.doubleValue(), Long.toString(numericValue));
        }
        if (value instanceof Short numericValue) {
            return numericSnapshot("java-short", numericValue.doubleValue(), Short.toString(numericValue));
        }
        if (value instanceof Byte numericValue) {
            return numericSnapshot("java-byte", numericValue.doubleValue(), Byte.toString(numericValue));
        }
        return NumericSnapshot.blocked(family + "-output-type-unsupported");
    }

    private static NumericSnapshot numericSnapshot(String kind, float[] values) {
        ArrayList<String> numericValues = new ArrayList<>(values.length);
        double[] doubles = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            numericValues.add(Float.toString(values[index]));
            doubles[index] = values[index];
        }
        return new NumericSnapshot(true, kind, values.length, numericValues, doubles, "none");
    }

    private static NumericSnapshot numericSnapshot(String kind, double[] values) {
        ArrayList<String> numericValues = new ArrayList<>(values.length);
        double[] doubles = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            numericValues.add(Double.toString(values[index]));
            doubles[index] = values[index];
        }
        return new NumericSnapshot(true, kind, values.length, numericValues, doubles, "none");
    }

    private static NumericSnapshot numericSnapshot(String kind, int[] values) {
        ArrayList<String> numericValues = new ArrayList<>(values.length);
        double[] doubles = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            numericValues.add(Integer.toString(values[index]));
            doubles[index] = values[index];
        }
        return new NumericSnapshot(true, kind, values.length, numericValues, doubles, "none");
    }

    private static NumericSnapshot numericSnapshot(String kind, long[] values) {
        ArrayList<String> numericValues = new ArrayList<>(values.length);
        double[] doubles = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            numericValues.add(Long.toString(values[index]));
            doubles[index] = values[index];
        }
        return new NumericSnapshot(true, kind, values.length, numericValues, doubles, "none");
    }

    private static NumericSnapshot numericSnapshot(String kind, short[] values) {
        ArrayList<String> numericValues = new ArrayList<>(values.length);
        double[] doubles = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            numericValues.add(Short.toString(values[index]));
            doubles[index] = values[index];
        }
        return new NumericSnapshot(true, kind, values.length, numericValues, doubles, "none");
    }

    private static NumericSnapshot numericSnapshot(String kind, byte[] values) {
        ArrayList<String> numericValues = new ArrayList<>(values.length);
        double[] doubles = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            numericValues.add(Byte.toString(values[index]));
            doubles[index] = values[index];
        }
        return new NumericSnapshot(true, kind, values.length, numericValues, doubles, "none");
    }

    private static NumericSnapshot numericSnapshot(String kind, double value, String numericValue) {
        return new NumericSnapshot(true, kind, 1, List.of(numericValue), new double[]{value}, "none");
    }

    private static NumericSnapshot structArrayNumericSnapshot(Object array, String family) {
        ArrayList<String> numericValues = new ArrayList<>();
        ArrayList<Double> values = new ArrayList<>();
        try {
            int length = Array.getLength(array);
            for (int index = 0; index < length; index++) {
                appendStructNumericValues(Array.get(array, index), "[" + index + "]", numericValues, values);
            }
        } catch (RuntimeException exception) {
            return NumericSnapshot.blocked(family + "-struct-output-type-unsupported");
        }
        return numericSnapshot("java-struct-array", numericValues, values);
    }

    private static NumericSnapshot structNumericSnapshot(String kind, Object structValue, String family) {
        ArrayList<String> numericValues = new ArrayList<>();
        ArrayList<Double> values = new ArrayList<>();
        try {
            appendStructNumericValues(structValue, "", numericValues, values);
        } catch (RuntimeException exception) {
            return NumericSnapshot.blocked(family + "-struct-output-type-unsupported");
        }
        return numericSnapshot(kind, numericValues, values);
    }

    private static NumericSnapshot numericSnapshot(String kind, List<String> numericValues, List<Double> values) {
        double[] doubles = new double[values.size()];
        for (int index = 0; index < values.size(); index++) {
            doubles[index] = values.get(index);
        }
        return new NumericSnapshot(true, kind, numericValues.size(), numericValues, doubles, "none");
    }

    private static void appendStructNumericValues(
            Object structValue,
            String path,
            ArrayList<String> numericValues,
            ArrayList<Double> values
    ) {
        if (structValue == null || !isGpuStructType(structValue.getClass())) {
            throw new IllegalArgumentException("Expected @GPUStruct value");
        }
        for (Field field : fixtureStructFields(structValue.getClass())) {
            String childPath = path.isBlank() ? field.getName() : path + "." + field.getName();
            Class<?> fieldType = field.getType();
            Object fieldValue = readStructField(structValue, field);
            if (fieldType.isPrimitive()) {
                appendPrimitiveStructNumericValue(childPath, fieldType, fieldValue, numericValues, values);
            } else if (fieldValue != null && isGpuStructType(fieldType)) {
                appendStructNumericValues(fieldValue, childPath, numericValues, values);
            } else {
                throw new IllegalArgumentException("Unsupported @GPUStruct comparison field: " + childPath);
            }
        }
    }

    private static Object readStructField(Object instance, Field field) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Struct field is not readable: " + field.getName(), exception);
        }
    }

    private static void appendPrimitiveStructNumericValue(
            String path,
            Class<?> type,
            Object fieldValue,
            ArrayList<String> numericValues,
            ArrayList<Double> values
    ) {
        double numericValue;
        String textValue;
        if (type == float.class) {
            float value = (Float) fieldValue;
            numericValue = value;
            textValue = Float.toString(value);
        } else if (type == double.class) {
            double value = (Double) fieldValue;
            numericValue = value;
            textValue = Double.toString(value);
        } else if (type == int.class) {
            int value = (Integer) fieldValue;
            numericValue = value;
            textValue = Integer.toString(value);
        } else if (type == long.class) {
            long value = (Long) fieldValue;
            numericValue = value;
            textValue = Long.toString(value);
        } else if (type == short.class) {
            short value = (Short) fieldValue;
            numericValue = value;
            textValue = Short.toString(value);
        } else if (type == byte.class) {
            byte value = (Byte) fieldValue;
            numericValue = value;
            textValue = Byte.toString(value);
        } else {
            throw new IllegalArgumentException("Unsupported primitive struct field type: " + type.getName());
        }
        numericValues.add(path + "=" + textValue);
        values.add(numericValue);
    }

    private static boolean isGpuStructArrayValue(Object value) {
        Class<?> type = value.getClass();
        return type.isArray() && type.getComponentType() != null && isGpuStructType(type.getComponentType());
    }

    private static void addResourceStatuses(
            ArrayList<GpuRuntimeMethodTestFixtureResourceStatus> resources,
            LinkedHashSet<String> blockers,
            ArrayList<String> diagnostics,
            GpuRuntimeMethodTestVectorPlan vector,
            String kind,
            List<String> refs,
            ClassLoader preferredClassLoader
    ) {
        for (int index = 0; index < refs.size(); index++) {
            String ref = refs.get(index);
            Optional<URL> location = findResource(ref, preferredClassLoader);
            if (location.isEmpty()) {
                blockers.add("fixture-resource-not-found");
                resources.add(new GpuRuntimeMethodTestFixtureResourceStatus(
                        vector.testId(),
                        kind,
                        index,
                        ref,
                        false,
                        "none",
                        -1L,
                        "none",
                        GpuRuntimeMethodTestFixturePayloadPreview.unavailable("fixture-resource-not-found"),
                        "fixture-resource-not-found"
                ));
                continue;
            }

            try {
                byte[] bytes = readResourceBytes(location.orElseThrow());
                GpuRuntimeMethodTestFixturePayloadPreview payloadPreview =
                        GpuRuntimeMethodTestFixturePayloadPreview.preview(ref, bytes);
                if (!payloadPreview.schemaReady()) {
                    blockers.add("fixture-payload-schema-not-ready");
                    diagnostics.add("Test vector " + vector.testId()
                            + " fixture payload preview is not ready: " + ref
                            + " (" + payloadPreview.blocker() + ")");
                }
                resources.add(new GpuRuntimeMethodTestFixtureResourceStatus(
                        vector.testId(),
                        kind,
                        index,
                        ref,
                        true,
                        location.map(URL::toString).orElse("none"),
                        bytes.length,
                        sha256(bytes),
                        payloadPreview,
                        "none"
                ));
            } catch (IOException | RuntimeException exception) {
                blockers.add("fixture-resource-read-failed");
                diagnostics.add("Test vector " + vector.testId()
                        + " fixture resource could not be read: " + ref
                        + " (" + exception.getClass().getSimpleName() + ": "
                        + normalize(exception.getMessage(), "read failed") + ")");
                resources.add(new GpuRuntimeMethodTestFixtureResourceStatus(
                        vector.testId(),
                        kind,
                        index,
                        ref,
                        false,
                        location.map(URL::toString).orElse("none"),
                        -1L,
                        "none",
                        GpuRuntimeMethodTestFixturePayloadPreview.unavailable("fixture-resource-read-failed"),
                        "fixture-resource-read-failed"
                ));
            }
        }
    }

    private static void addValueBindings(
            ArrayList<GpuRuntimeMethodTestFixtureValueBinding> bindings,
            LinkedHashSet<String> blockers,
            ArrayList<String> diagnostics,
            GpuRuntimeMethodTestVectorPlan vector,
            String kind,
            List<String> refs,
            List<GpuKernelParameterDescriptor> parameters,
            ClassLoader preferredClassLoader
    ) {
        List<GpuKernelParameterDescriptor> candidateParameters = parameters.stream()
                .filter(parameter -> parameterMatchesFixtureKind(parameter, kind))
                .toList();
        if (!refs.isEmpty() && candidateParameters.isEmpty()) {
            blockers.add("fixture-value-parameter-candidate-missing");
            diagnostics.add("Test vector " + vector.testId()
                    + " has " + kind + " fixture refs, but no descriptor parameters can receive that fixture kind");
        }

        for (int index = 0; index < refs.size(); index++) {
            String ref = refs.get(index);
            Optional<URL> location = findResource(ref, preferredClassLoader);
            if (location.isEmpty()) {
                blockers.add("fixture-resource-not-found");
                bindings.add(blockedValueBinding(vector, kind, index, ref, "none", "none", null, "fixture-resource-not-found"));
                continue;
            }

            GpuRuntimeMethodTestFixtureValuePayloadParser.Payload payload;
            try {
                payload = GpuRuntimeMethodTestFixtureValuePayloadParser.parse(readResourceBytes(location.orElseThrow()));
            } catch (IOException | RuntimeException exception) {
                blockers.add("fixture-value-payload-load-failed");
                diagnostics.add("Test vector " + vector.testId()
                        + " fixture value payload could not be loaded: " + ref
                        + " (" + exception.getClass().getSimpleName() + ": "
                        + normalize(exception.getMessage(), "load failed") + ")");
                bindings.add(blockedValueBinding(vector, kind, index, ref, "none", "none", null, "fixture-value-payload-load-failed"));
                continue;
            }

            int beforeCount = bindings.size();
            for (GpuKernelParameterDescriptor parameter : candidateParameters) {
                GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue value = payload.field(parameter.name());
                if (value != null) {
                    bindings.add(valueBinding(vector, kind, index, ref, parameter, value, blockers, diagnostics));
                } else if (payload.containsField(parameter.name())) {
                    blockers.add("fixture-value-field-unsupported");
                    diagnostics.add("Test vector " + vector.testId()
                            + " fixture field is present but has an unsupported value shape: " + ref + "#" + parameter.name());
                    bindings.add(blockedValueBinding(
                            vector,
                            kind,
                            index,
                            ref,
                            parameter.name(),
                            parameter.javaType(),
                            parameter.access(),
                            "fixture-value-field-unsupported"
                    ));
                }
            }
            if (bindings.size() == beforeCount) {
                blockers.add("fixture-value-parameter-not-found");
                diagnostics.add("Test vector " + vector.testId()
                        + " fixture payload did not contain a supported field matching a " + kind
                        + " descriptor parameter: " + ref);
                bindings.add(blockedValueBinding(vector, kind, index, ref, "none", "none", null, "fixture-value-parameter-not-found"));
            }
        }
    }

    private static GpuRuntimeMethodTestFixtureValueBinding valueBinding(
            GpuRuntimeMethodTestVectorPlan vector,
            String kind,
            int fixtureIndex,
            String resourceRef,
            GpuKernelParameterDescriptor parameter,
            GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue value,
            LinkedHashSet<String> blockers,
            ArrayList<String> diagnostics
    ) {
        String blocker = valueBindingBlocker(parameter, value);
        boolean ready = "none".equals(blocker);
        if (!ready) {
            blockers.add(blocker);
            diagnostics.add("Test vector " + vector.testId()
                    + " fixture field cannot bind to descriptor parameter " + parameter.name()
                    + ": " + blocker);
        }
        return new GpuRuntimeMethodTestFixtureValueBinding(
                vector.testId(),
                kind,
                fixtureIndex,
                resourceRef,
                parameter.name(),
                parameter.javaType(),
                parameter.access(),
                ready,
                ready ? value.valueKind() : "none",
                ready ? value.itemCount() : -1,
                ready ? value.flattenedNumericValues() : List.of(),
                ready ? value : null,
                blocker
        );
    }

    private static GpuRuntimeMethodTestFixtureValueBinding blockedValueBinding(
            GpuRuntimeMethodTestVectorPlan vector,
            String kind,
            int fixtureIndex,
            String resourceRef,
            String parameterName,
            String javaType,
            GpuKernelParameterAccess access,
            String blocker
    ) {
        return new GpuRuntimeMethodTestFixtureValueBinding(
                vector.testId(),
                kind,
                fixtureIndex,
                resourceRef,
                parameterName,
                javaType,
                access,
                false,
                "none",
                -1,
                List.of(),
                blocker
        );
    }

    private static LinkedHashMap<String, List<GpuRuntimeMethodTestFixtureValueBinding>> bindingsByTestId(
            List<GpuRuntimeMethodTestFixtureValueBinding> bindings
    ) {
        LinkedHashMap<String, ArrayList<GpuRuntimeMethodTestFixtureValueBinding>> grouped = new LinkedHashMap<>();
        for (GpuRuntimeMethodTestFixtureValueBinding binding : bindings) {
            grouped.computeIfAbsent(binding.testId(), ignored -> new ArrayList<>()).add(binding);
        }
        LinkedHashMap<String, List<GpuRuntimeMethodTestFixtureValueBinding>> result = new LinkedHashMap<>();
        for (Map.Entry<String, ArrayList<GpuRuntimeMethodTestFixtureValueBinding>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return result;
    }

    private static GpuRuntimeMethodTestInvocationMaterialization materializeInvocation(
            String testId,
            List<GpuRuntimeMethodTestFixtureValueBinding> bindings,
            List<GpuKernelParameterDescriptor> parameters
    ) {
        ArrayList<GpuRuntimeMethodTestInvocationArgument> arguments = new ArrayList<>();
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
            GpuKernelParameterDescriptor parameter = parameters.get(parameterIndex);
            GpuRuntimeMethodTestFixtureValueBinding inputBinding = findBinding(bindings, "input", parameter.name());
            GpuRuntimeMethodTestFixtureValueBinding expectedBinding = findBinding(bindings, "expectedOutput", parameter.name());
            GpuRuntimeMethodTestInvocationArgument argument = materializeArgument(
                    testId,
                    parameterIndex,
                    parameter,
                    inputBinding,
                    expectedBinding,
                    blockers,
                    diagnostics
            );
            arguments.add(argument);
        }
        return new GpuRuntimeMethodTestInvocationMaterialization(
                testId,
                blockers.isEmpty(),
                arguments,
                List.copyOf(blockers),
                diagnostics
        );
    }

    private static GpuRuntimeMethodTestInvocationArgument materializeArgument(
            String testId,
            int parameterIndex,
            GpuKernelParameterDescriptor parameter,
            GpuRuntimeMethodTestFixtureValueBinding inputBinding,
            GpuRuntimeMethodTestFixtureValueBinding expectedBinding,
            LinkedHashSet<String> blockers,
            ArrayList<String> diagnostics
    ) {
        if (parameter.access() == GpuKernelParameterAccess.LOCAL) {
            return blockedInvocationArgument(testId, parameterIndex, parameter, "fixture-invocation-local-parameter-unsupported", blockers, diagnostics);
        }

        MaterializedFixtureValue expectedOutput = null;
        if (expectedBinding != null && expectedBinding.bindingReady()) {
            try {
                expectedOutput = materializeFixtureValue(
                        expectedBinding.javaType(),
                        fixtureValue(expectedBinding),
                        false,
                        expectedBinding.itemCount()
                );
            } catch (RuntimeException exception) {
                return blockedInvocationArgument(testId, parameterIndex, parameter, "fixture-invocation-expected-output-materialization-failed", blockers, diagnostics);
            }
        } else if (expectedBinding != null) {
            return blockedInvocationArgument(testId, parameterIndex, parameter, expectedBinding.blocker(), blockers, diagnostics);
        }

        MaterializedFixtureValue argumentValue;
        if (inputBinding != null && inputBinding.bindingReady()) {
            try {
                argumentValue = materializeFixtureValue(
                        inputBinding.javaType(),
                        fixtureValue(inputBinding),
                        false,
                        inputBinding.itemCount()
                );
            } catch (RuntimeException exception) {
                return blockedInvocationArgument(testId, parameterIndex, parameter, "fixture-invocation-argument-materialization-failed", blockers, diagnostics);
            }
        } else if (inputBinding != null) {
            return blockedInvocationArgument(testId, parameterIndex, parameter, inputBinding.blocker(), blockers, diagnostics);
        } else if (expectedOutput != null && parameter.access() == GpuKernelParameterAccess.READ_WRITE && isArrayJavaType(parameter.javaType())) {
            try {
                argumentValue = materializeFixtureValue(parameter.javaType(), null, true, expectedOutput.itemCount());
            } catch (RuntimeException exception) {
                return blockedInvocationArgument(testId, parameterIndex, parameter, "fixture-invocation-output-argument-materialization-failed", blockers, diagnostics);
            }
        } else {
            return blockedInvocationArgument(testId, parameterIndex, parameter, "fixture-invocation-input-missing", blockers, diagnostics);
        }

        return new GpuRuntimeMethodTestInvocationArgument(
                testId,
                parameterIndex,
                parameter.name(),
                parameter.javaType(),
                parameter.access(),
                true,
                argumentValue.kind(),
                argumentValue.itemCount(),
                argumentValue.value(),
                argumentValue.numericValues(),
                expectedOutput != null,
                expectedOutput == null ? "none" : expectedOutput.kind(),
                expectedOutput == null ? -1 : expectedOutput.itemCount(),
                expectedOutput == null ? null : expectedOutput.value(),
                expectedOutput == null ? List.of() : expectedOutput.numericValues(),
                "none"
        );
    }

    private static GpuRuntimeMethodTestInvocationArgument blockedInvocationArgument(
            String testId,
            int parameterIndex,
            GpuKernelParameterDescriptor parameter,
            String blocker,
            LinkedHashSet<String> blockers,
            ArrayList<String> diagnostics
    ) {
        blockers.add(blocker);
        diagnostics.add("Test vector " + testId
                + " invocation argument cannot be materialized for parameter " + parameter.name()
                + ": " + blocker);
        return new GpuRuntimeMethodTestInvocationArgument(
                testId,
                parameterIndex,
                parameter.name(),
                parameter.javaType(),
                parameter.access(),
                false,
                "none",
                -1,
                null,
                List.of(),
                false,
                "none",
                -1,
                null,
                List.of(),
                blocker
        );
    }

    private static GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue fixtureValue(
            GpuRuntimeMethodTestFixtureValueBinding binding
    ) {
        if (binding.fixtureValue() instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue value) {
            return value;
        }
        throw new IllegalArgumentException("Fixture binding does not carry a materializable value payload");
    }

    private static GpuRuntimeMethodTestFixtureValueBinding findBinding(
            List<GpuRuntimeMethodTestFixtureValueBinding> bindings,
            String kind,
            String parameterName
    ) {
        for (GpuRuntimeMethodTestFixtureValueBinding binding : bindings) {
            if (binding.kind().equals(kind) && binding.parameterName().equals(parameterName)) {
                return binding;
            }
        }
        return null;
    }

    private static MaterializedFixtureValue materializeFixtureValue(
            String javaType,
            GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue fixtureValue,
            boolean zeroFilled,
            int itemCount
    ) {
        String normalizedType = normalize(javaType, "");
        if (zeroFilled && isStructArrayJavaType(normalizedType)) {
            Class<?> componentType = resolveArrayComponentType(normalizedType);
            Object value = materializeZeroFilledStructArray(componentType, itemCount);
            return new MaterializedFixtureValue(
                    "zero-filled-struct-array",
                    itemCount,
                    value,
                    List.of()
            );
        }
        if (zeroFilled && isArrayJavaType(normalizedType)) {
            int length = itemCount;
            List<String> values = zeroValues(length);
            return new MaterializedFixtureValue(
                    "zero-filled-" + normalizedType.replace("[]", "-array"),
                    length,
                    materializeArray(normalizedType, values, true, length),
                    values
            );
        }
        if (fixtureValue instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.StructValue structValue) {
            Class<?> structType = resolveJavaType(normalizedType);
            return new MaterializedFixtureValue(
                    "java-struct-object",
                    1,
                    materializeStruct(structType, structValue),
                    fixtureValue.flattenedNumericValues()
            );
        }
        if (fixtureValue instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.StructArrayValue structArrayValue) {
            Class<?> componentType = resolveArrayComponentType(normalizedType);
            return new MaterializedFixtureValue(
                    "java-struct-array",
                    structArrayValue.itemCount(),
                    materializeStructArray(componentType, structArrayValue),
                    fixtureValue.flattenedNumericValues()
            );
        }
        if (!(fixtureValue instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.NumericValue numericValue)) {
            throw new IllegalArgumentException("Unsupported fixture value kind for Java type: " + javaType);
        }
        List<String> numericValues = numericValue.numbers();
        if (isArrayJavaType(normalizedType)) {
            int length = numericValues.size();
            List<String> values = numericValues;
            return new MaterializedFixtureValue(
                    "java-" + normalizedType.replace("[]", "-array"),
                    length,
                    materializeArray(normalizedType, values, false, length),
                    values
            );
        }
        if (zeroFilled) {
            throw new IllegalArgumentException("Cannot zero-fill scalar fixture argument");
        }
        if (numericValues.size() != 1) {
            throw new IllegalArgumentException("Scalar fixture argument requires exactly one value");
        }
        return new MaterializedFixtureValue(
                "java-" + normalizedType,
                1,
                materializeScalar(normalizedType, numericValues.get(0)),
                numericValues
        );
    }

    private static Object materializeArray(String javaType, List<String> numericValues, boolean zeroFilled, int length) {
        return switch (javaType) {
            case "float[]" -> {
                float[] values = new float[length];
                if (!zeroFilled) {
                    for (int index = 0; index < values.length; index++) {
                        values[index] = Float.parseFloat(numericValues.get(index));
                    }
                }
                yield values;
            }
            case "double[]" -> {
                double[] values = new double[length];
                if (!zeroFilled) {
                    for (int index = 0; index < values.length; index++) {
                        values[index] = Double.parseDouble(numericValues.get(index));
                    }
                }
                yield values;
            }
            case "int[]" -> {
                int[] values = new int[length];
                if (!zeroFilled) {
                    for (int index = 0; index < values.length; index++) {
                        values[index] = Integer.parseInt(integralNumber(numericValues.get(index)));
                    }
                }
                yield values;
            }
            case "long[]" -> {
                long[] values = new long[length];
                if (!zeroFilled) {
                    for (int index = 0; index < values.length; index++) {
                        values[index] = Long.parseLong(integralNumber(numericValues.get(index)));
                    }
                }
                yield values;
            }
            case "short[]" -> {
                short[] values = new short[length];
                if (!zeroFilled) {
                    for (int index = 0; index < values.length; index++) {
                        values[index] = Short.parseShort(integralNumber(numericValues.get(index)));
                    }
                }
                yield values;
            }
            case "byte[]" -> {
                byte[] values = new byte[length];
                if (!zeroFilled) {
                    for (int index = 0; index < values.length; index++) {
                        values[index] = Byte.parseByte(integralNumber(numericValues.get(index)));
                    }
                }
                yield values;
            }
            default -> throw new IllegalArgumentException("Unsupported fixture array type: " + javaType);
        };
    }

    private static Object materializeScalar(String javaType, String numericValue) {
        return switch (javaType) {
            case "float" -> Float.parseFloat(numericValue);
            case "double" -> Double.parseDouble(numericValue);
            case "int" -> Integer.parseInt(integralNumber(numericValue));
            case "long" -> Long.parseLong(integralNumber(numericValue));
            case "short" -> Short.parseShort(integralNumber(numericValue));
            case "byte" -> Byte.parseByte(integralNumber(numericValue));
            default -> throw new IllegalArgumentException("Unsupported fixture scalar type: " + javaType);
        };
    }

    private static Object materializeStructArray(
            Class<?> componentType,
            GpuRuntimeMethodTestFixtureValuePayloadParser.StructArrayValue value
    ) {
        Object array = Array.newInstance(componentType, value.itemCount());
        for (int index = 0; index < value.items().size(); index++) {
            Array.set(array, index, materializeStruct(componentType, value.items().get(index)));
        }
        return array;
    }

    private static Object materializeZeroFilledStructArray(Class<?> componentType, int itemCount) {
        int length = Math.max(itemCount, 0);
        Object array = Array.newInstance(componentType, length);
        for (int index = 0; index < length; index++) {
            Array.set(array, index, newStructInstance(componentType));
        }
        return array;
    }

    private static Object materializeStruct(
            Class<?> structType,
            GpuRuntimeMethodTestFixtureValuePayloadParser.StructValue value
    ) {
        Object instance = newStructInstance(structType);
        for (Field field : fixtureStructFields(structType)) {
            GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue fieldValue = value.field(field.getName());
            if (fieldValue == null) {
                throw new IllegalArgumentException("Missing fixture struct field: " + field.getName());
            }
            setStructField(instance, field, materializeStructField(field.getType(), fieldValue));
        }
        return instance;
    }

    private static Object materializeStructField(
            Class<?> fieldType,
            GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue fieldValue
    ) {
        if (fieldType.isPrimitive()) {
            if (!(fieldValue instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.NumericValue numericValue)
                    || numericValue.array()
                    || numericValue.numbers().size() != 1) {
                throw new IllegalArgumentException("Primitive struct field requires one numeric value");
            }
            return materializeScalar(fieldType.getName(), numericValue.numbers().get(0));
        }
        if (isGpuStructType(fieldType)) {
            if (!(fieldValue instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.StructValue structValue)) {
                throw new IllegalArgumentException("Nested struct field requires a struct object value");
            }
            return materializeStruct(fieldType, structValue);
        }
        throw new IllegalArgumentException("Unsupported fixture struct field type: " + fieldType.getName());
    }

    private static Object newStructInstance(Class<?> structType) {
        try {
            Constructor<?> constructor = structType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Fixture struct type requires an accessible no-arg constructor: "
                    + structType.getName(), exception);
        }
    }

    private static void setStructField(Object instance, Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(instance, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Fixture struct field is not writable: " + field.getName(), exception);
        }
    }

    private static List<String> zeroValues(int length) {
        ArrayList<String> values = new ArrayList<>();
        for (int index = 0; index < length; index++) {
            values.add("0");
        }
        return values;
    }

    private static String integralNumber(String number) {
        return new BigDecimal(number).toBigIntegerExact().toString();
    }

    private static boolean isArrayJavaType(String javaType) {
        return normalize(javaType, "").endsWith("[]");
    }

    private static boolean isStructArrayJavaType(String javaType) {
        if (!isArrayJavaType(javaType)) {
            return false;
        }
        try {
            return isGpuStructType(resolveArrayComponentType(javaType));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean parameterMatchesFixtureKind(GpuKernelParameterDescriptor parameter, String kind) {
        if (parameter == null || parameter.access() == GpuKernelParameterAccess.LOCAL) {
            return false;
        }
        if ("expectedOutput".equals(kind)) {
            return parameter.access() == GpuKernelParameterAccess.READ_WRITE;
        }
        return parameter.access() == GpuKernelParameterAccess.READ_ONLY
                || parameter.access() == GpuKernelParameterAccess.READ_WRITE
                || parameter.access() == GpuKernelParameterAccess.VALUE;
    }

    private static String valueBindingBlocker(
            GpuKernelParameterDescriptor parameter,
            GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue value
    ) {
        String javaType = normalize(parameter.javaType(), "");
        if (value instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.StructValue structValue) {
            if (isArrayJavaType(javaType)) {
                return "fixture-value-shape-mismatch";
            }
            Class<?> structType;
            try {
                structType = resolveJavaType(javaType);
            } catch (RuntimeException exception) {
                return "fixture-value-java-type-unsupported";
            }
            if (!isGpuStructType(structType)) {
                return "fixture-value-java-type-unsupported";
            }
            return structValueBlocker(structType, structValue);
        }
        if (value instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.StructArrayValue structArrayValue) {
            if (!isArrayJavaType(javaType)) {
                return "fixture-value-shape-mismatch";
            }
            Class<?> componentType;
            try {
                componentType = resolveArrayComponentType(javaType);
            } catch (RuntimeException exception) {
                return "fixture-value-java-type-unsupported";
            }
            if (!isGpuStructType(componentType)) {
                return "fixture-value-java-type-unsupported";
            }
            for (GpuRuntimeMethodTestFixtureValuePayloadParser.StructValue item : structArrayValue.items()) {
                String blocker = structValueBlocker(componentType, item);
                if (!"none".equals(blocker)) {
                    return blocker;
                }
            }
            return "none";
        }
        if (!(value instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.NumericValue numericValue)) {
            return "fixture-value-field-unsupported";
        }
        if (!supportedNumericJavaType(javaType)) {
            return "fixture-value-java-type-unsupported";
        }
        boolean arrayType = javaType.endsWith("[]");
        if (arrayType != numericValue.array()) {
            return "fixture-value-shape-mismatch";
        }
        if (isIntegralJavaType(javaType) && numericValue.numbers().stream().anyMatch(number -> !isIntegralNumber(number))) {
            return "fixture-value-integral-number-required";
        }
        return "none";
    }

    private static String structValueBlocker(
            Class<?> structType,
            GpuRuntimeMethodTestFixtureValuePayloadParser.StructValue value
    ) {
        for (Field field : fixtureStructFields(structType)) {
            GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue fieldValue = value.field(field.getName());
            if (fieldValue == null) {
                return "fixture-value-struct-field-missing";
            }
            String blocker = structFieldValueBlocker(field.getType(), fieldValue);
            if (!"none".equals(blocker)) {
                return blocker;
            }
        }
        return "none";
    }

    private static String structFieldValueBlocker(
            Class<?> fieldType,
            GpuRuntimeMethodTestFixtureValuePayloadParser.FixtureValue fieldValue
    ) {
        if (fieldType.isArray()) {
            return "fixture-value-struct-field-type-unsupported";
        }
        if (fieldType.isPrimitive()) {
            if (!(fieldValue instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.NumericValue numericValue)) {
                return "fixture-value-struct-field-shape-mismatch";
            }
            if (numericValue.array() || numericValue.numbers().size() != 1) {
                return "fixture-value-struct-field-shape-mismatch";
            }
            if (isIntegralPrimitive(fieldType) && !isIntegralNumber(numericValue.numbers().get(0))) {
                return "fixture-value-integral-number-required";
            }
            return "none";
        }
        if (isGpuStructType(fieldType)) {
            if (!(fieldValue instanceof GpuRuntimeMethodTestFixtureValuePayloadParser.StructValue structValue)) {
                return "fixture-value-struct-field-shape-mismatch";
            }
            return structValueBlocker(fieldType, structValue);
        }
        return "fixture-value-struct-field-type-unsupported";
    }

    private static boolean isIntegralPrimitive(Class<?> type) {
        return type == int.class || type == long.class || type == short.class || type == byte.class;
    }

    private static boolean supportedNumericJavaType(String javaType) {
        return switch (normalize(javaType, "")) {
            case "float", "double", "int", "long", "short", "byte",
                    "float[]", "double[]", "int[]", "long[]", "short[]", "byte[]" -> true;
            default -> false;
        };
    }

    private static boolean isIntegralJavaType(String javaType) {
        return switch (normalize(javaType, "")) {
            case "int", "long", "short", "byte", "int[]", "long[]", "short[]", "byte[]" -> true;
            default -> false;
        };
    }

    private static boolean isIntegralNumber(String number) {
        try {
            return new BigDecimal(number).stripTrailingZeros().scale() <= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static Class<?> resolveArrayComponentType(String javaType) {
        String normalizedType = normalize(javaType, "");
        if (!normalizedType.endsWith("[]")) {
            throw new IllegalArgumentException("Fixture Java type is not an array: " + javaType);
        }
        return resolveJavaType(normalizedType.substring(0, normalizedType.length() - 2));
    }

    private static Class<?> resolveJavaType(String javaType) {
        String normalizedType = normalize(javaType, "");
        return switch (normalizedType) {
            case "float" -> float.class;
            case "double" -> double.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "short" -> short.class;
            case "byte" -> byte.class;
            default -> resolveReferenceJavaType(normalizedType);
        };
    }

    private static Class<?> resolveReferenceJavaType(String javaType) {
        ClassNotFoundException lastFailure = null;
        for (String candidate : javaTypeNameCandidates(javaType)) {
            for (ClassLoader classLoader : candidateClassLoaders(null)) {
                try {
                    return Class.forName(candidate, true, classLoader);
                } catch (ClassNotFoundException exception) {
                    lastFailure = exception;
                }
            }
        }
        throw new IllegalArgumentException("Fixture Java type could not be loaded: " + javaType, lastFailure);
    }

    private static List<String> javaTypeNameCandidates(String javaType) {
        String normalizedType = normalize(javaType, "");
        ArrayList<String> candidates = new ArrayList<>();
        candidates.add(normalizedType);
        String candidate = normalizedType;
        while (candidate.contains(".")) {
            int dotIndex = candidate.lastIndexOf('.');
            candidate = candidate.substring(0, dotIndex) + '$' + candidate.substring(dotIndex + 1);
            if (!candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    private static boolean isGpuStructType(Class<?> type) {
        return type != null && GpuAnnotationSupport.hasAnnotation(type, GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES);
    }

    private static List<Field> fixtureStructFields(Class<?> structType) {
        ArrayList<Field> fields = new ArrayList<>();
        for (Field field : structType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return List.copyOf(fields);
    }

    private static byte[] readResourceBytes(URL location) throws IOException {
        try (InputStream inputStream = location.openStream()) {
            return inputStream.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private static boolean selectionVectorResourcesReady(
            GpuRuntimeMethodTestVectorPlan vector,
            List<GpuRuntimeMethodTestFixtureResourceStatus> resources
    ) {
        if (!vector.selectionProbe() || vector.expectedOutputRefs().isEmpty()) {
            return false;
        }
        LinkedHashSet<String> expectedRefs = new LinkedHashSet<>();
        expectedRefs.addAll(vector.inputRefs());
        expectedRefs.addAll(vector.expectedOutputRefs());
        Set<String> availableRefs = resources.stream()
                .filter(resource -> resource.testId().equals(vector.testId()))
                .filter(GpuRuntimeMethodTestFixtureResourceStatus::available)
                .filter(resource -> resource.payloadPreview().schemaReady())
                .map(GpuRuntimeMethodTestFixtureResourceStatus::resourceRef)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return availableRefs.containsAll(expectedRefs);
    }

    private static Optional<URL> findResource(String resourceRef, ClassLoader preferredClassLoader) {
        String normalizedRef = normalizeResourceRef(resourceRef);
        if (normalizedRef.isBlank()) {
            return Optional.empty();
        }
        for (ClassLoader classLoader : candidateClassLoaders(preferredClassLoader)) {
            URL resource = classLoader.getResource(normalizedRef);
            if (resource != null) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }

    private static List<ClassLoader> candidateClassLoaders(ClassLoader preferredClassLoader) {
        ArrayList<ClassLoader> classLoaders = new ArrayList<>();
        if (preferredClassLoader != null) {
            classLoaders.add(preferredClassLoader);
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null && !classLoaders.contains(contextClassLoader)) {
            classLoaders.add(contextClassLoader);
        }
        ClassLoader runtimeClassLoader = GpuRuntimeMethodTestProbes.class.getClassLoader();
        if (runtimeClassLoader != null && !classLoaders.contains(runtimeClassLoader)) {
            classLoaders.add(runtimeClassLoader);
        }
        return List.copyOf(classLoaders);
    }

    private static String normalizeResourceRef(String resourceRef) {
        String value = normalize(resourceRef, "");
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private static GpuRuntimeMethodTestProbePlan blocked(
            GpuKernelDescriptor descriptor,
            boolean artifactLoaded,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuRuntimeMethodTestProbePlan(
                descriptor == null ? "unknown" : descriptor.kernelName(),
                descriptor == null ? "" : descriptor.kernelResource(),
                descriptor == null ? "" : descriptor.irGpuResource(),
                artifactLoaded,
                List.of(),
                blockers,
                diagnostics
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String capitalize(String value) {
        String normalized = normalize(value, "value");
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private record MaterializedFixtureValue(
            String kind,
            int itemCount,
            Object value,
            List<String> numericValues
    ) {
        private MaterializedFixtureValue {
            numericValues = numericValues == null ? List.of() : List.copyOf(numericValues);
        }
    }

    private record ReferenceTolerance(double absolute, double relative) {

        private ReferenceTolerance {
            absolute = Double.isFinite(absolute) && absolute > 0.0d ? absolute : 0.0d;
            relative = Double.isFinite(relative) && relative > 0.0d ? relative : 0.0d;
        }

        private static ReferenceTolerance zero() {
            return new ReferenceTolerance(0.0d, 0.0d);
        }
    }

    private record NumericSnapshot(
            boolean comparable,
            String kind,
            int itemCount,
            List<String> numericValues,
            double[] values,
            String blocker
    ) {

        private NumericSnapshot {
            kind = normalize(kind, comparable ? "java-value" : "none");
            itemCount = comparable ? Math.max(itemCount, 0) : -1;
            numericValues = comparable && numericValues != null ? List.copyOf(numericValues) : List.of();
            values = comparable && values != null ? values.clone() : new double[0];
            blocker = normalize(blocker, comparable ? "none" : "fixture-reference-output-type-unsupported");
        }

        @Override
        public double[] values() {
            return values.clone();
        }

        private static NumericSnapshot blocked(String blocker) {
            return new NumericSnapshot(false, "none", -1, List.of(), new double[0], blocker);
        }
    }

    private record GpuProbeExecutionConfigResolution(
            boolean ready,
            GpuExecutionConfig executionConfig,
            String blocker
    ) {

        private GpuProbeExecutionConfigResolution {
            blocker = normalize(blocker, ready ? "none" : "fixture-gpu-probe-launch-size-missing");
        }

        private static GpuProbeExecutionConfigResolution ready(GpuExecutionConfig executionConfig) {
            return new GpuProbeExecutionConfigResolution(true, executionConfig, "none");
        }

        private static GpuProbeExecutionConfigResolution blocked(GpuExecutionConfig executionConfig, String blocker) {
            return new GpuProbeExecutionConfigResolution(false, executionConfig, blocker);
        }
    }
}
