package net.zerocloud.pdf;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The result of publishing one workflow output.
 *
 * @since 0.1.0
 */
public final class PublicationReceipt {

    private final Path target;
    private final PublicationStatus status;

    PublicationReceipt(Path target, PublicationStatus status) {
        this.target = Objects.requireNonNull(target, "target");
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * Returns the caller-supplied target path.
     *
     * @return the publication target
     */
    public Path getTarget() {
        return target;
    }

    /**
     * Returns the target disposition.
     *
     * @return the publication status
     */
    public PublicationStatus getStatus() {
        return status;
    }
}
