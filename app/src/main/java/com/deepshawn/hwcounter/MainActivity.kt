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

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        vm.sync.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val s = data.settings
            HwTheme(paletteKey = s.theme, darkMode = s.dark, shapeStyle = s.shape, compact = s.compact) {
                App(vm, data)
            }
        }
    }
}
