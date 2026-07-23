/**
 * CUDA runtime backend implementation and staged native bridge support.
 *
 * <p>This package owns CUDA-specific provider, adapter, lowering, execution skeleton, native bridge, inventory,
 * readiness, and validation code. CUDA remains staged/fail-closed for production execution until the backend contract
 * and hardware validation gates are explicitly promoted.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime.cuda;
