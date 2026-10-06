# Issue #82 execution receipt

Completed local delivery for parent review. Source: [authoritative execution contract](execution-contract.md). Issue/spec snapshots and closed #81 dependency observation are retained in this directory.

Baseline and final HEAD: `df2df726a2695267bf63a7c9df49a4a7481f0169`. Branch: `main`. Entry was clean. No commit, push, PR, tracker change, release, production signing, deployment or publication-credential access. Final worktree contains only ticket changes; ignored caches and historical evidence are preserved.

Candidate identity: `6bddeb6c581c68067181d5164913df460a24332c8a3be98e5d3338bf47a67de2`.

Contract identity: `c0592466347f023e3ae02b432bb02684d1d47f45201285511bd26f9a7e052617`.

Frozen unsigned candidate: 2,868 source inputs, 32 contract inputs, 23 staged artifacts and 645 staged harness inputs. [Current archive](candidate-final-r2/README.md) retains source and artifact/contract/harness ZIP entries with SHA-256 manifests and ordered parts of at most 48 MiB. Concatenate each manifest’s parts in order, verify the whole-archive hash, then extract separately. All previous candidate attempts remain retained.

Main changes: repository-only T21 recorder, exact named-case runner and real-process observations; closed five-chain collector and live replay; four-tuple Foundation routing; meaningful adversarial collector and inventory-witness validation; test-only staged-JAR isolation helper; coordinated inventory/docs/provenance. Shipping Worker code, public APIs, runtime closure, first-party inventory hashes and shipping POMs are unchanged. [Changed-file ownership](changed-file-ownership.json) binds every reviewable ticket file; its [compressed TSV](changed-file-ownership.tsv.gz) records exact path, SHA-256, length and ownership.

