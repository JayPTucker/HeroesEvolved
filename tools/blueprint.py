"""Heroes Evolved blueprints: validate, render and convert text structure files (standard library only).

A blueprint is a readable text file: a palette mapping single characters to block states,
then horizontal layers drawn as character grids from the bottom up. See
.claude/skills/minecraft-building/references/blueprint-format.md for the format.

Usage:
  python3 tools/blueprint.py validate <file.blueprint>...
  python3 tools/blueprint.py render <file.blueprint> [--out preview.png] [--cut Y] [--scale N] [--view se|sw|ne|nw]
  python3 tools/blueprint.py from-nbt <structure.nbt> [--out name.blueprint]
  python3 tools/blueprint.py stats <file.blueprint>
  python3 tools/blueprint.py to-nbt <file.blueprint> --out data/<ns>/structure/<name>.nbt
  python3 tools/blueprint.py set <file.blueprint> LAYER ROW COL VALUE [LAYER ROW COL VALUE ...]
  python3 tools/blueprint.py facts <block state>...

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


STAIR_QUARTERS = {  # raised quarters of each stair state, as the game reports them (BlockStateFactsGameTests)
    ("north", "straight"): "NW NE", ("north", "inner_left"): "NW NE SW", ("north", "inner_right"): "NW NE SE",
    ("north", "outer_left"): "NW", ("north", "outer_right"): "NE",
    ("south", "straight"): "SW SE", ("south", "inner_left"): "NE SW SE", ("south", "inner_right"): "NW SW SE",
    ("south", "outer_left"): "SE", ("south", "outer_right"): "SW",
    ("west", "straight"): "NW SW", ("west", "inner_left"): "NW SW SE", ("west", "inner_right"): "NW NE SW",
    ("west", "outer_left"): "SW", ("west", "outer_right"): "NW",
    ("east", "straight"): "NE SE", ("east", "inner_left"): "NW NE SE", ("east", "inner_right"): "NE SW SE",
    ("east", "outer_left"): "NE", ("east", "outer_right"): "SE",
}
T = 3 / 16   # a door's or trapdoor's thickness


def edge_box(side, thick, y0=0.0, y1=1.0):
    """A plate `thick` deep against one edge of the cell."""
    return {"north": (0, y0, 0, 1, y1, thick), "south": (0, y0, 1 - thick, 1, y1, 1),
            "west": (0, y0, 0, thick, y1, 1), "east": (1 - thick, y0, 0, 1, y1, 1),
            "up": (0, 1 - thick, 0, 1, 1, 1), "down": (0, 0, 0, 1, thick, 1)}[side]


def shape_boxes(state, neighbour):
    """The boxes (x0, y0, z0, x1, y1, z1 within the cell) a state fills, close to the game's own shapes.
    `neighbour(direction)` gives the state next door, for the arms of fences, walls, panes and bars."""
    name, p = short(state), full_props(state)
    facing = p.get("facing")
    if name.endswith("_stairs"):
        low, high = ((0, .5), (.5, 1)) if p.get("half", "bottom") == "bottom" else ((.5, 1), (0, .5))
        boxes = [(0, low[0], 0, 1, low[1], 1)]
        for q in STAIR_QUARTERS.get((facing or "north", p.get("shape", "straight")), "").split():
            x0 = 0 if q[1] == "W" else .5
            z0 = 0 if q[0] == "N" else .5
            boxes.append((x0, high[0], z0, x0 + .5, high[1], z0 + .5))
        return boxes
    if name.endswith("_slab"):
        return [{"bottom": (0, 0, 0, 1, .5, 1), "top": (0, .5, 0, 1, 1, 1)}.get(p.get("type", "bottom"), (0, 0, 0, 1, 1, 1))]
    if name.endswith("_trapdoor"):
        if p.get("open") == "true" and facing:
            return [edge_box(OPPOSITE[facing], T)]
        return [edge_box("up" if p.get("half") == "top" else "down", T)]
    if name.endswith("_door") and facing:
        if p.get("open") == "true":
            return [edge_box(COUNTER[facing] if p.get("hinge") == "left" else CLOCKWISE[facing], T)]
        return [edge_box(OPPOSITE[facing], T)]
    if name.endswith("_fence_gate") and facing:
        if p.get("open") == "true":
            return [(0, .3, .4375, .125, 1, .5625), (.875, .3, .4375, 1, 1, .5625)] if AXIS[facing] == "z" else \
                   [(.4375, .3, 0, .5625, 1, .125), (.4375, .3, .875, .5625, 1, 1)]
        return [(0, .3, .4375, 1, 1, .5625)] if AXIS[facing] == "z" else [(.4375, .3, 0, .5625, 1, 1)]

    def arms(post, arm, joins):
        boxes = [post]
        for d in ("north", "south", "west", "east"):
            n = neighbour(d)
            if n and n not in ("keep", "minecraft:air") and joins(n, d):
                a0, a1, y0, y1 = arm
                boxes.append({"north": (a0, y0, 0, a1, y1, .5), "south": (a0, y0, .5, a1, y1, 1),
                              "west": (0, y0, a0, .5, y1, a1), "east": (.5, y0, a0, 1, y1, a1)}[d])
        return boxes

    def sturdy_toward(n, d):
        return bool(offers(n, OPPOSITE[d], STURDY))
    if name.endswith("_fence"):
        return arms((.375, 0, .375, .625, 1, .625), (.4375, .5625, .375, .9375),
                    lambda n, d: short(n).endswith("_fence") or short(n).endswith("_fence_gate") or sturdy_toward(n, d))
    if name.endswith("_wall") and not name.endswith("_wall_sign") and not name.endswith("_wall_banner") and "torch" not in name:
        return arms((.25, 0, .25, .75, 1, .75), (.3125, .6875, 0, .875),
                    lambda n, d: short(n).endswith("_wall") or "bars" in short(n) or "pane" in short(n) or sturdy_toward(n, d))
    if name.endswith("_pane") or name == "glass_pane" or name.endswith("bars"):
        return arms((.4375, 0, .4375, .5625, 1, .5625), (.4375, .5625, 0, 1),
                    lambda n, d: "pane" in short(n) or "bars" in short(n) or short(n).endswith("_wall") or sturdy_toward(n, d))
    if name == "chain":
        return [{"x": (0, .4375, .4375, 1, .5625, .5625), "z": (.4375, .4375, 0, .5625, .5625, 1)}.get(
            p.get("axis", "y"), (.4375, 0, .4375, .5625, 1, .5625))]
    if name.endswith("lantern") and name not in ("sea_lantern", "jack_o_lantern"):
        if p.get("hanging") == "true":
            return [(.3125, .0625, .3125, .6875, .5625, .6875), (.4375, .5625, .4375, .5625, 1, .5625)]
        return [(.3125, 0, .3125, .6875, .5, .6875)]
    if name.endswith("wall_torch") and facing:
        x0, z0 = {"north": (.4375, .6875), "south": (.4375, .1875), "west": (.6875, .4375), "east": (.1875, .4375)}[facing]
        return [(x0, .2, z0, x0 + .125, .85, z0 + .125)]
    if name.endswith("torch"):
        return [(.4375, 0, .4375, .5625, .625, .5625)]
    if (name == "ladder" or name.endswith("_wall_sign") or name.endswith("_wall_banner")) and facing:
        return [edge_box(OPPOSITE[facing], .125, .1 if "sign" in name else 0, .85 if "sign" in name else 1)]
    if (name.endswith("_button") or name == "lever") and facing:
        face = p.get("face", "wall")
        if face == "floor":
            return [(.3125, 0, .375, .6875, .125, .625)]
        if face == "ceiling":
            return [(.3125, .875, .375, .6875, 1, .625)]
        x0, y0, z0, x1, y1, z1 = edge_box(OPPOSITE[facing], .125)
        return [(max(x0, .3125) if AXIS[facing] == "z" else x0, .375, max(z0, .3125) if AXIS[facing] == "x" else z0,
                 min(x1, .6875) if AXIS[facing] == "z" else x1, .625, min(z1, .6875) if AXIS[facing] == "x" else z1)]
    if name in ("vine", "glow_lichen", "sculk_vein"):
        return [edge_box(d, .0625) for d in ("north", "south", "west", "east", "up", "down") if p.get(d) == "true"] or [edge_box("down", .0625)]
    if name.endswith("carpet") or name.endswith("pressure_plate") or name.endswith("rail") or name == "redstone_wire":
        return [(0, 0, 0, 1, .0625, 1)]
    if name == "snow":
        return [(0, 0, 0, 1, int(p.get("layers", "1")) / 8, 1)]
    if name.endswith("_bed"):
        return [(0, 0, 0, 1, .5625, 1)]
    if name in ("chest", "trapped_chest", "ender_chest"):
        return [(.0625, 0, .0625, .9375, .875, .9375)]
    if name.endswith("candle") or name == "flower_pot" or name.startswith("potted_"):
        return [(.3125, 0, .3125, .6875, .375, .6875)]
    if name in ("end_rod", "lightning_rod") and facing:
        return [{"x": (0, .4375, .4375, 1, .5625, .5625), "z": (.4375, .4375, 0, .5625, .5625, 1)}.get(
            AXIS.get(facing), (.4375, 0, .4375, .5625, 1, .5625))]
    if name in PLANTS or name in DOUBLE_PLANTS or name in ("cobweb", "hanging_roots", "spore_blossom", "sweet_berry_bush"):
        return [(.25, 0, .25, .75, .8, .75)]
    return None   # a full cube


def rotate_state(state, turns):
    """The state turned clockwise (seen from above) `turns` quarter turns, as Rotation does in the game."""
    if turns % 4 == 0 or "[" not in state:
        return state
    p = props_of(state)
    for _ in range(turns % 4):
        q = dict(p)
        if p.get("facing") in CLOCKWISE:
            q["facing"] = CLOCKWISE[p["facing"]]
        if p.get("axis") in ("x", "z"):
            q["axis"] = "z" if p["axis"] == "x" else "x"
        for d in ("north", "east", "south", "west"):
            if d in p:
                q[CLOCKWISE[d]] = p[d]
        if "rotation" in p and p["rotation"].isdigit():
            q["rotation"] = str((int(p["rotation"]) + 4) % 16)
        p = q
    return block_id(state) + "[" + ",".join(f"{k}={v}" for k, v in p.items()) + "]"


def render(bp, out, cut=None, scale=6, view="se"):
    """An isometric view from one corner (cut away above `cut`) beside a plan of every layer. Partial blocks are drawn
    in their real shapes (slabs, stair corners, trapdoors and doors open or shut, fence and pane arms, lamps), and the
    plans mark which way stairs, doors and trapdoors face, so orientation can be checked before the game is started."""
    jar = client_jar()
    colors = Colors(jar)
    turns = {"se": 0, "ne": 1, "nw": 2, "sw": 3}[view]
    W0, H, D0 = bp.width, bp.height, bp.depth
    top = H - 1 if cut is None else min(H - 1, cut)
    grid = {}
    for x, y, z, ch in bp.cells():
        if y <= top:
            state = bp.palette.get(ch, "keep")
            if state not in ("keep", "minecraft:air"):
                w, d = W0, D0
                for _ in range(turns):   # a quarter turn clockwise: the north edge becomes the east edge
                    x, z = d - 1 - z, x
                    w, d = d, w
                grid[(x, y, z)] = rotate_state(state, turns)
    W, D = (W0, D0) if turns % 2 == 0 else (D0, W0)
    s = scale
    iso_w = (W + D) * s * 2 + 4
    iso_h = (W + D) * s + (top + 1) * s * 2 + 4
    cell = max(5, s)
    plan_w, plan_h = W0 * cell + 6, D0 * cell + 14
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

    # Viewed from the corner after turning the grid: each box shows its top, +z and +x faces.
    hw, hh, ch = 2 * s, s, 2 * s
    ox, oy = D * hw + 2, (top + 1) * ch + hh + 2

    def project(x, y, z):
        """A point in block units on the canvas, lined up with the full-cube fast path below."""
        return ox + (x - z) * hw, oy + (x + z - 1) * hh - (y - 1) * ch

    def fill(points, color):
        xs, ys = [q[0] for q in points], [q[1] for q in points]
        n = len(points)
        for py in range(int(math.floor(min(ys))), int(math.ceil(max(ys))) + 1):
            for px in range(int(math.floor(min(xs))), int(math.ceil(max(xs))) + 1):
                sign = 0
                inside = True
                for i in range(n):
                    ax, ay = points[i]
                    bx, by = points[(i + 1) % n]
                    cross = (bx - ax) * (py + .5 - ay) - (by - ay) * (px + .5 - ax)
                    if abs(cross) < 1e-9:
                        continue
                    if sign == 0:
                        sign = 1 if cross > 0 else -1
                    elif (cross > 0) != (sign > 0):
                        inside = False
                        break
                if inside:
                    put(px, py, color)

    def draw_box(x, y, z, box, top_c, side_c):
        x0, y0, z0, x1, y1, z1 = box
        X0, X1, Y0, Y1, Z0, Z1 = x + x0, x + x1, y + y0, y + y1, z + z0, z + z1
        fill([project(X0, Y1, Z0), project(X1, Y1, Z0), project(X1, Y1, Z1), project(X0, Y1, Z1)], top_c)
        fill([project(X0, Y0, Z1), project(X1, Y0, Z1), project(X1, Y1, Z1), project(X0, Y1, Z1)], shade(side_c, 0.8))
        fill([project(X1, Y0, Z0), project(X1, Y0, Z1), project(X1, Y1, Z1), project(X1, Y1, Z0)], shade(side_c, 0.62))

    for (x, y, z) in sorted(grid, key=lambda q: (q[0] + q[2], q[1])):
        state = grid[(x, y, z)]
        above, south, east = grid.get((x, y + 1, z)), grid.get((x, y, z + 1)), grid.get((x + 1, y, z))
        boxes = shape_boxes(state, lambda d: grid.get((x + SIDE[d][0], y, z + SIDE[d][2])))
        if boxes is None and solid(state) and above and solid(above) and shape_boxes(above, lambda d: None) is None \
                and south and solid(south) and shape_boxes(south, lambda d: None) is None \
                and east and solid(east) and shape_boxes(east, lambda d: None) is None:
            continue
        bid = block_id(state)
        top_c, side_c = colors.color(bid, "top"), colors.color(bid, "side")
        if not solid(state):
            top_c, side_c = shade(top_c, 1.1), shade(side_c, 1.1)
        if boxes is None:   # the fast path for full cubes
            cx, cy = ox + (x - z) * hw, oy + (x + z) * hh - y * ch
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
            continue
        for box in sorted(boxes, key=lambda b: ((b[0] + b[3]) / 2 + (b[2] + b[5]) / 2, b[1])):
            draw_box(x, y, z, box, top_c, side_c)
    # Plans, always north up: each cell's top colour; stairs darker on their low side, a pale bar where a door or an
    # open trapdoor stands, so facings can be read layer by layer.
    px0 = iso_w + 4
    for y in range(top + 1):
        col, row = y % per_row, y // per_row
        bx, by = px0 + col * plan_w, 2 + row * plan_h
        for i, digit in enumerate(str(y)):
            for dy in range(5):
                for dx in range(3):
                    if DIGITS[digit][dy] >> (2 - dx) & 1:
                        put(bx + i * 4 + dx, by + dy, (230, 230, 230))
        for z in range(D0):
            for x in range(W0):
                c = bp.layers[y][z][x] if z < len(bp.layers[y]) and x < len(bp.layers[y][z]) else "_"
                state = bp.palette.get(c, "keep")
                if state == "keep":
                    continue
                color = (52, 54, 62) if state == "minecraft:air" else colors.color(block_id(state), "top")
                x0, y0 = bx + x * cell, by + 8 + z * cell
                for dy in range(cell - 1):
                    for dx in range(cell - 1):
                        put(x0 + dx, y0 + dy, color)
                name, sp = short(state), full_props(state) if state != "minecraft:air" else {}
                if name.endswith("_stairs"):
                    raised = STAIR_QUARTERS.get((sp.get("facing", "north"), sp.get("shape", "straight")), "")
                    half = (cell - 1) / 2
                    for dy in range(cell - 1):
                        for dx in range(cell - 1):
                            q = ("N" if dy < half else "S") + ("W" if dx < half else "E")
                            if q not in raised:
                                put(x0 + dx, y0 + dy, shade(color, 0.55))
                elif (name.endswith("_door") or (name.endswith("_trapdoor") and sp.get("open") == "true")) and sp.get("facing"):
                    box = shape_boxes(state, lambda d: None)[0]
                    for dy in range(cell - 1):
                        for dx in range(cell - 1):
                            fx, fz = (dx + .5) / (cell - 1), (dy + .5) / (cell - 1)
                            inside = box[0] - .08 <= fx <= box[3] + .08 and box[2] - .08 <= fz <= box[5] + .08
                            put(x0 + dx, y0 + dy, (235, 225, 200) if inside else shade(color, 0.5))
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


# ----------------------------------------------------------------------------------------------------------------
# Placement checks: the game's own rules, applied to a blueprint's grid
# ----------------------------------------------------------------------------------------------------------------
# What each block needs to stay is read from the game's source (canSurvive of each block class) and what each neighbour
# offers from tools/block-facts.json.gz, which BlockStateFactsGameTests writes from the running game: for every block
# state, which of its faces are sturdy, give centre or rigid support, are full, and whether it counts as solid. So a
# lantern under a horizontal chain, a vine on iron bars or a torch on a slab's side is caught exactly. A neighbour that is
# keep (_) or outside the grid is unknown, and nothing is said about it. BlueprintPlacementGameTests is the authority:
# it places every blueprint and asks the game.

SIDE = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}
CLOCKWISE = {"north": "east", "east": "south", "south": "west", "west": "north"}
COUNTER = {v: k for k, v in CLOCKWISE.items()}
AXIS = {"north": "z", "south": "z", "west": "x", "east": "x"}
FACE_BIT = {"down": 0, "up": 1, "north": 2, "south": 3, "west": 4, "east": 5}
STURDY, CENTER, RIGID, FULL_FACE = 0, 6, 12, 18
SOLID_BIT, AIR_BIT = 1 << 24, 1 << 25
# BlockTags.DIRT plus farmland: where grass, ferns, flowers and saplings grow (BushBlock.mayPlaceOn).
SOIL = ("grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "moss_block", "mud", "muddy_mangrove_roots",
        "mycelium", "farmland")
DEAD_BUSH_SOIL = SOIL + ("sand", "red_sand", "suspicious_sand", "terracotta")
PLANTS = ("short_grass", "fern", "poppy", "dandelion", "cornflower", "allium", "azure_bluet", "oxeye_daisy", "lily_of_the_valley",
          "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "blue_orchid", "torchflower", "pink_petals", "dead_bush",
          "azalea", "flowering_azalea", "sweet_berry_bush")
DOUBLE_PLANTS = ("tall_grass", "large_fern", "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant")
_FACTS = None


def facts():
    """{block id: {"p": {property: [values]}, "d": default key, "s": {state key: mask}}} from the game, or {}."""
    global _FACTS
    if _FACTS is None:
        path = ROOT / "tools/block-facts.json.gz"
        try:
            _FACTS = json.loads(gzip.decompress(path.read_bytes()).decode("utf-8"))
        except OSError:
            _FACTS = {}
    return _FACTS


def props_of(state):
    m = re.search(r"\[(.*)\]$", state)
    return dict(kv.split("=", 1) for kv in m.group(1).split(",")) if m else {}


def short(state):
    return block_id(state).split(":", 1)[-1]


def full_props(state):
    """Every property of a state, with the block's defaults filled in."""
    entry = facts().get(block_id(state))
    p = dict(kv.split("=", 1) for kv in entry["d"].split(",")) if entry and entry["d"] else {}
    p.update(props_of(state))
    return p


