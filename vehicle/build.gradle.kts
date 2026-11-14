// Pleos SDK 어댑터. core/port 인터페이스를 구현만 한다 (pleos-sdk 스킬)
plugins {
    id("com.android.library")
}

android {
    namespace = "com.mulmicare.vehicle"
    compileSdk = 34
    defaultConfig {
        minSdk = 34
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // 차량 속도·기어는 Vehicle SDK가 아니라 Android Car API(CarPropertyManager)로 받는다
    useLibrary("android.car")
}

dependencies {
    implementation(project(":core"))
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // 내비: 경로 시작·남은 시간(F1.1), 회전 안내, 현재 위치, 경유지 추가(F7.3)
    implementation("ai.pleos.playground:NaviHelper:3.0.5")

    testImplementation("junit:junit:4.13.2")
}
