"""Heroes Evolved blueprints: validate, render and convert text structure files (standard library only).

A blueprint is a readable text file: a palette mapping single characters to block states,
then horizontal layers drawn as character grids from the bottom up. See
.claude/skills/minecraft-building/references/blueprint-format.md for the format.

Usage:
  python3 tools/blueprint.py validate <file.blueprint>...
  python3 tools/blueprint.py render <file.blueprint> [--out preview.png] [--cut Y] [--scale N]
  python3 tools/blueprint.py from-nbt <structure.nbt> [--out name.blueprint]
  python3 tools/blueprint.py stats <file.blueprint>
  python3 tools/blueprint.py to-nbt <file.blueprint> --out data/<ns>/structure/<name>.nbt

render writes one image: an isometric view (cut away above layer Y when --cut is given, so
interiors are visible) beside a top-down plan of every layer. Colors come from the real block
textures in the Minecraft client jar and the mod's own textures.
"""
import argparse
import glob
import gzip
import io
import json
import math
import os
import pathlib
import re
import struct
import sys
import zipfile
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
MOD_ASSETS = ROOT / "src/main/resources/assets"
DEFAULTS = {".": "minecraft:air", "_": "keep"}


# ----------------------------------------------------------------------
# Parsing
# ----------------------------------------------------------------------

class Blueprint:
    def __init__(self, name, palette, markers, layers, anchor, path, signs=None):
        self.name, self.palette, self.markers, self.layers, self.anchor, self.path = name, palette, markers, layers, anchor, path
        self.signs = signs or {}

    @property
    def width(self): return max((len(row) for layer in self.layers for row in layer), default=0)
    @property
    def depth(self): return max((len(layer) for layer in self.layers), default=0)
    @property
    def height(self): return len(self.layers)

    def cells(self):
        for y, layer in enumerate(self.layers):
            for z, row in enumerate(layer):
                for x, ch in enumerate(row):
                    yield x, y, z, ch


def parse(path):
    """Returns (blueprint, errors). Errors are human-readable strings with line numbers."""
    text = pathlib.Path(path).read_text(encoding="utf-8").splitlines()
    palette, markers, errors, signs = dict(DEFAULTS), {}, [], {}
    layers, name, anchor = {}, pathlib.Path(path).stem, (0, 0, 0)
    section, current = None, None
    for number, raw in enumerate(text, 1):
        line = raw.split("#", 1)[0].rstrip() if not section == "layer" else raw.rstrip()
        if section == "layer" and raw.lstrip().startswith("#"):
            continue
        stripped = line.strip()
        if not stripped:
            if section == "layer":
                section = None
            continue
        header = re.match(r"^layer\s+(\d+)(?:\s*-\s*(\d+))?\s*:\s*$", stripped)
        if header:
            low, high = int(header.group(1)), int(header.group(2) or header.group(1))
            current = []
            for y in range(low, high + 1):
                if y in layers:
                    errors.append(f"line {number}: layer {y} defined twice")
                layers[y] = current
            section = "layer"
            continue
        if section == "layer":
            current.append(line)
            continue
        if stripped == "palette:":
            section = "palette"
            continue
        if stripped == "signs:":
            section = "signs"
            continue
        if section == "signs":
            m = re.match(r"^([a-z0-9_]+)((?:\s+[a-z_]+)*)\s*=\s*(.*)$", stripped)
            if not m:
                errors.append(f"line {number}: signs look like  id [color] [glow] = line one | line two")
                continue
            lines = [l.strip() for l in m.group(3).split("|")]
            if len(lines) > 4:
                errors.append(f"line {number}: a sign holds four lines")
            for l in lines:
                if len(l) > 15:
                    errors.append(f"line {number}: '{l}' is {len(l)} characters; signs fit about 15")
            opts = m.group(2).split()
            signs[m.group(1)] = (lines[:4], next((o for o in opts if o != 'glow'), 'black'), 'glow' in opts)
            continue
        key = re.match(r"^(name|anchor)\s*:\s*(.+)$", stripped)
        if key:
            section = None
            if key.group(1) == "name":
                name = key.group(2).strip()
            else:
                parts = key.group(2).split()
                if len(parts) != 3 or not all(re.fullmatch(r"-?\d+", p) for p in parts):
                    errors.append(f"line {number}: anchor needs three integers: x y z")
                else:
                    anchor = tuple(int(p) for p in parts)
            continue
        if section == "palette":
            entry = re.match(r"^(\S)\s*=\s*(\S+)(?:\s+@(\S+))?\s*$", stripped)
            if not entry:
                errors.append(f"line {number}: palette entries look like  X = minecraft:stone  or  X = mod:block[prop=value] @marker")
                continue
            ch, state, marker = entry.groups()
            if ch == "#":
                errors.append(f"line {number}: '#' starts a comment and cannot be a palette letter")
                continue
            if ch in palette and ch not in DEFAULTS:
                errors.append(f"line {number}: '{ch}' is defined twice in the palette")
            palette[ch] = state if state == "keep" or ":" in state.split("[")[0] else "minecraft:" + state
            if marker:
                markers[ch] = marker
            continue
        errors.append(f"line {number}: unexpected text: {stripped}")
    if not layers:
        errors.append("no layers defined")
        return Blueprint(name, palette, markers, [], anchor, path, signs), errors
    top = max(layers)
    missing = [y for y in range(top + 1) if y not in layers]
    if missing:
        errors.append(f"missing layers: {missing}")
    ordered = [layers.get(y, []) for y in range(top + 1)]
    bp = Blueprint(name, palette, markers, ordered, anchor, path, signs)
    for ch, marker in markers.items():
        if marker.startswith('sign:') and marker[5:] not in signs:
            errors.append(f"marker @{marker} has no entry in the signs: section")
        if marker.startswith('painting:'):
            parts = marker[9:].split(':')
            variant_file = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/data/heroesevolved/painting_variant" / (parts[0] + ".json")
            if len(parts) != 2 or parts[1] not in ("north", "south", "east", "west"):
                errors.append(f"marker @{marker}: paintings look like @painting:<variant>:<north|south|east|west>")
            elif not variant_file.exists():
                errors.append(f"marker @{marker}: no painting variant {variant_file.name}")
    width, depth = bp.width, bp.depth
    for y, layer in enumerate(ordered):
        if len(layer) != depth:
            errors.append(f"layer {y}: has {len(layer)} rows, expected {depth}")
        for z, row in enumerate(layer):
            if len(row) != width:
                errors.append(f"layer {y} row {z}: has {len(row)} columns, expected {width} (pad with '_' to keep blocks or '.' for air)")
            for x, ch in enumerate(row):
                if ch not in palette:
                    errors.append(f"layer {y} row {z} col {x}: '{ch}' is not in the palette")
    return bp, errors


