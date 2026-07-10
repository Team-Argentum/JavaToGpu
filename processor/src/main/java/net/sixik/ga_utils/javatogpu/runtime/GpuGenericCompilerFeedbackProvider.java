package net.sixik.ga_utils.javatogpu.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative built-in parser for common NVIDIA, AMD, Intel, and generic compiler log formats.
 */
public final class GpuGenericCompilerFeedbackProvider implements GpuBackendCompilerFeedbackProvider {

    public static final String PROVIDER_ID = "compiler-feedback:generic";
    public static final String PROVIDER_VERSION = "generic-compiler-feedback:v1";

    private static final Pattern NVIDIA_REGISTERS = pattern("\\bUsed\\s+(\\d+)\\s+registers?\\b");
    private static final Pattern GENERAL_REGISTERS = pattern("^\\s*registers?\\s*[:=]\\s*(\\d+)\\b");
    private static final Pattern VECTOR_REGISTERS = pattern("\\bVGPRs?\\s*[:=]\\s*(\\d+)\\b");
    private static final Pattern SCALAR_REGISTERS = pattern("\\bSGPRs?\\s*[:=]\\s*(\\d+)\\b");
    private static final Pattern SPILL_STORES = pattern("(\\d+)\\s+bytes?\\s+spill stores?\\b");
    private static final Pattern SPILL_LOADS = pattern("(\\d+)\\s+bytes?\\s+spill loads?\\b");
    private static final Pattern SPILL_SIZE = pattern("\\bspill size\\s*[:=]\\s*(\\d+)\\b");
    private static final Pattern STACK_FRAME = pattern("\\bstack frame(?: size)?\\s*[:=]?\\s*(\\d+)\\s*bytes?\\b");
    private static final Pattern STACK_FRAME_BYTES_FIRST = pattern("(\\d+)\\s*bytes?\\s+stack frame\\b");
    private static final Pattern SCRATCH = pattern("\\bscratch(?: size)?\\s*[:=]\\s*(\\d+)\\s*bytes?\\b");
    private static final Pattern LOCAL_MEMORY = pattern("\\b(?:local|shared) memory(?: size)?\\s*[:=]?\\s*(\\d+)\\s*bytes?\\b");
    private static final Pattern LOCAL_MEMORY_BYTES_FIRST = pattern("(\\d+)\\s*bytes?\\s+(?:local|shared) memory\\b");
    private static final Pattern OCCUPANCY = pattern("\\boccupancy\\s*[:=]\\s*(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern KERNEL_NAME = pattern("\\bFunction properties for\\s+([^\\r\\n]+)");

    @Override
    public Optional<GpuBackendCompilerFeedback> inspect(GpuBackendCompilerFeedbackRequest request) {
        java.util.Objects.requireNonNull(request, "request");
        String log = request.compileLog();
        if (log.isBlank()) {
            return Optional.empty();
        }

        int generalRegisters = firstInt(log, NVIDIA_REGISTERS, GENERAL_REGISTERS);
        int vectorRegisters = firstInt(log, VECTOR_REGISTERS);
        int scalarRegisters = firstInt(log, SCALAR_REGISTERS);
        int spillStores = firstInt(log, SPILL_STORES);
        int spillLoads = firstInt(log, SPILL_LOADS);
        int spillSize = firstInt(log, SPILL_SIZE);
        int stackFrame = firstInt(log, STACK_FRAME, STACK_FRAME_BYTES_FIRST);
        int scratch = firstInt(log, SCRATCH);
        int localMemory = firstInt(log, LOCAL_MEMORY, LOCAL_MEMORY_BYTES_FIRST);
        int occupancyPermille = occupancyPermille(log);

        if (spillStores < 0 && spillSize >= 0) {
            spillStores = spillSize;
        }
        if (stackFrame < 0 && scratch >= 0) {
            stackFrame = scratch;
        }

        LinkedHashMap<String, String> rawFields = new LinkedHashMap<>();
        putKnown(rawFields, "spillSizeBytes", spillSize);
        putKnown(rawFields, "scratchBytes", scratch);
        GpuBackendCompilerFeedback feedback = new GpuBackendCompilerFeedback(
                extensionId(),
                extensionVersion(),
                firstText(log, KERNEL_NAME),
                generalRegisters,
                vectorRegisters,
                scalarRegisters,
                spillStores,
                spillLoads,
                stackFrame,
                localMemory,
                occupancyPermille,
                rawFields,
                List.of("compiler resource metrics were parsed conservatively from backend diagnostics")
        );
        return feedback.available() ? Optional.of(feedback) : Optional.empty();
    }

    @Override
    public String extensionId() {
        return PROVIDER_ID;
    }

    @Override
    public String extensionVersion() {
        return PROVIDER_VERSION;
    }

    /**
     * Backend-specific providers run first and can provide a more precise interpretation.
     */
    @Override
    public int extensionOrder() {
        return 1_000;
    }

    private static Pattern pattern(String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    }

    private static int firstInt(String text, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return GpuBackendCompilerFeedback.UNKNOWN;
                }
            }
        }
        return GpuBackendCompilerFeedback.UNKNOWN;
    }

    private static String firstText(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static int occupancyPermille(String text) {
        Matcher matcher = OCCUPANCY.matcher(text);
        if (!matcher.find()) {
            return GpuBackendCompilerFeedback.UNKNOWN;
        }
        try {
            return (int) Math.round(Double.parseDouble(matcher.group(1)) * 10.0);
        } catch (NumberFormatException ignored) {
            return GpuBackendCompilerFeedback.UNKNOWN;
        }
    }

    private static void putKnown(LinkedHashMap<String, String> fields, String key, int value) {
        if (value >= 0) {
            fields.put(key, Integer.toString(value));
        }
    }
}
