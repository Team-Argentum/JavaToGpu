package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.OptionalInt;

/**
 * Read-only dominance proof for the first CSE temp-extraction stage.
 */
public final class GpuIrCommonSubexpressionDominanceGuard {
    private final GpuIrCommonSubexpressionLocalExpressionPathOrder localPathOrder =
            new GpuIrCommonSubexpressionLocalExpressionPathOrder();

    public boolean firstOccurrenceDominatesReplacements(GpuIrCommonSubexpression candidate) {
        return firstDominatingStatementIndex(candidate).isPresent();
    }

    public GpuIrCommonSubexpressionDominanceStatus status(GpuIrCommonSubexpression candidate) {
        if (candidate.locations().isEmpty()) {
            return GpuIrCommonSubexpressionDominanceStatus.MISSING_TOP_LEVEL_ANCHOR;
        }

        OptionalInt firstStatementIndex = topLevelStatementIndex(candidate.locations().get(0));
        if (firstStatementIndex.isEmpty()) {
            return GpuIrCommonSubexpressionDominanceStatus.MISSING_TOP_LEVEL_ANCHOR;
        }

        int previousStatementIndex = firstStatementIndex.getAsInt();
        boolean hasLaterReplacementStatement = false;
        for (int index = 1; index < candidate.locations().size(); index++) {
            OptionalInt statementIndex = topLevelStatementIndex(candidate.locations().get(index));
            if (statementIndex.isEmpty()) {
                return GpuIrCommonSubexpressionDominanceStatus.MISSING_TOP_LEVEL_ANCHOR;
            }
            if (statementIndex.getAsInt() < previousStatementIndex) {
                return GpuIrCommonSubexpressionDominanceStatus.LOCATIONS_MOVE_BACKWARDS;
            }
            if (statementIndex.getAsInt() > firstStatementIndex.getAsInt()) {
                hasLaterReplacementStatement = true;
            }
            previousStatementIndex = statementIndex.getAsInt();
        }
        if (!hasLaterReplacementStatement) {
            if (hasLocalExpressionDominance(candidate)) {
                return GpuIrCommonSubexpressionDominanceStatus.LOCAL_EXPRESSION_DOWNSTREAM_REPLACEMENTS;
            }
            return GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE;
        }
        return GpuIrCommonSubexpressionDominanceStatus.TOP_LEVEL_DOWNSTREAM_REPLACEMENTS;
    }

    public OptionalInt firstDominatingStatementIndex(GpuIrCommonSubexpression candidate) {
        GpuIrCommonSubexpressionDominanceStatus status = status(candidate);
        if (status != GpuIrCommonSubexpressionDominanceStatus.TOP_LEVEL_DOWNSTREAM_REPLACEMENTS
                && status != GpuIrCommonSubexpressionDominanceStatus.LOCAL_EXPRESSION_DOWNSTREAM_REPLACEMENTS) {
            return OptionalInt.empty();
        }
        return topLevelStatementIndex(candidate.locations().get(0));
    }

    public boolean hasDominatingOccurrence(GpuIrCommonSubexpressionDominanceStatus status) {
        return status == GpuIrCommonSubexpressionDominanceStatus.TOP_LEVEL_DOWNSTREAM_REPLACEMENTS
                || status == GpuIrCommonSubexpressionDominanceStatus.LOCAL_EXPRESSION_DOWNSTREAM_REPLACEMENTS;
    }

    private boolean hasLocalExpressionDominance(GpuIrCommonSubexpression candidate) {
        if (candidate.locations().size() < 2 || !isSupportedExpressionAnchor(candidate.locations().get(0))) {
            return false;
        }
        String previousLocation = candidate.locations().get(0);
        boolean hasLaterLocalReplacement = false;
        for (int index = 1; index < candidate.locations().size(); index++) {
            String location = candidate.locations().get(index);
            if (!sameTopLevelStatement(previousLocation, location) || !isSupportedExpressionAnchor(location)) {
                return false;
            }
            if (!localPathOrder.appearsBefore(previousLocation, location)) {
                return false;
            }
            hasLaterLocalReplacement = true;
            previousLocation = location;
        }
        return hasLaterLocalReplacement;
    }

    private boolean sameTopLevelStatement(String left, String right) {
        OptionalInt leftStatement = topLevelStatementIndex(left);
        OptionalInt rightStatement = topLevelStatementIndex(right);
        return leftStatement.isPresent()
                && rightStatement.isPresent()
                && leftStatement.getAsInt() == rightStatement.getAsInt();
    }

    private boolean isSupportedExpressionAnchor(String location) {
        return location.contains(".initializer")
                || location.contains(".value")
                || location.contains(".return");
    }

    private OptionalInt topLevelStatementIndex(String location) {
        return GpuIrCommonSubexpressionLocation.parse(location).topLevelStatementIndex();
    }
}
