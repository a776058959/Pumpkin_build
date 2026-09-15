#!/system/bin/sh
#
# Pumpkin 原生 Linux（Alpine chroot）管理脚本
# ============================================================================
# 这不是 Termux 那种「用户态终端模拟」，而是在 Android 内核上直接 chroot 进一个
# 真实的 aarch64 Linux 用户空间：共用内核、共用网络栈，能跑真正的 musl/glibc
# 二进制、真正的软件包管理（apk/apt）、真正的 init 进程树。
#
# 本机已验证的前提：
#   * Magisk Kitsune 已 root：su → uid=0(root) context=u:r:magisk:s0
#   * SELinux Enforcing 下 chroot / mount 均可执行
#   * /data 是 ext4（非 sdcardfs），符号链接与权限位能正常保留
#   * 无 /dev/kvm → 跑不了 KVM 硬件加速的整机虚拟机；chroot 不需要它
#
# 为什么用「目录式 rootfs」而不是「ext4 镜像 + loop」：
#   本机 losetup -a 显示系统自己已占用 40+ 个 loop 设备（loop0..loop43），
#   loop 号属于稀缺资源。目录式方案功能等价，还少一层 loop 和一次 dd。
#
# 用法：
#   sh linux.sh setup    首次部署（下载 + 解压 + 配源）
#   sh linux.sh start    进入交互式 Linux shell
#   sh linux.sh exec "…" 在 Linux 里执行一条命令
#   sh linux.sh status   查看状态
#   sh linux.sh remove   彻底删除
# ============================================================================

ROOT=/data/local/pumpkin-linux
TARBALL=/data/local/tmp/alpine-rootfs.tar.gz
ALPINE_VER=3.20
ALPINE_REL=3.20.3
MIRROR=https://mirrors.tuna.tsinghua.edu.cn/alpine
FALLBACK=https://dl-cdn.alpinelinux.org/alpine

# chroot 内部必须重设 PATH：chroot 会继承 Android 的 PATH（/system/bin:...），
# 而 chroot 之后那个路径不存在，busybox 的 applet 会全部 "not found"。
LPATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# 在 Linux 里跑一条命令
lrun() {
    chroot $ROOT /bin/sh -c "export PATH=$LPATH HOME=/root TERM=${TERM:-xterm}; $1"
}

mounted() { grep -q " $ROOT/proc " /proc/mounts; }

do_mount() {
    mounted && return 0
    # 伪文件系统必须挂：没有 /proc 则 uname/free/ps 全空，
    # 没有 /dev 则连 /dev/null 都不存在，很多程序会直接报错退出。
    mount -t proc  none $ROOT/proc     2>/dev/null
    mount -t sysfs none $ROOT/sys      2>/dev/null
    mount -o bind /dev $ROOT/dev       2>/dev/null
    mount -t devpts none $ROOT/dev/pts 2>/dev/null
    echo "伪文件系统已挂载"
}

do_umount() {
    # 反复卸直到 /proc/mounts 里不再出现，避免叠加挂载导致的 Invalid argument
    i=0
    while [ $i -lt 20 ]; do
        grep -q " $ROOT/" /proc/mounts || break
        t=$(grep " $ROOT/" /proc/mounts | tail -1 | awk '{print $2}')
        umount "$t" 2>/dev/null || umount -l "$t" 2>/dev/null
        i=$((i+1))
    done
    umount $ROOT 2>/dev/null || umount -l $ROOT 2>/dev/null
    echo "已卸载"
}

