package net.zerocloud.pdf;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.zerocloud.pdf.provider.ProviderPreference;

/** Material request shape bound to one environment-scoped transaction ID. */
final class WorkflowRequestFingerprint {

    private static final long FIXED_RETAINED_BYTES = 128L;
    private static final long DECLARATION_RETAINED_BYTES = 64L;
    private static final long TARGET_RETAINED_BYTES = 96L;
    private static final long IDENTITY_RETAINED_BYTES = 32L;
    private static final long BYTE_DIGEST_RETAINED_BYTES = 32L;
    private static final long STRING_RETAINED_BYTES = 16L;

    private final byte[] digest;
    private final List<IdentityValue> identityValues;

    private WorkflowRequestFingerprint(
            byte[] digest,
            List<IdentityValue> identityValues) {
        this.digest = digest;
        this.identityValues = identityValues;
    }

    static WorkflowRequestFingerprint capture(
            WorkflowRequest request,
            WorkflowTransactionId transactionId,
            long maximumRetainedBytes) throws DocumentFailure {
        long remaining = charge(
                maximumRetainedBytes,
                FIXED_RETAINED_BYTES,
                transactionId);
        remaining = chargeString(
                remaining,
                transactionId.getValue(),
                transactionId);
        MessageDigest digest = sha256();
        List<IdentityValue> identities = new ArrayList<IdentityValue>();
        update(digest, "folio-pdf-workflow-transaction-v2");
        update(digest, request.getExecutionProfile().name());
        update(digest, request.getSaveMode().name());
        remaining = updateNullable(
                digest,
                request.getPrimarySourceName(),
                remaining,
                transactionId);

        update(digest, request.getSources().size());
        for (Map.Entry<String, DocumentSource> entry
                : request.getSources().entrySet()) {
            remaining = charge(
                    remaining,
                    DECLARATION_RETAINED_BYTES,
                    transactionId);
            DocumentSource source = entry.getValue();
            remaining = update(
                    digest,
                    entry.getKey(),
                    remaining,
                    transactionId);
            update(digest, source.getKind().name());
            update(digest, source.getMaximumBytes());
            switch (source.getKind()) {
                case PATH:
                    remaining = update(
                            digest,
                            source.getPath().toAbsolutePath()
                                    .normalize().toString(),
                            remaining,
                            transactionId);
                    break;
                case BYTES:
                    remaining = charge(
                            remaining,
                            BYTE_DIGEST_RETAINED_BYTES,
                            transactionId);
                    update(digest, source.getBytes().length);
                    source.updateByteDigest(digest);
                    break;
                case STREAM:
                    remaining = addIdentity(
                            digest,
                            identities,
                            source.getStream(),
                            remaining,
                            transactionId);
                    break;
                case CHANNEL:
                    remaining = addIdentity(
                            digest,
                            identities,
                            source.getChannel(),
                            remaining,
                            transactionId);
                    break;
                default:
                    throw new IllegalStateException("Unsupported Source kind.");
            }
            remaining = addIdentity(
                    digest,
                    identities,
                    source.getCredential(),
                    remaining,
                    transactionId);
        }

        update(digest, request.getPublicationTargets().size());
        for (Map.Entry<String, PublicationTarget> entry
                : request.getPublicationTargets().entrySet()) {
            remaining = charge(
                    remaining,
                    TARGET_RETAINED_BYTES,
                    transactionId);
            PublicationTarget target = entry.getValue();
            remaining = update(
                    digest,
                    entry.getKey(),
                    remaining,
                    transactionId);
            update(digest, target.getKind().name());
            if (target.getKind() == PublicationTarget.Kind.PATH) {
                remaining = update(
                        digest,
                        target.getPath().toAbsolutePath()
                                .normalize().toString(),
                        remaining,
                        transactionId);
            } else {
                remaining = addIdentity(
                        digest,
                        identities,
                        target.getStream(),
                        remaining,
                        transactionId);
            }
        }

        PdfOutputPolicy outputPolicy = request.getOutputPolicy();
        update(digest, outputPolicy != null);
        if (outputPolicy != null) {
            update(digest, outputPolicy.getVersion().name());
            PasswordSecurityPolicy security = outputPolicy.getPasswordSecurity();
            update(digest, security != null);
            if (security != null) {
                update(digest, security.getAlgorithm().name());
                update(digest, security.getEncryptionScope().name());
                update(digest, security.getPermissions().getStandardMask());
                remaining = addIdentity(
                        digest,
                        identities,
                        security.getOwnerCredential(),
                        remaining,
                        transactionId);
                remaining = addIdentity(
                        digest,
                        identities,
                        security.getUserCredential(),
                        remaining,
                        transactionId);
            }
        }
        remaining = updateNullable(
                digest,
                request.getLegacySecurityMode() == null
                        ? null : request.getLegacySecurityMode().name(),
                remaining,
                transactionId);

        update(digest, request.getProviderPreferences().size());
        for (Map.Entry<String, ProviderPreference> entry
                : request.getProviderPreferences().entrySet()) {
            remaining = charge(
                    remaining,
                    DECLARATION_RETAINED_BYTES,
                    transactionId);
            remaining = update(
                    digest,
                    entry.getKey(),
                    remaining,
                    transactionId);
            remaining = updateNullable(
                    digest,
                    entry.getValue().getPreferredProviderId().orElse(null),
                    remaining,
                    transactionId);
            update(
                    digest,
                    request.isRemoteDisclosureAuthorized(entry.getKey()));
        }
        return new WorkflowRequestFingerprint(digest.digest(), identities);
    }

