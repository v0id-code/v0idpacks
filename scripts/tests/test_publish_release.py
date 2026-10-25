import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


spec = importlib.util.spec_from_file_location("publisher", Path(__file__).parents[1] / "publish-release.py")
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class ReleasePublicationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "versions.json").write_text(json.dumps({"1.21.4": {}, "26.3": {}}))
        (self.root / "gradle.properties").write_text("mod_version=0.7.0\n")
        self.artifacts = self.root / "artifacts"
        self.artifacts.mkdir()
        self.write_jar("1.21.4")
        self.write_jar("26.3")
        self.environment = {
            "GITHUB_REPOSITORY": publisher.REPOSITORY,
            "GITHUB_REF": "refs/heads/main",
            "GITHUB_EVENT_NAME": "push",
            "GITHUB_SHA": "a" * 40,
            "GITHUB_RUN_NUMBER": "42",
            "GITHUB_RUN_ATTEMPT": "1",
        }

    def write_jar(self, minecraft, **changes):
        metadata = {
            "id": "v0idpacks", "version": "0.7.0", "environment": "client",
            "depends": {"minecraft": minecraft},
            "entrypoints": {"client": ["de.v0idpacks.fabric.V0idPacksClient"]},
        }
        metadata.update(changes)
        file = self.artifacts / f"v0idpacks-{minecraft}-0.7.0.jar"
        with zipfile.ZipFile(file, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps(metadata))
        return file

    def test_collects_complete_matching_build(self):
        version, files = publisher.collect_artifacts(self.artifacts, self.root)
        self.assertEqual(version, "0.7.0")
        self.assertEqual(len(files), 2)

    def test_rejects_missing_duplicate_and_extra_jars(self):
        file = self.artifacts / "v0idpacks-26.3-0.7.0.jar"
        contents = file.read_bytes()
        file.unlink()
        with self.assertRaises(ValueError):
            publisher.collect_artifacts(self.artifacts, self.root)
        file.write_bytes(contents)
        duplicate = self.artifacts / "duplicate"
        duplicate.mkdir()
        (duplicate / file.name).write_bytes(contents)
        with self.assertRaises(ValueError):
            publisher.collect_artifacts(self.artifacts, self.root)

    def test_rejects_mismatched_version_target_and_smoke_entrypoint(self):
        for changes in [
            {"version": "0.6.1"}, {"depends": {"minecraft": "1.21.5"}},
            {"entrypoints": {"client": ["de.v0idpacks.fabric.SmokeClient"]}},
        ]:
            with self.subTest(changes=changes):
                self.write_jar("1.21.4", **changes)
                with self.assertRaises(ValueError):
                    publisher.collect_artifacts(self.artifacts, self.root)

    def publish_with_mock(self, gh):
        collect = publisher.collect_artifacts
        with patch.object(publisher, "collect_artifacts", side_effect=lambda directory: collect(directory, self.root)):
            with patch.object(publisher, "gh", gh):
                publisher.publish(self.artifacts, self.environment)

    def test_publishes_only_after_upload(self):
        calls = []

        def gh(*arguments):
            calls.append(arguments)
            if arguments[0] == "api":
                return "a" * 40
            if arguments[:2] == ("release", "upload"):
                checksum = Path(arguments[-3])
                self.assertEqual(len(checksum.read_text().splitlines()), 2)
            return ""

        self.publish_with_mock(gh)
        mutations = [call[1] for call in calls if call[0] == "release"]
        self.assertEqual(mutations, ["create", "upload", "edit"])
        self.assertIn("--draft", calls[1])
        self.assertIn("--draft=false", calls[-1])

    def test_older_commit_is_never_published(self):
        calls = []

        def gh(*arguments):
            calls.append(arguments)
            return "b" * 40

        self.publish_with_mock(gh)
        self.assertEqual(len(calls), 1)

    def test_newer_commit_during_upload_keeps_draft(self):
        heads = iter(["a" * 40, "b" * 40])
        calls = []

        def gh(*arguments):
            calls.append(arguments)
            return next(heads) if arguments[0] == "api" else ""

        self.publish_with_mock(gh)
        self.assertFalse(any(call[:2] == ("release", "edit") for call in calls))

    def test_failed_upload_never_publishes(self):
        calls = []

        def gh(*arguments):
            calls.append(arguments)
            if arguments[:2] == ("release", "upload"):
                raise RuntimeError("Upload failed")
            return "a" * 40

        with self.assertRaises(RuntimeError):
            self.publish_with_mock(gh)
        self.assertFalse(any(call[:2] == ("release", "edit") for call in calls))

    def test_pull_requests_cannot_publish(self):
        self.environment["GITHUB_EVENT_NAME"] = "pull_request"
        with patch.object(publisher, "gh") as gh:
            with self.assertRaises(ValueError):
                publisher.publish(self.artifacts, self.environment)
            gh.assert_not_called()


if __name__ == "__main__":
    unittest.main()
