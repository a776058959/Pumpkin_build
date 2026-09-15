#!/system/bin/sh
# 把当前界面 dump 成「文字 + 中心坐标」，方便算点击位置。
#
# 为什么要这个：uiautomator 的 XML 是一整行，直接 grep 出来的 bounds 很难读；
# 而且在 PC 侧用 PowerShell 拼这类命令会被引号/重定向吃掉（踩过好几次）。
# 所以固定流程是：把这个脚本 push 上去，再 `sh /data/local/tmp/ui.sh [过滤词]`。
#
# 用法：
#   sh /data/local/tmp/ui.sh              列出全部可见文字
#   sh /data/local/tmp/ui.sh 刷新         只看含「刷新」的节点
#   sh /data/local/tmp/ui.sh 刷新 tap     直接点第一个匹配节点的中心

uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
FILTER="$1"

cat /sdcard/ui.xml | tr '>' '\n' | while read -r line; do
  T=$(echo "$line" | sed -n 's/.*text="\([^"]*\)".*/\1/p')
  [ -z "$T" ] && continue
  B=$(echo "$line" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
  [ -z "$B" ] && continue
  if [ -n "$FILTER" ]; then
    echo "$T" | grep -q "$FILTER" || continue
  fi
  set -- $B
  echo "$T  @ $(( ($1 + $3) / 2 )),$(( ($2 + $4) / 2 ))"
done
