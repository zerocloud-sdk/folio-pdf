# Source review dispositions

Review fixed point: `df2df726a2695267bf63a7c9df49a4a7481f0169`.
Review includes the final working tree and new files; no local commit is authorized.

| Axis / finding | Disposition | Evidence of correction or rationale |
| --- | --- | --- |
| Spec R1: actual owned-file permissions and real Worker link observations | Fixed before final staging | At public STAGED, the recorder checks every actual owned regular file for exact 0600 and requires a nonzero count. Existing real Worker probes attempt read/write against parent-prepared hard-link and symlink markers; parent access succeeds and external sentinel bytes remain unchanged. Creation denial remains explicitly qualified in the separate policy JVM. |
| Spec R1: hardcoded Publication Receipt fields | Fixed before final staging | Native/public fault/prerequisite recorders validate and serialize actual target names, statuses and partial-output flags. The collector requires those exact ordered observations. Guard tests reject wrong target names, status and partial-output flags. |
| Spec R2: inherited Facade receipt serializer | Fixed before final staging | The shared acceptance-only Facade recorder now validates and serializes every actual target name, status and partial-output flag, including its successful stream receipt. Both T20 and T21 expectations bind those observations. Collector guard tests reject forged names and flags for successful, committed, failed and unattempted targets. |
| Standards R1: possible duplicated pin helper | Retained, bounded judgment finding | The small repository-only helper follows the established T20 recorder pattern. Keeping profile-specific pin observation local avoids changing a predecessor recorder while certifying the new boundary. Both are governed by separate pinned inputs and complete staged source identity; this adds no shipping API or runtime duplication. |
| Standards recovery: possible duplicated tuple-observation sequence | Accepted, bounded judgment finding | The ticket-only continuation uses the unchanged driver's strict validator and observation primitives. Copying the small remaining JDK21 sequence avoids altering the frozen general runner and invalidating already verified predecessor records. It remains a delivery artifact, not a second general certification path. [Recovery review](standards-recovery.md) records no documented breach. |
| Spec recovery: identity reuse and retained timeout | No applicable finding | [Recovery review](spec-recovery.md) confirms original six-tuple identities/configurations/producers remain unchanged, all four environments are reobserved, both JDK21 tuples run fresh, seven mutation controls reject alteration, and the original failed gate stays failed. Final gates and evidence review remain required. |

Final [Standards](standards-final.md) and [Spec](spec-final.md) reviews confirm
these dispositions against the completed candidate, retained four-tuple
observations and live readiness. Both report zero unresolved applicable findings;
the two bounded duplication judgments remain accepted. No further source,
contract, artifact, harness, tool or configuration change was required.

The JDK 8 rehearsal revealed two legitimate vendor labels for the same pinned
executable: release IMPLEMENTOR is Eclipse Adoptium and runtime java.vendor is
Temurin. The environment observation retains both actual values independently.
Worker launcher validation compares the child runtime property against the
observed parent runtime property, while the immutable environment authority
continues to bind the release implementor, exact build and executable hash.
Inventory validation recognizes these runtime/launcher witnesses and the actual
prerequisite control command, requires them for Worker certification, and rejects
missing or malformed witnesses. No historical observation was relabeled.
