# Roadmap

Open PDF follows Capability Matrix Dependency Gates rather than calendar promises or a single serial implementation plan. Independent contexts may progress in parallel, but a capability cannot become `compatible` before every verified prerequisite is `compatible`.

## Foundation Release — 0.1.0

Document Engine, the full Foundation Composition Profile, page rendering, password encryption, existing-signature protection, the corresponding Stable Migration Facade, and every required Acceptance Profile on all certified platforms.

## Dependency DAG

1. **Foundation** establishes document reading, writing, low-level values and patches, layout, typography, barcodes, rendering, hostile-input processing, and acceptance infrastructure.
2. **Remaining Core** opens Forms, Trust, Conformance, SVG, and styled XML work once their verified Document Engine and Composition prerequisites are compatible.
3. **Parallel add-on work** opens Sanitization/pdfSweep, optimization, OCR, and XFA when their own verified Core prerequisites are compatible.
4. **HTML conversion** opens against compatible Forms, Layout, SVG, and styled XML capabilities; advanced multilingual profiles also require compatible shaping.
5. **Office conversion** remains last in the current advisory order because its layout and visual risk benefits from mature creation, typography, image, and rendering evidence. This is an engineering sequencing preference, not an asserted vendor dependency.

Community contribution may change advisory order without an ADR. Adding, removing, or weakening a verified Dependency Gate requires evidence and an ADR.
