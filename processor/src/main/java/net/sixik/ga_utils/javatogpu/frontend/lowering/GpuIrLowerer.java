package net.sixik.ga_utils.javatogpu.frontend.lowering;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
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
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrExpressionStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitch;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrSwitchCase;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrWhileLoop;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelNaming;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
        return lower(method, Map.of(), buildConstantRegistry(method, List.of()));
    }

    public List<GpuIrCompiledMethod> lower(ParsedGpuMethod kernelMethod, List<ParsedGpuMethod> helperMethods) {
        Map<HelperSignature, List<HelperDescriptor>> helperRegistry = buildHelperRegistry(helperMethods);
        Map<String, List<ConstantDescriptor>> constantRegistry = buildConstantRegistry(kernelMethod, helperMethods);
        List<GpuIrCompiledMethod> loweredHelpers = helperMethods.stream()
                .map(helperMethod -> compileMethod(helperMethod, helperRegistry, constantRegistry, false))
                .toList();
        GpuIrCompiledMethod kernel = compileMethod(kernelMethod, helperRegistry, constantRegistry, true);

        List<GpuIrCompiledMethod> compiledMethods = new ArrayList<>(loweredHelpers);
        compiledMethods.add(kernel);
        return compiledMethods;
    }

    private GpuIrCompiledMethod compileMethod(
            ParsedGpuMethod method,
            Map<HelperSignature, List<HelperDescriptor>> helperRegistry,
            Map<String, List<ConstantDescriptor>> constantRegistry,
            boolean kernelEntry
    ) {
        GpuIrMethod loweredMethod = lower(method, helperRegistry, constantRegistry);
        String emittedName = kernelEntry
                ? OpenClKernelNaming.toEntryPointName(loweredMethod.name())
                : OpenClKernelNaming.toHelperFunctionName(
                method.ownerSimpleName(),
                method.name(),
                method.parameters().stream().map(parameter -> parameter.javaType()).toList()
        );
        return new GpuIrCompiledMethod(
                method,
                loweredMethod,
                emittedName,
                List.copyOf(collectHelperDependencies(loweredMethod))
        );
    }

    private GpuIrMethod lower(
            ParsedGpuMethod method,
            Map<HelperSignature, List<HelperDescriptor>> helperRegistry,
            Map<String, List<ConstantDescriptor>> constantRegistry
    ) {
        Deque<Map<String, String>> scopes = new ArrayDeque<>();
        scopes.push(new HashMap<>());
        method.parameters().forEach(parameter -> scopes.peek().put(parameter.name(), GpuTypeSupport.parameterStorageType(parameter.javaType())));
        LoweringContext context = new LoweringContext(
                helperRegistry,
                method.ownerSimpleName(),
                method.ownerQualifiedName(),
                constantRegistry
        );

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

            if (expression instanceof MethodCallExpr methodCallExpr) {
                return new GpuIrExpressionStatement(lowerExpression(methodCallExpr, scopes, context));
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
                .flatMap(statement -> lowerSwitchEntryStatement(statement, scopes, context).stream())
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

    private List<GpuIrStatement> lowerSwitchEntryStatement(
            com.github.javaparser.ast.stmt.Statement statement,
            Deque<Map<String, String>> scopes,
            LoweringContext context
    ) {
        if (statement instanceof BlockStmt blockStmt) {
            scopes.push(new HashMap<>());
            List<GpuIrStatement> lowered = blockStmt.getStatements().stream()
                    .map(value -> lowerStatement(value, scopes, context))
                    .toList();
            scopes.pop();
            return lowered;
        }
        return List.of(lowerStatement(statement, scopes, context));
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
            ConstantDescriptor constant = resolveConstant(nameExpr.getNameAsString(), "", context);
            if (constant != null && lookupStorageType(scopes, nameExpr.getNameAsString()) == null) {
                return new GpuIrLiteral(constant.sourceText());
            }
            return new GpuIrVariableRef(nameExpr.getNameAsString());
        }

        if (expression instanceof IntegerLiteralExpr literalExpr) {
            return new GpuIrLiteral(literalExpr.getValue());
        }

        if (expression instanceof LongLiteralExpr literalExpr) {
            return new GpuIrLiteral(literalExpr.toString());
        }

        if (expression instanceof BooleanLiteralExpr literalExpr) {
            return new GpuIrLiteral(literalExpr.toString());
        }

        if (expression instanceof CharLiteralExpr literalExpr) {
            return new GpuIrLiteral(Integer.toString(literalExpr.asChar()));
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

        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            if (!(fieldAccessExpr.getScope() instanceof NameExpr nameExpr)) {
                throw new IllegalArgumentException("Unsupported field access for lowering: " + fieldAccessExpr);
            }
            if ("value".equals(fieldAccessExpr.getNameAsString())) {
                String storageType = lookupStorageType(scopes, nameExpr.getNameAsString());
                if (GpuTypeSupport.isPointerReferenceStorage(storageType)) {
                    return new GpuIrUnary("*", new GpuIrVariableRef(nameExpr.getNameAsString()));
                }
                if (GpuTypeSupport.isSupportedPointerType(GpuTypeSupport.declaredType(storageType))) {
                    return new GpuIrVariableRef(nameExpr.getNameAsString());
                }
                throw new IllegalArgumentException("The .value field is only supported on pointer helpers for lowering: " + fieldAccessExpr);
            }
            ConstantDescriptor constant = resolveConstant(fieldAccessExpr.getNameAsString(), nameExpr.getNameAsString(), context);
            if (constant != null) {
                return new GpuIrLiteral(constant.sourceText());
            }
            throw new IllegalArgumentException("Unsupported field access for lowering: " + fieldAccessExpr);
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
            List<String> argumentTypes = inferArgumentTypes(methodCallExpr, scopes, context);

            if ("GPU".equals(owner)) {
                List<GpuIrExpression> arguments = methodCallExpr.getArguments().stream()
                        .map(argument -> lowerExpression(argument, scopes, context))
                        .toList();
                GpuIntrinsic intrinsic = intrinsicDatabase.require(owner, methodCallExpr.getNameAsString(), argumentTypes);
                return new GpuIrIntrinsicCall(intrinsic.backendName(), intrinsic.resultType(), arguments);
            }

            HelperDescriptor helper = resolveHelperCall(owner, methodCallExpr.getNameAsString(), argumentTypes, context);
            if (helper != null) {
                List<GpuIrExpression> arguments = new ArrayList<>(methodCallExpr.getArguments().size());
                for (int i = 0; i < methodCallExpr.getArguments().size(); i++) {
                    arguments.add(lowerHelperArgument(
                            methodCallExpr.getArgument(i),
                            helper.argumentTypes().get(i),
                            scopes,
                            context
                    ));
                }
                return new GpuIrHelperCall(helper.emittedName(), helper.returnType(), arguments);
            }

            throw new IllegalArgumentException("Unknown or ambiguous @CCode helper call for lowering: "
                    + (owner.isBlank() ? methodCallExpr.getNameAsString() : owner + "." + methodCallExpr.getNameAsString()));
        }

        if (expression instanceof ObjectCreationExpr creationExpr
                && intrinsicDatabase.isAllowedAllocationType(creationExpr.getTypeAsString())
                && GpuTypeSupport.isSupportedPointerType(creationExpr.getTypeAsString())) {
            if (creationExpr.getArguments().size() > 1) {
                throw new IllegalArgumentException("Pointer helper allocation supports at most one constructor argument: " + creationExpr.getTypeAsString());
            }
            if (creationExpr.getArguments().size() == 1) {
                return lowerExpression(creationExpr.getArgument(0), scopes, context);
            }
            return new GpuIrLiteral(zeroLiteralForType(GpuTypeSupport.pointerValueType(creationExpr.getTypeAsString())));
        }

        throw new IllegalArgumentException("Unsupported expression for first lowering pass: " + expression);
    }

    private GpuIrExpression lowerHelperArgument(
            Expression argument,
            String expectedType,
            Deque<Map<String, String>> scopes,
            LoweringContext context
    ) {
        if (!GpuTypeSupport.isSupportedPointerType(expectedType)) {
            return lowerExpression(argument, scopes, context);
        }
        if (!(argument instanceof NameExpr nameExpr)) {
            throw new IllegalArgumentException("Pointer helper arguments must be pointer variables for lowering: " + argument);
        }
        String storageType = lookupStorageType(scopes, nameExpr.getNameAsString());
        if (GpuTypeSupport.isPointerReferenceStorage(storageType)) {
            return new GpuIrVariableRef(nameExpr.getNameAsString());
        }
        if (GpuTypeSupport.isSupportedPointerType(GpuTypeSupport.declaredType(storageType))) {
            return new GpuIrUnary("&", new GpuIrVariableRef(nameExpr.getNameAsString()));
        }
        throw new IllegalArgumentException("Pointer helper arguments must reference a supported pointer variable for lowering: " + argument);
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
            String storageType = lookupStorageType(scopes, nameExpr.getNameAsString());
            if (storageType != null) {
                return GpuTypeSupport.declaredType(storageType);
            }
            ConstantDescriptor constant = resolveConstant(nameExpr.getNameAsString(), "", context);
            return constant == null ? null : constant.javaType();
        }

        if (expression instanceof IntegerLiteralExpr) {
            return "int";
        }

        if (expression instanceof LongLiteralExpr) {
            return "long";
        }

        if (expression instanceof BooleanLiteralExpr) {
            return "boolean";
        }

        if (expression instanceof CharLiteralExpr) {
            return "int";
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
            String arrayType = GpuTypeSupport.declaredType(lookupStorageType(scopes, arrayAccessExpr.getName().toString()));
            if (arrayType != null && GpuTypeSupport.isSupportedArrayType(arrayType)) {
                return GpuTypeSupport.componentType(arrayType);
            }
            return null;
        }

        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            if (!(fieldAccessExpr.getScope() instanceof NameExpr nameExpr)) {
                return null;
            }
            if ("value".equals(fieldAccessExpr.getNameAsString())) {
                String storageType = lookupStorageType(scopes, nameExpr.getNameAsString());
                if (storageType == null || !GpuTypeSupport.isSupportedPointerType(GpuTypeSupport.declaredType(storageType))) {
                    return null;
                }
                return GpuTypeSupport.pointerValueType(storageType);
            }
            ConstantDescriptor constant = resolveConstant(fieldAccessExpr.getNameAsString(), nameExpr.getNameAsString(), context);
            return constant == null ? null : constant.javaType();
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
                HelperDescriptor helper = resolveHelperCall(
                        owner,
                        methodCallExpr.getNameAsString(),
                        inferArgumentTypes(methodCallExpr, scopes, context),
                        context
                );
                return helper == null ? null : helper.returnType();
            }
        }

        if (expression instanceof ObjectCreationExpr creationExpr
                && intrinsicDatabase.isAllowedAllocationType(creationExpr.getTypeAsString())
                && GpuTypeSupport.isSupportedPointerType(creationExpr.getTypeAsString())) {
            return creationExpr.getTypeAsString();
        }

        return null;
    }

    private String lookupStorageType(Deque<Map<String, String>> scopes, String name) {
        for (Map<String, String> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return null;
    }

    private Map<String, List<ConstantDescriptor>> buildConstantRegistry(ParsedGpuMethod kernelMethod, List<ParsedGpuMethod> helperMethods) {
        Map<String, List<ConstantDescriptor>> constantRegistry = new HashMap<>();
        List<ParsedGpuMethod> methods = new ArrayList<>();
        methods.add(kernelMethod);
        methods.addAll(helperMethods);
        for (ParsedGpuMethod method : methods) {
            for (ParsedGpuConstant constant : method.constants()) {
                List<ConstantDescriptor> constants = constantRegistry.computeIfAbsent(constant.name(), ignored -> new ArrayList<>());
                boolean alreadyPresent = constants.stream().anyMatch(existing ->
                        sameOwner(existing.ownerSimpleName(), existing.ownerQualifiedName(), constant.ownerSimpleName(), constant.ownerQualifiedName())
                                && existing.javaType().equals(constant.javaType())
                                && existing.sourceText().equals(constant.sourceText())
                );
                if (alreadyPresent) {
                    continue;
                }
                constants.add(new ConstantDescriptor(
                        constant.ownerSimpleName(),
                        constant.ownerQualifiedName(),
                        constant.name(),
                        constant.javaType(),
                        constant.sourceText()
                ));
            }
        }
        return constantRegistry;
    }

    private ConstantDescriptor resolveConstant(String constantName, String owner, LoweringContext context) {
        List<ConstantDescriptor> candidates = context.constantRegistry().getOrDefault(constantName, List.of());
        if (candidates.isEmpty()) {
            return null;
        }
        if (!owner.isBlank()) {
            List<ConstantDescriptor> ownerMatches = candidates.stream()
                    .filter(candidate -> ownerMatches(owner, candidate.ownerSimpleName(), candidate.ownerQualifiedName()))
                    .toList();
            return ownerMatches.size() == 1 ? ownerMatches.get(0) : null;
        }
        List<ConstantDescriptor> currentOwnerMatches = candidates.stream()
                .filter(candidate -> sameOwner(candidate.ownerSimpleName(), candidate.ownerQualifiedName(), context.ownerSimpleName(), context.ownerQualifiedName()))
                .toList();
        return currentOwnerMatches.size() == 1 ? currentOwnerMatches.get(0) : null;
    }

    private boolean ownerMatches(String owner, String ownerSimpleName, String ownerQualifiedName) {
        if (owner.equals(ownerSimpleName) || owner.equals(ownerQualifiedName)) {
            return true;
        }
        return ownerQualifiedName != null
                && !ownerQualifiedName.isBlank()
                && ownerQualifiedName.endsWith("." + owner);
    }

    private boolean sameOwner(String leftSimpleName, String leftQualifiedName, String rightSimpleName, String rightQualifiedName) {
        if (leftQualifiedName != null && !leftQualifiedName.isBlank()
                && rightQualifiedName != null && !rightQualifiedName.isBlank()) {
            return leftQualifiedName.equals(rightQualifiedName);
        }
        return leftSimpleName.equals(rightSimpleName);
    }

    private String zeroLiteralForType(String javaType) {
        return switch (javaType) {
            case "float" -> "0.0f";
            case "double" -> "0.0";
            default -> "0";
        };
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

    private HelperDescriptor resolveHelperCall(
            String owner,
            String methodName,
            List<String> argumentTypes,
            LoweringContext context
    ) {
        List<HelperDescriptor> compatibleCandidates = findCompatibleHelpers(methodName, argumentTypes, context.helperRegistry());
        if (compatibleCandidates.isEmpty()) {
            return null;
        }
        if (!owner.isBlank()) {
            List<HelperDescriptor> ownerMatches = compatibleCandidates.stream()
                    .filter(candidate -> ownerMatches(owner, candidate))
                    .toList();
            return selectBestHelper(ownerMatches, argumentTypes);
        }

        List<HelperDescriptor> currentOwnerMatches = compatibleCandidates.stream()
                .filter(candidate -> belongsToCurrentOwner(candidate, context))
                .toList();
        HelperDescriptor currentOwnerResolved = selectBestHelper(currentOwnerMatches, argumentTypes);
        if (currentOwnerResolved != null) {
            return currentOwnerResolved;
        }
        if (!currentOwnerMatches.isEmpty()) {
            return null;
        }
        return selectBestHelper(compatibleCandidates, argumentTypes);
    }

    private List<HelperDescriptor> findCompatibleHelpers(
            String methodName,
            List<String> argumentTypes,
            Map<HelperSignature, List<HelperDescriptor>> helperRegistry
    ) {
        List<HelperDescriptor> compatible = new ArrayList<>();
        for (Map.Entry<HelperSignature, List<HelperDescriptor>> entry : helperRegistry.entrySet()) {
            if (!entry.getKey().name().equals(methodName)) {
                continue;
            }
            if (!isCompatibleSignature(argumentTypes, entry.getKey().argumentTypes())) {
                continue;
            }
            compatible.addAll(entry.getValue());
        }
        return compatible;
    }

    private boolean isCompatibleSignature(List<String> argumentTypes, List<String> parameterTypes) {
        if (argumentTypes.size() != parameterTypes.size()) {
            return false;
        }
        for (int i = 0; i < argumentTypes.size(); i++) {
            if (!GpuTypeSupport.isHelperArgumentCompatible(argumentTypes.get(i), parameterTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    private HelperDescriptor selectBestHelper(List<HelperDescriptor> candidates, List<String> argumentTypes) {
        HelperDescriptor best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (HelperDescriptor candidate : candidates) {
            int score = helperCompatibilityScore(argumentTypes, candidate.argumentTypes());
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        return ambiguous ? null : best;
    }

    private int helperCompatibilityScore(List<String> argumentTypes, List<String> parameterTypes) {
        int score = 0;
        for (int i = 0; i < argumentTypes.size(); i++) {
            score += GpuTypeSupport.helperCompatibilityScore(argumentTypes.get(i), parameterTypes.get(i));
        }
        return score;
    }

    private boolean belongsToCurrentOwner(HelperDescriptor descriptor, LoweringContext context) {
        if (context.ownerQualifiedName() != null && !context.ownerQualifiedName().isBlank()
                && context.ownerQualifiedName().equals(descriptor.ownerQualifiedName())) {
            return true;
        }
        return context.ownerSimpleName() != null
                && !context.ownerSimpleName().isBlank()
                && context.ownerSimpleName().equals(descriptor.ownerSimpleName());
    }

    private boolean ownerMatches(String owner, HelperDescriptor descriptor) {
        if (owner.equals(descriptor.ownerSimpleName()) || owner.equals(descriptor.ownerQualifiedName())) {
            return true;
        }
        return descriptor.ownerQualifiedName() != null
                && !descriptor.ownerQualifiedName().isBlank()
                && descriptor.ownerQualifiedName().endsWith("." + owner);
    }

    private Map<HelperSignature, List<HelperDescriptor>> buildHelperRegistry(List<ParsedGpuMethod> helperMethods) {
        Map<HelperSignature, List<HelperDescriptor>> helperRegistry = new HashMap<>();
        for (ParsedGpuMethod helperMethod : helperMethods) {
            List<String> argumentTypes = helperMethod.parameters().stream().map(parameter -> parameter.javaType()).toList();
            helperRegistry.computeIfAbsent(new HelperSignature(helperMethod.name(), argumentTypes), ignored -> new ArrayList<>())
                    .add(new HelperDescriptor(
                            helperMethod.ownerSimpleName(),
                            helperMethod.ownerQualifiedName(),
                            helperMethod.name(),
                            OpenClKernelNaming.toHelperFunctionName(helperMethod.ownerSimpleName(), helperMethod.name(), argumentTypes),
                            helperMethod.returnType(),
                            argumentTypes,
                            helperMethod.inline()
                    ));
        }
        return helperRegistry;
    }

    private Set<String> collectHelperDependencies(GpuIrMethod method) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        for (GpuIrStatement statement : method.statements()) {
            collectHelperDependencies(statement, dependencies);
        }
        return dependencies;
    }

    private void collectHelperDependencies(GpuIrStatement statement, Set<String> dependencies) {
        if (statement instanceof GpuIrVariableDeclaration declaration) {
            collectHelperDependencies(declaration.initializer(), dependencies);
            return;
        }
        if (statement instanceof GpuIrAssignment assignment) {
            collectHelperDependencies(assignment.target(), dependencies);
            collectHelperDependencies(assignment.value(), dependencies);
            return;
        }
        if (statement instanceof GpuIrExpressionStatement expressionStatement) {
            collectHelperDependencies(expressionStatement.expression(), dependencies);
            return;
        }
        if (statement instanceof GpuIrForLoop loop) {
            collectHelperDependencies(loop.initializer(), dependencies);
            collectHelperDependencies(loop.condition(), dependencies);
            collectHelperDependencies(loop.update(), dependencies);
            loop.body().forEach(bodyStatement -> collectHelperDependencies(bodyStatement, dependencies));
            return;
        }
        if (statement instanceof GpuIrIf ifStatement) {
            collectHelperDependencies(ifStatement.condition(), dependencies);
            ifStatement.thenBranch().forEach(thenStatement -> collectHelperDependencies(thenStatement, dependencies));
            ifStatement.elseBranch().forEach(elseStatement -> collectHelperDependencies(elseStatement, dependencies));
            return;
        }
        if (statement instanceof GpuIrWhileLoop loop) {
            collectHelperDependencies(loop.condition(), dependencies);
            loop.body().forEach(bodyStatement -> collectHelperDependencies(bodyStatement, dependencies));
            return;
        }
        if (statement instanceof GpuIrDoWhileLoop loop) {
            loop.body().forEach(bodyStatement -> collectHelperDependencies(bodyStatement, dependencies));
            collectHelperDependencies(loop.condition(), dependencies);
            return;
        }
        if (statement instanceof GpuIrSwitch switchStatement) {
            collectHelperDependencies(switchStatement.selector(), dependencies);
            for (GpuIrSwitchCase switchCase : switchStatement.cases()) {
                switchCase.labels().forEach(label -> collectHelperDependencies(label, dependencies));
                switchCase.statements().forEach(caseStatement -> collectHelperDependencies(caseStatement, dependencies));
            }
            return;
        }
        if (statement instanceof GpuIrReturn gpuIrReturn && gpuIrReturn.value() != null) {
            collectHelperDependencies(gpuIrReturn.value(), dependencies);
        }
    }

    private void collectHelperDependencies(GpuIrExpression expression, Set<String> dependencies) {
        if (expression instanceof GpuIrHelperCall helperCall) {
            dependencies.add(helperCall.helperName());
            helperCall.arguments().forEach(argument -> collectHelperDependencies(argument, dependencies));
            return;
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            collectHelperDependencies(arrayAccess.index(), dependencies);
            return;
        }
        if (expression instanceof GpuIrBinary binary) {
            collectHelperDependencies(binary.left(), dependencies);
            collectHelperDependencies(binary.right(), dependencies);
            return;
        }
        if (expression instanceof GpuIrCast cast) {
            collectHelperDependencies(cast.expression(), dependencies);
            return;
        }
        if (expression instanceof GpuIrIntrinsicCall intrinsicCall) {
            intrinsicCall.arguments().forEach(argument -> collectHelperDependencies(argument, dependencies));
            return;
        }
        if (expression instanceof GpuIrTernary ternary) {
            collectHelperDependencies(ternary.condition(), dependencies);
            collectHelperDependencies(ternary.whenTrue(), dependencies);
            collectHelperDependencies(ternary.whenFalse(), dependencies);
            return;
        }
        if (expression instanceof GpuIrUnary unary) {
            collectHelperDependencies(unary.operand(), dependencies);
        }
    }

    private record LoweringContext(
            Map<HelperSignature, List<HelperDescriptor>> helperRegistry,
            String ownerSimpleName,
            String ownerQualifiedName,
            Map<String, List<ConstantDescriptor>> constantRegistry
    ) {
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

    private record ConstantDescriptor(
            String ownerSimpleName,
            String ownerQualifiedName,
            String name,
            String javaType,
            String sourceText
    ) {
    }
}
