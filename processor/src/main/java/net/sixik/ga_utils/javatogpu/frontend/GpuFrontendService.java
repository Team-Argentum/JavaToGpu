package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.opencl.OpenClKernelEmitter;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.intrinsics.GpuIntrinsicDatabase;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.lowering.GpuIrLowerer;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.parser.GpuMethodParser;
import net.sixik.ga_utils.javatogpu.frontend.validation.GpuSubsetValidator;

import java.util.List;

public final class GpuFrontendService {

    private final GpuMethodParser parser;
    private final GpuSubsetValidator validator;
    private final GpuIrLowerer lowerer;
    private final OpenClKernelEmitter emitter;

    public GpuFrontendService(
            GpuMethodParser parser,
            GpuSubsetValidator validator,
            GpuIrLowerer lowerer,
            OpenClKernelEmitter emitter
    ) {
        this.parser = parser;
        this.validator = validator;
        this.lowerer = lowerer;
        this.emitter = emitter;
    }

    public static GpuFrontendService createDefault() {
        GpuIntrinsicDatabase intrinsicDatabase = GpuIntrinsicDatabase.createDefault();
        return new GpuFrontendService(
                new GpuMethodParser(),
                new GpuSubsetValidator(intrinsicDatabase),
                new GpuIrLowerer(intrinsicDatabase),
                new OpenClKernelEmitter()
        );
    }

    public ParsedGpuMethod parseAndValidate(String methodSource) {
        ParsedGpuMethod method = parser.parseMethod(methodSource);
        validator.validateKernel(method, List.of());
        return method;
    }

    public GpuIrMethod parseValidateAndLower(String methodSource) {
        ParsedGpuMethod method = parseAndValidate(methodSource);
        return lowerer.lower(method);
    }

    public String parseValidateLowerAndEmit(String methodSource) {
        ParsedGpuMethod method = parseAndValidate(methodSource);
        GpuIrMethod irMethod = lowerer.lower(method);
        return emitter.emit(method, irMethod);
    }

    public String parseValidateLowerAndEmit(String methodSource, List<String> helperMethodSources) {
        ParsedGpuMethod kernelMethod = parser.parseMethod(methodSource);
        List<ParsedGpuMethod> helperMethods = helperMethodSources.stream()
                .map(parser::parseMethod)
                .toList();

        return validateLowerAndEmit(kernelMethod, helperMethods);
    }

    public String validateLowerAndEmit(ParsedGpuMethod kernelMethod, List<ParsedGpuMethod> helperMethods) {
        validator.validateKernel(kernelMethod, helperMethods);

        List<GpuIrCompiledMethod> compiledMethods = lowerer.lower(kernelMethod, helperMethods);
        List<GpuIrCompiledMethod> compiledHelpers = compiledMethods.subList(0, helperMethods.size());
        GpuIrCompiledMethod compiledKernel = compiledMethods.get(compiledMethods.size() - 1);
        return emitter.emitProgram(compiledKernel, compiledHelpers);
    }
}
