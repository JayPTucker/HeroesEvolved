"""Build the Sonic Resonance sounds from scratch: no recordings, only synthesis.

The power is Emma Coolidge (a cellist who sees sound as colour) and Black Canary (whose cry is a
weapon), so every sound has three layers:

* a shriek: a bright, formant-shaped harmonic tone, chorused from a few slightly detuned voices,
  with vibrato, that glides up into its pitch;
* a body: a bowed, cello-like drone under it (a root and a fifth), so the cry is a note, not noise;
* force and colour: a band-limited pressure blast and a low thump at the onset, and glassy high
  partials that ring out like light.

Everything is placed in a synthetic room (a decaying-noise impulse response) and written as 48 kHz
mono Ogg Vorbis, the format positional sounds need.

    python3 tools/generate-sonic-audio.py

Requires numpy and soundfile (the repo keeps them in build/audio-tools).
"""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'build' / 'audio-tools'))
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/heroesevolved/sounds/ability'
RATE = 48000
rng = np.random.default_rng(1985)


def seconds(n):
    return np.arange(int(RATE * n)) / RATE


def envelope(t, attack, hold, release):
    """Linear attack, flat hold, exponential release."""
    env = np.ones_like(t)
    env[t < attack] = t[t < attack] / attack
    tail = t > attack + hold
    env[tail] = np.exp(-(t[tail] - attack - hold) / release)
    return env


def band(signal, low, high):
    """A soft band-pass in the frequency domain."""
    spectrum = np.fft.rfft(signal)
    freqs = np.fft.rfftfreq(len(signal), 1 / RATE)
    shape = 1 / (1 + (low / np.maximum(freqs, 1)) ** 4) / (1 + (freqs / high) ** 4)
    return np.fft.irfft(spectrum * shape, len(signal))


def phase_of(freq):
    """Integrates an instantaneous frequency (Hz per sample) into phase."""
    return 2 * np.pi * np.cumsum(freq) / RATE


def formant(f):
    """How loud a harmonic at frequency f sounds through a bright, open-throated cry."""
    return (0.35 + np.exp(-((f - 1300) / 500) ** 2) + 1.2 * np.exp(-((f - 2700) / 600) ** 2)
            + 0.7 * np.exp(-((f - 3800) / 700) ** 2)) / (1 + (f / 7000) ** 3)


def shriek(t, pitch, vibrato_depth, vibrato_rate=6.2, voices=3, harmonics=14):
    """A chorus of detuned harmonic voices following the pitch curve `pitch` (Hz per sample)."""
    out = np.zeros_like(t)
    for v in range(voices):
        cents = (v - (voices - 1) / 2) * 7
        wobble = 1 + vibrato_depth * np.sin(2 * np.pi * (vibrato_rate + 0.4 * v) * t + v)
        f0 = pitch * 2 ** (cents / 1200) * wobble
        base = phase_of(f0)
        for n in range(1, harmonics + 1):
            amp = formant(n * np.mean(f0)) / n ** 0.6
            out += amp * np.sin(n * base + rng.uniform(0, 2 * np.pi))
    return out / voices


def bowed(t, root, length_scale=1.0):
    """A cello-like bowed tone: a sawtooth's harmonics, a slow bow attack, a little vibrato."""
    out = np.zeros_like(t)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.0 * t)
    for interval in (1.0, 1.5):
        base = phase_of(np.full_like(t, root * interval) * vib)
        for n in range(1, 22):
            out += (1 / n) * np.sin(n * base) * (0.55 if interval > 1 else 1.0)
    return out


def blast(t, length):
    noise = band(rng.standard_normal(len(t)), 300, 6500)
    thump = np.sin(2 * np.pi * (55 + 40 * np.exp(-t / 0.03)) * t)
    return noise * np.exp(-t / length) * 0.9 + thump * np.exp(-t / 0.09) * 1.4


def colour(t, start, count=5):
    """Glassy partials that ring out after the attack, each a little later than the last."""
    out = np.zeros_like(t)
    for i, f in enumerate([2637, 3136, 3520, 3951, 4699, 5274][:count]):
        at = t - start - i * 0.035
        live = at > 0
        out[live] += np.sin(2 * np.pi * f * at[live]) * np.exp(-at[live] / (0.35 + 0.1 * i)) / (1 + i * 0.3)
    return out


