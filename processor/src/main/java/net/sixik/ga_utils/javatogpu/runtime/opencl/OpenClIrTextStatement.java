package net.sixik.ga_utils.javatogpu.runtime.opencl;

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
        String expression
) {

    public OpenClIrTextStatement {
        typeName = typeName == null ? "" : typeName;
        target = target == null ? "" : target;
        expression = expression == null ? "" : expression;
    }

    public static OpenClIrTextStatement variable(int lineNumber, String typeName, String name, String expression) {
        return new OpenClIrTextStatement(Kind.VARIABLE, lineNumber, typeName, name, expression);
    }

    public static OpenClIrTextStatement assignment(int lineNumber, String target, String expression) {
        return new OpenClIrTextStatement(Kind.ASSIGNMENT, lineNumber, "", target, expression);
    }

    public static OpenClIrTextStatement returnStatement(int lineNumber, String expression) {
        return new OpenClIrTextStatement(Kind.RETURN, lineNumber, "", "", expression);
    }

    public enum Kind {
        VARIABLE,
        ASSIGNMENT,
        RETURN
    }
}
