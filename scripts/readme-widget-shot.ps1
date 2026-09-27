# Captures the home-screen widget from a connected phone into
# docs/screenshot-widget.png — the README's hero image. Part of the release
# checklist in docs/DEVELOPMENT.md, so the README always shows the widget as
# the release actually draws it.
#
#   powershell -ExecutionPolicy Bypass -File scripts/readme-widget-shot.ps1
#
# Needs adb and a phone with the release app's widget on the home screen. The
# widget is refreshed first, then found by its view id in the UI dump, so no
# coordinates are hard-coded.
param(
    [string]$Package = "dev.pi0trdotsys.homebrewweather",
    [string]$Out = (Join-Path $PSScriptRoot "..\docs\screenshot-widget.png")
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) "hbw-shot"
New-Item -ItemType Directory -Force $tmp | Out-Null

# Refresh every placed widget of the package and wait for the render.
$ids = (adb shell dumpsys appwidget) -join "`n" |
    Select-String -AllMatches -Pattern "id=(\d+)\s+host=[^\n]+\n\s+provider=ProviderId\{[^}]*cmp:ComponentInfo\{$([regex]::Escape($Package))/" |
    ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[1].Value }
if (-not $ids) { throw "No $Package widget on the home screen." }
foreach ($id in $ids) {
    adb shell am broadcast -a "$Package.ACTION_REFRESH" -n "$Package/$Package.widget.WeatherWidgetProvider" --ei appWidgetId $id | Out-Null
}
adb shell input keyevent KEYCODE_WAKEUP | Out-Null
adb shell input keyevent KEYCODE_HOME | Out-Null
Start-Sleep -Seconds 8

adb shell screencap -p /sdcard/hbw_shot.png | Out-Null
adb pull /sdcard/hbw_shot.png (Join-Path $tmp "screen.png") | Out-Null
adb shell uiautomator dump /sdcard/hbw_shot.xml | Out-Null
adb pull /sdcard/hbw_shot.xml (Join-Path $tmp "ui.xml") | Out-Null
adb shell rm -f /sdcard/hbw_shot.png /sdcard/hbw_shot.xml | Out-Null

[xml]$ui = Get-Content (Join-Path $tmp "ui.xml") -Encoding UTF8
$node = $ui.SelectNodes("//node[@resource-id='$($Package):id/widget_root']") | Select-Object -First 1
if (-not $node) { throw "Widget not visible on the current home screen page." }
if ($node.bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") { throw "Unreadable bounds: $($node.bounds)" }
$x1 = [int]$Matches[1]; $y1 = [int]$Matches[2]; $x2 = [int]$Matches[3]; $y2 = [int]$Matches[4]

$screen = [System.Drawing.Image]::FromFile((Join-Path $tmp "screen.png"))
try {
    $w = $x2 - $x1; $h = $y2 - $y1
    $crop = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($crop)
    $g.DrawImage($screen, (New-Object System.Drawing.Rectangle(0, 0, $w, $h)), (New-Object System.Drawing.Rectangle($x1, $y1, $w, $h)), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $crop.Save([System.IO.Path]::GetFullPath($Out), [System.Drawing.Imaging.ImageFormat]::Png)
    $crop.Dispose()
} finally {
    $screen.Dispose()
}
Write-Output "Saved ${w}x${h} widget to $([System.IO.Path]::GetFullPath($Out))"
