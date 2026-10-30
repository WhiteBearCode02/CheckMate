$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

# Deterministic conversion only: preserve the approved source artwork and all its elements.
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourcePath = [IO.Path]::GetFullPath((Join-Path $projectPath '../docs/assets/checkmate-icon.png'))
$resourcePath = Join-Path $projectPath 'app/src/main/res'
$previewPath = Join-Path $projectPath 'build/branding-review'
$utf8 = [Text.UTF8Encoding]::new($false)
$sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
$source = [Drawing.Bitmap]::new($sourcePath)
if ($source.Width -ne $source.Height) { $source.Dispose(); throw 'The approved icon must be square.' }

function New-IconPath([int]$Size, [string]$Shape) {
    $path = [Drawing.Drawing2D.GraphicsPath]::new()
    if ($Shape -eq 'Circle') {
        $path.AddEllipse(0, 0, $Size, $Size)
    } elseif ($Shape -eq 'Square') {
        $path.AddRectangle([Drawing.Rectangle]::new(0, 0, $Size, $Size))
    } else {
        $diameter = [single]($Size * 0.44)
        $path.AddArc(0, 0, $diameter, $diameter, 180, 90)
        $path.AddArc(($Size - $diameter), 0, $diameter, $diameter, 270, 90)
        $path.AddArc(($Size - $diameter), ($Size - $diameter), $diameter, $diameter, 0, 90)
        $path.AddArc(0, ($Size - $diameter), $diameter, $diameter, 90, 90)
        $path.CloseFigure()
    }
    return $path
}

function New-IconBitmap([int]$Size, [string]$Shape, [bool]$AdaptiveLayer = $false) {
    $bitmap = [Drawing.Bitmap]::new($Size, $Size, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $mask = $null
    try {
        $graphics.Clear([Drawing.Color]::Transparent)
        $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        if ($AdaptiveLayer) {
            # Android layers: 108dp; artwork: 64dp centered inside the 66dp safe area.
            $artSize = [single]($Size * 64.0 / 108.0)
        } else {
            # A launcher mask exposes the central 72dp viewport of the 108dp layers.
            $mask = New-IconPath $Size $Shape
            $graphics.SetClip($mask)
            $graphics.FillRectangle([Drawing.Brushes]::White, 0, 0, $Size, $Size)
            $artSize = [single]($Size * 64.0 / 72.0)
        }
        $offset = [single](($Size - $artSize) / 2.0)
        $target = [Drawing.RectangleF]::new($offset, $offset, $artSize, $artSize)
        $graphics.DrawImage($source, $target)
    } finally {
        if ($null -ne $mask) { $mask.Dispose() }
        $graphics.Dispose()
    }
    return $bitmap
}

function Save-Icon([Drawing.Bitmap]$Bitmap, [string]$Path) {
    try {
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path)) | Out-Null
        $Bitmap.Save($Path, [Drawing.Imaging.ImageFormat]::Png)
    } finally { $Bitmap.Dispose() }
}

try {
    Save-Icon (New-IconBitmap 432 'Square' $true) (Join-Path $resourcePath 'drawable-nodpi/checkmate_launcher_foreground.png')
    $densities = [ordered]@{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
    foreach ($density in $densities.GetEnumerator()) {
        $directory = Join-Path $resourcePath ('mipmap-' + $density.Key)
        Save-Icon (New-IconBitmap $density.Value 'RoundedSquare') (Join-Path $directory 'ic_launcher.png')
        Save-Icon (New-IconBitmap $density.Value 'Circle') (Join-Path $directory 'ic_launcher_round.png')
        # Remove only the two known default files in this density; never recurse or touch the source.
        foreach ($filename in @('ic_launcher.webp', 'ic_launcher_round.webp')) {
            $legacyPath = [IO.Path]::GetFullPath((Join-Path $directory $filename))
            if (-not $legacyPath.StartsWith($resourcePath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
                throw 'Resource cleanup escaped the intended resource directory.'
            }
            if (Test-Path -LiteralPath $legacyPath) { Remove-Item -LiteralPath $legacyPath }
        }
    }

    $adaptiveXml = @'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'@
    $adaptiveDirectory = Join-Path $resourcePath 'mipmap-anydpi-v26'
    [IO.Directory]::CreateDirectory($adaptiveDirectory) | Out-Null
    foreach ($filename in @('ic_launcher.xml', 'ic_launcher_round.xml')) {
        [IO.File]::WriteAllText((Join-Path $adaptiveDirectory $filename), $adaptiveXml + [Environment]::NewLine, $utf8)
        $oldXml = Join-Path $resourcePath ('mipmap-anydpi/' + $filename)
        if (Test-Path -LiteralPath $oldXml) { Remove-Item -LiteralPath $oldXml }
    }
    $foregroundXml = @'
<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/checkmate_launcher_foreground"
    android:gravity="fill"
    android:filter="true" />
'@
    $backgroundXml = @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#FFFFFF" />
</shape>
'@
    [IO.File]::WriteAllText((Join-Path $resourcePath 'drawable/ic_launcher_foreground.xml'), $foregroundXml + [Environment]::NewLine, $utf8)
    [IO.File]::WriteAllText((Join-Path $resourcePath 'drawable/ic_launcher_background.xml'), $backgroundXml + [Environment]::NewLine, $utf8)

    [IO.Directory]::CreateDirectory($previewPath) | Out-Null
    $sheet = [Drawing.Bitmap]::new(960, 420)
    $sheetGraphics = [Drawing.Graphics]::FromImage($sheet)
    $font = [Drawing.Font]::new('Segoe UI', 14)
    try {
        $sheetGraphics.Clear([Drawing.Color]::FromArgb(240, 243, 247))
        $shapes = @('Circle', 'RoundedSquare', 'Square')
        for ($i = 0; $i -lt $shapes.Count; $i++) {
            $sheetGraphics.DrawString($shapes[$i], $font, [Drawing.Brushes]::Black, ($i * 320 + 28), 12)
            $preview = New-IconBitmap 264 $shapes[$i]
            try { $sheetGraphics.DrawImageUnscaled($preview, ($i * 320 + 28), 45) } finally { $preview.Dispose() }
            $small = New-IconBitmap 48 $shapes[$i]
            try { $sheetGraphics.DrawImageUnscaled($small, ($i * 320 + 64), 345) } finally { $small.Dispose() }
            $sheetGraphics.DrawString('48px', $font, [Drawing.Brushes]::Black, ($i * 320 + 130), 354)
        }
        $sheet.Save((Join-Path $previewPath 'launcher-mask-preview.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $font.Dispose()
        $sheetGraphics.Dispose()
        $sheet.Dispose()
    }
} finally { $source.Dispose() }

if ((Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash -ne $sourceHash) {
    throw 'The approved original changed unexpectedly.'
}
Write-Output ('Original preserved: ' + $sourceHash)
Write-Output ('Launcher resources generated; preview: ' + (Join-Path $previewPath 'launcher-mask-preview.png'))
