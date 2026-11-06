plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 默认目标 / 可选目标清单，统一来自 gradle.properties
val defaultPackage: String =
    (project.findProperty("appwatch.defaultPackage") as String?) ?: "com.mobiletools.systemhelper"
val defaultName: String =
    (project.findProperty("appwatch.defaultName") as String?) ?: "SystemHelper"
val candidateList: List<String> =
    ((project.findProperty("appwatch.candidates") as String?) ?: defaultPackage)
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

android {
    namespace = "com.koketsu.appwatch"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.koketsu.appwatch"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ① Android 11+ 包可见性：<queries> 里直接列出所有候选包
        //    （占位符只用于默认目标的说明，真正的清单见 AndroidManifest.xml）
        manifestPlaceholders["targetPackage"] = defaultPackage

        // ② 注入 BuildConfig
        buildConfigField("String", "DEFAULT_PACKAGE", "\"$defaultPackage\"")
        buildConfigField("String", "DEFAULT_NAME", "\"$defaultName\"")
        buildConfigField(
            "String[]",
            "CANDIDATES",
            "new String[]{" + candidateList.joinToString(",") { "\"$it\"" } + "}"
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // 后台每天中午检查：WorkManager（系统级任务调度，重启后自动恢复）
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
