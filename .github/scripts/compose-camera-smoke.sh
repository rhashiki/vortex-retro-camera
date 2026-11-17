#!/usr/bin/env bash
set -Eeuo pipefail

on_exit() {
  code=$?
  if [ "$code" -ne 0 ]; then
    echo "::group::Android startup and instrumentation failure logs"
    adb logcat -d -v time | grep -A 70 -B 12 -E 'FATAL EXCEPTION|AndroidRuntime|Process: com.vortex.retrocamera|IllegalStateException' || true
    echo "::endgroup::"
  fi
}
trap on_exit EXIT

gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.vortex.retrocamera android.permission.CAMERA
adb logcat -c
adb shell am force-stop com.vortex.retrocamera
adb shell am start -W -n com.vortex.retrocamera/.MainActivity
sleep 6
PID="$(adb shell pidof com.vortex.retrocamera | tr -d '\r')"
if [ -z "$PID" ]; then
  echo "The app did not remain running after launch."
  exit 1
fi
echo "Vortex Camera running as PID $PID"
adb shell screencap -p /sdcard/vortex-premium-collapsed.png
adb pull /sdcard/vortex-premium-collapsed.png vortex-premium-collapsed.png
adb shell uiautomator dump /sdcard/vortex-window.xml
adb pull /sdcard/vortex-window.xml vortex-window.xml

python3 - <<'PY'
import re
import subprocess
import xml.etree.ElementTree as ET
root = ET.parse("vortex-window.xml").getroot()
node = next((n for n in root.iter("node") if "Abrir filtros retrô" in n.get("content-desc", "")), None)
if node is None:
    raise AssertionError("Botão de filtros Retrô não encontrado na árvore de acessibilidade")
bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
if len(bounds) != 4:
    raise AssertionError("Dimensões de controle inválidas")
subprocess.run(["adb", "shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2)], check=True)
PY
sleep 2
adb shell screencap -p /sdcard/vortex-premium-expanded.png
adb pull /sdcard/vortex-premium-expanded.png vortex-premium-expanded.png
gradle --no-daemon :app:connectedDebugAndroidTest
