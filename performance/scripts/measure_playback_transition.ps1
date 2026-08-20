param(
    [string] $AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string] $Serial = "emulator-5554",
    [int] $Iterations = 10,
    [string] $Output = ""
)

$ErrorActionPreference = "Stop"
$adb = Join-Path $AndroidSdk "platform-tools\adb.exe"
$package = "app.mpvnova.player"
$activity = "$package/.MPVActivity"
$mediaUri = "file:///sdcard/Download/mpvnova-benchmark.mp4"
$queryAction = "$package.BENCHMARK_QUERY_PLAYBACK"
$receiver = "$package/.BenchmarkProbeReceiver"

function Read-PlaybackStats {
    $response = (& $adb -s $Serial shell am broadcast -a $queryAction -n $receiver | Out-String)
    $match = [regex]::Match($response, 'data="(?<data>[^"]+)"')
    if (-not $match.Success) {
        throw "Benchmark playback probe did not respond: $response"
    }

    $stats = @{}
    foreach ($entry in $match.Groups['data'].Value.Split(',')) {
        $key, $value = $entry.Split('=', 2)
        $stats[$key] = $value
    }
    return $stats
}

function Read-Count([hashtable] $Stats, [string] $Key) {
    $value = $Stats[$Key]
    if ($null -eq $value -or $value -eq "null") { return 0 }
    return [int] $value
}

& $adb -s $Serial shell pm grant $package android.permission.READ_EXTERNAL_STORAGE | Out-Null
& $adb -s $Serial shell am force-stop $package
& $adb -s $Serial shell am start -W -a android.intent.action.VIEW -d $mediaUri -t video/mp4 -n $activity | Out-Null
Start-Sleep -Seconds 1
$startup = Read-PlaybackStats
Start-Sleep -Seconds 2

$samples = for ($iteration = 0; $iteration -lt $Iterations; $iteration++) {
    & $adb -s $Serial shell input keyevent 127
    Start-Sleep -Milliseconds 500
    $before = Read-PlaybackStats
    if ($before['pause'] -ne 'true') {
        throw "Playback did not pause before iteration $iteration."
    }

    & $adb -s $Serial shell input keyevent 126
    Start-Sleep -Seconds 1
    $after = Read-PlaybackStats
    if ($after['pause'] -ne 'false') {
        throw "Playback did not resume during iteration $iteration."
    }

    [ordered]@{
        iteration = $iteration
        decoderDrops = (Read-Count $after 'decoderDrops') - (Read-Count $before 'decoderDrops')
        voDrops = (Read-Count $after 'voDrops') - (Read-Count $before 'voDrops')
        mistimed = (Read-Count $after 'mistimed') - (Read-Count $before 'mistimed')
        delayed = (Read-Count $after 'delayed') - (Read-Count $before 'delayed')
    }
}

$result = [ordered]@{
    device = $Serial
    iterations = $Iterations
    startup = [ordered]@{
        decoderDrops = (Read-Count $startup 'decoderDrops')
        voDrops = (Read-Count $startup 'voDrops')
        mistimed = (Read-Count $startup 'mistimed')
        delayed = (Read-Count $startup 'delayed')
    }
    samples = $samples
}
$json = $result | ConvertTo-Json -Depth 5

if ($Output) {
    $json | Set-Content -LiteralPath $Output -Encoding utf8
}
$json
