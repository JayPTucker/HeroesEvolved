"""Builds the Wither's theme, "The Hungry Cage": three phase loops, a phase stinger, its last chord, the aftermath that
plays once it is dead, and the record. Original synthesis, no samples.

    py -3 tools/generate-wither-music.py

Writes src/main/resources/assets/heroesevolved/sounds/music/wither_*.ogg (mono, 44.1 kHz), played by
client/sound/WitherMusic while a fighter is in the Ossuary (the record by a jukebox).

Provenance: the user picked four files from their reference library (Gemini: Heroes Reborn; Music/Individual Layers
and Alternate Encodings and Music/Stingers and Transitions) on 2026-11-25: two Music_06 layers for the fight, a third
for after it dies, and Score 01's Stinger 03. Measured (scratch analysis, not kept):
  fight layer 1 (183300580): 81.6 s, A minor, a 75 bpm pulse driven in sixteenths, -20.8 dBFS, centroid 444 Hz, 60% of
    the energy at 60-250 Hz, 25% at 250 Hz-1 kHz, 7% at 1-4 kHz, 22% percussion, 4.1 low and 2.8 high onsets a second,
    an 8 dB loudness arc: a hush, a hit, a build, two sudden full stops two thirds in and near the end, a crescendo.
  fight layer 2 (432375702): 8 s: one impact, then a pulsing low drone (36% percussion, 38% at 250 Hz-1 kHz).
  after (1048779095): 81.6 s, A minor with a B-flat (Phrygian), 75 bpm, -21.8 dBFS, centroid 164 Hz, 88% under 250 Hz,
    11% percussion, half an onset a second: slow blocks of low chords re-struck bar by bar with short gaps.
  stinger (Score 01 Stinger 03): 6.4 s, -23.1 dBFS, centroid 478 Hz, 74% at 250 Hz-1 kHz, 34% percussion: one bright
    hit on a suspended chord ringing away.
The theme is designed to those shapes and numbers. Nothing was copied, resampled or layered from the references: every
chord, part, motif and drum pattern here is our own.

The idea: a broken cage round a hungry star. A minor with the Phrygian B-flat always pulling at it (the hunger is a half
step); the heartbeat is three hits and a gap, 3-3-2, one for each head; bone rattles and chain clanks are the kit's
metal. Its horn call climbs to a half step at the top (E to F, the star's pull) and falls home through B-flat to A.

- wither_hunger    Threefold Hunger: a relentless low pulse, big drums on the 3-3-2 heartbeat, bone rattles and chains,
                   low strings from the first hit, the horn call; a hush, a hit, a build, two full stops that slam back.
- wither_vessel    The Broken Vessel: the same 24 bars with the full kit, a spiccato ostinato, horns and stabs, a low
                   choir, and at each turn an impact that leaves a pulsing low drone.
- wither_critical  Critical Mass: all of it with the drums doubled, the call in the high brass and the choir chanting
                   it, a high string tremolo, a rising sub into each turn.
- wither_stinger   A phase change: one bright hit on a suspended chord, rattles and chains tumbling off it, about 6 s.
- wither_last_chord Its death: the heartbeat failing, a bell, A minor opening to a bare fifth.
- wither_aftermath After it dies: slow blocks of low chords (A minor, B-flat, F, D minor) re-struck bar by bar, a low
                   choir, a far bell; a seamless loop while the way out stands open.
- wither_record    The record: eight bars of each phase, the stinger between, the last chord, the aftermath.

All three fight loops are exactly 96 beats (24 bars at 75 bpm, 76.8 s) on one chart in three 8-bar sections, so each
can follow another; client/sound/WitherMusic changes them on the next bar (64 ticks).
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
kit = _load("sylar_music", "generate-sylar-music.py")
cues.OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/heroesevolved/sounds/music"
hz, time, env, lowpass, bandpass, voice, pad, mallet, bell, sung, finish = (
    cues.hz, cues.time, cues.env, cues.lowpass, cues.bandpass, cues.voice, cues.pad, cues.mallet, cues.bell,
    cues.sung, cues.finish)
hat, snare, add, note_env, fast_reverb = lastday.hat, lastday.snare, lastday.add, lastday.note_env, lastday.fast_reverb
taiko, tom, clap, shaker, crash, swell, horns, stab, spiccato, strings, brass_note, up = (
    kit.taiko, kit.tom, kit.clap, kit.shaker, kit.crash, kit.swell, kit.horns, kit.stab, kit.spiccato, kit.strings,
    kit.brass_note, kit.up)
RATE = cues.RATE
RNG = np.random.default_rng(3616)

BPM = 75
BEAT = 60 / BPM
BAR = 4 * BEAT
SIX = BEAT / 4
BARS = 24
LOOP = BARS * BAR          # 76.8 s
TAIL = 6.0

# Chords (B-flat is written A#): the low voicings the strings and horns hold.
VOICE = {"Am": ["A2", "E3", "C4"], "Bb": ["A#2", "F3", "D4"], "Dm": ["D3", "A3", "F4"], "F": ["F2", "C3", "A3"],
         "C": ["C3", "G3", "E4"], "G": ["G2", "D3", "B3"], "E": ["E2", "B2", "G#3"], "Esus": ["E2", "B2", "A3"],
         "Gm": ["G2", "D3", "A#3"]}
ROOT = {"Am": "A1", "Bb": "A#1", "Dm": "D2", "F": "F1", "C": "C2", "G": "G1", "E": "E1", "Esus": "E1", "Gm": "G1"}
# Three sections: the cage (A against B-flat), the call (the turn through F and C), the hunger (the long fall to E,
# which pulls back round to A).
CHART = ["Am", "Am", "Bb", "Am", "Dm", "Bb", "Esus", "E",
         "Am", "F", "C", "G", "Am", "F", "Bb", "E",
         "Dm", "Bb", "F", "C", "Am", "Bb", "Am", "E"]
CHORDS = [(c, VOICE[c]) for c in CHART]

# The horn call: up to the half step at the top, then down through B-flat home. Beats from the bar's start, lengths in beats.
CALL = [(0, "A4", 1), (1, "E5", 1.5), (2.5, "F5", .5), (3, "E5", 1)]
HOME = [(0, "D5", .5), (.5, "C5", .5), (1, "A#4", 1), (2, "A4", 2)]
# The heartbeat: three hits and a gap, in sixteenths.
HEART = [0, 3, 6]
# The full stops: the last beat of these bars falls silent, and the next downbeat slams back in.
STOPS = (15, 19)


def wrap(x):
    n = int(LOOP * RATE)
    head = x[:n].copy()
    head[:len(x) - n] += x[n:]
    return head


def highpass(x, cutoff, order=2):
    return sosfilt(butter(order, cutoff, btype="high", fs=RATE, output="sos"), x)


def section(bar):
    return bar // 8


# ----------------------------------------------------------------------
# The bone kit
# ----------------------------------------------------------------------

def bone(level=1.0, pitch=1.0):
    """A dry knock of bone on bone: a hollow wooden resonance and a click."""
    tt = time(0.16)
    f = 760 * pitch
    body = np.sin(2 * np.pi * f * tt) * np.exp(-tt * 55) + 0.5 * np.sin(2 * np.pi * f * 2.37 * tt) * np.exp(-tt * 80)
    click = bandpass(RNG.normal(0, 1, len(tt)), 1800, 6000) * np.exp(-tt * 260)
    return level * (body + 0.7 * click)


def rattle(level=1.0):
    """A ribcage shaken: five bone knocks tumbling over each other."""
    out = np.zeros(int(0.3 * RATE))
    for k in range(5):
        add(out, k * 0.028 + RNG.uniform(0, 0.008), (1 - k * 0.15) * bone(0.6, 0.85 + RNG.uniform(0, 0.4)))
    return level * out


def chain(level=1.0):
    """A chain pulled taut: a cluster of small metal rings, jangling and fading."""
    tt = time(0.5)
    x = np.zeros_like(tt)
    for k in range(7):
        at = k * 0.018 + RNG.uniform(0, 0.01)
        f = RNG.uniform(2300, 4800)
        on = tt >= at
        x += np.where(on, np.sin(2 * np.pi * f * (tt - at)) * np.exp(-(tt - at) * RNG.uniform(14, 26)), 0)
    return level * (x * 0.25 + 0.3 * highpass(RNG.normal(0, 1, len(tt)), 5000) * np.exp(-tt * 18))


def sub_rise(length):
    """A sub-bass that climbs and tightens into the next downbeat."""
    tt = time(length)
    f = 32 + 22 * (tt / length) ** 2
    x = np.sin(2 * np.pi * np.cumsum(f) / RATE) + 0.35 * np.sin(4 * np.pi * np.cumsum(f) / RATE)
    return np.tanh(1.6 * x) * (tt / length) ** 1.5


def staccato_bass(f, length):
    """The bass on the heartbeat: a short growl with a bite, saturated so it reads on small speakers."""
    tt, e = note_env(length, 0.005, 0.06)
    x = voice(tt, f, detune=4, harmonics=10, tilt=1.1)
    return np.tanh(2.2 * lowpass(x, 900)) * e * (0.7 + 0.3 * np.exp(-tt * 18))


def drone_pulse(notes, length, rate=7.0):
    """An impact's afterglow: low strings and horns throbbing on one chord, the throb slowing as it fades."""
    tt = time(length)
    tone = sum(voice(tt, hz(n), detune=6, harmonics=9, tilt=1.2) for n in notes) / len(notes)
    throb = 0.55 + 0.45 * np.sin(2 * np.pi * np.cumsum(rate * (1 - 0.45 * tt / length)) / RATE)
    return lowpass(tone, 1400) * throb * np.clip(tt / 0.08, 0, 1) * np.exp(-tt * 0.35) * np.clip((length - tt) / 0.6, 0, 1)


