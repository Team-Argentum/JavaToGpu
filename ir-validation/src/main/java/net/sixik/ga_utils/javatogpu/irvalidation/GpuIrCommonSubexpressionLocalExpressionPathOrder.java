package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Scanner-order comparator for same-statement CSE expression locations.
 *
 * <p>The dominance guard uses this helper to prove that a local expression occurrence appears
 * before another sibling/child occurrence in the same supported top-level statement value.
 * Keeping the ordering here makes future local proof slices easier to test without expanding
 * the dominance guard itself.</p>
 */
public final class GpuIrCommonSubexpressionLocalExpressionPathOrder {
    public int compare(String leftLocation, String rightLocation) {
        List<PathToken> leftTokens = pathTokens(expressionPath(Objects.requireNonNull(leftLocation, "leftLocation")));
        List<PathToken> rightTokens = pathTokens(expressionPath(Objects.requireNonNull(rightLocation, "rightLocation")));
        int tokenCount = Math.min(leftTokens.size(), rightTokens.size());
        for (int index = 0; index < tokenCount; index++) {
            int comparison = leftTokens.get(index).compareTo(rightTokens.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftTokens.size(), rightTokens.size());
    }

    public boolean appearsBefore(String leftLocation, String rightLocation) {
        return compare(leftLocation, rightLocation) < 0;
    }

    public String expressionPath(String location) {
        int rootIndex = rootExpressionSegmentIndex(location);
        return rootIndex < 0 ? location : location.substring(rootIndex);
    }

    private List<PathToken> pathTokens(String path) {
        List<PathToken> tokens = new ArrayList<>();
        int index = 0;
        while (index < path.length()) {
            char current = path.charAt(index);
            if (current == '.') {
                index++;
                continue;
            }
            if (current == '[') {
                int closing = path.indexOf(']', index);
                if (closing < 0) {
                    tokens.add(PathToken.text(path.substring(index)));
                    break;
                }
                tokens.add(PathToken.index(Integer.parseInt(path.substring(index + 1, closing))));
                index = closing + 1;
                continue;
            }
            int end = index + 1;
            while (end < path.length() && path.charAt(end) != '.' && path.charAt(end) != '[') {
                end++;
            }
            tokens.add(PathToken.text(path.substring(index, end)));
            index = end;
        }
        return tokens;
    }

    private int rootExpressionSegmentIndex(String location) {
        int initializer = location.indexOf(".initializer");
        int value = location.indexOf(".value");
        int gpuReturn = location.indexOf(".return");
        int result = -1;
        if (initializer >= 0) {
            result = initializer;
        }
        if (value >= 0 && (result < 0 || value < result)) {
            result = value;
        }
        if (gpuReturn >= 0 && (result < 0 || gpuReturn < result)) {
            result = gpuReturn;
        }
        return result;
    }

    private record PathToken(String text, Integer index) implements Comparable<PathToken> {
        static PathToken text(String text) {
            return new PathToken(text, null);
        }

        static PathToken index(int index) {
            return new PathToken(null, index);
        }

        @Override
        public int compareTo(PathToken other) {
            if (index != null || other.index != null) {
                if (index == null) {
                    return -1;
                }
                if (other.index == null) {
                    return 1;
                }
                return Integer.compare(index, other.index);
            }
            int rankComparison = Integer.compare(segmentRank(text), segmentRank(other.text));
            if (rankComparison != 0) {
                return rankComparison;
            }
            return text.compareTo(other.text);
        }

        private static int segmentRank(String text) {
            return switch (text) {
                case "initializer", "value", "return" -> 0;
                case "receiver" -> 10;
                case "arg" -> 20;
                case "index" -> 30;
                case "target" -> 40;
                case "left" -> 50;
                case "right" -> 60;
                case "operand" -> 70;
                case "condition" -> 80;
                case "true" -> 90;
                case "false" -> 100;
                case "expression" -> 110;
                default -> 1_000;
            };
        }
    }
}
