package net.sixik.ga_utils.javatogpu.frontend.validation;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GpuSubsetValidator {

    private static final Set<String> ALLOWED_BINARY_OPERATORS = Set.of(
            "+", "-", "*", "/", "%",
            "<", "<=", ">", ">=",
            "==", "!=",
            "&&", "||",
            "&", "|", "^",
            "<<", ">>"
    );

    private static final Set<String> INTEGER_ONLY_BINARY_OPERATORS = Set.of(
            "&", "|", "^",
            "<<", ">>"
    );

    private static final Set<String> ALLOWED_UNARY_OPERATORS = Set.of("!", "-", "~");
    private static final Set<UnaryExpr.Operator> ALLOWED_UPDATE_OPERATORS = Set.of(
            UnaryExpr.Operator.POSTFIX_INCREMENT,
            UnaryExpr.Operator.PREFIX_INCREMENT,
            UnaryExpr.Operator.POSTFIX_DECREMENT,
            UnaryExpr.Operator.PREFIX_DECREMENT
    );

    private final GpuIntrinsicDatabase intrinsicDatabase;

    public GpuSubsetValidator(GpuIntrinsicDatabase intrinsicDatabase) {
        this.intrinsicDatabase = intrinsicDatabase;
    }

    public void validate(ParsedGpuMethod method) {
        List<GpuValidationIssue> issues = new ArrayList<>();
        Deque<Map<String, String>> scopes = new ArrayDeque<>();
        scopes.push(new HashMap<>());
        method.parameters().forEach(parameter -> scopes.peek().put(parameter.name(), parameter.javaType()));

        validateReturnType(method, issues);
        validateParameters(method, issues);
        method.declaration().getBody().ifPresentOrElse(
                body -> body.getStatements().forEach(statement -> validateStatement(statement, issues, scopes)),
                () -> issues.add(issue(method.declaration(), "GPU method must have a body"))
        );

        if (!issues.isEmpty()) {
            throw new GpuValidationException(issues);
        }
    }

    private void validateReturnType(ParsedGpuMethod method, List<GpuValidationIssue> issues) {
        if (!"void".equals(method.returnType())) {
            issues.add(issue(
                    method.declaration().getType(),
                    "Non-void @GPU methods are not supported in the current pipeline; use a void method with output buffers"
            ));
        }
    }

    private void validateParameters(ParsedGpuMethod method, List<GpuValidationIssue> issues) {
        method.parameters().forEach(parameter -> {
            String type = parameter.javaType();
            if (!GpuTypeSupport.isSupportedParameterType(type)) {
                issues.add(new GpuValidationIssue(1, 1, "Unsupported GPU parameter type: " + type));
                return;
            }

            if (parameter.addressSpace() == GpuAddressSpace.GLOBAL && !GpuTypeSupport.isGlobalParameterCompatible(type)) {
                issues.add(new GpuValidationIssue(
                        1,
                        1,
                        "@GPUGlobal is only supported on primitive array parameters in the current pipeline: " + type
                ));
                return;
            }

            if (parameter.addressSpace() != GpuAddressSpace.GLOBAL && GpuTypeSupport.isSupportedArrayType(type)) {
                issues.add(new GpuValidationIssue(
                        1,
                        1,
                        "Primitive array parameters must be annotated with @GPUGlobal in the current pipeline: " + type
                ));
            }
        });
    }

    private void validateStatement(Statement statement, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        if (statement instanceof ExpressionStmt expressionStmt) {
            validateExpressionStatement(expressionStmt, issues, scopes);
            return;
        }

        if (statement instanceof ForStmt forStmt) {
            validateForLoop(forStmt, issues, scopes);
            return;
        }

        if (statement instanceof IfStmt ifStmt) {
            validateIf(ifStmt, issues, scopes);
            return;
        }

        if (statement instanceof WhileStmt whileStmt) {
            validateWhileLoop(whileStmt, issues, scopes);
            return;
        }

        if (statement instanceof DoStmt doStmt) {
            validateDoWhileLoop(doStmt, issues, scopes);
            return;
        }

        if (statement instanceof SwitchStmt switchStmt) {
            validateSwitch(switchStmt, issues, scopes);
            return;
        }

        if (statement instanceof BreakStmt || statement instanceof ContinueStmt) {
            return;
        }

        issues.add(issue(statement, "Unsupported statement in @GPU method: " + statement.getClass().getSimpleName()));
    }

    private void validateExpressionStatement(ExpressionStmt statement, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        Expression expression = statement.getExpression();

        if (expression instanceof VariableDeclarationExpr declarationExpr) {
            if (declarationExpr.getVariables().size() != 1) {
                issues.add(issue(statement, "Only single-variable declarations are supported in @GPU methods"));
                return;
            }

            VariableDeclarator variable = declarationExpr.getVariables().get(0);
            validateVariableType(variable, issues);
            if (variable.getInitializer().isEmpty()) {
                issues.add(issue(variable, "Variable declarations in @GPU methods must have an initializer"));
                return;
            }
            validateExpression(variable.getInitializer().orElseThrow(), issues, scopes);
            scopes.peek().put(variable.getNameAsString(), variable.getTypeAsString());
            return;
        }

        if (expression instanceof AssignExpr assignExpr) {
            validateAssignment(assignExpr, issues, scopes);
            return;
        }

        if (expression instanceof UnaryExpr unaryExpr) {
            validateUpdateExpression(unaryExpr, issues, scopes, "Unsupported expression statement in @GPU method: " + expression);
            return;
        }

        issues.add(issue(statement, "Unsupported expression statement in @GPU method: " + expression));
    }

    private void validateForLoop(ForStmt forStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        scopes.push(new HashMap<>());
        if (forStmt.getInitialization().size() != 1) {
            issues.add(issue(forStmt, "Only single-initializer for loops are supported in @GPU methods"));
        } else {
            validateForInitializer(forStmt.getInitialization().get(0), issues, scopes);
        }

        if (forStmt.getCompare().isEmpty()) {
            issues.add(issue(forStmt, "for loops must contain a compare expression"));
        } else {
            validateExpression(forStmt.getCompare().orElseThrow(), issues, scopes);
        }

        if (forStmt.getUpdate().size() != 1) {
            issues.add(issue(forStmt, "Only single-update for loops are supported in @GPU methods"));
        } else {
            validateForUpdate(forStmt.getUpdate().get(0), issues, scopes);
        }

        if (!forStmt.getBody().isBlockStmt()) {
            issues.add(issue(forStmt.getBody(), "for loop bodies must use braces in @GPU methods"));
            scopes.pop();
            return;
        }

        forStmt.getBody().asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes));
        scopes.pop();
    }

    private void validateIf(IfStmt ifStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        validateExpression(ifStmt.getCondition(), issues, scopes);

        if (!ifStmt.getThenStmt().isBlockStmt()) {
            issues.add(issue(ifStmt.getThenStmt(), "if branches must use braces in @GPU methods"));
        } else {
            scopes.push(new HashMap<>());
            ifStmt.getThenStmt().asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes));
            scopes.pop();
        }

        ifStmt.getElseStmt().ifPresent(elseStmt -> {
            if (elseStmt.isIfStmt()) {
                validateIf(elseStmt.asIfStmt(), issues, scopes);
                return;
            }

            if (!elseStmt.isBlockStmt()) {
                issues.add(issue(elseStmt, "else branches must use braces or else-if in @GPU methods"));
                return;
            }

            scopes.push(new HashMap<>());
            elseStmt.asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes));
            scopes.pop();
        });
    }

    private void validateWhileLoop(WhileStmt whileStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        validateExpression(whileStmt.getCondition(), issues, scopes);

        if (!whileStmt.getBody().isBlockStmt()) {
            issues.add(issue(whileStmt.getBody(), "while loop bodies must use braces in @GPU methods"));
            return;
        }

        scopes.push(new HashMap<>());
        whileStmt.getBody().asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes));
        scopes.pop();
    }

    private void validateDoWhileLoop(DoStmt doStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        if (!doStmt.getBody().isBlockStmt()) {
            issues.add(issue(doStmt.getBody(), "do-while loop bodies must use braces in @GPU methods"));
            return;
        }

        scopes.push(new HashMap<>());
        doStmt.getBody().asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes));
        scopes.pop();
        validateExpression(doStmt.getCondition(), issues, scopes);
    }

    private void validateSwitch(SwitchStmt switchStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        validateExpression(switchStmt.getSelector(), issues, scopes);

        scopes.push(new HashMap<>());
        for (SwitchEntry entry : switchStmt.getEntries()) {
            if (!isSupportedSwitchEntryType(entry.getType())) {
                issues.add(issue(entry, "Only classic switch cases and rule-style case -> with expressions/blocks are supported in @GPU methods"));
                continue;
            }

            entry.getLabels().forEach(label -> validateExpression(label, issues, scopes));
            entryStatements(entry).forEach(statement -> validateStatement(statement, issues, scopes));
        }
        scopes.pop();
    }

    private void validateForInitializer(Expression expression, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        if (!(expression instanceof VariableDeclarationExpr declarationExpr)) {
            issues.add(issue(expression, "Unsupported for initializer in @GPU method: " + expression));
            return;
        }

        if (declarationExpr.getVariables().size() != 1) {
            issues.add(issue(expression, "Only single-variable for initializers are supported in @GPU methods"));
            return;
        }

        VariableDeclarator variable = declarationExpr.getVariables().get(0);
        validateVariableType(variable, issues);
        if (variable.getInitializer().isEmpty()) {
            issues.add(issue(variable, "For initializer variables in @GPU methods must have an initializer"));
            return;
        }
        validateExpression(variable.getInitializer().orElseThrow(), issues, scopes);
        scopes.peek().put(variable.getNameAsString(), variable.getTypeAsString());
    }

    private void validateForUpdate(Expression expression, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        if (expression instanceof UnaryExpr unaryExpr) {
            validateUpdateExpression(unaryExpr, issues, scopes, "Unsupported for update in @GPU method: " + expression);
            return;
        }

        if (expression instanceof AssignExpr assignExpr) {
            validateAssignment(assignExpr, issues, scopes);
            return;
        }

        issues.add(issue(expression, "Unsupported for update in @GPU method: " + expression));
    }

    private void validateAssignment(AssignExpr assignExpr, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        validateExpression(assignExpr.getTarget(), issues, scopes);
        validateExpression(assignExpr.getValue(), issues, scopes);

        if (assignExpr.getOperator() == AssignExpr.Operator.ASSIGN) {
            return;
        }

        String compoundBinaryOperator = compoundBinaryOperator(assignExpr.getOperator());
        if (compoundBinaryOperator == null) {
            issues.add(issue(assignExpr, "Unsupported assignment operator in @GPU method: " + assignExpr.getOperator().asString()));
            return;
        }

        if (INTEGER_ONLY_BINARY_OPERATORS.contains(compoundBinaryOperator)) {
            String targetType = inferExpressionType(assignExpr.getTarget(), scopes);
            String valueType = inferExpressionType(assignExpr.getValue(), scopes);
            if (!GpuTypeSupport.isIntegralScalarType(targetType) || !GpuTypeSupport.isIntegralScalarType(valueType)) {
                issues.add(issue(
                        assignExpr,
                        "Operator " + assignExpr.getOperator().asString() + " is only supported for integral scalar expressions in @GPU methods"
                ));
            }
        }
    }

    private void validateExpression(Expression expression, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        if (expression instanceof NameExpr
                || expression instanceof IntegerLiteralExpr
                || expression instanceof BooleanLiteralExpr
                || expression instanceof DoubleLiteralExpr) {
            return;
        }

        if (expression instanceof EnclosedExpr enclosedExpr) {
            validateExpression(enclosedExpr.getInner(), issues, scopes);
            return;
        }

        if (expression instanceof ArrayAccessExpr arrayAccessExpr) {
            if (!(arrayAccessExpr.getName() instanceof NameExpr)) {
                issues.add(issue(arrayAccessExpr, "Only direct array access is supported in @GPU methods"));
                return;
            }

            validateExpression(arrayAccessExpr.getIndex(), issues, scopes);
            return;
        }

        if (expression instanceof BinaryExpr binaryExpr) {
            if (!ALLOWED_BINARY_OPERATORS.contains(binaryExpr.getOperator().asString())) {
                issues.add(issue(binaryExpr, "Unsupported binary operator in @GPU method: " + binaryExpr.getOperator().asString()));
                return;
            }
            validateExpression(binaryExpr.getLeft(), issues, scopes);
            validateExpression(binaryExpr.getRight(), issues, scopes);
            if (INTEGER_ONLY_BINARY_OPERATORS.contains(binaryExpr.getOperator().asString())) {
                String leftType = inferExpressionType(binaryExpr.getLeft(), scopes);
                String rightType = inferExpressionType(binaryExpr.getRight(), scopes);
                if (!GpuTypeSupport.isIntegralScalarType(leftType) || !GpuTypeSupport.isIntegralScalarType(rightType)) {
                    issues.add(issue(
                            binaryExpr,
                            "Operator " + binaryExpr.getOperator().asString() + " is only supported for integral scalar expressions in @GPU methods"
                    ));
                }
            }
            return;
        }

        if (expression instanceof ConditionalExpr conditionalExpr) {
            validateExpression(conditionalExpr.getCondition(), issues, scopes);
            validateExpression(conditionalExpr.getThenExpr(), issues, scopes);
            validateExpression(conditionalExpr.getElseExpr(), issues, scopes);
            return;
        }

        if (expression instanceof UnaryExpr unaryExpr) {
            if (!ALLOWED_UNARY_OPERATORS.contains(unaryExpr.getOperator().asString())) {
                issues.add(issue(unaryExpr, "Unsupported unary operator in @GPU method: " + unaryExpr.getOperator().asString()));
                return;
            }

            validateExpression(unaryExpr.getExpression(), issues, scopes);
            if ("~".equals(unaryExpr.getOperator().asString())) {
                String operandType = inferExpressionType(unaryExpr.getExpression(), scopes);
                if (!GpuTypeSupport.isIntegralScalarType(operandType)) {
                    issues.add(issue(
                            unaryExpr,
                            "Operator ~ is only supported for integral scalar expressions in @GPU methods"
                    ));
                }
            }
            return;
        }

        if (expression instanceof MethodCallExpr methodCallExpr) {
            validateMethodCall(methodCallExpr, issues, scopes);
            return;
        }

        if (expression instanceof CastExpr castExpr) {
            validateCast(castExpr, issues, scopes);
            return;
        }

        if (expression instanceof ObjectCreationExpr objectCreationExpr) {
            validateObjectCreation(objectCreationExpr, issues);
            return;
        }

        issues.add(issue(expression, "Unsupported expression in @GPU method: " + expression));
    }

    private void validateObjectCreation(ObjectCreationExpr creation, List<GpuValidationIssue> issues) {
        String typeName = creation.getTypeAsString();
        if (intrinsicDatabase.isAllowedAllocationType(typeName)) {
            issues.add(issue(creation, "Pointer helper allocation is not supported in the current @GPU lowering pass: " + typeName));
            return;
        }

        issues.add(issue(creation, "Object creation is not allowed in @GPU methods: " + typeName));
    }

    private void validateMethodCall(MethodCallExpr call, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        String owner = call.getScope().map(Node::toString).orElse("");
        if (!"GPU".equals(owner)) {
            issues.add(issue(call, "Only GPU.* calls are allowed in @GPU methods"));
            return;
        }

        call.getArguments().forEach(argument -> validateExpression(argument, issues, scopes));

        try {
            intrinsicDatabase.require(owner, call.getNameAsString(), inferArgumentTypes(call, scopes));
        } catch (IllegalArgumentException exception) {
            issues.add(issue(call, exception.getMessage()));
        }
    }

    private void validateVariableType(VariableDeclarator variable, List<GpuValidationIssue> issues) {
        if (!GpuTypeSupport.isSupportedLocalType(variable.getTypeAsString())) {
            issues.add(issue(variable, "Unsupported local type in @GPU method: " + variable.getTypeAsString()));
        }
    }

    private void validateCast(CastExpr castExpr, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes) {
        String targetType = castExpr.getTypeAsString();
        if (!GpuTypeSupport.isSupportedScalarType(targetType)) {
            issues.add(issue(castExpr, "Unsupported cast target type in @GPU method: " + targetType));
            return;
        }

        validateExpression(castExpr.getExpression(), issues, scopes);

        String sourceType = inferExpressionType(castExpr.getExpression(), scopes);
        if (sourceType == null || !GpuTypeSupport.isSupportedScalarType(sourceType)) {
            issues.add(issue(castExpr, "Only primitive scalar casts are supported in @GPU methods"));
        }
    }

    private void validateUpdateExpression(
            UnaryExpr unaryExpr,
            List<GpuValidationIssue> issues,
            Deque<Map<String, String>> scopes,
            String unsupportedMessage
    ) {
        if (!ALLOWED_UPDATE_OPERATORS.contains(unaryExpr.getOperator())) {
            issues.add(issue(unaryExpr, unsupportedMessage));
            return;
        }

        Expression target = unaryExpr.getExpression();
        if (!(target instanceof NameExpr) && !(target instanceof ArrayAccessExpr)) {
            issues.add(issue(unaryExpr, "Update expressions in @GPU methods must target a variable or direct array access"));
            return;
        }

        validateExpression(target, issues, scopes);
    }

    private String inferExpressionType(Expression expression, Deque<Map<String, String>> scopes) {
        if (expression instanceof NameExpr nameExpr) {
            return lookupType(scopes, nameExpr.getNameAsString());
        }

        if (expression instanceof IntegerLiteralExpr) {
            return "int";
        }

        if (expression instanceof BooleanLiteralExpr) {
            return "boolean";
        }

        if (expression instanceof DoubleLiteralExpr literalExpr) {
            String source = literalExpr.toString();
            if (source.endsWith("f") || source.endsWith("F")) {
                return "float";
            }
            return "double";
        }

        if (expression instanceof EnclosedExpr enclosedExpr) {
            return inferExpressionType(enclosedExpr.getInner(), scopes);
        }

        if (expression instanceof CastExpr castExpr) {
            String targetType = castExpr.getTypeAsString();
            if (!GpuTypeSupport.isSupportedScalarType(targetType)) {
                return null;
            }
            String sourceType = inferExpressionType(castExpr.getExpression(), scopes);
            if (sourceType == null || !GpuTypeSupport.isSupportedScalarType(sourceType)) {
                return null;
            }
            return targetType;
        }

        if (expression instanceof ArrayAccessExpr arrayAccessExpr) {
            String arrayType = lookupType(scopes, arrayAccessExpr.getName().toString());
            if (arrayType != null && GpuTypeSupport.isSupportedArrayType(arrayType)) {
                return GpuTypeSupport.componentType(arrayType);
            }
            return null;
        }

        if (expression instanceof BinaryExpr binaryExpr) {
            String operator = binaryExpr.getOperator().asString();
            String leftType = inferExpressionType(binaryExpr.getLeft(), scopes);
            String rightType = inferExpressionType(binaryExpr.getRight(), scopes);

            if ("&&".equals(operator) || "||".equals(operator)
                    || "<".equals(operator) || "<=".equals(operator)
                    || ">".equals(operator) || ">=".equals(operator)
                    || "==".equals(operator) || "!=".equals(operator)) {
                return "boolean";
            }

            if ("&".equals(operator) || "|".equals(operator) || "^".equals(operator)) {
                return inferIntegralResultType(leftType, rightType);
            }

            if ("<<".equals(operator) || ">>".equals(operator)) {
                if (!GpuTypeSupport.isIntegralScalarType(leftType) || !GpuTypeSupport.isIntegralScalarType(rightType)) {
                    return null;
                }
                return "long".equals(leftType) ? "long" : "int";
            }

            return inferNumericResultType(leftType, rightType);
        }

        if (expression instanceof ConditionalExpr conditionalExpr) {
            String whenTrueType = inferExpressionType(conditionalExpr.getThenExpr(), scopes);
            String whenFalseType = inferExpressionType(conditionalExpr.getElseExpr(), scopes);
            if (whenTrueType == null || whenFalseType == null) {
                return null;
            }
            if (whenTrueType.equals(whenFalseType)) {
                return whenTrueType;
            }
            return inferNumericResultType(whenTrueType, whenFalseType);
        }

        if (expression instanceof UnaryExpr unaryExpr) {
            String operandType = inferExpressionType(unaryExpr.getExpression(), scopes);
            String operator = unaryExpr.getOperator().asString();
            if ("!".equals(operator)) {
                return "boolean";
            }
            if ("-".equals(operator)) {
                return normalizeUnaryNumericType(operandType);
            }
            if ("~".equals(operator)) {
                return normalizeUnaryIntegralType(operandType);
            }
            return null;
        }

        if (expression instanceof MethodCallExpr methodCallExpr) {
            String owner = methodCallExpr.getScope().map(Node::toString).orElse("");
            if (!"GPU".equals(owner)) {
                return null;
            }

            try {
                return intrinsicDatabase.require(owner, methodCallExpr.getNameAsString(), inferArgumentTypes(methodCallExpr, scopes)).resultType();
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        return null;
    }

    private String lookupType(Deque<Map<String, String>> scopes, String name) {
        for (Map<String, String> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return null;
    }

    private String inferNumericResultType(String leftType, String rightType) {
        if (leftType == null || rightType == null) {
            return null;
        }
        if (!GpuTypeSupport.isSupportedScalarType(leftType) || !GpuTypeSupport.isSupportedScalarType(rightType)) {
            return null;
        }
        if ("double".equals(leftType) || "double".equals(rightType)) {
            return "double";
        }
        if ("float".equals(leftType) || "float".equals(rightType)) {
            return "float";
        }
        if ("long".equals(leftType) || "long".equals(rightType)) {
            return "long";
        }
        return "int";
    }

    private String inferIntegralResultType(String leftType, String rightType) {
        if (!GpuTypeSupport.isIntegralScalarType(leftType) || !GpuTypeSupport.isIntegralScalarType(rightType)) {
            return null;
        }
        if ("long".equals(leftType) || "long".equals(rightType)) {
            return "long";
        }
        return "int";
    }

    private String normalizeUnaryNumericType(String operandType) {
        if (!GpuTypeSupport.isSupportedScalarType(operandType)) {
            return null;
        }
        if ("double".equals(operandType) || "float".equals(operandType) || "long".equals(operandType)) {
            return operandType;
        }
        return "int";
    }

    private String normalizeUnaryIntegralType(String operandType) {
        if (!GpuTypeSupport.isIntegralScalarType(operandType)) {
            return null;
        }
        return "long".equals(operandType) ? "long" : "int";
    }

    private String compoundBinaryOperator(AssignExpr.Operator operator) {
        return switch (operator) {
            case PLUS -> "+";
            case MINUS -> "-";
            case MULTIPLY -> "*";
            case DIVIDE -> "/";
            case REMAINDER -> "%";
            case BINARY_AND -> "&";
            case BINARY_OR -> "|";
            case XOR -> "^";
            case LEFT_SHIFT -> "<<";
            case SIGNED_RIGHT_SHIFT -> ">>";
            default -> null;
        };
    }

    private List<String> inferArgumentTypes(MethodCallExpr call, Deque<Map<String, String>> scopes) {
        return call.getArguments().stream()
                .map(argument -> inferExpressionType(argument, scopes))
                .toList();
    }

    private boolean isSupportedSwitchEntryType(SwitchEntry.Type type) {
        return type == SwitchEntry.Type.STATEMENT_GROUP
                || type == SwitchEntry.Type.EXPRESSION
                || type == SwitchEntry.Type.BLOCK;
    }

    private List<Statement> entryStatements(SwitchEntry entry) {
        if (entry.getType() == SwitchEntry.Type.BLOCK
                && entry.getStatements().size() == 1
                && entry.getStatement(0).isBlockStmt()) {
            return entry.getStatement(0).asBlockStmt().getStatements();
        }
        return entry.getStatements();
    }

    private GpuValidationIssue issue(Node node, String message) {
        int line = node.getBegin().map(position -> position.line).orElse(-1);
        int column = node.getBegin().map(position -> position.column).orElse(-1);
        return new GpuValidationIssue(line, column, message);
    }
}
