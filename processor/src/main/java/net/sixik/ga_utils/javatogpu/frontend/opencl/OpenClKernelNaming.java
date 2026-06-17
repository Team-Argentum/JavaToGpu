package net.sixik.ga_utils.javatogpu.frontend.opencl;

import java.util.Set;
import java.util.List;

public final class OpenClKernelNaming {

    private static final Set<String> RESERVED_NAMES = Set.of(
            "kernel",
            "__kernel",
            "global",
            "__global",
            "local",
            "__local",
            "constant",
            "__constant",
            "private",
            "__private"
    );

    private OpenClKernelNaming() {
    }

    public static String toEntryPointName(String javaMethodName) {
        return sanitizeName(javaMethodName, "jtg_kernel");
    }

    public static String toHelperFunctionName(String ownerSimpleName, String javaMethodName, List<String> argumentTypes) {
        String suffix = argumentTypes.stream()
                .map(OpenClKernelNaming::sanitizeTypeFragment)
                .reduce((left, right) -> left + "_" + right)
                .orElse("void");
        String ownerPrefix = ownerSimpleName == null || ownerSimpleName.isBlank()
                ? ""
                : sanitizeTypeFragment(ownerSimpleName) + "_";
        return sanitizeName("jtg_fn_" + ownerPrefix + javaMethodName + "_" + suffix, "jtg_helper");
    }

    private static String sanitizeName(String javaMethodName, String fallback) {
        if (javaMethodName == null || javaMethodName.isBlank()) {
            return fallback;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < javaMethodName.length(); i++) {
            char current = javaMethodName.charAt(i);
            boolean valid = i == 0
                    ? Character.isLetter(current) || current == '_'
                    : Character.isLetterOrDigit(current) || current == '_';
            builder.append(valid ? current : '_');
        }

        String candidate = builder.toString();
        if (candidate.isEmpty()) {
            candidate = fallback;
        }
        if (!(Character.isLetter(candidate.charAt(0)) || candidate.charAt(0) == '_')) {
            candidate = "jtg_" + candidate;
        }
        if (candidate.startsWith("__") || RESERVED_NAMES.contains(candidate)) {
            candidate = "jtg_" + candidate;
        }

        return candidate;
    }

    private static String sanitizeTypeFragment(String typeName) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < typeName.length(); i++) {
            char current = typeName.charAt(i);
            if (Character.isLetterOrDigit(current) || current == '_') {
                builder.append(current);
            } else if (current == '[' || current == ']') {
                builder.append("arr");
            } else {
                builder.append('_');
            }
        }
        if (builder.isEmpty()) {
            return "arg";
        }
        return builder.toString();
    }
}