def mask_of(state):
    """The face mask of a state, or None when the state is unknown (keep, outside the grid, a block the game lacks)."""
    if state is None or state == "keep":
        return None
    entry = facts().get(block_id(state))
    if not entry:
        return None
    p = full_props(state)
    key = ",".join(f"{k}={p[k]}" for k in sorted(p))
    return entry["s"].get(key, entry["s"].get(entry["d"]))


def offers(state, face, kind):
    """Does `state` offer `kind` support on its `face`? None when unknown."""
    m = mask_of(state)
    if m is None:
        return None
    if kind == "solid":
        return bool(m & SOLID_BIT)
    if kind == "not_air":
        return not m & AIR_BIT
    return bool(m >> (kind + FACE_BIT[face]) & 1)


def property_errors(bp):
    """Property names and values the game doesn't know (a typo there is silently dropped by the game)."""
    errors = []
    for ch, state in bp.palette.items():
        entry = facts().get(block_id(state)) if state != "keep" else None
        if not entry:
            continue
        for k, v in props_of(state).items():
            if k not in entry["p"]:
                errors.append(f"palette '{ch}': {short(state)} has no property '{k}' (it has: {', '.join(entry['p']) or 'none'})")
            elif v not in entry["p"][k]:
                errors.append(f"palette '{ch}': {short(state)}'s {k} can't be '{v}' (it can be: {', '.join(entry['p'][k])})")
    return errors


