package com.jellystorage

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.jellystorage.play.AdConfig
import com.jellystorage.play.CrashReporter

class JellyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // 尽早初始化；失败不影响进游戏
        try {
            MobileAds.initialize(this) { }
            AdConfig.appId
        } catch (_: Throwable) {
        }
    }
}
