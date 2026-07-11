package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDiagnosticContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeKernelCompilationException;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeKernelExecutionException;

final class OpenClFailureFormatter {

    private OpenClFailureFormatter() {
    }

    static GpuRuntimeKernelCompilationException buildFailure(
            GpuRuntimeCompileRequest compileRequest,
            Throwable cause
    ) {
        return buildFailure(
                compileRequest,
                GpuRuntimeDiagnosticContext.fromRequest(compileRequest),
                cause
        );
    }

    static GpuRuntimeKernelCompilationException buildFailure(
            GpuRuntimeCompileRequest compileRequest,
            GpuRuntimeDiagnosticContext diagnosticContext,
            Throwable cause
    ) {
        GpuKernelDescriptor descriptor = compileRequest.descriptor();
        String deviceLabel = compileRequest.deviceProfile().deviceLabel();
        return new GpuRuntimeKernelCompilationException(
                "OpenCL kernel build failed for kernel "
                        + descriptor.kernelName()
                        + " on device "
                        + deviceLabel
                        + " ["
                        + descriptor.kernelResource()
                        + "]: "
                        + rootMessage(cause)
                        + "; check the generated kernel source and enable ABI debug for layout-sensitive failures"
                        + "; if this is a repeated driver-specific failure, compare against docs/Device-Quirks.md",
                diagnosticContext,
                cause
        );
    }

    static GpuRuntimeKernelExecutionException executionFailure(
            OpenClCompiledKernel compiledKernel,
            Throwable cause
    ) {
        return executionFailure(
                compiledKernel,
                GpuRuntimeDiagnosticContext.fromSnapshot(
                        compiledKernel.descriptor(),
                        compiledKernel.artifactSnapshot()
                ),
                cause
        );
    }

    static GpuRuntimeKernelExecutionException executionFailure(
            OpenClCompiledKernel compiledKernel,
            GpuRuntimeDiagnosticContext diagnosticContext,
            Throwable cause
    ) {
        GpuKernelDescriptor descriptor = compiledKernel.descriptor();
        String deviceLabel = compiledKernel.artifactSnapshot().compileProvenance().deviceLabel();
        return new GpuRuntimeKernelExecutionException(
                "OpenCL kernel execution failed for kernel "
                        + descriptor.kernelName()
                        + " on device "
                        + deviceLabel
                        + ": "
                        + rootMessage(cause)
                        + "; if the failure is capability-related, re-run the precheck or switch to a fallback backend",
                diagnosticContext,
                cause
        );
    }

    static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = current == null ? "Unknown OpenCL failure" : current.getMessage();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        if (message == null || message.isBlank()) {
            return throwable == null ? "Unknown OpenCL failure" : throwable.getClass().getSimpleName();
        }
        return message;
    }

    static String contextualMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown OpenCL failure";
        }
        String topLevel = throwable.getMessage();
        String root = rootMessage(throwable);
        if (topLevel == null || topLevel.isBlank()) {
            return root;
        }
        if (root.equals(topLevel) || topLevel.contains(root)) {
            return topLevel;
        }
        return topLevel + ": " + root;
    }
}
