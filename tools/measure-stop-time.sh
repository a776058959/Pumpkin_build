#!/system/bin/sh
# 量一次「点确定 → 服务端真的停下来」要多久。
# 停服是让服务端自己收尾（存世界），不是立刻 kill，所以断言前必须等 ——
# 第一版验证脚本固定 sleep 几秒就断言「应该已经停了」，误报过一次。
P=com.pumpkin.server
ui() { sh /data/local/tmp/ui.sh ""; }
running() { ! ui | grep -qF "未运行"; }

sh /data/local/tmp/goto.sh 运行 >/dev/null
if ! running; then
  echo "服务端没在跑，先起"
  sh /data/local/tmp/ui.sh 启动 | head -1 | sed -n 's/.*@ \([0-9]*\),\([0-9]*\)/\1 \2/p' | { read x y; input tap $x $y; }
  i=0; while [ $i -lt 90 ]; do running && break; sleep 3; i=$((i + 3)); done
fi
echo "起始状态：$(ui | grep 运行中 | head -1)"

sh /data/local/tmp/ui.sh 停止 | grep -F "停止  @ " | head -1 | sed -n 's/.*@ \([0-9]*\),\([0-9]*\)/\1 \2/p' | { read x y; input tap $x $y; }
sleep 2
echo "点了停止之后：$(ui | head -3)"

# 如果弹了确认，点确定
if ui | grep -qF "请确认"; then
  echo "弹了确认，点确定"
  sh /data/local/tmp/ui.sh 确定 | grep -F "确定  @ " | head -1 | sed -n 's/.*@ \([0-9]*\),\([0-9]*\)/\1 \2/p' | { read x y; input tap $x $y; }
fi

T=0
while [ $T -lt 120 ]; do
  if ! running; then echo "服务端已停止（用了约 ${T}s）"; exit 0; fi
  sleep 2
  T=$((T + 2))
done
echo "等了 120s 还在跑"
exit 1
