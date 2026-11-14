#!/usr/bin/env bash
# SC3 굽은 국도 시나리오를 Pleos Connect Emulator의 Virtual Console API로 재생한다.
# core 인수 테스트(ScenarioTest SC3)와 같은 흐름을 실제 시간으로 돌린다. 약 3분.
#
# 준비: 에뮬레이터 실행(Cold boot), 멀미케어 디버그 APK 설치 후
#   adb shell am start -n com.mulmicare.app/.MainActivity --ez autostart true
# 실행: bash scripts/emulator/sc3-country-road.sh
# 결과: adb logcat -s MulmiCare
#
# 조향 값은 Virtual Console 기준 핸들 회전각(오른쪽 양수). 앱이 앞바퀴 각도로 바꾼다(조향비 14).
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB:-${LOCALAPPDATA:-$HOME}/Android/Sdk/platform-tools/adb}"
BASE="http://127.0.0.1:8088"
WHEEL="${WHEEL:-40}"   # 굽은 길에서 돌리는 핸들 각도. 40° ≈ 앞바퀴 2.9°

"$ADB" forward tcp:8088 tcp:8088 >/dev/null

# Virtual Console은 브라우저 세션 신호가 1초마다 있어야 값을 받는다
( while true; do curl -s -m2 -X POST "$BASE/browser/session?client=mulmi-sc3" >/dev/null; sleep 1; done ) &
HEARTBEAT=$!
trap 'curl -s -m2 -X POST "$BASE/browser/session?client=mulmi-sc3&closing=1" >/dev/null; kill $HEARTBEAT 2>/dev/null' EXIT
sleep 2

START=$(date +%s)
at() { # at <초> : 시나리오 시작 기준으로 그 시각까지 기다린다
  local now; now=$(( $(date +%s) - START ))
  [ "$1" -gt "$now" ] && sleep $(( $1 - now ))
  printf '[%3ss] ' "$1"
}
SPEED=60
drive() { # drive <핸들각> [속도]
  [ $# -ge 2 ] && SPEED=$2
  curl -s -m3 -o /dev/null -X PUT "$BASE/vehicle/controls?speed=$SPEED&accelerator=20&brake=0&steering=$1"
  echo "핸들 $1°, 속도 ${SPEED}km/h"
}
signal() { curl -s -m3 -o /dev/null -X PUT "$BASE/vehicle/turn-signal?mode=$1"; echo "방향지시등 $1"; }
# adb shell은 인자를 기기 셸에서 다시 나누므로, 공백이 있는 말도 한 덩어리로 가도록 명령 전체를 따옴표로 보낸다
say() { "$ADB" shell "am broadcast -a com.mulmicare.DEBUG_SAY --es text '$1'" >/dev/null; echo "말함: $1"; }

at 0;   curl -s -m3 -o /dev/null -X PUT "$BASE/vehicle/hvac?power=true"; echo "공조 전원 켬 (꺼져 있으면 차량이 공조 설정을 거부함)"
at 0;   curl -s -m3 -o /dev/null -X PUT "$BASE/vehicle/gear?gear=Drive"; echo "기어 D"
at 0;   drive 0 60
at 10;  drive -"$WHEEL"         # 왼쪽
at 12;  drive 0
at 15;  drive "$WHEEL"          # 오른쪽
at 17;  drive 0
at 20;  drive -"$WHEEL"         # 3번째 → 굽은 길 구간 시작, "리듬 놀이 할까요?"
at 22;  say "응"                 # 놀이 시작
at 23;  drive 0
at 25;  drive "$WHEEL"          # → 오른쪽 소리
at 27;  drive 0
at 30;  signal BlinkLeft        # → 왼쪽 소리 (돌기 전 예고)
at 32;  drive -"$WHEEL"         # 같은 동작이라 다시 울리지 않음
at 34;  signal Off
at 34;  drive 0
at 40;  drive 0 48              # 급감속 → 별 하나
at 45;  drive "$WHEEL"          # → 오른쪽 소리
at 47;  drive 0
echo "직선 주행. 약 2분 뒤 구간 종료 → \"별 두 개\" → \"뒷자리는 괜찮아요?\""
# 구간 종료 시각은 이벤트 창 계산에 따라 조금씩 달라지므로, 질문이 나오면 바로 답한다
for _ in $(seq 1 180); do
  if "$ADB" logcat -d -s MulmiCare:* | grep -q "뒷자리는 괜찮아요"; then break; fi
  sleep 1
done
printf '[%3ss] ' $(( $(date +%s) - START )); say "괜찮아요"
sleep 2; echo "끝"
