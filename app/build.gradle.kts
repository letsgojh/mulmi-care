plugins {
    id("com.android.application")
}

android {
    namespace = "com.mulmicare.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mulmicare.app"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":vehicle"))
    // 음성 출력. ON_DEVICE 모드로 쓴다 (네트워크·앱 등록 불필요)
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    // 짧은 음성 응답 인식. ON_DEVICE 모드
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
}
