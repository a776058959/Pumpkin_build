#!/system/bin/sh
# 插件页「增删改查」全流程验证。
#
# 一条命令跑完，中间没有人工判断 —— 每步都断言，失败就打出是哪一步。
# 覆盖两个示例插件，正好是插件的两种形态：
#   console-font 改**显示**（TEXT 设置项）
#   confirm-stop 改**行为**（TOGGLE 设置项，会真的改变 App 点「停止」时的行为）
#
# 前置：先跑一次 reset.sh 把状态清干净（本脚本第 0 步会自己跑）。
# 依赖：/data/local/tmp/ 下有 ui.sh / goto.sh / ch.sh / reset.sh
#       root（Magisk）—— 读 Prefs 与私有目录做断言

P=com.pumpkin.server
XML=/data/data/$P/shared_prefs/pumpkin_shell.xml
FONT=plugin_ov_console.fontScale
STOP=plugin_ov_run.confirmStop
FAIL=0

ok()  { echo "    OK   $1"; }
bad() { echo "    FAIL $1"; FAIL=$((FAIL + 1)); }

# 断言界面文字里含有某个片段。
has()    { if ui "" | grep -qF "$2"; then ok "$1"; else bad "$1（界面里没有「$2」）"; fi; }
hasnot() { if ui "" | grep -qF "$2"; then bad "$1（界面里出现了不该有的「$2」）"; else ok "$1"; fi; }

ui()  { sh /data/local/tmp/ui.sh ""; }

# 找按钮坐标。**先精确匹配**（每行格式是「文本  @ x,y」，精确匹配就是「文本  @ 」前缀），
# 找不到才退回子串。必须先精确：ui.sh 是按子串过滤的，
# 比如「刷新」会同时命中状态文字「点「刷新」拉取…」和按钮本身，取第一行就会点错地方。
tap() {
  L=$(sh /data/local/tmp/ui.sh "$1" | grep -F "$1  @ " | head -1)
  [ -z "$L" ] && L=$(sh /data/local/tmp/ui.sh "$1" | head -1)
  echo "$L" | sed -n 's/.*@ \([0-9]*\),\([0-9]*\)/\1 \2/p'
}

click() {
  set -- $(tap "$1")
  if [ -z "$1" ] || [ -z "$2" ]; then bad "界面上找不到可点的「$1」"; return 1; fi
  input tap $1 $2
  sleep 4
}

# 点「某个插件那一行的」按钮：商店里插件一多，「安装」会同时出现好几个，
# 按文字找只会拿到第一个 —— 装错插件，后面断言全崩（而且看起来像功能坏了）。
# 所以先定位锚点（插件名）那一行，再取它之后第一个匹配的按钮。
click_near() {
  ANCHOR="$1"; LABEL="$2"
  set -- $(ui | awk -v a="$ANCHOR" -v b="$LABEL  @ " '
    index($0, a) { found = 1 }
    found && index($0, b) { print; exit }' \
    | sed -n 's/.*@ \([0-9]*\),\([0-9]*\)/\1 \2/p')
  if [ -z "$1" ] || [ -z "$2" ]; then bad "「$ANCHOR」那一行下面找不到「$LABEL」"; return 1; fi
  input tap $1 $2
  sleep 4
}

# 服务端在跑吗？认「运行中」而不是「未运行」：
# 运行页的状态文字有三种 ——「未运行」/「运行中 0:54」/「已停止（退出码 0）」，
# 找「未运行」的话，**停完之后也会被判定成"还在跑"**（踩过）。
server_running() {
  ui | grep -qF "运行中"
}

# 等某段文字出现（最多 $2 秒）。拉索引要走网络，固定 sleep 不够。
wait_for() {
  i=0
  while [ $i -lt $2 ]; do
    if ui | grep -qF "$1"; then return 0; fi
    sleep 2
    i=$((i + 2))
  done
  return 1
}

prefs() { su -c "grep -o '$1.>[^<]*' $XML" 2>/dev/null | sed 's/.*>//'; }

echo "==== 0. 清干净，取基线 ===="
sh /data/local/tmp/reset.sh >/dev/null 2>&1
sh /data/local/tmp/goto.sh 运行 >/dev/null
BASE=$(sh /data/local/tmp/ch.sh 2>/dev/null | sed 's/.*= //;s/ px//')
case "$BASE" in
  ''|NONE) BASE=0; echo "    警告：没量到基线（关于高度的断言都会失败）" ;;
  *)       echo "    基线控制台高度 = $BASE px（无插件、无覆盖）" ;;
esac
echo "    基线：点「停止」应当先弹确认"
sh /data/local/tmp/goto.sh 运行 >/dev/null
if server_running; then
  click 停止 >/dev/null
  has "默认会弹停止确认" "请确认"
  click 取消 >/dev/null
else
  # 「停止」在服务端没跑时是禁用的，点不出对话框 —— 这一步只能等有服务端时再验。
  echo "    （服务端未运行，跳过默认确认的验证）"
fi

echo
echo "==== 1. 页面分成两段，默认落在「已安装」 ===="
sh /data/local/tmp/goto.sh 插件 >/dev/null
S=$(ui)
has "有「已安装」段"   "已安装"
has "有「商店」段"     "商店"
has "空态给出了下一步" "去「商店」看看"
has "空态有「去商店」按钮" "去商店"
has "诊断区在（插件目录）" "插件目录"

