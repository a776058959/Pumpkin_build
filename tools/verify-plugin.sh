#!/system/bin/sh
# 验证「插件」页整页可用。
#
# 流程：重启 App → 点底栏第 3 项（插件）→ 把屏幕文字 dump 出来 → 检查
#       商店 / 已安装 / 插件设置 三块是否都渲染了。
#
# 底栏现在是 4 项（运行 / 更新 / 插件 / 设置），1080 宽：
#   中心 x = 135 / 405 / 675 / 945，y ≈ 2255（1080x2400、density 440 实测）。
#
# 注意：App 是 release 包（非 debuggable），run-as 用不了；
# 要往插件目录里看/放文件只能走 root（本机有 Magisk）。

P=com.pumpkin.server
DIR=/data/data/$P/files/plugins

dump() {
  uiautomator dump /sdcard/pg.xml >/dev/null 2>&1
  cat /sdcard/pg.xml | tr '>' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' | sed 's/text="//;s/"$//'
}

echo "=== 1. 重启 App ==="
am force-stop $P
sleep 1
am start -n $P/.MainActivity >/dev/null
sleep 9

echo
echo "=== 2. 点底栏「插件」（x=675） ==="
input tap 675 2255
sleep 3
dump | sed 's/^/    /'

echo
echo "=== 3. 插件目录 ==="
su -c "ls -la $DIR" 2>/dev/null | sed 's/^/    /'

echo
echo "=== 4. 回到运行页，确认第 3 项没抢到第 0 项的点击 ==="
input tap 135 2255
sleep 2
dump | grep -E "启动|停止|控制台" | sed 's/^/    /'

echo
echo "=== 5. 崩溃检查 ==="
logcat -d -t 400 2>/dev/null | grep -E "FATAL EXCEPTION|ClassNotFound|NoClassDefFound|DexClassLoader" | head -5
echo "（上面没有输出 = 没崩、也没有类加载错误）"
