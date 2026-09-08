# T71 second candidate — historical index publication failure

This directory preserves the actual unsigned build command, log and receipt
for candidate `e0b87341aaf0564eb2bf44dcabb9d90f665e5bb9a961d4d569ab35b653c2af63`
and contract `717a4baa6f11807aa8044c8306813b627b7e120b06bcf88f3b5ab1e7acebd750`.
Its [eight values observations](../foundation/T71-values-r2/) passed all 83
required tests and four chains per scope, but the final index publication step
failed. The current authority was not replaced by this attempt.

The [correction record](../T71-index-correction/README.md) retains the failure,
public regressions and controlled historical replay. These original artifacts
and identities remain historical. The runner correction changes source bytes,
so final certification requires a newly staged candidate and fresh observations.
