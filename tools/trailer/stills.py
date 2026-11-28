"""Saves frames of a rendered trailer as half-size PNGs, to look at the cut itself (titles, fades, the grade), and tiles
them into one sheet (<out>/sheet.png, three across at a quarter size) to look over a whole cut at once.

    "C:/Program Files/Blender Foundation/Blender 5.0/blender.exe" -b --factory-startup -P tools/trailer/stills.py -- build/trailer/test-shot.mp4 build/trailer/stills 1 40 120

Each number is a frame (from 1); the stills are <out>/<frame>.png.
"""
import os
import sys

import bpy
import numpy as np

args = sys.argv[sys.argv.index("--") + 1:]
movie, out, frames = os.path.abspath(args[0]), os.path.abspath(args[1]), [int(a) for a in args[2:]]
os.makedirs(out, exist_ok=True)
scene = bpy.context.scene
se = scene.sequence_editor_create()
strip = se.strips.new_movie(name="cut", filepath=movie, channel=1, frame_start=1)
r = scene.render
r.resolution_x, r.resolution_y, r.resolution_percentage = 1920, 1080, 50
r.use_sequencer = True
r.image_settings.media_type = "IMAGE"
r.image_settings.file_format = "PNG"
scene.view_settings.view_transform = "Standard"
scene.frame_end = strip.frame_final_end
files = []
for f in frames:
    scene.frame_set(f)
    r.filepath = os.path.join(out, "%05d.png" % f)
    bpy.ops.render.render(write_still=True)
    files.append(r.filepath)

# The sheet: every still at half its size (a quarter of the film's), three across, a thin black line between.
tiles = []
for file in files:
    img = bpy.data.images.load(file, check_existing=False)
    img.colorspace_settings.name = "Non-Color"
    w, h = img.size
    px = np.empty(w * h * 4, np.float32)
    img.pixels.foreach_get(px)
    bpy.data.images.remove(img)
    px = px.reshape(h, w, 4)[: h - h % 2, : w - w % 2]
    tiles.append(px.reshape(h // 2, 2, w // 2, 2, 4).mean(axis=(1, 3)))
th, tw = tiles[0].shape[:2]
across = 3
down = (len(tiles) + across - 1) // across
sheet = np.zeros((down * (th + 2), across * (tw + 2), 4), np.float32)
sheet[..., 3] = 1
for i, tile in enumerate(tiles):
    # Rows are bottom-up in Blender: the first still goes in the top row.
    row = down - 1 - i // across
    y, x = row * (th + 2), i % across * (tw + 2)
    sheet[y: y + th, x: x + tw] = tile
img = bpy.data.images.new("sheet", sheet.shape[1], sheet.shape[0], alpha=False)
img.colorspace_settings.name = "Non-Color"
img.pixels.foreach_set(sheet.ravel())
img.filepath_raw = os.path.join(out, "sheet.png")
img.file_format = "PNG"
img.save()
print("STILLS", len(frames), "of", strip.frame_final_duration, "frames to", out, "and sheet.png")
