"""Generate the original 16x16 Formula laboratory textures (standard library only).

Run: python3 tools/generate-formula-textures.py
Deterministic output: items, the Centrifuge and Synthesis Station faces, and the Drained effect icon.
"""
import pathlib
import struct
import zlib

root = pathlib.Path(__file__).resolve().parents[1]
textures = root / "src/main/resources/assets/heroesevolved/textures"

# Helixite's palette, shared with tools/generate-helixite-textures.py.
CRYSTAL = [(34, 92, 104), (52, 156, 160), (96, 222, 208), (184, 255, 240)]


def png(path, pixels):
    size = len(pixels)
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

CLEAR = (0, 0, 0, 0)
GLASS, GLASS_EDGE, STEEL, STEEL_DARK = (200, 230, 240, 150), (150, 190, 205, 255), (168, 176, 184, 255), (96, 104, 112, 255)
BLOOD, BLOOD_DARK = (180, 18, 32, 255), (110, 8, 20, 255)
SALINE, COMPOUND, FORMULA, FORMULA_GLOW = (170, 220, 235, 230), (110, 210, 190, 240), (255, 196, 64, 255), (255, 240, 170, 255)


def blank():
    return [[list(CLEAR) for _ in range(16)] for _ in range(16)]


def put(pixels, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        pixels[y][x] = list(color if len(color) == 4 else (*color, 255))


def syringe(fill=None):
    pixels = blank()
    # A diagonal barrel from lower left to upper right, plunger at the top, needle at the bottom.
    for i in range(8):
        x, y = 4 + i, 11 - i
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            edge = (dx, dy) in ((0, 0), (1, 1))
            put(pixels, x + dx, y + dy, GLASS_EDGE if edge else (fill if fill and i < 6 else GLASS))
    for i in range(3):
        put(pixels, 3 - i, 13 + i - 1, STEEL)
    put(pixels, 12, 3, STEEL_DARK); put(pixels, 13, 2, STEEL_DARK); put(pixels, 14, 1, STEEL); put(pixels, 12, 1, STEEL); put(pixels, 14, 3, STEEL)
    return pixels


def vial(liquid, glow=None):
    pixels = blank()
    for y in range(3, 15):
        for x in range(5, 11):
            edge = x in (5, 10) or y == 14
            if y < 5:
                put(pixels, x, y, (140, 100, 70, 255) if 6 <= x <= 9 else CLEAR)
            elif edge:
                put(pixels, x, y, GLASS_EDGE)
            elif y >= 7:
                put(pixels, x, y, glow if glow and (x + y) % 5 == 0 else liquid)
            else:
                put(pixels, x, y, GLASS)
    return pixels


def catalyst():
    pixels = vial(BLOOD)
    for x, y in ((7, 9), (8, 11), (7, 12), (8, 8)):
        put(pixels, x, y, CRYSTAL[2])
    return pixels


def machine_face(base, accent, front=None, lit=False):
    pixels = blank()
    for y in range(16):
        for x in range(16):
            border = x in (0, 15) or y in (0, 15)
            rivet = (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14))
            shade = base[0] - 18 if border else base[0] + ((x * 7 + y * 3) % 5) - 2
            put(pixels, x, y, STEEL_DARK if rivet else (shade, base[1] - (18 if border else 0), base[2] - (18 if border else 0), 255))
    if front == "centrifuge":
        # A round porthole with a blood-red rotor, glowing teal while spinning.
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if d < 5.5:
                    put(pixels, x, y, CRYSTAL[3] if lit and d < 2 else (CRYSTAL[1] if lit else (40, 46, 52)))
                elif d < 6.5:
                    put(pixels, x, y, STEEL)
        for x, y in ((7, 4), (8, 4), (11, 7), (11, 8), (7, 11), (8, 11), (4, 7), (4, 8)):
            put(pixels, x, y, BLOOD if not lit else FORMULA_GLOW)
    elif front == "station":
        # A glass cabinet with three flasks and a burner strip.
        for y in range(3, 12):
            for x in range(2, 14):
                put(pixels, x, y, (60, 70, 76, 255) if x in (2, 13) or y in (3, 11) else (30, 40, 46, 255))
        for i, liquid in enumerate((SALINE, COMPOUND, FORMULA)):
            for y in range(6, 11):
                put(pixels, 4 + i * 3, y, liquid); put(pixels, 5 + i * 3, y, liquid)
        for x in range(3, 13):
            put(pixels, x, 13, FORMULA_GLOW if lit else (90, 60, 30, 255))
    elif front == "top":
        for y in range(4, 12):
            for x in range(4, 12):
                put(pixels, x, y, accent if (x + y) % 3 else CRYSTAL[2])
    return pixels


