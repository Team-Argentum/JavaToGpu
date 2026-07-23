/**
 * JavaToGpu source annotations.
 *
 * <p>Use these annotations to mark GPU entry methods, helper methods, address spaces, structs, method tests, optimizer
 * intent, and backend/device constraints in ordinary Java source. The public Java facade intentionally uses
 * OpenCL-style naming, but portable annotations should be preferred over backend-specific escape hatches whenever the
 * concept is modeled.
 *
 * <p>Common user-facing groups:
 *
 * <ul>
 *     <li>Entry points and helpers: {@link net.sixik.ga_utils.javatogpu.api.annotations.GPU}, {@link net.sixik.ga_utils.javatogpu.api.annotations.CCode}, and {@link net.sixik.ga_utils.javatogpu.api.annotations.CCodeLibrary}.</li>
 *     <li>Address spaces and data model: {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUConstant}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPULocal}, and {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct}.</li>
 *     <li>Portable codegen hints: {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUWorkGroupSize}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUWorkGroupSizeHint}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorTypeHint}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUPacked}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUAligned}, and {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUAlwaysInline}.</li>
 *     <li>Runtime selection and evidence: {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUDeviceConstraint}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUFallbackVariant}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUTest}, and {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUTests}.</li>
 *     <li>Optimizer policy: {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUOptimize}.</li>
 * </ul>
 *
 * <p>Extension and compatibility groups:
 *
 * <ul>
 *     <li>Intrinsic and wrapper metadata: {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsic}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUIntrinsicLibrary}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorType}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUScalarAliasType}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerType}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerAddressSpace}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUPointerOperator}, and {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUVectorOperator}.</li>
 *     <li>Constant-data metadata: {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUConstantData} and {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUExternConstantData}.</li>
 *     <li>Backend-specific escape hatches: {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUAttribute}, {@link net.sixik.ga_utils.javatogpu.api.annotations.GPUAttributes}, {@link net.sixik.ga_utils.javatogpu.api.annotations.OpenCLAttributes}, and {@link net.sixik.ga_utils.javatogpu.api.annotations.OpenCLQualifiers}.</li>
 * </ul>
 */
package net.sixik.ga_utils.javatogpu.api.annotations;
