"""Prepare the user's chosen short Canary Cry excerpt without vocal synthesis.

Requires numpy, soundfile and FFmpeg (or imageio-ffmpeg).
Supply the reference media locally with --source. Nothing is stretched, pitched,
layered or looped; only a short crop, constant gain and edge fades are applied.
"""
from pathlib import Path
import argparse
import io
import json
import shutil
import subprocess
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/heroesevolved/sounds/ability"
CACHE = ROOT / "build/sonic-audio"
REFERENCE = "https://www.youtube.com/watch?v=UviuiYaAGQA&t=100"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source", type=Path, default=ROOT / "build/canary-reference.webm")
args = parser.parse_args()
if not args.source.is_file():
    parser.error("Supply the user-selected reference media with --source FILE.")
ffmpeg = shutil.which("ffmpeg")
if not ffmpeg:
    import imageio_ffmpeg
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
RATE = 48000
START = 100.55
DURATION = 5.60
OUT.mkdir(parents=True, exist_ok=True)
CACHE.mkdir(parents=True, exist_ok=True)
command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-ss", str(START),
           "-i", str(args.source), "-t", str(DURATION), "-ac", "1", "-ar", str(RATE),
           "-f", "f32le", "pipe:1"]
raw = subprocess.run(command, capture_output=True, check=True).stdout
excerpt = np.frombuffer(raw, dtype="<f4").astype(np.float64)
assert len(excerpt) >= int((DURATION - .02) * RATE) and np.all(np.isfinite(excerpt))
# One constant gain keeps the natural dynamics and original timbre intact.
excerpt *= .80 / max(np.max(np.abs(excerpt)), 1e-9)


def edge_fades(x, out_seconds=.07):
    x = x.copy()
    n = min(int(.01 * RATE), len(x) // 4)
    m = min(int(out_seconds * RATE), len(x) // 4)
    x[:n] *= np.linspace(0, 1, n)
    x[-m:] *= np.linspace(1, 0, m)
    return x


def write(name, seconds):
    x = edge_fades(excerpt[:round(seconds * RATE)])
    buffer = io.BytesIO()
    sf.write(buffer, x, RATE, format="OGG", subtype="VORBIS")
    data = buffer.getvalue()
    decoded, sample_rate = sf.read(io.BytesIO(data))
    peak = float(np.max(np.abs(decoded)))
    assert sample_rate == RATE and decoded.ndim == 1 and peak < .98
    (OUT / (name + ".ogg")).write_bytes(data)
    print(f"{name}: original-speed reference excerpt, {len(x) / RATE:.2f}s, peak {peak:.3f}")
    return decoded


write("sonic_cry", 1.55)
write("sonic_burst", .88)
held = write("sonic_scream", DURATION)
preview = edge_fades(held[:RATE * 5] * .75, .10)
sf.write(CACHE / "canary-scream-reference-preview.wav", preview, RATE, subtype="PCM_16")
manifest = {"source": REFERENCE, "source_title": "Black Canary (Dinah Lance) Canary Cry Evolution",
            "start_seconds": START, "end_seconds": START + DURATION,
            "processing": ["mono conversion", "constant gain", "short edge fades", "Vorbis encoding"],
            "sample_rate": RATE, "pitch_ratio": 1, "speed_ratio": 1, "looping": False}
(CACHE / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
