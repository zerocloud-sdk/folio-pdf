# Standards review — empty JVM property handling

Baseline and HEAD: `df2df726a2695267bf63a7c9df49a4a7481f0169`; commit list empty. Read-only review of the narrow source correction after R3; no builds, certification or source changes by the reviewer.

Standards remain CONTRIBUTING.md, CONTEXT.md, the applicable Worker/ownership/independent-evidence ADRs and ADR-0040, under the authoritative execution contract.

## Documented-standard breaches

None found; no unresolved applicable documented source findings.

`scripts/t03-foundation.py:1177` now uses the existing flat properties reader and independently strips key/value whitespace. It accepts legitimately empty `-XshowSettings` values without losing the `=` delimiter, while required vendor/build values still come from their actual settings keys. Immutable image, release IMPLEMENTOR, build and executable hashes retain their existing checks. This respects ADR-0040's requirement to certify actual environments rather than modifying an authority to fit the observation.

`scripts/tests/test_t03_foundation.py:19` exercises the complete observer with a clearly synthetic payload containing empty JVM settings, a continuation path, the distinct release/runtime vendor labels and launcher identity. It tests the failure-producing input shape through the owning observer rather than merely mirroring the parser expression. Synthetic observer output is explicitly marked as a fixture and does not claim certification. No Java source, shipping module POM, runtime inventory or public API changed.

The recorded original failed attempt and archived prior candidate remain historical; the narrow fix requires the stated fresh staging and certification rather than relabeling earlier reports.

## Judgment disposition

No additional Fowler smell warrants action. The bounded R1 pin-helper duplication retains its accepted disposition.

Counts: 0 new documented breaches, 0 unresolved applicable source findings. Final four-tuple certification and gate review is pending.
