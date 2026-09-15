#!/system/bin/sh
# 验证「控制台字号」插件的覆盖值真的生效。
#
# 量的是运行页控制台那个 Text 节点的**高度**。
# 没日志时它显示两行占位文字（「（还没有日志）\n启动服务器后这里会实时输出」），
# 两行的行高只由字号决定，所以：
#     倍率 1.0 → H        倍率 2.0 → ≈ 2H
# 这是个只跟字号有关的量，不用去猜别的布局变化。
#
# 覆盖值直接用 root 写进 Prefs（走的是同一条读取路径：
# MainActivity.render() 读 PluginKeys.CONSOLE_FONT_SCALE → state.consoleFontScale）。
# 界面上的写入路径（插件页的输入框）另有人工验证步骤，见文末。

P=com.pumpkin.server
XML=/data/data/$P/shared_prefs/pumpkin_shell.xml
KEY=plugin_ov_console.fontScale

console_height() {
  uiautomator dump /sdcard/f.xml >/dev/null 2>&1
  LINE=$(cat /sdcard/f.xml | tr '>' '\n' | grep '还没有日志' | head -1)
  B=$(echo "$LINE" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
  Y1=$(echo "$B" | sed 's/.*bounds="\[[0-9]*,\([0-9]*\)\].*/\1/')
  Y2=$(echo "$B" | sed 's/.*\]\[[0-9]*,\([0-9]*\)\].*/\1/')
  echo $((Y2 - Y1))
}

measure() {
  am force-stop $P
  sleep 2
  am start -n $P/.MainActivity >/dev/null
  sleep 9
  # 启动后停在运行页，直接量
  console_height
}

set_scale() {
  # 必须先停掉 App 再改文件：SharedPreferences 是内存里的，
  # App 一退就会把内存里那份写回文件，把这里的修改盖掉。
  am force-stop $P
  sleep 2
  if [ -z "$1" ]; then
    su -c "sed -i '/$KEY/d' $XML"
  else
    if su -c "grep -q '$KEY' $XML"; then
      su -c "sed -i 's|<string name=\"$KEY\">[^<]*</string>|<string name=\"$KEY\">$1</string>|' $XML"
    else
      su -c "sed -i 's|</map>|<string name=\"$KEY\">$1</string></map>|' $XML"
    fi
  fi
  su -c "grep -o '$KEY.>[^<]*' $XML" 2>/dev/null | sed 's/^/    现在 Prefs 里：/'
}

echo "=== 1. 基线：没有覆盖值（内置 10.5sp） ==="
set_scale ""
H0=$(measure)
echo "    控制台文字高度 = $H0 px"

echo
echo "=== 2. 覆盖成 2 倍 ==="
set_scale 2
H1=$(measure)
echo "    控制台文字高度 = $H1 px"

echo
echo "=== 3. 结论 ==="
if [ "$H0" -gt 0 ] && [ "$H1" -gt "$H0" ]; then
  R=$(( H1 * 10 / H0 ))
  echo "    H1/H0 = $R/10（期望 ≈ 20/10，也就是 2 倍）"
  if [ "$R" -ge 18 ] && [ "$R" -le 22 ]; then
    echo "    OK 插件覆盖值真的改变了控制台字号"
  else
    echo "    FAIL 高度变了但不是 2 倍，检查夹回逻辑（0.5~3，越界会夹回 1）"
  fi
else
  echo "    FAIL 没量到高度差（H0=$H0 H1=$H1）"
fi

echo
echo "=== 4. 还原 ==="
set_scale ""

echo
echo "=== 5. 崩溃检查 ==="
logcat -d -t 300 2>/dev/null | grep "FATAL EXCEPTION" | head -3
echo "（无输出 = 没有崩溃）"

# ---------------------------------------------------------------------------
# 界面写入路径（人工/交互式验证，不在上面自动化里）：
#   插件页 → 「插件设置」卡片 → 「字号倍率」输入框 → 输入 2 → 按返回收键盘
#   → 回运行页，控制台字号应当立刻变大（onPluginSettingChangedFromUi 会 refresh）。
# 之所以不写进脚本：输入文本要弹软键盘，键盘会盖住底栏，切页坐标就不稳了。
# ---------------------------------------------------------------------------
