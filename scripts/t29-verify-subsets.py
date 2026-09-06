#!/usr/bin/env python3
"""Independently reopen T29 subsets and compare the glyphs actually painted.

Usage: python3 scripts/t29-verify-subsets.py /explicit/qpdf PDF ORACLE.json
Uses pinned qpdf 12.4.0 and fontTools 4.59.2, never a product/backend API.
Supports the reference's retained GIDs and a product's explicit CIDToGIDMap.
"""
import base64
import hashlib
from io import BytesIO
import json
import re
from pathlib import Path
import subprocess
import sys

import fontTools
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
FONTS = ROOT / "pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance/fonts/noto"


def outlines(font, gid):
    glyph = font["glyf"][font.getGlyphName(gid)]
    coordinates, endpoints, flags = glyph.getCoordinates(font["glyf"])
    return list(coordinates), list(endpoints), list(flags)


def value(objects, item):
    seen = set()
    while isinstance(item, str) and re.fullmatch(r"\d+ \d+ R", item):
        if item in seen or len(seen) >= 16:
            raise ValueError("Cyclic or excessive reference indirection")
        seen.add(item)
        item = objects["obj:" + item]["value"]
    return item


def stream(objects, reference):
    return base64.b64decode(objects["obj:" + reference]["stream"]["data"], validate=True)


def painted_glyphs(data):
    """Closed text-only profile grammar; comments cannot masquerade as text."""
    token = re.compile(r"\s+|%[^\r\n]*|<<|>>|<[0-9a-fA-F\s]*>|/[^\s<>\[\]()%]+|"
                       r"[-+]?(?:\d+\.?\d*|\.\d+)|[A-Za-z]+")
    operands, stack, glyphs = [], [], []
    font, text_open, marked = None, False, 0
    offset = 0
    while offset < len(data):
        match = token.match(data, offset)
        if not match:
            raise ValueError("Unsupported content in the closed T29 profile")
        item = match.group()
        offset = match.end()
        if item.isspace() or item.startswith("%"):
            continue
        if item.startswith(("/", "<")) or item == ">>" or item[0] in "+-.0123456789":
            operands.append(item)
            continue
        if item == "Tf" and text_open and len(operands) == 2 and operands[0].startswith("/"):
            font = operands[0]
        elif item == "Tj" and text_open and font and len(operands) == 1:
            encoded = operands[0]
            if not encoded.startswith("<") or encoded.startswith("<<"):
                raise ValueError("Expected a two-byte CID string")
            codes = bytes.fromhex(encoded[1:-1])
            if len(codes) % 2:
                raise ValueError("Invalid two-byte CID string")
            glyphs.extend((font, int.from_bytes(codes[i:i + 2], "big")) for i in range(0, len(codes), 2))
        elif item == "q" and not operands:
            stack.append(font)
        elif item == "Q" and not operands and stack:
            font = stack.pop()
        elif item == "BT" and not operands and not text_open:
            text_open = True
        elif item == "ET" and not operands and text_open:
            text_open = False
        elif item == "BDC" and len(operands) == 5 and operands[:3] == ["/Span", "<<", "/ActualText"] \
                and operands[-1] == ">>":
            marked += 1
        elif item == "EMC" and not operands and marked:
            marked -= 1
        elif (item in ("Tm", "cm") and len(operands) == 6) or (item == "Tr" and len(operands) == 1):
            for operand in operands:
                float(operand)
        else:
            raise ValueError("Unsupported operator or operands in the closed T29 profile")
        operands.clear()
    if operands or stack or text_open or marked:
        raise ValueError("Unbalanced T29 page content")
    return glyphs


def unicode_map(data):
    result = {}
    for count, body in re.findall(r"(\d+)\s+beginbfchar(.*?)endbfchar", data.decode("ascii"), re.S):
        pairs = re.findall(r"<([0-9a-fA-F]{4})>\s*<([0-9a-fA-F]+)>", body)
        if len(pairs) != int(count):
            raise ValueError("Malformed T29 cluster mapping")
        for code, text in pairs:
            cid = int(code, 16)
            if cid in result:
                raise ValueError("Duplicate T29 cluster mapping")
            result[cid] = bytes.fromhex(text).decode("utf-16-be")
    if not result:
        raise ValueError("Missing T29 cluster mappings")
    return result


def compare_glyph(original, original_gid, retained, retained_gid, dependencies, visited):
    pair = original_gid, retained_gid
    if pair in visited:
        return
    visited.add(pair)
    if retained_gid >= retained["maxp"].numGlyphs or outlines(original, original_gid) != outlines(retained, retained_gid):
        raise ValueError("A required shaping outline or composite component is missing or changed")
    source = original["glyf"][original.getGlyphName(original_gid)]
    actual = retained["glyf"][retained.getGlyphName(retained_gid)]
    if source.isComposite():
        if not actual.isComposite() or len(source.components) != len(actual.components):
            raise ValueError("A required shaping outline or composite component is missing or changed")
        for component, child in zip(source.components, actual.components):
            if component.getComponentInfo()[1] != child.getComponentInfo()[1]:
                raise ValueError("A required shaping outline or composite component is missing or changed")
            original_child = original.getGlyphID(component.glyphName)
            dependencies.add(original_child)
            compare_glyph(original, original_child, retained, retained.getGlyphID(child.glyphName), dependencies, visited)


