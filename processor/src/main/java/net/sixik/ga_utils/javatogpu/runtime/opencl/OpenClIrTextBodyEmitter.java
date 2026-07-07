package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.ArrayList;

/**
 * Emits the first flat subset of parsed ir-text-v1 statements as OpenCL-C body text.
 *
 * <p>The emitter deliberately keeps expressions opaque because the current ir-text-v1 renderer already serializes
 * OpenCL-like expression text. Later slices can replace this with a typed expression emitter without changing the
 * source reconstruction diagnostics contract.
 */
public final class OpenClIrTextBodyEmitter {

    public static final OpenClIrTextBodyEmitter INSTANCE = new OpenClIrTextBodyEmitter();

    private OpenClIrTextBodyEmitter() {
    }

    public OpenClIrTextBodyEmissionResult emit(OpenClIrTextBodyParseResult parseResult) {
        ArrayList<String> blockers = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        if (parseResult == null || !parseResult.parsed()) {
            blockers.add("ir-text-body-not-parsed");
            diagnostics.add("OpenCL body emission skipped because ir-text-v1 parsing did not succeed");
            return new OpenClIrTextBodyEmissionResult(false, "", blockers, diagnostics);
        }

        StringBuilder builder = new StringBuilder();
        for (OpenClIrTextStatement statement : parseResult.statements()) {
            switch (statement.kind()) {
                case VARIABLE -> builder.append("    ")
                        .append(statement.typeName())
                        .append(' ')
                        .append(statement.target())
                        .append(" = ")
                        .append(statement.expression())
                        .append(";\n");
                case ASSIGNMENT -> builder.append("    ")
                        .append(statement.target())
                        .append(" = ")
                        .append(statement.expression())
                        .append(";\n");
                case RETURN -> emitReturn(builder, statement);
            }
        }

        diagnostics.add("OpenCL body emitter generated " + parseResult.statements().size() + " simple statement(s)");
        return new OpenClIrTextBodyEmissionResult(true, builder.toString(), blockers, diagnostics);
    }

    private static void emitReturn(StringBuilder builder, OpenClIrTextStatement statement) {
        builder.append("    return");
        if (!statement.expression().isBlank()) {
            builder.append(' ').append(statement.expression());
        }
        builder.append(";\n");
    }
}
