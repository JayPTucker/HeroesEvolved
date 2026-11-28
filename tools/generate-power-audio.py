"""Build every power's signature sounds from scratch: no recordings, only synthesis.

Each power gets one small palette of cues, played by ``sounds/PowerSounds`` whenever one of its actions is used:

* ``cast``    a light move (most presses, skill-tree actives)
* ``heavy``   an ultimate, a class ability or a signature move
* ``charge``  a wind-up that a release finishes (a spear, a skyburst, Critical Mass)
* ``impact``  a power's blow landing on someone (and most free follow-ups)
* ``end``     a held effect let go or dismissed
* ``strike``  a fighting-style blow (only powers with a style)
* ``dodge``   a fighting-style dodge (only powers with a style)

Every power has its own material (fire is filtered noise with crackle, ice is crystalline partials over hiss,
lightning is buzz and crackle, water is bubbles and spray, and so on), and the cues of one power share it, so a
fighter can be told by ear. Sonic Resonance, Vibe and Space-Time follow the themes of their existing hand-made cues
(cello and glass; chirp and grit; clockwork), which these never replace.

Writes 48 kHz mono Ogg Vorbis into ``assets/heroesevolved/sounds/power/<power>/<cue>_<n>.ogg``, adds an entry per
cue to ``sounds.json`` and a subtitle per cue to ``en_us.json`` (leaving every other entry as it is).

    py -3 tools/generate-power-audio.py            # everything
    py -3 tools/generate-power-audio.py pyrokinesis cryokinesis

Requires numpy, scipy and soundfile (kept in build/audio-tools: ``pip install --target build/audio-tools``).
"""
from pathlib import Path
import json
import re
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build" / "audio-tools"))
import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt, stft, istft, fftconvolve

RATE = 48000
ASSETS = ROOT / "src/main/resources/assets/heroesevolved"
OUT = ASSETS / "sounds/power"
rng = np.random.default_rng(0)

# ---------------------------------------------------------------------------------------------------------------
# Building blocks
# ---------------------------------------------------------------------------------------------------------------


def T(duration):
    return np.arange(int(duration * RATE)) / RATE


def white(n):
    return rng.standard_normal(n)


def pink(n):
    spectrum = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / RATE)
    spectrum /= np.sqrt(np.maximum(f, 20))
    out = np.fft.irfft(spectrum, n)
    return out / (np.std(out) + 1e-9)


def brown(n):
    out = hp(np.cumsum(rng.standard_normal(n)), 18, 2)
    return out / (np.std(out) + 1e-9)


def _sos(kind, f, order):
    nyq = RATE / 2
    if isinstance(f, (list, tuple)):
        f = [min(max(x, 10), nyq * 0.98) for x in f]
    else:
        f = min(max(f, 10), nyq * 0.98)
    return butter(order, f, kind, fs=RATE, output="sos")


def lp(x, f, order=2):
    return sosfilt(_sos("lowpass", f, order), x)


def hp(x, f, order=2):
    return sosfilt(_sos("highpass", f, order), x)


def bp(x, lo, hi, order=2):
    return sosfilt(_sos("bandpass", [lo, hi], order), x)


def path(t, points):
    """A value that glides geometrically through (time, value) points (values must be positive)."""
    times = [p[0] for p in points]
    values = np.log([p[1] for p in points])
    return np.exp(np.interp(t, times, values))


def sweep(x, centre, width=0.8):
    """Band-pass whose centre (Hz, an array as long as x) moves over time; width is in octaves."""
    n = len(x)
    f, frames, z = stft(x, RATE, nperseg=1024, noverlap=768, boundary="even")
    fc = np.interp(frames, np.arange(n) / RATE, centre)
    octave = np.log2(np.maximum(f[:, None], 1) / fc[None, :])
    z *= np.exp(-0.5 * (octave / width) ** 2)
    _, y = istft(z, RATE, nperseg=1024, noverlap=768, boundary=True)
    y = y[:n]
    return np.pad(y, (0, n - len(y)))


def env(t, attack, decay, start=0.0):
    """Linear attack, exponential decay, silent before start."""
    u = t - start
    return np.where(u < 0, 0, np.minimum(np.maximum(u, 0) / attack, 1) * np.exp(-np.maximum(u - attack, 0) / decay))


def swell(t, peak, end=None, power=2.0):
    """Rises to 1 at peak and falls to 0 at end (the clip's end by default)."""
    end = t[-1] if end is None else end
    rise = np.clip(t / peak, 0, 1) ** power
    fall = np.clip((end - t) / max(end - peak, 1e-3), 0, 1) ** 1.5
    return np.where(t < peak, rise, fall)


def ramp(t, start, end, power=1.0):
    return np.clip((t - start) / max(end - start, 1e-6), 0, 1) ** power


def tone(freq):
    return np.sin(phase(freq))


def phase(freq):
    assert np.ndim(freq) == 1, "pass a frequency per sample"
    return 2 * np.pi * np.cumsum(np.asarray(freq, dtype=float)) / RATE


def saw(freq, harmonics=30, rolloff=1.0):
    freq = np.asarray(freq, dtype=float)
    ph = phase(freq)
    out = np.zeros_like(ph)
    for k in range(1, harmonics + 1):
        mask = (k * freq) < 17000
        out += mask * np.sin(k * ph) / k ** rolloff
    return out * 0.6


def square(freq, harmonics=15):
    freq = np.asarray(freq, dtype=float)
    ph = phase(freq)
    out = np.zeros_like(ph)
    for k in range(1, 2 * harmonics, 2):
        out += ((k * freq) < 17000) * np.sin(k * ph) / k
    return out * 0.7


def thump(t, f0, f1, decay, start=0.0, glide=0.04, attack=0.002, knock=0.35):
    """A pitched body blow: a sine that drops from f0 to f1, with a short mid-range knock so that small
    speakers, which can't play the body, still hear the blow land."""
    u = np.maximum(t - start, 0)
    freq = f1 + (f0 - f1) * np.exp(-u / glide)
    body = tone(freq) * env(t, attack, decay, start)
    if knock:
        body += knock * bp(white(len(t)), 250, 2200) * env(t, .001, .014, start)
    return body


def ring(t, freq, decay, start=0.0, attack=0.001):
    return np.sin(2 * np.pi * freq * np.maximum(t - start, 0)) * env(t, attack, decay, start)


def burst(t, decay, lo=200, hi=12000, start=0.0, attack=0.001):
    return bp(white(len(t)), lo, hi) * env(t, attack, decay, start)


def grains(t, times, kernel_decay, lo, hi, gains=None):
    """Short noise grains at given times: crackle, clicks, pebbles, debris."""
    impulses = np.zeros(len(t))
    for i, at in enumerate(times):
        k = int(at * RATE)
        if 0 <= k < len(t):
            impulses[k] += (gains[i] if gains is not None else rng.exponential(1.0))
    m = int(kernel_decay * RATE * 6) + 8
    kt = np.arange(m) / RATE
    kernel = rng.standard_normal(m) * np.exp(-kt / kernel_decay)
    return bp(fftconvolve(impulses, kernel)[:len(t)], lo, hi)


def poisson(t, rate, start=0.0, end=None):
    """Event times for a (possibly time-varying) rate in events per second."""
    end = t[-1] if end is None else end
    rate = np.broadcast_to(np.asarray(rate, dtype=float), t.shape)
    chance = rate / RATE
    hits = rng.random(len(t)) < chance
    times = t[hits]
    return times[(times >= start) & (times <= end)]


def crackle(t, rate, decay=0.0004, lo=1500, hi=12000, start=0.0, end=None):
    return grains(t, poisson(t, rate, start, end), decay, lo, hi)


def clicks(t, times, freqs, decay=0.012, gains=None):
    """Resonant clicks: each a struck partial (plates, ratchets, clockwork)."""
    out = np.zeros(len(t))
    for i, (at, f) in enumerate(zip(times, freqs)):
        g = gains[i] if gains is not None else 1.0
        out += g * (ring(t, f, decay, at) + 0.5 * ring(t, f * 1.51, decay * 0.6, at))
        out += g * 0.4 * burst(t, 0.0015, f * 0.5, min(f * 3, 16000), at)
    return out


def bubbles(t, rate, fmin, fmax, decay=(0.015, 0.05), rise=(6, 30), start=0.0, end=None):
    """Bubble chirps: a sine that rises as the bubble surfaces."""
    out = np.zeros(len(t))
    for at in poisson(t, rate, start, end):
        f0 = np.exp(rng.uniform(np.log(fmin), np.log(fmax)))
        d = rng.uniform(*decay)
        s = rng.uniform(*rise)
        i0 = int(at * RATE)
        n = min(int(d * 7 * RATE), len(t) - i0)
        if n <= 8:
            continue
        u = np.arange(n) / RATE
        out[i0:i0 + n] += np.sin(2 * np.pi * f0 * (u + 0.5 * s * u * u)) * np.exp(-u / d) * rng.uniform(0.3, 1)
    return out


