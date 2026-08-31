package net.zerocloud.pdf.tools.release;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class ReleaseChecksums {

    static final List<Checksum> CENTRAL_CHECKSUMS = Collections.unmodifiableList(
            Arrays.asList(
                    new Checksum(".md5", "MD5"),
                    new Checksum(".sha1", "SHA-1"),
                    new Checksum(".sha256", "SHA-256"),
                    new Checksum(".sha512", "SHA-512")));

    private ReleaseChecksums() {
    }

    static String digestHex(String algorithm, byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm).digest(content);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                value.append(String.format("%02x", item & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK does not provide required digest "
                    + algorithm, e);
        }
    }

    static final class Checksum {
        final String suffix;
        final String algorithm;

        private Checksum(String suffix, String algorithm) {
            this.suffix = suffix;
            this.algorithm = algorithm;
        }
    }
}
