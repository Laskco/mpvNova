#!/bin/bash -e

## Dependency versions
# Make sure to keep v_ndk and v_ndk_n in sync, both are listed on the NDK download page

v_sdk=11076708_latest
v_ndk=r30
v_ndk_n=30.0.16248370
v_sdk_platform=37.0
v_sdk_build_tools=36.0.0

v_lua=5.2.4
v_unibreak=8.0
v_harfbuzz=14.6.0
v_fribidi=1.0.17
v_freetype=2.15.0
v_mbedtls=3.6.7
v_dav1d=1.5.4
v_libxml2=2.15.4
v_fontconfig=2.18.3
v_libass=7b4c7be1dbb024bd58dd31fc390643ac57d8049d
libass_repository=https://github.com/Laskco/mpvNova-libass.git
v_libplacebo=7.375.0
v_curl=8.23.0
# Pin upstream mpv; apply the default-off FEL and external subtitle URL patches.
v_mpv=492bae0768f5e826991bf9fb222fa303371e8498


## Dependency tree

dep_mbedtls=()
dep_dav1d=()
dep_libxml2=()
dep_ffmpeg=(mbedtls dav1d libxml2)
dep_freetype2=()
dep_fontconfig=(libxml2 freetype2)
dep_fribidi=()
dep_harfbuzz=()
dep_unibreak=()
dep_libass=(freetype2 fontconfig fribidi harfbuzz unibreak)
dep_lua=()
dep_libplacebo=()
dep_curl=(mbedtls)
dep_mpv=(ffmpeg libass lua libplacebo curl)
dep_mpvnova=(mpv)


## for CI workflow

# pinned ffmpeg revision
v_ci_ffmpeg=n9.0.2
v_ci_libplacebo=54c8476dc9ef15d5b68c7dd4e431804abc5bc183
v_ci_arches=armv7l-arm64-x86-x86_64
v_ci_prefix_mode=full-mpv

# filename used to uniquely identify a build prefix
ci_tarball="prefix-upstream-ndk-${v_ndk}-arches-${v_ci_arches}-mode-${v_ci_prefix_mode}-lua-${v_lua}-unibreak-${v_unibreak}-harfbuzz-${v_harfbuzz}-fribidi-${v_fribidi}-freetype-${v_freetype}-libxml2-${v_libxml2}-fontconfig-${v_fontconfig}-mbedtls-${v_mbedtls}-dav1d-${v_dav1d}-libass-fork-${v_libass:0:12}-threads1-asm1-libplacebo-${v_libplacebo}-${v_ci_libplacebo:0:8}-curl-${v_curl}-ffmpeg-${v_ci_ffmpeg}-mpv-${v_mpv:0:8}-feloff2-urlsubs2-16krelro1.tgz"
