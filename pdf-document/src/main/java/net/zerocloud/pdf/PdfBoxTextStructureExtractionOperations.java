package net.zerocloud.pdf;

import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.cmap.CMapParser;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.MissingOperandException;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.contentstream.operator.OperatorProcessor;
import org.apache.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequence;
import org.apache.pdfbox.contentstream.operator.markedcontent.BeginMarkedContentSequenceWithProperties;
import org.apache.pdfbox.contentstream.operator.markedcontent.EndMarkedContentSequence;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.contentstream.operator.text.BeginText;
import org.apache.pdfbox.contentstream.operator.text.EndText;
import org.apache.pdfbox.contentstream.operator.text.MoveText;
import org.apache.pdfbox.contentstream.operator.text.MoveTextSetLeading;
import org.apache.pdfbox.contentstream.operator.text.NextLine;
import org.apache.pdfbox.contentstream.operator.text.SetCharSpacing;
import org.apache.pdfbox.contentstream.operator.text.SetFontAndSize;
import org.apache.pdfbox.contentstream.operator.text.SetTextHorizontalScaling;
import org.apache.pdfbox.contentstream.operator.text.SetTextLeading;
import org.apache.pdfbox.contentstream.operator.text.SetTextRenderingMode;
import org.apache.pdfbox.contentstream.operator.text.SetTextRise;
import org.apache.pdfbox.contentstream.operator.text.SetWordSpacing;
import org.apache.pdfbox.contentstream.operator.text.ShowText;
import org.apache.pdfbox.contentstream.operator.text.ShowTextAdjusted;
import org.apache.pdfbox.contentstream.operator.text.ShowTextLine;
import org.apache.pdfbox.contentstream.operator.text.ShowTextLineAndSpace;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.font.encoding.Encoding;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

final class PdfBoxTextStructureExtractionOperations {

    static final String CAPABILITY_ID = "document.text-structure.extract";

    private final PDDocument document;

    PdfBoxTextStructureExtractionOperations(PDDocument document) {
        this.document = document;
    }

    boolean supportsQuery(DocumentQuery<?> query) {
        return query instanceof ExtractTextAndStructure;
    }

