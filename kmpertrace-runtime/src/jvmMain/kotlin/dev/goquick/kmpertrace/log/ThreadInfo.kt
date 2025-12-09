package dev.goquick.kmpertrace.log

@PublishedApi
internal actual fun currentThreadNameOrNull(): String? = Thread.currentThread().name // desktop JVM exposes thread names
