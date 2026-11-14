#!/usr/bin/env bash
# 시연 영상용 전체 흐름: 한계령 (docs/demo-video.md). 원본 약 9분, 편집 후 2~3분.
#
# 실제 한계령 도로 좌표로 주행을 재현한다(drive_route.py). 에뮬레이터 내비는 위치를 바꿀 수 없어
# 같은 주행을 "주행 재현 지도"(브라우저)에 따로 보여 준다.
#
# 준비: 에뮬레이터 실행(Cold boot), 멀미케어 디버그 APK 설치(adb install -r -g ...), python
# 실행:
#   bash scripts/emulator/demo.sh            # 응답을 adb로 자동 입력 (이때는 Extended Controls 마이크를 끄세요)
#   VOICE=1 bash scripts/emulator/demo.sh    # 사람이 마이크로 직접 답함 (주변을 조용히)
# 재현 지도: 브라우저에서 http://127.0.0.1:8090/map/
#
# 장면이 바뀔 때 "=== 장면 N", 주행 토막이 바뀔 때 "=== 토막 N"(편집점)을 출력한다.
set -u
export MSYS_NO_PATHCONV=1

# Windows 파이썬에는 Windows 경로(C:/...)를 넘겨야 한다. Git Bash의 /c/... 경로는 C:\c\... 로 잘못 읽힌다
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
ADB="${ADB:-${LOCALAPPDATA:-$HOME}/Android/Sdk/platform-tools/adb}"
BASE="http://127.0.0.1:8088"
VOICE="${VOICE:-0}"
SEGMENTS="${SEGMENTS:-8.0-9.3,14.6-16.6,37-40.5}"   # 곧은 길 → 한계령 고갯길 → 양양 방향 곧은 길
START=$(date +%s)

"$ADB" forward tcp:8088 tcp:8088 >/dev/null
# 자동 응답이면 PC 마이크를 끈다: 주변의 "그만" 같은 말에 놀이가 끝나는 것을 막는다
if [ "$VOICE" = "1" ]; then "$ADB" emu avd hostmicon >/dev/null; else "$ADB" emu avd hostmicoff >/dev/null; fi
( while true; do curl -s -m2 -X POST "$BASE/browser/session?client=mulmi-demo" >/dev/null; sleep 1; done ) &
HEARTBEAT=$!
# 재현 지도 서버. 이전 실행이 남긴 서버가 포트를 잡고 있으면 끝내고 새로 띄운다
OLD=$(netstat -ano 2>/dev/null | grep -E "127.0.0.1:8090 .*LISTENING" | awk '{print $NF}' | head -1)
[ -n "$OLD" ] && taskkill //F //PID "$OLD" >/dev/null 2>&1
python -I -m http.server 8090 --bind 127.0.0.1 --directory "$HERE" >/dev/null 2>&1 &
MAPSERVER=$!
DRIVER=""
cleanup() {
  curl -s -m2 -X POST "$BASE/browser/session?client=mulmi-demo&closing=1" >/dev/null
  kill $HEARTBEAT $MAPSERVER 2>/dev/null
  [ -n "$DRIVER" ] && kill "$DRIVER" 2>/dev/null
}
trap cleanup EXIT
sleep 2

t() { printf '[%3ss] ' $(( $(date +%s) - START )); }
scene() { echo; echo "=== 장면 $1 ($(( $(date +%s) - START ))초): $2"; }
put() { curl -s -m3 -o /dev/null -X PUT "$BASE/vehicle/$1"; }
broadcast() { "$ADB" shell "am broadcast -a com.mulmicare.$1 $2" >/dev/null; }

wait_log() {
  local pattern=$1 limit=${2:-30} i
  for ((i = 0; i < limit * 2; i++)); do
    if "$ADB" logcat -d -s MulmiCare:* | grep -q "$pattern"; then return 0; fi
    sleep 0.5
  done
  t; echo "⚠ '$pattern' 이(가) ${limit}초 안에 나오지 않음"
  return 1
}

answer() {
  local text=$1 result=$2
  if [ "$VOICE" = "1" ]; then
    t; echo "🎙 지금 \"$text\" 라고 말하세요"
    wait_log "$result" 15
  else
    broadcast DEBUG_SAY "--es text '$text'"
    t; echo "말함(adb): $text"
    wait_log "$result" 10
  fi
}

# --- 준비 ---
put "hvac?power=true&driver_temp=22&passenger_temp=22&fan_speed=3&recirc=true"
put "gear?gear=Park"
put "turn-signal?mode=Off"
put "controls?speed=0&accelerator=0&brake=0&steering=0"
python -I "$HERE/drive_route.py" --init-only --segments "$SEGMENTS" >/dev/null
"$ADB" shell am force-stop com.mulmicare.app
"$ADB" logcat -c
"$ADB" shell am start -n com.mulmicare.app/.MainActivity >/dev/null   # 대기 단계 서비스 시작
sleep 6
"$ADB" shell monkey -p ai.umos.maps.android.navigation.app -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3
t; echo "준비 끝: 공조 22℃, P단, 내비 화면, 재현 지도 http://127.0.0.1:8090/map/ — 녹화를 시작하세요 (5초 뒤 시작)"
sleep 5

scene 1 "출발 전 — 내비 목적지 오색, 정차 중 한 번 케어 제안"
broadcast DEBUG_ROUTE "--es lat 38.0778216 --es lng 128.4461094 --es name '오색약수'"
t; echo "내비 목적지: 오색약수"
wait_log "케어를 켤까요" 30 && sleep 3
answer "응" "케어 시작"
wait_log "공조 BASE" 10
sleep 4

scene 2 "곧은 길 — 조용히 (토막 1)"
put "gear?gear=Drive"; t; echo "기어 D, 주행 재현 시작"
python -I -u "$HERE/drive_route.py" --segments "$SEGMENTS" &
DRIVER=$!

scene 3 "한계령 고갯길 — 구불길에서 묻고, 응하면 음악 (토막 2)"
wait_log "리듬 놀이 할까요" 300 && sleep 3
answer "응" "비트 시작"
t; echo "커브 방향대로 좌우 소리, 공조 강화 — 고갯길 주행 중"

scene 4 "양양 방향 곧은 길 — 구간 끝, 지킨 별, 뒷자리 확인 (토막 3)"
wait_log "뒷자리는 괜찮아요" 420 && sleep 3
answer "괜찮아요" "듣기 끝"

scene 5 "도착 — 케어 끝, 공조를 원래대로"
wait "$DRIVER"; DRIVER=""
put "gear?gear=Park"; t; echo "정차, P단"
sleep 2
broadcast DEBUG_END_CARE ""        # 에뮬레이터 내비는 위치가 고정이라 도착 이벤트를 adb로 대신함
t; echo "도착(재현)"
wait_log "공조 되돌리기 확인" 12
sleep 4
echo; t; echo "끝. 녹화를 멈추세요."
