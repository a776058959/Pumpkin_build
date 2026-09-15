#!/system/bin/sh
# 清掉插件覆盖值，恢复显示自动探测到的真实地址。
# 必须在应用停止时改（否则应用退出时会把内存里的旧值写回去）。

P=com.pumpkin.server
OV=/data/data/$P/shared_prefs/pumpkin_shell.xml

am force-stop $P
sleep 2

sed -i 's|<string name="plugin_ov_lan.host">[^<]*</string>|<string name="plugin_ov_lan.host"></string>|' $OV

echo "改完之后的覆盖值："
grep plugin_ov $OV | sed 's/^/    /'

am start -n $P/.MainActivity >/dev/null
sleep 9
echo
echo "运行页地址行："
uiautomator dump /sdcard/cl.xml >/dev/null 2>&1
cat /sdcard/cl.xml | tr '>' '\n' | grep -o 'text="[^"]*"' | grep -E "Java" | sed 's/text="//;s/"$//' | sed 's/^/    /'
