package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.util.Optional;

public final class AsmFrontendException extends RuntimeException {

    private final AsmFrontendFailureMetadata metadata;

    public AsmFrontendException(String message) {
        this(message, null);
    }

    public AsmFrontendException(String message, AsmFrontendFailureMetadata metadata) {
        super(message);
        this.metadata = metadata;
    }

    public Optional<AsmFrontendFailureMetadata> metadata() {
        return Optional.ofNullable(metadata);
    }
}
