#!/system/bin/sh
# 把某个插件「记录的版本」改掉，用来模拟「装了旧版」这个状态。
# 只为验证商店里「更新到 x.x」这条分支 —— 不然得先装一个旧 dex 才看得到。
# 用法：sh /data/local/tmp/fake-ver.sh <插件id> <版本>
P=com.pumpkin.server
XML=/data/data/$P/shared_prefs/pumpkin_shell.xml
ID="$1"
VER="$2"
[ -z "$ID" ] && { echo "用法：fake-ver.sh <插件id> <版本>"; exit 2; }

am force-stop $P
sleep 2
su -c "sed -i 's|<string name=\"plugin_ver_$ID\">[^<]*</string>|<string name=\"plugin_ver_$ID\">$VER</string>|' $XML"
echo "改后：$(su -c "grep -o 'plugin_ver_$ID.>[^<]*' $XML")"
am start -n $P/.MainActivity >/dev/null
sleep 8
echo done
