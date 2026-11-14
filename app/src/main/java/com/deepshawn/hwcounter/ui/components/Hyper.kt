package com.deepshawn.hwcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.fmt
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 懒加载列表里模拟 HyperOS 分组卡片：同组第一行上圆角、最后一行下圆角，中间直角，
 * 看起来是一张卡片，但每行仍是独立的 item（长名单也流畅）。
 */
@Composable
fun Modifier.listCell(index: Int, count: Int, radius: Dp = 16.dp, color: Color = rowTint()): Modifier {
    val top = if (index == 0) radius else 0.dp
    val bottom = if (index == count - 1) radius else 0.dp
    return this.padding(horizontal = 12.dp).fillMaxWidth()
        .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        .background(color)
}

/** 带“取消 / 确认”按钮的 HyperOS 对话框 */
@Composable
fun HyperDialog(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    summary: String? = null,
    confirmText: String = "确定",
    confirmEnabled: Boolean = true,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    SuperDialog(show = show, title = title, summary = summary, onDismissRequest = onDismiss) {
        Column {
            content()
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    confirmText, onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.weight(1f),
                    colors = if (danger) ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.error, textColor = Color.White) else ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/** 输入一个分数（修改基准分等） */
@Composable
fun NumberDialog(show: Boolean, title: String, summary: String, initial: Double, label: String = "分数", onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember(show, initial) { mutableStateOf(initial.fmt()) }
    val value = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    HyperDialog(show, title, onDismiss, confirmText = "确定", confirmEnabled = value != null, onConfirm = { value?.let(onConfirm) }) {
        Text(summary, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()))
        Spacer(Modifier.height(12.dp))
        TextField(text, { text = it }, label = label, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
    }
}

data class SheetAction(val title: String, val summary: String? = null, val enabled: Boolean = true, val danger: Boolean = false, val onClick: () -> Unit)

/** 底部操作面板（代替下拉菜单，手指更好够到） */
@Composable
fun ActionSheet(show: Boolean, title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    SuperBottomSheet(show = show, title = title, onDismissRequest = onDismiss) {
        Card(Modifier.padding(bottom = 16.dp)) {
            actions.forEach { a ->
                SuperArrow(
                    title = a.title, summary = a.summary, enabled = a.enabled,
                    titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(color = if (a.danger) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface),
                    onClick = { onDismiss(); a.onClick() },
                )
            }
        }
    }
}
