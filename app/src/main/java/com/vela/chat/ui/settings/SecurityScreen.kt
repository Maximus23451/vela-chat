package com.vela.chat.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.data.settings.AppLockMode
import com.vela.chat.ui.components.ConfirmDialog
import com.vela.chat.ui.components.PassphraseDialog
import com.vela.chat.ui.components.nova.NovaFilledChip
import com.vela.chat.ui.components.nova.NovaSectionHeader
import com.vela.chat.ui.components.nova.NovaSettingsRow
import com.vela.chat.ui.components.nova.NovaSliderRow
import com.vela.chat.ui.components.nova.NovaSwitchRow
import com.vela.chat.ui.components.nova.NovaTopBar
import com.vela.chat.ui.theme.nova.NovaTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Shortest PIN the setup dialog accepts; anything shorter is trivially guessable. */
private const val MIN_PIN_LENGTH = 4

/**
 * Privacy & security: app-lock mode selection (None / PIN / Biometric), PIN
 * setup & change, auto-lock timer, secure-screens (FLAG_SECURE) toggle, manual
 * lock, and a full encrypted backup (everything: conversations, profiles + API
 * keys, A2A peer tokens, personas, prompts, settings) via [BackupManager]. The
 * export/import passphrase is never stored — losing it makes the file unrecoverable.
 */
