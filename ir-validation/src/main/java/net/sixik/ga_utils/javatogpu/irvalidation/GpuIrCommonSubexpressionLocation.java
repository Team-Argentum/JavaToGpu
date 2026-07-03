package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed scanner location used by read-only CSE safety checks before any IR rewrite mutates the tree.
 */
public record GpuIrCommonSubexpressionLocation(
        String rawLocation,
        OptionalInt topLevelStatementIndex,
        boolean controlFlowScoped
) {
    private static final Pattern TOP_LEVEL_STATEMENT = Pattern.compile("^stmt\\[(\\d+)]");

    public GpuIrCommonSubexpressionLocation {
        if (rawLocation == null || rawLocation.isBlank()) {
            throw new IllegalArgumentException("rawLocation must not be blank");
        }
        topLevelStatementIndex = copy(topLevelStatementIndex);
    }

    public static GpuIrCommonSubexpressionLocation parse(String rawLocation) {
        return new GpuIrCommonSubexpressionLocation(
                rawLocation,
                parseTopLevelStatementIndex(rawLocation),
                isControlFlowScoped(rawLocation)
        );
    }

    public static Optional<Integer> topLevelStatementIndexOf(String rawLocation) {
        OptionalInt statementIndex = parseTopLevelStatementIndex(rawLocation);
        return statementIndex.isPresent()
                ? Optional.of(statementIndex.getAsInt())
                : Optional.empty();
    }

    private static OptionalInt parseTopLevelStatementIndex(String rawLocation) {
        if (rawLocation == null || rawLocation.isBlank()) {
            return OptionalInt.empty();
        }
        Matcher matcher = TOP_LEVEL_STATEMENT.matcher(rawLocation);
        if (!matcher.find()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(matcher.group(1)));
    }

    private static boolean isControlFlowScoped(String rawLocation) {
        return rawLocation.contains(".then")
                || rawLocation.contains(".else")
                || rawLocation.contains(".body")
                || rawLocation.contains(".case[")
                || rawLocation.contains(".condition")
                || rawLocation.contains(".initializer") && rawLocation.contains(".stmt[")
                || rawLocation.contains(".update");
    }

    private static OptionalInt copy(OptionalInt value) {
        if (value == null || value.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(value.getAsInt());
    }
}
