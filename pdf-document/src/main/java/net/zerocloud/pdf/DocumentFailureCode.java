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
