package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies the first conservative CSE rewrite shapes for top-level straight-line expressions only.
 */
public final class GpuIrCommonSubexpressionRewriteApplicator {
    private final GpuIrExpressionTypeResolver typeResolver;

    public GpuIrCommonSubexpressionRewriteApplicator() {
        this(new GpuIrExpressionTypeResolver());
    }

    public GpuIrCommonSubexpressionRewriteApplicator(GpuIrExpressionTypeResolver typeResolver) {
        this.typeResolver = Objects.requireNonNull(typeResolver, "typeResolver");
    }

    public GpuIrMethod apply(GpuIrMethod method, GpuIrCommonSubexpressionRewritePlanReport report) {
        Objects.requireNonNull(method, "method");
        return apply(method, report, null);
    }

    public GpuIrMethod apply(GpuIrCompiledMethod method, GpuIrCommonSubexpressionRewritePlanReport report) {
        Objects.requireNonNull(method, "method");
        return apply(method.irMethod(), report, method);
    }

    private GpuIrMethod apply(
            GpuIrMethod method,
            GpuIrCommonSubexpressionRewritePlanReport report,
            GpuIrCompiledMethod compiledMethod
    ) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(report, "report");
        if (!report.hasPlans()) {
            return method;
        }

        Map<Integer, List<GpuIrCommonSubexpressionRewritePlan>> plansByAnchorStatement = groupPlansByAnchorStatement(report.plans());
        Map<String, String> replacementsByLocation = replacementMap(report.previewReplacementEdits());
        Set<String> appliedReplacementLocations = new HashSet<>();
        List<GpuIrStatement> rewrittenStatements = new ArrayList<>();

        for (int index = 0; index < method.statements().size(); index++) {
            for (GpuIrCommonSubexpressionRewritePlan plan : plansByAnchorStatement.getOrDefault(index, List.of())) {
                rewrittenStatements.add(temporaryDeclaration(method, compiledMethod, plan));
            }
            rewrittenStatements.add(rewriteStatement(index, method.statements().get(index), replacementsByLocation, appliedReplacementLocations));
        }

        if (!appliedReplacementLocations.containsAll(replacementsByLocation.keySet())) {
            Set<String> missingLocations = new HashSet<>(replacementsByLocation.keySet());
            missingLocations.removeAll(appliedReplacementLocations);
            throw new IllegalArgumentException("CSE replacement locations were not applied: " + missingLocations);
        }

