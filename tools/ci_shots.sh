#!/usr/bin/env bash
# CI 模拟器取证：360dp（720x1600 @320dpi）下浮名取景 + 横屏键。产物写到 shots/。
set -u
mkdir -p shots
adb shell wm size 720x1600
adb shell wm density 320
adb shell settings put system accelerometer_rotation 0
adb install -r app/build/outputs/apk/debug/*.apk
A=com.guangyue.music/.V0cStageActivity
# 模拟器刚起来时系统很忙，Pixel Launcher 常报 ANR 弹窗盖住截图：后台 ANR 不弹窗，先等系统稳一会儿
adb shell settings put global anr_show_background 0 || true
sleep 20
shot() { adb exec-out screencap -p > "shots/$1.png"; echo "shot $1"; }

# 只拍一个样式（workflow_dispatch 的 scene 输入；R4 商籁起）：拍完把截图 base64 打进日志后直接退出。
emit_and_exit() {
  adb logcat -d -s AndroidRuntime SonnetStage RuntimeShader ActivityManager:E ActivityTaskManager:W > shots/logcat.txt 2>&1 || true
  echo "===LOGCAT==="; tail -c 4000 shots/logcat.txt
  # ANR 主线程栈（google_apis 镜像可 adb root）
  adb root >/dev/null 2>&1; sleep 3
  echo "===ANR==="
  adb shell 'for f in /data/anr/anr_*; do echo "== $f"; grep -A60 "^\"main\"" "$f" | head -70; done' 2>&1 | head -c 20000
  echo "===ANREND==="
  for f in shots/*.png; do
    n=$(basename "$f" .png)
    if command -v convert >/dev/null; then convert "$f" -resize 50% -quality 60 "/tmp/$n.jpg"; else cp "$f" "/tmp/$n.jpg"; fi
    echo "===IMG $n==="; base64 -w0 "/tmp/$n.jpg"; echo; echo "===END $n==="
  done
  exit 0
}
if [ "${SCENE:-}" = "sonnet" ]; then
  son() { # $1 = 名字, $2 = 毫秒；前台不是取证页（刚开机系统忙、被 ANR 关掉）就重拍，最多 3 次
    for try in 1 2 3; do
      adb shell am force-stop com.guangyue.music
      adb shell am start -W -n $A --es v0c_style sonnet --es v0c_fixture probe --el v0c_ms $2 --ez v0c_dark true >/dev/null
      sleep 25
      top=$(adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity")
      echo "son $1 try $try top: $top"
      if echo "$top" | grep -q V0cStageActivity; then break; fi
      adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
      sleep 10
    done
    shot "$1"
  }
  adb shell am start -W -n $A --es v0c_style sonnet --es v0c_fixture probe --el v0c_ms 37500 --ez v0c_dark true >/dev/null; sleep 60
  son sonnet-port-37 37500
  son sonnet-port-44 44500
  adb shell settings put system user_rotation 1
  adb shell wm user-rotation lock 1 || adb shell cmd window user-rotation lock 1 || true
  sleep 3
  son sonnet-land-37 37500
  son sonnet-land-39 39500
  son sonnet-land-44 44500
  son sonnet-land-14 14000
  son sonnet-land-33 33000
  son sonnet-land-3 3000
  adb shell settings put system user_rotation 0
  adb shell wm user-rotation lock 0 || adb shell cmd window user-rotation lock 0 || true
  emit_and_exit
fi

# 雾笺（B0 残差）：与 Folia 基准同一份探针歌词（v0c_fixture probe），静态时钟定格；竖屏、横屏各拍几刻（37.5s 是基准时刻）。
neb() { # $1 = 名字, $2 = 毫秒（等 45 s：模拟器上城市图与排版抢 CPU，排版最慢到 30 s）
  adb shell am force-stop com.guangyue.music
  adb shell am start -W -n $A --es v0c_style nebbia --es v0c_fixture probe --el v0c_ms $2 --ez v0c_dark true >/dev/null
  sleep 45
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  sleep 1
  shot "$1"
}
# 预热一次（冷启动时排版要多算一会儿），不留图
adb shell am start -W -n $A --es v0c_style nebbia --es v0c_fixture probe --el v0c_ms 37500 --ez v0c_dark true >/dev/null; sleep 30
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
