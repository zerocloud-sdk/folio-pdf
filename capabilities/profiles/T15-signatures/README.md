# Original incremental publication and signature corpus

These acceptance-only assets were authored for #77 under Apache-2.0.
`scripts/generate-t15-corpus.py` writes 88 minimal PDFs and the complete
`cases.properties` / `corpus.json` restriction matrix. It uses raw PDF syntax,
fixed-width ByteRanges and placeholder signature Contents. No signature is
cryptographically valid and no third-party signed document is included.

`scripts/generate-t15-expectations.py` then writes literal public expectations,
original 144-DPI sRGB pixel grids, and revision/syntax/changed-paint controls.
Both generators import only the Python standard library, never Folio, PDFBox,
an independent parser or a renderer. Running the two programs in sequence in
a fresh directory reproduces every generated asset byte for byte.

`products.json` binds six products per API, all four required chains, exact
appearance bytes and visual thresholds, and each incremental/signature rule
to its ISO clause and effective defect. The full restriction matrix is run
through public Native and Facade consumers; Java-authored fixtures additionally
exercise every exact and first-excess policy limit. See the
[certification profile](../../../docs/t15-certification.md).

The blue page rectangles, red original Stamp, green replacement/new Stamp,
private catalog values, field tree and hidden signature Widget are original
project content. The corpus has no fonts, network references or external data.
