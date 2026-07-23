/**
 * Runtime IR optimization, peephole review, common-subexpression review, optimizer evidence, and register-pressure
 * analysis support.
 *
 * <p>Optimizer mutation remains opt-in and fail-closed; this package is the target home for that advanced path.</p>
 *
 * <p>Root runtime optimizer value types such as optimization requests/reports, proof artifacts, peephole proposal
 * records, and register-pressure reports intentionally stay in the root package for now. They are public SPI/artifact
 * contracts. New optimizer implementations, built-in rules, analysis helpers, and review passes should live here.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime.optimization;
