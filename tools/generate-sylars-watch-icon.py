"""Draws the 16x16 item icon for Sylar's Watch: a brass pocket watch, hands stopped at ten past ten, a crack
across the glass, and a short chain. Writes src/main/resources/assets/heroesevolved/textures/item/sylars_watch.png."""
import math
import sys
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/heroesevolved/textures/item/sylars_watch.png"
DARK, BRASS, LIGHT, SHINE = (74, 52, 22, 255), (176, 132, 52, 255), (224, 186, 98, 255), (252, 236, 176, 255)
FACE, FACE_SHADE, INK, CRACK = (238, 232, 214, 255), (204, 196, 176, 255), (36, 30, 34, 255), (120, 128, 140, 255)
CHAIN = (150, 120, 70, 255)

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
cx, cy = 7.5, 8.5
for y in range(16):
    for x in range(16):
        d = math.hypot(x - cx, y - cy)
        if d <= 6.4:
            # case: dark rim, brass ring lit from the top left
            if d > 5.5:
                px[x, y] = DARK
            elif d > 4.5:
                px[x, y] = LIGHT if (x - cx) + (y - cy) < -2 else BRASS
            else:
                px[x, y] = FACE_SHADE if (x - cx) + (y - cy) > 3 else FACE
# crown and bow on top
for x, y, c in ((7, 1, DARK), (8, 1, DARK), (7, 2, LIGHT), (8, 2, BRASS), (6, 0, DARK), (9, 0, DARK), (7, 0, BRASS), (8, 0, BRASS)):
    px[x, y] = c
# hour marks at 12, 3, 6, 9
for x, y in ((7, 5), (8, 5), (11, 8), (11, 9), (7, 12), (8, 12), (4, 8), (4, 9)):
    px[x, y] = INK
# hands at ten past ten
for x, y in ((7, 8), (6, 7), (5, 6), (8, 8), (9, 7), (10, 6)):
    px[x, y] = INK
# the crack across the glass
for x, y in ((9, 10), (10, 11), (10, 12), (11, 10), (6, 11), (5, 12)):
    px[x, y] = CRACK
px[5, 6] = INK
px[3, 6] = SHINE
px[4, 5] = SHINE
# a short chain off the bow
for x, y in ((10, 1), (11, 2), (12, 2), (13, 3), (14, 4), (14, 5)):
    px[x, y] = CHAIN

OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print(f"wrote {OUT}")
if "--preview" in sys.argv:
    img.resize((256, 256), Image.NEAREST).save(OUT.with_name("sylars_watch_preview.png"))
