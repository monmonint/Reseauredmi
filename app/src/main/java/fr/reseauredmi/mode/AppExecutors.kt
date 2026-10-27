package fr.reseauredmi.mode

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object AppExecutors {
    val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mode-reseau").apply { isDaemon = true }
    }
}
