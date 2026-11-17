package com.deepshawn.hwcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshawn.hwcounter.data.AppViewModel
import com.deepshawn.hwcounter.ui.App
import com.deepshawn.hwcounter.ui.theme.HwTheme
import com.deepshawn.hwcounter.ui.theme.AppMotionScale
import com.deepshawn.hwcounter.ui.theme.UiPrefsStore
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UiPrefsStore.load(this)
        // 自建 Recomposer：协程上下文带上全局动画倍率，“动效”开关对全部 Compose 动画即时生效
        val context = AndroidUiDispatcher.Main + AppMotionScale
        val recomposer = Recomposer(context)
        lifecycleScope.launch(context) { recomposer.runRecomposeAndApplyChanges() }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = recomposer.resumeCompositionFrameClock()
            override fun onStop(owner: LifecycleOwner) = recomposer.pauseCompositionFrameClock()
            override fun onDestroy(owner: LifecycleOwner) = recomposer.cancel()
        })
        setContent(parent = recomposer) {
            // 性能模式：请求屏幕最高刷新率（如 120Hz）；关闭时交给系统
            val ultra = UiPrefsStore.prefs.ultra
            androidx.compose.runtime.LaunchedEffect(ultra) { applyRefreshRate(ultra) }
            val data by vm.data.collectAsStateWithLifecycle()
            val s = data.settings
            HwTheme(paletteKey = s.theme, darkMode = s.dark, shapeStyle = s.shape, compact = s.compact, ui = UiPrefsStore.prefs) {
                App(vm, data)
            }
        }
    }

    private fun applyRefreshRate(max: Boolean) {
        runCatching {
            val attrs = window.attributes
            if (max) {
                @Suppress("DEPRECATION")
                val d = if (android.os.Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
                val cur = d?.mode ?: return
                val best = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }.maxByOrNull { it.refreshRate } ?: return
                attrs.preferredDisplayModeId = best.modeId
                attrs.preferredRefreshRate = best.refreshRate
            } else {
                attrs.preferredDisplayModeId = 0
                attrs.preferredRefreshRate = 0f
            }
            window.attributes = attrs
        }
    }
}
