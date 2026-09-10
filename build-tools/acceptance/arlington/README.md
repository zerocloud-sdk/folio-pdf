# T10 Arlington acceptance supplement

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
