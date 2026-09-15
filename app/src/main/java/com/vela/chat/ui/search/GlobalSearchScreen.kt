package com.vela.chat.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.data.local.entity.ConversationWithPreview
import com.vela.chat.data.local.entity.MessageSearchResult
import com.vela.chat.ui.components.nova.NovaEmptyState
import com.vela.chat.ui.components.nova.NovaSectionHeader
import com.vela.chat.ui.components.nova.NovaSkeletonList
import com.vela.chat.ui.components.nova.NovaTopBar
import com.vela.chat.ui.theme.nova.NovaTokens

/**
 * Global search: one query across conversations (titles/previews), message
 * bodies (matched substring bolded) and profile names/models. Tapping a hit
 * opens the parent conversation.
 */
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val results = state.results

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            NovaTopBar(
                title = "Search",
                subtitle = "Chats, messages & profiles",
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search everything…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NovaTokens.Spacing.md, vertical = NovaTokens.Spacing.xs),
            )

            when {
                state.showSkeleton -> {
                    Column(Modifier.padding(NovaTokens.Spacing.lg)) {
                        NovaSkeletonList(rows = 5)
                    }
                }
                state.query.isBlank() -> {
                    NovaEmptyState(
                        icon = Icons.Rounded.Search,
                        title = "Search everything",
                        subtitle = "Conversation titles, message contents and profile names & models.",
                    )
                }
                state.showEmptyResults -> {
                    NovaEmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No results",
                        subtitle = "Nothing matches “${state.query}”. Try a shorter query.",
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = NovaTokens.Spacing.md,
                            vertical = NovaTokens.Spacing.sm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.xs),
                    ) {
                        if (results.conversations.isNotEmpty()) {
                            item(key = "header-conversations") {
                                NovaSectionHeader("Conversations (${results.conversations.size})")
                            }
                            items(results.conversations, key = { "c-${it.id}" }) { convo ->
                                ConversationHitRow(convo = convo, onOpen = { onOpenConversation(convo.id) })
                            }
                        }
                        if (results.messages.isNotEmpty()) {
                            item(key = "header-messages") {
                                NovaSectionHeader("Messages (${results.messages.size})")
                            }
                            items(results.messages, key = { "m-${it.messageId}" }) { hit ->
                                MessageHitRow(
                                    hit = hit,
                                    query = state.query,
                                    onOpen = { onOpenConversation(hit.conversationId) },
                                )
                            }
                        }
                        if (results.profiles.isNotEmpty()) {
                            item(key = "header-profiles") {
                                NovaSectionHeader("Profiles (${results.profiles.size})")
                            }
                            items(results.profiles, key = { "p-${it.id}" }) { profile ->
                                ProfileHitRow(profile = profile)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A conversation hit: title + preview, opens the conversation. */
@Composable
private fun ConversationHitRow(convo: ConversationWithPreview, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hitRowBackground()
            .clickable(onClick = onOpen)
            .padding(NovaTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
    ) {
        Icon(
            Icons.Rounded.ChatBubbleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                convo.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            convo.preview?.takeIf(String::isNotBlank)?.let { preview ->
                Text(
                    preview.replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A message hit: parent conversation title, the message content with the first
 * matched substring bolded; opens the conversation.
 */
@Composable
private fun MessageHitRow(hit: MessageSearchResult, query: String, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hitRowBackground()
            .clickable(onClick = onOpen)
            .padding(NovaTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                hit.conversationTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                highlightMatch(hit.content.replace("\n", " "), query),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A profile hit: name, provider and its configured model. */
@Composable
private fun ProfileHitRow(profile: com.vela.chat.domain.model.ApiProfile) {
    Row(
        Modifier
            .fillMaxWidth()
            .hitRowBackground()
            .padding(NovaTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NovaTokens.Spacing.sm),
    ) {
        Icon(
            Icons.Rounded.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                profile.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(profile.providerType.label, profile.model).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Rounded background treatment shared by the hit rows (composable color read). */
@Composable
private fun Modifier.hitRowBackground(): Modifier = this.background(
    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = ROW_BACKGROUND_ALPHA),
    shape = RoundedCornerShape(NovaTokens.Shape.small),
)

/**
 * Builds the content string with the first (case-insensitive) occurrence of
 * [query] bolded; without a match the plain text is returned.
 */
private fun highlightMatch(content: String, query: String): AnnotatedString {
    val trimmed = query.trim()
    val index = if (trimmed.isEmpty()) -1 else content.indexOf(trimmed, ignoreCase = true)
    if (index < 0) return buildAnnotatedString { append(content) }
    return buildAnnotatedString {
        append(content.substring(0, index))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(content.substring(index, index + trimmed.length))
        }
        append(content.substring(index + trimmed.length))
    }
}

/** Translucency of the hit-row background fill. */
private const val ROW_BACKGROUND_ALPHA = 0.5f