    private static long addIdentity(
            MessageDigest digest,
            List<IdentityValue> identities,
            Object value,
            long remaining,
            WorkflowTransactionId transactionId) throws DocumentFailure {
        update(digest, value != null);
        if (value != null) {
            remaining = charge(
                    remaining,
                    IDENTITY_RETAINED_BYTES,
                    transactionId);
            identities.add(new IdentityValue(value));
            remaining = update(
                    digest,
                    value.getClass().getName(),
                    remaining,
                    transactionId);
        }
        return remaining;
    }

    private static long updateNullable(
            MessageDigest digest,
            String value,
            long remaining,
            WorkflowTransactionId transactionId) throws DocumentFailure {
        update(digest, value != null);
        if (value != null) {
            remaining = update(
                    digest,
                    value,
                    remaining,
                    transactionId);
        }
        return remaining;
    }

    private static void update(MessageDigest digest, boolean value) {
        digest.update((byte) (value ? 1 : 0));
    }

    private static void update(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(4).putInt(value).array());
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(8).putLong(value).array());
    }

    private static void update(MessageDigest digest, String value) {
        update(digest, value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            digest.update((byte) (character >>> 8));
            digest.update((byte) character);
        }
    }

    private static long update(
            MessageDigest digest,
            String value,
            long remaining,
            WorkflowTransactionId transactionId) throws DocumentFailure {
        remaining = chargeString(remaining, value, transactionId);
        update(digest, value);
        return remaining;
    }

    private static long chargeString(
            long remaining,
            String value,
            WorkflowTransactionId transactionId) throws DocumentFailure {
        remaining = charge(
                remaining,
                STRING_RETAINED_BYTES,
                transactionId);
        return charge(
                remaining,
                2L * value.length(),
                transactionId);
    }

    private static long charge(
            long remaining,
            long amount,
            WorkflowTransactionId transactionId) throws DocumentFailure {
        if (amount > remaining) {
            throw new DocumentFailure(
                    DocumentFailureCode
                            .TRANSACTION_RETENTION_LIMIT_EXCEEDED,
                    HardenedWorkerEngine.CAPABILITY_ID,
                    "The transaction retained-metadata limit was exceeded.",
                    Collections.<PublicationReceipt>emptyList(),
                    transactionId);
        }
        return remaining - amount;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable.", failure);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WorkflowRequestFingerprint)) {
            return false;
        }
        WorkflowRequestFingerprint candidate =
                (WorkflowRequestFingerprint) other;
        if (!Arrays.equals(digest, candidate.digest)
                || identityValues.size() != candidate.identityValues.size()) {
            return false;
        }
        for (int index = 0; index < identityValues.size(); index++) {
            if (!identityValues.get(index).matches(
                    candidate.identityValues.get(index))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(digest);
        for (IdentityValue value : identityValues) {
            result = 31 * result + value.identityHash;
        }
        return result;
    }

    /** Does not keep caller-owned streams, channels, or credentials alive. */
    private static final class IdentityValue {

        private final WeakReference<Object> reference;
        private final int identityHash;

        private IdentityValue(Object value) {
            reference = new WeakReference<Object>(value);
            identityHash = System.identityHashCode(value);
        }

        private boolean matches(IdentityValue other) {
            Object value = reference.get();
            return value != null && value == other.reference.get();
        }
    }

}
