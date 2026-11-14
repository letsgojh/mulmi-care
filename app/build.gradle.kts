import java.util.Properties

plugins {
    id("com.android.application")
}

// 릴리스 서명: 저장소 밖 키를 keystore.properties(커밋 안 함)로 가리킨다. 없으면 서명 없이 빌드된다.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.mulmicare.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.mulmicare.app"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
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
