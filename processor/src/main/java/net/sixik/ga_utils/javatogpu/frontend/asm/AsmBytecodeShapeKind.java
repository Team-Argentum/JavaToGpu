package net.sixik.ga_utils.javatogpu.frontend.asm;

/**
 * Coarse JVM bytecode shape buckets used by read-only ASM ingestion inventory reports.
 */
public enum AsmBytecodeShapeKind {
    SUPPORTED_METHOD("supportedMethod", false),
    NON_STATIC_METHOD("nonStaticMethod", true),
    SYNCHRONIZED_METHOD("synchronizedMethod", true),
    ABSTRACT_OR_NATIVE_METHOD("abstractOrNativeMethod", true),
    EXCEPTION_HANDLER("exceptionHandler", true),
    ARRAY_LENGTH("arrayLength", true),
    ARRAY_ALLOCATION("arrayAllocation", true),
    OBJECT_ALLOCATION("objectAllocation", true),
    OBJECT_TYPE_CHECK("objectTypeCheck", true),
    FIELD_ACCESS("fieldAccess", true),
    DYNAMIC_INVOCATION("dynamicInvocation", true),
    VIRTUAL_DISPATCH("virtualDispatch", true),
    SPECIAL_INVOCATION("specialInvocation", true),
    UNSUPPORTED_STATIC_OWNER("unsupportedStaticOwner", true),
    UNSUPPORTED_DESCRIPTOR("unsupportedDescriptor", true),
    EXCEPTION_THROW("exceptionThrow", true),
    MONITOR_SYNCHRONIZATION("monitorSynchronization", true),
    UNKNOWN_INSTRUCTION_NODE("unknownInstructionNode", true);

    private final String artifactValue;
    private final boolean risky;

    AsmBytecodeShapeKind(String artifactValue, boolean risky) {
        this.artifactValue = artifactValue;
        this.risky = risky;
    }

    public String artifactValue() {
        return artifactValue;
    }

    public boolean risky() {
        return risky;
    }
}
