package net.sixik.ga_utils.javatogpu.processors;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import net.sixik.ga_utils.javatogpu.backend.GpuBackendSupport;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import net.sixik.ga_utils.javatogpu.api.GpuAnnotationSupport;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.GpuFrontendArtifactWriter;
import net.sixik.ga_utils.javatogpu.frontend.GpuFrontendCompilationResult;
import net.sixik.ga_utils.javatogpu.frontend.GpuFrontendResourcePaths;
import net.sixik.ga_utils.javatogpu.frontend.GpuFrontendService;
import net.sixik.ga_utils.javatogpu.frontend.GpuStructAliasRegistry;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationDiagnosticPolicy;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationMode;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationReportEntry;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuConstantDataKind;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstantData;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelNaming;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuStructParser;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import javax.annotation.processing.AbstractProcessor;
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
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("*")
public final class GpuCompilerProcessor extends AbstractProcessor {

    private static final String CALL_SITE_METADATA_PREFIX = "META-INF/javatogpu/call-sites/";
    private static final String HELPER_METADATA_PREFIX = "META-INF/javatogpu/ccode/";
    private static final String HELPER_LIBRARY_INDEX_PATH = HELPER_METADATA_PREFIX + "index.properties";
    private static final String INTRINSIC_METADATA_PREFIX = "META-INF/javatogpu/intrinsics/";
    private static final String INTRINSIC_LIBRARY_INDEX_PATH = INTRINSIC_METADATA_PREFIX + "index.properties";
    private static final String FALLBACK_VARIANT_PROVIDER_TYPE =
            "net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantProvider";
    private static final String FALLBACK_VARIANT_SERVICE_PATH = "META-INF/services/" + FALLBACK_VARIANT_PROVIDER_TYPE;
    private static final List<String> GPU_FALLBACK_VARIANT_ANNOTATIONS = List.of(
            "net.sixik.ga_utils.javatogpu.api.annotations.GPUFallbackVariant"
    );
    private static final GpuBackendTarget TARGET_BACKEND = GpuBackendTarget.OPENCL;

    private final Set<String> writtenResources = new HashSet<>();
    private final Set<String> writtenLaunchers = new HashSet<>();
    private final Set<String> writtenHelperMetadata = new HashSet<>();
    private final Set<String> writtenIntrinsicMetadata = new HashSet<>();
    private final Set<String> writtenFallbackVariantProviders = new HashSet<>();
    private final Set<String> writtenCallSiteMetadata = new HashSet<>();
    private final Set<String> reportedReturnValueConvenienceDiagnostics = new HashSet<>();
    private final Map<String, String> exportedHelperLibraries = new LinkedHashMap<>();
    private final Map<String, String> exportedIntrinsicLibraries = new LinkedHashMap<>();
    private final List<GpuIrValidationReportEntry> irValidationReportEntries = new ArrayList<>();
    private boolean irValidationReportWritten;
    private boolean fallbackVariantServiceWritten;

    private Trees trees;

