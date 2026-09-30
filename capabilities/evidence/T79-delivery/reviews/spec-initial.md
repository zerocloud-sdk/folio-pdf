# Initial Spec review

Compared the worktree with `7420a656d27d17b82c7632be7c6214f8a657a22f`
using `git diff 7420a656d27d17b82c7632be7c6214f8a657a22f --`, also reading
the new source, generators, models, producers, observers and tests. HEAD has
no commits beyond that baseline. Authority: `/workspace/contracts/issue-79-contract.md`;
profile trace: `docs/research/T79-clear-metadata-profile-audit.md`.

- **P1 — Valid all-content metadata Crypt filters regress on rewrite.**
  `PdfBoxMetadataEncryption.removeMetadataIdentityFilter`, called unconditionally
  from `PreparedOutput.apply`, invokes the clear-metadata filter validator for
  every leading metadata `Crypt`. A valid encrypted catalog metadata stream with
  `/Name /StdCF` therefore fails a new all-content rewrite. The contract says:
  “Preserve #78 all-content security and its successful profiles.” Separate
  input-scope validation from output normalization, preserving valid StdCF
  behavior while removing overrides incompatible with the selected new policy.
  Add original all-content StdCF metadata and public rewrite regression cases.

- **P2 — The metadata array model rejects a valid default Identity selection.**
  The new Arlington `Metadata.tsv` array predicate requires an explicit first
  `/Name /Identity`. Consequently `/Filter [/Crypt] /DecodeParms [<< >>]`
  fails qualification even though the runtime accepts it and ISO1 Table 14
  defines Identity as the omitted Name's default. The contract says:
  “Required successful cases are implemented; backend limitations are not
  converted into accepted unsupported results.” Admit and independently
  qualify correctly shaped default-parameter variants; retain the malformed
  shape, wrong order, duplicate and ordinary-stream Identity controls.

No scope creep identified. This is a static initial review: no builds, tests,
certification tools, staging or Git mutations were executed. Pending authorities,
final validation and same-candidate evidence were explicitly outside this initial
pass and are not certified by it. Final follow-up must inspect the fixes and
their actual regression evidence.
