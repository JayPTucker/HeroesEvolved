"""Builds Sylar's theme: three phase loops, a phase stinger, his last chord and the record of the whole fight.
Original synthesis, no samples.

    py -3 tools/generate-sylar-music.py

Writes src/main/resources/assets/heroesevolved/sounds/music/sylar_*.ogg (mono, 44.1 kHz), played by
client/sound/SylarMusic while a fighter is in the Collection (the record by a jukebox).

Provenance: the theme answers the need the user's reference library showed (Music/Gameplay Score, from Gemini: Heroes
Reborn). Its score banks come in states of one piece at rising intensity (Exploration, Combat, Stealth), the model for a
boss's phase loops; the boss theme escalates the way those states do. Designed to the numbers measured from them (scratch
analysis, not kept):
  - Score 04 Exploration and Stealth: about 80 s, 68 bpm, D minor, dark (most energy under 250 Hz), about -20 dBFS,
    percussion about 27% of the energy, 6 high onsets a second, a loudness that builds about 5 dB over the piece;
  - Score 04, 06 and 08 Combat: 80 to 85 s, D minor or A minor, -16 to -22 dBFS, percussion 34 to 44% of the energy,
    7 to 14% of it between 1 and 4 kHz, 9 to 15 low onsets and 5 to 7 high ones a second, flat loudness (3 to 5 dB range);
  - the bank's stingers (about 8 s, a peak in the low mids) and short cues (about 5 s) for the stinger and last chord.
Nothing was copied, resampled or layered from those files: every chord, part, motif and drum pattern here is our own.

- sylar_viewing     Viewing Hours: a watch's tick-tock, low strings on our D-minor progression, a music-box motif, a far
                    bell, distant drums and clockwork chimes that build through the loop and fall back to its start.
- sylar_unlocked    Unlocked: the same 24 bars with taiko-style drums, toms, claps, hats and shaker, a spiccato ostinato,
                    clockwork anvils on the off-beats, brass, and crashes and swells at the turns.
- sylar_masterwork  The Masterwork: all of it with the pulse doubled, his motif in the brass, a small choir, a high
                    tremolo, a crash every four bars.
- sylar_stinger     A phase change: a clock's strike, a drum hit, a brass rise and the choir, about 7 s.
- sylar_last_chord  His death: the watch's last ticks, a bell, D minor settling to an open fifth, then silence.
- sylar_record      The record (a rare re-fight drop): eight bars of each phase, the stinger between, the last chord.

All three loops are exactly 96 beats (24 bars at 68 bpm, 84.7 s), on one chart and in three 8-bar sections, so each can
follow another.
"""
from pathlib import Path
import importlib.util
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "build" / "audio-tools"))
import numpy as np  # noqa: E402
from scipy.signal import butter, sosfilt  # noqa: E402


def _load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


cues = _load("story_cues", "generate-story-cues.py")
lastday = _load("lastday_music", "generate-lastday-music.py")
cues.OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/heroesevolved/sounds/music"
hz, time, env, lowpass, bandpass, voice, pad, mallet, bell, thump, sung, finish = (
    cues.hz, cues.time, cues.env, cues.lowpass, cues.bandpass, cues.voice, cues.pad, cues.mallet, cues.bell, cues.thump,
    cues.sung, cues.finish)
hat, drum, snare, brass, add, note_env, fast_reverb = (
    lastday.hat, lastday.drum, lastday.snare, lastday.brass, lastday.add, lastday.note_env, lastday.fast_reverb)
RATE = cues.RATE
RNG = np.random.default_rng(1406 + 614)

BPM = 68
BEAT = 60 / BPM
BAR = 4 * BEAT
SIX = BEAT / 4
BARS = 24
LOOP = BARS * BAR          # 84.7 s
TAIL = 6.0

