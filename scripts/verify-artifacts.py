"""Check the distributable JARs, their game constraints and embedded pack formats."""
import json
import hashlib
import struct
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
matrix = json.loads((root / "versions.json").read_text(encoding="utf-8"))
mod_version = next(line.split("=", 1)[1].strip() for line in (root / "gradle.properties").read_text().splitlines() if line.startswith("mod_version="))
for version, spec in matrix.items():
    jar = root / "artifacts" / mod_version / f"v0idpacks-{version}-{mod_version}.jar"
    with zipfile.ZipFile(jar) as package:
        assert package.testzip() is None, f"Corrupt JAR: {version}"
        manifest = json.loads(package.read("fabric.mod.json"))
        assert manifest["environment"] == "client", version
        assert manifest["version"] == mod_version, version
        assert manifest["depends"]["minecraft"] == version, version
        assert manifest["entrypoints"]["client"] == ["de.v0idpacks.fabric.V0idPacksClient"], version
        assert not any("SmokeClient" in name for name in package.namelist()), version
        assert "de/v0idpacks/core/LibraryStore.class" in package.namelist(), version
        assert "de/v0idpacks/core/CrystalTextures.class" in package.namelist(), version
        assert "de/v0idpacks/core/LibraryStore$Project.class" in package.namelist(), version
        assert "de/v0idpacks/core/PackPresentation.class" in package.namelist(), version
        assert "de/v0idpacks/fabric/LogoPicker.class" in package.namelist(), version
        assert manifest["mixins"] == ["v0idpacks.mixins.json"], version
        mixins = json.loads(package.read("v0idpacks.mixins.json"))
        assert mixins["package"] == "de.v0idpacks.fabric.mixin", version
        assert mixins["client"] == ["PackTitleMixin"], version
        assert "de/v0idpacks/fabric/mixin/PackTitleMixin.class" in package.namelist(), version
        assert "de/v0idpacks/ui/PreviewRenderer.class" in package.namelist(), version
        assert "de/v0idpacks/ui/ModelLoader.class" in package.namelist(), version
        assert "de/v0idpacks/ui/PanelLayout.class" in package.namelist(), version
        assert "de/v0idpacks/core/MinecraftFormatting.class" in package.namelist(), version
        assert "de/v0idpacks/core/TextureCategory.class" in package.namelist(), version
        assert "de/v0idpacks/core/ArmorPart.class" in package.namelist(), version
        assert "de/v0idpacks/core/ToolType.class" in package.namelist(), version
        for name in ["core/UiPreferences", "ui/Branding", "ui/UiMotion", "ui/UiIcons"]:
            assert f"de/v0idpacks/{name}.class" in package.namelist(), (version, name)
        for name in ["grid", "star", "package", "search", "download", "check", "chevron-down"]:
            embedded = package.read(f"assets/v0idpacks/ui/{name}.png")
            assert embedded == (root / f"assets/ui/icons/{name}.png").read_bytes(), (version, name)
            assert struct.unpack(">II", embedded[16:24]) == (16, 16), (version, name)
        assert package.read("assets/v0idpacks/ui/FEATHER-LICENSE.txt") == (root / "assets/ui/icons/LICENSE").read_bytes(), version
        icon_path = f"assets/{manifest['id']}/icon.png"
        assert manifest["icon"] == icon_path, version
        icon = package.read(icon_path)
        assert icon[:8] == b"\x89PNG\r\n\x1a\n" and icon[12:16] == b"IHDR", version
        width, height = struct.unpack(">II", icon[16:24])
        assert width == height and width > 0, f"Mod Menu requires a square icon: {version}"
        branding = {"v0idpacks-logo.png": icon_path,
                    "v0idpacks-wordmark.png": "assets/v0idpacks/branding/v0idpacks-wordmark.png"}
        for name, embedded in branding.items():
            original = (root / "assets/branding/v1" / name).read_bytes()
            assert hashlib.sha256(package.read(embedded)).digest() == hashlib.sha256(original).digest(), (version, name)
        assert "assets/v0idpacks/branding/v0idpacks-logo.png" not in package.namelist(), version
        assert not any("drafts/" in name for name in package.namelist()), version
        class_data = package.read("de/v0idpacks/fabric/V0idPacksClient.class")
        assert struct.unpack(">H", class_data[6:8])[0] == spec["java"] + 44, version
        pack = json.loads(package.read("v0idpacks-pack-format.json"))
    cache = Path.home() / ".gradle/caches/fabric-loom" / version / "minecraft-client.jar"
    with zipfile.ZipFile(cache) as game:
        actual = json.loads(game.read("version.json"))["pack_version"]
    major = actual.get("resource_major", actual.get("resource"))
    minor = actual.get("resource_minor", 0)
    if "pack_format" in pack:
        assert pack["pack_format"] == major, (version, pack, actual)
    else:
        assert pack["min_format"] == pack["max_format"] == [major, minor], (version, pack, actual)
    print(f"PASS {jar.name} (Java {spec['java']}, pack {major}.{minor}, Mod Menu icon {width}x{height}, no test entrypoint)")
print(f"Verified {len(matrix)} distributable JARs.")
