# 原生 Linux（Alpine chroot）— 在 Android 手机上跑真正的 Linux

这不是 Termux 那种「用户态终端模拟」。它是在 **Android 内核上直接 chroot** 进一个真实的
aarch64 Linux 用户空间，与 Linux Deploy / Andronix 原理相同：

- **共用内核**：`uname -r` 返回的就是 Android 的内核 `4.14.186-perf-...`
- **共用网络栈**：Linux 里直接看到 `wlan0` 的 `192.168.10.175`，不需要任何转发
- **真正的用户空间**：musl libc、真正的 `init` 进程树、真正的软件包管理（24000+ 个包）
- **不是模拟**：没有 QEMU，没有用户态指令翻译，就是原生 aarch64 指令直接跑

## 已验证的环境（Redmi M2004J7AC / HyperOS V816 / Android 15）

| 项目 | 状态 |
|---|---|
| Root | Magisk **Kitsune** `R6687BB53-kitsune (27001)`，`su` → `uid=0 context=u:r:magisk:s0` |
| SELinux | `Enforcing` 下 chroot 与 mount 均可执行，无需 `setenforce 0` |
| 内核 | `4.14.186-perf` — 支持 loop 设备、`/proc/filesystems` 含 ext4 |
| `/data` | ext4（不是 sdcardfs），符号链接与权限位正常保留 |
| `/dev/kvm` | **不存在** → 跑不了 KVM 硬件加速的整机虚拟机；chroot 不需要它 |

## 快速开始

```sh
# 1. 把脚本推到手机
adb push tools/native-linux/linux.sh /data/local/tmp/linux.sh

# 2. 首次部署（下载 + 解压 Alpine + 配源）
adb shell su -c 'sh /data/local/tmp/linux.sh setup'

# 3. 进去用
adb shell su -c 'sh /data/local/tmp/linux.sh start'

# 或者只跑一条命令
adb shell su -c 'sh /data/local/tmp/linux.sh exec "uname -a"'
```

已实测可用：

```
Alpine 3.20.3        musl libc (aarch64)
内核 4.14.186-perf   aarch64
内存 5479 MB
apk  : 24059 distinct packages available
curl : HTTP 200  用时 0.51s
python3 3.12.13 / git 2.45.4 / htop 3.3.0  均正常运行
```

进入后可以直接 `apk add` 装任何东西（python3、gcc、nginx、ffmpeg…）。

## 踩过的坑（都已在脚本里处理）

### 1. chroot 里 PATH 必须重设
`chroot` 会继承 Android 的 `PATH=/system/bin:...`，chroot 之后该路径不存在，
busybox 的所有 applet 都报 `cat: not found` / `uname: not found`。
**症状极像「rootfs 坏了」，其实是 PATH 问题。**

脚本里统一用：
```sh
chroot $ROOT /bin/sh -c "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; …"
```

### 2. 不能写成 `chroot $ROOT env -i PATH=... cmd`
`chroot` 是在**宿主**环境里查找要执行的程序名的，此时 PATH 还是 Android 的，
找不到 `env`，报 `chroot: exec env: No such file or directory`。
必须 chroot 进去之后再设 PATH。

### 3. Android 没有 curl
自带的是 toybox。脚本会依次探测 `curl` → `wget` → `toybox wget`，
都没有就提示用 `adb push` 把 rootfs 推进去。

### 4. `/sdcard` 在 chroot 里不可见
要传文件就 `adb push <本地文件> /data/local/pumpkin-linux/root/`。
需要的话也可以在里面 `mount -o bind /sdcard` 挂进来。

### 5. 用目录式 rootfs，不用 ext4 镜像 + loop
本机 `losetup -a` 显示系统自己已占用 40+ 个 loop 设备（loop0..loop43），
loop 号是稀缺资源。目录式方案功能等价，还少一层 loop 和一次 dd。

### 6. 函数别命名成 `r`
Android 的 mksh 内置 `alias r='fc -e -'`（重复上一条命令），
别名优先级高于同名函数，会导致每次调用都变成 `fc` 并报
`history functions not available`。踩过一次，脚本里改名为 `inlinux`。

### 7. 卸载要处理叠加挂载
反复 `mount` 同一挂载点的 `dev`/`devpts` 会在 `/proc/mounts` 里留下多条条目，
之后普通 `umount` 报 `Invalid argument`。脚本用循环从最内层逐个卸到干净。

## 为什么不是别的方案

| 方案 | 为什么不用 |
|---|---|
| **Termux** | 用户态终端模拟，不是原生 Linux 用户空间（正是用户明确排除的） |
| **QEMU 整机虚拟机** | 无 `/dev/kvm`，纯软件模拟性能损失一个数量级 |
| **proot**（如 proot-distro） | 靠 `ptrace` 拦截系统调用，开销大，且不是真 chroot |
| **Linux Deploy** | 原理相同，但它是个已停止维护的 App；这里只要一个脚本 |

## 目录结构

```
/data/local/pumpkin-linux/         # rootfs（目录式）
/data/local/tmp/alpine-rootfs.tar.gz   # 下载的 rootfs 缓存
/data/local/tmp/linux.sh               # 管理脚本
```
