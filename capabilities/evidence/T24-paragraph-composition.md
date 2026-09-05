# T24 paragraph composition implementation and verification

Status: `experimental`

Capability: `composition.layout.paragraph-areas`

Acceptance Profile: `T24-paragraph-composition`

Release train: `0.1.0-SNAPSHOT`

This record tracks issue #25 against the fixed review base
`b3c79a9f3cf9d8ac79de90b59b143b0e621c9ea7`. Implementation and acceptance are
documented below; this is not a compatibility claim.

Before implementation, `main` resolved to that base with a clean worktree;
GitHub issue #25 was open with no comments on 2026-09-05.

The preimplementation command below passed on local GraalVM CE OpenJDK
17.0.9+9.1 (Linux), with 205 tests, zero failures, errors or skips:

```sh
./mvnw -B -ntp -pl pdf-document -am \
  -Dtest=FontLoadingWorkflowTest,CanvasWorkflowTest,CanvasImageColorTransparencyWorkflowTest,WorkflowExecutionProfileContractTest,RenderingWorkflowContractTest,HardenedWorkerRecoveryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The reported CI run 33945793004 had failures in
`RenderingWorkflowContractTest.defaultLogsKeepFontAndMissingResourceNamesPrivate`
and, on JDK 21, `HardenedWorkerRecoveryTest.workerTimeoutIsRecoverableBeforePublication`.
Both test classes passed in this local baseline. This local result does not
establish the other JDK gates or negate the historical CI failures.

## Public contract and independent acceptance

The public contract is documented in [paragraph-composition.md](../../docs/paragraph-composition.md).
`ParagraphCompositionWorkflowTest` executes the same contract in both profiles,
including mixed inline content across columns/pages, dimensions, margins,
area breaks, alignment, leading, width caps, repeated pinned fonts, publication
receipts, reopen geometry, signature/password restrictions and finite failures.

The representative artifact contains one paragraph spanning two explicit areas
on page 1 and the margin box on page 2. Both pages use MediaBox
`[0 0 612 792]`, 72-point margins and 40-point leading. The first page's area
coordinates, relative to its margin box, are `[0 480 72 560]` and
`[160 480 232 560]`. Text is 40 points; the inline blue graphic is 32 by 32
points. Input order is `AA AA `, the graphic, then `BΩ BΩ BΩ`.

The independent oracle in `T24ParagraphExpectations` uses the project fonts'
declared advances (A=24, B=26, space=10, omega=28 points), source top extents
(28 and 28.8 points), the two-line area capacity, and hand-calculated coordinates.
It never calls the paragraph implementation. The semantic observer checks every
character's source order, explicit Unicode mapping, matrix, advance and baseline
with an absolute `0.0001`-point tolerance, both page boxes, two embedded subset
fonts, and the transformed graphic box `[232 600 264 632]`. It reopens the actual
publication through public queries.

The separate reference PDF uses those hand-specified coordinates via T19 and
Canvas; it never invokes paragraph composition. The expected PNGs are the
pinned independent PDFium rendering of that reference, visually inspected on
2026-09-05. The fonts are intentionally simple project test outlines: A is a
triangle, B a rectangle, and omega a polygon. These are not substituted system
fonts. Expected and actual pages are 1224 by 1584 pixels at 144 DPI, opaque white
sRGB, with pinned PDFium smoothing. ImageMagick AE uses zero fuzz and a zero
difference threshold; secondary PDFBox renderer agreement has a 2,500 threshold.

- [qpdf syntax](T24-paragraph-composition-syntax.md): pass.
- [Independent project semantics and geometry](T24-paragraph-composition-semantic.md): pass.
- [Both independent visual comparisons](T24-paragraph-composition-visual.md): pass.
- [Page 1 profile](../profiles/T24-paragraph-composition-page-1-visual.properties).
- [Page 2 profile](../profiles/T24-paragraph-composition-page-2-visual.properties).

The two font hashes, expected declaration hash, input ID-neutral hash, tool
versions, thresholds, observations and expected/actual/difference raster hashes
are retained in these records and their `artifacts/` files. PDF bytes are never
normalized before validation or rendering; only evidence identity uses the
established trailer-ID-neutral hash.

## Open compatibility gates

The mandatory independent standards chain remains absent. Upstream capability
Dependency Gates are still experimental, and Foundation Noto/font-platform
certification is not provided by these small project fonts. There is no
`compatible` claim or Stable/Preview Migration Facade mapping. Inventory records
retain these gates regardless of implementation tests passing.

## Repository verification

The final public paragraph suite contains 42 cases across the two execution
profiles. It includes fractional exact-fit and nearby excess controls for
width, leading and explicit areas. The Worker codec contract accepts the new
paragraph outcome token and still rejects the first unassigned token.

| Gate | Result |
| --- | --- |
| T24 plus Canvas, font, Workflow, rendering and Worker recovery targeted suite | PASS; 245 tests at the font-preparation revision, followed by all 42 final T24 cases in the complete module run |
| Worker codec targeted regression | PASS; 11 tests |
| `./mvnw -B -ntp -pl pdf-acceptance -am test` | PASS; provider 7, document 659, acceptance 24 tests; zero failures/errors |
| `./scripts/acceptance /tmp/folio-t24-acceptance-final.v46o9nmb` | PASS; T24 syntax/semantic/visual PASS and both page comparisons AE 0 |
| `./scripts/inventory validate` | PASS; 18 capabilities, 12 facade surfaces, 17 exclusions |
| `./scripts/inventory generate` | PASS; both views generated by the tool |
| `./scripts/inventory check` | PASS; generated views current |
| `./mvnw -B -ntp verify` | PASS; all ten reactor modules, 724 tests, zero failures/errors |
| `./scripts/verify-jdk-matrix.sh` | PASS; all four complete JDK 8/11/17/21 builds, each 724 tests with zero failures/errors |
| Final fixed-base Standards/Spec review | PASS; both axes report zero unresolved findings across all 70 changed files |

The normal module run reports three pre-existing opt-in T22 scale tests as
skipped because `folio.pdf.t22.scale` is unset. No test selection or failure
suppression was added to the complete verification commands.

Canonical acceptance was generated outside the repository. All 21 T24 records
and artifacts reproduced the initially reviewed evidence exactly, except for
PDF trailer IDs under the documented ID-neutral identity policy. Only T24
outputs were copied back; existing evidence was preserved. Both full-page
PDFium rasters were visually inspected.

The matrix used already available Linux Eclipse Temurin container images:

| JDK | Image JAVA_VERSION | Local image ID |
| --- | --- | --- |
| 8 | `jdk8u502-b07` | `abe8c22c734bcff3c2e9ec5f8549f9aeb9a27aabc7b53974ddadcc903d30644a` |
| 11 | `jdk-11.0.32+9` | `e8dca93a5541cca084f88dceae31bdfdde0c15d1857c8f641f753c839538956f` |
| 17 | `jdk-17.0.20+8` | `20e832593c27816000db6315e1f554fcd0073c9ba95846ac117e5f3f5e0fea33` |
| 21 | `jdk-21.0.12+8` | `bd15556c8747f309a3d9ac0ca190d7a67dbb131275a41df51041af8460ca03b2` |

Each complete matrix run contains all 42 T24 profile cases, including the
fractional-fit controls, plus the public API/artifact and Worker protocol
contracts. The same three existing opt-in T22 scale cases are unselected.
The two historically reported CI failure methods passed in the local baseline,
full verify and each matrix run; no unrelated repair or failure suppression was
introduced for them. These are implementation verification results, not
Foundation font/platform or PDF standards certification.

The precommit review compared `git diff b3c79a9f3cf9d8ac79de90b59b143b0e621c9ea7`
and separately read every untracked file and binary artifact. At that point,
the branch remained at the fixed base with an empty index. The user subsequently
authorized a commit with DCO sign-off, a GitHub push, and closure of issue #25.


The following sections aggregate the two independent code-review axes.

## Standards

Documented-standard defects: **0**. Judgment-call smells: **0**.

All prior findings are resolved: fractional width and leading fits, explicit
area containment, and duplicated font preparation. No necessary correction
remains. The final review covers all 70 changed tracked/untracked files,
generated documentation and binary evidence against the fixed base. The
existing-test updates correctly synchronize the paragraph outcome token and
facade exclusion.

Verified receipts confirm full reactor and complete four-JDK success, including
all 42 paragraph cases per JDK. Inventory checks pass. All 21 canonical T24
outputs match retained evidence under the documented PDF identity policy;
syntax, semantic and visual chains pass, with AE 0 on both pages. Recorded
JDK image identities match local images. Compatibility remains experimental
with the stated standards and Foundation certification gates. At review time,
HEAD was unchanged and no staged changes or commits existed.

## Spec

Unresolved Spec findings: **0**.

The review covers all 70 working-tree files, including generated inventories,
untracked sources and binary evidence. The fractional-area finding is resolved.
No missing T24 requirement, incorrect implementation or scope creep remains.

Final receipts confirm full verify and all four JDK builds, the 690-test
acceptance-module/dependency run, all three T24 acceptance chains, both AE-0
page comparisons, canonical evidence reproduction and inventory checks.
Documentation, provenance, finite failure behavior, the public two-profile
contract, permissions, ownership and lifecycle requirements are covered.
Experimental status, missing standards evidence and open compatibility gates
remain explicit. At review time, HEAD was at the fixed base with an empty index.

Final finding counts: Standards **0** (no outstanding issue); Spec **0**
(no outstanding issue).
