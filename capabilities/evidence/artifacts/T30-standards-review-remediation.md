# T30 Standards review — preliminary remediation supplement

New hard violations: **0**. New optional observations: **0**. The initial report's one optional geometry-readability observation remains unchanged.

Reviewed the remediation against the initial scope and retained source/build-input hashes. Twelve non-evidence files changed: the private encoder integration, public Javadoc, public Workflow regression, acceptance fixtures/tests, CLI count, English/Chinese/profile documentation, usage summaries and provenance. The current 318-file acceptance-source declaration SHA-256 is `363252a35e3942965c7300eb810243434d999ea8242eea4eb3680301197f625e`; independently recomputing it matches the fresh recorder's declaration.

- `PdfBoxBarcodeOperations.java:131`: selecting the existing `Code128.CodeSet.AB` for AUTO input containing explicit FNC4 stays behind the existing private integration. It preserves ADR-0009/0010's module ownership and fixed Okapi dependency. Public signatures, resource ownership and checked failure boundaries remain unchanged, consistent with CONTRIBUTING.md and ADR-0025.
- `BarcodeWorkflowTest.java:178`: the new numeric shift/latch regression generates, publishes, reopens and independently decodes through `DocumentWorkflow.execute` in both profiles. This follows CONTRIBUTING.md's public behavioral seam. The retained remediation logs show the regression failing before repair and passing afterward, as required by the user's Red→Green discipline.
- `T30BarcodeProfile.java` and the updated acceptance tests add four literal payload/codeword/checksum/caption declarations. Expected values remain separate from product encoding, preserving ADR-0023's independent evidence boundary. Javadoc and both language contracts disclose the AUTO A/B behavior.

Independently verified the remediation archive and all six contained log hashes, plus all three focused-log hashes. Raw results match 90 Workflow tests with no failures/errors/skips, six acceptance tests with one explicit raster skip, and one passing CLI test. No Maven commands or implementation edits were performed during this follow-up.

This is a preliminary source review. The initial 112-page artifact records remain historical; the 116-page recording, inventory/evidence relinking, fresh root verification, full JDK matrix and final immutable-worktree audit are pending. Those checks remain necessary under CONTRIBUTING.md and ADR-0023/0029. No completion criterion or compatibility promotion is approved here, and this report makes no separate Spec determination.
