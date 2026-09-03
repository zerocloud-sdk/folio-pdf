# Compatibility inventories

Folio PDF keeps two versioned YAML authorities. The checked-in YAML is
normative; files below `docs/generated/` are deterministic views and must
never be edited by hand.

## Commands

Validate both authorities and every referenced repository file:

```text
./scripts/inventory validate
```

Regenerate the human-readable views:

```text
./scripts/inventory generate
```

The outputs are:

- `docs/generated/capability-matrix.md`
- `docs/generated/facade-surface.md`

Record the built-in T03 blank-document and T18 Canvas-image Acceptance
Profiles with the pinned external syntax validator, independent renderer,
raster comparator, and project semantic assertions:

```text
./scripts/provision-qpdf /path/to/qpdf-12.4.0-bin-linux-x86_64.zip
./scripts/provision-pdfium /path/to/pdfium-webassembly-linux-amd64
./scripts/provision-imagemagick /path/to/ImageMagick-7.1.2-30-gcc-x86_64.AppImage
./scripts/acceptance capabilities/evidence
```

Provisioning is deliberately offline. The operator supplies each release
asset locally; none of these commands downloads it. For qpdf,
`scripts/provision-qpdf` verifies the official 12.4.0 Linux x86-64 archive at SHA-256
`a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`
and verifies the extracted `bin/qpdf` SHA-256
`9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b`
before placing it in the ignored `.build-cache/qpdf/12.4.0` directory.
`scripts/qpdf-pin.properties` is the single operational pin authority and
also fixes the repository wrapper path consumed by the canonical command.

The PDFium renderer is the MIT-licensed pdfium-cli v0.11.2 WebAssembly Linux
x86-64 release containing BSD-3-Clause PDFium Chromium build 7881. Its direct
executable release asset has both distribution and executable SHA-256
`3ef3375c429ce665e834f933a028225bf28ac837695aaa69c6fc21facf6780ab`.
The ImageMagick 7.1.2-30 GCC x86-64 AppImage is under the ImageMagick License;
its direct executable release asset has both distribution and executable
SHA-256
`372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e`.
`scripts/pdfium-pin.properties` and `scripts/imagemagick-pin.properties` are
their operational authorities and pair each distribution identity with its
repository wrapper. The exact linked modules and bundled components are
recorded in the pinned
[PDFium](../docs/third-party/pdfium-cli-v0.11.2.md) and
[ImageMagick](../docs/third-party/imagemagick-7.1.2-30-appimage.md) notice
manifests. The provisioners verify the supplied asset, reported version, and
executable hash before placing it under the ignored `.build-cache/` tree. The
wrappers recheck digest markers and executable hashes and never search `PATH`
for a fallback.

The normal build, published artifacts, and inventory commands neither
download nor require qpdf, PDFium, or ImageMagick. Missing, unreadable,
incorrectly versioned, or digest-unmarked tools record the applicable chain as
`indeterminate`, never `pass`.

