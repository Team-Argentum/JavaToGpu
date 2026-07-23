package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Map;

/**
 * Small facade used by core services to publish framework-neutral runtime logs.
 */
public final class GpuRuntimeLogger {

    public static final String LOG_PROPERTY = "javatogpu.runtime.log";

    private static volatile GpuRuntimeLogBus configuredBus;

    private GpuRuntimeLogger() {
    }

    public static void use(GpuRuntimeLogBus bus) {
        configuredBus = bus == null ? GpuRuntimeLogBus.empty() : bus;
    }

    public static void reset() {
        configuredBus = null;
    }

    public static GpuRuntimeLogDispatchReport log(GpuRuntimeLogRecord record) {
        GpuRuntimeLogBus bus = configuredBus == null ? GpuRuntimeLogBus.loadDefault() : configuredBus;
        return bus.publish(record);
    }

    public static GpuRuntimeLogDispatchReport trace(String loggerName, String message) {
        return log(GpuRuntimeLogRecord.of(GpuRuntimeLogLevel.TRACE, loggerName, message));
    }

    public static GpuRuntimeLogDispatchReport debug(String loggerName, String message) {
        return log(GpuRuntimeLogRecord.of(GpuRuntimeLogLevel.DEBUG, loggerName, message));
    }

    public static GpuRuntimeLogDispatchReport info(String loggerName, String message) {
        return log(GpuRuntimeLogRecord.of(GpuRuntimeLogLevel.INFO, loggerName, message));
    }

    public static GpuRuntimeLogDispatchReport warn(String loggerName, String message) {
        return log(GpuRuntimeLogRecord.of(GpuRuntimeLogLevel.WARN, loggerName, message));
    }

    public static GpuRuntimeLogDispatchReport error(String loggerName, String message, Throwable throwable) {
        return log(new GpuRuntimeLogRecord(
                GpuRuntimeLogLevel.ERROR,
                loggerName,
                message,
                Map.of(),
                throwable
        ));
    }
}
