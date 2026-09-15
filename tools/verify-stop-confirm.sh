#!/system/bin/sh
# 验证「停止前确认」插件真的改变了 App 的行为 —— 不只是写了个值。
#
# 这条链路是插件的第二种形态（TOGGLE 改行为），和「控制台字号」（TEXT 改显示）不同：
# 光断言「覆盖值写进去了」是不够的，得看**确认框到底弹不弹**。
#
# 前置：手机上有已安装的服务端版本（App 的「停止」按钮只在运行时可点，
#       所以必须真的把服务端起起来）。见 tools/install-server-for-test.sh。
#
# 依赖：/data/local/tmp/ 下有 ui.sh / goto.sh

P=com.pumpkin.server
XML=/data/data/$P/shared_prefs/pumpkin_shell.xml
STOP=plugin_ov_run.confirmStop
FAIL=0

ok()  { echo "    OK   $1"; }
bad() { echo "    FAIL $1"; FAIL=$((FAIL + 1)); }

ui()  { sh /data/local/tmp/ui.sh ""; }
tap() {
  L=$(sh /data/local/tmp/ui.sh "$1" | grep -F "$1  @ " | head -1)
  [ -z "$L" ] && L=$(sh /data/local/tmp/ui.sh "$1" | head -1)
  echo "$L" | sed -n 's/.*@ \([0-9]*\),\([0-9]*\)/\1 \2/p'
}
click() {
  set -- $(tap "$1")
  if [ -z "$1" ] || [ -z "$2" ]; then bad "界面上找不到可点的「$1」"; return 1; fi
  input tap $1 $2
  sleep 3
}
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
has()    { if ui | grep -qF "$2"; then ok "$1"; else bad "$1（界面里没有「$2」）"; fi; }
hasnot() { if ui | grep -qF "$2"; then bad "$1（界面里出现了不该有的「$2」）"; else ok "$1"; fi; }
prefs()  { su -c "grep -o '$1.>[^<]*' $XML" 2>/dev/null | sed 's/.*>//'; }

# 服务端在跑吗？
#
# 认「运行中」而不是「未运行」：运行页的状态文字有三种 ——
#   从未启动 → 「未运行」      运行中 → 「运行中 0:54」      停过之后 → 「已停止（退出码 0）」
# 第一版这里找「未运行」，于是**停完之后也判定成"还在跑"**，误报过一次。
server_running() { ui | grep -qF "运行中"; }

wait_running() {
  i=0
  while [ $i -lt "$1" ]; do
    if server_running; then return 0; fi
    sleep 3
    i=$((i + 3))
  done
  return 1
}

# 停服不是立刻 kill：服务端要存世界（实测存完要几十秒），所以断言前必须等。
wait_stopped() {
  i=0
  while [ $i -lt "$1" ]; do
    if ! server_running; then return 0; fi
    sleep 3
    i=$((i + 3))
  done
  return 1
}

echo "==== 准备：清掉插件，起服务端 ===="
# 路径要和实际推上去的一致。第一版这里写成了 plugin-reset.sh（推上去叫 reset.sh），
# 再被 >/dev/null 2>&1 吃掉报错 —— 于是带着上一轮的残留插件在跑，
# 断言全乱套，看起来像功能坏了。**清理脚本失败必须让人看见。**
if ! sh /data/local/tmp/reset.sh; then
  bad "重置脚本跑不起来（路径对不对？）"
  echo "==== 有 $FAIL 项失败 ===="; exit $FAIL
fi
sh /data/local/tmp/goto.sh 运行 >/dev/null
if server_running; then
  echo "    服务端已经在跑"
else
  click 启动 >/dev/null
  if wait_running 90; then echo "    服务端已启动"; else
    bad "服务端起不来（先看运行页的日志 / last_crash.txt）"
    echo "==== 有 $FAIL 项失败 ===="; exit $FAIL
  fi
fi

echo
echo "==== 1. 默认（没有任何插件）：点「停止」应当先弹确认 ===="
click 停止 >/dev/null
has "弹出了确认框" "请确认"
click 取消 >/dev/null
if server_running; then ok "点「取消」后服务端还在跑"; else bad "取消之后服务端却停了"; fi

echo
echo "==== 2. 装「停止前确认」插件，把开关关掉 ===="
sh /data/local/tmp/goto.sh 插件 >/dev/null
click 商店 >/dev/null
sleep 6
click_near 停止前确认 安装 >/dev/null
has "插件装上了" "已装好"
click 已安装 >/dev/null
has "开关默认显示「开」" "停止前先确认：开"
click "停止前先确认" >/dev/null
has "点一下翻成「关」" "停止前先确认：关"
V=$(prefs "$STOP")
[ "$V" = "false" ] && ok "覆盖值写进 Prefs（$STOP=false）" || bad "覆盖值是 [$V]，期望 false"

echo
echo "==== 3. 关键：关掉确认后，点「停止」不该再弹确认 ===="
sh /data/local/tmp/goto.sh 运行 >/dev/null
click 停止 >/dev/null
hasnot "没有弹确认框" "请确认"
if wait_stopped 150; then
  ok "服务端确实停了（说明停止动作真的执行了，不是被确认框挡住）"
else
  bad "等了 150s 服务端还在跑"
fi

echo
echo "==== 4. 还原：卸载插件，确认值应当被清掉 ===="
sh /data/local/tmp/goto.sh 插件 >/dev/null
click 卸载 >/dev/null
click 确定 >/dev/null
V=$(prefs "$STOP")
[ -z "$V" ] && ok "覆盖值已清掉" || bad "覆盖值没清：$STOP=[$V]"
sh /data/local/tmp/goto.sh 运行 >/dev/null
click 启动 >/dev/null
if wait_running 90; then
  click 停止 >/dev/null
  has "卸载后确认框回来了（回到内置行为）" "请确认"
  click 取消 >/dev/null
else
  bad "还原后服务端起不来"
fi

echo
echo "==== 5. 崩溃检查 ===="
N=$(logcat -d -t 400 2>/dev/null | grep -cE "FATAL EXCEPTION|ClassNotFound|NoClassDefFound")
[ "$N" = "0" ] && ok "没有崩溃、没有类加载错误" || bad "有 $N 条异常"

echo
if [ "$FAIL" = "0" ]; then echo "==== 全部通过 ===="; else echo "==== 有 $FAIL 项失败 ===="; fi
exit $FAIL
