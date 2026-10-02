# Source Spec review, second recheck

Baseline: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`; HEAD remains that commit.
Authority: `/workspace/contracts/issue-80-contract.md`.
Reviewed tracked changes and untracked ticket sources. No implementation or
tracker mutation was performed. Result: **one remaining finding**; no scope
creep identified. Final certification and still-running gates are not certified
by this source review.

The previous effective-edition and routed-name findings are corrected. Replaying
their isolated originals now produces raw `Length (CryptFilter)` or
`Version (Catalog)` failures, or rejects wrong routed-name types before
authentication. Initial stream-type, Crypt-only decoding and PDF 2.0 CF Length
corrections remain present.

The acceptance-only qpdf overlay selects EFF through actual EF references and
admits the optional scalar Crypt parameter Type. It retains upstream warnings
and is separately named `12.4.0-folio-t80-r1`; historical T78/T79 tools remain
unchanged. Source/archive/base-patch/T80-patch/build-package/runtime identities
match their pins. Recorder and collector freeze the new wrapper and closed
runtime, with authority SHA-256
`40243a7a45ff595762b218d34b729fa36e21c7664a3419091bda9fef5a067b15`.
The review validated 3,338 closed identities and exercised actual qpdf: compressed,
scalar/array Crypt and indirect Crypt/StdCF originals passed; the T80 truncated
original failed. All private review intermediates were removed.

**Remaining extension dictionary types.** The contract requires “dictionary
types and lengths” and “algorithm/revision/version agreement,” including the
qualified PDF 1.7 R5/R6 extension profile. `scripts/t80-dictionary-check.py`
compares ADBE BaseVersion as a Python string and ExtensionLevel numerically
without checking their PDF types. A string `(/1.7)` BaseVersion receives raw
PASS although public Native rejects it. A real `8.0` ExtensionLevel receives
both raw PASS and successful public payload extraction;
`PdfBoxPasswordSecurity.requireAdobeExtension` uses `getInt`, which coerces it.
Require non-stream extension dictionaries, resolved BaseVersion Name and
ExtensionLevel integer types, and qualify exact single-defect type controls.
Isolated reproduction author: `/tmp/t80-spec-extension-types.py`.

The supplemental Arlington applicability guard leaves mandatory original
dictionary, credential/Perms, clear-content and exact-EF predicates active.
That qualification remains truthful only after the remaining original typed
extension predicate is closed and the final source is requalified.
