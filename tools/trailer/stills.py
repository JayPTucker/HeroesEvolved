"""Saves frames of a rendered trailer as half-size PNGs, to look at the cut itself (titles, fades, the grade).

    "C:/Program Files/Blender Foundation/Blender 5.0/blender.exe" -b --factory-startup -P tools/trailer/stills.py -- build/trailer/test-shot.mp4 build/trailer/stills 1 40 120

Each number is a frame (from 1); the stills are <out>/<frame>.png.
"""
import os
import sys

import bpy

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
for f in frames:
    scene.frame_set(f)
    r.filepath = os.path.join(out, "%05d.png" % f)
    bpy.ops.render.render(write_still=True)
print("STILLS", len(frames), "of", strip.frame_final_duration, "frames to", out)
