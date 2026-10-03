"""Release-only tooling. No credentials or publishing code enters the mod JAR."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen
import uuid
import zipfile


REPOSITORY = "KingdomFoxes/RALLE"
API = "https://api.modrinth.com/v2"
BUNDLE = Path("build/release")
DEPENDENCIES = [
    {"project_id": "P7dR8mSH", "dependency_type": "required"},  # Fabric API
    {"project_id": "ccKDOlHs", "dependency_type": "embedded"},  # owo-lib
    {"project_id": "dU5Gb9Ab", "dependency_type": "optional"},  # Wynntils
]
VERSION_PATTERN = r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?"


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if line and not line.startswith("#") and "=" in line)


def metadata(event, config):
    release = event["release"]
    if event["repository"]["full_name"] != REPOSITORY or release["draft"]:
        raise ValueError("Only published RALLE releases can be uploaded.")
    tag = release["tag_name"]
    version = tag.removeprefix("v")
    if not re.fullmatch(VERSION_PATTERN, version):
        raise ValueError("Use a version tag such as v0.1.9 or v0.1.9-alpha.1.")
    if version != config["mod_version"]:
        raise ValueError(f"Tag {tag} does not match mod_version={config['mod_version']}.")
    if config["archives_base_name"] != "ralle":
        raise ValueError("Expected RALLE archive name.")
    prerelease = "-" in version.split("+", 1)[0]
    if prerelease and not release["prerelease"]:
        raise ValueError("Mark alpha/beta/other prerelease tags as a GitHub pre-release.")
    channel = "alpha" if re.search(r"(?:^|[.-])alpha(?:[.-]|$)", version, re.I) else (
        "beta" if release["prerelease"] else "release")
    return {
        "tag": tag, "release_id": release["id"], "version_number": version,
        "name": release["name"] or f"RALLE {version}",
        "changelog": release["body"] or "", "version_type": channel,
        "game_versions": [config["minecraft_version"]], "loaders": ["fabric"],
        "dependencies": DEPENDENCIES,
        "filename": f"ralle-{version}.jar",
    }


def verify_jar(path, info):
    with zipfile.ZipFile(path) as jar:
        mod = json.loads(jar.read("fabric.mod.json"))
        if mod["id"] != "ralle" or mod["version"] != info["version_number"]:
            raise ValueError("JAR identity/version does not match the release.")
        if mod["environment"] != "client":
            raise ValueError("Expected a client-only JAR.")
        if "ralle-local-backend" in jar.namelist():
            raise ValueError("Local-backend builds cannot be published.")
        if "org/kingdomfoxes/ralle/RalleClient.class" not in jar.namelist():
            raise ValueError("Expected compiled production JAR, not sources.")


def api(path, token, data=None, content_type=None):
    headers = {"Authorization": token, "User-Agent": f"{REPOSITORY}/release-automation"}
    if content_type:
        headers["Content-Type"] = content_type
    request = Request(API + path, data=data, headers=headers)
    try:
        with urlopen(request, timeout=60) as response:
            return json.load(response)
    except HTTPError as error:
        # Do not dump request headers or credentials into workflow logs.
        raise RuntimeError(f"Modrinth API returned HTTP {error.code} for {path}. "
                           "Check project ID, token permissions, and project access.") from None


def existing_version(versions, info, jar):
    matches = [v for v in versions if v["version_number"] == info["version_number"]]
    if not matches:
        return None
    digest = hashlib.sha512(jar.read_bytes()).hexdigest()
    if len(matches) == 1:
        version = matches[0]
        if (version["status"] == "listed"
                and version["game_versions"] == info["game_versions"]
                and version["loaders"] == info["loaders"]
                and version["version_type"] == info["version_type"]
                and any(f["primary"] and f["hashes"].get("sha512") == digest
                        for f in version["files"])):
            return version["id"]
    raise ValueError("This version already exists on Modrinth with different files or metadata. "
                     "Resolve it manually or release a new version; it will not be overwritten.")


def multipart(payload, jar):
    boundary = "ralle-" + uuid.uuid4().hex
    data = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
            'Content-Type: application/json\r\n\r\n').encode()
    data += json.dumps(payload).encode() + b"\r\n"
    data += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
             f'filename="{jar.name}"\r\nContent-Type: application/java-archive\r\n\r\n').encode()
    data += jar.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    return data, f"multipart/form-data; boundary={boundary}"


def gh(*args, capture=False):
    return subprocess.run(["gh", *args], check=True, text=True, encoding="utf-8",
                          stdout=subprocess.PIPE if capture else None).stdout


def release_event(event):
    if event["repository"]["full_name"] != REPOSITORY:
        raise ValueError("Only RALLE releases can be uploaded.")
    if "release" in event:
        return event
    # Manual recovery uses the selected workflow commit's publishing tooling,
    # while both jobs still check out and build the original release tag.
    tag = event["inputs"]["tag"]
    if not re.fullmatch("v?" + VERSION_PATTERN, tag):
        raise ValueError("Select an existing version tag, such as 1.0.0.")
    result = dict(event)
    result["release"] = json.loads(gh("api", f"repos/{REPOSITORY}/releases/tags/{quote(tag, safe='')}",
                                      capture=True))
    if result["release"]["tag_name"] != tag:
        raise ValueError("GitHub returned a different release tag.")
    return result


def github_asset(info, jar):
    release = json.loads(gh("api", f"repos/{REPOSITORY}/releases/{info['release_id']}", capture=True))
    if release["draft"] or release["tag_name"] != info["tag"]:
        raise ValueError("GitHub release no longer matches the prepared release.")
    if any(asset["name"] == jar.name for asset in release["assets"]):
        # Preserve the already-published artifact on retry, even if ZIP timestamps
        # make a fresh Gradle build differ byte-for-byte from the first build.
        with tempfile.TemporaryDirectory() as directory:
            gh("release", "download", info["tag"], "--repo", REPOSITORY,
               "--pattern", jar.name, "--dir", directory)
            canonical = Path(directory) / jar.name
            verify_jar(canonical, info)
            shutil.copyfile(canonical, jar)
    else:
        if release.get("immutable"):
            raise ValueError("Immutable GitHub release has no JAR. Attach the production JAR "
                             "to its draft before publishing, then rerun this workflow.")
        gh("release", "upload", info["tag"], str(jar), "--repo", REPOSITORY)


def publish(info, jar):
    token = os.environ.get("MODRINTH_TOKEN", "")
    project_id = os.environ.get("MODRINTH_PROJECT_ID", "")
    if not token or not re.fullmatch(r"[A-Za-z0-9_-]+", project_id):
        raise ValueError("Set the MODRINTH_TOKEN repository secret and "
                         "MODRINTH_PROJECT_ID repository variable first. See docs/releases.md.")
    project = api("/project/" + quote(project_id, safe=""), token)
    # The maintainer-selected ID is authoritative. Draft projects may have a
    # temporary slug or no version-derived project type yet.
    if project["id"] != project_id and project.get("slug") != project_id:
        raise ValueError("Modrinth returned a different project than MODRINTH_PROJECT_ID.")
    versions = api(f"/project/{project['id']}/version", token)
    github_asset(info, jar)
    version_id = existing_version(versions, info, jar)
    if version_id:
        print(f"Modrinth version {version_id} already contains this JAR; skipping duplicate upload.")
        return
    payload = {key: info[key] for key in (
        "name", "version_number", "changelog", "version_type", "game_versions", "loaders", "dependencies")}
    payload.update(project_id=project["id"], featured=False, status="listed", environment="client_only",
                   file_parts=["file"], primary_file="file")
    data, content_type = multipart(payload, jar)
    # No automatic POST retries: an ambiguous network failure may have created
    # the version. Rerunning the workflow checks for it before another upload.
    result = api("/version", token, data, content_type)
    print(f"Published Modrinth version {result['id']} ({info['version_number']}).")


def main():
    event = release_event(json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8")))
    info = metadata(event, properties(Path("gradle.properties")))
    jar = BUNDLE / info["filename"]
    phase = sys.argv[1]
    if phase == "prepare":
        # The bundle is populated after Gradle clean, so no metadata lives in a
        # build directory that clean would erase.
        print(f"Preparing {info['tag']} for Minecraft {info['game_versions'][0]} ({info['version_type']}).")
    elif phase == "verify":
        source = Path("build/libs") / info["filename"]
        verify_jar(source, info)
        BUNDLE.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, jar)
    elif phase == "publish":
        verify_jar(jar, info)
        publish(info, jar)
    else:
        raise ValueError("Expected prepare, verify, or publish.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        sys.exit(1)
