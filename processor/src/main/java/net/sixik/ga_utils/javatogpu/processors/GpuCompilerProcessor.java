package net.sixik.ga_utils.javatogpu.processors;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import net.sixik.ga_utils.javatogpu.backend.GpuBackendSupport;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import net.sixik.ga_utils.javatogpu.api.GpuAnnotationSupport;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.GpuFrontendService;
import net.sixik.ga_utils.javatogpu.frontend.GpuStructAliasRegistry;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelNaming;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "net.sixik.ga_utils.javatogpu.api.annotations.GPU",
        "net.sixik.ga_utils.javatogpu.api.annotations.CCode",
        "net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary",
        "net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic",
        "net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsicLibrary",
        "net.sixik.ga_utils.javatogpu.api.annotations.GPUConstant",
        "net.sixik.ga_utils.javatogpu.api.annotations.GPULocal",
        "net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct"
})
public final class GpuCompilerProcessor extends AbstractProcessor {

    private static final String HELPER_METADATA_PREFIX = "META-INF/javatogpu/ccode/";
    private static final String HELPER_LIBRARY_INDEX_PATH = HELPER_METADATA_PREFIX + "index.properties";
    private static final String INTRINSIC_METADATA_PREFIX = "META-INF/javatogpu/intrinsics/";
    private static final String INTRINSIC_LIBRARY_INDEX_PATH = INTRINSIC_METADATA_PREFIX + "index.properties";
    private static final GpuBackendTarget TARGET_BACKEND = GpuBackendTarget.OPENCL;

    private final Set<String> writtenResources = new HashSet<>();
    private final Set<String> writtenLaunchers = new HashSet<>();
    private final Set<String> writtenHelperMetadata = new HashSet<>();
    private final Set<String> writtenIntrinsicMetadata = new HashSet<>();
    private final Map<String, String> exportedHelperLibraries = new LinkedHashMap<>();
    private final Map<String, String> exportedIntrinsicLibraries = new LinkedHashMap<>();

    private Trees trees;

    private static final List<String> GPU_ANNOTATIONS = GpuAnnotationSupport.GPU_ANNOTATION_TYPES;
    private static final List<String> CCODE_ANNOTATIONS = GpuAnnotationSupport.CCODE_ANNOTATION_TYPES;
    private static final List<String> CCODE_LIBRARY_ANNOTATIONS = GpuAnnotationSupport.CCODE_LIBRARY_ANNOTATION_TYPES;
    private static final List<String> GPU_INTRINSIC_ANNOTATIONS = GpuAnnotationSupport.GPU_INTRINSIC_ANNOTATION_TYPES;
    private static final List<String> GPU_INTRINSIC_LIBRARY_ANNOTATIONS = GpuAnnotationSupport.GPU_INTRINSIC_LIBRARY_ANNOTATION_TYPES;
    private static final List<String> GPU_CONSTANT_ANNOTATIONS = GpuAnnotationSupport.GPU_CONSTANT_ANNOTATION_TYPES;
    private static final List<String> GPU_GLOBAL_ANNOTATIONS = GpuAnnotationSupport.GPU_GLOBAL_ANNOTATION_TYPES;
    private static final List<String> GPU_LOCAL_ANNOTATIONS = GpuAnnotationSupport.GPU_LOCAL_ANNOTATION_TYPES;
    private static final List<String> GPU_STRUCT_ANNOTATIONS = GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES;

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of("javatogpu.debugAbi");
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        collectExportedHelperLibraries(roundEnv);
        collectExportedIntrinsicLibraries(roundEnv);

