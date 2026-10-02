# T80 source Standards review, revision 3

Baseline/current HEAD: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.
This recheck covers the source delta since `standards-source-r2.md`, retaining
its worktree/untracked-source coverage and documented standards/ADR assessment.
The sole execution contract remains `/workspace/contracts/issue-80-contract.md`.
No implementation, git or tracker mutation was performed.

**PASS for this source snapshot: zero unresolved documented-standard findings
and zero actionable heuristic findings.**

`requireAdobeExtension` resolves ordinary indirect values, requires non-stream
Extensions/ADBE dictionaries, retains BaseVersion's Name type and ExtensionLevel's
integer type/bounds, and validates optional Type names. It does not coerce a real
or string into an accepted integer/name. Existing safe diagnostics are reused;
no public backend signature or newer Java API is introduced.

The independent pypdf adapter applies typed, source-grounded predicates through
its separate parser. Its SubFilter predicate identifies that field directly.
The T80 graph projection resolves indirect extension values with a bounded,
cycle-checked traversal and does not change the historical T78 observer.
Five original positive cases and nine independent single-defect controls are
grounded in the audit's ISO 32000-1 §7.12/Tables 49–50 and EL3 §3.6.4 references;
neither author nor observer imports Folio implementation logic.

The initial memory/checkpoint and authorship fixes remain intact. Separately
pinned acceptance tools and profile adapters retain the R2 license, ownership,
identity and timeout assessment. No additional dependency, ownership,
Java 8 compatibility or clean-room provenance violation was identified.

Actual tests, complete gates, final unsigned-candidate certification and evidence
freshness remain the parent's separate delivery checks. Pending certification
is not a source Standards finding; this report does not certify an environment
or claim release publication.

Reviewed SHA-256 identities:

- T80 authority pin: `7c7ed55bbb3b1a1f898e50e381bb193e1df796b92a6a20677ced978e20df170a`
- `PdfBoxPasswordSecurity.java`: `c92f6b5cda7de1febc6b52ed44c6013fb7770e521dbcabfe9951cf1f8861074c`
- `t80-dictionary-check.py`: `3460ad4f5a33421fa31e30fa4ee44980fe9072614f0b888d3ec77eed70bfa49c`
- `t80-observer.py`: `9ad485707fb17607563d94d0a4aedb4c644d8c2a22a7b92e8da96709235a60ad`
- corpus author: `0605c4bdee9d14497f5e91b8b34da35330a1ec87a6ace727833c7a4f67ac94d8`
- control author: `3e33ad58faf3fa03c7d8625fc48d47ba387fa79299a5e34fe215af500d256f60`
- profile audit: `5efb3c016048fcfe1059e9b707362efc3a58cd1a3040b762efce1a40877a4e14`
