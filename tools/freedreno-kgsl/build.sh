#!/bin/bash
# Build graphics_driver/freedreno-<version>.tzst: Mesa's freedreno Gallium driver
# with the KGSL backend, GLX/DRI3 for Winlator's X server, for the Ubuntu 24.04
# arm64 (glibc) rootfs.
#
# Needs: aarch64-linux-gnu-gcc/g++, meson >= 1.5, ninja, python3-mako, pkg-config,
# glslang, bison, flex, and arm64 dev packages (libdrm, libx11, libxext, libxfixes,
# libxxf86vm, libx11-xcb, libxcb-{dri2,dri3,present,glx,shm,randr,sync,xfixes},
# libxshmfence, libexpat, zlib, libzstd, libelf, libudev).
#
# The patch adds the KGSL Gallium backend (from SolDev69/freedreno-gallium-mesa
# 24.1, with the 26.x fixes from Lincinyshan/KGSL-Freedreno) and two X11 changes:
# with MESA_LOADER_DRIVER_OVERRIDE=kgsl GLX opens /dev/kgsl-3d0 itself and the
# DRI3 helper skips xshmfences/XFixes regions (Winlator's X server has neither
# DRI3 FenceFromFD nor XFixes).
set -e
VER=26.3.0
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${1:-$PWD/freedreno-build}"
mkdir -p "$WORK" && cd "$WORK"

[ -d mesa ] || git clone --depth 1 -b mesa-$VER https://gitlab.freedesktop.org/mesa/mesa.git mesa
(cd mesa && git apply --check "$HERE/mesa-$VER-kgsl-x11.patch" 2>/dev/null && git apply "$HERE/mesa-$VER-kgsl-x11.patch" || true)

cat > cross.ini <<EOF
[binaries]
c = 'aarch64-linux-gnu-gcc'
cpp = 'aarch64-linux-gnu-g++'
ar = 'aarch64-linux-gnu-ar'
strip = 'aarch64-linux-gnu-strip'
pkg-config = 'pkg-config'
[properties]
pkg_config_libdir = '/usr/lib/aarch64-linux-gnu/pkgconfig:/usr/share/pkgconfig'
[host_machine]
system = 'linux'
cpu_family = 'aarch64'
cpu = 'armv8-a'
endian = 'little'
EOF

meson setup build mesa --cross-file cross.ini --prefix=/usr --libdir=lib \
    --buildtype=release -Db_ndebug=true -Dplatforms=x11 -Dgallium-drivers=freedreno \
    -Dfreedreno-kmds=kgsl -Dvulkan-drivers= -Dglx=dri -Degl=disabled -Dgbm=disabled \
    -Dglvnd=disabled -Dgles1=disabled -Dgles2=enabled -Dllvm=disabled -Dzstd=enabled \
    -Dvalgrind=disabled -Dlibunwind=disabled -Dlmsensors=disabled -Dbuild-tests=false
ninja -C build
rm -rf inst pkg && DESTDIR="$PWD/inst" ninja -C build install

mkdir -p pkg/usr/lib
cp inst/usr/lib/libGL.so.1.2.0 pkg/usr/lib/libGL.so.1.7.0      # name the app swaps per driver
cp inst/usr/lib/libgallium-$VER.so pkg/usr/lib/
cp "$(readlink -f /usr/lib/aarch64-linux-gnu/libxcb-glx.so.0)" pkg/usr/lib/libxcb-glx.so.0   # not in the rootfs
aarch64-linux-gnu-strip --strip-unneeded pkg/usr/lib/*
chmod 755 pkg/usr/lib/*
(cd pkg && tar --owner=0 --group=0 --numeric-owner -cf - ./usr) | zstd -19 -q -o freedreno-$VER.tzst
echo "Built $WORK/freedreno-$VER.tzst -> copy to app/src/main/assets/graphics_driver/"
