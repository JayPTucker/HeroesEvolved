"""Generate the hexagon frames used by the Abilities screen (standard library only).

Run: python3 tools/generate-hex-icons.py
Pointy-top hexagons, 26x30: open (white rim), learned (copper fill), locked (dim), power (gold), selected (bright ring).
"""
import math
import pathlib
import struct
import zlib

OUT = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/heroesevolved/textures/gui/sprites/hex"
W, H = 26, 30


def png(path, pixels):
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def inside(x, y, shrink=0.0):
    """Pointy-top hexagon test centred in the canvas."""
    cx, cy = (W - 1) / 2, (H - 1) / 2
    r = H / 2 - 0.5 - shrink
    dx, dy = abs(x - cx), abs(y - cy)
    half_w = r * math.sqrt(3) / 2
    return dx <= half_w and dy <= r - dx / math.sqrt(3)


def hexagon(rim, fill, inner_rim=None, glow=None):
    pixels = [[(0, 0, 0, 0)] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if not inside(x, y):
                if glow and inside(x, y, -1.2):
                    pixels[y][x] = glow
                continue
            if not inside(x, y, 1.6):
                pixels[y][x] = rim
            elif inner_rim and not inside(x, y, 2.6):
                pixels[y][x] = inner_rim
            else:
                # gentle vertical gradient for depth
                t = y / H
                pixels[y][x] = tuple(int(c * (1.05 - 0.25 * t)) for c in fill[:3]) + (fill[3],)
    return pixels


png(OUT / "open.png", hexagon((222, 218, 210, 255), (52, 46, 44, 240), (24, 20, 20, 255)))
png(OUT / "locked.png", hexagon((96, 92, 90, 255), (34, 31, 30, 235), (18, 16, 16, 255)))
png(OUT / "learned.png", hexagon((236, 226, 214, 255), (150, 82, 46, 245), (70, 34, 18, 255)))
png(OUT / "power.png", hexagon((246, 214, 120, 255), (120, 88, 30, 245), (60, 40, 10, 255)))
png(OUT / "selected.png", hexagon((255, 236, 150, 255), (0, 0, 0, 0), None, (255, 214, 90, 150)))
print("PASS: wrote hex frames: open, locked, learned, power, selected")