# ----------------------------------------------------------------------
# Block knowledge: valid ids and colors from the real assets
# ----------------------------------------------------------------------

def client_jar():
    jars = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/neoformruntime/artifacts/minecraft_*_client.jar")))
    return zipfile.ZipFile(jars[-1]) if jars else None


def known_blocks(jar):
    names = set()
    if jar:
        for entry in jar.namelist():
            m = re.match(r"assets/minecraft/blockstates/(.+)\.json$", entry)
            if m:
                names.add("minecraft:" + m.group(1))
    for path in MOD_ASSETS.glob("*/blockstates/*.json"):
        names.add(f"{path.parent.parent.name}:{path.stem}")
    names.add("minecraft:air")
    return names


def decode_png(data):
    """RGBA rows for 8-bit (and low-bit palette/grey) PNGs: enough for block textures."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    pos, idat, palette, alpha = 8, b"", None, None
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            alpha = body
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    if interlace:
        return None
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    bits = depth * channels
    stride = (w * bits + 7) // 8
    bpp = max(1, bits // 8)
    raw, out, prev, i = zlib.decompress(idat), [], bytearray(stride), 0
    for _ in range(h):
        f, line = raw[i], bytearray(raw[i + 1:i + 1 + stride]); i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        row = []
        for x in range(w):
            if depth == 8:
                px = line[x * channels:(x + 1) * channels]
            else:
                per = 8 // depth
                byte = line[x // per]
                px = [(byte >> (8 - depth * (x % per + 1))) & ((1 << depth) - 1)]
            if ctype == 0: v = px[0] * (255 // ((1 << depth) - 1)); row.append((v, v, v, 255))
            elif ctype == 2: row.append((px[0], px[1], px[2], 255))
            elif ctype == 3:
                r, g, b = palette[px[0]]
                row.append((r, g, b, alpha[px[0]] if alpha and px[0] < len(alpha) else 255))
            elif ctype == 4: row.append((px[0], px[0], px[0], px[1]))
            else: row.append(tuple(px))
        out.append(row)
    return out


def average(pixels):
    total, n = [0, 0, 0], 0
    for row in pixels:
        for r, g, b, a in row:
            if a > 40:
                total[0] += r; total[1] += g; total[2] += b; n += 1
    return tuple(v // max(1, n) for v in total) if n else None


class Colors:
    """Top and side colors per block id, from textures; falls back to a stable hash color."""
    SUFFIXES = ["_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_trapdoor", "_door", "_pane"]

    def __init__(self, jar):
        self.jar, self.cache = jar, {}
        self.names = set(jar.namelist()) if jar else set()

    def texture(self, namespace, name):
        if namespace == "minecraft":
            entry = f"assets/minecraft/textures/block/{name}.png"
            if entry in self.names:
                return average(decode_png(self.jar.read(entry)) or [])
        path = MOD_ASSETS / namespace / "textures/block" / f"{name}.png"
        if path.is_file():
            return average(decode_png(path.read_bytes()) or [])
        return None

    def color(self, block_id, face):
        key = (block_id, face)
        if key in self.cache:
            return self.cache[key]
        namespace, name = block_id.split(":", 1)
        candidates = [f"{name}_{face}", name, f"{name}_side", f"{name}_front", f"{name}_frame"]
        base = name
        for suffix in self.SUFFIXES:
            if base.endswith(suffix):
                base = base[: -len(suffix)]
                candidates += [f"{base}_{face}", base, base + "s", base + "_planks", base.replace("brick", "bricks")]
        if base.startswith("smooth_"):
            candidates += [base[7:] + "_top", base[7:]]
        for alias in (base.replace("waxed_", ""), base.replace("_block", "")):
            candidates += [alias, f"{alias}_{face}"]
        color = None
        for candidate in candidates:
            color = self.texture(namespace, candidate)
            if color:
                break
        if color is None:
            seed = zlib.crc32(block_id.encode())
            color = (80 + seed % 150, 80 + (seed >> 8) % 150, 80 + (seed >> 16) % 150)
        self.cache[key] = color
        return color


# ----------------------------------------------------------------------
# Rendering
# ----------------------------------------------------------------------

def png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    pathlib.Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                                   + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def block_id(state):
    return state.split("[", 1)[0]


TRANSPARENT = ("glass", "pane", "bars", "chain", "lantern", "torch", "ladder", "trapdoor", "door", "lectern", "chest",
               "brewing_stand", "cauldron", "cryochamber", "flower", "carpet", "button", "lever", "pressure_plate", "rail",
               "cobweb", "vine", "candle", "sign", "head", "skull", "banner", "pot", "scaffolding")


def solid(state):
    return state not in ("keep", "minecraft:air") and not any(t in state for t in TRANSPARENT)


def render(bp, out, cut=None, scale=6):
    jar = client_jar()
    colors = Colors(jar)
    W, H, D = bp.width, bp.height, bp.depth
    top = H - 1 if cut is None else min(H - 1, cut)
    grid = {}
    for x, y, z, ch in bp.cells():
        if y <= top:
            state = bp.palette.get(ch, "keep")
            if state not in ("keep", "minecraft:air"):
                grid[(x, y, z)] = state
    s = scale
    # Isometric canvas.
    iso_w = (W + D) * s * 2 + 4
    iso_h = (W + D) * s + (top + 1) * s * 2 + 4
    # Top-down plans: one small grid per layer, wrapped into columns.
    cell = max(3, s - 1)
    plan_w, plan_h = W * cell + 6, D * cell + 14
    per_row = max(1, min(top + 1, 6))
    plans_w = per_row * plan_w
    plans_h = math.ceil((top + 1) / per_row) * plan_h
    width, height = iso_w + plans_w + 8, max(iso_h, plans_h) + 4
    canvas = [[(28, 30, 36)] * width for _ in range(height)]

    def put(px, py, color):
        if 0 <= px < width and 0 <= py < height:
            canvas[py][px] = color

    def shade(color, f):
        return tuple(max(0, min(255, int(c * f))) for c in color)

    # Viewed from the south-east, above: each cube shows its top, south (+z) and east (+x) faces.
    hw, hh, ch = 2 * s, s, 2 * s
    ox, oy = D * hw + 2, (top + 1) * ch + hh + 2
    for (x, y, z) in sorted(grid, key=lambda p: (p[0] + p[2], p[1])):
        state = grid[(x, y, z)]
        above, south, east = grid.get((x, y + 1, z)), grid.get((x, y, z + 1)), grid.get((x + 1, y, z))
        if solid(state) and above and solid(above) and south and solid(south) and east and solid(east):
            continue
        bid = block_id(state)
        cx, cy = ox + (x - z) * hw, oy + (x + z) * hh - y * ch
        top_c, side_c = colors.color(bid, "top"), colors.color(bid, "side")
        if not solid(state):  # thin blocks read as slightly translucent
            top_c, side_c = shade(top_c, 1.1), shade(side_c, 1.1)
        for dy in range(-hh, hh + 1):
            span = int(hw * (1 - abs(dy) / hh))
            for dx in range(-span, span + 1):
                put(cx + dx, cy + dy, top_c)
        for t in range(hw + 1):
            y_south = cy + int(t * hh / hw)
            y_east = cy + hh - int(t * hh / hw)
            for dy in range(ch):
                put(cx - hw + t, y_south + dy, shade(side_c, 0.8))
                put(cx + t, y_east + dy, shade(side_c, 0.62))
    # Plans.
    px0 = iso_w + 4
    for y in range(top + 1):
        col, row = y % per_row, y // per_row
        bx, by = px0 + col * plan_w, 2 + row * plan_h
        for i, ch in enumerate(str(y)):
            for dy in range(5):
                for dx in range(3):
                    if DIGITS[ch][dy] >> (2 - dx) & 1:
                        put(bx + i * 4 + dx, by + dy, (230, 230, 230))
        for z in range(D):
            for x in range(W):
                ch = bp.layers[y][z][x] if z < len(bp.layers[y]) and x < len(bp.layers[y][z]) else "_"
                state = bp.palette.get(ch, "keep")
                if state == "keep":
                    continue
                color = (52, 54, 62) if state == "minecraft:air" else colors.color(block_id(state), "top")
                for dy in range(cell - 1):
                    for dx in range(cell - 1):
                        put(bx + x * cell + dx, by + 8 + z * cell + dy, color)
    png(out, [[canvas[y][x] for x in range(width)] for y in range(height)])


DIGITS = {"0": [7, 5, 5, 5, 7], "1": [2, 6, 2, 2, 7], "2": [7, 1, 7, 4, 7], "3": [7, 1, 7, 1, 7], "4": [5, 5, 7, 1, 1],
          "5": [7, 4, 7, 1, 7], "6": [7, 4, 7, 5, 7], "7": [7, 1, 1, 1, 1], "8": [7, 5, 7, 5, 7], "9": [7, 5, 7, 1, 7]}


# ----------------------------------------------------------------------
# Structure-block NBT import
# ----------------------------------------------------------------------

def read_nbt(stream):
    def payload(tag):
        if tag == 1: return struct.unpack(">b", stream.read(1))[0]
        if tag == 2: return struct.unpack(">h", stream.read(2))[0]
        if tag == 3: return struct.unpack(">i", stream.read(4))[0]
        if tag == 4: return struct.unpack(">q", stream.read(8))[0]
        if tag == 5: return struct.unpack(">f", stream.read(4))[0]
        if tag == 6: return struct.unpack(">d", stream.read(8))[0]
        if tag == 7: n = struct.unpack(">i", stream.read(4))[0]; return stream.read(n)
        if tag == 8: n = struct.unpack(">H", stream.read(2))[0]; return stream.read(n).decode("utf-8")
        if tag == 9:
            inner, n = stream.read(1)[0], struct.unpack(">i", stream.read(4))[0]
            return [payload(inner) for _ in range(n)]
        if tag == 10:
            out = {}
            while True:
                t = stream.read(1)[0]
                if t == 0: return out
                key = payload(8)
                out[key] = payload(t)
        if tag == 11: n = struct.unpack(">i", stream.read(4))[0]; return list(struct.unpack(f">{n}i", stream.read(4 * n)))
        if tag == 12: n = struct.unpack(">i", stream.read(4))[0]; return list(struct.unpack(f">{n}q", stream.read(8 * n)))
        raise ValueError(f"unknown NBT tag {tag}")
    tag = stream.read(1)[0]
    payload(8)
    return payload(tag)


# "#" starts a comment and "." / "_" are air and keep, so none of them are handed out.
CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!$%&*+=?@^~<>/\\|;,'\"`{}()-"


def from_nbt(path, out):
    data = pathlib.Path(path).read_bytes()
    root = read_nbt(io.BytesIO(gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data))
    sx, sy, sz = root["size"]
    states = []
    for entry in root["palette"]:
        props = entry.get("Properties", {})
        state = entry["Name"] + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")
        states.append(state)
    grid = [[["_"] * sx for _ in range(sz)] for _ in range(sy)]
    chars, used = {}, iter(CHARS)
    for block in root["blocks"]:
        x, y, z = block["pos"]
        state = states[block["state"]]
        if state == "minecraft:structure_void":
            continue
        if state == "minecraft:air":
            ch = "."
        else:
            if state not in chars:
                chars[state] = next(used)
            ch = chars[state]
        grid[y][z][x] = ch
    lines = [f"# Imported from {pathlib.Path(path).name}. Rename the palette letters to something memorable.",
             f"name: {pathlib.Path(out).stem}", "anchor: 0 0 0", "palette:"]
    lines += [f"  {ch} = {state}" for state, ch in chars.items()]
    for y in range(sy):
        lines += ["", f"layer {y}:"] + ["".join(row) for row in grid[y]]
    pathlib.Path(out).write_text("\n".join(lines) + "\n", encoding="utf-8")
    if root.get("entities"):
        print(f"note: {len(root['entities'])} entities in the structure were not imported")
    print(f"PASS: wrote {out} ({sx}x{sy}x{sz}, {len(chars)} block states)")


# ----------------------------------------------------------------------
# Structure export: blueprints become vanilla structure templates for world generation
# ----------------------------------------------------------------------

def write_nbt(value, name=""):
    """Encodes a Python value as NBT. Ints are TAG_Int unless wrapped: ("byte", 1), ("list", tag, items)."""
    out = io.BytesIO()

    def tag_of(v):
        if isinstance(v, tuple) and v[0] == "byte": return 1
        if isinstance(v, bool): return 1
        if isinstance(v, int): return 3
        if isinstance(v, float): return 6
        if isinstance(v, str): return 8
        if isinstance(v, tuple) and v[0] == "list": return 9
        if isinstance(v, list): return 9
        if isinstance(v, dict): return 10
        raise TypeError(v)

    def payload(v):
        t = tag_of(v)
        if t == 1: out.write(struct.pack(">b", int(v[1] if isinstance(v, tuple) else v)))
        elif t == 3: out.write(struct.pack(">i", v))
        elif t == 6: out.write(struct.pack(">d", v))
        elif t == 8: b = v.encode("utf-8"); out.write(struct.pack(">H", len(b))); out.write(b)
        elif t == 9:
            items = v[2] if isinstance(v, tuple) else v
            inner = v[1] if isinstance(v, tuple) else (tag_of(items[0]) if items else 10)
            out.write(bytes([inner])); out.write(struct.pack(">i", len(items)))
            for item in items: payload(item)
        elif t == 10:
            for k, item in v.items():
                out.write(bytes([tag_of(item)])); payload(k); payload(item)
            out.write(b"\x00")

    out.write(bytes([10])); payload(name); payload(value)
    return out.getvalue()


def state_entry(state):
    m = re.match(r"^([^\[]+)(?:\[(.*)\])?$", state)
    entry = {"Name": m.group(1)}
    if m.group(2):
        entry["Properties"] = dict(kv.split("=", 1) for kv in m.group(2).split(","))
    return entry


FACING_2D = {"south": 0, "west": 1, "north": 2, "east": 3}


def paintings(bp):
    """Painting entities for @painting:<variant>:<facing> cells. The entity sits at the anchor block's centre;
    the game recomputes the painting's real centre from it, also after the structure is rotated."""
    found = []
    for x, y, z, ch in bp.cells():
        marker = bp.markers.get(ch) or ""
        if not marker.startswith("painting:"):
            continue
        variant, facing = marker[9:].split(":")
        found.append({"blockPos": ("list", 3, [x, y, z]), "pos": ("list", 6, [x + 0.5, y + 0.5, z + 0.5]),
                      "nbt": {"id": "minecraft:painting", "variant": "heroesevolved:" + variant,
                              "facing": ("byte", FACING_2D[facing]), "Invulnerable": ("byte", 0)}})
    return found