# Our progression, a chord a bar, in D minor: the hall (D), the cases (B-flat, C), a look closer (G minor, A), the turn
# through E-flat in the middle, and the long A at the end of each phrase that pulls back round to D.
CHORDS = [
    ("D", ["D3", "F3", "A3"]), ("D", ["D3", "A3", "F4"]), ("A#", ["A#2", "D3", "F3"]), ("C", ["C3", "E3", "G3"]),
    ("D", ["D3", "F3", "A3"]), ("G", ["G2", "D3", "A#3"]), ("A", ["A2", "E3", "C#4"]), ("A", ["A2", "C#3", "E3"]),
    ("A#", ["A#2", "F3", "D4"]), ("F", ["F2", "C3", "A3"]), ("G", ["G2", "A#2", "D3"]), ("D", ["D3", "F3", "A3"]),
    ("D#", ["D#3", "G3", "A#3"]), ("D", ["D3", "F3", "A3"]), ("A", ["A2", "E3", "C#4"]), ("A", ["A2", "C#3", "G3"]),
    ("D", ["D3", "F3", "A3"]), ("A#", ["A#2", "D3", "F3"]), ("G", ["G2", "D3", "A#3"]), ("C", ["C3", "E3", "G3"]),
    ("D", ["D3", "A3", "F4"]), ("A#", ["A#2", "F3", "D4"]), ("A", ["A2", "E3", "C#4"]), ("A", ["A2", "C#3", "E3"]),
]
ROOT = {"D": "D2", "A#": "A#1", "C": "C2", "G": "G1", "A": "A1", "F": "F1", "D#": "D#2"}

# His motif: a note asked three times, then a falling answer. Beats from the bar's start, with lengths in beats.
MOTIF = [(0, "A4", 0.5), (0.5, "A4", 0.5), (1, "A#4", 0.5), (1.5, "A4", 1.5), (3, "F4", 0.5), (3.5, "E4", 0.5)]
ANSWER = [(0, "D4", 1), (1, "F4", 0.5), (1.5, "E4", 0.5), (2, "C#4", 1), (3, "A3", 1)]


def wrap(x):
    """Folds what rings on past the loop onto its start, so the loop joins itself."""
    n = int(LOOP * RATE)
    head = x[:n].copy()
    head[:len(x) - n] += x[n:]
    return head


def highpass(x, cutoff, order=2):
    return sosfilt(butter(order, cutoff, btype="high", fs=RATE, output="sos"), x)


def up(note, octaves=1):
    return note[:-1] + str(int(note[-1]) + octaves)


# ----------------------------------------------------------------------
# The kit: clockwork, drums and metal
# ----------------------------------------------------------------------

def tick(level=1.0, high=True):
    """A watch's tick (high) or tock (lower): a short resonant click."""
    n = int(0.045 * RATE)
    tt = np.arange(n) / RATE
    centre = 4400 if high else 3100
    click = bandpass(RNG.normal(0, 1, n), centre * 0.8, centre * 1.25) * np.exp(-tt * 170)
    ring = np.sin(2 * np.pi * centre * 1.5 * tt) * np.exp(-tt * 230)
    return level * (click + 0.4 * ring)


def anvil(f=1700, length=0.6, level=1.0):
    """Clockwork metal: a small struck plate, inharmonic partials falling at their own rates."""
    tt = time(length)
    x = np.zeros_like(tt)
    for ratio, amp, decay in ((1.0, 1.0, 9), (2.32, 0.6, 14), (3.91, 0.4, 20), (5.4, 0.25, 26)):
        x += amp * np.sin(2 * np.pi * f * ratio * tt) * np.exp(-tt * decay)
    x *= 1 - np.exp(-tt * 900)
    return level * x


def taiko(f=46, length=1.6, level=1.0):
    """A big drum: a falling body, a skin of filtered noise, and a slap at the front that reads on small speakers."""
    tt = time(length)
    sweep = f * (1 + 0.9 * np.exp(-tt * 14))
    body = np.sin(2 * np.pi * np.cumsum(sweep) / RATE) * np.exp(-tt * 6.0)
    skin = bandpass(RNG.normal(0, 1, len(tt)), 90, 700) * np.exp(-tt * 13)
    slap = bandpass(RNG.normal(0, 1, len(tt)), 1300, 5000) * np.exp(-tt * 55)
    return level * (body + 0.9 * skin + 0.6 * slap)


def tom(f=110, length=0.6, level=1.0):
    tt = time(length)
    sweep = f * (1 + 0.4 * np.exp(-tt * 20))
    body = np.sin(2 * np.pi * np.cumsum(sweep) / RATE) * np.exp(-tt * 8)
    skin = bandpass(RNG.normal(0, 1, len(tt)), 200, 2400) * np.exp(-tt * 22)
    return level * (body + 0.4 * skin)


