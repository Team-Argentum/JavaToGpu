package net.sixik.ga_utils.javatogpu.processors;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
import net.sixik.ga_utils.javatogpu.api.anotations.GPU;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.frontend.GpuFrontendService;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelNaming;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "net.sixik.ga_utils.javatogpu.api.anotations.GPU",
        "net.sixik.ga_utils.javatogpu.api.anotations.CCode"
})
public final class GpuCompilerProcessor extends AbstractProcessor {

    private final Set<String> writtenResources = new HashSet<>();
    private final Set<String> writtenLaunchers = new HashSet<>();

    private Trees trees;
    private GpuFrontendService frontendService;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(processingEnv);
        frontendService = GpuFrontendService.createDefault();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(GPU.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@GPU can only be used on methods", element);
                continue;
            }

            ExecutableElement method = (ExecutableElement) element;
            try {
                ParsedGpuMethod kernelMethod = parseMethod(method);
                List<ParsedGpuMethod> helpers = collectHelpers(roundEnv, method);
                String kernelSource = frontendService.validateLowerAndEmit(kernelMethod, helpers);
                writeKernelResource(method, kernelSource);
                writeLauncherSource(method, kernelSource);
            } catch (RuntimeException | IOException exception) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to compile @GPU method: " + exception.getMessage(),
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
                .filter(candidate -> candidate.getAnnotation(CCode.class) != null)
                .filter(candidate -> !candidate.equals(method))
                .map(this::extractMethodSource)
                .toList();
    }

    private ParsedGpuMethod parseMethod(ExecutableElement method) {
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        return new net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser().parseMethod(
                extractMethodSource(method),
                owner.getSimpleName().toString(),
                owner.getQualifiedName().toString()
        );
    }

    private List<ParsedGpuMethod> collectHelpers(RoundEnvironment roundEnv, ExecutableElement kernelMethod) {
        return roundEnv.getElementsAnnotatedWith(CCode.class).stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(candidate -> !candidate.equals(kernelMethod))
                .map(this::parseMethod)
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
        return parameter.asType() + " " + parameter.getSimpleName();
    }

    private String toParameterDescriptorSource(VariableElement parameter) {
        return "new net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterDescriptor("
                + toJavaStringLiteral(parameter.getSimpleName().toString()) + ", "
                + toJavaStringLiteral(parameter.asType().toString()) + ", "
                + "net.sixik.ga_utils.javatogpu.runtime.GpuKernelParameterAccess." + resolveParameterAccess(parameter)
                + ")";
    }

    private String resolveParameterAccess(VariableElement parameter) {
        GPUGlobal global = parameter.getAnnotation(GPUGlobal.class);
        if (global != null) {
            return global.constant() ? "READ_ONLY" : "READ_WRITE";
        }
        return "VALUE";
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
}
