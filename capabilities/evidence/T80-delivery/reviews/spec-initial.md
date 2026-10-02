# Initial Spec review

Baseline: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.
Authority: `/workspace/contracts/issue-80-contract.md`.
Reviewed tracked diff and untracked T80 implementation, corpus, observer,
collector, public tests, models and contracts. Source/tracker remained untouched.
This records the initial findings; a final recheck must establish their resolution.
Ongoing full gates and candidate certification were not treated as missing work.

1. **Invalid stream objects accepted as security dictionaries.** The contract
   requires “dictionary types and lengths … tampering rejection.”
   `PdfBoxPasswordSecurity.validateCryptFilters` accepted a stream as resolved
   `/CF/StdCF`; `PdfBoxEmbeddedFileEncryption.encrypted` accepted a stream as
   `/Crypt` DecodeParms. Isolated public Workflow probes for both malformed
   objects successfully extracted the original protected payload. Reject stream
   subclasses explicitly, resolve indirect dictionaries in independent predicates,
   and qualify separate single-defect controls. The parent began these corrections
   while this review was running.

2. **Independent original-byte observation rejects required Crypt positives.**
   The contract requires “admitted explicit /Crypt selections” and four chains for
   “each required positive input.” `scripts/t80-byte-check.py:decode` removed the
   sole Crypt filter then called the pinned `EncodedStreamObject.get_data`, whose
   decoder rejected the now absent Filter. With the correct credential, existing
   `aes-256-r6-crypt-scalar.pdf` and `aes-256-r6-eff-identity.pdf` returned
   `authenticated:false`, `diagnostic:input-rejected`; a phased probe confirmed
   successful authentication followed by failed ordinary decoding. Return the
   decrypted bytes when no ordinary filters remain. The parent began this fix.

3. **PDF 2.0 required AESV3 Length presence is not enforced.** The contract
   distinguishes “normative PDF 2.0 behavior” and requires dictionary/version
   agreement. The inherited audit and T80 Arlington CFM predicate require
   AESV3 StdCF Length at PDF 2.0, but the product and raw dictionary adapter allow
   omission. An original PDF 2.0 AESV3 attachment fixture with absent CF Length
   was accepted publicly and received raw-dictionary PASS. Add an edition-specific
   check and malformed/control coverage while preserving qualified PDF 1.7
   omission. This is especially material when supplemental Arlington is
   inapplicable to incremental originals.

No scope creep identified. Three substantive findings require final regression
and evidence requalification before completion.
