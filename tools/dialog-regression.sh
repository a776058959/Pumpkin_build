#!/system/bin/sh
# 弹窗全量回归：把每个 miuix 对话框都打开一次，dump 出内容供比对。
ADB=/data/local/tmp/nope
export PATH=/system/bin:$PATH

dump() {
  uiautomator dump /sdcard/t.xml >/dev/null 2>&1
  cat /sdcard/t.xml | tr '>' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' | sed 's/text="//;s/"$//'
}

tap() { input tap $1 $2; }

echo "########## 1. 设置页 → 启动方式 ##########"
tap 849 2234; sleep 2
tap 540 621;  sleep 3
echo "--- 弹窗内容 ---"; dump | sed 's/^/    /'
tap 540 2190; sleep 2   # 取消

echo
echo "########## 2. 更新页 → 删除版本 ##########"
tap 540 2234; sleep 2
tap 540 1868; sleep 3
echo "--- 弹窗内容 ---"; dump | sed 's/^/    /'
tap 540 2190; sleep 2   # 取消

echo
echo "########## 3. 完成 ##########"
echo "（检查更新→选择版本 需要联网，单独测）"
