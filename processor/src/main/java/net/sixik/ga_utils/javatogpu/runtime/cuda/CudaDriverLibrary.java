package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.lwjgl.system.Library;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.SharedLibrary;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Small CUDA Driver API shared-library probe used before real native handle ownership exists.
 */
final class CudaDriverLibrary {

    static final int CUDA_SUCCESS = 0;

    static final List<String> REQUIRED_MODULE_LOADER_SYMBOLS = List.of(
            "cuInit",
            "cuDriverGetVersion",
            "cuModuleLoadDataEx",
            "cuModuleGetFunction",
            "cuModuleUnload"
    );

    static final List<String> REQUIRED_ARGUMENT_BINDER_SYMBOLS = List.of(
            "cuMemAlloc_v2",
            "cuMemcpyHtoD_v2",
            "cuMemFree_v2"
    );

    static final List<String> REQUIRED_KERNEL_LAUNCHER_SYMBOLS = List.of(
            "cuLaunchKernel"
    );

    static final List<String> REQUIRED_READBACK_SYMBOLS = List.of(
            "cuMemcpyDtoH_v2"
    );

    private CudaDriverLibrary() {
    }

    static CudaDriverApiProbeResult probeDefault() {
        return probe(new LwjglSharedLibraryResolver(), defaultLibraryCandidates(), REQUIRED_MODULE_LOADER_SYMBOLS);
    }

    static CudaModuleLoadResult loadModuleFromPtx(CudaModuleLoadRequest request, String loaderId) {
        return loadModuleFromPtx(
                request,
                loaderId,
                new LwjglSharedLibraryResolver(),
                new JniDriverApiInvoker(),
                defaultLibraryCandidates()
        );
    }

