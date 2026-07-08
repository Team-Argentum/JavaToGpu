package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny parser for the first source-reconstruction slice of ir-text-v1.
 *
 * <p>This intentionally accepts only a small statement subset. Unsupported lines become blockers instead of exceptions
 * so production diagnostics can explain exactly which body feature prevents OpenCL reconstruction.
 */
public final class OpenClIrTextBodyParser {

    public static final OpenClIrTextBodyParser INSTANCE = new OpenClIrTextBodyParser();

    private static final Pattern VARIABLE = Pattern.compile("^var\\s+(\\S+)\\s+(\\S+)\\s+=\\s+(.+)$");
    private static final Pattern PRIVATE_ARRAY = Pattern.compile("^private-array\\s+(\\S+)\\s+(\\S+)\\[(.+)]$");
    private static final Pattern ASSIGNMENT = Pattern.compile("^set\\s+(.+?)\\s+=\\s+(.+)$");
    private static final Pattern FOR = Pattern.compile("^for\\s+init=\\((.*)\\)\\s+cond=(.*)\\s+update=\\((.*)\\)$");

    private OpenClIrTextBodyParser() {
    }

    public OpenClIrTextBodyParseResult parse(String body) {
        ArrayList<OpenClIrTextStatement> statements = new ArrayList<>();
        ArrayList<String> blockers = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        if (body == null || body.isBlank()) {
            blockers.add("ir-text-body-empty");
            diagnostics.add("ir-text-v1 body is empty");
            return new OpenClIrTextBodyParseResult(false, statements, blockers, diagnostics);
        }

        String[] lines = normalizeLines(body).split("\n", -1);
        int bodyLine = firstContentLine(lines);
        if (bodyLine >= 0 && lines[bodyLine].trim().startsWith("method ")) {
            bodyLine = firstContentLine(lines, bodyLine + 1);
        }
        if (bodyLine >= 0 && lines[bodyLine].trim().startsWith("helpers ")) {
            bodyLine = firstContentLine(lines, bodyLine + 1);
        }
        if (bodyLine < 0 || !"body".equals(lines[bodyLine].trim())) {
            blockers.add("ir-text-body-header-missing");
            diagnostics.add("ir-text-v1 body must start with a body header");
            return new OpenClIrTextBodyParseResult(false, statements, blockers, diagnostics);
        }

        ParseCursor cursor = parseBlock(lines, bodyLine + 1, 1, statements, blockers);
        collectTrailingUnsupportedLines(lines, cursor.index(), blockers);

        diagnostics.add("ir-text-v1 parser recognized " + statements.size() + " simple statement(s)");
        if (!blockers.isEmpty()) {
            appendUnsupportedSummary(blockers, diagnostics);
            diagnostics.add("ir-text-v1 parser found unsupported statement(s): " + String.join(",", blockers));
        }
        return new OpenClIrTextBodyParseResult(blockers.isEmpty(), statements, blockers, diagnostics);
    }

    private static void appendUnsupportedSummary(List<String> blockers, List<String> diagnostics) {
        LinkedHashMap<String, Integer> tokenCounts = new LinkedHashMap<>();
        for (String blocker : blockers) {
            unsupportedToken(blocker).ifPresent(token -> tokenCounts.merge(token, 1, Integer::sum));
        }
        diagnostics.add("ir-text-v1 parser blocker.count=" + blockers.size());
        diagnostics.add("ir-text-v1 parser unsupported.token.count=" + tokenCounts.size());
        int index = 0;
        for (Map.Entry<String, Integer> tokenCount : tokenCounts.entrySet()) {
            diagnostics.add("ir-text-v1 parser unsupported.token." + index + '='
                    + tokenCount.getKey() + ":" + tokenCount.getValue());
            index++;
        }
    }

    private static ParseCursor parseBlock(
            String[] lines,
            int startIndex,
            int expectedIndentLevel,
            List<OpenClIrTextStatement> statements,
            List<String> blockers
    ) {
        int index = startIndex;
        while (index < lines.length) {
            String rawLine = lines[index];
            if (rawLine.isBlank()) {
                index++;
                continue;
            }
            int indentLevel = indentLevel(rawLine);
            if (indentLevel < expectedIndentLevel) {
                break;
            }
            String line = rawLine.trim();
            if (indentLevel > expectedIndentLevel) {
                blockers.add("ir-text-line-" + (index + 1) + "-unexpected-indent");
                index++;
                continue;
            }
            if ("else".equals(line) || line.startsWith("else if ")) {
                break;
            }
            if (isSwitchCaseHeader(line)) {
                break;
            }

            ParseStatementResult parsed = parseStatement(lines, index, expectedIndentLevel, blockers);
            parsed.statement().ifPresent(statements::add);
            index = parsed.nextIndex();
        }
        return new ParseCursor(index);
    }

