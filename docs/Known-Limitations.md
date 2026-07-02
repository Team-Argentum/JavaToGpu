# Known Limitations

JavaToGpu is a restricted GPU-safe subset compiler, not a general Java-to-GPU runtime.

## Language Limits

- `@GPU` entry methods currently return `void`.
- Results should be written through output buffers or other supported output parameters.
- Arbitrary Java object allocation is not supported inside GPU code.
- Virtual dispatch, interface dispatch, dynamic dispatch, and arbitrary Java library calls are not supported inside GPU code.
- Exceptions, monitors, synchronization blocks, recursion, and general heap/object-graph semantics are not supported.
- Object arrays are not supported as a general language feature.

## ABI And Data Model Limits

- Arrays inside `@GPUStruct` fields are not supported.
- General source-level union authoring is not supported.
- Some low-level OpenCL concepts require explicit wrappers, qualifiers, or pointer-view patterns.
- Standalone host-side create/upload/readback helpers for every possible OpenCL image family are not part of the alpha API.

## Backend Limits

- OpenCL is the active backend.
- CUDA is planned but not implemented.
- The current local evidence set is NVIDIA OpenCL focused.
- Intel and AMD OpenCL validation must still be run on real hardware before cross-vendor production claims.

## ASM Frontend Limits

- The structured ASM frontend is for intentionally generated canonical bytecode.
- It is not a general JVM decompiler.
- Arbitrary JVM methods should be expected to fail unless they match the supported subset.

## Stability Limits

- Public API details can change before beta.
- Generated launcher shape can change before beta.
- Validation artifacts prove the tested machine and commit, not universal hardware behavior.

## Practical Rule

If a construct cannot be lowered predictably to OpenCL C and marshalled safely through the current ABI, JavaToGpu should reject it with a diagnostic instead of accepting it implicitly.

## Related Documents

- [Language Contract](Language-Contract.md)
- [ASM Contract](ASM-Contract.md)
- [OpenCL Data Model](OpenCL-Data-Model.md)
- [Troubleshooting](Troubleshooting.md)
