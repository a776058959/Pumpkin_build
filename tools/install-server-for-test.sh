#!/system/bin/sh
# 把 /data/local/tmp 下已推好的服务端二进制装成 App 的一个「已安装版本」。
#
# 为什么走这条路而不是点 UI 下载：只是为了让验证脚本能真的**启动**服务端
#（「停止前确认」插件要服务端在跑才有意义）。
# 走 UI 要 119MB 重下一遍、还要过版本选择器，没必要。
#
# 注意：这是**测试用的旁路**。真实用户走的是 App 的下载流程
#（校验 ELF、断点续传、多源回退），这里都不经过。
#
# 用法：
#   adb push <二进制> /data/local/tmp/pumpkin-server
#   sh /data/local/tmp/install-server.sh <tag>

P=com.pumpkin.server
XML=/data/data/$P/shared_prefs/pumpkin_shell.xml
TAG="$1"
[ -z "$TAG" ] && TAG=Custom-20260915-0450
D=/data/data/$P/files/versions/$TAG

echo "1. 停掉 App（改 Prefs 前必须停，否则内存里那份会把改动写回去）"
am force-stop $P
sleep 2

echo "2. 放二进制到 $D/pumpkin"
su -c "mkdir -p $D"
su -c "cp /data/local/tmp/pumpkin-server $D/pumpkin"

echo "3. 属主给 App 自己 + 可执行（插件目录那次的教训：属主不对会静默失败）"
su -c "chown -R u0_a29:u0_a29 /data/data/$P/files/versions"
su -c "chmod 700 $D/pumpkin"
su -c "ls -la $D"

echo "4. 记成当前版本（current_tag）"
su -c "sed -i /current_tag/d $XML"
su -c "sed -i 's|</map>|<string name=\"current_tag\">$TAG</string></map>|' $XML"
su -c "grep -o 'current_tag.>[^<]*' $XML"

echo "5. 重启 App"
am start -n $P/.MainActivity >/dev/null
sleep 8
echo "done"
