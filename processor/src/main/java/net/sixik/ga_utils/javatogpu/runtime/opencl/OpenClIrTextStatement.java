package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;

/**
 * Minimal parsed statement from the transitional ir-text-v1 body format.
 *
 * <p>The parser keeps expression text opaque for now. That lets reconstruction diagnostics validate body shape before
 * the OpenCL expression emitter is promoted to real source generation.
 */
public record OpenClIrTextStatement(
        Kind kind,
        int lineNumber,
        String typeName,
        String target,
        String expression,
        List<OpenClIrTextStatement> thenStatements,
        List<OpenClIrTextStatement> elseStatements,
        List<OpenClIrTextSwitchCase> switchCases
) {

    public OpenClIrTextStatement {
        typeName = typeName == null ? "" : typeName;
        target = target == null ? "" : target;
        expression = expression == null ? "" : expression;
        thenStatements = thenStatements == null ? List.of() : List.copyOf(thenStatements);
        elseStatements = elseStatements == null ? List.of() : List.copyOf(elseStatements);
        switchCases = switchCases == null ? List.of() : List.copyOf(switchCases);
    }

    public static OpenClIrTextStatement variable(int lineNumber, String typeName, String name, String expression) {
        return new OpenClIrTextStatement(Kind.VARIABLE, lineNumber, typeName, name, expression, List.of(), List.of(), List.of());
    }

    public static OpenClIrTextStatement assignment(int lineNumber, String target, String expression) {
        return new OpenClIrTextStatement(Kind.ASSIGNMENT, lineNumber, "", target, expression, List.of(), List.of(), List.of());
    }

    public static OpenClIrTextStatement returnStatement(int lineNumber, String expression) {
        return new OpenClIrTextStatement(Kind.RETURN, lineNumber, "", "", expression, List.of(), List.of(), List.of());
    }

    public static OpenClIrTextStatement ifStatement(
            int lineNumber,
            String condition,
            List<OpenClIrTextStatement> thenStatements,
            List<OpenClIrTextStatement> elseStatements
    ) {
        return new OpenClIrTextStatement(Kind.IF, lineNumber, "", "", condition, thenStatements, elseStatements, List.of());
    }

    public static OpenClIrTextStatement forStatement(
            int lineNumber,
            String initializer,
            String condition,
            String update,
            List<OpenClIrTextStatement> bodyStatements
    ) {
        return new OpenClIrTextStatement(Kind.FOR, lineNumber, initializer, update, condition, bodyStatements, List.of(), List.of());
    }

    public static OpenClIrTextStatement whileStatement(
            int lineNumber,
            String condition,
            List<OpenClIrTextStatement> bodyStatements
    ) {
        return new OpenClIrTextStatement(Kind.WHILE, lineNumber, "", "", condition, bodyStatements, List.of(), List.of());
    }

    public static OpenClIrTextStatement doWhileStatement(
            int lineNumber,
            String condition,
            List<OpenClIrTextStatement> bodyStatements
    ) {
        return new OpenClIrTextStatement(Kind.DO_WHILE, lineNumber, "", "", condition, bodyStatements, List.of(), List.of());
    }

    public static OpenClIrTextStatement switchStatement(
            int lineNumber,
            String selector,
            List<OpenClIrTextSwitchCase> switchCases
    ) {
        return new OpenClIrTextStatement(Kind.SWITCH, lineNumber, "", "", selector, List.of(), List.of(), switchCases);
    }

    public static OpenClIrTextStatement breakStatement(int lineNumber) {
        return new OpenClIrTextStatement(Kind.BREAK, lineNumber, "", "", "", List.of(), List.of(), List.of());
    }

    public static OpenClIrTextStatement continueStatement(int lineNumber) {
        return new OpenClIrTextStatement(Kind.CONTINUE, lineNumber, "", "", "", List.of(), List.of(), List.of());
    }

    public enum Kind {
        VARIABLE,
        ASSIGNMENT,
        RETURN,
        IF,
        FOR,
        WHILE,
        DO_WHILE,
        SWITCH,
        BREAK,
        CONTINUE
    }
}
