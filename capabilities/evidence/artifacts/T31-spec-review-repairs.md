Focused independent Spec follow-up: both original P2 findings are resolved. Final review gate remains open.

- PDF417 RAW ECI: the original `[901,65,927,26,300]` and `[924,0,0,0,0,0,927,26,300]` probes now fail with `BARCODE_INPUT_INVALID` and preserve the existing target in both execution profiles. The previously rejected `[901,927,26,65]` now publishes and independently decodes as `A`. Inspection confirms ECI remains within byte-compaction validation, with sticky 901 literal-tail state and complete 924 groups.
- DataMatrix RAW EDIFACT: `[240,5,240]` now automatically chooses 12×12 and independently decodes as `A`. Explicit 10×10 fails with `BARCODE_INPUT_INVALID` and preserves the existing target; explicit 12×12 still decodes as `A`. `[240,124]` now automatically selects 12×12 and preserves the empty decoded sequence. Both profiles behave identically. The capacity requirement is updated at every EDIFACT group, including a short tail after a full group, and enforced before symbol selection.

The unchanged original `RawProbe` and `DmRawProbe` were rerun against the repaired product classes; both processes exited 0. Inspection also covered the permanent valid/invalid Workflow cases. No additional finding arose from these repairs. No Maven run or implementation/test edit was performed by this reviewer.

Probe sources, outputs, PDFs, reviewed source snapshots, actual class hashes, command arguments and exit codes are retained under `.build-cache/t31/review-spec/repair-followup-1/`, indexed by `manifest.json`. Product classes were unchanged during these probes; original failing artifacts remain retained separately.

This focused closure does not close the final independent review gate. Fresh full Maven/JDK verification, expanded retained PDFium evidence, final inventories/documents and the final reviewed scope remain pending.
