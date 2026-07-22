package net.sixik.ga_utils.javatogpu.runtime.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Command-line entrypoint for CI validation of production-promotion explainability artifacts. */
public final class GpuProductionPromotionExplainabilityValidatorCli {

    private GpuProductionPromotionExplainabilityValidatorCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Expected path to production-promotion explainability properties file");
        }
        Path artifactPath = Path.of(args[0]);
        if (!Files.isRegularFile(artifactPath)) {
            throw new IllegalStateException("Missing production-promotion explainability artifact: " + artifactPath);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(artifactPath)) {
            properties.load(inputStream);
        }

        GpuProductionPromotionExplainabilityValidation.Result result =
                GpuProductionPromotionExplainabilityValidation.validate(properties);
        if (!result.valid()) {
            throw new IllegalStateException("Production-promotion explainability contract failed for "
                    + artifactPath + ": " + result.firstViolation());
        }
        System.out.println("Production-promotion explainability OK: " + result.summary());
    }
}
