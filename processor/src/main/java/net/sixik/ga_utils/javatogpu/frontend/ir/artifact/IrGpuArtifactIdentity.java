package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Builds stable identifiers for backend-neutral IR artifacts.
 *
 * <p>The runtime cache must distinguish kernels that share the same Java descriptor and backend target but were
 * optimized from different IR payloads. Hashing the deterministic artifact manifest gives each IR variant a compact
 * cache-safe identity without tying the runtime to one backend source format.</p>
 */
public final class IrGpuArtifactIdentity {

    public static final String MISSING_ARTIFACT_IDENTITY = "irgpu:none";

    private IrGpuArtifactIdentity() {
    }

    public static String stableIdentity(Optional<IrGpuArtifact> artifact) {
        return artifact.map(IrGpuArtifactIdentity::stableIdentity).orElse(MISSING_ARTIFACT_IDENTITY);
    }

    public static String stableIdentity(IrGpuArtifact artifact) {
        return "irgpu:sha256:" + stableHash(artifact);
    }

    public static String stableHash(IrGpuArtifact artifact) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = IrGpuArtifactSerializer.serialize(artifact).getBytes(StandardCharsets.UTF_8);
            return toHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0x0F, 16));
            builder.append(Character.forDigit(value & 0x0F, 16));
        }
        return builder.toString();
    }
}
