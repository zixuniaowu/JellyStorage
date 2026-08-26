import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseProperties = Properties().apply {
    val configFile = rootProject.file("release.properties")
    if (configFile.isFile) configFile.inputStream().use(::load)
}

fun releaseValue(name: String): String? =
    providers.environmentVariable(name).orNull?.trim()?.takeIf(String::isNotEmpty)
        ?: releaseProperties.getProperty(name)?.trim()?.takeIf(String::isNotEmpty)

val playStoreRelease = providers.gradleProperty("playStoreRelease")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"
val testRewardedId = "ca-app-pub-3940256099942544/5224354917"
val adMobAppId = if (playStoreRelease) releaseValue("ADMOB_APP_ID") ?: testAdMobAppId else testAdMobAppId
val interstitialId = if (playStoreRelease) releaseValue("ADMOB_INTERSTITIAL_ID") ?: testInterstitialId else testInterstitialId
val rewardedId = if (playStoreRelease) releaseValue("ADMOB_REWARDED_ID") ?: testRewardedId else testRewardedId
val privacyPolicyUrl = releaseValue("PRIVACY_POLICY_URL").orEmpty()

fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.jellystorage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jellystorage"
        minSdk = 26
        targetSdk = 36
        versionCode = 220
        versionName = "1.12.0"

        manifestPlaceholders["admobAppId"] = adMobAppId
        buildConfigField("boolean", "ADS_USE_TEST_MODE", (!playStoreRelease).toString())
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", quotedBuildConfig(interstitialId))
        buildConfigField("String", "ADMOB_REWARDED_ID", quotedBuildConfig(rewardedId))
        buildConfigField("String", "PRIVACY_POLICY_URL", quotedBuildConfig(privacyPolicyUrl))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val storePath = releaseValue("PLAY_UPLOAD_STORE_FILE")
        val storePasswordValue = releaseValue("PLAY_UPLOAD_STORE_PASSWORD")
        val keyAliasValue = releaseValue("PLAY_UPLOAD_KEY_ALIAS")
        val keyPasswordValue = releaseValue("PLAY_UPLOAD_KEY_PASSWORD")
        if (playStoreRelease && storePath != null && storePasswordValue != null &&
            keyAliasValue != null && keyPasswordValue != null
        ) {
            create("playUpload") {
                storeFile = rootProject.file(storePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("playUpload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    // 1.18+ is compiled against API 36.1/37; 1.17 is the API 36 line.
    implementation("androidx.core:core-ktx:1.17.0")
    // AdMob：插屏 + 激励（免费游戏变现）
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui")
    androidTestImplementation("androidx.compose.ui:ui-graphics")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

tasks.register("verifyPlayReleaseConfig") {
    group = "verification"
    description = "Checks signing, AdMob and privacy configuration before a Play Store bundle is built."
    doLast {
        val required = listOf(
            "ADMOB_APP_ID",
            "ADMOB_INTERSTITIAL_ID",
            "ADMOB_REWARDED_ID",
            "PRIVACY_POLICY_URL",
            "PLAY_UPLOAD_STORE_FILE",
            "PLAY_UPLOAD_STORE_PASSWORD",
            "PLAY_UPLOAD_KEY_ALIAS",
            "PLAY_UPLOAD_KEY_PASSWORD"
        )
        val missing = required.filter { releaseValue(it).isNullOrBlank() }
        check(missing.isEmpty()) {
            "Missing Play release values: ${missing.joinToString()}. " +
                "Copy release.properties.example to release.properties and fill it in."
        }
        check(releaseValue("PRIVACY_POLICY_URL")!!.startsWith("https://")) {
            "PRIVACY_POLICY_URL must be a public HTTPS URL."
        }
        val storePath = releaseValue("PLAY_UPLOAD_STORE_FILE")!!
        check(rootProject.file(storePath).isFile) {
            "Upload keystore does not exist: ${rootProject.file(storePath)}"
        }
        val productionAdIds = mapOf(
            "ADMOB_APP_ID" to releaseValue("ADMOB_APP_ID")!!,
            "ADMOB_INTERSTITIAL_ID" to releaseValue("ADMOB_INTERSTITIAL_ID")!!,
            "ADMOB_REWARDED_ID" to releaseValue("ADMOB_REWARDED_ID")!!
        )
        check(productionAdIds.values.none { it.startsWith("ca-app-pub-3940256099942544") }) {
            "A Play Store release must not use Google's sample AdMob IDs."
        }
        check(productionAdIds.getValue("ADMOB_APP_ID").matches(Regex("ca-app-pub-\\d{16}~\\d{10}"))) {
            "ADMOB_APP_ID has an invalid format."
        }
        productionAdIds.filterKeys { it != "ADMOB_APP_ID" }.forEach { (name, value) ->
            check(value.matches(Regex("ca-app-pub-\\d{16}/\\d{10}"))) {
                "$name has an invalid format."
            }
        }
    }
}

if (playStoreRelease) {
    tasks.configureEach {
        if (name == "bundleRelease" || name == "assembleRelease") {
            dependsOn("verifyPlayReleaseConfig")
        }
    }
}
