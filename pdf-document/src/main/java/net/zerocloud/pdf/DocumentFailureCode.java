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

    /** A bounded source exceeded its caller-declared byte limit. */
    SOURCE_LIMIT_EXCEEDED,

    /** The selected Save Mode is represented but not implemented. */
    SAVE_MODE_UNSUPPORTED,

    /** Cancellation was observed at an owned transaction boundary. */
    WORKFLOW_CANCELLED,

    /** The caller-declared workflow deadline has expired. */
    DEADLINE_EXCEEDED,

    /** A command is not part of the supported library-owned command set. */
    COMMAND_REJECTED,

    /** A query is not part of the supported library-owned query set. */
    QUERY_REJECTED,

    /** A supported query could not be evaluated. */
    QUERY_FAILED,

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

    /** A lazy PDF Value view was used after its Document Session ended. */
    PDF_VALUE_VIEW_EXPIRED,

    /** A caller-declared PDF Value inspection bound was exhausted. */
    PDF_VALUE_LIMIT_EXCEEDED,

    /** An Object Reference was supplied to a different Document Session. */
    OBJECT_REFERENCE_OWNERSHIP_INVALID,

    /** A Document Patch would introduce a cycle among its changed objects. */
    PATCH_CYCLE_REJECTED,

    /** A Document Patch attempted to change engine-owned stream metadata. */
    PATCH_STREAM_CHANGE_REJECTED,

    /** A Document Patch contains a PDF Value implementation not owned by Folio PDF. */
    PATCH_VALUE_REJECTED,

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
