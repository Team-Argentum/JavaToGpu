package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Describes whether a generated launcher exposes the narrow return-first convenience helpers.
 */
public record GpuGeneratedLauncherReturnValueConvenienceReport(
        boolean available,
        String status,
        String reason,
        String outputParameter,
        String outputType,
        String returnType
) {

    public GpuGeneratedLauncherReturnValueConvenienceReport {
        status = status == null || status.isBlank()
                ? (available ? "available" : "unavailable")
                : status;
        reason = reason == null || reason.isBlank() ? "unspecified" : reason;
        outputParameter = outputParameter == null ? "" : outputParameter;
        outputType = outputType == null ? "" : outputType;
        returnType = returnType == null ? "" : returnType;
    }

    public String summary() {
        if (!available) {
            return "unavailable: " + reason;
        }
        return "available: returns "
                + returnType
                + " from "
                + outputParameter
                + " ("
                + outputType
                + ")";
    }

    public String toMarkdown() {
        return "Return-first convenience: " + status + '\n'
                + "Reason: " + reason + '\n'
                + "Output parameter: " + (outputParameter.isBlank() ? "n/a" : outputParameter) + '\n'
                + "Output type: " + (outputType.isBlank() ? "n/a" : outputType) + '\n'
                + "Return type: " + (returnType.isBlank() ? "n/a" : returnType) + '\n';
    }
}
