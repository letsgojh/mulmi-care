---
name: pleos-sdk
description: Pleos Connect SDK(Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation) 사용법, 권한, Capability 확인, 에뮬레이터 설정. vehicle/ 모듈 어댑터를 만들거나 고칠 때, SDK 메서드·권한·gradle 의존성을 쓸 때, 조향각·방향지시등·제동·속도·기어·공조·경유지·TTS·STT·위치 API를 다룰 때 반드시 사용.
---

# Pleos Connect SDK

## 원칙
- **API 시그니처를 기억으로 쓰지 않는다.** 이 스킬을 만든 시점에는 문서 개요만 확인했다. 아래 "기획서 기재 API" 목록도 검증 전이다.
- 처음 쓰는 도메인은 해당 문서 페이지를 읽고 `references/<도메인>.md`에 정리한 다음 구현한다. 이미 정리된 파일이 있으면 그 파일부터 본다.
- 정리할 때는 클래스명, 인스턴스 얻는 법, 메서드 시그니처, 단위와 값 범위, enum, 콜백, 해제 메서드, Capability 확인 메서드, 권한 문자열을 문서 그대로 옮기고 출처 URL을 붙인다.

## 환경
저장소 URL, SDK 좌표, 에뮬레이터 AVD, JDK 설정은 [references/setup.md](references/setup.md)를 따른다.

## 이미 확인한 것
- Vehicle SDK 시그니처, 조향각이 **앞바퀴 각도**라는 점, 속도·기어는 Android Car API로 받는다는 점: [references/vehicle.md](references/vehicle.md)
- 선언 권한 목록과 이유: [references/permissions.md](references/permissions.md)
- TextToSpeech SDK(초기화 순서 주의): [references/tts.md](references/tts.md)
- SpeechToText SDK(마이크 열림 = onReady): [references/stt.md](references/stt.md)
- NaviHelper SDK(경로 시간, 회전 안내, 휴게소, 경유지): [references/navi.md](references/navi.md)
- 검증 항목 결과, **에뮬레이터 조향 값 차이(핸들각·부호 반대)**, Virtual Console HTTP API로 차량 값 바꾸기: [references/verification.md](references/verification.md)

## 문서에서 확인한 사실 (개요 페이지)
- 도메인마다 `checkXXXCapabilities` API로 지원 여부를 확인한다. 상세: Vehicle/check-capability
- API별 필요 권한을 앱에 선언해야 한다. **Pleos App Market과 협의·승인된 범위의 권한만** 쓸 수 있다. 승인되지 않은 권한을 선언하거나 그런 API를 호출하면 앱 심사에서 반려된다.
- Android Automotive로 옮겨지는 Vehicle SDK 기능은 최소 2개 메이저 버전 동안 하위 호환을 유지한다.

## 문서 위치
기준: `https://document.pleos.ai/docs/pleos-only/`

| 주제 | 경로 |
|---|---|
| SDK 개요 | `connect-sdk-pleos/` |
| 개발 환경 구성(SDK, 에뮬레이터) | `https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator` |
| 시작하기 / 프로젝트 생성·SDK 사용 신청 | `getting-started`, `create-my-project` |
| Vehicle SDK | `connect-sdk-pleos/Vehicle/intro` |
| Capability 확인 | `connect-sdk-pleos/Vehicle/check-capability` |
| NaviHelper SDK | `connect-sdk-pleos/NaviHelper/intro` |
| TextToSpeech SDK | `connect-sdk-pleos/TextToSpeech/intro` |
| SpeechToText SDK | `connect-sdk-pleos/SpeechToText/intro` |
| FusedLocation SDK | `connect-sdk-pleos/FusedLocation/intro` |
| 앱 기획·설계 가이드 | `vehicle-app-planning-guide/intro` |
| 앱 개발·연동 가이드 | `vehicle-app-developer-guide/intro` |
| Vehicle SDK 샘플 앱 | `tutorial/vehicle-sdk-sample-app` |
| 앱 테스트 가이드 | `app-test-guide/form-factor-test-guide` |
| 심사 가이드(SDK API) | `distribute/app-review-guide/review-functionality-performance-stability#sdk-api-review` |
| 에뮬레이터 릴리스 노트 | `release-note/emulator-release-note` |

## 기획서 기재 API (문서로 검증 필요)
| 용도 | 기획서 표기 | spec |
|---|---|---|
| 조향 시작 감지 | Vehicle `SteeringWheel` · `registerSteeringWheelAngle` | F4.2, F5.3, D1 |
| 방향지시등 | Vehicle `TurnSignal` · `registerTurnSignalState` | F4.3 |
| 제동 | Vehicle `Brake` (제동 페달 상태, 보조 신호) | F5.3 |
| 속도·기어 | 차량 속도, 기어(P단) | F1.1, F5.3, F8 |
| 공조 | `setHvacRecirculation`, `setHvacTemperature`, `setHvacFanSpeed`, `setHvacFanDirection` + 대응 조회·콜백 | F6 |
| 내비 | NaviHelper 경유지 추가, `onTBTInfo`, `onDrivingInfo`, 목적지 예상 소요 | F1.1, F7.3 |
| 음성 출력 | TextToSpeech (온디바이스 합성 병행) | F3, F5.7, F7 |
| 음성 인식 | SpeechToText ("응", "아니", "힘들어해요", "그만") | F3.3~F3.6, F7.2 |
| 위치 | FusedLocation (쉼터 탐색) | F7.2 |

## 구현 착수 전 검증 항목 (기획서)
결과는 `references/verification.md`에 기록한다. 확인되지 않는 항목은 범위에서 빼거나 사용자 입력으로 대신한다.
1. 방향지시등·조향각·제동 조회 권한, 공조 제어 권한이 공모전 참가자에게 승인되는가? 차량 속도와 기어(P단)를 조회할 수 있는가?
2. 내비에서 목적지까지 예상 소요 시간과 회전 안내 이벤트가 실제로 오는가? 회전 방향과 남은 거리가 들어 있는가?
3. 앱이 화면에 없는 상태에서도 차량 상태 수신과 음성 출력이 되는가? 질문 직후와 놀이 중에 짧은 음성 응답을 인식할 수 있는가?
4. 비트·효과음이 내비 안내 음성이나 재생 중인 음악과 겹치면 어떻게 되는가? (내비 우선)
5. 외기 유입 전환이 차량의 자동 내기 전환 기능과 충돌하지 않는가?

## 어댑터 작성 규칙
- `vehicle/`의 어댑터는 `core/port`의 인터페이스를 구현만 한다. 판단 로직을 넣지 않는다.
- 호출 순서: Capability 확인 → 권한 확인 → 호출 → 비동기 결과 처리 → 실패 시 `PortResult.Unsupported`/`Failed`로 돌려준다. 예외를 core까지 올리지 않는다.
- SDK 값을 `VehicleSignal`로 바꾸는 부분은 순수 함수로 분리해 단위 테스트한다(단위, 부호, enum 매핑).
- 등록한 리스너는 서비스 종료 시 반드시 해제한다.
- 권한은 필요한 최소한만 `AndroidManifest.xml`에 선언하고, 선언한 권한 목록을 `references/permissions.md`에 이유와 함께 적는다.