def impact(level=1.0, chord="Am"):
    """A section's turn: drums, a crash, a low brass stab, a rattle."""
    x = np.zeros(int(4 * RATE))
    add(x, 0, 1.0 * taiko(40, 2.0))
    add(x, 0, 0.55 * taiko(58, 1.2))
    add(x, 0, 0.32 * crash(3.0))
    add(x, 0, 0.42 * stab([up(n) for n in VOICE[chord]]))
    add(x, 0.02, 0.4 * rattle())
    return level * x


# ----------------------------------------------------------------------
# The layers
# ----------------------------------------------------------------------

def pulse(out, density):
    """The relentless low pulse under everything: the root in sixteenths (eighths in the first hush), B-flat leaning on
    A every fourth, an octave leap on the accents in the last phase."""
    half = 2 ** (1 / 12)
    for bar in range(BARS):
        b0 = bar * BAR
        root = hz(ROOT[CHORDS[bar][0]]) * 2
        step = 2 if density == 1 and section(bar) == 0 else 1
        for s in range(0, 16, step):
            f = root * (half if s % 8 == 6 else 1) * (2 if density >= 3 and s % 4 == 2 else 1)
            accent = 1.0 if s % 4 == 0 else 0.62
            add(out, b0 + s * SIX, (0.2 + 0.03 * density) * accent * staccato_bass(f, SIX * 0.82))


