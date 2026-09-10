# T10 Foundation page certification contract

This contract was frozen before collecting T72 product observations. It defines
the profile and cannot supply PASS by itself. The Native Commands and adapted
Facade subset are specified in [page manipulation](page-manipulation.md). The
current [Foundation Evidence inventory](../capabilities/foundation-evidence.yaml)
is the authority that binds the actual candidate, contract, harness, tools, and
reports in all eight required Ubuntu 24.04 JDK/execution tuples. Facade execution
is recorded separately as `IN_PROCESS`.

## Original corpus and expected products

Three independently authored PDF 1.7 Sources contain pages `A B C`, `D`, and `E`.
The primary Source inherits resources and page geometry through a nested page
tree. Each Source uses a resource named `Tile` for a resource-free Form XObject:
red for the primary, green for the appendix, blue for the cover. Every page has
two ordered content streams, a background rectangle underneath the tile and a
black corner mark clipped by its effective CropBox. No font, network resource,
JavaScript or external-file stream participates.

| Page | MediaBox | Effective CropBox | Clockwise rotation | Raster at 144 DPI |
| --- | --- | --- | --- | --- |
| A | 0 0 200 160 | 10 20 190 140 | 90 | 240 × 360 |
| B | 0 0 240 180 | MediaBox fallback | 0 | 480 × 360 |
| C | 0 0 160 200 | 5 15 155 185 | 180 | 300 × 340 |
| D | 0 0 200 160 | 10 20 190 140 | 0 | 360 × 240 |
| E | 0 0 200 160 | 10 20 190 140 | 270 | 240 × 360 |
| blank | 0 0 612 792 | MediaBox fallback | 0 | 1224 × 1584 |

BleedBox, TrimBox and ArtBox are respectively inset 2, 4 and 6 points from each
nonblank page's effective CropBox. Existing basic Text annotations carry distinct
names, Contents, Rect, a page reference and the Hidden flag. Their data and
retargeting are checked; this profile makes no annotation-appearance claim.
All three Source name trees define `shared`: primary page B, appendix page D,
cover page E. This exercises actual destination collisions during ordered merge.

Each interface must produce these four required independently observed products:

- `edited`: start with A B C, insert blank at 2, move page 4 to 1, copy page 2
  before original position 4, remove page 1, then change only the copied A's
  background from yellow to cyan. Final pages are A, blank, copy-A, B. The
  original A retains yellow and its original content/resource bytes; the copy's
  annotation name is `note-A-1`. `shared` targets page 4.
- `merged`: append `cover` then `appendix`, independently of declaration order.
  Final pages are A B C E D. Destinations are `shared` → 2, `shared-1` → 4,
  `shared-2` → 5. The same resource name retains each Source's distinct painting.
- `left`: pages 1–3 of the merged sequence, A B C; only `shared` → 2 survives.
- `right`: pages 3–5 of the merged sequence, C E D; `shared-1` → 2 and
  `shared-2` → 3 survive.

The last three products are declared together and produced by one successful
split, including a complete-range `merged` product. Split selection order differs
from Target declaration order. Native outcomes and Facade receipts must retain
the declaration order. Public consumer tests separately exercise invalid
selection, unsupported preservation, terminal Commands, failure receipts,
Source isolation and sibling rewriting.

Every tuple runs 65 public contracts: 53 Native page workflows under the selected
actual execution profile, 10 Facade consumer cases in IN_PROCESS, and two
actual-jar contract classes. Stable must expose exactly the declared types and
members; Preview must expose the Stable superset plus its declared additions.
The current Preview addition set is empty. A classpath containing both artifacts
must reject in either jar order.

## Independent observations

