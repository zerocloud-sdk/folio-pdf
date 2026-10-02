# T80 final source Standards review

Baseline and current HEAD: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.
Comparison covers the worktree diff and new T80 source, tests, original fixture
authors, controls, models, observers/collector, inventories and documentation.
Authority: `/workspace/contracts/issue-80-contract.md`, `CONTRIBUTING.md`,
`AGENTS.md`, `CONTEXT.md` and applicable ADRs, including 0002, 0006, 0009,
0016, 0017, 0019, 0021, 0023, 0025, 0029, 0031 and 0040.

Both documented findings from `standards-initial.md` are resolved:

- `selectStdCF` receives the workflow resource context and charges retained
  array/container memory before allocating its input-sized copies. It checks
  elapsed work while copying; `encrypted` also checks every filter-scan iteration.
  All parser callers pass the existing context, preserving the Worker ledger.
- The T80 provenance record identifies OpenAI Codex's authorship under operator
  mabaiqiu's direction, alongside origins, licenses and clean-room exclusions.

The recheck also covers strict stream/dictionary distinctions, admitted indirect
crypt-filter and parameter dictionaries, PDF 2.0 filter-length validation,
public credential/access guards, publication/incremental preservation and the
artifact contract's two public constants with value 24. No new runtime
dependency, backend exposure in public signatures, newer Java API or weakening
of the secure default was identified.

The initial **possible Duplicated Code** heuristic is accepted as an intentional
tradeoff: separate T79/T80 profile adapters retain historical identity and
provenance boundaries while reusing `RetainedFiles`, process supervision and
existing qualified primitives. It is not a documented-standard violation and
does not require refactoring for this bounded slice. Other baseline smells did
not yield an actionable finding.

Result: **PASS for this source snapshot; zero unresolved documented-standard
findings and zero actionable heuristic findings.** Mandatory test gates, final
candidate certification and evidence freshness are assessed separately by the
parent; this review makes no release or environment-certification claim.

Selected reviewed source SHA-256 identities:

- `PdfBoxEmbeddedFileEncryption.java`: `63cf8a23b907cc29814f2d521fc5235b1e8d4a19075ec259c3c487f2659ba5c7`
- `PdfBoxPasswordSecurity.java`: `e40f9249e86bfbabc51e0c0f6dc3da5d591474913526152bc0d9eec13e6e89a4`
- `PROVENANCE.md`: `491a89a8149769ddda3c2c2c327cd1bc83fd9e2188ec64f079df022b75def7a3`
