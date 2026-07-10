package net.sixik.ga_utils.javatogpu.runtime.opencl;

import dev.denismasterherobrine.packager.opencl.core.OpenClContext;
import dev.denismasterherobrine.packager.opencl.core.OpenClProgram;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.util.Locale;
import java.util.Objects;

/**
 * Runs an optional second OpenCL build for compiler diagnostics without affecting the production program.
 */
final class OpenClCompilerDiagnosticCapture {

    static final String ENABLED_PROPERTY = "javatogpu.opencl.compilerDiagnostics";
    static final String ARGS_PROPERTY = "javatogpu.opencl.compilerDiagnosticArgs";
    static final String ENABLED_ENVIRONMENT = "JTG_OPENCL_COMPILER_DIAGNOSTICS";
    static final String ARGS_ENVIRONMENT = "JTG_OPENCL_DIAGNOSTIC_COMPILE_ARGS";

    private OpenClCompilerDiagnosticCapture() {
    }

    static Result capture(
            OpenClContext context,
            String source,
            String productionBuildOptions,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        Objects.requireNonNull(context, "context");
        Configuration configuration = Configuration.resolve(deviceProfile);
        return capture(configuration, productionBuildOptions, options -> {
            OpenClProgram program = options.isBlank()
                    ? context.buildProgram(source)
                    : context.buildProgram(source, options);
            return new DiagnosticProgram() {
                @Override
                public String buildLog() {
                    return OpenClProgramBuildLogReader.read(program);
                }

                @Override
                public void close() {
                    program.close();
                }
            };
        });
    }

    static Result capture(
            Configuration configuration,
            String productionBuildOptions,
            DiagnosticProgramBuilder builder
    ) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(builder, "builder");
        if (!configuration.enabled()) {
            return new Result("disabled", "", configuration.source(), "", "diagnostic compilation is disabled");
        }
        if (configuration.diagnosticBuildOptions().isBlank()) {
            return new Result(
                    "skipped-no-options",
                    "",
                    configuration.source(),
                    "",
                    "no safe default diagnostic compile options are registered for this OpenCL vendor"
            );
        }

        String buildOptions = mergeOptions(productionBuildOptions, configuration.diagnosticBuildOptions());
        try (DiagnosticProgram program = builder.build(buildOptions)) {
            String buildLog = normalizeLog(program.buildLog());
            return new Result(
                    buildLog.isBlank() ? "completed-empty" : "recorded",
                    buildOptions,
                    configuration.source(),
                    buildLog,
                    buildLog.isBlank()
                            ? "diagnostic compilation completed but the driver returned an empty build log"
                            : "diagnostic compilation returned a driver build log"
            );
        } catch (RuntimeException exception) {
            return new Result(
                    "failed",
                    buildOptions,
                    configuration.source(),
                    "",
                    "diagnostic compilation failed without affecting the production program: "
                            + oneLine(exception.getMessage(), exception.getClass().getSimpleName())
            );
        } catch (Exception exception) {
            return new Result(
                    "failed",
                    buildOptions,
                    configuration.source(),
                    "",
                    "diagnostic program cleanup failed without affecting the production program: "
                            + oneLine(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }
    }

    private static String mergeOptions(String productionBuildOptions, String diagnosticBuildOptions) {
        String production = normalizeOptions(productionBuildOptions);
        String diagnostic = normalizeOptions(diagnosticBuildOptions);
        if (production.isBlank()) {
            return diagnostic;
        }
        if (diagnostic.isBlank()) {
            return production;
        }
        return production + " " + diagnostic;
    }

    private static String normalizeOptions(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeLog(String value) {
        return value == null ? "" : value.strip();
    }

    private static String oneLine(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    record Configuration(boolean enabled, String diagnosticBuildOptions, String source) {

        Configuration {
            diagnosticBuildOptions = normalizeOptions(diagnosticBuildOptions);
            source = source == null || source.isBlank() ? "none" : source;
        }

        static Configuration resolve(GpuRuntimeDeviceProfile deviceProfile) {
            String propertyArgs = System.getProperty(ARGS_PROPERTY, "");
            String environmentArgs = System.getenv(ARGS_ENVIRONMENT);
            String explicitArgs = !propertyArgs.isBlank() ? propertyArgs : environmentArgs;
            boolean enabled = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"))
                    || Boolean.parseBoolean(System.getenv(ENABLED_ENVIRONMENT))
                    || (explicitArgs != null && !explicitArgs.isBlank());
            if (!enabled) {
                return new Configuration(false, "", "disabled");
            }
            if (explicitArgs != null && !explicitArgs.isBlank()) {
                return new Configuration(
                        true,
                        explicitArgs,
                        !propertyArgs.isBlank() ? "system-property" : "environment"
                );
            }

            String vendor = deviceProfile == null ? "" : deviceProfile.vendor().toLowerCase(Locale.ROOT);
            if (vendor.contains("nvidia")) {
                return new Configuration(true, "-cl-nv-verbose", "nvidia-default");
            }
            return new Configuration(true, "", vendor.contains("amd") || vendor.contains("advanced micro devices")
                    ? "amd-default-missing"
                    : "vendor-default-missing");
        }
    }

    record Result(String status, String buildOptions, String source, String driverLog, String diagnostic) {

        Result {
            status = status == null || status.isBlank() ? "unknown" : status;
            buildOptions = normalizeOptions(buildOptions);
            source = source == null || source.isBlank() ? "none" : source;
            driverLog = normalizeLog(driverLog);
            diagnostic = oneLine(diagnostic, "none");
        }

        String mergeWithPrimaryLog(String primaryLog) {
            String normalizedPrimary = normalizeLog(primaryLog);
            if ("disabled".equals(status)) {
                return normalizedPrimary;
            }
            StringBuilder builder = new StringBuilder();
            builder.append("[javatogpu-opencl-compiler-diagnostics]\n");
            builder.append("status=").append(status).append('\n');
            builder.append("source=").append(source).append('\n');
            builder.append("options=").append(buildOptions).append('\n');
            builder.append("diagnostic=").append(diagnostic).append('\n');
            if (!normalizedPrimary.isBlank()) {
                builder.append("[primary-build-log]\n").append(normalizedPrimary).append('\n');
            }
            if (!driverLog.isBlank()) {
                builder.append("[diagnostic-build-log]\n").append(driverLog).append('\n');
            }
            return builder.toString().strip();
        }
    }

    @FunctionalInterface
    interface DiagnosticProgramBuilder {

        DiagnosticProgram build(String buildOptions);
    }

    interface DiagnosticProgram extends AutoCloseable {

        String buildLog();

        @Override
        void close() throws Exception;
    }
}
