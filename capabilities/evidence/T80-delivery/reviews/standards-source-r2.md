# T80 source Standards review, revision 2

Baseline/current HEAD: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.
Reviewed the tracked worktree diff and new ticket source, tests, original corpus
authors/controls, acceptance tools/models, inventories, contracts and documents.
The sole task contract is `/workspace/contracts/issue-80-contract.md`;
repository standards and applicable ADRs remain those listed in
`standards-source-final.md`. No product, tracker or git state was changed.

**PASS for the reviewed source snapshot. Zero unresolved documented-standard
findings; zero actionable heuristic findings.**

Both initial findings remain resolved: incremental filter copies are precharged
to the existing owned-memory ledger, input-sized filter work has checkpoints,
and T80 provenance identifies authorship. Stream/dictionary distinctions retain
valid indirect dictionaries, PDF 2.0 filter lengths are checked, public guards
retain attachment authentication/permissions, and artifact contracts include
both scope constants. No newer Java runtime API, public backend exposure,
runtime dependency or weakening of the secure default was identified.

The additive Apache-2.0 qpdf correction uses actual EF references and admits the
optional scalar Crypt parameter Type. Its separate `12.4.0-folio-t80-r1`
identity binds source/archive/patch, immutable build image, package manifest,
executable and complete runtime closure. Original malformed-file controls remain
required; the patch does not suppress syntax warnings or replace credential
proof. These tools stay outside product artifacts. Provenance records the
acceptance-only licenses; existing qpdf documentation records the GCC Runtime
Library Exception. Historical T78/T79 tool identities remain distinct.

The recorder's timeout is restored to **900000 ms**, within the unchanged shared
coordinator guard. Separately pinned profile adapters remain an accepted
identity/provenance tradeoff and reuse retained-file/process supervision.

Full validation, actual independent tests and final candidate certifications are
ongoing parent responsibilities. Pending evidence is **not** a Standards defect
and this report makes no candidate-certification or release-publication claim.

Reviewed SHA-256 identities:

- T80 authority pin: `40243a7a45ff595762b218d34b729fa36e21c7664a3419091bda9fef5a067b15`
- `PdfBoxEmbeddedFileEncryption.java`: `63cf8a23b907cc29814f2d521fc5235b1e8d4a19075ec259c3c487f2659ba5c7`
- `PdfBoxPasswordSecurity.java`: `e40f9249e86bfbabc51e0c0f6dc3da5d591474913526152bc0d9eec13e6e89a4`
- `T80EvidenceCommand.java`: `ecbe44131cf9e3b7a9f026180a4f9cf1ed23bd7c9fe29c4eb84e717d5fc20f35`
- qpdf T80 patch: `6ac1eda59517a08b805f7a337dfad1a9568e57fbe7b2e752fb024eb9eb9d2adf`
- qpdf provisioner: `f4836da829830f9d9d1b89e56a3165b53aa582a598bf9a81b9e46dfec81dca84`
- `PROVENANCE.md`: `57a019f78f2fbdba4ad01da858d925d8608eaca62dfa3dbfbbb3336ef154db7c`