The acceptance command creates the blank PDF through
`DocumentWorkflow.execute`, computes its ID-neutral input SHA-256, runs
`qpdf --check` on the unmodified artifact, and then reopens that same artifact
through the public workflow to assert the committed one-page sequence and
readable object graph; text order is not applicable because the profile emits
no text. The input hash replaces only the two hexadecimal trailer `/ID` values
with ASCII zeroes before hashing. This preserves reproducible evidence
metadata while honoring issue #1's explicit exclusion of byte-identical PDF
output; every other PDF byte remains hash-significant, and the file handed to
qpdf and PDFium is never normalized or rewritten. A repeat-run command test
requires the T06/T07 records and raw findings to reproduce exactly. The command
also drives the T10 page/merge/split, T11 metadata, T12 annotation/Action,
T13 text/structure, T14 image/resource, T15 incremental-publication, and T16
version/password-security
profiles to create paired
project-owned products and runs the same pinned qpdf syntax check on each.
The T13 producer also evaluates the
finished in-Session state through `ExtractTextAndStructure` before publication;
its tagged product includes MarkInfo, page StructParents, a ParentTree, and
bidirectional structure-parent links. The T14 producer similarly evaluates a
filtered image and subset font plus a
nested Form and soft mask through `ExtractImagesAndResources`. The T15 pair is
one public-workflow original and its two-page appended revision; the producer
requires exact original-prefix retention, a non-empty suffix, a committed
receipt, and public reopen before qpdf sees both unchanged files. These
implementation probes are not recorded as independent semantic chains.
The T16 pair is a public-workflow PDF 1.7 ADBE Extension Level 8 product and a
PDF 2.0 product, both using the secure V=5/R=6 AESV3 default. The producer
reopens each with user and owner credentials before qpdf runs `--check` plus
`--show-encryption`. qpdf receives the user credential through a temporary
password file that is deleted after inspection; the recorded invocation hides
its path and password-valued tool output is replaced with `<redacted>`.
The T17 producer appends two Canvas Programs to a project-authored page and
retains the unchanged result as one artifact covering lines, a cubic curve,
stroke, both fill and clip winding rules, an affine transform, nested graphics
state, all eight text rendering modes, explicit glyph matrices, Font resource
reuse, and preservation of existing content and resources. Pinned qpdf checks
that unchanged artifact for syntax. A distinct project-test producer reopens
it through public Folio PDF queries and compares project-owned semantic
expectations without using PDFBox as an oracle.
The T18 producer appends a version-2 Canvas Program to project-authored
existing content and resources. Its one unchanged artifact covers JPEG, PNG
alpha, single-image TIFF, raw and borrowed existing images; Device,
calibrated, and ICCBased fill/stroke color; explicit and soft masks; alpha,
Multiply blend mode, reusable isolated Transparency Groups, resource reuse,
and preservation. Pinned qpdf checks syntax, a distinct project-test producer
reopens it through T09/T14 public queries, and PDFium/ImageMagick supplies the
independent visual chain. The optional TwelveMonkeys TIFF provider is a direct
repository-acceptance runtime so this artifact always exercises the available
codec path.
The command records tool identity, version and distribution digest, exact or
documented ID-neutral input SHA-256 values, raw findings, chain-level results,
and the applicable determination beneath the requested
output directory. qpdf is syntax evidence only: its success is not a PDF
standards-conformance result and cannot satisfy the independent standards,
semantic, or visual chains.

For ordinary rewrite products, the ID-neutral policy requires exactly one
two-value trailer `/ID`. T15 may contain an `/ID` in more than one revision, so
its revision-aware policy replaces every hexadecimal two-value trailer `/ID`
with equal-length ASCII zeroes before hashing. Every other byte remains
hash-significant, and normalization is never applied to a qpdf input.

AES-256 authentication entries, identifiers, and ciphertext are intentionally
randomized, so T16 records a reproducible SHA-256 of the non-secret public
version, handler, scope, permission, and page-count observation instead of
claiming byte-identical encrypted output. The encrypted files handed to qpdf
are not normalized or rewritten. This observation hash is an input-identity
policy for the syntax run, not independent semantic or cryptographic proof.

For T07, PDFium receives that exact workflow-produced PDF and renders its one
effective MediaBox page at 144 DPI into an opaque sRGB RGB PNG of exactly
`1224x1584` pixels. The project-owned profile at
`capabilities/profiles/T03-document-blank-visual.properties` fixes the page
box, color, font and antialiasing policies, dimensions, expected-raster hash,
ImageMagick AE metric with fuzz `0%`, and zero-pixel capability and renderer-
agreement thresholds. The expected raster is a project-defined all-white
image; it is not Reference Suite output.

With the pinned ImageMagick asset provisioned, its exact bytes reproduce with:

```text
./scripts/container-bin/imagemagick -size 1224x1584 xc:'#ffffff' -colorspace sRGB -alpha off -type TrueColor -depth 8 -strip PNG24:capabilities/expected/T03-document-blank-144dpi-srgb.png
```

