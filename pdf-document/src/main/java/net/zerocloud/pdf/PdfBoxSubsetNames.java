package net.zerocloud.pdf;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Deterministic document-local subset tags, including names in existing objects. */
final class PdfBoxSubsetNames {
    private static final int TAG_COUNT = 308915776; // 26^6
    private final PDDocument document;
    private final WorkflowResourceContext resources;
    private final Set<String> reserved = new HashSet<String>();

    PdfBoxSubsetNames(PDDocument document, WorkflowResourceContext resources) {
        this.document = document; this.resources = resources;
    }

    String reserve(byte[] source, Collection<Integer> glyphs) throws DocumentFailure {
        try (WorkflowResourceContext.OwnedMemoryScope memory = resources.ownedMemoryScope()) {
            memory.retain(512L + 64L * glyphs.size());
            IdentityHashMap<COSBase, Boolean> visited = new IdentityHashMap<COSBase, Boolean>();
            collect(document.getDocument().getTrailer(), visited, memory);
            resources.requireObjectCount(document.getDocument().getXrefTable().size());
            for (COSObjectKey key : document.getDocument().getXrefTable().keySet()) {
                collect(document.getDocument().getObjectFromPool(key), visited, memory);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int offset = 0; offset < source.length; offset += 8192) {
                resources.checkpoint(); digest.update(source, offset, Math.min(8192, source.length - offset));
            }
            for (int glyph : new TreeSet<Integer>(glyphs)) {
                resources.checkpoint(); digest.update((byte) (glyph >>> 8)); digest.update((byte) glyph);
            }
            byte[] hash = digest.digest();
            long seed = 0;
            for (int index = 0; index < 4; index++) { seed = seed * 256 + (hash[index] & 255); }
            for (int collision = 0; collision < TAG_COUNT; collision++) {
                resources.checkpoint();
                String tag = tag((int) ((seed + collision) % TAG_COUNT));
                if (add(tag)) { return tag + "+"; }
            }
            throw PdfBoxFontFailures.operationLimitExceeded();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String tag(int value) {
        char[] letters = new char[6];
        for (int index = 5; index >= 0; index--) { letters[index] = (char) ('A' + value % 26); value /= 26; }
        return new String(letters);
    }

    /** No stream decoding: only declared names in the already admitted object graph. */
    private void collect(COSBase root, IdentityHashMap<COSBase, Boolean> visited,
            WorkflowResourceContext.OwnedMemoryScope memory) throws DocumentFailure {
        Deque<Iterator<COSBase>> stack = new ArrayDeque<Iterator<COSBase>>();
        stack.push(Collections.singleton(root).iterator());
        while (!stack.isEmpty()) {
            resources.checkpoint();
            Iterator<COSBase> values = stack.peek();
            if (!values.hasNext()) { stack.pop(); continue; }
            COSBase value = values.next();
            if (value instanceof COSName) {
                String name = ((COSName) value).getName();
                if (name.length() > 7 && name.charAt(6) == '+') {
                    boolean uppercase = true;
                    for (int index = 0; index < 6; index++) { uppercase &= name.charAt(index) >= 'A' && name.charAt(index) <= 'Z'; }
                    if (uppercase) { add(name.substring(0, 6)); }
                }
                continue;
            }
            if (!(value instanceof COSObject || value instanceof COSArray || value instanceof COSDictionary)
                    || visited.containsKey(value)) { continue; }
            memory.retain(96);
            visited.put(value, Boolean.TRUE);
            resources.requireNestingDepth(stack.size());
            if (value instanceof COSObject) {
                resources.observeObject((COSObject) value);
                stack.push(Collections.singleton(((COSObject) value).getObject()).iterator());
            } else if (value instanceof COSArray) { stack.push(((COSArray) value).iterator()); }
            else { stack.push(((COSDictionary) value).getValues().iterator()); }
        }
    }

    private boolean add(String tag) throws DocumentFailure {
        if (reserved.contains(tag)) { return false; }
        resources.retainOwnedMemory(96);
        reserved.add(tag);
        return true;
    }
}
