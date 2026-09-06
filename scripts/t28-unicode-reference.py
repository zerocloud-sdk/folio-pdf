#!/usr/bin/env python3
"""Create a raw PDF and glyph oracle from manual lines and pinned source fonts.

Usage: python3 scripts/t28-unicode-reference.py NEW-OUTPUT-DIRECTORY
Requires the separately pinned fontTools 4.59.2. No ICU, JVM, Folio, network,
system fonts, layout engine, shaping or producer-generated coordinates are used.
"""
from io import BytesIO
from pathlib import Path
import hashlib
import json
import re
import sys
import zlib

import fontTools
from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "pdf-acceptance/src/main/resources/net/zerocloud/pdf/acceptance"


def properties(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key] = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m[1], 16)), value)
    return values


class Pdf:
    """Minimal ISO 32000 object/xref writer, independent of all product code."""
    def __init__(self):
        self.objects = [b"", b""]  # Catalog and page tree filled after the pages.

    def add(self, data):
        self.objects.append(data.encode("ascii") if isinstance(data, str) else data)
        return len(self.objects)

    def stream(self, data, dictionary="", compress=True):
        encoded = zlib.compress(data, 9) if compress else data
        header = "<< /Length %d %s %s >>\nstream\n" % (
            len(encoded), "/Filter /FlateDecode" if compress else "", dictionary)
        return self.add(header.encode("ascii") + encoded + b"\nendstream")

    def write(self, target, pages):
        self.objects[0] = b"<< /Type /Catalog /Pages 2 0 R >>"
        self.objects[1] = ("<< /Type /Pages /Count %d /Kids [%s] >>" % (
            len(pages), " ".join("%d 0 R" % page for page in pages))).encode("ascii")
        data = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
        offsets = [0]
        for index, obj in enumerate(self.objects, 1):
            offsets.append(len(data))
            data.extend(("%d 0 obj\n" % index).encode("ascii") + obj + b"\nendobj\n")
        xref = len(data)
        data.extend(("xref\n0 %d\n0000000000 65535 f \n" % len(offsets)).encode("ascii"))
        for offset in offsets[1:]:
            data.extend(("%010d 00000 n \n" % offset).encode("ascii"))
        data.extend(("trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (
            len(offsets), xref)).encode("ascii"))
        target.write_bytes(data)


def embed(pdf, font, characters):
    name = "TREFAB+" + font["name"].getDebugName(6)
    cmap = font.getBestCmap()
    widths = [(font.getGlyphID(cmap[cp]), font["hmtx"][cmap[cp]][0]) for cp in sorted(characters)]
    mappings = [(font.getGlyphID(cmap[cp]), chr(cp).encode("utf-16-be").hex()) for cp in sorted(characters)]
    head = font["head"]
    box = "%d %d %d %d" % (head.xMin, head.yMin, head.xMax, head.yMax)
    ascent, descent = head.yMax, head.yMin
    options = subset.Options(retain_gids=True, layout_features=[], layout_scripts=[])
    selection = subset.Subsetter(options=options)
    selection.populate(unicodes=characters)
    selection.subset(font)
    output = BytesIO()
    font.recalcTimestamp = False
    font.save(output)
    program = pdf.stream(output.getvalue(), "/Length1 %d" % len(output.getvalue()))
    descriptor = pdf.add("<< /Type /FontDescriptor /FontName /%s /Flags 32 /FontBBox [%s] "
                         "/ItalicAngle 0 /Ascent %d /Descent %d /CapHeight %d /StemV 80 /FontFile2 %d 0 R >>"
                         % (name, box, ascent, descent, ascent, program))
    descendant = pdf.add("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /%s "
                         "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> "
                         "/FontDescriptor %d 0 R /CIDToGIDMap /Identity /DW 1000 /W [%s] >>"
                         % (name, descriptor, " ".join("%d [%d]" % pair for pair in widths)))
    unicode = ["/CIDInit /ProcSet findresource begin", "12 dict begin", "begincmap",
               "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def",
               "/CMapName /T28Reference def", "/CMapType 2 def", "1 begincodespacerange",
               "<0000> <FFFF>", "endcodespacerange"]
    for first in range(0, len(mappings), 100):
        block = mappings[first:first + 100]
        unicode += ["%d beginbfchar" % len(block)] + ["<%04X> <%s>" % pair for pair in block] + ["endbfchar"]
    unicode += ["endcmap", "CMapName currentdict /CMap defineresource pop", "end", "end"]
    mapping = pdf.stream("\n".join(unicode).encode("ascii"))
    return pdf.add("<< /Type /Font /Subtype /Type0 /BaseFont /%s /Encoding /Identity-H "
                   "/DescendantFonts [%d 0 R] /ToUnicode %d 0 R >>" % (name, descendant, mapping))


