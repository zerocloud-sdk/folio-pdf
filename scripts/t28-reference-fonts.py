#!/usr/bin/env python3
"""Build one complete static Noto CJK Regular reference from offline bytes.

Usage: python3 scripts/t28-reference-fonts.py SOURCE-VF.ttf NEW-TARGET.ttf
Requires the separately installed, pinned acceptance tool fontTools 4.59.2.
"""
from pathlib import Path
import hashlib
import json
import sys

import fontTools
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


def main():
    if len(sys.argv) != 3:
        raise ValueError("Expected a source font and a new target font")
    if fontTools.__version__ != "4.59.2":
        raise ValueError("The reference recipe requires fontTools 4.59.2")
    source, target = map(Path, sys.argv[1:])
    if target.exists():
        raise ValueError("The reference target must not already exist")
    manifest = Path(__file__).resolve().parents[1] / (
        "pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto/sources.json")
    admitted = {item["file"]: item["sha256"] for item in json.loads(manifest.read_text())
                if item["file"].endswith("-VF.ttf")}
    if admitted.get(source.name) != hashlib.sha256(source.read_bytes()).hexdigest():
        raise ValueError("Source SHA-256 does not match the pinned Noto CJK reference")
    with TTFont(source, recalcTimestamp=False) as variable:
        static = instantiateVariableFont(variable, {"wght": 400},
                                         inplace=True, updateFontNames=True)
        static.recalcTimestamp = False
        static.save(target)
    print(json.dumps({"file": target.name, "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                      "source": source.name, "fonttools": fontTools.__version__, "wght": 400}))


if __name__ == "__main__":
    main()