def stair_shape(at, x, y, z, p):
    """The shape the game gives a stair here (StairBlock.getStairsShape), or None when a neighbour that matters is unknown."""
    facing, half = p.get("facing", "north"), p.get("half", "bottom")

    def stair(direction):
        dx, dy, dz = SIDE[direction]
        s = at(x + dx, y + dy, z + dz)
        if s is None or s == "keep":
            return "unknown"
        return full_props(s) if short(s).endswith("_stairs") else None

    def can_take(direction):
        n = stair(direction)
        if n == "unknown":
            return None
        return n is None or n.get("facing") != facing or n.get("half") != half

    front = stair(facing)
    if front == "unknown":
        return None
    if front and front.get("half") == half and AXIS[front["facing"]] != AXIS[facing]:
        take = can_take(OPPOSITE[front["facing"]])
        if take is None:
            return None
        if take:
            return "outer_left" if front["facing"] == COUNTER[facing] else "outer_right"
    back = stair(OPPOSITE[facing])
    if back == "unknown":
        return None
    if back and back.get("half") == half and AXIS[back["facing"]] != AXIS[facing]:
        take = can_take(back["facing"])
        if take is None:
            return None
        if take:
            return "inner_left" if back["facing"] == COUNTER[facing] else "inner_right"
    return "straight"


