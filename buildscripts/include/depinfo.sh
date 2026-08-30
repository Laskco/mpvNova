#!/bin/bash -e

## Dependency versions
# Make sure to keep v_ndk and v_ndk_n in sync, both are listed on the NDK download page

v_sdk=11076708_latest
v_ndk=r29
v_ndk_n=29.0.14206865
v_sdk_platform=37.0
v_sdk_build_tools=36.0.0

v_lua=5.2.4
v_unibreak=7.0
v_harfbuzz=14.3.0
v_fribidi=1.0.16
v_freetype=2.14.3
v_mbedtls=3.6.7
v_dav1d=1.5.4
v_libxml2=2.15.3
v_fontconfig=2.18.3
v_libass=0.17.5
v_libplacebo=7.371.0
v_curl=8.21.0
v_mpv=9ce79bcaa0132660a2e45b6bfc1fb0c199665277


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
v_ci_ffmpeg=n9.0
v_ci_libplacebo=22ee762e8e0890fc54068beb670310f0edce7263
v_ci_arches=armv7l-arm64-x86-x86_64
v_ci_prefix_mode=full-mpv

# filename used to uniquely identify a build prefix
ci_tarball="prefix-ndk-${v_ndk}-arches-${v_ci_arches}-mode-${v_ci_prefix_mode}-lua-${v_lua}-unibreak-${v_unibreak}-harfbuzz-${v_harfbuzz}-fribidi-${v_fribidi}-freetype-${v_freetype}-libxml2-${v_libxml2}-fontconfig-${v_fontconfig}-mbedtls-${v_mbedtls}-dav1d-${v_dav1d}-libass-${v_libass}-libplacebo-${v_libplacebo}-${v_ci_libplacebo:0:8}-curl-${v_curl}-ffmpeg-${v_ci_ffmpeg}-mpv-${v_mpv:0:8}.tgz"
