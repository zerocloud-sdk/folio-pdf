# T11 Foundation metadata certification contract

This profile is frozen before collecting T73 product observations. It covers
the Native metadata contract and the complete mapping set in
[metadata and navigation](metadata-navigation.md). Qualification and execution
records must prove this contract; this document cannot supply PASS.

## Independently authored expectations

Original PDF Sources contain primary pages A, B, C and appendix page D. Each
Source and product declares PDF 2.0, and Native and Facade publication explicitly
select PDF 2.0. This is required for the `AFRelationship` file-specification
entry under ISO 32000-2:2020, 7.11.3 and Table 43. Independent original-fixture
qualification identified the earlier PDF 1.7 declaration as invalid before any
product observation. The profile does not claim PDF/A conformance.
Each
page has MediaBox and CropBox `[0 0 120 100]`, rotation zero, no fonts, and a
single opaque axis-aligned rectangle at `[10 20 40 30]`: A red, B green, C blue,
D black. Expected rasters are independently authored 240 by 200 opaque sRGB
pixel grids at 144 DPI, with the rectangle at pixel columns 20 through 99 and
rows 100 through 159. Every other pixel is white. All page observations retain
the complete ordered content program and unrelated page/catalog data.

The primary carries Info Title `Source title`, Author `Source author`, and
custom text `T73Keep` containing `Preserved info`; an unrelated private Catalog
dictionary contains Flag true and Numbers `[1 2]`. ISO 32000-1 14.3.3 requires
custom Info entries to be text strings. Existing non-string unknown Info is
tested separately for safe preservation, without claiming it passes this
standards profile. This distinction follows the standard before any product
observations. XMP has
an unknown namespace and a private metadata-stream entry. Its name tree covers
all eight destination styles, including nullable XYZ/FitH/FitV operands and
an exact FitR rectangle, across A, B and C. It includes `shared` targeting B.
Its outline has named and explicit targets and a targetless parent with
children on different pages. Embedded `payload.txt` contains exactly ASCII
`abc` (SHA-256
`ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`,
MD5 `900150983cd24fb0d6963f7d28e17f72`) with `text/plain`, description
`Primary payload`, relationship Data. A second attachment named
`secondary.bin` contains bytes `00 01 ff`. The destination and attachment keys
use one self-consistent ASCII PDF-string encoding, and the corpus freezes their
worked encoded-byte order explicitly. High-byte ordering and equivalent-key
encodings are exercised by the independently qualified standards controls.
Exact packet, operand and secondary-file expectations belong to the checked-in
corpus before its first product run.

The appendix has `shared` targeting D, a named outline reference to it, and
`payload.txt` containing ASCII `hello` followed by LF (SHA-256
`5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03`,
MD5 `b1946ac92492d2347c6235b4d2611184`), MIME `text/plain`, description
`Appendix payload`, relationship Supplement.

Each interface produces four independently observed outputs:

- `edited`: update primary Info and the complete XMP packet, edit named and
  explicit navigation and replace the named payload without losing unrelated
  entries; move C to position 1, then copy A at position 2 to position 4.
  Final pages C A B A. Destinations to A/B/C resolve to 2/3/1 and retain their
  exact operands. Removing referenced B is rejected before mutation.
- `merged`: append D to the primary, retaining A B C D. The original `shared`
  targets page 2 and the renamed `shared-1` targets page 4, including its outline
  reference. The second colliding file is `payload.txt-1` with appendix bytes.
  Primary XMP wins and merge fills only missing Info entries.
- `left`: split merged pages 1–2, A B; only their destinations and outline
  targets survive. Both attachment payloads and all retained metadata survive.
- `right`: split merged pages 3–4, C D, retargeted to 1/2; filtered parent
  outline branches retain only surviving targets. Attachments and metadata
  are detached from their Sources and sibling products.

The merged/left/right products are published by one complete split Target
group, declared in a different order from split selection to prove receipt
order. Native and Facade products have identical semantic expectations.

## Independent chains and controls

Syntax uses pinned qpdf on each exact product. Standards uses qualified
pdfcpu strict/offline and Arlington predicates. Required rule assignments are
frozen in the T11 standards catalog before product observations, including
Info entry kinds, Metadata stream structure, outline links/counts/destinations,
all destination array shapes, encoded name-tree ordering and uniqueness, file
specifications, embedded streams and checksum/size/relationship fields. Every
required rule needs a real illegal control and a matching checker diagnostic.
A missing predicate requires a separately versioned, qualified checker change;
it cannot be accepted by changing the producer name or rule assignment.

