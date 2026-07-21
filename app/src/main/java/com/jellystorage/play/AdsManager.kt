package com.jellystorage.play

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * 免费游戏变现：插屏（关卡/结算）+ 激励视频（双倍金/免费药）。
 * 无网或加载失败时静默跳过，不卡流程。
 */
class AdsManager(private val appContext: Context) {
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var initialized = false
    private var interstitialShows = 0

    fun ensureInit() {
        if (initialized) return
        initialized = true
        try {
            MobileAds.initialize(appContext) { }
            preloadAll()
        } catch (t: Throwable) {
            Log.w(TAG, "MobileAds init failed", t)
        }
    }

    fun preloadAll() {
        loadInterstitial()
        loadRewarded()
    }

    val isRewardedReady: Boolean get() = rewarded != null

    private fun loadInterstitial() {
        if (loadingInterstitial || interstitial != null) return
        loadingInterstitial = true
        InterstitialAd.load(
            appContext,
            AdConfig.interstitialUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingInterstitial = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                    interstitial = null
                    Log.d(TAG, "interstitial fail: ${error.message}")
                }
            }
        )
    }

    private fun loadRewarded() {
        if (loadingRewarded || rewarded != null) return
        loadingRewarded = true
        RewardedAd.load(
            appContext,
            AdConfig.rewardedUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadingRewarded = false
                    rewarded = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingRewarded = false
                    rewarded = null
                    Log.d(TAG, "rewarded fail: ${error.message}")
                }
            }
        )
    }

    /**
     * 插屏：用于结算返回标题等。失败直接 onDone。
     * @param everyN 每 N 次展示 1 次（降打扰）
     */
    fun showInterstitial(activity: Activity?, everyN: Int = 1, onDone: () -> Unit) {
        ensureInit()
        interstitialShows++
        if (everyN > 1 && interstitialShows % everyN != 0) {
            onDone()
            return
        }
        val ad = interstitial
        if (activity == null || ad == null) {
            loadInterstitial()
            onDone()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                loadInterstitial()
                onDone()
            }
        }
        try {
            ad.show(activity)
        } catch (t: Throwable) {
            Log.w(TAG, "show interstitial", t)
            loadInterstitial()
            onDone()
        }
    }

    /** 激励视频：看完才给奖励；未加载完成则 onFail */
    fun showRewarded(activity: Activity?, onReward: () -> Unit, onFail: () -> Unit = {}) {
        ensureInit()
        val ad = rewarded
        if (activity == null || ad == null) {
            loadRewarded()
            onFail()
            return
        }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded()
                if (!earned) onFail()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                loadRewarded()
                onFail()
            }
        }
        try {
            ad.show(activity) {
                earned = true
                onReward()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "show rewarded", t)
            loadRewarded()
            onFail()
        }
    }

    companion object {
        private const val TAG = "JellyAds"
    }
}