    private static ParseStatementResult parseStatement(
            String[] lines,
            int index,
            int indentLevel,
            List<String> blockers
    ) {
        String line = lines[index].trim();
        int lineNumber = index + 1;
        Matcher variable = VARIABLE.matcher(line);
        if (variable.matches()) {
            return ParseStatementResult.parsed(
                    OpenClIrTextStatement.variable(lineNumber, variable.group(1), variable.group(2), variable.group(3)),
                    index + 1
            );
        }
        Matcher privateArray = PRIVATE_ARRAY.matcher(line);
        if (privateArray.matches()) {
            return ParseStatementResult.parsed(
                    OpenClIrTextStatement.privateArray(lineNumber, privateArray.group(1), privateArray.group(2), privateArray.group(3)),
                    index + 1
            );
        }
        Matcher assignment = ASSIGNMENT.matcher(line);
        if (assignment.matches()) {
            return ParseStatementResult.parsed(
                    OpenClIrTextStatement.assignment(lineNumber, assignment.group(1), assignment.group(2)),
                    index + 1
            );
        }
        if (line.startsWith("expr ")) {
            return ParseStatementResult.parsed(
                    OpenClIrTextStatement.expressionStatement(lineNumber, line.substring("expr ".length())),
                    index + 1
            );
        }
        if (line.equals("expr")) {
            blockers.add("ir-text-line-" + lineNumber + "-expr-empty");
            return ParseStatementResult.unparsed(index + 1);
        }
        if (line.equals("return")) {
            return ParseStatementResult.parsed(OpenClIrTextStatement.returnStatement(lineNumber, ""), index + 1);
        }
        if (line.startsWith("return ")) {
            return ParseStatementResult.parsed(
                    OpenClIrTextStatement.returnStatement(lineNumber, line.substring("return ".length())),
                    index + 1
            );
        }
        if (line.equals("break") || line.equals("loop-break")) {
            return ParseStatementResult.parsed(OpenClIrTextStatement.breakStatement(lineNumber), index + 1);
        }
        if (line.equals("continue")) {
            return ParseStatementResult.parsed(OpenClIrTextStatement.continueStatement(lineNumber), index + 1);
        }
        if (line.startsWith("if ")) {
            return parseIf(lines, index, indentLevel, blockers);
        }
        if (line.startsWith("while ")) {
            return parseWhile(lines, index, indentLevel, blockers);
        }
        if ("do".equals(line)) {
            return parseDoWhile(lines, index, indentLevel, blockers);
        }
        if (line.startsWith("switch ")) {
            return parseSwitch(lines, index, indentLevel, blockers);
        }
        Matcher forLoop = FOR.matcher(line);
        if (forLoop.matches()) {
            return parseFor(lines, index, indentLevel, forLoop, blockers);
        }
        blockers.add("ir-text-line-" + lineNumber + "-unsupported-" + statementToken(line));
        return ParseStatementResult.unparsed(index + 1);
    }

    private static ParseStatementResult parseIf(
            String[] lines,
            int index,
            int indentLevel,
            List<String> blockers
    ) {
        String condition = lines[index].trim().substring("if ".length());
        ArrayList<OpenClIrTextStatement> thenStatements = new ArrayList<>();
        ArrayList<OpenClIrTextStatement> elseStatements = new ArrayList<>();
        ParseCursor thenCursor = parseBlock(lines, index + 1, indentLevel + 1, thenStatements, blockers);
        int nextIndex = thenCursor.index();
        if (thenStatements.isEmpty()) {
            blockers.add("ir-text-line-" + (index + 1) + "-if-body-empty");
        }
        if (nextIndex < lines.length && indentLevel(lines[nextIndex]) == indentLevel) {
            String nextLine = lines[nextIndex].trim();
            if (nextLine.startsWith("else if ")) {
                ParseStatementResult elseIf = parseIf(
                        replaceTrimmedLine(lines, nextIndex, nextLine.substring("else ".length())),
                        nextIndex,
                        indentLevel,
                        blockers
                );
                elseIf.statement().ifPresent(elseStatements::add);
                nextIndex = elseIf.nextIndex();
            } else if ("else".equals(nextLine)) {
                ParseCursor elseCursor = parseBlock(lines, nextIndex + 1, indentLevel + 1, elseStatements, blockers);
                nextIndex = elseCursor.index();
                if (elseStatements.isEmpty()) {
                    blockers.add("ir-text-line-" + (nextIndex + 1) + "-else-body-empty");
                }
            }
        }
        return ParseStatementResult.parsed(
                OpenClIrTextStatement.ifStatement(index + 1, condition, thenStatements, elseStatements),
                nextIndex
        );
    }

