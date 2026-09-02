package net.zerocloud.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.junit.Test;

public final class PdfBoxFontMetricPreflightTest {

    @Test
    public void countsSimpleWidthsBeforeBackendListCreation()
            throws Exception {
        COSDictionary font = font(COSName.TYPE1);
        font.setInt(COSName.FIRST_CHAR, 65);
        font.setInt(COSName.LAST_CHAR, 66);
        font.setItem(COSName.WIDTHS, array(integer(500), integer(600)));

        assertEquals(2, PdfBoxFontMetricPreflight.countEntries(font, 2));
        assertExhausted(font, 1);
    }

    @Test
    public void countsCidWidthArraysAndExpandedCompactRanges()
            throws Exception {
        COSDictionary compact = font(COSName.CID_FONT_TYPE2);
        compact.setItem(COSName.W,
                array(integer(65), integer(66), integer(500)));
        COSDictionary explicit = font(COSName.CID_FONT_TYPE2);
        explicit.setItem(COSName.W,
                array(integer(65), array(integer(500), integer(600))));

        assertEquals(5,
                PdfBoxFontMetricPreflight.countEntries(compact, 5));
        assertEquals(4,
                PdfBoxFontMetricPreflight.countEntries(explicit, 4));
        assertExhausted(compact, 4);
        assertExhausted(explicit, 3);
    }

    @Test
    public void countsAndValidatesVerticalMetricArrays() throws Exception {
        COSDictionary font = font(COSName.CID_FONT_TYPE0);
        font.setItem(COSName.DW2, array(integer(880), integer(-1000)));
        font.setItem(COSName.W2, array(
                integer(65), integer(66), integer(-1000),
                integer(250), integer(880)));

        assertEquals(7, PdfBoxFontMetricPreflight.countEntries(font, 7));
        assertExhausted(font, 6);

        font.setItem(COSName.W2, array(integer(65), integer(66)));
        assertMalformed(font);
    }

    @Test
    public void rejectsRangesThatWouldOverflowTheBackendLoop()
            throws Exception {
        COSDictionary huge = font(COSName.CID_FONT_TYPE2);
        huge.setItem(COSName.W, array(
                integer(0), integer(Integer.MAX_VALUE), integer(500)));
        assertExhausted(huge, 8);

        COSDictionary wrapping = font(COSName.CID_FONT_TYPE2);
        wrapping.setItem(COSName.W, array(
                integer(Integer.MAX_VALUE),
                integer(Integer.MAX_VALUE),
                integer(500)));
        assertMalformed(wrapping);
    }

    @Test
    public void validatesScalarMetricFieldsAndIntegerSelectors()
            throws Exception {
        COSDictionary defaultWidth = font(COSName.CID_FONT_TYPE2);
        defaultWidth.setItem(COSName.DW, COSName.getPDFName("Bad"));
        assertMalformed(defaultWidth);

        COSDictionary fractionalCid = font(COSName.CID_FONT_TYPE2);
        fractionalCid.setItem(COSName.W, array(
                new COSFloat(65.5f), array(integer(500))));
        assertMalformed(fractionalCid);

        COSDictionary reversedCidRange = font(COSName.CID_FONT_TYPE2);
        reversedCidRange.setItem(COSName.W, array(
                integer(66), integer(65), integer(500)));
        assertMalformed(reversedCidRange);

        COSDictionary simple = font(COSName.TYPE1);
        simple.setItem(COSName.FIRST_CHAR, COSName.getPDFName("Bad"));
        simple.setInt(COSName.LAST_CHAR, 65);
        simple.setItem(COSName.WIDTHS, array(integer(500)));
        assertMalformed(simple);
    }

    private static COSDictionary font(COSName subtype) {
        COSDictionary font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setItem(COSName.SUBTYPE, subtype);
        return font;
    }

    private static COSInteger integer(int value) {
        return COSInteger.get(value);
    }

    private static COSArray array(COSBase... values) {
        COSArray array = new COSArray();
        for (COSBase value : values) {
            array.add(value);
        }
        return array;
    }

    private static void assertExhausted(
            COSDictionary font,
            int maximum) throws Exception {
        try {
            PdfBoxFontMetricPreflight.countEntries(font, maximum);
            fail("Expected font-data limit exhaustion");
        } catch (PdfBoxFontMetricPreflight.LimitExceededException expected) {
            // Expected caller-bound rejection.
        }
    }

    private static void assertMalformed(COSDictionary font)
            throws Exception {
        try {
            PdfBoxFontMetricPreflight.countEntries(font, Integer.MAX_VALUE);
            fail("Expected malformed metric rejection");
        } catch (IOException expected) {
            // Expected safe rejection before PDFBox font construction.
        }
    }
}
