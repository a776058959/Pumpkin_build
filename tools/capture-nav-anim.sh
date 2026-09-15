#!/system/bin/sh
# 把「点击底栏」那一刻的动画逐帧拍下来。
#
# 关键技巧：先系统动画放慢 10 倍（animator_duration_scale），
# 原本 ~300ms 的弹簧动画变成几秒，screencap 就能采到足够多帧。
# 否则一帧都拍不到 —— 这正是这种"手感"问题难验证的原因。
#
# 判断依据：选中图标用强调色画、未选中是灰的，所以
# 「强调色像素的水平重心」就是胶囊的位置。
# 丝滑 = 重心单调移动；抽搐 = 重心来回跳（前进→回退→再前进）。

echo "=== 动画放慢 10 倍 ==="
settings put global animator_duration_scale 10
settings get global animator_duration_scale

echo
echo "=== 点「运行」并连拍 10 帧 ==="
# 先确保停在「设置」页，这样点「运行」有一段完整行程
input tap 849 2255
sleep 1

input tap 231 2255
i=1
while [ $i -le 10 ]; do
  screencap -p /sdcard/anim_$i.png
  i=$((i + 1))
done
echo "已拍 10 帧"

echo
echo "=== 恢复动画速度 ==="
settings put global animator_duration_scale 1
settings get global animator_duration_scale
