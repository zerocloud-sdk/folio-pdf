package net.zerocloud.pdf;

import java.util.Arrays;
import java.util.Objects;

/**
 * Caller-owned, destroyable password material.
 *
 * <p>Construction defensively copies the supplied character array. The
 * workflow takes a separate execution-local copy and never closes the
 * caller's credential. Callers should close the credential as soon as every
 * request that uses it has completed. Password material is never accepted as
 * a {@link String}.</p>
 */
public final class PasswordCredential implements AutoCloseable {

    private char[] characters;

    private PasswordCredential(char[] characters) {
        this.characters = Arrays.copyOf(characters, characters.length);
    }

    /**
     * Creates a credential from a defensively copied character array.
     *
     * @param characters password characters, which remain caller-owned
     * @return a destroyable credential
     */
    public static PasswordCredential of(char[] characters) {
        return new PasswordCredential(
                Objects.requireNonNull(characters, "characters"));
    }

    /** @return whether this credential's retained copy has been destroyed */
    public synchronized boolean isDestroyed() {
        return characters == null;
    }

    /** Destroys this credential's retained character copy. */
    @Override
    public synchronized void close() {
        if (characters != null) {
            Arrays.fill(characters, '\0');
            characters = null;
        }
    }

    synchronized char[] copyForExecution() {
        if (characters == null) {
            throw new IllegalStateException("The password credential is destroyed.");
        }
        return Arrays.copyOf(characters, characters.length);
    }

    synchronized int characterCountForExecution() {
        if (characters == null) {
            throw new IllegalStateException("The password credential is destroyed.");
        }
        return characters.length;
    }

    synchronized char[] copyForExecution(
            WorkflowResourceContext resources) throws DocumentFailure {
        if (characters == null) {
            throw new IllegalStateException("The password credential is destroyed.");
        }
        char[] copy = new char[characters.length];
        try {
            for (int offset = 0; offset < characters.length; offset += 4096) {
                resources.checkpoint();
                int length = Math.min(4096, characters.length - offset);
                System.arraycopy(characters, offset, copy, offset, length);
            }
            resources.checkpoint();
            return copy;
        } catch (DocumentFailure | RuntimeException | Error failure) {
            Arrays.fill(copy, '\0');
            throw failure;
        }
    }

    @Override
    public String toString() {
        return "PasswordCredential[redacted]";
    }
}
