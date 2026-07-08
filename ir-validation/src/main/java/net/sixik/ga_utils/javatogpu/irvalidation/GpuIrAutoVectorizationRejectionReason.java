package net.sixik.ga_utils.javatogpu.irvalidation;

public enum GpuIrAutoVectorizationRejectionReason {
    // The lowered IR is incomplete, so the scanner reports a diagnostic instead of throwing.
    INCOMPLETE_IR,

    // Loop shape is not the first supported fixed-width int induction pattern.
    UNSUPPORTED_LOOP_SHAPE,

    // The loop width is not one of the vector lane widths supported by the first analysis pass.
    UNSUPPORTED_LANE_COUNT,

    // The loop body is empty, so there is no lane-wise work to vectorize.
    EMPTY_BODY,

    // The body contains a statement that is not a simple array assignment.
    UNSUPPORTED_BODY_STATEMENT,

    // The assignment target is not indexed by the loop induction variable.
    NON_LANE_TARGET,

    // The assignment value may have side effects and cannot be duplicated/reordered safely.
    SIDE_EFFECTING_VALUE,

    // Typed preview could not prove one stable scalar element type for the future vector shape.
    UNSUPPORTED_ELEMENT_TYPE
}
