# T25-paragraph-publication

Capability: `composition.layout.paragraph-pagination`

Acceptance Profile: `T25-paragraph-publication`

Status: `experimental`

Release train: `0.1.0-SNAPSHOT`

standards evidence and compatibility Dependency Gates remain open.

Publication expires the original Session, and reopening the PDF supplies no semantic relayout state.

The expired Session rejects relayout with the existing IllegalStateException. The reopened PDF rejects it with COMPOSITION_RELAYOUT_UNSAFE. Both rejection paths run in the corpus producer and are recorded in semantic observations.

## Independent expectations

| Observation | Fixed expectation |
| --- | --- |
| Page count | 2 |
| Both page boxes | MediaBox/effective CropBox `[0, 0, 612, 792]`, unrotated |
| Page 1 complete text | `AAAAAAAAAAAA` |
| Page 2 complete text | `A` |
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
render configuration are recorded in the [page 1 profile](../profiles/T25-paragraph-publication-page-1-visual.properties)
and [page 2 profile](../profiles/T25-paragraph-publication-page-2-visual.properties).

## Public behavior and evidence

The public `DocumentWorkflow.execute` tests run in both execution profiles:
`publicationExpiresSessionAndReopenedPdfHasNoRelayoutState` in
[ParagraphPaginationWorkflowTest](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/ParagraphPaginationWorkflowTest.java).
The complete rule/default/conflict contract is
[Advanced paragraph pagination](../../docs/paragraph-pagination.md).

- [Syntax evidence](T25-paragraph-publication-syntax.md)
- [Semantic/structure evidence](T25-paragraph-publication-semantic.md)
- [Both visual comparisons](T25-paragraph-publication-visual.md)
- [Published product](artifacts/T25-paragraph-publication.pdf)
- [Independent reference](artifacts/T25-paragraph-publication-reference.pdf)

Missing tools or required observations yield INDETERMINATE. Syntax, semantic
and visual success do not close the missing standards or Foundation font/platform gates.
