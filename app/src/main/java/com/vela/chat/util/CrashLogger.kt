package com.vela.chat.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.vela.chat.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a process-wide uncaught-exception handler that writes the full stack
 * trace to a file before the app dies, so crashes can be diagnosed without adb.
 *
 * The previous (system) handler is still invoked afterwards, so the OS crash
 * dialog and normal behaviour are unchanged — this only *adds* a log.
 *
 * The file is written to the app's external files dir:
 *   /storage/emulated/0/Android/data/<applicationId>/files/vela-crash.log
 */
object CrashLogger {

    /** The most-recent, not-yet-acknowledged crash. */
    const val FILE_NAME = "vela-crash.log"

    /** The previous crash, retained for export after the user dismisses it. */
    const val ARCHIVE_FILE_NAME = "vela-crash-last.log"

    /** Directory crash logs live in (external files dir, browsable in file managers). */
    fun logDir(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    /** The best log file available to export, or null if none exists. */
    fun exportableLog(context: Context): File? {
        val current = File(logDir(context), FILE_NAME)
        if (current.exists()) return current
        val archived = File(logDir(context), ARCHIVE_FILE_NAME)
        return if (archived.exists()) archived else null
    }

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val rt = Runtime.getRuntime()
                val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                val maxMb = rt.maxMemory() / (1024 * 1024)
                val report = buildString {
                    appendLine("==== V.E.L.A. crash report ====")
                    appendLine("Time:     ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                    appendLine("App:      ${BuildConfig.APPLICATION_ID}  v${BuildConfig.VERSION_NAME}  (${BuildConfig.BUILD_TYPE})")
                    appendLine("Device:   ${Build.MANUFACTURER} ${Build.MODEL}  (${Build.DEVICE})")
                    appendLine("Android:  ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("ABIs:     ${Build.SUPPORTED_ABIS.joinToString()}")
                    appendLine("Heap:     ${usedMb} MB used / ${maxMb} MB max")
                    appendLine("Thread:   ${thread.name} (id ${thread.id})")
                    appendLine()
                    appendLine("---- stack trace (with caused-by chain) ----")
                    append(Log.getStackTraceString(throwable))
                }

                File(logDir(appContext), FILE_NAME).writeText(report)
                Log.e("VELA", "Crash written to ${logDir(appContext)}/$FILE_NAME", throwable)
            }
            // Preserve default behaviour (system crash dialog, process kill).
            previous?.uncaughtException(thread, throwable)
        }
    }
}