def tinkles(t, rate, fmin, fmax, decay=(0.05, 0.25), start=0.0, end=None):
    """Glassy struck partials with an inharmonic overtone: ice, glass, sparkle."""
    out = np.zeros(len(t))
    for at in poisson(t, rate, start, end):
        f = np.exp(rng.uniform(np.log(fmin), np.log(fmax)))
        d = rng.uniform(*decay)
        g = rng.uniform(0.25, 1)
        out += g * (ring(t, f, d, at) + 0.35 * ring(t, f * 2.76, d * 0.5, at))
    return out


def bell(t, freq, decay, start=0.0, ratios=(1, 2.01, 2.76, 4.07, 5.4), gains=(1, .5, .4, .25, .15)):
    out = np.zeros(len(t))
    for r, g in zip(ratios, gains):
        out += g * ring(t, freq * r, decay / (1 + 0.6 * (r - 1)), start, 0.002)
    return out


def chorus(t, freq, voices=3, cents=6, vibrato=(5.0, 0.004), partials=(1, .3, .12)):
    """A soft sung or bowed voice: a few detuned copies with vibrato."""
    out = np.zeros(len(t))
    freq = np.broadcast_to(np.asarray(freq, dtype=float), t.shape)
    for v in range(voices):
        detune = 2 ** ((v - (voices - 1) / 2) * cents / 1200)
        vib = 1 + vibrato[1] * np.sin(2 * np.pi * vibrato[0] * t + v * 1.7)
        ph = phase(freq * detune * vib)
        for k, g in enumerate(partials, start=1):
            out += g * np.sin(k * ph)
    return out / voices


def flange(x, depth=0.003, rate=0.7, mix=0.7):
    n = np.arange(len(x))
    delay = (depth * 0.5 * (1 + np.sin(2 * np.pi * rate * n / RATE))) * RATE + 1
    return x + mix * np.interp(n - delay, n, x, left=0)


def flutter(t, rate, depth):
    """Turbulent amplitude: smoothed noise around 1."""
    raw = lp(rng.standard_normal(len(t)), rate, 2)
    raw /= (np.std(raw) + 1e-9)
    return np.clip(1 + depth * raw, 0, None)


def crush(x, hold=6, bits=5):
    held = np.repeat(x[::hold], hold)[:len(x)]
    q = 2 ** bits
    return np.round(held * q) / q


def reverb(x, decay=0.6, mix=0.3, tone=5000, predelay=0.012):
    m = int(decay * 5 * RATE)
    kt = np.arange(m) / RATE
    ir = lp(rng.standard_normal(m), tone) * np.exp(-kt / decay)
    ir = np.concatenate([np.zeros(int(predelay * RATE)), ir])
    wet = fftconvolve(x, ir)[:len(x)]
    wet *= np.std(x) / (np.std(wet) + 1e-9)
    return x * (1 - mix * 0.5) + wet * mix


def echoes(x, taps):
    out = x.copy()
    for delay, gain in taps:
        k = int(delay * RATE)
        if k < len(x):
            out[k:] += x[:-k] * gain
    return out


