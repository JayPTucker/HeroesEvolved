"""Synthesize Vibe's original mono Vorbis cues. Requires numpy and soundfile."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/audio-tools"))
import numpy as np
import soundfile as sf

RATE = 48000
OUT = ROOT / "src/main/resources/assets/heroesevolved/sounds/ability"
RNG = np.random.default_rng(419)


def seconds(duration):
    return np.arange(round(duration * RATE)) / RATE


def chirp(t, start, end, time, vibrato=0):
    frequency = end + (start - end) * np.exp(-t / time) + vibrato * np.sin(2 * np.pi * 11 * t)
    return np.sin(2 * np.pi * np.cumsum(frequency) / RATE)


def tail(t, attack, decay):
    return np.minimum(t / attack, 1) * np.exp(-t / decay)


def grit(t):
    noise = RNG.normal(0, 1, t.size)
    soft = np.convolve(noise, np.ones(21) / 21, mode="same")
    return soft * np.sin(t * np.pi * 2 * 32) + .12 * noise


def write(name, sound):
    # Three quiet reflections keep the synthetic cues spatial without flattening their transients.
    mixed = sound.copy()
    for delay, gain in ((.027, .16), (.049, .10), (.088, -.06)):
        offset = round(delay * RATE)
        mixed[offset:] += sound[:-offset] * gain
    mixed *= np.minimum(np.arange(mixed.size)[::-1] / (RATE * .09), 1)
    mixed *= .76 / max(.76, np.max(np.abs(mixed)))
    sf.write(OUT / f"vibe_{name}.ogg", mixed, RATE, format="OGG", subtype="VORBIS")


t = seconds(.66)
blast = .62 * chirp(t, 157, 46, .048) * tail(t, .007, .12)
blast += .27 * chirp(t, 690, 128, .09, 18) * tail(t, .013, .105)
blast += .22 * grit(t) * tail(t, .006, .14)
write("pulse", blast)

t = seconds(1.13)
breach = .28 * chirp(t, 57, 144, .16, 7) * tail(t, .12, .65)
breach += .31 * chirp(t, 211, 522, .23, 14) * tail(t, .24, .5)
breach += .11 * chirp(t, 874, 1090, .16, 28) * tail(t, .30, .42)
breach += .22 * grit(t) * tail(t, .20, .48)
write("breach", breach)

t = seconds(.55)
shut = .49 * chirp(t, 396, 53, .075, 12) * tail(t, .018, .15)
shut += .25 * chirp(t, 1200, 310, .07) * tail(t, .016, .08)
shut += .22 * grit(t) * tail(t, .009, .12)
write("shut", shut)

t = seconds(1.47)
echo = .17 * chirp(t, 161, 202, .45, 8) * tail(t, .11, .69)
for offset, frequency in ((0, 440), (.16, 660), (.33, 884)):
    k = round(offset * RATE)
    note = t[:t.size - k]
    echo[k:] += .18 * chirp(note, frequency * .94, frequency, .14, 5) * tail(note, .06, .41)
echo += .14 * grit(t) * tail(t, .22, .44)
write("echo", echo)

t = seconds(1.16)
lock = .49 * chirp(t, 118, 38, .10, 3) * tail(t, .02, .34)
lock += .26 * chirp(t, 475, 102, .16, 31) * tail(t, .015, .22)
lock += .17 * chirp(t, 814, 176, .19, 39) * tail(t, .025, .29)
lock += .24 * grit(t) * tail(t, .012, .25)
write("collapse", lock)
