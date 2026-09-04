package net.zerocloud.pdf;

import java.util.Arrays;

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
