@file:OptIn(ExperimentalMaterial3Api::class)

package com.deepshawn.hwcounter.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.deepshawn.hwcounter.ui.components.glass
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.deepshawn.hwcounter.data.AppData
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.data.Settings
import com.deepshawn.hwcounter.ui.components.Confetti
import com.deepshawn.hwcounter.ui.screens.AssignmentScreen
import com.deepshawn.hwcounter.ui.screens.HomeScreen
import com.deepshawn.hwcounter.ui.screens.LiveScanScreen
import com.deepshawn.hwcounter.ui.screens.SeatingScreen
import com.deepshawn.hwcounter.ui.screens.RecognizeScreen
import com.deepshawn.hwcounter.ui.screens.ScoresScreen
import com.deepshawn.hwcounter.ui.screens.SettingsScreen
import com.deepshawn.hwcounter.ui.screens.PaperCountScreen
import com.deepshawn.hwcounter.ui.screens.SpineScreen
import com.deepshawn.hwcounter.ui.screens.StackCountScreen
import com.deepshawn.hwcounter.ui.screens.StatsScreen
import com.deepshawn.hwcounter.ui.screens.StudentDetailScreen
import com.deepshawn.hwcounter.ui.screens.StudentsScreen
import com.deepshawn.hwcounter.ui.screens.VoiceScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

sealed interface Route {
    data object Home : Route
    data object Students : Route
    data object Scores : Route
    data object Stats : Route
    data object Settings : Route
    data class Assign(val id: String) : Route
    data class Recognize(val assignmentId: String, val uris: List<Uri>) : Route
    data class Live(val assignmentId: String) : Route
    data class Stack(val assignmentId: String?) : Route
    data class Spine(val assignmentId: String) : Route
    data class Paper(val assignmentId: String?) : Route
    data class Voice(val assignmentId: String) : Route
    data class StudentDetail(val id: String) : Route
    data object Seating : Route
    data class Draft(val id: String) : Route
    data object Drafts : Route
    data class StatTable(val draftId: String?) : Route
    data class Group(val name: String) : Route
    data object Exams : Route
    data class Exam(val id: String) : Route
    data class ExamImport(val examId: String?) : Route
    data class BatchScore(val ids: List<String> = emptyList(), val reason: String = "") : Route
    data class Ledger(val studentId: String? = null) : Route
    data object Settlement : Route
    data class SettleRegister(val id: String) : Route
    data class Personal(val id: String) : Route
}

class Nav(private val stack: MutableList<Route>, private val canOpen: (Route) -> Boolean = { true }) {
    val current: Route get() = stack.last()
    val depth: Int get() = stack.size
    fun push(r: Route) { if (canOpen(r)) stack.add(r) }
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun replace(r: Route) { stack.removeAt(stack.lastIndex); stack.add(r) }
    fun tab(r: Route) { if (canOpen(r)) { stack.clear(); stack.add(r) } }
}

/** 全局 UI 工具：提示/撤销/彩带/反馈 */
class UiKit(
    val vm: AppViewModel,
    val nav: Nav,
    private val snack: SnackbarHostState,
    private val scope: CoroutineScope,
    private val ctx: Context,
    private val settings: () -> Settings,
    val celebrate: () -> Unit,
) {
    fun toast(msg: String) {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    fun undoable(msg: String) {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(msg, actionLabel = "撤销", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undo()
        }
    }

    fun haptic(strong: Boolean = false) {
        if (!settings().haptic) return
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createOneShot(if (strong) 60 else 18, if (strong) 255 else 120))
        } catch (_: Exception) {
        }
    }

    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }

    fun beep(ok: Boolean = true) {
        if (!settings().sound) return
        tone?.startTone(if (ok) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_NACK, 120)
    }
}

val LocalKit = staticCompositionLocalOf<UiKit> { error("no kit") }