do_setup() {
    if [ -f "$ROOT/etc/alpine-release" ]; then
        echo "已安装：Alpine $(cat $ROOT/etc/alpine-release)"
        echo "（要重装请先 sh $0 remove）"
        return 0
    fi

    mkdir -p $ROOT

    if [ ! -s "$TARBALL" ]; then
        echo "=== 下载 Alpine $ALPINE_REL rootfs ==="
        # Android 自带的是 toybox，没有 curl；wget 也只在部分版本里有。
        # 逐个探测可用的下载器，都没有就提示用 adb push（PC 端下载后推进来）。
        fetch() {
            url="$1"
            if command -v curl >/dev/null 2>&1; then
                curl -sL -o $TARBALL "$url"
            elif command -v wget >/dev/null 2>&1; then
                wget -q -O $TARBALL "$url"
            elif command -v toybox >/dev/null 2>&1 && toybox wget --help >/dev/null 2>&1; then
                toybox wget -O $TARBALL "$url" 2>/dev/null
            else
                return 127
            fi
        }
        fetch "$MIRROR/v$ALPINE_VER/releases/aarch64/alpine-minirootfs-$ALPINE_REL-aarch64.tar.gz"
        rc=$?
        if [ $rc -eq 127 ]; then
            echo "  设备上没有 curl/wget。请在电脑上执行："
            echo "    adb push alpine-minirootfs-$ALPINE_REL-aarch64.tar.gz $TARBALL"
            echo "  然后再跑一次 setup。"
            return 1
        fi
        if [ ! -s "$TARBALL" ]; then
            echo "  清华镜像失败，试官方 CDN"
            fetch "$FALLBACK/v$ALPINE_VER/releases/aarch64/alpine-minirootfs-$ALPINE_REL-aarch64.tar.gz"
        fi
    fi
    [ -s "$TARBALL" ] || { echo "rootfs 下载失败"; return 1; }
    echo "  rootfs: $(du -h $TARBALL | cut -f1)"

    echo "=== 解压 ==="
    tar xzf $TARBALL -C $ROOT || return 1

    echo "=== 配置软件源（清华镜像，官方 CDN 在国内常几十 KB/s）==="
    mkdir -p $ROOT/etc
    cat > $ROOT/etc/apk/repositories <<EOF
$MIRROR/v$ALPINE_VER/main
$MIRROR/v$ALPINE_VER/community
EOF
    # Android 的 /etc/resolv.conf 在 chroot 里看不到，必须自己写一份
    printf 'nameserver 223.5.5.5\nnameserver 119.29.29.29\n' > $ROOT/etc/resolv.conf

    do_mount
    echo "=== apk update ==="
    lrun 'apk update 2>&1 | tail -3'
    echo
    echo "部署完成。进入：sh $0 start"
}

do_status() {
    echo "rootfs: $ROOT"
    if [ -f "$ROOT/etc/alpine-release" ]; then
        echo "  发行版: Alpine $(cat $ROOT/etc/alpine-release)"
        echo "  占用:   $(du -sh $ROOT 2>/dev/null | cut -f1)"
        mounted && echo "  已挂载" || echo "  未挂载（start 时自动挂）"
        mounted && lrun 'echo "  内核: $(uname -r)  架构: $(uname -m)"'
    else
        echo "  未安装（跑 setup）"
    fi
}

case "$1" in
    setup) do_setup ;;
    start)
        [ -f "$ROOT/etc/alpine-release" ] || { echo "还没装，先跑 setup"; exit 1; }
        do_mount
        echo
        echo "进入 Alpine Linux。退出：exit"
        echo "说明：Android 的 /sdcard 在 chroot 里不可见；要传文件用"
        echo "      adb push <本地文件> $ROOT/root/"
        echo
        chroot $ROOT /bin/sh -c "export PATH=$LPATH HOME=/root TERM=${TERM:-xterm}; exec /bin/sh -i"
        ;;
    exec)
        shift
        [ -f "$ROOT/etc/alpine-release" ] || { echo "还没装，先跑 setup"; exit 1; }
        do_mount >/dev/null
        lrun "$*"
        ;;
    stop|umount) do_umount ;;
    status) do_status ;;
    remove)
        do_umount
        rm -rf $ROOT $TARBALL
        echo "已删除"
        ;;
    *) sed -n '3,28p' $0 | sed 's/^# \{0,1\}//' ;;
esac
