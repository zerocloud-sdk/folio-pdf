# T75 complete chain-record reader correction

Final inventory generation exposed eight text standards records of
5,290,339–5,399,862 bytes, each retaining 18,252 negative-control references.
The actual r3 generate, validate and check commands exited 0, but the generated
view blocked text because the inventory reader allowed only 3,000,000 code
points. The separate required-text assertion failed. Those original results
are preserved; successful inventory command exits did not complete text readiness.

The correction gives only chain records a 16 MiB accepted-input byte budget.
It checks every read chunk before retaining it and parses only after complete
EOF. The strict YAML options and all subsequent record, producer, report,
control and identity checks remain active. Ordinary authorities and execution
configurations retain their separate 3,000,000-code-point limit. The byte limit
is an accepted-input budget: an excess read may consume one 8 KiB chunk, array
copies and YAML objects use additional heap, and the preceding identity check
still hashes the original file. No product runtime or dependency changed.

The original public-command history is retained in
`diagnosis-and-focused-validation.tar.xz` with all 83 archived members compared
byte-for-byte against their sources:

- The initial trailing-comment attempt exited 0 and did not reproduce the bug.
- Leading comment lines before record tokens reproduced the actual parser
  failure; the same test passed after the separate chain parser budget.
- The exact 16 MiB case passed, but its first excess byte was wrongly accepted
  by the parser-only change. Complete byte-bounded reading closes that RED.
- Three final regressions passed, including altered control bytes, a failed
  record and the unchanged ordinary-authority limit.
- Complete inventory module verification passed 17 tests with no failures,
  errors or skips, and its generated-document check passed.
- The actual large r3 records now parse without YAML errors; precisely the five
  changed source identities still block the historical candidate correctly.

`diagnosis-and-validation-receipt.json` retains commands, scope and report
boundaries. Intermediate Maven XML was not captured for every run; original
logs/results and the specifically captured phase/module reports are distinct.
The five-file source freeze contains three original before snapshots and two
exact inverse reconstructions checked against independently retained r3 hashes.
No missing original observation is represented as recovered.

The independent [Standards report](standards-review-report.md) finds zero hard
violations and zero open judgement findings. Its 245 original files, including
the 244-entry manifest, are byte-verified in `standards-review.tar.xz`; three
original finalization files are retained separately to avoid circular hashes.
The independent [Spec report](spec-review-report.md) finds zero actionable
issues. Its 259 original files, including the 258-entry manifest, are retained
in `spec-review.tar.xz`. Reviewer diagnostics and their separate corrections
remain unchanged. Detailed receipts and archive identities are adjacent.

Both reviews use the fixed full-task baseline
`5b1603c435f11c40368f75b7b9a2777c9c5e9761`, the explicit precommit working-tree
adaptation, and the exact five-file increment over r3. All 1,933 source inputs
were checked; only the listed five changed, and all 30 contract inputs remained
unchanged. This closes only the correction. Complete host/four-JDK validation,
fresh certification of all 48 tuples, text-satisfied inventory, final review,
and authorized DCO delivery remain required. No criterion is marked complete.

`input-delta-from-original-full-review.json` mechanically links the current
freeze to the original full source review: 26 source inputs and one contract
input changed through the separately retained appearance, observer-budget,
Worker-batch and current inventory closures. Every other frozen identity
remains equal. This continuity table supplements the independent original
reports; it does not replace their conclusions or the new certification gate.
