# T30 independent Spec review — initial finding

Reviewed the captured staged, unstaged and untracked scope against base `5508429ec032e8879d6d359a018f25d25efe878c`, issue #31, parent #1 and its comment, the full handoff, domain/ADR contracts, and the T30 profile. No commits exist after the base. This report describes the implementation before remediation.

**[P1] Code128 AUTO silently changes FNC4 payloads before numeric runs.** Issue #31 requires “An independent decoder recovers each payload.” The [English contract](../../../docs/one-dimensional-barcodes.md#encoding-and-checks), lines 47–53, promises explicit FNC4 shift/latch semantics. [GS1 General Specifications 21.0.1, §5.4.3.4.2, page 300](https://ref.gs1.org/standards/genspecs/21.0.1/) states that “the value 128 is added to the ASCII value of the following data character”; a consecutive pair extends subsequent characters until another pair or symbol end.

At `pdf-document/src/main/java/net/zerocloud/pdf/PdfBoxBarcodeOperations.java:131–166`, ordinary AUTO input goes through Okapi ABC selection. For `Barcode1D.builder(CODE128, String.valueOf(FNC4) + "123456").build()`, actual published PDF paths decode to `123456`, rather than `\u00B123456`. The recovered words are `[104,100,99,12,34,56,30,106]`: Code C is inserted after FNC4. Two FNC4s likewise yield plain digits rather than `\u00B1` through `\u00B6`. Forced-B controls recover both expected payloads. Both IN_PROCESS and HARDENED_WORKER return COMMITTED for the wrong AUTO output. Thus callers receive successful but misleading barcodes, and the current 112-case evidence misses this supported input combination.

Minimal public-Workflow reproducer, PDFs and output are preserved at `/tmp/folio-t30-spec-probe/T30SpecMinimal.java`, `minimal-IN_PROCESS.pdf`, `minimal-HARDENED_WORKER.pdf`, and `minimal-result.txt`. Fix AUTO's function-state handling, add public regression/independent acceptance cases, and regenerate affected evidence and verification.

No other missing family, incorrect behavior or scope expansion was found. Independently checked all 689 retained artifact hashes, all 318 source declarations, and recorded 224 path/raster decodes and AE-zero comparisons. Root/matrix log hashes match. The new remediation test now differs from the prior 787-file validation manifest, so renewed validation is required. Experimental status and INDETERMINATE promotion gates are correctly retained.

**Counts: 1 unresolved spec gap; 0 scope issues.**
