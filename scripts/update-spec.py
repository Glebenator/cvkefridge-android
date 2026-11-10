#!/usr/bin/env python3
"""Keeps the copy of the shared specification in spec/ equal to the cvkefridge repository.

    python scripts/update-spec.py <commit>   Replace spec/ with the folder at that commit, and write spec.ref.
    python scripts/update-spec.py --check    Compare spec/ with the cvkefridge repository at the commit in spec.ref.

The cvkefridge repository is the reference: https://github.com/Glebenator/cvkefridge. Do not edit spec/ here.
This file is the same in cvkefridge-android and in cvkefridge-ios. It uses the Python standard library only.
"""
import io
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "spec"
REF = ROOT / "spec.ref"
ARCHIVE = "https://codeload.github.com/Glebenator/cvkefridge/tar.gz/%s"


def fetch(commit):
    """The files of spec/ at the commit, as {relative path: bytes}."""
    if not (len(commit) == 40 and all(c in "0123456789abcdef" for c in commit)):
        sys.exit("Give the full commit, 40 characters from 0 to 9 and a to f: %r" % commit)
    with urllib.request.urlopen(ARCHIVE % commit, timeout=60) as response:
        data = response.read()
    files = {}
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        for member in archive:
            parts = member.name.split("/")
            if not member.isfile() or len(parts) < 3 or parts[1] != "spec" or ".." in parts:
                continue
            files["/".join(parts[2:])] = archive.extractfile(member).read()
    if not files:
        sys.exit("The commit %s has no spec/ folder." % commit)
    return files


def local():
    return {path.relative_to(SPEC).as_posix(): path.read_bytes() for path in SPEC.rglob("*") if path.is_file()}


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    if sys.argv[1] == "--check":
        commit = REF.read_text(encoding="utf-8").strip()
        want, have = fetch(commit), local()
        problems = ["missing: " + name for name in sorted(set(want) - set(have))]
        problems += ["not in cvkefridge: " + name for name in sorted(set(have) - set(want))]
        problems += ["different: " + name for name in sorted(set(want) & set(have)) if want[name] != have[name]]
        for problem in problems:
            print(problem)
        print("spec/ is %s cvkefridge at %s" % ("different from" if problems else "equal to", commit))
        return 1 if problems else 0
    commit = sys.argv[1]
    files = fetch(commit)
    if SPEC.exists():
        shutil.rmtree(SPEC)
    for name, data in files.items():
        path = SPEC / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    REF.write_text(commit + "\n", encoding="utf-8", newline="\n")
    print("spec/ now has %d files from cvkefridge at %s" % (len(files), commit))
    return 0


if __name__ == "__main__":
    sys.exit(main())
