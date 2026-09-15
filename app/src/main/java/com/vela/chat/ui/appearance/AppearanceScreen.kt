package com.vela.chat.ui.appearance

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.chat.data.settings.AccentColor
import com.vela.chat.data.settings.BubbleStyle
import com.vela.chat.data.settings.ChatDensity
import com.vela.chat.data.settings.ThemeMode
import com.vela.chat.data.settings.ThemePreset
import com.vela.chat.ui.components.BackScaffold
import com.vela.chat.ui.components.SectionHeader
import com.vela.chat.ui.components.ToggleRow
import com.vela.chat.ui.settings.SettingsViewModel
import com.vela.chat.ui.theme.LocalBubbleStyle
import com.vela.chat.ui.theme.LocalChatDensity
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settingsOpt by viewModel.settings.collectAsStateWithLifecycle()
    val settings = settingsOpt ?: return

    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    BackScaffold(title = "Theme & colors", onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            SectionHeader("Theme Mode")
            ThemeMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.themeMode == mode, onClick = { viewModel.setThemeMode(mode) })
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.themeMode == mode, onClick = { viewModel.setThemeMode(mode) })
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "Follow system"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (supportsDynamic) {
                ToggleRow(
                    "Dynamic color",
                    settings.dynamicColor,
                    viewModel::setDynamicColor,
                    "Use Material You wallpaper colors (Android 12+)",
                )
            }

            ToggleRow(
                "AMOLED black",
                settings.amoledBlack,
                viewModel::setAmoled,
                "Pure black backgrounds in dark mode"
            )

            // Separate selection of presets
            if (settings.themeMode == ThemeMode.SYSTEM || settings.themeMode == ThemeMode.LIGHT) {
                SectionHeader("Light Theme Style")
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.lightThemePreset == preset,
                            onClick = { viewModel.setLightThemePreset(preset) },
                            label = { Text(preset.name.replace("_", " ").lowercase().capitalize()) }
                        )
                    }
                }
            }

            if (settings.themeMode == ThemeMode.SYSTEM || settings.themeMode == ThemeMode.DARK) {
                SectionHeader("Dark Theme Style")
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.darkThemePreset == preset,
                            onClick = { viewModel.setDarkThemePreset(preset) },
                            label = { Text(preset.name.replace("_", " ").lowercase().capitalize()) }
                        )
                    }
                }
            }

            // Custom Accent Color — only has any visual effect on ThemePreset.MATERIAL_YOU
            // (see NovaPalettes.schemeForPreset: seedColor is unused by every named preset,
            // which render their own fixed, curated colors by design). Dynamic color, when
            // active, also overrides the seed. Mirrors NovaTheme's useDynamicColor logic,
            // inverted, so the picker only appears when picking a swatch would do anything.
            val dynamicColorActive = settings.dynamicColor && supportsDynamic
            val lightAccentApplies = settings.lightThemePreset == ThemePreset.MATERIAL_YOU && !dynamicColorActive
            val darkAccentApplies = settings.darkThemePreset == ThemePreset.MATERIAL_YOU && !dynamicColorActive
            val showAccentPicker = when (settings.themeMode) {
                ThemeMode.LIGHT -> lightAccentApplies
                ThemeMode.DARK -> darkAccentApplies
                ThemeMode.SYSTEM -> lightAccentApplies || darkAccentApplies
            }

            SectionHeader("Accent Color")
            if (showAccentPicker) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccentColor.entries.forEach { accent ->
                        val selected = settings.accentColor == accent && settings.customAccentColor == null
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(accent.seed))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    viewModel.setCustomAccentColor(null)
                                    viewModel.setAccent(accent)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                    }
                }

                OutlinedTextField(
                    value = settings.customAccentColor.orEmpty(),
                    onValueChange = { value ->
                        // Validate hex format dynamically
                        if (value.isEmpty() || value.matches(Regex("^#[0-9a-fA-F]{0,6}$"))) {
                            viewModel.setCustomAccentColor(value.ifEmpty { null })
                        }
                    },
                    label = { Text("Custom Accent HEX Picker") },
                    placeholder = { Text("#6750A4") },
                    singleLine = true,
                    trailingIcon = {
                        if (!settings.customAccentColor.isNullOrEmpty()) {
                            IconButton(onClick = { viewModel.setCustomAccentColor(null) }) {
                                Icon(Icons.Rounded.Close, "Clear custom color")
                            }
                        }
                    },
                    supportingText = { Text("Enter a hex code (e.g. #BD93F9) to override accent colors") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    if (dynamicColorActive) {
                        "Not available: dynamic color is on, so the app follows your wallpaper's " +
                            "colors instead. Turn dynamic color off above, or switch the theme style " +
                            "above to \"Material you\", to pick an accent."
                    } else {
                        "Not available: the theme style selected above is a named preset with its " +
                            "own fixed colors. Switch it to \"Material you\" to pick a custom accent."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            // Bubble style
            SectionHeader("Message Bubble Style")
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BubbleStyle.entries.toList()) { style ->
                    FilterChip(
                        selected = settings.bubbleStyle == style,
                        onClick = { viewModel.setBubbleStyle(style) },
                        label = { Text(style.name.replace("_", " ").lowercase().capitalize()) }
                    )
                }
            }

            // Font size
            SectionHeader("Text Scale")
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                val labels = listOf("Small (85%)", "Normal (100%)", "Large (115%)", "XL (130%)")
                val steps = listOf(0.85f, 1.0f, 1.15f, 1.30f)
                val currentIndex = steps.indexOfFirst { (it - settings.fontSizeScale).let { d -> d < 0.05f && d > -0.05f } }.coerceAtLeast(1)
                
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { index ->
                        viewModel.setFontSizeScale(steps[index.roundToInt()])
                    },
                    valueRange = 0f..3f,
                    steps = 2
                )
                Text(
                    text = labels[currentIndex],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Chat density
            SectionHeader("Layout Spacing Density")
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ChatDensity.entries.toList()) { density ->
                    FilterChip(
                        selected = settings.chatDensity == density,
                        onClick = { viewModel.setChatDensity(density) },
                        label = { Text(density.name.lowercase().capitalize()) }
                    )
                }
            }

            // Custom Wallpaper
            SectionHeader("Conversation Wallpaper")
            OutlinedTextField(
                value = settings.customWallpaperPath.orEmpty(),
                onValueChange = { viewModel.setCustomWallpaperPath(it.ifEmpty { null }) },
                label = { Text("Custom Background Wallpaper Path") },
                placeholder = { Text("file:///storage/emulated/0/Download/wallpaper.jpg") },
                singleLine = true,
                trailingIcon = {
                    if (!settings.customWallpaperPath.isNullOrEmpty()) {
                        IconButton(onClick = { viewModel.setCustomWallpaperPath(null) }) {
                            Icon(Icons.Rounded.Close, "Clear wallpaper")
                        }
                    }
                },
                supportingText = { Text("Accepts local file paths or web image URLs.") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            )

            SectionHeader("Real-time Preview")
            PreviewBubbles()
        }
    }
}

