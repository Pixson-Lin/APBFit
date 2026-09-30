#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="/agent/repos/APBFit"
if [ ! -f "$REPO_ROOT/settings.gradle.kts" ]; then
  REPO_ROOT="/workspace"
fi
cd "$REPO_ROOT"

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/home/ubuntu/.gradle}"

printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

if [ ! -x gradlew ]; then
  gradle wrapper --gradle-version 8.9
  chmod +x gradlew
fi

./gradlew --no-daemon dependencies
