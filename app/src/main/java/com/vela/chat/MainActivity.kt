package com.vela.chat

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.vela.chat.ui.CrashReportScreen
import com.vela.chat.ui.MainViewModel
import com.vela.chat.ui.VelaApp
import com.vela.chat.ui.lock.AppLockScreen
import com.vela.chat.ui.rememberClipboardCopier
import com.vela.chat.util.AppLockController
import com.vela.chat.util.CrashLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var appLock: AppLockController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // If the previous run crashed, a report file exists. Show it instead of
        // re-entering the (still-crashing) app, so it can be read without adb.
        val crashFile = File(getExternalFilesDir(null) ?: filesDir, CrashLogger.FILE_NAME)
        val initialCrash = runCatching { if (crashFile.exists()) crashFile.readText() else null }.getOrNull()

        // FLAG_SECURE (Privacy → secure screens): blocks screenshots & recents preview.
        viewModel.settings
            .map { it.secureScreens }
            .distinctUntilChanged()
            .onEach { secure ->
                if (secure) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            .launchIn(lifecycleScope)

        // Auto-lock lifecycle bookkeeping.
        lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> appLock.onBackgrounded()
                    androidx.lifecycle.Lifecycle.Event.ON_START ->
                        lifecycleScope.launch { appLock.onResumed() }
                    else -> Unit
                }
            },
        )

        setContent {
            var crashReport by remember { mutableStateOf(initialCrash) }
            val copy = rememberClipboardCopier()

            val report = crashReport
            if (report != null) {
                CrashReportScreen(
                    report = report,
                    onCopy = { copy(report) },
                    onDismiss = {
                        // Archive (don't delete) so it stays exportable from Settings,
                        // but stops auto-showing on the next launch.
                        runCatching {
                            val archive = File(crashFile.parentFile, CrashLogger.ARCHIVE_FILE_NAME)
                            archive.delete()
                            if (!crashFile.renameTo(archive)) crashFile.delete()
                        }
                        crashReport = null
                    },
                )
            } else {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val locked by appLock.locked.collectAsStateWithLifecycle()
                if (locked) {
                    AppLockScreen(controller = appLock)
                } else {
                    VelaApp(settings = settings)
                }
            }
        }
    }
}
