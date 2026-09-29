package net.zerocloud.pdf.itext7.kernel.pdf;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.zerocloud.pdf.PasswordCredential;

/** Owns only temporary conversions, never an immutable public password contract. */
final class FacadePasswords {
    private FacadePasswords() { }

    static PasswordCredential credential(byte[] bytes, boolean unicode) {
        char[] characters;
        if (unicode) {
            char[] working = new char[bytes.length];
            try {
                CharBuffer decoded = CharBuffer.wrap(working);
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                CoderResult status = decoder.decode(ByteBuffer.wrap(bytes), decoded, true);
                if (status.isError()) { status.throwException(); }
                status = decoder.flush(decoded);
                if (status.isError()) { status.throwException(); }
                characters = Arrays.copyOf(working, decoded.position());
            } catch (CharacterCodingException invalid) {
                throw new IllegalArgumentException("An AES-256 credential must contain valid UTF-8.");
            } finally { Arrays.fill(working, '\0'); }
        } else {
            characters = new char[bytes.length];
            for (int index = 0; index < bytes.length; index++) { characters[index] = (char) (bytes[index] & 0xff); }
        }
        try { return PasswordCredential.of(characters); }
        finally { Arrays.fill(characters, '\0'); }
    }
}