| Criterion | Result | Observed behavior and evidence |
| --- | --- | --- |
| C1 | PASS | The 41 shared public cases execute in each actual mode on every JDK; all four original and fresh 137-case transcripts pass. Public records retain reopen, ownership, progress, safe failures and actual ordered receipts. [137 named cases](../../profiles/T21-hardened-worker/mandatory-tests.txt); [current Worker observations](../foundation/T82-final/worker/) |
| C2 | PASS | Exact named inventory; zero failures, ignored tests, assumptions or duplicates in every original and live replay. [137 named cases](../../profiles/T21-hardened-worker/mandatory-tests.txt); [transcript audit](final-audit.json) |
| C3 | PASS | Actual WorkerProtocolBoundaryTest framed experiments authenticate, reject hostile selectors/serialization/lengths and excess, and preserve sentinel products. [137 named cases](../../profiles/T21-hardened-worker/mandatory-tests.txt); [current Worker observations](../foundation/T82-final/worker/) |
| C4 | PASS | Shared public contracts and ordered-prefix/publication records prove declaration order, barriers, prefix visibility, first failure and unconsumed later inputs through both existing transports. [frozen coverage](../../profiles/T21-hardened-worker/coverage.json); [current Worker observations](../foundation/T82-final/worker/) |
| C5 | PASS | Actual separate child, effective roots, 0700 roots/0600 nonempty staged files, denied escape/link access/cross-transaction/descendant/INET and applicable Unix operations; paired permitted controls. Policy JVM and absent Java Unix API on 8/11 are explicitly qualified. [current Worker observations](../foundation/T82-final/worker/); [qualification](../../../docs/t21-certification.md) |
| C6 | PASS | Actual Java/runtime/vendor/build, prlimit hash/version, argv, cleared environment, installed Security Manager, closed production classpath/inventories, CPU/FD/JVM ceilings and actual elapsed termination; mandatory tests prove modeled aggregate admission. [current Worker observations](../foundation/T82-final/worker/); [resource scope](../../../docs/hardened-worker.md) |
| C7 | PASS | Cancellation/deadline/elapsed/crash/malformed records retain safe codes, actual ordered receipt names/status/partial flags, intact prepublication Targets, child termination before cleanup and no remaining owned child/data. [frozen coverage](../../profiles/T21-hardened-worker/coverage.json); [current Worker observations](../foundation/T82-final/worker/) |
| C8 | PASS | Shared named cases exercise cleanup failure, checked-primary precedence, caller exception identity, committed receipts, caller/module ownership and Session-view lifetime. [137 named cases](../../profiles/T21-hardened-worker/mandatory-tests.txt); [current Worker observations](../foundation/T82-final/worker/) |
| C9 | PASS | Actual unsupported-OS, missing-Java and disabled-prlimit controls require WORKER_UNAVAILABLE, actual unattempted receipts, unchanged Targets and empty owned storage; real prerequisites documented. [current Worker observations](../foundation/T82-final/worker/); [prerequisites](../../../docs/hardened-worker.md) |
| C10 | PASS | Actual public Native HARDENED_WORKER and existing public Facade IN_PROCESS observations retain matching document behavior/reopen/ownership/receipts; Worker controls remain justified Native-only. [frozen coverage](../../profiles/T21-hardened-worker/coverage.json); [Facade authority](../../../capabilities/facade-surface.yaml) |
| C11 | PASS | Exactly four Worker certifications, each with five separate passing qualified producer-bound chains, original raw findings and detected negatives. Independent qpdf/pdfcpu/Arlington/PDFium/ImageMagick profiles remain distinct. [current Worker observations](../foundation/T82-final/worker/); [chain audit](final-audit.json) |
| C12 | PASS | Frozen source/contracts/staged artifacts/harness and all observed environment/configuration/tool/input/transitive records retain their actual identities; live comparison and adversarial guards reject alteration/relabeling. [freeze](source-freeze-r3.json); [116 collector tests](validation/collector-suite-final-r2.txt); [staged closure](validation/stage-final-r2-closure.json) |
| C13 | PASS | 96 current certifications and 392 chain records; all thirteen selected/predecessor obligations SATISFIED; no global candidate/contract/evidence blocker. Remaining release blockers are unrelated. [live readiness](validation/inventory-readiness-final-r1.txt); [current index](../../../capabilities/foundation-evidence.yaml) |
| C14 | PASS | Only completed Worker capability/profile promoted to compatible; exactly the four actual Linux environments. Recovery/scale remains experimental; Windows/macOS remain uncertified. [Capability authority](../../../capabilities/capability-matrix.yaml); [Foundation authority](../../../capabilities/foundation-release.yaml) |
| C15 | PASS | Capability/Facade authorities, T21 and five-chain narratives, generated reports, English Worker/README, Chinese usage and provenance coordinated; exact 754/25 class inventories and six runtime dependencies documented. [Worker certification guide](../../../docs/t21-certification.md); [source ownership](changed-file-ownership.json) |
| C16 | PASS | 116 meaningful collector tests pass; mandatory coverage/skip/duplicate, altered findings/controls/products, producer and candidate/environment/configuration guards; final actual CLI collection passes. [116 collector tests](validation/collector-suite-final-r2.txt); [final CLI](validation/t21-cli-collect-final-r1.txt) |
| C17 | PASS | Focused public/real-process Worker, runner, T20/T21 collector, actual environment-observer and inventory witness/control checks retained, including diagnosed failed attempts. [validation records](validation/); [failed-attempt dispositions](failed-attempts.md) |
| C18 | PASS | Final full Ubuntu verify and inventory validate/generate/check pass. Live readiness exit 1 is exclusively unrelated obligations. Conditional JDK build matrix not triggered: shipping source and POM/build compatibility unchanged. All four actual T21 JDK tuples pass independently. [gate audit](final-audit.json); [live readiness](validation/inventory-readiness-final-r1.txt) |
| C19 | PASS | Baseline-relative Standards and Spec reviews with resolved earlier applicable findings. [Standards](reviews/standards-final.md); [Spec](reviews/spec-final.md); [source dispositions](reviews/source-dispositions.md) |
| C20 | PASS | This receipt, exact changed-file ownership manifest, entry/final audit, identities, commands/results, reviews, original failed attempts and unrelated blockers form the delivery. [final audit](final-audit.json); [ownership manifest](changed-file-ownership.tsv.gz) |

| Issue criterion | Contract mapping | Result |
| --- | --- | --- |
| AC1 | C1–C2 | PASS |
| AC2 | C3–C6 | PASS |
| AC3 | C7–C8 | PASS |
| AC4 | C9 | PASS |
| AC5 | C11–C14 | PASS |
| AC6 | C10 | PASS |
| AC7 | C15 | PASS |
| AC8 | C16–C19 | PASS |
| AC9 | C5–C6, C8–C10, C14–C15 | PASS |

All previously satisfied baseline obligations, including aggregate security, remain SATISFIED: `transactions, values, pages, metadata, annotations, text, images, incremental, security, limits, password-baseline, password-clear-metadata, password-attachments`.


Actual Worker scope is Ubuntu 24.04 / Linux x86-64. Each row has original and fresh named-case execution plus separately passing syntax, standards, semantic, visual and contract records.

