#!/system/bin/sh
# 把当前界面 dump 成「文字 + 中心坐标」，方便算点击位置。
#
# 为什么要这个：uiautomator 的 XML 是一整行，直接 grep 出来的 bounds 很难读；
# 而且在 PC 侧用 PowerShell 拼这类命令会被引号/重定向吃掉（踩过好几次）。
#
# 用法：
#   sh /data/local/tmp/ui.sh              列出全部可见文字
#   sh /data/local/tmp/ui.sh 刷新         只看含「刷新」的节点
#
# 说明：
# - **按子串匹配**，所以「刷新」会同时命中状态文字和按钮。要按文字点按钮时，
#   先做精确匹配（行格式是「文本  @ x,y」，即匹配「文本  @ 」这个前缀），
#   匹配不到再退回子串 —— 见 verify-plugin-crud.sh 里的 tap()。
# - 只有 content-desc（图标按钮）的节点也会列出来，标注为 [图标]。

uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
FILTER="$1"

cat /sdcard/ui.xml | tr '>' '\n' | while read -r line; do
  T=$(echo "$line" | sed -n 's/.*text="\([^"]*\)".*/\1/p')
  LABEL=""
  if [ -z "$T" ]; then
    # 图标按钮没有 text，只有 content-desc —— 不列出来的话，
    # 界面上看得见却"找不到"的控件就成了测试盲区（返回箭头踩过）。
    T=$(echo "$line" | sed -n 's/.*content-desc="\([^"]*\)".*/\1/p')
    [ -z "$T" ] && continue
    LABEL=" [图标]"
  fi
  B=$(echo "$line" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
  [ -z "$B" ] && continue
  if [ -n "$FILTER" ]; then
    echo "$T" | grep -q "$FILTER" || continue
  fi
  set -- $B
  echo "$T$LABEL  @ $(( ($1 + $3) / 2 )),$(( ($2 + $4) / 2 ))"
done
