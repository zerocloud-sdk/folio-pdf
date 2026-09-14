# Font layout, outline and name qualification

The public program/authoring suite passed 59 tests in 77.053 seconds, without
skips. The catalog now has 36 isolated negative controls. Additional rules
cover sfnt directory fields, ranges and padding; whole-font checksums; CFF
INDEX offsets; bounded Type 2 path execution; simple TrueType coordinate
bounds; embedded program names; and decoded FontFile2 length.

Independent Spec review found Type 1/3 CharStrings interpreted as Type 2.
Independent Standards review found conflicting platform PostScript names
hidden by the first English name record. Both regressions had actual public
CLI RED then GREEN. Original Type 1/3 probes now return INDETERMINATE, explicit
Type 2 remains PASS, and conflicting names return INDETERMINATE. Both axes
independently closed their original probes and adjacent controls. The four
archives preserve complete original and closure inputs, commands, raw qpdf
outputs, result records and identities. Every archived member was read back
and byte-compared with its original; `archive-identities.json` binds the bytes.

`observer.py` is the exact source at closure. All 26 incremental RED/GREEN logs
are retained, including failed test attempts. The first program-name attempt
used an unequal-length TrueType replacement; `r2-red` is the corrected real
false-PASS reproduction. The first full layout suite exposed a classification
regression for a missing required Top DICT; the focused required-INDEX fix and
subsequent full suite pass. A filename containing `green` never overrides its
recorded test outcome.

This is an incremental qualification of explicit rules, with unsupported font
grammars remaining INDETERMINATE. Font metadata/metric and owner checks,
content/structure qualification, the combined recorder, final certification
and final clean-context review remain outstanding. No criterion is checked
and no certification is promoted here.
