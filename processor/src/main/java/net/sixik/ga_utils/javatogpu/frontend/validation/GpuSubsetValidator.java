package net.sixik.ga_utils.javatogpu.frontend.validation;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
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
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuBuiltinConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStructField;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelNaming;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GpuSubsetValidator {

    private static final Pattern ATTRIBUTE_CALL_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\((.*)\\)$");
    private static final Pattern POSITIVE_INTEGER_PATTERN = Pattern.compile("^[1-9][0-9]*$");
    private static final Set<String> KERNEL_METHOD_ATTRIBUTES = Set.of(
            "reqd_work_group_size",
            "work_group_size_hint",
            "vec_type_hint"
    );
    private static final Set<String> HELPER_METHOD_ATTRIBUTES = Set.of(
            "always_inline",
            "noinline"
    );
    private static final Set<String> STRUCT_ATTRIBUTES = Set.of(
            "packed",
            "aligned"
    );
    private static final Set<String> FIELD_ATTRIBUTES = Set.of(
            "aligned"
    );
    private static final Set<String> PARAMETER_QUALIFIERS = Set.of(
            "const",
            "restrict",
            "volatile"
    );

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
        validateKernel(method, List.of(), List.of());
    }

    public void validateKernel(ParsedGpuMethod kernelMethod, List<ParsedGpuMethod> helperMethods) {
        validateKernel(kernelMethod, helperMethods, List.of());
    }

    public void validateKernel(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        List<GpuValidationIssue> issues = new ArrayList<>();
        validateOpenClAttributes(kernelMethod, helperMethods, structs, issues);
        Map<HelperSignature, List<HelperDescriptor>> helperRegistry = buildHelperRegistry(helperMethods, issues);
        Map<String, List<ConstantDescriptor>> constantRegistry = buildConstantRegistry(kernelMethod, helperMethods, structs, issues);
        Map<String, StructDescriptor> structRegistry = buildStructRegistry(structs, issues);

        helperMethods.forEach(helperMethod -> validateMethod(helperMethod, helperRegistry, constantRegistry, structRegistry, issues, false));
        validateMethod(kernelMethod, helperRegistry, constantRegistry, structRegistry, issues, true);

        if (!issues.isEmpty()) {
            throw new GpuValidationException(issues);
        }
    }

    private void validateMethod(
            ParsedGpuMethod method,
            Map<HelperSignature, List<HelperDescriptor>> helperRegistry,
            Map<String, List<ConstantDescriptor>> constantRegistry,
            Map<String, StructDescriptor> structRegistry,
            List<GpuValidationIssue> issues,
            boolean kernelEntry
    ) {
        Deque<Map<String, String>> scopes = new ArrayDeque<>();
        scopes.push(new HashMap<>());
        method.parameters().forEach(parameter -> scopes.peek().put(parameter.name(), GpuTypeSupport.parameterStorageType(parameter.javaType())));
        ValidationContext context = new ValidationContext(
                kernelEntry,
                method.returnType(),
                method.ownerSimpleName(),
                method.ownerQualifiedName(),
                helperRegistry,
                constantRegistry,
                structRegistry
        );

        validateReturnType(method, issues, kernelEntry, structRegistry);
        validateParameters(method, issues, kernelEntry, structRegistry);
        validateMethodBody(method, issues, scopes, context);
    }

    private void validateMethodBody(
            ParsedGpuMethod method,
            List<GpuValidationIssue> issues,
            Deque<Map<String, String>> scopes,
            ValidationContext context
    ) {
        boolean hasBody = method.declaration().getBody().isPresent();
        boolean hasNativeCode = !method.nativeCode().isBlank();

        if (context.kernelEntry()) {
            if (hasNativeCode) {
                issues.add(issue(method.declaration(), "@GPU methods cannot use @CCode(code = \"...\")"));
            }
            if (!hasBody) {
                issues.add(issue(method.declaration(), "GPU method must have a body"));
                return;
            }
        } else {
            if (hasBody && hasNativeCode) {
                issues.add(issue(method.declaration(), "@CCode helpers must use either a Java body or code = \"...\", not both"));
                return;
            }
            if (!hasBody && !hasNativeCode) {
                String message = method.nativeDeclaration()
                        ? "Native @CCode helper must define code = \"...\""
                        : "@CCode helper must have a body or define code = \"...\"";
                issues.add(issue(method.declaration(), message));
                return;
            }
            if (hasNativeCode) {
                return;
            }
        }

        method.declaration().getBody().ifPresent(body ->
                body.getStatements().forEach(statement -> validateStatement(statement, issues, scopes, context))
        );
    }

    private void validateReturnType(
            ParsedGpuMethod method,
            List<GpuValidationIssue> issues,
            boolean kernelEntry,
            Map<String, StructDescriptor> structRegistry
    ) {
        if (kernelEntry && !"void".equals(method.returnType())) {
            issues.add(issue(
                    method.declaration().getType(),
                    "Non-void @GPU methods are not supported in the current pipeline; use a void method with output buffers"
            ));
        }

        if (!kernelEntry
                && !"void".equals(method.returnType())
                && !GpuTypeSupport.isSupportedScalarType(method.returnType())) {
            if (GpuTypeSupport.isSupportedVectorType(method.returnType())) {
                return;
            }
            if (isStructType(method.returnType(), structRegistry)) {
                return;
            }
            issues.add(issue(
                    method.declaration().getType(),
                    "Unsupported @CCode helper return type: " + method.returnType()
            ));
        }
    }

    private void validateParameters(
            ParsedGpuMethod method,
            List<GpuValidationIssue> issues,
            boolean kernelEntry,
            Map<String, StructDescriptor> structRegistry
    ) {
        method.parameters().forEach(parameter -> {
            String type = parameter.javaType();
            if (!kernelEntry && parameter.addressSpace() != GpuAddressSpace.PRIVATE) {
                issues.add(new GpuValidationIssue(
                        1,
                        1,
                        "Address space annotations are only supported on @GPU entry parameters: " + parameter.name()
                ));
                return;
            }
            boolean supported = kernelEntry
                    ? GpuTypeSupport.isSupportedKernelParameterType(type)
                    || GpuTypeSupport.isSupportedVectorType(type)
                    || isStructType(type, structRegistry)
                    || isPackedArrayType(type, structRegistry)
                    : GpuTypeSupport.isSupportedHelperParameterType(type) || isStructType(type, structRegistry);
            if (!supported) {
                issues.add(new GpuValidationIssue(1, 1, "Unsupported GPU parameter type: " + type));
                return;
            }
            if (parameter.addressSpace() == GpuAddressSpace.GLOBAL && !GpuTypeSupport.isGlobalParameterCompatible(type)) {
                if (isPackedArrayType(type, structRegistry)) {
                    return;
                }
                issues.add(new GpuValidationIssue(
                        1,
                        1,
                        "@GPUGlobal is only supported on primitive, vector, or @GPUStruct array parameters in the current pipeline: " + type
                ));
                return;
            }

            if (parameter.addressSpace() == GpuAddressSpace.CONSTANT && !isSupportedArrayParameterType(type, structRegistry)) {
                issues.add(new GpuValidationIssue(
                        1,
                        1,
                        "@GPUConstant is only supported on primitive, vector, or @GPUStruct array parameters in the current pipeline: " + type
                ));
                return;
            }

            if (parameter.addressSpace() == GpuAddressSpace.LOCAL && !isSupportedArrayParameterType(type, structRegistry)) {
                issues.add(new GpuValidationIssue(
                        1,
                        1,
                        "@GPULocal is only supported on primitive, vector, or @GPUStruct array parameters in the current pipeline: " + type
                ));
                return;
            }

            if (parameter.addressSpace() == GpuAddressSpace.PRIVATE && isSupportedArrayParameterType(type, structRegistry)) {
                issues.add(new GpuValidationIssue(
                        1,
                        1,
                        "Array parameters must be annotated with @GPUGlobal, @GPUConstant, or @GPULocal in the current pipeline: " + type
                ));
            }

            validateParameterQualifiers(parameter, issues);
        });
    }

    private void validateParameterQualifiers(ParsedGpuParameter parameter, List<GpuValidationIssue> issues) {
        if (parameter.openClQualifiers().isEmpty()) {
            return;
        }
        boolean pointerLike = GpuTypeSupport.isSupportedPointerType(parameter.javaType()) || GpuTypeSupport.isArrayType(parameter.javaType());
        if (!pointerLike) {
            issues.add(new GpuValidationIssue(
                    1,
                    1,
                    "OpenCLQualifiers are only supported on pointer-like GPU parameters in the current pipeline: " + parameter.javaType()
            ));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String qualifier : parameter.openClQualifiers()) {
            if (!PARAMETER_QUALIFIERS.contains(qualifier)) {
                issues.add(new GpuValidationIssue(
                        1,
                        1,
                        "Unsupported OpenCL parameter qualifier: " + qualifier
                ));
                continue;
            }
            if (!seen.add(qualifier)) {
                issues.add(new GpuValidationIssue(
                        1,
                        1,
                        "Duplicate OpenCL parameter qualifier: " + qualifier
                ));
            }
        }
    }

    private void validateOpenClAttributes(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            List<GpuValidationIssue> issues
    ) {
        validateMethodAttributes(kernelMethod, true, issues);
        helperMethods.forEach(method -> validateMethodAttributes(method, false, issues));
        structs.forEach(struct -> validateStructAttributes(struct, issues));
    }

    private void validateMethodAttributes(ParsedGpuMethod method, boolean kernelEntry, List<GpuValidationIssue> issues) {
        Set<String> seenUniqueAttributes = new HashSet<>();
        for (String rawAttribute : method.openClAttributes()) {
            AttributeSpec attribute = parseAttribute(rawAttribute);
            if (!kernelEntry) {
                if (!HELPER_METHOD_ATTRIBUTES.contains(attribute.name())) {
                    issues.add(issue(
                            method.declaration(),
                            "OpenCL attribute '" + attribute.name() + "' is not valid on @CCode helpers"
                    ));
                    continue;
                }
                validateUniqueAttribute(method.declaration(), attribute, seenUniqueAttributes, issues);
                validateNoArgumentAttribute(method.declaration(), attribute, issues);
                continue;
            }
            if (!KERNEL_METHOD_ATTRIBUTES.contains(attribute.name())) {
                if (STRUCT_ATTRIBUTES.contains(attribute.name()) || FIELD_ATTRIBUTES.contains(attribute.name())) {
                    issues.add(issue(method.declaration(), "OpenCL attribute '" + attribute.name() + "' is not valid on @GPU methods"));
                }
                continue;
            }
            validateUniqueAttribute(method.declaration(), attribute, seenUniqueAttributes, issues);
            switch (attribute.name()) {
                case "reqd_work_group_size", "work_group_size_hint" ->
                        validateTriplePositiveIntegersAttribute(method.declaration(), attribute, issues);
                case "vec_type_hint" -> validateNonEmptySingleArgumentAttribute(method.declaration(), attribute, issues);
                default -> {
                }
            }
        }
    }

    private void validateStructAttributes(ParsedGpuStruct struct, List<GpuValidationIssue> issues) {
        Set<String> seenUniqueAttributes = new HashSet<>();
        for (String rawAttribute : struct.openClAttributes()) {
            AttributeSpec attribute = parseAttribute(rawAttribute);
            if (!STRUCT_ATTRIBUTES.contains(attribute.name())) {
                if (KERNEL_METHOD_ATTRIBUTES.contains(attribute.name())) {
                    issues.add(new GpuValidationIssue(1, 1, "OpenCL attribute '" + attribute.name() + "' is not valid on @GPUStruct types"));
                }
                continue;
            }
            validateUniqueAttribute(null, attribute, seenUniqueAttributes, issues, "Duplicate OpenCL attribute on @GPUStruct: " + attribute.name());
            if ("aligned".equals(attribute.name())) {
                validateSinglePositiveIntegerAttribute(null, attribute, issues, "@GPUStruct aligned(...) requires a single positive integer");
            }
        }

        for (ParsedGpuStructField field : struct.fields()) {
            Set<String> seenFieldAttributes = new HashSet<>();
            for (String rawAttribute : field.openClAttributes()) {
                AttributeSpec attribute = parseAttribute(rawAttribute);
                if (!FIELD_ATTRIBUTES.contains(attribute.name())) {
                    issues.add(new GpuValidationIssue(
                            1,
                            1,
                            "OpenCL attribute '" + attribute.name() + "' is not valid on @GPUStruct fields"
                    ));
                    continue;
                }
                validateUniqueAttribute(null, attribute, seenFieldAttributes, issues, "Duplicate OpenCL attribute on @GPUStruct field: " + attribute.name());
                validateSinglePositiveIntegerAttribute(null, attribute, issues, "@GPUStruct field aligned(...) requires a single positive integer");
            }
        }
    }

    private void validateUniqueAttribute(Node node, AttributeSpec attribute, Set<String> seenUniqueAttributes, List<GpuValidationIssue> issues) {
        validateUniqueAttribute(node, attribute, seenUniqueAttributes, issues, "Duplicate OpenCL attribute: " + attribute.name());
    }

    private void validateUniqueAttribute(
            Node node,
            AttributeSpec attribute,
            Set<String> seenUniqueAttributes,
            List<GpuValidationIssue> issues,
            String message
    ) {
        if (!seenUniqueAttributes.add(attribute.name())) {
            if (node != null) {
                issues.add(issue(node, message));
            } else {
                issues.add(new GpuValidationIssue(1, 1, message));
            }
        }
    }

    private void validateTriplePositiveIntegersAttribute(Node node, AttributeSpec attribute, List<GpuValidationIssue> issues) {
        if (attribute.arguments().size() != 3 || attribute.arguments().stream().anyMatch(argument -> !POSITIVE_INTEGER_PATTERN.matcher(argument).matches())) {
            issues.add(issue(node, attribute.name() + "(...) requires exactly three positive integer arguments"));
        }
    }

    private void validateNonEmptySingleArgumentAttribute(Node node, AttributeSpec attribute, List<GpuValidationIssue> issues) {
        if (attribute.arguments().size() != 1 || attribute.arguments().get(0).isBlank()) {
            issues.add(issue(node, attribute.name() + "(...) requires exactly one non-empty argument"));
        }
    }

    private void validateSinglePositiveIntegerAttribute(
            Node node,
            AttributeSpec attribute,
            List<GpuValidationIssue> issues,
            String message
    ) {
        if (attribute.arguments().size() != 1 || !POSITIVE_INTEGER_PATTERN.matcher(attribute.arguments().get(0)).matches()) {
            if (node != null) {
                issues.add(issue(node, message));
            } else {
                issues.add(new GpuValidationIssue(1, 1, message));
            }
        }
    }

    private void validateNoArgumentAttribute(Node node, AttributeSpec attribute, List<GpuValidationIssue> issues) {
        if (!attribute.arguments().isEmpty()) {
            issues.add(issue(node, attribute.name() + " does not accept arguments"));
        }
    }

    private AttributeSpec parseAttribute(String rawAttribute) {
        String trimmed = rawAttribute == null ? "" : rawAttribute.strip();
        Matcher matcher = ATTRIBUTE_CALL_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return new AttributeSpec(trimmed, trimmed, List.of());
        }
        String arguments = matcher.group(2).strip();
        List<String> values = arguments.isBlank()
                ? List.of()
                : java.util.Arrays.stream(arguments.split(","))
                .map(String::strip)
                .toList();
        return new AttributeSpec(trimmed, matcher.group(1), values);
    }

    private void validateStatement(
            Statement statement,
            List<GpuValidationIssue> issues,
            Deque<Map<String, String>> scopes,
            ValidationContext context
    ) {
        if (statement instanceof ExpressionStmt expressionStmt) {
            validateExpressionStatement(expressionStmt, issues, scopes, context);
            return;
        }

        if (statement instanceof BlockStmt blockStmt) {
            validateBlock(blockStmt, issues, scopes, context);
            return;
        }

        if (statement instanceof ForStmt forStmt) {
            validateForLoop(forStmt, issues, scopes, context);
            return;
        }

        if (statement instanceof IfStmt ifStmt) {
            validateIf(ifStmt, issues, scopes, context);
            return;
        }

        if (statement instanceof WhileStmt whileStmt) {
            validateWhileLoop(whileStmt, issues, scopes, context);
            return;
        }

        if (statement instanceof DoStmt doStmt) {
            validateDoWhileLoop(doStmt, issues, scopes, context);
            return;
        }

        if (statement instanceof SwitchStmt switchStmt) {
            validateSwitch(switchStmt, issues, scopes, context);
            return;
        }

        if (statement instanceof ReturnStmt returnStmt) {
            validateReturnStatement(returnStmt, issues, scopes, context);
            return;
        }

        if (statement instanceof BreakStmt || statement instanceof ContinueStmt) {
            return;
        }

        issues.add(issue(statement, "Unsupported statement in @GPU method: " + statement.getClass().getSimpleName()));
    }

    private void validateExpressionStatement(
            ExpressionStmt statement,
            List<GpuValidationIssue> issues,
            Deque<Map<String, String>> scopes,
            ValidationContext context
    ) {
        Expression expression = statement.getExpression();

        if (expression instanceof VariableDeclarationExpr declarationExpr) {
            if (declarationExpr.getVariables().size() != 1) {
                issues.add(issue(statement, "Only single-variable declarations are supported in @GPU methods"));
                return;
            }

            VariableDeclarator variable = declarationExpr.getVariables().get(0);
            validateVariableType(variable, issues, context.structRegistry());
            if (variable.getInitializer().isEmpty()) {
                issues.add(issue(variable, "Variable declarations in @GPU methods must have an initializer"));
                return;
            }
            validateExpression(variable.getInitializer().orElseThrow(), issues, scopes, context);
            scopes.peek().put(variable.getNameAsString(), variable.getTypeAsString());
            return;
        }

        if (expression instanceof AssignExpr assignExpr) {
            validateAssignment(assignExpr, issues, scopes, context);
            return;
        }

        if (expression instanceof UnaryExpr unaryExpr) {
            validateUpdateExpression(unaryExpr, issues, scopes, context, "Unsupported expression statement in @GPU method: " + expression);
            return;
        }

        if (expression instanceof MethodCallExpr methodCallExpr) {
            validateMethodCall(methodCallExpr, issues, scopes, context);
            String resultType = inferExpressionType(methodCallExpr, scopes, context);
            if (resultType != null && !"void".equals(resultType)) {
                issues.add(issue(statement, "Only void @CCode helper calls can be used as standalone statements in @GPU methods"));
            }
            return;
        }

        issues.add(issue(statement, "Unsupported expression statement in @GPU method: " + expression));
    }

    private void validateBlock(
            BlockStmt blockStmt,
            List<GpuValidationIssue> issues,
            Deque<Map<String, String>> scopes,
            ValidationContext context
    ) {
        scopes.push(new HashMap<>());
        blockStmt.getStatements().forEach(statement -> validateStatement(statement, issues, scopes, context));
        scopes.pop();
    }

    private void validateForLoop(ForStmt forStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        scopes.push(new HashMap<>());
        if (forStmt.getInitialization().size() != 1) {
            issues.add(issue(forStmt, "Only single-initializer for loops are supported in @GPU methods"));
        } else {
            validateForInitializer(forStmt.getInitialization().get(0), issues, scopes, context);
        }

        if (forStmt.getCompare().isEmpty()) {
            issues.add(issue(forStmt, "for loops must contain a compare expression"));
        } else {
            validateExpression(forStmt.getCompare().orElseThrow(), issues, scopes, context);
        }

        if (forStmt.getUpdate().size() != 1) {
            issues.add(issue(forStmt, "Only single-update for loops are supported in @GPU methods"));
        } else {
            validateForUpdate(forStmt.getUpdate().get(0), issues, scopes, context);
        }

        if (!forStmt.getBody().isBlockStmt()) {
            issues.add(issue(forStmt.getBody(), "for loop bodies must use braces in @GPU methods"));
            scopes.pop();
            return;
        }

        forStmt.getBody().asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes, context));
        scopes.pop();
    }

    private void validateIf(IfStmt ifStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        validateExpression(ifStmt.getCondition(), issues, scopes, context);

        if (!ifStmt.getThenStmt().isBlockStmt()) {
            issues.add(issue(ifStmt.getThenStmt(), "if branches must use braces in @GPU methods"));
        } else {
            scopes.push(new HashMap<>());
            ifStmt.getThenStmt().asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes, context));
            scopes.pop();
        }

        ifStmt.getElseStmt().ifPresent(elseStmt -> {
            if (elseStmt.isIfStmt()) {
                validateIf(elseStmt.asIfStmt(), issues, scopes, context);
                return;
            }

            if (!elseStmt.isBlockStmt()) {
                issues.add(issue(elseStmt, "else branches must use braces or else-if in @GPU methods"));
                return;
            }

            scopes.push(new HashMap<>());
            elseStmt.asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes, context));
            scopes.pop();
        });
    }

    private void validateWhileLoop(WhileStmt whileStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        validateExpression(whileStmt.getCondition(), issues, scopes, context);

        if (!whileStmt.getBody().isBlockStmt()) {
            issues.add(issue(whileStmt.getBody(), "while loop bodies must use braces in @GPU methods"));
            return;
        }

        scopes.push(new HashMap<>());
        whileStmt.getBody().asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes, context));
        scopes.pop();
    }

    private void validateDoWhileLoop(DoStmt doStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        if (!doStmt.getBody().isBlockStmt()) {
            issues.add(issue(doStmt.getBody(), "do-while loop bodies must use braces in @GPU methods"));
            return;
        }

        scopes.push(new HashMap<>());
        doStmt.getBody().asBlockStmt().getStatements().forEach(statement -> validateStatement(statement, issues, scopes, context));
        scopes.pop();
        validateExpression(doStmt.getCondition(), issues, scopes, context);
    }

    private void validateSwitch(SwitchStmt switchStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        validateExpression(switchStmt.getSelector(), issues, scopes, context);

        scopes.push(new HashMap<>());
        for (SwitchEntry entry : switchStmt.getEntries()) {
            if (!isSupportedSwitchEntryType(entry.getType())) {
                issues.add(issue(entry, "Only classic switch cases and rule-style case -> with expressions/blocks are supported in @GPU methods"));
                continue;
            }

            entry.getLabels().forEach(label -> validateExpression(label, issues, scopes, context));
            entryStatements(entry).forEach(statement -> validateStatement(statement, issues, scopes, context));
        }
        scopes.pop();
    }

    private void validateForInitializer(Expression expression, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        if (!(expression instanceof VariableDeclarationExpr declarationExpr)) {
            issues.add(issue(expression, "Unsupported for initializer in @GPU method: " + expression));
            return;
        }

        if (declarationExpr.getVariables().size() != 1) {
            issues.add(issue(expression, "Only single-variable for initializers are supported in @GPU methods"));
            return;
        }

        VariableDeclarator variable = declarationExpr.getVariables().get(0);
        validateVariableType(variable, issues, context.structRegistry());
        if (variable.getInitializer().isEmpty()) {
            issues.add(issue(variable, "For initializer variables in @GPU methods must have an initializer"));
            return;
        }
        validateExpression(variable.getInitializer().orElseThrow(), issues, scopes, context);
        scopes.peek().put(variable.getNameAsString(), variable.getTypeAsString());
    }

    private void validateForUpdate(Expression expression, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        if (expression instanceof UnaryExpr unaryExpr) {
            validateUpdateExpression(unaryExpr, issues, scopes, context, "Unsupported for update in @GPU method: " + expression);
            return;
        }

        if (expression instanceof AssignExpr assignExpr) {
            validateAssignment(assignExpr, issues, scopes, context);
            return;
        }

        issues.add(issue(expression, "Unsupported for update in @GPU method: " + expression));
    }

    private void validateAssignment(AssignExpr assignExpr, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        validateExpression(assignExpr.getTarget(), issues, scopes, context);
        validateExpression(assignExpr.getValue(), issues, scopes, context);

        if (assignExpr.getOperator() == AssignExpr.Operator.ASSIGN) {
            return;
        }

        String compoundBinaryOperator = compoundBinaryOperator(assignExpr.getOperator());
        if (compoundBinaryOperator == null) {
            issues.add(issue(assignExpr, "Unsupported assignment operator in @GPU method: " + assignExpr.getOperator().asString()));
            return;
        }

        if (INTEGER_ONLY_BINARY_OPERATORS.contains(compoundBinaryOperator)) {
            String targetType = inferExpressionType(assignExpr.getTarget(), scopes, context);
            String valueType = inferExpressionType(assignExpr.getValue(), scopes, context);
            if (!GpuTypeSupport.isIntegralScalarType(targetType) || !GpuTypeSupport.isIntegralScalarType(valueType)) {
                issues.add(issue(
                        assignExpr,
                        "Operator " + assignExpr.getOperator().asString() + " is only supported for integral scalar expressions in @GPU methods"
                ));
            }
        }
    }

    private void validateExpression(Expression expression, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        if (expression instanceof NameExpr
                || expression instanceof IntegerLiteralExpr
                || expression instanceof LongLiteralExpr
                || expression instanceof BooleanLiteralExpr
                || expression instanceof CharLiteralExpr
                || expression instanceof DoubleLiteralExpr) {
            if (expression instanceof NameExpr nameExpr && !isResolvableValueName(nameExpr.getNameAsString(), scopes, context)) {
                issues.add(issue(expression, "Unknown identifier in @GPU method: " + nameExpr.getNameAsString()));
            }
            return;
        }

        if (expression instanceof EnclosedExpr enclosedExpr) {
            validateExpression(enclosedExpr.getInner(), issues, scopes, context);
            return;
        }

        if (expression instanceof ArrayAccessExpr arrayAccessExpr) {
            if (!(arrayAccessExpr.getName() instanceof NameExpr)) {
                issues.add(issue(arrayAccessExpr, "Only direct array access is supported in @GPU methods"));
                return;
            }

            validateExpression(arrayAccessExpr.getIndex(), issues, scopes, context);
            return;
        }

        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            validateFieldAccess(fieldAccessExpr, issues, scopes, context);
            return;
        }

        if (expression instanceof BinaryExpr binaryExpr) {
            if (!ALLOWED_BINARY_OPERATORS.contains(binaryExpr.getOperator().asString())) {
                issues.add(issue(binaryExpr, "Unsupported binary operator in @GPU method: " + binaryExpr.getOperator().asString()));
                return;
            }
            validateExpression(binaryExpr.getLeft(), issues, scopes, context);
            validateExpression(binaryExpr.getRight(), issues, scopes, context);
            if (INTEGER_ONLY_BINARY_OPERATORS.contains(binaryExpr.getOperator().asString())) {
                String leftType = inferExpressionType(binaryExpr.getLeft(), scopes, context);
                String rightType = inferExpressionType(binaryExpr.getRight(), scopes, context);
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
            validateExpression(conditionalExpr.getCondition(), issues, scopes, context);
            validateExpression(conditionalExpr.getThenExpr(), issues, scopes, context);
            validateExpression(conditionalExpr.getElseExpr(), issues, scopes, context);
            return;
        }

        if (expression instanceof UnaryExpr unaryExpr) {
            if (!ALLOWED_UNARY_OPERATORS.contains(unaryExpr.getOperator().asString())) {
                issues.add(issue(unaryExpr, "Unsupported unary operator in @GPU method: " + unaryExpr.getOperator().asString()));
                return;
            }

            validateExpression(unaryExpr.getExpression(), issues, scopes, context);
            if ("~".equals(unaryExpr.getOperator().asString())) {
                String operandType = inferExpressionType(unaryExpr.getExpression(), scopes, context);
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
            validateMethodCall(methodCallExpr, issues, scopes, context);
            return;
        }

        if (expression instanceof CastExpr castExpr) {
            validateCast(castExpr, issues, scopes, context);
            return;
        }

        if (expression instanceof ObjectCreationExpr objectCreationExpr) {
            validateObjectCreation(objectCreationExpr, issues, scopes, context);
            return;
        }

        issues.add(issue(expression, "Unsupported expression in @GPU method: " + expression));
    }

    private void validateObjectCreation(
            ObjectCreationExpr creation,
            List<GpuValidationIssue> issues,
            Deque<Map<String, String>> scopes,
            ValidationContext context
    ) {
        String typeName = creation.getTypeAsString();
        if (intrinsicDatabase.isAllowedAllocationType(typeName)) {
            boolean pointerType = GpuTypeSupport.isSupportedPointerType(typeName);
            boolean scalarAliasType = GpuTypeSupport.isSupportedScalarAliasType(typeName);
            if (!pointerType && !scalarAliasType) {
                issues.add(issue(creation, "Unsupported intrinsic allocation type in @GPU methods: " + typeName));
                return;
            }
            if (creation.getArguments().size() > 1) {
                issues.add(issue(creation, "Intrinsic allocation supports at most one constructor argument in @GPU methods: " + typeName));
                return;
            }
            if (creation.getArguments().size() == 1) {
                Expression argument = creation.getArgument(0);
                validateExpression(argument, issues, scopes, context);
                String argumentType = inferExpressionType(argument, scopes, context);
                String expectedType = pointerType
                        ? GpuTypeSupport.pointerValueType(typeName)
                        : GpuTypeSupport.scalarAliasValueType(typeName);
                if (!GpuTypeSupport.isHelperArgumentCompatible(argumentType, expectedType)) {
                    issues.add(issue(
                            argument,
                            "Intrinsic allocation constructor argument type mismatch in @GPU methods: expected "
                                    + expectedType + " but got " + argumentType
                    ));
                }
            }
            return;
        }

        StructDescriptor struct = resolveStruct(typeName, context.structRegistry());
        if (struct != null) {
            if (creation.getArguments().size() != 0 && creation.getArguments().size() != struct.fields().size()) {
                issues.add(issue(
                        creation,
                        "Struct constructor argument count mismatch in @GPU methods: expected 0 or "
                                + struct.fields().size() + " but got " + creation.getArguments().size()
                ));
                return;
            }
            for (int i = 0; i < creation.getArguments().size(); i++) {
                Expression argument = creation.getArgument(i);
                validateExpression(argument, issues, scopes, context);
                String argumentType = inferExpressionType(argument, scopes, context);
                String fieldType = struct.fields().get(i).javaType();
                if (!isStructAssignmentCompatible(argumentType, fieldType, context.structRegistry())) {
                    issues.add(issue(argument, "Struct constructor argument type mismatch: expected " + fieldType + " but got " + argumentType));
                }
            }
            return;
        }

        if (GpuTypeSupport.isSupportedVectorType(typeName)) {
            validateVectorCreation(creation, issues, scopes, context);
            return;
        }

        issues.add(issue(creation, "Object creation is not allowed in @GPU methods: " + typeName));
    }

    private void validateVectorCreation(
            ObjectCreationExpr creation,
            List<GpuValidationIssue> issues,
            Deque<Map<String, String>> scopes,
            ValidationContext context
    ) {
        String typeName = creation.getTypeAsString();
        String componentType = GpuTypeSupport.vectorComponentType(typeName, "x");
        int width = GpuTypeSupport.vectorWidth(typeName);
        int argumentCount = creation.getArguments().size();
        if (argumentCount != 0 && argumentCount != 1 && argumentCount != width) {
            issues.add(issue(
                    creation,
                    "Vector constructor argument count mismatch in @GPU methods: expected 0, 1 or "
                            + width + " but got " + argumentCount
            ));
            return;
        }

        for (Expression argument : creation.getArguments()) {
            validateExpression(argument, issues, scopes, context);
        }
        if (argumentCount == 0) {
            return;
        }
        if (argumentCount == 1) {
            Expression argument = creation.getArgument(0);
            String argumentType = inferExpressionType(argument, scopes, context);
            if (GpuTypeSupport.isSupportedVectorType(argumentType)) {
                if (!GpuTypeSupport.isHelperArgumentCompatible(argumentType, typeName)) {
                    issues.add(issue(argument, "Vector constructor argument type mismatch: expected " + typeName + " but got " + argumentType));
                }
                return;
            }
            if (!GpuTypeSupport.isHelperArgumentCompatible(argumentType, componentType)) {
                issues.add(issue(argument, "Vector constructor scalar argument type mismatch: expected " + componentType + " but got " + argumentType));
            }
            return;
        }

        for (Expression argument : creation.getArguments()) {
            String argumentType = inferExpressionType(argument, scopes, context);
            if (!GpuTypeSupport.isHelperArgumentCompatible(argumentType, componentType)) {
                issues.add(issue(argument, "Vector constructor argument type mismatch: expected " + componentType + " but got " + argumentType));
            }
        }
    }

    private void validateMethodCall(MethodCallExpr call, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        String owner = call.getScope().map(Node::toString).orElse("");
        call.getArguments().forEach(argument -> validateExpression(argument, issues, scopes, context));

        if (intrinsicDatabase.isAllowedOwner(owner)) {
            try {
                intrinsicDatabase.require(owner, call.getNameAsString(), inferArgumentTypes(call, scopes, context));
            } catch (IllegalArgumentException exception) {
                issues.add(issue(call, exception.getMessage()));
            }
            return;
        }

        HelperResolution resolution = resolveHelperCall(owner, call.getNameAsString(), inferArgumentTypes(call, scopes, context), context);
        if (resolution.descriptor() != null) {
            validateHelperPointerArguments(call, resolution.descriptor(), issues, scopes);
            return;
        }
        issues.add(issue(call, resolution.errorMessage()));
    }

    private void validateHelperPointerArguments(
            MethodCallExpr call,
            HelperDescriptor helperDescriptor,
            List<GpuValidationIssue> issues,
            Deque<Map<String, String>> scopes
    ) {
        for (int i = 0; i < helperDescriptor.argumentTypes().size(); i++) {
            String expectedType = helperDescriptor.argumentTypes().get(i);
            if (!GpuTypeSupport.isSupportedPointerType(expectedType)) {
                continue;
            }
            Expression argument = call.getArgument(i);
            if (!(argument instanceof NameExpr nameExpr)) {
                issues.add(issue(argument, "Pointer helper arguments must be pointer variables in @GPU methods"));
                continue;
            }
            String storageType = lookupStorageType(scopes, nameExpr.getNameAsString());
            if (storageType == null || !GpuTypeSupport.isSupportedPointerType(GpuTypeSupport.declaredType(storageType))) {
                issues.add(issue(argument, "Pointer helper arguments must reference a supported pointer variable: " + argument));
            }
        }
    }

    private void validateVariableType(VariableDeclarator variable, List<GpuValidationIssue> issues) {
        validateVariableType(variable, issues, Map.of());
    }

    private void validateVariableType(
            VariableDeclarator variable,
            List<GpuValidationIssue> issues,
            Map<String, StructDescriptor> structRegistry
    ) {
        if (!GpuTypeSupport.isSupportedLocalType(variable.getTypeAsString())
                && !isStructType(variable.getTypeAsString(), structRegistry)) {
            issues.add(issue(variable, "Unsupported local type in @GPU method: " + variable.getTypeAsString()));
        }
    }

    private void validateFieldAccess(FieldAccessExpr fieldAccessExpr, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        if (fieldAccessExpr.getScope() instanceof NameExpr nameExpr && "value".equals(fieldAccessExpr.getNameAsString())) {
            String storageType = lookupStorageType(scopes, nameExpr.getNameAsString());
            String declaredType = GpuTypeSupport.declaredType(storageType);
            if (storageType == null || (!GpuTypeSupport.isSupportedPointerType(declaredType) && !GpuTypeSupport.isSupportedScalarAliasType(declaredType))) {
                issues.add(issue(fieldAccessExpr, "The .value field is only supported on pointer helpers and unsigned scalar aliases in @GPU methods"));
            }
            return;
        }

        String scopeType = inferExpressionType(fieldAccessExpr.getScope(), scopes, context);
        StructDescriptor struct = resolveStruct(scopeType, context.structRegistry());
        if (struct != null) {
            if (resolveStructField(struct, fieldAccessExpr.getNameAsString()) == null) {
                issues.add(issue(fieldAccessExpr, "Unknown struct field in @GPU methods: " + fieldAccessExpr));
            }
            return;
        }
        if (GpuTypeSupport.isSupportedVectorType(scopeType)) {
            if (GpuTypeSupport.vectorComponentType(scopeType, fieldAccessExpr.getNameAsString()) == null) {
                issues.add(issue(fieldAccessExpr, "Unknown vector component in @GPU methods: " + fieldAccessExpr));
            }
            return;
        }

        if (!(fieldAccessExpr.getScope() instanceof NameExpr nameExpr)) {
            issues.add(issue(fieldAccessExpr, "Unsupported field access in @GPU methods: " + fieldAccessExpr));
            return;
        }
        if (resolveConstant(fieldAccessExpr.getNameAsString(), nameExpr.getNameAsString(), context) == null) {
            issues.add(issue(fieldAccessExpr, "Unsupported field access in @GPU methods: " + fieldAccessExpr));
        }
    }

    private void validateCast(CastExpr castExpr, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        String targetType = castExpr.getTypeAsString();
        if (!GpuTypeSupport.isSupportedScalarType(targetType)) {
            issues.add(issue(castExpr, "Unsupported cast target type in @GPU method: " + targetType));
            return;
        }

        validateExpression(castExpr.getExpression(), issues, scopes, context);

        String sourceType = inferExpressionType(castExpr.getExpression(), scopes, context);
        if (sourceType == null || !GpuTypeSupport.isSupportedScalarType(sourceType)) {
            issues.add(issue(castExpr, "Only primitive scalar casts are supported in @GPU methods"));
        }
    }

    private void validateUpdateExpression(
            UnaryExpr unaryExpr,
            List<GpuValidationIssue> issues,
            Deque<Map<String, String>> scopes,
            ValidationContext context,
            String unsupportedMessage
    ) {
        if (!ALLOWED_UPDATE_OPERATORS.contains(unaryExpr.getOperator())) {
            issues.add(issue(unaryExpr, unsupportedMessage));
            return;
        }

        Expression target = unaryExpr.getExpression();
        if (!(target instanceof NameExpr) && !(target instanceof ArrayAccessExpr) && !(target instanceof FieldAccessExpr)) {
            issues.add(issue(unaryExpr, "Update expressions in @GPU methods must target a variable or direct array access"));
            return;
        }

        validateExpression(target, issues, scopes, context);
    }

    private void validateReturnStatement(ReturnStmt returnStmt, List<GpuValidationIssue> issues, Deque<Map<String, String>> scopes, ValidationContext context) {
        if (context.kernelEntry()) {
            issues.add(issue(returnStmt, "Return statements are not supported in @GPU entry methods"));
            return;
        }

        if ("void".equals(context.returnType())) {
            if (returnStmt.getExpression().isPresent()) {
                issues.add(issue(returnStmt, "Void @CCode helpers cannot return a value"));
            }
            return;
        }

        if (returnStmt.getExpression().isEmpty()) {
            issues.add(issue(returnStmt, "Non-void @CCode helpers must return a value"));
            return;
        }

        Expression expression = returnStmt.getExpression().orElseThrow();
        validateExpression(expression, issues, scopes, context);
        String expressionType = inferExpressionType(expression, scopes, context);
        if (!isReturnTypeCompatible(context.returnType(), expressionType)) {
            issues.add(issue(returnStmt, "Return type mismatch in @CCode helper: expected " + context.returnType() + " but got " + expressionType));
        }
    }

    private String inferExpressionType(Expression expression, Deque<Map<String, String>> scopes, ValidationContext context) {
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
            return "char";
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
            if (isInferableArrayType(arrayType, context.structRegistry())) {
                return GpuTypeSupport.componentType(arrayType);
            }
            return null;
        }

        if (expression instanceof FieldAccessExpr fieldAccessExpr) {
            if (fieldAccessExpr.getScope() instanceof NameExpr nameExpr && "value".equals(fieldAccessExpr.getNameAsString())) {
                String storageType = lookupStorageType(scopes, nameExpr.getNameAsString());
                if (storageType == null) {
                    return null;
                }
                String declaredType = GpuTypeSupport.declaredType(storageType);
                if (GpuTypeSupport.isSupportedPointerType(declaredType)) {
                    return GpuTypeSupport.pointerValueType(storageType);
                }
                if (GpuTypeSupport.isSupportedScalarAliasType(declaredType)) {
                    return GpuTypeSupport.scalarAliasValueType(declaredType);
                }
                return null;
            }
            String scopeType = inferExpressionType(fieldAccessExpr.getScope(), scopes, context);
            StructDescriptor struct = resolveStruct(scopeType, context.structRegistry());
            if (struct != null) {
                StructFieldDescriptor field = resolveStructField(struct, fieldAccessExpr.getNameAsString());
                return field == null ? null : field.javaType();
            }
            String vectorComponentType = GpuTypeSupport.vectorComponentType(scopeType, fieldAccessExpr.getNameAsString());
            if (vectorComponentType != null) {
                return vectorComponentType;
            }
            if (!(fieldAccessExpr.getScope() instanceof NameExpr nameExpr)) {
                return null;
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
            if (GpuTypeSupport.isSupportedVectorType(whenTrueType) || GpuTypeSupport.isSupportedVectorType(whenFalseType)) {
                return GpuTypeSupport.isHelperArgumentCompatible(whenTrueType, whenFalseType) ? whenTrueType : null;
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
            String owner = methodCallExpr.getScope().map(Node::toString).orElse("");
            if (intrinsicDatabase.isAllowedOwner(owner)) {
                try {
                    return intrinsicDatabase.require(owner, methodCallExpr.getNameAsString(), inferArgumentTypes(methodCallExpr, scopes, context)).resultType();
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }

            if (!intrinsicDatabase.isAllowedOwner(owner)) {
                HelperDescriptor helper = resolveHelperCall(
                        owner,
                        methodCallExpr.getNameAsString(),
                        inferArgumentTypes(methodCallExpr, scopes, context),
                        context
                ).descriptor();
                return helper == null ? null : helper.returnType();
            }
        }

        if (expression instanceof ObjectCreationExpr creationExpr
                && intrinsicDatabase.isAllowedAllocationType(creationExpr.getTypeAsString())
                && (GpuTypeSupport.isSupportedPointerType(creationExpr.getTypeAsString())
                || GpuTypeSupport.isSupportedScalarAliasType(creationExpr.getTypeAsString()))) {
            return creationExpr.getTypeAsString();
        }

        if (expression instanceof ObjectCreationExpr creationExpr) {
            if (GpuTypeSupport.isSupportedVectorType(creationExpr.getTypeAsString())) {
                return GpuTypeSupport.declaredType(creationExpr.getTypeAsString());
            }
            StructDescriptor struct = resolveStruct(creationExpr.getTypeAsString(), context.structRegistry());
            if (struct != null) {
                return struct.name();
            }
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

    private boolean isResolvableValueName(String name, Deque<Map<String, String>> scopes, ValidationContext context) {
        return lookupStorageType(scopes, name) != null || resolveConstant(name, "", context) != null;
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

    private List<String> inferArgumentTypes(MethodCallExpr call, Deque<Map<String, String>> scopes, ValidationContext context) {
        return call.getArguments().stream()
                .map(argument -> inferExpressionType(argument, scopes, context))
                .toList();
    }

    private boolean isReturnTypeCompatible(String expectedType, String actualType) {
        if (expectedType == null || actualType == null) {
            return false;
        }
        if (expectedType.equals(actualType)) {
            return true;
        }
        if (GpuTypeSupport.isSupportedVectorType(expectedType) || GpuTypeSupport.isSupportedVectorType(actualType)) {
            return GpuTypeSupport.isHelperArgumentCompatible(actualType, expectedType);
        }
        if ("boolean".equals(expectedType) || "boolean".equals(actualType)) {
            return false;
        }
        return GpuTypeSupport.isSupportedScalarType(expectedType) && GpuTypeSupport.isSupportedScalarType(actualType);
    }

    private HelperResolution resolveHelperCall(
            String owner,
            String methodName,
            List<String> argumentTypes,
            ValidationContext context
    ) {
        List<HelperDescriptor> compatibleCandidates = findCompatibleHelpers(methodName, argumentTypes, context.helperRegistry());
        if (compatibleCandidates.isEmpty()) {
            return new HelperResolution(null, unknownHelperMessage(owner, methodName));
        }

        if (!owner.isBlank()) {
            List<HelperDescriptor> ownerMatches = compatibleCandidates.stream()
                    .filter(candidate -> ownerMatches(owner, candidate))
                    .toList();
            HelperDescriptor resolved = selectBestHelper(ownerMatches, argumentTypes);
            if (resolved != null) {
                return new HelperResolution(resolved, null);
            }
            if (ownerMatches.isEmpty()) {
                return new HelperResolution(null, unknownHelperMessage(owner, methodName));
            }
            return new HelperResolution(null, ambiguousHelperMessage(owner, methodName, argumentTypes, true));
        }

        List<HelperDescriptor> currentOwnerMatches = compatibleCandidates.stream()
                .filter(candidate -> belongsToCurrentOwner(candidate, context))
                .toList();
        HelperDescriptor currentOwnerResolved = selectBestHelper(currentOwnerMatches, argumentTypes);
        if (currentOwnerResolved != null) {
            return new HelperResolution(currentOwnerResolved, null);
        }
        if (!currentOwnerMatches.isEmpty()) {
            return new HelperResolution(null, ambiguousHelperMessage(owner, methodName, argumentTypes, false));
        }
        HelperDescriptor resolved = selectBestHelper(compatibleCandidates, argumentTypes);
        if (resolved != null) {
            return new HelperResolution(resolved, null);
        }

        return new HelperResolution(null, ambiguousHelperMessage(owner, methodName, argumentTypes, false));
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

    private boolean belongsToCurrentOwner(HelperDescriptor descriptor, ValidationContext context) {
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

    private String unknownHelperMessage(String owner, String methodName) {
        return "Unknown @CCode helper call in @GPU method: "
                + (owner.isBlank() ? methodName : owner + "." + methodName);
    }

    private String ambiguousHelperMessage(String owner, String methodName, List<String> argumentTypes, boolean ownerQualified) {
        String callSite = owner.isBlank() ? methodName : owner + "." + methodName;
        return "Ambiguous @CCode helper call in @GPU method: "
                + callSite
                + argumentTypes
                + (ownerQualified
                ? "; use a more specific helper owner"
                : "; qualify it with the helper class name");
    }

    private Map<HelperSignature, List<HelperDescriptor>> buildHelperRegistry(List<ParsedGpuMethod> helperMethods, List<GpuValidationIssue> issues) {
        Map<HelperSignature, List<HelperDescriptor>> helperRegistry = new HashMap<>();
        for (ParsedGpuMethod helperMethod : helperMethods) {
            HelperSignature signature = new HelperSignature(
                    helperMethod.name(),
                    helperMethod.parameters().stream().map(parameter -> parameter.javaType()).toList()
            );
            HelperDescriptor descriptor = new HelperDescriptor(
                    helperMethod.ownerSimpleName(),
                    helperMethod.ownerQualifiedName(),
                    helperMethod.name(),
                    OpenClKernelNaming.toHelperFunctionName(helperMethod.ownerSimpleName(), helperMethod.name(), signature.argumentTypes()),
                    helperMethod.returnType(),
                    signature.argumentTypes(),
                    helperMethod.inline()
            );
            List<HelperDescriptor> candidates = helperRegistry.computeIfAbsent(signature, ignored -> new ArrayList<>());
            boolean duplicateOwner = candidates.stream().anyMatch(existing -> sameOwner(existing, descriptor));
            if (duplicateOwner) {
                issues.add(issue(helperMethod.declaration(), "Duplicate @CCode helper signature in owner: " + helperMethod.name() + signature.argumentTypes()));
                continue;
            }
            candidates.add(descriptor);
        }
        return helperRegistry;
    }

    private Map<String, List<ConstantDescriptor>> buildConstantRegistry(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs,
            List<GpuValidationIssue> issues
    ) {
        Map<String, List<ConstantDescriptor>> constantRegistry = new HashMap<>();
        List<ParsedGpuMethod> methods = new ArrayList<>();
        methods.add(kernelMethod);
        methods.addAll(helperMethods);
        for (ParsedGpuMethod method : methods) {
            for (ParsedGpuConstant constant : method.constants()) {
                List<ConstantDescriptor> constants = constantRegistry.computeIfAbsent(constant.name(), ignored -> new ArrayList<>());
                ConstantDescriptor existingConstant = constants.stream()
                        .filter(existing -> sameOwner(existing.ownerSimpleName(), existing.ownerQualifiedName(), constant.ownerSimpleName(), constant.ownerQualifiedName()))
                        .findFirst()
                        .orElse(null);
                if (existingConstant != null) {
                    if (!existingConstant.javaType().equals(constant.javaType())
                            || !existingConstant.sourceText().equals(constant.sourceText())) {
                        issues.add(issue(method.declaration(), "Duplicate GPU constant in owner: " + constant.name()));
                    }
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
        for (ParsedGpuStruct struct : structs) {
            for (ParsedGpuConstant constant : struct.constants()) {
                List<ConstantDescriptor> constants = constantRegistry.computeIfAbsent(constant.name(), ignored -> new ArrayList<>());
                ConstantDescriptor existingConstant = constants.stream()
                        .filter(existing -> sameOwner(existing.ownerSimpleName(), existing.ownerQualifiedName(), constant.ownerSimpleName(), constant.ownerQualifiedName()))
                        .findFirst()
                        .orElse(null);
                if (existingConstant != null) {
                    if (!existingConstant.javaType().equals(constant.javaType())
                            || !existingConstant.sourceText().equals(constant.sourceText())) {
                        issues.add(new GpuValidationIssue(1, 1, "Duplicate GPU struct constant in owner: " + constant.name()));
                    }
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
        for (GpuBuiltinConstant constant : intrinsicDatabase.builtinConstants()) {
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
        return constantRegistry;
    }

    private Map<String, StructDescriptor> buildStructRegistry(List<ParsedGpuStruct> structs, List<GpuValidationIssue> issues) {
        Map<String, StructDescriptor> registry = new HashMap<>();
        for (ParsedGpuStruct struct : structs) {
            List<StructFieldDescriptor> fields = struct.fields().stream()
                    .map(field -> new StructFieldDescriptor(field.name(), field.javaType()))
                    .toList();
            StructDescriptor descriptor = new StructDescriptor(
                    struct.ownerSimpleName(),
                    struct.ownerQualifiedName(),
                    GpuTypeSupport.simpleTypeName(struct.ownerSimpleName()),
                    fields
            );
            registerStructAlias(registry, descriptor.ownerSimpleName(), descriptor, issues);
            registerStructAlias(registry, descriptor.ownerQualifiedName(), descriptor, issues);
            registerStructAlias(registry, descriptor.name(), descriptor, issues);
        }
        return registry;
    }

    private void registerStructAlias(
            Map<String, StructDescriptor> registry,
            String alias,
            StructDescriptor descriptor,
            List<GpuValidationIssue> issues
    ) {
        if (alias == null || alias.isBlank()) {
            return;
        }
        StructDescriptor existing = registry.putIfAbsent(alias, descriptor);
        if (existing != null && !sameOwner(existing.ownerSimpleName(), existing.ownerQualifiedName(), descriptor.ownerSimpleName(), descriptor.ownerQualifiedName())) {
            issues.add(new GpuValidationIssue(1, 1, "Ambiguous GPU struct type alias: " + alias));
        }
    }

    private StructDescriptor resolveStruct(String typeName, Map<String, StructDescriptor> structRegistry) {
        if (typeName == null) {
            return null;
        }
        StructDescriptor direct = structRegistry.get(typeName);
        if (direct != null) {
            return direct;
        }
        return structRegistry.get(GpuTypeSupport.simpleTypeName(typeName));
    }

    private StructFieldDescriptor resolveStructField(StructDescriptor struct, String fieldName) {
        return struct.fields().stream()
                .filter(field -> field.name().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    private boolean isStructType(String typeName, Map<String, StructDescriptor> structRegistry) {
        return resolveStruct(typeName, structRegistry) != null;
    }

    private boolean isStructArrayType(String typeName, Map<String, StructDescriptor> structRegistry) {
        return GpuTypeSupport.isArrayType(typeName)
                && isStructType(GpuTypeSupport.componentType(GpuTypeSupport.declaredType(typeName)), structRegistry);
    }

    private boolean isVectorArrayType(String typeName) {
        return GpuTypeSupport.isArrayType(typeName)
                && GpuTypeSupport.isSupportedVectorType(GpuTypeSupport.componentType(GpuTypeSupport.declaredType(typeName)));
    }

    private boolean isPackedArrayType(String typeName, Map<String, StructDescriptor> structRegistry) {
        return isStructArrayType(typeName, structRegistry) || isVectorArrayType(typeName);
    }

    private boolean isSupportedArrayParameterType(String typeName, Map<String, StructDescriptor> structRegistry) {
        return GpuTypeSupport.isSupportedArrayType(typeName) || isPackedArrayType(typeName, structRegistry);
    }

    private boolean isInferableArrayType(String typeName, Map<String, StructDescriptor> structRegistry) {
        if (!GpuTypeSupport.isArrayType(typeName)) {
            return false;
        }
        String componentType = GpuTypeSupport.componentType(GpuTypeSupport.declaredType(typeName));
        return GpuTypeSupport.isSupportedScalarType(componentType)
                || GpuTypeSupport.isSupportedVectorType(componentType)
                || isStructType(componentType, structRegistry);
    }

    private boolean isStructAssignmentCompatible(String actualType, String targetType, Map<String, StructDescriptor> structRegistry) {
        if (actualType == null || targetType == null) {
            return false;
        }
        if (actualType.equals(targetType)) {
            return true;
        }
        if (isStructType(actualType, structRegistry) || isStructType(targetType, structRegistry)) {
            StructDescriptor actualStruct = resolveStruct(actualType, structRegistry);
            StructDescriptor targetStruct = resolveStruct(targetType, structRegistry);
            return actualStruct != null && targetStruct != null && actualStruct.name().equals(targetStruct.name());
        }
        return GpuTypeSupport.isHelperArgumentCompatible(actualType, targetType);
    }

    private ConstantDescriptor resolveConstant(String constantName, String owner, ValidationContext context) {
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
        if (currentOwnerMatches.size() == 1) {
            return currentOwnerMatches.get(0);
        }
        return null;
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

    private boolean sameOwner(HelperDescriptor left, HelperDescriptor right) {
        if (left.ownerQualifiedName() != null && !left.ownerQualifiedName().isBlank()
                && right.ownerQualifiedName() != null && !right.ownerQualifiedName().isBlank()) {
            return left.ownerQualifiedName().equals(right.ownerQualifiedName());
        }
        return left.ownerSimpleName().equals(right.ownerSimpleName());
    }

    private record ValidationContext(
            boolean kernelEntry,
            String returnType,
            String ownerSimpleName,
            String ownerQualifiedName,
            Map<HelperSignature, List<HelperDescriptor>> helperRegistry,
            Map<String, List<ConstantDescriptor>> constantRegistry,
            Map<String, StructDescriptor> structRegistry
    ) {
    }

    private record HelperResolution(
            HelperDescriptor descriptor,
            String errorMessage
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

    private record AttributeSpec(
            String raw,
            String name,
            List<String> arguments
    ) {
    }

    private record StructDescriptor(
            String ownerSimpleName,
            String ownerQualifiedName,
            String name,
            List<StructFieldDescriptor> fields
    ) {
    }

    private record StructFieldDescriptor(
            String name,
            String javaType
    ) {
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
