#!/system/bin/sh
# 决定性实验：不可见的页面会不会吃掉点击？
#
# 对照组选「配色块」：它在设置页顶部附近，不用滚动就能点到；
# 而运行页的同一坐标是控制台区域（没有可点控件）。
# 于是在运行页上点那个坐标 —— 设置页若被改了，就证明不可见页面在接收触摸。
#
# 判断依据用 Prefs 文件，不用 Toast（Toast 既进不了 uiautomator，也不进 logcat）。

pal() {
  su -c 'grep -o "<string name=\"palette\">[^<]*" /data/data/com.pumpkin.server/shared_prefs/*.xml'
}

# 脚本假定应用在前台。装完包之后应用是被杀掉的，不启动就会把通知栏拉下来。
# （踩过一次：swipe 变成下拉通知栏，dump 出来全是系统通知。）
input keyevent KEYCODE_BACK
am force-stop com.pumpkin.server
sleep 1
am start -n com.pumpkin.server/.MainActivity >/dev/null
sleep 8

echo "=== 1. 对照：当前 palette ==="
pal

echo
echo "=== 2. 到设置页，先滚回顶部，再取「松林绿」色块坐标 ==="
input tap 849 2255
sleep 3
# 页面改成常驻组合之后，切页不再销毁页面，滚动位置会保留下来 —— 所以先滚回顶部。
i=0
while [ $i -lt 5 ]; do
  input swipe 540 500 540 2000 200
  sleep 0.3
  i=$((i + 1))
done
sleep 1
uiautomator dump /sdcard/m.xml >/dev/null 2>&1
C=$(cat /sdcard/m.xml | tr '>' '\n' | grep 'text="松林绿"' | head -1 \
    | sed 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/')
set -- $C
if [ -z "$1" ]; then
  echo "  没找到「松林绿」，当前文字："
  cat /sdcard/m.xml | tr '>' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' | head -20
  exit 1
fi
CX=$(( ($1 + $3) / 2 ))
CY=$(( ($2 + $4) / 2 ))
echo "  「松林绿」色块中心 = ($CX,$CY)"

echo
echo "=== 3. 切回「运行」页，在同一坐标点一下 ==="
input tap 231 2255
sleep 3
uiautomator dump /sdcard/r.xml >/dev/null 2>&1
echo "  当前页首项 = $(cat /sdcard/r.xml | tr '>' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' | head -1)"
input tap $CX $CY
sleep 3

echo
echo "=== 4. 再看 palette ==="
pal
echo
echo "（若已变成 green → 不可见页面确实在吃点击）"