echo
echo "==== 2. 查：商店列表 ===="
click 商店 >/dev/null
if wait_for "控制台字号" 40; then :; else bad "等了 40 秒商店列表还没出来"; fi
has "商店拉到了索引"   "个插件"
has "列表里有控制台字号" "控制台字号"
has "列表里有停止前确认" "停止前确认"

echo
echo "==== 3. 增：安装 console-font（不需要 root） ===="
click_near 控制台字号 安装 >/dev/null
# 「已装好」这句状态文字在**商店**段里，切到「已安装」就看不到了 —— 先断言再切。
has "安装成功提示" "已装好"
click 已安装 >/dev/null
has "插件已被加载"       "已加载 1 个"
has "设置项出现在「已安装」段" "字号倍率"
su -c "test -f /data/data/$P/files/plugins/console-font/plugin.dex" \
  && ok "dex 落在 App 私有目录" || bad "没有 dex"
OWNER=$(su -c "stat -c %U /data/data/$P/files/plugins/console-font" 2>/dev/null)
[ "$OWNER" = "u0_a29" ] && ok "目录属主是 App（不是 root）" || bad "目录属主是 $OWNER"

echo
echo "==== 4. 改：写一个 TEXT 设置项，看它生效 ===="
set -- $(tap "字号倍率")
if [ -z "$1" ]; then bad "找不到字号倍率输入框"; else
  input tap $1 $2; sleep 2
  input text 2; sleep 1
  input keyevent 4      # 收键盘，否则它盖住底栏、切页点不准
  sleep 2
fi
V=$(prefs "$FONT")
[ "$V" = "2" ] && ok "覆盖值写进了 Prefs（$FONT=2）" || bad "覆盖值是 [$V]，期望 2"
sh /data/local/tmp/goto.sh 运行 >/dev/null
H=$(sh /data/local/tmp/ch.sh 2>/dev/null | sed 's/.*= //;s/ px//')
echo "    控制台高度 = $H px（基线 $BASE）"
if [ -n "$H" ] && [ "$H" != "NONE" ] && [ "$H" -gt "$BASE" ]; then
  ok "字号确实变大了（$BASE → $H）"
else
  bad "字号没变（$BASE → $H）"
fi

echo
echo "==== 5. 停用：停代码，但不动用户已经调好的值 ===="
sh /data/local/tmp/goto.sh 插件 >/dev/null
click 停用 >/dev/null
has "停用后不再加载它的代码" "已停用，跳过"
has "按钮翻成了「启用」"     "启用"
has "已加载列表变空"         "共加载 0 个插件"
hasnot "停用后设置项消失了"   "字号倍率"
sh /data/local/tmp/goto.sh 运行 >/dev/null
H2=$(sh /data/local/tmp/ch.sh 2>/dev/null | sed 's/.*= //;s/ px//')
[ "$H2" = "$H" ] && ok "停用后覆盖值仍然生效（$H2 px）" || bad "停用后覆盖值变了（$H → $H2）"

echo
echo "==== 6. 删：卸载要连覆盖一起清干净 ===="
sh /data/local/tmp/goto.sh 插件 >/dev/null
click 卸载 >/dev/null
has "卸载前有二次确认" "确定卸载"
click 确定 >/dev/null
# 同理：「已卸载」在商店段。这里断言「已安装」段自己的结果。
has "卸载后回到空态" "去「商店」看看"
su -c "test -d /data/data/$P/files/plugins/console-font" \
  && bad "插件目录还在" || ok "插件目录已删除"
V=$(prefs "$FONT")
[ -z "$V" ] && ok "覆盖值已清掉（不再有 $FONT）" || bad "覆盖值没清：$FONT=[$V]"
sh /data/local/tmp/goto.sh 运行 >/dev/null
H3=$(sh /data/local/tmp/ch.sh 2>/dev/null | sed 's/.*= //;s/ px//')
[ "$H3" = "$BASE" ] && ok "字号回到内置值（$BASE px）" || bad "字号没还原（基线 $BASE，现在 $H3）"

echo
echo "==== 7. 第二种形态：TOGGLE 插件真的改变了 App 的行为 ===="
sh /data/local/tmp/goto.sh 插件 >/dev/null
click 商店 >/dev/null
if wait_for "停止前确认" 20; then :; else bad "商店里没有停止前确认"; fi
click_near 停止前确认 安装 >/dev/null
click 已安装 >/dev/null
has "TOGGLE 设置项渲染成了按钮" "停止前先确认：开"

click "停止前先确认" >/dev/null
has "点一下翻成「关」" "停止前先确认：关"
V=$(prefs "$STOP")
[ "$V" = "false" ] && ok "覆盖值写进 Prefs（$STOP=false）" || bad "覆盖值是 [$V]，期望 false"

sh /data/local/tmp/goto.sh 运行 >/dev/null
if server_running; then
  click 停止 >/dev/null
  hasnot "关掉确认后，点「停止」不再弹确认" "请确认"
else
  echo "    （服务端未运行，跳过行为验证 —— 覆盖值本身已经验过了）"
fi

echo
echo "==== 8. 崩溃检查 ===="
N=$(logcat -d -t 400 2>/dev/null | grep -cE "FATAL EXCEPTION|ClassNotFound|NoClassDefFound")
[ "$N" = "0" ] && ok "没有崩溃、没有类加载错误" || {
  bad "有 $N 条异常"
  logcat -d -t 400 | grep -E "FATAL EXCEPTION|ClassNotFound|NoClassDefFound" | head -3
}

echo
if [ "$FAIL" = "0" ]; then echo "==== 全部通过 ===="; else echo "==== 有 $FAIL 项失败 ===="; fi
exit $FAIL
