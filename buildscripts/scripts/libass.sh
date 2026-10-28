#!/bin/bash -e

. ../../include/path.sh
. "$DIR/include/depinfo.sh"

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf _build$ndk_suffix
	exit 0
else
	exit 255
fi

expected_ref=${MPVNOVA_LIBASS_REF:-$v_libass}
if [ "$(git rev-parse HEAD)" != "$expected_ref" ]; then
	echo >&2 "Refusing to build libass outside pinned revision $expected_ref."
	exit 1
fi

export CPPFLAGS="${CPPFLAGS:+$CPPFLAGS }-DASS_EVENT_THREADS"

[ -f configure ] || ./autogen.sh

asm_args=--enable-asm
[[ "$ndk_triple" == "arm"* ]] && asm_args=

mkdir -p _build$ndk_suffix
cd _build$ndk_suffix

../configure \
	--host=$ndk_triple --with-pic $asm_args \
	--enable-static --disable-shared \
	--enable-libunibreak --enable-fontconfig

make -j$cores
make DESTDIR="$prefix_dir" install
