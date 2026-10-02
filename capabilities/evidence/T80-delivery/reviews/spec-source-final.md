# Source Spec recheck

Baseline: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba` (HEAD unchanged).
Authority: `/workspace/contracts/issue-80-contract.md`.
Result: **two remaining independent-predicate findings**. No scope creep found.
This review does not certify pending matrix, full-gate or final-candidate results.

All three initial findings are corrected in the product/source examined:

- Public Workflow probes now reject StdCF streams, Crypt DecodeParms streams and
  PDF 2.0 missing AESV3 CF Length with `PASSWORD_SECURITY_UNSUPPORTED`.
- Crypt-only and valid indirect-Crypt originals authenticate independently,
  match exact payload bytes, and retain unauthenticated clear observations.
- Original stream-type controls and PDF 2.0 Length controls are present. Older
  qualified PDF 1.7 omission remains distinct.
- `/tmp/T80-public-r8.log` records 16 Native, 6 Facade and 2 producer tests passing
  with zero skips. Broader running gates are outside this source conclusion.

1. **Raw dictionary edition ignores Catalog Version.** The contract requires
   “algorithm/revision/version agreement” and distinguishes “normative PDF 2.0
   behavior.” `scripts/t80-dictionary-check.py:41` uses only `reader.pdf_header`.
   Original header-1.7 files with Catalog `/Version /2.0` and omitted AESV3
   StdCF Length receive raw PASS, although the public product correctly rejects
   them. A Catalog `/Version (2.0)` likewise receives raw PASS while the product
   reports `PDF_VERSION_INVALID`. Independently validate typed supported version
   declarations and use the effective edition for the predicates; qualify separate
   Catalog-version controls. Reproduction: `/tmp/t80-spec-edition-fixtures.py`.

2. **Routed-name types can manufacture independent semantic success.** The
   contract requires “dictionary types” and qualified original security predicates.
   `scripts/t80-byte-check.py:selected` and EF route checks compare Python strings
   without requiring PDF Name objects. Four originals using strings for EmbeddedFile
   Type, Filespec Type, Crypt Name or Crypt parameter Type receive
   `authenticated:true`, `routes-consistent:true`, exact-payload success and no
   diagnostic, while public Native correctly rejects each. Require actual Name,
   non-stream Filespec/EF dictionaries and embedded stream types, then qualify
   single-defect type controls. Reproduction: `/tmp/t80-spec-routed-types.py`.

Supplemental Arlington applicability is explicit and does not waive the mandatory
raw original, credential/Perms, clear-byte or exact-EF predicates. Its limitation
does make the two remaining predicate gaps material before source freeze and
complete requalification.
