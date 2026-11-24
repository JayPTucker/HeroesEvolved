"""Builds the Wither's theme, "The Hungry Cage": three phase loops, a phase stinger, its last chord and the record.
Original synthesis, no samples.

    py -3 tools/generate-wither-music.py

Writes src/main/resources/assets/heroesevolved/sounds/music/wither_*.ogg (mono, 44.1 kHz), played by
client/sound/WitherMusic while a fighter is in the Ossuary (the record by a jukebox).

Provenance: the theme answers the need the user's reference library showed (Music/Gameplay Score, from Gemini: Heroes
Reborn): its Combat states are one piece's high-intensity version, the model for a boss fight that is already a fight
from its first bar. Designed to the numbers measured from Score 05, 06, 08 and 09 Combat (scratch analysis, 2026-11-24,
not kept): 80 to 84 s; -16 to -22 dBFS; a spectral centroid of 310 to 860 Hz; 38 to 57% of the energy at 60-250 Hz,
19 to 36% at 250 Hz-1 kHz and 5 to 10% at 1-4 kHz; percussion 34 to 44% of the energy; 5 to 12 low onsets and 1 to 5
high onsets a second; a loudness arc of 3 to 5 dB. The first Wither score measured 137 Hz, 78% under 250 Hz, under 1%
over 1 kHz and 25% percussion, with three phases that measured alike: a drone with thumps. Nothing was copied,
resampled or layered from the references: every chord, part, motif and drum pattern here is our own.

The idea: a broken cage round a hungry star. E minor with the Phrygian F always pulling at it (the hunger is a half
step); the heartbeat is three hits and a gap, 3-3-2, one for each head; bone rattles and chain clanks are the kit's
metal. Its horn call climbs to a half step at the top (B to C, the star's pull) and falls home through F to E.

- wither_hunger    Threefold Hunger: big drums on the 3-3-2 heartbeat, a staccato bass on the same rhythm, low strings,
                   bone rattles and chains, the horn call in the low horns, swells into each section.
- wither_vessel    The Broken Vessel: the same 32 bars with the full kit (claps, hats, toms, shaker), a spiccato
                   ostinato, horns on the changes, stabs on the accents, a low choir, crashes at the turns.
- wither_critical  Critical Mass: all of it with the drums doubled, the call in the high brass and the choir chanting
                   it, a high string tremolo, a rising sub at each section's end, a crash every four bars.
- wither_stinger   A phase change: a drum and crash, the E-F cluster in the brass rising, the choir, about 6 s.
- wither_last_chord Its death: the heartbeat failing, a bell, E minor opening to a bare fifth, then silence.
- wither_record    The record: eight bars of each phase, the stinger between, the last chord.

All three loops are exactly 128 beats (32 bars at 100 bpm, 76.8 s) on one chart in four 8-bar sections, so each can
follow another; client/sound/WitherMusic changes them on the next bar (48 ticks).
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

BPM = 100
BEAT = 60 / BPM
BAR = 4 * BEAT
SIX = BEAT / 4
BARS = 32
LOOP = BARS * BAR          # 76.8 s
TAIL = 6.0

E, F, C, A, G, D, B, AM = ("E", ["E3", "G3", "B3"]), ("F", ["F3", "A3", "C4"]), ("C", ["C3", "G3", "E4"]), \
    ("A", ["A2", "E3", "C4"]), ("G", ["G2", "D3", "B3"]), ("D", ["D3", "F#3", "A3"]), ("B", ["B2", "D#3", "F#3"]), \
    ("A", ["A2", "C3", "E3"])
# Four sections: the cage (E against F), the call (the turn to C and A), the swell (the long climb through G and D to
# B), the hunger (E and F trading every bar, then the cadence that pulls back round).
CHORDS = [E, E, F, E, AM, F, ("B", ["B2", "E3", "F#3"]), B,
          E, C, A, F, E, C, D, B,
          A, F, C, G, A, F, D, B,
          E, F, E, F, C, A, F, B]
ROOT = {"E": "E1", "F": "F1", "C": "C2", "A": "A1", "G": "G1", "D": "D2", "B": "B1"}

# The horn call: up to the half step at the top, then down through F home. Beats from the bar's start, lengths in beats.
CALL = [(0, "E4", 1), (1, "B4", 1.5), (2.5, "C5", .5), (3, "B4", 1)]
HOME = [(0, "A4", .5), (.5, "G4", .5), (1, "F4", 1), (2, "E4", 2)]
# The heartbeat: three hits and a gap, in sixteenths.
HEART = [0, 3, 6]


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


# ----------------------------------------------------------------------
# The layers
# ----------------------------------------------------------------------

def bed(out, density):
    """Every phase: the low strings, the heartbeat bass, the call, and the bell at each section's end."""
    for bar in range(BARS):
        b0 = bar * BAR
        root, notes = CHORDS[bar]
        sec = section(bar)
        add(out, b0, (0.24 if density == 1 else 0.19) * strings(BAR + 0.5, notes, 1500 + 700 * density))
        for s in HEART + [8 + h for h in HEART]:
            if density == 1 and s >= 8 and sec == 0:
                continue
            add(out, b0 + s * SIX, 0.34 * staccato_bass(hz(ROOT[root]) * (2 if s in (6, 14) else 1), SIX * 2.4))
        # The call on the first bar of each four, its way home on the third.
        motif = CALL if bar % 4 == 0 else HOME if bar % 4 == 2 else None
        if motif and (density >= 2 or sec in (1, 3)):
            octave = 0 if density == 1 else 0
            level = 0.22 if density == 1 else 0.18
            notes_out = np.zeros(int((BAR + 3) * RATE))
            for at, n, length in motif:
                add(notes_out, at * BEAT, brass_note(hz(up(n, octave - 1)), length * BEAT))
            add(out, b0, level * notes_out)
        if bar % 8 == 7:
            tt = time(4.0)
            add(out, b0 + 2 * BEAT, 0.13 * (bell(tt, hz("E5"), 0, decay=2.0) + 0.7 * bell(tt, hz("F5"), 0.03, decay=2.2)))