| JDK / build | Actual runtime vendor | Immutable image | Java SHA-256 | prlimit SHA-256 | Evidence |
| --- | --- | --- | --- | --- | --- |
| 8 / 1.8.0_502-b07 | Temurin | `docker.io/library/eclipse-temurin@sha256:a4da319337cb6504ba4fb663cbd72a75df5515efb31bbdc6fc4ad7ba8d710dee` | `fa55c40b8accf16501ed11ac41a2b6cf5f411d36ea083f935d4a87cd3a7131a8` | `f27cfd8c1512a4cc6541b59b80cb4cdfd6ef28c34aa21db4299b48264cd0d128` | [actual launcher](../../../capabilities/evidence/foundation/T82-final/worker/jdk8-hardened_worker/boundary-observations/launcher/actual.properties); [five chains](../../../capabilities/evidence/foundation/T82-final/worker/jdk8-hardened_worker) |
| 11 / 11.0.32+9 | Eclipse Adoptium | `docker.io/library/eclipse-temurin@sha256:09f6797de424a6a085da4db6ba64381b2a4d51c2100a5e91998b69d7c23e0ecb` | `4e58bcde757bca39c30453e5fb3ae1495e76ef484f35908c6a5d3d10e1392e53` | `f27cfd8c1512a4cc6541b59b80cb4cdfd6ef28c34aa21db4299b48264cd0d128` | [actual launcher](../../../capabilities/evidence/foundation/T82-final/worker/jdk11-hardened_worker/boundary-observations/launcher/actual.properties); [five chains](../../../capabilities/evidence/foundation/T82-final/worker/jdk11-hardened_worker) |
| 17 / 17.0.20+8 | Eclipse Adoptium | `docker.io/library/eclipse-temurin@sha256:61a94244559f2e89e4edb02bae37eeb8762ecf5deaf237251fa630e5120a8798` | `6dce8306ebf735a85decdc801d84431f6e84525b60adc885624df05edeffb481` | `f27cfd8c1512a4cc6541b59b80cb4cdfd6ef28c34aa21db4299b48264cd0d128` | [actual launcher](../../../capabilities/evidence/foundation/T82-final/worker/jdk17-hardened_worker/boundary-observations/launcher/actual.properties); [five chains](../../../capabilities/evidence/foundation/T82-final/worker/jdk17-hardened_worker) |
| 21 / 21.0.12+8-LTS | Eclipse Adoptium | `docker.io/library/eclipse-temurin@sha256:1ca5e470ad60db0d5b4137c1357068fa210051d815ad98ea9964f4bbe7367f8c` | `11af352aa2c506c4123a4e4c19c187d59e06cd0dff317d54f5e6806e07c6715d` | `f27cfd8c1512a4cc6541b59b80cb4cdfd6ef28c34aa21db4299b48264cd0d128` | [actual launcher](../../../capabilities/evidence/foundation/T82-final/worker/jdk21-hardened_worker/boundary-observations/launcher/actual.properties); [five chains](../../../capabilities/evidence/foundation/T82-final/worker/jdk21-hardened_worker) |

Release IMPLEMENTOR remains Eclipse Adoptium; JDK 8 actually reports java.vendor=Temurin. Both observations are preserved without relabeling. Actual Java path/argv, installed policy, prlimit version, cleared environment, dependency hashes, modes and effective limits are in each launcher record. Document inventory is 754 / `99cba401304fe7d1bbc279f8afd1cbac30bf6dc0609e2747966c276b51933297`; Provider inventory is 25 / `56340dc06714414d32db2af86d87db696cbe05de93b4ece4571bb3b412a76f16`. Closed runtime dependencies include PDFBox, pdfbox-io, FontBox, commons-logging, ICU4J and OkapiBarcode, with the existing separately qualified optional TIFF closure.

| Qualified external producer | Observed version | Observed SHA-256 |
| --- | --- | --- |
| qpdf | 12.4.0 | `9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b` |
| pdfcpu | 0.15.0 | `5d1a9ff691ae1d720ba822fdc93d48ffc306aadd9497a858bf270b780e7d7c7d` |
| arlington | 0.81 | `45de36669a3aad3087346335acdfbe716a27beb27f3a39202d3f8f4997ba7458` |
| pdfium-cli | v0.11.2-pdfium-chromium-7881 | `3ef3375c429ce665e834f933a028225bf28ac837695aaa69c6fc21facf6780ab` |
| imagemagick | 7.1.2-30 | `372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e` |

Every environment record retains the full observed tool catalog, corpus/configuration and before/after raw identities. Seven actual public one-page PDF outcomes retain the existing qualified independent profiles and detected invalid-PDF/two-page/pixel controls. These certify the boundary slice’s document predicates, not unrelated capabilities.

