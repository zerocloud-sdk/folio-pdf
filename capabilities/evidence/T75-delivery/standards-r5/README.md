# CMap program qualification and incremental review closure

`program-cmaps-r1/qualification.json` records 61 actual pinned qpdf CLI
observations, with exact input, observer and report identities. Every expected
PASS, FAIL and INDETERMINATE agrees. All 33 generated CMap negative controls
produce their expected rule findings. Five original Sources, five legal
operator variants, eight work/coverage boundaries and ten independent review
fixtures retain their raw qpdf graph, version, stderr and result records.
`observer.py` preserves the exact observed checker source. This is development
qualification, not final staged-candidate or environment certification.

Both independent review axes closed the CMap findings after public Red/Green
regressions. The final helper extraction addresses duplicated prefix-intersection
logic; the 35 program/authoring tests remained green afterward. The original
Standards direct-font probe incorrectly edited object 4 instead of Resources
object 3 and was withdrawn. Its corrected probe and the independently authored
direct-font boundary establish the real nested-dictionary coverage. Historical
reports are preserved, including the withdrawal and correction.

The first owner-source test mutated ToUnicode object 19, which belongs to the
unchanged Encoding 17. Its FAIL expectation was incorrect. The corrected test
mutates object 20 belonging to narrowed Encoding 16; the source-domain check was
removed for a real corrected RED, then restored for GREEN. Independent review
confirmed both bindings and inherited-source cases. The first additional control
authoring run assumed an inherited Encoding child contained a cidchar block;
that child is empty. The corrected recipe inserts its test blocks at the known
program boundary. Logs whose names contain `green` can therefore contain failed
attempts; the actual result in each log is authoritative.

Review raw directories are archived without changing member bytes.
`archive-identities.json` records every archive and member hash; all members were
read back and compared with their originals. The scope still lacks binary font,
content and structure qualification, the combined recorder, final candidate
certification and the final independent Standards/Spec gate.
