package net.sixik.ga_utils.javatogpu.frontend.diagnostics;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmFrontendException;
import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts ASM frontend failures into the source-snippet diagnostic model used by JavaToGpu.
 */
public final class AsmFrontendDiagnosticAdapter {

    public static final String ASM_FRONTEND_ERROR_CODE = "JTG-ASM-001";

    private final GpuDiagnosticRenderer renderer;

    public AsmFrontendDiagnosticAdapter() {
        this(new GpuDiagnosticRenderer());
    }

    public AsmFrontendDiagnosticAdapter(GpuDiagnosticRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public Optional<GpuSourceDiagnostic> toDiagnostic(
            AsmGpuMethod method,
            AsmFrontendException exception,
            String sourceName
    ) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(exception, "exception");

        MethodDeclaration declaration = method.parsedMethod().declaration();
        if (declaration == null || declaration.getRange().isEmpty()) {
            return Optional.empty();
        }

        Range range = declaration.getRange().orElseThrow();
        GpuSourceSpan methodSpan = new GpuSourceSpan(
                normalizedSourceName(sourceName, method),
                range.begin.line,
                range.begin.column,
                range.end.line,
                range.end.column
        );

        return Optional.of(GpuSourceDiagnostic.error(
                ASM_FRONTEND_ERROR_CODE,
                "ASM frontend cannot lower this method to GPU-safe IR",
                methodSpan,
                "method contains bytecode outside the supported GPU subset",
                List.of(exception.getMessage()),
                artifactFields(exception)
        ));
    }

    public Optional<String> render(
            AsmGpuMethod method,
            AsmFrontendException exception,
            String sourceName,
            List<String> sourceLines
    ) {
        Objects.requireNonNull(sourceLines, "sourceLines");
        return toDiagnostic(method, exception, sourceName)
                .map(diagnostic -> renderer.render(diagnostic, sourceLines));
    }

    private String normalizedSourceName(String sourceName, AsmGpuMethod method) {
        if (sourceName != null && !sourceName.isBlank()) {
            return sourceName;
        }
        String qualifiedName = method.parsedMethod().ownerQualifiedName();
        if (qualifiedName != null && !qualifiedName.isBlank()) {
            return qualifiedName.replace('.', '/') + ".java";
        }
        String simpleName = method.parsedMethod().ownerSimpleName();
        if (simpleName != null && !simpleName.isBlank()) {
            return simpleName + ".java";
        }
        return method.ownerInternalName() + ".java";
    }

    private Map<String, String> artifactFields(AsmFrontendException exception) {
        return exception.metadata()
                .map(metadata -> metadata.artifactFields("asmFailure"))
                .orElseGet(Map::of);
    }
}