def heartbeat(out, density):
    """The big drums: 3-3-2 in every bar (both halves from the second phase), a low hit on the gap."""
    for bar in range(BARS):
        b0 = bar * BAR
        sec = section(bar)
        hits = HEART + ([8 + h for h in HEART] if density >= 2 or sec >= 1 else [8])
        if density >= 3:
            hits = sorted(set(hits + [2, 10, 13, 15]))
        for s in hits:
            big = s in (0, 8)
            add(out, b0 + s * SIX, (1.0 if big else 0.7) * taiko(44 if big else 58, 1.3 if big else 0.8))
        if bar % 8 == 7 and bar < BARS - 1:
            add(out, b0 + BAR - 2 * BEAT, (0.22 + 0.06 * density) * swell(2 * BEAT))


def bones(out, density):
    """The cage's metal and bone: rattles on the off-beats, chains on the gaps, knocks on the sixteenths later."""
    for bar in range(BARS):
        b0 = bar * BAR
        sec = section(bar)
        for s in (4, 12):
            add(out, b0 + s * SIX, (0.30 + 0.06 * density) * rattle())
        if bar % 2 == 1 or density >= 2:
            add(out, b0 + 7 * SIX, (0.18 + 0.05 * density) * chain())
        if density == 1:
            step = 2 if sec >= 2 else 4
            for s in range(1, 16, step):
                add(out, b0 + s * SIX, 0.24 * bone(0.8 if s % 4 == 3 else 0.5, 1.2))
            for s in range(0, 16, 2):
                add(out, b0 + s * SIX + 0.005, 0.2 * shaker())
                if sec >= 1:
                    add(out, b0 + s * SIX, (0.3 if s % 4 == 0 else 0.2) * hat(0.05))