Every required product retains separate syntax, standards, semantic and visual
records. qpdf supplies syntax only. The frozen standards union contains 84
qualified rules: 68 assigned to pdfcpu strict/offline and 16 assigned to the
acceptance-only Arlington T10 r1 checker. They cover the actual Catalog/page
tree, rectangle/rotation, stream/filter, resource/Form, annotation and
destination/name-tree structures. Each rule has an original illegal fixture and
a matching real checker diagnostic. Six Arlington r1 predicates cover destination
array length/page kind and name-tree byte ordering/uniqueness, including high-byte
and alternate-string-encoding controls. The source, model, patch, executable,
pin, rule profile, fixture and diagnostic identities are all retained. An
unimplemented checker predicate cannot qualify a rule.

Semantic observations reopen through the public Native Interface and verify the
ordered pages, effective and optional boxes, rotation, exact decoded content and
Form resource programs, annotation data/page references/identifier collision,
and destination names and retargeted page numbers. A wrong-order or unchanged-copy
control must fail the frozen product expectations.

Content expectations describe the complete ordered painting program. ISO Table
30 permits splitting or joining the Contents streams at token boundaries; neither
stream count nor separator whitespace identifies page content. The T10 programs
contain only the frozen ASCII names, numeric operands and painting operators, so
semantic comparison concatenates streams in order and normalizes PDF whitespace
between those tokens. It preserves every operand, operator, resource name and
their order. Source segments and expected pixels remain unchanged. A development
generation probe exposed the old recorder's array-only assumption before its
first successful product publication; the producer now changes only the one
declared background instruction inside either representation.

Expected images are authored from the geometry above before observing products.
They are opaque 8-bit sRGB pixel grids at 144 DPI with clockwise quarter-turn
rotation, CropBox clipping and fixed drawing order. Independent PDFium renders
every page; ImageMagick uses AE, zero fuzz, threshold 0. Secondary renderer
agreement also uses threshold 0. Neither expected pixels nor thresholds may be
derived from observed product output or relaxed to obtain PASS. A real one-pixel
control must fail. Source, image and profile identities are checked explicitly.

Missing tools, missing rule qualification, interrupted/uncertain execution,
incorrect hashes or wrong environment/execution identity cannot produce PASS.
Existing T03/T09 evidence must be refreshed for the resulting candidate; historical
records retain their original identities. Other Foundation obligations remain
blocking. No platform claim is added for Windows or macOS.

The real standards negative evidence is retained per product: both checker
directories contain all assigned illegal PDFs, invocations, unchanged input
hashes and raw diagnostics. The top-level `negative/result.properties` records
the standards control as `fail` only when every one of the 84 illegal controls is
detected; `fail` there is the required negative-control outcome, not a failed
positive product.

## Reproduce the candidate-bound records

Provision the pinned qpdf, pdfcpu, original Arlington, Arlington T10 r1, PDFium,
ImageMagick, private comparator libraries, immutable Podman images, and explicit
HarfBuzz helper described by the T03 tool instructions and repository pin files.
Then use one unchanged staged candidate for pages and the invalidated prerequisite
records:

```sh
export FOLIO_HARFBUZZ_HELPER=/absolute/installation/bin/folio-harfbuzz
python3 scripts/t03-foundation.py stage
python3 scripts/t03-foundation.py plan .build-cache/t72-pages-plan --obligation pages
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T72-pages --obligation pages
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T72-transactions --obligation transactions
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T72-values --obligation values
```

Each `certify` destination must be fresh. The runner verifies the unchanged build
receipt before and after every tuple, mounts repository inputs read-only with no
network, retains actual environment and tool observations, and atomically merges
only records for the same candidate and contract identities. It never signs,
uploads, publishes, or relabels historical evidence. After all three invocations,
`./scripts/inventory readiness` must still report NOT READY while unrelated
Foundation obligations remain open.

The normative source is [ISO 32000-1:2008, Adobe's public publication](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf),
including page inheritance and Table 30, content streams/resources, Form XObject
Table 95, name trees/destinations, and annotation Table 164. Fixtures and expected
images are original Apache-2.0 project test material, not Reference Suite assets.
