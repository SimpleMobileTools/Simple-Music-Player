package org.fossify.musicplayer.objects

import java.util.concurrent.Executors

object MyExecutor {
    val myExecutor = Executors.newSingleThreadExecutor()
}
