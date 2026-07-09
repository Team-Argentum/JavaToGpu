package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactSerializer;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodDeviceConstraint;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodFallbackVariant;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeMethodVariantSelectorTest {

    @Test
    void prefersBestCompatibleDeviceBeforeVariantPriority() {
        GpuKernelDescriptor dgpu = descriptor("jtg_dgpu", "dgpu");
        GpuKernelDescriptor igpu = descriptor("jtg_igpu", "igpu");
        ClassLoader resources = resources(
                dgpu, artifact(dgpu, "noise", "dgpu", 10, GpuDeviceClassTarget.DGPU),
                igpu, artifact(igpu, "noise", "igpu", 100, GpuDeviceClassTarget.IGPU)
        );

        GpuRuntimeMethodVariantSelection selection = GpuRuntimeMethodVariantSelector.select(
                dgpu,
                List.of(igpu),
                resources,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                List.of(device("opencl-0", GpuDeviceClassTarget.DGPU, 80), device("opencl-1", GpuDeviceClassTarget.IGPU, 24)),
                GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns()
        );

        assertEquals("dgpu", selection.selectedVariantId());
        assertEquals("opencl-0", selection.deviceSelection().selectedDevice().orElseThrow().deviceId());
        assertTrue(selection.diagnostics().get(0).contains("selected fallback variant dgpu"));
    }

    @Test
    void explicitDeviceOverrideSelectsCompatibleFallbackVariant() {
        GpuKernelDescriptor dgpu = descriptor("jtg_dgpu", "dgpu");
        GpuKernelDescriptor igpu = descriptor("jtg_igpu", "igpu");
        ClassLoader resources = resources(
                dgpu, artifact(dgpu, "noise", "dgpu", 100, GpuDeviceClassTarget.DGPU),
                igpu, artifact(igpu, "noise", "igpu", 10, GpuDeviceClassTarget.IGPU)
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                .withDeviceOverride(GpuRuntimeDeviceOverride.byDeviceClass(GpuDeviceClassTarget.IGPU));

        GpuRuntimeMethodVariantSelection selection = GpuRuntimeMethodVariantSelector.select(
                dgpu,
                List.of(igpu),
                resources,
                options,
                List.of(device("opencl-0", GpuDeviceClassTarget.DGPU, 80), device("opencl-1", GpuDeviceClassTarget.IGPU, 24)),
                GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns()
        );

        assertEquals("igpu", selection.selectedVariantId());
        assertEquals("opencl-1", selection.deviceSelection().selectedDevice().orElseThrow().deviceId());
        assertTrue(selection.evaluations().stream()
                .anyMatch(evaluation -> evaluation.variantId().equals("dgpu") && !evaluation.accepted()));
    }

    @Test
    void reportsAbiMismatchAndFailsWhenNoVariantCanRun() {
        GpuKernelDescriptor primary = descriptor("jtg_primary", "primary");
        GpuKernelDescriptor mismatch = new GpuKernelDescriptor(
                "jtg_mismatch",
                "mismatch.cl",
                "kernel mismatch",
                "mismatch.irgpu.properties",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
        ClassLoader resources = resources(
                primary, artifact(primary, "noise", "primary", 100, GpuDeviceClassTarget.IGPU),
                mismatch, artifact(mismatch, "noise", "mismatch", 10, GpuDeviceClassTarget.DGPU)
        );

        GpuRuntimeMethodVariantSelectionException exception = assertThrows(
                GpuRuntimeMethodVariantSelectionException.class,
                () -> GpuRuntimeMethodVariantSelector.select(
                        primary,
                        List.of(mismatch),
                        resources,
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        List.of(device("opencl-0", GpuDeviceClassTarget.DGPU, 80)),
                        GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns()
                )
        );

        assertEquals(2, exception.evaluations().size());
        assertFalse(exception.evaluations().get(0).accepted());
        assertTrue(exception.evaluations().get(1).diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.contains("does not match primary ABI")));
    }

    private static GpuKernelDescriptor descriptor(String kernelName, String resourcePrefix) {
        return new GpuKernelDescriptor(
                kernelName,
                resourcePrefix + ".cl",
                "kernel " + kernelName,
                resourcePrefix + ".irgpu.properties",
                List.of(new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static IrGpuArtifact artifact(
            GpuKernelDescriptor descriptor,
            String groupId,
            String variantId,
            int priority,
            GpuDeviceClassTarget deviceClass
    ) {
        String methodName = variantId + "Method";
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(methodName, descriptor.kernelName(), List.of(), List.of(), List.of()),
                List.of(IrGpuBackendOutput.openClSource(descriptor.kernelResource())),
                "opencl",
                "off"
        ).withMethodDeviceConstraints(List.of(new IrGpuMethodDeviceConstraint(
                methodName,
                descriptor.kernelName(),
                List.of(GpuBackendTarget.OPENCL),
                List.of(),
                List.of(deviceClass),
                List.of(),
                "test"
        ))).withMethodFallbackVariants(List.of(new IrGpuMethodFallbackVariant(
                methodName,
                descriptor.kernelName(),
                groupId,
                variantId,
                priority,
                "test variant",
                "test"
        )));
    }

    private static GpuRuntimeDeviceProfile device(
            String deviceId,
            GpuDeviceClassTarget deviceClass,
            long computeUnits
    ) {
        return GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                deviceId,
                deviceClass + " test device",
                deviceClass == GpuDeviceClassTarget.DGPU ? "NVIDIA" : "Intel",
                "test-driver",
                "OpenCL 3.0 Test",
                deviceClass,
                computeUnits,
                8L * 1024L * 1024L * 1024L,
                64L * 1024L,
                1024L,
                1L,
                deviceClass == GpuDeviceClassTarget.IGPU,
                true,
                true,
                false
        );
    }

    private static ClassLoader resources(
            GpuKernelDescriptor firstDescriptor,
            IrGpuArtifact firstArtifact,
            GpuKernelDescriptor secondDescriptor,
            IrGpuArtifact secondArtifact
    ) {
        Map<String, byte[]> resources = Map.of(
                firstDescriptor.irGpuResource(), bytes(IrGpuArtifactSerializer.serialize(firstArtifact)),
                secondDescriptor.irGpuResource(), bytes(IrGpuArtifactSerializer.serialize(secondArtifact))
        );
        return new ClassLoader(GpuRuntimeMethodVariantSelectorTest.class.getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                byte[] value = resources.get(name);
                return value == null ? super.getResourceAsStream(name) : new ByteArrayInputStream(value);
            }
        };
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