Separate semantic records compare reopened observations to the fixed corpus:
Info, exact XMP and unknown content, outline shape, page identity and destination
parameters, attachment names/MIME/descriptions/relationships and exact payload
hashes. Controls with a wrong target, operand, packet, payload or retained value
must fail. Independent malicious/malformed XML, name-tree graph and attachment
limit profiles exercise both public interfaces; rejected XML must cause neither
external resource access nor partial mutation. An accepted XInclude-looking
element remains inert packet data and is never fetched.

Every Native XML safety run uses the execution profile selected by its
certification tuple. Under `HARDENED_WORKER`, each scenario also executes
forbidden file and outbound-network positive controls in the same Worker
Session after the candidate packet and confirms a distinct Worker process.
The evidence collector receives the certification tuple's selected execution
profile and requires it to match the Native root, every Native product, XML
safety and signed-Source records before assigning the tuple label. Every Facade
product and safety record must remain `IN_PROCESS`, and any missing per-scenario
Worker control is rejected.

Separate visual records use pinned PDFium and ImageMagick AE with zero fuzz and
threshold zero against the independently authored rasters above. Secondary
renderer agreement uses the same threshold. A real one-pixel change must fail.
Tools, fixed dimensions, color policy and expected images are hash-bound;
observed product output must never generate the expected pixels or thresholds.

All eight Native Ubuntu 24.04/Linux x86-64 JDK 8/11/17/21 ×
IN_PROCESS/HARDENED_WORKER tuples are required. Facade execution is separately
recorded as IN_PROCESS. The 64 MiB XMP command-bound test declares 512 MiB of
accounted owned memory so Worker transport staging can reach the unchanged
command limit; other policy fields retain the finite defaults. Default-policy
resource failures remain terminal and the stricter observed bound still wins.
Each certification binds the actual candidate, source
and contract hashes, immutable image, actual JDK build, execution settings,
qualified tools, unmodified reports and negative controls. Missing observations
or identity mismatches cannot produce PASS. Historical evidence is immutable.
Transactions, values and pages require refreshed evidence from their own
profiles for the same final candidate. Other Foundation obligations remain
blocking; Windows and macOS remain uncertified.

