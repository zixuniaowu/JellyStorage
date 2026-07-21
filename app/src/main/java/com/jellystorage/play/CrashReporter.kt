package com.jellystorage.play

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量崩溃落盘：无需 Firebase 也能在设置里看到上次崩溃。
 * Play Vitals 仍由系统自动采集；若你后续接 Crashlytics，可与此并存。
 */
object CrashReporter {
    private const val TAG = "JellyCrash"
    private const val FILE = "last_crash.txt"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                writeCrash(app, thread, error)
            } catch (t: Throwable) {
                Log.e(TAG, "failed to write crash", t)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrashSummary(context: Context): String? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        return try {
            f.readText().lineSequence().take(8).joinToString("\n").take(400)
        } catch (_: Throwable) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (_: Throwable) {
        }
    }

    private fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val body = buildString {
            appendLine("time=$stamp")
            appendLine("thread=${thread.name}")
            appendLine("message=${error.message}")
            appendLine(sw.toString().take(8000))
        }
        File(context.filesDir, FILE).writeText(body)
        Log.e(TAG, "crash saved", error)
    }
}