def bed(out, density):
    """Low strings from the first hit (bar 4), the call from the second section, a bell at each section's end."""
    for bar in range(BARS):
        b0 = bar * BAR
        root, notes = CHORDS[bar]
        sec = section(bar)
        if bar >= 4 or density >= 2:
            add(out, b0, (0.22 if density == 1 else 0.17) * strings(BAR + 0.5, notes, 2000 + 500 * density))
        motif = CALL if bar % 4 == 0 else HOME if bar % 4 == 2 else None
        if motif and (density >= 2 or sec >= 1):
            line = np.zeros(int((BAR + 3) * RATE))
            for at, n, length in motif:
                add(line, at * BEAT, brass_note(hz(up(n, -1)), length * BEAT))
            add(out, b0, (0.21 if density == 1 else 0.17) * line)
        if bar % 8 == 7:
            tt = time(4.0)
            add(out, b0 + 2 * BEAT, 0.12 * (bell(tt, hz("A5"), 0, decay=2.0) + 0.7 * bell(tt, hz("A#5"), 0.03, decay=2.2)))


def heartbeat(out, density):
    """The big drums: 3-3-2 on the first half bar, both halves once the hush is over, more in the last phase."""
    for bar in range(BARS):
        b0 = bar * BAR
        sec = section(bar)
        hush = density == 1 and bar < 4
        hits = HEART + ([] if hush else [8 + h for h in HEART] if density >= 2 or sec >= 1 else [8])
        if density >= 3:
            hits = sorted(set(hits + [2, 10, 13, 15]))
        for s in hits:
            big = s in (0, 8)
            add(out, b0 + s * SIX, (0.85 if hush else 1.0 if big else 0.7) * taiko(44 if big else 58, 1.3 if big else 0.8))
        if bar % 8 == 7 and bar < BARS - 1:
            add(out, b0 + BAR - 2 * BEAT, (0.22 + 0.06 * density) * swell(2 * BEAT))