def drained():
    pixels = [[list(CLEAR) for _ in range(18)] for _ in range(18)]
    for y in range(18):
        for x in range(18):
            d = ((x - 8.5) ** 2 / 1.2 + (y - 10) ** 2) ** 0.5
            if d < 5 or (y < 10 and abs(x - 8.5) < (10 - y) * 0.45 and y > 3):
                pixels[y][x] = list(BLOOD_DARK if x < 8 else BLOOD)
    for x, y in ((10, 9), (11, 10)):
        pixels[y][x] = [255, 140, 150, 255]
    for y in range(6, 15):
        pixels[y][3] = [30, 30, 30, 200]
    return pixels


items = textures / "item"
png(items / "empty_syringe.png", syringe())
png(items / "regenerative_blood_sample.png", syringe(BLOOD))
png(items / "isolated_catalyst.png", catalyst())
png(items / "buffered_saline.png", vial(SALINE))
png(items / "unstable_compound.png", vial(COMPOUND, CRYSTAL[3]))
png(items / "formula_vial.png", vial(FORMULA, FORMULA_GLOW))

blocks = textures / "block"
steel, copper = (150, 158, 166), (184, 110, 78)
png(blocks / "centrifuge_side.png", machine_face(steel, steel))
png(blocks / "centrifuge_top.png", machine_face(steel, (120, 128, 136, 255), "top"))
png(blocks / "centrifuge_front.png", machine_face(steel, steel, "centrifuge"))
png(blocks / "centrifuge_front_on.png", machine_face(steel, steel, "centrifuge", True))
png(blocks / "synthesis_station_side.png", machine_face(copper, copper))
png(blocks / "synthesis_station_top.png", machine_face(copper, (150, 86, 60, 255), "top"))
png(blocks / "synthesis_station_front.png", machine_face(copper, copper, "station"))
png(blocks / "synthesis_station_front_on.png", machine_face(copper, copper, "station", True))

png(textures / "mob_effect/drained.png", drained())
print("PASS: wrote 6 item, 8 block and 1 effect textures")


# ----------------------------------------------------------------------
# Cryochamber: steel frame, frosted glass, a glowing base plate and an item icon.
# ----------------------------------------------------------------------

def cryo_frame():
    pixels = blank()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            seam = y in (5, 10)
            shade = 70 if edge else 96 + ((x * 5 + y * 3) % 4) * 3 - (12 if seam else 0)
            put(pixels, x, y, (shade, shade + 6, shade + 14, 255))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(pixels, x, y, (150, 160, 172, 255))
    for y in range(3, 13):
        put(pixels, 7, y, (60, 150, 170, 255)); put(pixels, 8, y, (90, 200, 215, 255))  # coolant line
    return pixels


def cryo_glass():
    pixels = blank()
    for y in range(16):
        for x in range(16):
            frost = (x * 7 + y * 11) % 13 == 0 or (x + y) % 9 == 0
            edge = x in (0, 15) or y in (0, 15)
            put(pixels, x, y, (180, 225, 240, 200) if edge else (215, 245, 255, 150) if frost else (150, 210, 235, 90))
    return pixels


def cryo_base():
    pixels = cryo_frame()
    for y in range(4, 12):
        for x in range(4, 12):
            put(pixels, x, y, CRYSTAL[3] if (x + y) % 4 == 0 else CRYSTAL[2])
    return pixels


def cryo_item():
    pixels = blank()
    for y in range(1, 15):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (1, 14)
            put(pixels, x, y, (90, 98, 110, 255) if edge else (150, 210, 235, 255) if y < 12 else CRYSTAL[2])
    for y in range(4, 11):
        put(pixels, 7, y, (60, 70, 90, 255)); put(pixels, 8, y, (60, 70, 90, 255))  # the sleeper's silhouette
    put(pixels, 7, 3, (60, 70, 90, 255)); put(pixels, 8, 3, (60, 70, 90, 255))
    return pixels


png(blocks / "cryochamber_frame.png", cryo_frame())
png(blocks / "cryochamber_glass.png", cryo_glass())
png(blocks / "cryochamber_base.png", cryo_base())
png(items / "cryochamber.png", cryo_item())
print("PASS: wrote cryochamber frame, glass, base and item textures")
