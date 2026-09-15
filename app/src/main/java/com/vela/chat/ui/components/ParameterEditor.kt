package com.vela.chat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vela.chat.domain.model.GenerationParams
import kotlin.math.roundToInt

/**
 * Editor for the full set of sampler/generation parameters. Shared by the
 * per-chat config sheet and the global defaults screen.
 */
@Composable
fun ParameterEditor(
    params: GenerationParams,
    onChange: (GenerationParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ValueSlider(
            label = "Temperature",
            value = params.temperature,
            onValueChange = { onChange(params.copy(temperature = it)) },
            valueRange = 0f..2f,
        )
        ValueSlider(
            label = "Top P",
            value = params.topP,
            onValueChange = { onChange(params.copy(topP = it)) },
            valueRange = 0f..1f,
        )
        ValueSlider(
            label = "Top K",
            value = params.topK.toFloat(),
            onValueChange = { onChange(params.copy(topK = it.roundToInt())) },
            valueRange = 0f..100f,
            valueFormatter = { it.roundToInt().toString() },
        )
        ValueSlider(
            label = "Presence penalty",
            value = params.presencePenalty,
            onValueChange = { onChange(params.copy(presencePenalty = it)) },
            valueRange = -2f..2f,
        )
        ValueSlider(
            label = "Frequency penalty",
            value = params.frequencyPenalty,
            onValueChange = { onChange(params.copy(frequencyPenalty = it)) },
            valueRange = -2f..2f,
        )

        ToggleRow(
            label = "Limit max tokens",
            checked = params.limitMaxTokens,
            onCheckedChange = { onChange(params.copy(limitMaxTokens = it)) },
        )
        if (params.limitMaxTokens) {
            ValueSlider(
                label = "Max tokens",
                value = params.maxTokens.toFloat(),
                onValueChange = { onChange(params.copy(maxTokens = it.roundToInt())) },
                valueRange = 128f..8192f,
                valueFormatter = { it.roundToInt().toString() },
            )
        }

        ValueSlider(
            label = "Context window (messages, 0 = all)",
            value = params.contextWindowMessages.toFloat(),
            onValueChange = { onChange(params.copy(contextWindowMessages = it.roundToInt())) },
            valueRange = 0f..50f,
            valueFormatter = { if (it.roundToInt() == 0) "All" else it.roundToInt().toString() },
        )
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