def bones(out, density):
    """The cage's metal and bone: rattles on the back beats, chains on the gaps, knocks on the sixteenths in between."""
    for bar in range(BARS):
        b0 = bar * BAR
        sec = section(bar)
        for s in (4, 12):
            add(out, b0 + s * SIX, (0.30 + 0.06 * density) * rattle())
        if bar % 2 == 1 or density >= 2:
            add(out, b0 + 7 * SIX, (0.18 + 0.05 * density) * chain())
        if density == 1 and bar >= 4:
            step = 2 if sec >= 1 else 4
            for s in range(1, 16, step):
                add(out, b0 + s * SIX, 0.22 * bone(0.8 if s % 4 == 3 else 0.5, 1.2))
            for s in range(0, 16, 2):
                add(out, b0 + s * SIX + 0.005, 0.18 * shaker())
                if sec >= 1:
                    add(out, b0 + s * SIX, (0.28 if s % 4 == 0 else 0.18) * hat(0.05))


def kit_layer(out, density):
    """The full kit from the second phase: claps and snare on the back beats, hats, shaker, toms, crashes."""
    for bar in range(BARS):
        b0 = bar * BAR
        for s in (4, 12):
            add(out, b0 + s * SIX, (0.6 if density == 2 else 0.68) * clap())
            add(out, b0 + s * SIX, 0.3 * snare(0.25))
        for s in range(16):
            accent = 1.0 if s % 4 == 0 else 0.7 if s % 2 == 0 else 0.45
            if density == 2 and s % 2 == 1 and bar % 2 == 0:
                continue
            add(out, b0 + s * SIX, (0.5 if density == 2 else 0.6) * accent * hat(0.05 if s % 4 else 0.08))
            if s % 2 == 1:
                add(out, b0 + s * SIX + 0.008, 0.24 * shaker())
            if density >= 3 and s % 4 == 2:
                add(out, b0 + s * SIX, 0.15 * bone(0.7, 1.4))
        if bar % 4 == 3 and bar not in STOPS:
            for k, s in enumerate((10, 11, 12, 13, 14, 15)):
                add(out, b0 + s * SIX, 0.34 * tom(170 - k * 16))
        if density >= 3 and bar % 4 == 0 and bar % 8:
            add(out, b0, 0.26 * crash(2.4))


def ostinato(out, density):
    """Spiccato sixteenths: the root, its octave and the half step above, the cellos doubling below."""
    for bar in range(BARS):
        if density < 3 and bar % 8 == 0:
            continue
        b0 = bar * BAR
        f = hz(ROOT[CHORDS[bar][0]]) * 4
        half = 2 ** (1 / 12)
        figure = [1, 1, 2, 1, half, 1, 2, 1, 1, 1, 2, 1, half, 1, 1.5, 2]
        for s, mult in enumerate(figure):
            add(out, b0 + s * SIX, (0.3 if density == 2 else 0.34) * spiccato(f * mult, SIX * 0.85))
            add(out, b0 + s * SIX, 0.15 * spiccato(f * mult / 2, SIX * 0.85, 3200))
            if density >= 3 and s % 2 == 0:
                add(out, b0 + s * SIX, 0.11 * spiccato(f * mult * 2, SIX * 0.6, 9000))