Normative sources are ISO 32000-2:2020 and the corresponding ISO 32000-1:2008
requirements, including 7.9.6 name trees, 7.11 file
specifications and embedded streams, 12.3 navigation and 14.3 metadata, and the
associated-files relationship vocabulary already supported by the Native
contract. The [PDF Association's published ISO 32000-2 corrections](https://pdf-issues.pdfa.org/32000-2-2020/clause07.html)
identify `AFRelationship` as a PDF 2.0 entry. All new fixtures are project-authored
Apache-2.0 material.

## Qualified standards tooling

The frozen catalog has 170 distinct rules. `pdfcpu.properties` assigns 31;
`arlington-core.properties` assigns 35 reused core/value/navigation rules;
`arlington-metadata.properties` assigns 104 original metadata rules. The two
Arlington groups preserve the recorder's existing 128-rule bound. Their union
must equal `required-rules.txt`, with no duplicate assignments. Every rule has
an immutable original illegal control and a specific expected diagnostic.
`capabilities/expected/T11-standards-findings.json` freezes the qualified
metadata diagnostics; the generator never probes tools or Folio products.

The pinned `0.81-folio-t11-r1` Arlington build extends the established T10 patch
with FitR numeric operands, name-tree bounds and cycle detection, outline links,
counts and named-target resolution, embedded-file size/MD5/MIME checks, required
Filespec Type, and well-formed XML. These predicates inspect the independent
PDFium object graph and decoded bytes. They consume no Folio observations or
expected-product data. Legal multilevel name trees, closed outline parents and
filtered attachments qualify the corresponding positive behavior.

XML checking statically links the existing Ubuntu 24.04 Expat 2.6.1 archive
(`libexpat1-dev:amd64=2.6.1-2ubuntu0.4`, MIT), hash-bound in the checker pin.
It installs no external entity reader, disables parameter-entity parsing and
returns unavailable evidence if the decoded XML exceeds its 64 MiB bound.
The runtime PDF artifacts acquire no Expat dependency. The existing Native
XML defenses are qualified separately through the public interfaces.

Recreate the checker from the exact Arlington archive and excluded PDFix paths
specified by the [original installation guide](third-party/t03-standards-tools.md),
after verifying the archive against `source-sha256` in
`scripts/t11-arlington-pin.properties`. Extract a fresh tree at
`.build-cache/arlington/t11-r1`. The qualified Ubuntu 24.04 build uses CMake
3.28.3, GCC 13.3.0 (`Ubuntu 13.3.0-6ubuntu2~24.04.1`), and the static Expat
archive at `/usr/lib/x86_64-linux-gnu/libexpat.a`. From the repository root,
apply the cumulative T11 patch directly to that fresh upstream tree and build
with the fixed source path, date, and time below:

```python
from pathlib import Path
import hashlib
import subprocess

root = Path('.build-cache/arlington/t11-r1').resolve()
patch = Path('build-tools/acceptance/arlington/t11-r1.patch').resolve()
expat = Path('/usr/lib/x86_64-linux-gnu/libexpat.a').resolve(strict=True)
expected_expat = '15ab8d77ac56aa64a286b2b6e1b94743339782f2747917166372d7147759d3cf'
if hashlib.sha256(expat.read_bytes()).hexdigest() != expected_expat:
    raise RuntimeError('The static Expat archive does not match the T11 pin')

subprocess.run(
    ['patch', '-d', str(root), '-p1', '-i', str(patch)], check=True)
flags = (f'-ffile-prefix-map={root}=/arlington '
         '-Wno-builtin-macro-redefined '
         '-D__DATE__=\'"Sep 10 2026"\' -D__TIME__=\'"00:00:00"\'')
subprocess.run(
    ['cmake', '-S', str(root / 'TestGrammar'),
     '-B', str(root / 'build'), '-DPDFSDK_PDFIUM=ON',
     '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_CXX_FLAGS=' + flags,
     '-DFOLIO_EXPAT_STATIC=' + str(expat)], check=True)
subprocess.run(
    ['cmake', '--build', str(root / 'build'), '-j', '4'], check=True)
```

Verify `TestGrammar/bin/linux/TestGrammar`, the applied patch, the immediate
`tsv/latest/*.tsv` model digest, and the static Expat archive against every
digest in the T11 pin before qualification. A different compiler, dependency,
flag, model, or binary requires a reviewed new pin and fresh qualification.

For a profile that explicitly declares PDF 2.0, the pdfcpu adapter retains its
complete PDF 2.0 informational notice and recognizes only that exact pinned
notice followed by strict validation success. PDF 1.7 profiles reject the
notice as unavailable evidence.
Qualification of all assigned negative controls is still required; the notice
supplies no coverage. Unexpected output, warnings, missing tools and mismatched
identities remain unavailable evidence.

For development, run the real checker qualification explicitly:

```sh
./mvnw -B -ntp -pl pdf-acceptance -am -Pindependent-certification \
  -Dtest=T11StandardsQualificationTest,StandardsEvidenceCommandTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s scripts/tests -p 'test_t11_*.py'
```

These commands qualify checker behavior against original Sources and controls.
They do not establish candidate-bound product certification.

## Candidate-bound reproduction

After every source, contract, fixture and generated-document change is final,
run the full repository verification and stage one unsigned local candidate.
The stage receipt hashes every candidate input, required artifact, contract and
test harness. Staging does not publish or sign an artifact.

```sh
export FOLIO_HARFBUZZ_HELPER=/absolute/qualified/10.2.0/bin/folio-harfbuzz
./mvnw -B -ntp verify
./scripts/inventory validate
./scripts/inventory generate
./scripts/inventory check
python3 scripts/t03-foundation.py stage
```

An optional plan records the exact eight container commands without executing
them. Every output directory below must be new and repository-relative.

```sh
python3 scripts/t03-foundation.py plan .build-cache/t73-r6-metadata-plan --obligation metadata
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T73-r6-metadata --obligation metadata
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T73-r6-transactions --obligation transactions
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T73-r6-values --obligation values
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T73-r6-pages --obligation pages
```

Each invocation records exactly eight Native certifications and merges them
atomically into `capabilities/foundation-evidence.yaml`, retaining the other
current obligations. The metadata semantic reports also retain the public
73-test Native, Stable and Experimental Migration Facade, actual-jar and classpath contract run,
plus XML and signed-document safety findings. Refreshing transactions, values
and pages on the same staged candidate is mandatory because their earlier
candidate hashes cannot establish readiness for a changed candidate. Finish
with `./scripts/inventory readiness`; metadata may be satisfied while unrelated
Foundation obligations keep the aggregate result `NOT READY`.

When inspecting a retained metadata tuple independently, bind collection to its
declared Native execution profile explicitly:

```sh
python3 scripts/t03-foundation.py collect \
  capabilities/evidence/foundation/T73-r6-metadata/jdk21-hardened_worker/observations \
  --obligation metadata --execution-profile HARDENED_WORKER
```
