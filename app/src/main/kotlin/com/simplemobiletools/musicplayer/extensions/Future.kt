package com.simplemobiletools.musicplayer.extensions

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun <T> ListenableFuture<T>.getOrNull(timeout: Long = 15000L, unit: TimeUnit = TimeUnit.MILLISECONDS) = try {
    get(timeout, unit) as T
} catch (e: CancellationException) {
    null
} catch (e: ExecutionException) {
    null
} catch (e: TimeoutException) {
    null
}