    private static ParseStatementResult parseFor(
            String[] lines,
            int index,
            int indentLevel,
            Matcher forLoop,
            List<String> blockers
    ) {
        ArrayList<OpenClIrTextStatement> bodyStatements = new ArrayList<>();
        ParseCursor bodyCursor = parseBlock(lines, index + 1, indentLevel + 1, bodyStatements, blockers);
        if (bodyStatements.isEmpty()) {
            blockers.add("ir-text-line-" + (index + 1) + "-for-body-empty");
        }
        validateForHeaderStatement(forLoop.group(1), index + 1, "init", blockers);
        validateForHeaderStatement(forLoop.group(3), index + 1, "update", blockers);
        return ParseStatementResult.parsed(
                OpenClIrTextStatement.forStatement(
                        index + 1,
                        forLoop.group(1),
                        forLoop.group(2),
                        forLoop.group(3),
                        bodyStatements
                ),
                bodyCursor.index()
        );
    }

    private static ParseStatementResult parseWhile(
            String[] lines,
            int index,
            int indentLevel,
            List<String> blockers
    ) {
        String condition = lines[index].trim().substring("while ".length());
        ArrayList<OpenClIrTextStatement> bodyStatements = new ArrayList<>();
        ParseCursor bodyCursor = parseBlock(lines, index + 1, indentLevel + 1, bodyStatements, blockers);
        if (bodyStatements.isEmpty()) {
            blockers.add("ir-text-line-" + (index + 1) + "-while-body-empty");
        }
        return ParseStatementResult.parsed(
                OpenClIrTextStatement.whileStatement(index + 1, condition, bodyStatements),
                bodyCursor.index()
        );
    }

    private static ParseStatementResult parseDoWhile(
            String[] lines,
            int index,
            int indentLevel,
            List<String> blockers
    ) {
        ArrayList<OpenClIrTextStatement> bodyStatements = new ArrayList<>();
        ParseCursor bodyCursor = parseBlock(lines, index + 1, indentLevel + 1, bodyStatements, blockers);
        int nextIndex = bodyCursor.index();
        if (bodyStatements.isEmpty()) {
            blockers.add("ir-text-line-" + (index + 1) + "-do-body-empty");
        }
        if (nextIndex >= lines.length
                || indentLevel(lines[nextIndex]) != indentLevel
                || !lines[nextIndex].trim().startsWith("while ")) {
            blockers.add("ir-text-line-" + (index + 1) + "-do-while-condition-missing");
            return ParseStatementResult.parsed(
                    OpenClIrTextStatement.doWhileStatement(index + 1, "", bodyStatements),
                    nextIndex
            );
        }
        String condition = lines[nextIndex].trim().substring("while ".length());
        return ParseStatementResult.parsed(
                OpenClIrTextStatement.doWhileStatement(index + 1, condition, bodyStatements),
                nextIndex + 1
        );
    }