def master(x, target):
    """Same loudness for every cue: the loudest 100 ms sits at `target` RMS, peaks under -1 dBFS."""
    x = hp(x, 28, 2)
    x = np.nan_to_num(x)
    w = int(0.1 * RATE)
    heard = hp(x, 150, 2) * 0.75 + x * 0.25  # sub-bass counts for little: most speakers can't play it
    sq = np.convolve(heard * heard, np.ones(w) / w, mode="same")
    loudest = np.sqrt(np.max(sq)) + 1e-9
    x = x * (target / loudest)
    x = np.tanh(x * 1.15) / 1.15  # gentle limiting of the few peaks that stick out
    peak = np.max(np.abs(x)) + 1e-9
    if peak > 0.8:
        x *= 0.8 / peak
    fade = min(int(0.03 * RATE), len(x) // 4)
    x[-fade:] *= np.linspace(1, 0, fade) ** 2
    x[:48] *= np.linspace(0, 1, 48)
    return x.astype(np.float32)


LOUDNESS = {"cast": 0.21, "heavy": 0.25, "charge": 0.2, "impact": 0.22, "end": 0.17, "strike": 0.17, "dodge": 0.16}

# ---------------------------------------------------------------------------------------------------------------
# Recipes: one function per power, returning {cue: function(k) -> samples}; k is a small pitch factor per variant.
# ---------------------------------------------------------------------------------------------------------------


def cryokinesis():
    def cast(k):
        t = T(.65)
        hiss = sweep(white(len(t)), path(t, [(0, 2400 * k), (.25, 7200 * k), (.6, 5000 * k)]), .7) * env(t, .02, .17)
        frost = crackle(t, 260 * np.exp(-t / .15), .0003, 3000, 12000) * .35
        return reverb(hiss + tinkles(t, 34, 2600 * k, 7800 * k, start=.01, end=.3) * .5 + frost, .35, .25, 9000)

    def heavy(k):
        t = T(1.9)
        groan = lp(saw(path(t, [(0, 62 * k), (1.8, 44 * k)]) * (1 + .03 * np.sin(2 * np.pi * 2.6 * t))), 520) * swell(t, .18, 1.7)
        crack = burst(t, .035, 900, 11000) * 1.6 + thump(t, 110, 38, .35, glide=.06) * 1.2
        shatter = tinkles(t, 140 * np.exp(-t / .3), 1800 * k, 8500 * k, (.08, .5))
        frost = crackle(t, 400 * np.exp(-t / .4), .0004, 2500, 11000) * .4
        return reverb(groan * .6 + crack + shatter * .7 + frost, .9, .4, 8000)

    def charge(k):
        t = T(1.35)
        rise = ramp(t, 0, 1.3, 1.6)
        whistle = sweep(white(len(t)), path(t, [(0, 1600 * k), (1.35, 8200 * k)]), .35) * rise
        sparkle = tinkles(t, 10 + 90 * rise, 2400 * k, 8800 * k, (.04, .18)) * .55
        shimmer = tone(path(t, [(0, 1180 * k), (1.35, 2360 * k)])) * rise * .12
        return reverb(whistle + sparkle + shimmer, .5, .3, 9000)

    def impact(k):
        t = T(.5)
        crack = burst(t, .028, 800, 10000) * 1.4 + thump(t, 300 * k, 120 * k, .05) * .8
        spray = tinkles(t, 90, 2200 * k, 8000 * k, (.04, .2), 0, .16) * .6
        return reverb(crack + spray + crackle(t, 600 * np.exp(-t / .05), .0003, 3000, 12000) * .3, .3, .2, 9000)

    def end(k):
        t = T(.95)
        hiss = sweep(white(len(t)), path(t, [(0, 6400 * k), (.9, 1500 * k)]), .8) * env(t, .05, .32)
        drips = bubbles(t, 7, 1400 * k, 2600 * k, (.01, .03), (8, 20), .15) * .35
        return reverb(hiss + tinkles(t, 14, 1800 * k, 5000 * k, (.08, .35), 0, .6) * .4 + drips, .45, .3, 8000)

    def strike(k):
        t = T(.3)
        swish = sweep(white(len(t)), path(t, [(0, 1500 * k), (.2, 6200 * k)]), .6) * swell(t, .07, .28)
        return swish + tinkles(t, 18, 3000 * k, 7000 * k, (.04, .12), .04, .16) * .45

    def dodge(k):
        t = T(.45)
        scrape = sweep(white(len(t)), path(t, [(0, 2800 * k), (.15, 5200 * k), (.45, 2400 * k)]), .5)
        scrape *= swell(t, .12, .43) * flutter(t, 40, .5)
        return scrape + crackle(t, 220, .0003, 4000, 12000, 0, .3) * .2

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


def electrokinesis():
    def buzz(t, freq):
        return bp(saw(freq * (1 + .01 * rng.standard_normal(len(t)).cumsum() / np.sqrt(len(t)))), 180, 6000)

    def cast(k):
        t = T(.55)
        zap = buzz(t, path(t, [(0, 140 * k), (.5, 90 * k)])) * env(t, .004, .1)
        spark = crackle(t, 900 * np.exp(-t / .12), .0003, 2000, 11000) * .4
        snap = hp(white(len(t)), 3000) * env(t, .0005, .008) * .55
        return reverb(zap * 1.3 + spark + snap, .25, .2, 10000)

    def heavy(k):
        t = T(2.3)
        crack = white(len(t)) * env(t, .0005, .018) * 2 + bp(white(len(t)), 900, 9000) * env(t, .001, .06)
        roll = lp(brown(len(t)), 320) * env(t, .04, .8) * flutter(t, 6, .7) * 1.4
        spark = crackle(t, 1400 * np.exp(-t / .25), .0004, 1500, 13000) * .7
        zap = buzz(t, 70 * k) * env(t, .003, .3) * .5
        return reverb(crack + roll + spark + zap, 1.1, .4, 7000)

    def charge(k):
        t = T(1.35)
        rise = ramp(t, 0, 1.3, 1.4)
        freq = path(t, [(0, 52 * k), (1.35, 230 * k)])
        hum = buzz(t, freq) * (1 + .5 * np.sin(phase(path(t, [(0, 7), (1.35, 32)])))) * rise
        return reverb(hum + crackle(t, 40 + 900 * rise, .0003, 2000, 13000) * .6 * rise, .35, .25, 9000)

    def impact(k):
        t = T(.42)
        zap = buzz(t, 170 * k * (1 + .3 * np.sin(2 * np.pi * 37 * t))) * env(t, .002, .06)
        snap = hp(white(len(t)), 2500) * env(t, .0005, .01) * .5
        return reverb(zap * 1.3 + snap + crackle(t, 1500 * np.exp(-t / .06), .0003, 2000, 11000) * .4, .25, .2, 10000)

    def end(k):
        t = T(.8)
        down = buzz(t, path(t, [(0, 230 * k), (.75, 34 * k)])) * env(t, .01, .28)
        return reverb(down + crackle(t, 300 * np.exp(-t / .2), .0003, 2000, 12000) * .4, .35, .25, 8000)

    def strike(k):
        t = T(.26)
        snap = hp(white(len(t)), 2500) * env(t, .0005, .01) * .45
        zip_ = sweep(white(len(t)), path(t, [(0, 1600 * k), (.15, 6000 * k)]), .5) * swell(t, .05, .2) * .6
        jab = buzz(t, np.full(len(t), 190 * k)) * env(t, .002, .05)
        return snap + zip_ + jab + crackle(t, 1100 * np.exp(-t / .05), .0003, 2500, 11000) * .35

    def dodge(k):
        t = T(.36)
        flash = sweep(white(len(t)), path(t, [(0, 1000 * k), (.12, 9500 * k), (.35, 6000 * k)]), .45) * swell(t, .06, .34)
        return flash + crackle(t, 700 * np.exp(-t / .1), .0003, 2500, 13000) * .5 + buzz(t, 200 * k) * env(t, .003, .05) * .3

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


def flight():
    def cast(k):
        t = T(.85)
        gust = sweep(pink(len(t)), path(t, [(0, 300 * k), (.25, 1600 * k), (.8, 520 * k)]), .9) * swell(t, .24, .82)
        return reverb(gust * flutter(t, 10, .25) + thump(t, 70, 45, .2) * .25, .5, .25, 6000)

    def heavy(k):
        t = T(1.8)
        boom = thump(t, 75, 30, .32, glide=.08) * 1.2 + lp(white(len(t)), 900) * env(t, .002, .08)
        rush = sweep(pink(len(t)), path(t, [(0, 220 * k), (.5, 2600 * k), (1.75, 600 * k)]), 1.0) * swell(t, .4, 1.75)
        return reverb(boom + rush * flutter(t, 12, .3) * 1.2, .8, .35, 6000)

    def charge(k):
        t = T(1.4)
        rise = ramp(t, 0, 1.35, 1.3)
        swirl = sweep(pink(len(t)), path(t, [(0, 200 * k), (1.4, 1700 * k)]), .7)
        swirl *= rise * (1 + .45 * np.sin(phase(path(t, [(0, 3.5), (1.4, 9.0)]))))
        return reverb(swirl, .5, .3, 6000)

    def impact(k):
        t = T(.6)
        slam = thump(t, 95 * k, 35, .16) * 1.2 + lp(white(len(t)), 1500) * env(t, .002, .12)
        return reverb(slam + sweep(pink(len(t)), path(t, [(0, 1200), (.5, 300)]), .8) * env(t, .01, .2) * .5, .45, .3, 5000)

    def end(k):
        t = T(.9)
        return reverb(sweep(pink(len(t)), path(t, [(0, 1300 * k), (.85, 240 * k)]), .9) * env(t, .04, .32), .5, .3, 5000)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


def hydrokinesis():
    def cast(k):
        t = T(.72)
        swish = sweep(pink(len(t)), path(t, [(0, 400 * k), (.2, 2500 * k), (.7, 900 * k)]), .7) * swell(t, .19, .7)
        return reverb(swish + bubbles(t, 45, 400 * k, 1500 * k, start=.05, end=.5) * .45, .4, .28, 7000)

    def heavy(k):
        t = T(2.0)
        build = lp(pink(len(t)), 900) * ramp(t, 0, .35, 2) * (t < .4)
        crash = lp(white(len(t)), 6500) * env(t, .01, .7, .35) * 1.3 + thump(t, 70, 32, .4, .35) * .9
        foam = bubbles(t, 160 * np.exp(-np.maximum(t - .4, 0) / .5) * (t > .4), 300 * k, 2400 * k) * .5
        return reverb(build * .6 + crash + foam, 1.0, .4, 6000)

    def charge(k):
        t = T(1.3)
        rise = ramp(t, 0, 1.25, 1.2)
        gurgle = bubbles(t, 20 + 220 * rise, 280 * k, 1300 * k, (.01, .04))
        swirl = sweep(pink(len(t)), path(t, [(0, 160 * k), (1.3, 650 * k)]), .6) * rise * .6
        return reverb(gurgle * .7 + swirl, .45, .3, 6000)

    def impact(k):
        t = T(.55)
        splash = bp(white(len(t)), 700, 6500) * env(t, .002, .09) * 1.2 + thump(t, 160 * k, 80, .06) * .6
        drops = bubbles(t, 140, 1500 * k, 4200 * k, (.006, .02), (10, 40), 0, .22) * .5
        return reverb(splash + drops, .35, .25, 8000)

    def end(k):
        t = T(.95)
        drain = bubbles(t, 60 * np.exp(-t / .35), 600 * k, 1400 * k, (.015, .05), (2, 8))
        trickle = bp(white(len(t)), 1500, 5000) * env(t, .05, .3) * flutter(t, 30, .8) * .25
        return reverb(drain * .6 + trickle, .5, .3, 7000)

    def strike(k):
        t = T(.3)
        whip = sweep(pink(len(t)), path(t, [(0, 600 * k), (.11, 4200 * k)]), .5) * swell(t, .09, .13)
        crack = bp(white(len(t)), 1000, 6000) * env(t, .0005, .01, .11) * .55
        return whip + crack + bubbles(t, 120, 1800 * k, 4000 * k, (.005, .015), (10, 40), .1, .22) * .4

    def dodge(k):
        t = T(.45)
        slide = sweep(pink(len(t)), path(t, [(0, 500 * k), (.2, 1900 * k), (.45, 900 * k)]), .6) * swell(t, .14, .43)
        return slide + bubbles(t, 50, 600 * k, 1800 * k, start=.05, end=.35) * .35

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


def _glass_chord(t, k, pitch=1.0, beat=0.7):
    pitch = np.broadcast_to(np.asarray(pitch, dtype=float), t.shape)
    out = np.zeros(len(t))
    for f, g in ((523.25, 1), (784, .7), (1046.5, .5), (1568, .3)):
        out += g * (tone(f * k * pitch) + tone((f * k + beat) * pitch)) * .5
    return out


def invisibility():
    def cast(k):
        t = T(.85)
        chord = _glass_chord(t, k) * env(t, .08, .4)
        ping = bell(t, 2093 * k, .35, .02) * .35
        air = sweep(white(len(t)), path(t, [(0, 3000), (.5, 6000)]), .8) * env(t, .05, .2) * .25
        return reverb(chord * .6 + ping + air, .6, .35, 9000)

    def heavy(k):
        t = T(1.6)
        boom = thump(t, 64, 28, .4, glide=.07) * 1.2 + hp(white(len(t)), 2000) * env(t, .001, .03) * .6
        chord = _glass_chord(t, k, path(t, [(0, .98), (.3, 1.0)])) * swell(t, .12, 1.5) * .8
        return reverb(boom + chord + tinkles(t, 40 * np.exp(-t / .3), 2000 * k, 6000 * k) * .3, .9, .4, 8000)

    def charge(k):
        t = T(1.3)
        rise = ramp(t, 0, 1.25, 1.4)
        chord = _glass_chord(t, k, path(t, [(0, .8), (1.3, 1.0)]), path(t, [(0, .5), (1.3, 6)])) * rise
        return reverb(chord * .8 + sweep(white(len(t)), path(t, [(0, 1500), (1.3, 7000)]), .4) * rise * .2, .6, .35, 9000)

    def impact(k):
        t = T(.45)
        thunk = ring(t, 320 * k, .08) + .6 * ring(t, 640 * k, .05) + .4 * ring(t, 2600 * k, .12)
        return reverb(thunk + lp(white(len(t)), 2000) * env(t, .001, .02) * .8, .35, .25, 9000)

    def end(k):
        t = T(.9)
        chord = _glass_chord(t, k, path(t, [(0, 1.0), (.9, .7)])) * env(t, .01, .35)
        return reverb(chord * .6 + tinkles(t, 18, 2200 * k, 6000 * k, (.05, .2), 0, .5) * .35, .6, .35, 9000)

    def strike(k):
        t = T(.26)
        return thump(t, 180 * k, 90, .05) + ring(t, 1800 * k, .06) * .3 + lp(white(len(t)), 1800) * env(t, .002, .03) * .5

    def dodge(k):
        t = T(.36)
        swoosh = sweep(white(len(t)), path(t, [(0, 1000 * k), (.3, 4200 * k)]), .6) * swell(t, .1, .34)
        return swoosh + _glass_chord(t, k) * env(t, .03, .12) * .3

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


def matter_assembly():
    def clunk(t, at, k=1.0, size=1.0):
        return (thump(t, 170 * k / size, 110 * k / size, .07 * size, at) +
                .5 * ring(t, 900 * k / size, .15 * size, at) + .3 * ring(t, 1350 * k / size, .1 * size, at) +
                .5 * lp(white(len(t)), 3000) * env(t, .001, .02, at))

    def cast(k):
        t = T(.75)
        times = np.cumsum([.06 * .82 ** i for i in range(10)])
        tick = clicks(t, times, [(1800, 2600, 3400)[i % 3] * k for i in range(10)], .01)
        return reverb(tick * .5 + clunk(t, times[-1] + .05, k), .35, .25, 8000)

    def heavy(k):
        t = T(1.65)
        times = np.sort(rng.uniform(0, .6, 30))
        cascade = clicks(t, times, rng.uniform(1500, 4200, 30) * k, .012) * .4
        locks = sum(clunk(t, at, k, s) for at, s in ((.25, 1.1), (.45, 1.25), (.62, 1.5)))
        boom = thump(t, 60, 32, .5, .64, .06) * 1.1
        return reverb(cascade + locks * .7 + boom, .8, .35, 7000)

    def charge(k):
        t = T(1.25)
        times, at, gap = [], 0.0, .12
        while at < 1.2:
            times.append(at)
            at += gap
            gap = max(gap * .9, .022)
        freqs = path(np.array(times), [(0, 1400 * k), (1.2, 3800 * k)])
        return reverb(clicks(t, times, freqs, .009, np.linspace(.4, 1, len(times))) * .7, .3, .25, 8000)

    def impact(k):
        t = T(.42)
        hit = burst(t, .02, 600, 9000) + ring(t, 2400 * k, .1) * .5 + ring(t, 3700 * k, .08) * .35 + thump(t, 200, 90, .05)
        return reverb(hit, .3, .2, 8000)

    def end(k):
        t = T(.85)
        times = np.cumsum([.03 * 1.22 ** i for i in range(9)])
        tick = clicks(t, times, rng.uniform(1300, 3000, 9) * k, .01, np.linspace(1, .3, 9))
        crumble = crackle(t, 260 * np.exp(-t / .3), .002, 300, 2500) * .5
        return reverb(tick * .5 + crumble, .4, .25, 6000)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


C5, E5, G5 = 523.25, 659.25, 783.99


def empathic_mimicry():
    def note(t, f, at, decay):
        return chorus(t, f) * env(t, .03, decay, at)

    def cast(k):
        t = T(1.0)
        arp = sum(note(t, f * k, at, .32) for f, at in ((C5, 0), (E5, .07), (G5, .14)))
        return reverb(echoes(arp * .5, [(.18, .4), (.36, .2)]), .6, .35, 8000)

    def heavy(k):
        t = T(1.8)
        chord = sum(chorus(t, f * k) * g for f, g in ((261.6, 1), (392, .8), (C5, .7), (E5, .5), (G5, .4))) * swell(t, .32, 1.75)
        sparkle = tinkles(t, 30, 1800 * k, 5200 * k, (.1, .4), .2, 1.3) * .2
        return reverb(echoes(chord * .35, [(.2, .35)]) + sparkle, 1.0, .45, 8000)

    def charge(k):
        t = T(1.3)
        glide = path(t, [(0, .8), (1.3, 1.0)])
        chord = sum(chorus(t, f * k * glide) * g for f, g in ((C5, 1), (E5, .7), (G5, .5)))
        return reverb(chord * ramp(t, 0, 1.25, 1.6) * .4, .6, .35, 8000)

    def impact(k):
        t = T(.55)
        hit = bell(t, 1046.5 * k, .35) + bell(t, 1318.5 * k, .3) * .6
        return reverb(echoes(hit * .5, [(.12, .35)]), .4, .3, 9000)

    def end(k):
        t = T(1.0)
        arp = sum(note(t, f * k, at, .3) for f, at in ((G5, 0), (E5, .09), (C5, .18)))
        return reverb(echoes(arp * .45, [(.2, .35), (.4, .15)]), .6, .35, 7000)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


def phasing():
    def warble(t, freq):
        freq = np.broadcast_to(np.asarray(freq, dtype=float), t.shape)
        return sum(tone(freq * d) for d in (1, 1.012, .988)) / 3

    def cast(k):
        t = T(.75)
        ghost = warble(t, path(t, [(0, 420 * k), (.7, 240 * k)])) * swell(t, .12, .7) * .5
        air = flange(bp(white(len(t)), 500, 3200), .004, 1.3) * swell(t, .15, .7) * .5
        return reverb(ghost + air, .6, .35, 6000)

    def heavy(k):
        t = T(1.6)
        dense = thump(t, 58 * k, 27, .5, glide=.07) * 1.3 + lp(white(len(t)), 400) * env(t, .002, .1)
        crunch = crackle(t, 500 * np.exp(-t / .25), .002, 300, 2600) * .6
        return reverb(dense + crunch + flange(warble(t, 110 * k), .004, .5) * env(t, .1, .4) * .3, .8, .35, 4000)

    def charge(k):
        t = T(1.3)
        rise = ramp(t, 0, 1.25, 1.3)
        ghost = flange(warble(t, path(t, [(0, 150 * k), (1.3, 470 * k)])), .006, path(t, [(0, .5), (1.3, 3)]).mean())
        return reverb(ghost * rise * .6 + flange(bp(white(len(t)), 400, 2500), .005, 1.0) * rise * .3, .6, .35, 6000)

    def impact(k):
        t = T(.45)
        return reverb(thump(t, 115 * k, 48, .1) * 1.2 + lp(white(len(t)), 700) * env(t, .001, .05) * .7, .3, .2, 4000)

    def end(k):
        t = T(.8)
        ghost = warble(t, path(t, [(0, 500 * k), (.7, 180 * k)])) * env(t, .02, .3) * .5
        return reverb(ghost + thump(t, 300 * k, 100, .03, .62) * .6, .5, .35, 6000)

    def strike(k):
        t = T(.3)
        whoosh = flange(sweep(white(len(t)), path(t, [(0, 600 * k), (.2, 2600 * k)]), .6), .003, 2) * swell(t, .08, .2) * .7
        return whoosh + thump(t, 130 * k, 60, .05, .09)

    def dodge(k):
        t = T(.45)
        return flange(sweep(white(len(t)), path(t, [(0, 500 * k), (.2, 2400 * k), (.45, 900 * k)]), .6), .004, 1.6) * swell(t, .14, .43)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


def pyrokinesis():
    def roar(t, cutoff):
        body = lp(brown(len(t)) * .6 + pink(len(t)) * .4, cutoff)
        return body * flutter(t, 14, .5)

    def cast(k):
        t = T(.72)
        whoosh = sweep(pink(len(t)), path(t, [(0, 250 * k), (.15, 1400 * k), (.7, 600 * k)]), 1.0) * swell(t, .14, .7)
        return reverb(whoosh * flutter(t, 18, .35) + crackle(t, 160 * np.exp(-t / .3), .0006, 1500, 8000) * .5 +
                      roar(t, 300) * env(t, .03, .2) * .4, .4, .25, 6000)

    def heavy(k):
        t = T(2.0)
        fire = roar(t, 950 * k) * swell(t, .25, 1.95) * 1.2
        pop = thump(t, 85, 38, .3, glide=.06)
        return reverb(fire + pop + crackle(t, 240, .0007, 1200, 8000) * .55 * swell(t, .2, 1.9), .8, .35, 5000)

    def charge(k):
        t = T(1.4)
        rise = ramp(t, 0, 1.35, 1.4)
        body = sweep(pink(len(t)) * flutter(t, 14, .5), path(t, [(0, 260 * k), (1.4, 1900 * k)]), 1.1) * rise
        return reverb(body + crackle(t, 20 + 380 * rise, .0006, 1500, 8000) * .5 * rise, .5, .3, 6000)

    def impact(k):
        t = T(.55)
        blast = thump(t, 105 * k, 45, .1) + lp(white(len(t)), 3600) * env(t, .003, .12) * 1.1
        return reverb(blast + crackle(t, 700 * np.exp(-t / .1), .0006, 1500, 9000) * .6, .4, .25, 6000)

    def end(k):
        t = T(.62)
        snuff = sweep(pink(len(t)), path(t, [(0, 1500 * k), (.6, 280 * k)]), .9) * env(t, .01, .18)
        return reverb(snuff + hp(white(len(t)), 4000) * env(t, .003, .05) * .3 + crackle(t, 40, .0006, 1500, 7000, 0, .4) * .4, .35, .25, 6000)

    def strike(k):
        t = T(.3)
        whoosh = sweep(pink(len(t)), path(t, [(0, 420 * k), (.14, 1900 * k)]), .8) * swell(t, .08, .2)
        return whoosh * flutter(t, 25, .3) + thump(t, 200 * k, 80, .03, .1) * .7 + crackle(t, 200, .0005, 2000, 8000, .08, .28) * .3

    def dodge(k):
        t = T(.4)
        jet = bp(pink(len(t)), 320, 2600) * (1 + .5 * np.sin(2 * np.pi * 34 * t)) * env(t, .01, .16)
        return jet + roar(t, 250) * env(t, .01, .12) * .5

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


def quake():
    def rumble(t, cutoff=180):
        grind = bp(brown(len(t)), 180, 1400) * .35
        return (lp(brown(len(t)), cutoff) + grind) * flutter(t, 7, .6)

    def cast(k):
        t = T(.8)
        return reverb(thump(t, 72 * k, 34, .22, glide=.05) * 1.2 + rumble(t) * env(t, .02, .33) +
                      crackle(t, 50 * np.exp(-t / .2), .002, 400, 3200) * .5, .5, .3, 4000)

    def heavy(k):
        t = T(2.3)
        quake_ = rumble(t, 150) * swell(t, .12, 2.25) * 1.6 + thump(t, 60 * k, 26, .6, glide=.1) * 1.2
        cracks = crackle(t, 18, .004, 400, 4000, .05, 1.6) * .7
        return reverb(quake_ + cracks, 1.0, .35, 3000)

    def charge(k):
        t = T(1.4)
        rise = ramp(t, 0, 1.35, 1.3)
        drone = tone(path(t, [(0, 30 * k), (1.4, 62 * k)])) * .7 + rumble(t, 220)
        return reverb(drone * rise + crackle(t, 5 + 70 * rise, .002, 400, 3500) * .5 * rise, .6, .3, 3500)

    def impact(k):
        t = T(.55)
        rock = bp(white(len(t)), 300, 5000) * env(t, .001, .05) * 1.2 + thump(t, 125 * k, 50, .12) * 1.1
        return reverb(rock + crackle(t, 120 * np.exp(-t / .12), .002, 500, 4000) * .6, .4, .25, 4000)

    def end(k):
        t = T(.9)
        return reverb(rumble(t) * env(t, .02, .35) + crackle(t, 18, .002, 600, 3500, 0, .7) * .5, .5, .3, 3500)

    def strike(k):
        t = T(.3)
        return thump(t, 92 * k, 45, .07) * 1.2 + lp(white(len(t)), 1200) * env(t, .002, .04) * .6

    def dodge(k):
        t = T(.4)
        scrape = bp(white(len(t)), 420, 2600) * flutter(t, 28, .8) * swell(t, .08, .38)
        return scrape + thump(t, 80, 40, .06) * .5

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


def radiation():
    def geiger(t, rate, start=0.0, end=None):
        return grains(t, poisson(t, rate, start, end), .00015, 2000, 12000)

    def hum(t, freq):
        freq = np.broadcast_to(np.asarray(freq, dtype=float), t.shape)
        return (tone(freq) + .5 * tone(freq * 2) + .3 * tone(freq * 3) + .15 * square(freq * 4, 4)) * .5

    def cast(k):
        t = T(.72)
        glow = sweep(pink(len(t)), path(t, [(0, 300 * k), (.3, 1200 * k), (.7, 500 * k)]), .7) * swell(t, .2, .7) * .6
        return reverb(geiger(t, 90 * np.exp(-t / .3)) * .6 + hum(t, 60 * k) * swell(t, .15, .7) * .5 + glow, .45, .3, 7000)

    def heavy(k):
        t = T(2.0)
        boom = thump(t, 62, 26, .5, glide=.08) * 1.3 + lp(brown(len(t)), 650) * swell(t, .15, 1.95) * flutter(t, 10, .5)
        return reverb(boom + geiger(t, 260 * swell(t, .1, 1.9)) * .5 + hum(t, 50 * k) * swell(t, .1, 1.9) * .6, 1.0, .4, 5000)

    def charge(k):
        t = T(1.4)
        rise = ramp(t, 0, 1.35, 1.3)
        return reverb(hum(t, path(t, [(0, 45 * k), (1.4, 112 * k)])) * rise * .7 + geiger(t, 8 + 360 * rise) * .55, .5, .3, 7000)

    def impact(k):
        t = T(.5)
        return reverb(hum(t, 120 * k) * env(t, .002, .07) + geiger(t, 400, 0, .16) * .6 + thump(t, 110, 50, .07) * .8, .3, .25, 8000)

    def end(k):
        t = T(.9)
        return reverb(hum(t, path(t, [(0, 110 * k), (.85, 40 * k)])) * env(t, .02, .35) * .7 + geiger(t, 140 * np.exp(-t / .3)) * .5, .5, .3, 7000)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


def regeneration():
    def beat(t, at, k=1.0, gain=1.0):
        return gain * lp(thump(t, 62 * k, 44, .07, at, .03, knock=.6) + .7 * thump(t, 72 * k, 50, .06, at + .18, .03, knock=.6), 700)

    def pad(t, k):
        return sum(chorus(t, f * k, 3, 8, (4.5, .003), (1, .25)) * g for f, g in ((220, 1), (277.2, .7), (329.6, .6)))

    def cast(k):
        t = T(.9)
        return reverb(beat(t, 0, k, 1.4) + pad(t, k) * swell(t, .2, .88) * .35 +
                      tinkles(t, 16, 2500 * k, 6000 * k, (.06, .25), .1, .55) * .15, .5, .35, 6000)

    def heavy(k):
        t = T(1.7)
        whoosh = sweep(pink(len(t)), path(t, [(0, 300), (.6, 1400), (1.6, 500)]), .9) * swell(t, .55, 1.6) * .4
        return reverb(beat(t, 0, k, 1.6) + beat(t, .55, k, 1.8) + pad(t, k) * swell(t, .45, 1.65) * .5 + whoosh, .8, .4, 5000)

    def charge(k):
        t = T(1.4)
        times = [0, .45, .8, 1.07, 1.27]
        return reverb(sum(beat(t, at, k, .8 + .25 * i) for i, at in enumerate(times)) + pad(t, k * path(t, [(0, .9), (1.4, 1.0)])) * ramp(t, 0, 1.3, 1.5) * .3, .5, .3, 5000)

    def impact(k):
        t = T(.4)
        wet = thump(t, 92 * k, 40, .06) * 1.2 + lp(white(len(t)), 600) * env(t, .003, .05) + bp(white(len(t)), 400, 1200) * env(t, .004, .03) * .5
        return reverb(wet, .25, .2, 4000)

    def end(k):
        t = T(.8)
        exhale = sweep(pink(len(t)), path(t, [(0, 1500 * k), (.75, 320 * k)]), .9) * env(t, .06, .25) * .6
        return reverb(exhale + pad(t, k) * env(t, .02, .3) * .3, .5, .35, 5000)

    def strike(k):
        t = T(.25)
        return thump(t, 112 * k, 55, .05) * 1.2 + lp(white(len(t)), 900) * env(t, .002, .03) * .7

    def dodge(k):
        t = T(.35)
        return sweep(pink(len(t)), path(t, [(0, 260 * k), (.2, 950 * k)]), .7) * swell(t, .1, .33) + bp(white(len(t)), 800, 3000) * env(t, .002, .03, .2) * .3

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


def shapeshifting():
    def ripple(t, start, end, count, f0, f1, decel=False):
        u = np.linspace(0, 1, count) ** (1.6 if decel else .65)
        times = start + (end - start) * u + rng.normal(0, .004, count)
        freqs = path(np.linspace(0, 1, count), [(0, f0), (1, f1)]) * rng.uniform(.92, 1.08, count)
        return clicks(t, times, freqs, .014, rng.uniform(.4, 1, count))

    def slither(t, peak, end):
        return bp(white(len(t)), 2000, 6500) * swell(t, peak, end) * flutter(t, 60, .6)

    def cast(k):
        t = T(.8)
        return reverb(ripple(t, 0, .4, 24, 2400 * k, 4800 * k) * .45 + slither(t, .2, .5) * .25 +
                      (ring(t, 880 * k, .22, .42) + .6 * ring(t, 1320 * k, .16, .42)) * .35, .45, .3, 8000)

    def heavy(k):
        t = T(1.7)
        growl = bp(saw(72 * k * (1 + .02 * np.sin(2 * np.pi * 3 * t))), 300, 1500) * (1 + .5 * np.sin(2 * np.pi * 21 * t)) * swell(t, .3, 1.2)
        brass = sum(ring(t, f * k, .5, .7) * g for f, g in ((440, 1), (660, .7), (880, .5)))
        return reverb(growl * .5 + ripple(t, 0, .7, 60, 1800 * k, 5200 * k) * .4 + brass * .4, .8, .35, 7000)

    def charge(k):
        t = T(1.3)
        rise = ramp(t, 0, 1.25, 1.2)
        return reverb(ripple(t, 0, 1.25, 45, 2000 * k, 5200 * k) * .4 + slither(t, 1.2, 1.3) * .3 * rise, .4, .3, 8000)

    def impact(k):
        t = T(.45)
        clank = ring(t, 700 * k, .12) + .7 * ring(t, 1050 * k, .1) + .5 * ring(t, 1900 * k, .08)
        return reverb(clank * .6 + thump(t, 150, 70, .05) + burst(t, .01, 1000, 8000) * .5, .3, .25, 8000)

    def end(k):
        t = T(.8)
        return reverb(ripple(t, 0, .5, 20, 4200 * k, 2000 * k, True) * .45 + clicks(t, [.58], [1200 * k], .03) * .6, .45, .3, 7000)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


def sonic_resonance():
    def cello(t, freq, brightness=1.0):
        partials = tuple((1 / n) * brightness ** (n - 1) for n in range(1, 9))
        bow = bp(white(len(t)), 800, 4000) * .04
        return chorus(t, freq, 2, 4, (5.5, .005), partials) + bow

    def glass(t, freqs, decay, start=0.0):
        return sum(ring(t, f, decay, start) * g for f, g in zip(freqs, (1, .6, .4)))

    def cast(k):
        t = T(.85)
        note = (cello(t, 220 * k) + .5 * cello(t, 330 * k)) * swell(t, .06, .8) * .35
        return reverb(note + glass(t, (1760 * k, 2640 * k, 3520 * k), .4, .03) * .25, .7, .4, 9000)

    def heavy(k):
        t = T(1.8)
        chord = sum(cello(t, f * k) * g for f, g in ((110, 1), (164.8, .8), (220, .7))) * swell(t, .25, 1.75) * .35
        shimmer = tinkles(t, 26, 2000 * k, 6000 * k, (.15, .5), .1, 1.3) * .2
        return reverb(chord + shimmer + thump(t, 90, 40, .15) * .6, 1.0, .45, 8000)

    def charge(k):
        t = T(1.4)
        rise = ramp(t, 0, 1.35, 1.3)
        return reverb(cello(t, 220 * k * path(t, [(0, .97), (1.4, 1.0)]), .6 + .4 * rise.mean()) * rise * .4, .7, .4, 8000)

    def impact(k):
        t = T(.5)
        return reverb(glass(t, (1320 * k, 1980 * k, 2970 * k), .2) * .5 + thump(t, 160, 80, .04) * .6, .45, .35, 9000)

    def end(k):
        t = T(.9)
        fall = glass(t, (1760 * k, 2640 * k), .3) * .3 + sweep(white(len(t)), path(t, [(0, 4000), (.85, 900)]), .7) * env(t, .05, .3) * .4
        return reverb(fall + cello(t, path(t, [(0, 220 * k), (.9, 196 * k)])) * env(t, .02, .3) * .25, .7, .4, 8000)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


def superspeed():
    def whine(t, points):
        return tone(path(t, points))

    def cast(k):
        t = T(.42)
        zip_ = sweep(white(len(t)), path(t, [(0, 700 * k), (.12, 6500 * k), (.4, 2500 * k)]), .5) * swell(t, .07, .4)
        return zip_ + whine(t, [(0, 900 * k), (.4, 300 * k)]) * env(t, .005, .12) * .15 + crackle(t, 500 * np.exp(-t / .08), .0003, 2500, 12000) * .4

    def heavy(k):
        t = T(1.4)
        crack = white(len(t)) * env(t, .0005, .01) * 1.8 + thump(t, 110, 40, .2) * .8
        rush = sweep(pink(len(t)), path(t, [(0, 4000), (1.35, 300)]), .9) * env(t, .01, .45) * 1.1
        return reverb(crack + rush + crackle(t, 700 * np.exp(-t / .3), .0004, 2000, 12000) * .5, .7, .3, 7000)

    def charge(k):
        t = T(1.3)
        rise = ramp(t, 0, 1.25, 1.3)
        return reverb(whine(t, [(0, 300 * k), (1.3, 2400 * k)]) * rise * .3 + crackle(t, 40 + 700 * rise, .0003, 2500, 12000) * .45 * rise +
                      sweep(pink(len(t)), path(t, [(0, 400), (1.3, 3000)]), .7) * rise * .6, .4, .25, 8000)

    def impact(k):
        t = T(.4)
        return thump(t, 122 * k, 50, .07) * 1.2 + hp(white(len(t)), 3000) * env(t, .0005, .008) * .35 + crackle(t, 900 * np.exp(-t / .05), .0003, 2500, 10000) * .35

    def end(k):
        t = T(.7)
        return reverb(sweep(pink(len(t)), path(t, [(0, 3000 * k), (.65, 300 * k)]), .8) * env(t, .01, .25) + whine(t, [(0, 1600 * k), (.7, 200 * k)]) * env(t, .01, .2) * .15, .4, .25, 7000)

    def strike(k):
        t = T(.28)
        blows = sum(thump(t, 130 * k, 60, .03, at) for at in (0, .05, .1))
        return blows + sweep(white(len(t)), path(t, [(0, 1500), (.15, 7000)]), .5) * swell(t, .05, .2) * .4

    def dodge(k):
        t = T(.3)
        return sweep(white(len(t)), path(t, [(0, 800 * k), (.1, 7200 * k), (.3, 3000 * k)]), .45) * swell(t, .05, .29) + crackle(t, 400, .0003, 2500, 12000, 0, .15) * .3

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


PENTA = (880, 990, 1110, 1320, 1480, 1760, 1980, 2220, 2640)


def telekinesis():
    def thrum(t, freq):
        freq = np.broadcast_to(np.asarray(freq, dtype=float), t.shape)
        return (tone(freq + 3 * np.sin(2 * np.pi * 2.2 * t)) + .5 * tone(freq * 2.005)) * (1 + .3 * np.sin(2 * np.pi * 3 * t))

    def cast(k):
        t = T(.8)
        pull = lp(pink(len(t)), 1300) * ramp(t, 0, .32, 2.5) * (t < .34)
        return reverb(pull * .9 + thrum(t, 66 * k) * swell(t, .3, .78) * .45 +
                      sweep(white(len(t)), path(t, [(0, 1600), (.8, 500)]), .8) * env(t, .01, .2, .33) * .4, .6, .35, 5000)

    def heavy(k):
        t = T(1.8)
        suck = sweep(pink(len(t)), path(t, [(0, 200 * k), (.62, 3200 * k)]), .8) * ramp(t, 0, .62, 2.2) * (t < .62)
        boom = thump(t, 62, 25, .5, .65, .07) * 1.4 + lp(white(len(t)), 2500) * env(t, .001, .1, .65)
        return reverb(suck + boom, .9, .4, 5000)

    def charge(k):
        t = T(1.4)
        rise = ramp(t, 0, 1.35, 1.3)
        return reverb(thrum(t, path(t, [(0, 50 * k), (1.4, 110 * k)])) * rise * .6 + sweep(pink(len(t)), path(t, [(0, 300), (1.4, 1200)]), .6) * rise * .4, .6, .35, 5000)

    def impact(k):
        t = T(.45)
        return reverb(thump(t, 100 * k, 40, .1) * 1.3 + lp(white(len(t)), 1500) * env(t, .002, .05) * .8, .35, .25, 4000)

    def end(k):
        t = T(.8)
        return reverb(sweep(pink(len(t)), path(t, [(0, 1500 * k), (.75, 200 * k)]), .8) * env(t, .02, .25) + thrum(t, path(t, [(0, 90 * k), (.8, 45 * k)])) * env(t, .02, .3) * .35, .5, .3, 5000)

    def strike(k):
        t = T(.25)
        return thump(t, 140 * k, 70, .04) * 1.2 + lp(white(len(t)), 1500) * env(t, .002, .03) * .6

    def dodge(k):
        t = T(.36)
        return flange(sweep(pink(len(t)), path(t, [(0, 400 * k), (.3, 1500 * k)]), .7), .005, .8) * swell(t, .1, .34)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end, strike=strike, dodge=dodge)