def clap(level=1.0):
    """Three quick bursts of bright noise, then a short tail."""
    tt = time(0.4)
    x = np.zeros_like(tt)
    for k, at in enumerate((0, 0.011, 0.023)):
        on = tt >= at
        x += np.where(on, np.exp(-(tt - at) * (160 if k < 2 else 26)), 0)
    return level * bandpass(RNG.normal(0, 1, len(tt)), 900, 7000) * x


def shaker(level=1.0):
    tt = time(0.09)
    return level * highpass(RNG.normal(0, 1, len(tt)), 6500) * np.sin(np.pi * np.clip(tt / 0.09, 0, 1)) ** 2


def crash(length=3.2, level=1.0):
    """A cymbal: bright noise and a few metallic partials, falling slowly."""
    tt = time(length)
    noise = highpass(RNG.normal(0, 1, len(tt)), 3200)
    metal = sum(np.sin(2 * np.pi * f * tt + RNG.uniform(0, 6)) for f in (3150, 4720, 6310, 7900)) * 0.08
    return level * (noise + metal) * np.exp(-tt * 1.4) * (1 - np.exp(-tt * 600))


def swell(length=2 * BAR / 4 * 2, level=1.0):
    """A cymbal played backwards: noise rising into the next downbeat."""
    tt = time(length)
    rise = (tt / length) ** 3
    return level * highpass(RNG.normal(0, 1, len(tt)), 2800) * rise


# ----------------------------------------------------------------------
# The orchestra
# ----------------------------------------------------------------------

def strings(length, notes, cutoff=900):
    """A held string chord with a gentle swell, for the bed."""
    tt = time(length)
    x = pad(tt, notes, detune=6, cutoff=cutoff)
    shape = np.clip(tt / 0.6, 0, 1) * np.clip((length - tt) / 0.8, 0, 1)
    return x * shape


def spiccato(f, length, bright=7000):
    """A short bowed note: a saw cut short, a bite at the front, brighter than the bed."""
    tt, e = note_env(length, 0.004, 0.04)
    x = np.zeros_like(tt)
    for k in range(1, 16):
        if f * k > 9000:
            break
        x += np.sin(2 * np.pi * f * k * tt) / k ** 1.05
    return lowpass(x * e * (0.65 + 0.35 * np.exp(-tt * 40)), bright)


def horns(notes, length, bright=5200):
    """Brass that opens up: stacked saws whose filter rises with the swell, a little edge on top."""
    tt = np.arange(int(length * RATE)) / RATE
    x = sum(voice(tt, hz(n), detune=5, harmonics=14, tilt=0.95) for n in notes) / len(notes)
    shape = np.sin(np.pi * np.clip(tt / length, 0, 1)) ** 1.2
    return (shape * lowpass(x, bright) + (1 - shape) * lowpass(x, 1000)) * shape


def stab(notes, level=1.0):
    """A short brass hit on an accent."""
    tt = np.arange(int(0.45 * RATE)) / RATE
    x = sum(voice(tt, hz(n), detune=4, harmonics=14, tilt=0.9) for n in notes) / len(notes)
    return level * lowpass(x, 5000) * np.exp(-tt * 7) * (1 - np.exp(-tt * 300))


def line(motif, instrument, octave=0):
    """Plays a motif (beats, note, length) on {@code instrument(f, length)} into one bar's worth of sound."""
    out = np.zeros(int((BAR + 3) * RATE))
    for at, note, length in motif:
        add(out, at * BEAT, instrument(hz(up(note, octave)), length * BEAT))
    return out


def brass_note(f, length):
    tt = np.arange(int((length + 0.25) * RATE)) / RATE
    x = sum(np.sin(2 * np.pi * f * k * tt) / k ** 0.9 for k in range(1, 14) if f * k < 8000)
    e = np.clip(tt / 0.05, 0, 1) * np.clip((length + 0.25 - tt) / 0.25, 0, 1)
    return lowpass(x * e, 4200)


# ----------------------------------------------------------------------
# The layers
# ----------------------------------------------------------------------

def section(bar):
    return bar // 8


