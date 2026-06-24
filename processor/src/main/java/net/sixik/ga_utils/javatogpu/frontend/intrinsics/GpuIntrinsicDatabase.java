package net.sixik.ga_utils.javatogpu.frontend.intrinsics;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import net.sixik.ga_utils.javatogpu.backend.GpuBackendSupport;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUIntrinsic;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GpuIntrinsicDatabase {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)}");
    private static final Pattern THIS_PLACEHOLDER_PATTERN = Pattern.compile("\\{this}");
    private static final String API_PACKAGE_PREFIX = "net.sixik.ga_utils.javatogpu.api.";

    private final Map<String, List<GpuIntrinsic>> intrinsics;
    private final List<GpuBuiltinConstant> builtinConstants;
    private final GpuBackendTarget backendTarget;

    private GpuIntrinsicDatabase(
            Map<String, List<GpuIntrinsic>> intrinsics,
            List<GpuBuiltinConstant> builtinConstants,
            GpuBackendTarget backendTarget
    ) {
        this.intrinsics = intrinsics;
        this.builtinConstants = builtinConstants;
        this.backendTarget = backendTarget;
    }

    public static GpuIntrinsicDatabase createDefault() {
        return createDefault(GpuBackendTarget.OPENCL);
    }

    public static GpuIntrinsicDatabase createDefault(GpuBackendTarget backendTarget) {
        return createDefault(List.of(), backendTarget);
    }

    public static GpuIntrinsicDatabase createDefault(List<ParsedGpuMethod> additionalIntrinsicMethods) {
        return createDefault(additionalIntrinsicMethods, GpuBackendTarget.OPENCL);
    }

    public static GpuIntrinsicDatabase createDefault(List<ParsedGpuMethod> additionalIntrinsicMethods, GpuBackendTarget backendTarget) {
        Map<String, List<GpuIntrinsic>> values = new HashMap<>();
        registerIntrinsicOwner(values, GPU.class, backendTarget);
        List<GpuBuiltinConstant> builtinConstants = new ArrayList<>(readBuiltinConstants(GPU.class));
        registerParsedIntrinsicMethods(values, builtinConstants, additionalIntrinsicMethods, backendTarget);

        Map<String, List<GpuIntrinsic>> mutableIntrinsics = new HashMap<>();
        for (Map.Entry<String, List<GpuIntrinsic>> entry : values.entrySet()) {
            mutableIntrinsics.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        return new GpuIntrinsicDatabase(
                mutableIntrinsics,
                List.copyOf(builtinConstants),
                backendTarget
        );
    }

    public GpuIntrinsic require(String owner, String javaName, int arity) {
        ensureIntrinsicOwnerLoaded(owner);
        List<GpuIntrinsic> matches = intrinsics.get(key(owner, javaName, arity));
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown GPU intrinsic: " + owner + "." + javaName + "/" + arity);
        }
        matches = matches.stream().filter(intrinsic -> !intrinsic.instanceMethod()).toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown GPU intrinsic: " + owner + "." + javaName + "/" + arity);
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Ambiguous GPU intrinsic overload: " + owner + "." + javaName + "/" + arity);
        }
        return matches.get(0);
    }

    public GpuIntrinsic require(String owner, String javaName, List<String> argumentTypes) {
        ensureIntrinsicOwnerLoaded(owner);
        List<GpuIntrinsic> matches = intrinsics.get(key(owner, javaName, argumentTypes.size()));
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown GPU intrinsic: " + owner + "." + javaName + "/" + argumentTypes.size());
        }

        return matches.stream()
                .filter(intrinsic -> !intrinsic.instanceMethod())
                .filter(intrinsic -> intrinsic.argumentTypes().equals(argumentTypes))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown GPU intrinsic overload: " + owner + "." + javaName + argumentTypes
                ));
    }

    public boolean isAllowedOwner(String owner) {
        ensureIntrinsicOwnerLoaded(owner);
        return intrinsics.values().stream().flatMap(List::stream).anyMatch(intrinsic -> !intrinsic.instanceMethod()
                && (owner.equals(intrinsic.ownerSimpleName()) || owner.equals(intrinsic.ownerQualifiedName())));
    }

    public boolean hasAnyOwner(String owner) {
        ensureIntrinsicOwnerLoaded(owner);
        return intrinsics.values().stream().flatMap(List::stream).anyMatch(intrinsic ->
                owner.equals(intrinsic.ownerSimpleName()) || owner.equals(intrinsic.ownerQualifiedName()));
    }

    public GpuIntrinsic requireInstance(String ownerType, String javaName, List<String> argumentTypes) {
        ensureIntrinsicOwnerLoaded(ownerType);
        List<GpuIntrinsic> matches = intrinsics.get(key(ownerType, javaName, argumentTypes.size()));
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown GPU intrinsic: " + ownerType + "." + javaName + "/" + argumentTypes.size());
        }
        return matches.stream()
                .filter(GpuIntrinsic::instanceMethod)
                .filter(intrinsic -> intrinsic.argumentTypes().equals(argumentTypes))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown GPU intrinsic overload: " + ownerType + "." + javaName + argumentTypes
                ));
    }

    public boolean isAllowedAllocationType(String typeName) {
        return GpuTypeSupport.isSupportedPointerType(typeName)
                || GpuTypeSupport.isSupportedScalarAliasType(typeName);
    }

    public List<GpuBuiltinConstant> builtinConstants() {
        return builtinConstants;
    }

    private static void register(Map<String, List<GpuIntrinsic>> values, GpuIntrinsic intrinsic) {
        registerAlias(values, intrinsic.ownerSimpleName(), intrinsic);
        if (intrinsic.ownerQualifiedName() != null
                && !intrinsic.ownerQualifiedName().isBlank()
                && !intrinsic.ownerQualifiedName().equals(intrinsic.ownerSimpleName())) {
            registerAlias(values, intrinsic.ownerQualifiedName(), intrinsic);
        }
    }

    private static void registerAlias(Map<String, List<GpuIntrinsic>> values, String ownerAlias, GpuIntrinsic intrinsic) {
        if (ownerAlias == null || ownerAlias.isBlank()) {
            return;
        }
        List<GpuIntrinsic> existing = values.computeIfAbsent(key(ownerAlias, intrinsic.javaName(), intrinsic.arity()), ignored -> new ArrayList<>());
        boolean duplicate = existing.stream().anyMatch(candidate ->
                sameOwner(candidate, intrinsic) && candidate.instanceMethod() == intrinsic.instanceMethod() && candidate.argumentTypes().equals(intrinsic.argumentTypes()));
        if (!duplicate) {
            existing.add(intrinsic);
        }
    }

    private static void registerIntrinsicOwner(Map<String, List<GpuIntrinsic>> values, Class<?> ownerType, GpuBackendTarget backendTarget) {
        for (Method method : ownerType.getDeclaredMethods()) {
            GPUIntrinsic annotation = method.getAnnotation(GPUIntrinsic.class);
            if (annotation == null) {
                continue;
            }
            if (!GpuBackendSupport.supportsBackend(annotation.backends(), backendTarget)) {
                continue;
            }
            register(values, toIntrinsic(ownerType, method, annotation));
        }
    }

    private static GpuIntrinsic toIntrinsic(Class<?> ownerType, Method method, GPUIntrinsic annotation) {
        List<String> argumentTypes = javaTypeNames(method.getParameterTypes());
        String backendName = annotation.name().isBlank() ? method.getName() : annotation.name();
        boolean instanceMethod = !Modifier.isStatic(method.getModifiers());
        String codeTemplate = deriveCodeTemplate(annotation, method, instanceMethod);
        return new GpuIntrinsic(
                ownerType.getSimpleName(),
                ownerType.getName(),
                method.getName(),
                method.getParameterCount(),
                instanceMethod,
                inferKind(backendName),
                backendName,
                validateCodeTemplate(codeTemplate, method, instanceMethod),
                javaTypeName(method.getReturnType()),
                argumentTypes
        );
    }

    private static String deriveCodeTemplate(GPUIntrinsic annotation, Method method, boolean instanceMethod) {
        if (!annotation.code().isBlank()) {
            if (!annotation.operator().isBlank()) {
                throw new IllegalStateException("@GPUIntrinsic cannot declare both code and operator: "
                        + method.getDeclaringClass().getName() + "." + method.getName());
            }
            return annotation.code();
        }
        if (annotation.operator().isBlank()) {
            return "";
        }
        String operator = annotation.operator().trim();
        if (instanceMethod) {
            if (method.getParameterCount() == 0) {
                return "(" + operator + "{this})";
            }
            if (method.getParameterCount() == 1) {
                return "({this} " + operator + " {0})";
            }
            throw new IllegalStateException("Instance @GPUIntrinsic operator methods must take zero or one explicit argument: "
                    + method.getDeclaringClass().getName() + "." + method.getName());
        }
        if (method.getParameterCount() == 1) {
            return "(" + operator + "{0})";
        }
        if (method.getParameterCount() == 2) {
            return "({0} " + operator + " {1})";
        }
        throw new IllegalStateException("Static @GPUIntrinsic operator methods must take one or two arguments: "
                + method.getDeclaringClass().getName() + "." + method.getName());
    }

    private static GpuIntrinsicKind inferKind(String backendName) {
        if (backendName.startsWith("get_")) {
            return GpuIntrinsicKind.BUILTIN_ID;
        }
        if ("barrier".equals(backendName)) {
            return GpuIntrinsicKind.SYNCHRONIZATION;
        }
        if (Set.of("clamp", "mix", "step", "smoothstep", "hypot").contains(backendName)) {
            return GpuIntrinsicKind.COMMON;
        }
        return GpuIntrinsicKind.MATH;
    }

    private static List<GpuBuiltinConstant> readBuiltinConstants(Class<?> ownerType) {
        List<GpuBuiltinConstant> constants = new ArrayList<>();
        for (Field field : ownerType.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }
            String javaType = javaTypeName(field.getType());
            if (!isSupportedBuiltinConstantType(javaType)) {
                continue;
            }
            Object value;
            try {
                value = field.get(null);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to read GPU builtin constant: "
                        + ownerType.getName() + "." + field.getName(), exception);
            }
            constants.add(new GpuBuiltinConstant(
                    ownerType.getSimpleName(),
                    ownerType.getName(),
                    field.getName(),
                    javaType,
                    builtinConstantSource(javaType, value)
            ));
        }
        return constants;
    }

    private static boolean isSupportedBuiltinConstantType(String javaType) {
        return switch (javaType) {
            case "byte", "short", "int", "long", "float", "double", "boolean", "char" -> true;
            default -> false;
        };
    }

    private static String builtinConstantSource(String javaType, Object value) {
        return switch (javaType) {
            case "boolean" -> String.valueOf(value);
            case "float" -> value + "f";
            case "double" -> String.valueOf(value);
            case "long" -> value + "L";
            case "char" -> Integer.toString((Character) value);
            default -> String.valueOf(value);
        };
    }

    private static String validateCodeTemplate(String codeTemplate, Method method, boolean instanceMethod) {
        if (codeTemplate.isBlank()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(codeTemplate);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index < 0 || index >= method.getParameterCount()) {
                throw new IllegalStateException("@GPUIntrinsic code placeholder out of range for "
                        + method.getDeclaringClass().getName() + "." + method.getName()
                        + ": {" + index + "}");
            }
        }
        return codeTemplate;
    }

    private static void registerParsedIntrinsicMethods(
            Map<String, List<GpuIntrinsic>> values,
            List<GpuBuiltinConstant> builtinConstants,
            List<ParsedGpuMethod> intrinsicMethods,
            GpuBackendTarget backendTarget
    ) {
        Map<String, GpuBuiltinConstant> uniqueConstants = new HashMap<>();
        for (GpuBuiltinConstant constant : builtinConstants) {
            uniqueConstants.put(builtinConstantKey(constant), constant);
        }

        for (ParsedGpuMethod method : intrinsicMethods) {
            GPUIntrinsicAnnotation annotation = parseIntrinsicAnnotation(method);
            if (annotation == null) {
                continue;
            }
            if (!annotation.backends().contains(backendTarget)) {
                continue;
            }
            List<String> argumentTypes = method.parameters().stream().map(parameter -> parameter.javaType()).toList();
            boolean instanceMethod = isParsedInstanceIntrinsic(method, annotation);
            String codeTemplate = deriveCodeTemplate(annotation, method, instanceMethod);
            register(values, new GpuIntrinsic(
                    method.ownerSimpleName(),
                    method.ownerQualifiedName(),
                    method.name(),
                    method.parameters().size(),
                    instanceMethod,
                    inferKind(annotation.backendName()),
                    annotation.backendName(),
                    validateCodeTemplate(codeTemplate, method.ownerQualifiedName(), method.name(), method.parameters().size(), instanceMethod),
                    method.returnType(),
                    argumentTypes
            ));

            for (ParsedGpuConstant constant : method.constants()) {
                GpuBuiltinConstant builtinConstant = new GpuBuiltinConstant(
                        constant.ownerSimpleName(),
                        constant.ownerQualifiedName(),
                        constant.name(),
                        constant.javaType(),
                        constant.sourceText()
                );
                uniqueConstants.putIfAbsent(builtinConstantKey(builtinConstant), builtinConstant);
            }
        }

        builtinConstants.clear();
        builtinConstants.addAll(uniqueConstants.values());
    }

    private static GPUIntrinsicAnnotation parseIntrinsicAnnotation(ParsedGpuMethod method) {
        return method.declaration().getAnnotationByName("GPUIntrinsic")
                .map(annotation -> {
                    if (!annotation.isNormalAnnotationExpr()) {
                        return new GPUIntrinsicAnnotation(method.name(), "", "", EnumSet.of(GpuBackendTarget.OPENCL));
                    }
                    String backendName = annotation.asNormalAnnotationExpr().getPairs().stream()
                            .filter(pair -> pair.getNameAsString().equals("name"))
                            .findFirst()
                            .map(pair -> literalStringValue(pair.getValue() instanceof LiteralStringValueExpr literal ? literal : null))
                            .filter(value -> !value.isBlank())
                            .orElse(method.name());
                    String codeTemplate = annotation.asNormalAnnotationExpr().getPairs().stream()
                            .filter(pair -> pair.getNameAsString().equals("code"))
                            .findFirst()
                            .map(pair -> literalStringValue(pair.getValue() instanceof LiteralStringValueExpr literal ? literal : null))
                            .orElse("");
                    String operator = annotation.asNormalAnnotationExpr().getPairs().stream()
                            .filter(pair -> pair.getNameAsString().equals("operator"))
                            .findFirst()
                            .map(pair -> literalStringValue(pair.getValue() instanceof LiteralStringValueExpr literal ? literal : null))
                            .orElse("");
                    EnumSet<GpuBackendTarget> backends = annotation.asNormalAnnotationExpr().getPairs().stream()
                            .filter(pair -> pair.getNameAsString().equals("backends"))
                            .findFirst()
                            .map(pair -> GpuBackendSupport.parseBackendTargets(pair.getValue()))
                            .orElse(EnumSet.of(GpuBackendTarget.OPENCL));
                    if (!codeTemplate.isBlank() && !operator.isBlank()) {
                        throw new IllegalStateException("@GPUIntrinsic cannot declare both code and operator: " + method.ownerQualifiedName() + "." + method.name());
                    }
                    return new GPUIntrinsicAnnotation(backendName, codeTemplate, operator, backends);
                })
                .orElse(null);
    }

    private static String literalStringValue(LiteralStringValueExpr literal) {
        if (literal == null) {
            return "";
        }
        if (literal instanceof TextBlockLiteralExpr textBlockLiteral) {
            return textBlockLiteral.asString();
        }
        if (literal instanceof StringLiteralExpr stringLiteral) {
            return stringLiteral.asString();
        }
        return literal.getValue();
    }

    private static String validateCodeTemplate(String codeTemplate, String ownerQualifiedName, String methodName, int parameterCount, boolean instanceMethod) {
        if (codeTemplate.isBlank()) {
            return "";
        }
        if (instanceMethod && !THIS_PLACEHOLDER_PATTERN.matcher(codeTemplate).find() && parameterCount == 0) {
            return codeTemplate;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(codeTemplate);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index < 0 || index >= parameterCount) {
                throw new IllegalStateException("@GPUIntrinsic code placeholder out of range for "
                        + ownerQualifiedName + "." + methodName + ": {" + index + "}");
            }
        }
        return codeTemplate;
    }

    private static String deriveCodeTemplate(GPUIntrinsicAnnotation annotation, ParsedGpuMethod method, boolean instanceMethod) {
        if (!annotation.codeTemplate().isBlank()) {
            return annotation.codeTemplate();
        }
        if (annotation.operator().isBlank()) {
            return "";
        }
        String operator = annotation.operator().trim();
        if (instanceMethod) {
            if (method.parameters().isEmpty()) {
                return "(" + operator + "{this})";
            }
            if (method.parameters().size() == 1) {
                return "({this} " + operator + " {0})";
            }
            throw new IllegalStateException("Instance @GPUIntrinsic operator methods must take zero or one explicit argument: "
                    + method.ownerQualifiedName() + "." + method.name());
        }
        if (method.parameters().size() == 1) {
            return "(" + operator + "{0})";
        }
        if (method.parameters().size() == 2) {
            return "({0} " + operator + " {1})";
        }
        throw new IllegalStateException("Static @GPUIntrinsic operator methods must take one or two arguments: "
                + method.ownerQualifiedName() + "." + method.name());
    }

    private static boolean isParsedInstanceIntrinsic(ParsedGpuMethod method, GPUIntrinsicAnnotation annotation) {
        if (method.declaration().isStatic()) {
            return false;
        }
        return !annotation.operator().isBlank() || annotation.codeTemplate().contains("{this}");
    }

    private void ensureIntrinsicOwnerLoaded(String owner) {
        if (owner == null || owner.isBlank()) {
            return;
        }
        if (intrinsics.keySet().stream().anyMatch(key -> key.startsWith(owner + "#"))) {
            return;
        }
        for (String candidate : candidateIntrinsicOwnerClassNames(owner)) {
            try {
                Class<?> type = Class.forName(candidate, false, GpuIntrinsicDatabase.class.getClassLoader());
                registerIntrinsicOwner(intrinsics, type, backendTarget);
                if (intrinsics.keySet().stream().anyMatch(key -> key.startsWith(owner + "#"))) {
                    return;
                }
            } catch (ClassNotFoundException ignored) {
                // Best effort lookup for optional vector operator owners.
            }
        }
    }

    private static List<String> candidateIntrinsicOwnerClassNames(String owner) {
        List<String> candidates = new ArrayList<>();
        candidates.add(owner);
        if (!owner.contains(".")) {
            candidates.add(API_PACKAGE_PREFIX + owner);
        }
        return candidates;
    }

    private static List<String> javaTypeNames(Class<?>[] javaTypes) {
        List<String> names = new ArrayList<>(javaTypes.length);
        for (Class<?> javaType : javaTypes) {
            names.add(javaTypeName(javaType));
        }
        return List.copyOf(names);
    }

    private static String javaTypeName(Class<?> javaType) {
        if (javaType.isArray()) {
            return javaTypeName(javaType.getComponentType()) + "[]";
        }
        if (!javaType.isPrimitive()) {
            return javaType.getSimpleName();
        }
        if (javaType == Byte.TYPE) {
            return "byte";
        }
        if (javaType == Short.TYPE) {
            return "short";
        }
        if (javaType == Integer.TYPE) {
            return "int";
        }
        if (javaType == Long.TYPE) {
            return "long";
        }
        if (javaType == Float.TYPE) {
            return "float";
        }
        if (javaType == Double.TYPE) {
            return "double";
        }
        if (javaType == Boolean.TYPE) {
            return "boolean";
        }
        if (javaType == Character.TYPE) {
            return "char";
        }
        if (javaType == Void.TYPE) {
            return "void";
        }
        throw new IllegalArgumentException("Unsupported intrinsic Java type: " + javaType.getName());
    }

    private static String key(String owner, String javaName, int arity) {
        return owner + "#" + javaName + "#" + arity;
    }

    private static boolean sameOwner(GpuIntrinsic left, GpuIntrinsic right) {
        if (left.ownerQualifiedName() != null && !left.ownerQualifiedName().isBlank()
                && right.ownerQualifiedName() != null && !right.ownerQualifiedName().isBlank()) {
            return left.ownerQualifiedName().equals(right.ownerQualifiedName());
        }
        return left.ownerSimpleName().equals(right.ownerSimpleName());
    }

    private static String builtinConstantKey(GpuBuiltinConstant constant) {
        return constant.ownerQualifiedName() + "#" + constant.name() + "#" + constant.javaType() + "#" + constant.sourceText();
    }

    private record GPUIntrinsicAnnotation(
            String backendName,
            String codeTemplate,
            String operator,
            EnumSet<GpuBackendTarget> backends
    ) {
    }
}
