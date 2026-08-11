package net.zerocloud.pdf;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The disposition of one named publication target.
 *
 * <p>A receipt never claims cross-target atomicity. A failed stream target may
 * report that partial output is possible.</p>
 *
 * @since 0.1.0
 */
public final class PublicationReceipt {

    private final String targetName;
    private final Path pathTarget;
    private final PublicationStatus status;
    private final boolean partialOutputPossible;

    PublicationReceipt(Path pathTarget, PublicationStatus status) {
        this("target", pathTarget, status, false);
    }

    PublicationReceipt(
            String targetName,
            Path pathTarget,
            PublicationStatus status) {
        this(targetName, pathTarget, status, false);
    }

    PublicationReceipt(
            String targetName,
            Path pathTarget,
            PublicationStatus status,
            boolean partialOutputPossible) {
        this.targetName = Objects.requireNonNull(targetName, "targetName");
        this.pathTarget = pathTarget;
        this.status = Objects.requireNonNull(status, "status");
        this.partialOutputPossible = partialOutputPossible;
    }

    /**
     * Returns the request-local target name.
     *
     * @return the target name
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Returns the caller-supplied target path.
     *
     * <p>This compatibility accessor applies only to Path targets. New code
     * that handles both target forms should use {@link #getPathTarget()}.</p>
     *
     * @return the publication target
     * @throws IllegalStateException if this receipt describes a stream target
     * @deprecated use {@link #getPathTarget()} for the optional Path form
     */
    @Deprecated
    public Path getTarget() {
        if (pathTarget == null) {
            throw new IllegalStateException(
                    "The publication target is not a path target.");
        }
        return pathTarget;
    }

    /**
     * Returns the caller-supplied path for a path target.
     *
     * @return the optional path target
     */
    public Optional<Path> getPathTarget() {
        return Optional.ofNullable(pathTarget);
    }

    /**
     * Returns the target disposition.
     *
     * @return the publication status
     */
    public PublicationStatus getStatus() {
        return status;
    }

    /**
     * Reports whether a failed stream publication may have written bytes.
     *
     * @return {@code true} only when partial caller-owned stream output is possible
     */
    public boolean isPartialOutputPossible() {
        return partialOutputPossible;
    }
}
