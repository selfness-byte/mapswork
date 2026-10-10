#!/usr/bin/env python3
"""Install verified Android build tools in writable cloud storage."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
from urllib.parse import urlparse
import zipfile

TOOLS = Path(os.environ.get("MAPSWORK_TOOLS", "/workspace/.mapswork-tools"))
GRADLE_VERSION = "8.11.1"
COMMAND_TOOLS_ARCHIVE = "commandlinetools-linux-13114758_latest.zip"
COMMAND_TOOLS_SHA1 = "5fdcc763663eefb86a5b8879697aa6088b041e70"


def download_verified(url, target, expected, algorithm="sha256"):
    if target.exists() and hashlib.new(algorithm, target.read_bytes()).hexdigest() == expected:
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix(target.suffix + ".partial")
    digest = hashlib.new(algorithm)
    with urllib.request.urlopen(url, timeout=120) as response, partial.open("wb") as output:
        while block := response.read(1024 * 1024):
            digest.update(block)
            output.write(block)
    if digest.hexdigest() != expected:
        partial.unlink(missing_ok=True)
        raise RuntimeError(f"Checksum mismatch for {target.name}")
    partial.replace(target)
    print(f"Verified {target.name}", flush=True)


def java_options():
    task_user = TOOLS / "user-home"
    task_user.mkdir(parents=True, exist_ok=True)
    options = [f"-Duser.home={task_user}"]
    proxy = urlparse(os.environ.get("HTTPS_PROXY", ""))
    if proxy.hostname:
        for protocol in ("http", "https"):
            options.extend([f"-D{protocol}.proxyHost={proxy.hostname}",
                            f"-D{protocol}.proxyPort={proxy.port or 80}"])
        options.append("-Dhttp.nonProxyHosts=localhost|127.*|[::1]")
    trust = Path("/etc/ssl/certs/java/cacerts")
    if trust.exists():
        options.append(f"-Djavax.net.ssl.trustStore={trust}")
    return " ".join(options)


def main():
    TOOLS.mkdir(parents=True, exist_ok=True)
    downloads = TOOLS / "downloads"
    java_home = TOOLS / "jdk-21"
    if not (java_home / "bin/javac").exists():
        url = "https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz"
        with urllib.request.urlopen(url + ".sha256", timeout=30) as response:
            checksum = response.read().decode().strip().split()[0]
        archive = downloads / "jdk-21.tar.gz"
        download_verified(url, archive, checksum)
        with tempfile.TemporaryDirectory(dir=TOOLS) as temporary:
            with tarfile.open(archive) as bundle:
                bundle.extractall(temporary, filter="data")
            shutil.move(str(next(Path(temporary).iterdir())), str(java_home))

    gradle_home = TOOLS / f"gradle-{GRADLE_VERSION}"
    if not (gradle_home / "bin/gradle").exists():
        url = f"https://downloads.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip"
        with urllib.request.urlopen(url + ".sha256", timeout=30) as response:
            checksum = response.read().decode().strip()
        archive = downloads / f"gradle-{GRADLE_VERSION}.zip"
        download_verified(url, archive, checksum)
        with zipfile.ZipFile(archive) as bundle:
            bundle.extractall(TOOLS)
        (gradle_home / "bin/gradle").chmod(0o755)

    sdk = TOOLS / "android-sdk"
    sdkmanager = sdk / "cmdline-tools/19.0/bin/sdkmanager"
    if not sdkmanager.exists():
        archive = downloads / COMMAND_TOOLS_ARCHIVE
        download_verified("https://dl.google.com/android/repository/" + COMMAND_TOOLS_ARCHIVE,
                          archive, COMMAND_TOOLS_SHA1, "sha1")
        with tempfile.TemporaryDirectory(dir=TOOLS) as temporary:
            with zipfile.ZipFile(archive) as bundle:
                bundle.extractall(temporary)
            destination = sdk / "cmdline-tools/19.0"
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(Path(temporary) / "cmdline-tools"), destination)
        for binary in sdkmanager.parent.iterdir():
            binary.chmod(0o755)

    android_user = TOOLS / "android-user"
    (android_user / "cache").mkdir(parents=True, exist_ok=True)
    environment = dict(os.environ, JAVA_HOME=str(java_home), ANDROID_HOME=str(sdk),
                       ANDROID_USER_HOME=str(android_user),
                       GRADLE_USER_HOME=os.environ.get("MAPSWORK_GRADLE_CACHE", "/workspace/.mapswork-gradle"),
                       JAVA_TOOL_OPTIONS=java_options())
    subprocess.run([str(java_home / "bin/javac"), "-version"], check=True, env=environment)
    subprocess.run([str(gradle_home / "bin/gradle"), "--version"], check=True, env=environment)
    license_input = "y\n" * 100
    subprocess.run([str(sdkmanager), f"--sdk_root={sdk}", "--licenses"],
                   input=license_input, text=True, check=True, env=environment)
    subprocess.run([str(sdkmanager), f"--sdk_root={sdk}", "platforms;android-36",
                    "build-tools;36.0.0", "platform-tools"],
                   input=license_input, text=True, check=True, env=environment)
    print(f"Android SDK ready: {sdk}", flush=True)


if __name__ == "__main__":
    main()
