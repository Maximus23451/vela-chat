package com.vela.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A deliberately minimal screen (no app theming, no DI) shown on the launch
 * *after* a crash, so the captured stack trace can be read/screenshot/copied
 * without adb or a file manager.
 */
@Composable
fun CrashReportScreen(
    report: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(16.dp),
            ) {
                Text(
                    "V.E.L.A. crashed last time",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Copy or screenshot this and send it over so the cause can be fixed.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                SelectionContainer(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(
                        report,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("Copy") }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Dismiss & retry") }
                }
            }
        }
    }
}

/** Helper so MainActivity can copy the report without pulling in extra imports. */
@Composable
fun rememberClipboardCopier(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return { text -> clipboard.setText(AnnotatedString(text)) }
}
