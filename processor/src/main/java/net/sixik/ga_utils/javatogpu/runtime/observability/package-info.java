/**
 * Lifecycle events, logging services, journals, and observability harness support.
 *
 * <p>The normal runtime path should stay quiet unless an observability service is installed or explicitly requested.</p>
 *
 * <p>Root event, record, report, and service interfaces remain public contracts for generated/runtime code and external
 * ServiceLoader modules. Buses, built-in sinks, field-vocabulary helpers, and journal implementations should live here;
 * root classes with the same names are compatibility facades.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime.observability;