        return new GpuIrMethod(method.name(), rewrittenStatements);
    }

    private Map<Integer, List<GpuIrCommonSubexpressionRewritePlan>> groupPlansByAnchorStatement(
            List<GpuIrCommonSubexpressionRewritePlan> plans
    ) {
        Map<Integer, List<GpuIrCommonSubexpressionRewritePlan>> grouped = new HashMap<>();
        for (GpuIrCommonSubexpressionRewritePlan plan : plans) {
            grouped.computeIfAbsent(plan.insertionStatementIndex(), ignored -> new ArrayList<>()).add(plan);
        }
        return grouped;
    }

    private Map<String, String> replacementMap(List<GpuIrCommonSubexpressionRewriteEdit> edits) {
        Map<String, String> replacements = new HashMap<>();
        for (GpuIrCommonSubexpressionRewriteEdit edit : edits) {
            replacements.put(edit.replacementLocation(), edit.temporaryName());
        }
        return replacements;
    }

    private GpuIrVariableDeclaration temporaryDeclaration(
            GpuIrMethod method,
            GpuIrCompiledMethod compiledMethod,
            GpuIrCommonSubexpressionRewritePlan plan
    ) {
        GpuIrStatement anchorStatement = method.statements().get(plan.insertionStatementIndex());
        if (anchorStatement instanceof GpuIrVariableDeclaration declaration
                && plan.insertionAnchorLocation().startsWith("stmt[" + plan.insertionStatementIndex() + "].initializer")) {
            GpuIrExpression initializer = expressionAtLocation(declaration.initializer(), childPath(plan.insertionAnchorLocation(), ".initializer"));
            return new GpuIrVariableDeclaration(declaration.typeName(), plan.temporaryName(), initializer);
        }
        if (anchorStatement instanceof GpuIrAssignment assignment
                && plan.insertionAnchorLocation().startsWith("stmt[" + plan.insertionStatementIndex() + "].value")) {
            GpuIrExpression value = expressionAtLocation(assignment.value(), childPath(plan.insertionAnchorLocation(), ".value"));
            String typeName = typeOf(compiledMethod, method, value)
                    .orElseThrow(() -> new IllegalArgumentException("Cannot infer CSE temporary type for " + plan.insertionAnchorLocation()));
            return new GpuIrVariableDeclaration(typeName, plan.temporaryName(), value);
        }
        if (anchorStatement instanceof GpuIrReturn gpuReturn
                && plan.insertionAnchorLocation().startsWith("stmt[" + plan.insertionStatementIndex() + "].return")) {
            GpuIrExpression value = expressionAtLocation(gpuReturn.value(), childPath(plan.insertionAnchorLocation(), ".return"));
            String typeName = typeOf(compiledMethod, method, value)
                    .orElseThrow(() -> new IllegalArgumentException("Cannot infer CSE temporary type for " + plan.insertionAnchorLocation()));
            return new GpuIrVariableDeclaration(typeName, plan.temporaryName(), value);
        }
        throw new IllegalArgumentException("Only top-level variable initializer, assignment value, or return value anchors are supported for CSE rewrite application");
    }

    private java.util.Optional<String> typeOf(GpuIrCompiledMethod compiledMethod, GpuIrMethod method, GpuIrExpression expression) {
        return compiledMethod == null ? typeResolver.typeOf(method, expression) : typeResolver.typeOf(compiledMethod, expression);
    }

    private GpuIrStatement rewriteStatement(
            int statementIndex,
            GpuIrStatement statement,
            Map<String, String> replacementsByLocation,
            Set<String> appliedReplacementLocations
    ) {
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            String location = "stmt[" + statementIndex + "].initializer";
            GpuIrExpression replacement = rewriteExpression(location, declaration.initializer(), replacementsByLocation, appliedReplacementLocations);
            return new GpuIrVariableDeclaration(declaration.typeName(), declaration.name(), replacement);
        }
        if (statement instanceof GpuIrAssignment assignment) {
            String location = "stmt[" + statementIndex + "].value";
            GpuIrExpression replacement = rewriteExpression(location, assignment.value(), replacementsByLocation, appliedReplacementLocations);
            return new GpuIrAssignment(assignment.target(), replacement);
        }
        if (statement instanceof GpuIrReturn gpuReturn) {
            String location = "stmt[" + statementIndex + "].return";
            GpuIrExpression replacement = rewriteExpression(location, gpuReturn.value(), replacementsByLocation, appliedReplacementLocations);
            return new GpuIrReturn(replacement);
        }
        return statement;
    }

    private GpuIrExpression rewriteExpression(
            String location,
            GpuIrExpression expression,
            Map<String, String> replacementsByLocation,
            Set<String> appliedReplacementLocations
    ) {
        GpuIrExpression directReplacement = replacementExpression(location, replacementsByLocation, appliedReplacementLocations);
        if (directReplacement != null || expression == null) {
            return directReplacement;
        }

        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            return new GpuIrArrayAccess(arrayAccess.arrayName(), rewriteExpression(location + ".index", arrayAccess.index(), replacementsByLocation, appliedReplacementLocations));
        }
        if (expression instanceof GpuIrFieldAccess fieldAccess) {
            return new GpuIrFieldAccess(rewriteExpression(location + ".target", fieldAccess.target(), replacementsByLocation, appliedReplacementLocations), fieldAccess.fieldName());
        }
        if (expression instanceof GpuIrBinary binary) {
            return new GpuIrBinary(
                    binary.operator(),
                    rewriteExpression(location + ".left", binary.left(), replacementsByLocation, appliedReplacementLocations),
                    rewriteExpression(location + ".right", binary.right(), replacementsByLocation, appliedReplacementLocations)
            );
        }
        if (expression instanceof GpuIrUnary unary) {
            return new GpuIrUnary(unary.operator(), rewriteExpression(location + ".operand", unary.operand(), replacementsByLocation, appliedReplacementLocations));
        }
        if (expression instanceof GpuIrTernary ternary) {
            return new GpuIrTernary(
                    rewriteExpression(location + ".condition", ternary.condition(), replacementsByLocation, appliedReplacementLocations),
                    rewriteExpression(location + ".true", ternary.whenTrue(), replacementsByLocation, appliedReplacementLocations),
                    rewriteExpression(location + ".false", ternary.whenFalse(), replacementsByLocation, appliedReplacementLocations)
            );
        }
        if (expression instanceof GpuIrCast cast) {
            return new GpuIrCast(cast.targetType(), rewriteExpression(location + ".expression", cast.expression(), replacementsByLocation, appliedReplacementLocations));
        }
        if (expression instanceof GpuIrStructInit structInit) {
            return new GpuIrStructInit(structInit.structType(), rewriteExpressionList(location + ".arg", structInit.arguments(), replacementsByLocation, appliedReplacementLocations));
        }
        if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            return new GpuIrIntrinsicCall(
                    rewriteExpression(location + ".receiver", intrinsicCall.receiver(), replacementsByLocation, appliedReplacementLocations),
                    intrinsicCall.backendName(),
                    intrinsicCall.codeTemplate(),
                    intrinsicCall.resultType(),
                    rewriteExpressionList(location + ".arg", intrinsicCall.arguments(), replacementsByLocation, appliedReplacementLocations),
                    intrinsicCall.argumentTypes()
            );
        }
        if (expression instanceof GpuIrHelperCall helperCall) {
            return new GpuIrHelperCall(
                    helperCall.helperName(),
                    helperCall.resultType(),
                    rewriteExpressionList(location + ".arg", helperCall.arguments(), replacementsByLocation, appliedReplacementLocations)
            );
        }
        return expression;
    }

    private List<GpuIrExpression> rewriteExpressionList(
            String location,
            List<GpuIrExpression> expressions,
            Map<String, String> replacementsByLocation,
            Set<String> appliedReplacementLocations
    ) {
        List<GpuIrExpression> rewritten = new ArrayList<>();
        for (int index = 0; index < expressions.size(); index++) {
            rewritten.add(rewriteExpression(location + "[" + index + "]", expressions.get(index), replacementsByLocation, appliedReplacementLocations));
        }
        return rewritten;
    }

    private GpuIrExpression replacementExpression(
            String location,
            Map<String, String> replacementsByLocation,
            GpuIrExpression fallback
    ) {
        GpuIrExpression replacement = replacementExpression(location, replacementsByLocation, new HashSet<>());
        return replacement == null ? fallback : replacement;
    }

    private GpuIrExpression replacementExpression(
            String location,
            Map<String, String> replacementsByLocation,
            Set<String> appliedReplacementLocations
    ) {
        String temporaryName = replacementsByLocation.get(location);
        if (temporaryName == null) {
            return null;
        }
        appliedReplacementLocations.add(location);
        markCoveredChildReplacementLocations(location, replacementsByLocation, appliedReplacementLocations);
        return new GpuIrVariableRef(temporaryName);
    }

    private void markCoveredChildReplacementLocations(
            String parentLocation,
            Map<String, String> replacementsByLocation,
            Set<String> appliedReplacementLocations
    ) {
        String childPrefix = parentLocation + ".";
        for (String replacementLocation : replacementsByLocation.keySet()) {
            if (replacementLocation.startsWith(childPrefix)) {
                appliedReplacementLocations.add(replacementLocation);
            }
        }
    }

    private GpuIrExpression expressionAtLocation(GpuIrExpression expression, String path) {
        if (path.isBlank()) {
            return expression;
        }
        if (path.startsWith(".receiver") && expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            return expressionAtLocation(intrinsicCall.receiver(), path.substring(".receiver".length()));
        }
        if (path.startsWith(".arg[")) {
            int closingBracket = path.indexOf(']');
            if (closingBracket < 0) {
                throw new IllegalArgumentException("Unsupported CSE anchor expression path: " + path);
            }
            int argumentIndex = Integer.parseInt(path.substring(".arg[".length(), closingBracket));
            String remainingPath = path.substring(closingBracket + 1);
            if (expression instanceof GpuIrStructInit structInit) {
                return expressionAtLocation(structInit.arguments().get(argumentIndex), remainingPath);
            }
            if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
                return expressionAtLocation(intrinsicCall.arguments().get(argumentIndex), remainingPath);
            }
            if (expression instanceof GpuIrHelperCall helperCall) {
                return expressionAtLocation(helperCall.arguments().get(argumentIndex), remainingPath);
            }
            throw new IllegalArgumentException("Unsupported CSE anchor expression path: " + path);
        }
        if (path.startsWith(".left") && expression instanceof GpuIrBinary binary) {
            return expressionAtLocation(binary.left(), path.substring(".left".length()));
        }
        if (path.startsWith(".right") && expression instanceof GpuIrBinary binary) {
            return expressionAtLocation(binary.right(), path.substring(".right".length()));
        }
        if (path.startsWith(".operand") && expression instanceof GpuIrUnary unary) {
            return expressionAtLocation(unary.operand(), path.substring(".operand".length()));
        }
        if (path.startsWith(".condition") && expression instanceof GpuIrTernary ternary) {
            return expressionAtLocation(ternary.condition(), path.substring(".condition".length()));
        }
        if (path.startsWith(".true") && expression instanceof GpuIrTernary ternary) {
            return expressionAtLocation(ternary.whenTrue(), path.substring(".true".length()));
        }
        if (path.startsWith(".false") && expression instanceof GpuIrTernary ternary) {
            return expressionAtLocation(ternary.whenFalse(), path.substring(".false".length()));
        }
        if (path.startsWith(".expression") && expression instanceof GpuIrCast cast) {
            return expressionAtLocation(cast.expression(), path.substring(".expression".length()));
        }
        if (path.startsWith(".index") && expression instanceof GpuIrArrayAccess arrayAccess) {
            return expressionAtLocation(arrayAccess.index(), path.substring(".index".length()));
        }
        if (path.startsWith(".target") && expression instanceof GpuIrFieldAccess fieldAccess) {
            return expressionAtLocation(fieldAccess.target(), path.substring(".target".length()));
        }
        throw new IllegalArgumentException("Unsupported CSE anchor expression path: " + path);
    }

    private String childPath(String location, String rootSegment) {
        int rootIndex = location.indexOf(rootSegment);
        if (rootIndex < 0) {
            throw new IllegalArgumentException("Unsupported CSE anchor location: " + location);
        }
        return location.substring(rootIndex + rootSegment.length());
    }
}
