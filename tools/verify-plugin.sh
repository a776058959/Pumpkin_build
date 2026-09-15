#!/system/bin/sh
# 验证插件系统端到端可用。
#
# 流程：装插件文件 → 重启 App → 看「设置 → 插件」有没有认出来 → 填一个覆盖地址
#       → 去运行页看显示的联机地址有没有被改掉。
#
# 注意：插件目录是 App 的私有目录，文件由 root 推进去（644，App 可读）。
# App 是 release 包（非 debuggable），所以 run-as 用不了，只能走 root。

P=com.pumpkin.server
D=/data/data/$P/files/plugins/lan-address

dump() {
  uiautomator dump /sdcard/pg.xml >/dev/null 2>&1
  cat /sdcard/pg.xml | tr '>' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' | sed 's/text="//;s/"$//'
}

echo "=== 1. 放插件文件 ==="
mkdir -p $D
cp /data/local/tmp/plugin.dex  $D/plugin.dex
cp /data/local/tmp/plugin.json $D/plugin.json
chmod 644 $D/plugin.dex $D/plugin.json
ls -la $D

echo
echo "=== 2. 重启 App ==="
am force-stop $P
sleep 1
am start -n $P/.MainActivity >/dev/null
sleep 9

echo "=== 3. 进设置页并滚到「插件」卡片 ==="
input tap 849 2255
sleep 3
i=0
while [ $i -lt 5 ]; do
  input swipe 540 2000 540 500 300
  sleep 0.6
  i=$((i + 1))
done
sleep 1
echo "--- 屏幕上的文字 ---"
dump | sed 's/^/    /'

echo
echo "=== 4. 崩溃检查 ==="
logcat -d -t 300 2>/dev/null | grep -E "FATAL EXCEPTION|ClassNotFound|NoClassDefFound|DexClassLoader" | head -5
echo "（上面没有输出 = 没崩、也没有类加载错误）"