def main():
    if len(sys.argv) != 4 or fontTools.__version__ != "4.59.2":
        raise ValueError("Expected qpdf, reference PDF, oracle and fontTools 4.59.2")
    qpdf, pdf, oracle_path = sys.argv[1:]
    version = subprocess.check_output([qpdf, "--version"], text=True, timeout=30)
    if version.splitlines()[0] != "qpdf version 12.4.0":
        raise ValueError("The reference verifier requires qpdf 12.4.0")
    document = json.loads(subprocess.check_output(
        [qpdf, "--json", "--json-stream-data=inline", pdf], text=True, timeout=30))
    objects = document["qpdf"][1]
    oracle = json.loads(Path(oracle_path).read_text(encoding="utf-8"))
    needed = {}
    for lines in oracle["profiles"].values():
        for line in lines:
            for run in line["runs"]:
                needed.setdefault(run["font"], set()).update(g["g"] for g in run["glyphs"])
    pins = {entry["file"]: entry["sha256"] for entry in json.loads((FONTS / "sources.json").read_text())}
    if len(document["pages"]) != 8:
        raise ValueError("The T29 profile requires eight pages")
    receipt = dict(result="pass", fonts={})
    cache, originals = {}, {}
    try:
        for page_index, page in enumerate(document["pages"]):
            profile = list(oracle["profiles"])[page_index // 2]
            expected = [(run, glyph) for line in oracle["profiles"][profile] if line["page"] == page_index % 2 + 1
                        for run in line["runs"] for glyph in run["glyphs"]]
            page_value = value(objects, page["object"])
            fonts = value(objects, value(objects, page_value["/Resources"])["/Font"])
            painted = painted_glyphs(b"\n".join(stream(objects, ref) for ref in page["contents"]).decode("ascii"))
            if len(painted) != len(expected):
                raise ValueError("The page does not paint the required shaping glyphs")
            for (font_name, cid), (run, glyph) in zip(painted, expected):
                reference = fonts[font_name]
                if reference not in cache:
                    type0 = value(objects, reference)
                    descendant = value(objects, value(objects, type0["/DescendantFonts"])[0])
                    if type0["/Subtype"] != "/Type0" or descendant["/Subtype"] != "/CIDFontType2":
                        raise ValueError("Unsupported T29 font subtype")
                    descriptor = value(objects, descendant["/FontDescriptor"])
                    name = descriptor["/FontName"].split("+", 1)[1] + ".ttf"
                    data = stream(objects, descriptor["/FontFile2"])
                    retained = TTFont(BytesIO(data))
                    if name not in originals:
                        path = FONTS / name
                        if hashlib.sha256(path.read_bytes()).hexdigest() != pins[name]:
                            raise ValueError("Reference source font SHA-256 mismatch")
                        originals[name] = TTFont(path, recalcTimestamp=False)
                    cid_map = descendant["/CIDToGIDMap"]
                    cid_map = None if cid_map == "/Identity" else stream(objects, cid_map)
                    mapping = unicode_map(stream(objects, type0["/ToUnicode"])) if "/ToUnicode" in type0 else None
                    instance = dict(reference=reference, embedded_sha256=hashlib.sha256(data).hexdigest(), mappings=[])
                    entry = receipt["fonts"].setdefault(name, dict(shaped_glyphs=set(), composite_dependencies=set(), instances=[]))
                    entry["instances"].append(instance)
                    cache[reference] = name, retained, cid_map, mapping, instance, set()
                name, retained, cid_map, mapping, instance, visited = cache[reference]
                if name != run["font"]:
                    raise ValueError("The painted glyph uses the wrong explicit fallback font")
                if cid_map is not None and 2 * cid + 2 > len(cid_map):
                    raise ValueError("A required shaping outline or composite component is missing or changed")
                actual_gid = cid if cid_map is None else int.from_bytes(cid_map[2 * cid:2 * cid + 2], "big")
                entry = receipt["fonts"][name]
                compare_glyph(originals[name], glyph["g"], retained, actual_gid, entry["composite_dependencies"], visited)
                if mapping is not None:
                    logical = run["text"].encode("utf-16-be")
                    cluster = logical[2 * (glyph["cluster"] - run["start"]):2 * (glyph["end"] - run["start"])].decode("utf-16-be")
                    if mapping.get(cid) != cluster:
                        raise ValueError("The painted glyph has the wrong logical input cluster")
                entry["shaped_glyphs"].add(glyph["g"])
                instance["mappings"].append(dict(cid=cid, subset_gid=actual_gid, source_gid=glyph["g"]))
        if set(receipt["fonts"]) != set(needed):
            raise ValueError("The required embedded reference font set is incomplete")
        for name, entry in receipt["fonts"].items():
            if entry["shaped_glyphs"] != needed[name]:
                raise ValueError("The required shaped glyph set is incomplete")
            entry["shaped_glyphs"] = sorted(entry["shaped_glyphs"])
            entry["composite_dependencies"] = sorted(entry["composite_dependencies"])
    finally:
        for _, retained, _, _, _, _ in cache.values():
            retained.close()
        for original in originals.values():
            original.close()
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