def placement_errors(bp, notes=None):
    """Blocks that couldn't stay, pairs that don't match and shapes the game would redraw; heuristics go to `notes`."""
    errors = []
    notes = notes if notes is not None else []
    w, h, d = bp.width, bp.height, bp.depth

    def at(x, y, z):
        if not (0 <= x < w and 0 <= y < h and 0 <= z < d):
            return None
        try:
            ch = bp.layers[y][z][x]
        except IndexError:
            return None
        return bp.palette.get(ch, "keep")

    def side(x, y, z, direction):
        dx, dy, dz = SIDE[direction]
        return at(x + dx, y + dy, z + dz)

    def complain(x, y, z, state, what, into=None):
        (errors if into is None else into).append(f"layer {y} row {z} col {x}: {state.split(':', 1)[-1]} {what}")

    def needs(x, y, z, state, direction, kind, what):
        """The neighbour toward `direction` must offer `kind` on the face that touches this cell."""
        if offers(side(x, y, z, direction), OPPOSITE[direction], kind) is False:
            complain(x, y, z, state, what)

    for x, y, z, ch in bp.cells():
        state = bp.palette.get(ch, "keep")
        if state in ("keep", "minecraft:air"):
            continue
        name, p = short(state), full_props(state)
        facing = p.get("facing")
        # --- two-block things: both halves, matching
        if name.endswith("_door") and "half" in p:
            want = "upper" if p["half"] == "lower" else "lower"
            other = side(x, y, z, "up" if want == "upper" else "down")
            if other is not None and other != "keep" and not (short(other) == name and full_props(other).get("half") == want
                    and all(full_props(other).get(k) == p.get(k) for k in ("facing", "hinge", "open"))):
                complain(x, y, z, state, f"needs its {want} half {'above' if want == 'upper' else 'below'}, with the same facing, hinge and open")
            if p["half"] == "lower":
                needs(x, y, z, state, "down", STURDY, "stands on nothing sturdy (a door needs a full top face under it)")
                # Double doors: the hinges go on the outer sides. hinge=left is on the facing's counter-clockwise side.
                hinge_side = COUNTER[facing] if p.get("hinge") == "left" else CLOCKWISE[facing]
                beside = side(x, y, z, hinge_side)
                if beside and short(beside).endswith("_door") and full_props(beside).get("facing") == facing:
                    complain(x, y, z, state, f"has its hinge ({p.get('hinge')}, the {hinge_side} edge) against the door beside it: "
                             f"in a double door each hinge goes on the outer side")
        if name.endswith("_bed") and "part" in p and facing:
            toward = facing if p["part"] == "foot" else OPPOSITE[facing]
            want = "head" if p["part"] == "foot" else "foot"
            other = side(x, y, z, toward)
            if other is not None and other != "keep" and not (short(other) == name and full_props(other).get("part") == want
                    and full_props(other).get("facing") == facing):
                complain(x, y, z, state, f"needs its {want} to the {toward} (a bed's head is on its facing side)")
        if name in ("chest", "trapped_chest") and p.get("type") in ("left", "right") and facing:
            toward = CLOCKWISE[facing] if p["type"] == "left" else COUNTER[facing]
            want = "right" if p["type"] == "left" else "left"
            other = side(x, y, z, toward)
            if other is not None and other != "keep" and not (short(other) == name and full_props(other).get("type") == want
                    and full_props(other).get("facing") == facing):
                complain(x, y, z, state, f"needs a type={want} half to the {toward}, with the same facing")
        if name in DOUBLE_PLANTS and "half" in p:
            other = side(x, y, z, "up" if p["half"] == "lower" else "down")
            if other is not None and other != "keep" and not (short(other) == name and full_props(other).get("half") != p["half"]):
                complain(x, y, z, state, "needs its other half")
        # --- stairs: write the corner the game will draw
        if name.endswith("_stairs") and facing:
            shape = stair_shape(at, x, y, z, p)
            if shape and shape != p.get("shape", "straight"):
                complain(x, y, z, state, f"will be drawn {shape} by the game (it joins the stairs beside it); write shape={shape}")
        # --- held from the side opposite their facing (the facing is the way they stick out)
        if facing and (name.endswith("wall_torch") or name == "ladder" or name == "tripwire_hook"
                       or ((name == "lever" or name.endswith("_button")) and p.get("face") == "wall")):
            needs(x, y, z, state, OPPOSITE[facing], STURDY, f"hangs on its {OPPOSITE[facing]} side (opposite its facing), which isn't sturdy")
        if facing and ((name.endswith("_wall_sign") and "hanging" not in name) or name.endswith("_wall_banner")):
            needs(x, y, z, state, OPPOSITE[facing], "solid", f"hangs on its {OPPOSITE[facing]} side (opposite its facing), which isn't solid")
        if name.endswith("amethyst_bud") or name == "amethyst_cluster":
            if facing:
                needs(x, y, z, state, OPPOSITE[facing], STURDY, f"grows from its {OPPOSITE[facing]} side, which isn't sturdy")
        # --- held from their facing side
        if name == "bell" and p.get("attachment") == "single_wall" and facing:
            needs(x, y, z, state, facing, STURDY, f"a wall bell hangs on its facing side ({facing}), which isn't sturdy")
        if name == "cocoa" and facing:
            log = side(x, y, z, facing)
            if log and log != "keep" and "jungle" not in short(log):
                complain(x, y, z, state, f"cocoa grows on a jungle log on its facing side ({facing})")
        # --- vines and lichen: each face clings to a full face
        if name in ("vine", "glow_lichen", "sculk_vein"):
            for face in ("north", "south", "west", "east", "up", "down"):
                if p.get(face) != "true" or offers(side(x, y, z, face), OPPOSITE[face], FULL_FACE) is not False:
                    continue
                above = side(x, y, z, "up")
                if name == "vine" and face not in ("up", "down") and above and short(above) == "vine" and full_props(above).get(face) == "true":
                    continue   # held by the vine above it
                complain(x, y, z, state, f"clings to its {face} side ({face}=true), which has no full face to hold it")
        # --- things that stand on the block below
        below = side(x, y, z, "down")
        if name in ("torch", "soul_torch", "redstone_torch") or name.endswith("candle") or (
                name in ("lantern", "soul_lantern") and p.get("hanging") != "true"):
            needs(x, y, z, state, "down", CENTER, "stands on nothing that holds its centre")
        if (name.endswith("_sign") and "wall" not in name and "hanging" not in name) or (name.endswith("_banner") and "wall" not in name):
            needs(x, y, z, state, "down", "solid", "stands on nothing solid")
        if name.endswith("carpet") and offers(below, "up", "not_air") is False:
            complain(x, y, z, state, "lies on air")
        if name.endswith("rail"):
            needs(x, y, z, state, "down", RIGID, "needs a rigid block under it")
        if name.endswith("pressure_plate") and offers(below, "up", RIGID) is False and offers(below, "up", CENTER) is False:
            complain(x, y, z, state, "needs a rigid block or a centre support under it")
        if name == "redstone_wire" and below and short(below) != "hopper":
            needs(x, y, z, state, "down", STURDY, "needs a sturdy top face under it")
        if (name == "lever" or name.endswith("_button")) and p.get("face") == "floor":
            needs(x, y, z, state, "down", STURDY, "stands on nothing sturdy")
        if name in PLANTS or (name in DOUBLE_PLANTS and p.get("half") == "lower"):
            soil = DEAD_BUSH_SOIL if name == "dead_bush" else SOIL + (("clay",) if "azalea" in name else ())
            terracotta = name == "dead_bush" and below and short(below).endswith("terracotta") and "glazed" not in short(below)
            if below is not None and below != "keep" and short(below) not in soil and not terracotta:
                complain(x, y, z, state, f"cannot grow on {short(below)} (it needs {'sand, terracotta or ' if name == 'dead_bush' else ''}grass, dirt, moss or the like)")
        # --- things that hang from the block above
        if (name in ("lantern", "soul_lantern") and p.get("hanging") == "true") or name == "spore_blossom":
            needs(x, y, z, state, "up", CENTER, "hangs from nothing that holds its centre (a horizontal chain doesn't)")
        if name.endswith("_hanging_sign") and "wall" not in name:
            needs(x, y, z, state, "up", CENTER, "hangs from nothing that holds its centre")
        if name == "hanging_roots" or ((name == "lever" or name.endswith("_button")) and p.get("face") == "ceiling"):
            needs(x, y, z, state, "up", STURDY, "hangs from nothing sturdy")
        if name in ("cave_vines", "cave_vines_plant", "weeping_vines", "weeping_vines_plant"):
            above = side(x, y, z, "up")
            if not (above and short(above).startswith(name.split("_plant")[0])):
                needs(x, y, z, state, "up", STURDY, "hangs from nothing sturdy")
        # --- heuristics: probably wrong, worth a look
        if name.endswith("_trapdoor") and p.get("open") == "true" and facing:
            panel, away = side(x, y, z, OPPOSITE[facing]), side(x, y, z, facing)
            # A shutter hangs in the air beside a window; a railing or an arm stands on something, so it isn't asked about.
            if offers(away, OPPOSITE[facing], STURDY) and offers(panel, facing, "not_air") is False \
                    and offers(side(x, y, z, "down"), "up", "not_air") is False:
                complain(x, y, z, state, f"lies on its {OPPOSITE[facing]} edge, away from the wall to its {facing}; "
                         f"if it is a shutter, flat on that wall it would face {OPPOSITE[facing]}", notes)
    return errors


