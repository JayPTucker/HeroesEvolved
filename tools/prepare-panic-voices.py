"""Lifts single screaming voices out of the CC0 "Crowd in panic" recording for the Time vision.

The recording (credited in assets/heroesevolved/sounds/intro/CREDITS.txt) is a whole crowd. In a
few places one voice rises over the rest. For each of those excerpts this script follows that
voice's pitch frame by frame and applies a harmonic mask: the frequency bins on its harmonics are
kept, the crowd between them is turned down to FLOOR. Each cut is high-passed, levelled, faded and
written as 16-bit mono WAV at 32 kHz to assets/heroesevolved/audio/panic/, where the client's
PanicVoices plays them from people running past the player.

Input: build/intro-audio-sources/crowd-in-panic-hq.wav. The HQ MP3 is cached there by
tools/generate-intro-audio.py; decode it to WAV with tools/decode-audio.ps1.

Usage: python tools/prepare-panic-voices.py        (requires numpy)
"""
from pathlib import Path
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'build' / 'intro-audio-sources' / 'crowd-in-panic-hq.wav'
OUT = ROOT / 'src' / 'main' / 'resources' / 'assets' / 'heroesevolved' / 'audio' / 'panic'
RATE = 32000

# (start, end, lowest and highest pitch of the voice) for each excerpt, in seconds and hertz.
# Chosen by the pitch tracker in this file's history: where one voice carries most of the energy.
EXCERPTS = [
    (0.10, 0.56, 560, 820),
    (4.12, 4.78, 700, 950),
    (7.92, 9.15, 1080, 1420),
    (9.48, 10.86, 1000, 1420),
    (10.95, 11.42, 620, 900),
    (18.12, 18.48, 680, 900),
    (19.18, 19.50, 820, 1050),
    (20.62, 21.36, 1080, 1300),
    (21.95, 22.52, 620, 800),
    (33.48, 33.88, 980, 1180),
    (36.42, 36.88, 880, 1060),
]
FLOOR = 0.2            # the crowd under a voice, 14 dB down: enough left that it still breathes
N, HOP = 2048, 256     # analysis frames: 43 ms, hop 5 ms at 48 kHz
TOP = 11000            # harmonics are kept up to here; above it only the floor


def load(path):
    with wave.open(str(path)) as w:
        sr, ch = w.getframerate(), w.getnchannels()
        data = np.frombuffer(w.readframes(w.getnframes()), dtype='<i2').astype(np.float64)
    return sr, data.reshape(-1, ch).mean(1) / 32768


def frames(x, n, hop):
    count = 1 + (len(x) - n) // hop
    return np.arange(n)[None, :] + hop * np.arange(count)[:, None]


def track(x, sr, low, high):
    """The voice's pitch in every frame: a harmonic sum over a fine grid, smoothed by Viterbi."""
    pad = 16384
    win = np.hanning(N + 1)[:-1]
    spectrum = np.abs(np.fft.rfft(x[frames(x, N, HOP)] * win, pad, axis=1)) ** 0.6
    df = sr / pad
    grid = low * 2 ** (np.arange(0, np.log2(high / low), 1 / 120))  # tenth-of-a-semitone steps
    weights = np.zeros((len(grid), spectrum.shape[1]))
    for c, f0 in enumerate(grid):
        for h in range(1, 13):
            f = h * f0
            if f > 7000:
                break
            b = f / df
            i, a = int(b), b - int(b)
            w = (f0 + 27) / (h * f0 + 320)
            weights[c, i] += w * (1 - a)
            weights[c, i + 1] += w * a
    salience = spectrum @ weights.T
    salience /= salience.max(1, keepdims=True) + 1e-12
    jump = np.abs(np.arange(len(grid))[:, None] - np.arange(len(grid))[None, :]) * 0.004
    score, back = salience[0].copy(), np.zeros(salience.shape, int)
    for t in range(1, len(salience)):
        options = score[None, :] - jump
        back[t] = options.argmax(1)
        score = options.max(1) + salience[t]
    path = np.zeros(len(salience), int)
    path[-1] = score.argmax()
    for t in range(len(salience) - 1, 0, -1):
        path[t - 1] = back[t, path[t]]
    return grid[path]


