package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.List;

/** Shared nonblocking admission gate owned by one Workflow Environment. */
final class WorkflowConcurrencyGate {

    private final List<Integer> activeLimits = new ArrayList<Integer>();

    synchronized Permit tryAcquire(int requestedLimit) {
        long proposed = (long) activeLimits.size() + 1L;
        if (proposed > requestedLimit) {
            return null;
        }
        for (Integer activeLimit : activeLimits) {
            if (proposed > activeLimit.intValue()) {
                return null;
            }
        }
        Integer retainedLimit = Integer.valueOf(requestedLimit);
        activeLimits.add(retainedLimit);
        return new Permit(this, retainedLimit);
    }

    private synchronized void release(Integer retainedLimit) {
        if (!activeLimits.remove(retainedLimit)) {
            throw new IllegalStateException(
                    "Workflow concurrency permit was already released.");
        }
    }

    static final class Permit implements AutoCloseable {

        private WorkflowConcurrencyGate gate;
        private final Integer retainedLimit;

        private Permit(
                WorkflowConcurrencyGate gate,
                Integer retainedLimit) {
            this.gate = gate;
            this.retainedLimit = retainedLimit;
        }

        @Override
        public void close() {
            WorkflowConcurrencyGate current = gate;
            if (current != null) {
                gate = null;
                current.release(retainedLimit);
            }
        }
    }
}