def telepathy():
    def whisper(t, gain_env):
        syllables = np.clip(np.sin(phase(6.5 + 2 * lp(rng.standard_normal(len(t)), 2) * 3)), 0, None) ** 2
        f1 = 700 + 200 * np.sin(2 * np.pi * 1.3 * t)
        voice = sweep(white(len(t)), f1, .35) + .6 * sweep(white(len(t)), f1 * 2.6, .3)
        return voice * syllables * gain_env

    def cast(k):
        t = T(1.0)
        return reverb(bell(t, 880 * k, .9) * .35 + whisper(t, swell(t, .3, .95)) * .5, 1.0, .5, 8000)

    def heavy(k):
        t = T(1.9)
        cluster = sum(tone(f * k * (1 + .006 * np.sin(2 * np.pi * (4 + i) * t))) for i, f in enumerate((440, 466.2, 523.3, 554.4)))
        return reverb(cluster * swell(t, .7, 1.85) * .25 + whisper(t, swell(t, .5, 1.85)) * .7 + tone(np.full(len(t), 55 * k)) * swell(t, .4, 1.85) * .4, 1.3, .55, 7000)

    def charge(k):
        t = T(1.4)
        rise = ramp(t, 0, 1.35, 1.4)
        return reverb(whisper(t, rise) * .7 + tone(path(t, [(0, 300 * k), (1.4, 900 * k)])) * rise * .15, 1.0, .5, 8000)

    def impact(k):
        t = T(.5)
        return reverb((bell(t, 1244.5 * k, .35) + bell(t, 1318.5 * k, .3)) * .35 + hp(white(len(t)), 3000) * env(t, .001, .02) * .15, .7, .45, 9000)

    def end(k):
        t = T(.9)
        return reverb(bell(t, 440 * k, .7) * .35 + whisper(t, env(t, .02, .3)) * .4, 1.0, .5, 7000)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