@Composable
fun App(vm: AppViewModel, data: AppData) {
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val snack = remember { SnackbarHostState() }
    val readOnly by vm.readOnly.collectAsStateWithLifecycle()
    // 预览模式下，只用于登记/计数的页面不让进
    val nav = remember {
        Nav(stack) { r ->
            val editOnly = r is Route.Recognize || r is Route.Live || r is Route.Voice || r is Route.Spine || r is Route.Stack || r is Route.Paper || r is Route.Seating ||
                r is Route.Draft || r is Route.Drafts || r is Route.StatTable || r is Route.BatchScore
            when {
                editOnly && vm.readOnly.value -> { vm.notices.tryEmit("👀 预览模式：登录后才能使用"); false }
                // 预览模式不显示名单页（统计/热力图照常显示）
                r is Route.Students && vm.readOnly.value -> { vm.notices.tryEmit("👀 预览模式：登录后才能查看名单"); false }
                else -> true
            }
        }
    }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var confetti by remember { mutableIntStateOf(0) }
    val latestData by androidx.compose.runtime.rememberUpdatedState(data)
    val kit = remember {
        UiKit(vm, nav, snack, scope, ctx, { latestData.settings }) {
            if (latestData.settings.confetti) confetti++
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.notices.collect { kit.toast(it) } }

    BackHandler(enabled = stack.size > 1) { nav.pop() }
    // 回到根后再按返回：若不在首页则先回首页
    BackHandler(enabled = stack.size == 1 && stack[0] != Route.Home) { nav.tab(Route.Home) }

    // 退出登录回到预览时，如果正停在名单页，回首页
    androidx.compose.runtime.LaunchedEffect(readOnly) { if (readOnly && stack.any { it is Route.Students }) { stack.clear(); stack.add(Route.Home) } }

    val current = stack.last()
    val screenState = rememberSaveableStateHolder()
    val topLevel = current is Route.Home || current is Route.Students || current is Route.Scores || current is Route.Stats || current is Route.Settings
    val ui = com.deepshawn.hwcounter.ui.theme.LocalUiPrefs.current
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val glassBg = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 底栏页面：内容从玻璃底栏下穿过，需要留出底栏高度；二级页面：整体避开系统导航条
    val bottomInset = if (topLevel) 92.dp + navBottom + (if (readOnly) 44.dp else 0.dp) else 16.dp + (if (readOnly) 44.dp else 0.dp)
    val surface = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface

    CompositionLocalProvider(LocalKit provides kit) {
        top.yukonga.miuix.kmp.basic.Scaffold(containerColor = surface, contentWindowInsets = WindowInsets(0)) { _ ->
            Box(Modifier.fillMaxSize()) {
                // 页面内容：记录成玻璃的背景层（玻璃关闭时不记录，省一次绘制）
                Box(
                    Modifier.fillMaxSize()
                        .then(if (ui.glassOn) Modifier.layerBackdrop(backdrop) else Modifier)
                        .background(surface),
                ) {
                    // 液态玻璃：页面背后铺柔光背景，卡片 / 选项卡从这一层取样做毛玻璃
                    if (ui.glassOn) com.deepshawn.hwcounter.ui.components.GlassBackground(Modifier.fillMaxSize().layerBackdrop(glassBg))
                    CompositionLocalProvider(
                        com.deepshawn.hwcounter.ui.components.LocalBottomInset provides bottomInset,
                        com.deepshawn.hwcounter.ui.components.LocalGlassBg provides (if (ui.glassOn) glassBg else null),
                    ) {
                        val content: @Composable (Route) -> Unit = { route ->
                            if (route in listOf(Route.Home, Route.Students, Route.Scores, Route.Stats, Route.Settings)) {
                                screenState.SaveableStateProvider(route.toString()) { RouteContent(route, vm, data) }
                            } else Box(Modifier.fillMaxSize().navigationBarsPadding()) { RouteContent(route, vm, data) }
                        }
                        if (ui.motion) {
                            AnimatedContent(
                                targetState = Pair(current, stack.size),
                                transitionSpec = {
                                    val forward = targetState.second > initialState.second
                                    val back = targetState.second < initialState.second
                                    // 弹簧曲线：进入页面从右侧滑入，返回时向右滑出；切换底栏页面淡入并轻微放大
                                    val slide = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.92f, stiffness = 420f)
                                    val fadeSpec = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 500f)
                                    val transition = when {
                                        forward -> (slideInHorizontally(slide) { it } + fadeIn(fadeSpec, 0.85f)) togetherWith (slideOutHorizontally(slide) { -it / 4 } + fadeOut(fadeSpec, 0.5f) + androidx.compose.animation.scaleOut(fadeSpec, 0.96f))
                                        back -> (slideInHorizontally(slide) { -it / 4 } + fadeIn(fadeSpec, 0.5f) + androidx.compose.animation.scaleIn(fadeSpec, 0.96f)) togetherWith (slideOutHorizontally(slide) { it } + fadeOut(fadeSpec, 0.85f))
                                        else -> (fadeIn(tween(220)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 500f), 0.97f)) togetherWith fadeOut(tween(140))
                                    }
                                    transition.apply { targetContentZIndex = if (back) -1f else 1f }.using(null)
                                },
                                label = "nav",
                            ) { (route, _) -> content(route) }
                        } else {
                            // 动效关闭：直接切换，不保留两份页面，切换最快
                            androidx.compose.runtime.key(current, stack.size) { content(current) }
                        }
                    }
                }
                // 悬浮层：提示、预览横幅、液态玻璃底栏（不在背景层内，避免自我采样）
                CompositionLocalProvider(com.deepshawn.hwcounter.ui.components.LocalBackdrop provides (if (ui.glassOn) backdrop else null)) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SnackbarHost(snack) { GlassSnackbar(it) }
                        if (readOnly) PreviewBanner { nav.tab(Route.Settings) }
                        if (topLevel) GlassTabBar(
                            tabs = tabs.filter { !(readOnly && it.route == Route.Students) },
                            current = current,
                        ) { t -> if (current != t.route) { kit.haptic(); nav.tab(t.route) } }
                    }
                }
                if (!ui.lowPerf) Confetti(confetti)
                com.deepshawn.hwcounter.ui.screens.CloudConflictDialog(vm)
            }
        }
    }
}

