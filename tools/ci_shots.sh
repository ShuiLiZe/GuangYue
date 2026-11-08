#!/usr/bin/env bash
# CI 模拟器取证：360dp（720x1600 @320dpi）下浮名取景 + 横屏键。产物写到 shots/。
set -u
mkdir -p shots
adb shell wm size 720x1600
adb shell wm density 320
adb shell settings put system accelerometer_rotation 0
adb install -r app/build/outputs/apk/debug/*.apk
A=com.guangyue.music/.V0cStageActivity
shot() { adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }

# 雾笺（B0 残差）：与 Folia 基准同一份探针歌词（v0c_fixture probe），静态时钟定格；竖屏、横屏各拍几刻（37.5s 是基准时刻）。
neb() { # $1 = 名字, $2 = 毫秒
  adb shell am force-stop com.guangyue.music
  adb shell am start -W -n $A --es v0c_style nebbia --es v0c_fixture probe --el v0c_ms $2 --ez v0c_dark true >/dev/null
  sleep 14
  shot "$1"
}
neb nebbia-warm 37500
neb nebbia-port-37 37500
neb nebbia-port-44 44000
adb shell settings put system user_rotation 1
adb shell wm user-rotation lock 1 || adb shell cmd window user-rotation lock 1 || true
sleep 3
adb shell dumpsys window displays | grep -i "mCurrentRotation" | head -2
neb nebbia-land-37 37500
neb nebbia-land-41 41000
neb nebbia-land-44 44000
adb shell settings put system user_rotation 0
adb shell wm user-rotation lock 0 || adb shell cmd window user-rotation lock 0 || true
sleep 3
adb logcat -d -s AndroidRuntime NebbiaStage > shots/neb-crash.txt 2>&1 || true
echo "===NEBLOG==="; tail -c 3000 shots/neb-crash.txt

# 手动触发（workflow_dispatch，wip 分支迭代雾笺）时只拍雾笺，省时间
if [ "${GITHUB_EVENT_NAME:-}" != "workflow_dispatch" ]; then
for t in 2 6 14 26 40; do
  adb shell am force-stop com.guangyue.music
  adb shell am start -W -n $A --es v0c_style fume --ei v0c_fumesteps 40 --ef v0c_fumetime $t --el v0c_ms $((t*1000)) --ez v0c_dark true >/dev/null
  sleep 8
  shot "fume-t$t"
done

# 横屏键：系统自动旋转已关（accelerometer_rotation=0），点「切换横屏」应转成横屏。
adb shell am force-stop com.guangyue.music
adb shell am start -W -n $A --es v0c_style fume --ei v0c_fumesteps 40 --ef v0c_fumetime 14 --el v0c_ms 14000 >/dev/null
sleep 8
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml shots/ui-portrait.xml >/dev/null
b=$(grep -o 'content-desc="切换横屏"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' shots/ui-portrait.xml | grep -o 'bounds=.*' | grep -o '[0-9]\+' | tr '\n' ' ')
echo "rotate key bounds: $b" | tee shots/rotate.txt
if [ -n "$b" ]; then
  set -- $b; x=$(( ($1+$3)/2 )); y=$(( ($2+$4)/2 ))
  adb shell input tap $x $y
  sleep 6
  adb shell dumpsys window displays | grep -i "mCurrentRotation\|cur=" | head -3 | tee -a shots/rotate.txt
  shot "rotate-landscape"
  adb shell rm -f /sdcard/ui.xml; sleep 2; adb shell uiautomator dump /sdcard/ui.xml 2>&1 | tee -a shots/rotate.txt; adb pull /sdcard/ui.xml shots/ui-landscape.xml >/dev/null
  b2=$(grep -o 'content-desc="切换竖屏"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' shots/ui-landscape.xml | grep -o 'bounds=.*' | grep -o '[0-9]\+' | tr '\n' ' ')
  echo "portrait key bounds: $b2" | tee -a shots/rotate.txt
  if [ -n "$b2" ]; then
    set -- $b2; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 6
    adb shell dumpsys window displays | grep -i "mCurrentRotation\|cur=" | head -3 | tee -a shots/rotate.txt
    shot "rotate-back-portrait"
  fi
fi
fi
adb logcat -d -s V0cStage FumeBulletinStage AndroidRuntime NebbiaStage RuntimeShader skia > shots/logcat.txt 2>&1 || true
ls -la shots
# 产物配额满时也能取证：把缩小后的截图以 base64 打进日志。
echo "===ROTATE==="; cat shots/rotate.txt
echo "===LOGCAT==="; tail -c 4000 shots/logcat.txt
for f in shots/*.png; do
  n=$(basename "$f" .png)
  if command -v convert >/dev/null; then convert "$f" -resize 50% -quality 55 "/tmp/$n.jpg"; else cp "$f" "/tmp/$n.jpg"; fi
  echo "===IMG $n==="; base64 -w0 "/tmp/$n.jpg"; echo; echo "===END $n==="
done
