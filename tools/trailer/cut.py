"""Cuts the trailer: grades the filmed frames, lays them out with cards and titles, scores it and renders an MP4.

Run with Blender 5.0 (its video editor does the titles and the encoding):
    "C:/Program Files/Blender Foundation/Blender 5.0/blender.exe" -b --factory-startup -P tools/trailer/cut.py -- tools/trailer/test-shot.json

The edit file lists the timeline in order (paths are from the repository root):
    {
      "film": "build/trailer-preview/film",          frames from tools/trailer-preview.init.gradle
      "work": "build/trailer/work",                  graded frames and the cue sheet go here
      "out": "build/trailer/test-shot.mp4",
      "timeline": [
        {"card": "SOME OF US WOKE UP DIFFERENT.", "seconds": 2.5},
        {"shot": "11-firebending-sunset", "from": 1, "to": 252, "fade_in": 10,
         "cues": {"kick": "braam", "flip": "braam", "blue-kick": "hit"}},
        {"title": "HEROES EVOLVED", "sub": "Twenty powers. One world that remembers.", "seconds": 5}
      ]
    }
A shot's "cues" turn the markers its driver wrote (film/<shot>/shot.txt) into hits in the score (tools/trailer/score.py,
or with "score": "orchestra" the composed score, tools/trailer/compose.py). "bpm" cuts every item to the beat, "part"
names the music's section from that item on, and "sfx" lays the mod's own sounds over a marker or frame.
"music": "<file>" (with "music_offset" in seconds) plays that track instead, still under the sound effects.
"--score-only" after the edit file writes the score alone. The full guide is the trailer skill,
.claude/skills/minecraft-trailer/SKILL.md.

The look is done here in numpy rather than with the editor's strip modifiers, which crash Blender 5.0.1 when added from
a script: a warm-highlight, teal-shadow split tone, a gentle S-curve, a glow off the bright parts (fire reads hot), a
vignette, fine grain, and 2.39:1 letterbox bars.
"""
import json
import os
import sys
from concurrent.futures import ThreadPoolExecutor

import bpy
import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.dont_write_bytecode = True  # no __pycache__ left in tools/
import score  # noqa: E402

FPS = 24
W, H = 1920, 1080
BAR = round((H - W / 2.39) / 2)
FONT = "C:/Windows/Fonts/bahnschrift.ttf"


def path(p):
    return p if os.path.isabs(p) else os.path.join(ROOT, p)


# ---------------------------------------------------------------------------------------------------- The grade

def box(a, r, axis):
    """A box blur of radius r along one axis (edges held)."""
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis, dtype=np.float32)
    n = a.shape[axis]
    return (np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis) - np.take(c, np.arange(n), axis=axis)) / (2 * r + 1)


def blur(a, r, passes=2):
    for _ in range(passes):
        a = box(box(a, r, 0), r, 1)
    return a


_vignette = None


def vignette(h, w):
    global _vignette
    if _vignette is None or _vignette.shape != (h, w):
        y, x = np.mgrid[0:h, 0:w].astype(np.float32)
        r = np.sqrt(((x - w / 2) / (w / 2)) ** 2 + ((y - h / 2) / (h / 2)) ** 2 * .6)
        k = np.clip((r - .45) / .75, 0, 1)
        _vignette = 1 - .38 * k * k * (3 - 2 * k)
    return _vignette


