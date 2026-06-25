package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Small CLI entry point for generating a vendor-validation snapshot report.
 */
public final class OpenClValidationReporter {

    private static final String REPORT_FILE_PROPERTY = "javatogpu.opencl.validationReportFile";

    private OpenClValidationReporter() {
    }

    public static void main(String[] args) throws IOException {
        String markdown = buildReport();
        String outputPath = System.getProperty(REPORT_FILE_PROPERTY);
        if (outputPath != null && !outputPath.isBlank()) {
            Path path = Paths.get(outputPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, markdown, StandardCharsets.UTF_8);
            System.out.println("Wrote OpenCL validation report to " + path.toAbsolutePath());
        }
        System.out.println(markdown);
    }

    private static String buildReport() {
        StringBuilder markdown = new StringBuilder();
        String requestedVendor = env("JTG_VALIDATION_VENDOR");
        String runnerName = env("RUNNER_NAME");
        String runnerOs = env("RUNNER_OS");
        String gitSha = env("GITHUB_SHA");
        String gitRef = env("GITHUB_REF_NAME");

        markdown.append("# OpenCL Vendor Validation Snapshot\n\n");
        if (!requestedVendor.isBlank()) {
            markdown.append("- Requested vendor lane: `").append(requestedVendor).append("`\n");
        }
        if (!runnerName.isBlank() || !runnerOs.isBlank()) {
            markdown.append("- Runner: `").append((runnerName + " " + runnerOs).trim()).append("`\n");
        }
        if (!gitRef.isBlank()) {
            markdown.append("- Git ref: `").append(gitRef).append("`\n");
        }
        if (!gitSha.isBlank()) {
            markdown.append("- Git SHA: `").append(gitSha).append("`\n");
        }
        if (markdown.charAt(markdown.length() - 1) != '\n') {
            markdown.append('\n');
        }
        markdown.append('\n');

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            markdown.append(backend.validationReport().toMarkdown());
        } catch (Throwable failure) {
            markdown.append("## Report Failure\n\n");
            markdown.append("- Status: `failed to query OpenCL runtime`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n");
        }

        return markdown.toString();
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String sanitizeInline(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }
}
