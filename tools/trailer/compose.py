"""The trailer's score, composed: an orchestra (tools/trailer/orchestra.py, VSCO 2 CE samples, CC0) and our own synthesis
(tools/trailer/score.py's braams, booms and risers, and a formant choir) playing a written score on a beat grid, with
the mod's own power and story sounds laid over the cuts, the music ducked under them, and the mix mastered to
YouTube's -14 LUFS with a -1 dB ceiling.

How it is written follows .claude/skills/minecraft-trailer/references/trailer-music.md (researched trailer practice):
build by adding layers and keep the last climax the biggest; no bass in the beat before a big hit, silence before the
biggest; minor-key drive (i-VI-III-VII), the Phrygian bII for menace, and a lift to D major (a chromatic mediant from
Bb) for the title; close voicings for urgency, open for size; the motif on horns, at the end in three octaves; brass
played as sections; accents by place in the bar; percussion layers offset; a reverb per family with its low end cut.

The edit (tools/trailer/cut.py) hands it a cue sheet: the length, the tempo, the parts (where each section starts), the
cues (hits, braams, the drop, the title) and the sound effects (a file, a time, a gain). Every part has an arranger:

    prologue  the Last Day's evening: piano, harp, a low string pad, the motif on the piano
    light     the light growing: string tremolos and a timpani roll rising to the clash
    years     103 years later: a lone low D
    bunker    the pods: strings on a slow progression
    odessa    the ruins from the air: open strings, the horns (a section) on the motif, harp
    awaken    the pulse starting: cellos and basses on D
    powers    every power, in three tiers: strings over a D pedal; then horns, a moving bass, the bass drum's 3-3-2;
              then low brass, drum fills and the trumpet on the motif
    noticed   one low blow, then silence
    hunt      the world hunting: a dark ostinato on the Phrygian bII, low brass, a choir, tremolos high above
    collects  Sylar's card: the ostinato alone and a clock
    finale    the End: Bb, C, then D major, the whole orchestra on the motif in three octaves
    title, stinger

Cue kinds: hit, braam, title, freeze and drop (stopped time: everything but the sound effects stops, a thin held tone,
until the drop's hit). Run inside Blender (for the OGG decoder) through cut.py with "score": "orchestra" in the edit.
"""
import os
import sys
import wave

import numpy as np

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import orchestra as orc  # noqa: E402
import score as synth  # noqa: E402

RATE = orc.RATE
synth.SR = RATE
SOUNDS = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets",
                                      "heroesevolved", "sounds"))
RNG = np.random.default_rng(1406)

# Voicings, low to high: bass, low, mid, mid, high, high.
CH = {
    "Dm": [38, 50, 57, 62, 65, 69],
    "D": [38, 50, 57, 62, 66, 69],
    "Bb": [34, 46, 53, 62, 65, 70],
    "F": [41, 48, 53, 60, 65, 69],
    "C": [36, 48, 55, 60, 64, 67],
    "Gm": [43, 50, 55, 62, 67, 70],
    "Eb": [39, 51, 58, 63, 67, 70],
    "A": [45, 52, 57, 61, 64, 69],
}
# The trailer's motif, our own: D A F E | D C D (note, beats).
MOTIF = [(74, 2), (69, 1), (77, 1), (76, 2), (74, 1), (72, 1), (74, 4)]
STEM = {"violins": "strings_hi", "violas": "strings_hi", "cellos": "strings_lo", "basses": "strings_lo",
        "horns": "brass_hi", "trumpet": "brass_hi", "trombones": "brass_lo", "tuba": "brass_lo",
        "timpani": "perc", "bass": "perc", "tenor": "perc", "piano": "keys", "harp": "keys", "glock": "keys"}
# Stacked percussion a few milliseconds apart, so the layers don't flatten into one click.
OFFSET = {"bass_drum": 0.0, "timpani": .012, "timpani_roll": 0.0, "tenor_drum": .018}


