package com.beust.kobalt.maven

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class CompletedFuture<T>(val value: T) : Future<T> {
    override fun cancel(mayInterruptIfRunning: Boolean) = true
    override fun get(): T = value
    override fun get(timeout: Long, unit: TimeUnit): T = value
    override fun isCancelled(): Boolean = false
    override fun isDone(): Boolean = true
}

