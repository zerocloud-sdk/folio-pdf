#!/usr/bin/env python3
"""Write the independent T29 numeric oracle and raw PDF from official hb-shape.

Usage: python3 scripts/t29-shaping-reference.py /explicit/hb-shape NEW-DIRECTORY
Requires HarfBuzz 10.2.0 and fontTools 4.59.2. All fonts and line declarations
are pinned offline resources. No product adapter, PDFBox or ICU is invoked.
"""
from io import BytesIO
from pathlib import Path
import hashlib
import csv
import json
import re
import runpy
import subprocess
import sys

import fontTools
from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance"
# Share only the existing independent ISO 32000 object/xref writer.
Pdf = runpy.run_path(str(ROOT / "scripts/t28-unicode-reference.py"))["Pdf"]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def shape(executable, run):
    command = [str(executable), str(RESOURCES / "fonts/noto" / run["font"]),
               "--text=" + run["text"], "--script=" + run["script"],
               "--language=" + run["language"], "--direction=" + run["direction"],
               "--font-size=upem", "--font-funcs=ot", "--shapers=ot",
               "--cluster-level=0", "--no-glyph-names", "--output-format=json"]
    raw = subprocess.check_output(command, text=True, timeout=30)
    glyphs = json.loads(raw)
    # hb-shape defaults to scalar indices; the product contract uses UTF-16.
    starts = sorted({glyph["cl"] for glyph in glyphs} | {len(run["text"])})
    for glyph in glyphs:
        if glyph["g"] == 0 or glyph["cl"] < 0 or glyph["cl"] >= len(run["text"]):
            raise ValueError("The independent reference contains an invalid glyph or cluster")
        end = starts[starts.index(glyph["cl"]) + 1]
        glyph["cluster"] = run["start"] + len(run["text"][:glyph["cl"]].encode("utf-16-be")) // 2
        glyph["end"] = run["start"] + len(run["text"][:end].encode("utf-16-be")) // 2
    # Commands are reconstructible from these explicit inputs and the pinned
    # options above; an installation path is not part of the numeric oracle.
    return dict(run, glyphs=glyphs, raw=raw)


def embed(pdf, path, widths):
    with TTFont(path, recalcTimestamp=False) as font:
        name = "TREFAB+" + font["name"].getDebugName(6)
        box = "%d %d %d %d" % (font["head"].xMin, font["head"].yMin,
                                font["head"].xMax, font["head"].yMax)
        ascent, descent = font["head"].yMax, font["head"].yMin
        selection = subset.Subsetter(options=subset.Options(
            retain_gids=True, layout_features=[], layout_scripts=[]))
        selection.populate(gids=set(widths))
        selection.subset(font)
        data = BytesIO()
        font.recalcTimestamp = False
        font.save(data)
    program = pdf.stream(data.getvalue(), "/Length1 %d" % len(data.getvalue()))
    descriptor = pdf.add("<< /Type /FontDescriptor /FontName /%s /Flags 32 /FontBBox [%s] "
                         "/ItalicAngle 0 /Ascent %d /Descent %d /CapHeight %d /StemV 80 /FontFile2 %d 0 R >>"
                         % (name, box, ascent, descent, ascent, program))
    descendant = pdf.add("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /%s "
                         "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> "
                         "/FontDescriptor %d 0 R /CIDToGIDMap /Identity /DW 1000 /W [%s] >>"
                         % (name, descriptor, " ".join("%d [%d]" % item for item in sorted(widths.items()))))
    return pdf.add("<< /Type /Font /Subtype /Type0 /BaseFont /%s /Encoding /Identity-H "
                   "/DescendantFonts [%d 0 R] >>" % (name, descendant))


