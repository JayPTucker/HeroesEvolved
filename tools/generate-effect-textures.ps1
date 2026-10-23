# Procedural, original particle sprites. Run from any directory to regenerate.
Add-Type -AssemblyName System.Drawing
$effectAssets = Join-Path $PSScriptRoot '../src/main/resources/assets/heroesevolved'
$effectTextures = Join-Path $effectAssets 'textures/particle'
$effectDefinitions = Join-Path $effectAssets 'particles'
New-Item -ItemType Directory -Force $effectTextures, $effectDefinitions | Out-Null

foreach ($effectName in @('pressure_ring', 'energy_streak', 'power_mote')) {
    $effectSize = if ($effectName -eq 'pressure_ring') { 128 } else { 32 }
    $effectBitmap = [System.Drawing.Bitmap]::new($effectSize, $effectSize)
    try {
        for ($effectY = 0; $effectY -lt $effectSize; $effectY++) {
            for ($effectX = 0; $effectX -lt $effectSize; $effectX++) {
                $effectU = ($effectX + 0.5) / $effectSize * 2 - 1
                $effectV = ($effectY + 0.5) / $effectSize * 2 - 1
                $effectRadius = [Math]::Sqrt($effectU * $effectU + $effectV * $effectV)
                $effectAlpha = switch ($effectName) {
                    'pressure_ring' {
                        # A crisp pressure front with a softer inner echo.
                        [Math]::Exp(-[Math]::Pow(($effectRadius - 0.87) / 0.018, 2)) * (0.72 + 0.23 * [Math]::Sin([Math]::Atan2($effectV, $effectU) * 17) * [Math]::Sin([Math]::Atan2($effectV, $effectU) * 7)) +
                            [Math]::Exp(-[Math]::Pow(($effectRadius - 0.82) / 0.055, 2)) * 0.18
                    }
                    'energy_streak' {
                        # Feathered ribbon with a bright central filament.
                        [Math]::Exp(-$effectV * $effectV * 12) *
                            [Math]::Min(1, (1 - [Math]::Abs($effectU)) * 5)
                    }
                    'power_mote' {
                        [Math]::Exp(-$effectRadius * $effectRadius * 10) * 0.8 +
                            [Math]::Exp(-[Math]::Abs($effectU * $effectV) * 120) *
                            [Math]::Exp(-$effectRadius * $effectRadius * 7) * 0.18
                    }
                }
                $effectByte = [int]([Math]::Clamp($effectAlpha, 0.0, 1.0) * 255)
                $effectBitmap.SetPixel($effectX, $effectY,
                    [System.Drawing.Color]::FromArgb($effectByte, 255, 255, 255))
            }
        }
        $effectBitmap.Save((Join-Path $effectTextures "$effectName.png"),
            [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $effectBitmap.Dispose()
    }
    @{ textures = @("heroesevolved:$effectName") } | ConvertTo-Json |
        Set-Content -LiteralPath (Join-Path $effectDefinitions "$effectName.json") -Encoding utf8NoBOM
}
