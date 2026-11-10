# Decodes a compressed audio file (MP3, AAC, WMA...) to 16-bit PCM WAV with Windows' own Media
# Foundation, for tools that only read WAV (tools/prepare-panic-voices.py). Keeps the source's
# sample rate and channels.
#
#   powershell -File tools/decode-audio.ps1 build/intro-audio-sources/crowd-in-panic-hq.mp3
#
# Writes the WAV next to the input, with the same name.
param([Parameter(Mandatory = $true)][string]$Path)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$methods = [System.WindowsRuntimeSystemExtensions].GetMethods()
$asTask = ($methods | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
$asTaskWithProgress = ($methods | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncActionWithProgress`1' })[0]
function Await($operation, [Type]$type) { $task = $asTask.MakeGenericMethod($type).Invoke($null, @($operation)); $task.Wait(-1) | Out-Null; $task.Result }
function AwaitAction($operation) { $task = $asTaskWithProgress.MakeGenericMethod([double]).Invoke($null, @($operation)); $task.Wait(-1) | Out-Null }
[Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.StorageFolder, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Transcoding.MediaTranscoder, Windows.Media.Transcoding, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.MediaProperties.MediaEncodingProfile, Windows.Media.MediaProperties, ContentType = WindowsRuntime] | Out-Null

$source = Resolve-Path $Path
$sourceFile = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($source.Path)) ([Windows.Storage.StorageFile])
$probe = Await ([Windows.Media.MediaProperties.MediaEncodingProfile]::CreateFromFileAsync($sourceFile)) ([Windows.Media.MediaProperties.MediaEncodingProfile])
$folder = Await ([Windows.Storage.StorageFolder]::GetFolderFromPathAsync((Split-Path $source.Path))) ([Windows.Storage.StorageFolder])
$name = [System.IO.Path]::GetFileNameWithoutExtension($source.Path) + '.wav'
$output = Await ($folder.CreateFileAsync($name, [Windows.Storage.CreationCollisionOption]::ReplaceExisting)) ([Windows.Storage.StorageFile])

# The WAV encoder only accepts the source's own rate and channel count.
$wavProfile = [Windows.Media.MediaProperties.MediaEncodingProfile]::CreateWav([Windows.Media.MediaProperties.AudioEncodingQuality]::High)
$wavProfile.Audio.SampleRate = $probe.Audio.SampleRate
$wavProfile.Audio.ChannelCount = $probe.Audio.ChannelCount
$wavProfile.Audio.BitsPerSample = 16
$wavProfile.Audio.Bitrate = $probe.Audio.SampleRate * $probe.Audio.ChannelCount * 16

$transcoder = New-Object Windows.Media.Transcoding.MediaTranscoder
$transcoder.HardwareAccelerationEnabled = $false
$prepared = Await ($transcoder.PrepareFileTranscodeAsync($sourceFile, $output, $wavProfile)) ([Windows.Media.Transcoding.PrepareTranscodeResult])
if (-not $prepared.CanTranscode) { throw "Windows cannot decode $($source.Path): $($prepared.FailureReason)" }
AwaitAction ($prepared.TranscodeAsync())
"Wrote $(Join-Path (Split-Path $source.Path) $name) ($($probe.Audio.SampleRate) Hz, $($probe.Audio.ChannelCount) channels)"
