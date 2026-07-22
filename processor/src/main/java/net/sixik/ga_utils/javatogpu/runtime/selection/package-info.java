/**
 * Backend and device discovery, policy, ranking, and selection support.
 *
 * <p>This package is the target home for runtime placement logic that explains why a backend/device was selected or
 * rejected. Start with {@link net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeSelection} when writing new
 * advanced selection or discovery tooling. Candidate selection, discovery, and combined backend/device preflight
 * implementation helpers live here as well, while root-runtime classes keep compatibility facades for existing
 * callers.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime.selection;
