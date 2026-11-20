"""Builds the Last Day's music and two of its sounds: original synthesis, no samples.

    py -3 tools/generate-lastday-music.py

Writes src/main/resources/assets/heroesevolved/sounds/story/lastday_*.ogg (mono, 44.1 kHz).

Provenance: the two music stems answer the need the user's reference showed (Music/Individual Layers and Alternate
Encodings, Music_06, two of its layers from Gemini: Heroes Reborn): an uneasy evening bed, and a faster layer that comes in
over it when the action starts, the two sharing one tempo and key so the second can join the first at any moment. They are
designed to the numbers measured from those files (scratch analysis, not kept): about 81 seconds, 75 bpm with a 150 bpm
double-time feel in the percussion, A minor with an F-A-C colour, roots moving A, D, G and E; the bed low and dark (most of
its energy under 250 Hz, centroid near 160 Hz, about -22 dBFS RMS), the action layer steady in the highs with its mid-range
swelling through the middle (centroid near 440 Hz, about -21 dBFS). Nothing was copied, resampled or layered from those
files: every chord, part and motif here is our own, synthesized below.

- lastday_evening  the bed: low strings and a soft bass on our own A-minor progression, a slow pulse, a few far-off piano
                   notes of our own motif. Plays from the evening until the safe house.
- lastday_run      the action: sixteenth-note hats and shaker, low drums, a staccato low-string ostinato on the roots,
                   brass swells and a high tremolo building to the loop's end. Starts with the bed (silent) and comes up
                   over it when Peter and Sylar meet; both are loops of exactly 96 beats (76.8 s).
- lastday_shatter  every window on the avenue bursting at once: a pressure boom, then a cascade of glass, thinning out.
- lastday_ringing  the ringing in your ears after it: a high tone that fades over eight seconds, a heartbeat under it.
"""
from pathlib import Path
import importlib.util
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "build" / "audio-tools"))
import numpy as np  # noqa: E402
from scipy.signal import butter, lfilter, sosfilt  # noqa: E402

_spec = importlib.util.spec_from_file_location("story_cues", Path(__file__).with_name("generate-story-cues.py"))
cues = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cues)
hz, time, env, lowpass, bandpass, voice, pad, mallet, thump, finish = (cues.hz, cues.time, cues.env, cues.lowpass, cues.bandpass,
                                                                      cues.voice, cues.pad, cues.mallet, cues.thump, cues.finish)
RATE = cues.RATE
RNG = np.random.default_rng(614)

BPM = 75
BEAT = 60 / BPM
BAR = 4 * BEAT
BARS = 24
LOOP = BARS * BAR          # 76.8 s
TAIL = 6.0

# Our progression, a chord a bar: the street at evening (A), something wrong (B), and the build back round to A.
CHORDS = [
    ("A", ["A2", "E3", "C4"]), ("A", ["A2", "E3", "C4"]), ("F", ["F2", "C3", "A3"]), ("G", ["G2", "D3", "B3"]),
    ("A", ["A2", "E3", "C4"]), ("A", ["A2", "C3", "E3"]), ("D", ["D3", "F3", "A3"]), ("E", ["E2", "B2", "G#3"]),
    ("F", ["F2", "A2", "C3"]), ("G", ["G2", "B2", "D3"]), ("A", ["A2", "E3", "C4"]), ("G", ["G2", "C3", "E3"]),
    ("F", ["F2", "A2", "C3"]), ("G", ["G2", "B2", "D3"]), ("E", ["E2", "B2", "G#3"]), ("E", ["E2", "B2", "D3"]),
    ("A", ["A2", "E3", "C4"]), ("F", ["F2", "C3", "A3"]), ("C", ["C3", "E3", "G3"]), ("G", ["G2", "D3", "B3"]),
    ("D", ["D3", "F3", "A3"]), ("F", ["F2", "A2", "C3"]), ("E", ["E2", "B2", "G#3"]), ("E", ["E2", "G#2", "D3"]),
]
ROOT = {"A": "A1", "F": "F1", "G": "G1", "D": "D2", "E": "E1", "C": "C2"}


