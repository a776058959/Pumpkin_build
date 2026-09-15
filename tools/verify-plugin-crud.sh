#!/system/bin/sh
# 插件页「增删改查」全流程验证。
#
# 一条命令跑完，中间没有人工判断 —— 每步都断言，失败就打出是哪一步。
# 用到的坐标来自 1080x2400 / density 440 的实测 dump（见 tools/dump-ui.sh）。
#
# 依赖：
#   /data/local/tmp/ui.sh      文字 + 中心坐标
#   /data/local/tmp/goto.sh    切页并确认切过去了
#   /data/local/tmp/ch.sh      量运行页控制台文字高度
#   root（Magisk）             读 Prefs / 插件目录做断言

P=com.pumpkin.server
XML=/data/data/$P/shared_prefs/pumpkin_shell.xml
OV=plugin_ov_console.fontScale
FAIL=0

ok()   { echo "    OK   $1"; }
bad()  { echo "    FAIL $1"; FAIL=$((FAIL + 1)); }
has()  { if [ -n "$(echo "$1" | grep -F "$2")" ]; then ok "$3"; else bad "$3（界面文字里没有「$2」）"; fi; }

ui()  { sh /data/local/tmp/ui.sh "$1"; }

# 找按钮的坐标。**先按精确文本匹配**（界面每行打出来是「文本  @ x,y」，
# 所以精确匹配就是「文本  @ 」这个前缀），找不到才退回子串匹配。
# 为什么必须先精确：`ui.sh 刷新` 会同时匹配到状态文字「点「刷新」拉取…」和按钮「刷新」，
# 直接取第一行就会点在状态文字上 —— 表现为「点了没反应」，很容易误判成功能坏了。
tap() {
  L=$(ui "$1" | grep -F "$1  @ " | head -1)
  [ -z "$L" ] && L=$(ui "$1" | head -1)
  echo "$L" | sed -n 's/.*@ \([0-9]*\),\([0-9]*\)/\1 \2/p'
}
prefs() { su -c "grep -o '$1.>[^<]*' $XML" 2>/dev/null | sed 's/.*>//'; }
ovval() { prefs "$OV"; }

# 点某个按钮：先按文字找到坐标，再点。坐标找不到 / 解析不出来就直接算失败，
# 不能把空坐标喂给 input tap（那会抛 IllegalArgumentException 把脚本带偏）。
click() {
  C=$(tap "$1")
  set -- $C
  if [ -z "$1" ] || [ -z "$2" ]; then bad "界面上找不到可点的「$1」"; return 1; fi
  input tap $1 $2
  sleep 4
}

# 等某段文字出现在界面上（最多 $2 秒）。
# 拉索引要走网络，固定 sleep 不够 —— 上一版就因为 dump 早了一秒，
# 把「商店有内容」误判成失败（其实再等一会儿就出来了）。
wait_for() {
  i=0
  while [ $i -lt $2 ]; do
    if ui "" | grep -qF "$1"; then return 0; fi
    sleep 2
    i=$((i + 2))
  done
  return 1
}

echo "==== 0. 清干净，取基线 ===="
su -c "rm -rf /data/data/$P/files/plugins"
su -c "sed -i '/plugin_/d' $XML"
am force-stop $P; sleep 2; am start -n $P/.MainActivity >/dev/null; sleep 8
sh /data/local/tmp/goto.sh 运行 >/dev/null
BASE=$(sh /data/local/tmp/ch.sh 2>/dev/null | sed 's/.*= //;s/ px//')
case "$BASE" in
  ''|NONE) BASE=0; echo "    警告：没量到基线（下面关于高度的断言都会失败）" ;;
  *)       echo "    基线控制台高度 = $BASE px（无插件、无覆盖）" ;;
esac

