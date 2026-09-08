package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import net.zerocloud.pdf.DocumentPatch;

/** A dictionary view whose mutations are validated by its Native Session. @since 0.1.0 */
public class PdfDictionary extends PdfObject {
    final FacadeSession session;
    final FacadeLocation location;
    private final net.zerocloud.pdf.PdfDictionary value;
    final Map<PdfName, PdfObject> detached;

    /** Creates an empty detached dictionary. */
    public PdfDictionary() {
        session = null;
        location = null;
        value = null;
        detached = new LinkedHashMap<PdfName, PdfObject>();
    }

    PdfDictionary(FacadeSession session, FacadeLocation location, net.zerocloud.pdf.PdfDictionary value) {
        this.session = session;
        this.location = location;
        this.value = value;
        this.detached = null;
    }

    /** @return the number of entries */
    public int size() {
        return value == null ? detached.size() : session.view(value::size).intValue();
    }

    /** @param key entry name @return the associated value, or null */
    public PdfObject get(PdfName key) {
        return get(key, true);
    }

    /** @param key entry name @param asDirect whether references are dereferenced @return the value or null */
    public PdfObject get(PdfName key, boolean asDirect) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            return FacadeValues.direct(detached.get(key), asDirect);
        }
        return session.view(() -> value.get(key.nativeValue()), (child, nativeSession) ->
                child == null ? null : FacadeValues.wrap(child, session,
                        location == null ? null : location.dictionaryEntry(key.nativeValue()), asDirect, nativeSession));
    }

    /** @param key entry name @return whether the name is present, including a PDF null entry */
    public boolean containsKey(PdfName key) {
        return get(key, false) != null;
    }

    /** @return an immutable snapshot of the entry names */
    public Set<PdfName> keySet() {
        if (value == null) {
            return Collections.unmodifiableSet(new LinkedHashSet<PdfName>(detached.keySet()));
        }
        return session.view(() -> {
            Set<PdfName> keys = new LinkedHashSet<PdfName>();
            for (int index = 0, count = value.size(); index < count; index++) {
                keys.add(new PdfName(value.getEntry(index).getName().getValue()));
            }
            return Collections.unmodifiableSet(keys);
        });
    }

    /** @param key entry name @param replacement replacement value @return the previous value, or null */
    public PdfObject put(PdfName key, PdfObject replacement) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(replacement, "replacement");
        if (value == null) {
            return detached.put(key, replacement);
        }
        requireAttached();
        return session.call(nativeSession -> {
            PdfObject previous = FacadeValues.wrap(value.get(key.nativeValue()), session, null, false, nativeSession);
            nativeSession.execute(DocumentPatch.builder().setDictionaryEntry(location.path(), key.nativeValue(), replacement.valueForPatch()).build());
            location.dictionaryChanged(key.nativeValue());
            return previous;
        });
    }

    /** @param key entry name @return the removed value, or null */
    public PdfObject remove(PdfName key) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            return detached.remove(key);
        }
        requireAttached();
        return session.call(nativeSession -> {
            PdfObject previous = FacadeValues.wrap(value.get(key.nativeValue()), session, null, false, nativeSession);
            nativeSession.execute(DocumentPatch.builder().removeDictionaryEntry(location.path(), key.nativeValue()).build());
            location.dictionaryChanged(key.nativeValue());
            return previous;
        });
    }

    final void requireAttached() {
        if (location == null || !location.isAttached()) {
            throw new IllegalStateException("The facade value is no longer attached to this document.");
        }
    }

    @Override
    public byte getType() {
        return DICTIONARY;
    }

    @Override
    net.zerocloud.pdf.PdfValue nativeValue() {
        if (value != null) {
            return value;
        }
        return FacadeValues.convert(this);
    }
}
