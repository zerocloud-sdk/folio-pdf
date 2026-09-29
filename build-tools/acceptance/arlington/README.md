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

## T13 text qualification supplement

[t13-r1.patch](t13-r1.patch) is a separate cumulative Apache-2.0 patch against
the same pinned upstream archive. It retains the T10/T11/T12 checks and adds
the owning-font FontName relationships, including MMType1 shared descriptors
and PDF 2.0's optional Type3 Name/FontName relationship. It also evaluates a
wildcard ArrayLength against the current array element, as required for W2
triples, and follows UseCMap streams through the matching Encoding or
ToUnicode model. Generic warnings still prevent a passing observation.

The qualification uses original legal and illegal PDFs through
`StandardsEvidenceCommand`. The initial six-font set exposed unsupported
FontName predicates and an incorrect W2 array context. A seventh control
then exposed the missing FontMultipleMaster dispatch. The Type3 extension
retains both named and unnamed legal descriptors under PDF 2.0 Table 120;
the pdfcpu rejection of the unnamed legal case is retained as a tool limitation.
This is an in-progress, case-specific qualification, not a complete text
standards chain or product certification.

Reproduce under `.build-cache/arlington/t13-r1` using the T12 instructions,
the same static Expat archive, and the fixed build date `Sep 12 2026`.
The cumulative patch must replay from pristine archived inputs. Every resulting
changed or new source/model file must match the actual build input bytes.
[The T13 pin](../../../scripts/t13-arlington-pin.properties) binds the new
executable, cumulative patch and changed TSV model separately. Earlier pins
and historical observations remain unchanged.

The Type3 glyph supplement checks d0/d1 headers and declared widths through
the independent PDFium SDK graph. It recognizes PDF token delimiters, rejects
overlapping Differences sequences and requires each observed glyph width to
be established by explicit Differences. A BaseEncoding or an incomplete mapping
is unqualified and produces INDETERMINATE. Width agreement uses the pinned
SDK's FX_FLOAT precision on both operands, as permitted by ISO 32000-1 §7.3.3;
nonfinite converted values remain unqualified. Wy must still be zero.

The 64 MiB decoded-size observation occurs after the SDK decodes the stream;
it is not a bound on peak allocation during decoding. This qualification uses
fixed original small PDFs under the pin's process deadline and output limit.
It does not establish a hostile-input memory boundary for the independent SDK.

The simple-font supplement applies the required Widths cardinality to MMType1
and TrueType as well as Type1. Required-field evaluation uses the model's single
Required condition even when an absent value has no matching type index. A PDF
null counts as missing; all fifteen required-field null controls now fail.
The required-field pass skips values already validated as present and nonnull,
avoiding unsupported conditional warnings for fields such as supplied Length1.
Independent review confirmed that the pinned model has no per-type Required
groups. These fixes retain actual type/value checks and generic warning failures.

The 334-rule declaration union includes original CIDFont, descriptor and CMap
dictionary controls. Its publication positives are PDF 2.0; legacy PDF 1.7
conditional required-font behavior remains unqualified. Detailed font programs
and other decoded text/structure relationships are not implied by these model
declaration checks.

## T78 password-security models

T78 uses the unchanged upstream `fe4a1a8` TestGrammar binary with separate
`t78-input.patch` and `t78-output.patch` overlays on the frozen latest TSV
model. `scripts/provision-t78-arlington-model.py` applies both with zero fuzz
and checks the complete `scripts/t78-arlington-runtime.sha256` manifest.
The input model admits legacy/default representations; the output model
requires the closed all-content profile and PDF 2.0 writer permissions.
Neither changes the engine or previous profile models. Exact public clauses,
qualified rule/control findings, engine coverage gaps and the independent
pdfcpu/pypdf supplements are documented in `docs/research/T78-baseline-profile-audit.md`
and `docs/t78-certification.md`. Every model finding, including unknown crypt
filter keys, is interpreted under the frozen closed-profile policy.
