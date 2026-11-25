package com.nevroz.playeraero

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: PlayerVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val fpsList = remember { supportedFps() }
            LaunchedEffect(vm.prefs.fps) { applyFps(vm.prefs.fps) }
            AeroApp(vm, fpsList)
        }
    }

    private fun currentDisplay(): Display? {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
    }

    private fun supportedFps(): List<Int> {
        val d = currentDisplay() ?: return listOf(30, 60)
        val cur = d.mode
        val rates = d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .map { it.refreshRate.roundToInt() }
        return (rates + 30 + 60).distinct().sorted()
    }

    /** Просим систему переключить экран на режим с ближайшей частотой кадров. */
    private fun applyFps(fps: Int) {
        val lp = window.attributes
        if (fps <= 0) {
            lp.preferredDisplayModeId = 0
        } else {
            val d = currentDisplay() ?: return
            val cur = d.mode
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .minByOrNull { abs(it.refreshRate - fps) }
            lp.preferredDisplayModeId = best?.modeId ?: 0
        }
        window.attributes = lp
    }
}
