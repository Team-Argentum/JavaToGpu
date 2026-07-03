package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.OptionalInt;

/**
 * Read-only dominance proof for the first CSE temp-extraction stage.
 */
public final class GpuIrCommonSubexpressionDominanceGuard {
    public boolean firstOccurrenceDominatesReplacements(GpuIrCommonSubexpression candidate) {
        return firstDominatingStatementIndex(candidate).isPresent();
    }

    public OptionalInt firstDominatingStatementIndex(GpuIrCommonSubexpression candidate) {
        if (candidate.locations().isEmpty()) {
            return OptionalInt.empty();
        }

        OptionalInt firstStatementIndex = topLevelStatementIndex(candidate.locations().getFirst());
        if (firstStatementIndex.isEmpty()) {
            return OptionalInt.empty();
        }

        int previousStatementIndex = firstStatementIndex.getAsInt();
        for (int index = 1; index < candidate.locations().size(); index++) {
            OptionalInt statementIndex = topLevelStatementIndex(candidate.locations().get(index));
            if (statementIndex.isEmpty()) {
                return OptionalInt.empty();
            }
            if (statementIndex.getAsInt() < previousStatementIndex) {
                return OptionalInt.empty();
            }
            previousStatementIndex = statementIndex.getAsInt();
        }
        return firstStatementIndex;
    }

    private OptionalInt topLevelStatementIndex(String location) {
        return GpuIrCommonSubexpressionLocation.parse(location).topLevelStatementIndex();
    }
}