def brass_layer(out, density):
    """Horns holding the changes (the long tones the first reference brings in after its opening); stabs later."""
    for bar in range(BARS):
        b0 = bar * BAR
        root, notes = CHORDS[bar]
        sec = section(bar)
        if bar % 2 == 0 or density >= 3:
            length = (2 if density == 2 else 1) * BAR
            add(out, b0, (0.3 if density == 2 else 0.26) * horns([up(n) for n in notes], length))
        if sec >= 1 or density >= 3:
            for s in (0, 6, 8):
                add(out, b0 + s * SIX, (0.2 if density == 2 else 0.24) * stab([up(n) for n in notes]))


def choir(out, density):
    """A low choir on the chord tones from the second phase; in the last, it chants the call with the brass."""
    for bar in range(0, BARS, 2):
        b0 = bar * BAR
        root, notes = CHORDS[bar]
        tt = time(2 * BAR + 1)
        add(out, b0, sung(tt, hz(notes[1]), 0.1, 2 * BAR, "oh", voices=3, level=0.12 if density == 2 else 0.14))
        add(out, b0, sung(tt, hz(up(notes[-1])), 0.3, 2 * BAR - 0.3, "ah", voices=3, level=0.10 if density == 2 else 0.13))
    if density >= 3:
        for bar in range(BARS):
            motif = CALL if bar % 4 == 0 else HOME if bar % 4 == 2 else None
            if not motif:
                continue
            b0 = bar * BAR
            line = np.zeros(int((BAR + 3) * RATE))
            for at, n, length in motif:
                add(line, at * BEAT, brass_note(hz(n), length * BEAT))
                tt = time(length * BEAT + 0.6)
                add(line, at * BEAT, sung(tt, hz(n), 0.0, length * BEAT + 0.3, "ah", voices=3, level=0.9))
            add(out, b0, 0.16 * line)


def critical(out):
    """Only the last phase: a high string tremolo climbing through each section, a sub rising into each turn."""
    for bar in range(BARS):
        tt = time(BAR)
        name = "A5" if bar % 2 == 0 else "A#5"
        rate = 9 + (bar % 8) * 0.8
        trem = 0.5 + 0.5 * np.sin(2 * np.pi * rate * tt)
        tone = voice(tt, hz(name), detune=8, harmonics=6, tilt=1.2)
        level = 0.055 + 0.011 * (bar % 8)
        add(out, bar * BAR, level * lowpass(tone, 7000) * trem * np.clip(tt / 0.15, 0, 1) * np.clip((BAR - tt) / 0.15, 0, 1))
        if bar % 8 == 7 or bar in STOPS:
            add(out, bar * BAR, 0.34 * sub_rise(BAR))


def turns(hits, density):
    """The impacts at each section's turn and after each full stop; from the second phase each leaves a pulsing drone."""
    for bar in (4, 8, 16, 20):
        if density == 1 and bar == 8:
            continue
        b0 = bar * BAR
        chord = CHORDS[bar][0]
        add(hits, b0, (0.62 + 0.06 * density) * impact(1.0, chord))
        if density >= 2:
            add(hits, b0 + 0.05, (0.16 + 0.03 * density) * drone_pulse([up(n) for n in VOICE[chord][:2]], 2 * BAR))


def stops(t):
    """The full stops: the last beat of each stop bar falls silent (a few milliseconds' fade each side)."""
    gate = np.ones_like(t)
    for bar in STOPS:
        a, b = bar * BAR + 3 * BEAT, (bar + 1) * BAR
        fall = np.clip((t - a) / 0.03, 0, 1) * np.clip((b - 0.01 - t) / 0.03, 0, 1)
        gate *= 1 - 0.96 * fall
    return gate


def arc(t, levels):
    """The loudness arc: a hush, the hit, a dip, the build, the peak with its stops, the rise back round."""
    marks = [0, 4 * BAR, 4 * BAR + 0.5, 8 * BAR, 16 * BAR, 20 * BAR, 23 * BAR, LOOP]
    return np.interp(np.mod(t, LOOP), marks, levels)


# ----------------------------------------------------------------------
# The pieces
# ----------------------------------------------------------------------

