package com.simplemobiletools.musicplayer.extensions

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

fun <T> ListenableFuture<T>.getOrNull() = try {
    get() as T
} catch (e: CancellationException) {
    null
} catch (e: ExecutionException) {
    null
}
