package com.vela.chat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.vela.chat.ui.theme.nova.NovaTokens

/** Shortest passphrase an encrypting export accepts — mirrors [com.vela.chat.data.backup.BackupCrypto]'s own floor. */
const val MIN_BACKUP_PASSPHRASE_LENGTH = 8

/**
 * Passphrase entry shared by every AES-256-GCM export/import flow ([BackupManager]'s
 * full backup and the API-profiles export). On export ([requireConfirm]) it enforces
 * [MIN_BACKUP_PASSPHRASE_LENGTH] and a matching confirmation field, since this
 * passphrase is the only thing protecting every secret in the file and is never
 * stored anywhere — typo it here and the file is gone for good. On import there's
 * nothing to validate client-side: a wrong passphrase (or a corrupted file) simply
 * fails to decrypt, reported by the caller.
 */
@Composable
fun PassphraseDialog(
    title: String,
    confirmLabel: String,
    requireConfirm: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val valid = if (requireConfirm) {
        passphrase.length >= MIN_BACKUP_PASSPHRASE_LENGTH && passphrase == confirm
    } else {
        passphrase.isNotEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm)) {
                if (requireConfirm) {
                    Text(
                        "This passphrase is never stored. Losing it makes the file unrecoverable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireConfirm) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm passphrase") },
                        isError = confirm.isNotEmpty() && confirm != passphrase,
                        supportingText = {
                            if (confirm.isNotEmpty() && confirm != passphrase) {
                                Text("Passphrases don't match")
                            } else {
                                Text("$MIN_BACKUP_PASSPHRASE_LENGTH+ characters")
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