def fight(density, levels, wet, size):
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    pulse(out, density)
    bed(out, density)
    heartbeat(out, density)
    bones(out, density)
    if density >= 2:
        kit_layer(out, density)
        ostinato(out, density)
        brass_layer(out, density)
        choir(out, density)
    else:
        brass = np.zeros_like(t)
        for bar in range(8, BARS, 2):
            add(brass, bar * BAR, 0.22 * horns(CHORDS[bar][1], 2 * BAR, 2600))
        out += brass
    if density >= 3:
        critical(out)
    out *= stops(t)
    hits = np.zeros_like(t)
    turns(hits, density)
    return fast_reverb((out + hits) * arc(t, levels), wet, size)


def render_hunger():
    return fight(1, [0.7, 0.74, 0.88, 0.82, 0.98, 1.06, 1.1, 0.7], 0.24, 1.15)


def render_vessel():
    return fight(2, [0.7, 0.74, 0.9, 0.84, 1.0, 1.06, 1.08, 0.7], 0.2, 1.05)


def render_critical():
    return fight(3, [0.78, 0.82, 0.95, 0.9, 1.02, 1.06, 1.08, 0.78], 0.2, 1.0)


def render_stinger():
    """One bright hit on a suspended chord ringing away, rattles and chain ticks tumbling off it."""
    t = time(6.5)
    x = np.zeros_like(t)
    add(x, 0.0, 0.8 * taiko(40, 2.4))
    add(x, 0.0, 0.5 * taiko(62, 1.0))
    add(x, 0.0, 0.1 * crash(2.6))
    add(x, 0.0, 0.4 * stab(["A3", "E4", "G4", "D5"]))
    chord = ["A2", "E3", "A3", "D4", "E4", "G4", "C5"]
    add(x, 0.03, 0.55 * horns(chord[2:], 5.4, 2800))
    add(x, 0.03, 0.4 * strings(5.8, chord, 1600))
    for k in range(9):
        at = 0.08 + k * 0.17 + RNG.uniform(0, 0.03)
        add(x, at, (0.42 - 0.04 * k) * (rattle() if k % 3 == 0 else bone(0.7, 1.1 + 0.05 * k)))
        if k % 2 == 0:
            add(x, at + 0.05, (0.3 - 0.03 * k) * chain())
    x += sung(t, hz("E4"), 0.2, 4.6, "ah", voices=4, level=0.14)
    return fast_reverb(x, 0.3, 1.2)


def render_last_chord():
    t = time(8.0)
    x = np.zeros_like(t)
    # The heartbeat failing: three hits, slower and softer each time, then nothing where the fourth should be.
    at = 0.0
    for k in range(3):
        add(x, at, (0.6 - 0.15 * k) * taiko(44 - 3 * k, 1.6))
        at += 0.55 + 0.25 * k
    x += 0.3 * bell(t, hz("A4"), 1.9, decay=3.8) + 0.18 * bell(t, hz("E5"), 1.95, decay=3.2)
    minor = pad(t, ["A1", "E2", "A2", "C3", "E3"], detune=5, cutoff=1400) * env(t, 1.8, 2.8, 5.2, curve=1.6)
    fifth = pad(t, ["A1", "E2", "A2", "E3"], detune=4, cutoff=1000) * env(t, 2.2, 4.4, 7.8, curve=1.8)
    x += 0.8 * minor + 0.7 * fifth
    x += sung(t, hz("C4"), 2.0, 3.2, "oo", voices=3, level=0.08) + sung(t, hz("A3"), 2.3, 4.6, "oo", voices=3, level=0.07)
    return fast_reverb(x, 0.34, 1.25)


# After it dies: slow blocks of low chords, re-struck each bar with a short gap, a low choir, a far bell.
AFTER = ["Am", "Am", "Bb", "Bb", "F", "F", "Dm", "Dm",
         "Am", "Am", "Bb", "Bb", "Gm", "Gm", "Dm", "Dm",
         "Am", "Am", "F", "F", "Bb", "Bb", "Am", "Am"]
AFTER_VOICE = {"Am": ["A1", "E2", "A2", "C3"], "Bb": ["A#1", "F2", "A#2", "D3"], "F": ["F1", "C2", "F2", "A2"],
               "Dm": ["D2", "A2", "D3", "F3"], "Gm": ["G1", "D2", "G2", "A#2"]}


