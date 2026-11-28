"""The trailer's music, synthesised from nothing: no samples, no reference audio, all our own.

A low drone and a faint high shimmer under everything, a pulse that ticks and quickens, a noise riser into each big
moment, "braam" hits (a detuned brass-like chord through a filter that opens and closes) with a sub boom on the
impacts, a hard stop before the title and the biggest hit on it, all through one shared reverb.

It is driven by a cue sheet the edit writes (tools/trailer/cut.py): the length in seconds and the moments to hit, e.g.
    {"length": 14.2, "cues": [{"at": 1.7, "kind": "hit"}, {"at": 9.8, "kind": "title"}]}
Kinds: "braam" (the big one), "hit" (a smaller impact), "title" (silence just before, then the biggest hit),
"freeze" and "drop" (stopped time: the drone and pulse cut to a thin held tone until the drop's hit).

Run on its own with Blender's Python (numpy is bundled):
    "C:/Program Files/Blender Foundation/Blender 5.0/5.0/python/bin/python.exe" tools/trailer/score.py cues.json out.wav
"""
import json
import sys
import wave

import numpy as np

SR = 48000
D1, A1, D2, F2, A2, D3, F3, A3 = 36.71, 55.0, 73.42, 87.31, 110.0, 146.83, 174.61, 220.0


def seconds(n):
    return np.arange(int(n * SR)) / SR


def lowpass(x, cutoff, order=2):
    spectrum = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spectrum / np.sqrt(1 + (f / cutoff) ** (2 * order)), len(x))


def highpass(x, cutoff, order=2):
    spectrum = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    r = (f / cutoff) ** (2 * order)
    return np.fft.irfft(spectrum * np.sqrt(r / (1 + r)), len(x))


def saw(freq, t, phase=0.0):
    x = (t * freq + phase) % 1.0
    return 2 * x - 1


def env(t, attack, decay, hold=0.0):
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    d = np.where(t < attack + hold, 1.0, np.exp(-(t - attack - hold) / decay))
    return a * d


def braam(length=3.2, size=1.0, rng=None):
    """A detuned minor chord on saws, the filter flaring open on the hit and closing as it rings."""
    t = seconds(length)
    rng = rng or np.random.default_rng(1)
    chord = np.zeros_like(t)
    for note, level in ((D2, 1.0), (A2, .8), (D3, .7), (F3, .45), (A3, .35)):
        for detune in (-.006, 0, .0055):
            chord += level * saw(note * (1 + detune), t, rng.random())
    chord /= 9
    bright, dark = lowpass(chord, 2600 * size), lowpass(chord, 380)
    opening = np.exp(-t / .35)
    voice = np.tanh(2.2 * (bright * opening + dark * (1 - opening)))
    sub = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, .5, length], [62, 38, 36])) / SR)
    return (voice * .85 + sub * .7 * size) * env(t, .03, 1.3 * size, .25)


def boom(length=2.4, size=1.0):
    """A sub drop with a burst of filtered noise on top: the impact."""
    t = seconds(length)
    sweep = np.interp(t, [0, .9, length], [80, 30, 26])
    sub = np.sin(2 * np.pi * np.cumsum(sweep) / SR) * env(t, .004, .9 * size)
    noise = np.random.default_rng(7).standard_normal(len(t))
    crack = lowpass(highpass(noise, 120), 3500) * env(t, .002, .18 * size) * .5
    return np.tanh(1.6 * (sub + crack)) * size


def tick(level=1.0):
    """The pulse: a short low knock."""
    t = seconds(.35)
    knock = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, .08, .35], [110, 50, 45])) / SR)
    click = highpass(np.random.default_rng(3).standard_normal(len(t)), 1500) * env(t, .001, .012) * .25
    return (knock * env(t, .002, .09) + click) * level


def riser(length):
    """Filtered noise swelling and brightening into the moment it ends on."""
    t = seconds(length)
    noise = np.random.default_rng(11).standard_normal(len(t))
    steps = 6
    out = np.zeros_like(t)
    for i in range(steps):
        band = lowpass(highpass(noise, 200 + 300 * i), 600 + 1400 * i)
        weight = np.clip(1 - np.abs(t / length * (steps - 1) - i), 0, 1)
        out += band * weight
    return out * (t / length) ** 2.5 * .55


def reverb(x, length=2.8, seed=5):
    """One shared hall: decaying noise as the impulse response, convolved by FFT, a little darker each channel."""
    rng = np.random.default_rng(seed)
    t = seconds(length)
    out = []
    for channel in range(2):
        ir = rng.standard_normal(len(t)) * np.exp(-t / (length / 6.5))
        ir = lowpass(ir, 5200 - 900 * channel)
        ir[: int(.012 * SR)] *= np.linspace(0, 1, int(.012 * SR))
        n = len(x) + len(ir)
        wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)[: len(x)]
        out.append(wet / (np.max(np.abs(wet)) + 1e-9))
    return np.stack(out)


