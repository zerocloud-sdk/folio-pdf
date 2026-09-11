# Arlington acceptance supplements

[t10-r1.patch](t10-r1.patch) applies to the Apache-2.0 TestGrammar software in
Arlington commit `fe4a1a8897ec07f674c73160c35d748b29052f8f`. Copyright remains
with the original authors identified in each source file. The new lines were
authored by OpenAI Codex for the Folio PDF maintainer under Apache-2.0.
The original source archive, licenses, NOTICE, PDFium/component notices and TSV
model remain in the separate acceptance installation. This repository retains
the small source patch, not an upstream distribution or product dependency.

The four observed gaps and unchanged illegal controls are retained in
`capabilities/evidence/T72-standards-qualification/`. The supplement enforces
ISO 32000-1:2008 7.9.6 and Tables 30/151: distinct name-tree keys in unsigned
byte order; a Page reference cannot identify a Pages node already visited by
the checker; Fit/FitB destinations contain exactly two elements. It retains
cycle protection, remote destination indices, and existing diagnostic handling.
It does not globally convert Arlington warnings or informational output to
errors. The name-key qualification covers the flat name trees in T10; it makes
no new claim about key ranges spanning multiple leaf nodes.

The actual build identifies itself as `0.81-folio-t10-r1`, separately from
upstream `0.81`. No model overlay is needed. T03/T09 continue using their
unchanged upstream executable and model pins. The supplement remains an
independent PDFium parsing path, separate from product PDFBox and public Native
semantic observations. A standards result still requires every configured
illegal control to trigger its matching diagnostic.

`T10StandardsQualificationTest` invokes the existing public standards recorder.
Its RED run used the original Arlington pin and observed six undetected controls
for the four rules, including high bytes and identical strings expressed in
literal/hexadecimal notation. The test also requires legal Sources and the
unsigned-byte ordering positive to pass. Tool qualification and final candidate
certification are separate; passing this test does not certify a product.

Reproduction starts with the exact archive and excluded unused SDK paths in
[the original tool installation guide](../../../docs/third-party/t03-standards-tools.md).
Extract a separate tree at `.build-cache/arlington/t10-r1`, apply this patch with
`patch -p1`, and use the original CMake/GCC/PDFium Release configuration. Fix the
build date/time to `Sep 9 2026 00:00:00` and map the new source root to
`/arlington`. Retain the new executable, source-patch and unchanged model hashes
in the T10 pin; never replace the original pin or infer identity from a version
string. Compilation commands and raw logs are retained with T72 development
evidence.

## T12 annotation graph supplement

[t12-r1.patch](t12-r1.patch) is a cumulative Apache-2.0 patch against the same
upstream commit and archive. It includes the previously qualified T10/T11
checks and adds three ISO 32000-2:2020 predicates: Table 166's annotation NM
uniqueness within a page, Table 166's optional P reference to the containing
page, and Table 176's mutually exclusive Link Dest/A bindings. It does not
turn generic warnings into errors or restrict valid cross-page NM reuse.
Dictionary null values remain equivalent to absent optional keys.

The first `T12StandardsQualificationTest` run selected the T11 executable and
returned INDETERMINATE because those three unchanged original negative controls
were undetected. The separate T12 executable identifies itself as
`0.81-folio-t12-r1`. The same public recorder now qualifies all 174 required
rules (69 pdfcpu, 68 Arlington core and 37 Arlington annotation rules), including
legal cross-page names, absent optional P/Link bindings and null bindings.
This tool qualification is separate from candidate certification.

Extract the pinned source archive into `.build-cache/arlington/t12-r1`, with
the original guide's PDFix/bin exclusions, and apply `t12-r1.patch` with
`patch -p1`. The Release build uses the original GCC 13.3.0/CMake 3.28.3
configuration, `PDFSDK_PDFIUM=ON`, the T11 static Expat 2.6.1 archive and these
additional C++ flags, where `source` is the absolute extraction path:

```text
-ffile-prefix-map=<source>=/arlington -Wno-builtin-macro-redefined
-D__DATE__='"Sep 11 2026"' -D__TIME__='"00:00:00"'
```

The executable, cumulative patch, unchanged TSV model, source archive and
static Expat hashes are fixed in
[t12-arlington-pin.properties](../../../scripts/t12-arlington-pin.properties).
The cumulative patch was reapplied to pristine archived sources and all eight
resulting modified files matched the actual build inputs byte for byte.
The new header is original project work; inherited code retains its notices.
The installation, SDK and checker stay outside product runtime and artifacts.
