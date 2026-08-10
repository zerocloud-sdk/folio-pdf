package net.zerocloud.pdf;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable request for the minimal T01 path-based workflow.
 *
 * <p>A create request starts a new document and publishes one staged rewrite
 * to one path. An open request reads one path without publishing. Multi-source,
 * multi-target, incremental, deadline, cancellation, and resource-policy
 * options are intentionally outside T01.</p>
 *
 * @since 0.1.0
 */
public final class WorkflowRequest {

    private final Path source;
    private final Path publicationTarget;

    private WorkflowRequest(Path source, Path publicationTarget) {
        this.source = source;
        this.publicationTarget = publicationTarget;
    }

    /**
     * Creates a request for a new document and one path publication target.
     *
     * @param publicationTarget the path to replace after staging and validation
     * @return an immutable create request
     */
    public static WorkflowRequest create(Path publicationTarget) {
        return new WorkflowRequest(
                null,
                Objects.requireNonNull(publicationTarget, "publicationTarget"));
    }

    /**
     * Creates a read-only request for an existing path source.
     *
     * @param source the PDF path to open
     * @return an immutable open request
     */
    public static WorkflowRequest open(Path source) {
        return new WorkflowRequest(Objects.requireNonNull(source, "source"), null);
    }

    /**
     * Returns the source when this is an open request.
     *
     * @return the optional source path
     */
    public Optional<Path> getSource() {
        return Optional.ofNullable(source);
    }

    /**
     * Returns the target when this is a create request.
     *
     * @return the optional publication target
     */
    public Optional<Path> getPublicationTarget() {
        return Optional.ofNullable(publicationTarget);
    }
}