def main():
    if len(sys.argv) != 3 or fontTools.__version__ != "4.59.2":
        raise ValueError("Expected explicit hb-shape, a new directory and fontTools 4.59.2")
    executable = Path(sys.argv[1]).resolve(strict=True)
    target = Path(sys.argv[2])
    if target.exists():
        raise ValueError("The reference output directory must not already exist")
    version = subprocess.check_output([str(executable), "--version"], text=True, timeout=30)
    if version.splitlines() != ["hb-shape (HarfBuzz) 10.2.0", "Available shapers: ot,fallback"]:
        raise ValueError("The reference requires official hb-shape 10.2.0")
    # This is the Linux reference-authoring installation, not a platform
    # certification runner. Resolve the same loader environment used above.
    linkage = subprocess.check_output(["ldd", str(executable)], text=True, timeout=30)
    libraries = re.findall(r"^\s*libharfbuzz\.so(?:\.\d+)*\s+=>\s+(.+?)\s+\(", linkage, re.MULTILINE)
    if len(libraries) != 1:
        raise ValueError("The independent oracle's linked HarfBuzz library cannot be pinned")
    native_library = Path(libraries[0]).resolve(strict=True)
    corpus_path = RESOURCES / "shaping/T29-corpus.json"
    corpus = json.loads(corpus_path.read_text(encoding="utf-8"))
    manifest = json.loads((RESOURCES / "fonts/noto/sources.json").read_text())
    pins = {item["file"]: item["sha256"] for item in manifest}
    font_paths, font_heights = {}, {}
    for name in sorted({name for profile in corpus["profiles"] for name in profile["fonts"]}):
        path = RESOURCES / "fonts/noto" / name
        if digest(path) != pins[name]:
            raise ValueError("Reference font SHA-256 mismatch")
        with TTFont(path, recalcTimestamp=False) as font:
            if font["head"].unitsPerEm != 1000:
                raise ValueError("T29 reference fonts must use 1000 units/em")
            font_heights[name] = font["head"].yMax
        font_paths[name] = path
    oracle = dict(harfbuzz=corpus["harfbuzz"], profiles={})
    used = {name: {} for name in font_paths}
    page = corpus["page"]
    size, leading, margin = page["fontSize"], page["leading"], page["margin"]
    for profile in corpus["profiles"]:
        lines = []
        for index, line in enumerate(profile["lines"]):
            runs = [shape(executable, run) for run in line["runs"]]
            ascent = max(font_heights[run["font"]] for run in runs) * size / 1000
            y = page["height"] - margin - index % page["linesPerPage"] * leading - ascent
            x = margin
            for run in runs:
                for glyph in run["glyphs"]:
                    widths = used[run["font"]]
                    if glyph["g"] in widths and widths[glyph["g"]] != glyph["ax"]:
                        raise ValueError("The reference needs distinct CIDs for conflicting advances")
                    widths[glyph["g"]] = glyph["ax"]
                    glyph["x"] = x + glyph["dx"] * size / 1000
                    glyph["y"] = y + glyph["dy"] * size / 1000
                    x += glyph["ax"] * size / 1000
            if x - margin > profile["paragraphs"][line["paragraph"]]["width"]:
                raise ValueError("A manually declared reference line exceeds its fixed width")
            lines.append(dict(line, runs=runs, page=1 + index // page["linesPerPage"], baseline=y))
        oracle["profiles"][profile["id"]] = lines
    pdf = Pdf()
    embedded = {name: embed(pdf, path, used[name]) for name, path in font_paths.items()}
    page_ids = []
    for profile in corpus["profiles"]:
        for page_number in (1, 2):
            lines = [line for line in oracle["profiles"][profile["id"]] if line["page"] == page_number]
            names = sorted({run["font"] for line in lines for run in line["runs"]})
            keys = {name: "F%d" % index for index, name in enumerate(names)}
            operators = []
            for line in lines:
                for run in line["runs"]:
                    operators.append("/Span << /ActualText <FEFF%s> >> BDC" % run["text"].encode("utf-16-be").hex())
                    for glyph in run["glyphs"]:
                        operators.append("BT /%s %d Tf 1 0 0 1 %.3f %.3f Tm <%04X> Tj ET"
                                         % (keys[run["font"]], size, glyph["x"], glyph["y"], glyph["g"]))
                    operators.append("EMC")
            stream = pdf.stream("\n".join(operators).encode("ascii"), compress=False)
            page_ids.append(pdf.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %d %d] "
                                   "/Resources << /Font << %s >> >> /Contents %d 0 R >>"
                                   % (page["width"], page["height"],
                                      " ".join("/%s %d 0 R" % (keys[name], embedded[name]) for name in names), stream)))
    target.mkdir(parents=True)
    (target / "T29-oracle.json").write_text(json.dumps(oracle, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    pdf.write(target / "T29-shaping-reference.pdf", page_ids)
    write_java_inputs(target, corpus, oracle)
    receipt = dict(harfbuzz="10.2.0", harfbuzz_version_output=version,
                   hb_shape_sha256=digest(executable), fontTools=fontTools.__version__,
                   native_library=str(native_library), native_library_sha256=digest(native_library),
                   generation_sources={path.name: digest(path) for path in
                                       [Path(__file__), ROOT / "scripts/t28-unicode-reference.py"]},
                   corpus_sha256=digest(corpus_path), fonts={name: digest(path) for name, path in font_paths.items()},
                   artifacts={path.name: digest(path) for path in sorted(target.iterdir())})
    (target / "T29-reference-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")


def write_java_inputs(target, corpus, oracle):
    """Lossless plain-data views of the fixed inputs and independent oracle."""
    properties = ["profiles=" + ",".join(profile["id"] for profile in corpus["profiles"])]
    columns = ["profile", "page", "paragraph", "line", "run", "run_start", "run_hex", "font",
               "direction", "gid", "cluster_start", "cluster_end", "ax", "ay", "dx", "dy", "x", "y", "script", "language"]
    with (target / "T29-glyphs.tsv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow(columns)
        for profile_index, profile in enumerate(corpus["profiles"]):
            name = profile["id"]
            properties += [name + ".fonts=" + ",".join(profile["fonts"]),
                           name + ".paragraphs=" + str(len(profile["paragraphs"]))]
            for paragraph_index, paragraph in enumerate(profile["paragraphs"], 1):
                key = name + "." + str(paragraph_index)
                properties += [key + ".text=" + paragraph["text"], key + ".width=" + str(paragraph["width"])]
            for line_index, line in enumerate(oracle["profiles"][name], 1):
                for run_index, run in enumerate(line["runs"], 1):
                    for glyph in run["glyphs"]:
                        writer.writerow([name, profile_index * 2 + line["page"], line["paragraph"],
                                         line_index, run_index, run["start"], run["text"].encode("utf-16-be").hex(),
                                         run["font"], run["direction"], glyph["g"], glyph["cluster"], glyph["end"],
                                         glyph["ax"], glyph["ay"], glyph["dx"], glyph["dy"],
                                         "%.3f" % glyph["x"], "%.3f" % glyph["y"], run["script"], run["language"]])
    (target / "T29-corpus.properties").write_text("\n".join(properties) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