def place(track, sound, at, gain=1.0):
    start = int(at * SR)
    if start >= len(track):
        return
    end = min(len(track), start + len(sound))
    track[start:end] += sound[: end - start] * gain


def score(sheet):
    length = float(sheet["length"])
    cues = sorted(sheet["cues"], key=lambda c: c["at"])
    t = seconds(length)
    n = len(t)
    title = next((c["at"] for c in cues if c["kind"] == "title"), length)
    big = [c["at"] for c in cues if c["kind"] in ("braam", "title")]

    # The bed: a low drone and a faint shimmer, swelling in, ducking for each hit, gone in the gap before the title.
    drone = sum(saw(f * (1 + d), t, p) for f, d, p in ((D1, 0, .1), (D1, .004, .6), (A1, -.003, .3), (D2, .002, .8)))
    drone = lowpass(drone / 4, 180)
    shimmer = sum(np.sin(2 * np.pi * f * t + p) * (.5 + .5 * np.sin(2 * np.pi * r * t))
                  for f, p, r in ((587.33, 0, .13), (880.0, 1, .21), (1174.66, 2, .17))) / 3
    bed_level = np.clip(t / 2.5, 0, 1) * np.where(t > title - .4, np.clip((title - t) / .15, 0, 1), 1)
    after = t >= title
    bed_level = np.where(after, np.clip((t - title - .2) / 1.5, 0, 1) * np.clip((length - t) / 1.5, 0, 1) * .8, bed_level)
    # Stopped time ("freeze" to "drop"): the drone and the pulse cut out and only a thin held tone remains, until the
    # biggest hit lands as time starts again.
    frozen = np.zeros(n, bool)
    for start in (c["at"] for c in cues if c["kind"] == "freeze"):
        end = next((c["at"] for c in cues if c["kind"] == "drop" and c["at"] > start), start + 3)
        frozen |= (t >= start - .05) & (t < end)
    edge = np.convolve(frozen.astype(float), np.ones(int(.08 * SR)) / int(.08 * SR), mode="same")
    bed_level = bed_level * (1 - edge)
    held = (np.sin(2 * np.pi * 1760 * t) * .6 + np.sin(2 * np.pi * 2637 * t) * .4) * (.55 + .45 * np.sin(2 * np.pi * 3.1 * t))
    bed = (drone * .55 + shimmer * .05) * bed_level + held * edge * .045

    hits = np.zeros(n)
    # The pulse: every 0.6 s, twice as fast after the first big hit, stopping before the title and in stopped time.
    first = big[0] if big else title
    at = .6
    while at < title - .6:
        fast = at > first
        if not frozen[min(n - 1, int(at * SR))]:
            place(hits, tick(.35 + .4 * min(1, at / max(first, 1))), at, .9)
        at += .3 if fast else .6
    for cue in cues:
        kind, at = cue["kind"], cue["at"]
        if kind == "braam":
            place(hits, riser(1.4), at - 1.4, .7)
            place(hits, braam(3.2), at, 1.0)
            place(hits, boom(2.2, .9), at, .9)
        elif kind == "hit":
            place(hits, boom(1.6, .6), at, .75)
            place(hits, riser(.7), at - .7, .35)
        elif kind == "title":
            place(hits, riser(2.0), at - 2.4, .8)
            place(hits, braam(4.5, 1.25), at, 1.15)
            place(hits, boom(3.0, 1.2), at, 1.1)
        elif kind == "drop":
            place(hits, braam(4.0, 1.2), at, 1.1)
            place(hits, boom(3.0, 1.2), at, 1.1)
        elif kind == "freeze":
            place(hits, boom(1.2, .5), at, .6)
    # Nothing but the held tone rings through stopped time.
    hits[frozen] *= 0
    # Silence just before the title: cut everything for a beat.
    gap = (t > title - .4) & (t < title)
    hits[gap] *= np.clip((title - .4 - t[gap]) / .02 + 1, 0, 1)

    dry = bed + hits
    wet = reverb(dry)
    mix = np.stack([dry, dry]) * .8 + wet * np.max(np.abs(dry)) * .35
    # A little width: the right channel a few milliseconds late.
    mix[1] = np.concatenate([np.zeros(int(.004 * SR)), mix[1][: n - int(.004 * SR)]])
    # The beat of silence holds for the reverb too: nothing rings into it.
    hush = np.ones(n)
    hush[gap] = np.clip((title - .4 - t[gap]) / .03 + 1, 0, 1)
    mix *= hush * np.clip((length - t) / .05, 0, 1)
    return mix / (np.max(np.abs(mix)) + 1e-9) * .89


def write(sheet, path):
    mix = score(sheet)
    pcm = (np.clip(mix.T, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes(pcm.tobytes())
    return path


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as f:
        write(json.load(f), sys.argv[2])
    print("SCORE written", sys.argv[2])
