package com.jellystorage

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.jellystorage.softbody.GameView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 固定横屏，减少尺寸抖动
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // 安全区：刘海 / 状态栏 / 手势条不挡控件
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B1020))
                    .safeDrawingPadding()
            ) {
                GameView(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