def to_nbt(bp, out):
    """Exports a blueprint as a structure template. '_' cells become structure void (keep terrain)."""
    palette, index, blocks = [], {}, []
    for x, y, z, ch in bp.cells():
        state = bp.palette.get(ch, "keep")
        if state == "keep":
            continue
        if state not in index:
            index[state] = len(palette)
            palette.append(state_entry(state))
        block = {"pos": ("list", 3, [x, y, z]), "state": index[state]}
        marker = bp.markers.get(ch)
        if marker and marker.startswith("loot:") and "chest" in state or marker and marker.startswith("loot:") and "barrel" in state:
            block["nbt"] = {"id": block_id(state), "LootTable": "heroesevolved:chests/" + marker[5:]}
        elif marker and marker.startswith("sign:"):
            lines, color, glow = bp.signs.get(marker[5:], ([], "black", False))
            messages = [json.dumps({"text": line}) for line in (lines + ["", "", "", ""])[:4]]
            block["nbt"] = {"id": "minecraft:sign", "is_waxed": ("byte", 1),
                            "front_text": {"messages": ("list", 8, messages), "color": color, "has_glowing_text": ("byte", 1 if glow else 0)},
                            "back_text": {"messages": ("list", 8, ['""'] * 4), "color": "black", "has_glowing_text": ("byte", 0)}}
        blocks.append(block)
    root = {"size": ("list", 3, [bp.width, bp.height, bp.depth]), "palette": ("list", 10, palette),
            "blocks": ("list", 10, blocks), "entities": ("list", 10, paintings(bp)), "DataVersion": 3955}
    pathlib.Path(out).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(out).write_bytes(gzip.compress(write_nbt(root), mtime=0))
    print(f"PASS: exported {out} ({bp.width}x{bp.height}x{bp.depth}, {len(blocks)} blocks, {len(palette)} states)")


