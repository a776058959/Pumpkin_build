#!/system/bin/sh
# 抓「快速来回点底栏」期间的 CPU 采样，让热点函数自己现形。
#
# 为什么用 simpleperf 而不是继续猜：gfxinfo 只能告诉我们"UI 线程慢"，
# 不能告诉我们慢在哪个函数。simpleperf 带调用链采样，能直接点名。

P=com.pumpkin.server
OUT=/data/local/tmp/perf.data

am force-stop $P
sleep 1
am start -n $P/.MainActivity >/dev/null
sleep 8

PID=$(pidof $P)
echo "pid=$PID"
rm -f $OUT

echo "开始采样（15 秒，期间快速点击）…"
simpleperf record -p $PID -g -f 1000 -o $OUT --duration 15 &
SP=$!

sleep 2
i=0
while [ $i -lt 7 ]; do
  input tap 231 2255
  sleep 0.4
  input tap 540 2255
  sleep 0.4
  input tap 849 2255
  sleep 0.4
  i=$((i + 1))
done

wait $SP
echo
echo "=========== 热点函数（全部线程）==========="
simpleperf report -i $OUT --sort symbol --percent-limit 0.8 2>&1 | head -45

echo
echo "=========== 主线程（UI 线程）热点 ==========="
simpleperf report -i $OUT --sort thread,symbol 2>&1 | grep -E "main|RenderThread|Compose|measure|layout|draw|Shader|Recompos" | head -30