class Score:
    def __init__(self, sheet):
        self.length = float(sheet["length"])
        self.bpm = float(sheet.get("bpm", 96))
        self.beat = 60 / self.bpm
        self.n = int((self.length + 4) * RATE)
        self.stems = {k: np.zeros((self.n, 2), np.float32) for k in
                      ("strings_hi", "strings_lo", "brass_hi", "brass_lo", "perc", "keys", "choir", "synth", "sfx")}
        self.cues = sorted(sheet.get("cues", []), key=lambda c: c["at"])
        parts = sorted(sheet.get("parts", []), key=lambda p: p["at"])
        self.parts = [(p["name"], p["at"], parts[i + 1]["at"] if i + 1 < len(parts) else self.length) for i, p in enumerate(parts)]
        self.sfx = sheet.get("sfx", [])
        self.chords = []  # (time, chord name) as the arrangers lay them down, for the hits

    # ------------------------------------------------------------------ Writing
    def accent(self, t):
        """Weight by place in the bar: the downbeat strongest, beat three next, the offbeats lighter."""
        pos = round(t / self.beat * 2) % 8  # eighths in the bar
        return {0: .12, 4: .06}.get(pos, -.04 if pos % 2 else 0.0)

    def note(self, inst, at, pitch, beats, vel=.6, pan=0.0, players=1):
        """A note, or a section of `players` playing it a few milliseconds and cents apart, spread a little."""
        if at >= self.length + 3:
            return
        stem = STEM[inst.split("_")[0]]
        for p in range(players):
            spread = 0 if players == 1 else (p - (players - 1) / 2) / max(1, players - 1)
            when = at + OFFSET.get(inst, 0) + RNG.uniform(-.006, .006) + (.011 * p if p else 0)
            cents = 0 if players == 1 else RNG.uniform(-.06, .06)
            v = float(np.clip(vel + RNG.uniform(-.03, .03) - .04 * p, .05, 1))
            orc.load(inst).play(self.stems[stem], max(0, when), pitch + cents, max(.05, beats * self.beat), v,
                                float(np.clip(pan + .18 * spread, -1, 1)))

    def sound(self, stem, at, x, gain=1.0, pan=0.0):
        """A mono signal from our own synthesis, placed and panned."""
        i = int(at * RATE)
        if i >= self.n or i < 0:
            return
        x = np.asarray(x, np.float32)[: self.n - i] * gain
        left, right = np.cos((pan + 1) * np.pi / 4) * 1.414, np.sin((pan + 1) * np.pi / 4) * 1.414
        self.stems[stem][i:i + len(x), 0] += x * left
        self.stems[stem][i:i + len(x), 1] += x * right

    def chord_at(self, t):
        name = "Dm"
        for at, c in self.chords:
            if at <= t + .05:
                name = c
        return name

    def grid(self, a, b, step):
        t, out = a, []
        while t < b - 1e-6:
            out.append(t)
            t += step * self.beat
        return out

    def progression(self, a, b, names, bars_each):
        out, i = [], 0
        for t in self.grid(a, b, 4 * bars_each):
            out.append((t, min(b, t + 4 * bars_each * self.beat), names[i % len(names)]))
            i += 1
        self.chords.extend((t, c) for t, _, c in out)
        return out

    def motif(self, inst, at, end, shift, vel, pan=0.0, players=1, line=MOTIF):
        t = at
        for i, (pitch, beats) in enumerate(line):
            if t >= end:
                break
            # The phrase leans on its first and last notes.
            v = vel + (.06 if i in (0, len(line) - 1) else 0)
            self.note(inst, t, pitch + shift, min(beats, (end - t) / self.beat) + .25, v, pan, players)
            t += beats * self.beat

    def choir(self, at, pitch, beats, level=.3, vowel="ah"):
        """A small choir on one note: our own formant synthesis (additive voices through vowel resonances, each with
        its own vibrato and a few cents apart, a breath of noise), swelling in and out."""
        seconds = beats * self.beat
        t = np.arange(int(seconds * RATE)) / RATE
        if len(t) < 10:
            return
        f = orc.hz(pitch)
        formants = {"ah": [(730, 1.0, 90), (1090, .5, 110), (2440, .25, 170)],
                    "oh": [(570, 1.0, 80), (840, .45, 100), (2410, .2, 170)]}[vowel]

        def gain(freq):
            return sum(a / (1 + ((freq - c) / bw) ** 2) for c, a, bw in formants)

        out = np.zeros_like(t)
        for v in range(4):
            detune = 2 ** ((v - 1.5) * 6 / 1200)
            vib = 1 + .011 * np.sin(2 * np.pi * (5.1 + .3 * v) * t + v) * np.clip(t / .8, 0, 1)
            ph = 2 * np.pi * np.cumsum(f * detune * vib) / RATE
            k = 1
            while f * k < 6000:
                out += gain(f * k) / k ** .9 * np.sin(k * ph)
                k += 1
        breath = synth.lowpass(synth.highpass(RNG.standard_normal(len(t)), formants[0][0] * .7), formants[1][0] * 1.4)
        env = np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 1.2
        self.sound("choir", at, (out / 4 + .12 * breath) * env * level, pan=RNG.uniform(-.3, .3))

    def swell(self, end, beats=4, size=1.0):
        """A layered transition into `end`: string tremolos rising, a timpani roll, a noise riser."""
        start = max(0, end - beats * self.beat)
        steps = self.grid(start, end, 1)
        for i, t in enumerate(steps):
            r = (i + 1) / len(steps)
            self.note("violins_trem", t, 81, 1.2, (.2 + .6 * r) * size, .5)
            self.note("violins_trem", t, 74, 1.2, (.2 + .55 * r) * size, -.5)
            self.note("cellos_trem", t, 50, 1.2, (.25 + .5 * r) * size, -.2)
        self.note("timpani_roll", start, 2, beats, .75 * size)
        self.sound("synth", end - beats * self.beat, synth.riser(beats * self.beat), .55 * size)

    # ------------------------------------------------------------------ The parts
    def prologue(self, a, b):
        for t0, t1, c in self.progression(a, b, ["Dm", "Bb", "F", "C"], 2):
            v = CH[c]
            span = (t1 - t0) / self.beat + .5
            self.note("cellos", t0, v[1], span, .42, -.25)
            self.note("cellos", t0, v[2], span, .38, -.15)
            if t0 > a:
                self.note("basses", t0, v[0], span, .38, 0)
                self.note("violins", t0, v[4] + 12, span, .32, .45)
            self.note("glock", t0, v[5] + 12, 2, .32, .4)
            for i, t in enumerate(self.grid(t0, t1, .5)):
                self.note("piano", t, [v[3], v[4], v[5], v[4]][i % 4], .6, .4 + self.accent(t) * .5, .15)
        self.motif("piano", a + 8 * self.beat, b, 0, .58, .1)

    def light(self, a, b):
        steps = self.grid(a, b, 2)
        for i, t in enumerate(steps):
            r = (i + 1) / max(1, len(steps))
            self.note("violins_trem", t, 74, 2.3, .25 + .6 * r, .45)
            self.note("violins_trem", t, 81, 2.3, .2 + .55 * r, .55)
            self.note("cellos_trem", t, 50, 2.3, .3 + .55 * r, -.3)
            self.note("cellos_trem", t, 57, 2.3, .25 + .5 * r, -.2)
            if r > .4:
                self.note("bass_drum", t, 0, 1, .15 + .35 * r)
        roll = max(a, b - 4 * self.beat)
        self.note("timpani_roll", roll, 2, (b - roll) / self.beat, .8)
        self.sound("synth", b - 1.6, synth.riser(1.6), .6)

    def years(self, a, b):
        start = a + self.beat
        self.note("basses", start, 38, (b - start) / self.beat + 1, .2, 0)
        self.note("cellos", start, 50, (b - start) / self.beat + 1, .16, .1)
        self.note("piano", start, 50, 3, .26)

    def pads(self, a, b, names, vel, wide=False):
        for t0, t1, c in self.progression(a, b, names, 2):
            v = CH[c]
            span = (t1 - t0) / self.beat + .5
            self.note("basses", t0, v[0], span, vel, 0)
            self.note("cellos", t0, v[1], span, vel + .05, -.25)
            self.note("violas", t0, v[2], span, vel, .15)
            self.note("violas", t0, v[3], span, vel, .25)
            # Open for size: the violins an octave up.
            lift = 12 if wide else 0
            self.note("violins", t0, v[4] + lift, span, vel - .05, .45)
            self.note("violins", t0, v[5] + lift, span, vel - .05, .55)
            self.note("timpani", t0, 1, 2, vel)

    def bunker(self, a, b):
        self.pads(a, b, ["Dm", "Bb", "Gm", "A"], .3)

    def odessa(self, a, b):
        self.pads(a, b, ["Dm", "Bb", "F", "C"], .4, wide=True)
        self.motif("horns", a, b, -12, .6, -.1, players=3)
        for i, t in enumerate(self.grid(a, b, .5)):
            v = CH[self.chord_at(t)]
            self.note("harp", t, [v[2], v[3], v[4], v[5], v[4], v[3]][i % 6], 1, .33, .3)
        self.swell(b, 2, .6)

    def awaken(self, a, b):
        steps = self.grid(a, b, .5)
        for i, t in enumerate(steps):
            r = i / max(1, len(steps))
            self.note("cellos_spic", t, 50, .5, .62 + .25 * r + self.accent(t), -.2)
            self.note("basses_spic", t, 38, .5, .58 + .25 * r + self.accent(t), 0)
        self.note("violins", a, 81, (b - a) / self.beat, .36, .5)
        for t in self.grid(a + self.beat, b, 2):
            self.note("tenor_drum", t, 0, 1, .45)
        self.chords.append((a, "Dm"))

    def powers(self, a, b):
        """The montage builds in three tiers, keeping the choir and the full brass for the finale."""
        total = max(1e-6, b - a)
        for t0, t1, c in self.progression(a, b, ["Dm", "Bb", "F", "C"], 1):
            r = (t0 - a) / total
            tier = 1 if r < .34 else 2 if r < .67 else 3
            lift = (-.12, 0, .08)[tier - 1]
            v = CH[c]
            # Tier 1: the strings' ostinato in close voicing over a D pedal.
            for i, t in enumerate(self.grid(t0, t1, .25)):
                self.note("violins_spic", t, [v[5], v[4], v[3], v[4]][i % 4] + 12, .3, .45 + lift + .3 * r + self.accent(t), .6)
            for i, t in enumerate(self.grid(t0, t1, .5)):
                self.note("violas_spic", t, [v[2], v[3]][i % 2], .5, .4 + lift + .25 * r + self.accent(t), -.5)
                self.note("cellos_spic", t, v[1] + (12 if i % 4 == 2 else 0), .5, .5 + lift + .25 * r + self.accent(t), -.2)
            bass = 38 if tier == 1 else v[0]
            for t in self.grid(t0, t1, 1):
                self.note("basses_spic", t, bass, 1, .45 + lift + .3 * r + self.accent(t), 0)
            self.note("timpani", t0, 2, 2, .45 + .3 * r)
            if tier >= 2:
                span = (t1 - t0) / self.beat + .3
                self.note("horns", t0, v[2], span, .35 + .3 * r, -.25, players=2)
                self.note("horns", t0, v[3], span, .35 + .3 * r, -.1, players=2)
                # The bass drum's 3-3-2: the bar's downbeat, the dotted quarter, the half-point push.
                for k, (beat, vel) in enumerate(((0, .75), (1.5, .55), (3, .65))):
                    self.note("bass_drum", t0 + beat * self.beat, 0, 1, vel + .2 * r)
                for t in self.grid(t0, t1, .5):
                    self.note("tenor_drum", t + .5 * self.beat, 0, .5, .22 + .25 * r, .3)
            if tier == 3:
                span = (t1 - t0) / self.beat + .3
                self.note("trombones", t0, v[1], span, .45 + .3 * r, .15, players=2)
                self.note("tuba", t0, v[0], span, .4 + .3 * r, 0)
                # A fill of sixteenths on the tenor drum to close every second bar.
                if round((t0 - a) / self.beat) % 8 == 4:
                    for i, t in enumerate(self.grid(t1 - self.beat, t1, .25)):
                        self.note("tenor_drum", t, 0, .25, .4 + .1 * i, .3)
        last = b - 12 * self.beat
        if last > a + 4 * self.beat:
            self.motif("trumpet", last, b, 0, .72, .1, players=2)
        self.swell(b, 2, .8)

    def noticed(self, a, b):
        self.note("trombones_stac", a, 38, 2, .9, 0, players=2)
        self.note("tuba_stac", a, 26, 2, .9)
        self.note("timpani", a, 1, 3, .9)
        self.note("bass_drum", a, 0, 3, .85)
        self.sound("synth", a, synth.boom(2.0, .8), .6)

    def hunt(self, a, b, quiet=False):
        for t0, t1, c in self.progression(a, b, ["Dm", "Eb", "Dm", "C"], 2):
            r = (t0 - a) / max(1e-6, b - a)
            v = CH[c]
            root = v[1]
            for i, t in enumerate(self.grid(t0, t1, .5)):
                self.note("cellos_spic", t, root + [0, 0, 1, 0, 0, 0, -2, 0][i % 8], .5, .6 + .2 * r + self.accent(t), -.2)
            for t in self.grid(t0, t1, 1):
                self.note("basses_spic", t, v[0], 1, .56 + .2 * r + self.accent(t), 0)
            if quiet:
                continue
            span = (t1 - t0) / self.beat + .4
            self.note("trombones", t0, v[1], span, .52 + .25 * r, .15, players=2)
            self.note("tuba", t0, v[0], span, .5 + .25 * r, 0)
            self.note("violins_trem", t0, v[5] + 12, span, .3 + .25 * r, .55)
            self.choir(t0, v[3], span, .32 + .1 * r, "ah")
            self.choir(t0, v[4], span, .28 + .1 * r, "ah")
            for t in self.grid(t0, t1, 4):
                self.note("bass_drum", t, 0, 1, .65 + .25 * r)
                self.note("bass_drum", t + 2.5 * self.beat, 0, 1, .52 + .2 * r)
            for t in self.grid(t0, t1, .5):
                self.note("tenor_drum", t, 0, .5, .26 + .2 * r + self.accent(t) * .5, .3)

    def collects(self, a, b):
        self.hunt(a, b, quiet=True)
        for t in self.grid(a, b + 2 * self.beat, 1):
            self.note("glock", t, 93, .3, .2, .5)

    def finale(self, a, b):
        """The biggest climax: open voicing, the whole orchestra, the motif in three octaves, lifting from Bb and C to D
        major (a chromatic mediant from Bb) for the title."""
        for t0, t1, c in self.progression(a, b, ["Bb", "C", "D", "D"], 1):
            v = CH[c]
            # A crescendo to the title: everything a little stronger each bar.
            g = .16 * (t0 - a) / max(1e-6, b - a) - .06
            for i, t in enumerate(self.grid(t0, t1, .25)):
                self.note("violins_spic", t, [v[5], v[4], v[3], v[4]][i % 4] + 12, .3, .72 + g + self.accent(t), .6)
            for i, t in enumerate(self.grid(t0, t1, .5)):
                self.note("violas_spic", t, [v[2], v[3]][i % 2], .5, .68 + g + self.accent(t), -.5)
                self.note("cellos_spic", t, v[1], .5, .78 + g + self.accent(t), -.2)
                self.note("basses_spic", t, v[0], .5, .74 + g + self.accent(t), 0)
            span = (t1 - t0) / self.beat + .3
            self.note("violins", t0, v[5] + 12, span, .5 + g, .55)
            self.note("tuba", t0, v[0], span, .7 + g, 0)
            self.choir(t0, v[3], span, .4 + g, "ah")
            self.choir(t0, v[4] + 12, span, .32 + g, "ah")
            for t in self.grid(t0, t1, 1):
                self.note("bass_drum", t, 0, 1, .62 + g + self.accent(t))
                self.note("timpani", t, 2, 1, .5 + g + self.accent(t))
            for t in self.grid(t0, t1, .25):
                self.note("tenor_drum", t, 0, .25, .32 + g + self.accent(t) * .5, .3)
        self.motif("trumpet", a, b, 0, .8, .1, players=2)
        self.motif("horns", a, b, -12, .85, -.25, players=3)
        self.motif("trombones", a, b, -24, .75, .2, players=2)
        self.sound("synth", b - 1.6, synth.riser(1.6), .7)

    def title(self, a, b):
        pass  # the title is a cue (the biggest hit); its chord rings out from there, and nothing is added after it

    def stinger(self, a, b):
        start = a + self.beat
        for i, (pitch, _) in enumerate(MOTIF[:4]):
            self.note("piano", start + i * self.beat * 1.5, pitch, 2.5, .3, .1)
        # Back to D minor, and a flat sixth left hanging: not over.
        self.note("glock", start + 6.5 * self.beat, 82, 2, .2, .35)
        self.note("cellos_spic", b - self.beat, 38, 1, .6, -.2)

    # ------------------------------------------------------------------ Cues
    def hit(self, at):
        v = CH[self.chord_at(at)]
        self.note("horns_stac", at, v[2], 1, .9, -.2, players=2)
        self.note("horns_stac", at, v[3], 1, .9, -.05, players=2)
        self.note("trombones_stac", at, v[1], 1, .9, .15, players=2)
        self.note("timpani", at, 2, 2, .8)
        self.sound("synth", at, synth.boom(1.4, .6), .45)

    def braam(self, at, size=1.0, chord="Dm"):
        v = CH[chord]
        beats = 3 * size
        for pitch, inst, vel, pan, players in ((v[1], "trombones", .95, .15, 2), (v[2], "trombones", .9, .25, 2),
                                               (v[0], "tuba", .95, 0, 1), (v[3], "horns", .9, -.25, 3),
                                               (v[4], "horns", .85, -.1, 2), (v[0], "basses", .9, 0, 1),
                                               (v[1], "cellos", .9, -.2, 1)):
            self.note(inst, at, pitch, beats, vel, pan, players)
        self.note("timpani", at, 1, 3, 1.0)
        self.note("bass_drum", at, 0, 3, 1.0)
        self.sound("synth", at, synth.braam(3.2 * size, size), .4)
        self.sound("synth", at, synth.boom(2.2 * size, .9 * size), .55)

    def title_hit(self, at):
        """The biggest hit, on D major with an added ninth, spread wide, left to ring out."""
        self.braam(at, 1.3, "D")
        ring = 9
        for pitch, inst, vel, pan in ((78, "violins", .7, .5), (81, "violins", .66, .6), (64, "violas", .62, -.4),
                                      (69, "violas", .6, -.3), (50, "cellos", .7, -.15), (38, "basses", .7, 0)):
            self.note(inst, at, pitch, ring, vel, pan)
        self.note("horns", at, 62, ring * .8, .75, -.2, players=3)
        self.note("horns", at, 66, ring * .8, .7, -.1, players=2)
        self.choir(at, 74, ring, .4, "ah")
        self.choir(at, 78, ring, .34, "ah")

    # ------------------------------------------------------------------ All of it
    def compose(self, track=None):
        """Writes the score; or, given `track` (a finished piece of music the edit names: its file, the pieces of it to
        play, its stems), plays that instead. The sound effects, the ducking and the mastering are the same either way."""
        if track:
            self.load_track(track)
        else:
            for name, a, b in self.parts:
                getattr(self, name)(a, b)
            for cue in self.cues:
                kind, at = cue["kind"], cue["at"]
                if kind == "hit":
                    self.hit(at)
                elif kind == "braam":
                    chord = self.chord_at(at)
                    self.braam(at, .9, chord if chord in ("Dm", "Bb", "Eb", "D") else "Dm")
                elif kind == "drop":
                    self.braam(at, 1.3, self.chord_at(at))
                elif kind == "title":
                    self.title_hit(at)
        self.place_sfx()
        return self.mix()

    def load_track(self, spec):
        """Lays the track's pieces end to end (each from its own time in the track, joined with a 12 ms equal-power
        crossfade on a beat), and where the edit asks for stems, plays the chosen stems, from the same pieces, instead
        of the full mix. Stopped time (freeze to drop) plays `freeze`'s stems; the beat before the title is silent."""
        import aud
        pieces = sorted(spec["pieces"], key=lambda p: p["at"])
        fade = int(.012 * RATE)
        t = np.arange(self.n) / RATE

        def decode(path):
            x = np.asarray(aud.Sound(path).resample(RATE, True).data(), np.float32)
            x = x[:, None] if x.ndim == 1 else x
            return np.repeat(x, 2, axis=1) if x.shape[1] == 1 else x[:, :2]

        def assemble(x):
            out = np.zeros((self.n, 2), np.float32)
            for i, p in enumerate(pieces):
                a = int(round(p["at"] * RATE))
                b = int(round(pieces[i + 1]["at"] * RATE)) if i + 1 < len(pieces) else self.n
                s = int(round(p["from"] * RATE))
                seg = x[s: s + min(self.n, b + fade) - a]
                if not len(seg):
                    continue
                env = np.ones(len(seg), np.float32)
                k = min(fade, len(seg))
                if i:
                    env[:k] = np.sin(np.linspace(0, np.pi / 2, k))
                if len(seg) > b - a:
                    tail = len(seg) - (b - a)
                    env[b - a:] = np.cos(np.linspace(0, np.pi / 2, tail))
                out[a:a + len(seg)] += seg * env[:, None]
            return out

        # Where each stem (and the full mix) plays: the edit's changes, then stopped time and the title's silence.
        mixes = sorted(spec.get("mixes", []), key=lambda m: m["at"])
        names = [os.path.splitext(f)[0] for f in sorted(os.listdir(spec["stems"])) if f.lower().endswith(".wav")] \
            if spec.get("stems") else []
        label = {n: n.split(" ", 1)[-1] for n in names}  # "6 Strings" -> "Strings"
        weight = {"mix": np.ones(self.n, np.float32)}
        weight.update({n: np.zeros(self.n, np.float32) for n in names})

        def apply(mask, gains):
            if gains is None:
                weight["mix"][mask] = 1
                for n in names:
                    weight[n][mask] = 0
                return
            if not names:
                # No stems here (they stay off GitHub, over its file limit): the full mix, quieter by the loudest
                # stem's gain, halved. Stopped time's strings at 0.2 become the mix at 0.1.
                kept = [g for k, g in gains.items() if k != "*"]
                weight["mix"][mask] = .5 * max(kept + [gains.get("*", 0)])
                return
            weight["mix"][mask] = 0
            for n in names:
                weight[n][mask] = gains.get(label[n], gains.get("*", 0))

        for i, m in enumerate(mixes):
            end = mixes[i + 1]["at"] if i + 1 < len(mixes) else self.length + 4
            apply((t >= m["at"]) & (t < end), m["gains"])
        if spec.get("freeze") is not None:
            for c in self.cues:
                if c["kind"] == "freeze":
                    end = next((d["at"] for d in self.cues if d["kind"] == "drop" and d["at"] > c["at"]), c["at"] + 3)
                    apply((t >= c["at"]) & (t < end), spec["freeze"])
        for c in self.cues:
            if c["kind"] == "title":
                apply((t >= c["at"] - .9 * self.beat) & (t < c["at"]), {"*": 0})
        bed = np.zeros((self.n, 2), np.float32)
        for name, w in weight.items():
            if not w.any():
                continue
            w = smooth(w, int(.025 * RATE))
            src = spec["file"] if name == "mix" else os.path.join(spec["stems"], name + ".wav")
            bed += assemble(decode(src)) * w[:, None]
        # The edit's levels: each holds from its item until the next.
        levels = sorted(spec.get("levels", []), key=lambda m: m["at"])
        if levels:
            gain = np.ones(self.n, np.float32)
            for i, m in enumerate(levels):
                end = levels[i + 1]["at"] if i + 1 < len(levels) else self.length + 4
                gain[(t >= m["at"]) & (t < end)] = 10 ** (m["db"] / 20)
            bed *= smooth(gain, int(.05 * RATE))[:, None]
        self.stems["track"] = bed
        self.frozen_track = spec.get("freeze") is not None
        print("COMPOSE playing %s in %d pieces%s" % (os.path.basename(spec["file"]), len(pieces),
                                                      ", stems from " + os.path.basename(spec["stems"]) if names else ""))

    def place_sfx(self):
        try:
            import aud
        except ImportError:
            print("COMPOSE no OGG decoder (run inside Blender): sound effects left out")
            return
        for fx in self.sfx:
            path = os.path.join(SOUNDS, fx["sound"] + ".ogg")
            if not os.path.exists(path):
                print("COMPOSE missing sound", fx["sound"])
                continue
            x = aud.Sound(path).resample(RATE, True).data()
            mono = x.mean(axis=1) if x.ndim == 2 else x
            self.sound("sfx", fx["at"], mono, fx.get("gain", .8), fx.get("pan", 0.0))

    # ------------------------------------------------------------------ Mixing and mastering
    def mix(self):
        n = self.n
        t = np.arange(n) / RATE
        # Stopped time and the beat before the title: everything but the sound effects stops.
        frozen = np.zeros(n, bool)
        for c in self.cues:
            if c["kind"] == "freeze":
                end = next((d["at"] for d in self.cues if d["kind"] == "drop" and d["at"] > c["at"]), c["at"] + 3)
                frozen |= (t >= c["at"]) & (t < end)
            if c["kind"] == "title":
                frozen |= (t >= c["at"] - .9 * self.beat) & (t < c["at"])
        gate = 1 - smooth(frozen.astype(np.float32), int(.06 * RATE))
        # The lead-in to every braam and the drop has almost no bass: the low strings, low brass, drums and booms
        # drop away for the beat before it.
        lead = np.zeros(n, bool)
        for c in self.cues:
            if c["kind"] in ("braam", "drop", "title"):
                lead |= (t >= c["at"] - .85 * self.beat) & (t < c["at"] - .02)
        low_gate = 1 - .85 * smooth(lead.astype(np.float32), int(.05 * RATE))
        held = (np.sin(2 * np.pi * 1760 * t) * .6 + np.sin(2 * np.pi * 2637 * t) * .4) * (.55 + .45 * np.sin(2 * np.pi * 3.1 * t))
        title = next((c["at"] for c in self.cues if c["kind"] == "title"), self.length)
        held *= smooth((frozen & (t < title - self.beat)).astype(np.float32), int(.1 * RATE)) * .05
        track = "track" in self.stems
        if track:
            # A finished track keeps its own shape: no gate (load_track already chose its stems for stopped time), no
            # hall or EQ of ours; the held tone only if the edit thins the track for stopped time.
            gate[:] = 1
            if not getattr(self, "frozen_track", False):
                held[:] = 0

        # A reverb per family (sends, wet level), each with a short pre-delay and its low end cut.
        sends = {"strings_hi": .34, "strings_lo": .22, "brass_hi": .28, "brass_lo": .2, "perc": .16, "keys": .3,
                 "choir": .42, "synth": .18, "sfx": .08, "track": 0}
        levels = {"strings_hi": 1.0, "strings_lo": 1.0, "brass_hi": .9, "brass_lo": .85, "perc": .9, "keys": .8,
                  "choir": .55, "synth": .6, "sfx": 1.0, "track": 1.0}
        halls = {"strings": hall(2.8, .025), "brass": hall(2.4, .02), "other": hall(2.2, .015)}
        music = np.zeros((n, 2), np.float32)
        for name, x in self.stems.items():
            if name == "sfx" or not x.any():
                continue
            if not sends[name]:
                music += x * levels[name]
                continue
            ir = halls["strings" if name.startswith("strings") else "brass" if name.startswith("brass") else "other"]
            wet = highpass(convolve(x, ir), 220)
            y = (x * (1 - sends[name]) + wet * sends[name] * 2.2) * levels[name]
            if name in ("strings_lo", "brass_lo", "perc", "synth"):
                y *= low_gate[:, None]
            music += y
        if not track:
            # The low-mids, where a big ensemble muddies: about 3 dB out round 300 Hz on the whole bus.
            music = bell(music, 300, -3.0, .6)
        music *= gate[:, None]
        music[:, 0] += held
        music[:, 1] += held
        fx = self.stems["sfx"]
        fx = fx * (1 - sends["sfx"]) + highpass(convolve(fx, halls["other"]), 220) * sends["sfx"] * 2.2
        # Duck the music under the sound effects: about 5 dB at full effect, eased in 30 ms and out over 400.
        env = smooth(np.abs(fx).max(axis=1), int(.03 * RATE))
        loud = env[env > 1e-4]
        duck = 1 - .45 * np.clip(env / (np.percentile(loud, 90) + 1e-6 if len(loud) else 1), 0, 1)
        duck = release(duck, int(.4 * RATE))
        out = music * duck[:, None] + fx
        out = out[: int((self.length + .05) * RATE)]
        fade = np.clip((len(out) / RATE - t[: len(out)]) / .08, 0, 1)
        out *= fade[:, None]
        return master(out)


