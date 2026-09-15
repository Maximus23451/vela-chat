package com.vela.chat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.vela.chat.data.settings.AppSettings
import com.vela.chat.data.settings.BubbleStyle
import com.vela.chat.data.settings.ChatDensity
import com.vela.chat.ui.theme.nova.NovaTheme

/** How much vertical room chat bubbles get; consumed by the chat message list. */
val LocalChatDensity = staticCompositionLocalOf { ChatDensity.COZY }

/** Corner style of chat bubbles; consumed by the chat message list. */
val LocalBubbleStyle = staticCompositionLocalOf { BubbleStyle.ROUNDED }

/**
 * Legacy entry point kept for all existing call sites: a thin delegating
 * wrapper around [NovaTheme] that additionally provides the chat-specific
 * [LocalChatDensity] and [LocalBubbleStyle] locals with unchanged semantics.
 * New code may call [NovaTheme] directly.
 */
@Composable
fun VelaTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    NovaTheme(settings = settings) {
        CompositionLocalProvider(
            LocalChatDensity provides settings.chatDensity,
            LocalBubbleStyle provides settings.bubbleStyle,
            content = content,
        )
    }
}