The resulting SHA-256 is
`c7bbf03603aee1dba4ef80c9eee9abb93b7f3adfb94b84e4abf0203d78f89011`.

T18 uses the same page box, 144 DPI, opaque sRGB RGB PNG dimensions, pinned
tools, and zero-fuzz AE metric. Its profile is
`capabilities/profiles/T18-canvas-images-colors-transparency-visual.properties`.
The project-owned, visually reviewed PDFium expected raster is
`capabilities/expected/T18-canvas-images-colors-transparency-144dpi-srgb.png`,
SHA-256
`4027a0a929494c49051a3039be5bd1c06d2a6624ba7c161acb8c1bfe0780024a`.
The authoritative PDFium comparison threshold is AE `0`. The secondary
PDFBox-renderer disagreement ceiling is AE `2500` (less than 0.13 percent of
the 1,938,816-pixel raster), accommodating bounded renderer color-management
and antialiasing variation while still forcing review above that fixed
capability-specific tolerance. ImageMagick HDRI decimal AE values are parsed
exactly rather than rounded.

The harness validates PNG structure, dimensions, color type, and decodability
before comparison. ImageMagick receives raster paths only and produces
reviewable red/white difference artifacts. A threshold mismatch is `fail`.
Apache PDFBox Renderer 3.0.8 supplies a secondary raster only to detect
implementation-renderer disagreement; disagreement is review-required and
`indeterminate`, never `pass`. Unexpected tool results, invalid metrics,
missing difference output, or unusable rasters are also `indeterminate`.

`./scripts/inventory check` validates the authorities and fails if either
generated file is missing or stale. The repository root command
`./mvnw -B -ntp verify` invokes that check through the internal
`pdf-inventory-tool` build module. The tool is skipped for Maven install and
deploy and is not part of the Native Interface, BOM, or published product
artifacts.

## Capability Matrix schema

`capability-matrix.yaml` is the behavioral authority. Its top level fixes
`schema-version`, `release-train`, and the literal authority
`behavioral-capability`. Each capability requires:

- a unique lowercase `id` and owning `context`;
- a summary and Reference Suite source/role that identify the inventory input
  without treating another implementation as an oracle;
- at least one Native Interface mapping;
- exact `stable` and `preview` facade-surface ID lists;
- limitations, Dependency Gates, promotion gates, and certified platforms;
- one uniquely identified Acceptance Profile, implementation `evidence`, independent
  `acceptance-evidence`, provenance, and one declared state.

Repository paths are relative, use forward slashes, cannot escape the
repository lexically or through symbolic links, and must name existing regular
files. Unknown schema fields, duplicate identifiers, and mismatched release
trains fail validation. The inventory Release Train must also equal the project
version declared by the repository root `pom.xml`.

Every Dependency Gate has `capability` and `required-status: compatible`.
Targets must exist, the dependency graph must be acyclic, and a `compatible`
or `limited` capability cannot pass while a required capability is not
`compatible`.

## Capability states and evidence

Every Acceptance Profile declares the mandatory independent evidence chains.
`syntax`, `standards`, `semantic`, and `visual` are always mandatory; `human`
is additionally mandatory where the profile requires human review. Each
recorded chain has one result (`pass`, `fail`, or `indeterminate`) and its own
repository evidence record plus a versioned producer `kind`, `name`, and
`version`. Syntax, standards, and visual chains require external-tool
producers; semantic chains require project-test producers; human chains
require human-review producers. Independent chains cannot share a producer
name or canonical record, and cannot reuse an implementation or profile file
through an alternate relative spelling or in-repository symbolic link.

Profile evidence records carry exact `Status`, `Capability`, and
`Acceptance Profile` and `Release train` metadata. Independent chain records
carry exact `Capability`, `Acceptance Profile`, `Profile record`,
`Release train`, `Chain`, `Result`, and producer metadata. Each metadata label
must occur exactly once. Validation rejects missing, duplicate, or conflicting
metadata and values that disagree with the Capability Matrix.

