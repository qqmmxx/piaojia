package com.qqmmxx.piaojia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qqmmxx.piaojia.ui.theme.DefaultThemeSeed
import com.qqmmxx.piaojia.ui.theme.ThemeColors
import com.qqmmxx.piaojia.ui.theme.ThemePresets
import com.qqmmxx.piaojia.ui.theme.toHsl
import com.qqmmxx.piaojia.ui.theme.withHue
import kotlin.math.roundToInt

/**
 * 配色选择：预设色块 + 色相微调，内置一块实时预览。
 *
 * @param current 当前生效的种子色
 * @param onConfirm 点「确定」时回调，返回新的种子色
 */
@Composable
fun ThemePickerDialog(
    current: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    var draft by remember { mutableStateOf(current) }
    val hue = remember(draft) { draft.toHsl().first }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配色") },
        text = {
            Column {
                // 预览：直接用草稿色算一套配色，所见即所得
                ThemePreviewBar(seed = draft)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "预设",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 12 个预设分 3 行，每行 4 个
                ThemePresets.chunked(4).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowItems.forEach { preset ->
                            PresetSwatch(
                                color = preset.color,
                                label = preset.name,
                                selected = preset.color.toArgb() == draft.toArgb(),
                                onClick = { draft = preset.color },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "微调色相　当前 ${hue.roundToInt()}°",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = hue,
                    onValueChange = { draft = draft.withHue(it) },
                    valueRange = 0f..359f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) {
                Text("确定")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { draft = DefaultThemeSeed },
                    enabled = draft.toArgb() != DefaultThemeSeed.toArgb()
                ) {
                    Text("恢复默认")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

/** 用草稿色算一套配色，模拟「顶栏 + 正文金额」让用户直接看出效果 */
@Composable
private fun ThemePreviewBar(seed: Color) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scheme = remember(seed, dark) { ThemeColors.build(seed, dark) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "报销项目",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = scheme.onPrimaryContainer
            )
            Text(
                "设置",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onPrimaryContainer
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "总金额",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Text(
                "¥128.50",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.primary
            )
        }
    }
}

@Composable
private fun PresetSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
