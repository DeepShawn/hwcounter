@file:OptIn(ExperimentalMaterial3Api::class)

package com.deepshawn.hwcounter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshawn.hwcounter.cloud.HistoryItem
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.summary
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.components.AppCard
import com.deepshawn.hwcounter.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 设置页里的“云端同步”区块 */
@Composable
fun CloudSection(vm: AppViewModel) {
    val kit = LocalKit.current
    val scope = rememberCoroutineScope()
    val st by vm.sync.state.collectAsStateWithLifecycle()
    var history by remember { mutableStateOf<List<HistoryItem>?>(null) }
    var confirmRestore by remember { mutableStateOf<HistoryItem?>(null) }
    var changingPw by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }

    SectionTitle("云端同步", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    AppCard(Modifier.padding(horizontal = 12.dp)) {
        if (st.email == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("未登录 · 预览模式（只读）", fontWeight = FontWeight.Bold)
                    Text("老师用邮箱登录后可修改，数据在安卓、iPad、网页、Windows 间同步；学生、家长凭邀请码注册后可模拟炒股。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { kit.nav.push(com.deepshawn.hwcounter.ui.Route.Auth(0)) }, modifier = Modifier.weight(1f)) { Text("登录") }
                FilledTonalButton(onClick = { kit.nav.push(com.deepshawn.hwcounter.ui.Route.Auth(1)) }, modifier = Modifier.weight(1f)) { Text("注册") }
            }
        } else if (st.member != null) {
            val m = st.member!!
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${m.name.ifBlank { m.username }}（${m.username}）", fontWeight = FontWeight.Bold)
                    Text(if (m.active) "普通用户 · 只读查看老师的班级数据 · 统计 → 股票 → 炒股 里用模拟币交易" else "账号已被老师停用：不能查看数据和交易", fontSize = 12.sp, color = if (m.active) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
                if (st.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Button(
                enabled = !st.busy,
                onClick = { scope.launch { runCatching { vm.sync.refreshMember(); vm.refreshPreview() }.onSuccess { kit.toast("已刷新") }.onFailure { kit.toast(it.message ?: "刷新失败") } } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("刷新数据") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { changingPw = true }) { Text("修改密码") }
                TextButton(onClick = { scope.launch { vm.sync.signOut() } }) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (st.error) Icons.Filled.CloudOff else Icons.Filled.Cloud, null, tint = if (st.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.email ?: "", fontWeight = FontWeight.Bold)
                    Text(
                        "云端版本 v${st.version}" + if (st.lastSyncAt > 0) " · 上次同步 ${fmt.format(Date(st.lastSyncAt))}" else "",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (st.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动同步")
                    Text("改动后几秒自动上传；回到 App 时检查其它设备的更新", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(st.auto, { vm.sync.setAuto(it) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许未登录预览")
                    Text("打开后，任何人不登录也能只读查看你的班级数据（学生姓名、作业、积分）；关闭即恢复私密", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    st.publicRead != false, enabled = st.publicRead != null && !st.busy,
                    onCheckedChange = { v -> scope.launch { runCatching { vm.sync.setPublic(v) }.onFailure { kit.toast(it.message ?: "设置失败") } } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !st.busy, onClick = { scope.launch { vm.sync.syncNow() } }, modifier = Modifier.weight(1f)) { Text("立即同步") }
                FilledTonalButton(
                    enabled = !st.busy,
                    onClick = { scope.launch { runCatching { history = vm.sync.history() }.onFailure { kit.toast(it.message ?: "读取失败") } } },
                    modifier = Modifier.weight(1f),
                ) { Text("历史版本") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { changingPw = true }) { Text("修改密码") }
                TextButton(onClick = { scope.launch { vm.sync.signOut() } }) { Text("退出登录（本机数据保留）", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (st.message.isNotBlank()) {
            Text(st.message, fontSize = 12.sp, color = if (st.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Text("API Key 只保存在本机，不会上传。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }

    if (changingPw) ChangePasswordDialog(vm) { changingPw = false }

    history?.let { list ->
        AlertDialog(
            onDismissRequest = { history = null },
            title = { Text("云端历史版本") },
            text = {
                if (list.isEmpty()) Text("还没有历史版本（云端数据被覆盖前自动保存，保留最近 50 份 + 90 天内每天一份）")
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.id }) { h ->
                        Column(Modifier.fillMaxWidth().clickable { confirmRestore = h }.padding(vertical = 8.dp)) {
                            Text("v${h.version} · ${h.savedAt.take(10)}", fontWeight = FontWeight.Bold)
                            Text(h.device ?: "未知设备", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { history = null }) { Text("关闭") } },
        )
    }
    confirmRestore?.let { h ->
        ConfirmDialog("恢复 v${h.version}？", "本机数据会被替换为这个历史版本，并上传为云端最新版本。当前云端版本会自动进入历史，可再恢复回来。", "恢复", { confirmRestore = null }) {
            history = null
            scope.launch { runCatching { vm.sync.restoreHistory(h.id) }.onFailure { kit.toast(it.message ?: "恢复失败") } }
        }
    }
}

/** 全局冲突对话框：两台设备都改过数据时出现 */
@Composable
fun CloudConflictDialog(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val st by vm.sync.state.collectAsStateWithLifecycle()
    val remote = st.conflict ?: return
    val local = vm.data.collectAsStateWithLifecycle().value
    fun brief(d: com.deepshawn.hwcounter.data.AppData): String {
        val n = d.classes.size
        val s = d.classes.sumOf { it.students.size }
        val a = d.classes.sumOf { it.assignments.size }
        val done = d.classes.sumOf { c -> c.assignments.sumOf { c.summary(it).handedIn } }
        return "$n 个班 · $s 名学生 · $a 次作业 · 登记 $done 人次"
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("两台设备都改过数据") },
        text = {
            Column {
                Text("本机：${brief(local)}", fontSize = 13.sp)
                Text("云端 v${st.conflictVersion}：${brief(remote)}", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("推荐“合并”：两边的班级、学生、作业和登记都保留；同一个学生同一次作业两边不一致时以本机为准。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { scope.launch { vm.sync.resolve(2) } }) { Text("合并（推荐）") }
                TextButton(onClick = { scope.launch { vm.sync.resolve(0) } }) { Text("用云端覆盖本机") }
                TextButton(onClick = { scope.launch { vm.sync.resolve(1) } }) { Text("用本机覆盖云端") }
            }
        },
    )
}


/** 修改密码：旧密码 + 两次新密码，直接提交到 Supabase */
@Composable
private fun ChangePasswordDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val kit = LocalKit.current
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val pwOpts = KeyboardOptions(keyboardType = KeyboardType.Password)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(old, { old = it }, label = { Text("旧密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = pwOpts, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(new1, { new1 = it }, label = { Text("新密码（至少 6 位）") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = pwOpts, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(new2, { new2 = it }, label = { Text("再输一次新密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = pwOpts, modifier = Modifier.fillMaxWidth())
                Text("这个账号和你的游戏共用，改完后游戏里也用新密码登录。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                err?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && old.isNotEmpty() && new1.isNotEmpty(), onClick = {
                err = when {
                    new1.length < 6 -> "新密码至少 6 位"
                    new1 != new2 -> "两次输入的新密码不一样"
                    new1 == old -> "新密码不能和旧密码一样"
                    else -> null
                }
                if (err == null) {
                    busy = true
                    scope.launch {
                        runCatching { vm.sync.changePassword(old, new1) }
                            .onSuccess { kit.toast("密码已修改"); onDismiss() }
                            .onFailure { err = it.message ?: "修改失败" }
                        busy = false
                    }
                }
            }) { Text(if (busy) "提交中…" else "确认修改") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}