def bed(out, density):
    """What every phase shares: the ticking, the strings, the bass, the music box and the bell."""
    eighth = BEAT / 2
    for bar in range(BARS):
        b0 = bar * BAR
        root, notes = CHORDS[bar]
        sec = section(bar)
        # The watch: tick-tock on the eighths; ghost sixteenths come in as the first phase builds; all sixteenths later.
        step = SIX if density >= 3 else eighth
        for k in range(int(BAR / step + 0.5)):
            add(out, b0 + k * step, (0.28 if density == 1 else 0.42) * tick(1.0 if k % 2 == 0 else 0.75, high=k % 2 == 0))
        if density == 1 and sec >= 1:
            for k in range(8):
                add(out, b0 + (2 * k + 1) * SIX, (0.10 if sec == 1 else 0.16) * tick(0.6, high=True))
        add(out, b0, (0.34 if density == 1 else 0.26) * strings(BAR + 0.4, notes, 700 if density == 1 else 2400))
        bass_t, bass_e = note_env(BAR, 0.08, 0.6)
        add(out, b0, 0.28 * lowpass(voice(bass_t, hz(ROOT[root]), detune=3, harmonics=6, tilt=1.6), 300) * bass_e)
        # The music box: the motif on the first bar of each four, its answer on the third.
        motif = MOTIF if bar % 4 == 0 else ANSWER if bar % 4 == 2 else None
        if motif:
            tt = time(BAR + 2.5)
            box = sum(mallet(tt, hz(n), at * BEAT, decay=1.1 + length * 0.4) for at, n, length in motif)
            add(out, b0, (0.17 if density == 1 else 0.12) * box)
        if bar % 8 == 7:
            tt = time(4.0)
            add(out, b0 + 2 * BEAT, 0.11 * (bell(tt, hz("A4"), 0, decay=2.2) + 0.6 * bell(tt, hz("D4"), 0.02, decay=2.6)))


def distant(out):
    """The first phase's build: far drums on the downbeats, clockwork chimes, a swell into each section."""
    for bar in range(BARS):
        b0 = bar * BAR
        sec = section(bar)
        if sec >= 1:
            add(out, b0, (0.30 if sec == 1 else 0.42) * lowpass(taiko(44, 1.8), 900))
            if sec == 2:
                add(out, b0 + 2 * BEAT, 0.26 * lowpass(taiko(52, 1.2), 900))
        if bar % 2 == 1:
            add(out, b0 + 2 * BEAT + SIX, (0.10 + 0.04 * sec) * anvil(1650))
        if bar % 8 == 7 and bar < BARS - 1:
            add(out, b0 + BAR - 2 * BEAT, 0.16 * swell(2 * BEAT))


def drums(out, density):
    """The fight's kit: big drums, toms, claps, hats and shaker, clockwork anvils, crashes and swells at the turns."""
    for bar in range(BARS):
        b0 = bar * BAR
        sec = section(bar)
        root, notes = CHORDS[bar]
        kicks = [0, 6, 8, 11] if density == 2 else [0, 3, 6, 8, 10, 11, 14]
        for s in kicks:
            big = s in (0, 8)
            add(out, b0 + s * SIX, (0.84 if big else 0.58) * taiko(46 if big else 56, 1.4 if big else 0.9))
        for s in (4, 12):
            add(out, b0 + s * SIX, (0.56 if density == 2 else 0.64) * clap())
            add(out, b0 + s * SIX, 0.25 * snare(0.25))
        for s in range(16):
            accent = 1.0 if s % 4 == 0 else 0.75 if s % 2 == 0 else 0.5
            add(out, b0 + s * SIX, (0.60 if density == 2 else 0.70) * accent * hat(0.05 if s % 4 else 0.09))
            if s % 2 == 1:
                add(out, b0 + s * SIX + 0.01, 0.30 * shaker())
        # Clockwork on the off-beats: the second and fourth beats' "and".
        for s in (6, 14) if density == 2 else (2, 6, 10, 14):
            add(out, b0 + s * SIX, 0.26 * anvil(1700 if s % 8 == 6 else 2150, 0.45))
        if bar % 4 == 3:
            for k, s in enumerate((10, 11, 12, 13, 14, 15)):
                add(out, b0 + s * SIX, 0.34 * tom(160 - k * 14))
        if bar % 8 == 0:
            add(out, b0, (0.30 if density == 2 else 0.36) * crash())
        elif density >= 3 and bar % 4 == 0:
            add(out, b0, 0.26 * crash(2.4))
        if bar % 8 == 7 and bar < BARS - 1:
            add(out, b0 + BAR - 2 * BEAT, 0.26 * swell(2 * BEAT))


