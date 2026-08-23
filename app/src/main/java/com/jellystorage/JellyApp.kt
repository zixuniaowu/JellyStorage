package com.jellystorage

import android.app.Application
import com.jellystorage.play.CrashReporter
import com.jellystorage.play.GearArtAssets
import com.jellystorage.play.HeroArtAssets

class JellyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        GearArtAssets.initialize(this)
        HeroArtAssets.initialize(this)
        // Ads are initialized only after UMP has refreshed the user's consent state.
    }
}