    private static final List<String> GPU_ANNOTATIONS = GpuAnnotationSupport.GPU_ANNOTATION_TYPES;
    private static final List<String> CCODE_ANNOTATIONS = GpuAnnotationSupport.CCODE_ANNOTATION_TYPES;
    private static final List<String> CCODE_LIBRARY_ANNOTATIONS = GpuAnnotationSupport.CCODE_LIBRARY_ANNOTATION_TYPES;
    private static final List<String> GPU_INTRINSIC_ANNOTATIONS = GpuAnnotationSupport.GPU_INTRINSIC_ANNOTATION_TYPES;
    private static final List<String> GPU_INTRINSIC_LIBRARY_ANNOTATIONS = GpuAnnotationSupport.GPU_INTRINSIC_LIBRARY_ANNOTATION_TYPES;
    private static final List<String> GPU_CONSTANT_ANNOTATIONS = GpuAnnotationSupport.GPU_CONSTANT_ANNOTATION_TYPES;
    private static final List<String> GPU_CONSTANT_DATA_ANNOTATIONS = GpuAnnotationSupport.GPU_CONSTANT_DATA_ANNOTATION_TYPES;
    private static final List<String> GPU_EXTERN_CONSTANT_DATA_ANNOTATIONS = GpuAnnotationSupport.GPU_EXTERN_CONSTANT_DATA_ANNOTATION_TYPES;
    private static final List<String> GPU_GLOBAL_ANNOTATIONS = GpuAnnotationSupport.GPU_GLOBAL_ANNOTATION_TYPES;
    private static final List<String> GPU_LOCAL_ANNOTATIONS = GpuAnnotationSupport.GPU_LOCAL_ANNOTATION_TYPES;
    private static final List<String> GPU_STRUCT_ANNOTATIONS = GpuAnnotationSupport.GPU_STRUCT_ANNOTATION_TYPES;
    private static final List<String> GPU_POINTER_TYPE_ANNOTATIONS = GpuAnnotationSupport.GPU_POINTER_TYPE_ANNOTATION_TYPES;
    private static final List<String> GPU_SCALAR_ALIAS_TYPE_ANNOTATIONS = GpuAnnotationSupport.GPU_SCALAR_ALIAS_TYPE_ANNOTATION_TYPES;
    private static final List<String> GPU_VECTOR_TYPE_ANNOTATIONS = GpuAnnotationSupport.GPU_VECTOR_TYPE_ANNOTATION_TYPES;

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(
                "javatogpu.debugAbi",
                "javatogpu.irValidation",
                "javatogpu.irValidationDiagnostics",
                "javatogpu.irValidationReport",
                "javatogpu.returnValueConvenienceDiagnostics"
        );
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
            writeGpuCallSiteMetadata(roundEnv);
        } catch (IOException exception) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to write GPU call-site metadata: " + exception.getMessage()
            );
        }
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
            try {
                writeIrValidationReport();
            } catch (IOException exception) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to write JavaToGpu IR validation report: " + exception.getMessage()
                );
            }
        }

        Set<? extends Element> gpuElements = elementsAnnotatedWithAny(roundEnv, GPU_ANNOTATIONS);
        List<ExecutableElement> gpuMethods = gpuElements.stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .toList();
        if (!validateFallbackGroups(gpuMethods)) {
            return false;
        }
        for (Element element : gpuElements) {
            if (element.getKind() != ElementKind.METHOD) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@GPU can only be used on methods", element);
                continue;
            }

            ExecutableElement method = (ExecutableElement) element;
            try {
                registerAnnotatedTypes(method);
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
                        GpuIntrinsicDatabase.createDefault(intrinsics, TARGET_BACKEND),
                        irValidationMode(),
                        irValidationDiagnosticPolicy(),
                        message -> processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, method),
                        this::recordIrValidationReportEntry
                );
                GpuFrontendCompilationResult compilationResult = frontendService.compile(
                        kernelMethod,
                        helpers,
                        structs,
                        buildResourcePath(method)
                );
                String kernelSource = compilationResult.openClSource();
                writeFrontendArtifacts(method, compilationResult);
                writeLauncherSource(method, kernelSource, gpuMethods);
                emitReturnValueConvenienceDiagnostic(method);
            } catch (RuntimeException | IOException exception) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to compile @GPU method: "
                                + exception.getMessage()
                                + "; see docs/Troubleshooting.md for common fixes",
                        method
                );
            }
        }

        try {
            writeFallbackVariantProviders(gpuMethods);
        } catch (IOException exception) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to write @GPUFallbackVariant providers: " + exception.getMessage()
            );
        }

        return false;
    }

    /**
     * Indexes Java expressions that invoke GPU methods so runtime failures can point at the original call site.
     */
    private void writeGpuCallSiteMetadata(RoundEnvironment roundEnv) throws IOException {
        Map<String, List<CompileTimeGpuCallSite>> callSitesByCaller = collectGpuCallSites(roundEnv);
        for (Map.Entry<String, List<CompileTimeGpuCallSite>> entry : callSitesByCaller.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            String resourcePath = CALL_SITE_METADATA_PREFIX
                    + entry.getKey().replace('.', '/')
                    + ".properties";
            if (!writtenCallSiteMetadata.add(resourcePath)) {
                continue;
            }
            List<CompileTimeGpuCallSite> callSites = entry.getValue().stream()
                    .sorted(Comparator
                            .comparing(CompileTimeGpuCallSite::callerMethodName)
                            .thenComparingInt(CompileTimeGpuCallSite::line)
                            .thenComparingInt(CompileTimeGpuCallSite::column)
                            .thenComparing(CompileTimeGpuCallSite::targetOwnerName)
                            .thenComparing(CompileTimeGpuCallSite::targetMethodName))
                    .toList();
            String content = buildGpuCallSiteMetadata(callSites);
            Element origin = callSites.get(0).callerOwner();
            Writer sourceOutputWriter = processingEnv.getFiler()
                    .createResource(StandardLocation.SOURCE_OUTPUT, "", resourcePath, origin)
                    .openWriter();
            Writer classOutputWriter = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", resourcePath, origin)
                    .openWriter();
            try (Writer writer = new TeeWriter(sourceOutputWriter, classOutputWriter)) {
                writer.write(content);
            }
        }
    }

    private Map<String, List<CompileTimeGpuCallSite>> collectGpuCallSites(RoundEnvironment roundEnv) {
        Map<String, List<CompileTimeGpuCallSite>> callSitesByCaller = new LinkedHashMap<>();
        SourcePositions sourcePositions = trees.getSourcePositions();
        for (Element rootElement : roundEnv.getRootElements()) {
            TreePath rootPath = trees.getPath(rootElement);
            if (rootPath == null) {
                continue;
            }
            CompilationUnitTree compilationUnit = rootPath.getCompilationUnit();
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
                    TreePath invocationPath = getCurrentPath();
                    Element targetElement = trees.getElement(invocationPath);
                    if (targetElement instanceof ExecutableElement targetMethod
                            && hasAnyAnnotation(targetMethod, GPU_ANNOTATIONS)) {
                        CompileTimeGpuCallSite callSite = toGpuCallSite(
                                invocationPath,
                                compilationUnit,
                                sourcePositions,
                                targetMethod
                        );
                        if (callSite != null) {
                            callSitesByCaller.computeIfAbsent(
                                    callSite.callerClassName(),
                                    ignored -> new ArrayList<>()
                            ).add(callSite);
                        }
                    }
                    return super.visitMethodInvocation(invocation, unused);
                }
            }.scan(rootPath, null);
        }
        return callSitesByCaller;
    }

    private CompileTimeGpuCallSite toGpuCallSite(
            TreePath invocationPath,
            CompilationUnitTree compilationUnit,
            SourcePositions sourcePositions,
            ExecutableElement targetMethod
    ) {
        ExecutableElement callerMethod = null;
        TypeElement callerOwner = null;
        for (TreePath current = invocationPath.getParentPath(); current != null; current = current.getParentPath()) {
            Element element = trees.getElement(current);
            if (callerMethod == null
                    && current.getLeaf() instanceof MethodTree
                    && element instanceof ExecutableElement executableElement) {
                callerMethod = executableElement;
            }
            if (element instanceof TypeElement typeElement) {
                callerOwner = typeElement;
                break;
            }
        }
        if (callerMethod == null || callerOwner == null) {
            return null;
        }

        long startPosition = sourcePositions.getStartPosition(compilationUnit, invocationPath.getLeaf());
        long endPosition = sourcePositions.getEndPosition(compilationUnit, invocationPath.getLeaf());
        if (startPosition == Diagnostic.NOPOS || endPosition == Diagnostic.NOPOS) {
            return null;
        }
        long inclusiveEndPosition = Math.max(startPosition, endPosition - 1L);
        TypeElement targetOwner = targetMethod.getEnclosingElement() instanceof TypeElement typeElement
                ? typeElement
                : null;
        return new CompileTimeGpuCallSite(
                processingEnv.getElementUtils().getBinaryName(callerOwner).toString(),
                callerMethod.getSimpleName().toString(),
                sourceFileName(compilationUnit),
                toPositionInt(compilationUnit.getLineMap().getLineNumber(startPosition)),
                toPositionInt(compilationUnit.getLineMap().getColumnNumber(startPosition)),
                toPositionInt(compilationUnit.getLineMap().getLineNumber(inclusiveEndPosition)),
                toPositionInt(compilationUnit.getLineMap().getColumnNumber(inclusiveEndPosition)),
                invocationPath.getLeaf().toString(),
                targetOwner == null ? "unknown" : targetOwner.getQualifiedName().toString(),
                targetMethod.getSimpleName().toString(),
                callerOwner
        );
    }

    private String buildGpuCallSiteMetadata(List<CompileTimeGpuCallSite> callSites) {
        StringBuilder builder = new StringBuilder();
        appendProperty(builder, "format", "javatogpu.call-sites.v1");
        appendProperty(builder, "callSite.count", Integer.toString(callSites.size()));
        for (int index = 0; index < callSites.size(); index++) {
            CompileTimeGpuCallSite callSite = callSites.get(index);
            String prefix = "callSite." + index + ".";
            appendProperty(builder, prefix + "callerClassName", callSite.callerClassName());
            appendProperty(builder, prefix + "callerMethodName", callSite.callerMethodName());
            appendProperty(builder, prefix + "sourceName", callSite.sourceName());
            appendProperty(builder, prefix + "line", Integer.toString(callSite.line()));
            appendProperty(builder, prefix + "column", Integer.toString(callSite.column()));
            appendProperty(builder, prefix + "endLine", Integer.toString(callSite.endLine()));
            appendProperty(builder, prefix + "endColumn", Integer.toString(callSite.endColumn()));
            appendProperty(builder, prefix + "expression", callSite.expression());
            appendProperty(builder, prefix + "targetOwnerName", callSite.targetOwnerName());
            appendProperty(builder, prefix + "targetMethodName", callSite.targetMethodName());
        }
        return builder.toString();
    }

    private String sourceFileName(CompilationUnitTree compilationUnit) {
        String name = compilationUnit.getSourceFile() == null
                ? "unknown"
                : compilationUnit.getSourceFile().getName();
        int slashIndex = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slashIndex < 0 ? name : name.substring(slashIndex + 1);
    }

    private int toPositionInt(long position) {
        if (position < 0L || position > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) position;
    }

    private record CompileTimeGpuCallSite(
            String callerClassName,
            String callerMethodName,
            String sourceName,
            int line,
            int column,
            int endLine,
            int endColumn,
            String expression,
            String targetOwnerName,
            String targetMethodName,
            TypeElement callerOwner
    ) {
    }

    private void registerAnnotatedTypes(ExecutableElement method) {
        method.getParameters().forEach(parameter -> registerAnnotatedType(parameter.asType()));
        registerAnnotatedType(method.getReturnType());
    }

    private void registerAnnotatedType(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.ARRAY) {
            registerAnnotatedType(((ArrayType) typeMirror).getComponentType());
            return;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return;
        }

        DeclaredType declaredType = (DeclaredType) typeMirror;
        if (!(declaredType.asElement() instanceof TypeElement typeElement)) {
            return;
        }
        if (hasAnyAnnotation(typeElement, GPU_POINTER_TYPE_ANNOTATIONS)) {
            registerAnnotatedPointerType(typeElement);
        }
        if (hasAnyAnnotation(typeElement, GPU_SCALAR_ALIAS_TYPE_ANNOTATIONS)) {
            registerAnnotatedScalarAliasType(typeElement);
        }
        if (hasAnyAnnotation(typeElement, GPU_VECTOR_TYPE_ANNOTATIONS)) {
            registerAnnotatedVectorType(typeElement);
        }
    }

    private void registerAnnotatedPointerType(TypeElement typeElement) {
        String valueType = readStringAnnotationValue(typeElement, GPU_POINTER_TYPE_ANNOTATIONS, "valueType", "");
        String addressSpace = readStringAnnotationValue(typeElement, GPU_POINTER_TYPE_ANNOTATIONS, "addressSpace", "PRIVATE");
        addressSpace = addressSpace.substring(addressSpace.lastIndexOf('.') + 1);

        GpuTypeSupport.registerPointerType(
                typeElement.getSimpleName().toString(),
                typeElement.getQualifiedName().toString(),
                valueType,
                addressSpace
        );
    }

    private void registerAnnotatedScalarAliasType(TypeElement typeElement) {
        String backendType = readStringAnnotationValue(typeElement, GPU_SCALAR_ALIAS_TYPE_ANNOTATIONS, "backendType", "");
        String valueType = readStringAnnotationValue(typeElement, GPU_SCALAR_ALIAS_TYPE_ANNOTATIONS, "valueType", "");

        GpuTypeSupport.registerScalarAliasType(
                typeElement.getSimpleName().toString(),
                typeElement.getQualifiedName().toString(),
                backendType,
                valueType
        );
    }

    private void registerAnnotatedVectorType(TypeElement typeElement) {
        String openClType = readStringAnnotationValue(typeElement, GPU_VECTOR_TYPE_ANNOTATIONS, "openClType", "");
        String componentType = readStringAnnotationValue(typeElement, GPU_VECTOR_TYPE_ANNOTATIONS, "componentType", "");
        List<String> fields = readStringArrayAnnotationValue(typeElement, GPU_VECTOR_TYPE_ANNOTATIONS, "fields");
        int storageWidth = readIntAnnotationValue(typeElement, GPU_VECTOR_TYPE_ANNOTATIONS, "storageWidth", 0);

        GpuTypeSupport.registerVectorType(
                typeElement.getSimpleName().toString(),
                typeElement.getQualifiedName().toString(),
                openClType,
                componentType,
                fields,
                storageWidth
        );
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
                collectConstants(owner),
                collectConstantData(owner)
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
                .peek(this::registerAnnotatedTypes)
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
                .peek(this::registerAnnotatedTypes)
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

    private List<ParsedGpuConstantData> collectConstantData(TypeElement owner) {
        List<ParsedGpuConstantData> constantData = new ArrayList<>();
        for (Element element : owner.getEnclosedElements()) {
            if (element.getKind() != ElementKind.FIELD) {
                continue;
            }
            VariableElement field = (VariableElement) element;
            boolean embedded = hasAnyAnnotation(field, GPU_CONSTANT_DATA_ANNOTATIONS);
            boolean extern = hasAnyAnnotation(field, GPU_EXTERN_CONSTANT_DATA_ANNOTATIONS);
            if (!embedded && !extern) {
                continue;
            }
            if (embedded && extern) {
                throw new IllegalArgumentException("Field cannot declare both @GPUConstantData and @GPUExternConstantData: " + field.getSimpleName());
            }
            if (!field.getModifiers().contains(Modifier.STATIC) || !field.getModifiers().contains(Modifier.FINAL)) {
                throw new IllegalArgumentException((embedded ? "@GPUConstantData" : "@GPUExternConstantData") + " field must be static final: " + field.getSimpleName());
            }
            if (field.asType().getKind() != TypeKind.ARRAY) {
                throw new IllegalArgumentException((embedded ? "@GPUConstantData" : "@GPUExternConstantData") + " requires an array type: " + field.asType());
            }
            String componentType = ((ArrayType) field.asType()).getComponentType().toString();
            if (!GpuTypeSupport.isSupportedScalarType(componentType)) {
                throw new IllegalArgumentException((embedded ? "@GPUConstantData" : "@GPUExternConstantData") + " currently supports only primitive scalar arrays: " + field.asType());
            }
            constantData.add(new ParsedGpuConstantData(
                    owner.getSimpleName().toString(),
                    owner.getQualifiedName().toString(),
                    field.getSimpleName().toString(),
                    field.asType().toString(),
                    embedded ? extractConstantDataInitializer(field) : extractExternConstantDataInitializer(field),
                    embedded ? GpuConstantDataKind.EMBEDDED : GpuConstantDataKind.EXTERN
            ));
        }
        return constantData;
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

            List<ParsedGpuConstantData> constantData = collectConstantData(owner);
            properties.setProperty("constantData.count", Integer.toString(constantData.size()));
            for (int i = 0; i < constantData.size(); i++) {
                ParsedGpuConstantData constant = constantData.get(i);
                properties.setProperty("constantData." + i + ".name", constant.name());
                properties.setProperty("constantData." + i + ".javaType", constant.javaType());
                properties.setProperty("constantData." + i + ".initializerSource", constant.initializerSource());
                properties.setProperty("constantData." + i + ".kind", constant.kind().name());
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
            GpuBackendSupport.storeBackends(properties, "owner.backends", ownerBackends(owner));

            List<ParsedGpuConstant> constants = collectConstants(owner);
            properties.setProperty("constants.count", Integer.toString(constants.size()));
            for (int i = 0; i < constants.size(); i++) {
                ParsedGpuConstant constant = constants.get(i);
                properties.setProperty("constants." + i + ".name", constant.name());
                properties.setProperty("constants." + i + ".javaType", constant.javaType());
                properties.setProperty("constants." + i + ".sourceText", constant.sourceText());
            }

            List<ParsedGpuConstantData> constantData = collectConstantData(owner);
            properties.setProperty("constantData.count", Integer.toString(constantData.size()));
            for (int i = 0; i < constantData.size(); i++) {
                ParsedGpuConstantData constant = constantData.get(i);
                properties.setProperty("constantData." + i + ".name", constant.name());
                properties.setProperty("constantData." + i + ".javaType", constant.javaType());
                properties.setProperty("constantData." + i + ".initializerSource", constant.initializerSource());
                properties.setProperty("constantData." + i + ".kind", constant.kind().name());
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

    private void writeIrValidationReport() throws IOException {
        String reportPath = irValidationReportPath();
        if (reportPath == null || irValidationReportWritten) {
            return;
        }
        irValidationReportWritten = true;

        FileObject resource = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", reportPath);
        try (Writer writer = resource.openWriter()) {
            writer.write(buildIrValidationReportProperties());
        }
    }

    private String buildIrValidationReportProperties() {
        StringBuilder builder = new StringBuilder();
        builder.append("format=javatogpu.ir.validation.v1\n");
        builder.append("entry.count=").append(irValidationReportEntries.size()).append("\n");
        Map<String, Long> autoVectorizationNoCandidateDiagnosticCodeCounts =
                autoVectorizationNoCandidateDiagnosticCodeCounts();
        Map<String, Long> autoVectorizationNoCandidateBucketCounts =
                autoVectorizationNoCandidateBucketCounts();
        appendProperty(
                builder,
                "autoVectorizationNoCandidateBucketCounts",
                countSummary(autoVectorizationNoCandidateBucketCounts)
        );
        appendProperty(
                builder,
                "autoVectorizationNoCandidateUniqueBuckets",
                Integer.toString(autoVectorizationNoCandidateBucketCounts.size())
        );
        autoVectorizationNoCandidateBucketCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendProperty(
                        builder,
                        "autoVectorizationNoCandidateBucket." + entry.getKey(),
                        Long.toString(entry.getValue())
                ));
        appendProperty(
                builder,
                "autoVectorizationNoCandidateDiagnosticCodeCounts",
                countSummary(autoVectorizationNoCandidateDiagnosticCodeCounts)
        );
        appendProperty(
                builder,
                "autoVectorizationNoCandidateUniqueDiagnosticCodes",
                Integer.toString(autoVectorizationNoCandidateDiagnosticCodeCounts.size())
        );
        autoVectorizationNoCandidateDiagnosticCodeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> appendProperty(
                        builder,
                        "autoVectorizationNoCandidateDiagnosticCode." + entry.getKey(),
                        Long.toString(entry.getValue())
                ));
        for (int index = 0; index < irValidationReportEntries.size(); index++) {
            GpuIrValidationReportEntry entry = irValidationReportEntries.get(index);
            String prefix = "entry." + index + ".";
            appendProperty(builder, prefix + "provider", entry.provider());
            appendProperty(builder, prefix + "extensionId", entry.extensionId());
            appendProperty(builder, prefix + "extensionVersion", entry.extensionVersion());
            appendProperty(builder, prefix + "ruleId", entry.ruleId());
            appendProperty(builder, prefix + "severity", entry.severity().name());
            appendProperty(builder, prefix + "sourceAnchor", entry.sourceAnchor());
            appendProperty(builder, prefix + "methodName", entry.methodName());
            appendProperty(builder, prefix + "entryPoint", Boolean.toString(entry.entryPoint()));
            entry.values().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(value -> appendProperty(builder, prefix + value.getKey(), value.getValue()));
        }
        return builder.toString();
    }

    private Map<String, Long> autoVectorizationNoCandidateBucketCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (GpuIrValidationReportEntry entry : irValidationReportEntries) {
            String bucket = entry.values().get("autoVectorizationNoCandidateFirstBucket");
            if (bucket == null || bucket.isBlank() || "none".equals(bucket)) {
                continue;
            }
            counts.put(bucket, counts.getOrDefault(bucket, 0L) + 1L);
        }
        return counts;
    }

    private Map<String, Long> autoVectorizationNoCandidateDiagnosticCodeCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (GpuIrValidationReportEntry entry : irValidationReportEntries) {
            String code = entry.values().get("autoVectorizationNoCandidateDiagnosticCode");
            if (code == null || code.isBlank() || "none".equals(code)) {
                continue;
            }
            counts.put(code, counts.getOrDefault(code, 0L) + 1L);
        }
        return counts;
    }

    private String countSummary(Map<String, Long> counts) {
        if (counts.isEmpty()) {
            return "{}";
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private void appendProperty(StringBuilder builder, String key, String value) {
        builder.append(escapeProperty(key))
                .append('=')
                .append(escapeProperty(value == null ? "" : value))
                .append('\n');
    }

    private String escapeProperty(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '=', ':', '#', '!' -> builder.append('\\').append(ch);
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private void recordIrValidationReportEntry(GpuIrValidationReportEntry entry) {
        irValidationReportEntries.add(entry);
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
            List<ParsedGpuConstantData> constantData = readConstantData(properties, ownerSimpleName, ownerQualifiedName);
            int methodCount = Integer.parseInt(properties.getProperty("methods.count", "0"));
            net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser parser =
                    new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser();
            for (int i = 0; i < methodCount; i++) {
                String methodSource = properties.getProperty("methods." + i + ".source");
                if (methodSource == null || methodSource.isBlank()) {
                    continue;
                }
                helpers.add(parser.parseMethod(methodSource, ownerSimpleName, ownerQualifiedName, constants, constantData));
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

    private List<ParsedGpuConstantData> readConstantData(Properties properties, String ownerSimpleName, String ownerQualifiedName) {
        int constantCount = Integer.parseInt(properties.getProperty("constantData.count", "0"));
        List<ParsedGpuConstantData> constantData = new ArrayList<>(constantCount);
        for (int i = 0; i < constantCount; i++) {
            constantData.add(new ParsedGpuConstantData(
                    ownerSimpleName,
                    ownerQualifiedName,
                    properties.getProperty("constantData." + i + ".name"),
                    properties.getProperty("constantData." + i + ".javaType"),
                    properties.getProperty("constantData." + i + ".initializerSource"),
                    GpuConstantDataKind.valueOf(properties.getProperty("constantData." + i + ".kind", GpuConstantDataKind.EMBEDDED.name()))
            ));
        }
        return constantData;
    }

    private Set<String> extractScopedHelperOwners(ParsedGpuMethod method) {
        return extractScopedMethodOwners(method);
    }

    private Set<String> extractScopedMethodOwners(ParsedGpuMethod method) {
        return method.declaration().findAll(MethodCallExpr.class).stream()
                .map(call -> call.getScope().map(Node::toString).orElse(""))
                .filter(scope -> !scope.isBlank() && !"GPU".equals(scope))
                .filter(this::looksLikeTypeOwnerReference)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean looksLikeTypeOwnerReference(String scope) {
        String segment = lastScopeSegment(scope);
        return !segment.isBlank() && Character.isUpperCase(segment.charAt(0));
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

    private String extractConstantDataInitializer(VariableElement field) {
        TreePath path = trees.getPath(field);
        if (path != null && path.getLeaf() instanceof VariableTree variableTree && variableTree.getInitializer() != null) {
            return variableTree.getInitializer().toString();
        }
        throw new IllegalArgumentException("@GPUConstantData field must declare an inline initializer: " + field.getSimpleName());
    }

    private String extractExternConstantDataInitializer(VariableElement field) {
        TreePath path = trees.getPath(field);
        if (path != null && path.getLeaf() instanceof VariableTree variableTree && variableTree.getInitializer() != null) {
            String initializer = variableTree.getInitializer().toString();
            if (!"null".equals(initializer)) {
                throw new IllegalArgumentException("@GPUExternConstantData field must declare a null initializer: " + field.getSimpleName());
            }
            return initializer;
        }
        throw new IllegalArgumentException("@GPUExternConstantData field must declare a null initializer: " + field.getSimpleName());
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

    private String readStringAnnotationValue(
            Element element,
            List<String> annotationQualifiedNames,
            String propertyName,
            String defaultValue
    ) {
        Object value = readAnnotationValue(element, annotationQualifiedNames, propertyName);
        return value == null ? defaultValue : value.toString();
    }

    private int readIntAnnotationValue(
            Element element,
            List<String> annotationQualifiedNames,
            String propertyName,
            int defaultValue
    ) {
        Object value = readAnnotationValue(element, annotationQualifiedNames, propertyName);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private List<String> readStringArrayAnnotationValue(
            Element element,
            List<String> annotationQualifiedNames,
            String propertyName
    ) {
        Object value = readAnnotationValue(element, annotationQualifiedNames, propertyName);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(item -> item instanceof javax.lang.model.element.AnnotationValue annotationValue
                            ? annotationValue.getValue()
                            : item)
                    .map(Object::toString)
                    .toList();
        }
        return List.of(value.toString());
    }

    private Object readAnnotationValue(
            Element element,
            List<String> annotationQualifiedNames,
            String propertyName
    ) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (!annotationQualifiedNames.contains(mirror.getAnnotationType().toString())) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends javax.lang.model.element.AnnotationValue> entry
                    : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
                if (propertyName.equals(entry.getKey().getSimpleName().toString())) {
                    return entry.getValue().getValue();
                }
            }
        }
        return null;
    }

    private List<ParsedGpuMethod> filterMethodsForBackend(List<ParsedGpuMethod> methods, String annotationName) {
        return methods.stream()
                .filter(method -> GpuBackendSupport.supportsParsedMethodBackend(method, annotationName, TARGET_BACKEND))
                .toList();
    }

    private void writeFrontendArtifacts(
            ExecutableElement method,
            GpuFrontendCompilationResult compilationResult
    ) throws IOException {
        GpuFrontendArtifactWriter.write(compilationResult, resourcePath -> {
            if (!writtenResources.add(resourcePath)) {
                return Writer.nullWriter();
            }
            Writer sourceOutputWriter = processingEnv.getFiler()
                    .createResource(StandardLocation.SOURCE_OUTPUT, "", resourcePath, method)
                    .openWriter();
            Writer classOutputWriter = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", resourcePath, method)
                    .openWriter();
            return new TeeWriter(sourceOutputWriter, classOutputWriter);
        });
    }

    /**
     * Writes frontend artifacts both to generated sources for inspection and to class output for runtime classloader use.
     */
    private static final class TeeWriter extends Writer {
        private final Writer first;
        private final Writer second;

        private TeeWriter(Writer first, Writer second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            first.write(cbuf, off, len);
            second.write(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            IOException failure = null;
            try {
                first.flush();
            } catch (IOException exception) {
                failure = exception;
            }
            try {
                second.flush();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                first.close();
            } catch (IOException exception) {
                failure = exception;
            }
            try {
                second.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private void writeLauncherSource(
            ExecutableElement method,
            String kernelSource,
            List<ExecutableElement> gpuMethods
    ) throws IOException {
        String packageName = buildLauncherPackageName(method);
        String className = buildLauncherClassName(method);
        String qualifiedName = packageName + "." + className;
        if (!writtenLaunchers.add(qualifiedName)) {
            return;
        }

        String launcherSource = buildLauncherSource(method, kernelSource, packageName, className, gpuMethods);
        FileObject sourceFile = processingEnv.getFiler().createSourceFile(qualifiedName, method);
        try (Writer writer = sourceFile.openWriter()) {
            writer.write(launcherSource);
        }
    }

    private String buildLauncherSource(
            ExecutableElement method,
            String kernelSource,
            String packageName,
            String className,
            List<ExecutableElement> gpuMethods
    ) {
        String resourcePath = buildResourcePath(method);
        String irGpuResourcePath = buildIrGpuResourcePath(method);
        String parameterSignature = method.getParameters().stream()
                .map(this::toParameterDeclaration)
                .collect(Collectors.joining(", "));
        String returnType = method.getReturnType().toString();
        String parameterDescriptors = method.getParameters().stream()
                .map(this::toParameterDescriptorSource)
                .collect(Collectors.joining(",\n                    "));
        String fallbackDescriptors = fallbackMethodsFor(method, gpuMethods).stream()
                .map(this::toFallbackDescriptorSource)
                .collect(Collectors.joining(",\n                    "));
        ReturnValueConvenienceAnalysis returnValueConvenience = returnValueConvenienceAnalysis(method);

        return "package " + packageName + ";\n\n"
                + "/**\n"
                + " * Generated GPU launcher for the annotated method.\n"
                + " *\n"
                + " * <p>This class is generated by JavaToGpu. Application code may call these helpers directly, but\n"
                + " * should not edit this source file because it will be regenerated by the annotation processor.</p>\n"
                + " */\n"
                + "public final class " + className + " {\n"
                + "    /** Generated backend entry-point name. */\n"
                + "    public static final String KERNEL_NAME = " + toJavaStringLiteral(OpenClKernelNaming.toEntryPointName(method.getSimpleName().toString())) + ";\n"
                + "    /** Classpath resource containing the generated backend source. */\n"
                + "    public static final String KERNEL_RESOURCE = " + toJavaStringLiteral(resourcePath) + ";\n"
                + "    /** Classpath resource containing the generated IrGpu metadata artifact. */\n"
                + "    public static final String IRGPU_RESOURCE = " + toJavaStringLiteral(irGpuResourcePath) + ";\n"
                + "    /** Inlined backend source text used by simple runtime paths and diagnostics. */\n"
                + "    public static final String KERNEL_SOURCE = " + toJavaStringLiteral(kernelSource) + ";\n"
                + emitReturnValueConvenienceMetadata(returnValueConvenience)
                + "    /** Runtime descriptor consumed by GpuRuntime and reflection-based launcher helpers. */\n"
                + "    public static final net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor KERNEL_DESCRIPTOR =\n"
                + "            new net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor(\n"
                + "                    KERNEL_NAME,\n"
                + "                    KERNEL_RESOURCE,\n"
                + "                    KERNEL_SOURCE,\n"
                + "                    IRGPU_RESOURCE,\n"
                + "                    java.util.List.of(\n"
                + (parameterDescriptors.isEmpty() ? "" : "                    " + parameterDescriptors + "\n")
                + "                    )\n"
                + "            );\n\n"
                + "    /** Optional fallback variant descriptors for the same generated launch ABI. */\n"
                + "    public static final java.util.List<net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor> KERNEL_FALLBACK_DESCRIPTORS =\n"
                + "            java.util.List.of(\n"
                + (fallbackDescriptors.isEmpty() ? "" : "                    " + fallbackDescriptors + "\n")
                + "            );\n\n"
                + "    private " + className + "() {\n"
                + "    }\n\n"
                + "    /** Returns the generated backend source text embedded in this launcher. */\n"
                + "    public static String kernelSource() {\n"
                + "        return KERNEL_SOURCE;\n"
                + "    }\n\n"
                + "    /** Invokes with the generated default launch configuration and the active runtime backend. */\n"
                + "    public static " + returnType + " invoke(" + parameterSignature + ") {\n"
                + emitLauncherInvokeBody(method)
                + "    }\n"
                + emitExplicitWorkSizeLauncher(method, parameterSignature)
                + emitExplicitExecutionConfigLauncher(method, parameterSignature)
                + emitExplicitCompileOptionsLauncher(method, parameterSignature)
                + emitExplicitWorkSizeCompileOptionsLauncher(method, parameterSignature)
                + emitExplicitExecutionConfigCompileOptionsLauncher(method, parameterSignature)
                + emitPreparedLaunchers(method, parameterSignature)
                + emitStandardBackendDeviceLaunchers(method, parameterSignature)
                + emitExplicit3DWorkSizeLauncher(method, parameterSignature)
                + emitReturnValueConvenienceLaunchers(method, returnValueConvenience)
                + "}\n";
    }

    private List<ExecutableElement> fallbackMethodsFor(
            ExecutableElement method,
            List<ExecutableElement> gpuMethods
    ) {
        String groupId = readStringAnnotationValue(
                method,
                GPU_FALLBACK_VARIANT_ANNOTATIONS,
                "group",
                ""
        ).trim();
        if (groupId.isBlank()) {
            return List.of();
        }
        return gpuMethods.stream()
                .filter(candidate -> candidate != method)
                .filter(candidate -> groupId.equals(readStringAnnotationValue(
                        candidate,
                        GPU_FALLBACK_VARIANT_ANNOTATIONS,
                        "group",
                        ""
                ).trim()))
                .sorted(java.util.Comparator
                        .comparingInt((ExecutableElement candidate) -> readIntAnnotationValue(
                                candidate,
                                GPU_FALLBACK_VARIANT_ANNOTATIONS,
                                "priority",
                                0
                        )).reversed()
                        .thenComparing(this::fallbackVariantId)
                        .thenComparing(this::buildResourcePath))
                .toList();
    }

    private boolean validateFallbackGroups(List<ExecutableElement> gpuMethods) {
        LinkedHashMap<String, List<ExecutableElement>> groups = new LinkedHashMap<>();
        boolean valid = true;
        for (ExecutableElement method : gpuMethods) {
            if (!hasAnyAnnotation(method, GPU_FALLBACK_VARIANT_ANNOTATIONS)) {
                continue;
            }
            String groupId = readStringAnnotationValue(
                    method,
                    GPU_FALLBACK_VARIANT_ANNOTATIONS,
                    "group",
                    ""
            ).trim();
            if (groupId.isBlank()) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "@GPUFallbackVariant group must not be blank",
                        method
                );
                valid = false;
                continue;
            }
            groups.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(method);
        }

        for (Map.Entry<String, List<ExecutableElement>> entry : groups.entrySet()) {
            List<ExecutableElement> variants = entry.getValue();
            ExecutableElement reference = variants.get(0);
            LinkedHashMap<String, ExecutableElement> variantIds = new LinkedHashMap<>();
            for (ExecutableElement variant : variants) {
                String variantId = fallbackVariantId(variant);
                ExecutableElement previous = variantIds.putIfAbsent(variantId, variant);
                if (previous != null) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Duplicate @GPUFallbackVariant id '" + variantId + "' in group '" + entry.getKey() + "'",
                            variant
                    );
                    valid = false;
                }
                String mismatch = fallbackAbiMismatch(reference, variant);
                if (!mismatch.isBlank()) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@GPUFallbackVariant group '" + entry.getKey() + "' has incompatible launch ABI: " + mismatch,
                            variant
                    );
                    valid = false;
                }
            }
        }
        return valid;
    }

    private String fallbackAbiMismatch(ExecutableElement reference, ExecutableElement candidate) {
        if (!reference.getReturnType().toString().equals(candidate.getReturnType().toString())) {
            return "return type " + candidate.getReturnType() + " does not match " + reference.getReturnType();
        }
        if (reference.getParameters().size() != candidate.getParameters().size()) {
            return "parameter count " + candidate.getParameters().size()
                    + " does not match " + reference.getParameters().size();
        }
        for (int index = 0; index < reference.getParameters().size(); index++) {
            VariableElement expected = reference.getParameters().get(index);
            VariableElement actual = candidate.getParameters().get(index);
            String expectedType = launcherParameterType(expected.asType());
            String actualType = launcherParameterType(actual.asType());
            if (!expectedType.equals(actualType)) {
                return "parameter " + index + " type " + actualType + " does not match " + expectedType;
            }
            String expectedAccess = resolveParameterAccess(expected);
            String actualAccess = resolveParameterAccess(actual);
            if (!expectedAccess.equals(actualAccess)) {
                return "parameter " + index + " access " + actualAccess + " does not match " + expectedAccess;
            }
        }
        return "";
    }

    private String fallbackVariantId(ExecutableElement method) {
        String variantId = readStringAnnotationValue(
                method,
                GPU_FALLBACK_VARIANT_ANNOTATIONS,
                "id",
                ""
        ).trim();
        return variantId.isBlank() ? method.getSimpleName().toString() : variantId;
    }

    private String toFallbackDescriptorSource(ExecutableElement method) {
        String parameterDescriptors = method.getParameters().stream()
                .map(this::toParameterDescriptorSource)
                .collect(Collectors.joining(", "));
        return "new net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor("
                + toJavaStringLiteral(OpenClKernelNaming.toEntryPointName(method.getSimpleName().toString())) + ", "
                + toJavaStringLiteral(buildResourcePath(method)) + ", "
                + "\"\", "
                + toJavaStringLiteral(buildIrGpuResourcePath(method)) + ", "
                + "java.util.List.of(" + parameterDescriptors + ")"
                + ")";
    }

    private void writeFallbackVariantProviders(List<ExecutableElement> gpuMethods) throws IOException {
        LinkedHashMap<String, List<ExecutableElement>> methodsByProvider = new LinkedHashMap<>();
        for (ExecutableElement method : gpuMethods) {
            if (!hasAnyAnnotation(method, GPU_FALLBACK_VARIANT_ANNOTATIONS)) {
                continue;
            }
            String qualifiedName = buildFallbackProviderQualifiedName(method);
            methodsByProvider.computeIfAbsent(qualifiedName, ignored -> new ArrayList<>()).add(method);
        }
        if (methodsByProvider.isEmpty()) {
            return;
        }

        ArrayList<String> providerNames = new ArrayList<>();
        for (Map.Entry<String, List<ExecutableElement>> entry : methodsByProvider.entrySet()) {
            String qualifiedName = entry.getKey();
            if (!writtenFallbackVariantProviders.add(qualifiedName)) {
                providerNames.add(qualifiedName);
                continue;
            }
            List<ExecutableElement> methods = entry.getValue().stream()
                    .sorted(java.util.Comparator
                            .comparing((ExecutableElement method) -> readStringAnnotationValue(
                                    method,
                                    GPU_FALLBACK_VARIANT_ANNOTATIONS,
                                    "group",
                                    ""
                            ))
                            .thenComparing(this::fallbackVariantId)
                            .thenComparing(this::buildResourcePath))
                    .toList();
            FileObject sourceFile = processingEnv.getFiler().createSourceFile(
                    qualifiedName,
                    methods.toArray(Element[]::new)
            );
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(buildFallbackProviderSource(qualifiedName, methods));
            }
            providerNames.add(qualifiedName);
        }

        if (!fallbackVariantServiceWritten) {
            fallbackVariantServiceWritten = true;
            FileObject serviceFile = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    FALLBACK_VARIANT_SERVICE_PATH,
                    gpuMethods.toArray(Element[]::new)
            );
            try (Writer writer = serviceFile.openWriter()) {
                providerNames.stream().sorted().forEach(providerName -> {
                    try {
                        writer.write(providerName);
                        writer.write('\n');
                    } catch (IOException exception) {
                        throw new java.io.UncheckedIOException(exception);
                    }
                });
            } catch (java.io.UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }

    private String buildFallbackProviderQualifiedName(ExecutableElement method) {
        return buildLauncherPackageName(method) + "." + buildFallbackProviderClassName(method);
    }

    private String buildFallbackProviderClassName(ExecutableElement method) {
        List<String> ownerNames = collectOwnerNames(method);
        ownerNames.add("GpuFallbackVariantProvider");
        return String.join("_", ownerNames);
    }

    private String buildFallbackProviderSource(
            String qualifiedName,
            List<ExecutableElement> methods
    ) {
        int separator = qualifiedName.lastIndexOf('.');
        String packageName = qualifiedName.substring(0, separator);
        String className = qualifiedName.substring(separator + 1);
        TypeElement owner = (TypeElement) methods.get(0).getEnclosingElement();
        String registrations = methods.stream()
                .map(method -> "new net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantRegistration("
                        + toJavaStringLiteral(readStringAnnotationValue(
                        method,
                        GPU_FALLBACK_VARIANT_ANNOTATIONS,
                        "group",
                        ""
                ).trim()) + ", "
                        + toJavaStringLiteral(fallbackVariantId(method)) + ", "
                        + toFallbackDescriptorSource(method)
                        + ")")
                .collect(Collectors.joining(",\n                    "));
        return "package " + packageName + ";\n\n"
                + "public final class " + className
                + " implements net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantProvider {\n"
                + "    @Override\n"
                + "    public String providerId() {\n"
                + "        return " + toJavaStringLiteral("generated:" + owner.getQualifiedName()) + ";\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public java.util.List<net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantRegistration> variants() {\n"
                + "        return java.util.List.of(\n"
                + (registrations.isEmpty() ? "" : "                    " + registrations + "\n")
                + "        );\n"
                + "    }\n"
                + "}\n";
    }

    private String emitExplicitWorkSizeLauncher(ExecutableElement method, String parameterSignature) {
        if (!"void".equals(method.getReturnType().toString())) {
            return "";
        }

        String signature = parameterSignature.isEmpty()
                ? "long globalWorkSize"
                : "long globalWorkSize, " + parameterSignature;
        return "\n"
                + "    /** Invokes with an explicit 1D global work size and the active runtime backend. */\n"
                + "    public static void invokeWithGlobalWorkSize(" + signature + ") {\n"
                + emitLauncherInvokeBodyWithExplicitWorkSize(method)
                + "    }\n";
    }

    private String emitExplicitExecutionConfigLauncher(ExecutableElement method, String parameterSignature) {
        if (!"void".equals(method.getReturnType().toString())) {
            return "";
        }

        String signature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, " + parameterSignature;
        return "\n"
                + "    /** Invokes with an explicit cross-backend execution configuration. */\n"
                + "    public static void invokeWithConfig(" + signature + ") {\n"
                + emitLauncherInvokeBodyWithExecutionConfig(method)
                + "    }\n";
    }

    private String emitExplicit3DWorkSizeLauncher(ExecutableElement method, String parameterSignature) {
        if (!"void".equals(method.getReturnType().toString())) {
            return "";
        }

        String signature = parameterSignature.isEmpty()
                ? "long globalX, long globalY, long globalZ"
                : "long globalX, long globalY, long globalZ, " + parameterSignature;
        return "\n"
                + "    /** Invokes with an explicit 3D global shape and backend-selected local sizing. */\n"
                + "    public static void invokeWith3DWorkSize(" + signature + ") {\n"
                + "        invokeWithConfig(net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.threeDimensional(globalX, globalY, globalZ)"
                + (method.getParameters().isEmpty() ? "" : ", " + method.getParameters().stream()
                        .map(parameter -> parameter.getSimpleName().toString())
                        .collect(Collectors.joining(", ")))
                + ");\n"
                + "    }\n";
    }

    private String emitExplicitCompileOptionsLauncher(ExecutableElement method, String parameterSignature) {
        if (!"void".equals(method.getReturnType().toString())) {
            return "";
        }

        String signature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        return "\n"
                + "    /** Invokes with explicit compile/runtime options such as artifact dumping or optimizer policy. */\n"
                + "    public static void invokeWithCompileOptions(" + signature + ") {\n"
                + emitLauncherInvokeBodyWithCompileOptions(method)
                + "    }\n";
    }

    private String emitExplicitWorkSizeCompileOptionsLauncher(ExecutableElement method, String parameterSignature) {
        if (!"void".equals(method.getReturnType().toString())) {
            return "";
        }

        String signature = parameterSignature.isEmpty()
                ? "long globalWorkSize, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "long globalWorkSize, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        return "\n"
                + "    /** Invokes with explicit 1D work size plus compile/runtime options. */\n"
                + "    public static void invokeWithGlobalWorkSizeAndCompileOptions(" + signature + ") {\n"
                + emitLauncherInvokeBodyWithExplicitWorkSizeAndCompileOptions(method)
                + "    }\n";
    }

    private String emitExplicitExecutionConfigCompileOptionsLauncher(ExecutableElement method, String parameterSignature) {
        if (!"void".equals(method.getReturnType().toString())) {
            return "";
        }

        String signature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        return "\n"
                + "    /** Invokes with explicit execution configuration plus compile/runtime options. */\n"
                + "    public static void invokeWithConfigAndCompileOptions(" + signature + ") {\n"
                + emitLauncherInvokeBodyWithExecutionConfigAndCompileOptions(method)
                + "    }\n";
    }

    private String emitPreparedLaunchers(ExecutableElement method, String parameterSignature) {
        if (!"void".equals(method.getReturnType().toString())) {
            return "";
        }

        String configSignature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, " + parameterSignature;
        String compileOptionsSignature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        String configCompileOptionsSignature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;

        return "\n"
                + "    /** Prepares this generated launcher for repeated hot-loop calls. */\n"
                + "    public static net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher prepare(" + parameterSignature + ") {\n"
                + emitLauncherPrepareBody(method, "null", "null")
                + "    }\n\n"
                + "    /** Prepares this generated launcher with an explicit default execution configuration. */\n"
                + "    public static net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher prepareWithConfig(" + configSignature + ") {\n"
                + emitLauncherPrepareBody(method, "executionConfig", "null")
                + "    }\n\n"
                + "    /** Prepares this generated launcher with runtime compile options. */\n"
                + "    public static net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher prepareWithCompileOptions(" + compileOptionsSignature + ") {\n"
                + emitLauncherPrepareBody(method, "null", "compileOptions")
                + "    }\n\n"
                + "    /** Prepares this generated launcher with an explicit execution configuration and compile options. */\n"
                + "    public static net.sixik.ga_utils.javatogpu.api.GpuPreparedLauncher prepareWithConfigAndCompileOptions(" + configCompileOptionsSignature + ") {\n"
                + emitLauncherPrepareBody(method, "executionConfig", "compileOptions")
                + "    }\n";
    }

    private String emitStandardBackendDeviceLaunchers(ExecutableElement method, String parameterSignature) {
        if (!"void".equals(method.getReturnType().toString())) {
            return "";
        }

        String compileOptionsSignature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        String globalSignature = parameterSignature.isEmpty()
                ? "long globalWorkSize, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "long globalWorkSize, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        String global3DSignature = parameterSignature.isEmpty()
                ? "long globalX, long globalY, long globalZ, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "long globalX, long globalY, long globalZ, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        String configSignature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        String arguments = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        String argumentSuffix = arguments.isEmpty() ? "" : ", " + arguments;

        return "\n"
                + "    /** Selects a standard backend/device for this call, then invokes with compile options. */\n"
                + "    public static void invokeWithStandardBackendAndDevice(" + compileOptionsSignature + ") {\n"
                + "        try (net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope ignored = net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.useStandardBackendAndDevice(compileOptions)) {\n"
                + "            invokeWithCompileOptions(compileOptions" + argumentSuffix + ");\n"
                + "        }\n"
                + "    }\n\n"
                + "    /** Selects a standard backend/device for this call and uses an explicit 1D global size. */\n"
                + "    public static void invokeWithGlobalWorkSizeAndStandardBackendAndDevice(" + globalSignature + ") {\n"
                + "        try (net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope ignored = net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.useStandardBackendAndDevice(compileOptions)) {\n"
                + "            invokeWithGlobalWorkSizeAndCompileOptions(globalWorkSize, compileOptions" + argumentSuffix + ");\n"
                + "        }\n"
                + "    }\n\n"
                + "    /** Selects a standard backend/device for this call and uses an explicit 3D global shape. */\n"
                + "    public static void invokeWith3DWorkSizeAndStandardBackendAndDevice(" + global3DSignature + ") {\n"
                + "        invokeWithConfigAndStandardBackendAndDevice(net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.threeDimensional(globalX, globalY, globalZ), compileOptions" + argumentSuffix + ");\n"
                + "    }\n\n"
                + "    /** Selects a standard backend/device for this call and uses an explicit execution configuration. */\n"
                + "    public static void invokeWithConfigAndStandardBackendAndDevice(" + configSignature + ") {\n"
                + "        try (net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope ignored = net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.useStandardBackendAndDevice(compileOptions)) {\n"
                + "            invokeWithConfigAndCompileOptions(executionConfig, compileOptions" + argumentSuffix + ");\n"
                + "        }\n"
                + "    }\n";
    }

    private String emitReturnValueConvenienceMetadata(ReturnValueConvenienceAnalysis analysis) {
        ReturnValueOutputParameter output = analysis.output();
        return "    /** Metadata describing whether generated return-value convenience helpers are available. */\n"
                + "    public static final boolean RETURN_VALUE_CONVENIENCE_AVAILABLE = " + analysis.available() + ";\n"
                + "    public static final String RETURN_VALUE_CONVENIENCE_STATUS = " + toJavaStringLiteral(analysis.status()) + ";\n"
                + "    public static final String RETURN_VALUE_CONVENIENCE_REASON = " + toJavaStringLiteral(analysis.reason()) + ";\n"
                + "    public static final String RETURN_VALUE_CONVENIENCE_OUTPUT_PARAMETER = "
                + toJavaStringLiteral(output == null ? "" : output.parameter().getSimpleName().toString()) + ";\n"
                + "    public static final String RETURN_VALUE_CONVENIENCE_OUTPUT_TYPE = "
                + toJavaStringLiteral(output == null ? "" : output.arrayType()) + ";\n"
                + "    public static final String RETURN_VALUE_CONVENIENCE_RETURN_TYPE = "
                + toJavaStringLiteral(output == null ? "" : output.componentType()) + ";\n";
    }

    private String emitReturnValueConvenienceLaunchers(
            ExecutableElement method,
            ReturnValueConvenienceAnalysis analysis
    ) {
        if (!analysis.available()) {
            return "";
        }
        ReturnValueOutputParameter output = analysis.output();

        List<? extends VariableElement> parameters = method.getParameters();
        List<? extends VariableElement> parametersWithoutOutput = parameters.stream()
                .filter(parameter -> parameter != output.parameter())
                .toList();
        String parameterSignature = parametersWithoutOutput.stream()
                .map(this::toParameterDeclaration)
                .collect(Collectors.joining(", "));
        String argumentList = parametersWithoutOutput.stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        String outputLocal = uniqueGeneratedLocalName(method, "__javatogpu$returnOutput");
        String invokeArguments = parameters.stream()
                .map(parameter -> parameter == output.parameter() ? outputLocal : parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        String returnType = output.componentType();
        String outputArrayType = returnType + "[]";

        String noOutputArguments = argumentList.isEmpty() ? "" : ", " + argumentList;
        String noOutputSignature = parameterSignature.isEmpty() ? "" : parameterSignature;
        String globalSignature = parameterSignature.isEmpty()
                ? "long globalWorkSize"
                : "long globalWorkSize, " + parameterSignature;
        String configSignature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, " + parameterSignature;
        String compileOptionsSignature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        String globalCompileOptionsSignature = parameterSignature.isEmpty()
                ? "long globalWorkSize, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "long globalWorkSize, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;
        String configCompileOptionsSignature = parameterSignature.isEmpty()
                ? "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions"
                : "net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig, net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions compileOptions, " + parameterSignature;

        return "\n"
                + "    /** Allocates the generated output array, invokes one work item, and returns output[0]. */\n"
                + "    public static " + returnType + " invokeReturningFirst(" + noOutputSignature + ") {\n"
                + "        return invokeReturningFirst(1L" + noOutputArguments + ");\n"
                + "    }\n\n"
                + "    /** Allocates the generated output array for the explicit 1D work size and returns output[0]. */\n"
                + "    public static " + returnType + " invokeReturningFirst(" + globalSignature + ") {\n"
                + "        return invokeReturningFirstWithConfig(net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(globalWorkSize)" + noOutputArguments + ");\n"
                + "    }\n\n"
                + "    /** Allocates the generated output array from the execution config and returns output[0]. */\n"
                + "    public static " + returnType + " invokeReturningFirstWithConfig(" + configSignature + ") {\n"
                + "        " + outputArrayType + " " + outputLocal + " = new " + returnType + "[__javatogpu$returnOutputLength(executionConfig)];\n"
                + "        invokeWithConfig(executionConfig" + (invokeArguments.isEmpty() ? "" : ", " + invokeArguments) + ");\n"
                + "        return " + outputLocal + "[0];\n"
                + "    }\n\n"
                + "    /** Return-value convenience helper with explicit compile/runtime options. */\n"
                + "    public static " + returnType + " invokeReturningFirstWithCompileOptions(" + compileOptionsSignature + ") {\n"
                + "        return invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(1L, compileOptions" + noOutputArguments + ");\n"
                + "    }\n\n"
                + "    /** Return-value convenience helper with explicit 1D work size and compile/runtime options. */\n"
                + "    public static " + returnType + " invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(" + globalCompileOptionsSignature + ") {\n"
                + "        return invokeReturningFirstWithConfigAndCompileOptions(net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(globalWorkSize), compileOptions" + noOutputArguments + ");\n"
                + "    }\n\n"
                + "    /** Return-value convenience helper with explicit execution config and compile/runtime options. */\n"
                + "    public static " + returnType + " invokeReturningFirstWithConfigAndCompileOptions(" + configCompileOptionsSignature + ") {\n"
                + "        " + outputArrayType + " " + outputLocal + " = new " + returnType + "[__javatogpu$returnOutputLength(executionConfig)];\n"
                + "        invokeWithConfigAndCompileOptions(executionConfig, compileOptions" + (invokeArguments.isEmpty() ? "" : ", " + invokeArguments) + ");\n"
                + "        return " + outputLocal + "[0];\n"
                + "    }\n\n"
                + "    /** Selects a standard backend/device for this return-value convenience call. */\n"
                + "    public static " + returnType + " invokeReturningFirstWithStandardBackendAndDevice(" + compileOptionsSignature + ") {\n"
                + "        try (net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope ignored = net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.useStandardBackendAndDevice(compileOptions)) {\n"
                + "            return invokeReturningFirstWithCompileOptions(compileOptions" + noOutputArguments + ");\n"
                + "        }\n"
                + "    }\n\n"
                + "    /** Selects a standard backend/device and uses an explicit 1D work size for this convenience call. */\n"
                + "    public static " + returnType + " invokeReturningFirstWithGlobalWorkSizeAndStandardBackendAndDevice(" + globalCompileOptionsSignature + ") {\n"
                + "        try (net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope ignored = net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.useStandardBackendAndDevice(compileOptions)) {\n"
                + "            return invokeReturningFirstWithGlobalWorkSizeAndCompileOptions(globalWorkSize, compileOptions" + noOutputArguments + ");\n"
                + "        }\n"
                + "    }\n\n"
                + "    /** Selects a standard backend/device and uses an explicit execution config for this convenience call. */\n"
                + "    public static " + returnType + " invokeReturningFirstWithConfigAndStandardBackendAndDevice(" + configCompileOptionsSignature + ") {\n"
                + "        try (net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope ignored = net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.useStandardBackendAndDevice(compileOptions)) {\n"
                + "            return invokeReturningFirstWithConfigAndCompileOptions(executionConfig, compileOptions" + noOutputArguments + ");\n"
                + "        }\n"
                + "    }\n\n"
                + "    /** Validates the generated output-buffer length for return-value convenience helpers. */\n"
                + "    private static int __javatogpu$returnOutputLength(net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig executionConfig) {\n"
                + "        java.util.Objects.requireNonNull(executionConfig, \"executionConfig\");\n"
                + "        long itemCount = executionConfig.globalItemCount();\n"
                + "        if (itemCount <= 0L || itemCount > Integer.MAX_VALUE) {\n"
                + "            throw new IllegalArgumentException(\"Return-value convenience output length must be between 1 and Integer.MAX_VALUE: \" + itemCount);\n"
                + "        }\n"
                + "        return (int) itemCount;\n"
                + "    }\n";
    }

    private ReturnValueConvenienceAnalysis returnValueConvenienceAnalysis(ExecutableElement method) {
        if (!"void".equals(method.getReturnType().toString())) {
            return ReturnValueConvenienceAnalysis.unavailable("method-return-type-not-void", true);
        }

        ArrayList<ReturnValueOutputParameter> readWriteArrays = new ArrayList<>();
        for (VariableElement parameter : method.getParameters()) {
            if (!"READ_WRITE".equals(resolveParameterAccess(parameter)) || parameter.asType().getKind() != TypeKind.ARRAY) {
                continue;
            }
            TypeMirror componentType = ((ArrayType) parameter.asType()).getComponentType();
            readWriteArrays.add(new ReturnValueOutputParameter(
                    parameter,
                    parameter.asType().toString(),
                    componentType.toString(),
                    isSupportedReturnValueOutputComponent(componentType)
            ));
        }

        if (readWriteArrays.isEmpty()) {
            return ReturnValueConvenienceAnalysis.unavailable("no-read-write-output-array", false);
        }
        if (readWriteArrays.size() > 1) {
            return ReturnValueConvenienceAnalysis.unavailable(
                    "multiple-read-write-output-arrays",
                    readWriteArrays.stream().allMatch(output -> looksLikeOutputParameter(output.parameter()))
            );
        }

        ReturnValueOutputParameter output = readWriteArrays.get(0);
        if (!output.supportedPrimitive()) {
            return ReturnValueConvenienceAnalysis.unavailable("output-array-component-not-supported", true);
        }

        return ReturnValueConvenienceAnalysis.available(output);
    }

    private void emitReturnValueConvenienceDiagnostic(ExecutableElement method) {
        if (!returnValueConvenienceDiagnosticsEnabled()) {
            return;
        }
        ReturnValueConvenienceAnalysis analysis = returnValueConvenienceAnalysis(method);
        if (analysis.available() || !analysis.shouldReportDiagnostic()) {
            return;
        }
        String diagnosticKey = buildLauncherPackageName(method) + "." + buildLauncherClassName(method);
        if (!reportedReturnValueConvenienceDiagnostics.add(diagnosticKey)) {
            return;
        }
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "Return-first launcher helper was not generated for "
                        + method.getEnclosingElement()
                        + "#"
                        + method.getSimpleName()
                        + ": "
                        + analysis.reason()
                        + ". Keep exactly one primitive @GPUGlobal read-write output array, or use explicit output-buffer launchers. "
                        + "Runtime code can inspect this via GpuGeneratedLauncherInvoker.returnValueConvenience(...).",
                method
        );
    }

    private boolean looksLikeOutputParameter(VariableElement parameter) {
        String name = parameter.getSimpleName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.equals("out")
                || name.equals("output")
                || name.startsWith("out")
                || name.endsWith("out")
                || name.startsWith("output")
                || name.endsWith("output");
    }

    private boolean isSupportedReturnValueOutputComponent(TypeMirror componentType) {
        return switch (componentType.toString()) {
            case "byte", "short", "int", "long", "float", "double", "char" -> true;
            default -> false;
        };
    }

    private String uniqueGeneratedLocalName(ExecutableElement method, String baseName) {
        Set<String> parameterNames = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.toSet());
        String candidate = baseName;
        int suffix = 0;
        while (parameterNames.contains(candidate)) {
            suffix++;
            candidate = baseName + suffix;
        }
        return candidate;
    }

    private record ReturnValueConvenienceAnalysis(
            boolean available,
            String reason,
            boolean reportDiagnostic,
            ReturnValueOutputParameter output
    ) {

        private static ReturnValueConvenienceAnalysis available(ReturnValueOutputParameter output) {
            return new ReturnValueConvenienceAnalysis(true, "single-primitive-output-array", false, output);
        }

        private static ReturnValueConvenienceAnalysis unavailable(String reason, boolean reportDiagnostic) {
            return new ReturnValueConvenienceAnalysis(false, reason, reportDiagnostic, null);
        }

        private String status() {
            return available ? "available" : "unavailable";
        }

        private boolean shouldReportDiagnostic() {
            return reportDiagnostic;
        }
    }

    private record ReturnValueOutputParameter(
            VariableElement parameter,
            String arrayType,
            String componentType,
            boolean supportedPrimitive
    ) {
    }

    private String emitLauncherInvokeBody(ExecutableElement method) {
        String arguments = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));

        if ("void".equals(method.getReturnType().toString())) {
            return "        net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.invokeVariantsFromGeneratedLauncher("
                    + buildLauncherClassName(method)
                    + ".class, null, null, KERNEL_DESCRIPTOR, KERNEL_FALLBACK_DESCRIPTORS"
                    + (arguments.isEmpty() ? "" : ", " + arguments)
                    + ");\n";
        }

        return "        throw new UnsupportedOperationException(\"Non-void GPU launchers are not implemented yet\");\n";
    }

    private String emitLauncherInvokeBodyWithExplicitWorkSize(ExecutableElement method) {
        String arguments = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        return "        net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.invokeVariantsFromGeneratedLauncher("
                + buildLauncherClassName(method)
                + ".class, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(globalWorkSize), null, KERNEL_DESCRIPTOR, KERNEL_FALLBACK_DESCRIPTORS"
                + (arguments.isEmpty() ? "" : ", " + arguments)
                + ");\n";
    }

    private String emitLauncherInvokeBodyWithExecutionConfig(ExecutableElement method) {
        String arguments = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        return "        net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.invokeVariantsFromGeneratedLauncher("
                + buildLauncherClassName(method)
                + ".class, executionConfig, null, KERNEL_DESCRIPTOR, KERNEL_FALLBACK_DESCRIPTORS"
                + (arguments.isEmpty() ? "" : ", " + arguments)
                + ");\n";
    }

    private String emitLauncherInvokeBodyWithCompileOptions(ExecutableElement method) {
        String arguments = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        return "        net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.invokeVariantsFromGeneratedLauncher("
                + buildLauncherClassName(method)
                + ".class, null, compileOptions, KERNEL_DESCRIPTOR, KERNEL_FALLBACK_DESCRIPTORS"
                + (arguments.isEmpty() ? "" : ", " + arguments)
                + ");\n";
    }

    private String emitLauncherInvokeBodyWithExplicitWorkSizeAndCompileOptions(ExecutableElement method) {
        String arguments = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        return "        net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.invokeVariantsFromGeneratedLauncher("
                + buildLauncherClassName(method)
                + ".class, net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig.oneDimensional(globalWorkSize), compileOptions, KERNEL_DESCRIPTOR, KERNEL_FALLBACK_DESCRIPTORS"
                + (arguments.isEmpty() ? "" : ", " + arguments)
                + ");\n";
    }

    private String emitLauncherInvokeBodyWithExecutionConfigAndCompileOptions(ExecutableElement method) {
        String arguments = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        return "        net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.invokeVariantsFromGeneratedLauncher("
                + buildLauncherClassName(method)
                + ".class, executionConfig, compileOptions, KERNEL_DESCRIPTOR, KERNEL_FALLBACK_DESCRIPTORS"
                + (arguments.isEmpty() ? "" : ", " + arguments)
                + ");\n";
    }

    private String emitLauncherPrepareBody(
            ExecutableElement method,
            String executionConfigExpression,
            String compileOptionsExpression
    ) {
        String arguments = method.getParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        return "        return net.sixik.ga_utils.javatogpu.runtime.GpuRuntime.prepareVariantsFromGeneratedLauncher("
                + buildLauncherClassName(method)
                + ".class, "
                + executionConfigExpression
                + ", "
                + compileOptionsExpression
                + ", KERNEL_DESCRIPTOR, KERNEL_FALLBACK_DESCRIPTORS"
                + (arguments.isEmpty() ? "" : ", " + arguments)
                + ");\n";
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
        return GpuFrontendResourcePaths.openClResource(
                enclosingType.getQualifiedName().toString(),
                enclosingType.getSimpleName().toString(),
                method.getSimpleName().toString()
        );
    }

    private String buildIrGpuResourcePath(ExecutableElement method) {
        TypeElement enclosingType = (TypeElement) method.getEnclosingElement();
        return GpuFrontendResourcePaths.irGpuResource(
                enclosingType.getQualifiedName().toString(),
                enclosingType.getSimpleName().toString(),
                method.getSimpleName().toString()
        );
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

    private boolean returnValueConvenienceDiagnosticsEnabled() {
        String value = processingEnv.getOptions().getOrDefault("javatogpu.returnValueConvenienceDiagnostics", "summary");
        if (value == null) {
            return true;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "quiet", "off", "false", "none" -> false;
            default -> true;
        };
    }

    private GpuIrValidationMode irValidationMode() {
        return GpuIrValidationMode.parse(processingEnv.getOptions().get("javatogpu.irValidation"));
    }

    private GpuIrValidationDiagnosticPolicy irValidationDiagnosticPolicy() {
        return GpuIrValidationDiagnosticPolicy.parse(processingEnv.getOptions().get("javatogpu.irValidationDiagnostics"));
    }

    private String irValidationReportPath() {
        String reportPath = processingEnv.getOptions().get("javatogpu.irValidationReport");
        if (reportPath == null || reportPath.isBlank()) {
            return null;
        }
        String normalized = reportPath.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":") || normalized.contains("..")) {
            throw new IllegalArgumentException(
                    "javatogpu.irValidationReport must be a relative generated-source resource path"
            );
        }
        return normalized;
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