def time_manipulation():
    def tick(t, at, f):
        return clicks(t, [at], [f], .007) + .3 * lp(white(len(t)), 6000) * env(t, .0005, .003, at)

    def reverse_swell(t, until):
        return sweep(pink(len(t)), path(t, [(0, 400), (until, 3000)]), .8) * ramp(t, 0, until, 2.5) * (t < until)

    def cast(k):
        t = T(.8)
        ticks = sum(tick(t, at, f * k) for at, f in ((0, 3500), (.12, 2400), (.24, 3500)))
        return reverb(ticks * .5 + reverse_swell(t, .5) * .4 + bell(t, 1568 * k, .4, .5) * .3, .6, .35, 8000)

    def heavy(k):
        t = T(1.8)
        toll = bell(t, 392 * k, 1.5, .55, (1, 2, 2.4, 3, 4.2), (1, .6, .5, .3, .2))
        return reverb(reverse_swell(t, .55) * .7 + toll * .6 + sum(tick(t, .55 + i * .25, 3000 * k) for i in range(4)) * .3, 1.1, .45, 7000)

    def charge(k):
        t = T(1.3)
        times, at, gap = [], 0.0, .2
        while at < 1.25:
            times.append(at)
            at += gap
            gap = max(gap * .85, .03)
        ticks = sum(tick(t, a, (3500 if i % 2 == 0 else 2400) * k) for i, a in enumerate(times))
        return reverb(ticks * .5 + tone(path(t, [(0, 400 * k), (1.3, 1200 * k)])) * ramp(t, 0, 1.25, 1.5) * .12, .5, .3, 8000)

    def impact(k):
        t = T(.45)
        return reverb(tick(t, 0, 3800 * k) + burst(t, .02, 1500, 9000) * .5 + bell(t, 2349 * k, .2) * .3, .4, .3, 9000)

    def end(k):
        t = T(.9)
        times = np.cumsum([0, .08, .11, .15, .21, .3])
        return reverb(sum(tick(t, a, 3000 * k) * (1 - i * .12) for i, a in enumerate(times)) * .5 +
                      tone(path(t, [(0, 1100 * k), (.9, 300 * k)])) * env(t, .01, .3) * .1, .6, .35, 8000)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