def kit_layer(out, density):
    """The full kit from the second phase: claps and snare on the back beats, hats, shaker, toms, crashes."""
    for bar in range(BARS):
        b0 = bar * BAR
        for s in (4, 12):
            add(out, b0 + s * SIX, (0.62 if density == 2 else 0.7) * clap())
            add(out, b0 + s * SIX, 0.32 * snare(0.25))
        for s in range(16):
            accent = 1.0 if s % 4 == 0 else 0.7 if s % 2 == 0 else 0.45
            if density == 2 and s % 2 == 1 and bar % 2 == 0:
                continue
            add(out, b0 + s * SIX, (0.52 if density == 2 else 0.62) * accent * hat(0.05 if s % 4 else 0.08))
            if s % 2 == 1:
                add(out, b0 + s * SIX + 0.008, 0.26 * shaker())
            if density >= 3 and s % 4 == 2:
                add(out, b0 + s * SIX, 0.16 * bone(0.7, 1.4))
        if bar % 4 == 3:
            for k, s in enumerate((10, 11, 12, 13, 14, 15)):
                add(out, b0 + s * SIX, 0.36 * tom(170 - k * 16))
        if bar % 8 == 0:
            add(out, b0, (0.34 if density == 2 else 0.40) * crash())
        elif density >= 3 and bar % 4 == 0:
            add(out, b0, 0.28 * crash(2.4))


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
            add(out, b0 + s * SIX, (0.34 if density == 2 else 0.38) * spiccato(f * mult, SIX * 0.85))
            add(out, b0 + s * SIX, 0.17 * spiccato(f * mult / 2, SIX * 0.85, 3200))
            if density >= 3 and s % 2 == 0:
                add(out, b0 + s * SIX, 0.13 * spiccato(f * mult * 2, SIX * 0.6, 9000))


def brass_layer(out, density):
    """Horns swelling on the changes; stabs on the heartbeat's accents in the later sections."""
    for bar in range(BARS):
        b0 = bar * BAR
        root, notes = CHORDS[bar]
        sec = section(bar)
        if bar % 2 == 0 or density >= 3:
            length = (2 if density == 2 else 1) * BAR
            add(out, b0, (0.32 if density == 2 else 0.28) * horns([up(n) for n in notes], length))
        if sec >= 2 or density >= 3:
            for s in (0, 6, 8):
                add(out, b0 + s * SIX, (0.22 if density == 2 else 0.26) * stab([up(n) for n in notes]))


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
            add(out, b0, 0.17 * line)


def critical(out):
    """Only the last phase: a high string tremolo climbing through each section, a sub rising into each turn."""
    for bar in range(BARS):
        tt = time(BAR)
        name = "E6" if bar % 2 == 0 else "F6"
        rate = 9 + (bar % 8) * 0.8
        trem = 0.5 + 0.5 * np.sin(2 * np.pi * rate * tt)
        tone = voice(tt, hz(name), detune=8, harmonics=6, tilt=1.2)
        level = 0.06 + 0.012 * (bar % 8)
        add(out, bar * BAR, level * lowpass(tone, 7000) * trem * np.clip(tt / 0.15, 0, 1) * np.clip((BAR - tt) / 0.15, 0, 1))
        if bar % 8 == 7:
            add(out, bar * BAR, 0.36 * sub_rise(BAR))


def arc(t, levels):
    marks = [0, 8 * BAR, 16 * BAR, 20 * BAR, 24 * BAR, 28 * BAR, 31 * BAR, LOOP]
    return np.interp(np.mod(t, LOOP), marks, levels)


# ----------------------------------------------------------------------
# The pieces
# ----------------------------------------------------------------------

def render_hunger():
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    bed(out, 1)
    heartbeat(out, 1)
    bones(out, 1)
    brass = np.zeros_like(t)
    for bar in range(8, BARS, 2):
        add(brass, bar * BAR, 0.24 * horns(CHORDS[bar][1], 2 * BAR, 2600))
    out += brass
    return fast_reverb(out * arc(t, [0.82, 0.9, 1.0, .94, 1.0, 1.1, 1.05, 0.82]), 0.24, 1.15)


