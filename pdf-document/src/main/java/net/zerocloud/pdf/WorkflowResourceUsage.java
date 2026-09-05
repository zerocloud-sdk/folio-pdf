package net.zerocloud.pdf;

import java.time.Duration;
import java.util.Objects;

/**
 * Detached high-water resource observations for one completed workflow.
 *
 * <p>The values describe Folio-owned accounting, not whole-process RSS,
 * operating-system caches, or a kernel isolation boundary.</p>
 *
 * @since 0.1.0
 */
public final class WorkflowResourceUsage {

    private final long acceptedInputBytes;
    private final long observedPages;
    private final long observedObjects;
    private final long decompressedBytes;
    private final long decodedPixels;
    private final long peakOwnedMemoryBytes;
    private final long peakTemporaryStorageBytes;
    private final Duration elapsedTime;

    WorkflowResourceUsage(
            long acceptedInputBytes,
            long observedPages,
            long observedObjects,
            long decompressedBytes,
            long decodedPixels,
            long peakOwnedMemoryBytes,
            long peakTemporaryStorageBytes,
            Duration elapsedTime) {
        this.acceptedInputBytes = requireNonnegative(
                acceptedInputBytes,
                "acceptedInputBytes");
        this.observedPages = requireNonnegative(
                observedPages,
                "observedPages");
        this.observedObjects = requireNonnegative(
                observedObjects,
                "observedObjects");
        this.decompressedBytes = requireNonnegative(
                decompressedBytes,
                "decompressedBytes");
        this.decodedPixels = requireNonnegative(
                decodedPixels,
                "decodedPixels");
        this.peakOwnedMemoryBytes = requireNonnegative(
                peakOwnedMemoryBytes,
                "peakOwnedMemoryBytes");
        this.peakTemporaryStorageBytes = requireNonnegative(
                peakTemporaryStorageBytes,
                "peakTemporaryStorageBytes");
        this.elapsedTime = Objects.requireNonNull(elapsedTime, "elapsedTime");
        if (elapsedTime.isNegative()) {
            throw new IllegalArgumentException(
                    "elapsedTime must not be negative");
        }
    }

    /** @return accepted logical Source bytes */
    public long getAcceptedInputBytes() {
        return acceptedInputBytes;
    }

    /** @return distinct pages observed by transaction validation */
    public long getObservedPages() {
        return observedPages;
    }

    /** @return distinct indirect objects observed by transaction validation */
    public long getObservedObjects() {
        return observedObjects;
    }

    /** @return cumulative supported decoded-stream output bytes */
    public long getDecompressedBytes() {
        return decompressedBytes;
    }

    /** @return cumulative materializable decoded pixels */
    public long getDecodedPixels() {
        return decodedPixels;
    }

    /** @return peak accounted Folio-owned memory bytes */
    public long getPeakOwnedMemoryBytes() {
        return peakOwnedMemoryBytes;
    }

    /** @return peak accounted transaction temporary-storage bytes */
    public long getPeakTemporaryStorageBytes() {
        return peakTemporaryStorageBytes;
    }

    /** @return elapsed time observed through the environment Clock */
    public Duration getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Reports whether every represented observation is within a policy.
     * Nesting and concurrency are admission properties and are not represented
     * by this per-workflow usage value.
     *
     * @param policy the policy to compare
     * @return whether every represented value is within its inclusive bound
     */
    public boolean isWithin(WorkflowResourcePolicy policy) {
        WorkflowResourcePolicy required = Objects.requireNonNull(
                policy,
                "policy");
        return acceptedInputBytes <= required.getMaximumInputBytes()
                && observedPages <= required.getMaximumPages()
                && observedObjects <= required.getMaximumObjects()
                && decompressedBytes
                        <= required.getMaximumDecompressedBytes()
                && decodedPixels <= required.getMaximumDecodedPixels()
                && peakOwnedMemoryBytes
                        <= required.getMaximumOwnedMemoryBytes()
                && peakTemporaryStorageBytes
                        <= required.getMaximumTemporaryStorageBytes()
                && elapsedTime.compareTo(
                        required.getMaximumElapsedTime()) <= 0;
    }

    private static long requireNonnegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }
}
