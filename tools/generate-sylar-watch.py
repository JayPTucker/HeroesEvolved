"""Synthesize an original, quiet mechanical tick-tock cue. No sampled or downloaded audio."""
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'build/audio-tools'))
import numpy as np
import soundfile as sf

rate = 44100
audio = np.zeros(rate, dtype=np.float64)
rng = np.random.default_rng(1731)
for start, pitch in [(0.02, 3100), (0.50, 2300)]:
    t = np.arange(int(rate * .12)) / rate
    attack = 1 - np.exp(-t * 14000)
    tick = attack * (np.sin(t * pitch * 2 * np.pi) * np.exp(-t * 180)
                    + .34 * np.sin(t * pitch * 1.43 * 2 * np.pi) * np.exp(-t * 110)
                    + .22 * rng.standard_normal(len(t)) * np.exp(-t * 430))
    begin = int(start * rate)
    audio[begin:begin+len(tick)] += tick
audio *= .7 / max(np.max(np.abs(audio)), .001)
assert np.all(np.isfinite(audio))
target = root / 'src/main/resources/assets/heroesevolved/sounds/story/sylar_watch.ogg'
target.parent.mkdir(parents=True, exist_ok=True)
sf.write(target, audio, rate, format='OGG', subtype='VORBIS')
decoded, decoded_rate = sf.read(target)
assert decoded_rate == rate and np.all(np.isfinite(decoded)) and np.max(np.abs(decoded)) < .95
print(f'Generated original tick-tock: {len(decoded) / rate:.1f}s mono, peak {np.max(np.abs(decoded)):.3f}')
