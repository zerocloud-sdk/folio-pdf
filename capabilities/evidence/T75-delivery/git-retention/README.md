# T75 original Git evidence retention

The two `.gitattributes` rules preserve exact `.txt` and `.log` bytes below
`capabilities/evidence/T75-*`, following the existing T70–T72 convention.
Ordinary source and documentation whitespace checks remain unchanged.

Actual `git diff --no-index --check` observations failed with whitespace
diagnostics before the change (exit 3). After the change both logs were empty
(exit 1, because each added file still differs from `/dev/null`). The original
raw evidence hashes did not change. This closes the whitespace-specific control;
the final workspace and index checks remain separate delivery gates.

Independent [Standards](standards-report.md) and [Spec](spec-report.md) reviews
found no actionable issue. Both independently checked the 1,933 source and 30
contract identities against the complete host verification snapshot; all were
unchanged. The attributes file is outside those declared candidate input sets.

`original-archive-identities.json` records the original paths, archive digests,
and byte-verified member digests. The original pending-review receipt is retained
unchanged; `closure.json` records the later independent review outcome. Existing
coverage of 5,098 raw-whitespace files does not automatically prove coverage of
future outputs in other directory layouts.
