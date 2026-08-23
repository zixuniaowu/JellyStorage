package com.jellystorage

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import com.jellystorage.softbody.GameView
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 固定横屏，减少尺寸抖动
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersiveMode()
        configureGameGestureExclusion()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (BuildConfig.DEBUG && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val points = (0 until event.pointerCount).joinToString { i ->
                "${event.getPointerId(i)}=(${"%.0f".format(event.getX(i))},${"%.0f".format(event.getY(i))})"
            }
            android.util.Log.d("JellyRawTouch", "POINTER_DOWN $points")
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * 横屏游戏的左右下方是摇杆/攻击区，排除系统返回手势后，多指触控不会在
     * 到达 Compose 前被边缘手势截走。Android 对每条边限制约 200dp，故只排除底部控制带。
     */
    private fun configureGameGestureExclusion() {
        window.decorView.doOnLayout { view ->
            val density = resources.displayMetrics.density
            val edgeHeight = min(view.height, (200f * density).roundToInt())
            val edgeWidth = min(view.width / 3, (180f * density).roundToInt())
            val top = (view.height - edgeHeight).coerceAtLeast(0)
            ViewCompat.setSystemGestureExclusionRects(
                view,
                listOf(
                    Rect(0, top, edgeWidth, view.height),
                    Rect(view.width - edgeWidth, top, view.width, view.height)
                )
            )
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
