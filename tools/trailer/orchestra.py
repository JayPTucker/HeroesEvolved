"""The trailer's orchestra: a small sampler over the VSCO 2 Community Edition (Versilian Studios, CC0 1.0), whose
samples are downloaded once, never shipped, into build/vsco2 (see the trailer skill for the sparse checkout).

Samples are mapped from their file names (note, dynamic layer, round robin: VlnEns_Spic_A3_v2_rr1.wav) rather than
the library's SFZ files, and each instrument's octave numbering is checked against a measured pitch, because the
library does not number octaves the same way for every instrument. A note picks the nearest sampled note, the dynamic
layer nearest its velocity and the next round robin, is pitched by resampling, and is faded out over the instrument's
release once it ends.

    inst = orchestra.load("violins_spic")
    inst.play(out, start_seconds, midi_note, seconds, velocity 0..1, pan -1..1)   # out: (n, 2) float32 at RATE
"""
import glob
import os
import re
import wave

import numpy as np

RATE = 44100
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "build", "vsco2"))
PITCH = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
NOTE = r"(?P<n>[A-G][#b]?-?\d)"


def midi(name):
    m = re.fullmatch(r"([A-G])([#b]?)(-?\d)", name)
    return 12 * (int(m.group(3)) + 1) + PITCH[m.group(1)] + {"#": 1, "b": -1, "": 0}[m.group(2)]


def hz(note):
    return 440.0 * 2 ** ((note - 69) / 12)


def read(path):
    """A WAV as float32 (n, 2) at its own rate (16 or 24 bit, mono or stereo)."""
    with wave.open(path) as w:
        n, ch, width, rate = w.getnframes(), w.getnchannels(), w.getsampwidth(), w.getframerate()
        raw = w.readframes(n)
    if width == 2:
        x = np.frombuffer(raw, "<i2").astype(np.float32) / 32768
    elif width == 3:
        b = np.frombuffer(raw, np.uint8).reshape(-1, 3).astype(np.int32)
        v = b[:, 0] | b[:, 1] << 8 | b[:, 2] << 16
        x = np.where(v & 0x800000, v - 0x1000000, v).astype(np.float32) / 8388608
    else:
        x = np.frombuffer(raw, "<i4").astype(np.float32) / 2147483648
    x = x.reshape(-1, ch)
    if ch == 1:
        x = np.repeat(x, 2, axis=1)
    return x[:, :2], rate


def measured(x, rate, guess):
    """Which octave of the named note the sample really sounds in (-2 to +2): the candidate whose harmonics hold the
    spectrum's energy and whose half-harmonics don't. A candidate an octave too low scores half, one too high is
    penalised by the odd harmonics between its own."""
    seg = x[int(.15 * rate): int(.15 * rate) + 16384, 0]
    if len(seg) < 4096:
        return 0
    spec = np.abs(np.fft.rfft(seg * np.hanning(len(seg))))
    freqs = np.fft.rfftfreq(len(seg), 1 / rate)

    def at(f):
        if f >= freqs[-1]:
            return 0.0
        i = int(round(f / freqs[1]))
        return float(spec[max(0, i - 2): i + 3].max())

    best, score = 0, -1e30
    for k in (-2, -1, 0, 1, 2):
        f = hz(guess + 12 * k)
        if f < 20 or f > 5000:
            continue
        s = sum(at(h * f) for h in range(1, 7)) - .7 * sum(at((h - .5) * f) for h in range(1, 7))
        if s > score:
            best, score = k, s
    return best