def fast_reverb(x, wet=0.28, size=1.0):
    """The same Schroeder room as the story cues' (four combs into two allpasses), filtered instead of looped."""
    out = np.zeros_like(x)
    for delay_ms, feedback in ((29.7, 0.80), (37.1, 0.78), (41.1, 0.76), (43.7, 0.74)):
        d = int(RATE * delay_ms * size / 1000)
        a = np.zeros(d + 1); a[0] = 1; a[d] = -feedback
        out += lfilter([1.0], a, x) / 4
    for delay_ms, g in ((5.0, 0.7), (1.7, 0.7)):
        d = int(RATE * delay_ms / 1000)
        b = np.zeros(d + 1); b[0] = -g; b[d] = 1
        a = np.zeros(d + 1); a[0] = 1; a[d] = -g
        out = lfilter(b, a, out)
    return (1 - wet) * x + wet * lowpass(out, 5000)


def wrap(x):
    """Folds what rings on past the loop back onto its start, so it joins itself without a seam."""
    n = int(LOOP * RATE)
    head = x[:n].copy()
    head[:len(x) - n] += x[n:]
    return head


def add(out, start, sig):
    """Adds `sig` into `out` from `start` seconds."""
    i = int(start * RATE)
    if i >= len(out):
        return
    j = min(len(out), i + len(sig))
    out[i:j] += sig[:j - i]


def note_env(length, attack=0.01, release=0.08):
    n = int(length * RATE)
    t = np.arange(n) / RATE
    e = np.minimum(1, t / max(attack, 1e-4)) * np.clip((length - t) / max(release, 1e-4), 0, 1)
    return t, e


# ----------------------------------------------------------------------
# The evening's bed
# ----------------------------------------------------------------------

def evening():
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    for i, (root, chord) in enumerate(CHORDS):
        start = i * BAR
        seg_len = BAR + 1.6
        tt = np.arange(int(seg_len * RATE)) / RATE
        fade = np.clip(tt / 1.1, 0, 1) ** 1.5 * np.clip((seg_len - tt) / 1.4, 0, 1)
        # Low strings, warm and closed, legato from bar to bar.
        strings = sum(voice(tt, hz(n), detune=6, harmonics=7, tilt=1.5) for n in chord) / len(chord)
        add(out, start, 0.75 * lowpass(strings, 1300) * fade)
        # The bass under it: a soft sine with a little second harmonic, held the whole bar.
        f = hz(ROOT[root])
        bass = np.sin(2 * np.pi * f * tt) + 0.25 * np.sin(2 * np.pi * 2 * f * tt) + 0.08 * np.sin(2 * np.pi * 3 * f * tt)
        add(out, start, 0.55 * bass * np.clip(tt / 0.25, 0, 1) * np.clip((seg_len - tt) / 1.2, 0, 1))
    # A slow pulse, a soft felt beat every half bar (the street's heartbeat), quieter in the middle phrase.
    for k in range(BARS * 2):
        s = k * 2 * BEAT
        level = 0.32 if 8 * BAR <= s < 16 * BAR else 0.45
        add(out, s, level * thump(time(1.2), 0, f=52, knock=380, level=1.0))
    # Our motif, far off on a piano-like mallet: two phrases, the second answering the first, and a third that hangs.
    motif = {0: [("E5", 0), ("C5", 2)], 1: [("D5", 0), ("B4", 3)], 2: [("C5", 0), ("A4", 2)], 3: [("B4", 0), ("D5", 2.5)],
             4: [("E5", 0), ("A5", 2)], 5: [("G5", 0), ("E5", 2)], 6: [("F5", 0), ("D5", 2)], 7: [("E5", 0)],
             16: [("A4", 0), ("C5", 1.5), ("E5", 3)], 17: [("F5", 0), ("C5", 2)], 18: [("E5", 0), ("G5", 2)], 19: [("D5", 0)],
             20: [("F5", 0), ("A5", 2)], 21: [("C6", 0), ("A5", 2)], 22: [("G#5", 0), ("E5", 2)], 23: [("B4", 0), ("D5", 2)]}
    for bar, notes in motif.items():
        for name, beat in notes:
            add(out, bar * BAR + beat * BEAT, 0.20 * mallet(time(3.0), hz(name), 0, decay=1.5, bright=0.5))
    # In the uneasy middle, a held high note that won't settle, and a low cluster under it.
    for name, bar, bars, level in (("E5", 8, 4, 0.05), ("F5", 12, 3, 0.05), ("D#5", 15, 1, 0.04)):
        tt = time(bars * BAR)
        tone = np.sin(2 * np.pi * hz(name) * tt) + np.sin(2 * np.pi * hz(name) * 1.003 * tt + 1.1)
        add(out, bar * BAR, level * tone * np.sin(np.pi * np.clip(tt / (bars * BAR), 0, 1)) ** 1.4)
    out += 0.03 * bandpass(RNG.normal(0, 1, len(t)), 250, 1800)
    marks = [0, 4 * BAR, 8 * BAR, 12 * BAR, 16 * BAR, 20 * BAR, LOOP]
    level = [0.8, 0.9, 0.75, 0.85, 1.0, 0.9, 0.8]
    arc = np.interp(np.mod(t, LOOP), marks, level)
    x = fast_reverb(out * arc, 0.32, 1.25)
    finish("lastday_evening", wrap(x), -27.0, loop=True)


