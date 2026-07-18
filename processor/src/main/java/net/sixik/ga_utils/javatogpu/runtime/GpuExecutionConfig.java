package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Explicit execution configuration for runtime kernel launches.
 */
public record GpuExecutionConfig(
        int dimensions,
        long globalX,
        long globalY,
        long globalZ,
        long localX,
        long localY,
        long localZ
) {

    public GpuExecutionConfig {
        if (dimensions < 1 || dimensions > 3) {
            throw new IllegalArgumentException("dimensions must be 1, 2, or 3: " + dimensions);
        }
        if (globalX <= 0L) {
            throw new IllegalArgumentException("globalX must be positive: " + globalX);
        }
        if (dimensions == 1) {
            if (globalY != 1L) {
                throw new IllegalArgumentException("globalY must be 1 for 1D execution: " + globalY);
            }
            if (globalZ != 1L) {
                throw new IllegalArgumentException("globalZ must be 1 for 1D execution: " + globalZ);
            }
            if (localY != 0L) {
                throw new IllegalArgumentException("localY must be 0 for 1D execution: " + localY);
            }
            if (localZ != 0L) {
                throw new IllegalArgumentException("localZ must be 0 for 1D execution: " + localZ);
            }
            if (localX < 0L) {
                throw new IllegalArgumentException("localX must be >= 0: " + localX);
            }
        } else if (dimensions == 2) {
            if (globalY <= 0L) {
                throw new IllegalArgumentException("globalY must be positive for 2D execution: " + globalY);
            }
            if (globalZ != 1L) {
                throw new IllegalArgumentException("globalZ must be 1 for 2D execution: " + globalZ);
            }
            if (localZ != 0L) {
                throw new IllegalArgumentException("localZ must be 0 for 2D execution: " + localZ);
            }
            boolean localUnset = localX == 0L && localY == 0L;
            boolean localSet = localX > 0L && localY > 0L;
            if (!localUnset && !localSet) {
                throw new IllegalArgumentException("localX/localY must both be zero or both be > 0 for 2D execution");
            }
        } else {
            if (globalY <= 0L) {
                throw new IllegalArgumentException("globalY must be positive for 3D execution: " + globalY);
            }
            if (globalZ <= 0L) {
                throw new IllegalArgumentException("globalZ must be positive for 3D execution: " + globalZ);
            }
            boolean localUnset = localX == 0L && localY == 0L && localZ == 0L;
            boolean localSet = localX > 0L && localY > 0L && localZ > 0L;
            if (!localUnset && !localSet) {
                throw new IllegalArgumentException("localX/localY/localZ must all be zero or all be > 0 for 3D execution");
            }
        }
    }

    public GpuExecutionConfig(int dimensions, long globalX, long globalY, long localX, long localY) {
        this(dimensions, globalX, globalY, 1L, localX, localY, 0L);
    }

    public static GpuExecutionConfig oneDimensional(long globalWorkSize) {
        return new GpuExecutionConfig(1, globalWorkSize, 1L, 1L, 0L, 0L, 0L);
    }

    public static GpuExecutionConfig oneDimensional(long globalWorkSize, long localWorkSize) {
        return new GpuExecutionConfig(1, globalWorkSize, 1L, 1L, localWorkSize, 0L, 0L);
    }

    public static GpuExecutionConfig twoDimensional(long globalX, long globalY) {
        return new GpuExecutionConfig(2, globalX, globalY, 1L, 0L, 0L, 0L);
    }

    public static GpuExecutionConfig twoDimensional(long globalX, long globalY, long localX, long localY) {
        return new GpuExecutionConfig(2, globalX, globalY, 1L, localX, localY, 0L);
    }

    public static GpuExecutionConfig threeDimensional(long globalX, long globalY, long globalZ) {
        return new GpuExecutionConfig(3, globalX, globalY, globalZ, 0L, 0L, 0L);
    }

    public static GpuExecutionConfig threeDimensional(long globalX, long globalY, long globalZ, long localX, long localY, long localZ) {
        return new GpuExecutionConfig(3, globalX, globalY, globalZ, localX, localY, localZ);
    }

    public long globalWorkSize() {
        return globalX;
    }

    /**
     * Returns the global launch shape in the same dimensionality that will be submitted to the backend.
     */
    public String globalShape() {
        return switch (dimensions) {
            case 1 -> Long.toString(globalX);
            case 2 -> globalX + "x" + globalY;
            case 3 -> globalX + "x" + globalY + "x" + globalZ;
            default -> throw new IllegalStateException("unsupported execution dimensions: " + dimensions);
        };
    }

    /**
     * Returns the explicit local shape, or {@code auto} when the backend should choose it.
     */
    public String localShape() {
        if (!hasExplicitLocalSize()) {
            return "auto";
        }
        return switch (dimensions) {
            case 1 -> Long.toString(localX);
            case 2 -> localX + "x" + localY;
            case 3 -> localX + "x" + localY + "x" + localZ;
            default -> throw new IllegalStateException("unsupported execution dimensions: " + dimensions);
        };
    }

    /**
     * Returns whether this config asks the backend to use an explicit local work-group size.
     */
    public boolean hasExplicitLocalSize() {
        return localX > 0L;
    }

    /**
     * Returns the total number of global work-items addressed by this launch.
     */
    public long globalItemCount() {
        return globalX * globalY * globalZ;
    }

    /**
     * Returns the total explicit local work-group size, or {@code 0} when local sizing is automatic.
     */
    public long localItemCount() {
        return hasExplicitLocalSize() ? localX * Math.max(1L, localY) * Math.max(1L, localZ) : 0L;
    }

    /**
     * Human-readable summary suitable for examples, diagnostics, and lifecycle journal fields.
     */
    public String summary() {
        return dimensions + "D global=" + globalShape() + ", local=" + localShape();
    }
}
