package net.zerocloud.pdf;

import java.util.Objects;

/**
 * Caller-supplied identity for one logical Hardened Worker transaction.
 *
 * <p>The value is an opaque, non-secret application identifier. It is scoped
 * to the {@link WorkflowEnvironment} that first admits it.</p>
 *
 * @since 0.1.0
 */
public final class WorkflowTransactionId {

    /** Maximum number of ASCII characters in a version-1 identity. */
    public static final int MAXIMUM_LENGTH_VERSION_1 = 128;

    private final String value;

    private WorkflowTransactionId(String value) {
        this.value = value;
    }

    /**
     * Creates an identity from a stable application value.
     *
     * @param value one to 128 URL-safe ASCII characters
     * @return the immutable identity
     */
    public static WorkflowTransactionId of(String value) {
        String required = Objects.requireNonNull(value, "value");
        if (required.isEmpty()
                || required.length() > MAXIMUM_LENGTH_VERSION_1) {
            throw new IllegalArgumentException(
                    "A transaction identity must contain 1 to 128 characters.");
        }
        for (int index = 0; index < required.length(); index++) {
            char character = required.charAt(index);
            boolean allowed = character >= 'a' && character <= 'z'
                    || character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9'
                    || character == '.'
                    || character == '_'
                    || character == '~'
                    || character == '-';
            if (!allowed) {
                throw new IllegalArgumentException(
                        "A transaction identity must use URL-safe ASCII characters.");
            }
        }
        return new WorkflowTransactionId(required);
    }

    /** @return the caller-supplied opaque value */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorkflowTransactionId
                && value.equals(((WorkflowTransactionId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "WorkflowTransactionId[value=" + value + "]";
    }
}
