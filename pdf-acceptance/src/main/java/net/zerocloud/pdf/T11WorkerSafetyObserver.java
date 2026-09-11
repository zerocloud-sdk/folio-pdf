package net.zerocloud.pdf;

import java.lang.management.ManagementFactory;

/** Acceptance-harness bridge to the worker's active isolation controls. */
public final class T11WorkerSafetyObserver {
    private T11WorkerSafetyObserver() {
    }

    /**
     * Exercises forbidden file and network access in the current worker.
     * @param session the active Hardened Worker Session
     * @return detached observations from that same worker process
     * @throws DocumentFailure if the authenticated probe cannot complete
     */
    public static Observation observe(DocumentSession session)
            throws DocumentFailure {
        HardenedWorkerEngine.IsolationProbe probe =
                HardenedWorkerEngine.probeIsolation(session);
        return new Observation(
                probe.isFilesystemEscapeDenied(),
                probe.isOutboundNetworkDenied(),
                !ManagementFactory.getRuntimeMXBean().getName().equals(
                        probe.getWorkerProcessIdentity()));
    }

    /** Detached subset needed by the T11 acceptance recorder. */
    public static final class Observation {
        private final boolean fileAccessDenied;
        private final boolean networkAccessDenied;
        private final boolean processIsolated;

        private Observation(
                boolean fileAccessDenied,
                boolean networkAccessDenied,
                boolean processIsolated) {
            this.fileAccessDenied = fileAccessDenied;
            this.networkAccessDenied = networkAccessDenied;
            this.processIsolated = processIsolated;
        }

        public boolean isFileAccessDenied() {
            return fileAccessDenied;
        }

        public boolean isNetworkAccessDenied() {
            return networkAccessDenied;
        }

        public boolean isProcessIsolated() {
            return processIsolated;
        }
    }
}