def vibe():
    def chirp(t, start, end, time, vibrato=0):
        frequency = end + (start - end) * np.exp(-t / time) + vibrato * np.sin(2 * np.pi * 11 * t)
        return np.sin(2 * np.pi * np.cumsum(frequency) / RATE)

    def grit(t):
        noise = rng.normal(0, 1, t.size)
        soft = np.convolve(noise, np.ones(21) / 21, mode="same")
        return soft * np.sin(t * np.pi * 2 * 32) + .12 * noise

    def tail(t, attack, decay):
        return np.minimum(t / attack, 1) * np.exp(-t / decay)

    def space(x):
        return echoes(x, [(.027, .16), (.049, .10), (.088, -.06)])

    def cast(k):
        t = T(.55)
        return space(.55 * chirp(t, 300 * k, 90 * k, .05) * tail(t, .008, .11) + .25 * chirp(t, 900 * k, 260 * k, .07, 14) * tail(t, .01, .09) + .2 * grit(t) * tail(t, .006, .1))

    def heavy(k):
        t = T(1.4)
        return space(.55 * chirp(t, 130 * k, 34 * k, .14, 4) * tail(t, .02, .45) + .3 * chirp(t, 520 * k, 90 * k, .2, 30) * tail(t, .02, .35) + .28 * grit(t) * tail(t, .012, .4))

    def charge(k):
        t = T(1.2)
        rise = ramp(t, 0, 1.15, 1.3)
        return space(.4 * chirp(t, 60 * k, 210 * k, .5, 4 + 20 * rise.mean()) * rise + .3 * chirp(t, 400 * k, 820 * k, .45, 18) * rise * .6 + .18 * grit(t) * rise)

    def impact(k):
        t = T(.4)
        return space(.6 * chirp(t, 220 * k, 58 * k, .03) * tail(t, .004, .08) + .25 * grit(t) * tail(t, .004, .07))

    def end(k):
        t = T(.6)
        return space(.5 * chirp(t, 600 * k, 80 * k, .06, 10) * tail(t, .012, .14) + .2 * grit(t) * tail(t, .008, .12))

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