def lift(x, sr, f0):
    """Keeps the bins on the voice's harmonics, turns the rest down to FLOOR, and resynthesises."""
    win = np.hanning(N + 1)[:-1]
    idx = frames(x, N, HOP)
    spectrum = np.fft.rfft(x[idx] * win, axis=1)
    freqs = np.fft.rfftfreq(N, 1 / sr)
    mask = np.zeros(spectrum.shape)
    for t, pitch in enumerate(f0):
        bumps = np.zeros(len(freqs))
        for h in range(1, int(TOP / pitch) + 1):
            centre = h * pitch
            sigma = max(26.0, 0.012 * centre)
            bumps = np.maximum(bumps, np.exp(-0.5 * ((freqs - centre) / sigma) ** 2))
        mask[t] = FLOOR + (1 - FLOOR) * bumps
    mask *= 1 / np.sqrt(1 + (150 / np.maximum(freqs, 1)) ** 4)  # high-pass: no traffic rumble
    out = np.zeros(len(x))
    norm = np.zeros(len(x))
    synth = np.fft.irfft(spectrum * mask, N, axis=1) * win
    for t, start in enumerate(idx[:, 0]):
        out[start:start + N] += synth[t]
        norm[start:start + N] += win ** 2
    return out / np.maximum(norm, 1e-3)


def harmonic_share(x, sr, f0):
    """How much of the energy between 250 Hz and 7 kHz sits on the tracked harmonics."""
    win = np.hanning(N + 1)[:-1]
    power = np.abs(np.fft.rfft(x[frames(x, N, HOP)] * win, axis=1)) ** 2
    freqs = np.fft.rfftfreq(N, 1 / sr)
    band = (freqs > 250) & (freqs < 7000)
    shares = []
    for t, pitch in enumerate(f0[:len(power)]):
        near = np.zeros(len(freqs), bool)
        for h in range(1, int(7000 / pitch) + 1):
            near |= np.abs(freqs - h * pitch) < max(1.6 * sr / N, 0.025 * h * pitch)
        shares.append(power[t, near & band].sum() / (power[t, band].sum() + 1e-12))
    return float(np.mean(shares))


def resample(x, sr, rate):
    """Band-limited resampling in the frequency domain."""
    spectrum = np.fft.rfft(x)
    count = int(round(len(x) * rate / sr))
    keep = count // 2 + 1
    out = np.zeros(keep, complex)
    out[:min(keep, len(spectrum))] = spectrum[:min(keep, len(spectrum))]
    return np.fft.irfft(out, count) * count / len(x)


def finish(x, rate):
    """Even loudness from cut to cut, no clipping, and fades so a cut never clicks."""
    window = int(0.2 * rate)
    loudest = max(np.sqrt(np.mean(x[i:i + window] ** 2)) for i in range(0, max(1, len(x) - window), window // 4))
    x = x * (10 ** (-13 / 20) / max(loudest, 1e-9))
    peak = np.abs(x).max()
    if peak > 0.89:
        x *= 0.89 / peak
    fade_in, fade_out = int(0.012 * rate), int(0.09 * rate)
    x[:fade_in] *= 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, fade_in))
    x[-fade_out:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, fade_out))
    return x


def save(path, x, rate):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(np.clip(np.round(x * 32767), -32768, 32767).astype('<i2').tobytes())


def main():
    if not SOURCE.exists():
        raise SystemExit(f'{SOURCE} is missing: run tools/generate-intro-audio.py, then tools/decode-audio.ps1')
    sr, mono = load(SOURCE)
    for old in OUT.glob('scream_*.wav'):
        old.unlink()
    for number, (start, end, low, high) in enumerate(EXCERPTS, 1):
        # A little context either side, so the tracker and the mask settle before the voice starts.
        a, b = int((start - 0.05) * sr), int((end + 0.05) * sr) + N
        piece = mono[max(0, a):b]
        f0 = track(piece, sr, low, high)
        lifted = lift(piece, sr, f0)
        before, after = harmonic_share(piece, sr, f0), harmonic_share(lifted, sr, f0)
        trimmed = lifted[int(0.05 * sr):int(0.05 * sr) + int((end - start) * sr)]
        out = finish(resample(trimmed, sr, RATE), RATE)
        save(OUT / f'scream_{number:02d}.wav', out, RATE)
        print(f'scream_{number:02d}  {start:5.2f}-{end:5.2f}s  pitch {np.median(f0):5.0f} Hz  '
              f'voice share {before:.2f} -> {after:.2f}  {len(out) / RATE:.2f}s')


if __name__ == '__main__':
    main()
