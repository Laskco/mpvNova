param(
    [string]$AndroidNdk = $env:ANDROID_NDK_HOME,
    [switch]$HostOnly
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$crate = Join-Path $root 'native/anitomy'
$oldRustFlags = $env:RUSTFLAGS
Push-Location $crate
try {
    if ($HostOnly) {
        cargo build --release --locked
        if ($LASTEXITCODE -ne 0) { throw 'Host parser build failed' }
        return
    }
    if (!$AndroidNdk) { throw 'Set ANDROID_NDK_HOME or pass -AndroidNdk with NDK 29.0.14206865.' }
    $revision = Get-Content (Join-Path $AndroidNdk 'source.properties') | Select-String '^Pkg.Revision\s*=\s*29\.0\.14206865$'
    if (!$revision) { throw 'This build is pinned to Android NDK 29.0.14206865.' }
    $hostTag = if ($IsWindows) { 'windows-x86_64' } elseif ($IsMacOS) { 'darwin-x86_64' } else { 'linux-x86_64' }
    $suffix = if ($IsWindows) { '.exe' } else { '' }
    $bin = Join-Path $AndroidNdk "toolchains/llvm/prebuilt/$hostTag/bin"
    $targets = @(
        @('aarch64-linux-android', 'aarch64-linux-android23', 'arm64-v8a'),
        @('armv7-linux-androideabi', 'armv7a-linux-androideabi23', 'armeabi-v7a'),
        @('i686-linux-android', 'i686-linux-android23', 'x86'),
        @('x86_64-linux-android', 'x86_64-linux-android23', 'x86_64')
    )
    foreach ($target in $targets) {
        $linkerKey = 'CARGO_TARGET_' + $target[0].ToUpperInvariant().Replace('-', '_') + '_LINKER'
        $oldLinker = [Environment]::GetEnvironmentVariable($linkerKey, 'Process')
        try {
            [Environment]::SetEnvironmentVariable($linkerKey, (Join-Path $bin "clang$suffix"), 'Process')
            $env:RUSTFLAGS = "-C link-arg=--target=$($target[1]) -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
            cargo build --release --locked --target $target[0]
            if ($LASTEXITCODE -ne 0) { throw "Parser build failed: $($target[0])" }
            $library = Join-Path $crate "target/$($target[0])/release/libmpvnova_anitomy.so"
            $symbols = & (Join-Path $bin "llvm-nm$suffix") -D --defined-only $library
            if ($LASTEXITCODE -ne 0 -or !($symbols -match 'Java_app_mpvnova_player_AnitomyNg_parseNative')) {
                throw "Missing JNI entry point: $($target[0])"
            }
        } finally {
            [Environment]::SetEnvironmentVariable($linkerKey, $oldLinker, 'Process')
        }
    }
    # Publish only after every architecture built successfully; never copy playback libraries.
    foreach ($target in $targets) {
        $source = Join-Path $crate "target/$($target[0])/release/libmpvnova_anitomy.so"
        $destination = Join-Path $root "app/src/main/jniLibs/$($target[2])/libmpvnova_anitomy.so"
        Copy-Item -LiteralPath $source -Destination $destination
        Get-FileHash -LiteralPath $destination -Algorithm SHA256
    }
} finally {
    $env:RUSTFLAGS = $oldRustFlags
    Pop-Location
}
