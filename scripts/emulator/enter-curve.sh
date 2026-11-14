#!/usr/bin/env bash
# 굽은 길 구간에 들어가서 "리듬 놀이 할까요?"가 나오게 한다 (약 25초).
# 그 뒤 HOLD초 동안 Virtual Console 세션을 유지한다. 그동안 마이크로 직접 "응"이라고 답해 볼 수 있다.
# 에뮬레이터 마이크: Extended Controls(…) → Microphone → "Virtual microphone uses host audio input" 켜기
#
# 실행: bash scripts/emulator/enter-curve.sh [HOLD초, 기본 60]
set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB:-${LOCALAPPDATA:-$HOME}/Android/Sdk/platform-tools/adb}"
BASE="http://127.0.0.1:8088"
WHEEL="${WHEEL:-40}"
HOLD="${1:-60}"

"$ADB" forward tcp:8088 tcp:8088 >/dev/null
( while true; do curl -s -m2 -X POST "$BASE/browser/session?client=mulmi-curve" >/dev/null; sleep 1; done ) &
HEARTBEAT=$!
trap 'curl -s -m2 -X POST "$BASE/browser/session?client=mulmi-curve&closing=1" >/dev/null; kill $HEARTBEAT 2>/dev/null' EXIT
sleep 2

drive() { curl -s -m3 -o /dev/null -X PUT "$BASE/vehicle/controls?speed=60&accelerator=20&brake=0&steering=$1"; echo "핸들 $1°"; }

curl -s -m3 -o /dev/null -X PUT "$BASE/vehicle/gear?gear=Drive"; echo "기어 D, 60km/h"
drive 0; sleep 3
drive -"$WHEEL"; sleep 2; drive 0; sleep 3
drive "$WHEEL";  sleep 2; drive 0; sleep 3
drive -"$WHEEL"; sleep 2; drive 0
echo "굽은 길 구간 시작 → \"리듬 놀이 할까요?\" (마이크로 답해 보세요). ${HOLD}초 유지"
sleep "$HOLD"
