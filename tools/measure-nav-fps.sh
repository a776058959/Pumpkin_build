#!/system/bin/sh
# 量「来回快点底栏三个按钮」的帧率。
#
# 用 gfxinfo 拿真实的逐帧耗时 —— "像帧数低"这种描述只有帧时间能证伪/证实：
# 如果是抖动（轨迹问题），帧时间是正常的；如果是掉帧（渲染太重），
# 就会看到大量 >16.7ms 的帧。两者修法完全不同，必须先分清。

P=com.pumpkin.server
OUT=/sdcard/gfx_$1.txt

# 从干净状态开始
am force-stop $P
sleep 1
am start -n $P/.MainActivity >/dev/null
sleep 8

# 清掉累计统计，只量下面这段
dumpsys gfxinfo $P reset >/dev/null
sleep 1

echo "开始快速来回点击…"
i=0
while [ $i -lt 5 ]; do
  input tap 231 2255
  sleep 0.4
  input tap 540 2255
  sleep 0.4
  input tap 849 2255
  sleep 0.4
  i=$((i + 1))
done

sleep 1
dumpsys gfxinfo $P > $OUT
echo "写入 $OUT"
grep -E "Total frames|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number Slow UI thread|Number Slow bitmap uploads|Number Slow issue draw commands" $OUT
