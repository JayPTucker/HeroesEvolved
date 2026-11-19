"""Builds the short story cues for the Last Day's pods and the bunker's waking: original synthesis, no samples.

    py -3 tools/generate-story-cues.py

Writes src/main/resources/assets/heroesevolved/sounds/story/*.ogg (mono, 44.1 kHz).

Provenance: each cue answers a need the user's reference library (docs/agents/audio.md: Music/Short Music Cues and
Music/Stingers and Transitions from Gemini: Heroes Reborn) shows a cue for, and is designed to the numbers measured from
it: 3 to 10 seconds, about -18 to -23 dBFS RMS, a peak one to three seconds in and a two to three second decay, most of
the energy between 250 Hz and 2 kHz, low roots on D and A. Nothing was copied, resampled or layered from those files:
every tone here is synthesized below, on our own chords and motifs.

- story_sealed   the lid closes on the Last Day and you go under: a felt thump, a D minor pad whose filter closes, frost
                 shimmer (a stinger that lands and decays).
- story_years    "103 years later...": a long swell from silence into a held chord change, one chime as the counter turns
                 over, a cold tail (a transition).
- story_wake_music  the pods open 103 years on (everyone, the Helix Ward's sleeper too): eighty seconds, designed to the
                 shape of the user's reference track Music_03, with swells after their Score 01 stingers inside it (see
                 wake_music); it loops until they leave the bunker, then fades away.
- story_ward     the Helix Ward: two glass tones rubbing a semitone apart, a slowing heartbeat, a falling line (sealed and
                 woken there).
"""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "build" / "audio-tools"))
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402
from scipy.signal import butter, sosfilt  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/heroesevolved/sounds/story"
RATE = 44100
RNG = np.random.default_rng(1406)


def hz(note):
    """Note name (A4 = 440) to frequency."""
    names = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    name, octave = note[:-1], int(note[-1])
    return 440.0 * 2 ** ((names[name] + 12 * (octave - 4)) / 12)


