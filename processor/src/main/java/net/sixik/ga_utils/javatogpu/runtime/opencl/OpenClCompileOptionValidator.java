package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.util.List;
import java.util.Set;

final class OpenClCompileOptionValidator {

    private static final Set<String> FLAG_OPTIONS = Set.of(
            "-cl-denorms-are-zero",
            "-cl-fast-relaxed-math",
            "-cl-finite-math-only",
            "-cl-kernel-arg-info",
            "-cl-mad-enable",
            "-cl-no-signed-zeros",
            "-cl-opt-disable",
            "-cl-single-precision-constant",
            "-w",
            "-Werror"
    );

    private static final Set<String> VALUE_OPTIONS = Set.of("-cl-std");

    private OpenClCompileOptionValidator() {
    }

    static String toBuildOptions(List<String> compileArgs) {
        if (compileArgs == null || compileArgs.isEmpty()) {
            return "";
        }

        return String.join(" ", compileArgs.stream()
                .map(OpenClCompileOptionValidator::normalize)
                .toList());
    }

    private static String normalize(String option) {
        if (option == null || option.isBlank()) {
            throw new IllegalArgumentException("OpenCL compile option must not be blank");
        }
        if (!option.equals(option.trim())) {
            throw new IllegalArgumentException("OpenCL compile option must not have leading or trailing whitespace: '" + option + "'");
        }
        if (option.indexOf('\u0000') >= 0 || option.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("OpenCL compile option must be a single command-line token: '" + option + "'");
        }
        if (FLAG_OPTIONS.contains(option)) {
            return option;
        }
        if (isMacroDefinition(option) || isIncludePath(option)) {
            return option;
        }

        int equalsIndex = option.indexOf('=');
        if (equalsIndex > 0) {
            String name = option.substring(0, equalsIndex);
            String value = option.substring(equalsIndex + 1);
            if (VALUE_OPTIONS.contains(name) && !value.isBlank()) {
                return option;
            }
        }

        throw new IllegalArgumentException(
                "Unsupported OpenCL compile option: '"
                        + option
                        + "'. Supported flags include -cl-fast-relaxed-math, -cl-mad-enable, -cl-opt-disable, -cl-std=..., -DNAME=VALUE, and -I/path"
        );
    }

    private static boolean isMacroDefinition(String option) {
        return option.startsWith("-D") && option.length() > 2;
    }

    private static boolean isIncludePath(String option) {
        return option.startsWith("-I") && option.length() > 2;
    }
}
