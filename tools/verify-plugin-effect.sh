#!/system/bin/sh
# 重启应用后检查：运行页显示的地址是否等于插件覆盖值。
# 重启是为了避开软键盘（用 UI 输入后键盘挡着底栏，切页不可靠）。

P=com.pumpkin.server
OV=/data/data/$P/shared_prefs/pumpkin_shell.xml

am force-stop $P
sleep 2
am start -n $P/.MainActivity >/dev/null
sleep 9

echo "=== 运行页首屏的地址行 ==="
uiautomator dump /sdcard/r2.xml >/dev/null 2>&1
cat /sdcard/r2.xml | tr '>' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' \
  | grep -E "Java|基岩|未检测" | sed 's/text="//;s/"$//' | sed 's/^/    /'

echo
echo "=== Prefs 里的插件覆盖值 ==="
su -c "grep plugin_ov $OV" | sed 's/^/    /'

echo
echo "=== 对比结论 ==="
HOST=$(su -c "grep -o 'plugin_ov_lan.host.>[^<]*' $OV" | sed 's/.*>//')
if [ -n "$HOST" ]; then
  if grep -q "$HOST" /sdcard/r2.xml; then
    echo "    OK 运行页显示的地址里包含插件覆盖值：$HOST"
  else
    echo "    FAIL 运行页没有体现覆盖值，期望包含：$HOST"
  fi
else
  echo "    （没有覆盖值，运行页应显示自动探测到的地址）"
fi

echo
echo "=== 崩溃检查 ==="
logcat -d -t 200 2>/dev/null | grep "FATAL EXCEPTION" | head -3
echo "（无输出 = 没有崩溃）"