def time(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def env(t, attack, peak_at, release_end, curve=2.0):
    """Rises from silence to 1 over `attack` ending at `peak_at`, holds, then falls to silence at `release_end`."""
    e = np.ones_like(t)
    start = max(peak_at - attack, 0)
    e = np.where(t < start, 0, e)
    rising = (t >= start) & (t < peak_at)
    e = np.where(rising, np.clip((t - start) / max(attack, 1e-6), 0, 1) ** 1.5, e)
    falling = t >= peak_at
    e = np.where(falling, np.clip(1 - (t - peak_at) / max(release_end - peak_at, 1e-6), 0, 1) ** curve, e)
    return e


def lowpass(x, cutoff, order=2):
    return sosfilt(butter(order, cutoff, btype="low", fs=RATE, output="sos"), x)


def bandpass(x, low, high, order=2):
    return sosfilt(butter(order, [low, high], btype="band", fs=RATE, output="sos"), x)


def voice(t, f, detune=0.0, harmonics=8, tilt=1.4):
    """A soft string-like tone: additive saw with a rolled-off top, two voices detuned against each other."""
    out = np.zeros_like(t)
    for d in (-detune, detune) if detune else (0.0,):
        ff = f * 2 ** (d / 1200)
        phase = RNG.uniform(0, 2 * np.pi)
        for k in range(1, harmonics + 1):
            if ff * k > 9000:
                break
            out += np.sin(2 * np.pi * ff * k * t + phase * k) / k ** tilt
    return out


def pad(t, notes, detune=7, cutoff=1200):
    x = sum(voice(t, hz(n), detune) for n in notes) / len(notes)
    return lowpass(x, cutoff)


def mallet(t, f, start, decay=1.4, bright=1.0):
    """A struck tone (celesta-like): fundamental, octave and a soft twelfth, a fast attack and an exponential fall."""
    tt = t - start
    on = tt >= 0
    e = np.where(on, (1 - np.exp(-np.clip(tt, 0, None) * 300)) * np.exp(-np.clip(tt, 0, None) / decay), 0)
    x = np.sin(2 * np.pi * f * tt) + 0.35 * bright * np.sin(2 * np.pi * 2 * f * tt) + 0.12 * bright * np.sin(2 * np.pi * 3 * f * tt)
    return x * e


def bell(t, f, start, decay=2.5):
    """A small bell: inharmonic partials, each fading at its own rate."""
    tt = t - start
    out = np.zeros_like(t)
    for ratio, level, k in ((1.0, 1.0, 1.0), (2.76, 0.45, 0.6), (5.40, 0.22, 0.35), (8.93, 0.10, 0.2)):
        out += level * np.where(tt >= 0, np.sin(2 * np.pi * f * ratio * tt) * np.exp(-np.clip(tt, 0, None) / (decay * k)), 0)
    return out * np.where(tt >= 0, 1 - np.exp(-np.clip(tt, 0, None) * 400), 0)


def thump(t, start, f=58, knock=520, level=1.0):
    """A felt blow: a falling sine with a short mid-range knock, so it reads on small speakers too."""
    tt = t - start
    on = tt >= 0
    sweep = f * (1 + 0.6 * np.exp(-np.clip(tt, 0, None) * 18))
    body = np.where(on, np.sin(2 * np.pi * np.cumsum(sweep) / RATE) * np.exp(-np.clip(tt, 0, None) * 5.5), 0)
    click = bandpass(RNG.normal(0, 1, len(t)), knock * 0.6, knock * 1.6) * np.where(on, np.exp(-np.clip(tt, 0, None) * 38), 0)
    return level * (body + 0.5 * click)


def air(t, low, high):
    return bandpass(RNG.normal(0, 1, len(t)), low, high)


def space(x, wet=0.28, size=1.0):
    """A small room's worth of reverb: four combs into two allpasses (Schroeder)."""
    out = np.zeros(len(x) + RATE * 2)
    padded = np.concatenate([x, np.zeros(RATE * 2)])
    for delay_ms, feedback in ((29.7, 0.78), (37.1, 0.76), (41.1, 0.74), (43.7, 0.72)):
        d = int(RATE * delay_ms * size / 1000)
        y = np.copy(padded)
        for i in range(d, len(y)):
            y[i] += feedback * y[i - d]
        out += y / 4
    for delay_ms, g in ((5.0, 0.7), (1.7, 0.7)):
        d = int(RATE * delay_ms / 1000)
        y = np.zeros_like(out)
        for i in range(len(out)):
            y[i] = -g * out[i] + (out[i - d] if i >= d else 0) + (g * y[i - d] if i >= d else 0)
        out = y
    return (1 - wet) * padded[:len(x)] + wet * lowpass(out[:len(x)], 5000)


def finish(name, x, rms_db=-20.0, loop=False):
    """Normalise loudness on the band above 150 Hz (sub-bass alone is silent on laptop speakers), keep peaks under 0.8.
    A loop keeps its ends as they are (no fade), so it joins itself."""
    x = x - np.mean(x)
    if not loop:
        x = x * np.minimum(1, (len(x) - np.arange(len(x))) / (RATE * 0.25))
    audible = sosfilt(butter(2, 150, btype="high", fs=RATE, output="sos"), x)
    gain = 10 ** (rms_db / 20) / (np.sqrt(np.mean(audible ** 2)) + 1e-12)
    x = x * gain
    peak = np.max(np.abs(x))
    if peak > 0.78:
        x = np.tanh(x / peak * 1.2) / np.tanh(1.2) * 0.78
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.ogg"
    for _ in range(4):
        # In blocks: libsndfile's Vorbis encoder overflows the stack on Windows when handed a long cue in one write.
        with sf.SoundFile(path, "w", RATE, 1, format="OGG", subtype="VORBIS") as ogg:
            for i in range(0, len(x), 4096):
                ogg.write(x[i:i + 4096].astype(np.float32))
        decoded, _ = sf.read(path)
        if np.max(np.abs(decoded)) < 0.95:
            break
        x *= 0.92
    decoded, _ = sf.read(path)
    rms = 20 * np.log10(np.sqrt(np.mean(decoded ** 2)) + 1e-12)
    print(f"{name:14} {len(decoded) / RATE:4.1f}s  rms {rms:6.1f} dBFS  peak {np.max(np.abs(decoded)):.2f}")


def sealed():
    t = time(6.0)
    x = 0.6 * thump(t, 0.05, f=55, knock=300, level=0.8)
    # The pad closes as the cold takes you: its filter falls from bright to muffled.
    chord = pad(t, ["D2", "A2", "F3", "A3"], detune=6, cutoff=1600)
    muffled = pad(t, ["D2", "A2", "F3", "A3"], detune=6, cutoff=380)
    blend = np.clip((t - 0.8) / 3.5, 0, 1)
    x += 1.6 * ((1 - blend) * chord + blend * muffled) * env(t, 0.7, 1.4, 6.0, curve=1.6)
    # Frost: a glassy cluster high above, fluttering, gone before the end.
    frost = sum(np.sin(2 * np.pi * hz(n) * t) for n in ("E6", "F6", "A6")) / 3
    x += 0.06 * frost * (0.6 + 0.4 * np.sin(2 * np.pi * 5.3 * t)) * env(t, 1.6, 2.6, 5.2)
    x += 0.02 * air(t, 2500, 6000) * env(t, 1.2, 2.2, 5.0)
    finish("story_sealed", space(x, 0.3), -24.0)


def years():
    t = time(9.5)
    # From nothing, the minor chord grows; as the counter turns over it lifts into B-flat major seven, and is left there.
    first = pad(t, ["D2", "A2", "E3", "F3"], detune=8, cutoff=900)
    second = pad(t, ["A#1", "F2", "A2", "D3"], detune=8, cutoff=1100)
    turn = np.clip((t - 4.6) / 0.9, 0, 1)
    x = 1.2 * ((1 - turn) * first + turn * second) * env(t, 4.4, 4.8, 9.5, curve=1.4)
    x += 0.35 * np.sin(2 * np.pi * hz("D2") * t) * env(t, 4.0, 4.6, 9.0)
    x += 0.07 * air(t, 400, 2400) * env(t, 4.0, 4.4, 7.5)
    x += 0.55 * (bell(t, hz("A5"), 4.6, 2.6) + 0.6 * bell(t, hz("D6"), 4.62, 2.2))
    finish("story_years", space(x, 0.35, 1.2), -25.5)


def ward():
    t = time(7.0)
    # A needle's glint, two glass tones a semitone apart, a heartbeat that slows, and a line that sinks.
    x = 0.25 * mallet(t, 4186.0, 0.25, decay=0.18, bright=0.2)
    glass = np.sin(2 * np.pi * hz("C6") * t) + np.sin(2 * np.pi * hz("C#6") * t) + 0.3 * np.sin(2 * np.pi * hz("C7") * t)
    x += 0.15 * glass * (0.7 + 0.3 * np.sin(2 * np.pi * 3.1 * t)) * env(t, 1.8, 2.2, 6.6)
    for beat in (0.9, 1.85, 2.95, 4.2, 5.6):
        x += 0.55 * thump(t, beat, f=52, knock=340, level=1.0) + 0.35 * thump(t, beat + 0.22, f=60, knock=380, level=0.7)
    slide = hz("E4") * 2 ** (-np.clip((t - 2.2) / 3.3, 0, 1) * 2 / 12)
    line = np.sin(2 * np.pi * np.cumsum(slide * (1 + 0.004 * np.sin(2 * np.pi * 5.5 * t))) / RATE)
    x += 0.30 * line * env(t, 0.6, 2.4, 6.2)
    x += 0.8 * pad(t, ["A2", "C3", "D#3"], detune=9, cutoff=700) * env(t, 1.5, 2.6, 7.0, curve=1.5)
    finish("story_ward", space(x, 0.34, 1.1), -25.0)


def shimmer(t, f, start, length):
    """A held high tone, glassy: two voices a few cents apart, a slow swell in and out, a little tremolo."""
    tt = t - start
    on = (tt >= 0) & (tt <= length)
    e = np.where(on, np.sin(np.pi * np.clip(tt / length, 0, 1)) ** 1.6, 0)
    tone = np.sin(2 * np.pi * f * tt) + np.sin(2 * np.pi * f * 1.004 * tt + 1.3) + 0.25 * np.sin(2 * np.pi * 2 * f * tt)
    return tone * e * (0.85 + 0.15 * np.sin(2 * np.pi * 4.2 * t))


VOWELS = {"oh": ((400, 1.0, 90), (800, 0.45, 110), (2700, 0.12, 200)), "oo": ((320, 1.0, 80), (850, 0.25, 110), (2500, 0.08, 200)),
          "ah": ((750, 1.0, 110), (1200, 0.55, 130), (2800, 0.15, 200))}


def sung(t, f, start, length, vowel="oh", voices=3, level=1.0):
    """A small choir holding one note: additive voices whose harmonics are shaped by the vowel's formants (resonance
    curves), each with its own vibrato (about 5.5 Hz, 1.2%) and a few cents off the others, a breath of noise through the
    same formants, swelling in and out over `length`. Our own synthesis, written to how the user's reference stingers
    sing (high, round vowels round C5 to D5)."""
    tt = t - start
    on = (tt >= 0) & (tt <= length)
    if not on.any():
        return np.zeros_like(t)
    seg = tt[on]
    e = np.sin(np.pi * np.clip(seg / length, 0, 1)) ** 1.4
    out = np.zeros_like(seg)
    formants = VOWELS[vowel]

    def gain(freq):
        return sum(a / (1 + ((freq - c) / bw) ** 2) for c, a, bw in formants)

    for v in range(voices):
        detune = 2 ** (((v - (voices - 1) / 2) * 7) / 1200)
        rate, phase0 = 5.2 + 0.4 * v, 1.7 * v
        vib = 1 + 0.012 * np.sin(2 * np.pi * rate * seg + phase0) * np.clip(seg / 0.8, 0, 1)
        ph = 2 * np.pi * np.cumsum(f * detune * vib) / RATE
        k = 1
        while f * k < 5000:
            out += gain(f * k) / k ** 0.9 * np.sin(k * ph)
            k += 1
    breath = bandpass(RNG.normal(0, 1, len(seg)), formants[0][0] * 0.7, formants[1][0] * 1.3)
    out = out / voices + 0.15 * breath
    full = np.zeros_like(t)
    full[on] = out * e * level
    return full


def stinger(t, start):
    """A swell in the shape of the user's Score 01 stingers (Stingers and Transitions, Stinger 01 to 03: a rise to the peak
    1.5 to 2.5 s in, silence by about 5 s, D and A in the bass, held high tones round A4 to D5 with a few notes climbing to
    C6), placed inside the waking music at `start`. Ours: D minor opening onto an unresolved B-flat over D."""
    tt = t - start
    x = 0.9 * pad(tt, ["D2", "A2", "F3", "A3"], detune=6, cutoff=1800) * env(tt, 1.6, 1.9, 5.2, curve=1.8)
    lift = np.clip((tt - 2.2) / 0.8, 0, 1)
    x += 0.7 * lift * pad(tt, ["D2", "A#2", "D3", "F3"], detune=6, cutoff=2200) * env(tt, 1.0, 2.6, 5.6, curve=1.6)
    # Voices: a choir holding A4 and D5 through the swell, and one line rising E5 to G5 over the top of it.
    x += sung(t, hz("A4"), start + 0.2, 5.0, "oo", level=0.10) + sung(t, hz("D5"), start + 0.8, 4.4, "oh", level=0.12)
    x += sung(t, hz("E5"), start + 1.6, 2.0, "oh", voices=2, level=0.09) + sung(t, hz("G5"), start + 2.8, 2.6, "oh", voices=2, level=0.08)
    x += 0.12 * mallet(t, hz("C6"), start + 3.0, decay=1.6)
    return x * ((tt >= 0) & (tt < 7))


def wake_music():
    """The waking (story_wake_music), a loop of exactly 128 beats (about 83.5 s) that plays until the player leaves the
    bunker. Designed to the shape of the user's reference track (Music/Individual Layers and Alternate Encodings, Music_03:
    D minor, about 92 bpm, roots D-F-D-C, near -25 dBFS, a hush in the middle and its peak late), with two swells in the
    shape of the user's Score 01 stingers inside it; the chords, motif and sound are ours, synthesized here.

    It loops without a seam: everything that rings on past the end (the last chord, the pulse, the reverb) is wrapped round
    onto the start, and the loudness arc ends where it began."""
    beat = 60 / 92
    bar = 4 * beat
    span = 2 * bar
    loop = 16 * span
    tail = 8.0
    t = time(loop + tail)
    out = np.zeros_like(t)
    # Chords, two bars each: Dm, F, Dm, C, again; Bb and Gm in the hush; Dm, F, C, Dm for the swell.
    progression = [("D2", ["D3", "F3", "A3"]), ("F2", ["F3", "A3", "C4"]), ("D2", ["D3", "F3", "A3"]), ("C2", ["C3", "E3", "G3"])] * 2
    progression += [("A#1", ["A#2", "D3", "F3"]), ("G1", ["G2", "A#2", "D3"])] * 2
    progression += [("D2", ["D3", "F3", "A3"]), ("F2", ["F3", "A3", "C4"]), ("C2", ["C3", "E3", "G3"]), ("D2", ["D3", "E3", "A3"])]
    for i, (root, chord) in enumerate(progression):
        start = i * span
        seg = (t >= start) & (t < start + span + 1.0)
        tt = t[seg] - start
        fade = np.clip(tt / 0.8, 0, 1) * np.clip((span + 1.0 - tt) / 1.0, 0, 1)
        out[seg] += 0.9 * pad(tt, chord, detune=7, cutoff=3000) * fade
        out[seg] += 0.55 * np.sin(2 * np.pi * hz(root) * tt) * fade
    # The pod's counter, a soft pulse on every beat, gone in the hush.
    hush0, hush1 = 8 * span, 12 * span
    for k in range(128):
        s = k * beat
        if hush0 - beat <= s < hush1:
            continue
        out += 0.18 * mallet(t, hz("D5") if k % 4 == 0 else hz("A4"), s, decay=0.25, bright=0.3)
    # Our motif, twice through the line and once an octave up in the swell.
    motif = [("A4", 0), ("D5", 1), ("E5", 2), ("F5", 3), ("E5", 5), ("C5", 6), ("D5", 8)]
    for start, lift in ((2 * span, 0), (5 * span, 0), (12 * span, 12)):
        for note, at in motif:
            out += 0.5 * mallet(t, hz(note) * 2 ** (lift / 12), start + at * beat, decay=1.6)
    # High held notes over it (a C5 over the line, a lone A4 in the hush, D5, E5, A5 rising to a C6 at the peak), and the two
    # stinger swells: one carrying the line down into the hush, one falling back from the peak.
    for note, start, length, level in (("C5", 4, 26, 0.09), ("G5", 16, 10, 0.04), ("A4", hush0 + 2, 16, 0.07),
                                       ("D5", 12 * span, 6, 0.08), ("E5", 12 * span + 4, 5, 0.08), ("A5", 12 * span + 7, 6, 0.07),
                                       ("C6", 12 * span + 10, 8, 0.06)):
        out += level * shimmer(t, hz(note), start, length)
    out += 1.1 * stinger(t, hush0 - 3.0) + 1.1 * stinger(t, 15 * span - 2.0)
    out += 0.05 * air(t, 300, 2200)
    # Loudness arc over one loop, ending where it starts.
    marks = [0, 2 * span, 6 * span, hush0, hush1, 12 * span, 14 * span, loop]
    level = [0.45, 0.6, 0.75, 0.4, 0.35, 0.85, 1.0, 0.45]
    arc = np.interp(np.mod(t, loop), marks, level)
    x = space(out * arc, 0.3, 1.2)
    # Wrap the tail round onto the head: the loop's seam is the same music on both sides of it.
    n = int(loop * RATE)
    head = x[:n].copy()
    head[:len(x) - n] += x[n:]
    finish("story_wake_music", head, -27.5, loop=True)


if __name__ == "__main__":
    sealed()
    years()
    ward()
    wake_music()
