package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrPrivateArrayDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only guard that rejects reuse candidates when operands may be reassigned between occurrences.
 */
public final class GpuIrCommonSubexpressionMutationGuard {
    private static final Pattern TOP_LEVEL_STATEMENT = Pattern.compile("^stmt\\[(\\d+)]");
    private static final Pattern VARIABLE_FINGERPRINT = Pattern.compile("var\\(([^)]*)\\)");
    private static final Pattern ARRAY_FINGERPRINT = Pattern.compile("array\\(([^,)]*)");

    public boolean isStableBetweenOccurrences(GpuIrMethod method, GpuIrCommonSubexpression candidate) {
        Set<String> referencedVariables = referencedVariables(candidate.fingerprint());
        if (referencedVariables.isEmpty()) {
            return true;
        }

        OptionalInt firstStatement = firstTopLevelStatement(candidate);
        OptionalInt lastStatement = lastTopLevelStatement(candidate);
        if (firstStatement.isEmpty() || lastStatement.isEmpty() || firstStatement.getAsInt() == lastStatement.getAsInt()) {
            return true;
        }

        for (int index = firstStatement.getAsInt() + 1; index < lastStatement.getAsInt(); index++) {
            if (index >= 0 && index < method.statements().size() && mutatesAny(method.statements().get(index), referencedVariables)) {
                return false;
            }
        }
        return true;
    }

    private OptionalInt firstTopLevelStatement(GpuIrCommonSubexpression candidate) {
        return candidate.locations().stream()
                .mapToInt(this::topLevelStatementIndexOrMinusOne)
                .filter(index -> index >= 0)
                .min();
    }

    private OptionalInt lastTopLevelStatement(GpuIrCommonSubexpression candidate) {
        return candidate.locations().stream()
                .mapToInt(this::topLevelStatementIndexOrMinusOne)
                .filter(index -> index >= 0)
                .max();
    }

    private int topLevelStatementIndexOrMinusOne(String location) {
        Matcher matcher = TOP_LEVEL_STATEMENT.matcher(location);
        if (!matcher.find()) {
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private Set<String> referencedVariables(String fingerprint) {
        Set<String> variables = new HashSet<>();
        Matcher matcher = VARIABLE_FINGERPRINT.matcher(fingerprint);
        while (matcher.find()) {
            variables.add(unescape(matcher.group(1)));
        }
        Matcher arrayMatcher = ARRAY_FINGERPRINT.matcher(fingerprint);
        while (arrayMatcher.find()) {
            variables.add(unescape(arrayMatcher.group(1)));
        }
        return variables;
    }

    private String unescape(String value) {
        return value.replace("\\,", ",").replace("\\)", ")").replace("\\\\", "\\");
    }

    private boolean mutatesAny(GpuIrStatement statement, Set<String> names) {
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            return names.contains(declaration.name());
        }
        if (statement instanceof GpuIrPrivateArrayDeclaration declaration) {
            return names.contains(declaration.name());
        }
        if (statement instanceof GpuIrAssignment assignment) {
            return mutatesAny(assignment.target(), names);
        }
        return false;
    }

    private boolean mutatesAny(GpuIrExpression target, Set<String> names) {
        if (target instanceof GpuIrVariableRef variableRef) {
            return names.contains(variableRef.name());
        }
        if (target instanceof GpuIrArrayAccess arrayAccess) {
            return names.contains(arrayAccess.arrayName());
        }
        if (target instanceof GpuIrFieldAccess fieldAccess) {
            return mutatesAny(fieldAccess.target(), names);
        }
        return false;
    }
}
