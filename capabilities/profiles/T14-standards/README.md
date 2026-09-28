# T14 bounded declaration qualification

`qualification.json` freezes 56 project-authored malformed Sources for the 28
image/font/resource rule groups checked by `scripts/t14-observer.py` against
pinned qpdf graphs. `scripts/generate-t14-standards.py` reproduces them by
changing one documented field of the original positive corpus and rebuilding
the PDF cross-reference table. Each control retains its specific expected rule.

`pdfcpu.properties` selects 46 existing original Catalog/page/Form/resource
controls from T03/T09/T10 for strict offline pdfcpu 0.15.0. Their original
locations, hashes and exact required findings are preserved. All 102 controls
must be detected, and the positive image predicates must cover the entire
28-group union. Syntax errors, unsupported tools or an unqualified rule are not
substitutes for detection of the intended defect.

The [T14 contract](../../../docs/t14-certification.md) defines the scope and
standards sources. These predicates establish the closed extraction profile,
not general PDF conformance or arbitrary codec/font-program validity. Actual
public product, raw process and candidate/environment receipts are retained
separately by the Foundation certification runner.