Required predecessor refresh order completed: transactions → values → pages → metadata → annotations → text → images → incremental → password-baseline → password-clear-metadata → password-attachments → limits → worker. [Original refresh ledger](refresh-final-r1/results.json) and [continuation ledger](refresh-final-r2/results.json) retain commands, timestamps, prior-index hashes and each merge result. The first attachment gate remains failed because it exceeded the delivery wrapper's three-hour budget. Its [diagnosis](validation/refresh-final-r1-password-attachments-timeout.json) and [strict reuse receipt](../../../capabilities/evidence/foundation/T82-final-r2/password-attachments/reuse-receipt.json) distinguish six complete unchanged JDK8/11/17 tuples from two fresh JDK21 tuples. The continuation uses a six-hour delivery budget; Worker limits and the frozen staged candidate did not change. Seven missing/duplicate/identity mutation controls are rejected. Final index: 96 certifications / 392 chain records; all thirteen selected and previously certified obligations are SATISFIED.

| Final gate | Result | Exact command / evidence |
| --- | --- | --- |
| full-ubuntu-verify-final-r1 | PASS | `./mvnw -B -ntp verify`; [retained log](../../../capabilities/evidence/T82-delivery/validation/full-ubuntu-verify-final-r1.txt) |
| collector-suite-final-r2 | PASS | `python3 -B -m unittest discover -s scripts/tests -p test_*foundation.py`; [retained log](../../../capabilities/evidence/T82-delivery/validation/collector-suite-final-r2.txt) |
| stage-final-r2 | PASS | `python3 -B scripts/t03-foundation.py stage`; [retained log](../../../capabilities/evidence/T82-delivery/validation/stage-final-r2.txt) |
| inventory-validate-final-r1 | PASS | `./scripts/inventory validate`; [retained log](../../../capabilities/evidence/T82-delivery/validation/inventory-validate-final-r1.txt) |
| inventory-generate-final-r1 | PASS | `./scripts/inventory generate`; [retained log](../../../capabilities/evidence/T82-delivery/validation/inventory-generate-final-r1.txt) |
| inventory-check-final-r1 | PASS | `./scripts/inventory check`; [retained log](../../../capabilities/evidence/T82-delivery/validation/inventory-check-final-r1.txt) |
| inventory-readiness-final-r1 | NOT READY: unrelated obligations only (exit 1) | `./scripts/inventory readiness`; [retained log](../../../capabilities/evidence/T82-delivery/validation/inventory-readiness-final-r1.txt) |
| t21-cli-collect-final-r1 | PASS | `python3 -B scripts/t03-foundation.py collect capabilities/evidence/foundation/T82-final/worker/jdk17-hardened_worker/observations --obligation worker --execution-profile HARDENED_WORKER`; [retained log](../../../capabilities/evidence/T82-delivery/validation/t21-cli-collect-final-r1.txt) |
| t80-resume-guards-r1 | PASS | `python3 -B capabilities/evidence/T82-delivery/resume-attachments.py --check-only`; [retained log](../../../capabilities/evidence/T82-delivery/validation/t80-resume-guards-r1.txt) |
| refresh-final-r2-password-attachments | PASS | `python3 -B capabilities/evidence/T82-delivery/resume-attachments.py`; [retained log](../../../capabilities/evidence/T82-delivery/validation/refresh-final-r2-password-attachments.txt) |
| refresh-final-r2-limits | PASS | `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T82-final/limits --obligation limits`; [retained log](../../../capabilities/evidence/T82-delivery/validation/refresh-final-r2-limits.txt) |
| refresh-final-r2-worker | PASS | `python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T82-final/worker --obligation worker`; [retained log](../../../capabilities/evidence/T82-delivery/validation/refresh-final-r2-worker.txt) |

Final full verification totals: `{"errors": 0, "failures": 0, "skipped": 4, "tests": 1660}`. The four baseline optional skips are three #83 recovery/scale cases and one deferred #94 barcode raster case; mandatory T21 originals and live replays have zero skips. [All full-build reports](validation/full-ubuntu-verify-final-r1-reports/summary.json) retain exact XML/TXT hashes. The earlier full pass and all 219 exact reports are also preserved in reviewable paths.

Conditional `./scripts/verify-jdk-matrix.sh` was not triggered because no shipping source, shipping POM or build-compatibility change occurred. Four immutable-image T21 JDK runs remain mandatory and passed. Focused checks and the 116-test collector suite are recorded independently of the full build.

Standards: final review retained with no unresolved applicable finding. Spec: final review retained with no unresolved applicable finding. Earlier permission/link and actual Native/Facade receipt findings were corrected before staging; the bounded pin-helper duplication judgment was explicitly retained. The delivery-only timeout continuation also passed [Standards recovery review](reviews/standards-recovery.md) and [Spec recovery review](reviews/spec-recovery.md); its bounded observation-sequence duplication preserves the frozen runner and completed records. [Dispositions](reviews/source-dispositions.md).

