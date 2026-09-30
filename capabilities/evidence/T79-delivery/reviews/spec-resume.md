# Resumed final source Spec review

Reviewed the dirty worktree with
`git diff 7420a656d27d17b82c7632be7c6214f8a657a22f --` and read the
new implementation, public tests, producers, observers, collector, controls,
model overlays, and profile documentation identified by `ticket-paths.json`
and `git status --short -uall`. Rechecked HEAD: it still equals that baseline;
the intervening commit list is empty. Sole specification:
`/workspace/contracts/issue-79-contract.md`.

**No unresolved implementation, missing-scope, or scope-creep findings.**

The earlier reviews were considered. Initial P1 remains resolved by separating
input filter validation from normalization for new encryption in
`PdfBoxMetadataEncryption`, with public rewrites of all-content StdCF metadata
under both selected scopes. Initial P2 remains resolved by the catalog-specific
model and admitted Identity default/null/omitted variants. Neither finding is
reopened.

The reviewed Native changes admit required R4/R5/R6 clear-metadata inputs,
derive metadata-sensitive R4 output keys, update R6 Perms, and exempt only the
catalog metadata stream bytes. Public tests retain owner proof, restricted-user
permissions, donor protection, incremental/signature restrictions, caller
ownership, cleanup, safe preflight, and publication guarantees. Facade selectors
9/10/11 and bit 8 agree with the manifest and documented RC4-40 boundary.

Independent observers examine original XMP and protected bytes in addition to
the dictionary flag. Separate clear-metadata models preserve baseline checks;
the collector replays pinned tools and checks identities, chains, controls,
and actual Native/Facade mode declarations. Authorities retain the baseline
dependency and inherited value gate, while attachments and aggregate security
remain incomplete. English/Chinese material and provenance agree with that
bounded scope and the Ubuntu/JDK-only certification claim.

This is a source review, not certification. Full verification, JDK matrix,
fresh same-candidate certification, final readiness, and the completion receipt
remain the executor's acknowledged completion gates. Their unfinished status
is not reported as an implementation defect here. No builds, certification,
source/authority edits, staging, commits, or tracker actions were performed;
this report is the reviewer's only write.

## Inventory regression addendum

Reviewed `InventoryCommandTest.java:36` and its child/count assertions against
the manifest, generated views, and retained failure logs. **No new findings.**
The required bit-8 constant accounts for 215 surfaces; the child correctly has
62 Stable mappings, zero Preview additions, and no explicit exclusion. This
supports the clause: “Stable/Preview compiled surface and artifact contracts
agree with the manifest.” Existing assertions remain intact. The generated
readiness still exposes outstanding certification gates. Focused/full reruns
remain executor validation; this addendum reports no unobserved test pass and
involved no code edits or builds.