# ----------------------------------------------------------------------
# The fight's layer over it
# ----------------------------------------------------------------------

def hat(length=0.06, bright=1.0):
    n = int(length * RATE)
    tt = np.arange(n) / RATE
    noise = sosfilt(butter(2, 7000 if bright >= 1 else 5200, btype="high", fs=RATE, output="sos"), RNG.normal(0, 1, n))
    return noise * np.exp(-tt * (70 if bright >= 1 else 45))


def drum(f=58, knock=300, length=0.9):
    return thump(time(length), 0, f=f, knock=knock, level=1.0)


def snare(length=0.35):
    n = int(length * RATE)
    tt = np.arange(n) / RATE
    body = np.sin(2 * np.pi * 190 * tt) * np.exp(-tt * 28)
    rattle = bandpass(RNG.normal(0, 1, n), 1500, 7000) * np.exp(-tt * 16)
    return 0.6 * body + 0.5 * rattle


def staccato(f, length):
    """A short low-string note: a bowed saw cut short, a quick bite at the front."""
    tt, e = note_env(length, 0.006, 0.05)
    x = np.zeros_like(tt)
    for k in range(1, 12):
        if f * k > 6000:
            break
        x += np.sin(2 * np.pi * f * k * tt) / k ** 1.1
    e = e * (0.7 + 0.3 * np.exp(-tt * 30))
    return lowpass(x * e, 4200)


def brass(notes, length):
    """A swell of low brass: stacked saws whose filter opens as it rises and closes as it falls."""
    tt = np.arange(int(length * RATE)) / RATE
    x = sum(voice(tt, hz(n), detune=5, harmonics=10, tilt=1.0) for n in notes) / len(notes)
    shape = np.sin(np.pi * np.clip(tt / length, 0, 1)) ** 1.3
    bright = lowpass(x, 3600)
    dark = lowpass(x, 900)
    return (shape * bright + (1 - shape) * dark) * shape


def run_layer():
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    six = BEAT / 4
    for bar in range(BARS):
        b0 = bar * BAR
        root = CHORDS[bar][0]
        phrase = bar // 8
        # Hats on every sixteenth (the 150 bpm feel), accents on the eighths, a looser shaker under them.
        for s in range(16):
            accent = 1.0 if s % 2 == 0 else 0.55
            add(out, b0 + s * six, 0.34 * accent * hat(0.05 if s % 4 else 0.09))
            if s % 2 == 1:
                add(out, b0 + s * six + 0.012, 0.16 * hat(0.08, bright=0.5))
        # Low drums: our own figure, a boom on the one, a push before the three, and more of it as it builds.
        kicks = [0, 6, 8, 11] if phrase == 0 else [0, 3, 6, 8, 11, 14] if phrase == 1 else [0, 3, 6, 8, 10, 11, 13, 14]
        for s in kicks:
            add(out, b0 + s * six, (0.7 if s in (0, 8) else 0.5) * drum(f=56 if s in (0, 8) else 64, knock=420))
        for s in (4, 12):
            add(out, b0 + s * six, (0.45 if phrase == 0 else 0.6) * snare())
        if bar % 4 == 3:
            # A fill into the next phrase: the last beat in sixteenths.
            for s in range(12, 16):
                add(out, b0 + s * six, 0.3 * snare(0.2))
        # The ostinato: sixteenths on the root, an octave jump and the fifth, staccato, after the first two bars.
        if bar >= 2:
            f = hz(ROOT[root]) * 2
            figure = [1, 1, 2, 1, 1.5, 1, 2, 1, 1, 1, 2, 1, 1.5, 1, 2, 1.5]
            for s, mult in enumerate(figure):
                add(out, b0 + s * six, (0.40 if phrase == 0 else 0.48) * staccato(f * mult, six * 0.9))
        # Brass swells on the chord changes of the middle and last phrases.
        if phrase >= 1 and bar % 2 == 0:
            notes = [n[:-1] + str(int(n[-1]) + 0) for n in CHORDS[bar][1]]
            add(out, b0, (0.46 if phrase == 1 else 0.56) * brass(notes, 2 * BAR))
        # The last phrase: a high tremolo that tightens toward the seam.
        if phrase == 2:
            tt = time(BAR)
            name = "E5" if bar % 2 == 0 else "F5"
            rate = 12 + (bar - 16) * 0.8
            trem = 0.5 + 0.5 * np.sin(2 * np.pi * rate * tt)
            tone = voice(tt, hz(name), detune=8, harmonics=6, tilt=1.3)
            add(out, b0, 0.16 * lowpass(tone, 5000) * trem * np.clip(tt / 0.2, 0, 1) * np.clip((BAR - tt) / 0.2, 0, 1))
    marks = [0, 2 * BAR, 8 * BAR, 12 * BAR, 16 * BAR, 22 * BAR, LOOP]
    level = [0.82, 0.86, 0.9, 1.0, 0.95, 1.05, 0.82]
    arc = np.interp(np.mod(t, LOOP), marks, level)
    x = fast_reverb(out * arc, 0.18, 0.9)
    finish("lastday_run", wrap(x), -24.0, loop=True)


