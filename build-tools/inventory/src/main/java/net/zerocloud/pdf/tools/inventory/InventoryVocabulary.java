package net.zerocloud.pdf.tools.inventory;

enum ObligationKind {
    BEHAVIOR("behavior"), CONTROL("control"), AGGREGATE("aggregate"), RELEASE("release");

    private final String value;

    ObligationKind(String value) {
        this.value = value;
    }

    static ObligationKind from(String value) {
        for (ObligationKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        return null;
    }

    boolean requiresPdfEvidence() {
        return this == BEHAVIOR || this == CONTROL;
    }

    @Override
    public String toString() {
        return value;
    }
}

enum CapabilityState {
    PLANNED("planned"),
    EXPERIMENTAL("experimental"),
    COMPATIBLE("compatible"),
    LIMITED("limited");

    private final String value;

    CapabilityState(String value) {
        this.value = value;
    }

    static CapabilityState from(String value) {
        for (CapabilityState candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}

enum ProducerKind {
    EXTERNAL_TOOL("external-tool"),
    PROJECT_TEST("project-test"),
    HUMAN_REVIEW("human-review");

    private final String value;

    ProducerKind(String value) {
        this.value = value;
    }

    static ProducerKind from(String value) {
        for (ProducerKind candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}

enum AcceptanceChain {
    SYNTAX("syntax", ProducerKind.EXTERNAL_TOOL),
    STANDARDS("standards", ProducerKind.EXTERNAL_TOOL),
    SEMANTIC("semantic", ProducerKind.PROJECT_TEST),
    VISUAL("visual", ProducerKind.EXTERNAL_TOOL),
    HUMAN("human", ProducerKind.HUMAN_REVIEW);

    private final String value;
    private final ProducerKind producerKind;

    AcceptanceChain(String value, ProducerKind producerKind) {
        this.value = value;
        this.producerKind = producerKind;
    }

    static AcceptanceChain from(String value) {
        for (AcceptanceChain candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    ProducerKind requiredProducerKind() {
        return producerKind;
    }

    @Override
    public String toString() {
        return value;
    }
}

enum EvidenceResult {
    PASS("pass"),
    FAIL("fail"),
    INDETERMINATE("indeterminate");

    private final String value;

    EvidenceResult(String value) {
        this.value = value;
    }

    static EvidenceResult from(String value) {
        for (EvidenceResult candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}

enum FacadeAvailability {
    STABLE("stable"),
    PREVIEW("preview");

    private final String value;

    FacadeAvailability(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
