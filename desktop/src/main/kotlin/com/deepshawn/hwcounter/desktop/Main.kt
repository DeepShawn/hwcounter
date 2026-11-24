package com.deepshawn.hwcounter.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.deepshawn.hwcounter.data.AppData
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 加载状态 */
enum class Load { LOADING, OK, NONE, ERROR }

data class ToastMsg(val text: String, val undoable: Boolean, val at: Long = System.nanoTime())

/** 整个客户端的状态：数据、本机偏好、当前页面、登录身份、同步、撤销 */
class AppState(val prefs: Prefs, initial: AppData?) {
    var data by mutableStateOf(initial)
    var load by mutableStateOf(Load.LOADING)
    var tab by mutableStateOf(Tab.HOME)
    val stack = mutableStateListOf<Route>()
    var guide by mutableStateOf(!prefs.onboarded)
    var mode by mutableStateOf(Mode.GUEST)
    var me by mutableStateOf<com.deepshawn.hwcounter.cloud.Trade.Me?>(null)
    var sync by mutableStateOf(SyncUi())
    var toast by mutableStateOf<ToastMsg?>(null)
    /** 弹出的对话框（盖在页面上） */
    var dialog by mutableStateOf<(@Composable () -> Unit)?>(null)
    val undo = ArrayDeque<AppData>()
    var onChange: (() -> Unit)? = null
    var live: Live? = null
    val editable: Boolean get() = mode == Mode.TEACHER
    fun open(r: Route) { stack.add(r) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun switchTab(t: Tab) { tab = t; stack.clear() }
    fun say(text: String, undoable: Boolean = false) { toast = ToastMsg(text, undoable) }
    fun pushUndo(d: AppData) { undo.addLast(d); while (undo.size > 30) undo.removeFirst() }

    /** 修改数据（只有老师能改）：保存到本机，几秒后自动上传 */
    fun update(msg: String? = null, undoable: Boolean = true, f: (AppData) -> AppData): Boolean {
        if (!editable) { say(if (mode == Mode.MEMBER) "普通用户只能查看，不能修改" else "预览模式：登录后才能修改"); return false }
        val old = data ?: AppData(); val n = f(old)
        if (n == old) return false
        if (undoable) pushUndo(old)
        data = n; Account.saveLocal(n); onChange?.invoke()
        if (msg != null) say(msg, undoable)
        return true
    }
    fun updateClass(classId: String, msg: String? = null, f: (com.deepshawn.hwcounter.data.SchoolClass) -> com.deepshawn.hwcounter.data.SchoolClass) =
        update(msg) { d -> d.copy(classes = d.classes.map { if (it.id == classId) f(it) else it }) }
    fun doUndo() {
        val prev = undo.removeLastOrNull() ?: return
        if (!editable) return
        data = prev; Account.saveLocal(prev); onChange?.invoke(); toast = null
    }
}

fun main() = application {
    val prefs = remember { Prefs() }
    val state = remember { AppState(prefs, Cloud.loadCache()) }
    val win = rememberWindowState(size = DpSize(1180.dp, 820.dp))
    Window(
        onCloseRequest = ::exitApplication, state = win, title = "作业通", icon = painterResource("icon.png"),
        onPreviewKeyEvent = { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && state.stack.isNotEmpty()) { state.back(); true } else false
        },
    ) {
        window.minimumSize = java.awt.Dimension(760, 560)
        HwTheme(prefs) { AppRoot(state) }
    }
}

@Composable
fun AppRoot(state: AppState, autoLoad: Boolean = true) {
    val scope = rememberCoroutineScope()
    val live = remember { Live(state, scope).also { state.live = it } }
    fun reload() { live.load() }
    LaunchedEffect(Unit) {
        if (!autoLoad) return@LaunchedEffect
        reload()
        // 老师每分钟检查一次同步；查看时每 5 分钟刷新一次
        var n = 0
        while (true) { kotlinx.coroutines.delay(60_000L); n++; if (state.mode == Mode.TEACHER) live.tick() else if (n % 5 == 0) live.tick() }
    }
    LaunchedEffect(state.toast) { val t = state.toast ?: return@LaunchedEffect; kotlinx.coroutines.delay(if (t.undoable) 5000 else 3000); if (state.toast == t) state.toast = null }
    val colors = MiuixTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.surface)) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(state)
            Box(Modifier.weight(1f).fillMaxHeight()) { Content(state, ::reload) }
        }
        ToastBar(state)
        state.sync.conflict?.let { if (state.editable) ConflictDialog(live) }
        state.dialog?.invoke()
        if (state.guide) Guide { state.guide = false; state.prefs.onboarded = true }
    }
}

