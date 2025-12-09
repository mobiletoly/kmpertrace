package dev.goquick.kmpertrace.log

@PublishedApi
internal actual fun currentThreadNameOrNull(): String? = Thread.currentThread().name // Android threads are named by default
