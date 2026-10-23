# Runs the real particle mesh builders without opening Minecraft or loading a world.
$ErrorActionPreference = 'Stop'
$effectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $effectRoot
try {
    & (Join-Path $effectRoot 'gradlew.bat') compileJava writeClientLegacyClasspath
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the effect code' }

    $effectCheckDir = Join-Path $effectRoot 'build/effect-geometry-check'
    New-Item -ItemType Directory -Force $effectCheckDir | Out-Null
    $effectClasspath = (Get-Content 'build/moddev/clientLegacyClasspath.txt' -Raw).Trim() -replace '\r?\n', ';'
    $effectClasspath += ';' + (Join-Path $effectRoot 'build/classes/java/main')
    $effectClasspath += ';' + (Join-Path $effectRoot 'build/moddev/artifacts/neoforge-21.1.242.jar')
    @('-cp', ('"' + $effectClasspath.Replace('\', '/') + '"'),
        ('"' + (Join-Path $PSScriptRoot 'EffectGeometryCheck.java').Replace('\', '/') + '"')) |
        Set-Content -LiteralPath (Join-Path $effectCheckDir 'args.txt') -Encoding utf8NoBOM
    $effectJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    & $effectJava ('@' + (Join-Path $effectCheckDir 'args.txt'))
    if ($LASTEXITCODE -ne 0) { throw 'Effect geometry verification failed' }
} finally {
    Pop-Location
}