@Composable
private fun Sidebar(state: AppState) {
    val colors = MiuixTheme.colorScheme
    val look = LocalLook.current
    Column(Modifier.width(220.dp).fillMaxHeight().background(colors.surfaceContainer).padding(12.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(look.palette.a, look.palette.b))), contentAlignment = Alignment.Center) { LineIcon("📚", Color.White, size = 20.dp) }
            Spacer(Modifier.width(10.dp))
            Column { Text("作业通", fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(when (state.mode) { Mode.TEACHER -> "老师 · 可修改"; Mode.MEMBER -> "普通用户 · 只读"; else -> "Windows 版" }, fontSize = 11.sp, color = sub()) }
        }
        Spacer(Modifier.height(10.dp))
        val d = state.data
        if (d != null && (d.classes.size > 1 || (state.editable && d.classes.isNotEmpty()))) {
            val c = currentClass(state)
            Text("班级", Modifier.padding(start = 10.dp, bottom = 4.dp), fontSize = 12.sp, color = sub())
            d.classes.forEach { x ->
                val on = x.id == c?.id
                Text(
                    "${x.emoji} ${x.name}", Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { state.prefs.classId = x.id; state.stack.clear() }.padding(horizontal = 10.dp, vertical = 7.dp),
                    fontSize = 13.5.sp, color = if (on) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Tab.entries.forEach { t ->
            val on = state.tab == t && state.stack.isEmpty()
            val active = state.tab == t
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (active) colors.primary.copy(alpha = if (on) 0.16f else 0.08f) else Color.Transparent)
                    .clickable { state.switchTab(t) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIcon(t.icon, if (active) colors.primary else colors.onSurface, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(t.label, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = if (active) colors.primary else colors.onSurface)
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.editable) Text(if (state.sync.busy) "同步中…" else state.sync.msg.ifBlank { "云同步已开启" }, Modifier.padding(horizontal = 8.dp), fontSize = 11.sp, color = if (state.sync.error) BAD else sub(), maxLines = 2)
        Text("v$APP_VERSION", Modifier.padding(8.dp), fontSize = 11.sp, color = sub())
    }
}

fun currentClass(state: AppState) = state.data?.let { d -> d.classes.firstOrNull { it.id == state.prefs.classId } ?: d.classes.firstOrNull { it.id == d.currentClassId } ?: d.classes.firstOrNull() }

@Composable
private fun Content(state: AppState, reload: () -> Unit) {
    val d = state.data
    val c = currentClass(state)
    if (d == null || c == null) {
        if (state.tab == Tab.SETTINGS) { SettingsPage(null, state, reload); return }
        Page("作业通", null) {
            when {
                d != null && state.editable -> { Empty("🏫", "还没有班级，先新建一个"); More("新建班级") { state.dialog = { ClassDialog(state, null) } } }
                d != null -> Empty("🏫", "还没有班级")
                state.load == Load.LOADING -> Empty("⏳", "正在加载…")
                state.load == Load.NONE -> Empty("🔒", "老师还没有开放查看")
                else -> Empty("📶", "加载失败，请检查网络后刷新")
            }
            if (state.load != Load.LOADING && !(d != null && state.editable)) Text("重新加载", Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(cardColor()).clickable { reload() }.padding(horizontal = 20.dp, vertical = 10.dp), color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    val rules = d.settings.rules
    val ctx = Ctx(state, c, rules)
    when (val top = state.stack.lastOrNull()) {
        is Route.Assign -> AssignPage(ctx, top.id)
        is Route.Student -> StudentPage(ctx, top.id)
        is Route.Group -> GroupPage(ctx, top.id)
        is Route.Exam -> ExamPage(ctx, top.id)
        Route.Exams -> ExamListPage(ctx)
        null -> when (state.tab) {
            Tab.HOME -> HomePage(ctx)
            Tab.ROSTER -> RosterPage(ctx)
            Tab.SCORES -> ScoresPage(ctx)
            Tab.STATS -> StatsPage(ctx)
            Tab.SETTINGS -> SettingsPage(ctx, state, reload)
        }
    }
}

/** 页面：顶部标题栏（有返回）+ 居中内容列（桌面上限宽 860） */
@Composable
fun Page(title: String, onBack: (() -> Unit)?, actions: (@Composable () -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Text("‹ 返回", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 6.dp), color = MiuixTheme.colorScheme.primary, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            actions?.invoke()
        }
        Box(Modifier.fillMaxSize()) {
            // 滚轮、触摸屏手指滑动、按住鼠标拖动都能滚动；右侧有滚动条
            Box(Modifier.fillMaxSize().dragScroll(scroll).verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 860.dp).fillMaxWidth().padding(bottom = 32.dp)) {
                    Text(title, Modifier.padding(start = 26.dp, end = 26.dp, bottom = 10.dp), fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    content()
                }
            }
            androidx.compose.foundation.VerticalScrollbar(
                androidx.compose.foundation.rememberScrollbarAdapter(scroll),
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 2.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/** 新手提示（只存本机，可跳过） */
private val GUIDE = listOf(
    Triple("📚", "欢迎使用作业通 · Windows 版", "这里可以查看老师登记的作业、积分、考试和统计；老师登录后也能在电脑上登记和修改，自动同步到手机和 iPad。") to listOf("左侧五个页面：首页、名单、积分、统计、设置", "数据每 5 分钟自动刷新，也可以在 设置 → 刷新数据"),
    Triple("✅", "作业", "首页列出每次作业的交齐率，点进去可以看每位同学的情况。") to listOf("颜色代表状态：已交、优秀、迟交、未交、请假、复审不合格、抄袭", "可以按状态筛选，切换网格 / 列表"),
    Triple("🏆", "名单与积分", "名单里点同学进入档案：积分、名次、作业足迹、考试和每一笔积分流水。") to listOf("积分页有排行榜和加减分记录", "小组里可以看组长、组员和各自积分"),
    Triple("📈", "统计", "统计页与手机端相同：作业、考试、关注、信誉、股票、小组、抄袭七个栏目。") to listOf("信誉：信誉榜（越优秀越高）；股票：关注度、信誉分、小组综合行情", "抄袭：提供者与抄袭者的关系图"),
    Triple("🎨", "个性化", "设置里可以换主题配色、深色模式、界面动效和作业页布局。") to listOf("这些只保存在这台电脑上", "随时可以在 设置 → 新手提示 再看一遍"),
)

@Composable
fun Guide(onDone: () -> Unit) {
    var i by remember { mutableStateOf(0) }
    val (head, tips) = GUIDE[i]
    val (emoji, title, body) = head
    val look = LocalLook.current
    val colors = MiuixTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(26.dp)).background(colors.surfaceContainer).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("新手提示 ${i + 1}/${GUIDE.size}", Modifier.weight(1f), fontSize = 13.sp, color = sub())
                Text("跳过", Modifier.clip(CircleShape).clickable(onClick = onDone).padding(8.dp), color = colors.primary, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.padding(vertical = 12.dp).size(80.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(look.palette.a, look.palette.b))), contentAlignment = Alignment.Center) { LineIcon(emoji, Color.White, size = 38.dp) }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(body, Modifier.padding(vertical = 8.dp), fontSize = 14.sp, color = sub())
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(track()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tips.forEach { t -> Row { Text("•  ", color = look.palette.a); Text(t, fontSize = 13.5.sp) } }
            }
            Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GUIDE.indices.forEach { k -> Box(Modifier.height(7.dp).width(if (k == i) 18.dp else 7.dp).clip(CircleShape).background(if (k == i) colors.primary else track())) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (i > 0) Text("上一步", Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(track()).clickable { i-- }.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
                val last = i == GUIDE.lastIndex
                Text(if (last) "开始查看" else "下一步", Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(colors.primary).clickable { if (last) onDone() else i++ }.padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
