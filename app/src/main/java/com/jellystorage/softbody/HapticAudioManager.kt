package com.jellystorage.softbody

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jellystorage.R

class HapticAudioManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_GAME)
                .build()
        )
        .build()

    private var thudSoundId: Int = 0
    private var rustleSoundId: Int = 0
    private var squishSoundId: Int = 0
    private var hitId: Int = 0
    private var critId: Int = 0
    private var killId: Int = 0
    private var hurtId: Int = 0
    private var uiId: Int = 0
    /** 外部开关：设置页 soundOn / hapticsOn */
    @Volatile var soundEnabled: Boolean = true
    @Volatile var hapticsEnabled: Boolean = true

    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 55)
    } catch (_: Throwable) {
        null
    }

    init {
        // Compile-time resource IDs keep release shrinking and validation reliable.
        hitId = soundPool.load(context, R.raw.sfx_hit, 1)
        critId = soundPool.load(context, R.raw.sfx_crit, 1)
        killId = soundPool.load(context, R.raw.sfx_kill, 1)
        hurtId = soundPool.load(context, R.raw.sfx_hurt, 1)
        uiId = soundPool.load(context, R.raw.sfx_ui, 1)
    }

    fun thud() {
        playSound(thudSoundId, 0.6f)
        if (thudSoundId == 0) toneBeep(ToneGenerator.TONE_PROP_BEEP, 35)
    }

    fun rustle() {
        playSound(rustleSoundId, 0.4f)
        if (rustleSoundId == 0) toneBeep(ToneGenerator.TONE_PROP_ACK, 30)
    }

    fun squish() {
        playSound(squishSoundId, 0.5f)
        if (squishSoundId == 0) toneBeep(ToneGenerator.TONE_CDMA_PIP, 40)
    }

    fun uiClick() {
        if (!soundEnabled) return
        if (uiId != 0) playSound(uiId, 0.35f)
        else toneBeep(ToneGenerator.TONE_PROP_BEEP2, 25)
    }

    fun onObjectSettled() {
        if (hapticsEnabled) vibrateSoft()
        if (soundEnabled) squish()
    }

    fun onPackSuccess() {
        if (hapticsEnabled) vibrateSnap()
        if (soundEnabled) squish()
    }

    fun vibrateSnap() {
        if (!hapticsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            vibrator?.vibrate(effect)
        } else {
            vibrator?.vibrate(VibrationEffect.createOneShot(20L, 140))
        }
    }

    /** Combat feedback: 1 light, 2 crit, 3 kill, 4 hurt, 5 skill, 6 ultimate. */
    fun combatPulse(level: Int) {
        if (level <= 0) return
        if (hapticsEnabled) {
            when (level) {
                1 -> vibrateOneShot(22L, 120)
                2 -> {
                    vibrateOneShot(18L, 200)
                    vibrator?.let {
                        it.vibrate(VibrationEffect.createOneShot(36L, 255))
                    }
                }
                3 -> vibrateOneShot(55L, 255)
                4 -> vibrateOneShot(45L, 220)
                5 -> vibrateOneShot(34L, 185)
                else -> vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 28L, 34L, 68L),
                        intArrayOf(0, 170, 0, 255),
                        -1
                    )
                )
            }
        }
        if (!soundEnabled) return
        when (level) {
            1 -> {
                if (hitId != 0) playSound(hitId, 0.55f)
                else if (thudSoundId != 0) playSound(thudSoundId, 0.55f)
                else toneBeep(ToneGenerator.TONE_PROP_BEEP, 40)
            }
            2 -> {
                if (critId != 0) playSound(critId, 0.7f)
                else {
                    if (thudSoundId != 0) playSound(thudSoundId, 0.65f)
                    if (squishSoundId != 0) playSound(squishSoundId, 0.45f)
                    else toneBeep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 55)
                }
            }
            3 -> {
                if (killId != 0) playSound(killId, 0.75f)
                else {
                    if (thudSoundId != 0) playSound(thudSoundId, 0.7f)
                    if (squishSoundId != 0) playSound(squishSoundId, 0.55f)
                    else toneBeep(ToneGenerator.TONE_CDMA_CONFIRM, 70)
                }
            }
            4 -> {
                if (hurtId != 0) playSound(hurtId, 0.65f)
                else if (rustleSoundId != 0) playSound(rustleSoundId, 0.55f)
                else toneBeep(ToneGenerator.TONE_PROP_NACK, 50)
            }
            5 -> {
                if (critId != 0) playSound(critId, 0.52f, 1.25f)
                else toneBeep(ToneGenerator.TONE_CDMA_PIP, 55)
            }
            else -> {
                if (killId != 0) playSound(killId, 0.82f, 0.88f)
                if (critId != 0) playSound(critId, 0.68f, 1.18f)
                else toneBeep(ToneGenerator.TONE_CDMA_CONFIRM, 100)
            }
        }
    }

    private fun toneBeep(type: Int, durationMs: Int) {
        if (!soundEnabled) return
        try {
            tone?.startTone(type, durationMs)
        } catch (_: Throwable) {
        }
    }

    private fun vibrateSoft() {
        vibrateOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    private fun vibrateOneShot(ms: Long, amp: Int) {
        if (!hapticsEnabled) return
        val a = amp.coerceIn(1, 255)
        vibrator?.vibrate(VibrationEffect.createOneShot(ms, a))
    }

    private fun playSound(soundId: Int, volume: Float, rate: Float = 1f) {
        if (!soundEnabled || soundId == 0) return
        try {
            soundPool.play(soundId, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try {
            soundPool.release()
        } catch (_: Throwable) {
        }
        try {
            tone?.release()
        } catch (_: Throwable) {
        }
    }
}

@Composable
fun rememberHapticAudioManager(): HapticAudioManager {
    val context = LocalContext.current
    return remember { HapticAudioManager(context) }
}
