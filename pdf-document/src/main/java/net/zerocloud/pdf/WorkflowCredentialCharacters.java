package net.zerocloud.pdf;

import java.util.Arrays;
import java.security.SecureRandom;

/** Accounted, execution-local password characters with deterministic cleanup. */
final class WorkflowCredentialCharacters implements AutoCloseable {

    private char[] characters;
    private WorkflowResourceContext.MemoryReservation reservation;

    private WorkflowCredentialCharacters(
            char[] characters,
            WorkflowResourceContext.MemoryReservation reservation) {
        this.characters = characters;
        this.reservation = reservation;
    }

    static WorkflowCredentialCharacters copyOf(
            PasswordCredential credential,
            WorkflowResourceContext resources) throws DocumentFailure {
        int length = lengthOf(credential);
        WorkflowResourceContext.MemoryReservation reservation = resources == null
                ? null : resources.reserveOwnedMemory(2L * length);
        try {
            char[] characters = resources == null
                    ? credential.copyForExecution()
                    : credential.copyForExecution(resources);
            return new WorkflowCredentialCharacters(characters, reservation);
        } catch (IllegalStateException destroyed) {
            closeReservation(reservation);
            throw destroyedFailure();
        } catch (DocumentFailure | RuntimeException | Error failure) {
            closeReservation(reservation);
            throw failure;
        }
    }

    static int lengthOf(PasswordCredential credential)
            throws DocumentFailure {
        try {
            return credential.characterCountForExecution();
        } catch (IllegalStateException destroyed) {
            throw destroyedFailure();
        }
    }

    static WorkflowCredentialCharacters randomOwner(WorkflowResourceContext resources)
            throws DocumentFailure {
        WorkflowResourceContext.MemoryReservation reservation = resources.reserveOwnedMemory(160L);
        byte[] entropy = new byte[32];
        char[] characters = new char[64];
        try {
            new SecureRandom().nextBytes(entropy);
            for (int index = 0; index < entropy.length; index++) {
                characters[2 * index] = "0123456789abcdef".charAt((entropy[index] & 0xff) >>> 4);
                characters[2 * index + 1] = "0123456789abcdef".charAt(entropy[index] & 0xf);
            }
            return new WorkflowCredentialCharacters(characters, reservation);
        } catch (RuntimeException | Error failure) {
            Arrays.fill(characters, '\0');
            reservation.close();
            throw failure;
        } finally {
            Arrays.fill(entropy, (byte) 0);
        }
    }

    char[] get() {
        if (characters == null) {
            throw new IllegalStateException(
                    "Credential characters are no longer available.");
        }
        return characters;
    }

    @Override
    public void close() {
        if (characters != null) {
            Arrays.fill(characters, '\0');
            characters = null;
        }
        if (reservation != null) {
            reservation.close();
            reservation = null;
        }
    }

    private static void closeReservation(
            WorkflowResourceContext.MemoryReservation reservation) {
        if (reservation != null) {
            reservation.close();
        }
    }

    private static DocumentFailure destroyedFailure() {
        return PdfBoxWorkflowEngine.versionFailure(
                DocumentFailureCode.CREDENTIAL_DESTROYED,
                "A password credential was destroyed before execution.");
    }
}
