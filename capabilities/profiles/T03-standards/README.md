# T03 ordinary-PDF standards rules

The blank-document profile requires the ISO 32000-1:2008 document-structure
rules below. It uses an ordinary PDF 1.7 document with a Catalog, one flat
page-tree root, blank Letter pages and empty Resources. The semantic chain
must establish that scope. Encryption, signatures, content programs, fonts,
annotations, forms, metadata and PDF/A or PDF/UA claims are outside this
fixture; their later profiles need their own coverage. This does not exempt
them from the Foundation Release.

Physical serialization (header, indirect objects, object/xref streams, stream
lengths, offsets and trailer decoding) belongs to the independent **syntax**
chain. Its qpdf result is never reported as standards evidence. The standards
chain checks the decoded object model and page-tree relationships using two
separately versioned external checkers. Neither checker's generic successful
exit establishes this profile's coverage.

The correctness authority is [ISO 32000-1:2008, clauses 7.7.2 and 7.7.3,
Tables 28–30](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf).
The [pdfcpu strict-mode policy](https://pdfcpu.io/core/validate/) explicitly
limits its claim to implemented rules and permits reader recovery. The
[Arlington model and TestGrammar](https://github.com/pdf-association/arlington-pdf-model/tree/fe4a1a8897ec07f674c73160c35d748b29052f8f)
provide independent dictionary requirements, type/value rules and inheritance
checks. Arlington's embedded parser repairs some Count defects; pdfcpu supplies
the Count and parent-link checks. Unqualified predicates and warnings cannot
produce PASS.

| Required rule IDs | Requirement | Qualified checker |
| --- | --- | --- |
| `catalog-type-required`, `catalog-type` | Catalog Type is present and has the Catalog name value. | pdfcpu |
| `catalog-pages`, `catalog-pages-indirect` | Catalog Pages exists and refers indirectly to the page tree. | pdfcpu |
| `pages-type`, `pages-type-value` | Root Type exists and is Pages. | Arlington |
| `pages-kids`, `pages-kid-reference` | Kids is an array of indirect page-tree references. | pdfcpu |
| `pages-count-required`, `pages-count-integer`, `pages-count-nonnegative`, `pages-count-value` | Count is a present, nonnegative integer matching the actual leaf count. | pdfcpu |
| `page-type`, `page-type-value` | Each page has the Page type. | pdfcpu |
| `page-parent-required`, `page-parent-indirect`, `page-parent-link` | Parent is present, indirect and identifies the containing page-tree node. | pdfcpu |
| `page-resources-required` | Resources is present directly or by inheritance. | Arlington |
| `page-resources-type` | Resources is a dictionary. | pdfcpu |
| `page-mediabox-required`, `page-mediabox-rectangle`, `page-mediabox-numbers` | The effective MediaBox exists and has four numeric coordinates. | pdfcpu |

The union is **22 required rules**. Both [pdfcpu.properties](pdfcpu.properties)
and [arlington.properties](arlington.properties) are mandatory, with their
complete required-rule sets. Every rule has a hashed project-owned negative
PDF and a diagnostic that the actual qualified checker must emit. A missing
rule, control, tool, version, executable/model identity or reliable finding
leaves standards INDETERMINATE. An observed invalid product is FAIL.

The fixtures are small, independently authored PDF objects serialized with
ordinary byte offsets; no product or Reference Suite output is the fixture
authority. `fixtures/valid.pdf` contains three indirect objects. Every other
fixture changes the single named rule (some defects necessarily have secondary
consequences) and recomputes the xref offsets. They are Apache-2.0 project data.
The exact bytes and hashes are retained in this directory and the properties.

The initial tool-qualification reports are in
[T70-standards-qualification](../../evidence/T70-standards-qualification/README.md).
Those reports qualify rules on this corpus; they are not final candidate or
environment certification. Every final T03 certification must rerun both
checkers and their controls against its actual product and record separate
tool findings, hashes and execution configuration.
