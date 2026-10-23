"""Original reactor charge loop and skin-UV emission mask, generated without external media."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'build/audio-tools'))
import numpy as np
import soundfile as sf
from PIL import Image

rate = 44100
t = np.arange(rate * 4) / rate
# All frequencies and modulation cycles close at the four-second seam.
rumble = sum(np.sin(2 * np.pi * frequency * t) * amplitude for frequency, amplitude in
             [(45, .22), (60, .17), (90, .12), (120, .08), (300, .08), (450, .06), (600, .035)])
whine = np.sin(2 * np.pi * 780 * t + 1.7 * np.sin(2 * np.pi * 7 * t)) * .13
pulse = .78 + .14 * np.sin(2 * np.pi * 3 * t) + .08 * np.sin(2 * np.pi * 13 * t)
signal = np.tanh((rumble + whine) * pulse * 1.2) * .8
audio = ROOT / 'src/main/resources/assets/heroesevolved/sounds/ability/nuclear_charge.ogg'
sf.write(audio, signal.astype(np.float32), rate, format='OGG', subtype='VORBIS')
decoded, _ = sf.read(audio)
assert np.isfinite(decoded).all() and np.max(np.abs(decoded)) < .98

# Skin coordinates stay intact: only a separate additive heat layer is drawn.
y, x = np.mgrid[0:64, 0:64]
base = .30 + .10 * np.sin(x * .7 + y * .3) ** 2
veins = np.exp(-((np.sin(x * .65 + np.sin(y * .31)) / .16) ** 2)) * .5
glow = np.clip(base + veins, 0, 1)
pixels = np.zeros((64, 64, 4), dtype=np.uint8)
pixels[:, :, :3] = (glow[:, :, None] * 255).astype(np.uint8)
pixels[:, :, 3] = 255
# The face glows more gently, preserving its features; hands and chest burn hottest.
pixels[0:16, 0:32, :3] //= 2
for x0, y0, x1, y1 in [(20, 20, 28, 29), (44, 24, 52, 32), (36, 56, 44, 64)]:
    pixels[y0:y1, x0:x1, :3] = np.maximum(pixels[y0:y1, x0:x1, :3], 210)
texture = ROOT / 'src/main/resources/assets/heroesevolved/textures/entity/radiation_heat.png'
texture.parent.mkdir(parents=True, exist_ok=True)
Image.fromarray(pixels, 'RGBA').save(texture)
print(f'Charge: {len(decoded) / rate:.1f}s mono, peak {np.max(np.abs(decoded)):.3f}; heat mask: 64 x 64')
