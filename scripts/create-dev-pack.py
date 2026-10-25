"""Create a local-only smoke-test pack from an already downloaded Minecraft client.

No game assets are checked in or included in the distributed mod.
"""
import json
import sys
import zipfile
from pathlib import Path

version = sys.argv[1] if len(sys.argv) > 1 else "1.21.4"
project = Path(__file__).resolve().parents[1]
cache = Path.home() / ".gradle/caches/fabric-loom" / version / "minecraft-client.jar"
destination = project / "run" / version / "resourcepacks" / "Workshop Test (local)"
with zipfile.ZipFile(cache) as source:
    game = json.loads(source.read("version.json"))
    fmt = game["pack_version"]["resource"]
    if isinstance(fmt, dict):
        fmt = fmt["major"]
    files = [
        "item/diamond_sword", "item/iron_sword", "item/golden_sword", "item/netherite_sword",
        "item/diamond_axe", "item/diamond_pickaxe", "item/emerald", "item/diamond",
        "item/apple", "item/golden_apple", "item/bow", "item/ender_pearl",
        "block/stone", "block/oak_planks", "block/diamond_block", "block/grass_block_top"
    ]
    for name in files:
        asset = "assets/minecraft/textures/" + name + ".png"
        target = destination / asset
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read(asset))
    (destination / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": fmt, "description": "Local v0idpacks smoke test"}}), encoding="utf-8")
print(destination)
