#!/bin/bash
# Build graphics_driver/turnip-<version>-aio.tzst: Turnip (Mesa Vulkan for Adreno) with the
# Banners-Turnip "Banners-AIO" recipe, one driver for Adreno 6xx/7xx/710-722/8xx, for the
# Ubuntu 24.04 arm64 (glibc) rootfs.
#
# Needs the same cross setup as tools/freedreno-kgsl/build.sh (aarch64-linux-gnu-gcc/g++,
# meson >= 1.7, python3-mako, arm64 dev packages) without libxcb-keysyms-dev (not in the rootfs).
#
# Last built 2026-11-17 from Banners-Turnip e2cf8a1, Mesa main 01638af585b5.
set -e
WORK="${1:-$PWD/turnip-aio-build}"
BT_REF="${BT_REF:-e2cf8a1}"
mkdir -p "$WORK" && cd "$WORK"

[ -d bt ] || git clone https://github.com/The412Banner/Banners-Turnip bt
(cd bt && git checkout -q "$BT_REF")
MESA_COMMIT="${MESA_COMMIT:-$(cat bt/mesa_hash.txt)}"

rm -rf mesa && mkdir mesa && cd mesa
git init -q && git remote add origin https://gitlab.freedesktop.org/mesa/mesa.git
git fetch -q --depth 1 origin "$MESA_COMMIT" && git checkout -q FETCH_HEAD

# Linux (glibc) KGSL patch and the common patches, as bt/build_turnip_linux.sh applies them
patch -p1 -N --fuzz=3 < ../bt/patches/linux/kgsl-drm-node.patch
export EXTRA_PATCH=patches/a8xx_gen8.patch
bash ../bt/patches/common/apply_common.sh .
# The AIO recipe (bt/.github/workflows/turnip_build_combined.yml, variant "aio")
patch -p1 -N --fuzz=4 < ../bt/patches/a8xx_gen8.patch
for s in patches/a710-720.py patches/one/one_gpu_gates.py patches/one/one_fixes.py patches/a8xx_no_double_threadsize.py; do
    python3 "../bt/$s"
done

cat > ../cross.ini <<EOF
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
meson setup ../build --cross-file ../cross.ini --prefix=/usr --libdir=lib --buildtype=release \
    -Db_ndebug=true -Dvulkan-drivers=freedreno -Dfreedreno-kmds=msm,kgsl -Dgallium-drivers= \
    -Dplatforms=x11 -Dopengl=false -Dgbm=disabled -Dglx=disabled -Degl=disabled -Dllvm=disabled \
    -Dvulkan-layers= -Dtools= -Dxmlconfig=disabled -Dzstd=enabled -Dvalgrind=disabled \
    -Dlibunwind=disabled -Dlmsensors=disabled -Dbuild-tests=false
ninja -C ../build

vm=$(grep -oE 'VK_HEADER_VERSION_COMPLETE VK_MAKE_API_VERSION\(0, 1, [0-9]+' include/vulkan/vulkan_core.h | grep -oE '[0-9]+$')
vp=$(grep '^#define VK_HEADER_VERSION ' include/vulkan/vulkan_core.h | awk '{print $3}')
VER=$(cut -d- -f1 VERSION)
cd .. && rm -rf pkg && mkdir -p pkg/usr/lib pkg/usr/share/vulkan/icd.d
cp build/src/freedreno/vulkan/libvulkan_freedreno.so pkg/usr/lib/
aarch64-linux-gnu-strip --strip-unneeded pkg/usr/lib/libvulkan_freedreno.so
cat > pkg/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json <<EOF
{
    "ICD": {
        "api_version": "1.$vm.$vp",
        "library_arch": "64",
        "library_path": "/data/data/com.winlator/files/rootfs/usr/lib/libvulkan_freedreno.so"
    },
    "file_format_version": "1.0.1"
}
EOF
(cd pkg && tar --owner=0 --group=0 --numeric-owner -cf - usr) | zstd -19 -q -o "turnip-$VER-aio.tzst"
echo "Built $WORK/turnip-$VER-aio.tzst -> copy to app/src/main/assets/graphics_driver/"
