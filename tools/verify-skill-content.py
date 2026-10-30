"""Validate the skill catalog, menu translations and bundled voice assets (standard library only)."""
import json
import pathlib
import struct

root = pathlib.Path(__file__).resolve().parents[1]
resources = root / "src/main/resources"
assets = resources / "assets/heroesevolved"
trees = json.loads((resources / "data/heroesevolved/skill_trees.json").read_text(encoding="utf-8"))
lang = json.loads((assets / "lang/en_us.json").read_text(encoding="utf-8"))
sounds = json.loads((assets / "sounds.json").read_text(encoding="utf-8"))
expected = {"quake", "flight", "cryokinesis", "electrokinesis", "telekinesis", "telepathy",
            "regeneration", "time_manipulation", "radiation", "sonic_resonance", "superspeed", "precognition", "pyrokinesis",
            "invisibility", "phasing", "empathic_mimicry"}
assert {t["power"] for t in trees} == expected and len(trees) == 16
nodes = 0
actives = 0
for tree in trees:
    assert len(tree["branches"]) == 3
    for branch, lane in enumerate(tree["branches"]):
        assert 3 <= len(lane) <= 12
        for tier, node in enumerate(lane):
            nodes += 1
            assert node["name"] and node["description"]
            if node.get("active", tier == 1):
                actives += 1
                key = 'action.heroesevolved.' + node.get("id", f'{tree["power"]}_{branch}_{tier}')
                assert lang[key] == node["name"] and lang[key + ".desc"] == node["description"]
                assert 0 < node["energy"] <= 100 and 20 <= node["cooldown"] <= 1200
for name in ("foretold", "revised"):
    assert len((assets / f"paintings/{name}.map").read_bytes()) == 16384
for event, definition in sounds.items():
    subtitle = definition.get("subtitle")
    if subtitle:
        assert subtitle in lang, f"Missing subtitle: {subtitle}"
    for sound in definition["sounds"]:
        name = sound if isinstance(sound, str) else sound["name"]
        if name.startswith("heroesevolved:"):
            path = assets / "sounds" / (name.split(":", 1)[1] + ".ogg")
            assert path.is_file(), f"Missing recording for {event}: {path}"
for name in ("sonic_cry", "sonic_burst", "sonic_scream"):
    data = (assets / f"sounds/ability/{name}.ogg").read_bytes()
    header = data.index(b"\x01vorbis")
    channels = data[header + 11]
    sample_rate = struct.unpack_from("<I", data, header + 12)[0]
    cursor = 0
    last_sample = 0
    while cursor < len(data):
        assert data[cursor:cursor + 4] == b"OggS", f"Invalid Ogg page in {name}"
        granule = struct.unpack_from("<Q", data, cursor + 6)[0]
        if granule != 0xFFFFFFFFFFFFFFFF:
            last_sample = max(last_sample, granule)
        count = data[cursor + 26]
        cursor += 27 + count + sum(data[cursor + 27:cursor + 27 + count])
    assert channels == 1 and sample_rate == 48000, "Positional voices must be mono"
    duration = last_sample / sample_rate
    if name == "sonic_scream":
        assert 5.5 <= duration <= 6.5, "The held cry must be one continuous sound covering the five-second channel"
    else:
        assert 0.5 <= duration <= 2.5
    print(f"PASS: {name}: mono Vorbis, {duration:.2f}s, {len(data):,} bytes")
credits = (assets / "sounds/ability/sonic_credits.txt").read_text(encoding="utf-8")
assert "No recordings" in credits and "generate-sonic-audio.py" in credits, "Sonic sounds must be the original synthesized set"

# Every skill lookup in the code must name a node that exists, or buying that node silently does nothing.
import re
ids = {node.get("id", f'{tree["power"]}_{b}_{i}') for tree in trees for b, lane in enumerate(tree["branches"]) for i, node in enumerate(lane)}
source = pathlib.Path(__file__).resolve().parents[1] / "src/main/java"
lookups = 0
for java in source.rglob("*.java"):
    text = java.read_text(encoding="utf-8")
    # Telekinesis's own helper adds its prefix; everything else must use the full id.
    prefix = "telekinesis_" if java.parent.name == "telekinesis" else ""
    for match in re.finditer(r'\bhas\(\s*[\w.()]+\s*,\s*"([a-z0-9_]+)"\s*\)', text):
        key = match.group(1)
        full = key if key.startswith(prefix) else prefix + key
        line = text[:match.start()].count("\n") + 1
        assert full in ids, f"{java.relative_to(source)}:{line} checks skill '{key}', which no skill tree defines"
        lookups += 1
print(f"PASS: {nodes} skill nodes, {actives} optional actions, {lookups} skill lookups, all custom sound files/subtitles and voice provenance.")
