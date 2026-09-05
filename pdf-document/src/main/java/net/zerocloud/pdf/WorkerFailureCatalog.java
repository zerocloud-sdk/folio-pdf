package net.zerocloud.pdf;

import java.util.Objects;

/**
 * Closed parent-owned vocabulary for failures crossing the Worker boundary.
 *
 * <p>The transport carries only an integer token. The receiving side
 * reconstructs every code, capability, and diagnostic from this catalog, so a
 * compromised child cannot place its own strings in a public failure.</p>
 */
final class WorkerFailureCatalog {

    private static final String[] CAPABILITIES = {
        PdfBoxWorkflowEngine.CAPABILITY_ID,
        PdfBoxWorkflowEngine.INCREMENTAL_CAPABILITY_ID,
        PdfBoxWorkflowEngine.VERSION_SECURITY_CAPABILITY_ID,
        PdfBoxPageOperations.CAPABILITY_ID,
        PdfBoxMetadataOperations.CAPABILITY_ID,
        PdfBoxAnnotationOperations.CAPABILITY_ID,
        PdfBoxValueAdapter.CAPABILITY_ID,
        PdfBoxCanvasOperations.CAPABILITY_ID,
        PdfBoxCanvasResourceOperations.CAPABILITY_ID,
        PdfBoxPositionedTextOperations.CAPABILITY_ID,
        PdfBoxTextStructureExtractionOperations.CAPABILITY_ID,
        PdfBoxImageResourceExtractionOperations.CAPABILITY_ID,
        WorkflowResourceContext.CAPABILITY_ID,
        HardenedWorkerEngine.CAPABILITY_ID,
        Rendering.CAPABILITY_ID,
        PdfBoxParagraphOperations.CAPABILITY_ID,
        PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID
    };