def validate(paths):
    jar = client_jar()
    blocks = known_blocks(jar)
    ok = True
    for path in paths:
        bp, errors = parse(path)
        for ch, state in bp.palette.items():
            # The game's own list (block-facts) also knows the blocks of the mods it runs with, such as Simple Radio's.
            if state != "keep" and block_id(state) not in blocks and block_id(state) not in facts():
                errors.append(f"palette '{ch}': unknown block {block_id(state)}")
            props = re.search(r"\[(.*)\]$", state)
            if props and not re.fullmatch(r"[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*", props.group(1)):
                errors.append(f"palette '{ch}': malformed properties [{props.group(1)}]")
        notes = []
        if not errors:
            errors.extend(property_errors(bp))
        if not errors:
            errors.extend(placement_errors(bp, notes))
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
        for n in notes:
            print("  note: " + n)
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


# ----------------------------------------------------------------------
# Editing single cells
# ----------------------------------------------------------------------

def set_cells(path, edits):
    """Changes cells in place: each edit is (layer, row, col, value), value a palette letter or a block state.

    A state that isn't in the palette yet gets a new letter (one with no marker is reused when it already exists). A layer
    drawn as a range (layer 2-4:) is split first so only the named layer changes. Comments and everything else stay put.
    """
    bp, errors = parse(path)
    if errors:
        print("\n".join(errors)); return False
    lines = pathlib.Path(path).read_text(encoding="utf-8").splitlines()
    full = lambda st: st if st == "keep" or ":" in st.split("[")[0] else "minecraft:" + st
    taken = set(bp.palette)
    # Letters and digits first, then Greek and Cyrillic; never '@' (it reads as a marker), quotes or a backslash.
    letters = "".join(c for c in CHARS if c.isalnum()) + "αβγδεζηθικλμνξπρστυφχψωΓΔΘΛΞΠΣΦΨΩабвгдежзийклмнопрстуфхцчшщ"
    pool = [c for c in letters if c not in taken]
    added = []

    def letter(value):
        if len(value) == 1 and value in bp.palette:
            return value
        want = full(value)
        for ch, st in bp.palette.items():
            if st == want and ch not in bp.markers:
                return ch
        for ch, st in added:
            if st == want:
                return ch
        ch = pool.pop(0)
        added.append((ch, want))
        return ch

    def headers():
        found = []
        for i, raw in enumerate(lines):
            m = re.match(r"^layer\s+(\d+)(?:\s*-\s*(\d+))?\s*:\s*$", raw.strip())
            if m:
                found.append((i, int(m.group(1)), int(m.group(2) or m.group(1))))
        return found

    def rows_of(start):
        """Line numbers of a layer's grid rows (comment lines inside the layer skipped)."""
        out, i = [], start + 1
        while i < len(lines) and lines[i].strip():
            if not lines[i].lstrip().startswith("#"):
                out.append(i)
            i += 1
        return out, i

    for y, z, x, value in edits:
        ch = letter(value)
        for at, low, high in headers():
            if low <= y <= high:
                break
        else:
            print(f"no layer {y}"); return False
        if low != high:
            grid, end = rows_of(at)
            body = [lines[i] for i in grid]
            parts = []
            for a, b in ((low, y - 1), (y, y), (y + 1, high)):
                if a <= b:
                    parts += [f"layer {a}:" if a == b else f"layer {a}-{b}:"] + body + [""]
            lines[at:end] = parts[:-1]
            at = lines.index(f"layer {y}:", at)
        grid, _ = rows_of(at)
        row = lines[grid[z]]
        if x >= len(row):
            print(f"layer {y} row {z} has no col {x}"); return False
        lines[grid[z]] = row[:x] + ch + row[x + 1:]
    if added:
        last = max(i for i, raw in enumerate(lines) if re.match(r"^\s+\S\s*=\s*\S+", raw) and not raw.strip().startswith("#")
                   and i < headers()[0][0])
        indent = re.match(r"^(\s*)", lines[last]).group(1)
        lines[last + 1:last + 1] = [f"{indent}{ch} = {st.removeprefix('minecraft:')}" for ch, st in added]
    pathlib.Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    for ch, st in added:
        print(f"added palette '{ch}' = {st}")
    print(f"PASS: set {len(edits)} cell(s) in {path}")
    return True


