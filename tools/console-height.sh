#!/system/bin/sh
# 量运行页控制台那个 Text 节点的高度（px）。
# 没日志时它显示两行占位文字，行高只由字号决定 —— 所以这是「字号」的干净观测量。
uiautomator dump /sdcard/h.xml >/dev/null 2>&1
LINE=$(cat /sdcard/h.xml | tr '>' '\n' | grep '还没有日志' | head -1)
B=$(echo "$LINE" | sed -n 's/.*bounds="\[[0-9]*,\([0-9]*\)\]\[[0-9]*,\([0-9]*\)\]".*/\1 \2/p')
set -- $B
if [ -z "$1" ]; then echo "NONE"; exit 1; fi
echo "console height = $(( $2 - $1 )) px"
