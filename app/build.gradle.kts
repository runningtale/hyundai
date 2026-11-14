import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// LLM API 키는 local.properties의 LLM_API_KEY에서만 읽음 (저장소에 올리지 않음)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val llmApiKey: String = localProps.getProperty("LLM_API_KEY", "")

android {
    namespace = "com.runningtale"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.runningtale"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "LLM_API_KEY", "\"$llmApiKey\"")
    }

    buildTypes {
        release {
            // 제출용 APK에는 키를 넣지 않음 → 항상 데모 모드
            buildConfigField("String", "LLM_API_KEY", "\"\"")
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    implementation("androidx.media:media:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
