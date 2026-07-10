package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/** Command-line entrypoint for blocking OpenCL CI only on compiler-resource regressions. */
public final class OpenClCompilerResourceDriftValidatorCli {

    private static final Set<String> ADVISORY_STATUSES = Set.of(
            "stable",
            "changed",
            "improved",
            "no-baseline",
            "unavailable"
    );

    private OpenClCompilerResourceDriftValidatorCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Expected path to compiler-resource drift properties file");
        }
        Path artifactPath = Path.of(args[0]);
        if (!Files.isRegularFile(artifactPath)) {
            throw new IllegalStateException("Missing compiler-resource drift artifact: " + artifactPath);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(artifactPath)) {
            properties.load(inputStream);
        }
        String status = properties.getProperty("status", "unknown");
        boolean regression = Boolean.parseBoolean(properties.getProperty("regression", "false"));
        String diagnostic = properties.getProperty("diagnostic", "");
        if (regression || "regressed".equals(status)) {
            throw new IllegalStateException(
                    "Compiler-resource drift regressed for " + artifactPath + ": " + diagnostic
            );
        }
        if (!ADVISORY_STATUSES.contains(status)) {
            throw new IllegalStateException(
                    "Compiler-resource drift artifact has unsupported status " + status + " for " + artifactPath
            );
        }
        System.out.println("Compiler-resource drift OK: status=" + status + ", regression=false");
    }
}
