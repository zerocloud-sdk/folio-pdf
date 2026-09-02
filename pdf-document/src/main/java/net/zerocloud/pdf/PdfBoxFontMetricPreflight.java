package net.zerocloud.pdf;

import java.io.IOException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;

/** Bounds PDFBox font-metric traversal before font construction. */
final class PdfBoxFontMetricPreflight {

    private PdfBoxFontMetricPreflight() {
    }

    static int countEntries(COSDictionary font, int maximum)
            throws IOException, LimitExceededException {
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum must not be negative");
        }
        Counter counter = new Counter(maximum);
        COSName subtype = font.getCOSName(COSName.SUBTYPE);
        if (COSName.CID_FONT_TYPE0.equals(subtype)
                || COSName.CID_FONT_TYPE2.equals(subtype)) {
            optionalNumber(font, COSName.DW, "DW");
            countDefaultVerticalMetrics(font, counter);
            countHorizontalCidMetrics(font, counter);
            countVerticalCidMetrics(font, counter);
        } else if (!COSName.TYPE0.equals(subtype)) {
            countSimpleWidths(font, counter);
        }
        return counter.value;
    }

    private static void countSimpleWidths(
            COSDictionary font,
            Counter counter) throws IOException, LimitExceededException {
        Integer first = optionalCode(font, COSName.FIRST_CHAR);
        Integer last = optionalCode(font, COSName.LAST_CHAR);
        if ((first == null) != (last == null)) {
            throw new IOException("FirstChar and LastChar must be declared together");
        }
        if (first != null && last.intValue() < first.intValue()) {
            throw new IOException("LastChar precedes FirstChar");
        }
        COSArray widths = optionalArray(font, COSName.WIDTHS);
        if (widths == null) {
            return;
        }
        if (first == null
                || widths.size() != last.intValue() - first.intValue() + 1) {
            throw new IOException(
                    "Widths does not match FirstChar and LastChar");
        }
        counter.add(widths.size());
        requireNumbers(widths, "Widths");
    }

    private static void countDefaultVerticalMetrics(
            COSDictionary font,
            Counter counter) throws IOException, LimitExceededException {
        COSArray metrics = optionalArray(font, COSName.DW2);
        if (metrics == null) {
            return;
        }
        counter.add(metrics.size());
        if (metrics.size() != 2) {
            throw new IOException("DW2 must contain two numbers");
        }
        requireNumbers(metrics, "DW2");
    }

    private static void countHorizontalCidMetrics(
            COSDictionary font,
            Counter counter) throws IOException, LimitExceededException {
        COSArray widths = optionalArray(font, COSName.W);
        if (widths == null) {
            return;
        }
        counter.add(widths.size());
        int index = 0;
        while (index < widths.size()) {
            int first = cid(widths.getObject(index++), "W");
            if (index >= widths.size()) {
                throw new IOException("W has an incomplete width entry");
            }
            COSBase next = widths.getObject(index++);
            if (next instanceof COSArray) {
                COSArray explicitWidths = (COSArray) next;
                counter.add(explicitWidths.size());
                requireNumbers(explicitWidths, "W");
            } else {
                int last = cid(next, "W");
                if (last < first) {
                    throw new IOException("W range is reversed");
                }
                if (index >= widths.size()) {
                    throw new IOException("W has an incomplete width range");
                }
                number(widths.getObject(index++), "W");
                counter.add((long) last - first + 1L);
                if (last == Integer.MAX_VALUE) {
                    throw new IOException("W range would overflow PDFBox");
                }
            }
        }
    }

    private static void countVerticalCidMetrics(
            COSDictionary font,
            Counter counter) throws IOException, LimitExceededException {
        COSArray metrics = optionalArray(font, COSName.W2);
        if (metrics == null) {
            return;
        }
        counter.add(metrics.size());
        int index = 0;
        while (index < metrics.size()) {
            int first = cid(metrics.getObject(index++), "W2");
            if (index >= metrics.size()) {
                throw new IOException("W2 has an incomplete metric entry");
            }
            COSBase next = metrics.getObject(index++);
            if (next instanceof COSArray) {
                COSArray explicitMetrics = (COSArray) next;
                counter.add(explicitMetrics.size());
                if (explicitMetrics.size() % 3 != 0) {
                    throw new IOException(
                            "W2 explicit metrics must contain triples");
                }
                requireNumbers(explicitMetrics, "W2");
            } else {
                int last = cid(next, "W2");
                if (last < first) {
                    throw new IOException("W2 range is reversed");
                }
                if (metrics.size() - index < 3) {
                    throw new IOException("W2 has an incomplete metric range");
                }
                number(metrics.getObject(index++), "W2");
                number(metrics.getObject(index++), "W2");
                number(metrics.getObject(index++), "W2");
            }
        }
    }

    private static COSArray optionalArray(
            COSDictionary dictionary,
            COSName key) throws IOException {
        COSBase value = dictionary.getDictionaryObject(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof COSArray)) {
            throw new IOException(key.getName() + " is not an array");
        }
        return (COSArray) value;
    }

    private static void requireNumbers(COSArray values, String name)
            throws IOException {
        for (int index = 0; index < values.size(); index++) {
            number(values.getObject(index), name);
        }
    }

    private static COSNumber number(COSBase value, String name)
            throws IOException {
        if (!(value instanceof COSNumber)) {
            throw new IOException(name + " contains a non-number");
        }
        COSNumber number = (COSNumber) value;
        float converted = number.floatValue();
        if (Float.isNaN(converted) || Float.isInfinite(converted)) {
            throw new IOException(name + " contains a non-finite number");
        }
        return number;
    }

    private static void optionalNumber(
            COSDictionary dictionary,
            COSName key,
            String name) throws IOException {
        COSBase value = dictionary.getDictionaryObject(key);
        if (value != null) {
            number(value, name);
        }
    }

    private static Integer optionalCode(
            COSDictionary dictionary,
            COSName key) throws IOException {
        COSBase value = dictionary.getDictionaryObject(key);
        if (value == null) {
            return null;
        }
        int code = integer(value, key.getName());
        if (code > 255) {
            throw new IOException(key.getName() + " is outside simple-font range");
        }
        return Integer.valueOf(code);
    }

    private static int cid(COSBase value, String name) throws IOException {
        return integer(value, name);
    }

    private static int integer(COSBase value, String name) throws IOException {
        if (!(value instanceof COSInteger)) {
            throw new IOException(name + " contains a non-integer selector");
        }
        long number = ((COSInteger) value).longValue();
        if (number < 0L || number > Integer.MAX_VALUE) {
            throw new IOException(name + " contains an out-of-range selector");
        }
        return (int) number;
    }

    static final class LimitExceededException extends Exception {

        private static final long serialVersionUID = 1L;
    }

    private static final class Counter {

        private final int maximum;
        private int value;

        Counter(int maximum) {
            this.maximum = maximum;
        }

        void add(long count) throws LimitExceededException {
            if (count < 0L || count > maximum - (long) value) {
                throw new LimitExceededException();
            }
            value += (int) count;
        }
    }
}
