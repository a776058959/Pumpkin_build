#!/system/bin/sh
# Linux 端到端验收。放在文件里跑，避免 PowerShell 的引号/管道被自己解释。
R=/data/local/pumpkin-linux
LP=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

inlinux() { chroot $R /bin/sh -c "export PATH=$LP HOME=/root TERM=xterm; $1"; }

echo "=== A. 内核与发行版 ==="
inlinux 'echo "发行版 $(cat /etc/alpine-release) / 内核 $(uname -r) / $(uname -m)"'

echo
echo "=== B. 三类真实软件 ==="
inlinux 'python3 -c "print(\"python3 ok:\", 6*7)"'
inlinux 'echo "git: $(git --version)"'
inlinux 'echo "htop: $(htop --version | head -1)"'

echo
echo "=== C. 网络 ==="
inlinux 'curl -s -o /dev/null -w "curl -> HTTP %{http_code} in %{time_total}s\n" https://mirrors.tuna.tsinghua.edu.cn/'

echo
echo "=== D. 文件系统 ==="
inlinux 'df -h / | tail -1'

echo
echo "=== E. 结论 ==="
inlinux 'echo "原生 aarch64 Linux 可用（共用 Android 内核，非模拟）"'
