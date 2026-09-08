package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfArray;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfIndirectReference;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfBoolean;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfNull;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfString;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfStream;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfNumber;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfObject;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PdfValuesFacadeTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void mappedNumberChangesPublishAndReopenThroughBothInterfaces() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-number.pdf");
        PdfName counter = new PdfName("Counter");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary catalog = document.getCatalog().getPdfObject();
            PdfNumber initial = new PdfNumber(2);
            initial.setValue(3);
            assertNull(catalog.put(counter, initial));
            PdfNumber observed = (PdfNumber) catalog.get(new PdfName("Counter"));
            assertTrue(observed.isNumber());
            assertEquals(PdfObject.NUMBER, observed.getType());
            assertEquals(3, observed.intValue());
            observed.setValue(7.25d);
            assertEquals(7.25d, ((PdfNumber) catalog.get(counter)).doubleValue(), 0d);
        }
        try (PdfReader reader = new PdfReader(output.toString());
                PdfDocument reopened = new PdfDocument(reader)) {
            PdfNumber value = (PdfNumber) reopened.getCatalog().getPdfObject().get(counter);
            assertEquals(7.25d, value.doubleValue(), 0d);
            assertEquals(1, reopened.getNumberOfPages());
        }
        new DocumentWorkflow().execute(WorkflowRequest.open(output, SaveMode.REWRITE), session -> {
            net.zerocloud.pdf.PdfDictionary root = (net.zerocloud.pdf.PdfDictionary) session.query(
                    InspectObject.version1(session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(8, 0)));
            assertEquals(net.zerocloud.pdf.PdfNumber.of(new BigDecimal("7.25")),
                    root.get(net.zerocloud.pdf.PdfName.of("Counter")));
            return null;
        });
    }

    @Test
    public void mappedScalarsKeepKindsUnicodeAndExactBytesAfterReplacementAndReopen() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-scalars.pdf");
        byte[] binary = {0, 40, 41, 92, (byte) 0xff};
        String unicode = "值补丁 \uD83D\uDCC4";
        PdfString text = new PdfString(unicode);
        assertEquals(unicode, text.getValue());
        assertArrayEquals(("\ufeff" + unicode).getBytes(StandardCharsets.UTF_16BE), text.getValueBytes());
        PdfString bytes = new PdfString(binary);
        binary[0] = 77;
        bytes.getValueBytes()[1] = 88;
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Null"), new PdfNull());
            assertTrue(root.get(new PdfName("Null")).isNull());
            assertEquals(PdfObject.NULL, root.put(new PdfName("Null"), PdfNull.PDF_NULL).getType());
            root.put(new PdfName("Boolean"), PdfBoolean.FALSE);
            assertFalse(((PdfBoolean) root.put(new PdfName("Boolean"), new PdfBoolean(true))).getValue());
            root.put(new PdfName("Text"), new PdfString("before"));
            assertEquals("before", ((PdfString) root.put(new PdfName("Text"), text)).getValue());
            root.put(new PdfName("Bytes"), bytes);
            root.put(new PdfName("Name"), new PdfName("Before"));
            assertEquals("Before", ((PdfName) root.put(new PdfName("Name"), new PdfName("After /# Ω"))).getValue());
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            assertTrue(root.get(new PdfName("Null")).isNull());
            assertTrue(root.get(new PdfName("Boolean")).isBoolean());
            assertEquals(PdfObject.BOOLEAN, PdfBoolean.TRUE.getType());
            assertTrue(((PdfBoolean) root.get(new PdfName("Boolean"))).getValue());
            assertTrue(root.get(new PdfName("Text")).isString());
            assertEquals(PdfObject.STRING, root.get(new PdfName("Text")).getType());
            assertEquals(10, root.get(new PdfName("Text")).getType());
            assertEquals(unicode, ((PdfString) root.get(new PdfName("Text"))).getValue());
            assertArrayEquals(new byte[] {0, 40, 41, 92, (byte) 0xff}, ((PdfString) root.get(new PdfName("Bytes"))).getValueBytes());
            assertTrue(root.get(new PdfName("Name")).isName());
            assertEquals("After /# Ω", ((PdfName) root.get(new PdfName("Name"))).getValue());
        }
        new DocumentWorkflow().execute(WorkflowRequest.open(output, SaveMode.REWRITE), session -> {
            net.zerocloud.pdf.PdfDictionary root = (net.zerocloud.pdf.PdfDictionary) session.query(
                    InspectObject.version1(session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(12, 0)));
            assertEquals(net.zerocloud.pdf.PdfNull.INSTANCE, root.get(net.zerocloud.pdf.PdfName.of("Null")));
            assertEquals(net.zerocloud.pdf.PdfBoolean.of(true), root.get(net.zerocloud.pdf.PdfName.of("Boolean")));
            assertEquals(net.zerocloud.pdf.PdfString.of(text.getValueBytes()), root.get(net.zerocloud.pdf.PdfName.of("Text")));
            assertEquals(net.zerocloud.pdf.PdfName.of("After /# Ω"), root.get(net.zerocloud.pdf.PdfName.of("Name")));
            return null;
        });
    }

    @Test
    public void textInspectionDecodesPdfDocEncodingWithoutChangingItsRawBytes() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-pdfdocencoding.pdf");
        byte[] encoded = {24, (byte) 128, (byte) 138, (byte) 160, (byte) 254};
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            document.getCatalog().getPdfObject().put(new PdfName("Text"), new PdfString(encoded));
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfString inspected = (PdfString) document.getCatalog().getPdfObject().get(new PdfName("Text"));
            assertEquals("˘•−€þ", inspected.getValue());
            assertArrayEquals(encoded, inspected.getValueBytes());
        }
    }

    @Test
    public void textInspectionRecognizesUtf8AndPreservesMalformedBinaryStrings() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-unicode-encodings.pdf");
        byte[] utf8 = "\ufeff值\uD83D\uDCC4".getBytes(StandardCharsets.UTF_8);
        byte[] malformed = {(byte) 0xfe, (byte) 0xff, (byte) 0xd8, 0};
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Utf8"), new PdfString(utf8));
            root.put(new PdfName("Malformed"), new PdfString(malformed));
            root.put(new PdfName("Undefined"), new PdfString(new byte[] {127, (byte) 159, (byte) 173}));
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            assertEquals("值\uD83D\uDCC4", ((PdfString) root.get(new PdfName("Utf8"))).getValue());
            assertArrayEquals(utf8, ((PdfString) root.get(new PdfName("Utf8"))).getValueBytes());
            assertEquals("\ufffd", ((PdfString) root.get(new PdfName("Malformed"))).getValue());
            assertArrayEquals(malformed, ((PdfString) root.get(new PdfName("Malformed"))).getValueBytes());
            assertEquals("\ufffd\ufffd\ufffd", ((PdfString) root.get(new PdfName("Undefined"))).getValue());
        }
    }

    @Test
    public void invalidJavaTextIsRejectedBeforeChangingTheDocument() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-invalid-text.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Text"), new PdfString("retained"));
            for (String invalid : new String[] {"bad\ud800", "bad\udc00", "\ud800x"}) {
                try {
                    root.put(new PdfName("Text"), new PdfString(invalid));
                    fail("An unpaired surrogate must not silently lose text");
                } catch (IllegalArgumentException expected) {
                    assertEquals("The PDF text contains an unpaired surrogate.", expected.getMessage());
                }
            }
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            assertEquals("retained", ((PdfString) document.getCatalog().getPdfObject().get(new PdfName("Text"))).getValue());
        }
    }

    @Test
    public void nestedDictionaryInsertionReplacementAndRemovalPublishThroughNativePatches() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-dictionaries.pdf");
        PdfName settings = new PdfName("Settings");
        PdfDictionary initial = new PdfDictionary();
        assertEquals(0, initial.size());
        initial.put(new PdfName("Counter"), new PdfNumber(4));
        initial.put(new PdfName("Obsolete"), new PdfString("remove"));
        assertTrue(initial.containsKey(new PdfName("Counter")));
        assertTrue(initial.keySet().contains(new PdfName("Obsolete")));
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(settings, initial);
            PdfDictionary live = (PdfDictionary) root.get(settings, true);
            assertTrue(live.isDictionary());
            assertEquals(3, live.getType());
            assertEquals(2, live.size());
            assertTrue(live.keySet().contains(new PdfName("Counter")));
            assertTrue(root.get(settings, false).isDictionary());
            PdfNumber previous = (PdfNumber) live.put(new PdfName("Counter"), new PdfNumber(12));
            previous.setValue(99);
            assertEquals(12, ((PdfNumber) live.get(new PdfName("Counter"))).intValue());
            assertEquals("remove", ((PdfString) live.remove(new PdfName("Obsolete"))).getValue());
            assertNull(live.remove(new PdfName("Absent")));
            assertFalse(live.containsKey(new PdfName("Obsolete")));
            assertEquals(1, live.size());
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfDictionary live = (PdfDictionary) document.getCatalog().getPdfObject().get(settings);
            assertEquals(1, live.size());
            assertEquals(12, ((PdfNumber) live.get(new PdfName("Counter"))).intValue());
        }
        new DocumentWorkflow().execute(WorkflowRequest.open(output, SaveMode.REWRITE), session -> {
            net.zerocloud.pdf.PdfDictionary root = (net.zerocloud.pdf.PdfDictionary) session.query(
                    InspectObject.version1(session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(8, 0)));
            net.zerocloud.pdf.PdfDictionary nested = (net.zerocloud.pdf.PdfDictionary) root.get(net.zerocloud.pdf.PdfName.of("Settings"));
            assertEquals(1, nested.size());
            assertEquals(net.zerocloud.pdf.PdfNumber.of(12), nested.get(net.zerocloud.pdf.PdfName.of("Counter")));
            return null;
        });
    }

    @Test
    public void arrayInsertionReplacementAndRemovalPublishThroughNativePatches() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-arrays.pdf");
        List<PdfObject> input = new ArrayList<PdfObject>(Arrays.asList(new PdfNumber(1), new PdfString("remove")));
        PdfArray initial = new PdfArray(input);
        input.clear();
        assertEquals(2, initial.size());
        initial.add(PdfNull.PDF_NULL);
        initial.add(1, PdfBoolean.FALSE);
        assertFalse(((PdfBoolean) initial.set(1, PdfBoolean.TRUE)).getValue());
        initial.remove(2);
        assertEquals(0, new PdfArray().size());
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Items"), initial);
            PdfArray live = (PdfArray) root.get(new PdfName("Items"));
            assertTrue(live.isArray());
            assertEquals(1, live.getType());
            assertEquals(3, live.size());
            live.add(1, new PdfNumber(2));
            PdfNumber replaced = (PdfNumber) live.set(0, new PdfNumber(3));
            assertEquals(1, replaced.intValue());
            replaced.setValue(99);
            live.remove(3);
            live.add(new PdfString("tail"));
            assertTrue(live.get(2, false).isBoolean());
            ((PdfNumber) live.get(1, true)).setValue(4);
            assertEquals(3, ((PdfNumber) live.get(0)).intValue());
            assertEquals(4, ((PdfNumber) live.get(1)).intValue());
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfArray live = (PdfArray) document.getCatalog().getPdfObject().get(new PdfName("Items"));
            assertEquals(4, live.size());
            assertEquals(3, ((PdfNumber) live.get(0)).intValue());
            assertEquals(4, ((PdfNumber) live.get(1)).intValue());
            assertTrue(((PdfBoolean) live.get(2)).getValue());
            assertEquals("tail", ((PdfString) live.get(3)).getValue());
        }
        new DocumentWorkflow().execute(WorkflowRequest.open(output, SaveMode.REWRITE), session -> {
            net.zerocloud.pdf.PdfDictionary root = (net.zerocloud.pdf.PdfDictionary) session.query(
                    InspectObject.version1(session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(12, 0)));
            net.zerocloud.pdf.PdfArray array = (net.zerocloud.pdf.PdfArray) root.get(net.zerocloud.pdf.PdfName.of("Items"));
            assertEquals(4, array.size());
            assertEquals(net.zerocloud.pdf.PdfNumber.of(4), array.get(1));
            return null;
        });
    }

    @Test
    public void observedArrayValuesFollowInsertionsAndRemovalThroughAnotherView() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-array-views.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary nested = new PdfDictionary();
            nested.put(new PdfName("Counter"), new PdfNumber(30));
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Items"), new PdfArray(Arrays.asList(new PdfNumber(10), new PdfNumber(20), nested)));
            PdfArray first = (PdfArray) root.get(new PdfName("Items"));
            PdfArray second = (PdfArray) root.get(new PdfName("Items"));
            PdfNumber held = (PdfNumber) first.get(1);
            PdfDictionary heldDictionary = (PdfDictionary) first.get(2);
            second.add(0, new PdfNumber(5));
            held.setValue(21);
            heldDictionary.put(new PdfName("Counter"), new PdfNumber(31));
            assertEquals(10, ((PdfNumber) second.get(1)).intValue());
            assertEquals(21, ((PdfNumber) second.get(2)).intValue());
            first.remove(0);
            held.setValue(22);
            assertEquals(22, ((PdfNumber) second.get(1)).intValue());
            second.set(1, new PdfNumber(80));
            held.setValue(99);
            assertEquals(80, ((PdfNumber) first.get(1)).intValue());
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfArray array = (PdfArray) document.getCatalog().getPdfObject().get(new PdfName("Items"));
            assertEquals(80, ((PdfNumber) array.get(1)).intValue());
            assertEquals(31, ((PdfNumber) ((PdfDictionary) array.get(2)).get(new PdfName("Counter"))).intValue());
        }
    }

    @Test
    public void replacedDictionaryValuesCannotMutateTheNewOccupantOfTheirLocation() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-dictionary-views.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary initial = new PdfDictionary();
            initial.put(new PdfName("Counter"), new PdfNumber(40));
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Config"), initial);
            PdfDictionary first = (PdfDictionary) root.get(new PdfName("Config"));
            PdfDictionary second = (PdfDictionary) root.get(new PdfName("Config"));
            PdfNumber held = (PdfNumber) first.get(new PdfName("Counter"));
            PdfNumber alias = (PdfNumber) second.get(new PdfName("Counter"));
            held.setValue(41);
            assertEquals(41, alias.intValue());
            second.put(new PdfName("Counter"), new PdfNumber(50));
            held.setValue(99);
            assertEquals(50, ((PdfNumber) first.get(new PdfName("Counter"))).intValue());
            PdfDictionary replacement = new PdfDictionary();
            replacement.put(new PdfName("Counter"), new PdfNumber(55));
            root.put(new PdfName("Config"), replacement);
            try {
                first.put(new PdfName("Counter"), new PdfNumber(90));
                fail("A removed container view must not edit its replacement");
            } catch (IllegalStateException expected) {
                assertEquals("The facade value is no longer attached to this document.", expected.getMessage());
            }
            assertEquals(50, ((PdfNumber) second.get(new PdfName("Counter"))).intValue());
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfDictionary current = (PdfDictionary) document.getCatalog().getPdfObject().get(new PdfName("Config"));
            assertEquals(55, ((PdfNumber) current.get(new PdfName("Counter"))).intValue());
        }
    }

    @Test
    public void readerWriterEditingAndCopyingPreserveTheSourceAndExistingValues() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("facade-edit-source.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(source.toString()))) {
            document.addNewPage();
            document.getCatalog().getPdfObject().put(new PdfName("Counter"), new PdfNumber(7));
        }
        byte[] original = Files.readAllBytes(source);
        for (boolean edit : new boolean[] {false, true}) {
            Path output = temporaryFolder.getRoot().toPath().resolve("facade-edited-" + edit + ".pdf");
            try (PdfReader reader = new PdfReader(source.toString());
                    PdfDocument document = new PdfDocument(reader, new PdfWriter(output.toString()))) {
                reader.close();
                assertEquals(1, document.getNumberOfPages());
                if (edit) {
                    ((PdfNumber) document.getCatalog().getPdfObject().get(new PdfName("Counter"))).setValue(9);
                    document.addNewPage();
                }
            }
            assertArrayEquals(original, Files.readAllBytes(source));
            try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
                assertEquals(edit ? 2 : 1, document.getNumberOfPages());
                assertEquals(edit ? 9 : 7, ((PdfNumber) document.getCatalog().getPdfObject().get(new PdfName("Counter"))).intValue());
            }
        }
    }

    @Test
    public void indirectAliasesKeepSessionIdentityDuringValueChangesAndReferenceReplacement() throws Exception {
        Path source = copyFixture("protected-containers.pdf");
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-references.pdf");
        byte[] original = Files.readAllBytes(source);
        PdfIndirectReference retained;
        try (PdfReader reader = new PdfReader(source.toString());
                PdfDocument document = new PdfDocument(reader, new PdfWriter(output.toString()))) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            assertTrue(root.getIndirectReference().getRefersTo().isDictionary());
            retained = (PdfIndirectReference) root.get(new PdfName("Scalar"), false);
            assertTrue(retained.isIndirectReference());
            assertEquals(5, retained.getType());
            assertEquals(retained, root.get(new PdfName("ScalarAlias"), false));
            assertEquals(retained.hashCode(), root.get(new PdfName("ScalarAlias"), false).hashCode());
            PdfNumber value = (PdfNumber) root.get(new PdfName("Scalar"));
            assertEquals(retained, value.getIndirectReference());
            value.setValue(42);
            assertEquals(42, ((PdfNumber) retained.getRefersTo()).intValue());
            assertEquals(42, ((PdfNumber) root.get(new PdfName("ScalarAlias"))).intValue());
            root.put(new PdfName("NewAlias"), value);
            assertEquals(retained, root.get(new PdfName("NewAlias"), false));
            PdfArray aliases = new PdfArray(Arrays.asList(retained));
            root.put(new PdfName("Aliases"), aliases);
            PdfArray live = (PdfArray) root.get(new PdfName("Aliases"));
            assertEquals(retained, live.get(0, false));
            assertEquals(42, ((PdfNumber) live.get(0)).intValue());
            live.set(0, new PdfNumber(6));
            assertNull(live.get(0).getIndirectReference());
            assertEquals(42, ((PdfNumber) root.get(new PdfName("NewAlias"))).intValue());
        }
        assertArrayEquals(original, Files.readAllBytes(source));
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            assertFalse(retained.equals(root.get(new PdfName("Scalar"), false)));
            assertEquals(root.get(new PdfName("Scalar"), false), root.get(new PdfName("NewAlias"), false));
            assertEquals(42, ((PdfNumber) root.get(new PdfName("ScalarAlias"))).intValue());
            assertEquals(6, ((PdfNumber) ((PdfArray) root.get(new PdfName("Aliases"))).get(0)).intValue());
        }
    }

    @Test
    public void streamBytesAndDictionaryChangesKeepIndirectAliasesAfterPublication() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-streams.pdf");
        byte[] initial = {1, 2, 3};
        PdfStream detached = new PdfStream(initial);
        initial[0] = 90;
        detached.getBytes()[0] = 80;
        assertArrayEquals(new byte[] {1, 2, 3}, detached.getBytes());
        detached.setData(new byte[] {4, 5});
        detached.put(new PdfName("Temporary"), new PdfString("remove"));
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Payload"), detached);
            PdfStream live = (PdfStream) root.get(new PdfName("Payload"));
            assertTrue(live.isStream());
            assertFalse(live.isDictionary());
            assertEquals(9, live.getType());
            assertArrayEquals(new byte[] {4, 5}, live.getBytes());
            PdfIndirectReference reference = live.getIndirectReference();
            assertTrue(reference != null);
            root.put(new PdfName("Alias"), live);
            assertEquals(reference, root.get(new PdfName("Alias"), false));
            root.remove(new PdfName("Payload"));
            byte[] replacement = {6, 7, 8, 9};
            live.setData(replacement);
            replacement[0] = 99;
            live.put(new PdfName("Tag"), new PdfName("edited"));
            assertEquals("remove", ((PdfString) live.remove(new PdfName("Temporary"))).getValue());
            assertArrayEquals(new byte[] {6, 7, 8, 9}, ((PdfStream) reference.getRefersTo()).getBytes());
            assertEquals(reference, ((PdfStream) root.get(new PdfName("Alias"))).getIndirectReference());
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfStream stream = (PdfStream) document.getCatalog().getPdfObject().get(new PdfName("Alias"));
            assertArrayEquals(new byte[] {6, 7, 8, 9}, stream.getBytes());
            assertEquals("edited", ((PdfName) stream.get(new PdfName("Tag"))).getValue());
            assertFalse(stream.containsKey(new PdfName("Temporary")));
        }
        new DocumentWorkflow().execute(WorkflowRequest.open(output, SaveMode.REWRITE), session -> {
            net.zerocloud.pdf.PdfDictionary root = (net.zerocloud.pdf.PdfDictionary) session.query(
                    InspectObject.version1(session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(16, 16)));
            net.zerocloud.pdf.PdfValue alias = root.get(net.zerocloud.pdf.PdfName.of("Alias"));
            net.zerocloud.pdf.PdfStream stream = alias instanceof net.zerocloud.pdf.PdfIndirectReference
                    ? (net.zerocloud.pdf.PdfStream) session.query(InspectObject.version1(
                            ((net.zerocloud.pdf.PdfIndirectReference) alias).getReference(), PdfInspectionLimits.of(16, 16)))
                    : (net.zerocloud.pdf.PdfStream) alias;
            assertArrayEquals(new byte[] {6, 7, 8, 9}, stream.readBytes());
            assertEquals(net.zerocloud.pdf.PdfName.of("FlateDecode"), stream.getDictionary().get(net.zerocloud.pdf.PdfName.of("Filter")));
            return null;
        });
    }

    @Test
    public void readerWriterRetainsPagesQueuedBeforeLazySessionInitialization() throws Exception {
        Path source = temporaryFolder.getRoot().toPath().resolve("queued-page-source.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(source.toString()))) {
            document.addNewPage();
        }
        for (boolean inspect : new boolean[] {false, true}) {
            Path output = temporaryFolder.getRoot().toPath().resolve("queued-page-" + inspect + ".pdf");
            try (PdfReader reader = new PdfReader(source.toString());
                    PdfDocument document = new PdfDocument(reader, new PdfWriter(output.toString()))) {
                document.addNewPage();
                if (inspect) {
                    assertTrue(document.getCatalog().getPdfObject().isDictionary());
                }
                assertEquals(2, document.getNumberOfPages());
            }
            try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
                assertEquals(2, document.getNumberOfPages());
            }
        }
    }

    @Test
    public void detachedContainmentCyclesFailBeforeMutationWhileSharedChildrenPublish() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-containment-cycles.pdf");
        PdfArray arrayCycle = new PdfArray();
        arrayCycle.add(arrayCycle);
        PdfDictionary dictionaryCycle = new PdfDictionary();
        dictionaryCycle.put(new PdfName("Self"), dictionaryCycle);
        PdfStream streamCycle = new PdfStream(new byte[] {1});
        streamCycle.put(new PdfName("Self"), streamCycle);
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Kept"), new PdfNumber(17));
            for (PdfObject invalid : new PdfObject[] {arrayCycle, dictionaryCycle, streamCycle}) {
                try {
                    root.put(new PdfName("Rejected"), invalid);
                    fail("A Java containment cycle must be rejected before a Native Patch");
                } catch (PdfException failure) {
                    assertEquals("PATCH_CYCLE_REJECTED: The Document Patch would introduce a cyclic PDF value.", failure.getMessage());
                    assertNull(failure.getCause());
                }
                assertFalse(root.containsKey(new PdfName("Rejected")));
                assertEquals(17, ((PdfNumber) root.get(new PdfName("Kept"))).intValue());
            }
            PdfArray shared = new PdfArray(Arrays.asList(new PdfNumber(8)));
            root.put(new PdfName("Shared"), new PdfArray(Arrays.asList(shared, shared)));
            root.put(new PdfName("Continued"), PdfBoolean.TRUE);
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            assertTrue(((PdfBoolean) root.get(new PdfName("Continued"))).getValue());
            assertFalse(root.containsKey(new PdfName("Rejected")));
            PdfArray outer = (PdfArray) root.get(new PdfName("Shared"));
            assertEquals(8, ((PdfNumber) ((PdfArray) outer.get(0)).get(0)).intValue());
            assertEquals(8, ((PdfNumber) ((PdfArray) outer.get(1)).get(0)).intValue());
        }
    }

    @Test(timeout = 15000)
    public void deeplyNestedDetachedValuesReachNativeLimitsWithoutAJavaStackOverflow() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-deep-values.pdf");
        byte[] prior = "retain prior publication".getBytes(StandardCharsets.US_ASCII);
        Files.write(output, prior);
        PdfObject nested = PdfNull.PDF_NULL;
        for (int depth = 0; depth < 12000; depth++) {
            nested = new PdfArray(Arrays.asList(nested));
        }
        PdfDocument document = new PdfDocument(new PdfWriter(output.toString()));
        document.addNewPage();
        try {
            document.getCatalog().getPdfObject().put(new PdfName("Deep"), nested);
            fail("Deep values must reach the actual Native nesting guard");
        } catch (PdfException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            assertEquals(DocumentFailureCode.NESTING_LIMIT_EXCEEDED, ((DocumentFailure) failure.getCause()).getCode());
        } finally {
            try {
                document.close();
            } catch (PdfException failure) {
                assertTrue(failure.getCause() instanceof DocumentFailure);
                assertEquals(DocumentFailureCode.NESTING_LIMIT_EXCEEDED, ((DocumentFailure) failure.getCause()).getCode());
            }
        }
        assertArrayEquals(prior, Files.readAllBytes(output));
    }

    @Test
    public void detachedContainersHonorDereferenceFlagsAndKeepReferenceOwnership() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("detached-reference-views.pdf");
        PdfArray array = new PdfArray();
        PdfDictionary dictionary = new PdfDictionary();
        PdfIndirectReference reference;
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            reference = document.getCatalog().getPdfObject().getIndirectReference();
            array.add(reference);
            dictionary.put(new PdfName("Root"), reference);
            assertEquals(reference, array.get(0, false));
            assertEquals(reference, dictionary.get(new PdfName("Root"), false));
            assertTrue(array.get(0).isDictionary());
            assertTrue(array.get(0, true).isDictionary());
            assertTrue(dictionary.get(new PdfName("Root")).isDictionary());
            assertTrue(dictionary.get(new PdfName("Root"), true).isDictionary());
            assertEquals(reference, array.get(0).getIndirectReference());
        }
        assertEquals(reference, array.get(0, false));
        assertEquals(reference, dictionary.get(new PdfName("Root"), false));
        try {
            array.get(0);
            fail("Dereferencing remains owned by the closed Session");
        } catch (IllegalStateException expected) {
            assertEquals("The facade document is closed.", expected.getMessage());
        }
    }

    @Test
    public void closedContainerAndStreamViewsRetainTheNativeExpiryFailure() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-expired-views.pdf");
        List<Runnable> reads;
        PdfNumber number;
        PdfString string;
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Number"), new PdfNumber(3));
            root.put(new PdfName("Text"), new PdfString("retained"));
            root.put(new PdfName("Items"), new PdfArray(Arrays.asList(PdfBoolean.TRUE)));
            root.put(new PdfName("Data"), new PdfStream(new byte[] {1, 2}));
            number = (PdfNumber) root.get(new PdfName("Number"));
            string = (PdfString) root.get(new PdfName("Text"));
            PdfArray array = (PdfArray) root.get(new PdfName("Items"));
            PdfStream stream = (PdfStream) root.get(new PdfName("Data"));
            reads = Arrays.asList(() -> root.size(), () -> root.get(new PdfName("Number")),
                    () -> root.keySet(), () -> root.containsKey(new PdfName("Number")),
                    () -> array.size(), () -> array.get(0), () -> stream.getBytes(),
                    () -> stream.size(), () -> stream.get(new PdfName("Length")));
        }
        assertEquals(3, number.intValue());
        assertEquals("retained", string.getValue());
        for (Runnable read : reads) {
            try {
                read.run();
                fail("A closed Session view must produce its Native expiry failure");
            } catch (PdfException failure) {
                assertTrue(failure.getCause() instanceof DocumentFailure);
                assertEquals(DocumentFailureCode.PDF_VALUE_VIEW_EXPIRED, ((DocumentFailure) failure.getCause()).getCode());
                assertNull(failure.getCause().getCause());
            }
        }
    }

    @Test(timeout = 30000)
    public void facadeContainerInspectionIsBoundedWithoutPoisoningValidMutation() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-container-limit.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Counter"), new PdfNumber(3));
            PdfNumber held = (PdfNumber) root.get(new PdfName("Counter"));
            int reads = 0;
            try {
                for (; reads <= 100000; reads++) {
                    root.get(new PdfName("Counter"));
                }
                fail("The mapped Native inspection must enforce its traversal bound");
            } catch (PdfException failure) {
                assertTrue(failure.getCause() instanceof DocumentFailure);
                assertEquals(DocumentFailureCode.PDF_VALUE_LIMIT_EXCEEDED, ((DocumentFailure) failure.getCause()).getCode());
                assertTrue(reads >= 99990 && reads < 100000);
            }
            held.setValue(7);
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            assertEquals(7, ((PdfNumber) document.getCatalog().getPdfObject().get(new PdfName("Counter"))).intValue());
        }
    }

    @Test(timeout = 30000)
    public void facadeDecodedStreamReadsShareTheirCumulativeBoundAndStillPermitAValidPatch() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-stream-limit.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Data"), new PdfStream(new byte[1 << 20]));
            PdfStream stream = (PdfStream) root.get(new PdfName("Data"));
            for (int read = 0; read < 64; read++) {
                assertEquals(1 << 20, stream.getBytes().length);
            }
            try {
                stream.getBytes();
                fail("Repeated reads must share the 64 MiB decoded-byte bound");
            } catch (PdfException failure) {
                assertTrue(failure.getCause() instanceof DocumentFailure);
                assertEquals(DocumentFailureCode.PDF_VALUE_LIMIT_EXCEEDED, ((DocumentFailure) failure.getCause()).getCode());
            }
            stream.setData(new byte[] {9});
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            assertArrayEquals(new byte[] {9}, ((PdfStream) document.getCatalog().getPdfObject().get(new PdfName("Data"))).getBytes());
        }
    }

    @Test
    public void callerStreamsStayOpenAcrossCreateEditAndReopen() throws Exception {
        TrackedOutput first = new TrackedOutput();
        try (PdfDocument document = new PdfDocument(new PdfWriter(first))) {
            document.addNewPage();
            document.getCatalog().getPdfObject().put(new PdfName("Counter"), new PdfNumber(3));
            assertEquals(0, first.size());
        }
        assertFalse(first.closed);
        assertTrue(first.flushed);
        TrackedInput input = new TrackedInput(first.toByteArray());
        TrackedOutput edited = new TrackedOutput();
        try (PdfReader reader = new PdfReader(input);
                PdfDocument document = new PdfDocument(reader, new PdfWriter(edited))) {
            reader.close();
            assertFalse(input.closed);
            ((PdfNumber) document.getCatalog().getPdfObject().get(new PdfName("Counter"))).setValue(8);
        }
        assertFalse(input.closed);
        assertFalse(edited.closed);
        assertTrue(edited.flushed);
        TrackedInput reopenedInput = new TrackedInput(edited.toByteArray());
        try (PdfReader reader = new PdfReader(reopenedInput); PdfDocument document = new PdfDocument(reader)) {
            assertEquals(8, ((PdfNumber) document.getCatalog().getPdfObject().get(new PdfName("Counter"))).intValue());
        }
        assertFalse(reopenedInput.closed);
        new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("source", net.zerocloud.pdf.DocumentSource.stream(new ByteArrayInputStream(edited.toByteArray()), edited.size()))
                .primarySource("source").saveMode(SaveMode.REWRITE).build(), session -> {
                    net.zerocloud.pdf.PdfDictionary root = (net.zerocloud.pdf.PdfDictionary) session.query(
                            InspectObject.version1(session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(8, 0)));
                    assertEquals(net.zerocloud.pdf.PdfNumber.of(8), root.get(net.zerocloud.pdf.PdfName.of("Counter")));
                    return null;
                });
    }

    @Test
    public void replacedDirectStreamLengthBecomesDetachedAfterDataChange() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-detached-length.pdf");
        byte[] replacement = new byte[8192];
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Data"), new PdfStream(new byte[] {1, 2, 3}));
            PdfStream stream = (PdfStream) root.get(new PdfName("Data"));
            PdfNumber oldLength = (PdfNumber) stream.get(new PdfName("Length"));
            int previousLength = oldLength.intValue();
            stream.setData(replacement);
            PdfNumber currentLength = (PdfNumber) stream.get(new PdfName("Length"));
            assertEquals(previousLength, oldLength.intValue());
            oldLength.setValue(123);
            assertEquals(123, oldLength.intValue());
            assertEquals(currentLength.intValue(), ((PdfNumber) stream.get(new PdfName("Length"))).intValue());
            assertArrayEquals(replacement, stream.getBytes());
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            assertArrayEquals(replacement, ((PdfStream) document.getCatalog().getPdfObject().get(new PdfName("Data"))).getBytes());
        }
    }

    @Test
    public void callerStreamsAlsoPublishWithoutOpeningAValueView() throws Exception {
        TrackedOutput blank = new TrackedOutput();
        try (PdfDocument document = new PdfDocument(new PdfWriter(blank))) {
            document.addNewPage();
            assertEquals(0, blank.size());
        }
        TrackedInput input = new TrackedInput(blank.toByteArray());
        TrackedOutput copy = new TrackedOutput();
        try (PdfReader reader = new PdfReader(input);
                PdfDocument document = new PdfDocument(reader, new PdfWriter(copy))) {
            document.addNewPage();
            assertEquals(0, copy.size());
        }
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(copy.toByteArray()));
                PdfDocument document = new PdfDocument(reader)) {
            assertEquals(2, document.getNumberOfPages());
        }
        assertFalse(input.closed);
        assertFalse(blank.closed);
        assertFalse(copy.closed);
        assertTrue(blank.flushed);
        assertTrue(copy.flushed);
    }

    @Test
    public void failedCallerInputIsSafeAndNeverClosed() throws Exception {
        TrackedInput malformed = new TrackedInput("caller-private invalid PDF".getBytes(StandardCharsets.US_ASCII));
        try {
            new PdfReader(malformed);
            fail("Malformed caller input must not create a Reader");
        } catch (IOException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            assertEquals(DocumentFailureCode.SOURCE_READ_FAILED, ((DocumentFailure) failure.getCause()).getCode());
            assertFalse(failure.toString().contains("caller-private"));
            assertNull(failure.getCause().getCause());
        }
        assertFalse(malformed.closed);
        boolean[] closed = {false};
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("caller-private read failure");
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        };
        try {
            new PdfReader(broken);
            fail("Caller I/O failure must not create a Reader");
        } catch (IOException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            assertEquals(DocumentFailureCode.SOURCE_READ_FAILED, ((DocumentFailure) failure.getCause()).getCode());
            assertFalse(failure.toString().contains("caller-private"));
            assertNull(failure.getCause().getCause());
        }
        assertFalse(closed[0]);
    }

    @Test
    public void failedCallerPublicationRetainsItsActualReceiptAndNeverRetriesOrCloses() throws Exception {
        for (boolean flushFailure : new boolean[] {false, true}) {
            for (boolean valueView : new boolean[] {false, true}) {
                FailingOutput output = new FailingOutput(flushFailure);
                PdfDocument document = new PdfDocument(new PdfWriter(output));
                document.addNewPage();
                if (valueView) {
                    document.getCatalog().getPdfObject().put(new PdfName("Counter"), new PdfNumber(5));
                }
                DocumentFailure failure = expectNativeFailure(DocumentFailureCode.PUBLICATION_FAILED, document::close);
                assertEquals(1, failure.getPublicationReceipts().size());
                PublicationReceipt receipt = failure.getPublicationReceipts().get(0);
                assertEquals("target", receipt.getTargetName());
                assertEquals(PublicationStatus.FAILED, receipt.getStatus());
                assertFalse(receipt.getPathTarget().isPresent());
                assertTrue(receipt.isPartialOutputPossible());
                assertFalse(failure.toString().contains("caller-private"));
                int attempts = output.attempts;
                document.close();
                assertEquals(attempts, output.attempts);
                assertFalse(output.closed);
            }
        }
    }

    @Test
    public void foreignAndCyclicReferencesRejectImmediatelyAndAllowLaterPublication() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-reference-failures.pdf");
        PdfIndirectReference expired;
        try (PdfDocument other = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            other.addNewPage();
            expired = other.getCatalog().getPdfObject().getIndirectReference();
        }
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()));
                PdfDocument other = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()))) {
            document.addNewPage();
            other.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Counter"), new PdfNumber(1));
            PdfDictionary foreignBody = other.getCatalog().getPdfObject();
            for (PdfObject foreign : Arrays.asList(expired, foreignBody.getIndirectReference(), foreignBody)) {
                DocumentFailure failure = expectNativeFailure(DocumentFailureCode.OBJECT_REFERENCE_OWNERSHIP_INVALID,
                        () -> root.put(new PdfName("Foreign"), foreign));
                assertEquals("The Object Reference does not belong to this Session.", failure.getDiagnostic());
                assertFalse(root.containsKey(new PdfName("Foreign")));
            }
            expectNativeFailure(DocumentFailureCode.PATCH_CYCLE_REJECTED,
                    () -> root.put(new PdfName("Cycle"), root.getIndirectReference()));
            assertFalse(root.containsKey(new PdfName("Cycle")));
            ((PdfNumber) root.get(new PdfName("Counter"))).setValue(7);
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            assertEquals(7, ((PdfNumber) root.get(new PdfName("Counter"))).intValue());
            assertFalse(root.containsKey(new PdfName("Foreign")));
            assertFalse(root.containsKey(new PdfName("Cycle")));
        }
    }

    @Test
    public void invalidIndicesAndCoreChangesKeepTheEarlierStatePublishable() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-atomic-failures.pdf");
        byte[] painting = "q Q\n".getBytes(StandardCharsets.US_ASCII);
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            PdfDictionary root = document.getCatalog().getPdfObject();
            root.put(new PdfName("Items"), new PdfArray(Arrays.asList(new PdfNumber(4))));
            PdfArray items = (PdfArray) root.get(new PdfName("Items"));
            for (Runnable invalid : Arrays.<Runnable>asList(
                    () -> items.set(-1, PdfNull.PDF_NULL), () -> items.set(1, PdfNull.PDF_NULL),
                    () -> items.add(-1, PdfNull.PDF_NULL), () -> items.add(2, PdfNull.PDF_NULL),
                    () -> items.remove(-1), () -> items.remove(1))) {
                expectNativeFailure(DocumentFailureCode.COMMAND_REJECTED, invalid);
                assertEquals(1, items.size());
                assertEquals(4, ((PdfNumber) items.get(0)).intValue());
            }
            PdfObject pages = root.get(new PdfName("Pages"), false);
            expectNativeFailure(DocumentFailureCode.COMMAND_REJECTED, () -> root.remove(new PdfName("Pages")));
            expectNativeFailure(DocumentFailureCode.COMMAND_REJECTED, () -> root.put(new PdfName("Type"), new PdfName("Page")));
            assertEquals(pages, root.get(new PdfName("Pages"), false));
            assertEquals("Catalog", ((PdfName) root.get(new PdfName("Type"))).getValue());
            PdfDictionary pagesDictionary = (PdfDictionary) root.get(new PdfName("Pages"));
            PdfDictionary page = (PdfDictionary) ((PdfArray) pagesDictionary.get(new PdfName("Kids"))).get(0);
            PdfName contentsName = new PdfName("Contents");
            PdfName resourcesName = new PdfName("Resources");
            page.put(contentsName, new PdfStream(painting));
            PdfObject contentReference = page.get(contentsName, false);
            page.put(contentsName, new PdfArray(Arrays.asList(contentReference)));
            PdfArray contents = (PdfArray) page.get(contentsName);
            for (Runnable invalid : Arrays.<Runnable>asList(
                    () -> page.put(contentsName, new PdfNumber(9)),
                    () -> page.put(contentsName, new PdfDictionary()),
                    () -> page.put(contentsName, new PdfArray(Arrays.asList(new PdfNumber(9)))),
                    () -> contents.set(0, new PdfNumber(9)),
                    () -> contents.add(PdfNull.PDF_NULL),
                    () -> page.remove(resourcesName),
                    () -> page.put(resourcesName, PdfNull.PDF_NULL))) {
                expectNativeFailure(DocumentFailureCode.COMMAND_REJECTED, invalid);
                assertEquals(1, contents.size());
                assertEquals(contentReference, contents.get(0, false));
                assertArrayEquals(painting, ((PdfStream) contents.get(0)).getBytes());
                assertTrue(page.get(resourcesName) instanceof PdfDictionary);
                assertEquals(1, document.getNumberOfPages());
            }
            pagesDictionary.put(resourcesName, new PdfDictionary());
            page.remove(resourcesName);
            assertNull(page.get(resourcesName));
            expectNativeFailure(DocumentFailureCode.COMMAND_REJECTED, () -> pagesDictionary.remove(resourcesName));
            assertTrue(pagesDictionary.get(resourcesName) instanceof PdfDictionary);
            ((PdfNumber) items.get(0)).setValue(9);
            assertEquals(1, document.getNumberOfPages());
        }
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            assertEquals(1, document.getNumberOfPages());
            PdfArray items = (PdfArray) document.getCatalog().getPdfObject().get(new PdfName("Items"));
            assertEquals(9, ((PdfNumber) items.get(0)).intValue());
            PdfDictionary pages = (PdfDictionary) document.getCatalog().getPdfObject().get(new PdfName("Pages"));
            assertTrue(pages.get(new PdfName("Resources")) instanceof PdfDictionary);
            PdfDictionary page = (PdfDictionary) ((PdfArray) pages.get(new PdfName("Kids"))).get(0);
            assertNull(page.get(new PdfName("Resources")));
            PdfArray contents = (PdfArray) page.get(new PdfName("Contents"));
            assertEquals(1, contents.size());
            assertArrayEquals(painting, ((PdfStream) contents.get(0)).getBytes());
        }
    }

    @Test
    public void protectedStreamAndVersionAliasesKeepNativeFailuresAndStillPermitStreamChanges() throws Exception {
        Path input = copyFixture("protected-containers.pdf");
        byte[] source = Files.readAllBytes(input);
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-protected-values.pdf");
        try (PdfReader reader = new PdfReader(input.toString());
                PdfDocument document = new PdfDocument(reader, new PdfWriter(output.toString()))) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            PdfStream stream = (PdfStream) root.get(new PdfName("T09Value"));
            PdfArray filters = (PdfArray) root.get(new PdfName("Encoding"));
            PdfDictionary parameters = (PdfDictionary) root.get(new PdfName("Parameters"));
            for (Runnable invalid : Arrays.<Runnable>asList(
                    () -> stream.put(new PdfName("Length"), new PdfNumber(0)),
                    () -> stream.remove(new PdfName("Filter")),
                    () -> filters.set(0, new PdfName("ASCII85Decode")),
                    () -> parameters.put(new PdfName("Predictor"), new PdfNumber(2)),
                    () -> ((PdfArray) parameters.get(new PdfName("Nested"))).remove(0))) {
                expectNativeFailure(DocumentFailureCode.PATCH_STREAM_CHANGE_REJECTED, invalid);
                assertArrayEquals(new byte[] {1, 2, 3}, stream.getBytes());
            }
            PdfArray extensions = (PdfArray) root.get(new PdfName("ExtensionItems"));
            DocumentFailure version = expectNativeFailure(DocumentFailureCode.COMMAND_REJECTED, () -> extensions.remove(0));
            assertEquals("document.version-password-security", version.getCapabilityId());
            assertEquals(1, extensions.size());
            stream.setData(new byte[] {6, 7});
            stream.put(new PdfName("T09Private"), new PdfName("Retained"));
        }
        assertArrayEquals(source, Files.readAllBytes(input));
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfStream stream = (PdfStream) document.getCatalog().getPdfObject().get(new PdfName("T09Value"));
            assertArrayEquals(new byte[] {6, 7}, stream.getBytes());
            assertEquals("FlateDecode", ((PdfName) stream.get(new PdfName("Filter"))).getValue());
            assertEquals("Retained", ((PdfName) stream.get(new PdfName("T09Private"))).getValue());
        }
    }

    @Test
    public void facadeChangesPreserveUnknownEncodedResourcesAndVisibleContent() throws Exception {
        Path input = copyFixture("unknown-encoded-resource.pdf");
        byte[] source = Files.readAllBytes(input);
        Path output = temporaryFolder.getRoot().toPath().resolve("facade-preserved-resource.pdf");
        try (PdfReader reader = new PdfReader(input.toString());
                PdfDocument document = new PdfDocument(reader, new PdfWriter(output.toString()))) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            PdfStream unknown = (PdfStream) root.get(new PdfName("T09Value"));
            expectNativeFailure(DocumentFailureCode.QUERY_FAILED, () -> unknown.getBytes());
            ((PdfNumber) root.get(new PdfName("Scalar"))).setValue(23);
        }
        assertArrayEquals(source, Files.readAllBytes(input));
        assertTrue(new String(Files.readAllBytes(output), StandardCharsets.ISO_8859_1)
                .contains("\u0000T09 opaque\r\n(endstream)\\\u00ff\u0001"));
        try (PdfReader reader = new PdfReader(output.toString()); PdfDocument document = new PdfDocument(reader)) {
            PdfDictionary root = document.getCatalog().getPdfObject();
            assertEquals(23, ((PdfNumber) root.get(new PdfName("Scalar"))).intValue());
            PdfStream unknown = (PdfStream) root.get(new PdfName("T09Value"));
            assertEquals("T09Opaque", ((PdfName) unknown.get(new PdfName("Filter"))).getValue());
            assertEquals("authored", ((PdfString) ((PdfDictionary) unknown.get(new PdfName("DecodeParms")))
                    .get(new PdfName("Token"))).getValue());
            assertEquals("Retained", ((PdfName) unknown.get(new PdfName("T09Private"))).getValue());
            PdfDictionary page = (PdfDictionary) ((PdfArray) ((PdfDictionary) root.get(new PdfName("Pages")))
                    .get(new PdfName("Kids"))).get(0);
            PdfDictionary privateResources = (PdfDictionary) ((PdfDictionary) page.get(new PdfName("Resources")))
                    .get(new PdfName("T09Private"));
            assertEquals(unknown.getIndirectReference(), privateResources.get(new PdfName("Blob"), false));
            assertArrayEquals("q 0.2 0.4 0.8 rg 10 20 60 40 re f Q\n".getBytes(StandardCharsets.US_ASCII),
                    ((PdfStream) page.get(new PdfName("Contents"))).getBytes());
        }
    }

    private Path copyFixture(String name) throws IOException {
        Path copy = temporaryFolder.getRoot().toPath().resolve(name);
        try (InputStream input = PdfValuesFacadeTest.class.getResourceAsStream("/net/zerocloud/pdf/consumer/t09/" + name)) {
            assertTrue("The authored fixture must travel with the test artifact: " + name, input != null);
            Files.copy(input, copy);
        }
        return copy;
    }

    private static DocumentFailure expectNativeFailure(DocumentFailureCode code, Runnable action) {
        try {
            action.run();
            fail("Expected Native failure " + code);
        } catch (PdfException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            DocumentFailure nativeFailure = (DocumentFailure) failure.getCause();
            assertEquals(code, nativeFailure.getCode());
            assertTrue(failure.getMessage().startsWith(code.name() + ": "));
            assertNull(nativeFailure.getCause());
            return nativeFailure;
        }
        throw new AssertionError("No Native failure");
    }

    private static final class FailingOutput extends OutputStream {
        private final boolean flushFailure;
        private int attempts;
        private boolean closed;

        FailingOutput(boolean flushFailure) {
            this.flushFailure = flushFailure;
        }

        @Override
        public void write(int value) throws IOException {
            attempts++;
            if (!flushFailure) {
                throw new IOException("caller-private write failure");
            }
        }

        @Override
        public void flush() throws IOException {
            attempts++;
            throw new IOException("caller-private flush failure");
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackedInput extends ByteArrayInputStream {
        boolean closed;

        TrackedInput(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class TrackedOutput extends ByteArrayOutputStream {
        boolean closed;
        boolean flushed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        @Override
        public void flush() throws IOException {
            flushed = true;
            super.flush();
        }
    }

    @Test
    public void readerKeepsValuesAfterClosingAndDeletingTheOriginalSource() throws Exception {
        Path input = temporaryFolder.getRoot().toPath().resolve("detached-values.pdf");
        try (PdfDocument created = new PdfDocument(new PdfWriter(input.toString()))) {
            created.addNewPage();
            created.getCatalog().getPdfObject().put(new PdfName("Counter"), new PdfNumber(19));
        }
        PdfReader reader = new PdfReader(input.toString());
        try (PdfDocument view = new PdfDocument(reader)) {
            reader.close();
            reader.close();
            Files.delete(input);
            assertEquals(1, view.getNumberOfPages());
            assertEquals(19, ((PdfNumber) view.getCatalog().getPdfObject().get(new PdfName("Counter"))).intValue());
        }
    }

    @Test(timeout = 15000)
    public void rejectedCloseFromAnotherThreadLeavesTheOwnerAbleToPublish() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("owner-close.pdf");
        try (PdfDocument document = new PdfDocument(new PdfWriter(output.toString()))) {
            document.addNewPage();
            document.getCatalog().getPdfObject().put(new PdfName("Counter"), new PdfNumber(31));
            AtomicReference<Throwable> rejected = new AtomicReference<Throwable>();
            Thread other = new Thread(() -> {
                try {
                    document.close();
                } catch (Throwable failure) {
                    rejected.set(failure);
                }
            });
            other.start();
            other.join(10000);
            assertFalse(other.isAlive());
            assertTrue(rejected.get() instanceof IllegalStateException);
            assertEquals(1, document.getNumberOfPages());
            assertEquals(31, ((PdfNumber) document.getCatalog().getPdfObject().get(new PdfName("Counter"))).intValue());
        }
        try (PdfReader reader = new PdfReader(output.toString());
                PdfDocument reopened = new PdfDocument(reader)) {
            assertEquals(31, ((PdfNumber) reopened.getCatalog().getPdfObject().get(new PdfName("Counter"))).intValue());
        }
    }

    @Test(timeout = 15000)
    public void cancelledInitializationCannotFallBackToAnotherPublishingWorkflow() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("cancelled-initialization.pdf");
        byte[] previous = "keep the previous target".getBytes(StandardCharsets.US_ASCII);
        Files.write(output, previous);
        PdfDocument document = new PdfDocument(new PdfWriter(output.toString()));
        document.addNewPage();
        Thread.currentThread().interrupt();
        try {
            document.getCatalog();
            fail("An interrupted initialization must cancel its Native workflow");
        } catch (PdfException failure) {
            assertTrue(failure.getMessage().startsWith("WORKFLOW_CANCELLED:"));
        } finally {
            Thread.interrupted();
        }
        try {
            document.close();
        } catch (PdfException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            assertEquals(DocumentFailureCode.WORKFLOW_CANCELLED, ((DocumentFailure) failure.getCause()).getCode());
        }
        document.close();
        assertArrayEquals(previous, Files.readAllBytes(output));
    }
}
