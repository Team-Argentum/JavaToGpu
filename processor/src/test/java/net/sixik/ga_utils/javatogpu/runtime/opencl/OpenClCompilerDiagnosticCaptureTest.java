package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClCompilerDiagnosticCaptureTest {

    @Test
    void disabledCaptureDoesNotRunDiagnosticBuilder() {
        AtomicBoolean invoked = new AtomicBoolean();

        OpenClCompilerDiagnosticCapture.Result result = OpenClCompilerDiagnosticCapture.capture(
                new OpenClCompilerDiagnosticCapture.Configuration(false, "", "disabled"),
                "-cl-fast-relaxed-math",
                options -> {
                    invoked.set(true);
                    throw new AssertionError("disabled diagnostic builder must not run");
                }
        );

        assertFalse(invoked.get());
        assertEquals("disabled", result.status());
        assertEquals("primary log", result.mergeWithPrimaryLog("primary log"));
    }

    @Test
    void nvidiaConfigurationUsesVerboseDiagnosticBuildByDefault() {
        String previousEnabled = System.getProperty(OpenClCompilerDiagnosticCapture.ENABLED_PROPERTY);
        String previousArgs = System.getProperty(OpenClCompilerDiagnosticCapture.ARGS_PROPERTY);
        try {
            System.setProperty(OpenClCompilerDiagnosticCapture.ENABLED_PROPERTY, "true");
            System.clearProperty(OpenClCompilerDiagnosticCapture.ARGS_PROPERTY);

            OpenClCompilerDiagnosticCapture.Configuration configuration =
                    OpenClCompilerDiagnosticCapture.Configuration.resolve(device("NVIDIA Corporation"));

            assertTrue(configuration.enabled());
            assertEquals("-cl-nv-verbose", configuration.diagnosticBuildOptions());
            assertEquals("nvidia-default", configuration.source());
        } finally {
            restoreProperty(OpenClCompilerDiagnosticCapture.ENABLED_PROPERTY, previousEnabled);
            restoreProperty(OpenClCompilerDiagnosticCapture.ARGS_PROPERTY, previousArgs);
        }
    }

    @Test
    void amdCaptureSkipsWhenNoExplicitSafeOptionsAreConfigured() {
        AtomicBoolean invoked = new AtomicBoolean();

        OpenClCompilerDiagnosticCapture.Result result = OpenClCompilerDiagnosticCapture.capture(
                new OpenClCompilerDiagnosticCapture.Configuration(true, "", "amd-default-missing"),
                "",
                options -> {
                    invoked.set(true);
                    throw new AssertionError("AMD diagnostic builder must not run without configured options");
                }
        );

        assertFalse(invoked.get());
        assertEquals("skipped-no-options", result.status());
        assertTrue(result.mergeWithPrimaryLog("").contains("source=amd-default-missing"));
    }

    @Test
    void explicitDiagnosticBuildMergesProductionOptionsAndDriverLog() {
        AtomicReference<String> capturedOptions = new AtomicReference<>();

        OpenClCompilerDiagnosticCapture.Result result = OpenClCompilerDiagnosticCapture.capture(
                new OpenClCompilerDiagnosticCapture.Configuration(true, "-vendor-diagnostics", "system-property"),
                "-cl-fast-relaxed-math",
                options -> {
                    capturedOptions.set(options);
                    return diagnosticProgram("Used 28 registers, 0 bytes spill stores, 0 bytes spill loads");
                }
        );

        assertEquals("-cl-fast-relaxed-math -vendor-diagnostics", capturedOptions.get());
        assertEquals("recorded", result.status());
        String compileLog = result.mergeWithPrimaryLog("primary warning");
        assertTrue(compileLog.contains("[primary-build-log]"));
        assertTrue(compileLog.contains("[diagnostic-build-log]"));
        assertTrue(compileLog.contains("Used 28 registers"));
    }

    @Test
    void diagnosticBuildFailureIsRecordedWithoutEscaping() {
        OpenClCompilerDiagnosticCapture.Result result = OpenClCompilerDiagnosticCapture.capture(
                new OpenClCompilerDiagnosticCapture.Configuration(true, "-unsupported-option", "test"),
                "",
                options -> {
                    throw new IllegalArgumentException("driver rejected option");
                }
        );

        assertEquals("failed", result.status());
        assertTrue(result.diagnostic().contains("driver rejected option"));
        assertTrue(result.mergeWithPrimaryLog("").contains("status=failed"));
    }

    private static OpenClCompilerDiagnosticCapture.DiagnosticProgram diagnosticProgram(String buildLog) {
        return new OpenClCompilerDiagnosticCapture.DiagnosticProgram() {
            @Override
            public String buildLog() {
                return buildLog;
            }

            @Override
            public void close() {
            }
        };
    }

    private static GpuRuntimeDeviceProfile device(String vendor) {
        return new GpuRuntimeDeviceProfile(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "Test GPU",
                vendor,
                "driver",
                "OpenCL 3.0"
        );
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
