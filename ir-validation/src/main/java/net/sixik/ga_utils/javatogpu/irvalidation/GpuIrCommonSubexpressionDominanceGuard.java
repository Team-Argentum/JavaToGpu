package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only dominance proof for the first CSE temp-extraction stage.
 */
public final class GpuIrCommonSubexpressionDominanceGuard {
    private static final Pattern TOP_LEVEL_STATEMENT = Pattern.compile("^stmt\\[(\\d+)]");

    public boolean firstOccurrenceDominatesReplacements(GpuIrCommonSubexpression candidate) {
        if (candidate.locations().isEmpty()) {
            return false;
        }

        int previousStatementIndex = -1;
        for (String location : candidate.locations()) {
            OptionalInt statementIndex = topLevelStatementIndex(location);
            if (statementIndex.isEmpty()) {
                return false;
            }
            if (statementIndex.getAsInt() < previousStatementIndex) {
                return false;
            }
            previousStatementIndex = statementIndex.getAsInt();
        }
        return true;
    }

    private OptionalInt topLevelStatementIndex(String location) {
        Matcher matcher = TOP_LEVEL_STATEMENT.matcher(location);
        if (!matcher.find()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(matcher.group(1)));
    }
}