def power_amplification():
    """Ando's red lightning and the Supercharge: an amplifier's growl. Electrokinesis is a clean saw buzz; this is a
    square-ish hum driven into soft clipping (warmer, lower, dirtier), with a rising "power-up" whine for anything it
    winds up or hands to someone, and crackle that is bit-crushed so it sounds overloaded rather than crisp. Made from
    scratch: the reference library has no amplifier sound, and nothing in it was used."""

    def growl(t, freq, drive=2.2):
        ph = phase(freq)
        raw = np.sin(ph) + .45 * np.sin(2 * ph + .3) + .3 * np.sign(np.sin(ph)) * .5
        return bp(np.tanh(raw * drive) / np.tanh(drive), 90, 5200)

    def whine(t, freq):
        ph = phase(freq)
        return np.sin(ph) * .6 + np.sin(2 * ph) * .25 + np.sin(3 * ph) * .12

    def grit(t, rate, lo=1800, hi=11000):
        # Crushed at full scale, then put back: quiet crackle would otherwise round away to silence.
        raw = crackle(t, rate, .0004, lo, hi)
        scale = np.max(np.abs(raw)) + 1e-9
        return crush(raw / scale, 4, 6) * scale

    def cast(k):
        t = T(.6)
        surge = growl(t, path(t, [(0, 85 * k), (.12, 175 * k), (.55, 120 * k)])) * env(t, .006, .14)
        lift = sweep(white(len(t)), path(t, [(0, 700 * k), (.18, 5200 * k)]), .5) * swell(t, .06, .3) * .45
        spark = grit(t, 800 * np.exp(-t / .13)) * .45
        return reverb(surge * 1.2 + lift + spark, .3, .22, 8500)

    def heavy(k):
        t = T(2.4)
        crack = white(len(t)) * env(t, .0006, .02) * 1.8 + bp(white(len(t)), 700, 8000) * env(t, .002, .08)
        hum = growl(t, np.full(len(t), 55 * k), 3) * env(t, .01, .9) * flutter(t, 7, .6) * 1.1
        rise = whine(t, path(t, [(0, 220 * k), (.7, 520 * k), (2.3, 440 * k)])) * swell(t, .5, 2.2) * .35
        sub = thump(t, 70 * k, 38 * k, .5, knock=.4)
        spark = grit(t, 1300 * np.exp(-t / .3), 1400, 12000) * .7
        return reverb(crack + hum + rise + sub + spark, 1.15, .4, 6500)

    def charge(k):
        t = T(1.4)
        up = ramp(t, 0, 1.35, 1.5)
        hum = growl(t, path(t, [(0, 48 * k), (1.4, 150 * k)]), 1.6 + 1.6 * up)
        hum *= (1 + .55 * np.sin(phase(path(t, [(0, 4), (1.4, 26)])))) * (.35 + .65 * up)
        pitch = whine(t, path(t, [(0, 170 * k), (1.4, 1150 * k)])) * up ** 1.3 * .4
        return reverb(hum + pitch + grit(t, 30 + 900 * up) * .55 * up, .35, .25, 8500)

    def impact(k):
        t = T(.45)
        snap = hp(white(len(t)), 2200) * env(t, .0005, .012) * .6
        hit = growl(t, 160 * k * (1 + .35 * np.sin(2 * np.pi * 31 * t)), 3) * env(t, .002, .07)
        return reverb(snap + hit * 1.25 + grit(t, 1500 * np.exp(-t / .06)) * .45, .25, .2, 9000)

    def end(k):
        t = T(.85)
        gate = (np.sin(phase(path(t, [(0, 18), (.8, 6)]))) > -.3).astype(float)
        down = growl(t, path(t, [(0, 190 * k), (.8, 36 * k)])) * env(t, .01, .3) * (.5 + .5 * gate)
        return reverb(down + grit(t, 420 * np.exp(-t / .18)) * .45, .35, .25, 7500)

    return dict(cast=cast, heavy=heavy, charge=charge, impact=impact, end=end)


