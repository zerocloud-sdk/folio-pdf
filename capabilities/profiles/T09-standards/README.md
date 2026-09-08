# T09 independent standards qualification

This profile freezes 51 rules for the actual T09 PDF 1.7 products. The union is
required independently by `T09IndependentEvidence`: 23 rules from pdfcpu strict
and 28 from Arlington. A checker only contributes rules after its real product
check and every hash-pinned illegal control produce the required diagnostic.
Missing rules, executables, models, control identities or uncertain results
cannot qualify. These are acceptance-only tools and project-authored fixtures.

The 22 Catalog, page-tree and Page rules from [T03](../T03-standards/README.md)
remain required and are rechecked for each T09 product. T09 adds 29 rules for
Contents, page-piece data, Info/date obligations, typed Catalog/viewer values,
and stream Filter/DecodeParms structures. The table below is the full union.

The normative scope is ISO 32000-1:2008 clauses 7.3–7.4, 7.5.5, 7.7.2–7.7.3,
12.2 and 14.3–14.5: Tables 5 (streams), 8 (LZW/Flate parameters), 15 (trailer),
28–30 (Catalog/Pages/Page), 150 (viewer preferences), 317 (Info), 318 (page-piece)
and 319 (application data). The authorized public standard is
[PDF 32000-1:2008](https://opensource.adobe.com/dc-acrobat-sdk-docs/standards/pdfstandards/pdf/PDF32000_2008.pdf).
Physical xref/Length/serialization is checked by qpdf, and exact private-value
meaning is checked by project-owned semantic expectations. Generic private
dictionaries admit all PDF value kinds; model traversal is not a claim that
arbitrary vendor entries have typed ISO constraints. This profile does not
certify unrelated feature dictionaries, PDF/A, PDF/UA or all stream filters.

## Actual qualification and known gaps

[The retained qualification observations](../../evidence/T71-standards-qualification/observations.json)
bind 62 real checker invocations, their unchanged input PDFs, executable hashes,
exit codes and raw output. They cover both tools against each of the 29 new
illegal controls, the original valid Source and a valid DecodeParms `[null]`
control. This is checker qualification, not candidate certification. Each final
candidate certification repeats all 51 selected rule controls for each product.

Arlington reports structural findings even with exit 0, so an exit code alone
never establishes validity. The pinned build misses the null LastModified and
null indirect Info controls; pdfcpu detects their required-date failures.
pdfcpu misses absent/direct Info in this page-piece document, unknown Filter
names, several DecodeParms types and the invalid Predictor controls; Arlington
detects them. Some pdfcpu Filter/DecodeParms failures happen during read-context
processing rather than its strict-validation diagnostic adapter, and are not
counted as qualified coverage. Both tools accept the valid null parameter slot.

Arlington selects its shared `FilterLZWDecode` parameter model for the tested
Flate Predictor. Qualification is restricted to the common ISO integer and
allowed-value rule (1, 2, 10–15); it does not extend to untested filter-specific
Colors, BitsPerComponent, Columns or other parameters. The position control
uses `[ASCIIHexDecode FlateDecode]` with `[null << /Predictor 9 >>]`, testing the
second parameter position with a legal null first slot. Count and member-type
controls are separate.

## Frozen rule/control mapping

| Rule | Required checker | Project-owned illegal control |
| --- | --- | --- |
| `catalog-lang-string` | arlington | [catalog-lang-string.pdf](fixtures/catalog-lang-string.pdf) |
| `catalog-page-mode-name` | arlington | [catalog-page-mode-name.pdf](fixtures/catalog-page-mode-name.pdf) |
| `catalog-page-mode-value` | arlington | [catalog-page-mode-value.pdf](fixtures/catalog-page-mode-value.pdf) |
| `catalog-pages` | pdfcpu | [catalog-pages.pdf](../T03-standards/fixtures/catalog-pages.pdf) |
| `catalog-pages-indirect` | pdfcpu | [catalog-pages-indirect.pdf](../T03-standards/fixtures/catalog-pages-indirect.pdf) |
| `catalog-type` | pdfcpu | [catalog-type.pdf](../T03-standards/fixtures/catalog-type.pdf) |
| `catalog-type-required` | pdfcpu | [catalog-type-required.pdf](../T03-standards/fixtures/catalog-type-required.pdf) |
| `data-lastmodified-date` | arlington | [data-lastmodified-date.pdf](fixtures/data-lastmodified-date.pdf) |
| `data-lastmodified-null` | pdfcpu | [data-lastmodified-null.pdf](fixtures/data-lastmodified-null.pdf) |
| `data-lastmodified-required` | arlington | [data-lastmodified-required.pdf](fixtures/data-lastmodified-required.pdf) |
| `info-moddate-date` | pdfcpu | [info-moddate-date.pdf](fixtures/info-moddate-date.pdf) |
| `info-moddate-required` | pdfcpu | [info-moddate-required.pdf](fixtures/info-moddate-required.pdf) |
| `page-contents-array-member` | arlington | [page-contents-array-member.pdf](fixtures/page-contents-array-member.pdf) |
| `page-contents-type` | arlington | [page-contents-type.pdf](fixtures/page-contents-type.pdf) |
| `page-mediabox-numbers` | pdfcpu | [page-mediabox-numbers.pdf](../T03-standards/fixtures/page-mediabox-numbers.pdf) |
| `page-mediabox-rectangle` | pdfcpu | [page-mediabox-rectangle.pdf](../T03-standards/fixtures/page-mediabox-rectangle.pdf) |
| `page-mediabox-required` | pdfcpu | [page-mediabox-required.pdf](../T03-standards/fixtures/page-mediabox-required.pdf) |
| `page-parent-indirect` | pdfcpu | [page-parent-indirect.pdf](../T03-standards/fixtures/page-parent-indirect.pdf) |
| `page-parent-link` | pdfcpu | [page-parent-link.pdf](../T03-standards/fixtures/page-parent-link.pdf) |
| `page-parent-required` | pdfcpu | [page-parent-required.pdf](../T03-standards/fixtures/page-parent-required.pdf) |
| `page-resources-required` | arlington | [page-resources-required.pdf](../T03-standards/fixtures/page-resources-required.pdf) |
| `page-resources-type` | pdfcpu | [page-resources-type.pdf](../T03-standards/fixtures/page-resources-type.pdf) |
| `page-type` | pdfcpu | [page-type.pdf](../T03-standards/fixtures/page-type.pdf) |
| `page-type-value` | pdfcpu | [page-type-value.pdf](../T03-standards/fixtures/page-type-value.pdf) |
| `pages-count-integer` | pdfcpu | [pages-count-integer.pdf](../T03-standards/fixtures/pages-count-integer.pdf) |
| `pages-count-nonnegative` | pdfcpu | [pages-count-nonnegative.pdf](../T03-standards/fixtures/pages-count-nonnegative.pdf) |
| `pages-count-required` | pdfcpu | [pages-count-required.pdf](../T03-standards/fixtures/pages-count-required.pdf) |
| `pages-count-value` | pdfcpu | [pages-count-value.pdf](../T03-standards/fixtures/pages-count-value.pdf) |
| `pages-kid-reference` | pdfcpu | [pages-kid-reference.pdf](../T03-standards/fixtures/pages-kid-reference.pdf) |
| `pages-kids` | pdfcpu | [pages-kids.pdf](../T03-standards/fixtures/pages-kids.pdf) |
| `pages-type` | arlington | [pages-type.pdf](../T03-standards/fixtures/pages-type.pdf) |
| `pages-type-value` | arlington | [pages-type-value.pdf](../T03-standards/fixtures/pages-type-value.pdf) |
| `piece-entry-dictionary` | arlington | [piece-entry-dictionary.pdf](fixtures/piece-entry-dictionary.pdf) |
| `pieceinfo-dictionary` | arlington | [pieceinfo-dictionary.pdf](fixtures/pieceinfo-dictionary.pdf) |
| `stream-decodeparams-array-member` | arlington | [stream-decodeparams-array-member.pdf](fixtures/stream-decodeparams-array-member.pdf) |
| `stream-decodeparams-count` | arlington | [stream-decodeparams-count.pdf](fixtures/stream-decodeparams-count.pdf) |
| `stream-decodeparams-position` | arlington | [stream-decodeparams-position.pdf](fixtures/stream-decodeparams-position.pdf) |
| `stream-decodeparams-type` | arlington | [stream-decodeparams-type.pdf](fixtures/stream-decodeparams-type.pdf) |
| `stream-filter-array-name-type` | arlington | [stream-filter-array-name-type.pdf](fixtures/stream-filter-array-name-type.pdf) |
| `stream-filter-array-name-value` | arlington | [stream-filter-array-name-value.pdf](fixtures/stream-filter-array-name-value.pdf) |
| `stream-filter-name-value` | arlington | [stream-filter-name-value.pdf](fixtures/stream-filter-name-value.pdf) |
| `stream-filter-type` | arlington | [stream-filter-type.pdf](fixtures/stream-filter-type.pdf) |
| `stream-flate-predictor-type` | arlington | [stream-flate-predictor-type.pdf](fixtures/stream-flate-predictor-type.pdf) |
| `stream-flate-predictor-value` | arlington | [stream-flate-predictor-value.pdf](fixtures/stream-flate-predictor-value.pdf) |
| `trailer-info-indirect` | arlington | [trailer-info-indirect.pdf](fixtures/trailer-info-indirect.pdf) |
| `trailer-info-null` | pdfcpu | [trailer-info-null.pdf](fixtures/trailer-info-null.pdf) |
| `trailer-info-required` | arlington | [trailer-info-required.pdf](fixtures/trailer-info-required.pdf) |
| `viewer-hide-toolbar-boolean` | arlington | [viewer-hide-toolbar-boolean.pdf](fixtures/viewer-hide-toolbar-boolean.pdf) |
| `viewer-numcopies-integer` | arlington | [viewer-numcopies-integer.pdf](fixtures/viewer-numcopies-integer.pdf) |
| `viewer-numcopies-positive` | arlington | [viewer-numcopies-positive.pdf](fixtures/viewer-numcopies-positive.pdf) |
| `viewer-preferences-dictionary` | arlington | [viewer-preferences-dictionary.pdf](fixtures/viewer-preferences-dictionary.pdf) |

The `.properties` files pin each control SHA-256 and the expected diagnostic.
Changing a rule, fixture, pin or matcher changes the certification inputs.