def ostinato(out, density):
    """Spiccato sixteenths on the root and its octave, a turn on the fifth; the cellos double them below."""
    for bar in range(BARS):
        if bar % 8 == 0 and density < 3:
            continue
        b0 = bar * BAR
        f = hz(ROOT[CHORDS[bar][0]]) * 4
        figure = [1, 1, 2, 1, 1, 1.5, 1, 2, 1, 1, 2, 1, 1.5, 1, 2, 1]
        for s, mult in enumerate(figure):
            add(out, b0 + s * SIX, (0.36 if density == 2 else 0.40) * spiccato(f * mult, SIX * 0.85))
            add(out, b0 + s * SIX, 0.18 * spiccato(f * mult / 2, SIX * 0.85, 3500))
            if density >= 3 and s % 2 == 0:
                add(out, b0 + s * SIX, 0.14 * spiccato(f * mult * 2, SIX * 0.6, 9000))


def brass_layer(out, density):
    """Horns swelling on the chord changes from the second section; stabs on the accents in the third."""
    for bar in range(BARS):
        b0 = bar * BAR
        root, notes = CHORDS[bar]
        sec = section(bar)
        if (sec >= 1 and bar % 2 == 0) or density >= 3:
            length = (2 if density == 2 else 1) * BAR
            add(out, b0, (0.36 if density == 2 else 0.30) * horns([up(n) for n in notes], length))
        if sec == 2 or density >= 3:
            for s in (0, 6, 11):
                add(out, b0 + s * SIX, (0.22 if density == 2 else 0.26) * stab([up(n) for n in notes]))


def masterwork_layers(out):
    """Only the third phase: his motif in the brass, a small choir on the chord tones, a high tremolo to the seam."""
    for bar in range(BARS):
        b0 = bar * BAR
        motif = MOTIF if bar % 4 == 0 else ANSWER if bar % 4 == 2 else None
        if motif and section(bar) != 1:
            add(out, b0, 0.20 * line(motif, brass_note))
    for bar in range(0, BARS, 2):
        b0 = bar * BAR
        root, notes = CHORDS[bar]
        tt = time(2 * BAR + 1)
        add(out, b0, sung(tt, hz(up(notes[-1])), 0.1, 2 * BAR, "ah", voices=3, level=0.14))
        add(out, b0, sung(tt, hz(up(notes[1])), 0.3, 2 * BAR - 0.3, "oo", voices=2, level=0.12))
    for bar in range(16, BARS):
        tt = time(BAR)
        name = "D6" if bar % 2 == 0 else "C#6"
        rate = 10 + (bar - 16) * 0.9
        trem = 0.5 + 0.5 * np.sin(2 * np.pi * rate * tt)
        tone = voice(tt, hz(name), detune=8, harmonics=6, tilt=1.2)
        add(out, bar * BAR, 0.16 * lowpass(tone, 7000) * trem * np.clip(tt / 0.2, 0, 1) * np.clip((BAR - tt) / 0.2, 0, 1))


def arc(t, levels):
    marks = [0, 4 * BAR, 8 * BAR, 12 * BAR, 16 * BAR, 20 * BAR, 22 * BAR, LOOP]
    return np.interp(np.mod(t, LOOP), marks, levels)


# ----------------------------------------------------------------------
# The pieces
# ----------------------------------------------------------------------

def render_viewing():
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    bed(out, 1)
    distant(out)
    # It builds through the loop, the way the reference's quiet state does, then falls back to join its start.
    return fast_reverb(out * arc(t, [0.70, 0.76, 0.84, 0.92, 1.0, 1.08, 1.0, 0.70]), 0.30, 1.1)


def render_unlocked():
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    bed(out, 2)
    drums(out, 2)
    ostinato(out, 2)
    brass_layer(out, 2)
    return fast_reverb(out * arc(t, [0.9, 0.92, 1.0, 1.0, 1.02, 1.05, 1.0, 0.9]), 0.2, 1.0)


