package net.zerocloud.pdf;

/**
 * Stable operational failure categories for the Document Workflow.
 *
 * @since 0.1.0
 */
public enum DocumentFailureCode {
    /** A request cannot be executed as supplied. */
    INVALID_REQUEST,

    /** The source could not be opened as a PDF document. */
    SOURCE_READ_FAILED,

    /** A PDF header or catalog version declaration is malformed. */
    PDF_VERSION_INVALID,

    /** A syntactically valid PDF version is outside the supported input set. */
    PDF_VERSION_UNSUPPORTED,

    /** A password-protected Source requires an opening credential. */
    CREDENTIAL_REQUIRED,

    /** The supplied opening credential was not accepted. */
    CREDENTIAL_REJECTED,

    /** A credential was destroyed before the workflow could copy it. */
    CREDENTIAL_DESTROYED,

    /** The requested or encountered password-security form is unsupported. */
    PASSWORD_SECURITY_UNSUPPORTED,

    /** A protected Source rewrite requires a complete explicit output policy. */
    PASSWORD_SECURITY_POLICY_REQUIRED,

    /** Obsolete password-security output was requested without legacy mode. */
    LEGACY_SECURITY_MODE_REQUIRED,

    /** The effective user permissions do not authorize an operation. */
    DOCUMENT_PERMISSION_DENIED,

    /** A bounded source exceeded its caller-declared byte limit. */
    SOURCE_LIMIT_EXCEEDED,

    /** Aggregate bytes accepted from all workflow Sources exceeded policy. */
    WORKFLOW_INPUT_LIMIT_EXCEEDED,

    /** The transaction-wide page-count policy was exhausted. */
    PAGE_LIMIT_EXCEEDED,

    /** The transaction-wide indirect-object policy was exhausted. */
    OBJECT_LIMIT_EXCEEDED,

    /** The transaction-wide container or graph-depth policy was exhausted. */
    NESTING_LIMIT_EXCEEDED,

    /** The transaction-wide supported filter-output policy was exhausted. */
    DECOMPRESSION_LIMIT_EXCEEDED,

    /** The transaction-wide decoded-pixel policy was exhausted. */
    PIXEL_LIMIT_EXCEEDED,

    /** The transaction-wide accounted owned-memory policy was exhausted. */
    MEMORY_LIMIT_EXCEEDED,

    /** The transaction-wide temporary-storage policy was exhausted. */
    TEMPORARY_STORAGE_LIMIT_EXCEEDED,

    /** The finite policy elapsed-time limit was observed. */
    ELAPSED_TIME_LIMIT_EXCEEDED,

    /** The shared Workflow Environment concurrency gate was saturated. */
    CONCURRENCY_LIMIT_EXCEEDED,

    /** The configured environment temporary-storage root is unavailable. */
    TEMPORARY_STORAGE_UNAVAILABLE,

    /** The local Hardened Worker cannot be launched on this host. */
    WORKER_UNAVAILABLE,

    /** A Worker frame failed authentication or sequence validation. */
    WORKER_AUTHENTICATION_FAILED,

    /** A Worker frame used an unsupported protocol version or opcode. */
    WORKER_PROTOCOL_REJECTED,

    /** A Worker message exceeded the configured transport bound. */
    WORKER_MESSAGE_LIMIT_EXCEEDED,

    /** The Worker exited, was forcibly stopped, or returned no valid result. */
    WORKER_TERMINATED,

    /** Another attempt with the same transaction identity is still running. */
    TRANSACTION_IN_PROGRESS,

    /** The transaction identity already has a final retained outcome. */
    TRANSACTION_ALREADY_FINAL,

    /** An identity was reused for a materially different workflow request. */
    TRANSACTION_IDENTITY_MISMATCH,

    /** The finite environment transaction ledger cannot admit another identity. */
    TRANSACTION_RETENTION_LIMIT_EXCEEDED,

    /** Legacy result retained for prerelease outcome compatibility. */
    SAVE_MODE_UNSUPPORTED,

    /** Incremental publication was requested without an existing primary Source. */
    INCREMENTAL_SOURCE_REQUIRED,

    /** A command cannot be represented by the version-1 incremental policy. */
    INCREMENTAL_COMMAND_REJECTED,

    /** A Source with an Existing Signature cannot be republished by rewrite. */
    SIGNED_REWRITE_REJECTED,

    /** Existing signature structures cannot be interpreted safely. */
    SIGNATURE_STRUCTURE_INVALID,

    /** An Existing Signature permission does not authorize the requested workflow. */
    SIGNATURE_POLICY_REJECTED,

    /** Cancellation was observed during cooperative owned workflow work. */
    WORKFLOW_CANCELLED,

    /** The caller-declared workflow deadline expired at an owned checkpoint. */
    DEADLINE_EXCEEDED,

    /** A command is not part of the supported library-owned command set. */
    COMMAND_REJECTED,

    /** A query is not part of the supported library-owned query set. */
    QUERY_REJECTED,

    /** A supported query could not be evaluated. */
    QUERY_FAILED,

    /** Rendering contains non-finite numbers, invalid colors or an invalid crop. */
    RENDER_OPTIONS_INVALID,

    /** Rendering cannot represent the requested raster dimensions safely. */
    RENDER_DIMENSIONS_EXCEEDED,

    /** A page cannot be rendered under the declared rendering profile. */
    RENDER_FAILED,

    /** PNG consumption failed and a caller stream may contain partial bytes. */
    RENDER_OUTPUT_FAILED,

    /** A staged rendered page was closed or its workflow callback ended. */
    RENDER_RESULT_EXPIRED,

    /** A page range is outside the current document or is not ordered. */
    PAGE_RANGE_INVALID,

