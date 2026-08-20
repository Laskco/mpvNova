param(
    [Parameter(Mandatory = $true)]
    [string] $Ffmpeg,
    [string] $AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string] $Serial = "emulator-5554"
)

$ErrorActionPreference = "Stop"
$adb = Join-Path $AndroidSdk "platform-tools\adb.exe"
$output = Join-Path $env:TEMP "mpvnova-synthetic-benchmark.mp4"

& $Ffmpeg `
    -hide_banner -loglevel error `
    -f lavfi -i "testsrc2=size=1280x720:rate=30" `
    -f lavfi -i "sine=frequency=440:sample_rate=48000" `
    -t 60 `
    -c:v libx264 -preset ultrafast -crf 20 -g 60 -pix_fmt yuv420p `
    -c:a aac -b:a 128k -movflags +faststart `
    -y $output

if ($LASTEXITCODE -ne 0) {
    throw "FFmpeg failed to create benchmark media."
}

& $adb -s $Serial push $output /sdcard/Download/mpvnova-benchmark.mp4
if ($LASTEXITCODE -ne 0) {
    throw "Failed to push benchmark media to $Serial."
}

Write-Output "/sdcard/Download/mpvnova-benchmark.mp4"