    private static ParseStatementResult parseSwitch(
            String[] lines,
            int index,
            int indentLevel,
            List<String> blockers
    ) {
        String selector = lines[index].trim().substring("switch ".length());
        ArrayList<OpenClIrTextSwitchCase> switchCases = new ArrayList<>();
        int nextIndex = index + 1;
        while (nextIndex < lines.length) {
            String rawLine = lines[nextIndex];
            if (rawLine.isBlank()) {
                nextIndex++;
                continue;
            }
            int caseIndent = indentLevel(rawLine);
            if (caseIndent <= indentLevel) {
                break;
            }
            if (caseIndent > indentLevel + 1) {
                blockers.add("ir-text-line-" + (nextIndex + 1) + "-unexpected-indent");
                nextIndex++;
                continue;
            }
            String line = rawLine.trim();
            if (!isSwitchCaseHeader(line)) {
                blockers.add("ir-text-line-" + (nextIndex + 1) + "-switch-case-expected");
                nextIndex++;
                continue;
            }
            ArrayList<OpenClIrTextStatement> caseStatements = new ArrayList<>();
            ParseCursor caseCursor = parseBlock(lines, nextIndex + 1, indentLevel + 2, caseStatements, blockers);
            if (caseStatements.isEmpty()) {
                blockers.add("ir-text-line-" + (nextIndex + 1) + "-switch-case-body-empty");
            }
            if ("default".equals(line)) {
                switchCases.add(OpenClIrTextSwitchCase.defaultBlock(nextIndex + 1, caseStatements));
            } else {
                switchCases.add(OpenClIrTextSwitchCase.caseBlock(nextIndex + 1, parseCaseLabels(line), caseStatements));
            }
            nextIndex = caseCursor.index();
        }
        if (switchCases.isEmpty()) {
            blockers.add("ir-text-line-" + (index + 1) + "-switch-cases-empty");
        }
        return ParseStatementResult.parsed(
                OpenClIrTextStatement.switchStatement(index + 1, selector, switchCases),
                nextIndex
        );
    }

    private static void validateForHeaderStatement(
            String headerStatement,
            int lineNumber,
            String role,
            List<String> blockers
    ) {
        String statement = headerStatement == null ? "" : headerStatement.trim();
        if (VARIABLE.matcher(statement).matches()
                || PRIVATE_ARRAY.matcher(statement).matches()
                || ASSIGNMENT.matcher(statement).matches()) {
            return;
        }
        blockers.add("ir-text-line-" + lineNumber + "-for-" + role + "-unsupported-" + statementToken(statement));
    }

    private static void collectTrailingUnsupportedLines(String[] lines, int startIndex, List<String> blockers) {
        for (int index = startIndex; index < lines.length; index++) {
            if (!lines[index].isBlank()) {
                blockers.add("ir-text-line-" + (index + 1) + "-unsupported-" + statementToken(lines[index].trim()));
            }
        }
    }

    private static int firstContentLine(String[] lines) {
        return firstContentLine(lines, 0);
    }

    private static int firstContentLine(String[] lines, int startIndex) {
        for (int index = startIndex; index < lines.length; index++) {
            if (!lines[index].isBlank()) {
                return index;
            }
        }
        return -1;
    }

    private static String normalizeLines(String body) {
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.contains("\\n") && !normalized.contains("\n")) {
            return normalized.replace("\\n", "\n");
        }
        return normalized;
    }

    private static String[] replaceTrimmedLine(String[] lines, int index, String replacement) {
        String[] copy = lines.clone();
        copy[index] = "  ".repeat(indentLevel(lines[index])) + replacement;
        return copy;
    }

    private static int indentLevel(String rawLine) {
        int spaces = 0;
        while (spaces < rawLine.length() && rawLine.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces / 2;
    }

    private static String statementToken(String line) {
        String token = line.split("\\s+", 2)[0].replaceAll("[^A-Za-z0-9_-]", "-");
        return token.isBlank() ? "unknown" : token;
    }

    private static java.util.Optional<String> unsupportedToken(String blocker) {
        String marker = "-unsupported-";
        int markerIndex = blocker.lastIndexOf(marker);
        if (markerIndex < 0) {
            return java.util.Optional.empty();
        }
        String token = blocker.substring(markerIndex + marker.length()).trim();
        return token.isBlank() ? java.util.Optional.of("unknown") : java.util.Optional.of(token);
    }

    private static boolean isSwitchCaseHeader(String line) {
        return "default".equals(line) || line.startsWith("case ");
    }

    private static List<String> parseCaseLabels(String line) {
        String labels = line.substring("case ".length()).trim();
        if (labels.isBlank()) {
            return List.of();
        }
        ArrayList<String> parsedLabels = new ArrayList<>();
        for (String label : labels.split(",")) {
            String trimmed = label.trim();
            if (!trimmed.isBlank()) {
                parsedLabels.add(trimmed);
            }
        }
        return parsedLabels;
    }

    private record ParseCursor(int index) {
    }

    private record ParseStatementResult(java.util.Optional<OpenClIrTextStatement> statement, int nextIndex) {

        private static ParseStatementResult parsed(OpenClIrTextStatement statement, int nextIndex) {
            return new ParseStatementResult(java.util.Optional.of(statement), nextIndex);
        }

        private static ParseStatementResult unparsed(int nextIndex) {
            return new ParseStatementResult(java.util.Optional.empty(), nextIndex);
        }
    }
}
