#!/system/bin/sh
# 端到端验证 CONFIRM 型对话框：下载 → 暂停 → 删除任务（弹确认框）→ 点确定 → 看是否真的删掉。
# 这是唯一能真实触发 confirm() 的路径（clear* 那几个目前没 UI 入口）。

dump() {
  uiautomator dump /sdcard/c.xml >/dev/null 2>&1
  cat /sdcard/c.xml | tr '>' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' | sed 's/text="//;s/"$//'
}
tap() { input tap $1 $2; }
find_y() {  # find_y "文字" → 打印它的 y 中心
  uiautomator dump /sdcard/c.xml >/dev/null 2>&1
  cat /sdcard/c.xml | tr '>' '\n' | grep "text=\"$1\"" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | \
    sed 's/[^0-9]/ /g' | awk '{print int(($2+$4)/2)}'
}

echo "########## 1. 到更新页，点检查更新 ##########"
tap 540 2234; sleep 2
Y=$(find_y "检查更新"); [ -n "$Y" ] && tap 540 $Y
echo "等联网检查…"; sleep 18
dump | grep -E "共|可用版本" | sed 's/^/    /'

echo
echo "########## 2. 开始下载 ##########"
Y=$(find_y "下载"); [ -n "$Y" ] && tap 540 $Y
sleep 12
echo "--- 下载中状态 ---"; dump | grep -E "%|下载中|暂停" | head -4 | sed 's/^/    /'

echo
echo "########## 3. 暂停 ##########"
Y=$(find_y "暂停"); if [ -n "$Y" ]; then tap 540 $Y; echo "已点暂停 (y=$Y)"; else echo "没找到暂停按钮"; fi
sleep 3
echo "--- 暂停后 ---"; dump | grep -E "继续|暂停|已暂停|删除" | head -5 | sed 's/^/    /'

echo
echo "########## 4. 删除下载任务 → 应弹 CONFIRM ##########"
Y=$(find_y "删除下载任务")
if [ -n "$Y" ]; then
  tap 540 $Y; sleep 3
  echo "--- 弹窗内容 ---"; dump | sed 's/^/    /'
else
  echo "没找到删除下载任务按钮"
fi
