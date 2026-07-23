package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedback;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Example read-only compiler feedback parser discovered through ServiceLoader.
 */
public final class ExampleBackendCompilerFeedbackProvider implements GpuBackendCompilerFeedbackProvider {

    private static final Pattern REGISTERS = Pattern.compile("example\\.registers\\s*=\\s*(\\d+)");
    private static final Pattern LOCAL_MEMORY = Pattern.compile("example\\.localMemoryBytes\\s*=\\s*(\\d+)");
    private static final Pattern OCCUPANCY = Pattern.compile("example\\.occupancyPermille\\s*=\\s*(\\d+)");

    @Override
    public Optional<GpuBackendCompilerFeedback> inspect(GpuBackendCompilerFeedbackRequest request) {
        String log = request.compileLog();
        int registers = firstInt(log, REGISTERS);
        if (registers < 0) {
            return Optional.empty();
        }
        int localMemoryBytes = firstInt(log, LOCAL_MEMORY);
        int occupancyPermille = firstInt(log, OCCUPANCY);
        return Optional.of(new GpuBackendCompilerFeedback(
                extensionId(),
                extensionVersion(),
                "exampleCompilerFeedbackKernel",
                registers,
                GpuBackendCompilerFeedback.UNKNOWN,
                GpuBackendCompilerFeedback.UNKNOWN,
                0,
                0,
                0,
                localMemoryBytes,
                occupancyPermille,
                Map.of("example.provider", "matched"),
                List.of("example compiler feedback provider parsed synthetic metrics")
        ));
    }

    @Override
    public String extensionId() {
        return "examples.compiler-feedback.synthetic";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 500;
    }

    private static int firstInt(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return GpuBackendCompilerFeedback.UNKNOWN;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return GpuBackendCompilerFeedback.UNKNOWN;
        }
    }
}
