# ASM Contract

JavaToGpu includes a structured ASM frontend for integrations that already own an AST, bytecode generator, or compiler pipeline.

This frontend is not a general JVM decompiler. It expects a canonical GPU-safe bytecode subset emitted intentionally by tooling you control.

## Recommended Architecture

Use this path:

```text
your AST or IR -> canonical supported ASM -> JavaToGpu ASM frontend -> IR -> OpenCL
```

Do not rely on this path:

```text
arbitrary JVM bytecode -> automatic recovery -> GPU kernel
```

## Expected Bytecode Shape

- Static methods only.
- Predictable control-flow graphs.
- Explicit locals for important intermediate values.
- Stable stack shapes at merge points.
- `INVOKESTATIC` calls to supported helper owners.
- Whitelisted construction only for supported wrappers and value types.
- Math and backend operations normalized to `GPU.*` or known helper methods.

## Unsupported Bytecode Shapes

- `invokevirtual`, `invokeinterface`, and `invokedynamic` as general dispatch mechanisms.
- Exception tables and exception-driven control flow.
- Monitor enter/exit and Java synchronization semantics.
- General object allocation and object graph access.
- Recursion and unbounded dynamic call graphs.
- Bytecode patterns that cannot be proven GPU-safe before lowering.

## Best Practices

- Keep the operand stack shallow.
- Prefer simple locals over clever stack manipulation.
- Normalize loops before emission.
- Keep helper signatures explicit and stable.
- Emit predictable casts and primitive operations.
- Treat validation errors as frontend contract feedback, not runtime surprises.

## Future Direction

A broader arbitrary-ASM ingestion frontier is planned, but it is not part of the current alpha contract. The current frontend is deliberately conservative so compiler integrations can generate predictable GPU-safe input.

## Related Documents

- [Language Contract](Language-Contract.md)
- [Known Limitations](Known-Limitations.md)
- [API Overview](API-Overview.md)
