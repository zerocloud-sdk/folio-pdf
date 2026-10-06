# Trusted in-process hostile-input certification

Issue #81 owns `document.hostile-input-limits`, profile
`T20-hostile-input-limits`, and Foundation obligation `limits`. The normative
policy remains [the hostile-input guide](hostile-input-policy.md) and the public
`WorkflowResourcePolicy` contract. This certification preserves the existing
Native implementation and #80 password scopes.

The [closed inventory](../capabilities/profiles/T20-hostile-input/coverage.json)
maps all ten dimensions, their phase applicability, 75 fixed experiments,
existing public regression methods and the corresponding Facade observations.
The operands are authored independently by
`scripts/generate-t20-corpus.py`: three indirect objects and one page, a
trailer/reference/Catalog/seven-array depth of ten, known AHx/RunLength,
ASCII85, AHx/Flate and AHx/LZW stage lengths, six materializable pixels and
four returned XMP bytes. Input and snapshot bounds use independently authored
file lengths. No quota discovery or implementation parser generates an
acceptance expectation.

Clock, one-byte stream and latch controls distinguish inclusive elapsed
equality from one nanosecond excess, expired deadline equality, backwards time,
mid-read cancellation, incoming and active concurrency ceilings and permit
release. Source forms, request precedence, stricter local failures, repeated
decoding, supported stream-dictionary changes, retained and working memory,
terminal poisoning, expired Session views and storage cleanup are observed
through `DocumentWorkflow.execute`. The public regression suite supplies the
broader Patch/content scope, Canvas, numeric, ownership and publication cases.
The same candidate's established password recorders retain credential-copying,
destruction and cleanup observations.

Temporary-storage equality and first excess are fixed snapshot experiments.
Publication additionally checks staged/adjacent file lifetimes and cumulative
high-water usage using the independently authored uncompressed Source and an
incremental blank product. The stream peak must equal the 354-byte snapshot
plus the emitted file length; the Path peak must equal that snapshot plus two
live files. This controlled case has no stream-cache allocation. An injected
Clock cancels after observing nonempty adjacent staging and before commitment.
Every experiment inspects both the owned storage root and all Target directories
after termination. Other workflows still charge cache-page capacity; this
case does not generalize file size into a bound on arbitrary cache usage.
Pre-commit Paths remain intact and ordered committed, failed and unattempted
receipts retain their actual partial-output observations.
Caller streams/channels/outputs remain open. Resource failure stays terminal
when caught, and malformed/unsupported operands retain their owning semantics.

The five chains are separate. The contract chain owns enforcement predicates.
Every successful published positive is deliberately a resource-free, one-page
PDF 1.7 outcome: Native first/second and stream high-water Targets,
the earlier committed Target of a failed workflow, Facade
stream/earlier-commit outcomes and the two inherited
T03 baseline outcomes. Their syntax, standards, semantic and visual chains explicitly
reuse the qualified T03 profile, pinned qpdf, pdfcpu, Arlington, PDFium and
ImageMagick tools, original rule-specific standards controls, two-page semantic
control and one-pixel visual control. The visual policy remains 144 DPI, opaque
sRGB, white background, AE zero and zero fuzz. Read-only hostile operands do not
promote additional PDF feature capabilities.
Successful split products retain their qualified page/content/box/rotation
evidence through the required same-candidate T10 predecessor refresh.

`T20EvidenceCommand` records original findings and exact input/tool identities
before and after recording. Collection verifies the closed inventory and
retained manifest, checks the staged source/artifact/harness identities and
executes a fresh recorder in the exact pinned Ubuntu image. Original and live
PDFs must match after the existing documented trailer-ID normalization; exact
original hashes remain retained. Every original raster is compared to its live
counterpart with the pinned external comparator. Original text findings and
public observations must match the replay after replacing only observation
paths and verified PDF/raster hash differences. Missing tools, rules, records,
controls, identities or altered findings cannot produce PASS. Foundation
collection also requires all five producer-bound chain records.
The original and live public/artifact suites use `T20ContractTestCommand`:
all 133 tests must execute with zero failures, ignored tests and assumption
skips. A plain passing JUnit count cannot establish this requirement.

The required scope is exactly Ubuntu 24.04 / Linux x86-64 with the declared
Temurin JDK 8, 11, 17 and 21 images, Native `IN_PROCESS` and actual Facade
`IN_PROCESS`. Windows x86-64 and macOS x86-64/arm64 remain uncertified and are
not Foundation 0.1.0 blockers. The existing Native-only policy-control mapping
decision remains authoritative; there is no Reference Suite resource-policy
control to map. Underlying document operations keep their Facade obligations.

This is cooperative enforcement of modeled Folio usage. It establishes no
JVM heap, process RSS, arbitrary callback/backend/native termination or
sandbox guarantee. Worker isolation, recovery and scale certification remain
separate obligations.

Use the existing local route, with the complete explicit HarfBuzz installation,
the pinned Ubuntu Python runtime and writable Maven cache selected:

```sh
python3 -B scripts/t03-foundation.py stage
python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T81-final/limits --obligation limits
python3 -B scripts/t03-foundation.py collect capabilities/evidence/foundation/T81-final/limits/jdk17-in_process/observations --obligation limits --execution-profile IN_PROCESS
```

Staging is unsigned and local. Refresh predecessor evidence in declared order
before recording `limits`: transactions, values, pages, metadata, annotations,
text, images, incremental, password-baseline, password-clear-metadata and
password-attachments. Changed source, contracts, profiles, harness, tools or
artifacts require restaging and fresh affected observations. Promotion follows
complete evidence and satisfied dependencies; changes caused by promotion
require the same honest refresh. Overall Foundation readiness retains concrete
later blockers. This route performs no push, PR, tracker edit or release
publication.