def render_masterwork():
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    bed(out, 3)
    drums(out, 3)
    ostinato(out, 3)
    brass_layer(out, 3)
    masterwork_layers(out)
    return fast_reverb(out * arc(t, [0.95, 0.97, 1.0, 1.02, 1.0, 1.05, 1.02, 0.95]), 0.2, 1.0)


def render_stinger():
    t = time(7.0)
    x = 0.55 * (bell(t, hz("D3"), 0.0, decay=3.5) + 0.5 * bell(t, hz("A3"), 0.02, decay=3.0) + 0.3 * bell(t, hz("D4"), 0.04, decay=2.4))
    x += 0.3 * bell(t, hz("D5"), 0.05, decay=2.0)
    add(x, 0.0, 0.7 * taiko(42, 2.4))
    add(x, 0.0, 0.3 * crash(4.0))
    add(x, 0.25, 0.55 * horns(["D3", "A3", "D4", "F4"], 4.6))
    x += sung(t, hz("D5"), 0.6, 4.0, "ah", voices=3, level=0.2) + sung(t, hz("A4"), 0.9, 3.6, "oh", voices=3, level=0.16)
    for k, beat in enumerate((1.6, 1.85, 2.1, 2.35)):
        add(x, beat, (0.25 + 0.06 * k) * tick(1.0, high=k % 2 == 0))
    add(x, 2.6, 0.5 * taiko(50, 1.8))
    return fast_reverb(x, 0.3, 1.2)


def render_last_chord():
    t = time(7.5)
    x = np.zeros_like(t)
    at = 0.0
    for k in range(6):
        add(x, at, (0.32 - 0.04 * k) * tick(1.0, high=k % 2 == 0))
        at += 0.36 + 0.12 * k
    x += 0.35 * bell(t, hz("D4"), 0.9, decay=3.6)
    minor = pad(t, ["D2", "A2", "D3", "F3", "A3"], detune=5, cutoff=1400) * env(t, 1.2, 2.0, 4.2, curve=1.6)
    fifth = pad(t, ["D2", "A2", "D3", "A3"], detune=4, cutoff=1000) * env(t, 1.5, 3.6, 7.3, curve=1.8)
    x += 0.8 * minor + 0.7 * fifth
    x += sung(t, hz("F4"), 1.0, 3.2, "oo", voices=2, level=0.07)
    return fast_reverb(x, 0.32, 1.2)


def level_to(x, rms_db):
    """Scales a part to a loudness (above 150 Hz, as finish measures it) so pieces joined in the record match."""
    audible = highpass(x, 150)
    return x * 10 ** (rms_db / 20) / (np.sqrt(np.mean(audible ** 2)) + 1e-12)


def record(viewing, unlocked, masterwork, stinger, last):
    """The whole fight on one record: eight bars of each phase, the stinger at each turn, the last chord."""
    eight = int(8 * BAR * RATE)
    fade = int(0.6 * RATE)
    parts = [level_to(viewing, -29.0)[:eight], level_to(unlocked, -24.5)[eight:2 * eight],
             level_to(masterwork, -23.5)[2 * eight:3 * eight]]
    out = np.zeros(3 * eight + len(last) + RATE)
    for k, part in enumerate(parts):
        start = k * eight
        seg = part.copy()
        seg[:fade] *= np.linspace(0, 1, fade) if k else 1
        seg[-fade:] *= np.linspace(1, 0, fade)
        out[start:start + len(seg)] += seg
        if k:
            sting = level_to(stinger, -24.0)
            out[start - int(0.3 * RATE):start - int(0.3 * RATE) + len(sting)] += 0.8 * sting
    end = 3 * eight
    out[end:end + len(last)] += level_to(last, -25.0)
    return out


if __name__ == "__main__":
    viewing = render_viewing()
    finish("sylar_viewing", wrap(viewing), -27.0, loop=True)
    unlocked = render_unlocked()
    finish("sylar_unlocked", wrap(unlocked), -22.5, loop=True)
    masterwork = render_masterwork()
    finish("sylar_masterwork", wrap(masterwork), -22.0, loop=True)
    stinger = render_stinger()
    finish("sylar_stinger", stinger, -23.0)
    last = render_last_chord()
    finish("sylar_last_chord", last, -23.0)
    finish("sylar_record", record(viewing, unlocked, masterwork, stinger, last), -22.5)
