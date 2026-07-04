package net.sixik.ga_utils.javatogpu.frontend;

import com.github.javaparser.ast.type.Type;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelEmitter;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassRunner;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationMode;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationRunner;
import net.sixik.ga_utils.javatogpu.frontend.lowering.GpuIrLowerer;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser;
import net.sixik.ga_utils.javatogpu.frontend.validation.GpuSubsetValidator;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GpuFrontendService {

    private final GpuMethodParser parser;
    private final GpuSubsetValidator validator;
    private final GpuIrLowerer lowerer;
    private final OpenClKernelEmitter emitter;
    private final GpuIrPassRunner passRunner;
    private final GpuIrValidationRunner validationRunner;

    public GpuFrontendService(
            GpuMethodParser parser,
            GpuSubsetValidator validator,
            GpuIrLowerer lowerer,
            OpenClKernelEmitter emitter,
            GpuIrPassRunner passRunner
    ) {
        this(
                parser,
                validator,
                lowerer,
                emitter,
                passRunner,
                GpuIrValidationRunner.disabled()
        );
    }

    public GpuFrontendService(
            GpuMethodParser parser,
            GpuSubsetValidator validator,
            GpuIrLowerer lowerer,
            OpenClKernelEmitter emitter,
            GpuIrPassRunner passRunner,
            GpuIrValidationRunner validationRunner
    ) {
        this.parser = parser;
        this.validator = validator;
        this.lowerer = lowerer;
        this.emitter = emitter;
        this.passRunner = passRunner;
        this.validationRunner = validationRunner;
    }

    public GpuFrontendService(
            GpuMethodParser parser,
            GpuSubsetValidator validator,
            GpuIrLowerer lowerer,
            OpenClKernelEmitter emitter
    ) {
        this(parser, validator, lowerer, emitter, GpuIrPassRunner.loadFromServiceLoader());
    }

    public static GpuFrontendService createDefault() {
        return create(GpuIntrinsicDatabase.createDefault());
    }

    public static GpuFrontendService create(GpuIntrinsicDatabase intrinsicDatabase) {
        return create(intrinsicDatabase, GpuIrValidationMode.OFF);
    }

    public static GpuFrontendService create(GpuIntrinsicDatabase intrinsicDatabase, GpuIrValidationMode validationMode) {
        return new GpuFrontendService(
                new GpuMethodParser(),
                new GpuSubsetValidator(intrinsicDatabase),
                new GpuIrLowerer(intrinsicDatabase),
                new OpenClKernelEmitter(),
                GpuIrPassRunner.loadFromServiceLoader(),
                GpuIrValidationRunner.loadFromServiceLoader(validationMode)
        );
    }

    public ParsedGpuMethod parseAndValidate(String methodSource) {
        ParsedGpuMethod method = parser.parseMethod(methodSource);
        validator.validateKernel(method, List.of(), List.of());
        return method;
    }

    public GpuIrMethod parseValidateAndLower(String methodSource) {
        ParsedGpuMethod method = parseAndValidate(methodSource);
        return lowerer.lower(method);
    }

    public String parseValidateLowerAndEmit(String methodSource) {
        ParsedGpuMethod method = parseAndValidate(methodSource);
        GpuIrMethod irMethod = lowerer.lower(method);
        GpuIrCompiledMethod compiledMethod = new GpuIrCompiledMethod(method, irMethod, method.name(), List.of());
        passRunner.run(compiledMethod, List.of(), List.of());
        validationRunner.run(compiledMethod, List.of(), List.of());
        return emitter.emit(method, irMethod);
    }

    public String parseValidateLowerAndEmit(String methodSource, List<String> helperMethodSources) {
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource);
        List<ParsedGpuMethod> helperMethods = helperMethodSources.stream()
                .map(parser::parseMethod)
                .toList();

        return validateLowerAndEmit(kernelMethod, helperMethods, List.of());
    }

    public String validateLowerAndEmit(ParsedGpuMethod kernelMethod, List<ParsedGpuMethod> helperMethods) {
        return validateLowerAndEmit(kernelMethod, helperMethods, List.of());
    }

    public String validateLowerAndEmit(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        List<ParsedGpuStruct> relevantStructs = selectRelevantStructs(kernelMethod, helperMethods, structs);

        validator.validateKernel(kernelMethod, helperMethods, relevantStructs);

        List<GpuIrCompiledMethod> compiledMethods = lowerer.lower(kernelMethod, helperMethods, relevantStructs);
        List<GpuIrCompiledMethod> compiledHelpers = compiledMethods.subList(0, helperMethods.size());
        GpuIrCompiledMethod compiledKernel = compiledMethods.get(compiledMethods.size() - 1);
        passRunner.run(compiledKernel, compiledHelpers, relevantStructs);
        validationRunner.run(compiledKernel, compiledHelpers, relevantStructs);
        return emitter.emitProgram(
                compiledKernel,
                GpuProgramAssemblySupport.selectReachableHelpers(
                        compiledKernel,
                        compiledHelpers,
                        "Lowered kernel references unknown helper: ",
                        "Recursive @CCode helper calls are not supported: "
                ),
                relevantStructs
        );
    }

    private List<ParsedGpuStruct> selectRelevantStructs(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        if (structs.isEmpty()) {
            return List.of();
        }

        GpuStructAliasRegistry<ParsedGpuStruct> registry = buildStructAliasRegistry(structs);
        LinkedHashSet<String> reachableStructNames = new LinkedHashSet<>();

        registerMethodStructReferences(kernelMethod, registry, reachableStructNames);
        helperMethods.forEach(helperMethod -> registerMethodStructReferences(helperMethod, registry, reachableStructNames));

        Deque<String> pending = new ArrayDeque<>(reachableStructNames);
        while (!pending.isEmpty()) {
            String structName = pending.removeFirst();
            ParsedGpuStruct struct = registry.resolve(structName);
            if (struct == null) {
                continue;
            }

            for (String nestedStructName : referencedStructNames(struct.fields().stream().map(field -> field.javaType()).toList(), registry)) {
                if (reachableStructNames.add(nestedStructName)) {
                    pending.addLast(nestedStructName);
                }
            }
        }

        return structs.stream()
                .filter(struct -> reachableStructNames.contains(struct.ownerQualifiedName()))
                .toList();
    }

    private void registerMethodStructReferences(
            ParsedGpuMethod method,
            GpuStructAliasRegistry<ParsedGpuStruct> registry,
            Set<String> reachableStructNames
    ) {
        List<String> referencedTypes = new ArrayList<>();
        referencedTypes.add(method.returnType());
        method.parameters().forEach(parameter -> referencedTypes.add(parameter.javaType()));
        if (method.declaration() != null) {
            method.declaration().findAll(Type.class).forEach(type -> referencedTypes.add(type.asString()));
        }
        reachableStructNames.addAll(referencedStructNames(referencedTypes, registry));
    }

    private Set<String> referencedStructNames(List<String> referencedTypes, GpuStructAliasRegistry<ParsedGpuStruct> registry) {
        LinkedHashSet<String> structNames = new LinkedHashSet<>();
        for (String referencedType : referencedTypes) {
            String normalizedType = normalizeReferencedType(referencedType);
            if (normalizedType == null) {
                continue;
            }
            ParsedGpuStruct struct = registry.resolve(normalizedType);
            if (struct != null) {
                structNames.add(struct.ownerQualifiedName());
            }
        }
        return structNames;
    }

    private String normalizeReferencedType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        String normalized = GpuTypeSupport.declaredType(typeName.strip());
        while (GpuTypeSupport.isArrayType(normalized)) {
            normalized = GpuTypeSupport.componentType(normalized);
        }
        return normalized;
    }

    private GpuStructAliasRegistry<ParsedGpuStruct> buildStructAliasRegistry(List<ParsedGpuStruct> structs) {
        return GpuStructAliasRegistry.create(
                structs,
                ParsedGpuStruct::ownerSimpleName,
                ParsedGpuStruct::ownerQualifiedName,
                (left, right) -> left.ownerQualifiedName().equals(right.ownerQualifiedName())
        );
    }
}
