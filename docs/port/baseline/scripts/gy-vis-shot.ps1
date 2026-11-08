param([string]$styles="monet", [long]$ms=37500, [string]$dark="true")
$a="D:\tool\platform-tools\adb.exe"
$out="D:\workspaces\GuangYue-u2\docs\port\baseline\screenshots\visualizer"
mkdir -Force $out | Out-Null
foreach ($s in $styles.Split(",")) {
  & $a -s emulator-5554 shell am force-stop com.guangyue.music
  & $a -s emulator-5554 shell am start -n com.guangyue.music/.V0cStageActivity --es v0c_fixture probe --el v0c_ms $ms --es v0c_style $s --ez v0c_dark $dark | Out-Null
  & $a -s emulator-5554 shell wm user-rotation lock 1 | Out-Null
  Start-Sleep 24
  & $a -s emulator-5554 shell screencap -p /sdcard/b0.png
  $t = $ms / 1000
  & $a -s emulator-5554 pull /sdcard/b0.png "$out\gy-$s-t$t-land.png" 2>&1 | Out-Null
  "$s done"
}