echo
echo "==== 1. 查：商店列表 ===="
sh /data/local/tmp/goto.sh 插件 >/dev/null
click 刷新 >/dev/null
if wait_for "共 1 个插件" 40; then :; else bad "等了 40 秒商店列表还没出来"; fi
S=$(ui "")
has "$S" "共 1 个插件" "商店拉到了索引"
has "$S" "控制台字号" "列表里有控制台字号"
has "$S" "安装" "有「安装」按钮"
echo
echo "==== 2. 增：安装（不需要 root） ===="
click 安装 >/dev/null
S=$(ui "")
has "$S" "已装好" "安装成功提示"
has "$S" "已加载 1 个" "插件已被加载"
has "$S" "字号倍率" "插件声明的设置项出现了"
# 私有目录只有 root 读得到（App 是 release 包，run-as 用不了），所以探测走 su。
su -c "test -f /data/data/$P/files/plugins/console-font/plugin.dex" \
  && ok "dex 落在 App 私有目录" || bad "没有 dex"
OWNER=$(su -c "stat -c %U /data/data/$P/files/plugins/console-font" 2>/dev/null)
[ "$OWNER" = "u0_a29" ] && ok "目录属主是 App（不是 root）" || bad "目录属主是 $OWNER"

echo
echo "==== 3. 改：写一个设置项，看它生效 ===="
C=$(tap "字号倍率")
set -- $C
input tap $1 $2; sleep 2
input text 2; sleep 1
input keyevent 4      # 收键盘，否则它盖住底栏、切页点不准
sleep 2
V=$(ovval)
[ "$V" = "2" ] && ok "覆盖值写进了 Prefs（$OV=2）" || bad "覆盖值是 [$V]，期望 2"

sh /data/local/tmp/goto.sh 运行 >/dev/null
H=$(sh /data/local/tmp/ch.sh | sed 's/.*= //;s/ px//')
echo "    控制台高度 = $H px（基线 $BASE）"
if [ -n "$H" ] && [ -n "$BASE" ] && [ "$H" -gt "$BASE" ]; then
  ok "字号确实变大了（$BASE → $H）"
else
  bad "字号没变（$BASE → $H）"
fi

echo
echo "==== 4. 停用：停代码，但不动用户已经调好的值 ===="
sh /data/local/tmp/goto.sh 插件 >/dev/null
click 停用 >/dev/null
S=$(ui "")
has "$S" "已停用，跳过" "停用后不再加载它的代码"
has "$S" "启用" "按钮翻成了「启用」"
has "$S" "共加载 0 个插件" "已加载列表变空"
sh /data/local/tmp/goto.sh 运行 >/dev/null
H2=$(sh /data/local/tmp/ch.sh | sed 's/.*= //;s/ px//')
[ "$H2" = "$H" ] && ok "停用后覆盖值仍然生效（$H2 px）" || bad "停用后覆盖值变了（$H → $H2）"

echo
echo "==== 5. 删：卸载要清干净（这是刚修的那个 bug） ===="
sh /data/local/tmp/goto.sh 插件 >/dev/null
click 卸载 >/dev/null
S=$(ui "")
has "$S" "确定卸载" "卸载前有二次确认"
click 确定 >/dev/null
S=$(ui "")
has "$S" "已卸载" "卸载成功提示"

[ -d /data/data/$P/files/plugins/console-font ] && bad "插件目录还在" || ok "插件目录已删除"
V=$(ovval)
[ -z "$V" ] && ok "覆盖值已清掉（卸载后不再有 $OV）" || bad "覆盖值没清：$OV=[$V]"

sh /data/local/tmp/goto.sh 运行 >/dev/null
H3=$(sh /data/local/tmp/ch.sh | sed 's/.*= //;s/ px//')
[ "$H3" = "$BASE" ] && ok "字号回到内置值（$BASE px）" || bad "字号没还原（基线 $BASE，现在 $H3）"

echo
echo "==== 6. 崩溃检查 ===="
N=$(logcat -d -t 400 2>/dev/null | grep -cE "FATAL EXCEPTION|ClassNotFound|NoClassDefFound")
[ "$N" = "0" ] && ok "没有崩溃、没有类加载错误" || { bad "有 $N 条异常"; logcat -d -t 400 | grep -E "FATAL EXCEPTION|ClassNotFound|NoClassDefFound" | head -3; }

echo
if [ "$FAIL" = "0" ]; then echo "==== 全部通过 ===="; else echo "==== 有 $FAIL 项失败 ===="; fi
exit $FAIL
