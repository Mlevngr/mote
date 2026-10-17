import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("release", Path(__file__).parents[1] / "release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def test_properties_are_single_source(self):
        self.assertEqual(("0.8.1", 57), release.read_version("# comment\nversionName=0.8.1\nversionCode=57\n"))

    def test_rejects_nonstandard_or_missing_versions(self):
        for text in ("versionName=0.8.0-r26\nversionCode=57", "versionName=0.08.1\nversionCode=57", "versionCode=57", "versionName=0.8.1\nversionCode=0"):
            with self.assertRaises(ValueError):
                release.read_version(text)

    def test_migration_from_legacy_release(self):
        release.validate("0.8.1", 57, "v0.8.1", ("0.8.0", 56))

    def test_rejects_mismatched_tag_and_nonincreasing_versions(self):
        for name, code, tag in (("0.8.1", 57, "v0.8.2"), ("0.8.1", 56, "v0.8.1"), ("0.8.0", 57, "v0.8.0")):
            with self.assertRaises(ValueError):
                release.validate(name, code, tag, ("0.8.0", 56))

    def test_prerelease_to_stable_is_an_upgrade(self):
        release.validate("0.9.0", 59, "v0.9.0", ("0.9.0-rc.1", 58))

    def test_prerelease_numbers_are_compared_numerically(self):
        release.validate("0.9.0-beta.10", 60, "v0.9.0-beta.10", ("0.9.0-beta.2", 59))

    def test_obtainium_filter_selects_only_the_main_apk(self):
        import re
        names = ["Mote.apk", "Mote-AI-Organizer.apk", "Mote-Local-Organizer.apk", "Mote.apk.idsig"]
        self.assertEqual(["Mote.apk"], [name for name in names if re.fullmatch(r"Mote\.apk", name)])

    def test_stable_to_same_version_prerelease_is_rejected(self):
        with self.assertRaises(ValueError):
            release.validate("0.9.0-beta.1", 60, "v0.9.0-beta.1", ("0.9.0", 59))

    def test_old_and_build_tools_37_signer_formats(self):
        digest = "a" * 64
        for label in ("Signer #1", "V3.0 Signer:", "Signer (minSdkVersion=28, maxSdkVersion=32)"):
            self.assertEqual({digest}, release.signer_digests(f"{label} certificate SHA-256 digest: {digest}\n"))

    def test_multiple_schemes_must_all_use_the_locked_signer(self):
        old, other = "a" * 64, "b" * 64
        text = f"V3.0 Signer: certificate SHA-256 digest: {old}\n"
        self.assertEqual({old}, release.signer_digests(text + text))
        self.assertNotEqual({old}, release.signer_digests(
            text + f"V3.1 Signer: certificate SHA-256 digest: {other}\n"))

    def test_missing_or_source_stamp_only_signers_fail_closed(self):
        self.assertEqual(set(), release.signer_digests(""))
        self.assertEqual(set(), release.signer_digests(
            "Source Stamp Signer certificate SHA-256 digest: " + "a" * 64))
