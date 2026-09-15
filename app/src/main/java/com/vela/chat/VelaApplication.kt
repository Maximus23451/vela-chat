package com.vela.chat

import android.app.Application
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.vela.chat.data.repository.ApiProfileRepository
import com.vela.chat.data.repository.LibraryRepository
import com.vela.chat.data.repository.PersonaRepository
import com.vela.chat.util.CrashLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VelaApplication : Application() {

    @Inject lateinit var apiProfileRepository: ApiProfileRepository
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var personaRepository: PersonaRepository

    // A handler so an unexpected failure in background init is logged, never crashed.
    // (Without this, an uncaught exception in a launched coroutine reaches the
    // thread's default handler and would terminate the process.)
    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Background initialization failed", t)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    override fun onCreate() {
        // Install first so any crash from this point on (including first-screen
        // composition) is written to a retrievable log file.
        CrashLogger.install(this)
        super.onCreate()
        // PDFBox-Android loads its font/resource map from assets — meaningful
        // main-thread work, so it runs in the background instead of delaying
        // startup. Attachments are only processed long after launch, and a
        // device-specific init failure degrades gracefully (handled at use site).
        appScope.launch {
            runCatching { PDFBoxResourceLoader.init(applicationContext) }
                .onFailure { Log.e(TAG, "PDFBox init failed", it) }
        }

        appScope.launch {
            // Each repository call is already runCatching-guarded internally; the
            // handler above is a final safety net.
            apiProfileRepository.createDefaultIfEmpty()
            libraryRepository.seedDefaultsIfEmpty()
            personaRepository.seedDefaultsIfEmpty()
        }
    }

    private companion object {
        const val TAG = "VELA"
    }
}