def validate(paths):
    jar = client_jar()
    blocks = known_blocks(jar)
    ok = True
    for path in paths:
        bp, errors = parse(path)
        for ch, state in bp.palette.items():
            if state != "keep" and block_id(state) not in blocks:
                errors.append(f"palette '{ch}': unknown block {block_id(state)}")
            props = re.search(r"\[(.*)\]$", state)
            if props and not re.fullmatch(r"[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*", props.group(1)):
                errors.append(f"palette '{ch}': malformed properties [{props.group(1)}]")
        used = {ch for _, _, _, ch in bp.cells()}
        unused = [ch for ch in bp.palette if ch not in used and ch not in DEFAULTS]
        if errors:
            ok = False
            print(f"FAIL: {path}")
            for e in errors:
                print("  " + e)
        else:
            print(f"PASS: {path} ({bp.width}x{bp.height}x{bp.depth}, {len(bp.palette) - 2} palette entries"
                  + (f", unused: {''.join(unused)}" if unused else "") + ")")
    return ok


def stats(path):
    bp, errors = parse(path)
    counts = {}
    for _, _, _, ch in bp.cells():
        counts[bp.palette.get(ch, "?")] = counts.get(bp.palette.get(ch, "?"), 0) + 1
    print(f"{bp.name}: {bp.width} wide (x) by {bp.height} tall (y) by {bp.depth} deep (z), anchor {bp.anchor}")
    for state, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        print(f"  {n:5d}  {state}")
    print("markers:", ", ".join(f"{ch}=@{m}" for ch, m in bp.markers.items()) or "none")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    v = sub.add_parser("validate"); v.add_argument("files", nargs="+")
    r = sub.add_parser("render"); r.add_argument("file"); r.add_argument("--out"); r.add_argument("--cut", type=int); r.add_argument("--scale", type=int, default=6)
    n = sub.add_parser("from-nbt"); n.add_argument("file"); n.add_argument("--out")
    s = sub.add_parser("stats"); s.add_argument("file")
    t = sub.add_parser("to-nbt"); t.add_argument("file"); t.add_argument("--out", required=True)
    args = parser.parse_args()
    if args.command == "validate":
        sys.exit(0 if validate(args.files) else 1)
    if args.command == "render":
        bp, errors = parse(args.file)
        if errors:
            print("\n".join(errors)); sys.exit(1)
        out = args.out or str(pathlib.Path(args.file).with_suffix(".png"))
        render(bp, out, args.cut, args.scale)
        print(f"PASS: rendered {out}")
    if args.command == "from-nbt":
        from_nbt(args.file, args.out or str(pathlib.Path(args.file).with_suffix(".blueprint")))
    if args.command == "stats":
        stats(args.file)
    if args.command == "to-nbt":
        bp, errors = parse(args.file)
        if errors:
            print("\n".join(errors)); sys.exit(1)
        to_nbt(bp, args.out)


if __name__ == "__main__":
    main()