def describe(states):
    """Prints what the game says about block states: properties, defaults, and what each face holds."""
    kinds = (("sturdy", STURDY), ("centre", CENTER), ("rigid", RIGID), ("full", FULL_FACE))
    for raw in states:
        state = raw if ":" in raw.split("[")[0] else "minecraft:" + raw
        entry = facts().get(block_id(state))
        if not entry:
            print(f"{raw}: not a block the game knows"); continue
        p = full_props(state)
        print(f"{block_id(state)}[{','.join(f'{k}={v}' for k, v in sorted(p.items()))}]")
        for k, values in entry["p"].items():
            print(f"  {k}: {' | '.join(values)}" + ("   (default " + p[k] + ")" if k in p else ""))
        m = mask_of(state)
        print(f"  solid: {'yes' if m & SOLID_BIT else 'no'} (wall and standing signs, banners need a solid neighbour)")
        for face in ("up", "down", "north", "south", "west", "east"):
            held = [n for n, k in kinds if m >> (k + FACE_BIT[face]) & 1]
            print(f"  {face:5} face: {', '.join(held) if held else '-'}")
    print("sturdy: torches, ladders, buttons, levers, doors, redstone; centre: lanterns, candles, standing torches, hanging signs;"
          "\nrigid: rails, pressure plates; full: vines and glow lichen.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    v = sub.add_parser("validate"); v.add_argument("files", nargs="+")
    r = sub.add_parser("render"); r.add_argument("file"); r.add_argument("--out"); r.add_argument("--cut", type=int); r.add_argument("--scale", type=int, default=6)
    r.add_argument("--view", choices=("se", "sw", "ne", "nw"), default="se", help="the corner the camera looks from")
    n = sub.add_parser("from-nbt"); n.add_argument("file"); n.add_argument("--out")
    s = sub.add_parser("stats"); s.add_argument("file")
    t = sub.add_parser("to-nbt"); t.add_argument("file"); t.add_argument("--out", required=True)
    f = sub.add_parser("facts", help="what the game says about block states: facts oak_stairs[half=top] chain[axis=x]")
    f.add_argument("states", nargs="+")
    e = sub.add_parser("set", help="change cells: set <file> LAYER ROW COL VALUE [LAYER ROW COL VALUE ...]")
    e.add_argument("file"); e.add_argument("cells", nargs="+")
    args = parser.parse_args()
    if args.command == "validate":
        sys.exit(0 if validate(args.files) else 1)
    if args.command == "render":
        bp, errors = parse(args.file)
        if errors:
            print("\n".join(errors)); sys.exit(1)
        out = args.out or str(pathlib.Path(args.file).with_suffix(".png"))
        render(bp, out, args.cut, args.scale, args.view)
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
    if args.command == "facts":
        describe(args.states)
    if args.command == "set":
        if len(args.cells) % 4:
            print("cells come in fours: LAYER ROW COL VALUE (a palette letter or a block state)"); sys.exit(1)
        edits = [(int(args.cells[i]), int(args.cells[i + 1]), int(args.cells[i + 2]), args.cells[i + 3]) for i in range(0, len(args.cells), 4)]
        sys.exit(0 if set_cells(args.file, edits) else 1)


if __name__ == "__main__":
    main()
