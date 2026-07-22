package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendSourcePromotionManifestTemplateCli;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuBackendSourcePromotionManifestValidatorCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourcePromotionManifestCliTest {

    private static final String GIT_SHA = "1ec61b94d629c8f4f5346ed321bc63fd2d50442c";

    @TempDir
    Path tempDirectory;

    @Test
    void writesPendingTemplateAndValidatesApprovedManifest() throws Exception {
        Path candidate = writeCandidate();
        Path template = tempDirectory.resolve("manifest-template.properties");
        Path manifest = tempDirectory.resolve("manifest.properties");
        Path validation = tempDirectory.resolve("validation/manifest-validation.properties");

        GpuBackendSourcePromotionManifestTemplateCli.main(new String[]{
                candidate.toString(),
                template.toString(),
                GIT_SHA
        });
        String pending = Files.readString(template);
        assertTrue(pending.contains("status=pending"));
        Files.writeString(
                manifest,
                approve(pending),
                StandardCharsets.UTF_8
        );

        assertDoesNotThrow(() -> GpuBackendSourcePromotionManifestValidatorCli.main(new String[]{
                candidate.toString(),
                manifest.toString(),
                validation.toString(),
                GIT_SHA
        }));

        String validationText = Files.readString(validation);
        assertTrue(validationText.contains("status=approved"));
        assertTrue(validationText.contains("valid=true"));
        assertTrue(validationText.contains("binding.gitShaMatched=true"));
        assertTrue(validationText.contains("authorization.defaultProductionSourceSwitching=disabled"));
    }

    @Test
    void writesBlockedValidationArtifactBeforeFailing() throws Exception {
        Path candidate = writeCandidate();
        Path manifest = tempDirectory.resolve("manifest-mismatch.properties");
        Path validation = tempDirectory.resolve("validation/manifest-blocked.properties");
        byte[] candidateBytes = Files.readAllBytes(candidate);
        String approved = GpuBackendSourcePromotionManifestTest.approvedManifest(candidateBytes)
                .replace("binding.gitSha=" + GIT_SHA, "binding.gitSha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        Files.writeString(manifest, approved, StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> GpuBackendSourcePromotionManifestValidatorCli.main(
                new String[]{candidate.toString(), manifest.toString(), validation.toString(), GIT_SHA}
        ));

        String validationText = Files.readString(validation);
        assertTrue(validationText.contains("status=blocked"));
        assertTrue(validationText.contains("valid=false"));
        assertTrue(validationText.contains("blocker.0=manifest-git-sha-mismatch"));
    }

    private Path writeCandidate() throws Exception {
        Path candidate = tempDirectory.resolve("candidate.properties");
        Files.writeString(
                candidate,
                GpuBackendSourcePromotionManifestTest.candidateText(),
                StandardCharsets.UTF_8
        );
        return candidate;
    }

    private static String approve(String pending) {
        return pending.replace("status=pending", "status=approved")
                .replace("approval.id=REQUIRED", "approval.id=approval:release-2026-07-10")
                .replace("approval.approvedBy=REQUIRED", "approval.approvedBy=release-operator")
                .replace("approval.approvedAtUtc=REQUIRED", "approval.approvedAtUtc=2026-07-10T20:00:00Z");
    }
}
