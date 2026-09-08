# T70 delivery receipt

This delivery covers only `document.blank.create-publish-reopen` and the
Foundation `transactions` obligation (#70). The existing 12 lifecycle entries
are Stable; Preview includes the same 12 entries with no additional mapping.
The reviewed changes remain uncommitted on `main`, relative to
`9418b472c99aa87692a0f1ba7f31808e64c5af77`.

## Certified candidate

- Candidate identity: `62490fcbe08601f9c112ab00393980c77126e3462d43ae2425fb40a7b92965da`.
- Contract identity: `55208249c84c88ec480ba16523f6c475cd04ad87b03714353c17afee28c5d662`.
- Authority: [`foundation-evidence.yaml`](../../foundation-evidence.yaml).
- Actual observations: [`T70-20260908-candidate-02`](../foundation/T70-20260908-candidate-02/).
- Frozen build receipt: `target/foundation-0.1.0/build-inputs.json`, covering
  884 source inputs, 30 contract inputs, 23 product artifacts and 24 acceptance
  harness/runtime files.

All eight pinned Ubuntu 24.04/Linux x86-64 JDK 8/11/17/21 ×
`IN_PROCESS`/`HARDENED_WORKER` tuples retain separate passing syntax, standards,
semantic and visual chains. Each tuple runs 34 public-consumer and actual-jar
contracts. Native uses the requested execution profile; the Stable Facade
observations explicitly record its `IN_PROCESS` default.

The standards chain uses qualified, pinned pdfcpu and Arlington checkers with
22 required rules and 352 detected rule-specific negative observations across
the two products and eight tuples. The original 144 DPI, 1224×1584, opaque sRGB,
zero-fuzz, AE 0 visual contract is unchanged. Single-pixel controls report AE 1
and fail; syntax and semantic negative controls also fail as required.

## Delivery validation

| Validation | Result | Record |
| --- | --- | --- |
| `./mvnw -B -ntp verify` | PASS; 1,186 tests, 0 failures, 0 errors, 4 skips | [Full reactor log](../T70-development/full-verify-final.txt) |
| `./scripts/verify-jdk-matrix.sh` | PASS, exit 0; all four JDKs | [Raw matrix log](jdk-matrix.txt), [invocation and totals](jdk-matrix-result.txt) |
| `./scripts/inventory generate` | PASS | [Log](inventory-generate.txt) |
| `./scripts/inventory validate` | PASS | [Log](inventory-validate.txt) |
| `./scripts/inventory check` | PASS | [Log](inventory-check.txt) |
| `./scripts/inventory readiness` | Expected exit 1: `SATISFIED transactions (#70)`, global `NOT READY` | [Full blocker report](inventory-readiness.txt) |
| `git diff --check` | PASS, exit 0 | [Result](git-diff-check.txt) |

| Matrix JDK | Tests | Failures | Errors | Skips | Reactor duration |
| --- | ---: | ---: | ---: | ---: | --- |
| 8 | 1,186 | 0 | 0 | 4 | 40:49 min |
| 11 | 1,186 | 0 | 0 | 4 | 33:20 min |
| 17 | 1,186 | 0 | 0 | 4 | 34:23 min |
| 21 | 1,186 | 0 | 0 | 4 | 34:33 min |

The main-workspace full verify used the explicit
`FOLIO_HARFBUZZ_HELPER=/home/ubuntu/IdeaProjects/open-pdf/.build-cache/harfbuzz/10.2.0-final/bin/folio-harfbuzz`.
The matrix invocation retains the same installation path in its result record.
The complete matrix finished at `2026-09-08T10:17:58Z`.

Build-input checks pass [before the matrix](matrix-inputs-before.txt),
[after the main verify](main-inputs-after.txt), and
[after the full matrix](matrix-inputs-after.txt). Both workspaces retain the same
build receipt, SHA-256
`5636c17c0c881a95a9636927e4d59b59b11e51813cb6e7b71252d9a843003f8f`.

The full reactor's four skips are the three opt-in Worker scale tests and one
opt-in T30 raster test. They are not counted as executed tests; all 34 T03
contracts in each certification tuple execute without skips.

The unchanged default JDK-matrix driver runs in
`/tmp/t70-jdk-matrix-workspace`, an isolated copy of this exact candidate. The
matrix uses the four immutable images from `foundation-environments.yaml` and
the same explicit Folio HarfBuzz installation. Candidate source, contract,
artifact and harness bytes are checked against the staged build receipt.

## Independent review

The separate [Standards report](standards-review.md) and
[Spec report](spec-review.md) use the fixed baseline above and cover the complete
tracked, moved and new working-tree scope. They include the actual candidate
artifacts, retained evidence and delivery gates. The Spec reviewer also retained
its [evidence audit](evidence-audit.json).

## Scope and remaining limits

Readiness reports `SATISFIED transactions (#70)` and global `NOT READY`.
Other Foundation obligations retain their existing blockers. The standards
qualification covers the declared minimal blank-document profile, not all
ISO 32000 requirements or PDF/A/PDF/UA conformance. Windows and macOS remain
uncertified. This candidate's evidence cannot certify later changed artifacts.

No commit, push, PR, tracker change, tag, signing, publication or production
credential operation was performed. Product dependencies remain within the
existing first-party runtime; external checkers and comparator libraries stay
inside the acceptance boundary. Earlier failed/stopped attempts and strict
Red→Green→Refactor observations remain in [`T70-development`](../T70-development/).
