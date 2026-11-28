# Runs the real particle mesh builders without opening Minecraft or loading a world.
# Works in Windows PowerShell 5.1 and PowerShell 7.
$ErrorActionPreference = 'Stop'

# Runs a native tool and leaves the verdict to its exit code. When output is redirected, Windows PowerShell 5.1
# turns every line a tool writes to stderr (javac's warnings) into an error, and 'Stop' would end the script there.
# The tool is looked up first, still under 'Stop', so a missing one fails rather than leaving the last exit code.
function Invoke-EffectTool([string]$Tool, [string[]]$Arguments) {
    $command = Get-Command $Tool
    $ErrorActionPreference = 'Continue'
    & $command @Arguments
}

$effectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $effectRoot
try {
    Invoke-EffectTool (Join-Path $effectRoot 'gradlew.bat') @('compileJava', 'writeClientLegacyClasspath')
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the effect code' }

    $effectCheckDir = Join-Path $effectRoot 'build/effect-geometry-check'
    New-Item -ItemType Directory -Force $effectCheckDir | Out-Null
    $effectClasspath = (Get-Content 'build/moddev/clientLegacyClasspath.txt' -Raw).Trim() -replace '\r?\n', ';'
    $effectClasspath += ';' + (Join-Path $effectRoot 'build/classes/java/main')
    $effectClasspath += ';' + (Join-Path $effectRoot 'build/moddev/artifacts/neoforge-21.1.242.jar')
    $effectArgs = @('-cp', ('"' + $effectClasspath.Replace('\', '/') + '"'),
        ('"' + (Join-Path $PSScriptRoot 'EffectGeometryCheck.java').Replace('\', '/') + '"')) -join "`n"
    # java reads a byte-order mark as part of the first argument. Windows PowerShell 5.1 has no utf8NoBOM
    # encoding and its utf8 writes one, so write the file through .NET, which does the same in 5.1 and 7.
    [System.IO.File]::WriteAllText((Join-Path $effectCheckDir 'args.txt'), $effectArgs + "`n",
        (New-Object System.Text.UTF8Encoding $false))
    $effectJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    Invoke-EffectTool $effectJava @('@' + (Join-Path $effectCheckDir 'args.txt'))
    if ($LASTEXITCODE -ne 0) { throw 'Effect geometry verification failed' }
} finally {
    Pop-Location
}
