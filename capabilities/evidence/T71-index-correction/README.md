# T71 evidence index correction

These are development observations, not final-candidate certification. The
[second attempt](../T71-index-attempt/) completed eight T09 scopes, then rejected
index publication. The runner treated an embedded `/workspace` tool-observer
path in raw environment JSON as a repository reference. The real authority kept
SHA-256 `1d3a30c58ac868a06ca249c76f32ddc5264faa3cd709633158a36691759123e9`.

| Observation | Actual result |
| --- | --- |
| [Public index replay RED](index-publish-red.txt) | Existing complete observations fail publication. |
| [Isolated full-graph RED](full-index-replay-red.txt) | The same public command rejects the preserved graph. |
| [Raw-observation RED](native-observation-red.txt) | A minimal CLI index fails on unchanged raw payloads containing tool namespace paths. Changed bytes already reject. |
| [First GREEN](native-observation-green.txt) | All 18 runner tests pass; independent review subsequently finds the exception too broad. |
| [Scope RED](observation-scope-red.txt) | Three assertions fail: the same field name in a configuration, certification record and nested finding wrongly suppresses recursive verification. |
| [Minimal GREEN](observation-scope-green.txt) | All 19 runner tests pass. |
| [Refactor GREEN](observation-scope-refactor-green.txt) | All 19 runner tests remain passing after sharing the ancestor set. |
| [Full-graph GREEN](full-index-replay-final.txt) | Public replay exits 0, retains eight historical scopes and their original identities, and leaves the real authority unchanged. |
| [Historical actual certification attempt](historical-values-r2.txt) | Eight actual T09 observations pass; the final index step fails. |

The public regression command is
`python3 -m unittest discover -s scripts/tests -p '*foundation*.py'`.
The replay invokes `scripts/t03-foundation.py merge-index` against an isolated
root with the original source, contract, staged artifacts, harness and recorded
observations. The command used `.build-cache/t71-index-replay` in the isolated
candidate worktree. The verified graph is also archived under
`/home/ubuntu/IdeaProjects/open-pdf/.build-cache/t71/index-replay-r2` in the
delivery workspace; all 1,032 source/artifact/contract/harness
references matched the second attempt's build receipt when archived. Its index
is a regression result and is never copied into the actual authority.

The fix assigns the report role only through an actual certification record's
root `report` reference. Only that report's root `environment-observations` list
contains opaque raw payloads. Every referenced file still receives repository
containment and exact-hash checks. All other references retain recursion.

Independent reviewer `/root/t71_final_standards` closed the P2 finding after
checking the correction, actual RED/GREEN/Refactor logs, successful full replay
and unchanged real-authority hash. No Standards or smell finding remains in
this correction. Final candidate certification and dual-axis signoff remain
separate gates; no completion criterion is marked here.

Independent reviewer `/root/t71_final_spec` also reported no Spec source
findings, reran all 19 public CLI tests successfully, and independently
rehashed all 1,032 archived references with the original identities. The
reviewer's evidence-location clarification is incorporated above.
