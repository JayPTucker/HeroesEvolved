"""Generate the original eclipse emblem: the inventory Skills button sprites and the creative tab icon.

Run: python3 tools/generate-eclipse-icons.py
The 20x18 button reproduces the vanilla recipe-book button's frame (outline, bevel and the blue
highlighted state) so the two buttons sit side by side as a matching pair.
"""
import math
import pathlib
import struct
import zlib

root = pathlib.Path(__file__).resolve().parents[1]
textures = root / "src/main/resources/assets/heroesevolved/textures"
CLEAR = (0, 0, 0, 0)


def png(path, pixels):
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def eclipse(pixels, cx, cy, scale):
    """A black moon over the sun: a dark disc, a white-hot rim, a golden corona and short flares."""
    moon, rim, corona = 3.6 * scale, 4.4 * scale, 6.4 * scale
    for y in range(len(pixels)):
        for x in range(len(pixels[0])):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            angle = math.atan2(dy, dx)
            flare = d < corona + 1.6 * scale and abs(math.sin(angle * 4)) > 0.92
            if d < moon:
                pixels[y][x] = (14, 10, 22, 255) if d < moon - 1 else (40, 30, 52, 255)
            elif d < rim:
                pixels[y][x] = (255, 250, 220, 255)
            elif d < corona:
                t = (d - rim) / (corona - rim)
                pixels[y][x] = (255, int(214 - 90 * t), int(90 - 70 * t), 255)
            elif flare:
                pixels[y][x] = (232, 122, 26, 255)


def button(highlighted):
    outline, fill, shade = ((0, 7, 62), (136, 146, 201), (52, 62, 117)) if highlighted else ((0, 0, 0), (198, 198, 198), (85, 85, 85))
    w, h = 20, 18
    pixels = [[CLEAR for _ in range(w)] for _ in range(h)]
    for y in range(h):
        for x in range(w):
            corner = (x < 2 and y < 2 and x + y < 2) or (x > w - 3 and y < 2 and (w - 1 - x) + y < 2) \
                or (x < 2 and y > h - 3 and x + (h - 1 - y) < 2) or (x > w - 3 and y > h - 3 and (w - 1 - x) + (h - 1 - y) < 2)
            if corner:
                continue
            if x == 0 or y == 0 or x == w - 1 or y == h - 1 or (x == 1 and y in (1, h - 2)) or (x == w - 2 and y in (1, h - 2)):
                pixels[y][x] = (*outline, 255)
            elif y == 1 or x == 1:
                pixels[y][x] = (255, 255, 255, 255)
            elif y == h - 2 or x == w - 2:
                pixels[y][x] = (*shade, 255)
            else:
                pixels[y][x] = (*fill, 255)
    eclipse(pixels, 10, 9, 0.95)
    return pixels


def item():
    pixels = [[CLEAR for _ in range(16)] for _ in range(16)]
    eclipse(pixels, 8, 8, 0.98)
    return pixels


png(textures / "gui/sprites/skills_button.png", button(False))
png(textures / "gui/sprites/skills_button_highlighted.png", button(True))
png(textures / "item/eclipse_emblem.png", item())
print("PASS: wrote skills_button, skills_button_highlighted and eclipse_emblem")
