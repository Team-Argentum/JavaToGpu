package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/** Command-line entrypoint for blocking OpenCL CI only on launch-advisory regressions. */
public final class OpenClKernelLaunchAdvisoryDriftValidatorCli {

    private static final Set<String> ADVISORY_STATUSES = Set.of(
            "stable",
            "changed",
            "improved",
            "no-baseline",
            "unavailable"
    );

    private OpenClKernelLaunchAdvisoryDriftValidatorCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Expected path to kernel launch-advisory drift properties file");
        }
        Path artifactPath = Path.of(args[0]);
        if (!Files.isRegularFile(artifactPath)) {
            throw new IllegalStateException("Missing kernel launch-advisory drift artifact: " + artifactPath);
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
                    "Kernel launch-advisory drift regressed for " + artifactPath + ": " + diagnostic
            );
        }
        if (!ADVISORY_STATUSES.contains(status)) {
            throw new IllegalStateException(
                    "Kernel launch-advisory drift artifact has unsupported status " + status + " for " + artifactPath
            );
        }
        System.out.println("Kernel launch-advisory drift OK: status=" + status + ", regression=false");
    }
}
