#!/system/bin/sh
# 切到指定页，并**确认真的切过去了**。
#
# 为什么要确认：`input tap` 打在底栏上有时候会被吃掉（实测偶发，
# 大概是上一帧的动画还没结束）。直接接着 tap 页面里的按钮，
# 就会点在上一页的空白处 —— 表现为「点了没反应」，容易误判成功能坏了。
#
# 用法：sh /data/local/tmp/goto.sh 运行|更新|插件|设置

PAGE="$1"
case "$PAGE" in
  运行) X=193; TITLE=控制台 ;;
  更新) X=424; TITLE=版本 ;;
  插件) X=655; TITLE=插件商店 ;;
  设置) X=887; TITLE=外观 ;;
  *) echo "用法：goto.sh 运行|更新|插件|设置"; exit 2 ;;
esac

i=0
while [ $i -lt 5 ]; do
  input tap $X 2278
  sleep 3
  uiautomator dump /sdcard/g.xml >/dev/null 2>&1
  if cat /sdcard/g.xml | tr '>' '\n' | grep -q "text=\"$TITLE"; then
    echo "已在「$PAGE」页（第 $((i + 1)) 次点击）"
    exit 0
  fi
  i=$((i + 1))
done

echo "切不到「$PAGE」页（试了 5 次）"
exit 1
