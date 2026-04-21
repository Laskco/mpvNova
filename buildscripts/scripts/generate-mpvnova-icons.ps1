Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$resRoot = Join-Path $repoRoot "app\src\main\res"
$drawableRoot = Join-Path $resRoot "drawable-nodpi"
$fastlaneImageRoot = Join-Path $repoRoot "fastlane\metadata\android\en-US\images"
$buildRoot = Join-Path $repoRoot "build\icon-render"
$iconSizes = @{
    "mdpi" = 48
    "hdpi" = 72
    "xhdpi" = 96
    "xxhdpi" = 144
    "xxxhdpi" = 192
}
$tvBannerPath = Join-Path $resRoot "mipmap-xhdpi\ic_banner.png"
$storeIconPath = Join-Path $fastlaneImageRoot "icon.png"
$storeBannerPath = Join-Path $fastlaneImageRoot "tvBanner.png"

function Get-SourceSvgPath {
    $candidates = @(
        (Join-Path $env:USERPROFILE "Downloads\mpvNova_stacked_clean.svg"),
        (Join-Path $repoRoot "design\mpvNova_stacked_clean.svg")
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Unable to find mpvNova_stacked_clean.svg in Downloads or design\."
}

function Get-BrowserPath {
    $candidates = @(
        "C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe",
        "C:\Program Files\Google\Chrome\Application\chrome.exe",
        "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        "C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "Unable to find Brave, Chrome, or Edge for SVG rendering."
}

function New-RoundedRectPath {
    param(
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [float]$Radius
    )

    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $Radius * 2
    $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
    $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
    $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function Write-RenderHtml {
    param(
        [string]$SourceSvgPath,
        [string]$Destination,
        [string]$BackgroundHex
    )

    $svgUri = ([System.Uri](Resolve-Path $SourceSvgPath).Path).AbsoluteUri
    $html = @"
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<style>
html, body {
    margin: 0;
    width: 1024px;
    height: 1024px;
    overflow: hidden;
    background: #$BackgroundHex;
}
body {
    display: flex;
    align-items: center;
    justify-content: center;
}
img {
    width: 820px;
    height: auto;
    display: block;
}
</style>
</head>
<body>
<img src="$svgUri" alt="mpvNova icon source" />
</body>
</html>
"@

    Set-Content -Path $Destination -Value $html -Encoding UTF8
}

function Invoke-BrowserRender {
    param(
        [string]$BrowserPath,
        [string]$HtmlPath,
        [string]$OutputPath
    )

    $htmlUri = ([System.Uri](Resolve-Path $HtmlPath).Path).AbsoluteUri
    & $BrowserPath `
        "--headless" `
        "--disable-gpu" `
        "--hide-scrollbars" `
        "--allow-file-access-from-files" `
        "--run-all-compositor-stages-before-draw" `
        "--virtual-time-budget=4000" `
        "--window-size=1024,1024" `
        "--screenshot=$OutputPath" `
        $htmlUri | Out-Null

    if (-not (Test-Path $OutputPath)) {
        throw "Browser render failed to create $OutputPath"
    }
}

function New-AlphaBitmap {
    param(
        [string]$BlackRenderPath,
        [string]$WhiteRenderPath
    )

    $blackBitmap = [System.Drawing.Bitmap]::new($BlackRenderPath)
    $whiteBitmap = [System.Drawing.Bitmap]::new($WhiteRenderPath)
    $result = [System.Drawing.Bitmap]::new($blackBitmap.Width, $blackBitmap.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

    for ($y = 0; $y -lt $blackBitmap.Height; $y++) {
        for ($x = 0; $x -lt $blackBitmap.Width; $x++) {
            $black = $blackBitmap.GetPixel($x, $y)
            $white = $whiteBitmap.GetPixel($x, $y)

            $alphaR = 255 - ($white.R - $black.R)
            $alphaG = 255 - ($white.G - $black.G)
            $alphaB = 255 - ($white.B - $black.B)
            $alpha = [int][Math]::Round(([Math]::Max(0, [Math]::Min(255, $alphaR)) + [Math]::Max(0, [Math]::Min(255, $alphaG)) + [Math]::Max(0, [Math]::Min(255, $alphaB))) / 3.0)

            if ($alpha -le 0) {
                $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                continue
            }

            $red = [int][Math]::Round([Math]::Min(255.0, $black.R * 255.0 / $alpha))
            $green = [int][Math]::Round([Math]::Min(255.0, $black.G * 255.0 / $alpha))
            $blue = [int][Math]::Round([Math]::Min(255.0, $black.B * 255.0 / $alpha))
            $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($alpha, $red, $green, $blue))
        }
    }

    $blackBitmap.Dispose()
    $whiteBitmap.Dispose()
    return $result
}

function New-MonochromeBitmap {
    param(
        [System.Drawing.Bitmap]$SourceBitmap
    )

    $result = [System.Drawing.Bitmap]::new($SourceBitmap.Width, $SourceBitmap.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $SourceBitmap.Height; $y++) {
        for ($x = 0; $x -lt $SourceBitmap.Width; $x++) {
            $pixel = $SourceBitmap.GetPixel($x, $y)
            if ($pixel.A -eq 0) {
                $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 255, 255, 255))
            } else {
                $result.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($pixel.A, 255, 255, 255))
            }
        }
    }

    return $result
}

function New-IconBitmap {
    param(
        [System.Drawing.Bitmap]$ForegroundBitmap
    )

    $bitmap = [System.Drawing.Bitmap]::new(1024, 1024, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.Clear([System.Drawing.Color]::Transparent)

    $bgPath = New-RoundedRectPath -X 64 -Y 64 -Width 896 -Height 896 -Radius 220
    $bgBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 8, 22, 36))
    $highlightBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.RectangleF]::new(64, 64, 896, 896),
        [System.Drawing.Color]::FromArgb(50, 98, 215, 255),
        [System.Drawing.Color]::FromArgb(0, 98, 215, 255),
        135.0
    )
    $borderPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(56, 196, 231, 255), 10)

    $graphics.FillPath($bgBrush, $bgPath)
    $graphics.FillPath($highlightBrush, $bgPath)
    $graphics.DrawPath($borderPen, $bgPath)
    $graphics.DrawImage($ForegroundBitmap, 0, 0, 1024, 1024)

    $bgBrush.Dispose()
    $highlightBrush.Dispose()
    $borderPen.Dispose()
    $bgPath.Dispose()
    $graphics.Dispose()
    return $bitmap
}

function Get-VisibleBounds {
    param(
        [System.Drawing.Bitmap]$Bitmap
    )

    $minX = $Bitmap.Width
    $minY = $Bitmap.Height
    $maxX = -1
    $maxY = -1

    for ($y = 0; $y -lt $Bitmap.Height; $y++) {
        for ($x = 0; $x -lt $Bitmap.Width; $x++) {
            if ($Bitmap.GetPixel($x, $y).A -le 8) {
                continue
            }

            if ($x -lt $minX) { $minX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }

    if ($maxX -lt $minX -or $maxY -lt $minY) {
        return [System.Drawing.Rectangle]::new(0, 0, $Bitmap.Width, $Bitmap.Height)
    }

    return [System.Drawing.Rectangle]::new($minX, $minY, ($maxX - $minX + 1), ($maxY - $minY + 1))
}

function New-BannerBitmap {
    param(
        [System.Drawing.Bitmap]$ForegroundBitmap,
        [int]$Width = 320,
        [int]$Height = 180,
        [double]$HorizontalPadding = 52.0,
        [double]$VerticalPadding = 34.0
    )

    $bitmap = [System.Drawing.Bitmap]::new($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.Clear([System.Drawing.Color]::Transparent)

    $visibleBounds = Get-VisibleBounds -Bitmap $ForegroundBitmap
    $maxWidth = $Width - ($HorizontalPadding * 2.0)
    $maxHeight = $Height - ($VerticalPadding * 2.0)
    $scale = [Math]::Min($maxWidth / $visibleBounds.Width, $maxHeight / $visibleBounds.Height)
    $targetWidth = [int][Math]::Round($visibleBounds.Width * $scale)
    $targetHeight = [int][Math]::Round($visibleBounds.Height * $scale)
    $x = [int][Math]::Round(($Width - $targetWidth) / 2.0)
    $y = [int][Math]::Round(($Height - $targetHeight) / 2.0)
    $destination = [System.Drawing.Rectangle]::new($x, $y, $targetWidth, $targetHeight)

    $graphics.DrawImage(
        $ForegroundBitmap,
        $destination,
        $visibleBounds.X,
        $visibleBounds.Y,
        $visibleBounds.Width,
        $visibleBounds.Height,
        [System.Drawing.GraphicsUnit]::Pixel
    )

    $graphics.Dispose()
    return $bitmap
}

function Save-ScaledBitmap {
    param(
        [System.Drawing.Bitmap]$SourceBitmap,
        [int]$Size,
        [string]$Destination
    )

    $target = [System.Drawing.Bitmap]::new($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.DrawImage($SourceBitmap, 0, 0, $Size, $Size)
    $graphics.Dispose()

    $directory = Split-Path -Parent $Destination
    if (-not (Test-Path $directory)) {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }

    $target.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    $target.Dispose()
}

if (-not (Test-Path $buildRoot)) {
    New-Item -ItemType Directory -Path $buildRoot | Out-Null
}

$sourceSvgPath = Get-SourceSvgPath
$browserPath = Get-BrowserPath
$blackHtmlPath = Join-Path $buildRoot "mpvNova-render-black.html"
$whiteHtmlPath = Join-Path $buildRoot "mpvNova-render-white.html"
$blackPngPath = Join-Path $buildRoot "mpvNova-render-black.png"
$whitePngPath = Join-Path $buildRoot "mpvNova-render-white.png"

Write-RenderHtml -SourceSvgPath $sourceSvgPath -Destination $blackHtmlPath -BackgroundHex "000000"
Write-RenderHtml -SourceSvgPath $sourceSvgPath -Destination $whiteHtmlPath -BackgroundHex "ffffff"
Invoke-BrowserRender -BrowserPath $browserPath -HtmlPath $blackHtmlPath -OutputPath $blackPngPath
Invoke-BrowserRender -BrowserPath $browserPath -HtmlPath $whiteHtmlPath -OutputPath $whitePngPath

$foregroundBitmap = New-AlphaBitmap -BlackRenderPath $blackPngPath -WhiteRenderPath $whitePngPath
$monochromeBitmap = New-MonochromeBitmap -SourceBitmap $foregroundBitmap
$iconBitmap = New-IconBitmap -ForegroundBitmap $foregroundBitmap
$bannerBitmap = New-BannerBitmap -ForegroundBitmap $foregroundBitmap
$storeBannerBitmap = New-BannerBitmap -ForegroundBitmap $foregroundBitmap -Width 1280 -Height 720 -HorizontalPadding 208.0 -VerticalPadding 136.0

foreach ($density in $iconSizes.Keys) {
    $size = $iconSizes[$density]
    Save-ScaledBitmap -SourceBitmap $iconBitmap -Size $size -Destination (Join-Path $resRoot "mipmap-$density\mpv_launcher_icon.png")
}

$drawableSize = 768
Save-ScaledBitmap -SourceBitmap $foregroundBitmap -Size $drawableSize -Destination (Join-Path $drawableRoot "mpv_logo.png")
Save-ScaledBitmap -SourceBitmap $monochromeBitmap -Size $drawableSize -Destination (Join-Path $drawableRoot "mpv_monochrome.png")
$bannerBitmap.Save($tvBannerPath, [System.Drawing.Imaging.ImageFormat]::Png)
Save-ScaledBitmap -SourceBitmap $iconBitmap -Size 512 -Destination $storeIconPath
$storeBannerBitmap.Save($storeBannerPath, [System.Drawing.Imaging.ImageFormat]::Png)

$foregroundBitmap.Dispose()
$monochromeBitmap.Dispose()
$iconBitmap.Dispose()
$bannerBitmap.Dispose()
$storeBannerBitmap.Dispose()
