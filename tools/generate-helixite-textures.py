"""Generate the original 16x16 Helixite ore and shard textures (standard library only).

Run: python tools/generate-helixite-textures.py
Output is deterministic, so regenerating does not change committed files.
"""
import pathlib
import random
import struct
import zlib

root = pathlib.Path(__file__).resolve().parents[1]
textures = root / "src/main/resources/assets/heroesevolved/textures"

# Helixite: a teal crystal with violet depth, matching the Formula's glow.
CRYSTAL = [(34, 92, 104), (52, 156, 160), (96, 222, 208), (184, 255, 240)]
VIOLET = (120, 84, 200)


def png(path, pixels):
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def host(seed, base, spread, streaks):
    rng = random.Random(seed)
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            shade = base + rng.randint(-spread, spread)
            if streaks and (y % 5 == 0 or (y + x // 6) % 7 == 0):
                shade -= 9
            row.append([shade, shade, shade + 2, 255])
        rows.append(row)
    return rows


def crystals(pixels, seed):
    # Three small clusters, each a diagonal prism with a bright tip.
    rng = random.Random(seed)
    for cx, cy in ((4, 4), (11, 7), (6, 12)):
        for step in range(4):
            x, y = cx + step - 1, cy - step + 1
            for dx, dy, tone in ((0, 0, 2), (1, 0, 1), (0, 1, 0), (-1, 0, 1)):
                px, py = x + dx, y + dy
                if 0 <= px < 16 and 0 <= py < 16:
                    color = CRYSTAL[min(3, tone + (step == 3))]
                    if rng.random() < 0.18:
                        color = VIOLET
                    pixels[py][px] = [*color, 255]
        pixels[cy - 2][cx + 2] = [*CRYSTAL[3], 255]


def shard():
    pixels = [[[0, 0, 0, 0] for _ in range(16)] for _ in range(16)]
    # A long faceted shard from the lower left to the upper right, with a violet core.
    for i in range(11):
        x, y = 3 + i, 13 - i
        width = 1 if i in (0, 10) else 2 if i in (1, 9) else 3
        for w in range(-width // 2, width - width // 2):
            px, py = x + w, y + w
            if 0 <= px < 16 and 0 <= py < 16:
                edge = w == -width // 2
                core = w == 0 and 3 <= i <= 7
                color = CRYSTAL[3] if edge else VIOLET if core else CRYSTAL[2 if i > 5 else 1]
                pixels[py][px] = [*color, 255]
    for x, y in ((3, 13), (2, 14)):
        pixels[y][x] = [*CRYSTAL[0], 255]
    pixels[3][13] = [255, 255, 255, 255]
    return pixels


stone = host(11, 124, 10, False)
crystals(stone, 5)
png(textures / "block/helixite_ore.png", stone)

deepslate = host(29, 74, 8, True)
crystals(deepslate, 17)
png(textures / "block/deepslate_helixite_ore.png", deepslate)

def meteorite():
    # Helixite fallen from the sky: a fused black crust, glassy where it melted, split by glowing teal veins with a
    # little violet deep in them. Its veins wander across the tile edges so neighbouring blocks read as one lump.
    rng = random.Random(41)
    pixels = []
    for y in range(16):
        row = []
        for x in range(16):
            shade = 34 + rng.randint(-8, 8)
            if (x * 3 + y * 5) % 11 == 0:
                shade += 10  # glassy glints in the crust
            row.append([shade, shade - 2, shade + 2, 255])
        pixels.append(row)
    veins = [[(0, 5), (3, 6), (6, 8), (9, 7), (12, 9), (15, 10)],
             [(4, 0), (5, 3), (7, 5), (6, 8)],
             [(9, 15), (10, 12), (12, 9)],
             [(1, 15), (2, 12), (4, 11), (6, 8)]]
    for vein in veins:
        for (x0, y0), (x1, y1) in zip(vein, vein[1:]):
            steps = max(abs(x1 - x0), abs(y1 - y0))
            for i in range(steps + 1):
                x = x0 + round((x1 - x0) * i / max(1, steps))
                y = y0 + round((y1 - y0) * i / max(1, steps))
                pixels[y][x] = [*CRYSTAL[2], 255]
                for dx, dy in ((1, 0), (0, 1)):
                    px, py = x + dx, y + dy
                    if px < 16 and py < 16 and pixels[py][px][0] < 80:
                        pixels[py][px] = [*CRYSTAL[1], 255]
    for x, y in ((6, 8), (12, 9), (4, 11)):
        pixels[y][x] = [*CRYSTAL[3], 255]
    for x, y in ((7, 5), (10, 12)):
        pixels[y][x] = [*VIOLET, 255]
    return pixels


png(textures / "item/helixite_shard.png", shard())
png(textures / "block/helixite_meteorite.png", meteorite())
print("PASS: wrote helixite_ore, deepslate_helixite_ore, helixite_meteorite and helixite_shard textures")