def grade(rgb, frame, look):
    """The trailer's look on one frame (floats 0..1, rows bottom-up as Blender keeps them)."""
    h, w, _ = rgb.shape
    x = rgb * np.array(look.get("balance", [1.03, 1.0, .965]), np.float32) * look.get("exposure", 1.0)
    # Glow: the bright parts, blurred wide at quarter size and laid back over the frame, warm.
    bright = np.clip(x.max(axis=2, keepdims=True) - .70, 0, 1) * x
    small = bright[: h - h % 4, : w - w % 4].reshape(h // 4, 4, w // 4, 4, 3).mean(axis=(1, 3))
    glow = blur(small, 5) * .6 + blur(small, 16) * .4
    glow = np.repeat(np.repeat(glow, 4, axis=0), 4, axis=1)
    glow = blur(glow, 2, 1)
    full = np.zeros_like(x)
    full[: glow.shape[0], : glow.shape[1]] = glow
    x = x + full * look.get("glow", .75) * np.array([1.0, .82, .62], np.float32)
    # A soft shoulder instead of a hard clip, so the sun and fire roll off to white rather than flattening.
    x = np.where(x > .8, .8 + .2 * np.tanh((x - .8) / .2), x)
    # A gentle S-curve for contrast.
    x = x + look.get("contrast", .3) * (x * x * (3 - 2 * x) - x)
    # Split tone: teal in the shadows, amber in the highlights.
    lum = (x @ np.array([.2126, .7152, .0722], np.float32))[..., None]
    x = x + (1 - lum) ** 2 * np.array([-.025, .008, .03], np.float32) + lum ** 2 * np.array([.03, .006, -.035], np.float32)
    lum = (x @ np.array([.2126, .7152, .0722], np.float32))[..., None]
    x = lum + (x - lum) * look.get("saturation", 1.1)
    # The vignette darkens the edges but leaves light sources alone: a greyed sun reads as dirt.
    keep = np.clip((lum - .75) / .2, 0, 1)
    x = x * (vignette(h, w)[..., None] * (1 - keep) + keep)
    # Fine grain, mostly in the mid-tones.
    grain = np.random.default_rng(frame).standard_normal((h, w, 1)).astype(np.float32) * .014
    x = x + grain * np.clip(lum * (1 - lum) * 4, 0, 1)
    x = np.clip(x, 0, 1)
    x[:BAR] = 0
    x[h - BAR:] = 0
    return x


def load(file, size=(W, H)):
    """A frame as floats, rows bottom-up as Blender keeps them: raw RGB from the recorder, or a PNG."""
    if file.endswith(".rgb"):
        w, h = size
        raw = np.fromfile(file, dtype=np.uint8).reshape(h, w, 3)[::-1]
        return raw.astype(np.float32) / 255
    img = bpy.data.images.load(file, check_existing=False)
    img.colorspace_settings.name = "Non-Color"
    w, h = img.size
    buf = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(buf)
    bpy.data.images.remove(img)
    return buf.reshape(h, w, 4)[..., :3].copy()


def save(rgb, file):
    h, w, _ = rgb.shape
    img = bpy.data.images.new("graded", w, h, alpha=False)
    img.colorspace_settings.name = "Non-Color"
    rgba = np.concatenate([rgb, np.ones((h, w, 1), np.float32)], axis=2)
    img.pixels.foreach_set(rgba.ravel())
    img.filepath_raw = file
    img.file_format = "PNG"
    img.save()
    bpy.data.images.remove(img)


SCORE_ONLY = False


def grade_shot(film, work, item):
    """Grades a shot's frames into work/graded/<shot>/, skipping any already graded and newer than the film."""
    shot, first, last = item["shot"], item.get("from", 1), item.get("to")
    source = os.path.join(film, shot)
    notes = read_notes(source)
    last = last or notes["frames"]
    target = os.path.join(work, "graded", shot)
    if SCORE_ONLY:
        # Laying out the timeline for the score alone: the frames' names, nothing graded.
        return [os.path.join(target, "%05d.png" % n) for n in range(first, last + 1)], notes
    os.makedirs(target, exist_ok=True)
    # Graded frames are kept between cuts, with the look they were graded with: a different look regrades them.
    look = json.dumps(item.get("look", {}), sort_keys=True)
    kept = os.path.join(target, "look.json")
    stale = item.get("regrade") or not os.path.exists(kept) or open(kept, encoding="utf-8").read() != look
    with open(kept, "w", encoding="utf-8") as f:
        f.write(look)
    files, pending = [], []
    vignette(H, W)  # made once, before the threads share it
    # Blender's image calls stay on this thread; the numpy grade (which lets go of the GIL) runs on four more.
    with ThreadPoolExecutor(4) as pool:
        for n in range(first, last + 1):
            src, dst = os.path.join(source, "%05d.rgb" % n), os.path.join(target, "%05d.png" % n)
            if not os.path.exists(src):
                src = os.path.join(source, "%05d.png" % n)
            if not os.path.exists(dst) or os.path.getmtime(dst) < os.path.getmtime(src) or stale:
                pending.append((dst, pool.submit(grade, load(src, notes["size"]), n, item.get("look", {}))))
            while len(pending) > 6 or pending and n == last:
                done, future = pending.pop(0)
                save(future.result(), done)
            files.append(dst)
            if n % 48 == 0:
                print("TRAILER_CUT graded %s %d/%d" % (shot, n - first + 1, last - first + 1), flush=True)
    return files, notes


def read_notes(folder):
    notes = {"marks": {}, "size": (W, H)}
    with open(os.path.join(folder, "shot.txt"), encoding="utf-8") as f:
        for line in f:
            parts = line.split()
            if parts[0] == "mark":
                notes["marks"][parts[1]] = int(parts[2])
            elif parts[0] == "frames":
                notes["frames"] = int(parts[1])
            elif parts[0] == "size":
                notes["size"] = (int(parts[1]), int(parts[2]))
    return notes


# ---------------------------------------------------------------------------------------------------- The edit

def text(se, name, words, start, length, size, y, channel, fade=8, bold=False):
    strip = se.strips.new_effect(name=name, type="TEXT", channel=channel, frame_start=start, length=length)
    strip.text = words
    strip.font = bpy.data.fonts.load(FONT, check_existing=True)
    strip.font_size = size
    strip.use_bold = bold
    strip.color = (.95, .93, .88, 1)
    strip.location = (.5, y)
    strip.alignment_x = "CENTER"
    strip.anchor_x = "CENTER"
    strip.anchor_y = "CENTER"
    fades(strip, start, length, fade)
    return strip


def fades(strip, start, length, fade, fade_in=True, fade_out=True):
    """Fades a strip in and out by keying its opacity."""
    if fade <= 0:
        return
    keys = []
    if fade_in:
        keys += [(start, 0), (start + fade, 1)]
    if fade_out:
        keys += [(start + length - fade, 1), (start + length, 0)]
    for frame, alpha in keys:
        strip.blend_alpha = alpha
        strip.keyframe_insert("blend_alpha", frame=frame)


def spaced(words):
    """Letters spread apart, the way trailer titles are set (the text strip has no tracking of its own)."""
    return "  ".join(" ".join(word) for word in words.split(" "))


class Grid:
    """Where the beats fall on the timeline, in frames counted from 1. A tempo gives a regular grid (96 bpm is exactly
    15 frames a beat). A track gives its measured beats (tools/trailer/analyze.py writes them beside it), because a
    generated track drifts: Suno's ran from 98.4 to 100 bpm, half a second off any fixed grid by the end. The edit may
    play pieces of a track: each piece starts at a timeline time with a time in the track, and the beats are that
    piece's."""

    def __init__(self, bpm=None, beats=None):
        self.step = FPS * 60 / bpm if bpm else None
        self.beats = np.asarray(beats, float) if beats is not None else None
        self.pieces = [(0.0, 0.0)]

    def start(self, at, source, exact=False):
        """A new piece of the track from timeline `at` seconds, at `source` seconds into the track (moved to its
        nearest beat unless `exact`: a hit pushed half a beat ahead of the bar starts on its own attack). Returns the
        track time used."""
        if self.beats is not None and not exact:
            source = float(self.beats[np.argmin(np.abs(self.beats - source))])
        if self.pieces and abs(self.pieces[-1][0] - at) < 1e-6:
            self.pieces[-1] = (at, source)
        else:
            self.pieces.append((at, source))
        return source

    def near(self, frame):
        """The beat (a fractional frame) nearest a timeline frame."""
        if self.beats is None:
            return 1 + round((frame - 1) / self.step) * self.step
        at, source = self.pieces[-1]
        frames = 1 + (self.beats - source + at) * FPS
        # Past either end of the track's beats (a stinger after the music has ended), the grid goes on at its tempo:
        # snapping to the last beat there once gave the stinger no frames at all.
        step = self.period()
        if frame > frames[-1]:
            return float(frames[-1] + round((frame - frames[-1]) / step) * step)
        if frame < frames[0]:
            return float(frames[0] - round((frames[0] - frame) / step) * step)
        return float(frames[np.argmin(np.abs(frames - frame))])

    def period(self):
        if self.beats is None:
            return self.step
        return float(np.median(np.diff(self.beats))) * FPS


def cue_frames(item, notes):
    return [notes["marks"][mark] + (cue.get("shift", 0) if isinstance(cue, dict) else 0)
            for mark, cue in item.get("cues", {}).items() if mark in notes["marks"]]


def slide(item, notes, frame, grid):
    """Slides a shot's stretch less than half a beat either way so that its first cue (with its shift) lands on a beat.
    `frame` is where the shot starts on the timeline."""
    first = item.get("from", 1)
    cues = cue_frames(item, notes)
    if cues:
        at = frame + cues[0] - first
        first += int(round(at - grid.near(at)))
    beat = int(round(grid.period()))
    while first < 1:
        first += beat
    return moved(item, notes, first)


def moved(item, notes, first):
    """The stretch moved to start at `first`, its end moved with it: the length to fit to the beat is the one the edit
    asked for."""
    item = dict(item)
    last = item.get("to") or notes["frames"]
    item["from"], item["to"] = first, last + first - item.get("from", 1)
    return item


def fit_end(item, notes, frame, grid):
    """Ends a shot's stretch on a beat, so the next cut lands on one. Rounding the length down once cut Starfall's
    impact off (its hit played over the next shot): every cue stays inside the stretch with a third of a second after
    it, growing it by beats while the film has frames."""
    item = dict(item)
    first, last = item["from"], item.get("to") or notes["frames"]
    cues = cue_frames(item, notes)
    beat = grid.period()
    end = max(grid.near(frame + last - first + 1), grid.near(frame + beat))
    while cues and frame + max(cues) - first > end - 9 and first + (end + beat - frame) - 1 <= notes["frames"]:
        end = grid.near(end + beat)
    length = int(round(end)) - frame
    step = int(round(beat))
    while first + length - 1 > notes["frames"] and first - step >= 1:
        first -= step
    item["from"], item["to"] = first, min(notes["frames"], first + length - 1)
    return item


def beat_length(frames, frame, grid):
    """A card's or the title's length, ending on a beat (at least one beat long)."""
    if not grid:
        return frames
    return int(round(max(grid.near(frame + frames), grid.near(frame + grid.period())))) - frame


def sfx_at(fx, frame, first, notes):
    """When a sound effect plays, in timeline seconds: at a shot's marker, at a frame of the shot, or (for a card or
    the title) frames after the item starts."""
    at = fx.get("at", 0)
    if isinstance(at, str):
        at = notes["marks"].get(at) if notes else None
        if at is None:
            return None
    offset = at - first if notes else at
    return (frame - 1 + offset + fx.get("shift", 0)) / FPS


def build(edit):
    film, work = path(edit.get("film", "build/trailer-preview/film")), path(edit.get("work", "build/trailer/work"))
    os.makedirs(work, exist_ok=True)
    scene = bpy.context.scene
    se = scene.sequence_editor_create()
    frame = 1
    cues, parts, effects, mixes, levels = [], [], [], [], []
    # Every cut lands on a beat: the track's measured beats when the edit names a track that has been analysed, or the
    # tempo's grid (96 bpm is exactly 15 frames a beat at 24 fps).
    grid = None
    track = path(edit["music"]) if edit.get("music") else None
    measured = os.path.splitext(track)[0] + ".json" if track else None
    if measured and os.path.exists(measured):
        with open(measured, encoding="utf-8") as f:
            grid = Grid(beats=json.load(f)["beats"])
        grid.start(0.0, float(edit.get("music_offset", 0)))
    elif edit.get("bpm"):
        grid = Grid(bpm=edit["bpm"])
    for i, item in enumerate(edit["timeline"]):
        notes = None
        # "track": {"from": seconds} plays the track from there, from this item's start; with "at": a marker, from that
        # marker (a hit hides the join). "stems": {"Strings": 1, "*": 0} plays those stems instead of the full mix from
        # this item on, "stems": "mix" goes back to the full mix.
        anchor = item.get("track")
        if anchor is not None and not anchor.get("at") and grid:
            grid.start((frame - 1) / FPS, anchor["from"], anchor.get("exact", False))
        if "stems" in item:
            mixes.append({"at": (frame - 1) / FPS, "gains": None if item["stems"] == "mix" else item["stems"]})
        if "music_db" in item:
            # The track's level from this item on, so the montage can sit under the finale.
            levels.append({"at": (frame - 1) / FPS, "db": float(item["music_db"])})
        if "shot" in item:
            notes = read_notes(os.path.join(film, item["shot"]))
            if grid:
                mark = None
                if anchor is not None and anchor.get("at") in notes["marks"]:
                    cue = item.get("cues", {}).get(anchor["at"])
                    mark = notes["marks"][anchor["at"]] + (cue.get("shift", 0) if isinstance(cue, dict) else 0)
                # An exact hit the track already reaches within about half a beat moves the picture instead: the shot
                # slides so its marker lands on the hit, and the music plays on unbroken. (Jumping back would play the
                # hit's attack twice.)
                aligned = False
                if mark is not None and anchor.get("exact") and grid.beats is not None:
                    at_piece, source = grid.pieces[-1]
                    target = 1 + (at_piece + anchor["from"] - source) * FPS
                    natural = frame + mark - item.get("from", 1)
                    if abs(natural - target) <= .6 * grid.period() and item.get("from", 1) + natural - target >= 1:
                        item = moved(item, notes, item.get("from", 1) + int(round(natural - target)))
                        aligned = True
                if not aligned:
                    item = slide(item, notes, frame, grid)
                    if mark is not None:
                        grid.start((frame - 1 + mark - item["from"]) / FPS, anchor["from"], anchor.get("exact", False))
                item = fit_end(item, notes, frame, grid)
            files, notes = grade_shot(film, work, item)
            if not SCORE_ONLY:
                strip = se.strips.new_image(name=item["shot"], filepath=files[0], channel=1, frame_start=frame)
                for f in files[1:]:
                    strip.elements.append(os.path.basename(f))
            length = len(files)
            first = item.get("from", 1)
            # A cue is a kind of hit, or {"kind": ..., "shift": frames} to land it on the frame the impact shows (a
            # strike reaches the screen a few ticks after the driver throws it: many frames in slow motion).
            for mark, cue in item.get("cues", {}).items():
                cue = cue if isinstance(cue, dict) else {"kind": cue}
                if mark in notes["marks"]:
                    at = frame - 1 + notes["marks"][mark] - first + cue.get("shift", 0)
                    cues.append({"at": at / FPS, "kind": cue["kind"]})
            if (item.get("fade_in") or item.get("fade_out")) and not SCORE_ONLY:
                black = se.strips.new_effect(name="fade%d" % i, type="COLOR", channel=9, frame_start=frame, length=length)
                black.color = (0, 0, 0)
                keys = []
                if item.get("fade_in"):
                    keys += [(frame, 1), (frame + item["fade_in"], 0)]
                if item.get("fade_out"):
                    keys += [(frame + length - item["fade_out"], 0), (frame + length, 1)]
                for at, alpha in keys:
                    black.blend_alpha = alpha
                    black.keyframe_insert("blend_alpha", frame=at)
        elif "card" in item:
            length = beat_length(round(item["seconds"] * FPS), frame, grid)
            if item["card"]:
                text(se, "card%d" % i, item["card"], frame, length, 46, .5, 3, fade=10)
            # A card may start a cue of its own: "freeze" on a black beat cuts the music into stopped time.
            if item.get("cue"):
                cues.append({"at": (frame - 1) / FPS, "kind": item["cue"]})
        elif "title" in item:
            length = beat_length(round(item["seconds"] * FPS), frame, grid)
            cues.append({"at": (frame - 1) / FPS, "kind": "title"})
            text(se, "title", spaced(item["title"]), frame, length, 118, .53, 3, fade=0, bold=True)
            if item.get("sub"):
                text(se, "sub", item["sub"], frame + 18, length - 18, 34, .40, 4, fade=12)
            if item.get("small"):
                text(se, "small", item["small"], frame + 30, length - 30, 24, .30, 5, fade=12)
            # The title cuts in on the hit and fades out at the end.
            fades(se.strips["title"], frame, length, 18, fade_in=False)
        else:
            raise ValueError("Unknown timeline item %r" % item)
        # A part of the score starts with the item, or at one of its markers ("finale@resume").
        if item.get("part"):
            name, _, mark = item["part"].partition("@")
            at = sfx_at({"at": mark}, frame, item.get("from", 1), notes) if mark else (frame - 1) / FPS
            parts.append({"name": name, "at": at if at is not None else (frame - 1) / FPS})
        # The item's own sound effects: the mod's power and story sounds, laid over the cut.
        for fx in item.get("sfx", []):
            at = sfx_at(fx, frame, item.get("from", 1) if notes else 0, notes)
            if at is not None:
                effects.append({"at": at, "sound": fx["sound"], "gain": fx.get("gain", .8), "pan": fx.get("pan", 0.0)})
        frame += length
    end = frame - 1
    sheet = {"length": end / FPS, "cues": cues, "parts": parts, "sfx": effects, "bpm": edit.get("bpm", 96)}
    if track:
        # The pieces of the track the edit plays, and which stems.
        stems = path(edit["stems"]) if edit.get("stems") else None
        if stems and not os.path.isdir(stems):
            print("TRAILER_TRACK no stems at %s: the full mix plays, quieter where the edit asks for stems" % stems)
            stems = None
        pieces = [{"at": a, "from": s} for a, s in grid.pieces] if grid and grid.beats is not None \
            else [{"at": 0.0, "from": float(edit.get("music_offset", 0))}]
        sheet["track"] = {"file": track, "pieces": pieces, "stems": stems, "mixes": mixes, "levels": levels,
                          "freeze": edit.get("freeze_stems")}
        for p in pieces:
            print("TRAILER_TRACK at %6.2f s plays the track from %6.2f s" % (p["at"], p["from"]), flush=True)
    with open(os.path.join(work, "cues.json"), "w", encoding="utf-8") as f:
        json.dump(sheet, f, indent=1)
    # The generated score follows the cue sheet; an edit may name a track instead (the mod's own music, or a licensed
    # track the user supplies), which goes through the same mix: the mod's sounds on top, the track ducked under them.
    if track:
        import compose
        music = os.path.join(work, "score.wav")
        compose.write(sheet, music, sheet["track"])
    elif edit.get("score") == "orchestra":
        # The composed score: the orchestra and our synthesis on the beat grid, the sound effects over it, mastered.
        import compose
        music = os.path.join(work, "score.wav")
        compose.write(sheet, music)
    else:
        music = os.path.join(work, "score.wav")
        score.write(sheet, music)
    if not SCORE_ONLY:
        se.strips.new_sound(name="score", filepath=music, channel=10, frame_start=1)
    print("TRAILER_CUT %d frames (%.1f s), cues %s" % (end, end / FPS, cues), flush=True)
    return end


def render(edit, end):
    scene = bpy.context.scene
    r = scene.render
    r.resolution_x, r.resolution_y, r.resolution_percentage = W, H, 100
    r.fps, r.fps_base = FPS, 1
    scene.frame_start, scene.frame_end = 1, end
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    r.use_sequencer = True
    r.use_compositing = False
    r.image_settings.media_type = "VIDEO"
    r.image_settings.file_format = "FFMPEG"
    r.ffmpeg.format = "MPEG4"
    r.ffmpeg.codec = "H264"
    r.ffmpeg.constant_rate_factor = "HIGH"
    r.ffmpeg.ffmpeg_preset = "GOOD"
    r.ffmpeg.audio_codec = "AAC"
    r.ffmpeg.audio_bitrate = 256
    r.ffmpeg.audio_mixrate = 48000
    r.ffmpeg.audio_channels = "STEREO"
    out = path(edit["out"])
    os.makedirs(os.path.dirname(out), exist_ok=True)
    r.filepath = out
    bpy.ops.render.render(animation=True)
    print("TRAILER_CUT rendered", out, flush=True)


def main():
    """cut.py -- <edit.json> [--score-only]: with --score-only, only the timeline and the score (work/score.wav)."""
    global SCORE_ONLY
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    SCORE_ONLY = "--score-only" in args
    with open(path(args[0]), encoding="utf-8") as f:
        edit = json.load(f)
    end = build(edit)
    if not SCORE_ONLY:
        render(edit, end)


main()
