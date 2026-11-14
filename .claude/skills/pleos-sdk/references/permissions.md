# 선언할 권한 (S10: 필요한 최소한만, 이유와 함께)

app/src/main/AndroidManifest.xml에 선언하는 권한은 이 목록과 같아야 한다. `safety-guard`가 대조한다.

| 권한 | 이유 | spec | 상태 |
|---|---|---|---|
| `pleos.car.permission.CAR_DRIVE` | 앞바퀴 조향각 수신 | D1, F4.2, F5.3 | 어댑터 구현됨 |
| `pleos.car.permission.CAR_LIGHTS` | 방향지시등 상태 수신 | F4.3 | 어댑터 구현됨 |
| `android.car.permission.CAR_SPEED` | 차량 속도 (감속 판단). dangerous → 런타임 요청 | D1, F5.3 | 어댑터 구현됨 |
| `android.car.permission.CAR_POWERTRAIN` | 기어(P단) 판단 | F1.1, F8 | 어댑터 구현됨 |
| `pleos.car.permission.TTS_SERVICE` | 음성 출력 (Pleos TTS) | F3, F5.7, F7 | 구현됨 |
| `pleos.car.permission.STT_SERVICE` | 짧은 음성 응답 인식 (Pleos STT). 질문 직후와 놀이 중에만 (F3.6) | F3, F7 | 구현됨 |
| `android.permission.FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | 앱이 화면에 없어도 신호 수신·소리 출력 | 검증 항목 3 | 구현됨 |
| `android.permission.INTERNET` | **우리가 선언하지 않음. TTS·STT AAR이 합쳐 넣음.** ON_DEVICE 모드라 쓰지 않는다. 위치·기록을 보내는 코드 없음(S8) | — | SDK 유래 |

## 아직 추가하지 않은 것 (기능 구현 때 추가)
- 공조 조회·제어 (F6): `pleos.car.permission.CAR_CLIMATE`, `pleos.car.permission.CONTROL_CAR_CLIMATE`
- 위치 (F7 쉼터): FusedLocation SDK 문서 확인 후

## 공모전 참가자에게 승인되는지 (검증 항목 1)
아직 확인 안 함. 에뮬레이터에서 각 리스너가 값을 받는지로 먼저 확인하고 결과를 verification.md에 적는다.
