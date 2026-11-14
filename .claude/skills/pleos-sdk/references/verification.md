# 구현 착수 전 검증 항목 결과

환경: Pleos Connect Emulator v2.1.0 (API 34, user 빌드), 2026-11-14. 실차 확인은 아직 없음.

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| 1 | 조향·방향지시등 조회 권한, 속도·기어 조회 | **에뮬레이터에서 통과** | 권한 4개 granted. 조향각·방향지시등(Pleos SDK), 속도·기어(Car API) 모두 수신. 공모전 참가자 승인 여부(App Market 심사)는 미확인 |
| 1 | 공조 조회·제어 | **에뮬레이터에서 통과 (조건부)** | 기준값 읽기(온도·풍량·최대 풍량·풍향·바깥 기온) 성공. 기본 21℃ → 굽은 길에서 강화 19.5℃·풍량 4·얼굴 방향 적용, 다시 읽어 반영 확인. **공조 전원이 꺼져 있으면 설정이 거부됨(code 2)**. 앱의 전원 켜기 요청은 에뮬레이터에서 반영되지 않아 Virtual Console로 전원을 켜고 시험함 |
| 2 | 내비 예상 소요 시간, 회전 안내 이벤트 | 미확인 | NaviHelper SDK 아직 안 봄 |
| 3 | 앱이 화면에 없을 때 차량 상태 수신 | **에뮬레이터에서 통과** | 포그라운드 서비스(specialUse)에서 수신 |
| 3 | 음성 출력 | **에뮬레이터에서 통과** | Android 기본 TTS는 엔진이 없어 실패(-1) → Pleos TextToSpeech SDK(ON_DEVICE, ko-KR)로 바꿔 포그라운드 서비스에서 재생 확인 (references/tts.md) |
| 3 | 짧은 음성 응답 인식 | **에뮬레이터에서 통과** | Pleos STT(ON_DEVICE, ko-KR). 질문을 다 말한 뒤 마이크 열림 → 사람 목소리 "응"으로 비트 시작, "그만"으로 비트 끝 확인 (references/stt.md) |
| 4 | 비트·효과음과 내비 안내 음성이 겹칠 때 | 미확인 | |
| 5 | 외기 유입 전환과 자동 내기 전환 충돌 | 미확인 | 에뮬레이터 공조 AUTO는 꺼진 상태에서만 시험함. 실차 확인 필요 |

## 에뮬레이터에서 발견한 차이 (중요)
- **조향각**: SDK 주석은 "앞바퀴 각도, 왼쪽 음수"인데, 에뮬레이터는 Virtual Console의 **핸들 회전각을 그대로, 오른쪽을 음수로** 준다. (콘솔 오른쪽 90° → 앱 수신 -90.0)
  → `SteeringCalibration.PLEOS_EMULATOR`(부호 반전, 조향비 14)로 보정. 실차는 `SDK_FRONT_WHEEL`(그대로)로 가정. **실차에서 꼭 확인할 것.**
- `adb shell cmd car_service inject-vhal-event`는 user 빌드라 막혀 있다.

## 공조 시험 전 준비
- Virtual Console로 공조 전원을 켠다: `PUT /vehicle/hvac?power=true` (세션 신호 필요)

## 차량 값 바꾸는 법: Virtual Console
- `adb forward tcp:8088 tcp:8088` → 브라우저 `http://127.0.0.1:8088/` (Cold boot 필요)
- 스크립트로도 된다(HTTP API). **1초마다 `POST /browser/session?client=<id>`를 보내야** 값을 받는다. 안 보내면 503 `emulator_inactive`.
  - `PUT /vehicle/controls?speed=<km/h>&accelerator=<%>&brake=<%>&steering=<핸들°, 오른쪽 +>`
  - `PUT /vehicle/gear?gear=Park|Reverse|Neutral|Drive`
  - `PUT /vehicle/turn-signal?mode=Off|BlinkLeft|BlinkRight|...`
  - `GET /vehicle/state`, `GET /vehicle/catalog`(전체 카드 목록, HVAC 포함)
- 시나리오 재생 스크립트: `scripts/emulator/sc3-country-road.sh`
