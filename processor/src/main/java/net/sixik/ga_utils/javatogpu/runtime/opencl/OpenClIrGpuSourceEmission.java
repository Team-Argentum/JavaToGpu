package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Analyses whether serialized IrGpu method bodies can already be emitted as OpenCL source.
 */
public record OpenClIrGpuSourceEmission(
        boolean sourceGenerated,
        String source,
        List<String> blockers,
        List<String> diagnostics
) {

    public OpenClIrGpuSourceEmission {
        source = source == null ? "" : source;
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static OpenClIrGpuSourceEmission inspect(IrGpuArtifact artifact) {
        if (artifact == null) {
            return blocked(
                    List.of("irgpu-artifact-missing"),
                    List.of("OpenCL source emission skipped because no IrGpu artifact was available")
            );
        }

        List<IrGpuMethodBody> methodBodies = artifact.module().methodBodies();
        ArrayList<String> blockers = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        if (methodBodies.isEmpty()) {
            blockers.add("irgpu-method-bodies-missing");
            diagnostics.add("IrGpu artifact has no serialized method bodies to emit");
            return blocked(blockers, diagnostics);
        }

        long entryBodyCount = methodBodies.stream()
                .filter(methodBody -> "entry".equals(methodBody.role()))
                .count();
        if (entryBodyCount == 0) {
            blockers.add("irgpu-entry-body-missing");
        }
        if (entryBodyCount > 1) {
            blockers.add("irgpu-entry-body-ambiguous");
        }

        String emittedEntryBody = "";
        IrGpuMethodBody entryBody = null;
        LinkedHashMap<String, String> emittedHelperBodies = new LinkedHashMap<>();
        for (IrGpuMethodBody methodBody : methodBodies) {
            MethodBodyInspection inspection = inspectMethodBody(methodBody, blockers, diagnostics);
            if ("entry".equals(methodBody.role())) {
                entryBody = methodBody;
                emittedEntryBody = inspection.emittedBody().orElse("");
            }
            if ("helper".equals(methodBody.role())) {
                inspection.emittedBody().ifPresent(body -> emittedHelperBodies.put(methodBody.emittedName(), body));
            }
        }

        diagnostics.add("IrGpu method bodies are serialized as ir-text-v1 snapshots");
        OpenClIrGpuSourceAssemblyResult assemblyResult = OpenClIrGpuSourceAssembler.INSTANCE.assemble(
                artifact,
                entryBody,
                emittedEntryBody,
                emittedHelperBodies
        );
        if (assemblyResult.assembled()) {
            diagnostics.addAll(assemblyResult.diagnostics());
            diagnostics.add("OpenCL source assembler produced a diagnostic IrGpu-derived source payload");
            return new OpenClIrGpuSourceEmission(true, assemblyResult.source(), blockers, diagnostics);
        }
        assemblyResult.blockers().forEach(blocker -> add(blockers, blocker));
        diagnostics.addAll(assemblyResult.diagnostics());
        diagnostics.add("OpenCL source assembly is blocked by missing IrGpu metadata or unsupported ir-text-v1 features");
        return blocked(blockers, diagnostics);
    }

    private static MethodBodyInspection inspectMethodBody(
            IrGpuMethodBody methodBody,
            List<String> blockers,
            List<String> diagnostics
    ) {
        String role = methodBody.role().isBlank() ? "unknown" : methodBody.role();
        String emittedName = methodBody.emittedName().isBlank() ? methodBody.name() : methodBody.emittedName();
        String prefix = "irgpu-" + role + "-" + emittedName + "-";
        if (!"ir-text-v1".equals(methodBody.format())) {
            add(blockers, prefix + "format-unsupported-" + methodBody.format());
        }
        if (methodBody.body().isBlank()) {
            add(blockers, prefix + "body-empty");
            return MethodBodyInspection.empty();
        }
        OpenClIrTextBodyParseResult parseResult = OpenClIrTextBodyParser.INSTANCE.parse(methodBody.body());
        String emittedBody = "";
        if (parseResult.parsed()) {
            diagnostics.add(prefix + "parsed.statement.count=" + parseResult.statements().size());
            OpenClIrTextBodyEmissionResult emissionResult = OpenClIrTextBodyEmitter.INSTANCE.emit(parseResult);
            if (emissionResult.emitted()) {
                emittedBody = emissionResult.body();
                diagnostics.add(prefix + "emitted.body.length=" + emissionResult.body().length());
            } else {
                add(blockers, prefix + "opencl-body-emission-failed");
                emissionResult.blockers().forEach(blocker -> add(blockers, prefix + blocker));
            }
            diagnostics.addAll(emissionResult.diagnostics());
        } else {
            add(blockers, prefix + "ir-text-parse-failed");
            parseResult.blockers().forEach(blocker -> add(blockers, prefix + blocker));
        }
        diagnostics.addAll(parseResult.diagnostics());
        return new MethodBodyInspection(Optional.ofNullable(emittedBody).filter(value -> !value.isBlank()));
    }

    private static OpenClIrGpuSourceEmission blocked(List<String> blockers, List<String> diagnostics) {
        return new OpenClIrGpuSourceEmission(false, "", blockers, diagnostics);
    }

    private static void add(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private record MethodBodyInspection(Optional<String> emittedBody) {

        private static MethodBodyInspection empty() {
            return new MethodBodyInspection(Optional.empty());
        }
    }
}
