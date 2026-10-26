"""Publish a complete, verified Actions build as downloadable GitHub release assets."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "v0id-code/v0idpacks"


def collect_artifacts(directory, root=ROOT):
    matrix = json.loads((root / "versions.json").read_text(encoding="utf-8"))
    properties = (root / "gradle.properties").read_text(encoding="utf-8")
    version = re.search(r"^mod_version\s*=\s*(\S+)\s*$", properties, re.MULTILINE)[1]
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?", version):
        raise ValueError(f"Invalid mod_version: {version}")
    files = list(Path(directory).rglob("*.jar"))
    expected = {f"v0idpacks-{minecraft}-{version}.jar": minecraft for minecraft in matrix}
    if len(files) != len(expected) or {file.name for file in files} != expected.keys():
        raise ValueError("Release requires exactly one production JAR for every Minecraft version.")
    for file in files:
        with zipfile.ZipFile(file) as jar:
            if jar.testzip() is not None:
                raise ValueError(f"Corrupt JAR: {file.name}")
            metadata = json.loads(jar.read("fabric.mod.json"))
            if (metadata.get("id") != "v0idpacks"
                    or metadata.get("version") != version
                    or metadata.get("environment") != "client"
                    or metadata.get("depends", {}).get("minecraft") != expected[file.name]
                    or metadata.get("entrypoints", {}).get("client") != ["de.v0idpacks.fabric.V0idPacksClient"]
                    or any("SmokeClient" in name for name in jar.namelist())):
                raise ValueError(f"Incorrect version, target or test entrypoint: {file.name}")
    return version, sorted(files)


def gh(*arguments):
    return subprocess.check_output(["gh", *arguments], text=True).strip()


def release_notes(version, root=ROOT):
    changelog = root / "CHANGELOG.md"
    if not changelog.is_file():
        return ""
    section = re.search(
        rf"^## {re.escape(version)}\s*\n(.*?)(?=^## |\Z)",
        changelog.read_text(encoding="utf-8"), re.MULTILINE | re.DOTALL,
    )
    return section[1].strip() + "\n\n" if section else ""


def publish(directory, environment=None):
    env = os.environ if environment is None else environment
    if (env.get("GITHUB_REPOSITORY") != REPOSITORY
            or env.get("GITHUB_REF") != "refs/heads/main"
            or env.get("GITHUB_EVENT_NAME") not in ("push", "workflow_dispatch")):
        raise ValueError("Only main-branch builds of the official repository may be published.")
    sha = env["GITHUB_SHA"]
    number = env["GITHUB_RUN_NUMBER"]
    attempt = env["GITHUB_RUN_ATTEMPT"]
    if not re.fullmatch(r"[a-f0-9]{40}", sha) or not number.isdigit() or not attempt.isdigit():
        raise ValueError("Invalid GitHub build identity.")
    version, files = collect_artifacts(directory)

    def is_current():
        return gh("api", f"repos/{REPOSITORY}/git/ref/heads/main", "--jq", ".object.sha") == sha

    if not is_current():
        print("Skipped: main already contains a newer commit.")
        return

    tag = f"build-{number}-{attempt}"
    with tempfile.TemporaryDirectory() as temporary:
        checksums = Path(temporary) / "SHA256SUMS.txt"
        checksums.write_text("".join(
            f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.name}\n" for file in files
        ), encoding="utf-8")
        notes = Path(temporary) / "notes.md"
        notes.write_text(
            f"v0idpacks {version} · Build {number} (Versuch {attempt})\n\n"
            + release_notes(version) +
            f"Commit: {sha}\n\n"
            "Die passende JAR für deine Minecraft-Version herunterladen und in den mods-Ordner legen.\n",
            encoding="utf-8",
        )
        # Draft assets are hidden from the public feed until the entire upload succeeds.
        gh("release", "create", tag, "--repo", REPOSITORY, "--target", sha,
           "--title", f"v0idpacks {version} · Build {number}", "--notes-file", str(notes), "--draft")
        gh("release", "upload", tag, *(str(file) for file in files), str(checksums), "--repo", REPOSITORY)
        if not is_current():
            print(f"Skipped publication: a newer commit exists. {tag} remains a draft.")
            return
        gh("release", "edit", tag, "--repo", REPOSITORY, "--draft=false", "--latest")
    print(f"Published {tag}: {len(files)} direct JAR downloads for v0idpacks {version}.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", type=Path, required=True)
    publish(parser.parse_args().artifacts)
