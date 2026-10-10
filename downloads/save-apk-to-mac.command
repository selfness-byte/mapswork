#!/bin/bash
set -euo pipefail
if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "이 저장 도구는 Mac에서 실행하세요."
    exit 1
fi
task_folder="$(osascript -e 'POSIX path of (choose folder with prompt "mapswork-github 폴더를 선택하세요")')"
task_destination="$task_folder/mapswork-0.3.0-test.apk"
task_expected="488fec7dc837b7060b0be8801906814ebc28d5bac0c0b16d60de91fbce09e467"
if [[ -e "$task_destination" ]]; then
    if [[ "$(shasum -a 256 "$task_destination" | awk '{print $1}')" == "$task_expected" ]]; then
        echo "이미 저장되어 있습니다: $task_destination"
        exit 0
    fi
    echo "같은 이름의 다른 파일이 있습니다. 기존 파일을 보존하고 중단합니다."
    exit 1
fi
task_download="$(mktemp /tmp/mapswork-apk.XXXXXX)"
trap 'rm -f "$task_download"' EXIT
curl --fail --silent --show-error --location "https://raw.githubusercontent.com/selfness-byte/mapswork/codex/nearby-shop-alert/downloads/mapswork-0.3.0-test.apk" -o "$task_download"
if [[ "$(shasum -a 256 "$task_download" | awk '{print $1}')" != "$task_expected" ]]; then
    echo "APK 체크섬이 다릅니다. 저장하지 않았습니다."
    exit 1
fi
cp -n "$task_download" "$task_destination"
if [[ "$(shasum -a 256 "$task_destination" | awk '{print $1}')" != "$task_expected" ]]; then
    echo "저장한 파일을 확인하지 못했습니다."
    exit 1
fi
echo "APK 저장 완료: $task_destination"