    /** A page insertion or movement position is outside its defined sequence. */
    PAGE_POSITION_INVALID,

    /** A merge command selected an invalid named Source. */
    MERGE_SOURCE_INVALID,

    /** A split command did not map every named publication Target exactly once. */
    SPLIT_TARGET_INVALID,

    /** A page operation cannot prove that sensitive structures will survive. */
    PRESERVATION_UNSUPPORTED,

    /** A page or named-destination removal would orphan a managed target. */
    DESTINATION_CONFLICT,

    /** A supported annotation graph is malformed or cannot be updated safely. */
    ANNOTATION_INVALID,

    /** An annotation selected for mutation does not exist. */
    ANNOTATION_NOT_FOUND,

    /** An annotation cannot be flattened under the version-1 contract. */
    ANNOTATION_FLATTENING_UNSUPPORTED,

    /** A caller-declared annotation count or decoded-byte bound was exhausted. */
    ANNOTATION_LIMIT_EXCEEDED,

    /** A supported Action graph is malformed or cannot be updated safely. */
    ACTION_INVALID,

    /** A caller-declared Action count bound was exhausted. */
    ACTION_LIMIT_EXCEEDED,

    /** A caller-declared metadata traversal or byte bound was exhausted. */
    METADATA_LIMIT_EXCEEDED,

    /** A lazy PDF Value view was used after its Document Session ended. */
    PDF_VALUE_VIEW_EXPIRED,

    /** A caller-declared PDF Value inspection bound was exhausted. */
    PDF_VALUE_LIMIT_EXCEEDED,

    /** A caller-declared text or logical-structure extraction bound was exhausted. */
    EXTRACTION_LIMIT_EXCEEDED,

    /** An Object Reference was supplied to a different Document Session. */
    OBJECT_REFERENCE_OWNERSHIP_INVALID,

    /** A Document Patch would introduce a cycle among its changed objects. */
    PATCH_CYCLE_REJECTED,

    /** A Document Patch attempted to change engine-owned stream metadata. */
    PATCH_STREAM_CHANGE_REJECTED,

    /** A Document Patch contains a PDF Value implementation not owned by Folio PDF. */
    PATCH_VALUE_REJECTED,

    /** A Canvas Program has an invalid number, operation, or state transition. */
    CANVAS_PROGRAM_INVALID,

    /** A declared Canvas Font is unavailable or not a supported Font resource. */
    CANVAS_RESOURCE_INVALID,

    /** Existing page content or resources cannot be preserved for Canvas drawing. */
    CANVAS_PRESERVATION_UNSUPPORTED,

    /** Encoded, sampled, or borrowed Canvas Image data is malformed or incompatible. */
    CANVAS_IMAGE_INVALID,

    /** A Canvas Image input or color profile is well-formed but unsupported. */
    CANVAS_RESOURCE_UNSUPPORTED,

    /** A requested optional Canvas Image codec is not available. */
    CANVAS_IMAGE_CODEC_UNAVAILABLE,

    /** A Canvas Color Space, color, alpha, blend mode, or group is invalid. */
    CANVAS_GRAPHICS_INVALID,

    /** A version-2 Canvas resource or generated-content bound was exceeded. */
    CANVAS_RESOURCE_LIMIT_EXCEEDED,

    /** A positioned Unicode text declaration is invalid. */
    POSITIONED_TEXT_INVALID,

    /** Explicit font source data could not be loaded or parsed safely. */
    FONT_SOURCE_INVALID,

    /** Explicit font data uses a format or profile outside version 1. */
    FONT_FORMAT_UNSUPPORTED,

    /** A font program's declared permissions prohibit outline embedding. */
    FONT_EMBEDDING_RESTRICTED,

    /** No declared font supplies one of the requested Unicode scalars. */
    FONT_GLYPH_MISSING,

    /** A requested source mapping cannot be represented unambiguously. */
    FONT_MAPPING_UNSUPPORTED,

    /** A caller-declared T19 source, text, fallback, or content bound was exhausted. */
    FONT_LIMIT_EXCEEDED,

    /** Existing page content or resources cannot be preserved for positioned text. */
    POSITIONED_TEXT_PRESERVATION_UNSUPPORTED,

    /** A paragraph, page, margin, area or inline declaration is invalid. */
    COMPOSITION_INVALID,

    /** No remaining declared area can fit the next line or atomic inline. */
    COMPOSITION_AREA_EXHAUSTED,

    /** A finite paragraph-flow declaration, line or generated-content bound was exceeded. */
    COMPOSITION_LIMIT_EXCEEDED,

    /** Finite areas cannot satisfy the declared keep or widow/orphan rules. */
    COMPOSITION_CONSTRAINT_UNSATISFIED,

    /** No current unsealed buffered paragraph flow is available for relayout. */
    COMPOSITION_RELAYOUT_UNSAFE,

    /** A staged document could not be written. */
    DOCUMENT_WRITE_FAILED,

    /** A staged output did not pass parseability validation. */
    DOCUMENT_VALIDATION_FAILED,

    /** A validated staged output could not be committed to its target. */
    PUBLICATION_FAILED,

    /** A library-owned document resource could not be closed cleanly. */
    RESOURCE_CLOSE_FAILED,

    /** A requested Capability Provider could not be selected or executed. */
    CAPABILITY_PROVIDER_FAILED,

    /** No eligible Capability Provider is registered for a requested capability. */
    CAPABILITY_PROVIDER_NOT_FOUND,

    /** A specifically selected Capability Provider reports unavailable. */
    CAPABILITY_PROVIDER_UNAVAILABLE,

    /** A remote Provider was requested without explicit disclosure permission. */
    REMOTE_DISCLOSURE_NOT_AUTHORIZED
}
