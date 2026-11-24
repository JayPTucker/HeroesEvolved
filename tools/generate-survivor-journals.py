"""Generate vanilla Book and Quill loot from survivor-journals.json. No mod item or quest reward.

Run with py -3 tools/generate-survivor-journals.py [--check]. Existing supplies and story-note rolls stay intact.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/survivor-journals.json"
LOOT = ROOT / "src/main/resources/data/heroesevolved/loot_table/chests"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    journals = json.loads(SOURCE.read_text(encoding="utf-8"))
    assert len({j["id"] for j in journals}) == len(journals), "Journal IDs must be unique"
    assert len({j["title"] for j in journals}) == len(journals), "Journal titles must be unique"
    entries = []
    for journal in journals:
        pages = [f'{journal["title"]}\n\n{journal["author"]}\n\nThe last entry was never finished.', *journal["pages"]]
        entries.append({
            "type": "minecraft:item", "name": "minecraft:writable_book",
            "functions": [
                {"function": "minecraft:set_name", "name": {"text": journal["title"]}, "target": "custom_name"},
                {"function": "minecraft:set_lore", "lore": [{"text": f'An unfinished journal by {journal["author"]}.', "italic": True}], "mode": "replace_all"},
                {"function": "minecraft:set_writable_book_pages", "pages": pages, "mode": "replace_all"}
            ]
        })
    generated = {"type": "minecraft:chest", "pools": [{"rolls": 1, "entries": entries}]}
    outputs = {LOOT / "survivor_journals.json": generated}
    # A separate optional roll preserves all pre-existing supplies and story-note odds.
    for name in ("ruins_house", "odessa_supplies"):
        path = LOOT / f"{name}.json"
        table = json.loads(path.read_text(encoding="utf-8"))
        table["pools"] = [p for p in table["pools"] if p.get("name") != "heroesevolved:survivor_journals"]
        table["pools"].append({
            "name": "heroesevolved:survivor_journals", "rolls": 1,
            "conditions": [{"condition": "minecraft:random_chance", "chance": 0.4}],
            "entries": [{"type": "minecraft:loot_table", "value": "heroesevolved:chests/survivor_journals"}]
        })
        outputs[path] = table
    stale = []
    for path, data in outputs.items():
        expected = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != expected:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.write_text(expected, encoding="utf-8")
    if stale:
        raise SystemExit("Stale survivor journals: " + ", ".join(stale))
    print(f'{len(journals)} survivor journals: {"checked" if args.check else "generated"}.')


if __name__ == "__main__":
    main()