def room(signal, size=0.9, mix=0.28):
    """Convolution with a decaying, darkening noise tail."""
    t = seconds(size)
    ir = band(rng.standard_normal(len(t)), 200, 9000) * np.exp(-t / (size / 4.5))
    ir[:int(0.012 * RATE)] = 0
    ir /= np.sqrt(np.sum(ir ** 2))
    wet = np.fft.irfft(np.fft.rfft(signal, len(signal) + len(ir)) * np.fft.rfft(ir, len(signal) + len(ir)))[:len(signal)]
    return signal * (1 - mix) + wet * mix * 2.2


def write(name, signal, fade=0.04):
    fade_in = int(0.004 * RATE); fade_out = int(fade * RATE)
    signal[:fade_in] *= np.linspace(0, 1, fade_in)
    signal[-fade_out:] *= np.linspace(1, 0, fade_out)
    signal = np.tanh(signal / np.max(np.abs(signal)) * 1.4) * 0.9
    path = OUT / f'{name}.ogg'
    for _ in range(4):
        sf.write(path, signal.astype(np.float32), RATE, format='OGG', subtype='VORBIS')
        decoded, _ = sf.read(path)
        peak = np.max(np.abs(decoded))
        if peak < 0.98:
            break
        signal *= 0.94 / peak
    print(f'{path.relative_to(ROOT)}: {len(signal) / RATE:.2f}s')


# The Cry: a fast glide up into a piercing note over a bowed root, a pressure hit, then colour.
t = seconds(1.3)
pitch = 740 + 360 * (1 - np.exp(-t / 0.05))
cry = (shriek(t, pitch, 0.012) * envelope(t, 0.012, 0.28, 0.22) * 1.0
       + bowed(t, 110) * envelope(t, 0.03, 0.3, 0.3) * 0.22
       + blast(t, 0.12) * 0.55
       + colour(t, 0.05) * 0.35)
write('sonic_cry', room(cry, 1.0, 0.3), fade=0.12)

# The burst (the Wall, the Vault, the awakening): mostly force, a short rising chirp, a bright ring.
t = seconds(0.95)
pitch = 600 + 900 * (t / 0.95) ** 0.5
burst = (blast(t, 0.16) * 1.1
         + shriek(t, pitch, 0.006, voices=2, harmonics=10) * envelope(t, 0.008, 0.06, 0.12) * 0.7
         + bowed(t, 82.4) * envelope(t, 0.01, 0.05, 0.18) * 0.25
         + colour(t, 0.02, 4) * 0.4)
write('sonic_burst', room(burst, 0.8, 0.25), fade=0.1)

# The held scream: one continuous 5.8 s performance (the channel is at most five seconds). The
# note climbs slowly, the vibrato widens as it strains, the chorus thickens, and colour keeps
# shimmering over a steady bowed drone. Nothing repeats, so it never sounds looped.
t = seconds(5.8)
pitch = 980 + 90 * (t / 5.8) + 25 * np.sin(2 * np.pi * 0.31 * t)
depth = 0.008 + 0.012 * (t / 5.8)
strain = shriek(t, pitch, 0, voices=4, harmonics=14)
# Vibrato that deepens over time, applied as a slow phase wobble on a second chorus layer.
strain += shriek(t, pitch * (1 + depth * np.sin(2 * np.pi * 6.4 * t)), 0, voices=2, harmonics=10) * 0.6
tremolo = 1 + 0.12 * np.sin(2 * np.pi * 7.3 * t) * (t / 5.8)
sparkle = np.zeros_like(t)
for start in np.arange(0.15, 5.6, 0.42):
    sparkle += colour(t, start + rng.uniform(-0.05, 0.05), 3) * rng.uniform(0.5, 1.0)
scream = (strain * tremolo * envelope(t, 0.06, 5.5, 0.15) * 0.9
          + bowed(t, 98) * envelope(t, 0.25, 5.4, 0.2) * 0.25
          + band(rng.standard_normal(len(t)), 1500, 7000) * 0.06 * envelope(t, 0.05, 5.5, 0.15)
          + blast(t, 0.1) * 0.5
          + sparkle * 0.12)
write('sonic_scream', room(scream, 1.1, 0.3), fade=0.2)
