#!/usr/bin/env python3
"""Validate versions and stage verified, consistently named GitHub Release assets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
APP_ID = "com.mlevngr.inknote"
VERSION_RE = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(alpha|beta|rc)\.(0|[1-9]\d*))?")


def read_version(text):
    props = dict(re.findall(r"^(versionName|versionCode)=(\S+)\s*$", text, re.M))
    if set(props) != {"versionName", "versionCode"}:
        raise ValueError("version.properties must contain versionName and versionCode")
    name, code = props["versionName"], int(props["versionCode"])
    if not VERSION_RE.fullmatch(name) or not 1 <= code <= 2100000000:
        raise ValueError("Invalid SemVer versionName or Android versionCode")
    return name, code


def validate(name, code, tag, previous=None):
    if tag != f"v{name}":
        raise ValueError(f"Tag {tag!r} must equal v{name}")
    if previous:
        old_name, old_code = previous
        if code <= old_code:
            raise ValueError(f"versionCode must exceed previously published {old_code}")
        def key(value):
            match = VERSION_RE.fullmatch(value)
            if not match:
                raise ValueError(f"Unsupported previous version: {value}")
            major, minor, patch, label, number = match.groups()
            return (int(major), int(minor), int(patch),
                    {"alpha": 0, "beta": 1, "rc": 2, None: 3}[label], int(number or 0))
        if key(name) <= key(old_name):
            raise ValueError("versionName must increase too; do not republish an existing version")


def run(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True, stderr=subprocess.STDOUT).strip()


def previous_version(tag):
    # Older releases kept the version directly in Gradle. Keep the migration check compatible.
    files = run("git", "ls-tree", "--name-only", tag).splitlines()
    if "version.properties" in files:
        return read_version(run("git", "show", f"{tag}:version.properties"))
    gradle = run("git", "show", f"{tag}:app/build.gradle.kts")
    return re.search(r'versionName = "([^"]+)"', gradle)[1], int(re.search(r"versionCode = (\d+)", gradle)[1])


def build_tools():
    sdk = os.getenv("ANDROID_HOME") or os.getenv("ANDROID_SDK_ROOT")
    if not sdk:
        props = (ROOT / "local.properties").read_text()
        sdk = re.search(r"^sdk.dir=(.+)$", props, re.M)[1]
    candidates = [p for p in (Path(sdk) / "build-tools").iterdir() if re.fullmatch(r"\d+\.\d+\.\d+", p.name)]
    return max(candidates, key=lambda p: tuple(map(int, p.name.split("."))))


def signer_digests(output):
    # Build Tools 37 labels signers by scheme; older tools number them.
    # Source-stamp certificates are not installation signers and must not count.
    return set(re.findall(
        r"^(?:Signer #\d+|Signer \(minSdkVersion=[^\n]+\)|V\d+(?:\.\d+)? Signer:)"
        r" certificate SHA-256 digest: ([0-9a-f]{64})$", output, re.M))


def package(output, name, code, tag):
    changes = run("git", "status", "--porcelain", "--untracked-files=all")
    if changes:
        raise ValueError(f"Package from a clean committed checkout; unexpected changes:\n{changes}")
    if run("git", "rev-parse", f"{tag}^{{commit}}") != run("git", "rev-parse", "HEAD"):
        raise ValueError("Release tag must point at the checkout being packaged")
    tools = build_tools()
    expected_signer = (ROOT / "signing/certificate.sha256").read_text().strip()
    assets = [
        ("app", "Mote.apk", APP_ID),
        ("plugins/ai-organizer", "Mote-AI-Organizer.apk", "com.mlevngr.mote.plugin.ai.organizer"),
        ("plugins/local-organizer", "Mote-Local-Organizer.apk", "com.mlevngr.mote.plugin.local.organizer"),
    ]
    staged = []
    for module, filename, app_id in assets:
        directory = ROOT / module / "build/outputs/apk/release"
        metadata = json.loads((directory / "output-metadata.json").read_text())
        elements = metadata["elements"]
        if len(elements) != 1 or metadata["applicationId"] != app_id:
            raise ValueError(f"Expected one universal APK for {module}")
        element = elements[0]
        apk = directory / element["outputFile"]
        if apk.resolve().parent != directory.resolve():
            raise ValueError("APK outputFile must not escape the output directory")
        badging = run(str(tools / "aapt"), "dump", "badging", str(apk))
        actual = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
        if not actual or actual[1] != app_id or (actual[2], actual[3]) != (str(element["versionCode"]), element["versionName"]):
            raise ValueError(f"APK manifest and Gradle metadata differ for {module}")
        if module == "app" and (element["versionName"], element["versionCode"]) != (name, code):
            raise ValueError("Main APK is stale: rebuild after changing version.properties")
        signature = run(str(tools / "apksigner"), "verify", "--print-certs", str(apk))
        digests = signer_digests(signature)
        if digests != {expected_signer}:
            raise ValueError(f"Signing certificate check failed for {module} using {tools}; expected {expected_signer}, parsed {digests}. Tool output:\n{signature}")
        staged.append((apk, filename))
    output.mkdir(parents=True, exist_ok=True)
    # Fail rather than leaving stale APKs from a previous run available to the upload glob.
    if any(output.iterdir()):
        raise ValueError(f"Output directory must be empty: {output}")
    hashes = {}
    for apk, filename in staged:
        shutil.copy2(apk, output / filename)
        hashes[filename] = hashlib.sha256((output / filename).read_bytes()).hexdigest()
    (output / "SHA256SUMS").write_text("".join(f"{value}  {key}\n" for key, value in hashes.items()))
    (output / "release.json").write_text(json.dumps({
        "applicationId": APP_ID, "versionName": name, "versionCode": code,
        "tag": tag, "commit": run("git", "rev-parse", "HEAD"),
        "signerSha256": expected_signer, "sha256": hashes,
    }, indent=2) + "\n")
    print(f"Verified APKs, signatures, metadata and checksums in {output}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["check", "package"])
    parser.add_argument("--tag", required=True)
    parser.add_argument("--previous-tag")
    parser.add_argument("--history", action="store_true", help="Check against every other fetched v* tag")
    parser.add_argument("--output", type=Path, default=ROOT / "build/release")
    args = parser.parse_args()
    name, code = read_version((ROOT / "version.properties").read_text())
    validate(name, code, args.tag, previous_version(args.previous_tag) if args.previous_tag else None)
    if args.history:
        tags = run("git", "tag", "--list", "v*").splitlines()
        for tag in tags:
            if tag != args.tag:
                validate(name, code, args.tag, previous_version(tag))
    print(f"Validated {args.tag} (versionCode {code})")
    if args.command == "package":
        package(args.output, name, code, args.tag)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        detail = str(error)
        if isinstance(error, subprocess.CalledProcessError):
            detail += "\n" + (error.output or "")
        if os.getenv("GITHUB_ACTIONS") == "true":
            escaped = detail.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
            print(f"::error title=Release verification::{escaped}")
        print(detail, file=sys.stderr)
        sys.exit(1)
