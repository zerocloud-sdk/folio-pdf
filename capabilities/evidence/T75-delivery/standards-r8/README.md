# Font descriptor bounds and independent review closure

The program, authoring, new text-plan and existing T12 recorder suites pass
68 tests in 100.358 seconds without skips. The font catalog has 45 isolated
controls. New rules compare independently drawn glyph bounds with the PDF
FontBBox, normalize either diagonal order, resolve indirect coordinates,
validate TrueType unitsPerEm before scaling, and require CID FDArray/FDSelect.
Actual RED precedes each behavior and authoring change; the logs are retained.

Independent Standards and Spec review found indirect coordinates incorrectly
rejected, missing FDSelect without a result record, incorrect CID matrix
composition and floating-point false failures at exact CFF matrix boundaries.
Indirect coordinates now pass; missing FDArray and FDSelect produce explicit
FAIL records. General CFF matrix handling was removed from this qualification:
explicit Top DICT or selected FD FontMatrix values return INDETERMINATE. This
is an acceptance-tool boundary; product extraction behavior is unchanged and
all required original corpus CFF fonts use implicit default matrices.

Both axes independently closed their original probes. The four archives
preserve original and closure inputs, commands, results and tool identities.
Every member was read back and byte-compared; `archive-identities.json` binds
all files. `observer.py` is the exact source at closure. Twenty incremental
logs plus the full-suite log are retained. The earlier 62-test pass preceded
the review fixes and does not override the original review failures.

This remains development qualification. Width and further owner checks,
content/structure qualification, complete text recording and collection, final
environment certification, earlier-obligation refresh and final review remain
outstanding. No completion checkbox or certification is promoted.
