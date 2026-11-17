package com.deepshawn.hwcounter.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.components.HCard
import com.deepshawn.hwcounter.ui.components.HTabs
import com.deepshawn.hwcounter.ui.components.MiuixPage
import com.deepshawn.hwcounter.ui.components.pressScale
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ---------------- 通用表单部件（登录、注册、下单共用） ----------------

/** 圆角输入框：上方标签、左侧图标、聚焦时描边；密码框带显示 / 隐藏 */
@Composable
fun FormField(
    value: String, onChange: (String) -> Unit, label: String, icon: ImageVector?,
    modifier: Modifier = Modifier, placeholder: String = "", password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text, error: String? = null, hint: String? = null,
    mono: Boolean = false, letterSpacing: TextUnit = TextUnit.Unspecified, fontSize: TextUnit = 16.sp,
    textAlign: TextAlign = TextAlign.Start, height: Dp = 52.dp, trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val stroke = when { error != null && value.isNotEmpty() -> colors.error; focused -> colors.primary; else -> Color.Transparent }
    Column(modifier) {
        if (label.isNotEmpty()) Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(height).clip(shape).background(colors.secondaryContainer).border(1.5.dp, stroke, shape).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { Icon(icon, null, tint = if (focused) colors.primary else colors.onSurfaceVariantSummary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val style = TextStyle(
                    fontSize = fontSize, color = colors.onSurface, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    letterSpacing = letterSpacing, textAlign = textAlign, fontWeight = if (mono) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = style.copy(color = colors.onSurfaceVariantSummary.copy(alpha = 0.7f), fontFamily = FontFamily.Default, letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.Normal), modifier = Modifier.fillMaxWidth(), maxLines = 1)
                BasicTextField(
                    value, onChange, singleLine = true, textStyle = style, cursorBrush = SolidColor(colors.primary),
                    visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                )
            }
            trailing?.invoke()
            if (password) Icon(
                if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "隐藏密码" else "显示密码",
                tint = colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 8.dp).size(22.dp).clip(CircleShape).clickable { show = !show },
            )
        }
        val note = if (error != null && value.isNotEmpty()) error else hint
        if (note != null) Text(note, fontSize = 12.sp, color = if (error != null && value.isNotEmpty()) colors.error else colors.onSurfaceVariantSummary, modifier = Modifier.padding(start = 4.dp, top = 5.dp))
    }
}

