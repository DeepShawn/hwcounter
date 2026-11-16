package com.deepshawn.hwcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepshawn.hwcounter.ui.LocalKit
import com.deepshawn.hwcounter.ui.theme.LocalUiPrefs
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * HyperOS 风格页面：大标题随滚动收起为小标题，顶栏为磨砂玻璃（内容从下面滑过），
 * 列表自动留出状态栏、顶栏和底部悬浮栏的空间。
 *
 * @param floating 页面上的悬浮控件（如多选操作栏），可用传入的 Backdrop 做液态玻璃
 */
@Composable
fun MiuixPage(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    extraBottom: Dp = 0.dp,
    floating: @Composable BoxScope.(Backdrop?) -> Unit = {},
    /** 页面左右滑动切换选项卡：(项数, 当前项, 切换) */
    swipe: Triple<Int, Int, (Int) -> Unit>? = null,
    content: LazyListScope.() -> Unit,
) {
    val ui = LocalUiPrefs.current
    val glassBg = LocalGlassBg.current
    // 玻璃开启时页面透明，露出背后的柔光背景
    val surface = if (glassBg != null && ui.glassOn) Color.Transparent else MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop()
    val combined = if (glassBg != null) com.kyant.backdrop.backdrops.rememberCombinedBackdrop(glassBg, backdrop) else backdrop
    val glass = if (ui.glassOn) backdrop else null
    val barBackdrop = if (ui.glassOn) combined else null
    val behavior = MiuixScrollBehavior()
    val bottom = LocalBottomInset.current + extraBottom
    // 页面打开（或切换选项卡）时的入场动画起点
    val entrance = androidx.compose.runtime.remember(swipe?.second) { PageEntrance(System.currentTimeMillis()) }
    androidx.compose.runtime.CompositionLocalProvider(LocalPageEntrance provides entrance) {
    Box(modifier.fillMaxSize().then(if (swipe != null) Modifier.swipeToSwitch(swipe.first, swipe.second, swipe.third) else Modifier)) {
        Scaffold(
            containerColor = surface,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                // 顶栏背景：列表在顶部时完全透明（与页面背景融为一体，没有分界线）；
                // 内容滚到顶栏下面时，毛玻璃随滚动距离渐渐出现
                val density = androidx.compose.ui.platform.LocalDensity.current
                val fade = androidx.compose.runtime.remember(listState, density) {
                    androidx.compose.runtime.derivedStateOf {
                        if (listState.firstVisibleItemIndex > 0) 1f
                        else (listState.firstVisibleItemScrollOffset / with(density) { 48.dp.toPx() }).coerceIn(0f, 1f)
                    }
                }
                Box {
                    Box(
                        Modifier.matchParentSize()
                            .graphicsLayer { alpha = fade.value }
                            .frosted(barBackdrop, MiuixTheme.colorScheme.surface),
                    )
                TopAppBar(
                    title = title,
                    largeTitle = largeTitle,
                    color = Color.Transparent,
                    scrollBehavior = behavior,
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.padding(start = 16.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MiuixTheme.colorScheme.onSurface)
                        }
                    },
                    actions = actions,
                )
                }
            },
        ) { pad ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .then(if (glass != null) Modifier.layerBackdrop(glass) else Modifier)
                    .background(surface)
                    .then(if (ui.lowPerf) Modifier else Modifier.overScrollVertical().scrollEndHaptic())
                    .nestedScroll(behavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = bottom + 12.dp),
                content = content,
            )
        }
        CompositionLocalProvider(LocalBackdrop provides glass) { floating(glass) }
    }
    }
}

/** HyperOS 分组：小标题 + 圆角卡片 */
fun LazyListScope.group(title: String?, key: String? = null, content: @Composable ColumnScope.() -> Unit) {
    item(key = key) {
        Column {
            if (title != null) SmallTitle(title)
            HCard(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth(), content = content)
        }
    }
}

/** 返回上一页（给 MiuixPage 的 onBack 用） */
@Composable
fun popNav(): () -> Unit {
    val kit = LocalKit.current
    return { kit.nav.pop() }
}
