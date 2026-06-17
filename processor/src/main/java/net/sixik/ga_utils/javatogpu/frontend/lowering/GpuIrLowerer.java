package net.sixik.ga_utils.javatogpu.frontend.lowering;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsic;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrHelperCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrContinue;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrDoWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelNaming;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GpuIrLowerer {

    private static final Set<String> ALLOWED_BINARY_OPERATORS = Set.of(
            "+", "-", "*", "/", "%",
            "<", "<=", ">", ">=",
            "==", "!=",
            "&&", "||",
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

    public GpuIrLowerer(GpuIntrinsicDatabase intrinsicDatabase) {
        this.intrinsicDatabase = intrinsicDatabase;
    }

    public GpuIrMethod lower(ParsedGpuMethod method) {
        return lower(method, Map.of());
    }

    public List<GpuIrCompiledMethod> lower(ParsedGpuMethod kernelMethod, List<ParsedGpuMethod> helperMethods) {
        Map<HelperSignature, HelperDescriptor> helperRegistry = buildHelperRegistry(helperMethods);
        List<GpuIrCompiledMethod> loweredHelpers = helperMethods.stream()
                .map(helperMethod -> new GpuIrCompiledMethod(helperMethod, lower(helperMethod, helperRegistry)))
                .toList();
        GpuIrCompiledMethod kernel = new GpuIrCompiledMethod(kernelMethod, lower(kernelMethod, helperRegistry));

        List<GpuIrCompiledMethod> compiledMethods = new ArrayList<>(loweredHelpers);
        compiledMethods.add(kernel);
        return compiledMethods;
    }

    private GpuIrMethod lower(ParsedGpuMethod method, Map<HelperSignature, HelperDescriptor> helperRegistry) {
        Deque<Map<String, String>> scopes = new ArrayDeque<>();
        scopes.push(new HashMap<>());
        method.parameters().forEach(parameter -> scopes.peek().put(parameter.name(), parameter.javaType()));
        LoweringContext context = new LoweringContext(helperRegistry);

        List<GpuIrStatement> statements = new ArrayList<>();
        method.declaration().getBody()
                .orElseThrow(() -> new IllegalArgumentException("GPU method must have a body"))
                .getStatements()
                .forEach(statement -> statements.add(lowerStatement(statement, scopes, context)));

        return new GpuIrMethod(method.name(), statements);
    }

    private GpuIrStatement lowerStatement(
            com.github.javaparser.ast.stmt.Statement statement,
            Deque<Map<String, String>> scopes,
            LoweringContext context
    ) {
        if (statement instanceof ForStmt forStmt) {
            return lowerForLoop(forStmt, scopes, context);
        }

        if (statement instanceof IfStmt ifStmt) {
            return lowerIf(ifStmt, scopes, context);
        }

        if (statement instanceof WhileStmt whileStmt) {
            return lowerWhileLoop(whileStmt, scopes, context);
        }

        if (statement instanceof DoStmt doStmt) {
            return lowerDoWhileLoop(doStmt, scopes, context);
        }

        if (statement instanceof SwitchStmt switchStmt) {
            return lowerSwitch(switchStmt, scopes, context);
        }

        if (statement instanceof ReturnStmt returnStmt) {
            return new GpuIrReturn(
                    returnStmt.getExpression().map(expression -> lowerExpression(expression, scopes, context)).orElse(null)
            );
        }

        if (statement instanceof BreakStmt) {
            return new GpuIrBreak();
        }

        if (statement instanceof ContinueStmt) {
            return new GpuIrContinue();
        }

        if (statement instanceof ExpressionStmt expressionStmt) {
            Expression expression = expressionStmt.getExpression();

            if (expression instanceof VariableDeclarationExpr declarationExpr) {
                VariableDeclarator variable = declarationExpr.getVariables().get(0);
                GpuIrExpression initializer = variable.getInitializer()
                        .map(value -> lowerExpression(value, scopes, context))
                        .orElseThrow(() -> new IllegalArgumentException("Variable declaration must have an initializer"));
                scopes.peek().put(variable.getNameAsString(), variable.getTypeAsString());
                return new GpuIrVariableDeclaration(
                        variable.getTypeAsString(),
                        variable.getNameAsString(),
                        initializer
                );
            }

            if (expression instanceof AssignExpr assignExpr) {
                return lowerAssignment(assignExpr, scopes, context);
            }

            if (expression instanceof UnaryExpr unaryExpr) {
                return lowerUpdateExpression(unaryExpr, scopes, context);
            }
        }

        throw new IllegalArgumentException("Unsupported statement for first lowering pass: " + statement);
    }

    private GpuIrIf lowerIf(IfStmt ifStmt, Deque<Map<String, String>> scopes, LoweringContext context) {
        if (!ifStmt.getThenStmt().isBlockStmt()) {
            throw new IllegalArgumentException("If then-branch must use braces");
        }

        scopes.push(new HashMap<>());
        List<GpuIrStatement> thenBranch = ifStmt.getThenStmt().asBlockStmt().getStatements().stream()
                .map(statement -> lowerStatement(statement, scopes, context))
                .toList();
        scopes.pop();
        List<GpuIrStatement> elseBranch = ifStmt.getElseStmt()
                .map(statement -> {
                    if (statement.isIfStmt()) {
                        return List.<GpuIrStatement>of(lowerIf(statement.asIfStmt(), scopes, context));
                    }
                    if (statement.isBlockStmt()) {
                        scopes.push(new HashMap<>());
                        List<GpuIrStatement> lowered = statement.asBlockStmt().getStatements().stream()
                                .map(value -> lowerStatement(value, scopes, context))
                                .toList();
                        scopes.pop();
                        return lowered;
                    }
                    throw new IllegalArgumentException("If else-branch must use braces or else-if");
                })
                .orElse(List.of());

        return new GpuIrIf(
                lowerExpression(ifStmt.getCondition(), scopes, context),
                thenBranch,
                elseBranch
        );
    }

    private GpuIrWhileLoop lowerWhileLoop(WhileStmt whileStmt, Deque<Map<String, String>> scopes, LoweringContext context) {
        if (!whileStmt.getBody().isBlockStmt()) {
            throw new IllegalArgumentException("While loop body must use braces");
        }

        scopes.push(new HashMap<>());
        List<GpuIrStatement> body = whileStmt.getBody().asBlockStmt().getStatements().stream()
                .map(statement -> lowerStatement(statement, scopes, context))
                .toList();
        scopes.pop();

        return new GpuIrWhileLoop(lowerExpression(whileStmt.getCondition(), scopes, context), body);
    }

    private GpuIrDoWhileLoop lowerDoWhileLoop(DoStmt doStmt, Deque<Map<String, String>> scopes, LoweringContext context) {
        if (!doStmt.getBody().isBlockStmt()) {
            throw new IllegalArgumentException("Do-while loop body must use braces");
        }

        scopes.push(new HashMap<>());
        List<GpuIrStatement> body = doStmt.getBody().asBlockStmt().getStatements().stream()
                .map(statement -> lowerStatement(statement, scopes, context))
                .toList();
        scopes.pop();

        return new GpuIrDoWhileLoop(body, lowerExpression(doStmt.getCondition(), scopes, context));
    }

    private GpuIrSwitch lowerSwitch(SwitchStmt switchStmt, Deque<Map<String, String>> scopes, LoweringContext context) {
        scopes.push(new HashMap<>());
        List<GpuIrSwitchCase> cases = switchStmt.getEntries().stream()
                .map(entry -> lowerSwitchCase(entry, scopes, context))
                .toList();
        scopes.pop();

        return new GpuIrSwitch(
                lowerExpression(switchStmt.getSelector(), scopes, context),
                cases
        );
    }

    private GpuIrSwitchCase lowerSwitchCase(SwitchEntry entry, Deque<Map<String, String>> scopes, LoweringContext context) {
        if (!isSupportedSwitchEntryType(entry.getType())) {
            throw new IllegalArgumentException("Only classic switch cases and rule-style case -> with expressions/blocks are supported");
        }

        List<GpuIrStatement> statements = entryStatements(entry).stream()
                .map(statement -> lowerStatement(statement, scopes, context))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (entry.getType() != SwitchEntry.Type.STATEMENT_GROUP && !endsControlTransfer(statements)) {
            statements.add(new GpuIrBreak());
        }

        return new GpuIrSwitchCase(
                entry.getLabels().stream().map(label -> lowerExpression(label, scopes, context)).toList(),
                statements,
                entry.getLabels().isEmpty()
        );
    }

    private GpuIrForLoop lowerForLoop(ForStmt forStmt, Deque<Map<String, String>> scopes, LoweringContext context) {
        if (forStmt.getInitialization().size() != 1) {
            throw new IllegalArgumentException("Only single-initializer for loops are supported");
        }
        if (forStmt.getCompare().isEmpty()) {
            throw new IllegalArgumentException("For loop compare expression is required");
        }
        if (forStmt.getUpdate().size() != 1) {
            throw new IllegalArgumentException("Only single-update for loops are supported");
        }

        scopes.push(new HashMap<>());
        GpuIrStatement initializer = lowerForInitializer(forStmt.getInitialization().get(0), scopes, context);
        GpuIrExpression condition = lowerExpression(forStmt.getCompare().orElseThrow(), scopes, context);
        GpuIrStatement update = lowerForUpdate(forStmt.getUpdate().get(0), scopes, context);
        List<GpuIrStatement> body = forStmt.getBody().asBlockStmt().getStatements().stream()
                .map(statement -> lowerStatement(statement, scopes, context))
                .toList();
        scopes.pop();

        return new GpuIrForLoop(initializer, condition, update, body);
    }

    private GpuIrStatement lowerForInitializer(Expression expression, Deque<Map<String, String>> scopes, LoweringContext context) {
        if (expression instanceof VariableDeclarationExpr declarationExpr) {
            VariableDeclarator variable = declarationExpr.getVariables().get(0);
            GpuIrExpression initializer = variable.getInitializer()
                    .map(value -> lowerExpression(value, scopes, context))
                    .orElseThrow(() -> new IllegalArgumentException("For initializer must declare a value"));
            scopes.peek().put(variable.getNameAsString(), variable.getTypeAsString());
            return new GpuIrVariableDeclaration(
                    variable.getTypeAsString(),
                    variable.getNameAsString(),
                    initializer
            );
        }

        throw new IllegalArgumentException("Unsupported for initializer: " + expression);
    }

    private GpuIrStatement lowerForUpdate(Expression expression, Deque<Map<String, String>> scopes, LoweringContext context) {
        if (expression instanceof UnaryExpr unaryExpr && ALLOWED_UPDATE_OPERATORS.contains(unaryExpr.getOperator())) {
            return lowerUpdateExpression(unaryExpr, scopes, context);
        }

        if (expression instanceof AssignExpr assignExpr) {
            return lowerAssignment(assignExpr, scopes, context);
        }

        throw new IllegalArgumentException("Unsupported for update: " + expression);
    }

    private GpuIrStatement lowerAssignment(AssignExpr assignExpr, Deque<Map<String, String>> scopes, LoweringContext context) {
        GpuIrExpression target = lowerExpression(assignExpr.getTarget(), scopes, context);
        if (assignExpr.getOperator() == AssignExpr.Operator.ASSIGN) {
            return new GpuIrAssignment(target, lowerExpression(assignExpr.getValue(), scopes, context));
        }

        String compoundBinaryOperator = compoundBinaryOperator(assignExpr.getOperator());
        if (compoundBinaryOperator == null) {
            throw new IllegalArgumentException("Unsupported assignment operator for lowering: " + assignExpr.getOperator().asString());
        }

        return new GpuIrAssignment(
                target,
                new GpuIrBinary(
                        compoundBinaryOperator,
                        target,
                        lowerExpression(assignExpr.getValue(), scopes, context)
                )
        );
    }

    private GpuIrStatement lowerUpdateExpression(UnaryExpr unaryExpr, Deque<Map<String, String>> scopes, LoweringContext context) {
        if (!ALLOWED_UPDATE_OPERATORS.contains(unaryExpr.getOperator())) {
            throw new IllegalArgumentException("Unsupported update operator for lowering: " + unaryExpr.getOperator().asString());
        }

        GpuIrExpression target = lowerExpression(unaryExpr.getExpression(), scopes, context);
        String operator = isIncrement(unaryExpr.getOperator()) ? "+" : "-";
        return new GpuIrAssignment(
                target,
                new GpuIrBinary(
                        operator,
                        target,
                        new GpuIrLiteral("1")
                )
        );
    }

    private GpuIrExpression lowerExpression(Expression expression, Deque<Map<String, String>> scopes, LoweringContext context) {
        if (expression instanceof NameExpr nameExpr) {
            return new GpuIrVariableRef(nameExpr.getNameAsString());
        }

        if (expression instanceof IntegerLiteralExpr literalExpr) {
            return new GpuIrLiteral(literalExpr.getValue());
        }

        if (expression instanceof BooleanLiteralExpr literalExpr) {
            return new GpuIrLiteral(literalExpr.toString());
        }

        if (expression instanceof DoubleLiteralExpr literalExpr) {
            return new GpuIrLiteral(literalExpr.toString());
        }

        if (expression instanceof EnclosedExpr enclosedExpr) {
            return lowerExpression(enclosedExpr.getInner(), scopes, context);
        }

        if (expression instanceof CastExpr castExpr) {
            return new GpuIrCast(
                    castExpr.getTypeAsString(),
                    lowerExpression(castExpr.getExpression(), scopes, context)
            );
        }

        if (expression instanceof ArrayAccessExpr arrayAccessExpr) {
            return new GpuIrArrayAccess(
                    arrayAccessExpr.getName().toString(),
                    lowerExpression(arrayAccessExpr.getIndex(), scopes, context)
            );
        }

        if (expression instanceof BinaryExpr binaryExpr) {
            if (!ALLOWED_BINARY_OPERATORS.contains(binaryExpr.getOperator().asString())) {
                throw new IllegalArgumentException("Unsupported binary operator for lowering: " + binaryExpr.getOperator().asString());
            }
            return new GpuIrBinary(
                    binaryExpr.getOperator().asString(),
                    lowerExpression(binaryExpr.getLeft(), scopes, context),
                    lowerExpression(binaryExpr.getRight(), scopes, context)
            );
        }

        if (expression instanceof ConditionalExpr conditionalExpr) {
            return new GpuIrTernary(
                    lowerExpression(conditionalExpr.getCondition(), scopes, context),
                    lowerExpression(conditionalExpr.getThenExpr(), scopes, context),
                    lowerExpression(conditionalExpr.getElseExpr(), scopes, context)
            );
        }

        if (expression instanceof UnaryExpr unaryExpr) {
            if (ALLOWED_UNARY_OPERATORS.contains(unaryExpr.getOperator().asString())) {
                return new GpuIrUnary(
                        unaryExpr.getOperator().asString(),
                        lowerExpression(unaryExpr.getExpression(), scopes, context)
                );
            }
        }

        if (expression instanceof MethodCallExpr methodCallExpr) {
            String owner = methodCallExpr.getScope().map(Expression::toString).orElse("");
            List<GpuIrExpression> arguments = methodCallExpr.getArguments().stream()
                    .map(argument -> lowerExpression(argument, scopes, context))
                    .toList();
            List<String> argumentTypes = inferArgumentTypes(methodCallExpr, scopes, context);

            if ("GPU".equals(owner)) {
                GpuIntrinsic intrinsic = intrinsicDatabase.require(owner, methodCallExpr.getNameAsString(), argumentTypes);
                return new GpuIrIntrinsicCall(intrinsic.backendName(), intrinsic.resultType(), arguments);
            }

            HelperDescriptor helper = context.helperRegistry().get(new HelperSignature(methodCallExpr.getNameAsString(), argumentTypes));
            if (helper != null) {
                return new GpuIrHelperCall(helper.emittedName(), helper.returnType(), arguments);
            }

            throw new IllegalArgumentException("Unknown @CCode helper call for lowering: " + methodCallExpr.getNameAsString());
        }

        throw new IllegalArgumentException("Unsupported expression for first lowering pass: " + expression);
    }

    private boolean isIncrement(UnaryExpr.Operator operator) {
        return operator == UnaryExpr.Operator.POSTFIX_INCREMENT || operator == UnaryExpr.Operator.PREFIX_INCREMENT;
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

    private List<String> inferArgumentTypes(MethodCallExpr call, Deque<Map<String, String>> scopes, LoweringContext context) {
        return call.getArguments().stream()
                .map(argument -> inferExpressionType(argument, scopes, context))
                .toList();
    }

    private String inferExpressionType(Expression expression, Deque<Map<String, String>> scopes, LoweringContext context) {
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
            return inferExpressionType(enclosedExpr.getInner(), scopes, context);
        }

        if (expression instanceof CastExpr castExpr) {
            String targetType = castExpr.getTypeAsString();
            if (!GpuTypeSupport.isSupportedScalarType(targetType)) {
                return null;
            }
            String sourceType = inferExpressionType(castExpr.getExpression(), scopes, context);
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
            String leftType = inferExpressionType(binaryExpr.getLeft(), scopes, context);
            String rightType = inferExpressionType(binaryExpr.getRight(), scopes, context);

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
            String whenTrueType = inferExpressionType(conditionalExpr.getThenExpr(), scopes, context);
            String whenFalseType = inferExpressionType(conditionalExpr.getElseExpr(), scopes, context);
            if (whenTrueType == null || whenFalseType == null) {
                return null;
            }
            if (whenTrueType.equals(whenFalseType)) {
                return whenTrueType;
            }
            return inferNumericResultType(whenTrueType, whenFalseType);
        }

        if (expression instanceof UnaryExpr unaryExpr) {
            String operandType = inferExpressionType(unaryExpr.getExpression(), scopes, context);
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
            String owner = methodCallExpr.getScope().map(Expression::toString).orElse("");
            if ("GPU".equals(owner)) {
                try {
                    return intrinsicDatabase.require(owner, methodCallExpr.getNameAsString(), inferArgumentTypes(methodCallExpr, scopes, context)).resultType();
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }

            if (!owner.equals("GPU")) {
                HelperDescriptor helper = context.helperRegistry().get(new HelperSignature(methodCallExpr.getNameAsString(), inferArgumentTypes(methodCallExpr, scopes, context)));
                return helper == null ? null : helper.returnType();
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

    private boolean isSupportedSwitchEntryType(SwitchEntry.Type type) {
        return type == SwitchEntry.Type.STATEMENT_GROUP
                || type == SwitchEntry.Type.EXPRESSION
                || type == SwitchEntry.Type.BLOCK;
    }

    private List<com.github.javaparser.ast.stmt.Statement> entryStatements(SwitchEntry entry) {
        if (entry.getType() == SwitchEntry.Type.BLOCK
                && entry.getStatements().size() == 1
                && entry.getStatement(0).isBlockStmt()) {
            return entry.getStatement(0).asBlockStmt().getStatements();
        }
        return entry.getStatements();
    }

    private boolean endsControlTransfer(List<GpuIrStatement> statements) {
        if (statements.isEmpty()) {
            return false;
        }
        GpuIrStatement last = statements.get(statements.size() - 1);
        return last instanceof GpuIrBreak || last instanceof GpuIrContinue || last instanceof GpuIrReturn;
    }

    private Map<HelperSignature, HelperDescriptor> buildHelperRegistry(List<ParsedGpuMethod> helperMethods) {
        Map<HelperSignature, HelperDescriptor> helperRegistry = new HashMap<>();
        for (ParsedGpuMethod helperMethod : helperMethods) {
            List<String> argumentTypes = helperMethod.parameters().stream().map(parameter -> parameter.javaType()).toList();
            helperRegistry.put(
                    new HelperSignature(helperMethod.name(), argumentTypes),
                    new HelperDescriptor(
                            helperMethod.ownerSimpleName(),
                            helperMethod.ownerQualifiedName(),
                            helperMethod.name(),
                            OpenClKernelNaming.toHelperFunctionName(helperMethod.ownerSimpleName(), helperMethod.name(), argumentTypes),
                            helperMethod.returnType(),
                            argumentTypes,
                            helperMethod.inline()
                    )
            );
        }
        return helperRegistry;
    }

    private record LoweringContext(Map<HelperSignature, HelperDescriptor> helperRegistry) {
    }

    private record HelperSignature(String name, List<String> argumentTypes) {
    }

    private record HelperDescriptor(
            String ownerSimpleName,
            String ownerQualifiedName,
            String javaName,
            String emittedName,
            String returnType,
            List<String> argumentTypes,
            boolean inline
    ) {
    }
}