@Composable
private fun PreviewBubbles() {
    val density = LocalChatDensity.current
    val bubbleStyle = LocalBubbleStyle.current

    val itemHorizontalPadding = when (density) {
        ChatDensity.COMPACT -> 8.dp
        ChatDensity.COZY -> 12.dp
        ChatDensity.ROOMY -> 16.dp
    }
    val itemVerticalPadding = when (density) {
        ChatDensity.COMPACT -> 3.dp
        ChatDensity.COZY -> 6.dp
        ChatDensity.ROOMY -> 10.dp
    }

    val bubbleShapeUser = when (bubbleStyle) {
        BubbleStyle.ROUNDED -> RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp)
        BubbleStyle.SEMI_ROUNDED -> RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp)
        BubbleStyle.SHARP -> RoundedCornerShape(2.dp)
    }

    val bubbleShapeAssistant = when (bubbleStyle) {
        BubbleStyle.ROUNDED -> RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)
        BubbleStyle.SEMI_ROUNDED -> RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp)
        BubbleStyle.SHARP -> RoundedCornerShape(2.dp)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding), horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .clip(bubbleShapeUser)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("How does the new style look?", color = MaterialTheme.colorScheme.onPrimary) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding)) {
            Box(
                Modifier
                    .clip(bubbleShapeAssistant)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text("It looks stunning, sleek, and highly responsive!", color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

// Simple extension helper
private fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
