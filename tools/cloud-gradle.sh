#!/usr/bin/env bash
set -euo pipefail
task_tools="${MAPSWORK_TOOLS:-/workspace/.mapswork-tools}"
export JAVA_HOME="$task_tools/jdk-21"
export ANDROID_HOME="$task_tools/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$task_tools/android-user"
export GRADLE_USER_HOME="${MAPSWORK_GRADLE_CACHE:-/workspace/.mapswork-gradle}"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS="$(python3 - <<'PY'
import sys
sys.path.insert(0, 'tools')
from bootstrap_android import java_options
print(java_options())
PY
)"
if [[ -f ./gradlew ]]; then
    exec bash ./gradlew --console=plain "$@"
fi
exec "$task_tools/gradle-8.11.1/bin/gradle" --console=plain "$@"
