package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelEmitter;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.lowering.GpuIrLowerer;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser;
import net.sixik.ga_utils.javatogpu.frontend.validation.GpuSubsetValidator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GpuFrontendService {

    private final GpuMethodParser parser;
    private final GpuSubsetValidator validator;
    private final GpuIrLowerer lowerer;
    private final OpenClKernelEmitter emitter;

    public GpuFrontendService(
            GpuMethodParser parser,
            GpuSubsetValidator validator,
            GpuIrLowerer lowerer,
            OpenClKernelEmitter emitter
    ) {
        this.parser = parser;
        this.validator = validator;
        this.lowerer = lowerer;
        this.emitter = emitter;
    }

    public static GpuFrontendService createDefault() {
        GpuIntrinsicDatabase intrinsicDatabase = GpuIntrinsicDatabase.createDefault();
        return new GpuFrontendService(
                new GpuMethodParser(),
                new GpuSubsetValidator(intrinsicDatabase),
                new GpuIrLowerer(intrinsicDatabase),
                new OpenClKernelEmitter()
        );
    }

    public ParsedGpuMethod parseAndValidate(String methodSource) {
        ParsedGpuMethod method = parser.parseMethod(methodSource);
        validator.validateKernel(method, List.of());
        return method;
    }

    public GpuIrMethod parseValidateAndLower(String methodSource) {
        ParsedGpuMethod method = parseAndValidate(methodSource);
        return lowerer.lower(method);
    }

    public String parseValidateLowerAndEmit(String methodSource) {
        ParsedGpuMethod method = parseAndValidate(methodSource);
        GpuIrMethod irMethod = lowerer.lower(method);
        return emitter.emit(method, irMethod);
    }

    public String parseValidateLowerAndEmit(String methodSource, List<String> helperMethodSources) {
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource);
        List<ParsedGpuMethod> helperMethods = helperMethodSources.stream()
                .map(parser::parseMethod)
                .toList();

        return validateLowerAndEmit(kernelMethod, helperMethods);
    }

    public String validateLowerAndEmit(ParsedGpuMethod kernelMethod, List<ParsedGpuMethod> helperMethods) {
        validator.validateKernel(kernelMethod, helperMethods);

        List<GpuIrCompiledMethod> compiledMethods = lowerer.lower(kernelMethod, helperMethods);
        List<GpuIrCompiledMethod> compiledHelpers = compiledMethods.subList(0, helperMethods.size());
        GpuIrCompiledMethod compiledKernel = compiledMethods.get(compiledMethods.size() - 1);
        return emitter.emitProgram(compiledKernel, selectReachableHelpers(compiledKernel, compiledHelpers));
    }

    private List<GpuIrCompiledMethod> selectReachableHelpers(
            GpuIrCompiledMethod kernelMethod,
            List<GpuIrCompiledMethod> helperMethods
    ) {
        Map<String, GpuIrCompiledMethod> helpersByName = new LinkedHashMap<>();
        for (GpuIrCompiledMethod helperMethod : helperMethods) {
            helpersByName.put(helperMethod.emittedName(), helperMethod);
        }

        detectRecursiveHelpers(helpersByName);

        LinkedHashSet<String> reachableNames = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(kernelMethod.helperDependencies());
        while (!pending.isEmpty()) {
            String helperName = pending.removeFirst();
            if (!reachableNames.add(helperName)) {
                continue;
            }
            GpuIrCompiledMethod helper = helpersByName.get(helperName);
            if (helper == null) {
                throw new IllegalArgumentException("Lowered kernel references unknown helper: " + helperName);
            }
            pending.addAll(helper.helperDependencies());
        }

        List<GpuIrCompiledMethod> reachableHelpers = new ArrayList<>();
        for (GpuIrCompiledMethod helperMethod : helperMethods) {
            if (reachableNames.contains(helperMethod.emittedName())) {
                reachableHelpers.add(helperMethod);
            }
        }
        return reachableHelpers;
    }

    private void detectRecursiveHelpers(Map<String, GpuIrCompiledMethod> helpersByName) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> active = new LinkedHashSet<>();
        Deque<String> path = new ArrayDeque<>();

        for (String helperName : helpersByName.keySet()) {
            detectRecursiveHelpers(helperName, helpersByName, visited, active, path);
        }
    }

    private void detectRecursiveHelpers(
            String helperName,
            Map<String, GpuIrCompiledMethod> helpersByName,
            Set<String> visited,
            Set<String> active,
            Deque<String> path
    ) {
        if (visited.contains(helperName)) {
            return;
        }
        if (active.contains(helperName)) {
            throw new IllegalArgumentException("Recursive @CCode helper calls are not supported: " + formatCycle(path, helperName));
        }

        GpuIrCompiledMethod helperMethod = helpersByName.get(helperName);
        if (helperMethod == null) {
            return;
        }

        active.add(helperName);
        path.addLast(helperName);
        for (String dependencyName : helperMethod.helperDependencies()) {
            detectRecursiveHelpers(dependencyName, helpersByName, visited, active, path);
        }
        path.removeLast();
        active.remove(helperName);
        visited.add(helperName);
    }

    private String formatCycle(Deque<String> path, String repeatedHelperName) {
        List<String> cycle = new ArrayList<>();
        boolean inCycle = false;
        for (String helperName : path) {
            if (helperName.equals(repeatedHelperName)) {
                inCycle = true;
            }
            if (inCycle) {
                cycle.add(helperName);
            }
        }
        cycle.add(repeatedHelperName);
        return String.join(" -> ", cycle);
    }
}
