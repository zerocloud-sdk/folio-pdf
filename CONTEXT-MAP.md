# Context Map

## Program Language

- [Open PDF Program](./CONTEXT.md) — owns suite-wide identity, interface, and release language

## Contexts

- [Document Engine](./contexts/document-engine/CONTEXT.md) — owns PDF reading, writing, pages, resources, and document lifecycle
- [Composition](./contexts/composition/CONTEXT.md) — owns layout, typography, graphics, and barcodes
- [Forms](./contexts/forms/CONTEXT.md) — owns AcroForm, XFA, and interactive field behavior
- [Trust](./contexts/trust/CONTEXT.md) — owns encryption, signatures, timestamps, and validation
- [Conformance](./contexts/conformance/CONTEXT.md) — owns archival and accessibility standards
- [Conversion](./contexts/conversion/CONTEXT.md) — owns HTML, SVG, OCR, rendering, and Office conversion
- [Sanitization](./contexts/sanitization/CONTEXT.md) — owns secure redaction and optimization
- [Migration](./contexts/migration/CONTEXT.md) — owns the iText 7.2.6 reference mapping and compatibility evidence

## Relationships

- **Composition → Document Engine**: Composition produces page content and resources that Document Engine persists.
- **Forms / Trust / Conformance / Sanitization → Document Engine**: Each context changes or verifies a document through the Document Engine Native Interface rather than a backend-specific object model.
- **Conversion → Document Engine / Composition**: Conversion turns external representations into documents or document content and exposes external engines through Capability Providers.
- **Migration → all contexts**: Migration maps Reference Suite capabilities to the owning context without dictating their internal model.
