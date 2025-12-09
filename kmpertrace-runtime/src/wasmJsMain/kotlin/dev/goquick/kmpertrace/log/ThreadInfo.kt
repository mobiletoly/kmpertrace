package dev.goquick.kmpertrace.log

@PublishedApi
internal actual fun currentThreadNameOrNull(): String? = null // JS/Wasm has no thread identity to report
