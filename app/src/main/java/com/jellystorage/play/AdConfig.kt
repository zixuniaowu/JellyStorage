package com.jellystorage.play

import com.jellystorage.BuildConfig

/**
 * 广告位配置（Google AdMob）。
 *
 * ## 你要投放真实广告时
 * 1. 打开 [AdMob 控制台](https://admob.google.com/) 创建应用
 * 2. 创建「插页式」「激励视频」广告单元
 * 3. 在本地 `release.properties` 中填写 ID
 * 4. 使用 `bundleRelease -PplayStoreRelease=true` 生成受校验的上架包
 *
 * 当前默认是 **Google 官方测试 ID**，可安全调试，不会产生收益。
 */
object AdConfig {
    val useTestAds: Boolean get() = BuildConfig.ADS_USE_TEST_MODE
    val interstitialUnitId: String get() = BuildConfig.ADMOB_INTERSTITIAL_ID
    val rewardedUnitId: String get() = BuildConfig.ADMOB_REWARDED_ID
}
