# 개발 환경 (이 PC에서 확인한 사실, 2026-11-14)

## Maven 저장소와 좌표
- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (인증 없음). `settings.gradle.kts`에 등록함
- Vehicle SDK: `ai.pleos.playground:Vehicle:2.3.2`
  - 전이 의존성: `ai.pleos.playground:vehicle-aidl:1.1.1`, `ai.pleos.playground:vehicle-data:1.1.11`, `com.google.dagger:hilt-android:2.51.1`, `kotlin-stdlib:1.9.25`
  - 소스 jar도 있다: Gradle 캐시 `~/.gradle/caches/modules-2/files-2.1/ai.pleos.playground/Vehicle/2.3.2/` → API 시그니처를 확인할 때 문서와 함께 본다
- NaviHelper, TextToSpeech, SpeechToText, FusedLocation의 좌표는 아직 확인하지 않음. 문서 확인 후 여기에 추가

## 에뮬레이터
- AVD: `Pleos_Connect_v2.1.0`
- 시스템 이미지: `system-images/android-34/pleos_car_emulator/x86_64` (API 34, `android-automotive`)
- 따라서 `compileSdk = 34`, `minSdk = 34`, `targetSdk = 34`

## 빌드 도구
- JDK: Android Studio 내장 JBR (`C:/Program Files/Android/Android Studio/jbr`, JDK 25). PATH에 `java`가 없으므로 `JAVA_HOME`을 지정해서 실행한다
  ```bash
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
  ./gradlew :core:test
  ```
- Gradle 9.4.1 (JDK 25에서 실행 가능. 8.x는 JDK 25에서 실행되지 않음), AGP 9.2.0 (Kotlin 내장 지원), Kotlin 2.4.0
- 모듈 바이트코드 타깃: JVM 17

## 참고
- 이전 프로젝트 `~/Desktop/button-coach`도 같은 저장소와 Vehicle 2.3.2를 쓴다. 어댑터 작성 시 그쪽 사용 예를 참고할 수 있다 (판단 근거는 공식 문서와 소스 jar)
