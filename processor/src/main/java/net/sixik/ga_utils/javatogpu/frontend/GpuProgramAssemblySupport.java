package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GpuProgramAssemblySupport {

    private GpuProgramAssemblySupport() {
    }

    static List<GpuIrCompiledMethod> selectReachableHelpers(
            GpuIrCompiledMethod kernelMethod,
            List<GpuIrCompiledMethod> helperMethods,
            String missingHelperMessagePrefix,
            String recursiveHelperMessagePrefix
    ) {
        Map<String, GpuIrCompiledMethod> helpersByName = new LinkedHashMap<>();
        for (GpuIrCompiledMethod helperMethod : helperMethods) {
            helpersByName.put(helperMethod.emittedName(), helperMethod);
        }

        detectRecursiveHelpers(helpersByName, recursiveHelperMessagePrefix);

        LinkedHashSet<String> reachableNames = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(kernelMethod.helperDependencies());
        while (!pending.isEmpty()) {
            String helperName = pending.removeFirst();
            if (!reachableNames.add(helperName)) {
                continue;
            }
            GpuIrCompiledMethod helper = helpersByName.get(helperName);
            if (helper == null) {
                throw new IllegalArgumentException(missingHelperMessagePrefix + helperName);
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

    private static void detectRecursiveHelpers(
            Map<String, GpuIrCompiledMethod> helpersByName,
            String recursiveHelperMessagePrefix
    ) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> active = new LinkedHashSet<>();
        Deque<String> path = new ArrayDeque<>();

        for (String helperName : helpersByName.keySet()) {
            detectRecursiveHelpers(helperName, helpersByName, visited, active, path, recursiveHelperMessagePrefix);
        }
    }

    private static void detectRecursiveHelpers(
            String helperName,
            Map<String, GpuIrCompiledMethod> helpersByName,
            Set<String> visited,
            Set<String> active,
            Deque<String> path,
            String recursiveHelperMessagePrefix
    ) {
        if (visited.contains(helperName)) {
            return;
        }
        if (active.contains(helperName)) {
            throw new IllegalArgumentException(recursiveHelperMessagePrefix + formatCycle(path, helperName));
        }

        GpuIrCompiledMethod helperMethod = helpersByName.get(helperName);
        if (helperMethod == null) {
            return;
        }

        active.add(helperName);
        path.addLast(helperName);
        for (String dependencyName : helperMethod.helperDependencies()) {
            detectRecursiveHelpers(
                    dependencyName,
                    helpersByName,
                    visited,
                    active,
                    path,
                    recursiveHelperMessagePrefix
            );
        }
        path.removeLast();
        active.remove(helperName);
        visited.add(helperName);
    }

    private static String formatCycle(Deque<String> path, String repeatedHelperName) {
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
