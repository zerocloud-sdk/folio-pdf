# Explicit HarfBuzz shaping

T29 (#30) adds the experimental `composition.shaping.harf-buzz` capability to
the existing Composition commands. The project owns the C adapter and its
Java Provider. HarfBuzz **10.2.0** is the fixed acceptance engine; the caller
installs the native library and helper separately. Neither is bundled in the
default runtime, and there is no unofficial Java HarfBuzz wrapper.

The four [acceptance profiles](../capabilities/profiles/T29-shaping-reference.md)
cover Arabic, Hebrew, Devanagari and Thai with explicit, hash-pinned fonts.
Local tests or an installed executable alone do not establish compatibility.
For Foundation 0.1.0, [ADR-0040](adr/0040-certify-only-observed-foundation-environments.md)
requires the actual Ubuntu 24.04/Linux x86-64 JDK 8/11/17/21 environments.
Windows x86-64 and macOS x86-64/arm64 remain uncertified and are not required
release gates. Missing required Ubuntu/JDK observations or tools remains
`INDETERMINATE`; the [readiness report](generated/foundation-readiness.md)
tracks the exact boundary and remaining obligations.

## Explicit installation

Supply the official source archive pinned in
[`scripts/harfbuzz-pin.properties`](../scripts/harfbuzz-pin.properties) and a
new installation directory. The repository-owned installer requires Python
3.12+, Meson 1.3.2+, Ninja, pkg-config and native C/C++ compilers on PATH:

```sh
python3 scripts/install-harfbuzz.py /explicit/harfbuzz-10.2.0.tar.xz \
  /explicit/folio-harfbuzz-10.2.0
```

The command verifies the archive hash, builds the unchanged upstream source
and project adapter, and writes `installation.json` with source, helper,
installed library, compiler and build-tool hashes plus the actual build
commands, effective build options, verbose compiler/linker output and resolved
linker hashes. It refuses an existing installation path and removes its own
incomplete installation after a build failure. The installer clears inherited
`DESTDIR` and selects the recorded PATH executables for Ninja and pkg-config;
compiler flags are retained in the receipt. No download occurs. The
installed helper is `bin/folio-harfbuzz` (`bin/folio-harfbuzz.exe` on Windows).
Linux and macOS helper lookup uses the adjacent `lib` directory; Windows uses
the adjacent engine DLL in `bin`. Actual platform execution remains required.

The receipt's `installed` status describes a build. It is not a passing T29
acceptance result. Compiler prerequisites, licenses and the independent
`hb-shape` oracle installation are recorded in
[the native dependency notice](third-party/harfbuzz-10.2.0.md).

For repository validation, export the absolute installed helper path. The
Maven test profile consumes this explicit environment variable; a command-line
`-Dfolio.harfBuzzHelper=/absolute/helper` can also select the test installation.
The JDK matrix mounts the entire installer-created directory read-only so the
helper keeps its adjacent shared engine in each Linux container:

```sh
export FOLIO_HARFBUZZ_HELPER=/explicit/folio-harfbuzz-10.2.0/bin/folio-harfbuzz
./mvnw -B -ntp verify
./scripts/verify-jdk-matrix.sh
```

Missing native prerequisites fail the required native tests. They are not
silently skipped. The build profile adds no native installation or discovery
to application runtime behavior.

After provisioning the pinned qpdf, PDFium and ImageMagick tools described in
the [acceptance guide](../capabilities/README.md), select a Python environment
with fontTools 4.59.2 and run the public evidence entry point:

```sh
export FOLIO_SHAPING_PYTHON=/absolute/python-with-fonttools-4.59.2
./scripts/acceptance /new/evidence-directory
```

The entry records five T29 behavior chains: native metrics, reopened semantic
geometry, embedded subsets, syntax and raster comparison, plus a separate
installation traceability record. Missing prerequisites produce
`INDETERMINATE` records. The separate CLI integration test runs this same
entry and requires the two explicit executable environment variables above:

```sh
python3 -m unittest discover -s scripts/tests -p test_t29_acceptance_entry.py
```

For a T29 platform run, the dedicated reactor profile records the same T29
chains without invoking the earlier suites:

```sh
./mvnw -B -ntp -pl pdf-acceptance -am -Pacceptance-t29-record \
  -DskipTests -Dacceptance.output=/new/t29-evidence verify
```

On Windows, use `mvnw.cmd` and that host's absolute helper, Python and output
paths. This entry has been executed on Linux only. It keeps the current
reactor modules together, so it does not depend on separately installed
SNAPSHOT POMs. `-DskipTests` selects evidence recording; it does not replace
the required Maven test or JDK matrix gates. Other-platform tool and native
observations remain required even when their Worker mode is not applicable.

The traceability command can also be invoked directly:

```sh
"$FOLIO_SHAPING_PYTHON" scripts/t29-native-observation.py "$FOLIO_HARFBUZZ_HELPER"
```

It emits JSON and exits 0 for PASS, 1 for a mismatch, or 2 for INDETERMINATE.
It verifies installer artifacts and, on Linux, records an actual separate
helper startup's loaded files through `/proc/PID/maps`. This is a snapshot in
the inherited loader environment, not an observation of every Workflow call.
Ambiguous `LD_PRELOAD`/`LD_AUDIT` interposition remains INDETERMINATE. Live
observation on other platforms is currently unimplemented; those targets
remain uncertified and are outside Foundation 0.1.0's required release gates.

## Selection and input

Register `HarfBuzzCapabilityProvider` from `pdf-conversion` in the
`WorkflowEnvironment`, then select it on the `WorkflowRequest`:

```java
HarfBuzzCapabilityProvider provider = new HarfBuzzCapabilityProvider(
        helperAbsolutePath, existingStagingRoot, "10.2.0",
        ProviderLimits.bounded(4L << 20, 32L + 24L * 4096,
                Duration.ofSeconds(10)));
WorkflowEnvironment environment = WorkflowEnvironment.builder()
        .provider(provider).build();
ProviderPreference preference = ProviderPreference.prefer(
        ShapingRequest.CAPABILITY_ID, HarfBuzzCapabilityProvider.PROVIDER_ID);
// Add .providerPreference(preference) to the existing WorkflowRequest builder.
DocumentWorkflow workflow = new DocumentWorkflow(environment);
```

Registration alone does not enable Composition shaping. An explicit request
preference, including `ProviderPreference.any` for this capability, enables it
for that transaction's Composition commands. Selection uses the existing
Provider eligibility and remote-disclosure rules and appears in the Workflow
Outcome. There is no native installation discovery, system-font lookup,
runtime font download, or implicit network access.

Continue to declare `FontSelection.explicit(...)` in fallback order, finite
`FontLimits`, and the existing paragraph/table limits. Font source ownership,
staging, embedding permissions and publication-version checks apply before
painting. Every complete ICU extended grapheme selects the first declared
font that covers all its participating scalars. Combining marks and their
base therefore share a font. If no single declared font covers the cluster,
the existing missing-glyph failure applies; scalar coverage spread over
several fonts is insufficient for the shaped path.
Every scalar visit used to check that shared font consumes the existing
fallback-check budget, in addition to the initial scalar-selection visits.

## Shaping and layout

ICU4J 77.1 still supplies paragraph bidi analysis, extended graphemes and
Unicode line opportunities. The native adapter supplies OpenType substitution
and positioning. It explicitly uses the `ot` shaper and OpenType font
functions, default features, monotone grapheme clusters and a scale equal to
the font's units per em. Each run has an explicit horizontal direction,
script and language. Composition uses `ar`, `he`, `hi` and `th` for the four
profile scripts and `und` for other scripts.

Runs split at font, script, font-size and resolved bidi-level boundaries.
Adjacent text inlines still participate in one ICU analysis. A font-size
boundary inside a grapheme preserves both declared sizes and the outer
grapheme's indivisible layout boundary, but native shaping happens separately
on each side of that style boundary. Cross-style mark attachment is not
promised: an isolated Devanagari mark run can receive HarfBuzz's dotted-circle
glyph. A same-size base and mark can be shaped together.

Whole-run shaping gives an initial width estimate. Every proposed line
fragment is reshaped and refitted with its actual boundaries and line bidi
levels. WRAP prefers Unicode opportunities and can fall back to original ICU
grapheme boundaries, including boundaries inside a previously formed native
ligature. It never splits an original extended grapheme. REJECT, VISIBLE,
atomic graphics and tab fields retain their existing layout contracts.

For example, the pinned 12-point Noto Sans Arabic input `بببب` at width 17
produces two independently joined pairs, each advancing 16.344 points. At
width 12 it produces four isolated glyphs, each advancing 11.916 points.
Noto Sans `ffi` at width 6 reshapes into three lines instead of retaining an
overwide whole-run ligature. Table intrinsic minima consider legal partitions
at original grapheme boundaries, reshaping each candidate fragment. This
accounts for both wider isolated forms and narrower joined forms: the Arabic
`لا` ligature advances 6.984 points and fits a 7-point column even though
isolated `ل` advances 8.340 points. Actual line fitting still validates the
resolved FIXED/AUTO columns. Preferred width describes the unbroken input runs.

This profile uses admitted static TrueType outlines and horizontal writing.
It introduces no public feature override, per-run language selector, variable
font instance, vertical writing, automatic hyphenation or runtime font
discovery. Existing missing-scalar and unsupported-font checks still apply.

## Published fonts and text observations

Painting uses native glyph IDs, advances and offsets. A source glyph need not
have its own cmap entry: ligatures, reordered forms, marks and inserted glyphs
can enter the embedded program directly. Subsetting includes the transitive
composite dependencies of those glyphs and preserves embedding restrictions.
The admitted TrueType composite profile includes axis reflections; fractional
scaling, shear and unsupported attachment forms remain subject to font
preflight rejection.

PDF character codes, source glyph IDs and embedded subset glyph IDs are
distinct identifiers. A CIDToGID map connects painted codes to the actual
embedded glyphs. Separate codes may refer to the same source glyph when its
advance or logical cluster mapping differs. Subset prefixes contain six
uppercase letters and are deterministic for the source and selected glyphs,
with collision checks against other subsets and existing document names.

Each painted code has a ToUnicode mapping to its complete logical input
cluster. Several glyphs can share that mapping. ActualText records the logical
input of each native run. `ExtractTextAndStructure` exposes individual items in
painting order and observes ActualText for aggregate text. This preserves
logical text within a run; it does not reconstruct a whole paragraph's logical
order across reordered bidi runs, restore nonpainting controls or separators,
or add Tagged PDF structure.

Marked-content ActualText requires an effective PDF version of at least 1.5.
Shaped Composition rejects an older incremental Source with
`PDF_VERSION_UNSUPPORTED` before publication, including BMP-only fonts and
text. It does not upgrade the Source version implicitly. This follows
[ISO 32000-1 §14.9.4](https://opensource.adobe.com/dc-acrobat-sdk-docs/standards/pdfstandards/pdf/PDF32000_2008.pdf).

Direct `DrawPositionedUnicodeText` keeps its existing unshaped, scalar-order
contract even when a shaping preference is present. Composition without a
shaping preference also keeps the T28 unshaped behavior. Opting into shaping
can change glyph counts, advances, line/page breaks, extracted mappings and
generated bytes without changing paragraph or table command versions.
Migration Facade callers must use this Native Interface selection explicitly:
the current Preview `layout.Document` exposes closing behavior only and does
not map shaping. T29 has an explicit Facade Surface exclusion and introduces
no stable or preview shaping stub.

## Limits, ownership and Worker execution

The existing Provider limits bound each request, result and elapsed execution.
The request includes the complete explicit font program; input limits must
accommodate it as well as the text and framing. Composition derives a native
glyph ceiling from the registered output-byte limit. Native glyph-count checks
occur after shaping and do not bound all intermediate native allocation.

Workflow code models request/result copies, native-result metadata, candidate
atoms and retained line plans. Candidate lines retain their own reservations
until a containing layout chooses them. Rejected lines and table candidates
release their shaping data; selected plans retain it through painting.
Temporary intrinsic-width measurements release their own reservations.
Initial whole-run measurements belong to the current layout or table emission,
so completed and replaced layouts release them even when a buffered flow
continues to retain its font snapshot for later relayout.
Table shaping minima use a finite dynamic program over the original grapheme
boundaries between forced separators. It minimizes the largest advance of a
fragment partition and prunes candidates that cannot improve the current
bound. In the worst case it considers a quadratic number of boundary pairs;
both candidate visits and measured fragment lengths consume the existing
table layout-work budget. Exhausting that budget remains a table-limit
failure before publication.
Font/code-point, fallback, line, generated-content, cancellation and overall
deadline limits continue to apply. These counters measure modeled
project-owned memory rather than JVM allocation, RSS or native heap usage.

On the [supported Hardened Worker platforms](hardened-worker.md), the Worker
receives the validated Composition declarations and performs layout. A closed
shaping request/result exchange asks the parent to invoke the selected
Provider. The real native helper therefore executes in the parent-side
subprocess boundary in both Workflow modes. It is **not** fully contained by
the PDF Worker's filesystem, network, memory or CPU restrictions. The existing
subprocess deadline, byte limits, stream cleanup, direct-child termination and
private staging cleanup apply. Worker support remains limited to its existing
Linux/JDK envelope. Foundation 0.1.0 requires actual native and both Workflow
observations on every declared Ubuntu/JDK profile. Other platforms retain an
explicit Worker applicability record if separately investigated.
The T29 recorder writes that applicability into every chain. On Linux, an
unavailable Worker keeps the native and IN_PROCESS observations but leaves
the combined product chains `INDETERMINATE`, with `WORKER_UNAVAILABLE` and the
actual completed mode coverage recorded. It never substitutes IN_PROCESS
success for a missing required Worker run.

An unavailable helper or wrong engine version fails through the existing
Provider failure catalog. Malformed results, missing glyphs, unsupported input
and limits fail before publication. Targets remain unchanged and publication
receipts remain `NOT_ATTEMPTED` for those transaction failures.

## Native request and result format

The helper uses the existing [version-1 subprocess envelope](capability-providers.md).
The capability payload consists of big-endian integers and bytes. `HRQ1`
contains, in order:

| Field | Encoding |
| --- | --- |
| Magic | 32-bit `0x48525131` |
| Font length, text length, glyph ceiling, direction | Four 32-bit integers; text length counts UTF-16 code units; direction 0=LTR, 1=RTL |
| Script | Four ASCII letters |
| Language length and language | 32-bit length followed by 1–63 ASCII letters/digits in nonempty hyphen-separated groups |
| Font | Exactly the declared font bytes |
| Text | Exactly the declared number of big-endian UTF-16 code units, with valid surrogate pairs |

The absolute payload ceiling is 64 MiB; text and glyph ceilings are each
1,048,576. Empty font/text, extra bytes, malformed encodings and invalid fields
are rejected. The helper shapes face index zero; Composition supplies an
admitted single-face font program.

`HRS1` has a 32-byte header: magic `0x48525331`, status, engine major/minor/micro,
units per em, direction and glyph count, each a 32-bit integer. Each following
24-byte glyph contains source GID, UTF-16 cluster start, x/y advance and x/y
offset. Advances and offsets are signed font units. Cluster ends are the next
greater start or input length; multiple glyphs may share a range.

Status 0 is success, 1 is invalid input/font, 2 is glyph-limit exhaustion,
3 is a compiled/runtime version mismatch, and 4 is a shaping/allocation
failure. A failure carries no glyph entries. Java validates exact length,
direction, engine version, units per em, nonzero source glyph IDs and monotone
scalar-boundary clusters before Composition additionally checks internal
cluster boundaries against ICU graphemes. The installed helper checks the linked
runtime version against the headers used to build it on every request.

The independent reference uses official `hb-shape`, fontTools, qpdf and pinned
PDFium/ImageMagick tools. Native observations compare original numeric GIDs and
cluster/position data; reopened observations compare text and geometry; the
independent subset verifier follows actual painted mappings and compares
embedded outlines and components with the pinned source fonts. These are
separate evidence chains, and a missing chain is never a passing result.
