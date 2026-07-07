package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny parser for the first source-reconstruction slice of ir-text-v1.
 *
 * <p>This intentionally accepts only simple flat body statements. Unsupported lines become blockers instead of
 * exceptions so production diagnostics can explain exactly which body feature prevents OpenCL reconstruction.
 */
public final class OpenClIrTextBodyParser {

    public static final OpenClIrTextBodyParser INSTANCE = new OpenClIrTextBodyParser();

    private static final Pattern VARIABLE = Pattern.compile("^var\\s+(\\S+)\\s+(\\S+)\\s+=\\s+(.+)$");
    private static final Pattern ASSIGNMENT = Pattern.compile("^set\\s+(.+?)\\s+=\\s+(.+)$");

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
        if (bodyLine < 0 || !"body".equals(lines[bodyLine].trim())) {
            blockers.add("ir-text-body-header-missing");
            diagnostics.add("ir-text-v1 body must start with a body header");
            return new OpenClIrTextBodyParseResult(false, statements, blockers, diagnostics);
        }

        for (int index = bodyLine + 1; index < lines.length; index++) {
            String rawLine = lines[index];
            if (rawLine.isBlank()) {
                continue;
            }
            String line = rawLine.trim();
            int lineNumber = index + 1;
            Matcher variable = VARIABLE.matcher(line);
            if (variable.matches()) {
                statements.add(OpenClIrTextStatement.variable(
                        lineNumber,
                        variable.group(1),
                        variable.group(2),
                        variable.group(3)
                ));
                continue;
            }
            Matcher assignment = ASSIGNMENT.matcher(line);
            if (assignment.matches()) {
                statements.add(OpenClIrTextStatement.assignment(lineNumber, assignment.group(1), assignment.group(2)));
                continue;
            }
            if (line.equals("return")) {
                statements.add(OpenClIrTextStatement.returnStatement(lineNumber, ""));
                continue;
            }
            if (line.startsWith("return ")) {
                statements.add(OpenClIrTextStatement.returnStatement(lineNumber, line.substring("return ".length())));
                continue;
            }
            blockers.add("ir-text-line-" + lineNumber + "-unsupported-" + statementToken(line));
        }

        diagnostics.add("ir-text-v1 parser recognized " + statements.size() + " simple statement(s)");
        if (!blockers.isEmpty()) {
            diagnostics.add("ir-text-v1 parser found unsupported statement(s): " + String.join(",", blockers));
        }
        return new OpenClIrTextBodyParseResult(blockers.isEmpty(), statements, blockers, diagnostics);
    }

    private static int firstContentLine(String[] lines) {
        for (int index = 0; index < lines.length; index++) {
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

    private static String statementToken(String line) {
        String token = line.split("\\s+", 2)[0].replaceAll("[^A-Za-z0-9_-]", "-");
        return token.isBlank() ? "unknown" : token;
    }
}
