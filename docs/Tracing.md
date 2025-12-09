# Tracing & Log Binding Guide

This library keeps span context in coroutine-friendly storage so logs can opt in to carry trace IDs. The moving parts:

- **Tracer.span / traceSpan**: creates a `TraceContext`, installs it into the coroutine context, and sets `LoggingBindingStorage` to `BindToSpan`.
- **TraceContextStorage.element(...)**: platform-specific hook that preserves the active `TraceContext` across suspension/resume.
- **LoggingBindingStorage.element(...)**: platform-specific hook that preserves whether logs should bind to the current span.
- **Log.logInternal**: reads `LoggingBindingStorage`; if it is `BindToSpan` and a `TraceContext` exists, it attaches trace/span IDs to emitted `LogEvent`s.

## Execution flow (end-to-end)

1. `Tracer.span { ... }` creates a `TraceContext` (traceId, spanId, parentSpanId, spanName, source metadata).
2. The coroutine context is augmented with:
   - The `TraceContext` itself (for direct lookup).
   - `TraceContextStorage.element(...)` (to re-install the context on each resume).
   - `LoggingBindingStorage.element(BindToSpan)` (to signal logs should attach IDs).
3. Inside the span, any `Log.*` call checks `LoggingBindingStorage`. If bound, it pulls the current `TraceContext` and writes trace/span IDs into the `LogEvent`.
4. When the span completes, we emit SPAN_END with duration and any error info.

## Platform propagation strategies

- **JVM / Android**: ThreadLocal stores the current context/binding; `ThreadContextElement` saves/restores it on dispatch.
- **iOS / Native**: ThreadLocal stores values; a continuation wrapper re-installs the captured context when the coroutine resumes on any thread.
- **JS / Wasm**: Single-threaded; values are tucked directly into the coroutine context (no ThreadLocal needed).

## Stack traces in structured logs

- `stack_trace` is always the last field and is quoted with real newlines preserved (no `\n` escaping). Quotes inside the stack are escaped (`\"`). The value starts with a newline so the exception header begins on its own line.
- Platform backends no longer print the throwable separately; read the stack from the structured suffix (or via the parser/CLI).
- Logcat may wrap long lines; the parser coalesces until it sees `}|`, so multiline stacks are still parsed correctly.
- Canonical structured fields (aliases removed): `ts`, `lvl`, `trace`, `span`, `parent`, `ev`, `name`, `dur`, `head`, `log`, `src_comp`, `src_op`, `src_hint`, `file`, `line`, `fn`, `svc`, `env`, `thread`, `stack_trace`, plus any custom attributes. `head` is a short message snippet; rendered human text comes from the prefix.

## When do logs bind to spans?

- Binding is on when the current coroutine context includes `LoggingBindingStorage.element(BindToSpan)` (set by `Tracer.span`).
- Binding is off when you log outside spans or manually set `LoggingBindingStorage` to `Unbound`.
- Even with binding on, `Log.logInternal` still filters by `LoggerConfig.minLevel` and `LoggerConfig.filter`.

## Quick usage

```kotlin
suspend fun doWork() = traceSpan("App", "loadData") {
    log { "fetching data" }      // bound to span (trace/span IDs present)
    val result = repo.fetch()
    log(Level.DEBUG) { "result=${result.size}" }
}

// Outside a span:
Log.i { "started app" }          // unbound; no trace/span IDs attached
```
