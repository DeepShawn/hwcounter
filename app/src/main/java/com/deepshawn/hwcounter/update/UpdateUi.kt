package com.deepshawn.hwcounter.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private fun mb(bytes: Long) = if (bytes <= 0) "" else "%.1f MB".format(bytes / 1048576.0)

/** 打开 App 和每次回到前台时自动检查（Updater 内部限制频率）；有结果时弹窗 */
@Composable
fun UpdateHost() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) scope.launch { Updater.check(ctx.applicationContext) } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    UpdateDialog()
}

/** 新版本弹窗：说明 → 下载进度 → 安装 */
@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shown = Updater.prompt != null
    val st = Updater.status
    val colors = MiuixTheme.colorScheme
    val release = when (st) {
        is Updater.Status.Available -> st.release
        is Updater.Status.Downloading -> st.release
        is Updater.Status.Ready -> st.release
        else -> (Updater.prompt as? Updater.Status.Available)?.release ?: (Updater.prompt as? Updater.Status.Ready)?.release
    }
    val close = { Updater.prompt = null }
    SuperDialog(
        show = shown && release != null,
        title = if (st is Updater.Status.Ready) "新版本已下载好" else "发现新版本 v${release?.version ?: ""}",
        summary = "当前 v${Updater.current} → 最新 v${release?.version ?: ""}" + (release?.size?.let { s -> mb(s).takeIf { it.isNotEmpty() }?.let { " · $it" } } ?: ""),
        onDismissRequest = close,
    ) {
        Column {
            if (release != null && release.notes.isNotBlank()) {
                Text(
                    release.notes.lines().filterNot { it.startsWith("### ") }.joinToString("\n").trim(),
                    fontSize = 13.sp, color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                )
            }
            when (st) {
                is Updater.Status.Downloading -> {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                    Text("正在下载 ${(st.progress * 100).roundToInt()}%，可以关掉弹窗，下载好会再提示", fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 6.dp))
                }
                is Updater.Status.Failed -> Text(st.message, fontSize = 12.sp, color = colors.error, modifier = Modifier.padding(top = 10.dp))
                else -> {}
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(if (st is Updater.Status.Downloading) "后台下载" else "稍后", onClick = close, modifier = Modifier.weight(1f))
                TextButton(
                    when (st) {
                        is Updater.Status.Ready -> "立即安装"
                        is Updater.Status.Downloading -> "下载中…"
                        is Updater.Status.Failed -> "重试"
                        else -> "下载更新"
                    },
                    enabled = st !is Updater.Status.Downloading,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when (st) {
                            is Updater.Status.Ready -> if (Updater.install(ctx, st.file)) close() else
                                android.widget.Toast.makeText(ctx, "请允许“安装未知应用”，返回后再点一次“立即安装”", android.widget.Toast.LENGTH_LONG).show()
                            else -> release?.let { r -> scope.launch { Updater.download(ctx.applicationContext, r) } }
                        }
                    },
                )
            }
            if (release != null && st !is Updater.Status.Downloading) {
                TextButton("跳过这个版本", onClick = { Updater.skip(ctx, release.version); close() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

/** 设置页“软件更新”分组内容 */
@Composable
fun ColumnScope.UpdateSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf(Updater.prefs(ctx)) }
    fun set(f: (Updater.Prefs) -> Updater.Prefs) { p = f(Updater.prefs(ctx)); Updater.setPrefs(ctx, p) }
    val st = Updater.status
    LaunchedEffect(st) { p = Updater.prefs(ctx) }
    val summary = when (st) {
        Updater.Status.Idle -> "当前 v${Updater.current}" + if (p.lastCheck > 0) " · 上次检查 ${java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(java.util.Date(p.lastCheck))}" else ""
        Updater.Status.Checking -> "正在检查…"
        is Updater.Status.UpToDate -> "已是最新版本 v${Updater.current}"
        is Updater.Status.Available -> "发现新版本 v${st.release.version}，点击查看"
        is Updater.Status.Downloading -> "正在下载 v${st.release.version}：${(st.progress * 100).roundToInt()}%"
        is Updater.Status.Ready -> "v${st.release.version} 已下载好，点击安装"
        is Updater.Status.Failed -> st.message
    }
    SuperArrow(
        title = "检查更新", summary = summary,
        onClick = {
            when (st) {
                is Updater.Status.Available, is Updater.Status.Ready, is Updater.Status.Downloading -> Updater.prompt = st
                Updater.Status.Checking -> {}
                else -> scope.launch { Updater.check(ctx.applicationContext, manual = true) }
            }
        },
    )
    SuperSwitch(checked = p.autoCheck, onCheckedChange = { v -> set { it.copy(autoCheck = v) } }, title = "自动检查更新", summary = "打开 App 时检查（最多每 6 小时一次）")
    SuperSwitch(
        checked = p.autoDownload, onCheckedChange = { v -> set { it.copy(autoDownload = v) } }, enabled = p.autoCheck,
        title = "自动下载更新", summary = "发现新版本后在后台下载，下载好再提示安装",
    )
    SuperSwitch(
        checked = p.wifiOnly, onCheckedChange = { v -> set { it.copy(wifiOnly = v) } }, enabled = p.autoCheck && p.autoDownload,
        title = "仅 Wi-Fi 下自动下载", summary = "用流量时只提示，不自动下载",
    )
    if (p.skip.isNotEmpty()) SuperArrow(title = "已跳过 v${p.skip}", summary = "点击取消跳过，下次检查会再提示", onClick = { set { it.copy(skip = "") } })
}
