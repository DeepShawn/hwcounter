package com.deepshawn.hwcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
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
    return this.appear().padding(horizontal = 12.dp).fillMaxWidth()
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

/**
 * 操作面板里的一项。[icon]（emoji）和 [tint] 让这一项带一个彩色图标块；[section] 相同的项放在同一张卡片里，卡片上方显示分组名。
 */
data class SheetAction(
    val title: String, val summary: String? = null, val enabled: Boolean = true, val danger: Boolean = false,
    val icon: String? = null, val tint: Color? = null, val section: String? = null, val onClick: () -> Unit,
)

/** 底部操作面板（代替下拉菜单，手指更好够到）：分组卡片 + 彩色图标 + 标题说明 */
@Composable
fun ActionSheet(show: Boolean, title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    SuperBottomSheet(show = show, title = title, onDismissRequest = onDismiss) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            val groups = actions.groupBy { it.section }
            groups.entries.forEachIndexed { gi, (section, items) ->
                if (section != null) Text(section, Modifier.padding(start = 12.dp, top = if (gi == 0) 0.dp else 12.dp, bottom = 6.dp), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = colors.onSurfaceVariantSummary)
                else if (gi > 0) Spacer(Modifier.height(12.dp))
                Card {
                    items.forEachIndexed { i, a ->
                        val tint = if (a.danger) colors.error else a.tint ?: colors.primary
                        Row(
                            Modifier.fillMaxWidth().then(if (a.enabled) Modifier.clickable { onDismiss(); a.onClick() } else Modifier)
                                .alpha(if (a.enabled) 1f else 0.4f).padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (a.icon != null) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { LineIcon(a.icon, tint, size = 20.dp) }
                                Spacer(Modifier.width(14.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(a.title, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = if (a.danger) colors.error else colors.onSurface)
                                if (!a.summary.isNullOrBlank()) Text(a.summary, fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 2.dp))
                            }
                            Text("›", Modifier.padding(start = 10.dp), fontSize = 22.sp, color = colors.onSurfaceVariantSummary)
                        }
                        if (i < items.lastIndex) Box(Modifier.padding(start = if (a.icon != null) 70.dp else 16.dp).fillMaxWidth().height(0.6.dp).background(colors.dividerLine))
                    }
                }
            }
        }
    }
}
