#!/usr/bin/env bash
set -euo pipefail
cd /workspace/mapswork
if [[ ! -f tools/bootstrap_android.py || ! -f app/build.gradle.kts ]]; then
    echo 'Mapswork application source is required in /workspace/mapswork.' >&2
    echo 'Restore the completed source ZIP or the environment snapshot before running setup.' >&2
    exit 1
fi
python3 tools/bootstrap_android.py
tools/cloud-gradle.sh :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
