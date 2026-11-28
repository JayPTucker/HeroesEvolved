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
A shot's "cues" turn the markers its driver wrote (film/<shot>/shot.txt) into hits in the score (tools/trailer/score.py).
"music": "<file>" plays that track instead of the generated score. The full guide is the trailer skill,
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


def grade_shot(film, work, item):
    """Grades a shot's frames into work/graded/<shot>/, skipping any already graded and newer than the film."""
    shot, first, last = item["shot"], item.get("from", 1), item.get("to")
    source = os.path.join(film, shot)
    notes = read_notes(source)
    last = last or notes["frames"]
    target = os.path.join(work, "graded", shot)
    os.makedirs(target, exist_ok=True)
    files, pending = [], []
    vignette(H, W)  # made once, before the threads share it
    # Blender's image calls stay on this thread; the numpy grade (which lets go of the GIL) runs on four more.
    with ThreadPoolExecutor(4) as pool:
        for n in range(first, last + 1):
            src, dst = os.path.join(source, "%05d.rgb" % n), os.path.join(target, "%05d.png" % n)
            if not os.path.exists(src):
                src = os.path.join(source, "%05d.png" % n)
            if not os.path.exists(dst) or os.path.getmtime(dst) < os.path.getmtime(src) or item.get("regrade"):
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


def build(edit):
    film, work = path(edit.get("film", "build/trailer-preview/film")), path(edit.get("work", "build/trailer/work"))
    os.makedirs(work, exist_ok=True)
    scene = bpy.context.scene
    se = scene.sequence_editor_create()
    frame = 1
    cues = []
    for i, item in enumerate(edit["timeline"]):
        if "shot" in item:
            files, notes = grade_shot(film, work, item)
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
            if item.get("fade_in") or item.get("fade_out"):
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
            length = round(item["seconds"] * FPS)
            text(se, "card%d" % i, item["card"], frame, length, 46, .5, 3, fade=10)
        elif "title" in item:
            length = round(item["seconds"] * FPS)
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
        frame += length
    end = frame - 1
    sheet = {"length": end / FPS, "cues": cues}
    with open(os.path.join(work, "cues.json"), "w", encoding="utf-8") as f:
        json.dump(sheet, f, indent=1)
    # The generated score follows the cue sheet; an edit may name a track instead (the mod's own music, or a licensed
    # track the user supplies), which then plays from the first frame.
    if edit.get("music"):
        music = path(edit["music"])
    else:
        music = os.path.join(work, "score.wav")
        score.write(sheet, music)
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
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    with open(path(args[0]), encoding="utf-8") as f:
        edit = json.load(f)
    render(edit, build(edit))


main()
