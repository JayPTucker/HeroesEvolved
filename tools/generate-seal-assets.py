"""The Living Seal's artwork that ships as files: the Riftbrand effect icon (a violet seal-sigil, three orbit rings
round a white fracture). Everything else the fight shows is drawn in code (SealArt, SealBossBar). No external artwork
and no image library: the PNG is written with zlib alone, so it runs on any Python 3.

Run with: py -3 tools/generate-seal-assets.py
"""
from pathlib import Path
import math, struct, zlib

ROOT = Path(__file__).resolve().parents[1]
assets = ROOT / 'src/main/resources/assets/heroesevolved'


def png(path, w, h, pixels):
    """pixels: list of rows of (r, g, b, a)."""
    raw = b''.join(b'\x00' + bytes(c for px in row for c in px) for row in pixels)
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


SEAL = (167, 138, 242)
FRACTURE = (240, 242, 220)
VOID = (27, 16, 48)

def riftbrand():
    n = 18
    img = [[(0, 0, 0, 0) for _ in range(n)] for _ in range(n)]
    def put(x, y, col, a):
        if 0 <= x < n and 0 <= y < n:
            r, g, b, old = img[y][x]
            if a > old:
                img[y][x] = (*col, a)
    c = (n - 1) / 2
    # A dark disc behind it, so it reads on any background.
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c)
            if d < 8.2:
                put(x, y, VOID, int(200 * min(1, (8.2 - d) / 1.5)))
    # Three orbit rings, tilted.
    for ring, (rx, ry, tilt) in enumerate([(7.2, 3.2, .5), (6.4, 3.6, -.6), (5.0, 5.0, 0)]):
        for k in range(200):
            a = k / 200 * math.tau
            px, py = math.cos(a) * rx, math.sin(a) * ry
            qx = px * math.cos(tilt) - py * math.sin(tilt)
            qy = px * math.sin(tilt) + py * math.cos(tilt)
            put(round(c + qx), round(c + qy), SEAL, 255 if ring < 2 else 190)
    # A white fracture through the middle.
    for (x0, y0, x1, y1) in [(5, 4, 9, 9), (9, 9, 8, 13), (9, 9, 13, 11)]:
        steps = 20
        for i in range(steps + 1):
            t = i / steps
            put(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), FRACTURE, 255)
    put(9, 9, FRACTURE, 255); put(8, 9, FRACTURE, 255); put(9, 8, FRACTURE, 255)
    png(assets / 'textures/mob_effect/riftbrand.png', n, n, img)


riftbrand()
print('wrote textures/mob_effect/riftbrand.png')
