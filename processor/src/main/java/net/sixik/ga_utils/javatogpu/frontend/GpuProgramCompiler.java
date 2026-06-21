package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.asm.AsmGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;

import java.util.List;

/**
 * Public facade over the JavaToGpu frontend pipeline.
 *
 * <p>This class keeps the existing source-first flow, but also exposes the structured ASM frontend as
 * another official entry point into the same IR and OpenCL emitter pipeline.</p>
 */
public final class GpuProgramCompiler {

    private final GpuFrontendService sourceFrontend;
    private final AsmFrontendService asmFrontend;

    public GpuProgramCompiler(
            GpuFrontendService sourceFrontend,
            AsmFrontendService asmFrontend
    ) {
        this.sourceFrontend = sourceFrontend;
        this.asmFrontend = asmFrontend;
    }

    public static GpuProgramCompiler createDefault() {
        return create(GpuIntrinsicDatabase.createDefault());
    }

    public static GpuProgramCompiler create(GpuIntrinsicDatabase intrinsicDatabase) {
        return new GpuProgramCompiler(
                GpuFrontendService.create(intrinsicDatabase),
                AsmFrontendService.create(intrinsicDatabase)
        );
    }

    public ParsedGpuMethod parseAndValidateSource(String methodSource) {
        return sourceFrontend.parseAndValidate(methodSource);
    }

    public GpuIrMethod lowerSource(String methodSource) {
        return sourceFrontend.parseValidateAndLower(methodSource);
    }

    public String compileSource(String methodSource) {
        return sourceFrontend.parseValidateLowerAndEmit(methodSource);
    }

    public String compileSource(String methodSource, List<String> helperMethodSources) {
        return sourceFrontend.parseValidateLowerAndEmit(methodSource, helperMethodSources);
    }

    public String compileSource(
            ParsedGpuMethod kernelMethod,
            List<ParsedGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        return sourceFrontend.validateLowerAndEmit(kernelMethod, helperMethods, structs);
    }

    public GpuIrMethod liftStructuredAsm(AsmGpuMethod method) {
        return asmFrontend.validateAndLiftStructured(method);
    }

    public GpuIrMethod liftLinearAsm(AsmGpuMethod method) {
        return asmFrontend.validateAndLiftLinear(method);
    }

    public String compileStructuredAsm(AsmGpuMethod kernelMethod, List<AsmGpuMethod> helperMethods) {
        return asmFrontend.validateLowerAndEmitStructured(kernelMethod, helperMethods);
    }

    public String compileStructuredAsm(
            AsmGpuMethod kernelMethod,
            List<AsmGpuMethod> helperMethods,
            List<ParsedGpuStruct> structs
    ) {
        return asmFrontend.validateLowerAndEmitStructured(kernelMethod, helperMethods, structs);
    }
}