    static CudaModuleLoadResult loadModuleFromPtx(
            CudaModuleLoadRequest request,
            String loaderId,
            SharedLibraryResolver resolver,
            DriverApiInvoker invoker,
            List<String> libraryCandidates
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(invoker, "invoker");
        ArrayList<String> diagnostics = new ArrayList<>();
        SharedLibraryHandle library = null;
        SymbolAddresses symbols = null;
        for (String candidate : normalizeCandidates(libraryCandidates)) {
            diagnostics.add("probing CUDA Driver API library candidate: " + candidate);
            try {
                library = resolver.open(candidate);
                symbols = resolveRequiredSymbols(library, diagnostics);
                break;
            } catch (MissingSymbolsException exception) {
                closeQuietly(library, diagnostics);
                return CudaModuleLoadResult.unsupported(request.loaderMode(), exception.blockers(), diagnostics);
            } catch (RuntimeException | LinkageError exception) {
                closeQuietly(library, diagnostics);
                library = null;
                diagnostics.add("CUDA Driver API library candidate unavailable: "
                        + candidate
                        + " ("
                        + exceptionMessage(exception)
                        + ")");
            }
        }
        if (library == null || symbols == null) {
            diagnostics.add("No CUDA Driver API library candidate could be loaded");
            return CudaModuleLoadResult.unsupported(
                    request.loaderMode(),
                    List.of("cuda-driver-library-unavailable"),
                    diagnostics
            );
        }

        ByteBuffer ptxBuffer = null;
        long moduleHandle = 0L;
        boolean transferOwnership = false;
        try {
            int initStatus = invoker.cuInit(0, symbols.cuInit());
            if (initStatus != CUDA_SUCCESS) {
                return failedAfterClosing(
                        loaderId,
                        List.of("cuda-driver-cuInit-failed:" + initStatus),
                        append(diagnostics, "CUDA cuInit failed with code " + initStatus)
                );
            }
            String ptxSource = request.moduleArtifact().requireSource();
            CudaPtxCompatibilityMetadata ptxMetadata = CudaPtxCompatibilityMetadata.fromSource(ptxSource);
            diagnostics.add(ptxMetadata.diagnosticLine());
            ptxBuffer = MemoryUtil.memUTF8(ptxSource, true);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                java.nio.IntBuffer driverVersionOut = stack.mallocInt(1);
                int driverVersionStatus = invoker.cuDriverGetVersion(
                        MemoryUtil.memAddress(driverVersionOut),
                        symbols.cuDriverGetVersion()
                );
                if (driverVersionStatus != CUDA_SUCCESS) {
                    return failedAfterClosing(
                            loaderId,
                            List.of("cuda-driver-cuDriverGetVersion-failed:" + driverVersionStatus),
                            append(diagnostics, "CUDA cuDriverGetVersion failed with code " + driverVersionStatus)
                    );
                }
                int driverVersionRaw = driverVersionOut.get(0);
                String driverVersion = formatDriverVersion(driverVersionRaw);
                diagnostics.add("CUDA Driver API version reported " + driverVersion + " (" + driverVersionRaw + ")");
                CudaPtxDriverCompatibility ptxDriverCompatibility = CudaPtxDriverCompatibility.evaluate(
                        ptxMetadata,
                        driverVersionRaw
                );
                diagnostics.addAll(ptxDriverCompatibility.diagnostics());
                if (!ptxDriverCompatibility.passedOrSkipped()) {
                    return CudaModuleLoadResult.unsupported(
                            request.loaderMode(),
                            ptxDriverCompatibility.blockers(),
                            diagnostics
                    );
                }
                CudaPtxDeviceCompatibility ptxDeviceCompatibility = CudaPtxDeviceCompatibility.evaluate(
                        ptxMetadata,
                        request.compiledKernel().deviceProfile()
                );
                diagnostics.addAll(ptxDeviceCompatibility.diagnostics());
                if (!ptxDeviceCompatibility.passedOrSkipped()) {
                    return CudaModuleLoadResult.unsupported(
                            request.loaderMode(),
                            ptxDeviceCompatibility.blockers(),
                            diagnostics
                    );
                }
                PointerBuffer moduleOut = stack.mallocPointer(1);
                int moduleLoadStatus = invoker.cuModuleLoadDataEx(
                        MemoryUtil.memAddress(moduleOut),
                        MemoryUtil.memAddress(ptxBuffer),
                        0,
                        0L,
                        0L,
                        symbols.cuModuleLoadDataEx()
                );
                if (moduleLoadStatus != CUDA_SUCCESS) {
                    return failedAfterClosing(
                            loaderId,
                            List.of("cuda-driver-cuModuleLoadDataEx-failed:" + moduleLoadStatus),
                            append(diagnostics, "CUDA cuModuleLoadDataEx failed with code " + moduleLoadStatus)
                    );
                }
                moduleHandle = moduleOut.get(0);
                PointerBuffer functionOut = stack.mallocPointer(1);
                ByteBuffer kernelName = stack.UTF8(request.kernelName(), true);
                int functionStatus = invoker.cuModuleGetFunction(
                        MemoryUtil.memAddress(functionOut),
                        moduleHandle,
                        MemoryUtil.memAddress(kernelName),
                        symbols.cuModuleGetFunction()
                );
                if (functionStatus != CUDA_SUCCESS) {
                    int unloadStatus = invoker.cuModuleUnload(moduleHandle, symbols.cuModuleUnload());
                    return failedAfterClosing(
                            loaderId,
                            List.of("cuda-driver-cuModuleGetFunction-failed:" + functionStatus),
                            append(
                                    diagnostics,
                                    "CUDA cuModuleGetFunction failed with code "
                                            + functionStatus
                                            + "; cuModuleUnload after failure returned "
                                            + unloadStatus
                            )
                    );
                }
                CudaDriverLoadedModule loadedModule = new CudaDriverLoadedModule(
                        loaderId,
                        library.loadedName(),
                        library.path(),
                        request.kernelName(),
                        moduleHandle,
                        functionOut.get(0),
                        symbols.cuModuleUnload(),
                        driverVersionRaw,
                        driverVersion,
                        ptxMetadata,
                        ptxDeviceCompatibility,
                        ptxDriverCompatibility,
                        library,
                        invoker,
                        append(diagnostics, "CUDA module/function handles loaded through Driver API")
                );
                transferOwnership = true;
                return CudaModuleLoadResult.succeeded(
                        loaderId,
                        loadedModule,
                        loadedModule.diagnostics()
                );
            }
        } catch (RuntimeException | LinkageError exception) {
            if (moduleHandle != 0L && symbols != null) {
                try {
                    int unloadStatus = invoker.cuModuleUnload(moduleHandle, symbols.cuModuleUnload());
                    diagnostics.add("CUDA cuModuleUnload after exception returned " + unloadStatus);
                } catch (RuntimeException unloadException) {
                    diagnostics.add("CUDA cuModuleUnload after exception failed: " + exceptionMessage(unloadException));
                }
            }
            return failedAfterClosing(
                    loaderId,
                    List.of("cuda-driver-module-load-exception:" + exception.getClass().getSimpleName()),
                    append(diagnostics, exceptionMessage(exception))
            );
        } finally {
            if (ptxBuffer != null) {
                MemoryUtil.memFree(ptxBuffer);
            }
            if (!transferOwnership) {
                closeQuietly(library, diagnostics);
            }
        }
    }

    static CudaDriverApiProbeResult probe(
            SharedLibraryResolver resolver,
            List<String> libraryCandidates,
            List<String> requiredSymbols
    ) {
        Objects.requireNonNull(resolver, "resolver");
        List<String> candidates = normalizeCandidates(libraryCandidates);
        List<String> symbols = requiredSymbols == null ? List.of() : List.copyOf(requiredSymbols);
        ArrayList<String> diagnostics = new ArrayList<>();
        for (String candidate : candidates) {
            diagnostics.add("probing CUDA Driver API library candidate: " + candidate);
            try (SharedLibraryHandle library = resolver.open(candidate)) {
                ArrayList<String> resolved = new ArrayList<>();
                ArrayList<String> missing = new ArrayList<>();
                for (String symbol : symbols) {
                    long address = library.findSymbol(symbol);
                    if (address == 0L) {
                        missing.add(symbol);
                    } else {
                        resolved.add(symbol);
                    }
                }
                if (!missing.isEmpty()) {
                    diagnostics.add("CUDA Driver API library loaded but required symbols are missing: "
                            + String.join(", ", missing));
                    return CudaDriverApiProbeResult.missingSymbols(
                            library.loadedName(),
                            library.path(),
                            resolved,
                            missing,
                            diagnostics
                    );
                }
                diagnostics.add("CUDA Driver API library loaded and required symbols resolved");
                return CudaDriverApiProbeResult.available(
                        library.loadedName(),
                        library.path(),
                        resolved,
                        diagnostics
                );
            } catch (RuntimeException | LinkageError exception) {
                diagnostics.add("CUDA Driver API library candidate unavailable: "
                        + candidate
                        + " ("
                        + exceptionMessage(exception)
                        + ")");
            }
        }
        diagnostics.add("No CUDA Driver API library candidate could be loaded");
        return CudaDriverApiProbeResult.unavailable(diagnostics);
    }

    static List<String> defaultLibraryCandidates() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return List.of("nvcuda");
        }
        if (osName.contains("mac")) {
            return List.of("cuda");
        }
        return List.of("cuda");
    }

    private static List<String> normalizeCandidates(List<String> libraryCandidates) {
        if (libraryCandidates == null || libraryCandidates.isEmpty()) {
            return defaultLibraryCandidates();
        }
        List<String> normalized = libraryCandidates.stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return normalized.isEmpty() ? defaultLibraryCandidates() : normalized;
    }

    private static String exceptionMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getName() : message;
    }

    static String formatDriverVersion(int rawDriverVersion) {
        if (rawDriverVersion <= 0) {
            return "unknown";
        }
        int major = rawDriverVersion / 1000;
        int minor = (rawDriverVersion % 1000) / 10;
        return major + "." + minor;
    }

    private static SymbolAddresses resolveRequiredSymbols(SharedLibraryHandle library, List<String> diagnostics) {
        ArrayList<String> missing = new ArrayList<>();
        long cuInit = requiredSymbol(library, "cuInit", missing);
        long cuDriverGetVersion = requiredSymbol(library, "cuDriverGetVersion", missing);
        long cuModuleLoadDataEx = requiredSymbol(library, "cuModuleLoadDataEx", missing);
        long cuModuleGetFunction = requiredSymbol(library, "cuModuleGetFunction", missing);
        long cuModuleUnload = requiredSymbol(library, "cuModuleUnload", missing);
        if (!missing.isEmpty()) {
            diagnostics.add("CUDA Driver API library loaded but required symbols are missing: "
                    + String.join(", ", missing));
            throw new MissingSymbolsException(missing);
        }
        diagnostics.add("CUDA Driver API library loaded and required symbols resolved");
        return new SymbolAddresses(cuInit, cuDriverGetVersion, cuModuleLoadDataEx, cuModuleGetFunction, cuModuleUnload);
    }

    private static long requiredSymbol(SharedLibraryHandle library, String name, List<String> missing) {
        long address = library.findSymbol(name);
        if (address == 0L) {
            missing.add(name);
        }
        return address;
    }

    private static CudaModuleLoadResult failedAfterClosing(
            String loaderId,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return CudaModuleLoadResult.failed(loaderId, blockers, diagnostics);
    }

    private static void closeQuietly(SharedLibraryHandle library, List<String> diagnostics) {
        if (library == null) {
            return;
        }
        try {
            library.close();
        } catch (RuntimeException exception) {
            diagnostics.add("CUDA Driver API library close failed: " + exceptionMessage(exception));
        }
    }

    private static List<String> append(List<String> diagnostics, String diagnostic) {
        ArrayList<String> updated = new ArrayList<>(diagnostics == null ? List.of() : diagnostics);
        if (diagnostic != null && !diagnostic.isBlank()) {
            updated.add(diagnostic);
        }
        return List.copyOf(updated);
    }

    private record SymbolAddresses(
            long cuInit,
            long cuDriverGetVersion,
            long cuModuleLoadDataEx,
            long cuModuleGetFunction,
            long cuModuleUnload
    ) {
    }

    private static final class MissingSymbolsException extends RuntimeException {
        private final List<String> blockers;

        private MissingSymbolsException(List<String> missingSymbols) {
            super("CUDA Driver API required symbols missing: " + String.join(", ", missingSymbols));
            this.blockers = java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("cuda-driver-symbols-missing"),
                            missingSymbols.stream().map(symbol -> "cuda-driver-symbol-missing:" + symbol)
                    )
                    .toList();
        }

        private List<String> blockers() {
            return blockers;
        }
    }

    interface SharedLibraryResolver {
        SharedLibraryHandle open(String libraryName);
    }

    interface SharedLibraryHandle extends AutoCloseable {
        String loadedName();

        String path();

        long findSymbol(String symbolName);

        @Override
        void close();
    }

    interface DriverApiInvoker {
        int CUDA_ERROR_NOT_SUPPORTED = 801;

        int cuInit(int flags, long functionAddress);

        default int cuDriverGetVersion(long versionOutAddress, long functionAddress) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }

        int cuModuleLoadDataEx(
                long moduleOutAddress,
                long imageAddress,
                int optionCount,
                long optionsAddress,
                long optionValuesAddress,
                long functionAddress
        );

        int cuModuleGetFunction(
                long functionOutAddress,
                long moduleHandle,
                long kernelNameAddress,
                long functionAddress
        );

        int cuModuleUnload(long moduleHandle, long functionAddress);

        default int cuMemAlloc(long devicePointerOutAddress, long byteCount, long functionAddress) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }

        default int cuMemcpyHtoD(long devicePointer, long hostPointerAddress, long byteCount, long functionAddress) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }

        default int cuMemcpyDtoH(long hostPointerAddress, long devicePointer, long byteCount, long functionAddress) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }

        default int cuMemFree(long devicePointer, long functionAddress) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }

        default int cuLaunchKernel(
                long functionHandle,
                int gridDimX,
                int gridDimY,
                int gridDimZ,
                int blockDimX,
                int blockDimY,
                int blockDimZ,
                int sharedMemoryBytes,
                long streamHandle,
                long kernelParameterTableAddress,
                long extraAddress,
                long functionAddress
        ) {
            return CUDA_ERROR_NOT_SUPPORTED;
        }
    }

    private static final class JniDriverApiInvoker implements DriverApiInvoker {
        @Override
        public int cuInit(int flags, long functionAddress) {
            return JNI.invokeI(flags, functionAddress);
        }

        @Override
        public int cuDriverGetVersion(long versionOutAddress, long functionAddress) {
            return JNI.invokePI(versionOutAddress, functionAddress);
        }

        @Override
        public int cuModuleLoadDataEx(
                long moduleOutAddress,
                long imageAddress,
                int optionCount,
                long optionsAddress,
                long optionValuesAddress,
                long functionAddress
        ) {
            return JNI.invokePPPPI(
                    moduleOutAddress,
                    imageAddress,
                    optionCount,
                    optionsAddress,
                    optionValuesAddress,
                    functionAddress
            );
        }

        @Override
        public int cuModuleGetFunction(
                long functionOutAddress,
                long moduleHandle,
                long kernelNameAddress,
                long functionAddress
        ) {
            return JNI.invokePPPI(functionOutAddress, moduleHandle, kernelNameAddress, functionAddress);
        }

        @Override
        public int cuModuleUnload(long moduleHandle, long functionAddress) {
            return JNI.invokePI(moduleHandle, functionAddress);
        }

        @Override
        public int cuMemAlloc(long devicePointerOutAddress, long byteCount, long functionAddress) {
            return JNI.invokePPI(devicePointerOutAddress, byteCount, functionAddress);
        }

        @Override
        public int cuMemcpyHtoD(long devicePointer, long hostPointerAddress, long byteCount, long functionAddress) {
            return JNI.invokePPPI(devicePointer, hostPointerAddress, byteCount, functionAddress);
        }

        @Override
        public int cuMemcpyDtoH(long hostPointerAddress, long devicePointer, long byteCount, long functionAddress) {
            return JNI.invokePPPI(hostPointerAddress, devicePointer, byteCount, functionAddress);
        }

        @Override
        public int cuMemFree(long devicePointer, long functionAddress) {
            return JNI.invokePI(devicePointer, functionAddress);
        }

        @Override
        public int cuLaunchKernel(
                long functionHandle,
                int gridDimX,
                int gridDimY,
                int gridDimZ,
                int blockDimX,
                int blockDimY,
                int blockDimZ,
                int sharedMemoryBytes,
                long streamHandle,
                long kernelParameterTableAddress,
                long extraAddress,
                long functionAddress
        ) {
            return JNI.callPPPPI(
                    functionHandle,
                    gridDimX,
                    gridDimY,
                    gridDimZ,
                    blockDimX,
                    blockDimY,
                    blockDimZ,
                    sharedMemoryBytes,
                    streamHandle,
                    kernelParameterTableAddress,
                    extraAddress,
                    functionAddress
            );
        }
    }

    private static final class LwjglSharedLibraryResolver implements SharedLibraryResolver {
        @Override
        public SharedLibraryHandle open(String libraryName) {
            SharedLibrary library = Library.loadNative(
                    CudaDriverLibrary.class,
                    "net.sixik.ga_utils.javatogpu.runtime.cuda",
                    libraryName,
                    false
            );
            return new LwjglSharedLibraryHandle(libraryName, library);
        }
    }

    private record LwjglSharedLibraryHandle(String requestedName, SharedLibrary library) implements SharedLibraryHandle {

        private LwjglSharedLibraryHandle {
            Objects.requireNonNull(library, "library");
        }

        @Override
        public String loadedName() {
            String name = library.getName();
            return name == null || name.isBlank() ? requestedName : name;
        }

        @Override
        public String path() {
            String path = library.getPath();
            return path == null ? "" : path;
        }

        @Override
        public long findSymbol(String symbolName) {
            return library.getFunctionAddress(symbolName);
        }

        @Override
        public void close() {
            library.free();
        }
    }
}
