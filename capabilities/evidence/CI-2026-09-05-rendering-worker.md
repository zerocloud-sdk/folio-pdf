# CI rendering and Worker termination regression repair

The failing [CI run 33957144822](https://github.com/zerocloud-sdk/folio-pdf/actions/runs/33957144822)
compiled successfully but failed the rendering privacy test on JDK 8, 11, 17
and 21, and the hard elapsed-time test on JDK 21. These failures predate the
T26 table implementation. This repair is based on the T26 commit
`18433183ebbeb9cc3217f90f57f0456ed118a393`.

## Causes and boundaries

A cold PDFBox font cache scans installed fonts. Its warning that a font header
was rejected and recorded as ignored previously poisoned the active rendering
log scope. A page that successfully used a different fallback font therefore
failed with `RENDER_FAILED`. The scope now suppresses that specific discovery
warning without treating it as an incomplete render. The exception matches
only `FileSystemFontProvider`, a level below SEVERE, and the discovery-only
`Could not load font file '` prefix. Selected-font load failures use a different
prefix and retain the existing failure behavior. All backend log text remains
private on the rendering thread.

Separately, when the Worker watchdog kills a process, the reader can receive
EOF before the caller next polls the process. A truncated frame was then
reported as `WORKER_PROTOCOL_REJECTED`, despite the already established elapsed
stop. Protocol failure mapping now preserves an established elapsed or
cancellation reason. An attached `DocumentFailure` is still returned first;
existing terminal resource failures remain authoritative. Authentication,
frame validation and protocol rejection without an established stop are
unchanged.

## Regression evidence

- On the original code, rerunning the existing rendering privacy test with a
  fresh empty font cache reproduced the CI `RENDER_FAILED` on IN_PROCESS;
  the same test passed with the ambient warm cache. A temporary local probe
  attributed the failure to the ignored NotoColorEmoji font header warning.
- `RenderingColdStartTest` runs the public Workflow assertions in a fresh JVM,
  with an isolated home/cache and a project-authored sfnt header lacking all
  mandatory tables. It checks that discovery actually records the malformed
  installed font, substitution succeeds, names remain private, caller JUL
  filters are restored, and a real missing page resource still fails.
- `physicalElapsedLimitDuringStartupPreservesItsFailureCode` uses a fixed
  logical Clock and a 75 ms physical Worker limit. Before the fix, the JDK 21
  container reproduced the wrong protocol failure; a temporary probe confirmed
  a truncated frame header with `stop=ELAPSED`. The test also checks the target
  sentinel is preserved. The original 200 ms hard-stop test is retained.
- All temporary diagnostic probes were removed. The two new regressions plus
  the two existing rendering profile cases and original hard-stop case passed
  together: 5 tests, no failures, errors or skips.

## Independent implementation reviews

Standards: pass, zero hard violations and zero optional Fowler findings.
The separate reviewer checked the precise warning prefix against the pinned
Apache PDFBox 3.0.8 source, the stable failure precedence, public Workflow test
seams, Java 8 boundaries, inventory and provenance.

Spec: pass, zero remaining findings and no additional scope. The separate
reviewer confirmed the intended rendering and Worker fixes preserve privacy,
real render failures and ordinary protocol rejection.

These implementation checks do not promote any Capability or supply an
independent standards/conformance Acceptance Evidence chain. The T26 evidence
and its earlier full-build receipt remain a historical implementation snapshot.

## Full verification

Completed on 2026-09-05. All five complete reactor builds passed.

| Runtime | Suites | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Host JDK 17 | 49 | 864 | 0 | 0 | 3 |
| Temurin 8 | 49 | 864 | 0 | 0 | 3 |
| Temurin 11 | 49 | 864 | 0 | 0 | 3 |
| Temurin 17 | 49 | 864 | 0 | 0 | 3 |
| Temurin 21 | 49 | 864 | 0 | 0 | 3 |

The three skips in every run are the existing opt-in
`HardenedWorkerScaleProfileTest` cases. No rendering, protocol, isolation or
T26 table regression was skipped. Both new CI regressions passed on each JDK.

Commands: `./mvnw -B -ntp verify` on the host, and
`./scripts/verify-jdk-matrix.sh 8`, `11`, `17`, `21` in four isolated clones
(one complete verify reactor per invocation, at most two running concurrently).
Before launch, all 1,045 tracked and new input files were hash-compared with
each clone; the root inputs were compared again after all builds. No tested
source changed. This verification receipt was completed afterward.

Input manifest SHA-256:
`205858954926c53cd58013884031f2f4a13277c2b9058622c3689a8e2bcdb510`.

Full build log SHA-256 values:

```text
555b49eac703bbec90e7b3784aaa8d2da038288e2b44949a63ccd6208d3fcbf9  /tmp/folio-ci-verify.log
7e80c3c5e713495d0a0772093ccecb5d9fcfe136fcb66670db702dcf884529f1  /tmp/folio-ci-matrix-blcwrp5x/jdk8.log
3dcb76c65b103bbedc3fd254cb3c1fe0599d84a3b46a5257b47212081f5bf86c  /tmp/folio-ci-matrix-blcwrp5x/jdk11.log
3fad9dd08fa82705a74c89dc1de64c0e607d41e4579d9f853416b75377974950  /tmp/folio-ci-matrix-blcwrp5x/jdk17.log
e20091a94b5006071cf456a782b55cbb7a993656af451b6322451e88c919a113  /tmp/folio-ci-matrix-blcwrp5x/jdk21.log
```

Verified repair source SHA-256 values:

```text
b6ed1e9d16eaeda758f52c9da863050490109c89cf4bb2e26061b6b9a6a9454c  pdf-document/src/main/java/net/zerocloud/pdf/HardenedWorkerEngine.java
eb6f23328c61f22be2e6592b25db6aa16a75031961c0a43e936279c4acea0951  pdf-document/src/main/java/net/zerocloud/pdf/RenderingLogScope.java
5c76e23e86e054cf33727792271b95a671dd36426fceba145cd463f8dc973be5  pdf-document/src/test/java/net/zerocloud/pdf/HardenedWorkerIsolationTest.java
784aade8e8283e63d80cad15b6e9bf517c42a846cd2ab1b7f68a82c8e7ca4cae  pdf-document/src/test/java/net/zerocloud/pdf/consumer/RenderingColdStartTest.java
```

Inventory generation/check and whitespace validation passed. The repair is
ready for the authorized DCO-signed commit and push. The ticket is closed only
after the exact pushed commit passes GitHub Actions; that remote result is
recorded on the ticket rather than claimed by this pre-push receipt.
