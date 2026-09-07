# T30 independent Spec follow-up — FNC4 repair

The initial payload defect is resolved in the focused independent checks. This supplements, and does not replace, `T30-spec-review.md`.

The reviewer recompiled the original temporary probes against the current product classes and reran public `DocumentWorkflow.execute` generation, publication, reopening, and independent ZXing decoding. No Maven command or repository product/test edit was performed.

- Original AUTO probe: **240 cases, 0 failures**, compared with 65 payload mismatches before the repair. It includes single/pair FNC4, ASCII/control/DEL transitions, numeric runs, and literal backslash prefixes.
- Minimal IN_PROCESS and HARDENED_WORKER controls: **8 published pages, all correctly decoded**. Single FNC4 plus `123456` now yields `\u00B123456`; double FNC4 yields `\u00B1` through `\u00B6`. AUTO agrees with forced B, and both profiles return COMMITTED.
- No regression appeared in these checks. Restricting ordinary AUTO FNC4 encoding to Okapi's A/B selection prevents the inserted Code C latch from changing the intended extended-character payload.

Probe sources, fresh PDFs, and exact output remain in `/tmp/folio-t30-spec-rerun-de9lv368/`; the original reproduction files remain intact. Reviewed `PdfBoxBarcodeOperations.java` SHA-256: `307f3c1943c2cd46c1024582b21ca9623ba63a50bbf226fcf42524fc6c03c614`. Compiled class SHA-256: `ffa684264c7ca34230cf8044531226d4d17e4f22678342a222e523f92626c82e`.

**Focused result: 0 unresolved payload findings; 0 observed regressions.** Refreshed independent acceptance coverage, retained artifacts, full verification, and the complete follow-up review remain pending. No completion or compatibility criterion is approved by this focused check.
