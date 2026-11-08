package com.scalari.fichiers.ui.vault

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val MAX_PIN = 12

/** Clavier numérique de saisie du code. [resetKey] vide la saisie quand il change. */
@Suppress("DEPRECATION")
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: String?,
    busy: Boolean,
    resetKey: Int,
    minLength: Int = 4,
    onBiometric: (() -> Unit)? = null,
    onSubmit: (String) -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(resetKey) { pin = "" }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier
            .widthIn(max = 360.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(18.dp)) {
            repeat(maxOf(minLength, pin.length)) { i ->
                val filled = i < pin.length
                val s by animateFloatAsState(if (filled) 1f else 0.6f, label = "dot")
                Box(
                    Modifier
                        .size(14.dp)
                        .scale(s)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            CircleShape,
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        when (key) {
                            "bio" -> if (onBiometric != null) {
                                FilledTonalIconButton(onClick = onBiometric, modifier = Modifier.size(72.dp), enabled = !busy) {
                                    Icon(Icons.Filled.Fingerprint, "Empreinte")
                                }
                            }

                            "del" -> TextButton(
                                onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                enabled = !busy && pin.isNotEmpty(),
                                modifier = Modifier.size(72.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Backspace, "Effacer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (pin.isEmpty()) 0.38f else 1f),
                                )
                            }

                            else -> FilledTonalIconButton(
                                onClick = {
                                    if (pin.length < MAX_PIN) {
                                        pin += key
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.size(72.dp),
                            ) { Text(key, style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledIconButton(
            onClick = { onSubmit(pin) },
            enabled = !busy && pin.length >= minLength,
            modifier = Modifier.size(64.dp),
        ) { Icon(Icons.Filled.Check, "Valider") }
    }
    }
}