| State | Required evidence | Claims and gates |
| --- | --- | --- |
| `planned` | No implementation evidence, Acceptance Evidence, or evidence record. | No certified platform claim. Future mandatory chains are still declared. |
| `experimental` | At least one implementation evidence item and an evidence record. Acceptance chains may be absent, partial, failing, or indeterminate. | No certified platform claim. Open Dependency or promotion gates may remain. |
| `compatible` | Implementation evidence plus `pass` records for every mandatory Acceptance Evidence chain. Implementation tests alone are insufficient. | Every Dependency Gate is `compatible` and no promotion gate remains open. |
| `limited` | The same complete mandatory Acceptance Evidence required for `compatible`. | At least one explicit limitation, every Dependency Gate is `compatible`, and no promotion gate remains open. |

The Acceptance Profile `state` must equal the capability `status`. Promotion is
conservative: complete evidence permits a state but never automatically
changes it.

## Facade Surface Manifest schema

`facade-surface.yaml` is the source-surface authority. It fixes the same schema
version and Release Train and uses the literal authority
`migration-source-surface`. Each unique stable surface ID records:

- Reference Suite type and member descriptor;
- Folio PDF type and member mapping;
- generic and exception contracts;
- one or more behavioral capability IDs;
- stable or preview availability through its containing list.

Stable surfaces may reference only `compatible` capabilities. Preview surfaces
may reference only `compatible` or `experimental` capabilities. A surface
cannot appear in both lists.

Reference types must be below `com.itextpdf.*`. Their Folio PDF types must
preserve the exact suffix below `net.zerocloud.pdf.itext7.*`, so the declared
mapping remains eligible for mechanical import-prefix replacement.

The Capability Matrix facade ID lists and manifest references must agree
exactly. Every capability must either have at least one corresponding surface
or one explicit `excluded-capabilities` entry with a ticket and reason; it
cannot have both. This rule gives every generated capability entry an accurate
facade backlink while every generated surface or exclusion links to its
behavioral capability.

## Authority boundaries

The Capability Matrix answers whether behavior has the required evidence. The
Facade Surface Manifest answers which migration source shapes exist. A method
mapping without passing behavioral evidence is not compatible, and behavioral
coverage without a surface entry does not lower source-migration effort.
Generated Markdown is review material, not a third authority.

The T02 validator and generator evidence is recorded in
`capabilities/evidence/T02-inventory-authorities.md`.

The current T06 syntax and semantic, T07 visual, and overall records are
`capabilities/evidence/T06-document-blank-syntax.md`,
`capabilities/evidence/T06-document-blank-semantic.md`,
`capabilities/evidence/T07-document-blank-visual.md`, and
`capabilities/evidence/T06-document-blank-determination.md`. Syntax and
semantic pass for their shared artifact, and visual passes against that same
PDF. Standards evidence is absent, so the overall profile remains
`indeterminate` and the capability remains `experimental`.

The current T10 syntax record is
`capabilities/evidence/T10-page-manipulation-merge-split-syntax.md`. Its two
project-produced artifacts pass pinned qpdf 12.4.0 syntax checks. Standards,
semantic, and visual Acceptance Evidence remain absent, so the T10 capability
also remains `experimental`.

The T11 through T16 profiles likewise have one passing qpdf syntax record for
each pair of public-workflow products. Their mandatory standards, semantic,
and visual Acceptance Evidence chains remain absent. T17 has passing syntax
and project-owned semantic records, while its mandatory standards and visual
chains remain absent. T18 has passing syntax, project-owned semantic, and
independent visual records, while its mandatory standards chain remains
absent. Their Dependency Gates remain open while prerequisites are
`experimental`; none is promoted beyond `experimental`.

The T08 release-gate evidence is recorded in
`capabilities/evidence/T08-secure-maven-central-rehearsal.md`. T08 validates a
non-publishing Maven Central rehearsal and protected production staging
contract. It does not add a behavioral capability, Native Interface member, or
Migration Facade surface, so the Capability Matrix and Facade Surface
authorities remain unchanged.