def render_aftermath():
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    for bar in range(BARS):
        b0 = bar * BAR
        notes = AFTER_VOICE[AFTER[bar]]
        length = BAR - 0.14
        tt = time(length + 0.6)
        # The block: a dark pad and low strings, struck at the bar and cut just before the next.
        block = 1.6 * pad(tt, notes, detune=1.5, cutoff=900)
        shape = np.clip(tt / 0.12, 0, 1) * np.clip((length - tt) / 0.12, 0, 1) * (0.9 + 0.1 * np.exp(-tt * 1.5))
        add(out, b0, 0.5 * block * shape)
        add(out, b0, 0.2 * strings(length, notes[1:], 1100))
        add(out, b0, 0.16 * strings(length, [up(n) for n in notes[1:]], 2200))
        # A sub drone on the root under the change of chord, every other bar.
        if bar % 2 == 0:
            st = time(2 * BAR)
            f = hz(notes[0])
            add(out, b0, 0.24 * np.sin(2 * np.pi * f * st) * np.clip(st / 0.4, 0, 1) * np.clip((2 * BAR - st) / 0.5, 0, 1))
        # A low choir on the third every four bars, and a soft knock where the heartbeat used to be.
        if bar % 4 == 0:
            ct = time(4 * BAR)
            add(out, b0, sung(ct, hz(up(notes[2])), 0.3, 4 * BAR - 0.6, "oo", voices=3, level=0.1))
        if bar % 8 == 4:
            add(out, b0, 0.14 * taiko(40, 2.2))
        if bar % 8 == 6:
            bt = time(5.0)
            add(out, b0 + BEAT, 0.05 * bell(bt, hz("E5"), 0, decay=3.0))
    swell_arc = np.interp(np.mod(t, LOOP), [0, 8 * BAR, 12 * BAR, 16 * BAR, 20 * BAR, LOOP], [0.9, 1.0, 1.12, 0.92, 1.0, 0.9])
    return fast_reverb(out * swell_arc, 0.32, 1.3)


def level_to(x, rms_db):
    audible = highpass(x, 150)
    return x * 10 ** (rms_db / 20) / (np.sqrt(np.mean(audible ** 2)) + 1e-12)


def record(hunger, vessel, crit, stinger, last, after):
    eight = int(8 * BAR * RATE)
    fade = int(0.5 * RATE)
    parts = [level_to(hunger, -24.0)[:eight], level_to(vessel, -23.5)[eight:2 * eight], level_to(crit, -23.0)[2 * eight:3 * eight]]
    tail = level_to(after, -27.0)[:eight]
    out = np.zeros(3 * eight + len(last) + eight + RATE)
    for k, part in enumerate(parts):
        start = k * eight
        seg = part.copy()
        if k:
            seg[:fade] *= np.linspace(0, 1, fade)
        seg[-fade:] *= np.linspace(1, 0, fade)
        out[start:start + len(seg)] += seg
        if k:
            sting = level_to(stinger, -24.0)
            out[start - int(0.3 * RATE):start - int(0.3 * RATE) + len(sting)] += 0.8 * sting
    end = 3 * eight
    out[end:end + len(last)] += level_to(last, -25.0)
    begin = end + len(last) - int(2 * RATE)
    seg = tail.copy()
    seg[:2 * RATE] *= np.linspace(0, 1, 2 * RATE)
    seg[-4 * RATE:] *= np.linspace(1, 0, 4 * RATE)
    out[begin:begin + len(seg)] += seg
    return out


if __name__ == "__main__":
    hunger = render_hunger()
    finish("wither_hunger", wrap(hunger), -25.8, loop=True)
    vessel = render_vessel()
    finish("wither_vessel", wrap(vessel), -23.9, loop=True)
    crit = render_critical()
    finish("wither_critical", wrap(crit), -23.4, loop=True)
    stinger = render_stinger()
    finish("wither_stinger", stinger, -23.5)
    last = render_last_chord()
    finish("wither_last_chord", last, -24.6)
    after = render_aftermath()
    finish("wither_aftermath", wrap(after), -27.8, loop=True)
    finish("wither_record", record(hunger, vessel, crit, stinger, last, after), -27.0)
