package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.launch.GpuRuntimeCompileRequestSupport;

import java.util.List;
import java.util.Optional;

public record GpuFrontendCompilationResult(
        String openClSource,
        IrGpuArtifact irGpuArtifact
) {

    public String openClResource() {
        return irGpuArtifact == null ? "" : irGpuArtifact.derivedOpenClResource();
    }

    public String irGpuResource() {
        return GpuFrontendResourcePaths.irGpuResourceForOpenClResource(openClResource());
    }

    /**
     * Builds the runtime descriptor that Java and ASM frontends hand to backend compilation.
     */
    public GpuKernelDescriptor toKernelDescriptor() {
        if (irGpuArtifact == null) {
            throw new IllegalStateException("IrGpu artifact is required to build a runtime kernel descriptor");
        }
        return new GpuKernelDescriptor(
                irGpuArtifact.module().entryEmittedName(),
                openClResource(),
                openClSource,
                irGpuResource(),
                irGpuArtifact.entryParameters().stream()
                        .map(this::toParameterDescriptor)
                        .toList()
        );
    }

    public GpuRuntimeCompileRequest toRuntimeCompileRequest(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        return GpuRuntimeCompileRequestSupport.fromDescriptor(
                toKernelDescriptor(),
                compileOptions,
                deviceProfile,
                Optional.ofNullable(irGpuArtifact)
        );
    }

    public GpuRuntimeCompileRequest toRuntimeCompileRequest(GpuRuntimeDeviceProfile deviceProfile) {
        return toRuntimeCompileRequest(null, deviceProfile);
    }

    private GpuKernelParameterDescriptor toParameterDescriptor(IrGpuEntryParameter parameter) {
        return new GpuKernelParameterDescriptor(
                parameter.name(),
                parameter.javaType(),
                accessFor(parameter)
        );
    }

    private GpuKernelParameterAccess accessFor(IrGpuEntryParameter parameter) {
        List<String> qualifiers = parameter.openClQualifiers();
        if ("LOCAL".equals(parameter.addressSpace())) {
            return GpuKernelParameterAccess.LOCAL;
        }
        if (!parameter.javaType().endsWith("[]")) {
            return GpuKernelParameterAccess.VALUE;
        }
        if (parameter.constant() || qualifiers.contains("const")) {
            return GpuKernelParameterAccess.READ_ONLY;
        }
        return GpuKernelParameterAccess.READ_WRITE;
    }
}
