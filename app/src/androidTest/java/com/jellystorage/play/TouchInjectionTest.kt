package com.jellystorage.play

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真多点触控注入：adb 的多条 input 命令互相抢占，无法形成并发手指。
 * 本测试直接向当前 RESUMED 的 Activity 窗口派发两指 MotionEvent 流：
 *   指0 = 摇杆（左下按下并拖动）
 *   指1 = 攻击（期间按下、保持、抬起）
 * 结果由 logcat(JellyInput/JellyCast) 判定。
 *
 * 用法：先把游戏导航到战斗中，然后
 *   adb shell am instrument -w -e class com.jellystorage.play.TouchInjectionTest \
 *     com.jellystorage.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class TouchInjectionTest {

    @Test
    fun injectTwoFingerDuringLiveArena() {
        val inst = InstrumentationRegistry.getInstrumentation()
        // 自行启动游戏（注册进 lifecycle monitor，拿 Activity 引用）
        val intent = android.content.Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            com.jellystorage.MainActivity::class.java
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = inst.startActivitySync(intent)
        val decor = activity.window.decorView

        fun pp(id: Int): MotionEvent.PointerProperties = MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        fun pc(x: Float, y: Float): MotionEvent.PointerCoords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        fun ev(at: Long, action: Int, props: Array<MotionEvent.PointerProperties>, cs: Array<MotionEvent.PointerCoords>, atEnd: Long = at) =
            MotionEvent.obtain(
                at, atEnd, action, props.size, props, cs,
                0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            )
        fun tap(x: Float, y: Float) {
            val t = SystemClock.uptimeMillis()
            inst.runOnMainSync {
                val d = decor.dispatchTouchEvent(ev(t, MotionEvent.ACTION_DOWN, arrayOf(pp(0)), arrayOf(pc(x, y))))
                android.util.Log.d("JellyInput", "TAP($x,$y) down=$d")
            }
            Thread.sleep(90) // 真实墙钟间隔：Compose 手势协程需要跨帧处理 DOWN→UP
            inst.runOnMainSync {
                decor.dispatchTouchEvent(ev(t, MotionEvent.ACTION_UP, arrayOf(pp(0)), arrayOf(pc(x, y)), t + 90))
            }
            Thread.sleep(60)
        }

        // ── 导航：标题→继续冒险→(剧情跳过)→地图节点→(剧情跳过)→战斗 ──
        Thread.sleep(3500) // 等标题稳定
        tap(2150f, 504f); Thread.sleep(2400) // 继续冒险 / 出征
        tap(2350f, 1250f); Thread.sleep(2000) // 跳过剧情（若有）
        tap(900f, 1180f); Thread.sleep(2400)  // 进战斗节点
        tap(2350f, 1250f); Thread.sleep(2800) // 跳过前置剧情

        val t0 = SystemClock.uptimeMillis()
        val joyFrom = 600f to 950f     // 摇杆起点（窗口坐标）
        val joyTo = 1000f to 800f      // 摇杆拖动终点
        val atk = 2632f to 1093f       // 攻击钮（窗口坐标）

        val events = ArrayList<MotionEvent>()
        // 指0 按下（摇杆）
        events += ev(t0, MotionEvent.ACTION_DOWN, arrayOf(pp(0)), arrayOf(pc(joyFrom.first, joyFrom.second)))
        // 指0 拖动 200ms
        for (i in 1..5) {
            val t = t0 + i * 40L
            val x = joyFrom.first + (joyTo.first - joyFrom.first) * i / 5f
            val y = joyFrom.second + (joyTo.second - joyFrom.second) * i / 5f
            events += ev(t, MotionEvent.ACTION_MOVE, arrayOf(pp(0)), arrayOf(pc(x, y)))
        }
        // 指1 按下（攻击）—— 第二根手指
        events += ev(
            t0 + 240, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            arrayOf(pp(0), pp(1)),
            arrayOf(pc(joyTo.first, joyTo.second), pc(atk.first, atk.second))
        )
        // 双指保持 600ms（持续 MOVE 维持按下状态）
        for (i in 1..12) {
            val t = t0 + 240 + i * 50L
            events += ev(
                t, MotionEvent.ACTION_MOVE,
                arrayOf(pp(0), pp(1)),
                arrayOf(pc(joyTo.first, joyTo.second), pc(atk.first, atk.second))
            )
        }
        // 指1 抬起（攻击手指）
        events += ev(
            t0 + 900, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            arrayOf(pp(0), pp(1)),
            arrayOf(pc(joyTo.first, joyTo.second), pc(atk.first, atk.second))
        )
        // 指0 抬起（摇杆）
        events += ev(t0 + 960, MotionEvent.ACTION_UP, arrayOf(pp(0)), arrayOf(pc(joyTo.first, joyTo.second)))

        android.util.Log.d("JellyInput", "INJECT begin: 2-pointer gesture, ${events.size} events")
        // 按时间轴带真实间隔派发：Compose 需要跨帧看到指针流
        for (e in events) {
            inst.runOnMainSync { decor.dispatchTouchEvent(e) }
            Thread.sleep(45)
        }
        android.util.Log.d("JellyInput", "INJECT end")
        Thread.sleep(1200)
    }
}