        try {
            writeHelperMetadata(roundEnv);
        } catch (IOException exception) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to write @CCode metadata: " + exception.getMessage()
            );
        }
        try {
            writeIntrinsicMetadata(roundEnv);
        } catch (IOException exception) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to write @GPUIntrinsic metadata: " + exception.getMessage()
            );
        }

        if (roundEnv.processingOver()) {
            try {
                writeHelperLibraryIndex();
            } catch (IOException exception) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to write @CCode library index: " + exception.getMessage()
                );
            }
            try {
                writeIntrinsicLibraryIndex();
            } catch (IOException exception) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to write @GPUIntrinsic library index: " + exception.getMessage()
                );
            }
        }

        for (Element element : elementsAnnotatedWithAny(roundEnv, GPU_ANNOTATIONS)) {
            if (element.getKind() != ElementKind.METHOD) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@GPU can only be used on methods", element);
                continue;
            }

            ExecutableElement method = (ExecutableElement) element;
            try {
                ParsedGpuMethod kernelMethod = parseMethod(method);
                List<ParsedGpuMethod> helpers = collectHelpers(roundEnv, kernelMethod, method);
                List<ParsedGpuMethod> intrinsics = collectIntrinsics(roundEnv, kernelMethod, helpers, method);
                List<ParsedGpuStruct> structs = collectStructs(roundEnv);
                if (debugAbiEnabled()) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.NOTE,
                            buildAbiHintMessage(kernelMethod, structs),
                            method
                    );
                }
                GpuFrontendService frontendService = GpuFrontendService.create(
                        GpuIntrinsicDatabase.createDefault(intrinsics, TARGET_BACKEND)
                );
                String kernelSource = frontendService.validateLowerAndEmit(kernelMethod, helpers, structs);
                writeKernelResource(method, kernelSource);
                writeLauncherSource(method, kernelSource);
            } catch (RuntimeException | IOException exception) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to compile @GPU method: "
                                + exception.getMessage()
                                + "; see docs/gpu-diagnostics-guide.md for common fixes",
                        method
                );
            }
        }

        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private String extractMethodSource(ExecutableElement method) {
        TreePath path = trees.getPath(method);
        if (path == null) {
            throw new IllegalStateException("Cannot resolve source tree for method " + method.getSimpleName());
        }
        return path.getLeaf().toString();
    }

    private List<String> collectHelperSources(ExecutableElement method) {
        return method.getEnclosingElement().getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(candidate -> hasAnyAnnotation(candidate, CCODE_ANNOTATIONS))
                .filter(candidate -> !candidate.equals(method))
                .map(this::extractMethodSource)
                .toList();
    }

    private ParsedGpuMethod parseMethod(ExecutableElement method) {
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        return new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser().parseMethod(
                extractMethodSource(method),
                owner.getSimpleName().toString(),
                owner.getQualifiedName().toString(),
                collectConstants(owner)
        );
    }

    private ParsedGpuStruct parseStruct(TypeElement type) {
        return new GpuStructParser().parseStruct(
                extractTypeSource(type),
                type.getSimpleName().toString(),
                type.getQualifiedName().toString()
        );
    }

    private String extractTypeSource(TypeElement type) {
        TreePath path = trees.getPath(type);
        if (path == null) {
            throw new IllegalStateException("Cannot resolve source tree for type " + type.getSimpleName());
        }
        return path.getLeaf().toString();
    }

    private List<ParsedGpuStruct> collectStructs(RoundEnvironment roundEnv) {
        return elementsAnnotatedWithAny(roundEnv, GPU_STRUCT_ANNOTATIONS).stream()
                .filter(element -> element.getKind().isClass() || element.getKind().isInterface())
                .map(TypeElement.class::cast)
                .map(this::parseStruct)
                .toList();
    }

    private List<ParsedGpuMethod> collectHelpers(
            RoundEnvironment roundEnv,
            ParsedGpuMethod kernelMethod,
            ExecutableElement kernelElement
    ) {
        Set<String> reachableOwners = new LinkedHashSet<>(extractScopedHelperOwners(kernelMethod));
        List<ParsedGpuMethod> currentHelpers = elementsAnnotatedWithAny(roundEnv, CCODE_ANNOTATIONS).stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(candidate -> !candidate.equals(kernelElement))
                .filter(this::isSupportedHelperMethod)
                .map(this::parseMethod)
                .filter(helper -> isReachableHelperOwner(helper, kernelMethod, reachableOwners))
                .toList();

        Map<String, ParsedGpuMethod> helpers = new LinkedHashMap<>();
        currentHelpers.forEach(helper -> helpers.put(helperKey(helper), helper));
        loadClasspathHelpers(kernelMethod, currentHelpers).forEach(helper -> helpers.putIfAbsent(helperKey(helper), helper));
        validateReusableHelperOwners(kernelElement, kernelMethod, currentHelpers, List.copyOf(helpers.values()));
        return List.copyOf(helpers.values());
    }

    private List<ParsedGpuMethod> collectIntrinsics(
            RoundEnvironment roundEnv,
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            ExecutableElement kernelElement
    ) {
        List<ParsedGpuMethod> currentIntrinsics = elementsAnnotatedWithAny(roundEnv, GPU_INTRINSIC_ANNOTATIONS).stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(this::isSupportedIntrinsicMethod)
                .map(this::parseMethod)
                .toList();

        Map<String, ParsedGpuMethod> intrinsics = new LinkedHashMap<>();
        currentIntrinsics.forEach(intrinsic -> intrinsics.put(helperKey(intrinsic), intrinsic));
        loadClasspathIntrinsics(kernelMethod, helperMethods, currentIntrinsics).forEach(intrinsic -> intrinsics.putIfAbsent(helperKey(intrinsic), intrinsic));
        validateReusableIntrinsicOwners(kernelElement, kernelMethod, helperMethods, currentIntrinsics, List.copyOf(intrinsics.values()));
        return List.copyOf(intrinsics.values());
    }

    private List<ParsedGpuMethod> loadClasspathHelpers(ParsedGpuMethod kernelMethod, List<ParsedGpuMethod> currentHelpers) {
        Map<String, ParsedGpuMethod> loadedHelpers = new LinkedHashMap<>();
        Set<String> attemptedOwners = new HashSet<>();
        Deque<String> pendingOwners = new ArrayDeque<>(extractScopedHelperOwners(kernelMethod));
        currentHelpers.forEach(helper -> pendingOwners.addAll(extractScopedHelperOwners(helper)));

        while (!pendingOwners.isEmpty()) {
            String ownerReference = pendingOwners.removeFirst();
            if (ownerReference.isBlank() || "GPU".equals(ownerReference) || !attemptedOwners.add(ownerReference)) {
                continue;
            }

            for (ParsedGpuMethod helper : readHelperMetadata(ownerReference)) {
                if (loadedHelpers.putIfAbsent(helperKey(helper), helper) == null) {
                    pendingOwners.addAll(extractScopedHelperOwners(helper));
                }
            }
        }

        return List.copyOf(loadedHelpers.values());
    }

    private boolean isReachableHelperOwner(
            ParsedGpuMethod helper,
            ParsedGpuMethod kernelMethod,
            Set<String> reachableOwners
    ) {
        if (helper.ownerQualifiedName().equals(kernelMethod.ownerQualifiedName())
                || helper.ownerSimpleName().equals(kernelMethod.ownerSimpleName())) {
            return true;
        }
        if (reachableOwners.isEmpty()) {
            return false;
        }
        return reachableOwners.contains(helper.ownerSimpleName())
                || reachableOwners.contains(helper.ownerQualifiedName())
                || reachableOwners.contains(lastScopeSegment(helper.ownerQualifiedName()));
    }

    private List<ParsedGpuMethod> loadClasspathIntrinsics(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuMethod> currentIntrinsics
    ) {
        Map<String, ParsedGpuMethod> loadedIntrinsics = new LinkedHashMap<>();
        Set<String> attemptedOwners = new HashSet<>();
        Deque<String> pendingOwners = new ArrayDeque<>(extractScopedMethodOwners(kernelMethod));
        helperMethods.forEach(helper -> pendingOwners.addAll(extractScopedMethodOwners(helper)));
        currentIntrinsics.forEach(intrinsic -> pendingOwners.addAll(extractScopedMethodOwners(intrinsic)));

        while (!pendingOwners.isEmpty()) {
            String ownerReference = pendingOwners.removeFirst();
            if (ownerReference.isBlank() || "GPU".equals(ownerReference) || !attemptedOwners.add(ownerReference)) {
                continue;
            }

            for (ParsedGpuMethod intrinsic : readIntrinsicMetadata(ownerReference)) {
                if (loadedIntrinsics.putIfAbsent(helperKey(intrinsic), intrinsic) == null) {
                    pendingOwners.addAll(extractScopedMethodOwners(intrinsic));
                }
            }
        }

        return List.copyOf(loadedIntrinsics.values());
    }

    private List<ParsedGpuConstant> collectConstants(TypeElement owner) {
        return owner.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.FIELD)
                .map(VariableElement.class::cast)
                .filter(this::isSupportedGpuConstantField)
                .map(field -> new ParsedGpuConstant(
                        owner.getSimpleName().toString(),
                        owner.getQualifiedName().toString(),
                        field.getSimpleName().toString(),
                        field.asType().toString(),
                        extractConstantSource(field)
                ))
                .toList();
    }

    private void writeHelperMetadata(RoundEnvironment roundEnv) throws IOException {
        Map<TypeElement, List<ExecutableElement>> helpersByOwner = elementsAnnotatedWithAny(roundEnv, CCODE_ANNOTATIONS).stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .collect(Collectors.groupingBy(
                        method -> (TypeElement) method.getEnclosingElement(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : helpersByOwner.entrySet()) {
            TypeElement owner = entry.getKey();
            if (!isExportedHelperLibrary(owner)) {
                continue;
            }
            String simpleResourcePath = helperSimpleMetadataPath(owner.getSimpleName().toString());
            String qualifiedResourcePath = helperQualifiedMetadataPath(owner.getQualifiedName().toString());

            Properties properties = new Properties();
            properties.setProperty("ownerSimpleName", owner.getSimpleName().toString());
            properties.setProperty("ownerQualifiedName", owner.getQualifiedName().toString());
            GpuBackendSupport.storeBackends(properties, "owner.backends", helperOwnerBackends(owner));

            List<ParsedGpuConstant> constants = collectConstants(owner);
            properties.setProperty("constants.count", Integer.toString(constants.size()));
            for (int i = 0; i < constants.size(); i++) {
                ParsedGpuConstant constant = constants.get(i);
                properties.setProperty("constants." + i + ".name", constant.name());
                properties.setProperty("constants." + i + ".javaType", constant.javaType());
                properties.setProperty("constants." + i + ".sourceText", constant.sourceText());
            }

            properties.setProperty("methods.count", Integer.toString(entry.getValue().size()));
            for (int i = 0; i < entry.getValue().size(); i++) {
                properties.setProperty("methods." + i + ".source", extractMethodSource(entry.getValue().get(i)));
            }

            if (writtenHelperMetadata.add(simpleResourcePath)) {
                writeHelperMetadataProperties(simpleResourcePath, properties, owner);
            }
            if (writtenHelperMetadata.add(qualifiedResourcePath)) {
                writeHelperMetadataProperties(qualifiedResourcePath, properties, owner);
            }
        }
    }

    private void writeIntrinsicMetadata(RoundEnvironment roundEnv) throws IOException {
        Map<TypeElement, List<ExecutableElement>> intrinsicsByOwner = elementsAnnotatedWithAny(roundEnv, GPU_INTRINSIC_ANNOTATIONS).stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .collect(Collectors.groupingBy(
                        method -> (TypeElement) method.getEnclosingElement(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : intrinsicsByOwner.entrySet()) {
            TypeElement owner = entry.getKey();
            if (!isExportedIntrinsicLibrary(owner)) {
                continue;
            }
            String simpleResourcePath = intrinsicSimpleMetadataPath(owner.getSimpleName().toString());
            String qualifiedResourcePath = intrinsicQualifiedMetadataPath(owner.getQualifiedName().toString());

            Properties properties = new Properties();
            properties.setProperty("ownerSimpleName", owner.getSimpleName().toString());
            properties.setProperty("ownerQualifiedName", owner.getQualifiedName().toString());

            List<ParsedGpuConstant> constants = collectConstants(owner);
            properties.setProperty("constants.count", Integer.toString(constants.size()));
            for (int i = 0; i < constants.size(); i++) {
                ParsedGpuConstant constant = constants.get(i);
                properties.setProperty("constants." + i + ".name", constant.name());
                properties.setProperty("constants." + i + ".javaType", constant.javaType());
                properties.setProperty("constants." + i + ".sourceText", constant.sourceText());
            }

            properties.setProperty("methods.count", Integer.toString(entry.getValue().size()));
            for (int i = 0; i < entry.getValue().size(); i++) {
                properties.setProperty("methods." + i + ".source", extractMethodSource(entry.getValue().get(i)));
            }

            if (writtenIntrinsicMetadata.add(simpleResourcePath)) {
                writeMetadataProperties(simpleResourcePath, properties, owner, "JavaToGpu @GPUIntrinsic metadata");
            }
            if (writtenIntrinsicMetadata.add(qualifiedResourcePath)) {
                writeMetadataProperties(qualifiedResourcePath, properties, owner, "JavaToGpu @GPUIntrinsic metadata");
            }
        }
    }

    private void collectExportedHelperLibraries(RoundEnvironment roundEnv) {
        elementsAnnotatedWithAny(roundEnv, CCODE_LIBRARY_ANNOTATIONS).stream()
                .filter(element -> element.getKind().isClass() || element.getKind().isInterface())
                .map(TypeElement.class::cast)
                .forEach(owner -> exportedHelperLibraries.put(owner.getQualifiedName().toString(), owner.getSimpleName().toString()));
    }

    private void collectExportedIntrinsicLibraries(RoundEnvironment roundEnv) {
        elementsAnnotatedWithAny(roundEnv, GPU_INTRINSIC_LIBRARY_ANNOTATIONS).stream()
                .filter(element -> element.getKind().isClass() || element.getKind().isInterface())
                .map(TypeElement.class::cast)
                .forEach(owner -> exportedIntrinsicLibraries.put(owner.getQualifiedName().toString(), owner.getSimpleName().toString()));
    }

    private boolean isExportedHelperLibrary(TypeElement owner) {
        return exportedHelperLibraries.containsKey(owner.getQualifiedName().toString());
    }

    private boolean isExportedIntrinsicLibrary(TypeElement owner) {
        return exportedIntrinsicLibraries.containsKey(owner.getQualifiedName().toString());
    }

    private void writeHelperLibraryIndex() throws IOException {
        writeLibraryIndex(exportedHelperLibraries, HELPER_LIBRARY_INDEX_PATH, "JavaToGpu reusable @CCode helper libraries");
    }

    private void writeIntrinsicLibraryIndex() throws IOException {
        writeLibraryIndex(exportedIntrinsicLibraries, INTRINSIC_LIBRARY_INDEX_PATH, "JavaToGpu reusable @GPUIntrinsic libraries");
    }

    private void writeLibraryIndex(Map<String, String> libraries, String resourcePath, String comment) throws IOException {
        if (libraries.isEmpty()) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty("owners.count", Integer.toString(libraries.size()));
        int index = 0;
        for (Map.Entry<String, String> entry : libraries.entrySet()) {
            properties.setProperty("owners." + index + ".qualified", entry.getKey());
            properties.setProperty("owners." + index + ".simple", entry.getValue());
            index++;
        }

        FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", resourcePath);
        try (Writer writer = resource.openWriter()) {
            properties.store(writer, comment);
        }
    }

    private void writeHelperMetadataProperties(String resourcePath, Properties properties, TypeElement owner) throws IOException {
        writeMetadataProperties(resourcePath, properties, owner, "JavaToGpu @CCode metadata");
    }

    private void writeMetadataProperties(String resourcePath, Properties properties, TypeElement owner, String comment) throws IOException {
        FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", resourcePath, owner);
        try (Writer writer = resource.openWriter()) {
            properties.store(writer, comment);
        }
    }

    private List<ParsedGpuMethod> readHelperMetadata(String ownerReference) {
        String simpleOwnerName = lastScopeSegment(ownerReference);
        List<ParsedGpuMethod> helpers = new ArrayList<>();
        Map<String, ParsedGpuMethod> uniqueHelpers = new LinkedHashMap<>();
        List<String> indexedOwners = resolveIndexedOwners(ownerReference);
        if (ownerReference.contains(".")) {
            indexedOwners.forEach(indexedOwner ->
                    readHelperMetadataFromResource(helperQualifiedMetadataPath(indexedOwner)).forEach(helper -> uniqueHelpers.putIfAbsent(helperKey(helper), helper))
            );
        } else {
            if (!indexedOwners.isEmpty()) {
                indexedOwners.forEach(indexedOwner ->
                        readHelperMetadataFromResource(helperQualifiedMetadataPath(indexedOwner)).forEach(helper -> uniqueHelpers.putIfAbsent(helperKey(helper), helper))
                );
            } else {
                readHelperMetadataFromResource(helperSimpleMetadataPath(simpleOwnerName)).forEach(helper -> uniqueHelpers.putIfAbsent(helperKey(helper), helper));
            }
        }
        helpers.addAll(uniqueHelpers.values());
        return helpers;
    }

    private List<ParsedGpuMethod> readIntrinsicMetadata(String ownerReference) {
        String simpleOwnerName = lastScopeSegment(ownerReference);
        List<ParsedGpuMethod> intrinsics = new ArrayList<>();
        Map<String, ParsedGpuMethod> uniqueIntrinsics = new LinkedHashMap<>();
        List<String> indexedOwners = resolveIndexedOwners(ownerReference, INTRINSIC_LIBRARY_INDEX_PATH);
        if (ownerReference.contains(".")) {
            indexedOwners.forEach(indexedOwner ->
                    readIntrinsicMetadataFromResource(intrinsicQualifiedMetadataPath(indexedOwner)).forEach(intrinsic -> uniqueIntrinsics.putIfAbsent(helperKey(intrinsic), intrinsic))
            );
        } else {
            if (!indexedOwners.isEmpty()) {
                indexedOwners.forEach(indexedOwner ->
                        readIntrinsicMetadataFromResource(intrinsicQualifiedMetadataPath(indexedOwner)).forEach(intrinsic -> uniqueIntrinsics.putIfAbsent(helperKey(intrinsic), intrinsic))
                );
            } else {
                readIntrinsicMetadataFromResource(intrinsicSimpleMetadataPath(simpleOwnerName)).forEach(intrinsic -> uniqueIntrinsics.putIfAbsent(helperKey(intrinsic), intrinsic));
            }
        }
        intrinsics.addAll(uniqueIntrinsics.values());
        return intrinsics;
    }

    private void validateReusableHelperOwners(
            ExecutableElement kernelElement,
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> currentHelpers,
            List<ParsedGpuMethod> allHelpers
    ) {
        Set<String> availableOwners = new HashSet<>();
        allHelpers.forEach(helper -> {
            availableOwners.add(helper.ownerSimpleName());
            availableOwners.add(helper.ownerQualifiedName());
        });

        Set<String> ownerReferences = new HashSet<>(extractScopedHelperOwners(kernelMethod));
        currentHelpers.forEach(helper -> ownerReferences.addAll(extractScopedHelperOwners(helper)));
        for (String ownerReference : ownerReferences) {
            if (ownerReference.isBlank() || "GPU".equals(ownerReference) || availableOwners.contains(ownerReference) || availableOwners.contains(lastScopeSegment(ownerReference))) {
                continue;
            }

            TypeElement ownerType = resolveTypeElement(ownerReference, kernelElement);
            if (ownerType == null || !hasCCodeMethods(ownerType)) {
                continue;
            }
            if (!supportsHelperOwner(ownerType, TARGET_BACKEND) || !hasCCodeMethodsForBackend(ownerType, TARGET_BACKEND)) {
                throw new IllegalStateException(
                        "Reusable @CCode helper owner "
                                + ownerType.getQualifiedName()
                                + " does not target backend " + TARGET_BACKEND
                );
            }
            if (!hasAnyAnnotation(ownerType, CCODE_LIBRARY_ANNOTATIONS)) {
                throw new IllegalStateException(
                        "Reusable @CCode helper owner "
                                + ownerType.getQualifiedName()
                                + " must be annotated with @CCodeLibrary to be used from another compilation unit"
                );
            }
            throw new IllegalStateException(
                    "Reusable @CCode helper owner "
                            + ownerType.getQualifiedName()
                            + " is annotated with @CCodeLibrary but helper metadata was not found on the classpath; "
                            + "recompile the helper library with the JavaToGpu processor and include its compiled output or JAR"
            );
        }
    }

    private void validateReusableIntrinsicOwners(
            ExecutableElement kernelElement,
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuMethod> currentIntrinsics,
            List<ParsedGpuMethod> allIntrinsics
    ) {
        Set<String> availableOwners = new HashSet<>();
        allIntrinsics.forEach(intrinsic -> {
            availableOwners.add(intrinsic.ownerSimpleName());
            availableOwners.add(intrinsic.ownerQualifiedName());
        });

        Set<String> ownerReferences = new HashSet<>(extractScopedMethodOwners(kernelMethod));
        helperMethods.forEach(helper -> ownerReferences.addAll(extractScopedMethodOwners(helper)));
        currentIntrinsics.forEach(intrinsic -> ownerReferences.addAll(extractScopedMethodOwners(intrinsic)));
        for (String ownerReference : ownerReferences) {
            if (ownerReference.isBlank()
                    || "GPU".equals(ownerReference)
                    || availableOwners.contains(ownerReference)
                    || availableOwners.contains(lastScopeSegment(ownerReference))) {
                continue;
            }

            TypeElement ownerType = resolveTypeElement(ownerReference, kernelElement);
            if (ownerType == null || !hasGpuIntrinsicMethods(ownerType)) {
                continue;
            }
            if (!supportsIntrinsicOwner(ownerType, TARGET_BACKEND) || !hasGpuIntrinsicMethodsForBackend(ownerType, TARGET_BACKEND)) {
                throw new IllegalStateException(
                        "Reusable @GPUIntrinsic owner "
                                + ownerType.getQualifiedName()
                                + " does not target backend " + TARGET_BACKEND
                );
            }
            if (!hasAnyAnnotation(ownerType, GPU_INTRINSIC_LIBRARY_ANNOTATIONS)) {
                throw new IllegalStateException(
                        "Reusable @GPUIntrinsic owner "
                                + ownerType.getQualifiedName()
                                + " must be annotated with @GPUIntrinsicLibrary to be used from another compilation unit"
                );
            }
            throw new IllegalStateException(
                    "Reusable @GPUIntrinsic owner "
                            + ownerType.getQualifiedName()
                            + " is annotated with @GPUIntrinsicLibrary but intrinsic metadata was not found on the classpath; "
                            + "recompile the intrinsic library with the JavaToGpu processor and include its compiled output or JAR"
            );
        }
    }

    private TypeElement resolveTypeElement(String ownerReference, ExecutableElement kernelElement) {
        if (ownerReference.contains(".")) {
            return processingEnv.getElementUtils().getTypeElement(ownerReference);
        }

        TypeElement enclosingType = (TypeElement) kernelElement.getEnclosingElement();
        String currentPackage = processingEnv.getElementUtils().getPackageOf(enclosingType).getQualifiedName().toString();
        TypeElement samePackageType = processingEnv.getElementUtils().getTypeElement(currentPackage + "." + ownerReference);
        if (samePackageType != null) {
            return samePackageType;
        }

        TreePath path = trees.getPath(kernelElement);
        if (path == null) {
            return null;
        }

        for (ImportTree importTree : path.getCompilationUnit().getImports()) {
            if (importTree.isStatic()) {
                continue;
            }
            String importName = importTree.getQualifiedIdentifier().toString();
            if (importName.endsWith("." + ownerReference)) {
                TypeElement importedType = processingEnv.getElementUtils().getTypeElement(importName);
                if (importedType != null) {
                    return importedType;
                }
            }
            if (importName.endsWith(".*")) {
                String packageName = importName.substring(0, importName.length() - 2);
                TypeElement importedType = processingEnv.getElementUtils().getTypeElement(packageName + "." + ownerReference);
                if (importedType != null) {
                    return importedType;
                }
            }
        }

        return processingEnv.getElementUtils().getTypeElement("java.lang." + ownerReference);
    }

    private boolean hasCCodeMethods(TypeElement ownerType) {
        return ownerType.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .anyMatch(method -> hasAnyAnnotation(method, CCODE_ANNOTATIONS));
    }

    private boolean hasCCodeMethodsForBackend(TypeElement ownerType, GpuBackendTarget backendTarget) {
        return ownerType.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(method -> hasAnyAnnotation(method, CCODE_ANNOTATIONS))
                .anyMatch(method -> GpuBackendSupport.supportsBackend(readBackendsAnnotationValue(method, CCODE_ANNOTATIONS), backendTarget));
    }

    private boolean hasGpuIntrinsicMethods(TypeElement ownerType) {
        return ownerType.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .anyMatch(method -> hasAnyAnnotation(method, GPU_INTRINSIC_ANNOTATIONS));
    }

    private boolean hasGpuIntrinsicMethodsForBackend(TypeElement ownerType, GpuBackendTarget backendTarget) {
        return ownerType.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(method -> hasAnyAnnotation(method, GPU_INTRINSIC_ANNOTATIONS))
                .anyMatch(method -> GpuBackendSupport.supportsBackend(readBackendsAnnotationValue(method, GPU_INTRINSIC_ANNOTATIONS), backendTarget));
    }

    private boolean hasAnnotation(Element element, String annotationQualifiedName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(annotationQualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyAnnotation(Element element, List<String> annotationQualifiedNames) {
        return annotationQualifiedNames.stream().anyMatch(name -> hasAnnotation(element, name));
    }

    private Set<? extends Element> elementsAnnotatedWithAny(RoundEnvironment roundEnv, List<String> annotationQualifiedNames) {
        LinkedHashSet<Element> elements = new LinkedHashSet<>();
        for (String annotationQualifiedName : annotationQualifiedNames) {
            TypeElement annotationType = processingEnv.getElementUtils().getTypeElement(annotationQualifiedName);
            if (annotationType != null) {
                elements.addAll(roundEnv.getElementsAnnotatedWith(annotationType));
            }
        }
        return elements;
    }

    private List<String> resolveIndexedOwners(String ownerReference) {
        return resolveIndexedOwners(ownerReference, HELPER_LIBRARY_INDEX_PATH);
    }

    private List<String> resolveIndexedOwners(String ownerReference, String indexPath) {
        Properties properties = readLibraryIndex(indexPath);
        if (properties.isEmpty()) {
            return ownerReference.contains(".") ? List.of(ownerReference) : List.of();
        }

        int ownerCount = Integer.parseInt(properties.getProperty("owners.count", "0"));
        List<String> resolvedOwners = new ArrayList<>();
        for (int i = 0; i < ownerCount; i++) {
            String qualifiedName = properties.getProperty("owners." + i + ".qualified");
            String simpleName = properties.getProperty("owners." + i + ".simple");
            if (qualifiedName == null || simpleName == null) {
                continue;
            }
            if (ownerReference.contains(".")) {
                if (ownerReference.equals(qualifiedName)) {
                    resolvedOwners.add(qualifiedName);
                }
            } else if (ownerReference.equals(simpleName)) {
                resolvedOwners.add(qualifiedName);
            }
        }

        if (resolvedOwners.isEmpty() && ownerReference.contains(".")) {
            return List.of(ownerReference);
        }
        return resolvedOwners;
    }

    private Properties readHelperLibraryIndex() {
        return readLibraryIndex(HELPER_LIBRARY_INDEX_PATH);
    }

    private Properties readLibraryIndex(String indexPath) {
        Properties properties = new Properties();
        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "", indexPath);
            try (InputStream inputStream = resource.openInputStream()) {
                properties.load(inputStream);
            }
        } catch (IOException ignored) {
            return new Properties();
        }
        return properties;
    }

    private List<ParsedGpuMethod> readHelperMetadataFromResource(String resourcePath) {
        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "", resourcePath);
            Properties properties = new Properties();
            try (InputStream inputStream = resource.openInputStream()) {
                properties.load(inputStream);
            }
            if (!GpuBackendSupport.containsBackend(properties, "owner.backends", TARGET_BACKEND)) {
                return List.of();
            }
        } catch (IOException exception) {
            return List.of();
        }
        return filterMethodsForBackend(readMethodsMetadataFromResource(resourcePath), "CCode");
    }

    private List<ParsedGpuMethod> readIntrinsicMetadataFromResource(String resourcePath) {
        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "", resourcePath);
            Properties properties = new Properties();
            try (InputStream inputStream = resource.openInputStream()) {
                properties.load(inputStream);
            }
            if (!GpuBackendSupport.containsBackend(properties, "owner.backends", TARGET_BACKEND)) {
                return List.of();
            }
        } catch (IOException exception) {
            return List.of();
        }
        return filterMethodsForBackend(readMethodsMetadataFromResource(resourcePath), "GPUIntrinsic");
    }

    private List<ParsedGpuMethod> readMethodsMetadataFromResource(String resourcePath) {
        List<ParsedGpuMethod> helpers = new ArrayList<>();
        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "", resourcePath);
            Properties properties = new Properties();
            try (InputStream inputStream = resource.openInputStream()) {
                properties.load(inputStream);
            }

            String ownerSimpleName = properties.getProperty("ownerSimpleName", "");
            String ownerQualifiedName = properties.getProperty("ownerQualifiedName", ownerSimpleName);
            List<ParsedGpuConstant> constants = readConstants(properties, ownerSimpleName, ownerQualifiedName);
            int methodCount = Integer.parseInt(properties.getProperty("methods.count", "0"));
            net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                    new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
            for (int i = 0; i < methodCount; i++) {
                String methodSource = properties.getProperty("methods." + i + ".source");
                if (methodSource == null || methodSource.isBlank()) {
                    continue;
                }
                helpers.add(parser.parseMethod(methodSource, ownerSimpleName, ownerQualifiedName, constants));
            }
        } catch (IOException exception) {
            return List.of();
        }
        return helpers;
    }

    private List<ParsedGpuConstant> readConstants(Properties properties, String ownerSimpleName, String ownerQualifiedName) {
        int constantCount = Integer.parseInt(properties.getProperty("constants.count", "0"));
        List<ParsedGpuConstant> constants = new ArrayList<>(constantCount);
        for (int i = 0; i < constantCount; i++) {
            constants.add(new ParsedGpuConstant(
                    ownerSimpleName,
                    ownerQualifiedName,
                    properties.getProperty("constants." + i + ".name"),
                    properties.getProperty("constants." + i + ".javaType"),
                    properties.getProperty("constants." + i + ".sourceText")
            ));
        }
        return constants;
    }

    private Set<String> extractScopedHelperOwners(ParsedGpuMethod method) {
        return extractScopedMethodOwners(method);
    }

    private Set<String> extractScopedMethodOwners(ParsedGpuMethod method) {
        return method.declaration().findAll(MethodCallExpr.class).stream()
                .map(call -> call.getScope().map(Node::toString).orElse(""))
                .filter(scope -> !scope.isBlank() && !"GPU".equals(scope))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String lastScopeSegment(String scope) {
        int separator = scope.lastIndexOf('.');
        return separator >= 0 ? scope.substring(separator + 1) : scope;
    }

    private String helperKey(ParsedGpuMethod helper) {
        return helper.ownerQualifiedName() + "#" + helper.name() + helper.parameters().stream()
                .map(parameter -> parameter.javaType())
                .collect(Collectors.joining(",", "(", ")"));
    }

    private String helperSimpleMetadataPath(String ownerSimpleName) {
        return HELPER_METADATA_PREFIX + ownerSimpleName + ".properties";
    }

    private String helperQualifiedMetadataPath(String ownerQualifiedName) {
        return HELPER_METADATA_PREFIX + ownerQualifiedName.replace('.', '/') + ".properties";
    }

    private String intrinsicSimpleMetadataPath(String ownerSimpleName) {
        return INTRINSIC_METADATA_PREFIX + ownerSimpleName + ".properties";
    }

    private String intrinsicQualifiedMetadataPath(String ownerQualifiedName) {
        return INTRINSIC_METADATA_PREFIX + ownerQualifiedName.replace('.', '/') + ".properties";
    }

    private boolean isSupportedGpuConstantField(VariableElement field) {
        return field.getModifiers().contains(Modifier.STATIC)
                && field.getModifiers().contains(Modifier.FINAL)
                && field.getConstantValue() != null
                && GpuTypeSupport.isSupportedScalarType(field.asType().toString());
    }

    private String extractConstantSource(VariableElement field) {
        TreePath path = trees.getPath(field);
        if (path != null && path.getLeaf() instanceof VariableTree variableTree && variableTree.getInitializer() != null) {
            return variableTree.getInitializer().toString();
        }
        return toConstantSource(field.asType().toString(), field.getConstantValue());
    }

    private String toConstantSource(String javaType, Object value) {
        return switch (javaType) {
            case "boolean" -> String.valueOf(value);
            case "float" -> value + "f";
            case "double" -> String.valueOf(value);
            case "long" -> value + "L";
            default -> String.valueOf(value);
        };
    }

    private boolean isSupportedHelperMethod(ExecutableElement method) {
        if (!hasAnyAnnotation(method, CCODE_ANNOTATIONS)
                || !GpuBackendSupport.supportsBackend(readBackendsAnnotationValue(method, CCODE_ANNOTATIONS), TARGET_BACKEND)) {
            return false;
        }
        return supportsHelperOwner((TypeElement) method.getEnclosingElement(), TARGET_BACKEND);
    }

    private boolean isSupportedIntrinsicMethod(ExecutableElement method) {
        if (!hasAnyAnnotation(method, GPU_INTRINSIC_ANNOTATIONS)
                || !GpuBackendSupport.supportsBackend(readBackendsAnnotationValue(method, GPU_INTRINSIC_ANNOTATIONS), TARGET_BACKEND)) {
            return false;
        }
        return supportsIntrinsicOwner((TypeElement) method.getEnclosingElement(), TARGET_BACKEND);
    }

    private boolean supportsHelperOwner(TypeElement owner, GpuBackendTarget backendTarget) {
        return !hasAnyAnnotation(owner, CCODE_LIBRARY_ANNOTATIONS)
                || GpuBackendSupport.supportsBackend(readBackendsAnnotationValue(owner, CCODE_LIBRARY_ANNOTATIONS), backendTarget);
    }

    private boolean supportsIntrinsicOwner(TypeElement owner, GpuBackendTarget backendTarget) {
        return !hasAnyAnnotation(owner, GPU_INTRINSIC_LIBRARY_ANNOTATIONS)
                || GpuBackendSupport.supportsBackend(readBackendsAnnotationValue(owner, GPU_INTRINSIC_LIBRARY_ANNOTATIONS), backendTarget);
    }

    private GpuBackendTarget[] helperOwnerBackends(TypeElement owner) {
        return hasAnyAnnotation(owner, CCODE_LIBRARY_ANNOTATIONS)
                ? readBackendsAnnotationValue(owner, CCODE_LIBRARY_ANNOTATIONS)
                : new GpuBackendTarget[]{TARGET_BACKEND};
    }

    private GpuBackendTarget[] ownerBackends(TypeElement owner) {
        return hasAnyAnnotation(owner, GPU_INTRINSIC_LIBRARY_ANNOTATIONS)
                ? readBackendsAnnotationValue(owner, GPU_INTRINSIC_LIBRARY_ANNOTATIONS)
                : new GpuBackendTarget[]{TARGET_BACKEND};
    }

    private GpuBackendTarget[] readBackendsAnnotationValue(Element element, List<String> annotationQualifiedNames) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (!annotationQualifiedNames.contains(mirror.getAnnotationType().toString())) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends javax.lang.model.element.AnnotationValue> entry
                    : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
                if (!"backends".equals(entry.getKey().getSimpleName().toString())) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<? extends javax.lang.model.element.AnnotationValue> values =
                        (List<? extends javax.lang.model.element.AnnotationValue>) entry.getValue().getValue();
                return values.stream()
                        .map(value -> value.getValue().toString())
                        .map(name -> name.substring(name.lastIndexOf('.') + 1))
                        .map(GpuBackendTarget::valueOf)
                        .toArray(GpuBackendTarget[]::new);
            }
        }
        return new GpuBackendTarget[]{TARGET_BACKEND};
    }

    private List<ParsedGpuMethod> filterMethodsForBackend(List<ParsedGpuMethod> methods, String annotationName) {
        return methods.stream()
                .filter(method -> GpuBackendSupport.supportsParsedMethodBackend(method, annotationName, TARGET_BACKEND))
                .toList();
    }

    private void writeKernelResource(ExecutableElement method, String kernelSource) throws IOException {
        String resourcePath = buildResourcePath(method);
        if (!writtenResources.add(resourcePath)) {
            return;
        }

        Filer filer = processingEnv.getFiler();
        FileObject resource = filer.createResource(StandardLocation.SOURCE_OUTPUT, "", resourcePath, method);
        try (Writer writer = resource.openWriter()) {
            writer.write(kernelSource);
        }
    }

    private void writeLauncherSource(ExecutableElement method, String kernelSource) throws IOException {
        String packageName = buildLauncherPackageName(method);
        String className = buildLauncherClassName(method);
        String qualifiedName = packageName + "." + className;
        if (!writtenLaunchers.add(qualifiedName)) {
            return;
        }

        String launcherSource = buildLauncherSource(method, kernelSource, packageName, className);
        FileObject sourceFile = processingEnv.getFiler().createSourceFile(qualifiedName, method);
        try (Writer writer = sourceFile.openWriter()) {
            writer.write(launcherSource);
        }
    }

    private String buildLauncherSource(ExecutableElement method, String kernelSource, String packageName, String className) {
        String resourcePath = buildResourcePath(method);
        String parameterSignature = method.getParameters().stream()
                .map(this::toParameterDeclaration)
                .collect(Collectors.joining(", "));
        String returnType = method.getReturnType().toString();
        String parameterDescriptors = method.getParameters().stream()
                .map(this::toParameterDescriptorSource)
                .collect(Collectors.joining(",\n                    "));

        return "package " + packageName + ";\n\n"
                + "public final class " + className + " {\n"
                + "    public static final String KERNEL_NAME = " + toJavaStringLiteral(OpenClKernelNaming.toEntryPointName(method.getSimpleName().toString())) + ";\n"
                + "    public static final String KERNEL_RESOURCE = " + toJavaStringLiteral(resourcePath) + ";\n"
                + "    public static final String KERNEL_SOURCE = " + toJavaStringLiteral(kernelSource) + ";\n"
                + "    public static final net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor KERNEL_DESCRIPTOR =\n"
                + "            new net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor(\n"
                + "                    KERNEL_NAME,\n"
                + "                    KERNEL_RESOURCE,\n"
                + "                    KERNEL_SOURCE,\n"
                + "                    java.util.List.of(\n"
                + (parameterDescriptors.isEmpty() ? "" : "                    " + parameterDescriptors + "\n")
                + "                    )\n"
                + "            );\n\n"
                + "    private " + className + "() {\n"
                + "    }\n\n"
                + "    public static String kernelSource() {\n"
                + "        return KERNEL_SOURCE;\n"
                + "    }\n\n"
                + "    public static " + returnType + " invoke(" + parameterSignature + ") {\n"
                + emitLauncherInvokeBody(method)
                + "    }\n"
                + "}\n";
    }

    private String emitLauncherInvokeBody(ExecutableElement method) {
        String arguments = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));

        if ("void".equals(method.getReturnType().toString())) {
            return "        net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.invoke(KERNEL_DESCRIPTOR"
                    + (arguments.isEmpty() ? "" : ", " + arguments)
                    + ");\n";
        }

        return "        throw new UnsupportedOperationException(\"Non-void GPU launchers are not implemented yet\");\n";
    }

    private String toParameterDeclaration(VariableElement parameter) {
        return launcherParameterType(parameter.asType()) + " " + parameter.getSimpleName();
    }

    private String launcherParameterType(TypeMirror parameterType) {
        if (parameterType.getKind().isPrimitive()) {
            return parameterType.toString();
        }
        if (parameterType.getKind() == TypeKind.ARRAY) {
            ArrayType arrayType = (ArrayType) parameterType;
            return launcherParameterType(arrayType.getComponentType()) + "[]";
        }
        if (parameterType.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) parameterType;
            if (declaredType.asElement() instanceof TypeElement typeElement
                    && !typeElement.getModifiers().contains(Modifier.PUBLIC)) {
                return Object.class.getName();
            }
        }
        return parameterType.toString();
    }

    private String toParameterDescriptorSource(VariableElement parameter) {
        return "new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor("
                + toJavaStringLiteral(parameter.getSimpleName().toString()) + ", "
                + toJavaStringLiteral(parameter.asType().toString()) + ", "
                + "net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess." + resolveParameterAccess(parameter)
                + ")";
    }

    private String resolveParameterAccess(VariableElement parameter) {
        if (hasAnyAnnotation(parameter, GPU_LOCAL_ANNOTATIONS)) {
            return "LOCAL";
        }
        if (hasAnyAnnotation(parameter, GPU_CONSTANT_ANNOTATIONS)) {
            return "READ_ONLY";
        }
        if (hasAnyAnnotation(parameter, GPU_GLOBAL_ANNOTATIONS)) {
            return readGlobalConstantFlag(parameter) ? "READ_ONLY" : "READ_WRITE";
        }
        return "VALUE";
    }

    private boolean readGlobalConstantFlag(VariableElement parameter) {
        for (AnnotationMirror mirror : parameter.getAnnotationMirrors()) {
            if (!GPU_GLOBAL_ANNOTATIONS.contains(mirror.getAnnotationType().toString())) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends javax.lang.model.element.AnnotationValue> entry
                    : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
                if ("constant".equals(entry.getKey().getSimpleName().toString())) {
                    return Boolean.parseBoolean(entry.getValue().getValue().toString());
                }
            }
        }
        return false;
    }

    private String buildResourcePath(ExecutableElement method) {
        TypeElement enclosingType = (TypeElement) method.getEnclosingElement();
        String qualifiedName = enclosingType.getQualifiedName().toString().replace('.', '/');
        return "javatogpu/" + qualifiedName + "/" + method.getSimpleName() + ".cl";
    }

    private String buildLauncherPackageName(ExecutableElement method) {
        TypeElement enclosingType = (TypeElement) method.getEnclosingElement();
        String ownerPackage = processingEnv.getElementUtils().getPackageOf(enclosingType).getQualifiedName().toString();
        return ownerPackage.isEmpty() ? "generated" : ownerPackage + ".generated";
    }

    private String buildLauncherClassName(ExecutableElement method) {
        List<String> ownerNames = collectOwnerNames(method);
        ownerNames.add(method.getSimpleName().toString());
        ownerNames.add("GpuLauncher");
        return String.join("_", ownerNames);
    }

    private List<String> collectOwnerNames(ExecutableElement method) {
        java.util.LinkedList<String> names = new java.util.LinkedList<>();
        Element current = method.getEnclosingElement();
        while (current instanceof TypeElement typeElement) {
            names.addFirst(typeElement.getSimpleName().toString());
            current = current.getEnclosingElement();
        }
        return names;
    }

    private String toJavaStringLiteral(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        builder.append("\"");
        return builder.toString();
    }

    private boolean debugAbiEnabled() {
        return Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("javatogpu.debugAbi", "false"));
    }

    private String buildAbiHintMessage(ParsedGpuMethod kernelMethod, List<ParsedGpuStruct> structs) {
        StringBuilder builder = new StringBuilder();
        builder.append("OpenCL ABI hints for ")
                .append(kernelMethod.ownerQualifiedName())
                .append("#")
                .append(kernelMethod.name())
                .append(":\n");

        GpuStructAliasRegistry<ParsedGpuStruct> structRegistry = GpuStructAliasRegistry.create(
                structs,
                ParsedGpuStruct::ownerSimpleName,
                ParsedGpuStruct::ownerQualifiedName,
                (left, right) -> left.ownerQualifiedName().equals(right.ownerQualifiedName())
        );

        for (var parameter : kernelMethod.parameters()) {
            builder.append("- ")
                    .append(parameter.name())
                    .append(" : ")
                    .append(parameter.javaType())
                    .append(" [")
                    .append(parameter.addressSpace())
                    .append("]\n");
            appendSourceAbiHint(builder, parameter.javaType(), structRegistry, 1, new HashSet<>());
        }
        return builder.toString();
    }

    private void appendSourceAbiHint(
            StringBuilder builder,
            String javaType,
            GpuStructAliasRegistry<ParsedGpuStruct> structRegistry,
            int indent,
            Set<String> activeTypes
    ) {
        String prefix = "  ".repeat(indent);
        String declaredType = GpuTypeSupport.declaredType(javaType);

        if (GpuTypeSupport.isArrayType(declaredType)) {
            String componentType = GpuTypeSupport.componentType(declaredType);
            builder.append(prefix)
                    .append("array of ")
                    .append(componentType)
                    .append("\n");
            appendSourceAbiHint(builder, componentType, structRegistry, indent + 1, activeTypes);
            return;
        }
        if (GpuTypeSupport.isSupportedVectorType(declaredType)) {
            builder.append(prefix)
                    .append("vector ")
                    .append(GpuTypeSupport.openClVectorTypeName(declaredType))
                    .append(" size=")
                    .append(GpuTypeSupport.vectorByteSize(declaredType))
                    .append("\n");
            return;
        }

        ParsedGpuStruct struct = resolveParsedStruct(declaredType, structRegistry);
        if (struct == null) {
            builder.append(prefix)
                    .append("scalar/pointer-like ")
                    .append(declaredType)
                    .append("\n");
            return;
        }

        if (!activeTypes.add(struct.ownerQualifiedName())) {
            builder.append(prefix)
                    .append(struct.ownerQualifiedName())
                    .append(" (recursive reference)\n");
            return;
        }

        builder.append(prefix)
                .append("struct ")
                .append(struct.ownerQualifiedName())
                .append("\n");
        for (var field : struct.fields()) {
            builder.append(prefix)
                    .append("  ")
                    .append(field.name())
                    .append(" : ")
                    .append(field.javaType())
                    .append("\n");
            appendSourceAbiHint(builder, field.javaType(), structRegistry, indent + 2, activeTypes);
        }
        activeTypes.remove(struct.ownerQualifiedName());
    }

    private ParsedGpuStruct resolveParsedStruct(String typeName, GpuStructAliasRegistry<ParsedGpuStruct> structRegistry) {
        return structRegistry.resolve(typeName);
    }
}