    private static final Descriptor[] SPECIFIC = {
        descriptor(DocumentFailureCode.RENDER_OPTIONS_INVALID,
                "Rendering requires finite positive dimensions, valid colors, and a contained crop."),
        descriptor(DocumentFailureCode.RENDER_DIMENSIONS_EXCEEDED,
                "The requested raster dimensions exceed the supported address space."),
        descriptor(DocumentFailureCode.RENDER_FAILED,
                "The page could not be rendered under the declared profile."),
        descriptor(DocumentFailureCode.RENDER_RESULT_EXPIRED,
                "The rendered page is no longer active."),
        descriptor(DocumentFailureCode.RENDER_OUTPUT_FAILED,
                "PNG consumption failed; the caller stream may contain partial output."),
        descriptor(DocumentFailureCode.PAGE_RANGE_INVALID,
                "The rendering page is outside the current document."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker rendering value is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker temporary-storage release is inapplicable."),
        descriptor(DocumentFailureCode.ACTION_INVALID,
                "The supported Actions could not be updated safely."),
        descriptor(DocumentFailureCode.ACTION_LIMIT_EXCEEDED,
                "The Action query exceeded its caller-declared bound."),
        descriptor(DocumentFailureCode.ANNOTATION_FLATTENING_UNSUPPORTED,
                "The annotation cannot be flattened safely under the version-1 contract."),
        descriptor(DocumentFailureCode.ANNOTATION_INVALID,
                "The supported annotations could not be updated safely."),
        descriptor(DocumentFailureCode.ANNOTATION_LIMIT_EXCEEDED,
                "The annotation query exceeded a caller-declared bound."),
        descriptor(DocumentFailureCode.ANNOTATION_NOT_FOUND,
                "An annotation selected for removal does not exist."),
        descriptor(DocumentFailureCode.CANVAS_GRAPHICS_INVALID,
                "The Canvas color or transparency declaration is invalid."),
        descriptor(DocumentFailureCode.CANVAS_IMAGE_CODEC_UNAVAILABLE,
                "The optional Canvas Image codec is unavailable."),
        descriptor(DocumentFailureCode.CANVAS_IMAGE_INVALID,
                "The Canvas Image is invalid."),
        descriptor(DocumentFailureCode.CANVAS_PRESERVATION_UNSUPPORTED,
                "The page content or resources cannot be preserved safely for Canvas drawing."),
        descriptor(DocumentFailureCode.CANVAS_PROGRAM_INVALID,
                "The Canvas Program is invalid."),
        descriptor(DocumentFailureCode.CANVAS_RESOURCE_INVALID,
                "The Canvas Font resource is invalid."),
        descriptor(DocumentFailureCode.CANVAS_RESOURCE_LIMIT_EXCEEDED,
                "The Canvas resource limit was exceeded."),
        descriptor(DocumentFailureCode.CANVAS_RESOURCE_UNSUPPORTED,
                "The Canvas image or color resource is unsupported."),
        descriptor(DocumentFailureCode.CAPABILITY_PROVIDER_FAILED,
                "The Capability Provider could not execute the requested operation."),
        descriptor(DocumentFailureCode.CAPABILITY_PROVIDER_NOT_FOUND,
                "No eligible Capability Provider is registered for the requested capability."),
        descriptor(DocumentFailureCode.CAPABILITY_PROVIDER_UNAVAILABLE,
                "The selected Capability Provider is unavailable."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "A Document Patch cannot change engine-owned version or password-security state."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "A successful split must be the final Document Command in its workflow."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The command is not supported by this workflow version."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The document outline could not be updated safely."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The Document Patch contains an invalid PDF number."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The Document Patch contains an unavailable Object Reference."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The Document Patch stream could not be created."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The Document Patch stream could not be decoded."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The Document Patch target is not a dictionary."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The embedded files could not be updated safely."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The Info command contains a value that document information cannot hold."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The Info command contains an invalid entry name."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The named destinations could not be updated safely."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The XMP packet exceeds the supported metadata packet size."),
        descriptor(DocumentFailureCode.COMMAND_REJECTED,
                "The XMP packet is not a well-formed XMP metadata packet."),
        descriptor(DocumentFailureCode.CONCURRENCY_LIMIT_EXCEEDED,
                "The workflow concurrency limit was exceeded."),
        descriptor(DocumentFailureCode.CREDENTIAL_DESTROYED,
                "A password credential was destroyed before execution."),
        descriptor(DocumentFailureCode.CREDENTIAL_REJECTED,
                "The supplied Source credential was not accepted."),
        descriptor(DocumentFailureCode.CREDENTIAL_REQUIRED,
                "The password-protected Source requires a credential."),
        descriptor(DocumentFailureCode.DEADLINE_EXCEEDED,
                "The workflow deadline has expired."),
        descriptor(DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED,
                "The workflow decompression limit was exceeded."),
        descriptor(DocumentFailureCode.DESTINATION_CONFLICT,
                "A destination removal conflicts with an existing managed "
                        + "annotation or Action target."),
        descriptor(DocumentFailureCode.DESTINATION_CONFLICT,
                "A page removal conflicts with an existing managed destination."),
        descriptor(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                "Protected rewrite requires proven owner authority."),
        descriptor(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                "The Source credential does not authorize Canvas drawing."),
        descriptor(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                "The Source credential does not authorize positioned text."),
        descriptor(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                "The Source credential does not authorize this document operation."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged document could not be validated."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged document is empty."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged document is not a parseable PDF document."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged document must contain at least one page."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged incremental revision could not be validated."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged incremental revision does not preserve its Source prefix."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged owner credential does not have unrestricted authority."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged password-security dictionary does not match the output policy."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged password-security state does not match the output policy."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged password-security state does not preserve its Source."),
        descriptor(DocumentFailureCode.DOCUMENT_VALIDATION_FAILED,
                "The staged PDF version does not match the output policy."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "A new document could not be initialized."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The annotation update could not be completed safely."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The blank page could not be added."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The blank page could not be inserted."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The Canvas Program could not be applied."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The document could not be staged for publication."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The document information could not be updated safely."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The Document Patch could not be applied."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The metadata operation could not be completed safely."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The named Sources could not be merged safely."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The page operation could not be completed safely."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The pages could not be copied."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The pages could not be moved."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The pages could not be removed."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The positioned Unicode text could not be applied."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The split products could not be created safely."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The XMP metadata could not be updated safely."),
        descriptor(DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED,
                "The workflow elapsed-time limit was exceeded."),
        descriptor(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                "The image and resource extraction limit was exceeded."),
        descriptor(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                "The text and logical-structure extraction limit was exceeded."),
        descriptor(DocumentFailureCode.FONT_EMBEDDING_RESTRICTED,
                "The font embedding permissions reject this operation."),
        descriptor(DocumentFailureCode.FONT_FORMAT_UNSUPPORTED,
                "The font format or profile is unsupported."),
        descriptor(DocumentFailureCode.FONT_GLYPH_MISSING,
                "No declared font contains every requested Unicode scalar."),
        descriptor(DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                "The font operation limit was exceeded."),
        descriptor(DocumentFailureCode.FONT_MAPPING_UNSUPPORTED,
                "The requested Unicode mapping cannot be represented safely."),
        descriptor(DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely."),
        descriptor(DocumentFailureCode.INCREMENTAL_COMMAND_REJECTED,
                "The command is not supported for INCREMENTAL publication."),
        descriptor(DocumentFailureCode.INCREMENTAL_SOURCE_REQUIRED,
                "INCREMENTAL publication requires an existing primary Source."),
        descriptor(DocumentFailureCode.INVALID_REQUEST,
                "The publication target parent must be an existing directory."),
        descriptor(DocumentFailureCode.INVALID_REQUEST,
                "The workflow request has no source or publication target."),
        descriptor(DocumentFailureCode.INVALID_REQUEST,
                "The workflow request must select a declared primary source."),
        descriptor(DocumentFailureCode.INVALID_REQUEST,
                "The workflow request must select a supported Save Mode."),
        descriptor(DocumentFailureCode.LEGACY_SECURITY_MODE_REQUIRED,
                "Obsolete password-security output requires Legacy Security Mode."),
        descriptor(DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                "The workflow owned-memory limit was exceeded."),
        descriptor(DocumentFailureCode.MERGE_SOURCE_INVALID,
                "The merge command must name unique declared non-primary Sources."),
        descriptor(DocumentFailureCode.METADATA_LIMIT_EXCEEDED,
                "The metadata access limit was exceeded."),
        descriptor(DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                "The workflow nesting-depth limit was exceeded."),
        descriptor(DocumentFailureCode.OBJECT_LIMIT_EXCEEDED,
                "The workflow PDF-object limit was exceeded."),
        descriptor(DocumentFailureCode.OBJECT_REFERENCE_OWNERSHIP_INVALID,
                "The Object Reference belongs to a different Document Session."),
        descriptor(DocumentFailureCode.OBJECT_REFERENCE_OWNERSHIP_INVALID,
                "The Object Reference does not belong to this Session."),
        descriptor(DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                "The workflow page-count limit was exceeded."),
        descriptor(DocumentFailureCode.PAGE_POSITION_INVALID,
                "The page position is outside the current document."),
        descriptor(DocumentFailureCode.PAGE_RANGE_INVALID,
                "The Canvas page selection is invalid."),
        descriptor(DocumentFailureCode.PAGE_RANGE_INVALID,
                "The destination page is outside the current document."),
        descriptor(DocumentFailureCode.PAGE_RANGE_INVALID,
                "The page range is outside the current document."),
        descriptor(DocumentFailureCode.PAGE_RANGE_INVALID,
                "The positioned-text page selection is invalid."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED,
                "A protected merged Source requires protected publication."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_POLICY_REQUIRED,
                "Rewriting a protected Source requires an explicit "
                        + "password-security output policy."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "A merged Source cannot be published with weaker password security."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "A password credential exceeds the supported output length."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "AES-256 output requires PDF 1.7 or PDF 2.0."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Attachment-only password encryption is unsupported."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Changing password security is supported only for REWRITE publication."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Metadata-clear input is supported only for revision 4 password security."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Obsolete password-security output is supported only with PDF 1.7."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Only encryption of all document content is supported for output."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Only the Standard password-security handler is supported."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Only whole-document Standard crypt filters are supported."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Output password credentials must use printable ASCII characters."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Owner and user credentials must be distinct."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Owner and user credentials must both be non-empty."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "PDF 1.7 AES-256 requires a supported ADBE extension declaration."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "PDF 2.0 password security requires accessibility permission bit 10."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "PDF 2.0 requires revision 6 and accessibility permission bit 10."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "RC4-40 output is unsupported because its security-handler "
                        + "revision cannot be selected reliably."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "Standard password security does not support a SubFilter."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The AES-256 authentication entries are malformed."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The AES-256 permission integrity value is inconsistent."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The AES-256 permission integrity value is malformed."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The catalog ADBE extension entry is not a dictionary."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The catalog ADBE extension entry is not safely removable."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The catalog Extensions entry is not a dictionary."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The password-encryption filter combination is not supported."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The password-security authentication entries are malformed."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The password-security dictionary is not supported."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The password-security permission word has invalid reserved bits."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The password-security permission word is malformed."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The password-security revision is incompatible with the effective PDF version."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The RC4-40 revision and permission mask are inconsistent."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The Standard crypt-filter AuthEvent is unsupported."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The Standard crypt-filter dictionary is missing."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The Standard crypt-filter Length is malformed."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The Standard crypt-filter method is unsupported."),
        descriptor(DocumentFailureCode.PASSWORD_SECURITY_UNSUPPORTED,
                "The Standard password-security revision is not supported."),
        descriptor(DocumentFailureCode.PATCH_CYCLE_REJECTED,
                "The Document Patch would introduce a reference cycle."),
        descriptor(DocumentFailureCode.PATCH_STREAM_CHANGE_REJECTED,
                "The Document Patch cannot change engine-owned stream metadata."),
        descriptor(DocumentFailureCode.PATCH_VALUE_REJECTED,
                "The Document Patch contains a value not owned by Folio PDF."),
        descriptor(DocumentFailureCode.PATCH_VALUE_REJECTED,
                "The Document Patch contains an invalid PDF string."),
        descriptor(DocumentFailureCode.PATCH_VALUE_REJECTED,
                "The PDF Value is not owned by Folio PDF."),
        descriptor(DocumentFailureCode.PDF_VALUE_LIMIT_EXCEEDED,
                "The PDF Value inspection limit was exceeded."),
        descriptor(DocumentFailureCode.PDF_VALUE_VIEW_EXPIRED,
                "The PDF Value view is no longer active."),
        descriptor(DocumentFailureCode.PDF_VERSION_INVALID,
                "A PDF 1.x header must begin at byte zero."),
        descriptor(DocumentFailureCode.PDF_VERSION_INVALID,
                "The PDF version declaration is malformed."),
        descriptor(DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                "A merged Source cannot be published with an older PDF version."),
        descriptor(DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                "A PDF 2.0 Source cannot be rewritten as PDF 1.7."),
        descriptor(DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                "INCREMENTAL publication cannot change the effective PDF version."),
        descriptor(DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                "Only PDF 1.7 and PDF 2.0 output are supported."),
        descriptor(DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                "Positioned Unicode text requires PDF 1.2 or newer."),
        descriptor(DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                "Supplementary Unicode mappings require PDF 1.5 or newer."),
        descriptor(DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                "The declared PDF version is not supported."),
        descriptor(DocumentFailureCode.PIXEL_LIMIT_EXCEEDED,
                "The workflow decoded-pixel limit was exceeded."),
        descriptor(DocumentFailureCode.POSITIONED_TEXT_INVALID,
                "The positioned Unicode text declaration is invalid."),
        descriptor(DocumentFailureCode.POSITIONED_TEXT_PRESERVATION_UNSUPPORTED,
                "The page content or resources cannot be preserved safely "
                        + "for positioned text."),
        descriptor(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                "The document contains annotation or Action structures that "
                        + "this page operation cannot preserve safely."),
        descriptor(DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                "The document contains structures that this page operation "
                        + "cannot preserve safely."),
        descriptor(DocumentFailureCode.PUBLICATION_FAILED,
                "The validated document could not be committed to its target."),
        descriptor(DocumentFailureCode.PUBLICATION_FAILED,
                "The validated document could not be written to its stream target."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The document images and resources could not be extracted safely."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The document information could not be inspected safely."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The document outline could not be inspected safely."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The document root could not be inspected."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The document text and logical structure could not be extracted safely."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The embedded files could not be inspected safely."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The metadata query could not be evaluated safely."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The named destinations could not be inspected safely."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The Object Reference could not be inspected."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The Object Reference is unavailable."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The page count could not be evaluated."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The page Object Reference could not be evaluated."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The page Object Reference could not be resolved from the page tree."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The PDF number could not be inspected."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The PDF object could not be inspected."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The PDF stream could not be decoded."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The PDF string could not be inspected."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The PDF Value kind is not supported by this workflow version."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The requested PDF object is unavailable."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The supported Actions could not be inspected safely."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The supported annotations could not be inspected safely."),
        descriptor(DocumentFailureCode.QUERY_FAILED,
                "The XMP metadata could not be inspected safely."),
        descriptor(DocumentFailureCode.QUERY_REJECTED,
                "The query is not supported by this workflow version."),
        descriptor(DocumentFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED,
                "Remote disclosure was not authorized for the requested capability."),
        descriptor(DocumentFailureCode.RESOURCE_CLOSE_FAILED,
                "A library-owned document resource could not be closed cleanly."),
        descriptor(DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                "The Existing Signature policy does not permit Canvas drawing."),
        descriptor(DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                "The Existing Signature policy does not permit positioned text."),
        descriptor(DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                "The Existing Signature policy does not permit this workflow."),
        descriptor(DocumentFailureCode.SIGNATURE_STRUCTURE_INVALID,
                "The Existing Signature policy could not be determined safely."),
        descriptor(DocumentFailureCode.SIGNED_REWRITE_REJECTED,
                "A Source with an Existing Signature cannot be published with REWRITE."),
        descriptor(DocumentFailureCode.SOURCE_LIMIT_EXCEEDED,
                "The source exceeds its declared byte limit."),
        descriptor(DocumentFailureCode.SOURCE_READ_FAILED,
                "The source could not be opened as a PDF document."),
        descriptor(DocumentFailureCode.SOURCE_READ_FAILED,
                "The source could not be preflighted safely."),
        descriptor(DocumentFailureCode.SPLIT_TARGET_INVALID,
                "The split command must define every publication Target once."),
        descriptor(DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                "The workflow temporary-storage limit was exceeded."),
        descriptor(DocumentFailureCode.TEMPORARY_STORAGE_UNAVAILABLE,
                "The workflow temporary-storage root is unavailable."),
        descriptor(DocumentFailureCode.WORKER_AUTHENTICATION_FAILED,
                "The Worker frame sequence is not applicable."),
        descriptor(DocumentFailureCode.WORKER_AUTHENTICATION_FAILED,
                "Worker authentication material is missing or truncated."),
        descriptor(DocumentFailureCode.WORKER_AUTHENTICATION_FAILED,
                "Worker frame authentication failed."),
        descriptor(DocumentFailureCode.WORKER_MESSAGE_LIMIT_EXCEEDED,
                "The Worker message-size limit was exceeded."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A required Worker credential is missing."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker annotation identifier list is empty."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker boolean value is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker byte value exceeds its requested bound."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker Command value is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker credential length is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker decimal value is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker Document Patch is empty."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker Font Source identifier was repeated."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker message contains trailing data."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker message could not be encoded."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker Object Reference is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker PDF stream dictionary is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker private-file reference is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker private-file reference is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker Query result is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker Query value is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker resource result is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker string representation is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker value is truncated."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "A Worker value length is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "An atomic Worker Command batch was not accepted."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "Java object serialization is not accepted by the Worker."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The accepted atomic Command batch is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The atomic Worker Command batch length changed."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The atomic Worker Command batch length is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The atomic Worker Command opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker annotation color is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker annotation preflight detail is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker annotation type is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Canvas Color Space is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Canvas Command version is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Canvas Image kind is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Canvas instruction is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Canvas preflight detail is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Canvas Program preflight is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Canvas preflight is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Canvas Program is too deeply nested."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Canvas Program version is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Command batch count is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Command batch item count is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Command batch item is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Command count is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker document-permission mask is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Command opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Command preflight detail is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Command preflight detail is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Command preflight is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Command preflight is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker credential is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker credential request is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker credential request was repeated."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker credential response is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker deadline is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker embedded-file value is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker explicit Font Selection is empty."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker failure descriptor is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker finish message is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker font resource kind is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Font Source identifier is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker font sources were requested out of order."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker font-source identifier is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker font-source identifier space was exhausted."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker font-source identifier was repeated."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker font-source read is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker font-source request is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker font-source response is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker font-source response opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker frame body was truncated."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker frame header was truncated."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker frame length is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker frame sequence was exhausted."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker image resource kind is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker image-mask relationship is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker image-mask target is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker image-mask target is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker initialization opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker initialization value is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker input request is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker input response opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker isolation-probe target is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker message limit does not match its launch boundary."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker owned-memory grant does not match its launch boundary."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker owned-memory probe is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker message limit is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker memory amount is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker memory-control length is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker memory grant is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker memory release is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker memory reservation is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker memory synchronization is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker memory synchronization response is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker receive-memory grant is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker receive-memory grant is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker numeric operand count is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker outcome capability is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker outline target is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker output credential is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker page destination is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker page destination is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF array view is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF dictionary view is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF output version is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF stream view is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF Value descriptor is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF Value kind is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF Value opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF Value response is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF Value view is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF Value view operation is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF Value view space is exhausted."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker PDF version is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker positioned-text preflight detail is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker positioned-text preflight is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker product declaration is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker progress acknowledgement is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker progress acknowledgement is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker progress boundary is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker progress frame is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker progress phase is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker progress sequence is incomplete."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker progress sequence is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker protocol value is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker protocol version is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Query opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Query result opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Reference Font Set selection is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker remote Font Source is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker request opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker requested an inapplicable Command item."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker requested inapplicable Command preflight details."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker requested unsupported Command preflight details."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker resource graph is cyclic."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker resource policy is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker resource type is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker response opcode is unsupported."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker soft Canvas mask is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Source credential is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Source request is out of order."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Source request is unavailable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Source response is inapplicable."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker Source temporary-storage allowance is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker split declaration count is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker stream dictionary is invalid."),
        descriptor(DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker value version is unsupported."),
        descriptor(DocumentFailureCode.WORKER_TERMINATED,
                "The Worker could not complete the transaction."),
        descriptor(DocumentFailureCode.WORKER_TERMINATED,
                "The Worker did not terminate after completing its response."),
        descriptor(DocumentFailureCode.WORKER_TERMINATED,
                "The Worker terminated without a valid response."),
        descriptor(DocumentFailureCode.WORKER_TERMINATED,
                "The Worker termination wait was interrupted."),
        descriptor(DocumentFailureCode.WORKER_TERMINATED,
                "The Worker wait was interrupted."),
        descriptor(DocumentFailureCode.WORKER_UNAVAILABLE,
                "The supported local Worker launcher is unavailable."),
        descriptor(DocumentFailureCode.WORKER_UNAVAILABLE,
                "The Worker authentication primitive is unavailable."),
        descriptor(DocumentFailureCode.WORKFLOW_CANCELLED,
                "The workflow was cancelled."),
        descriptor(DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED,
                "The workflow input-byte limit was exceeded."),
        descriptor(DocumentFailureCode.COMPOSITION_INVALID,
                "The paragraph flow declaration is invalid."),
        descriptor(DocumentFailureCode.COMPOSITION_AREA_EXHAUSTED,
                "The remaining layout areas cannot contain the paragraph flow."),
        descriptor(DocumentFailureCode.COMPOSITION_LIMIT_EXCEEDED,
                "The paragraph composition limit was exceeded."),
        descriptor(DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                "The Existing Signature policy does not permit paragraph composition."),
        descriptor(DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                "The Source credential does not authorize paragraph composition."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The paragraph flow could not be applied safely."),
        descriptor(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The paragraph flow could not be relaid out safely."),
        descriptor(DocumentFailureCode.COMPOSITION_CONSTRAINT_UNSATISFIED,
                "The finite layout areas cannot satisfy the paragraph constraints."),
        descriptor(DocumentFailureCode.COMPOSITION_RELAYOUT_UNSAFE,
                "The paragraph flow is not available for safe relayout.")
    };

    private WorkerFailureCatalog() {
    }

    static int encode(DocumentFailure failure) throws DocumentFailure {
        Objects.requireNonNull(failure, "failure");
        int capability = capabilityIndex(failure.getCapabilityId());
        if (capability < 0) {
            throw rejected();
        }
        for (int index = 0; index < SPECIFIC.length; index++) {
            Descriptor descriptor = SPECIFIC[index];
            if (descriptor.code == failure.getCode()
                    && descriptor.diagnostic.equals(
                            failure.getDiagnostic())
                    && descriptor.permits(capability)) {
                return token(index, capability);
            }
        }
        throw rejected();
    }

    static DocumentFailure decode(int token) throws DocumentFailure {
        if (token < 0) {
            throw rejected();
        }
        int capability = token % CAPABILITIES.length;
        int descriptor = token / CAPABILITIES.length;
        if (descriptor < 0 || descriptor >= SPECIFIC.length) {
            throw rejected();
        }
        Descriptor value = SPECIFIC[descriptor];
        if (!value.permits(capability)) {
            throw rejected();
        }
        return new DocumentFailure(
                value.code,
                CAPABILITIES[capability],
                value.diagnostic);
    }

    private static int token(int descriptor, int capability)
            throws DocumentFailure {
        long value = (long) descriptor * CAPABILITIES.length + capability;
        if (value > Integer.MAX_VALUE) {
            throw rejected();
        }
        return (int) value;
    }

    static int capabilityCount() {
        return CAPABILITIES.length;
    }

    static int capabilityIndex(String capabilityId) {
        for (int index = 0; index < CAPABILITIES.length; index++) {
            if (CAPABILITIES[index].equals(capabilityId)) {
                return index;
            }
        }
        return -1;
    }

    private static int permittedCapabilities(
            DocumentFailureCode code,
            String diagnostic) {
        if (diagnostic.equals("The rendering page is outside the current document.")) {
            return mask(Rendering.CAPABILITY_ID);
        }
        switch (code) {
            case COMPOSITION_CONSTRAINT_UNSATISFIED:
            case COMPOSITION_RELAYOUT_UNSAFE:
                return mask(PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID);
            case COMPOSITION_INVALID:
            case COMPOSITION_AREA_EXHAUSTED:
            case COMPOSITION_LIMIT_EXCEEDED:
                return mask(PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID);
            case RENDER_OPTIONS_INVALID:
            case RENDER_DIMENSIONS_EXCEEDED:
            case RENDER_FAILED:
            case RENDER_RESULT_EXPIRED:
            case RENDER_OUTPUT_FAILED:
                return mask(Rendering.CAPABILITY_ID);
            case ACTION_INVALID:
            case ACTION_LIMIT_EXCEEDED:
            case ANNOTATION_FLATTENING_UNSUPPORTED:
            case ANNOTATION_INVALID:
            case ANNOTATION_LIMIT_EXCEEDED:
            case ANNOTATION_NOT_FOUND:
                return mask(PdfBoxAnnotationOperations.CAPABILITY_ID);
            case CANVAS_GRAPHICS_INVALID:
            case CANVAS_IMAGE_CODEC_UNAVAILABLE:
            case CANVAS_IMAGE_INVALID:
            case CANVAS_RESOURCE_LIMIT_EXCEEDED:
            case CANVAS_RESOURCE_UNSUPPORTED:
                return mask(PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                        PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID);
            case CANVAS_PRESERVATION_UNSUPPORTED:
            case CANVAS_PROGRAM_INVALID:
            case CANVAS_RESOURCE_INVALID:
                return mask(
                        PdfBoxCanvasOperations.CAPABILITY_ID,
                        PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                        PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID);
            case CAPABILITY_PROVIDER_FAILED:
            case CAPABILITY_PROVIDER_NOT_FOUND:
            case CAPABILITY_PROVIDER_UNAVAILABLE:
            case CONCURRENCY_LIMIT_EXCEEDED:
            case INVALID_REQUEST:
            case PUBLICATION_FAILED:
            case QUERY_REJECTED:
            case REMOTE_DISCLOSURE_NOT_AUTHORIZED:
            case SOURCE_LIMIT_EXCEEDED:
            case SOURCE_READ_FAILED:
                return mask(PdfBoxWorkflowEngine.CAPABILITY_ID);
            case COMMAND_REJECTED:
                return commandRejectedCapabilities(diagnostic);
            case CREDENTIAL_DESTROYED:
            case CREDENTIAL_REJECTED:
            case CREDENTIAL_REQUIRED:
            case LEGACY_SECURITY_MODE_REQUIRED:
            case PASSWORD_SECURITY_POLICY_REQUIRED:
            case PASSWORD_SECURITY_UNSUPPORTED:
            case PDF_VERSION_INVALID:
                return mask(
                        PdfBoxWorkflowEngine.VERSION_SECURITY_CAPABILITY_ID);
            case DEADLINE_EXCEEDED:
            case WORKFLOW_CANCELLED:
                return mask(PdfBoxWorkflowEngine.CAPABILITY_ID);
            case DECOMPRESSION_LIMIT_EXCEEDED:
            case ELAPSED_TIME_LIMIT_EXCEEDED:
            case OBJECT_LIMIT_EXCEEDED:
            case PAGE_LIMIT_EXCEEDED:
            case PIXEL_LIMIT_EXCEEDED:
            case TEMPORARY_STORAGE_LIMIT_EXCEEDED:
            case TEMPORARY_STORAGE_UNAVAILABLE:
            case WORKFLOW_INPUT_LIMIT_EXCEEDED:
                return mask(WorkflowResourceContext.CAPABILITY_ID);
            case DESTINATION_CONFLICT:
                return diagnostic.startsWith("A destination removal")
                        ? mask(PdfBoxAnnotationOperations.CAPABILITY_ID)
                        : mask(PdfBoxMetadataOperations.CAPABILITY_ID);
            case DOCUMENT_PERMISSION_DENIED:
                return permissionCapabilities(diagnostic);
            case DOCUMENT_VALIDATION_FAILED:
                return diagnostic.contains("password-security")
                        || diagnostic.contains("owner credential")
                        || diagnostic.contains("PDF version")
                                ? mask(PdfBoxWorkflowEngine
                                        .VERSION_SECURITY_CAPABILITY_ID)
                                : mask(PdfBoxWorkflowEngine.CAPABILITY_ID);
            case DOCUMENT_WRITE_FAILED:
                return writeFailureCapabilities(diagnostic);
            case EXTRACTION_LIMIT_EXCEEDED:
                return diagnostic.startsWith("The image")
                        ? mask(PdfBoxImageResourceExtractionOperations
                                .CAPABILITY_ID)
                        : mask(PdfBoxTextStructureExtractionOperations
                                .CAPABILITY_ID);
            case FONT_EMBEDDING_RESTRICTED:
            case FONT_FORMAT_UNSUPPORTED:
            case FONT_GLYPH_MISSING:
            case FONT_LIMIT_EXCEEDED:
            case FONT_MAPPING_UNSUPPORTED:
            case FONT_SOURCE_INVALID:
            case POSITIONED_TEXT_INVALID:
            case POSITIONED_TEXT_PRESERVATION_UNSUPPORTED:
                return mask(PdfBoxPositionedTextOperations.CAPABILITY_ID,
                        PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID);
            case INCREMENTAL_COMMAND_REJECTED:
            case INCREMENTAL_SOURCE_REQUIRED:
            case SIGNATURE_STRUCTURE_INVALID:
            case SIGNED_REWRITE_REJECTED:
                return mask(PdfBoxWorkflowEngine.INCREMENTAL_CAPABILITY_ID);
            case MEMORY_LIMIT_EXCEEDED:
            case NESTING_LIMIT_EXCEEDED:
                return mask(
                        WorkflowResourceContext.CAPABILITY_ID,
                        HardenedWorkerEngine.CAPABILITY_ID);
            case MERGE_SOURCE_INVALID:
            case PAGE_POSITION_INVALID:
            case SPLIT_TARGET_INVALID:
                return mask(PdfBoxPageOperations.CAPABILITY_ID);
            case METADATA_LIMIT_EXCEEDED:
                return mask(PdfBoxMetadataOperations.CAPABILITY_ID);
            case OBJECT_REFERENCE_OWNERSHIP_INVALID:
            case PATCH_CYCLE_REJECTED:
            case PATCH_STREAM_CHANGE_REJECTED:
            case PATCH_VALUE_REJECTED:
            case PDF_VALUE_LIMIT_EXCEEDED:
            case PDF_VALUE_VIEW_EXPIRED:
                return mask(PdfBoxValueAdapter.CAPABILITY_ID);
            case PAGE_RANGE_INVALID:
                return pageRangeCapabilities(diagnostic);
            case PDF_VERSION_UNSUPPORTED:
                return diagnostic.startsWith("Positioned")
                        || diagnostic.startsWith("Supplementary")
                                ? mask(PdfBoxPositionedTextOperations
                                        .CAPABILITY_ID, PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID)
                                : mask(PdfBoxWorkflowEngine
                                        .VERSION_SECURITY_CAPABILITY_ID);
            case PRESERVATION_UNSUPPORTED:
                return diagnostic.contains("annotation or Action")
                        ? mask(PdfBoxAnnotationOperations.CAPABILITY_ID)
                        : mask(
                                PdfBoxPageOperations.CAPABILITY_ID,
                                PdfBoxMetadataOperations.CAPABILITY_ID);
            case QUERY_FAILED:
                return queryFailureCapabilities(diagnostic);
            case RESOURCE_CLOSE_FAILED:
                return mask(
                        PdfBoxWorkflowEngine.CAPABILITY_ID,
                        PdfBoxPageOperations.CAPABILITY_ID);
            case SIGNATURE_POLICY_REJECTED:
                return signatureCapabilities(diagnostic);
            case WORKER_AUTHENTICATION_FAILED:
            case WORKER_MESSAGE_LIMIT_EXCEEDED:
            case WORKER_PROTOCOL_REJECTED:
            case WORKER_TERMINATED:
            case WORKER_UNAVAILABLE:
                return mask(HardenedWorkerEngine.CAPABILITY_ID);
            default:
                return 0;
        }
    }

    private static int commandRejectedCapabilities(String diagnostic) {
        if (diagnostic.contains("Document Patch")) {
            return mask(PdfBoxValueAdapter.CAPABILITY_ID);
        }
        if (diagnostic.contains("successful split")) {
            return mask(PdfBoxPageOperations.CAPABILITY_ID);
        }
        if (diagnostic.equals(
                "The command is not supported by this workflow version.")) {
            return mask(PdfBoxWorkflowEngine.CAPABILITY_ID);
        }
        return mask(PdfBoxMetadataOperations.CAPABILITY_ID);
    }

    private static int permissionCapabilities(String diagnostic) {
        if (diagnostic.contains("paragraph composition")) { return mask(PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID); }
        if (diagnostic.contains("Canvas")) {
            return mask(
                    PdfBoxCanvasOperations.CAPABILITY_ID,
                    PdfBoxCanvasResourceOperations.CAPABILITY_ID);
        }
        if (diagnostic.contains("positioned text")) {
            return mask(PdfBoxPositionedTextOperations.CAPABILITY_ID);
        }
        return mask(PdfBoxWorkflowEngine.VERSION_SECURITY_CAPABILITY_ID);
    }

    private static int writeFailureCapabilities(String diagnostic) {
        if (diagnostic.contains("paragraph flow")) { return mask(PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID); }
        if (diagnostic.contains("Canvas Program")) {
            return mask(
                    PdfBoxCanvasOperations.CAPABILITY_ID,
                    PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                    PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID);
        }
        if (diagnostic.contains("positioned Unicode text")) {
            return mask(PdfBoxPositionedTextOperations.CAPABILITY_ID,
                    PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID);
        }
        if (diagnostic.contains("annotation update")) {
            return mask(PdfBoxAnnotationOperations.CAPABILITY_ID);
        }
        if (diagnostic.contains("metadata")
                || diagnostic.contains("document information")) {
            return mask(PdfBoxMetadataOperations.CAPABILITY_ID);
        }
        if (diagnostic.contains("page operation")
                || diagnostic.contains("pages")
                || diagnostic.contains("Sources")
                || diagnostic.contains("split products")
                || diagnostic.contains("blank page could be inserted")) {
            return mask(PdfBoxPageOperations.CAPABILITY_ID);
        }
        if (diagnostic.contains("Document Patch")) {
            return mask(PdfBoxValueAdapter.CAPABILITY_ID);
        }
        return mask(PdfBoxWorkflowEngine.CAPABILITY_ID);
    }

    private static int pageRangeCapabilities(String diagnostic) {
        if (diagnostic.contains("Canvas")) {
            return mask(
                    PdfBoxCanvasOperations.CAPABILITY_ID,
                    PdfBoxCanvasResourceOperations.CAPABILITY_ID);
        }
        if (diagnostic.contains("positioned-text")) {
            return mask(PdfBoxPositionedTextOperations.CAPABILITY_ID);
        }
        if (diagnostic.contains("destination")) {
            return mask(PdfBoxMetadataOperations.CAPABILITY_ID);
        }
        return mask(PdfBoxPageOperations.CAPABILITY_ID);
    }

    private static int queryFailureCapabilities(String diagnostic) {
        if (diagnostic.contains("images and resources")) {
            return mask(PdfBoxImageResourceExtractionOperations
                    .CAPABILITY_ID);
        }
        if (diagnostic.contains("text and logical structure")) {
            return mask(PdfBoxTextStructureExtractionOperations
                    .CAPABILITY_ID);
        }
        if (diagnostic.contains("annotation")
                || diagnostic.contains("Actions")) {
            return mask(PdfBoxAnnotationOperations.CAPABILITY_ID);
        }
        if (diagnostic.contains("metadata")
                || diagnostic.contains("document information")
                || diagnostic.contains("outline")
                || diagnostic.contains("embedded files")
                || diagnostic.contains("named destinations")
                || diagnostic.contains("XMP")) {
            return mask(PdfBoxMetadataOperations.CAPABILITY_ID);
        }
        if (diagnostic.contains("page Object Reference")) {
            return mask(PdfBoxPageOperations.CAPABILITY_ID);
        }
        if (diagnostic.equals("The page count could not be evaluated.")) {
            return mask(PdfBoxWorkflowEngine.CAPABILITY_ID);
        }
        return mask(PdfBoxValueAdapter.CAPABILITY_ID);
    }

    private static int signatureCapabilities(String diagnostic) {
        if (diagnostic.contains("paragraph composition")) { return mask(PdfBoxParagraphOperations.CAPABILITY_ID, PdfBoxParagraphOperations.PAGINATION_CAPABILITY_ID); }
        if (diagnostic.contains("Canvas")) {
            return mask(
                    PdfBoxCanvasOperations.CAPABILITY_ID,
                    PdfBoxCanvasResourceOperations.CAPABILITY_ID);
        }
        if (diagnostic.contains("positioned text")) {
            return mask(PdfBoxPositionedTextOperations.CAPABILITY_ID);
        }
        return mask(PdfBoxWorkflowEngine.INCREMENTAL_CAPABILITY_ID);
    }

    private static int mask(String... capabilityIds) {
        int result = 0;
        for (String capabilityId : capabilityIds) {
            int index = capabilityIndex(capabilityId);
            if (index < 0) {
                throw new ExceptionInInitializerError(
                        "Unknown Worker failure capability");
            }
            result |= 1 << index;
        }
        return result;
    }

    private static Descriptor descriptor(
            DocumentFailureCode code,
            String diagnostic) {
        return new Descriptor(
                code,
                diagnostic,
                permittedCapabilities(code, diagnostic));
    }

    private static DocumentFailure rejected() {
        return WorkerCodecIO.workerFailure(
                DocumentFailureCode.WORKER_PROTOCOL_REJECTED,
                "The Worker failure descriptor is unsupported.");
    }

    private static final class Descriptor {
        private final DocumentFailureCode code;
        private final String diagnostic;
        private final int permittedCapabilities;

        private Descriptor(
                DocumentFailureCode code,
                String diagnostic,
                int permittedCapabilities) {
            if (permittedCapabilities == 0) {
                throw new ExceptionInInitializerError(
                        "Unclassified Worker failure descriptor: " + code);
            }
            this.code = code;
            this.diagnostic = diagnostic;
            this.permittedCapabilities = permittedCapabilities;
        }

        private boolean permits(int capability) {
            return (permittedCapabilities & (1 << capability)) != 0;
        }
    }
}