def main():
    if len(sys.argv) != 2 or fontTools.__version__ != "4.59.2":
        raise ValueError("Expected a new output directory and fontTools 4.59.2")
    target = Path(sys.argv[1])
    if target.exists():
        raise ValueError("The reference output directory must not already exist")
    corpus_path = RESOURCES / "unicode/T28-corpus.properties"
    corpus = properties(corpus_path)
    pins = properties(RESOURCES / "fonts/noto/fonts.properties")
    fonts, used = {}, {}
    for filename in sorted({name for key, value in corpus.items() if key.endswith(".fonts") for name in value.split(",")}):
        path = RESOURCES / "fonts/noto" / filename
        if hashlib.sha256(path.read_bytes()).hexdigest() != pins[filename + ".sha256"]:
            raise ValueError("Reference font SHA-256 mismatch: " + filename)
        fonts[filename] = TTFont(path, recalcTimestamp=False)
        if fonts[filename]["head"].unitsPerEm != 1000:
            raise ValueError("The declared metric reference requires 1000 units/em")
        used[filename] = set()
    rows = ["profile\tindex\tline\tunicode\tfont\tgid\tx\ty\tadvance"]
    pages = []
    for profile in corpus["profiles"].split(","):
        choices = corpus[profile + ".fonts"].split(",")
        line_index, item_index = 0, 0
        page = []
        for paragraph in range(1, int(corpus[profile + ".paragraphs"]) + 1):
            for line in corpus["%s.%d.lines" % (profile, paragraph)].split("|"):
                selected = [(cp, next(name for name in choices if ord(cp) in fonts[name].getBestCmap())) for cp in line]
                y = 720 - 48 * line_index - max(fonts[name]["head"].yMax * .012 for cp, name in selected)
                x = 72.
                for cp, name in selected:
                    font = fonts[name]
                    glyph = font.getBestCmap()[ord(cp)]
                    gid = font.getGlyphID(glyph)
                    advance = font["hmtx"][glyph][0] * .012
                    used[name].add(ord(cp))
                    page.append((name, gid, x, y))
                    rows.append("%s\t%d\t%d\t%04X\t%s\t%d\t%.3f\t%.3f\t%.3f" % (
                        profile, item_index, line_index, ord(cp), font["name"].getDebugName(6), gid, x, y, advance))
                    item_index += 1
                    x += advance
                line_index += 1
        pages.append(page)
    pdf = Pdf()
    embedded = {}  # References assigned once per explicit font.
    for name, font in fonts.items():
        if used[name]:
            embedded[name] = embed(pdf, font, used[name])
        font.close()
    page_ids = []
    for page in pages:
        names = sorted({name for name, gid, x, y in page})
        font_keys = {name: "F%d" % (index + 1) for index, name in enumerate(names)}
        stream = "\n".join("BT /%s 12 Tf 1 0 0 1 %.3f %.3f Tm <%04X> Tj ET" % (
            font_keys[name], x, y, gid) for name, gid, x, y in page)
        content = pdf.stream(stream.encode("ascii"), compress=False)
        page_ids.append(pdf.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                               "/Resources << /Font << %s >> >> /Contents %d 0 R >>" % (
            " ".join("/%s %d 0 R" % (font_keys[name], embedded[name]) for name in names), content)))
    target.mkdir(parents=True)
    (target / "T28-glyphs.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    pdf.write(target / "T28-unicode-reference.pdf", page_ids)
    receipt = {"fontTools": fontTools.__version__, "corpus_sha256": hashlib.sha256(corpus_path.read_bytes()).hexdigest(),
               "artifacts": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(target.iterdir())}}
    (target / "T28-reference-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
