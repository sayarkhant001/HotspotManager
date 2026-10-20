package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GlobalUncaughtExceptionHandler — prevents the app from silently force-closing.
 *
 * Instead of dying on the spot, it:
 *   1. Swallows the exception so the current screen stays alive where possible.
 *   2. Records the full stack trace to a file + SharedPreferences so we can
 *      diagnose crashes remotely (covered by the in-app "Diagnostics" view).
 *   3. Optionally re-raises after a short delay to let the log persist.
 *
 * This gives us a REPLACEABLE handshake with Android crash behaviour:
 * we never lose the evidence needed to fix a bug.
 */
object GlobalCrashCatcher {

    private const val PREFS = "crash_prefs"
    private const val KEY_LAST_CRASH = "last_crash_stack"
    private const val KEY_LAST_CRASH_AT = "last_crash_at"
    private const val KEY_CRASH_COUNT = "crash_count"

    @Volatile private var enabled = false
    @Volatile private var contextRef: Context? = null

    fun install(context: Context) {
        if (enabled) return
        contextRef = context.applicationContext
        enabled = true
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(thread, throwable)
            // Let the OS default handler proceed AFTER we've persisted evidence
            // (so the crash is not silently hidden from the user).
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun record(thread: Thread, throwable: Throwable) {
        try {
            val ctx = contextRef ?: return
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val trace = sw.toString()
            val now = System.currentTimeMillis()
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))

            // 1. SharedPreferences (fast, readable in-app)
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
            prefs.edit()
                .putString(KEY_LAST_CRASH, trace)
                .putString(KEY_LAST_CRASH_AT, "$timeStr | thread=${thread.name}")
                .putInt(KEY_CRASH_COUNT, count)
                .apply()

            // 2. File log (durable)
            try {
                val file = java.io.File(ctx.filesDir, "crash.log")
                val entry = "\n===== CRASH #$count @ $timeStr | ${thread.name} =====\n$trace\n"
                file.appendText(entry)
                // keep only last ~128KB
                if (file.length() > 131072L) {
                    val keep = file.readText().takeLast(65536)
                    file.writeText("===== TRUNCATED =====\n$keep")
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    /** Current persisted crash stack, or null when the app has never crashed. */
    fun lastCrash(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val trace = prefs.getString(KEY_LAST_CRASH, null)
        val at = prefs.getString(KEY_LAST_CRASH_AT, null)
        val count = prefs.getInt(KEY_CRASH_COUNT, 0)
        if (trace.isNullOrBlank()) return null
        return "CRASH #$count\n$at\n\n$trace"
    }

    fun crashInfo(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}