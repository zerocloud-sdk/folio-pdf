# Source Spec review, third recheck

Baseline: `08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`; HEAD remains that commit.
Authority: `/workspace/contracts/issue-80-contract.md`.
Result: **PASS for reviewed source; no remaining in-scope source findings and no
scope creep identified.** Implementation and tracker remained untouched.
This conclusion does not certify the still-pending final candidate or full gates.

All initial and subsequent findings are resolved: invalid StdCF/Crypt parameter
streams are rejected; Crypt-only decoding succeeds; effective PDF 2.0 enforces
AESV3 CF Length; Catalog Version and routed names retain their PDF types; the
qualified PDF 1.7 extension declaration rejects string BaseVersion, real/string
ExtensionLevel, stream dictionaries and incorrect optional Type fields.

The extension recheck used the original two failing probes: public Workflow now
returns `PASSWORD_SECURITY_UNSUPPORTED`, and the independent raw adapter fails.
All five new typed/indirect extension positives pass independent raw predicates;
the three selected public probes return exact protected payloads. Independent qpdf
graphs resolve all five to `/BaseVersion /1.7`, integer ExtensionLevel 8. All nine
new extension controls produce their expected named findings. No historical T78
observer was modified to obtain these results.

The separately named acceptance-only qpdf overlay and its prior reviewed source,
archive, patch, package and runtime identities remain closed. Current authority
SHA-256 is `7c7ed55bbb3b1a1f898e50e381bb193e1df796b92a6a20677ced978e20df170a`;
review identity verification passes for 3,361 entries. Source recording still
requires all declared encrypted positives, actual Native/Facade products, four
chains, 111 scope controls and retained baseline qualification. Raw typed original,
credential/Perms, unauthenticated clear-content and exact-EF predicates remain
mandatory irrespective of supplemental Arlington applicability. Producer labels
and tool limitations do not replace those predicates.

The source stays within the attachment capability and matching Facade constants,
selection and observations, preserving secure defaults and established ownership,
permissions, version, publication, incremental, signature and Worker boundaries.
Aggregate security retains exactly its three existing members and requires current
candidate-bound evidence. No release-publication or tracker action is introduced.

Completion still requires the parent to record passing full verification, actual
JDK/environment/profile matrix, real-tool and collector gates, all predecessor
refreshes in contract order, current final attachment certifications, inventory
checks and the final criterion-mapped delivery receipt. This source PASS is not
a substitute for those observations.