    TextStructureExtraction evaluate(ExtractTextAndStructure query)
            throws DocumentFailure {
        ExtractionState state = new ExtractionState(query.getLimits());
        try {
            COSBase pageTree = document.getDocumentCatalog()
                    .getCOSObject().getDictionaryObject(COSName.PAGES);
            List<PdfBoxPageTreePreflight.PageView> pageViews =
                    state.pageViews(pageTree);
            List<PageText> pages = new ArrayList<PageText>(
                    pageViews.size());
            List<COSDictionary> pageDictionaries =
                    new ArrayList<COSDictionary>(pageViews.size());
            for (int index = 0; index < pageViews.size(); index++) {
                PdfBoxPageTreePreflight.PageView pageView =
                        pageViews.get(index);
                pageDictionaries.add(pageView.source());
                PDPage page = new PDPage(pageView.effective());
                state.accountPageStreams(page);
                PageEngine engine = new PageEngine(index + 1, state);
                engine.processPage(page);
                pages.add(engine.result(page));
            }
            List<LogicalStructureElement> structureRoots =
                    new StructureExtractor(
                            document, state, pages, pageDictionaries).extract();
            return new TextStructureExtraction(
                    pages,
                    structureRoots,
                    state.diagnostics);
        } catch (ExtractionLimitException
                | ExtractionLimitRuntimeException exhausted) {
            throw failure(
                    DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED,
                    "The text and logical-structure extraction limit was exceeded.");
        } catch (ExtractionMalformedRuntimeException malformed) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The document text and logical structure could not be extracted safely.");
        } catch (IOException malformed) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The document text and logical structure could not be extracted safely.");
        } catch (RuntimeException malformed) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The document text and logical structure could not be extracted safely.");
        }
    }

    private static final class ExtractionState {

        private final ExtractionLimits limits;
        private final List<ExtractionDiagnostic> diagnostics =
                new ArrayList<ExtractionDiagnostic>();
        private int contentStreams;
        private long decodedBytes;
        private int textItems;
        private int unicodeCodePoints;
        private int toUnicodeMappings;
        private int fontDataEntries;
        private int markedContentSequences;
        private int structureElements;
        private int structureItems;
        private int roleMappings;
        private final IdentityHashMap<COSDictionary, CMap> explicitCMaps =
                new IdentityHashMap<COSDictionary, CMap>();
        private final IdentityHashMap<COSDictionary, Boolean> fontsInspected =
                new IdentityHashMap<COSDictionary, Boolean>();
        private final IdentityHashMap<COSDictionary, DeclaredEncoding>
                fontEncodings =
                        new IdentityHashMap<COSDictionary, DeclaredEncoding>();
        private final IdentityHashMap<COSDictionary, DeclaredEncoding>
                encodingDictionaries =
                        new IdentityHashMap<COSDictionary, DeclaredEncoding>();
        private final IdentityHashMap<COSArray, String[]> differenceArrays =
                new IdentityHashMap<COSArray, String[]>();
        private final IdentityHashMap<COSStream, Boolean> fontDataInspected =
                new IdentityHashMap<COSStream, Boolean>();
        private final IdentityHashMap<COSStream, byte[]> fontDataHeaders =
                new IdentityHashMap<COSStream, byte[]>();
        private final IdentityHashMap<COSStream, Boolean>
                contentStreamsInspected =
                        new IdentityHashMap<COSStream, Boolean>();

        ExtractionState(ExtractionLimits limits) {
            this.limits = limits;
        }

        List<PdfBoxPageTreePreflight.PageView> pageViews(COSBase value)
                throws IOException {
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Page-tree root is malformed");
            }
            try {
                return PdfBoxPageTreePreflight.pages(
                        (COSDictionary) value,
                        limits.getMaximumPages(),
                        limits.getMaximumPageTreeNodes());
            } catch (PdfBoxPageTreePreflight.LimitExceededException
                    exhausted) {
                throw new ExtractionLimitException();
            }
        }

        void accountPageStreams(PDPage page) throws IOException {
            COSBase value = page.getCOSObject().getDictionaryObject(
                    COSName.CONTENTS);
            if (value == null) {
                return;
            }
            if (value instanceof COSStream) {
                accountAndValidateStream(
                        new PDStream((COSStream) value), 1);
                return;
            }
            if (!(value instanceof COSArray)) {
                throw new IOException("Page Contents is malformed");
            }
            COSArray streams = (COSArray) value;
            if (streams.size() > limits.getMaximumContentStreams()
                    - contentStreams) {
                throw new ExtractionLimitException();
            }
            ByteArrayOutputStream combined = new ByteArrayOutputStream();
            for (int index = 0; index < streams.size(); index++) {
                COSBase stream = streams.getObject(index);
                if (!(stream instanceof COSStream)) {
                    throw new IOException(
                            "Page Contents array member is not a stream");
                }
                accountStreamOccurrence(1);
                byte[] content = decodedBytes((COSStream) stream);
                combined.write(content, 0, content.length);
                combined.write('\n');
            }
            PdfBoxContentStreamPreflight.validate(combined.toByteArray());
        }

        void accountFormStream(PDFormXObject form, int depth)
                throws IOException {
            accountAndValidateStream(form.getContentStream(), depth);
        }

        private void accountAndValidateStream(PDStream stream, int depth)
                throws IOException {
            accountStreamOccurrence(depth);
            COSStream dictionary = stream.getCOSObject();
            if (contentStreamsInspected.containsKey(dictionary)) {
                accountDecodedStream(stream);
                return;
            }
            byte[] content = decodedBytes(dictionary);
            PdfBoxContentStreamPreflight.validate(content);
            contentStreamsInspected.put(dictionary, Boolean.TRUE);
        }

        private void accountStreamOccurrence(int depth)
                throws ExtractionLimitException {
            if (depth > limits.getMaximumContentStreamDepth()) {
                throw new ExtractionLimitException();
            }
            if (contentStreams >= limits.getMaximumContentStreams()) {
                throw new ExtractionLimitException();
            }
            contentStreams++;
        }

        private void accountDecodedStream(PDStream stream)
                throws IOException {
            byte[] buffer = new byte[8192];
            try (InputStream input = stream.createInputStream()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    accountDecodedBytes(count);
                }
            }
        }

        private void accountDecodedBytes(int count)
                throws ExtractionLimitException {
            if (count < 0
                    || decodedBytes
                    > limits.getMaximumDecodedBytes() - count) {
                throw new ExtractionLimitException();
            }
            decodedBytes += count;
        }

        void inspectFontDictionary(COSDictionary dictionary)
                throws IOException {
            inspectFontDictionary(dictionary, false);
        }

        private void inspectFontDictionary(
                COSDictionary dictionary,
                boolean descendant) throws IOException {
            validateFontKind(dictionary, descendant);
            if (!fontsInspected.containsKey(dictionary)) {
                fontsInspected.put(dictionary, Boolean.TRUE);
                inspectFontInputs(dictionary);
                fontEncodings.put(dictionary, readDeclaredEncoding(dictionary));
                COSBase value = dictionary.getDictionaryObject(
                        COSName.TO_UNICODE);
                if (value != null) {
                    if (!(value instanceof COSStream)) {
                        throw new IOException("ToUnicode is not a stream");
                    }
                    byte[] bytes = decodedBytes((COSStream) value);
                    int remaining = limits.getMaximumToUnicodeMappings()
                            - toUnicodeMappings;
                    int mappings;
                    try {
                        mappings = PdfBoxCMapPreflight.countMappings(
                                bytes, remaining);
                    } catch (PdfBoxCMapPreflight.LimitExceededException
                            exhausted) {
                        throw new ExtractionLimitException();
                    }
                    accountToUnicodeMappings(mappings);
                    try (RandomAccessRead input =
                            new RandomAccessReadBuffer(bytes)) {
                        try {
                            explicitCMaps.put(
                                    dictionary,
                                    new CMapParser(true).parse(input));
                        } catch (ClassCastException malformed) {
                            throw new IOException("Malformed ToUnicode CMap");
                        } catch (IllegalArgumentException malformed) {
                            throw new IOException("Malformed ToUnicode CMap");
                        }
                    }
                }
            }
        }

        private void inspectFontInputs(COSDictionary dictionary)
                throws IOException {
            COSName subtype = dictionary.getCOSName(COSName.SUBTYPE);
            if (COSName.TYPE3.equals(subtype)) {
                throw new IOException("Type3 fonts are outside version 1");
            }
            if (COSName.TYPE0.equals(subtype)) {
                COSBase encoding = dictionary.getDictionaryObject(
                        COSName.ENCODING);
                if (!COSName.IDENTITY_H.equals(encoding)
                        && !COSName.IDENTITY_V.equals(encoding)) {
                    throw new IOException(
                            "Type0 font Encoding is outside version 1");
                }
            }

            int remaining = limits.getMaximumFontDataEntries()
                    - fontDataEntries;
            int metricEntries;
            try {
                metricEntries = PdfBoxFontMetricPreflight.countEntries(
                        dictionary, remaining);
            } catch (PdfBoxFontMetricPreflight.LimitExceededException
                    exhausted) {
                throw new ExtractionLimitException();
            }
            accountFontDataEntries(metricEntries);
            inspectFontDescriptor(dictionary.getDictionaryObject(
                    COSName.FONT_DESC));
            inspectCidToGidMap(dictionary.getDictionaryObject(
                    COSName.CID_TO_GID_MAP));

            if (COSName.TYPE0.equals(subtype)) {
                COSBase descendants = dictionary.getDictionaryObject(
                        COSName.DESCENDANT_FONTS);
                if (!(descendants instanceof COSArray)
                        || ((COSArray) descendants).size() != 1) {
                    throw new IOException(
                            "Type0 font must have one descendant font");
                }
                COSBase descendant = ((COSArray) descendants).getObject(0);
                if (!(descendant instanceof COSDictionary)
                        || descendant instanceof COSStream) {
                    throw new IOException(
                            "Type0 descendant font is malformed");
                }
                COSDictionary descendantDictionary =
                        (COSDictionary) descendant;
                COSName descendantType = descendantDictionary.getCOSName(
                        COSName.TYPE, COSName.FONT);
                COSName descendantSubtype = descendantDictionary.getCOSName(
                        COSName.SUBTYPE);
                if (!COSName.FONT.equals(descendantType)
                        || (!COSName.CID_FONT_TYPE0.equals(descendantSubtype)
                                && !COSName.CID_FONT_TYPE2.equals(
                                        descendantSubtype))) {
                    throw new IOException(
                            "Type0 descendant font subtype is unsupported");
                }
                inspectFontDictionary(descendantDictionary, true);
                rejectBackendSubtypeRepair(
                        dictionary,
                        descendantDictionary,
                        descendantSubtype);
            }
        }

        private static void validateFontKind(
                COSDictionary dictionary,
                boolean descendant) throws IOException {
            if (!COSName.FONT.equals(
                    dictionary.getDictionaryObject(COSName.TYPE))) {
                throw new IOException("Font Type is malformed");
            }
            COSBase subtypeValue = dictionary.getDictionaryObject(
                    COSName.SUBTYPE);
            if (!(subtypeValue instanceof COSName)) {
                throw new IOException("Font Subtype is malformed");
            }
            COSName subtype = (COSName) subtypeValue;
            boolean supported = descendant
                    ? COSName.CID_FONT_TYPE0.equals(subtype)
                            || COSName.CID_FONT_TYPE2.equals(subtype)
                    : COSName.TYPE1.equals(subtype)
                            || COSName.MM_TYPE1.equals(subtype)
                            || COSName.TRUE_TYPE.equals(subtype)
                            || COSName.TYPE0.equals(subtype);
            if (!supported) {
                throw new IOException("Font Subtype is outside version 1");
            }
        }

        private void rejectBackendSubtypeRepair(
                COSDictionary parent,
                COSDictionary descendant,
                COSName declaredSubtype) throws IOException {
            COSDictionary descriptor = parent.getCOSDictionary(
                    COSName.FONT_DESC);
            if (descriptor == null) {
                descriptor = descendant.getCOSDictionary(COSName.FONT_DESC);
            }
            if (descriptor == null) {
                return;
            }
            COSStream program = descriptor.getCOSStream(COSName.FONT_FILE);
            if (program == null) {
                program = descriptor.getCOSStream(COSName.FONT_FILE2);
            }
            if (program == null) {
                program = descriptor.getCOSStream(COSName.FONT_FILE3);
            }
            if (program == null) {
                return;
            }
            byte[] header = fontDataHeaders.get(program);
            if (header == null) {
                throw new IOException("Embedded Type0 font was not inspected");
            }
            COSName detectedSubtype = detectedCompositeSubtype(header);
            if (detectedSubtype != null
                    && !detectedSubtype.equals(declaredSubtype)) {
                throw new IOException(
                        "Embedded Type0 font subtype is contradictory");
            }
        }

        private static COSName detectedCompositeSubtype(byte[] header) {
            if (matches(header, 0, 1, 0, 0)
                    || matches(header, 't', 'r', 'u', 'e')
                    || matches(header, 't', 't', 'c', 'f')
                    || matches(header, 'O', 'T', 'T', 'O')) {
                return COSName.CID_FONT_TYPE2;
            }
            if ((header[0] == '%' && header[1] == '!')
                    || ((header[0] & 0xff) == 0x80
                            && (header[1] == 1 || header[1] == 2))
                    || (header[0] >= 1
                            && header[3] >= 1
                            && header[3] <= 4)) {
                return COSName.CID_FONT_TYPE0;
            }
            return null;
        }

        private static boolean matches(
                byte[] bytes,
                int first,
                int second,
                int third,
                int fourth) {
            return bytes[0] == (byte) first
                    && bytes[1] == (byte) second
                    && bytes[2] == (byte) third
                    && bytes[3] == (byte) fourth;
        }

        private void inspectFontDescriptor(COSBase value)
                throws IOException {
            if (value == null) {
                return;
            }
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("FontDescriptor is malformed");
            }
            COSDictionary descriptor = (COSDictionary) value;
            inspectFontData(descriptor.getDictionaryObject(COSName.FONT_FILE));
            inspectFontData(descriptor.getDictionaryObject(COSName.FONT_FILE2));
            inspectFontData(descriptor.getDictionaryObject(COSName.FONT_FILE3));
        }

        private void inspectFontData(COSBase value) throws IOException {
            if (value == null) {
                return;
            }
            if (!(value instanceof COSStream)) {
                throw new IOException("Font data entry is not a stream");
            }
            COSStream stream = (COSStream) value;
            if (fontDataInspected.containsKey(stream)) {
                return;
            }
            fontDataInspected.put(stream, Boolean.TRUE);
            byte[] header = new byte[4];
            int headerBytes = 0;
            byte[] buffer = new byte[8192];
            try (InputStream input = stream.createInputStream()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    accountDecodedBytes(count);
                    int copied = Math.min(count, header.length - headerBytes);
                    if (copied > 0) {
                        System.arraycopy(
                                buffer, 0, header, headerBytes, copied);
                        headerBytes += copied;
                    }
                }
            }
            fontDataHeaders.put(stream, header);
        }

        private void inspectCidToGidMap(COSBase value) throws IOException {
            if (value == null
                    || COSName.getPDFName("Identity").equals(value)) {
                return;
            }
            inspectFontData(value);
        }

        String inferredUnicode(PDFont font, int code) throws IOException {
            if (!(font instanceof PDSimpleFont)) {
                return null;
            }
            COSDictionary dictionary = font.getCOSObject();
            inspectFontDictionary(dictionary);
            DeclaredEncoding encoding = fontEncodings.get(dictionary);
            String name = encoding.glyphName(code);
            return name == null
                    ? null
                    : GlyphList.getAdobeGlyphList().toUnicode(name);
        }

        private DeclaredEncoding readDeclaredEncoding(COSDictionary font)
                throws IOException {
            if (COSName.TYPE0.equals(font.getCOSName(COSName.SUBTYPE))) {
                return DeclaredEncoding.NONE;
            }
            COSBase value = font.getDictionaryObject(COSName.ENCODING);
            if (value == null) {
                return DeclaredEncoding.NONE;
            }
            if (value instanceof COSName) {
                Encoding base = Encoding.getInstance((COSName) value);
                return base == null
                        ? DeclaredEncoding.NONE
                        : new DeclaredEncoding(base, null);
            }
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Simple-font Encoding is malformed");
            }
            COSDictionary dictionary = (COSDictionary) value;
            if (encodingDictionaries.containsKey(dictionary)) {
                return encodingDictionaries.get(dictionary);
            }

            COSBase baseValue = dictionary.getDictionaryObject(
                    COSName.BASE_ENCODING);
            Encoding base = null;
            if (baseValue != null) {
                if (!(baseValue instanceof COSName)) {
                    throw new IOException("BaseEncoding is malformed");
                }
                base = Encoding.getInstance((COSName) baseValue);
            }
            String[] differences = readDifferences(dictionary);
            DeclaredEncoding result = base == null && differences == null
                    ? DeclaredEncoding.NONE
                    : new DeclaredEncoding(base, differences);
            encodingDictionaries.put(dictionary, result);
            return result;
        }

        private String[] readDifferences(COSDictionary encoding)
                throws IOException {
            COSBase value = encoding.getDictionaryObject(COSName.DIFFERENCES);
            if (value == null) {
                return null;
            }
            if (!(value instanceof COSArray)) {
                throw new IOException("Encoding Differences is malformed");
            }
            COSArray entries = (COSArray) value;
            if (differenceArrays.containsKey(entries)) {
                return differenceArrays.get(entries);
            }
            accountFontDataEntries(entries.size());
            String[] result = new String[256];
            int current = -1;
            for (int index = 0; index < entries.size(); index++) {
                COSBase entry = entries.getObject(index);
                if (entry instanceof COSInteger) {
                    long characterCode = ((COSInteger) entry).longValue();
                    if (characterCode < 0L || characterCode > 255L) {
                        throw new IOException(
                                "Encoding Differences code is out of range");
                    }
                    current = (int) characterCode;
                } else if (entry instanceof COSName) {
                    if (current < 0 || current > 255) {
                        throw new IOException(
                                "Encoding Differences has no character code");
                    }
                    result[current] = ((COSName) entry).getName();
                    current++;
                } else {
                    throw new IOException("Encoding Differences is malformed");
                }
            }
            differenceArrays.put(entries, result);
            return result;
        }

        String explicitUnicode(PDFont font, byte[] sourceCode)
                throws IOException {
            COSDictionary dictionary = font.getCOSObject();
            inspectFontDictionary(dictionary);
            CMap cmap = explicitCMaps.get(dictionary);
            return cmap == null ? null : cmap.toUnicode(sourceCode);
        }

        private byte[] decodedBytes(COSStream stream) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            try (InputStream input = stream.createInputStream()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    accountDecodedBytes(count);
                    output.write(buffer, 0, count);
                }
            }
            return output.toByteArray();
        }

        int nextTextItem() throws ExtractionLimitException {
            if (textItems >= limits.getMaximumTextItems()) {
                throw new ExtractionLimitException();
            }
            textItems++;
            return textItems;
        }

        void accountUnicode(String value) throws ExtractionLimitException {
            int count = value.codePointCount(0, value.length());
            if (unicodeCodePoints
                    > limits.getMaximumUnicodeCodePoints() - count) {
                throw new ExtractionLimitException();
            }
            unicodeCodePoints += count;
        }

        void accountToUnicodeMappings(long count)
                throws ExtractionLimitException {
            if (count < 0L
                    || count > limits.getMaximumToUnicodeMappings()
                            - (long) toUnicodeMappings) {
                throw new ExtractionLimitException();
            }
            toUnicodeMappings += (int) count;
        }

        private void accountFontDataEntries(int count)
                throws ExtractionLimitException {
            if (count < 0
                    || count > limits.getMaximumFontDataEntries()
                            - fontDataEntries) {
                throw new ExtractionLimitException();
            }
            fontDataEntries += count;
        }

        void nextMarkedContentSequence() throws ExtractionLimitException {
            if (markedContentSequences
                    >= limits.getMaximumMarkedContentSequences()) {
                throw new ExtractionLimitException();
            }
            markedContentSequences++;
        }

        void requireMarkedContentDepth(int depth)
                throws ExtractionLimitException {
            if (depth > limits.getMaximumMarkedContentDepth()) {
                throw new ExtractionLimitException();
            }
        }

        int nextStructureElement(int depth) throws ExtractionLimitException {
            if (depth > limits.getMaximumStructureDepth()) {
                throw new ExtractionLimitException();
            }
            if (structureElements >= limits.getMaximumStructureElements()) {
                throw new ExtractionLimitException();
            }
            structureElements++;
            return structureElements;
        }

        void nextStructureItem() throws ExtractionLimitException {
            if (structureItems >= limits.getMaximumStructureItems()) {
                throw new ExtractionLimitException();
            }
            structureItems++;
        }

        void nextRoleMapping() throws ExtractionLimitException {
            if (roleMappings >= limits.getMaximumRoleMappings()) {
                throw new ExtractionLimitException();
            }
            roleMappings++;
        }
    }

    private static final class DeclaredEncoding {

        private static final DeclaredEncoding NONE =
                new DeclaredEncoding(null, null);

        private final Encoding base;
        private final String[] differences;

        DeclaredEncoding(Encoding base, String[] differences) {
            this.base = base;
            this.differences = differences;
        }

        String glyphName(int code) {
            if (code < 0 || code > 255) {
                return null;
            }
            if (differences != null && differences[code] != null) {
                return differences[code];
            }
            return base == null ? null : base.getName(code);
        }
    }

    private static final class PageEngine extends PDFStreamEngine {

        private final int pageNumber;
        private final ExtractionState state;
        private final List<TextItem> items = new ArrayList<TextItem>();
        private final List<SequenceBuilder> sequences =
                new ArrayList<SequenceBuilder>();
        private final Deque<SequenceBuilder> activeSequences =
                new ArrayDeque<SequenceBuilder>();
        private final StringBuilder pageText = new StringBuilder();
        private final Deque<SourceCodeFrame> sourceFrames =
                new ArrayDeque<SourceCodeFrame>();
        private final IdentityHashMap<COSStream, Boolean> activeForms =
                new IdentityHashMap<COSStream, Boolean>();
        private final Deque<OperatorBalance> operatorBalances =
                new ArrayDeque<OperatorBalance>();

        PageEngine(int pageNumber, ExtractionState state) {
            this.pageNumber = pageNumber;
            this.state = state;
            operatorBalances.addLast(new OperatorBalance());
            addOperator(new BeginText(this));
            addOperator(new BeginMarkedContentSequence(this));
            addOperator(new BeginMarkedContentSequenceWithProperties(this));
            addOperator(new Concatenate(this));
            addOperator(new BoundedDrawObject(this));
            addOperator(new EndText(this));
            addOperator(new EndMarkedContentSequence(this));
            addOperator(new SetGraphicsStateParameters(this));
            addOperator(new Save(this));
            addOperator(new Restore(this));
            addOperator(new NextLine(this));
            addOperator(new SetCharSpacing(this));
            addOperator(new MoveText(this));
            addOperator(new MoveTextSetLeading(this));
            addOperator(new SetFontAndSize(this));
            addOperator(new ShowText(this));
            addOperator(new ShowTextAdjusted(this));
            addOperator(new SetTextLeading(this));
            addOperator(new SetMatrix(this));
            addOperator(new SetTextRenderingMode(this));
            addOperator(new SetTextRise(this));
            addOperator(new SetWordSpacing(this));
            addOperator(new SetTextHorizontalScaling(this));
            addOperator(new ShowTextLine(this));
            addOperator(new ShowTextLineAndSpace(this));
        }

        void showBoundedForm(PDFormXObject form) throws IOException {
            COSStream stream = form.getCOSObject();
            if (activeForms.put(stream, Boolean.TRUE) != null) {
                throw new IOException("Cyclic form XObject graph");
            }
            try {
                state.accountFormStream(form, activeForms.size() + 1);
                OperatorBalance formBalance = new OperatorBalance();
                operatorBalances.addLast(formBalance);
                increaseLevel();
                try {
                    if (form instanceof PDTransparencyGroup) {
                        showTransparencyGroup((PDTransparencyGroup) form);
                    } else {
                        showForm(form);
                    }
                    formBalance.requireBalanced();
                } finally {
                    decreaseLevel();
                    operatorBalances.removeLast();
                }
            } finally {
                activeForms.remove(stream);
            }
        }

        @Override
        protected void operatorException(
                Operator operator,
                List<COSBase> operands,
                IOException exception) throws IOException {
            throw exception;
        }

        @Override
        protected void processOperator(
                Operator operator,
                List<COSBase> operands) throws IOException {
            validateSupportedOperands(operator.getName(), operands);
            operatorBalances.peekLast().accept(operator.getName());
            if (OperatorName.SET_FONT_AND_SIZE.equals(operator.getName())) {
                preflightNamedFont(operands);
            } else if (OperatorName.SET_GRAPHICS_STATE_PARAMS.equals(
                    operator.getName())) {
                COSArray fontSetting = preflightGraphicsStateFont(operands);
                applyBoundedGraphicsState(fontSetting);
                return;
            } else if (OperatorName.BEGIN_MARKED_CONTENT_SEQ.equals(
                    operator.getName())) {
                preflightMarkedContentProperty(operands);
            }
            super.processOperator(operator, operands);
        }

        @Override
        public void beginMarkedContentSequence(
                COSName tag,
                COSDictionary properties) {
            if (tag == null || !activeForms.isEmpty()) {
                throw new ExtractionMalformedRuntimeException();
            }
            try {
                state.nextMarkedContentSequence();
                state.requireMarkedContentDepth(activeSequences.size() + 1);
                String language = optionalString(properties, COSName.LANG);
                String alternate = optionalString(properties, COSName.ALT);
                String actual = optionalString(properties, COSName.ACTUAL_TEXT);
                state.accountUnicode(tag.getName());
                accountNullable(language);
                accountNullable(alternate);
                accountNullable(actual);
                Integer mcid = optionalNonNegativeInteger(
                        properties, COSName.MCID);
                SequenceBuilder parent = activeSequences.peekLast();
                SequenceBuilder sequence = new SequenceBuilder(
                        sequences.size() + 1,
                        tag.getName(),
                        mcid,
                        parent == null ? null : Integer.valueOf(parent.id),
                        language,
                        alternate,
                        actual);
                sequences.add(sequence);
                activeSequences.addLast(sequence);
            } catch (ExtractionLimitException exhausted) {
                throw new ExtractionLimitRuntimeException();
            } catch (IOException malformed) {
                throw new ExtractionMalformedRuntimeException();
            }
        }

        @Override
        public void endMarkedContentSequence() {
            if (!activeForms.isEmpty() || activeSequences.isEmpty()) {
                throw new ExtractionMalformedRuntimeException();
            }
            SequenceBuilder completed = activeSequences.removeLast();
            if (completed.actualText != null && !hasActiveActualText()) {
                pageText.append(completed.actualText);
            }
        }

        @Override
        protected void showText(byte[] string) throws IOException {
            PDFont font = getGraphicsState().getTextState().getFont();
            if (font == null) {
                throw new IOException("Text has no current font");
            }
            sourceFrames.push(new SourceCodeFrame(font, string));
            try {
                super.showText(string);
                if (sourceFrames.peek().hasRemaining()) {
                    throw new IOException("Text source-code traversal was inconsistent");
                }
            } finally {
                sourceFrames.pop();
            }
        }

        @Override
        protected void showGlyph(
                Matrix textRenderingMatrix,
                PDFont font,
                int code,
                Vector displacement) throws IOException {
            if (sourceFrames.isEmpty()) {
                throw new IOException("Text source code was unavailable");
            }
            state.nextTextItem();
            SourceCode source = sourceFrames.peek().next();
            if (source.numericValue != code) {
                throw new IOException("Text source-code traversal was inconsistent");
            }

            int pageIndex = items.size() + 1;
            String explicit = state.explicitUnicode(font, source.bytes);
            String inferred = state.inferredUnicode(font, code);
            if (explicit != null) {
                state.accountUnicode(explicit);
            }
            if (inferred != null && !inferred.equals(explicit)) {
                state.accountUnicode(inferred);
            }
            boolean contradictory = explicit != null
                    && inferred != null
                    && !explicit.equals(inferred);
            CharacterMapping.Confidence confidence;
            String selected;
            if (contradictory) {
                confidence = CharacterMapping.Confidence.CONTRADICTORY;
                selected = null;
            } else if (explicit != null) {
                confidence = CharacterMapping.Confidence.EXPLICIT;
                selected = explicit;
            } else if (inferred != null) {
                confidence = CharacterMapping.Confidence.INFERRED;
                selected = inferred;
            } else {
                confidence = CharacterMapping.Confidence.MISSING;
                selected = null;
            }
            CharacterMapping mapping = new CharacterMapping(
                    source.bytes,
                    confidence,
                    selected,
                    explicit,
                    inferred);
            String contribution = selected == null || hasActiveActualText()
                    ? ""
                    : selected;
            if (selected != null) {
                if (!hasActiveActualText()) {
                    pageText.append(selected);
                }
            } else if (contradictory) {
                state.diagnostics.add(new ExtractionDiagnostic(
                        ExtractionDiagnostic.Code.CONTRADICTORY_UNICODE_MAPPING,
                        pageNumber,
                        pageIndex,
                        source.bytes,
                        "Explicit and standard Unicode mappings disagree for this character code."));
            } else {
                state.diagnostics.add(new ExtractionDiagnostic(
                        ExtractionDiagnostic.Code.MISSING_UNICODE_MAPPING,
                        pageNumber,
                        pageIndex,
                        source.bytes,
                        "No defensible Unicode mapping is available for this character code."));
            }
            List<Integer> markedContentIds = new ArrayList<Integer>();
            for (SequenceBuilder sequence : activeSequences) {
                markedContentIds.add(Integer.valueOf(sequence.id));
                sequence.textItemIndices.add(Integer.valueOf(pageIndex));
            }
            items.add(new TextItem(
                    pageIndex,
                    mapping,
                    contribution,
                    geometry(textRenderingMatrix, displacement),
                    markedContentIds));
        }

        PageText result(PDPage page) {
            if (!activeSequences.isEmpty()
                    || operatorBalances.size() != 1
                    || !operatorBalances.peekLast().isBalanced()) {
                throw new ExtractionMalformedRuntimeException();
            }
            PDRectangle cropBox = page.getCropBox();
            List<MarkedContentSequence> detachedSequences =
                    new ArrayList<MarkedContentSequence>(sequences.size());
            for (SequenceBuilder sequence : sequences) {
                detachedSequences.add(sequence.detach());
            }
            return new PageText(
                    pageNumber,
                    page.getRotation(),
                    decimal(page.getUserUnit()),
                    decimal(cropBox.getLowerLeftX()),
                    decimal(cropBox.getLowerLeftY()),
                    decimal(cropBox.getUpperRightX()),
                    decimal(cropBox.getUpperRightY()),
                    pageText.toString(),
                    items,
                    detachedSequences);
        }

        private boolean hasActiveActualText() {
            for (SequenceBuilder sequence : activeSequences) {
                if (sequence.actualText != null) {
                    return true;
                }
            }
            return false;
        }

        private void accountNullable(String value)
                throws ExtractionLimitException {
            if (value != null) {
                state.accountUnicode(value);
            }
        }

        private void preflightNamedFont(List<COSBase> operands)
                throws IOException {
            if (operands.size() != 2
                    || !(operands.get(0) instanceof COSName)) {
                throw new IOException("Tf operands are malformed");
            }
            requireFiniteNumber(operands.get(1), "Tf font size");
            COSDictionary resources = resourceDictionary();
            if (resources == null) {
                throw new IOException("Font operator has no resources");
            }
            COSBase fonts = resources.getDictionaryObject(COSName.FONT);
            if (!(fonts instanceof COSDictionary)
                    || fonts instanceof COSStream) {
                throw new IOException("Font resources are malformed");
            }
            inspectRawFont(((COSDictionary) fonts).getDictionaryObject(
                    (COSName) operands.get(0)));
        }

        private COSArray preflightGraphicsStateFont(List<COSBase> operands)
                throws IOException {
            requireSingleNameOperand(operands, "gs");
            COSDictionary resources = resourceDictionary();
            if (resources == null) {
                throw new IOException(
                        "Graphics-state operator has no resources");
            }
            COSBase states = resources.getDictionaryObject(
                    COSName.EXT_G_STATE);
            if (!(states instanceof COSDictionary)
                    || states instanceof COSStream) {
                throw new IOException("ExtGState resources are malformed");
            }
            COSBase selected = ((COSDictionary) states).getDictionaryObject(
                    (COSName) operands.get(0));
            if (!(selected instanceof COSDictionary)
                    || selected instanceof COSStream) {
                throw new IOException("ExtGState resource is malformed");
            }
            COSBase setting = ((COSDictionary) selected).getDictionaryObject(
                    COSName.FONT);
            if (setting == null) {
                return null;
            }
            if (!(setting instanceof COSArray)
                    || ((COSArray) setting).size() != 2) {
                throw new IOException("ExtGState font setting is malformed");
            }
            requireFiniteNumber(
                    ((COSArray) setting).getObject(1),
                    "ExtGState font size");
            inspectRawFont(((COSArray) setting).getObject(0));
            return (COSArray) setting;
        }

        private void applyBoundedGraphicsState(COSArray fontSetting)
                throws IOException {
            if (fontSetting == null) {
                return;
            }
            COSArray detachedSetting = new COSArray();
            detachedSetting.add(fontSetting.get(0));
            detachedSetting.add(fontSetting.get(1));
            COSDictionary extractionState = new COSDictionary();
            extractionState.setItem(COSName.FONT, detachedSetting);
            new PDExtendedGraphicsState(extractionState)
                    .copyIntoGraphicsState(getGraphicsState());
        }

        private void preflightMarkedContentProperty(List<COSBase> operands)
                throws IOException {
            if (operands.size() != 2
                    || !(operands.get(0) instanceof COSName)) {
                throw new IOException("BDC operands are malformed");
            }
            COSBase property = operands.get(1);
            if (property instanceof COSName) {
                COSDictionary resources = resourceDictionary();
                if (resources == null) {
                    throw new IOException(
                            "Marked-content property has no resources");
                }
                COSBase properties = resources.getDictionaryObject(
                        COSName.PROPERTIES);
                if (!(properties instanceof COSDictionary)
                        || properties instanceof COSStream) {
                    throw new IOException(
                            "Marked-content properties are malformed");
                }
                property = ((COSDictionary) properties).getDictionaryObject(
                        (COSName) property);
            }
            if (!(property instanceof COSDictionary)
                    || property instanceof COSStream) {
                throw new IOException(
                        "Marked-content property is malformed");
            }
        }

        private COSDictionary resourceDictionary() throws IOException {
            if (getResources() == null) {
                return null;
            }
            COSDictionary resources = getResources().getCOSObject();
            if (resources instanceof COSStream) {
                throw new IOException("Resources dictionary is malformed");
            }
            return resources;
        }

        private void inspectRawFont(COSBase value) throws IOException {
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Font resource is malformed");
            }
            state.inspectFontDictionary((COSDictionary) value);
        }

        private static void requireSingleNameOperand(
                List<COSBase> operands,
                String operator) throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSName)) {
                throw new IOException(operator + " operands are malformed");
            }
        }

        private static void validateSupportedOperands(
                String operator,
                List<COSBase> operands) throws IOException {
            if (OperatorName.BEGIN_TEXT.equals(operator)
                    || OperatorName.END_TEXT.equals(operator)
                    || OperatorName.NEXT_LINE.equals(operator)
                    || OperatorName.SAVE.equals(operator)
                    || OperatorName.RESTORE.equals(operator)
                    || OperatorName.END_MARKED_CONTENT.equals(operator)) {
                requireNoOperands(operands, operator);
            } else if (OperatorName.SET_CHAR_SPACING.equals(operator)
                    || OperatorName.SET_TEXT_HORIZONTAL_SCALING.equals(operator)
                    || OperatorName.SET_TEXT_LEADING.equals(operator)
                    || OperatorName.SET_TEXT_RISE.equals(operator)
                    || OperatorName.SET_WORD_SPACING.equals(operator)) {
                requireFiniteNumbers(operands, 1, operator);
            } else if (OperatorName.MOVE_TEXT.equals(operator)
                    || OperatorName.MOVE_TEXT_SET_LEADING.equals(operator)) {
                requireFiniteNumbers(operands, 2, operator);
            } else if (OperatorName.CONCAT.equals(operator)
                    || OperatorName.SET_MATRIX.equals(operator)) {
                requireFiniteNumbers(operands, 6, operator);
            } else if (OperatorName.SET_TEXT_RENDERINGMODE.equals(operator)) {
                requireRenderingMode(operands);
            } else if (OperatorName.SET_FONT_AND_SIZE.equals(operator)) {
                if (operands.size() != 2
                        || !(operands.get(0) instanceof COSName)) {
                    throw new IOException("Tf operands are malformed");
                }
                requireFiniteNumber(operands.get(1), "Tf font size");
            } else if (OperatorName.SHOW_TEXT.equals(operator)
                    || OperatorName.SHOW_TEXT_LINE.equals(operator)) {
                requireSingleStringOperand(operands, operator);
            } else if (OperatorName.SHOW_TEXT_ADJUSTED.equals(operator)) {
                requireTextAdjustmentArray(operands);
            } else if (OperatorName.SHOW_TEXT_LINE_AND_SPACE.equals(operator)) {
                if (operands.size() != 3
                        || !(operands.get(2) instanceof COSString)) {
                    throw new IOException("double-quote operands are malformed");
                }
                requireFiniteNumber(operands.get(0), "word spacing");
                requireFiniteNumber(operands.get(1), "character spacing");
            } else if (OperatorName.DRAW_OBJECT.equals(operator)
                    || OperatorName.SET_GRAPHICS_STATE_PARAMS.equals(operator)
                    || OperatorName.BEGIN_MARKED_CONTENT.equals(operator)) {
                requireSingleNameOperand(operands, operator);
            } else if (OperatorName.BEGIN_MARKED_CONTENT_SEQ.equals(operator)
                    && (operands.size() != 2
                            || !(operands.get(0) instanceof COSName))) {
                throw new IOException("BDC operands are malformed");
            }
        }

        private static void requireNoOperands(
                List<COSBase> operands,
                String operator) throws IOException {
            if (!operands.isEmpty()) {
                throw new IOException(operator + " operands are malformed");
            }
        }

        private static void requireFiniteNumbers(
                List<COSBase> operands,
                int expected,
                String operator) throws IOException {
            if (operands.size() != expected) {
                throw new IOException(operator + " operands are malformed");
            }
            for (COSBase operand : operands) {
                requireFiniteNumber(operand, operator + " operand");
            }
        }

        private static void requireRenderingMode(List<COSBase> operands)
                throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSInteger)) {
                throw new IOException("Tr operand is malformed");
            }
            long mode = ((COSInteger) operands.get(0)).longValue();
            if (mode < 0L || mode > 7L) {
                throw new IOException("Tr operand is out of range");
            }
        }

        private static void requireSingleStringOperand(
                List<COSBase> operands,
                String operator) throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSString)) {
                throw new IOException(operator + " operand is malformed");
            }
        }

        private static void requireTextAdjustmentArray(List<COSBase> operands)
                throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSArray)) {
                throw new IOException("TJ operand is malformed");
            }
            COSArray adjustments = (COSArray) operands.get(0);
            for (int index = 0; index < adjustments.size(); index++) {
                COSBase value = adjustments.getObject(index);
                if (value instanceof COSString) {
                    continue;
                }
                requireFiniteNumber(value, "TJ adjustment");
            }
        }

        private static void requireFiniteNumber(
                COSBase value,
                String description) throws IOException {
            if (!(value instanceof COSNumber)) {
                throw new IOException(description + " is malformed");
            }
            float converted = ((COSNumber) value).floatValue();
            if (Float.isNaN(converted) || Float.isInfinite(converted)) {
                throw new IOException(description + " is malformed");
            }
        }

        private static final class OperatorBalance {

            private boolean inTextObject;
            private int graphicsSaves;

            void accept(String operator) throws IOException {
                if (OperatorName.BEGIN_TEXT.equals(operator)) {
                    if (inTextObject) {
                        throw new IOException("Nested BT operator");
                    }
                    inTextObject = true;
                } else if (OperatorName.END_TEXT.equals(operator)) {
                    if (!inTextObject) {
                        throw new IOException("Unmatched ET operator");
                    }
                    inTextObject = false;
                } else if (OperatorName.SAVE.equals(operator)) {
                    if (graphicsSaves == Integer.MAX_VALUE) {
                        throw new IOException("Graphics-state depth overflow");
                    }
                    graphicsSaves++;
                } else if (OperatorName.RESTORE.equals(operator)) {
                    if (graphicsSaves == 0) {
                        throw new IOException("Unmatched Q operator");
                    }
                    graphicsSaves--;
                } else if (isTextObjectOperator(operator) && !inTextObject) {
                    throw new IOException(
                            "Text operator is outside a text object");
                }
            }

            private static boolean isTextObjectOperator(String operator) {
                return OperatorName.NEXT_LINE.equals(operator)
                        || OperatorName.SET_CHAR_SPACING.equals(operator)
                        || OperatorName.MOVE_TEXT.equals(operator)
                        || OperatorName.MOVE_TEXT_SET_LEADING.equals(operator)
                        || OperatorName.SET_FONT_AND_SIZE.equals(operator)
                        || OperatorName.SHOW_TEXT.equals(operator)
                        || OperatorName.SHOW_TEXT_ADJUSTED.equals(operator)
                        || OperatorName.SET_TEXT_LEADING.equals(operator)
                        || OperatorName.SET_MATRIX.equals(operator)
                        || OperatorName.SET_TEXT_RENDERINGMODE.equals(operator)
                        || OperatorName.SET_TEXT_RISE.equals(operator)
                        || OperatorName.SET_WORD_SPACING.equals(operator)
                        || OperatorName.SET_TEXT_HORIZONTAL_SCALING.equals(
                                operator)
                        || OperatorName.SHOW_TEXT_LINE.equals(operator)
                        || OperatorName.SHOW_TEXT_LINE_AND_SPACE.equals(
                                operator);
            }

            boolean isBalanced() {
                return !inTextObject && graphicsSaves == 0;
            }

            void requireBalanced() throws IOException {
                if (!isBalanced()) {
                    throw new IOException("Content operator state is unbalanced");
                }
            }
        }

        private static TextGeometry geometry(
                Matrix matrix,
                Vector displacement) {
            Point2D.Float start = matrix.transformPoint(0f, 0f);
            Point2D.Float end = matrix.transformPoint(
                    displacement.getX(), displacement.getY());
            return new TextGeometry(
                    decimal(matrix.getScaleX()),
                    decimal(matrix.getShearY()),
                    decimal(matrix.getShearX()),
                    decimal(matrix.getScaleY()),
                    decimal(matrix.getTranslateX()),
                    decimal(matrix.getTranslateY()),
                    decimal(end.x - start.x),
                    decimal(end.y - start.y));
        }
    }

    private static final class BoundedDrawObject extends OperatorProcessor {

        private final PageEngine engine;

        BoundedDrawObject(PageEngine engine) {
            super(engine);
            this.engine = engine;
        }

        @Override
        public void process(Operator operator, List<COSBase> operands)
                throws IOException {
            if (operands.size() != 1
                    || !(operands.get(0) instanceof COSName)) {
                throw new MissingOperandException(operator, operands);
            }
            COSName name = (COSName) operands.get(0);
            COSDictionary resources = engine.resourceDictionary();
            if (resources == null) {
                throw new IOException("XObject operator has no resources");
            }
            COSBase entries = resources.getDictionaryObject(COSName.XOBJECT);
            if (!(entries instanceof COSDictionary)
                    || entries instanceof COSStream) {
                throw new IOException("XObject resources are malformed");
            }
            COSBase selected = ((COSDictionary) entries)
                    .getDictionaryObject(name);
            if (!(selected instanceof COSStream)) {
                throw new IOException("XObject resource is malformed");
            }
            COSName subtype = ((COSStream) selected).getCOSName(
                    COSName.SUBTYPE);
            if (COSName.IMAGE.equals(subtype)) {
                return;
            }
            if (!COSName.FORM.equals(subtype)) {
                throw new IOException("XObject subtype is unsupported");
            }
            COSStream form = (COSStream) selected;
            validateFormDictionary(form);
            COSBase rawType = form.getItem(COSName.TYPE);
            COSBase rawSubtype = form.getItem(COSName.SUBTYPE);
            try {
                PDXObject object = engine.getResources().getXObject(name);
                if (!(object instanceof PDFormXObject)) {
                    throw new IOException("Form XObject is malformed");
                }
                engine.showBoundedForm((PDFormXObject) object);
            } finally {
                form.setItem(COSName.TYPE, rawType);
                form.setItem(COSName.SUBTYPE, rawSubtype);
            }
        }

        private static void validateFormDictionary(COSStream form)
                throws IOException {
            if (!COSName.XOBJECT.equals(form.getDictionaryObject(COSName.TYPE))) {
                throw new IOException("Form Type is malformed");
            }
            requireNumberArray(form, COSName.BBOX, 4, true);
            requireNumberArray(form, COSName.MATRIX, 6, false);
            COSBase resources = form.getDictionaryObject(COSName.RESOURCES);
            if (resources != null
                    && (!(resources instanceof COSDictionary)
                            || resources instanceof COSStream)) {
                throw new IOException("Form Resources is malformed");
            }
            COSBase formType = form.getDictionaryObject(COSName.FORMTYPE);
            if (formType != null
                    && (!(formType instanceof COSInteger)
                            || ((COSInteger) formType).longValue() != 1L)) {
                throw new IOException("FormType is unsupported");
            }
        }

        private static void requireNumberArray(
                COSDictionary dictionary,
                COSName name,
                int size,
                boolean required) throws IOException {
            COSBase value = dictionary.getDictionaryObject(name);
            if (value == null && !required) {
                return;
            }
            if (!(value instanceof COSArray) || ((COSArray) value).size() != size) {
                throw new IOException(name.getName() + " is malformed");
            }
            COSArray values = (COSArray) value;
            for (int index = 0; index < values.size(); index++) {
                COSBase member = values.getObject(index);
                if (!(member instanceof COSNumber)) {
                    throw new IOException(name.getName() + " is malformed");
                }
                float number = ((COSNumber) member).floatValue();
                if (Float.isNaN(number) || Float.isInfinite(number)) {
                    throw new IOException(name.getName() + " is malformed");
                }
            }
        }

        @Override
        public String getName() {
            return OperatorName.DRAW_OBJECT;
        }
    }

    private static final class SequenceBuilder {

        private final int id;
        private final String tag;
        private final Integer markedContentId;
        private final Integer parentId;
        private final String language;
        private final String alternateText;
        private final String actualText;
        private final List<Integer> textItemIndices = new ArrayList<Integer>();

        SequenceBuilder(
                int id,
                String tag,
                Integer markedContentId,
                Integer parentId,
                String language,
                String alternateText,
                String actualText) {
            this.id = id;
            this.tag = tag;
            this.markedContentId = markedContentId;
            this.parentId = parentId;
            this.language = language;
            this.alternateText = alternateText;
            this.actualText = actualText;
        }

        MarkedContentSequence detach() {
            return new MarkedContentSequence(
                    id,
                    tag,
                    markedContentId,
                    parentId,
                    language,
                    alternateText,
                    actualText,
                    textItemIndices);
        }
    }

    private static final class StructureExtractor {

        private static final COSName STRUCT_TREE_ROOT =
                COSName.getPDFName("StructTreeRoot");
        private static final COSName ROLE_MAP = COSName.getPDFName("RoleMap");
        private static final COSName NAMESPACES =
                COSName.getPDFName("Namespaces");
        private static final COSName NAMESPACE = COSName.getPDFName("NS");
        private static final COSName STRUCTURE_TYPE = COSName.getPDFName("S");
        private static final COSName PAGE = COSName.getPDFName("Pg");
        private static final COSName CHILDREN = COSName.getPDFName("K");
        private static final COSName MCR = COSName.getPDFName("MCR");
        private static final COSName STREAM = COSName.getPDFName("Stm");
        private static final COSName STREAM_OWNER = COSName.getPDFName("StmOwn");

        private static final Set<String> STANDARD_ROLES =
                Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                        "Document", "Part", "Art", "Sect",
                        "Div", "BlockQuote", "Caption", "TOC",
                        "TOCI", "Index", "NonStruct", "Private", "P", "H",
                        "H1", "H2", "H3", "H4", "H5", "H6",
                        "L", "LI", "Lbl", "LBody",
                        "Table", "TR", "TH", "TD", "THead", "TBody",
                        "TFoot", "Span", "Quote", "Note",
                        "Reference", "BibEntry", "Code", "Link", "Annot",
                        "Ruby", "RB", "RT", "RP", "Warichu", "WT", "WP",
                        "Figure", "Formula", "Form")));

        private final PDDocument document;
        private final ExtractionState state;
        private final List<PageText> pages;
        private final IdentityHashMap<COSDictionary, Integer> pageNumbers =
                new IdentityHashMap<COSDictionary, Integer>();
        private final IdentityHashMap<COSDictionary, Boolean> visitedElements =
                new IdentityHashMap<COSDictionary, Boolean>();
        private final Map<String, String> roleMap =
                new HashMap<String, String>();

        StructureExtractor(
                PDDocument document,
                ExtractionState state,
                List<PageText> pages,
                List<COSDictionary> pageDictionaries) {
            this.document = document;
            this.state = state;
            this.pages = pages;
            for (int index = 0; index < pageDictionaries.size(); index++) {
                pageNumbers.put(
                        pageDictionaries.get(index),
                        Integer.valueOf(index + 1));
            }
        }

        List<LogicalStructureElement> extract() throws IOException {
            COSDictionary catalog = document.getDocumentCatalog()
                    .getCOSObject();
            COSBase rootValue = catalog.getDictionaryObject(STRUCT_TREE_ROOT);
            if (rootValue == null) {
                return Collections.emptyList();
            }
            COSDictionary root = requiredDictionary(rootValue);
            if (!STRUCT_TREE_ROOT.equals(root.getDictionaryObject(COSName.TYPE))) {
                throw new IOException("StructTreeRoot Type is malformed");
            }
            if (root.getDictionaryObject(NAMESPACES) != null) {
                throw new IOException(
                        "PDF 2.0 structure namespaces are outside version 1");
            }
            readRoleMap(root);
            String documentLanguage = optionalString(catalog, COSName.LANG);
            Language inherited = documentLanguage == null
                    ? Language.none()
                    : new Language(
                            documentLanguage,
                            LogicalStructureElement.LanguageSource.DOCUMENT);
            COSBase children = root.getDictionaryObject(CHILDREN);
            if (children == null) {
                return Collections.emptyList();
            }
            return rootElements(children, inherited, root);
        }

        private void readRoleMap(COSDictionary root) throws IOException {
            COSBase value = root.getDictionaryObject(ROLE_MAP);
            if (value == null) {
                return;
            }
            COSDictionary mappings = requiredDictionary(value);
            for (COSName key : mappings.keySet()) {
                state.nextRoleMapping();
                if (STANDARD_ROLES.contains(key.getName())) {
                    throw new IOException(
                            "RoleMap cannot redefine a standard role");
                }
                COSBase mapped = mappings.getDictionaryObject(key);
                if (!(mapped instanceof COSName)) {
                    throw new IOException("RoleMap value is not a name");
                }
                String declared = key.getName();
                String resolved = ((COSName) mapped).getName();
                account(declared);
                account(resolved);
                roleMap.put(declared, resolved);
            }
            for (String role : roleMap.keySet()) {
                resolveRole(role);
            }
        }

        private List<LogicalStructureElement> rootElements(
                COSBase value,
                Language inherited,
                COSDictionary root) throws IOException {
            List<LogicalStructureElement> roots =
                    new ArrayList<LogicalStructureElement>();
            IdentityHashMap<COSDictionary, Boolean> active =
                    new IdentityHashMap<COSDictionary, Boolean>();
            if (value instanceof COSArray) {
                COSArray array = (COSArray) value;
                for (int index = 0; index < array.size(); index++) {
                    state.nextStructureItem();
                    roots.add(element(
                            requiredDictionary(array.getObject(index)),
                            1,
                            inherited,
                            null,
                            root,
                            active));
                }
            } else {
                state.nextStructureItem();
                roots.add(element(
                        requiredDictionary(value),
                        1,
                        inherited,
                        null,
                        root,
                        active));
            }
            return roots;
        }

        private LogicalStructureElement element(
                COSDictionary dictionary,
                int depth,
                Language inheritedLanguage,
                Integer inheritedPage,
                COSDictionary expectedParent,
                IdentityHashMap<COSDictionary, Boolean> active)
                throws IOException {
            Deque<ElementFrame> stack = new ArrayDeque<ElementFrame>();
            stack.push(beginElement(
                    dictionary,
                    depth,
                    inheritedLanguage,
                    inheritedPage,
                    expectedParent,
                    active));
            while (!stack.isEmpty()) {
                ElementFrame current = stack.peek();
                if (!current.hasNextChild()) {
                    LogicalStructureElement completed = current.detach();
                    stack.pop();
                    active.remove(current.dictionary);
                    if (stack.isEmpty()) {
                        return completed;
                    }
                    stack.peek().children.add(
                            LogicalStructureItem.element(completed));
                    continue;
                }

                COSBase value = current.nextChild();
                state.nextStructureItem();
                if (value instanceof COSInteger) {
                    long markedContentId = ((COSInteger) value).longValue();
                    if (markedContentId < 0L
                            || markedContentId > Integer.MAX_VALUE) {
                        throw new IOException(
                                "Marked-content identifier is out of range");
                    }
                    current.children.add(LogicalStructureItem.markedContent(
                            contentReference(
                                    (int) markedContentId,
                                    current.page)));
                    continue;
                }
                COSDictionary child = requiredDictionary(value);
                COSBase type = child.getDictionaryObject(COSName.TYPE);
                if (type != null && !(type instanceof COSName)) {
                    throw new IOException(
                            "Logical-structure child Type is malformed");
                }
                if (MCR.equals(type)) {
                    current.children.add(LogicalStructureItem.markedContent(
                            contentReference(child, current.page)));
                } else if (type == null || COSName.STRUCT_ELEM.equals(type)) {
                    stack.push(beginElement(
                            child,
                            current.depth + 1,
                            current.effectiveLanguage,
                            current.page,
                            current.dictionary,
                            active));
                } else {
                    throw new IOException(
                            "Unsupported logical-structure child");
                }
            }
            throw new IOException("Logical structure traversal failed");
        }

        private ElementFrame beginElement(
                COSDictionary dictionary,
                int depth,
                Language inheritedLanguage,
                Integer inheritedPage,
                COSDictionary expectedParent,
                IdentityHashMap<COSDictionary, Boolean> active)
                throws IOException {
            if (visitedElements.put(dictionary, Boolean.TRUE) != null) {
                throw new IOException("Logical-structure element is repeated");
            }
            if (active.put(dictionary, Boolean.TRUE) != null) {
                throw new IOException("Cyclic logical structure");
            }
            boolean accepted = false;
            try {
                COSBase type = dictionary.getDictionaryObject(COSName.TYPE);
                if (type != null && !COSName.STRUCT_ELEM.equals(type)) {
                    throw new IOException("StructElem Type is malformed");
                }
                if (dictionary.getDictionaryObject(NAMESPACE) != null) {
                    throw new IOException(
                            "PDF 2.0 structure namespaces are outside version 1");
                }
                if (dictionary.getDictionaryObject(COSName.P) != expectedParent) {
                    throw new IOException("StructElem parent is inconsistent");
                }
                int id = state.nextStructureElement(depth);
                String role = requiredName(dictionary, STRUCTURE_TYPE);
                RoleResult resolved = resolveRole(role);
                String declaredLanguage = optionalString(
                        dictionary, COSName.LANG);
                Language effective = effectiveLanguage(
                        declaredLanguage, inheritedLanguage);
                String alternate = optionalString(dictionary, COSName.ALT);
                String actual = optionalString(
                        dictionary, COSName.ACTUAL_TEXT);
                account(role);
                if (resolved.resolvedRole != null
                        && !resolved.resolvedRole.equals(role)) {
                    account(resolved.resolvedRole);
                }
                accountNullable(declaredLanguage);
                if (declaredLanguage == null) {
                    accountNullable(effective.value);
                }
                accountNullable(alternate);
                accountNullable(actual);
                Integer page = elementPage(dictionary, inheritedPage);
                ElementFrame frame = new ElementFrame(
                        dictionary,
                        depth,
                        id,
                        role,
                        resolved,
                        declaredLanguage,
                        effective,
                        alternate,
                        actual,
                        page,
                        dictionary.getDictionaryObject(CHILDREN));
                accepted = true;
                return frame;
            } finally {
                if (!accepted) {
                    active.remove(dictionary);
                }
            }
        }

        private MarkedContentReference contentReference(
                COSDictionary dictionary,
                Integer inheritedPage) throws IOException {
            if (dictionary.getDictionaryObject(STREAM) != null
                    || dictionary.getDictionaryObject(STREAM_OWNER) != null) {
                throw new IOException(
                        "Form-stream marked-content references are outside version 1");
            }
            Integer page = elementPage(dictionary, inheritedPage);
            Integer mcid = optionalNonNegativeInteger(
                    dictionary, COSName.MCID);
            if (mcid == null) {
                throw new IOException("Marked-content reference has no MCID");
            }
            return contentReference(mcid.intValue(), page);
        }

        private MarkedContentReference contentReference(
                int markedContentId,
                Integer page) throws IOException {
            if (markedContentId < 0 || page == null) {
                throw new IOException("Marked-content reference has no page");
            }
            Integer sequenceId = null;
            for (MarkedContentSequence sequence : pages.get(
                    page.intValue() - 1).getMarkedContentSequences()) {
                if (sequence.getMarkedContentId().isPresent()
                        && sequence.getMarkedContentId().get().intValue()
                                == markedContentId) {
                    if (sequenceId != null) {
                        throw new IOException("Ambiguous page MCID");
                    }
                    sequenceId = Integer.valueOf(sequence.getId());
                }
            }
            return new MarkedContentReference(
                    page.intValue(), markedContentId, sequenceId);
        }

        private Integer elementPage(
                COSDictionary dictionary,
                Integer inheritedPage) throws IOException {
            COSBase value = dictionary.getDictionaryObject(PAGE);
            if (value == null) {
                return inheritedPage;
            }
            if (!(value instanceof COSDictionary)) {
                throw new IOException("Structure page is not a dictionary");
            }
            Integer number = pageNumbers.get((COSDictionary) value);
            if (number == null) {
                throw new IOException("Structure page is not in the document");
            }
            return number;
        }

        private RoleResult resolveRole(String role) throws IOException {
            if (STANDARD_ROLES.contains(role)) {
                return new RoleResult(
                        role,
                        LogicalStructureElement.RoleResolution.STANDARD);
            }
            String current = role;
            Set<String> visited = new HashSet<String>();
            while (roleMap.containsKey(current)) {
                if (!visited.add(current)) {
                    throw new IOException("Cyclic RoleMap");
                }
                current = roleMap.get(current);
                if (STANDARD_ROLES.contains(current)) {
                    return new RoleResult(
                            current,
                            LogicalStructureElement.RoleResolution.ROLE_MAP);
                }
            }
            return new RoleResult(
                    null,
                    LogicalStructureElement.RoleResolution.UNRESOLVED);
        }

        private void account(String value) throws ExtractionLimitException {
            state.accountUnicode(value);
        }

        private void accountNullable(String value)
                throws ExtractionLimitException {
            if (value != null) {
                account(value);
            }
        }

        private static final class ElementFrame {

            private final COSDictionary dictionary;
            private final int depth;
            private final int id;
            private final String role;
            private final RoleResult resolved;
            private final String declaredLanguage;
            private final Language effectiveLanguage;
            private final String alternate;
            private final String actual;
            private final Integer page;
            private final COSBase childValue;
            private final List<LogicalStructureItem> children =
                    new ArrayList<LogicalStructureItem>();
            private int childIndex;

            ElementFrame(
                    COSDictionary dictionary,
                    int depth,
                    int id,
                    String role,
                    RoleResult resolved,
                    String declaredLanguage,
                    Language effectiveLanguage,
                    String alternate,
                    String actual,
                    Integer page,
                    COSBase childValue) {
                this.dictionary = dictionary;
                this.depth = depth;
                this.id = id;
                this.role = role;
                this.resolved = resolved;
                this.declaredLanguage = declaredLanguage;
                this.effectiveLanguage = effectiveLanguage;
                this.alternate = alternate;
                this.actual = actual;
                this.page = page;
                this.childValue = childValue;
            }

            boolean hasNextChild() {
                if (childValue == null) {
                    return false;
                }
                if (childValue instanceof COSArray) {
                    return childIndex < ((COSArray) childValue).size();
                }
                return childIndex == 0;
            }

            COSBase nextChild() {
                if (childValue instanceof COSArray) {
                    return ((COSArray) childValue).getObject(childIndex++);
                }
                childIndex++;
                return childValue;
            }

            LogicalStructureElement detach() {
                return new LogicalStructureElement(
                        id,
                        role,
                        resolved.resolvedRole,
                        resolved.resolution,
                        declaredLanguage,
                        effectiveLanguage.value,
                        effectiveLanguage.source,
                        alternate,
                        actual,
                        children);
            }
        }

        private static Language effectiveLanguage(
                String declared,
                Language inherited) {
            if (declared != null) {
                return new Language(
                        declared,
                        LogicalStructureElement.LanguageSource.SELF);
            }
            if (inherited.source
                    == LogicalStructureElement.LanguageSource.SELF
                    || inherited.source
                    == LogicalStructureElement.LanguageSource.ANCESTOR) {
                return new Language(
                        inherited.value,
                        LogicalStructureElement.LanguageSource.ANCESTOR);
            }
            return inherited;
        }
    }

    private static final class RoleResult {

        private final String resolvedRole;
        private final LogicalStructureElement.RoleResolution resolution;

        RoleResult(
                String resolvedRole,
                LogicalStructureElement.RoleResolution resolution) {
            this.resolvedRole = resolvedRole;
            this.resolution = resolution;
        }
    }

    private static final class Language {

        private final String value;
        private final LogicalStructureElement.LanguageSource source;

        Language(
                String value,
                LogicalStructureElement.LanguageSource source) {
            this.value = value;
            this.source = source;
        }

        static Language none() {
            return new Language(
                    null,
                    LogicalStructureElement.LanguageSource.NONE);
        }
    }

    private static String optionalString(
            COSDictionary dictionary,
            COSName name) throws IOException {
        if (dictionary == null) {
            return null;
        }
        COSBase value = dictionary.getDictionaryObject(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof COSString)) {
            throw new IOException(name.getName() + " is not a string");
        }
        return ((COSString) value).getString();
    }

    private static Integer optionalNonNegativeInteger(
            COSDictionary dictionary,
            COSName name) throws IOException {
        if (dictionary == null) {
            return null;
        }
        COSBase value = dictionary.getDictionaryObject(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof COSInteger)) {
            throw new IOException(name.getName() + " is not an integer");
        }
        long number = ((COSInteger) value).longValue();
        if (number < 0L || number > Integer.MAX_VALUE) {
            throw new IOException(name.getName() + " is out of range");
        }
        return Integer.valueOf((int) number);
    }

    private static COSDictionary requiredDictionary(COSBase value)
            throws IOException {
        if (!(value instanceof COSDictionary)
                || value instanceof COSStream) {
            throw new IOException("Expected a structure dictionary");
        }
        return (COSDictionary) value;
    }

    private static String requiredName(
            COSDictionary dictionary,
            COSName name) throws IOException {
        COSBase value = dictionary.getDictionaryObject(name);
        if (!(value instanceof COSName)) {
            throw new IOException(name.getName() + " is not a name");
        }
        return ((COSName) value).getName();
    }

    private static final class SourceCode {

        private final int numericValue;
        private final byte[] bytes;

        SourceCode(int numericValue, byte[] bytes) {
            this.numericValue = numericValue;
            this.bytes = bytes;
        }
    }

    private static final class SourceCodeFrame {

        private final PDFont font;
        private final byte[] source;
        private final ByteArrayInputStream input;
        private int offset;

        SourceCodeFrame(PDFont font, byte[] source) {
            this.font = font;
            this.source = source;
            this.input = new ByteArrayInputStream(source);
        }

        boolean hasRemaining() {
            return input.available() > 0;
        }

        SourceCode next() throws IOException {
            if (!hasRemaining()) {
                throw new IOException("Text source code was unavailable");
            }
            int before = input.available();
            int code = font.readCode(input);
            int length = before - input.available();
            if (length < 1) {
                throw new IOException("Font consumed no source-code bytes");
            }
            byte[] bytes = Arrays.copyOfRange(source, offset, offset + length);
            offset += length;
            return new SourceCode(code, bytes);
        }
    }

    private static final class ExtractionLimitException extends IOException {

        private static final long serialVersionUID = 1L;
    }

    private static final class ExtractionLimitRuntimeException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    private static final class ExtractionMalformedRuntimeException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    private static BigDecimal decimal(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new ExtractionMalformedRuntimeException();
        }
        if (Float.compare(value, -0.0f) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = new BigDecimal(Float.toString(value))
                .stripTrailingZeros();
        return decimal.scale() < 0 ? decimal.setScale(0) : decimal;
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }
}