@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val settingsOpt by viewModel.settings.collectAsStateWithLifecycle()
    val settings = settingsOpt ?: return
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPinDialog by remember { mutableStateOf(false) }
    var showRemovePinConfirm by remember { mutableStateOf(false) }
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    // Written to a file the user picks, never passed through an Intent extra: a real
    // history easily exceeds Binder's ~1 MB transaction limit, which made the old
    // share-sheet export fail silently (FAILED BINDER TRANSACTION, no chooser shown).
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val envelope = pendingExport
        pendingExport = null
        if (uri == null || envelope == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Couldn't open the file" }
                        .use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
                }
            }
            snackbarHostState.showSnackbar(
                saved.fold(
                    onSuccess = { "Backup saved" },
                    onFailure = { "Couldn't save backup: ${it.message}" },
                ),
            )
        }
    }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val text = runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
            }.getOrNull()
            if (text != null) pendingImport = text
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val lockEnabled = settings.appLockMode != AppLockMode.NONE

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NovaTopBar(
                title = "App lock & privacy",
                subtitle = "PIN, biometrics & backups",
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NovaTokens.Spacing.md)
                .padding(bottom = NovaTokens.Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs),
        ) {
            NovaSectionHeader("App lock")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = NovaTokens.Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
            ) {
                NovaFilledChip(
                    selected = settings.appLockMode == AppLockMode.NONE,
                    onClick = { viewModel.setLockMode(AppLockMode.NONE) },
                    label = "None",
                )
                NovaFilledChip(
                    selected = settings.appLockMode == AppLockMode.PIN,
                    onClick = {
                        if (viewModel.hasPin) viewModel.setLockMode(AppLockMode.PIN) else showPinDialog = true
                    },
                    label = "PIN",
                )
                NovaFilledChip(
                    selected = settings.appLockMode == AppLockMode.BIOMETRIC,
                    onClick = { if (viewModel.deviceSecure) viewModel.setLockMode(AppLockMode.BIOMETRIC) },
                    label = "Biometric",
                )
            }
            if (!viewModel.deviceSecure) {
                Text(
                    "Biometric unlock needs a screen lock (PIN, pattern or password) set in Android settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = NovaTokens.Spacing.md),
                )
            }
            if (settings.appLockMode == AppLockMode.PIN && viewModel.hasPin) {
                NovaSettingsRow(
                    title = "Change PIN",
                    subtitle = "Replace the stored PIN (minimum $MIN_PIN_LENGTH digits)",
                    icon = Icons.Rounded.Password,
                    onClick = { showPinDialog = true },
                )
                NovaSettingsRow(
                    title = "Remove PIN",
                    subtitle = "Turns the lock off and deletes the stored PIN",
                    icon = Icons.Rounded.Lock,
                    onClick = { showRemovePinConfirm = true },
                )
            }
            if (lockEnabled) {
                NovaSliderRow(
                    title = "Auto-lock after",
                    value = settings.autoLockMinutes.toFloat(),
                    onValueChange = { viewModel.setAutoLockMinutes(it.toInt().coerceIn(AUTO_LOCK_MIN, AUTO_LOCK_MAX)) },
                    valueLabel = "${settings.autoLockMinutes} min",
                    range = AUTO_LOCK_MIN.toFloat()..AUTO_LOCK_MAX.toFloat(),
                    steps = AUTO_LOCK_MAX - AUTO_LOCK_MIN - 1,
                )
                NovaSettingsRow(
                    title = "Lock now",
                    subtitle = "Hide all content behind the lock screen immediately",
                    icon = Icons.Rounded.LockClock,
                    onClick = viewModel::lockNow,
                )
            }

            NovaSectionHeader("Privacy")
            NovaSwitchRow(
                title = "Secure screens",
                subtitle = "Blur the app in recents and block screenshots",
                icon = Icons.Rounded.ScreenLockPortrait,
                checked = settings.secureScreens,
                onCheckedChange = viewModel::setSecureScreens,
            )

            NovaSectionHeader("Backup")
            NovaSettingsRow(
                title = "Export backup",
                subtitle = "Everything, encrypted with a passphrase you set",
                icon = Icons.Rounded.Archive,
                onClick = { showExportPassphraseDialog = true },
            )
            NovaSettingsRow(
                title = "Import backup",
                subtitle = "Restore conversations, profiles, keys & settings",
                icon = Icons.Rounded.Restore,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            )
            Text(
                "Full backup: every conversation, profile (with its API key), agent token, " +
                    "persona, prompt and setting. The file is only as safe as your passphrase — " +
                    "it is never stored, and a lost passphrase makes the backup unrecoverable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = NovaTokens.Spacing.md),
            )
        }
    }

    if (showExportPassphraseDialog) {
        PassphraseDialog(
            title = "Encrypt backup",
            confirmLabel = "Export",
            requireConfirm = true,
            onConfirm = { passphrase ->
                scope.launch {
                    val envelope = viewModel.exportBackup(passphrase) ?: return@launch
                    showExportPassphraseDialog = false
                    pendingExport = envelope
                    exportLauncher.launch("vela-backup-${LocalDate.now()}.json")
                }
            },
            onDismiss = { showExportPassphraseDialog = false },
        )
    }

    pendingImport?.let { content ->
        PassphraseDialog(
            title = "Decrypt backup",
            confirmLabel = "Import",
            requireConfirm = false,
            onConfirm = { passphrase ->
                viewModel.importBackup(content, passphrase)
                pendingImport = null
            },
            onDismiss = { pendingImport = null },
        )
    }

    if (showPinDialog) {
        PinSetupDialog(
            title = if (viewModel.hasPin) "Change PIN" else "Set a PIN",
            onConfirm = { pin ->
                viewModel.savePin(pin) { saved -> if (saved) showPinDialog = false }
            },
            onDismiss = { showPinDialog = false },
        )
    }

    if (showRemovePinConfirm) {
        ConfirmDialog(
            title = "Remove PIN?",
            message = "The stored PIN is deleted and the app lock turns off.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removePin() },
            onDismiss = { showRemovePinConfirm = false },
        )
    }
}

/**
 * PIN setup/change dialog: numeric PIN plus confirmation, minimum
 * [MIN_PIN_LENGTH] digits. Confirm stays disabled until both rules hold.
 */
@Composable
private fun PinSetupDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val valid = pin.length >= MIN_PIN_LENGTH && pin == confirm && pin.all(Char::isDigit)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(MAX_PIN_ENTRY_LENGTH) },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit).take(MAX_PIN_ENTRY_LENGTH) },
                    label = { Text("Confirm PIN") },
                    isError = confirm.isNotEmpty() && confirm != pin,
                    supportingText = {
                        if (confirm.isNotEmpty() && confirm != pin) {
                            Text("PINs don't match")
                        } else {
                            Text(
                                "$MIN_PIN_LENGTH+ digits",
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Auto-lock range in minutes (Privacy & Security slider). */
private const val AUTO_LOCK_MIN = 1
private const val AUTO_LOCK_MAX = 60

/** Cap on how many digits can be typed into the PIN fields (PBKDF2 cost guard). */
private const val MAX_PIN_ENTRY_LENGTH = 12
