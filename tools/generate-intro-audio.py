"""Build intro audio: original siren/blast and edited CC0 human panic recordings.

Requires numpy and soundfile. The source recording is cached under build/;
see the shipped CREDITS.txt for its author, license, and processing details.
"""
from pathlib import Path
from urllib.request import Request, urlopen
import sys

# Allow the build-local dependency folder used by the repo's validation workflow.
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'build' / 'audio-tools'))
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/heroesevolved/sounds/intro'
OUT.mkdir(parents=True, exist_ok=True)
RATE = 44100


def write(name, signal, peak=None):
    signal = np.tanh(signal) * 0.82
    if peak is not None:
        signal *= peak / max(np.max(np.abs(signal)), 1e-9)
    path = OUT / f'{name}.ogg'
    # High-quality encoding matters for the blast's dense bass and sharp transients.
    quality = 0.05 if name == 'nuclear_blast' else None
    for attempt in range(4):
        sf.write(path, signal.astype(np.float32), RATE, format='OGG', subtype='VORBIS', compression_level=quality)
        decoded, _ = sf.read(path)
        decoded_peak = np.max(np.abs(decoded))
        if decoded_peak < 0.98:
            break
        signal *= 0.94 / decoded_peak
    else:
        raise ValueError(f'Could not encode {name} without clipped reconstructed peaks')


# Mechanical civil-defence rotor: rising/falling harmonics with spinning amplitude modulation.
t = np.arange(RATE * 8) / RATE
phase = 2 * np.pi * (460 * t - 150 * 8 / (2 * np.pi) * np.sin(2 * np.pi * t / 8))
rotor = sum(np.sin(phase * harmonic) / harmonic ** 1.3 for harmonic in range(1, 9))
signal = rotor * (0.78 + 0.15 * np.sin(2 * np.pi * 23 * t))
write('nuclear_siren', signal * 0.7)

# Real performers: retain their natural formants, breaths, and overlapping reactions.
source = ROOT / 'build/intro-audio-sources/crowd-in-panic-hq.mp3'
if not source.exists():
    source.parent.mkdir(parents=True, exist_ok=True)
    request = Request('https://cdn.freesound.org/previews/491/491405_9616576-hq.mp3',
                      headers={'User-Agent': 'HeroesEvolved audio asset build'})
    source.write_bytes(urlopen(request, timeout=60).read())
recording, source_rate = sf.read(source, always_2d=True)
recording = recording.mean(axis=1)
for name, start, end in [('crowd_panic', 2.4, 5.4),
                         ('crowd_panic_2', 12.5, 15.5),
                         ('crowd_panic_3', 24.8, 28.0)]:
    clip = recording[int(start * source_rate):int(end * source_rate)]
    clip = np.interp(np.arange(int((end - start) * RATE)) / RATE,
                     np.arange(len(clip)) / source_rate, clip)
    clip -= clip.mean()
    spectrum = np.fft.rfft(clip)
    frequencies = np.fft.rfftfreq(len(clip), 1 / RATE)
    spectrum *= (1 - np.exp(-(frequencies / 85) ** 4)) * np.exp(-(frequencies / 12000) ** 6)
    clip = np.fft.irfft(spectrum, n=len(clip))
    clip *= 0.85 / max(np.max(np.abs(clip)), 1e-9)
    fade_in, fade_out = int(RATE * 0.04), int(RATE * 0.16)
    clip[:fade_in] *= np.linspace(0, 1, fade_in)
    clip[-fade_out:] *= np.linspace(1, 0, fade_out)
    write(name, clip, 0.88)

# Layered blast: a sharp pressure crack, audible bass, turbulent roar, and secondary city echoes.
rng = np.random.default_rng(52177)
t = np.arange(RATE * 6) / RATE


def noise_band(low, high, slope=0):
    spectrum = np.fft.rfft(rng.normal(size=len(t)))
    frequencies = np.fft.rfftfreq(len(t), 1 / RATE)
    spectrum *= ((frequencies >= low) & (frequencies <= high)) / np.maximum(frequencies, 1) ** slope
    band = np.fft.irfft(spectrum, n=len(t))
    return band / np.sqrt(np.mean(band ** 2))


crack = noise_band(300, 11000)
roar = noise_band(70, 2500, 0.55)
rumble = noise_band(30, 220, 0.35)
attack = 1 - np.exp(-t / 0.003)
blast = crack * attack * np.exp(-t / 0.13) * 1.4
blast += roar * (1 - np.exp(-t / 0.025)) * np.exp(-t / 1.8) * 0.85
blast += rumble * (1 - np.exp(-t / 0.05)) * np.exp(-t / 2.7) * 0.8
blast += np.sin(2 * np.pi * (29 * t + 80 * (1 - np.exp(-t / 1.6)))) * np.exp(-t / 1.9) * 0.55
for delay, gain in [(0.65, 0.48), (1.35, 0.32), (2.6, 0.20), (3.7, 0.12)]:
    shifted = np.maximum(t - delay, 0)
    blast += roar * (t >= delay) * (1 - np.exp(-shifted / 0.015)) * np.exp(-shifted / 0.48) * gain
blast[-int(RATE * 0.4):] *= np.linspace(1, 0, int(RATE * 0.4))
# Leave codec headroom: Vorbis reconstruction can overshoot the input peaks.
write('nuclear_blast', blast * 1.25, 0.88)
print('Generated looping siren, three recorded human panic variations, and layered nuclear blast.')