Failed attempts are preserved as failures with their original transcripts/raw observations and candidate archives: runtime placement, concurrent build outputs, acceptance compile/setup issues, inventory proposal/test-expectation errors, staged-JAR helper and socket-path qualification, distinct vendor labels and empty JVM settings parsing. [Diagnoses and correction evidence](failed-attempts.md). None demonstrated a required shipping Worker behavior gap; no product policy, runtime inventory or classpath authority was relaxed.

Remaining release blockers are outside #82:

- `recovery` (#83): capability document.hardened-worker.recovery-scale is experimental, requires compatible
- `recovery` (#83): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on document.hardened-worker.recovery-scale
- `recovery` (#83): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on document.hardened-worker.recovery-scale
- `recovery` (#83): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on document.hardened-worker.recovery-scale
- `recovery` (#83): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on document.hardened-worker.recovery-scale
- `recovery` (#83): unresolved retained limitation: Historical one-environment scale observations do not certify all required final Ubuntu/JDK candidate profiles; #83 must retain actual opt-in runs.
- `recovery` (#83): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `recovery` (#83): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `recovery` (#83): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `recovery` (#83): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `canvas` (#85): capability composition.canvas.draw-positioned-text is experimental, requires compatible
- `canvas` (#85): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.canvas.draw-positioned-text
- `canvas` (#85): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.canvas.draw-positioned-text
- `canvas` (#85): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.canvas.draw-positioned-text
- `canvas` (#85): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.canvas.draw-positioned-text
- `canvas` (#85): missing required Facade mapping set for kernel PdfCanvas paths and encoded glyphs
- `canvas` (#85): unresolved retained limitation: The split Canvas graphics surface requires #85/#86 reconciliation; missing required drawing behavior remains blocking.
- `canvas` (#85): unresolved retained limitation: The borrowed-font restrictions must cover the approved Foundation Canvas profile under #85.
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `canvas` (#85): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): capability composition.canvas.images-colors-transparency is experimental, requires compatible
- `graphics` (#86): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `graphics` (#86): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.canvas.images-colors-transparency
- `graphics` (#86): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.canvas.images-colors-transparency
- `graphics` (#86): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.canvas.images-colors-transparency
- `graphics` (#86): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.canvas.images-colors-transparency
- `graphics` (#86): missing required Facade mapping set for image, color and transparency graphics
- `graphics` (#86): unresolved retained limitation: Image normalization and precision/color restrictions require #86 evidence for every promised image profile.
- `graphics` (#86): unresolved retained limitation: Borrowed-image filter restrictions require #86 reconciliation with required Foundation graphics.
- `graphics` (#86): unresolved retained limitation: Required graphics Facade and certification are still absent; downstream non-goals do not waive them.
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `graphics` (#86): incomplete prerequisite obligation canvas
- `fonts` (#87): capability composition.fonts.load-embed-subset-fallback is experimental, requires compatible
- `fonts` (#87): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `fonts` (#87): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.fonts.load-embed-subset-fallback
- `fonts` (#87): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.fonts.load-embed-subset-fallback
- `fonts` (#87): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.fonts.load-embed-subset-fallback
- `fonts` (#87): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.fonts.load-embed-subset-fallback
- `fonts` (#87): missing required Facade mapping set for font factory, font and positioned text
- `fonts` (#87): unresolved retained limitation: Positioned-text clipping restrictions require #87 reconciliation with the required text/Canvas families.
- `fonts` (#87): unresolved retained limitation: Admitted static font formats and metadata restrictions require #87 certification for all Foundation reference fonts.
- `fonts` (#87): unresolved retained limitation: Scalar/glyph ambiguity restrictions and the shaping split require #87/#93 evidence for required multilingual behavior.
- `fonts` (#87): unresolved retained limitation: Required font Facade and certification are absent; later-release exclusions cannot waive them.
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `fonts` (#87): incomplete prerequisite obligation canvas
- `rendering` (#88): capability conversion.rendering is experimental, requires compatible
- `rendering` (#88): incompatible Dependency Gate conversion.capability-provider.select-execute
- `rendering` (#88): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `rendering` (#88): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `rendering` (#88): incompatible Dependency Gate document.hardened-worker.recovery-scale
- `rendering` (#88): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on conversion.rendering
- `rendering` (#88): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on conversion.rendering
- `rendering` (#88): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on conversion.rendering
- `rendering` (#88): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on conversion.rendering
- `rendering` (#88): missing required Facade mapping set for pdfRender page rendering
- `rendering` (#88): unresolved retained limitation: Annotation rendering restrictions require #88 evidence against the Foundation Rendering profile.
- `rendering` (#88): unresolved retained limitation: Font substitution and platform-codec diagnostics are not compatibility evidence; #88 must certify required font/image outcomes.
- `rendering` (#88): unresolved retained limitation: Independent rendering option coverage, standards/semantic chains and Facade mappings remain incomplete under #88.
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `rendering` (#88): incomplete prerequisite obligation providers
- `rendering` (#88): incomplete prerequisite obligation graphics
- `rendering` (#88): incomplete prerequisite obligation fonts
- `rendering` (#88): incomplete prerequisite obligation recovery
- `providers` (#84): capability conversion.capability-provider.select-execute is experimental, requires compatible
- `providers` (#84): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on conversion.capability-provider.select-execute
- `providers` (#84): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on conversion.capability-provider.select-execute
- `providers` (#84): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on conversion.capability-provider.select-execute
- `providers` (#84): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on conversion.capability-provider.select-execute
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual, contract)
- `providers` (#84): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual, contract)
- `paragraphs` (#89): capability composition.layout.paragraph-areas is experimental, requires compatible
- `paragraphs` (#89): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `paragraphs` (#89): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `paragraphs` (#89): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `paragraphs` (#89): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.layout.paragraph-areas
- `paragraphs` (#89): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.layout.paragraph-areas
- `paragraphs` (#89): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.layout.paragraph-areas
- `paragraphs` (#89): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.layout.paragraph-areas
- `paragraphs` (#89): missing required Facade mapping set for layout Document, Paragraph, Text and AreaBreak
- `paragraphs` (#89): unresolved retained limitation: The full pinned Foundation font/typography profile remains uncertified under #87/#89/#93; four-platform wording is superseded only by ADR-0040.
- `paragraphs` (#89): unresolved retained limitation: Unicode/shaping/hyphenation/vertical-text restrictions must be reconciled with full required Foundation paragraph behavior under #89/#93.
- `paragraphs` (#89): unresolved retained limitation: Required advanced paragraphs/tables and migration mappings remain separate blocking obligations.
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `paragraphs` (#89): incomplete prerequisite obligation canvas
- `paragraphs` (#89): incomplete prerequisite obligation graphics
- `paragraphs` (#89): incomplete prerequisite obligation fonts
- `pagination` (#90): capability composition.layout.paragraph-pagination is experimental, requires compatible
- `pagination` (#90): incompatible Dependency Gate composition.layout.paragraph-areas
- `pagination` (#90): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `pagination` (#90): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `pagination` (#90): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.layout.paragraph-pagination
- `pagination` (#90): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.layout.paragraph-pagination
- `pagination` (#90): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.layout.paragraph-pagination
- `pagination` (#90): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.layout.paragraph-pagination
- `pagination` (#90): missing required Facade mapping set for layout paragraph properties, relayout and flushing
- `pagination` (#90): unresolved retained limitation: Tab variants and excluded leaders require #90 audit against the complete Foundation paragraph contract.
- `pagination` (#90): unresolved retained limitation: Small-font independent pages do not certify all required Foundation reference fonts/environments under #90.
- `pagination` (#90): unresolved retained limitation: Required standards, compatible dependencies and paragraph Facade remain incomplete under #90.
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `pagination` (#90): incomplete prerequisite obligation paragraphs
- `pagination` (#90): incomplete prerequisite obligation fonts
- `pagination` (#90): incomplete prerequisite obligation graphics
- `tables` (#92): capability composition.layout.tables is experimental, requires compatible
- `tables` (#92): incompatible Dependency Gate composition.layout.paragraph-areas
- `tables` (#92): incompatible Dependency Gate composition.layout.paragraph-pagination
- `tables` (#92): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `tables` (#92): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `tables` (#92): incomplete prerequisite obligation paragraphs
- `tables` (#92): incomplete prerequisite obligation pagination
- `tables` (#92): incomplete prerequisite obligation fonts
- `tables` (#92): incomplete prerequisite obligation graphics
- `tables` (#92): incomplete prerequisite obligation tables-base
- `tables` (#92): incomplete prerequisite obligation tables-pagination
- `shaping` (#93): capability composition.shaping.harf-buzz is experimental, requires compatible
- `shaping` (#93): incompatible Dependency Gate conversion.capability-provider.select-execute
- `shaping` (#93): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `shaping` (#93): incompatible Dependency Gate composition.layout.paragraph-areas
- `shaping` (#93): incompatible Dependency Gate composition.layout.paragraph-pagination
- `shaping` (#93): incompatible Dependency Gate composition.layout.tables
- `shaping` (#93): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.shaping.harf-buzz
- `shaping` (#93): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.shaping.harf-buzz
- `shaping` (#93): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.shaping.harf-buzz
- `shaping` (#93): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.shaping.harf-buzz
- `shaping` (#93): missing required Facade mapping set for layout typography and explicit shaping configuration
- `shaping` (#93): unresolved retained limitation: Admitted shaping/font/feature profiles require #93 evidence for every required representative writing system.
- `shaping` (#93): unresolved retained limitation: Cross-style grapheme/run restrictions must be reconciled with required Foundation shaping under #93.
- `shaping` (#93): unresolved retained limitation: Parent-side native resource limits and required Linux observations remain #93 responsibilities; Windows/macOS gates are removed by ADR-0040.
- `shaping` (#93): unresolved retained limitation: Missing actual required Ubuntu/JDK native observations still block #93; Windows/macOS absence is explicitly not a Foundation 0.1.0 blocker.
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `shaping` (#93): incomplete prerequisite obligation providers
- `shaping` (#93): incomplete prerequisite obligation fonts
- `shaping` (#93): incomplete prerequisite obligation paragraphs
- `shaping` (#93): incomplete prerequisite obligation pagination
- `shaping` (#93): incomplete prerequisite obligation tables
- `barcodes-1d` (#94): capability composition.barcodes.one-dimensional is experimental, requires compatible
- `barcodes-1d` (#94): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `barcodes-1d` (#94): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `barcodes-1d` (#94): incompatible Dependency Gate composition.fonts.load-embed-subset-fallback
- `barcodes-1d` (#94): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.barcodes.one-dimensional
- `barcodes-1d` (#94): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.barcodes.one-dimensional
- `barcodes-1d` (#94): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.barcodes.one-dimensional
- `barcodes-1d` (#94): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.barcodes.one-dimensional
- `barcodes-1d` (#94): missing required Facade mapping set for barcodes one-dimensional generation modes
- `barcodes-1d` (#94): unresolved retained limitation: All 116 cases require final candidate/environment evidence and compatible dependencies under #94.
- `barcodes-1d` (#94): unresolved retained limitation: Independent standards, required environment/font evidence and matching 1D Facade remain incomplete.
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-1d` (#94): incomplete prerequisite obligation canvas
- `barcodes-1d` (#94): incomplete prerequisite obligation graphics
- `barcodes-1d` (#94): incomplete prerequisite obligation fonts
- `barcodes-2d` (#95): capability composition.barcodes.two-dimensional is experimental, requires compatible
- `barcodes-2d` (#95): incompatible Dependency Gate composition.canvas.draw-positioned-text
- `barcodes-2d` (#95): incompatible Dependency Gate composition.canvas.images-colors-transparency
- `barcodes-2d` (#95): missing certified environment ubuntu-24.04-linux-x86-64-jdk8 on composition.barcodes.two-dimensional
- `barcodes-2d` (#95): missing certified environment ubuntu-24.04-linux-x86-64-jdk11 on composition.barcodes.two-dimensional
- `barcodes-2d` (#95): missing certified environment ubuntu-24.04-linux-x86-64-jdk17 on composition.barcodes.two-dimensional
- `barcodes-2d` (#95): missing certified environment ubuntu-24.04-linux-x86-64-jdk21 on composition.barcodes.two-dimensional
- `barcodes-2d` (#95): missing required Facade mapping set for barcodes QR, DataMatrix and PDF417 modes
- `barcodes-2d` (#95): unresolved retained limitation: Typed control/file-ID migration differences need exact #95 Facade and semantic evidence.
- `barcodes-2d` (#95): unresolved retained limitation: Every Reference Suite PDF417 generation mode must be reconciled under #95; raster-only inversion and other exclusions cannot hide required vector modes.
- `barcodes-2d` (#95): unresolved retained limitation: The complete 224-page-per-mode corpus and damaged controls need final candidate/environment evidence under #95.
- `barcodes-2d` (#95): unresolved retained limitation: Independent standards, compatible dependencies, required environments and 2D Facade remain incomplete.
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `barcodes-2d` (#95): incomplete prerequisite obligation canvas
- `barcodes-2d` (#95): incomplete prerequisite obligation graphics
- `tables-base` (#91): missing independently certifiable subcapability composition.layout.tables.base (aggregate composition.layout.tables)
- `tables-base` (#91): missing required Facade mapping set for layout Table and Cell properties and lifecycle
- `tables-base` (#91): unresolved retained limitation: AUTO allocation restrictions require #91 proof against full required fixed/automatic table behavior.
- `tables-base` (#91): unresolved retained limitation: Cell-content and nesting restrictions require #91 reconciliation with the Foundation table profile.
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-base` (#91): incomplete prerequisite obligation pagination
- `tables-base` (#91): incomplete prerequisite obligation fonts
- `tables-base` (#91): incomplete prerequisite obligation graphics
- `tables-pagination` (#92): missing independently certifiable subcapability composition.layout.tables.pagination (aggregate composition.layout.tables)
- `tables-pagination` (#92): missing required Facade mapping set for layout Table and Cell properties and lifecycle
- `tables-pagination` (#92): unresolved retained limitation: Restricted incremental large-table mode must cover every required Foundation flushing case under #92.
- `tables-pagination` (#92): unresolved retained limitation: T26/T27 evidence must be retained for the two independent subcapabilities on final required profiles.
- `tables-pagination` (#92): unresolved retained limitation: Required standards, dependency, Foundation typography/environment and Table/Cell Facade certification remain incomplete.
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk8/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk8/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk11/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk11/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk17/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk17/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk21/IN_PROCESS (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): missing certification ubuntu-24.04-linux-x86-64-jdk21/HARDENED_WORKER (required chains: syntax, standards, semantic, visual)
- `tables-pagination` (#92): incomplete prerequisite obligation tables-base
- `tables-pagination` (#92): incomplete prerequisite obligation pagination
- `tables-pagination` (#92): incomplete prerequisite obligation fonts
- `tables-pagination` (#92): incomplete prerequisite obligation graphics
- `environments` (#69): missing certification ubuntu-24.04-linux-x86-64-jdk8/REPOSITORY (required chains: contract)
- `environments` (#69): missing certification ubuntu-24.04-linux-x86-64-jdk11/REPOSITORY (required chains: contract)
- `environments` (#69): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract)
- `environments` (#69): missing certification ubuntu-24.04-linux-x86-64-jdk21/REPOSITORY (required chains: contract)
- `java-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk8/REPOSITORY (required chains: contract, artifact)
- `java-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk11/REPOSITORY (required chains: contract, artifact)
- `java-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, artifact)
- `java-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk21/REPOSITORY (required chains: contract, artifact)
- `facade-artifacts` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, artifact)
- `acceptance` (#70): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, review)
- `docs` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, review)
- `provenance` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: review, supply-chain)
- `supply-chain` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: supply-chain, review)
- `reproducibility` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: reproducibility, artifact, signature)
- `reproducibility` (#96): incomplete prerequisite obligation integration
- `candidate` (#97): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: artifact, signature, review)
- `candidate` (#97): incomplete prerequisite obligation reproducibility
- `candidate` (#97): incomplete prerequisite obligation publication-controls
- `publication-controls` (#97): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, review)
- `integration` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk8/REPOSITORY (required chains: contract, review)
- `integration` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk11/REPOSITORY (required chains: contract, review)
- `integration` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk17/REPOSITORY (required chains: contract, review)
- `integration` (#96): missing certification ubuntu-24.04-linux-x86-64-jdk21/REPOSITORY (required chains: contract, review)
- `integration` (#96): incomplete prerequisite obligation recovery
- `integration` (#96): incomplete prerequisite obligation canvas
- `integration` (#96): incomplete prerequisite obligation graphics
- `integration` (#96): incomplete prerequisite obligation fonts
- `integration` (#96): incomplete prerequisite obligation rendering
- `integration` (#96): incomplete prerequisite obligation providers
- `integration` (#96): incomplete prerequisite obligation paragraphs
- `integration` (#96): incomplete prerequisite obligation pagination
- `integration` (#96): incomplete prerequisite obligation tables
- `integration` (#96): incomplete prerequisite obligation shaping
- `integration` (#96): incomplete prerequisite obligation barcodes-1d
- `integration` (#96): incomplete prerequisite obligation barcodes-2d
- `integration` (#96): incomplete prerequisite obligation tables-base
- `integration` (#96): incomplete prerequisite obligation tables-pagination
- `integration` (#96): incomplete prerequisite obligation environments
- `integration` (#96): incomplete prerequisite obligation java-artifacts
- `integration` (#96): incomplete prerequisite obligation facade-artifacts
- `integration` (#96): incomplete prerequisite obligation acceptance
- `integration` (#96): incomplete prerequisite obligation docs
- `integration` (#96): incomplete prerequisite obligation provenance
- `integration` (#96): incomplete prerequisite obligation supply-chain

Scope and residual limits: Windows/macOS are explicitly uncertified; #83 recovery/scale remains experimental. Java Unix-domain APIs are absent on 8/11, and the policy qualification JVM is distinct from the production Worker. Modeled Folio memory/storage accounting, JVM heap/direct ceilings, CPU 300 / descriptors 64, monotonic elapsed termination and ownership are separate guarantees. No full RSS/native-memory, kernel/container isolation, arbitrary-bytecode or physical secure-erasure certification is claimed. Parent review/publication selection remains the only handoff action; no planning decision is needed for completed #82.

The ownership manifest/summary exclude themselves to avoid circular hashes. All indexed reports and transitive references, original historical #81 evidence, ticket failed attempts and ignored caches remain retained. Publication and any later authorized DCO commit belong to the parent orchestrator.

