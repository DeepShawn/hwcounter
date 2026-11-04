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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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
                r is Route.Draft || r is Route.Drafts || r is Route.StatTable
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
    val topLevel = current is Route.Home || current is Route.Students || current is Route.Scores || current is Route.Stats || current is Route.Settings

    CompositionLocalProvider(LocalKit provides kit) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snack) },
                bottomBar = {
                  androidx.compose.foundation.layout.Column {
                    if (readOnly) PreviewBanner { nav.tab(Route.Settings) }
                    if (topLevel) {
                        NavigationBar {
                            tabs.filter { !(readOnly && it.route == Route.Students) }.forEach { t ->
                                val sel = current == t.route
                                NavigationBarItem(
                                    selected = sel,
                                    onClick = { if (!sel) { kit.haptic(); nav.tab(t.route) } },
                                    icon = { Icon(if (sel) t.iconSel else t.icon, t.label) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                  }
                },
            ) { pad ->
                Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
                    AnimatedContent(
                        targetState = Pair(current, stack.size),
                        transitionSpec = {
                            val forward = targetState.second > initialState.second
                            val back = targetState.second < initialState.second
                            when {
                                forward -> (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
                                back -> (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                                else -> fadeIn() togetherWith fadeOut()
                            }
                        },
                        label = "nav",
                    ) { (route, _) ->
                        RouteContent(route, vm, data)
                    }
                }
            }
            Confetti(confetti)
            com.deepshawn.hwcounter.ui.screens.CloudConflictDialog(vm)
        }
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
        Modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFFFFF4E5))
            .clickable { onLogin() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("👀 预览模式 · 只读　", color = androidx.compose.ui.graphics.Color(0xFF6B4300), fontSize = 13.sp)
        Text("登录后可修改 ›", color = androidx.compose.ui.graphics.Color(0xFF6B4300), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}
