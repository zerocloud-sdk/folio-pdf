/**
 * The Folio PDF by ZeroCloud transactional Document Workflow Native Interface.
 *
 * <p>This is an independent project and is not affiliated with LibrePDF
 * OpenPDF or Apryse/iText. Backend classes are not part of this package's
 * public contract.</p>
 *
 * <p>The package includes immutable annotation, resource-free normal
 * appearance, local Navigation Target, and inert GoTo Action values. The
 * exact T12 allowlist, flattening contract, and page-operation policy are
 * documented in {@code docs/annotations-actions.md}.</p>
 *
 * <p>Detached T13 page-text, character-mapping evidence, marked-content, and
 * Tagged PDF logical-structure values are bounded by {@link
 * net.zerocloud.pdf.ExtractionLimits}. Their ordering, geometry, mapping,
 * language, role, and failure contracts are documented in
 * {@code docs/text-logical-structure.md}.</p>
 *
 * <p>Detached T14 Image and Document Resource Inventory values are bounded by
 * {@link net.zerocloud.pdf.ResourceExtractionLimits}. Their traversal,
 * identity, Page Usage, filter, color, mask, font, byte-selection, lifecycle,
 * and failure contracts are documented in
 * {@code docs/image-resource-extraction.md}.</p>
 */
package net.zerocloud.pdf;
