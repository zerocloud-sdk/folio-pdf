# Frozen T10 rule catalog

| Rule | Required checker | Normative requirement |
| --- | --- | --- |
| `annotation-contents-string` | pdfcpu | ISO 32000-1:2008 Table 164: Contents is a text string |
| `annotation-flags-integer` | pdfcpu | ISO 32000-1:2008 Table 164: F is an integer |
| `annotation-name-string` | pdfcpu | ISO 32000-1:2008 Table 164: NM is a text string |
| `annotation-page-indirect` | pdfcpu | ISO 32000-1:2008 Table 164: P is an indirect page reference |
| `annotation-page-type` | pdfcpu | ISO 32000-1:2008 Table 164: P refers to a Page object |
| `annotation-rect-numbers` | pdfcpu | ISO 32000-1:2008 Table 164: Rect has four numbers |
| `annotation-rect-rectangle` | pdfcpu | ISO 32000-1:2008 Table 164: Rect has four coordinates |
| `annotation-rect-required` | pdfcpu | ISO 32000-1:2008 Table 164: Rect is required |
| `annotation-subtype-name` | pdfcpu | ISO 32000-1:2008 Table 164: Subtype is a name |
| `annotation-subtype-required` | pdfcpu | ISO 32000-1:2008 Table 164: Subtype is required |
| `annotation-type-name` | pdfcpu | ISO 32000-1:2008 Table 164: Type is a name |
| `annotation-type-value` | pdfcpu | ISO 32000-1:2008 Table 164: the optional Type is Annot |
| `catalog-names-dictionary` | pdfcpu | ISO 32000-1:2008 Table 28: Names is a dictionary |
| `catalog-pages` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `catalog-pages-indirect` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `catalog-type` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `catalog-type-required` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `destination-fit-count` | arlington | ISO 32000-1:2008 Table 151: a Fit destination contains only a page and the Fit name |
| `destination-mode-name` | pdfcpu | ISO 32000-1:2008 Table 151: destination mode is a name |
| `destination-mode-value` | pdfcpu | ISO 32000-1:2008 Table 151: destination mode is one of the defined views |
| `destination-page-indirect` | pdfcpu | ISO 32000-1:2008 12.3.2.2: local destinations use an indirect page reference |
| `destination-page-type` | arlington | ISO 32000-1:2008 12.3.2.2: local destinations refer to a Page object |
| `destination-value-type` | pdfcpu | ISO 32000-1:2008 12.3.2.3: a named destination is an array or dictionary |
| `form-bbox-numbers` | pdfcpu | ISO 32000-1:2008 Table 95: BBox has numeric coordinates |
| `form-bbox-rectangle` | pdfcpu | ISO 32000-1:2008 Table 95: BBox has four coordinates |
| `form-bbox-required` | pdfcpu | ISO 32000-1:2008 Table 95: BBox is required |
| `form-formtype-integer` | pdfcpu | ISO 32000-1:2008 Table 95: FormType is an integer |
| `form-formtype-value` | pdfcpu | ISO 32000-1:2008 Table 95: FormType is 1 |
| `form-matrix-array` | pdfcpu | ISO 32000-1:2008 Table 95: Matrix is an array |
| `form-matrix-count` | pdfcpu | ISO 32000-1:2008 Table 95: Matrix contains six numbers |
| `form-matrix-numbers` | pdfcpu | ISO 32000-1:2008 Table 95: Matrix contains numbers |
| `form-resources-dictionary` | pdfcpu | ISO 32000-1:2008 Table 95: Resources is a dictionary |
| `form-subtype-name` | pdfcpu | ISO 32000-1:2008 Table 95: Subtype is a name |
| `form-subtype-required` | pdfcpu | ISO 32000-1:2008 Table 95: Subtype is required |
| `form-type-name` | pdfcpu | ISO 32000-1:2008 Table 95: Type is a name |
| `form-type-value` | pdfcpu | ISO 32000-1:2008 Table 95: the optional Type is XObject |
| `names-dests-tree` | pdfcpu | ISO 32000-1:2008 Table 31: Dests is a name tree |
| `nametree-key-order` | arlington | ISO 32000-1:2008 Table 36: name-tree keys are in lexical order |
| `nametree-key-order-high-bytes` | arlington | ISO 32000-1:2008 7.9.6: name-tree keys are compared byte by byte |
| `nametree-key-string` | pdfcpu | ISO 32000-1:2008 Table 36: a name-tree key is a string |
| `nametree-key-unique` | arlington | ISO 32000-1:2008 7.9.6: name-tree key ranges do not overlap |
| `nametree-key-unique-encoding` | arlington | ISO 32000-1:2008 7.9.6: key equality compares bytes, not the literal or hexadecimal notation |
| `nametree-names-array` | pdfcpu | ISO 32000-1:2008 Table 36: Names is an array |
| `nametree-pair-count` | pdfcpu | ISO 32000-1:2008 Table 36: Names contains key-value pairs |
| `nametree-root-entry` | arlington | ISO 32000-1:2008 7.9.6: the root contains either Names or Kids |
| `page-annots-array` | pdfcpu | ISO 32000-1:2008 Table 30: Annots is an array |
| `page-annots-member` | pdfcpu | ISO 32000-1:2008 12.5.2: Annots contains annotation dictionaries |
| `page-artbox-numbers` | pdfcpu | ISO 32000-1:2008 Table 30: ArtBox has numeric coordinates |
| `page-artbox-rectangle` | pdfcpu | ISO 32000-1:2008 Table 30: ArtBox has four coordinates |
| `page-bleedbox-numbers` | pdfcpu | ISO 32000-1:2008 Table 30: BleedBox has numeric coordinates |
| `page-bleedbox-rectangle` | pdfcpu | ISO 32000-1:2008 Table 30: BleedBox has four coordinates |
| `page-contents-array-member` | arlington | T09 ISO Contents/Filter rules |
| `page-contents-nonempty` | pdfcpu | ISO 32000-1:2008 Table 30: a writer shall not create an empty Contents array |
| `page-contents-type` | arlington | T09 ISO Contents/Filter rules |
| `page-cropbox-numbers` | pdfcpu | ISO 32000-1:2008 Table 30: CropBox has numeric coordinates |
| `page-cropbox-rectangle` | pdfcpu | ISO 32000-1:2008 Table 30: CropBox has four coordinates |
| `page-mediabox-numbers` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `page-mediabox-rectangle` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `page-mediabox-required` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `page-parent-indirect` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `page-parent-link` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `page-parent-required` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `page-resources-required` | arlington | T03 ISO Catalog/Pages/Page rules |
| `page-resources-type` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `page-rotate-integer` | pdfcpu | ISO 32000-1:2008 Table 30: Rotate is an integer |
| `page-rotate-value` | pdfcpu | ISO 32000-1:2008 Table 30: Rotate is a multiple of 90 |
| `page-trimbox-numbers` | pdfcpu | ISO 32000-1:2008 Table 30: TrimBox has numeric coordinates |
| `page-trimbox-rectangle` | pdfcpu | ISO 32000-1:2008 Table 30: TrimBox has four coordinates |
| `page-type` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `page-type-value` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `pages-count-integer` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `pages-count-nonnegative` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `pages-count-required` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `pages-count-value` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `pages-kid-reference` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `pages-kids` | pdfcpu | T03 ISO Catalog/Pages/Page rules |
| `pages-type` | arlington | T03 ISO Catalog/Pages/Page rules |
| `pages-type-value` | arlington | T03 ISO Catalog/Pages/Page rules |
| `resource-xobject-map` | pdfcpu | ISO 32000-1:2008 Table 33: XObject is a dictionary |
| `resource-xobject-stream` | pdfcpu | ISO 32000-1:2008 7.8.3 and 8.8.1: XObjects are streams |
| `stream-filter-array-name-type` | arlington | T09 ISO Contents/Filter rules |
| `stream-filter-array-name-value` | arlington | T09 ISO Contents/Filter rules |
| `stream-filter-name-value` | arlington | T09 ISO Contents/Filter rules |
| `stream-filter-type` | arlington | T09 ISO Contents/Filter rules |
