/**
 * Runtime diagnostics, artifact summaries, failure evidence, production gates, and user-facing reports.
 *
 * <p>Use this package for evidence that helps users or CI understand runtime behavior without opening backend-specific
 * implementation classes. Generated call-site metadata resolution, compact runtime failure rendering, portable artifact
 * property maps, compile-cache invalidation stamp construction, compiler-feedback inspection, promotion artifact names,
 * source-promotion blocker classification, compile artifact dumping, and production-promotion explainability formatting
 * now live here, while the old root helpers remain compatibility facades.</p>
 *
 * <p>Root runtime value/report contracts such as compiler-feedback reports, artifact snapshots, runtime exceptions, and
 * diagnostic contexts intentionally stay in the root package for now because generated artifacts, tests, and extension
 * APIs use those shapes directly. New implementation helpers should still prefer this diagnostics package.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime.diagnostics;
