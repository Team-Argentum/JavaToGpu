/**
 * Private-address-space pointer wrappers for scalar-by-reference helper patterns.
 *
 * <p>Use these types when a GPU helper needs to mutate a scalar value through a small Java wrapper. Address-space-aware
 * packed-buffer views live in the {@code global}, {@code constant}, and {@code local} subpackages.
 */
package net.sixik.ga_utils.javatogpu.api.pointers;
