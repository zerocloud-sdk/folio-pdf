# Spec review — delivery timeout recovery

Baseline: `df2df726a2695267bf63a7c9df49a4a7481f0169`. Focused read-only review of the six delivery helpers, original timeout wrapper/diagnosis/ledger, retained reuse-control results, and live merge validator against the entire authoritative contract. No builds, tests, certification runs or index edits performed.

No concrete applicable finding identified in this recovery change.

Execution step 5 permits reuse only when “all candidate, contract, environment, configuration and transitive report identities still match.” `resume-attachments.py` preserves the six original JDK8/11/17 configurations and qualified producer labels, verifies their complete 24-chain transitive closure through the existing strict merge validator, compares the regenerated actual command/configuration, and requires freshly observed environments to equal the original observations. Candidate/source/staged-build guards remain in force. The seven retained mutation controls reject missing/duplicate scopes, missing chains and altered configuration, report, environment or candidate identities. Both JDK21 tuples execute afresh; partial original JDK21 output is excluded.

The original 10,800-second failure remains failed in its original ledger, with wrapper, log and diagnosis retained. The continuation changes only the delivery wrapper budget to 21,600 seconds. It preserves the ten completed obligations and proceeds attachments → limits → worker, stopping on failure, retaining prior-index hashes and using the existing locked, atomic local evidence-index update. It neither changes Worker limits nor expands shipping or publication scope.

`audit-final.py` requires the complete ordered refresh, final scoped counts, original/fresh mandatory transcripts, identity-bound final gates and preserved predecessor readiness before emitting its audit. Receipt rendering leaves C19/C20 pending until final review. This report approves only recovery logic; the running continuation, final evidence/gates, closing ownership audit and final Standards/Spec dispositions remain to be verified.
