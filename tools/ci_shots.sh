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
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml shots/ui-landscape.xml >/dev/null
  b2=$(grep -o 'content-desc="切换竖屏"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' shots/ui-landscape.xml | grep -o 'bounds=.*' | grep -o '[0-9]\+' | tr '\n' ' ')
  echo "portrait key bounds: $b2" | tee -a shots/rotate.txt
  if [ -n "$b2" ]; then
    set -- $b2; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 6
    adb shell dumpsys window displays | grep -i "mCurrentRotation\|cur=" | head -3 | tee -a shots/rotate.txt
    shot "rotate-back-portrait"
  fi
fi
adb logcat -d -s V0cStage FumeBulletinStage AndroidRuntime > shots/logcat.txt 2>&1 || true
ls -la shots
