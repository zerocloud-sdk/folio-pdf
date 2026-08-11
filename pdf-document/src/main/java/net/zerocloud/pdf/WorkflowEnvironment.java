package net.zerocloud.pdf;

import java.time.Clock;
import java.util.Objects;

/**
 * Immutable environment shared by reusable Document Workflow instances.
 *
 * <p>T03 places deadline time behind this environment instead of accepting a
 * Clock directly at the {@link DocumentWorkflow} seam. A caller-supplied
 * Clock must be safe for the way the resulting environment is shared.</p>
 *
 * @since 0.1.0
 */
public final class WorkflowEnvironment {

    private static final WorkflowEnvironment SYSTEM_DEFAULTS =
            new WorkflowEnvironment(Clock.systemUTC());

    private final Clock clock;

    private WorkflowEnvironment(Clock clock) {
        this.clock = clock;
    }

    /**
     * Returns the immutable environment using the UTC system clock.
     *
     * @return the system-default environment
     */
    public static WorkflowEnvironment systemDefaults() {
        return SYSTEM_DEFAULTS;
    }

    /**
     * Creates an immutable environment using an explicit deadline Clock.
     *
     * @param clock the execution clock
     * @return an environment owning the supplied Clock
     */
    public static WorkflowEnvironment withClock(Clock clock) {
        return new WorkflowEnvironment(Objects.requireNonNull(clock, "clock"));
    }

    Clock getClock() {
        return clock;
    }
}