/** 整行大按钮 */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, color: Color = MiuixTheme.colorScheme.primary) {
    val on = enabled && !busy
    Box(
        modifier.fillMaxWidth().height(52.dp).then(if (on) Modifier.pressScale() else Modifier).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) color else color.copy(alpha = 0.35f)).clickable(enabled = on, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 密码要求（与 Supabase 的强密码策略一致） */
private fun pwRules(pw: String, again: String?) = listOfNotNull(
    "至少 6 位" to (pw.length >= 6),
    "小写字母" to pw.any { it.isLowerCase() },
    "大写字母" to pw.any { it.isUpperCase() },
    "数字" to pw.any { it.isDigit() },
    "符号" to pw.any { !it.isLetterOrDigit() },
    again?.let { "两次一致" to (pw.isNotEmpty() && pw == it) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasswordRules(pw: String, again: String?) {
    val colors = MiuixTheme.colorScheme
    val good = Color(0xFF16A34A)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pwRules(pw, again).forEach { (t, ok) ->
            Row(
                Modifier.clip(CircleShape).background(if (ok) good.copy(alpha = 0.12f) else colors.secondaryContainer).padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (ok) good else colors.onSurfaceVariantSummary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(t, fontSize = 12.sp, color = if (ok) good else colors.onSurfaceVariantSummary)
            }
        }
    }
}

// ---------------- 登录 / 注册 ----------------

/** 登录与注册页：mode 0 登录、1 注册 */
@Composable
fun AuthScreen(vm: AppViewModel, initialMode: Int = 0) {
    val kit = LocalKit.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val st by vm.sync.state.collectAsState()
    var mode by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(initialMode) }
    var role by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) } // 注册：0 学生 / 家长（邀请码）、1 老师（邮箱）
    var account by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pw1 by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var mailSent by remember { mutableStateOf<String?>(null) }
    val wasLoggedIn = remember { st.email != null }

    // 登录 / 注册成功后自动返回
    LaunchedEffect(st.email) { if (st.email != null && !wasLoggedIn) kit.nav.pop() }

    fun run(ok: String?, f: suspend () -> Unit) {
        busy = true; err = ""
        scope.launch {
            runCatching { f() }.onSuccess { if (ok != null) kit.toast(ok) }.onFailure { err = it.message ?: "操作失败" }
            busy = false
        }
    }

    val okUser = Regex("^[A-Za-z0-9_]{3,20}$").matches(user)
    val okPw = pwRules(pw1, pw2).all { it.second }
    val okMail = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email)

    MiuixPage(title = if (mode == 0) "登录" else "注册", largeTitle = if (mode == 0) "登录" else "注册", onBack = { kit.nav.pop() }) {
        item(key = "hero") {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(colors.primary, colors.primary.copy(alpha = 0.55f)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (mode == 0) Icons.Rounded.Person else if (role == 0) Icons.Rounded.FamilyRestroom else Icons.Rounded.School, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.height(14.dp))
                Text(if (mode == 0) "欢迎回来" else "创建账号", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (mode == 0) "老师用邮箱登录，学生、家长用注册时的用户名登录"
                    else if (role == 0) "凭老师给的邀请码注册，只读查看班级数据、模拟炒股" else "老师用邮箱注册，管理班级并在多台设备间同步",
                    fontSize = 13.sp, color = colors.onSurfaceVariantSummary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                )
            }
        }
        item(key = "tabs") { HTabs(listOf("登录", "注册"), mode, { mode = it; err = "" }, Modifier.padding(horizontal = 16.dp), height = 44.dp) }
        if (mode == 1) item(key = "roles") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleCard("学生 / 家长", "凭邀请码注册", Icons.Rounded.FamilyRestroom, role == 0, Modifier.weight(1f)) { role = 0; err = "" }
                RoleCard("老师", "用邮箱注册", Icons.Rounded.School, role == 1, Modifier.weight(1f)) { role = 1; err = "" }
            }
        }
        item(key = "form-$mode-$role") {
            HCard(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(), radius = 22.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        mode == 0 -> {
                            FormField(account, { account = it.trim() }, "账号", Icons.Rounded.Person, placeholder = "邮箱或用户名", keyboard = KeyboardType.Email)
                            FormField(pw, { pw = it }, "密码", Icons.Rounded.Lock, placeholder = "输入密码", password = true)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.weight(1f))
                                Text("忘记密码？", fontSize = 13.sp, color = colors.primary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                    if ("@" !in account) kit.toast("老师请先填写邮箱；邀请码注册的账号忘记密码请联系老师")
                                    else run("重置密码邮件已发送到 $account") { vm.sync.recover(account) }
                                }.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            ErrorText(err)
                            BigButton("登录", { run(null) { vm.sync.signIn(account, pw) } }, enabled = account.isNotBlank() && pw.isNotEmpty(), busy = busy)
                        }
                        role == 0 -> {
                            FormField(
                                code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) }, "邀请码", Icons.Rounded.Key,
                                placeholder = "8 位，向老师索取", mono = true, letterSpacing = 3.sp, fontSize = 18.sp,
                                trailing = { Text("${code.length}/8", fontSize = 12.sp, color = if (code.length == 8) Color(0xFF16A34A) else colors.onSurfaceVariantSummary) },
                            )
                            FormField(user, { user = it.trim().take(20) }, "用户名", Icons.Rounded.Person, placeholder = "以后用它登录", error = "3–20 位字母、数字或下划线", hint = "3–20 位字母、数字或下划线")
                            FormField(name, { name = it.take(20) }, "显示名（可选）", Icons.Rounded.Badge, placeholder = "如：张三家长")
                            FormField(pw1, { pw1 = it }, "密码", Icons.Rounded.Lock, placeholder = "设置密码", password = true)
                            FormField(pw2, { pw2 = it }, "确认密码", Icons.Rounded.Lock, placeholder = "再输一次", password = true)
                            PasswordRules(pw1, pw2)
                            ErrorText(err)
                            BigButton("注册并登录", { run("注册成功，已登录") { vm.sync.registerWithInvite(code, user, pw1, name.trim()) } }, enabled = code.length == 8 && okUser && okPw, busy = busy)
                            Text("一个邀请码只能注册一个账号。模拟币由老师发放，与真实货币无关。", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        }
                        else -> {
                            mailSent?.let {
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF16A34A).copy(alpha = 0.12f)).padding(12.dp)) {
                                    Text("注册成功！请到邮箱 $it 点确认链接，然后回来登录。", fontSize = 13.sp, color = Color(0xFF16A34A))
                                }
                            }
                            FormField(email, { email = it.trim() }, "邮箱", Icons.Rounded.Email, placeholder = "name@example.com", keyboard = KeyboardType.Email, error = "邮箱格式不对")
                            FormField(pw1, { pw1 = it }, "密码", Icons.Rounded.Lock, placeholder = "设置密码", password = true)
                            FormField(pw2, { pw2 = it }, "确认密码", Icons.Rounded.Lock, placeholder = "再输一次", password = true)
                            PasswordRules(pw1, pw2)
                            ErrorText(err)
                            BigButton("注册老师账号", {
                                run(null) {
                                    if (!vm.sync.signUp(email, pw1)) { mailSent = email; account = email }
                                }
                            }, enabled = okMail && okPw, busy = busy)
                        }
                    }
                }
            }
        }
        item(key = "switch") {
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == 0) "还没有账号？" else "已经有账号？", fontSize = 14.sp, color = colors.onSurfaceVariantSummary)
                Text(if (mode == 0) "去注册" else "去登录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { mode = 1 - mode; err = "" }.padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun ErrorText(err: String) {
    if (err.isNotBlank()) Text(err, fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
}

@Composable
private fun RoleCard(title: String, sub: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.heightIn(min = 104.dp).pressScale().clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.12f) else colors.secondaryContainer)
            .border(2.dp, if (selected) colors.primary else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (selected) colors.primary else colors.onSurfaceVariantSummary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (selected) Color.White else colors.onSurfaceVariantSummary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (selected) colors.primary else colors.onSurfaceVariantSummary.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (selected) colors.primary else colors.onSurface)
        Text(sub, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
    }
}