def smooth(x, width):
    """A moving average (box) of width samples, same length."""
    if width < 2:
        return x
    c = np.cumsum(np.concatenate([[0.0], x]))
    half = width // 2
    idx = np.arange(len(x))
    lo, hi = np.clip(idx - half, 0, len(x)), np.clip(idx + half + 1, 0, len(x))
    return ((c[hi] - c[lo]) / (hi - lo)).astype(np.float32)


def release(g, width):
    """Lets a gain fall at once but rise only over `width` samples (blockwise, so it stays fast)."""
    block = 256
    m = len(g) // block * block
    blocks = g[:m].reshape(-1, block).min(axis=1)
    out = blocks.copy()
    k = max(1, width // block)
    for i in range(1, len(out)):
        out[i] = min(blocks[i], out[i - 1] + (1 - out[i - 1]) / k)
    full = np.repeat(out, block)
    return np.concatenate([full, np.full(len(g) - m, full[-1] if m else 1.0)]).astype(np.float32)


def hall(seconds, predelay):
    """A stereo hall: decaying, slightly darkening noise after a short pre-delay, each side its own."""
    t = np.arange(int(seconds * RATE)) / RATE
    out = []
    for side in range(2):
        rng = np.random.default_rng(20 + side)
        x = rng.standard_normal(len(t)) * np.exp(-t / (seconds / 6.5))
        x = synth.lowpass(x, 6500 - 1200 * side)
        x[: int(predelay * RATE)] = 0
        out.append(x / np.sqrt((x ** 2).sum()))
    return np.stack(out, axis=1).astype(np.float32)


def convolve(x, ir):
    n = len(x) + len(ir)
    size = 1 << (n - 1).bit_length()
    out = np.empty_like(x)
    for c in range(2):
        y = np.fft.irfft(np.fft.rfft(x[:, c], size) * np.fft.rfft(ir[:, c], size), size)
        out[:, c] = y[: len(x)]
    return out


def shape(x, curve):
    """Applies a gain curve over frequency (a function of the frequencies in Hz) to both channels."""
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    g = curve(f)
    out = np.empty_like(x)
    for c in range(x.shape[1]):
        out[:, c] = np.fft.irfft(np.fft.rfft(x[:, c]) * g, len(x))
    return out


def highpass(x, cutoff):
    return shape(x, lambda f: np.sqrt((f / cutoff) ** 4 / (1 + (f / cutoff) ** 4)))


def bell(x, centre, db, octaves):
    """A gentle bell EQ: `db` at `centre`, falling off over about `octaves` either side."""
    return shape(x, lambda f: 10 ** (db / 20 * np.exp(-(np.log2(np.maximum(f, 1) / centre) / octaves) ** 2)))


def loudness(x):
    """Integrated loudness, ITU-R BS.1770: K-weighting (applied in the frequency domain), 400 ms blocks with 75%
    overlap, the absolute gate at -70 LUFS and the relative gate 10 LU under."""
    f = np.fft.rfftfreq(len(x), 1 / RATE)
    z = np.exp(1j * 2 * np.pi * f / 48000)
    shelf = (1.53512485958697 - 2.69169618940638 / z + 1.19839281085285 / z ** 2) / (1 - 1.69065929318241 / z + .73248077421585 / z ** 2)
    high = (1 - 2 / z + 1 / z ** 2) / (1 - 1.99004745483398 / z + .99007225036621 / z ** 2)
    k = np.abs(shelf * high)
    power = np.zeros(len(x))
    for c in range(x.shape[1]):
        y = np.fft.irfft(np.fft.rfft(x[:, c]) * k, len(x))
        power += y ** 2
    block, hop = int(.4 * RATE), int(.1 * RATE)
    c = np.cumsum(np.concatenate([[0.0], power]))
    starts = np.arange(0, len(power) - block, hop)
    ms = (c[starts + block] - c[starts]) / block
    lk = -.691 + 10 * np.log10(ms + 1e-12)
    keep = ms[lk > -70]
    if not len(keep):
        return -70.0
    rel = -.691 + 10 * np.log10(keep.mean()) - 10
    keep = keep[(-.691 + 10 * np.log10(keep + 1e-12)) > rel]
    return float(-.691 + 10 * np.log10(keep.mean()))


def limit(x, ceiling):
    """A lookahead peak limiter: the gain each 256-sample block needs, held over its neighbours, released over 80 ms."""
    need = np.minimum(1, ceiling / (np.abs(x).max(axis=1) + 1e-9))
    block = 256
    m = len(need) // block * block
    g = need[:m].reshape(-1, block).min(axis=1)
    g = np.minimum(g, np.concatenate([g[1:], g[-1:]]))
    g = np.minimum(g, np.concatenate([g[:1], g[:-1]]))
    k = max(1, int(.08 * RATE) // block)
    for i in range(1, len(g)):
        g[i] = min(g[i], g[i - 1] + (1 - g[i - 1]) / k)
    centres = np.arange(len(g)) * block + block / 2
    full = np.interp(np.arange(len(x)), centres, g)
    full = np.minimum(full, need)
    return x * full[:, None]


def master(x, target=-14.0, ceiling=-1.0):
    """To YouTube's loudness: gentle glue (a light soft saturation), the level set to the target loudness, then the
    limiter under the ceiling, measured again and nudged."""
    x = np.tanh(x / (np.abs(x).max() + 1e-9)) / np.tanh(1.0)
    peak = 10 ** (ceiling / 20)
    for _ in range(3):
        x = x * 10 ** ((target - loudness(x)) / 20)
        x = limit(x, peak)
    print("COMPOSE mastered to %.1f LUFS, peak %.2f dBFS" % (loudness(x), 20 * np.log10(np.abs(x).max() + 1e-9)))
    return x


def write(sheet, path, track=None):
    x = Score(sheet).compose(track)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    return path
