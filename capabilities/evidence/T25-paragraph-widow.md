# T25-paragraph-widow

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-widow`

Status: `experimental`

Release train: `0.1.0-SNAPSHOT`

standards evidence and compatibility Dependency Gates remain open.

A four-line paragraph splits 2/2 instead of 3/1 when widows is 2.

Default 1. Every continuation fragment, including the final one, must meet the minimum; an unsplit short paragraph need not. Recompute on width changes; do not silently drop or duplicate lines.

## Independent expectations

| Observation | Fixed expectation |
| --- | --- |
| Page count | 2 |
| Both page boxes | MediaBox/effective CropBox `[0, 0, 612, 792]`, unrotated |
| Page 1 complete text | `AA` |
| Page 2 complete text | `AB` |
| Font size and advances | 40 points; A=24, B=26, space=10, omega=28 |
| Geometry tolerance | 0.0001 point for every matrix, advance and baseline |
| Primary visual bound | ImageMagick AE 0, zero fuzz, 144 DPI, opaque white sRGB |
| Secondary renderer bound | 2500; exact changed RGB pixels must also stay within this bound |

The full hand-specified per-run coordinates are in
[T25ParagraphExpectations](../../pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T25ParagraphExpectations.java).
This oracle imports no paragraph declarations or layout implementation.
[T25ParagraphProducts](../../pdf-acceptance/src/main/java/net/zerocloud/pdf/acceptance/T25ParagraphProducts.java)
authors the independent reference using only blank pages and positioned text.
The source font hashes are fixed in the [aggregate record](T25-paragraph-pagination.md).

Both selected pages are mandatory. Their immutable raster hashes and complete
render configuration are recorded in the [page 1 profile](../profiles/T25-paragraph-widow-page-1-visual.properties)
and [page 2 profile](../profiles/T25-paragraph-widow-page-2-visual.properties).

## Public behavior and evidence

The public `DocumentWorkflow.execute` tests run in both execution profiles:
`widowMinimumMovesOneEarlierLineAcrossThePageBoundary; widowAndOrphanCountsRecomputeForDifferentPageWidths; incomingAndOutgoingFragmentMinimaApplyToEveryPage; impossibleKeepsAndFragmentMinimaFailWithoutPublishing` in
[ParagraphPaginationWorkflowTest](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/ParagraphPaginationWorkflowTest.java).
The complete rule/default/conflict contract is
[Advanced paragraph pagination](../../docs/paragraph-pagination.md).

- [Syntax evidence](T25-paragraph-widow-syntax.md)
- [Semantic/structure evidence](T25-paragraph-widow-semantic.md)
- [Both visual comparisons](T25-paragraph-widow-visual.md)
- [Published product](artifacts/T25-paragraph-widow.pdf)
- [Independent reference](artifacts/T25-paragraph-widow-reference.pdf)

Missing tools or required observations yield INDETERMINATE. Syntax, semantic
and visual success do not close the missing standards or Foundation font/platform gates.