# ----------------------------------------------------------------------
# The wave
# ----------------------------------------------------------------------

def tink(f, length=0.08, level=1.0):
    """One shard: a few inharmonic partials, struck, gone in a moment."""
    n = int(length * RATE)
    tt = np.arange(n) / RATE
    x = np.zeros(n)
    for ratio, amp, k in ((1.0, 1.0, 1.0), (1.47, 0.6, 1.3), (2.09, 0.4, 1.7), (2.83, 0.25, 2.2)):
        x += amp * np.sin(2 * np.pi * f * ratio * tt + RNG.uniform(0, 6.28)) * np.exp(-tt * 55 * k)
    return level * x * np.minimum(1, tt * 4000)


def shatter():
    t = time(3.2)
    out = np.zeros_like(t)
    # The pressure wave: a deep boom you feel, with a knock and a roar of air.
    out += 1.2 * thump(t, 0.0, f=44, knock=420, level=1.0)
    out += 0.35 * lowpass(RNG.normal(0, 1, len(t)), 600) * env(t, 0.01, 0.03, 1.2, curve=2.5)
    # The crash: bright noise as a hundred panes give at once, falling away.
    crash = sosfilt(butter(2, 2200, btype="high", fs=RATE, output="sos"), RNG.normal(0, 1, len(t)))
    out += 0.45 * crash * env(t, 0.005, 0.06, 1.1, curve=2.2)
    # The shards: dense at first, then thinning as the last pieces hit the street.
    for i in range(520):
        when = 0.04 + RNG.exponential(0.45)
        if when > 3.0:
            continue
        f = RNG.uniform(2300, 8200)
        level = 0.22 * np.exp(-when / 1.4) * RNG.uniform(0.4, 1.0)
        add(out, when, tink(f, RNG.uniform(0.04, 0.12), level))
    finish("lastday_shatter", fast_reverb(out, 0.22, 1.4), -16.0)


def ringing():
    t = time(9.0)
    # The tone: high and thin, a second faint one beating against it, fading slowly once the first seconds pass.
    fade = np.where(t < 1.6, 1.0, np.exp(-(t - 1.6) / 2.4))
    fade *= np.minimum(1, t / 0.03)
    tone = np.sin(2 * np.pi * 4100 * t) + 0.35 * np.sin(2 * np.pi * 4127 * t + 0.7) + 0.08 * np.sin(2 * np.pi * 8200 * t)
    x = 0.42 * tone * fade
    # Under it, muffled: a heartbeat, and the low rumble of a city you can't quite hear.
    for k, beat in enumerate(np.arange(0.4, 8.6, 0.82)):
        lvl = 0.5 * (0.6 + 0.4 * np.exp(-beat / 4))
        x += lvl * thump(t, beat, f=48, knock=180, level=1.0) + 0.6 * lvl * thump(t, beat + 0.19, f=55, knock=200, level=0.8)
    x += 0.10 * lowpass(RNG.normal(0, 1, len(t)), 180) * np.clip(t / 0.5, 0, 1) * np.clip((9 - t) / 2, 0, 1)
    finish("lastday_ringing", x, -24.0)


if __name__ == "__main__":
    evening()
    run_layer()
    shatter()
    ringing()
