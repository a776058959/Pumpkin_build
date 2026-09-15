#!/system/bin/sh
# 把插件状态清干净，回到「什么都没装」的样子。跑验证脚本前先跑这个。
#
# 为什么单独做成脚本：这些命令里有 `su -c '...'` 加内部引号，
# 拼在 PC 侧的 `adb shell "..."` 里会被 PowerShell 和手机 sh **双重解释**
#（PowerShell 里 `\"` 不是转义，是字面的反斜杠加引号 —— 这条踩过好几次）。
# 写成文件推上去就没有这个问题。
#
# 用法：sh /data/local/tmp/reset.sh       （要先 push）

P=com.pumpkin.server
XML=/data/data/$P/shared_prefs/pumpkin_shell.xml

echo "1. 停掉 App（SharedPreferences 在内存里，不停就会把改动盖回去）"
am force-stop $P
sleep 2

echo "2. 删插件目录"
su -c "rm -rf /data/data/$P/files/plugins"

echo "3. 删掉所有 plugin_ 开头的偏好"
su -c "sed -i /plugin_/d $XML"

echo "4. 剩下的 plugin_ 键（应该为空）"
su -c "grep -o 'plugin_[a-z._-]*' $XML" 2>/dev/null

echo "5. 重启 App"
am start -n $P/.MainActivity >/dev/null
sleep 8
echo "done"
