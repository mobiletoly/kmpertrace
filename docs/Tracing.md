# Tracing & Log Binding Guide

This library keeps span context in coroutine-friendly storage so logs can opt in to carry trace IDs. The moving parts:

- **Tracer.span / traceSpan**: creates a `TraceContext`, installs it into the coroutine context, and sets `LoggingBindingStorage` to `BindToSpan`.
- **TraceContextStorage.element(...)**: platform-specific hook that preserves the active `TraceContext` across suspension/resume.
- **LoggingBindingStorage.element(...)**: platform-specific hook that preserves whether logs should bind to the current span.
- **Log.logInternal**: reads `LoggingBindingStorage`; if it is `BindToSpan` and a `TraceContext` exists, it attaches trace/span IDs to emitted log records, renders the structured suffix, and emits a `LogRecord` into configured sinks.

## Execution flow (end-to-end)

1. `Tracer.span { ... }` creates a `TraceContext` (traceId, spanId, parentSpanId, spanName, source metadata).
2. The coroutine context is augmented with:
   - The `TraceContext` itself (for direct lookup).
   - `TraceContextStorage.element(...)` (to re-install the context on each resume).
   - `LoggingBindingStorage.element(BindToSpan)` (to signal logs should attach IDs).
3. Inside the span, any `Log.*` call checks `LoggingBindingStorage`. If bound, it pulls the current `TraceContext` and writes trace/span IDs into the structured log record.
4. When the span completes, we emit SPAN_END with duration and any error info.

## Platform propagation strategies

- **JVM / Android**: ThreadLocal stores the current context/binding; `ThreadContextElement` saves/restores it on dispatch.
- **iOS / Native**: ThreadLocal stores values; a continuation wrapper re-installs the captured context when the coroutine resumes on any thread.
- **JS / Wasm**: Single-threaded; values are tucked directly into the coroutine context (no ThreadLocal needed).

## Stack traces in structured logs

- `stack_trace` is always the last field and is quoted with real newlines preserved (no `\n` escaping). Quotes inside the stack are escaped (`\"`). The value starts with a newline so the exception header begins on its own line.
- Platform sinks no longer print the throwable separately; read the stack from the structured suffix (or via the parser/CLI).
- Logcat may wrap long lines; the parser coalesces until it sees `}|`, so multiline stacks are still parsed correctly.
- Canonical structured fields (aliases removed): `ts`, `lvl`, `trace`, `span`, `parent`, `kind`, `name`, `dur`, `head`, `log`, `src_comp`, `src_op`, `src_hint`, `file`, `line`, `fn`, `svc`, `env`, `thread`, `stack_trace`, plus span attributes and error fields (`status`, `err_type`, `err_msg`). `head` is a short message snippet; rendered human text comes from the prefix.

## Span attributes

- Span attributes are key/value strings associated with spans and emitted on `SPAN_END`.
- Attribute keys use a prefix convention:
  - `a:` — normal attributes (intended to be safe to show in UIs by default).
  - `d:` — debug attributes (may contain sensitive information; gated by config).
- Keep attributes targeted and on-point: short, meaningful identifiers that you would be comfortable rendering in trace UIs.

When passing attributes via APIs (e.g. `traceSpan(..., attributes = ...)`), you do not include the
wire prefix. Use a leading `?` to mark debug attributes (e.g. `"?userEmail"`).

Attribute keys are restricted to `[A-Za-z0-9_.-]`. If a key contains invalid characters, it is
emitted as `invalid_<key_with_invalid_chars_replaced_with_underscore>`.

## Debug-only span attributes

- Span attributes whose keys start with `d:` are not emitted by default.
- To emit them (e.g. for local debugging), set `KmperTrace.configure(emitDebugAttributes = true)`.

## When do logs bind to spans?

- Binding is on when the current coroutine context includes `LoggingBindingStorage.element(BindToSpan)` (set by `Tracer.span`).
- Binding is off when you log outside spans.
- Filtering and sinks are configured via `KmperTrace.configure(...)` (e.g., `minLevel`, `filter`, `emitDebugAttributes`).

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