POWERS = dict(
    power_amplification=power_amplification,
    cryokinesis=cryokinesis, electrokinesis=electrokinesis, flight=flight, hydrokinesis=hydrokinesis,
    invisibility=invisibility, matter_assembly=matter_assembly, empathic_mimicry=empathic_mimicry, phasing=phasing,
    pyrokinesis=pyrokinesis, quake=quake, radiation=radiation, regeneration=regeneration, shapeshifting=shapeshifting,
    sonic_resonance=sonic_resonance, superspeed=superspeed, telekinesis=telekinesis,
    telepathy=telepathy, time_manipulation=time_manipulation, vibe=vibe)

VARIANTS = {"cast": 3, "impact": 3, "strike": 3, "dodge": 2, "heavy": 2, "charge": 1, "end": 2}
PITCH = (1.0, 0.94, 1.065)

SUBTITLES = {
    "power_amplification": ("Power surges", "Red lightning booms", "Power builds", "Red spark snaps", "Power drains away", None, None),
    "cryokinesis": ("Frost hisses", "Ice groans and shatters", "Frost gathers", "Ice cracks", "Ice melts away", "Ice swishes", "Skates carve"),
    "electrokinesis": ("Electricity zaps", "Thunder cracks", "Charge builds", "Shock lands", "Current fades", "Spark snaps", "Lightning flashes"),
    "flight": ("Wind gusts", "Air booms", "Wind gathers", "Air slams", "Wind settles", None, None),
    "hydrokinesis": ("Water swishes", "Wave crashes", "Water gathers", "Water splashes", "Water drains away", "Water whips", "Water slides"),
    "invisibility": ("Force field hums", "Force field booms", "Force field builds", "Force field thuds", "Force field fades", "Force palm thuds", "Force field shimmers"),
    "matter_assembly": ("Matter assembles", "Construct locks together", "Matter ratchets", "Shard strikes", "Construct comes apart", None, None),
    "empathic_mimicry": ("Echo chimes", "Echoes swell", "Echoes gather", "Echo rings", "Echoes fade", None, None),
    "phasing": ("Earth moves", "Earth thuds", "Ground rumbles", "Rock lands", "Ground settles", "Stone fist swings", "Ground slides"),
    "pyrokinesis": ("Fire whooshes", "Fire roars", "Flames build", "Fire bursts", "Flames die down", "Flame punch", "Flame jets"),
    "quake": ("Ground thumps", "Earth rumbles", "Tremor builds", "Rock cracks", "Ground settles", "Pressure palm thuds", "Ground scrapes"),
    "radiation": ("Radiation crackles", "Reactor booms", "Radiation builds", "Radiation burns", "Radiation cools", None, None),
    "regeneration": ("Heart beats", "Heart surges", "Heartbeat quickens", "Wet blow lands", "Body knits", "Punch lands", "Brawler ducks"),
    "shapeshifting": ("Plates ripple", "Body transforms", "Plates shift", "Plates clank", "Plates settle", None, None),
    "sonic_resonance": ("Note rings", "Chord swells", "Note builds", "Glass rings", "Sound hushes", None, None),
    "superspeed": ("Speedster zips", "Sonic boom", "Speed builds", "Blur strike lands", "Speedster slows", "Blur punches", "Speedster blurs"),
    "telekinesis": ("Telekinesis thrums", "Telekinetic implosion", "Telekinesis builds", "Telekinetic blow", "Telekinesis releases", "Telekinetic swat", "Telekinetic slide"),
    "telepathy": ("Minds whisper", "Psychic storm", "Whispers build", "Psychic stab", "Whispers fade", None, None),
    "time_manipulation": ("Clock ticks", "Clock tolls", "Ticking quickens", "Time cracks", "Ticking slows", None, None),
    "vibe": ("Vibration pulses", "Vibration surges", "Vibration builds", "Vibration strikes", "Vibration fades", None, None),
}
CUES = ("cast", "heavy", "charge", "impact", "end", "strike", "dodge")


def seed(*parts):
    return zlib.crc32("/".join(parts).encode())


def build(selected):
    global rng
    written = {}
    for power in selected:
        recipes = POWERS[power]()
        (OUT / power).mkdir(parents=True, exist_ok=True)
        for cue, make in recipes.items():
            names = []
            for v in range(VARIANTS[cue]):
                rng = np.random.default_rng(seed(power, cue, str(v)))
                samples = master(make(PITCH[v]), LOUDNESS[cue])
                name = f"{cue}_{v + 1}"
                sf.write(OUT / power / f"{name}.ogg", samples, RATE, format="OGG", subtype="VORBIS")
                names.append(name)
            written[(power, cue)] = names
            print(f"{power:18s} {cue:7s} {len(names)}")
    return written


def every_cue():
    """Every cue of every power, whatever this run rebuilt (sounds.json lists them all)."""
    out = {}
    for power, make in POWERS.items():
        for cue in make():
            out[(power, cue)] = [f"{cue}_{v + 1}" for v in range(VARIANTS[cue])]
    return out


def update_sounds_json():
    file = ASSETS / "sounds.json"
    sounds = json.loads(file.read_text(encoding="utf-8"))
    for key in [k for k in sounds if k.startswith("power.")]:
        del sounds[key]
    for (power, cue), names in every_cue().items():
        entry = {"subtitle": f"subtitles.heroesevolved.power.{power}.{cue}",
                 "sounds": [f"heroesevolved:power/{power}/{n}" for n in names]}
        sounds[f"power.{power}.{cue}"] = entry
    file.write_text(json.dumps(sounds, indent=2) + "\n", encoding="utf-8")


def update_lang():
    file = ASSETS / "lang/en_us.json"
    text = file.read_text(encoding="utf-8")
    text = re.sub(r'\n\s*"subtitles\.heroesevolved\.power\.[^"]+": "[^"]*",?', "", text)
    lines = []
    for power in POWERS:
        for cue, subtitle in zip(CUES, SUBTITLES[power]):
            if subtitle is not None:
                lines.append(f'  "subtitles.heroesevolved.power.{power}.{cue}": "{subtitle}"')
    body = text.rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip()
    if not body.endswith(","):
        body += ","
    file.write_text(body + "\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")


if __name__ == "__main__":
    chosen = sys.argv[1:] or list(POWERS)
    unknown = [p for p in chosen if p not in POWERS]
    if unknown:
        sys.exit(f"unknown powers: {unknown}")
    build(chosen)
    update_sounds_json()
    update_lang()
