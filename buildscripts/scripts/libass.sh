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

fontconfig_build="$DIR/deps/fontconfig/_build$ndk_suffix"
if python3 - "$fontconfig_build" <<'PY'
import json
import pathlib
import re
import shlex
import sys

build = pathlib.Path(sys.argv[1])
config = (build / 'meson-config.h').read_text()
atomic = re.search(r'^#define HAVE_(STDATOMIC|INTEL_ATOMIC)_PRIMITIVES 1$', config, re.M)
headers = [build / 'config.h', build / 'meson-config.h', build.parent / 'config-fixups.h']
disabled = any(re.search(r'^\s*#\s*define\s+FC_NO_MT\b', p.read_text(), re.M)
               for p in headers if p.is_file())
commands = json.loads((build / 'compile_commands.json').read_text())
for command in commands:
    args = command.get('arguments') or shlex.split(command['command'])
    disabled |= any('FC_NO_MT' in arg or arg.startswith('@') for arg in args)
sys.exit(0 if atomic and commands and not disabled else 1)
PY
then
    export CPPFLAGS="$CPPFLAGS -DASS_FONTCONFIG_ATOMIC_REFS=1"
fi

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
