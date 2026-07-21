package com.jellystorage.play

/**
 * 广告位配置（Google AdMob）。
 *
 * ## 你要投放真实广告时
 * 1. 打开 [AdMob 控制台](https://admob.google.com/) 创建应用
 * 2. 创建「插页式」「激励视频」广告单元
 * 3. 把下面的 **TEST** ID 换成你的正式 ID
 * 4. AndroidManifest 里的 APPLICATION_ID 也换成你的应用 ID（`ca-app-pub-xxxx~yyyy`）
 *
 * 当前默认是 **Google 官方测试 ID**，可安全调试，不会产生收益。
 */
object AdConfig {
    /** true=测试广告（推荐联调）；上线前改 false 并填正式 unit id */
    const val USE_TEST_ADS = true

    // —— 正式投放时改这里 ——
    const val PROD_APP_ID = "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"
    const val PROD_INTERSTITIAL = "ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz"
    const val PROD_REWARDED = "ca-app-pub-xxxxxxxxxxxxxxxx/wwwwwwwwww"

    // Google sample units（勿用于上架收益）
    private const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    val appId: String get() = if (USE_TEST_ADS) TEST_APP_ID else PROD_APP_ID
    val interstitialUnitId: String get() = if (USE_TEST_ADS) TEST_INTERSTITIAL else PROD_INTERSTITIAL
    val rewardedUnitId: String get() = if (USE_TEST_ADS) TEST_REWARDED else PROD_REWARDED
}
