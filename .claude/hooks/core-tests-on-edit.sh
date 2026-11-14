#!/usr/bin/env bash
# PostToolUse 훅: core/ 아래 Kotlin 파일을 수정하면 core 테스트를 돌린다.
# 실패하면 exit 2로 결과를 Claude에게 돌려준다.
input="$(cat)"

# core/src 아래 .kt 파일만 대상 (Windows 경로의 \\ 와 / 모두 처리)
if ! printf '%s' "$input" | grep -qE 'core[\\/]+src[\\/][^"]*\.kt"'; then
  exit 0
fi

root="${CLAUDE_PROJECT_DIR:-.}"
cd "$root" || exit 0

# 아직 Gradle 프로젝트가 없으면 건너뛴다
if [ ! -f ./gradlew ]; then
  exit 0
fi

# JAVA_HOME이 없으면 Android Studio 내장 JDK를 쓴다
if [ -z "$JAVA_HOME" ] && [ -d "/c/Program Files/Android/Android Studio/jbr" ]; then
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
fi

out="$(./gradlew :core:test --console=plain -q 2>&1)"
status=$?
if [ $status -ne 0 ]; then
  {
    echo "core 테스트 실패 (TDD Red 단계라면 정상입니다):"
    printf '%s\n' "$out" | grep -E "FAILED|Test.*>|expected|Expected|actual|Exception|error:|e: " | head -40
  } >&2
  exit 2
fi
exit 0
