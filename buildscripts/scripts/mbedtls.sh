#!/bin/bash -e

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	make clean
	exit 0
else
	exit 255
fi

$0 clean # separate building not supported, always clean
if [[ "$ndk_triple" == "i686"* ]]; then
	./scripts/config.py unset MBEDTLS_AESNI_C
else
	./scripts/config.py set MBEDTLS_AESNI_C
fi

# since 3.6.6 mbedTLS defaults to /dev/random, which is known to block unnecessarily
./scripts/config.py set MBEDTLS_PLATFORM_DEV_RANDOM '"/dev/urandom"'

make -j$cores no_test

# Keep the ARMv7 fallback while making the existing runtime-dispatched Armv8
# crypto implementation available to the experimental 32-bit TV build.
if [[ "${MPVNOVA_ARM32_TLS_ACCELERATION:-0}" == 1 && "$ndk_triple" == arm-* ]]; then
	for source in aes gcm; do
		$CC -O2 -g -fPIC -Iinclude -Ilibrary -DMBEDTLS_ARCH_IS_ARMV8_A \
			-c "library/$source.c" -o "library/$source.o"
	done
	# Only this translation unit can contain Armv8 crypto instructions. Calls
	# remain guarded by mbedTLS's Linux HWCAP check; software code is retained.
	$CC -O2 -g -fPIC -march=armv8-a+crypto -Iinclude -Ilibrary \
		-c library/aesce.c -o library/aesce.o
	$AR r library/libmbedcrypto.a library/aes.o library/gcm.o library/aesce.o
fi
make DESTDIR="$prefix_dir" install
