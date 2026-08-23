package com.jellystorage.play

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * 免费游戏变现：插屏（关卡/结算）+ 激励视频（双倍金/免费药）。
 * 无网或加载失败时静默跳过，不卡流程。
 */
class AdsManager(private val appContext: Context) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var initialized = false
    private var consentRequestStarted = false
    private var interstitialShows = 0

    /** 插页展示后的未决回调：dismiss 回调偶发丢失时由 resume 兜底触发 */
    private var pendingInterstitialDone: (() -> Unit)? = null
    private var pendingInterstitialShownAt = 0L

    init {
        // 广告 Activity 关闭后宿主 Activity 恢复前台；若此时 dismiss 回调未到（GMS 偶发丢失），
        // 超过 2 秒仍挂起则直接放行，避免结算/返回标题被卡死。
        (appContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    val pending = pendingInterstitialDone ?: return
                    if (SystemClock.elapsedRealtime() - pendingInterstitialShownAt > 2_000L) {
                        pendingInterstitialDone = null
                        Log.w(TAG, "interstitial dismiss callback lost, releasing via resume watchdog")
                        pending()
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
        )
    }

    /** Refresh consent once per app session, then initialize ads only when allowed. */
    fun gatherConsent(activity: Activity?, onComplete: (String?) -> Unit = {}) {
        if (activity == null) {
            onComplete("无法打开广告隐私设置")
            return
        }
        if (consentRequestStarted) return
        consentRequestStarted = true
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    initializeAdsIfAllowed()
                    onComplete(formError?.message)
                }
            },
            { requestError ->
                // A valid decision from a previous session may still allow requests.
                initializeAdsIfAllowed()
                Log.w(TAG, "consent update failed: ${requestError.message}")
                onComplete(requestError.message)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity?, onComplete: (String?) -> Unit = {}) {
        if (activity == null) {
            onComplete("无法打开广告隐私设置")
            return
        }
        if (consentInformation.privacyOptionsRequirementStatus !=
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ) {
            onComplete("当前地区无需额外广告隐私设置")
            return
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            initializeAdsIfAllowed()
            onComplete(formError?.message)
        }
    }

    private fun initializeAdsIfAllowed() {
        if (initialized || !consentInformation.canRequestAds()) return
        initialized = true
        try {
            MobileAds.initialize(appContext) { }
            preloadAll()
        } catch (t: Throwable) {
            Log.w(TAG, "MobileAds init failed", t)
        }
    }

    fun preloadAll() {
        if (!initialized) return
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
        initializeAdsIfAllowed()
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
        var fired = false
        fun completeOnce() {
            if (fired) return
            fired = true
            pendingInterstitialDone = null
            loadInterstitial()
            onDone()
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                completeOnce()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                completeOnce()
            }
        }
        try {
            pendingInterstitialDone = ::completeOnce
            pendingInterstitialShownAt = SystemClock.elapsedRealtime()
            ad.show(activity)
        } catch (t: Throwable) {
            Log.w(TAG, "show interstitial", t)
            completeOnce()
        }
    }

    /** 激励视频：看完才给奖励；未加载完成则 onFail */
    fun showRewarded(activity: Activity?, onReward: () -> Unit, onFail: () -> Unit = {}) {
        initializeAdsIfAllowed()
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
