import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import release


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.event = {
            "repository": {"full_name": "KingdomFoxes/RALLE"},
            "release": {"id": 123, "tag_name": "v0.1.9", "name": "RALLE 0.1.9",
                        "body": "## Changes\nA fix.", "draft": False, "prerelease": False},
        }
        self.config = {"mod_version": "0.1.9", "archives_base_name": "ralle",
                       "minecraft_version": "1.21.11"}
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.jar = Path(self.directory.name) / "ralle-0.1.9.jar"
        self.info = release.metadata(self.event, self.config)
        self.write_jar()

    def write_jar(self, version="0.1.9", local=False, sources=False):
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({
                "id": "ralle", "version": version, "environment": "client"}))
            if not sources:
                jar.writestr("org/kingdomfoxes/ralle/RalleClient.class", b"compiled")
            if local:
                jar.writestr("ralle-local-backend", "true")

    def existing(self):
        return {"id": "abc123", "version_number": "0.1.9", "status": "listed",
                "game_versions": ["1.21.11"], "loaders": ["fabric"], "version_type": "release",
                "files": [{"primary": True, "hashes": {
                    "sha512": hashlib.sha512(self.jar.read_bytes()).hexdigest()}}]}

    def test_title_and_multiline_changelog_preserved(self):
        self.assertEqual("RALLE 0.1.9", self.info["name"])
        self.assertEqual("## Changes\nA fix.", self.info["changelog"])
        self.assertEqual("release", self.info["version_type"])

    def test_tags_with_or_without_v(self):
        self.event["release"]["tag_name"] = "0.1.9"
        self.assertEqual("0.1.9", release.metadata(self.event, self.config)["version_number"])

    def test_mismatched_or_unsafe_tag_rejected(self):
        for tag in ("v0.2.0", "v0.1.9\nmalicious", "../../0.1.9", "v0.1.9;echo", "-0.1.9"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                self.event["release"]["tag_name"] = tag
                release.metadata(self.event, self.config)

    def test_channels_and_prerelease_flag(self):
        for version, channel in (("0.1.9-alpha.1", "alpha"), ("0.1.9-beta.2", "beta"),
                                 ("0.1.9-rc.1", "beta"), ("0.1.9", "beta")):
            self.event["release"].update(tag_name="v" + version, prerelease=True)
            self.config["mod_version"] = version
            with self.subTest(version=version):
                self.assertEqual(channel, release.metadata(self.event, self.config)["version_type"])
        self.event["release"]["prerelease"] = False
        self.event["release"]["tag_name"] = "v0.1.9-alpha.1"
        self.config["mod_version"] = "0.1.9-alpha.1"
        with self.assertRaises(ValueError):
            release.metadata(self.event, self.config)

    def test_drafts_and_other_repositories_rejected(self):
        self.event["release"]["draft"] = True
        with self.assertRaises(ValueError):
            release.metadata(self.event, self.config)
        self.event["release"]["draft"] = False
        self.event["repository"]["full_name"] = "someone/RALLE"
        with self.assertRaises(ValueError):
            release.metadata(self.event, self.config)

    def test_only_production_jar_allowed(self):
        release.verify_jar(self.jar, self.info)
        for args in ({"version": "0.1.8"}, {"local": True}, {"sources": True}):
            with self.subTest(args=args), self.assertRaises(ValueError):
                self.write_jar(**args)
                release.verify_jar(self.jar, self.info)

    def test_duplicate_same_primary_file_skipped(self):
        self.assertIsNone(release.existing_version([], self.info, self.jar))
        self.assertEqual("abc123", release.existing_version([self.existing()], self.info, self.jar))

    def test_conflicting_or_hidden_version_rejected(self):
        for key, value in (("status", "draft"), ("loaders", ["forge"]),
                           ("game_versions", ["1.21.10"]), ("version_type", "beta"), ("files", [])):
            version = self.existing()
            version[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                release.existing_version([version], self.info, self.jar)

    def test_multipart_preserves_binary_and_json(self):
        payload = {"changelog": "Text with \"quotes\"\nNew line.", "file_parts": ["file"]}
        data, content_type = release.multipart(payload, self.jar)
        self.assertIn(json.dumps(payload).encode(), data)
        self.assertIn(self.jar.read_bytes(), data)
        self.assertIn(b'name="file"; filename="ralle-0.1.9.jar"', data)
        self.assertTrue(content_type.startswith("multipart/form-data; boundary=ralle-"))

    def test_missing_credentials_never_uploads(self):
        with patch.dict("os.environ", {"MODRINTH_TOKEN": "", "MODRINTH_PROJECT_ID": ""}), \
                patch.object(release, "api") as api, patch.object(release, "gh") as gh:
            with self.assertRaises(ValueError):
                release.publish(self.info, self.jar)
            api.assert_not_called()
            gh.assert_not_called()

    def test_wrong_project_never_uploads(self):
        with patch.dict("os.environ", {"MODRINTH_TOKEN": "test", "MODRINTH_PROJECT_ID": "project"}), \
                patch.object(release, "api", return_value={"slug": "other", "project_type": "mod"}), \
                patch.object(release, "gh") as gh:
            with self.assertRaises(ValueError):
                release.publish(self.info, self.jar)
            gh.assert_not_called()

    def test_publish_sends_release_metadata_and_same_jar(self):
        with patch.dict("os.environ", {"MODRINTH_TOKEN": "test", "MODRINTH_PROJECT_ID": "project"}), \
                patch.object(release, "github_asset"), patch.object(release, "api", side_effect=[
                    {"id": "project", "slug": "ralle", "project_type": "mod"}, [], {"id": "new"}]) as api:
            release.publish(self.info, self.jar)
            path, token, data, content_type = api.call_args.args
            self.assertEqual("/version", path)
            self.assertIn(b'"project_id": "project"', data)
            self.assertIn(b'"version_number": "0.1.9"', data)
            self.assertIn(b'"dependency_type": "embedded"', data)
            self.assertIn(b'"environment": "client_only"', data)
            self.assertIn(self.jar.read_bytes(), data)

    def test_github_retry_reuses_existing_jar(self):
        original = self.jar.read_bytes()
        def fake_gh(*args, **kwargs):
            if args[0] == "api":
                return json.dumps({"draft": False, "tag_name": "v0.1.9", "assets": [
                    {"name": self.jar.name}]})
            self.assertEqual(("release", "download"), args[:2])
            (Path(args[-1]) / self.jar.name).write_bytes(original)
        # Simulate a rebuild with different ZIP metadata.
        self.jar.write_bytes(b"rebuild placeholder")
        with patch.object(release, "gh", side_effect=fake_gh):
            release.github_asset(self.info, self.jar)
        self.assertEqual(original, self.jar.read_bytes())

    def test_immutable_release_without_jar_never_uploads(self):
        with patch.object(release, "gh", return_value=json.dumps({
                "draft": False, "tag_name": "v0.1.9", "assets": [], "immutable": True})) as gh:
            with self.assertRaises(ValueError):
                release.github_asset(self.info, self.jar)
            self.assertEqual(1, gh.call_count)


if __name__ == "__main__":
    unittest.main()
