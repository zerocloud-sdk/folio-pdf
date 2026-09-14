# T75 extraction visual evidence index

Capability: `document.text-structure.extract`
Acceptance Profile: `T13-text-logical-structure`
Profile record: `capabilities/evidence/T13-text-logical-structure.md`
Release train: `0.1.0-SNAPSHOT`
Chain: `visual`
Result: `pass`
Producer kind: `external-tool`
Producer: `pdfium-cli`
Producer version: `v0.11.2-pdfium-chromium-7881`

Pinned PDFium renders the three visually observable corpus cases through both
interfaces at 144 DPI into 240 by 200 opaque RGB rasters. Original expectations
use project-authored rectangular glyphs and declared transforms. ImageMagick
7.1.2-30 applies zero AE threshold/fuzz to the independent primary comparison.
The secondary renderer has a separately frozen font-edge allowance; decimal
AE and exact changed-pixel counts retain distinct meanings and bounds.

Four actual PDF defects change glyph ink or placement. Three original secondary
raster defects remove a glyph, open an interior hole or add outside ink; the
edge-only positive must remain accepted. The collector verifies the exact
specified pixel changes, opacity, raw tool output and original file identities.
The [T13 contract](../../docs/t13-certification.md) specifies the two corpus
cases without a required raster chain. Original qualification remains in
[T75 delivery evidence](T75-delivery/README.md); actual candidate and environment
certification is governed by the [Foundation authority](../foundation-evidence.yaml).