class Instrument:
    """One playing style of one instrument: regions by note and dynamic layer, round robins cycled."""

    def __init__(self, name, pattern, rx, release=.35, gain=1.0, layers=None, keymap=None, fixed=False, prefer=None,
                 shift=None):
        self.name, self.release, self.gain, self.fixed = name, release, gain, fixed
        self.regions = {}  # note -> {layer: [paths]}
        files = sorted(glob.glob(os.path.join(ROOT, pattern)))
        if prefer:
            # Several microphone mixes of the same note: keep the preferred one where it exists.
            kept = [f for f in files if prefer in os.path.basename(f)]
            files = kept or files
        for f in files:
            m = re.search(rx, os.path.basename(f))
            if not m:
                continue
            d = m.groupdict()
            if keymap is not None:
                note = keymap[d["k"]]
            elif fixed:
                note = int(d.get("k") or 0)
            else:
                note = midi(d["n"])
            layer = d.get("v") or "1"
            layer = layers.index(layer) + 1 if layers else int(layer)
            self.regions.setdefault(note, {}).setdefault(layer, []).append(f)
        if not self.regions:
            raise FileNotFoundError("no samples for %s in %s (is build/vsco2 downloaded?)" % (name, pattern))
        self.cache, self.turn = {}, {}
        # Octave numbering: measure the middle sample's pitch and shift every region to match.
        if shift is not None:
            # A style whose pitch can't be measured cleanly (a tremolo's noise) is given its family's shift.
            self.regions = {n + shift: v for n, v in self.regions.items()}
            self.shift = shift
        elif not fixed and keymap is None:
            notes = sorted(self.regions)
            probe = notes[len(notes) // 2]
            layer = sorted(self.regions[probe])[-1]
            x, rate = read(self.regions[probe][layer][0])
            shift = 12 * measured(x, rate, probe)
            if shift:
                self.regions = {n + shift: v for n, v in self.regions.items()}
            self.shift = shift
        else:
            self.shift = 0

    def sample(self, path):
        if path not in self.cache:
            x, rate = read(path)
            if rate != RATE:
                idx = np.arange(0, len(x), rate / RATE)
                x = np.stack([np.interp(idx, np.arange(len(x)), x[:, c]) for c in range(2)], axis=1).astype(np.float32)
            self.cache[path] = x
        return self.cache[path]

    def pick(self, note, velocity):
        near = min(self.regions, key=lambda n: (abs(n - note), n))
        layers = sorted(self.regions[near])
        layer = layers[min(len(layers) - 1, int(round(velocity * (len(layers) - 1))))]
        paths = self.regions[near][layer]
        key = (near, layer)
        i = self.turn.get(key, 0)
        self.turn[key] = i + 1
        return near, paths[i % len(paths)]

    def render(self, note, seconds, velocity=.7):
        near, path = self.pick(note, velocity)
        x = self.sample(path)
        ratio = 1.0 if self.fixed else 2 ** ((note - near) / 12)
        want = int((seconds + self.release) * RATE)
        if ratio != 1.0:
            idx = np.arange(0, min(len(x) - 1, want * ratio), ratio)
            x = np.stack([np.interp(idx, np.arange(len(x)), x[:, c]) for c in range(2)], axis=1).astype(np.float32)
        x = x[:want].copy()
        # Release: a quarter-sine fade from the note's end; a held sample ends there even if the recording rings on.
        end = int(seconds * RATE)
        if end < len(x):
            tail = len(x) - end
            x[end:] *= np.cos(np.linspace(0, np.pi / 2, tail))[:, None] ** 2
        # A touch of attack smoothing so a cut-in sample never clicks.
        a = min(len(x), 64)
        x[:a] *= np.linspace(0, 1, a)[:, None]
        return x * self.gain * (.35 + .65 * velocity)

    def play(self, out, start, note, seconds, velocity=.7, pan=0.0):
        x = self.render(note, seconds, velocity)
        i = int(start * RATE)
        if i >= len(out) or i + len(x) <= 0:
            return
        if i < 0:
            x, i = x[-i:], 0
        n = min(len(x), len(out) - i)
        left, right = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
        out[i:i + n, 0] += x[:n, 0] * left * 1.414
        out[i:i + n, 1] += x[:n, 1] * right * 1.414


KIT = {
    # Strings: sustains (with vibrato), short spiccato, tremolo.
    "violins": ("Strings/Violin Section/susVib/*.wav", NOTE + r"_v(?P<v>\d)", .6, 1.0, {}),
    "violins_spic": ("Strings/Violin Section/Spic/*.wav", NOTE + r"_v(?P<v>\d)", .12, 1.0, {}),
    "violins_trem": ("Strings/Violin Section/Trem/*.wav", NOTE + r"_v(?P<v>\d)", .5, 1.0, {}),
    "violas": ("Strings/Viola Section/susvib/*.wav", NOTE + r"_v(?P<v>\d)", .6, 1.0, {}),
    "violas_spic": ("Strings/Viola Section/spic/*.wav", NOTE + r"_v(?P<v>\d)", .12, 1.0, {}),
    "cellos": ("Strings/Cello Section/susvib/*.wav", NOTE + r"_v(?P<v>\d)", .6, 1.0, {}),
    "cellos_spic": ("Strings/Cello Section/spic/*.wav", NOTE + r"_v(?P<v>\d)", .14, 1.0, {}),
    "cellos_trem": ("Strings/Cello Section/trem/*.wav", NOTE + r"_v(?P<v>\d)", .5, 1.0, {"shift": 12}),
    "basses": ("Strings/Solo Contrabass/SusVib/*.wav", NOTE + r"_v(?P<v>\d)", .6, 1.3, {}),
    "basses_spic": ("Strings/Solo Contrabass/Spic/*.wav", NOTE + r"_v(?P<v>\d)", .15, 1.3, {}),
    # Brass.
    "horns": ("Brass/F Horn/sus/*.wav", NOTE + r"_v(?P<v>\d)", .5, 1.0, {}),
    "horns_stac": ("Brass/F Horn/stac/*.wav", NOTE + r"_v(?P<v>\d)", .25, 1.0, {}),
    "trombones": ("Brass/Tenor Trombone/sus/*.wav", NOTE + r"_v(?P<v>\d)", .45, 1.0, {}),
    "trombones_stac": ("Brass/Tenor Trombone/stac/*.wav", NOTE + r"_v(?P<v>\d)", .25, 1.0, {}),
    "tuba": ("Brass/Tuba/sus/*.wav", NOTE + r"_v(?P<v>\d)", .45, 1.0, {}),
    "tuba_stac": ("Brass/Tuba/stac/*.wav", NOTE + r"_v(?P<v>\d)", .25, 1.0, {"prefer": "_Sum"}),
    "trumpet": ("Brass/Trumpet/sus/*.wav", NOTE + r"_v(?P<v>\d)", .4, .9, {}),
    "trumpet_stac": ("Brass/Trumpet/stac/*.wav", NOTE + r"_v(?P<v>\d)", .2, .9, {}),
    # Keys and colours.
    "harp": ("Strings/Harp/*.wav", NOTE + r"_(?P<v>mf)", 1.6, 1.0, {"layers": ["mf"]}),
    "glock": ("Percussion/Glock/*.wav", NOTE + r"\.wav", 2.0, .7, {}),
    # Percussion: no pitch to change, a drum per "note" number.
    "timpani": ("Percussion/Timpani/Timpani*_Hit_*_Sum.wav", r"Timpani(?P<k>\d)_Hit_v(?P<v>\d)", 2.0, 1.0, {"fixed": True}),
    "timpani_roll": ("Percussion/Timpani/Rolls/*.wav", r"Timpani(?P<k>\d)_Roll_v(?P<v>\d)", 1.0, 1.0, {"fixed": True}),
    "bass_drum": ("VSCO 1 Percussion/drums/bass/*.wav", r"bdrum\d*_(?P<v>ppp|pp|p|mf|f|ff|fff)_", 2.5, 1.0,
                  {"fixed": True, "layers": ["ppp", "pp", "p", "mf", "f", "ff", "fff"]}),
    "tenor_drum": ("VSCO 1 Percussion/drums/tenor/*/*.wav", r"tenor[HL]_(?P<v>ppp|pp|p|mf|f|ff|fff)_", .8, .9,
                   {"fixed": True, "layers": ["ppp", "pp", "p", "mf", "f", "ff", "fff"]}),
}


def piano_keymap():
    chart = os.path.join(ROOT, "Keys", "Upright Piano", "MappingChart.txt")
    keys = {}
    with open(chart, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = re.match(r"(\d{3})=(\d+)", line.strip())
            if m:
                keys[m.group(1)] = int(m.group(2))
    return keys


_loaded = {}


def load(name):
    if name in _loaded:
        return _loaded[name]
    if name == "piano":
        inst = Instrument("piano", "Keys/Upright Piano/Player_*.wav", r"Player_dyn(?P<v>\d)_rr\d_(?P<k>\d{3})", 1.2, 1.0,
                          keymap=piano_keymap())
    else:
        pattern, rx, release, gain, extra = KIT[name]
        inst = Instrument(name, pattern, rx, release, gain, **extra)
    _loaded[name] = inst
    return inst


if __name__ == "__main__":
    # Lists every instrument: its notes, layers and octave shift. Run with Blender's Python (-I for the downloaded data).
    for name in list(KIT) + ["piano"]:
        inst = load(name)
        notes = sorted(inst.regions)
        layers = sorted({l for v in inst.regions.values() for l in v})
        print("%-15s %3d notes %3d..%-3d layers %s shift %+d" % (name, len(notes), notes[0], notes[-1], layers, inst.shift))