/** 液态玻璃底部导航：悬浮胶囊，选中项的淡色胶囊会滑动；按住底栏左右拖动即可切换页面 */
@Composable
private fun GlassTabBar(tabs: List<Tab>, current: Route, onSelect: (Tab) -> Unit) {
    val colors = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
    val selected = tabs.indexOfFirst { it.route == current }.coerceAtLeast(0)
    com.deepshawn.hwcounter.ui.components.SlidingSegments(
        tabs.size, selected, { i -> tabs.getOrNull(i)?.let(onSelect) },
        Modifier.padding(horizontal = 18.dp).widthIn(max = 520.dp).fillMaxWidth().height(68.dp)
            .glass(shape = shape, refraction = 22.dp)
            .padding(6.dp),
        thumb = { m -> androidx.compose.foundation.layout.Box(m.clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(colors.primary.copy(alpha = 0.14f))) },
    ) { st ->
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, t ->
                val sel = i == st.hot
                val fg = if (sel) colors.primary else colors.onSurfaceVariantSummary
                androidx.compose.foundation.layout.Column(
                    Modifier.weight(1f).fillMaxSize()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(t) }
                        .semantics { this.selected = i == selected; this.role = androidx.compose.ui.semantics.Role.Tab },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    val bump by androidx.compose.animation.core.animateFloatAsState(if (sel) 1.12f else 1f, com.deepshawn.hwcounter.ui.components.Springs.bouncy, label = "tabIcon")
                    val tint by androidx.compose.animation.animateColorAsState(fg, label = "tabTint")
                    Icon(if (sel) t.iconSel else t.icon, t.label, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = bump; scaleY = bump })
                    Text(t.label, color = fg, fontSize = 11.sp, fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun GlassSnackbar(data: androidx.compose.material3.SnackbarData) {
    val colors = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier.padding(horizontal = 24.dp, vertical = 8.dp).widthIn(max = 520.dp)
            .glass(shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), tint = colors.surfaceContainerHighest, refraction = 10.dp)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, Modifier.weight(1f, fill = false).padding(vertical = 8.dp), fontSize = 14.sp, color = colors.onSurface, maxLines = 3)
        data.visuals.actionLabel?.let { label ->
            androidx.compose.material3.TextButton(onClick = { data.performAction() }) { Text(label, color = colors.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
        } ?: Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun RouteContent(route: Route, vm: AppViewModel, data: AppData) {
    when (route) {
        Route.Home -> HomeScreen(vm, data)
        Route.Students -> StudentsScreen(vm, data)
        Route.Scores -> ScoresScreen(vm, data)
        Route.Stats -> StatsScreen(vm, data)
        Route.Settings -> SettingsScreen(vm, data)
        is Route.Assign -> AssignmentScreen(vm, data, route.id)
        is Route.Recognize -> RecognizeScreen(vm, data, route.assignmentId, route.uris)
        is Route.Live -> LiveScanScreen(vm, data, route.assignmentId)
        is Route.Stack -> StackCountScreen(vm, data, route.assignmentId)
        is Route.Spine -> SpineScreen(vm, data, route.assignmentId)
        is Route.Paper -> PaperCountScreen(vm, data, route.assignmentId)
        is Route.Voice -> VoiceScreen(vm, data, route.assignmentId)
        is Route.StudentDetail -> StudentDetailScreen(vm, data, route.id)
        Route.Seating -> SeatingScreen(vm, data)
        is Route.Draft -> com.deepshawn.hwcounter.ui.screens.DraftScreen(vm, data, route.id)
        Route.Drafts -> com.deepshawn.hwcounter.ui.screens.DraftListScreen(vm, data)
        is Route.StatTable -> com.deepshawn.hwcounter.ui.screens.StatTableScreen(vm, data, route.draftId)
        is Route.Group -> com.deepshawn.hwcounter.ui.screens.GroupDetailScreen(vm, data, route.name)
        Route.Exams -> com.deepshawn.hwcounter.ui.screens.ExamListScreen(vm, data)
        is Route.Exam -> com.deepshawn.hwcounter.ui.screens.ExamDetailScreen(vm, data, route.id)
        is Route.ExamImport -> com.deepshawn.hwcounter.ui.screens.ExamImportScreen(vm, data, route.examId)
        is Route.BatchScore -> com.deepshawn.hwcounter.ui.screens.BatchScoreScreen(vm, data, route.ids, route.reason)
        is Route.Ledger -> com.deepshawn.hwcounter.ui.screens.LedgerScreen(vm, data, route.studentId)
        Route.Settlement -> com.deepshawn.hwcounter.ui.screens.SettlementScreen(vm, data)
        is Route.SettleRegister -> com.deepshawn.hwcounter.ui.screens.SettleRegisterScreen(vm, data, route.id)
        is Route.Personal -> com.deepshawn.hwcounter.ui.screens.PersonalReportScreen(vm, data, route.id)
    }
}

private data class Tab(val route: Route, val label: String, val icon: ImageVector, val iconSel: ImageVector)

private val tabs = listOf(
    Tab(Route.Home, "首页", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Route.Students, "名单", Icons.Outlined.People, Icons.Filled.People),
    Tab(Route.Scores, "积分", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
    Tab(Route.Stats, "统计", Icons.Outlined.Insights, Icons.Filled.Insights),
    Tab(Route.Settings, "设置", Icons.Outlined.Settings, Icons.Filled.Settings),
)

/** 拍照 / 相册多选 */
class PhotoLauncher(val takePhoto: () -> Unit, val pickGallery: () -> Unit)

@Composable
fun rememberPhotoLauncher(onPhotos: (List<Uri>) -> Unit): PhotoLauncher {
    val ctx = LocalContext.current
    val kit = LocalKit.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pending
        if (ok && u != null) onPhotos(listOf(u))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { list ->
        if (list.isNotEmpty()) onPhotos(list)
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "hw_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        pending = u
        try {
            camera.launch(u)
        } catch (e: Exception) {
            kit.toast("无法打开相机：${e.message}")
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else kit.toast("需要相机权限才能拍照")
    }
    return remember(camera, gallery, perm) {
        PhotoLauncher(
            takePhoto = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else perm.launch(Manifest.permission.CAMERA)
            },
            pickGallery = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

/** 预览模式横幅：未登录时只读 */
@Composable
private fun PreviewBanner(onLogin: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.padding(bottom = 8.dp)
            .glass(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), tint = androidx.compose.ui.graphics.Color(0xFFFFF4E5), refraction = 8.dp, elevated = false)
            .clickable { onLogin() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("👀 预览模式 · 只读　", color = androidx.compose.ui.graphics.Color(0xFF6B4300), fontSize = 13.sp)
        Text("登录后可修改 ›", color = androidx.compose.ui.graphics.Color(0xFF6B4300), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}
