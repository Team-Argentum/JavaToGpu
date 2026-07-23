/**
 * Native-memory allocation services and provider bridges used by backend adapters.
 *
 * <p>This package is intended to keep LWJGL/Panama/native-memory integration behind a stable service boundary.</p>
 *
 * <p>The root {@code GpuRuntimeNativeMemoryServiceRegistry} remains a compatibility facade. New native-memory provider
 * discovery and future Panama work should prefer this domain package, while the provider interface and allocation value
 * records stay in root runtime as public SPI contracts for now.</p>
 */
package net.sixik.ga_utils.javatogpu.runtime.memory;
