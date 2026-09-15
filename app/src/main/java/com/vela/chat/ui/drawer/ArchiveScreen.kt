package com.vela.chat.ui.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.data.local.entity.ConversationWithPreview
import com.vela.chat.ui.components.nova.NovaEmptyState
import com.vela.chat.ui.components.nova.NovaTopBar
import com.vela.chat.ui.theme.nova.NovaTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Formats the archived date shown in each row. */
private val ArchivedDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, uuuu").withZone(ZoneId.systemDefault())

/**
 * Archive: every archived conversation with unarchive and delete-with-undo.
 * Tapping a row reopens it in the chat.
 */
@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingDelete) {
        val pending = pendingDelete ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted \"${pending.title}\"",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.commitPendingDelete()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NovaTopBar(
                title = "Archive",
                subtitle = "${archived.size} archived",
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        val visible = archived.filterNot { it.id == pendingDelete?.id }
        if (visible.isEmpty()) {
            Column(Modifier.padding(padding)) {
                NovaEmptyState(
                    icon = Icons.Rounded.Archive,
                    title = "Archive is empty",
                    subtitle = "Swipe a conversation left in the drawer to archive it.",
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = NovaTokens.Spacing.md,
                    vertical = NovaTokens.Spacing.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs),
            ) {
                items(visible, key = { it.id }) { convo ->
                    ArchivedRow(
                        convo = convo,
                        onOpen = { onOpenConversation(convo.id) },
                        onUnarchive = { viewModel.unarchive(convo.id) },
                        onDelete = { viewModel.swipeDelete(convo) },
                    )
                }
            }
        }
    }
}

/** One archived conversation: title, date, unarchive + delete actions. */
@Composable
private fun ArchivedRow(
    convo: ConversationWithPreview,
    onOpen: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = NovaTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                convo.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Archived ${ArchivedDateFormat.format(Instant.ofEpochMilli(convo.updatedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onUnarchive) {
            Icon(Icons.Rounded.Unarchive, contentDescription = "Unarchive")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