def render_vessel():
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    bed(out, 2)
    heartbeat(out, 2)
    bones(out, 2)
    kit_layer(out, 2)
    ostinato(out, 2)
    brass_layer(out, 2)
    choir(out, 2)
    return fast_reverb(out * arc(t, [0.86, 0.92, 1.0, .95, 1.0, 1.08, 1.04, 0.86]), 0.2, 1.05)


def render_critical():
    t = time(LOOP + TAIL)
    out = np.zeros_like(t)
    bed(out, 3)
    heartbeat(out, 3)
    bones(out, 3)
    kit_layer(out, 3)
    ostinato(out, 3)
    brass_layer(out, 3)
    choir(out, 3)
    critical(out)
    return fast_reverb(out * arc(t, [0.88, 0.94, 1.0, .96, 1.02, 1.08, 1.05, 0.88]), 0.2, 1.0)


def render_stinger():
    t = time(6.5)
    x = np.zeros_like(t)
    add(x, 0.0, 0.85 * taiko(40, 2.4))
    add(x, 0.0, 0.34 * crash(4.0))
    add(x, 0.0, 0.5 * rattle())
    add(x, 0.12, 0.55 * horns(["E3", "B3", "E4", "F4"], 4.4))
    add(x, 0.6, 0.4 * horns(["E4", "B4", "C5"], 3.6, 6000))
    x += sung(t, hz("E5"), 0.5, 3.8, "ah", voices=4, level=0.2) + sung(t, hz("B4"), 0.8, 3.4, "oh", voices=3, level=0.16)
    for k, s in enumerate((0, 3, 6)):
        add(x, 2.4 + s * SIX, (0.5 + 0.1 * k) * taiko(52, 1.2))
    add(x, 2.4, 0.4 * chain())
    return fast_reverb(x, 0.3, 1.2)


def render_last_chord():
    t = time(8.0)
    x = np.zeros_like(t)
    # The heartbeat failing: three hits, slower and softer each time, then nothing where the fourth should be.
    at = 0.0
    for k in range(3):
        add(x, at, (0.6 - 0.15 * k) * taiko(44 - 3 * k, 1.6))
        at += 0.55 + 0.25 * k
    x += 0.3 * bell(t, hz("E4"), 1.9, decay=3.8) + 0.18 * bell(t, hz("B4"), 1.95, decay=3.2)
    minor = pad(t, ["E2", "B2", "E3", "G3", "B3"], detune=5, cutoff=1400) * env(t, 1.8, 2.8, 5.2, curve=1.6)
    fifth = pad(t, ["E2", "B2", "E3", "B3"], detune=4, cutoff=1000) * env(t, 2.2, 4.4, 7.8, curve=1.8)
    x += 0.8 * minor + 0.7 * fifth
    x += sung(t, hz("G4"), 2.0, 3.2, "oo", voices=3, level=0.08) + sung(t, hz("E4"), 2.3, 4.6, "oo", voices=3, level=0.07)
    return fast_reverb(x, 0.34, 1.25)


def level_to(x, rms_db):
    audible = highpass(x, 150)
    return x * 10 ** (rms_db / 20) / (np.sqrt(np.mean(audible ** 2)) + 1e-12)


def record(hunger, vessel, crit, stinger, last):
    eight = int(8 * BAR * RATE)
    fade = int(0.5 * RATE)
    parts = [level_to(hunger, -24.0)[:eight], level_to(vessel, -23.5)[eight:2 * eight], level_to(crit, -23.0)[3 * eight:4 * eight]]
    out = np.zeros(3 * eight + len(last) + RATE)
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
    return out


if __name__ == "__main__":
    hunger = render_hunger()
    finish("wither_hunger", wrap(hunger), -25.1, loop=True)
    vessel = render_vessel()
    finish("wither_vessel", wrap(vessel), -23.5, loop=True)
    crit = render_critical()
    finish("wither_critical", wrap(crit), -22.5, loop=True)
    stinger = render_stinger()
    finish("wither_stinger", stinger, -22.0)
    last = render_last_chord()
    finish("wither_last_chord", last, -23.0)
    finish("wither_record", record(hunger, vessel, crit, stinger, last), -24.0)
