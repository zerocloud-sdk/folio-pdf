# Spec precheck — final attachment tuple recovery

Baseline: `05e7f546f5885680e333ad8d3dea3645b25d26b7`.
Read-only review against the sole issue-81 contract of `resume-attachments-last-final-r2.py` and `diagnose-attachments-jdk21-worker.py`, including their differences from the previously reviewed relaunch helpers and retained r3 tuple records. Neither helper was executed by this reviewer.

No concrete Spec blocker:

- For “Preserve their declared execution coverage and historical records,” the recovery retains the five original complete tuples plus r3 JDK17 Worker and JDK21 IN_PROCESS. Both added tuples have passing complete 24-test transcripts and four distinct producer-bound chain records at the current candidate identity. The failed r3 JDK21 Worker transcript reports 24 tests and one failure; no complete chain records exist for that tuple. The helper never substitutes the diagnostic or failed tuple for certification.
- For “Each binds exact … source/contracts, candidate artifacts, harness … and actual tool/native identities,” the helper validates retained configuration inputs, candidate/environment/contract/execution hashes, original recorder and full-suite commands, JVM options/settings and chain producers. It re-observes all four JDK environments before and after, compares original before/available closing identities, and preserves original environment references, including r3's JDK21 record.
- For “Retain original findings and artifact hashes,” both complete original trees are sealed, including interrupted and failed transcripts. Fresh output is mandatory. The original-file, unchanged candidate/staged-build and unchanged authority-byte guards remain before publication; existing transitive hash validation remains active.
- For “Candidate changes invalidate prior evidence honestly,” no bound candidate input changed. Only the entire missing JDK21 Worker tuple is freshly executed through the existing complete suite, recorder and four-chain collector. The helper requires precisely eight attachment scopes with seven retained and one fresh, proves an exact 88-scope union retaining all 80 accepted predecessors, and uses unchanged locked atomic publication.
- The JDK21 diagnostic copy changes tuple/output paths and the observed failure description only. It retains the exact original image, settings, JVM flags, full 24-test suite and 600-second diagnostic bound. Its record explicitly remains diagnostic-only; neither a passing replay nor pressure snapshots establish a cause, fix or certification.

No tests or input edits by this reviewer. Diagnostic completion, successful fresh tuple execution/publication, four final limits certifications, final gates and actual delivery review remain pending.
